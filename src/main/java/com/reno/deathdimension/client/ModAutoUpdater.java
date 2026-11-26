package com.reno.deathdimension.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.reno.deathdimension.DeathDimension;
import com.reno.deathdimension.config.ModConfig;
import net.minecraft.Util;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforgespi.language.IModFileInfo;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class ModAutoUpdater {
    public static final String UPDATE_JSON_URL =
            "https://raw.githubusercontent.com/Reno11052009/death-dimension/main/update.json";
    public static final String RELEASES_URL =
            "https://github.com/Reno11052009/death-dimension/releases";
    private static final String RELEASE_DOWNLOAD_TEMPLATE =
            "https://github.com/Reno11052009/death-dimension/releases/download/v%s/deathdimension-%s.jar";

    private static final long MAX_DOWNLOAD_BYTES = 50L * 1024L * 1024L;
    private static final Pattern SAFE_VERSION = Pattern.compile("^[0-9A-Za-z][0-9A-Za-z._-]{0,63}$");
    private static final Pattern SAFE_JAR_NAME = Pattern.compile("^[0-9A-Za-z._-]+\\.jar$");
    private static final Set<String> ALLOWED_DOWNLOAD_HOSTS = Set.of(
            "github.com",
            "objects.githubusercontent.com",
            "release-assets.githubusercontent.com",
            "raw.githubusercontent.com"
    );

    public enum State {
        IDLE,
        CHECKING,
        UP_TO_DATE,
        UPDATE_AVAILABLE,
        DOWNLOADING,
        INSTALLED_RESTART_REQUIRED,
        FAILED
    }

    public record UpdateInfo(String targetVersion, String downloadUrl, String releaseUrl) {}

    public record StatusSnapshot(
            State state,
            String currentVersion,
            @Nullable String targetVersion,
            int progressPercent,
            String releaseUrl,
            @Nullable String downloadUrl,
            boolean canAutoInstall
    ) {}

    private static final AtomicBoolean autoCheckTriggered = new AtomicBoolean(false);
    private static final AtomicBoolean busy = new AtomicBoolean(false);
    private static volatile StatusSnapshot status = new StatusSnapshot(
            State.IDLE, "unknown", null, 0, RELEASES_URL, null, false
    );

    private ModAutoUpdater() {}

    public static StatusSnapshot getStatus() {
        return status;
    }

    public static void triggerInitialCheckIfNeeded() {
        if (!ModConfig.checkUpdates) {
            return;
        }
        if (autoCheckTriggered.compareAndSet(false, true)) {
            startCheckAsync(ModConfig.autoInstallUpdates);
        }
    }

    public static void startCheckAsync(boolean autoInstall) {
        if (!busy.compareAndSet(false, true)) {
            return;
        }
        String currentVersion = resolveCurrentVersion();
        String mcVersion = resolveMinecraftVersion();
        boolean canInstall = canModifyModsDir();
        status = new StatusSnapshot(State.CHECKING, currentVersion, null, 0, RELEASES_URL, null, canInstall);

        runDaemon("DeathDimension-UpdateCheck", () -> {
            try {
                HttpClient client = buildClient();
                HttpRequest request = HttpRequest.newBuilder(URI.create(UPDATE_JSON_URL))
                        .timeout(Duration.ofSeconds(15))
                        .header("User-Agent", "DeathDimension-Updater/" + currentVersion)
                        .GET()
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IOException("HTTP " + response.statusCode());
                }

                UpdateInfo info = parseUpdateManifest(response.body(), mcVersion);
                if (info == null || compareVersions(info.targetVersion(), currentVersion) <= 0) {
                    status = new StatusSnapshot(
                            State.UP_TO_DATE, currentVersion, info != null ? info.targetVersion() : currentVersion,
                            100, info != null ? info.releaseUrl() : RELEASES_URL, null, canInstall
                    );
                    return;
                }

                if (autoInstall && canInstall) {
                    performInstall(client, currentVersion, info);
                } else {
                    status = new StatusSnapshot(
                            State.UPDATE_AVAILABLE, currentVersion, info.targetVersion(),
                            0, info.releaseUrl(), info.downloadUrl(), canInstall
                    );
                }
            } catch (Exception e) {
                DeathDimension.LOGGER.warn("DeathDimension update check failed: {}", e.getMessage());
                status = new StatusSnapshot(State.FAILED, currentVersion, null, 0, RELEASES_URL, null, canInstall);
            } finally {
                busy.set(false);
            }
        });
    }

    public static void startInstallCurrentUpdateAsync() {
        StatusSnapshot snapshot = status;
        if (snapshot.targetVersion() == null || snapshot.downloadUrl() == null || !snapshot.canAutoInstall()) {
            return;
        }
        if (!busy.compareAndSet(false, true)) {
            return;
        }
        UpdateInfo info = new UpdateInfo(snapshot.targetVersion(), snapshot.downloadUrl(), snapshot.releaseUrl());
        runDaemon("DeathDimension-UpdateInstall", () -> {
            try {
                performInstall(buildClient(), snapshot.currentVersion(), info);
            } catch (Exception e) {
                DeathDimension.LOGGER.warn("DeathDimension update installation failed: {}", e.getMessage());
                status = new StatusSnapshot(
                        State.FAILED, snapshot.currentVersion(), info.targetVersion(),
                        0, info.releaseUrl(), info.downloadUrl(), snapshot.canAutoInstall()
                );
            } finally {
                busy.set(false);
            }
        });
    }

    private static void performInstall(HttpClient client, String currentVersion, UpdateInfo info)
            throws IOException, InterruptedException {
        if (!isAllowedDownloadUrl(info.downloadUrl())) {
            throw new IOException("Disallowed download URL host");
        }
        if (!SAFE_VERSION.matcher(info.targetVersion()).matches()) {
            throw new IOException("Unsafe target version string");
        }

        Path modsDir = FMLPaths.MODSDIR.get().toAbsolutePath().normalize();
        Files.createDirectories(modsDir);
        Path targetJar = modsDir.resolve("deathdimension-" + info.targetVersion() + ".jar").normalize();
        Path partFile = modsDir.resolve("deathdimension-" + info.targetVersion() + ".jar.part").normalize();
        if (!targetJar.startsWith(modsDir) || !partFile.startsWith(modsDir)) {
            throw new IOException("Resolved path escapes mods directory");
        }

        status = new StatusSnapshot(
                State.DOWNLOADING, currentVersion, info.targetVersion(),
                0, info.releaseUrl(), info.downloadUrl(), true
        );

        HttpRequest request = HttpRequest.newBuilder(URI.create(info.downloadUrl()))
                .timeout(Duration.ofSeconds(60))
                .header("User-Agent", "DeathDimension-Updater/" + currentVersion)
                .GET()
                .build();

        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            try (InputStream ignored = response.body()) {
                // close body
            }
            throw new IOException("Download failed with HTTP " + response.statusCode());
        }

        long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
        if (contentLength > MAX_DOWNLOAD_BYTES) {
            try (InputStream ignored = response.body()) {
                // close body
            }
            throw new IOException("Download exceeds safety limit");
        }

        try {
            long totalRead = 0L;
            int lastPercent = -1;
            try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(partFile)) {
                byte[] buffer = new byte[16384];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    totalRead += read;
                    if (totalRead > MAX_DOWNLOAD_BYTES) {
                        throw new IOException("Download exceeded safety limit");
                    }
                    out.write(buffer, 0, read);
                    if (contentLength > 0L) {
                        int percent = (int) Math.min(99L, (totalRead * 100L) / contentLength);
                        if (percent != lastPercent) {
                            lastPercent = percent;
                            status = new StatusSnapshot(
                                    State.DOWNLOADING, currentVersion, info.targetVersion(),
                                    percent, info.releaseUrl(), info.downloadUrl(), true
                            );
                        }
                    }
                }
            }

            if (!isValidModJar(partFile)) {
                throw new IOException("Downloaded file failed DeathDimension JAR integrity check");
            }

            Files.move(partFile, targetJar, StandardCopyOption.REPLACE_EXISTING);
            cleanupOldModJar(modsDir, targetJar);

            DeathDimension.LOGGER.info("Installed DeathDimension {} to {}", info.targetVersion(), targetJar);
            status = new StatusSnapshot(
                    State.INSTALLED_RESTART_REQUIRED, currentVersion, info.targetVersion(),
                    100, info.releaseUrl(), info.downloadUrl(), true
            );
        } catch (IOException e) {
            try {
                Files.deleteIfExists(partFile);
            } catch (IOException ignored) {
            }
            throw e;
        }
    }

    private static void cleanupOldModJar(Path modsDir, Path newJar) {
        Path currentJar = resolveCurrentModJar();
        if (currentJar == null) {
            return;
        }
        Path normalizedOld = currentJar.toAbsolutePath().normalize();
        Path normalizedNew = newJar.toAbsolutePath().normalize();
        if (normalizedOld.equals(normalizedNew) || !Files.isRegularFile(normalizedOld) || !normalizedOld.startsWith(modsDir)) {
            return;
        }
        try {
            Files.deleteIfExists(normalizedOld);
        } catch (IOException lockedOnWindows) {
            normalizedOld.toFile().deleteOnExit();
            scheduleDeferredWindowsDelete(modsDir, normalizedOld);
        }
    }

    private static void scheduleDeferredWindowsDelete(Path modsDir, Path oldJar) {
        if (Util.getPlatform() != Util.OS.WINDOWS) {
            return;
        }
        String fileName = oldJar.getFileName() != null ? oldJar.getFileName().toString() : "";
        if (!oldJar.startsWith(modsDir) || !SAFE_JAR_NAME.matcher(fileName).matches()) {
            return;
        }
        // ponytail: Windows locks loaded JARs until JVM exit; spawns a single delayed del only for this validated mods/*.jar path.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                new ProcessBuilder("cmd.exe", "/c", "ping -n 3 127.0.0.1 >nul & del /f /q \"" + oldJar + "\"")
                        .start();
            } catch (IOException ignored) {
            }
        }, "DeathDimension-OldJarCleanup"));
    }

    @Nullable
    public static UpdateInfo parseUpdateManifest(String jsonText, String mcVersion) {
        if (jsonText == null || jsonText.isBlank() || mcVersion == null || mcVersion.isBlank()) {
            return null;
        }
        JsonElement parsed = JsonParser.parseString(jsonText);
        if (!parsed.isJsonObject()) {
            return null;
        }
        JsonObject root = parsed.getAsJsonObject();

        String homepage = getString(root, "homepage");
        String releaseUrl = isAllowedDownloadUrl(homepage) ? homepage : RELEASES_URL;

        JsonObject promos = getObject(root, "promos");
        if (promos == null) {
            return null;
        }
        String recommended = getString(promos, mcVersion + "-recommended");
        String latest = getString(promos, mcVersion + "-latest");
        String target = pickHigherVersion(recommended, latest);
        if (target == null || !SAFE_VERSION.matcher(target).matches()) {
            return null;
        }

        String downloadUrl = null;
        JsonObject downloads = getObject(root, "downloads");
        if (downloads != null) {
            JsonObject mcDownloads = getObject(downloads, mcVersion);
            if (mcDownloads != null) {
                String candidate = getString(mcDownloads, target);
                if (isAllowedDownloadUrl(candidate)) {
                    downloadUrl = candidate;
                }
            }
        }
        if (downloadUrl == null) {
            downloadUrl = String.format(Locale.ROOT, RELEASE_DOWNLOAD_TEMPLATE, target, target);
        }

        return new UpdateInfo(target, downloadUrl, releaseUrl);
    }

    public static boolean isAllowedDownloadUrl(@Nullable String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(url.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                return false;
            }
            String host = uri.getHost();
            return host != null && ALLOWED_DOWNLOAD_HOSTS.contains(host.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static boolean isValidModJar(Path jarPath) {
        if (jarPath == null || !Files.isRegularFile(jarPath)) {
            return false;
        }
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(jarPath))) {
            ZipEntry entry;
            boolean hasModsToml = false;
            boolean mentionsModId = false;
            int entryCount = 0;
            while ((entry = zis.getNextEntry()) != null) {
                entryCount++;
                String name = entry.getName();
                if ("META-INF/neoforge.mods.toml".equals(name)) {
                    hasModsToml = true;
                    byte[] bytes = zis.readNBytes(32_768);
                    String content = new String(bytes, StandardCharsets.UTF_8);
                    mentionsModId = content.contains(DeathDimension.MODID);
                }
                zis.closeEntry();
            }
            return entryCount > 0 && hasModsToml && mentionsModId;
        } catch (IOException e) {
            return false;
        }
    }

    public static int compareVersions(String a, String b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;

        String[] aSplit = a.trim().split("-", 2);
        String[] bSplit = b.trim().split("-", 2);
        String[] aParts = aSplit[0].split("\\.");
        String[] bParts = bSplit[0].split("\\.");

        int max = Math.max(aParts.length, bParts.length);
        for (int i = 0; i < max; i++) {
            String ap = i < aParts.length ? aParts[i] : "0";
            String bp = i < bParts.length ? bParts[i] : "0";
            int cmp = compareSegment(ap, bp);
            if (cmp != 0) {
                return cmp;
            }
        }

        boolean aPre = aSplit.length > 1 && !aSplit[1].isEmpty();
        boolean bPre = bSplit.length > 1 && !bSplit[1].isEmpty();
        if (aPre && !bPre) return -1;
        if (!aPre && bPre) return 1;
        if (aPre) {
            return aSplit[1].compareToIgnoreCase(bSplit[1]);
        }
        return 0;
    }

    private static int compareSegment(String a, String b) {
        try {
            return Long.compare(Long.parseLong(a), Long.parseLong(b));
        } catch (NumberFormatException ignored) {
            return a.compareToIgnoreCase(b);
        }
    }

    @Nullable
    private static String pickHigherVersion(@Nullable String a, @Nullable String b) {
        boolean aValid = a != null && SAFE_VERSION.matcher(a.trim()).matches();
        boolean bValid = b != null && SAFE_VERSION.matcher(b.trim()).matches();
        if (!aValid && !bValid) return null;
        if (!aValid) return b.trim();
        if (!bValid) return a.trim();
        return compareVersions(a, b) >= 0 ? a.trim() : b.trim();
    }

    @Nullable
    private static String getString(JsonObject obj, String key) {
        JsonElement el = obj.get(key);
        return (el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) ? el.getAsString() : null;
    }

    @Nullable
    private static JsonObject getObject(JsonObject obj, String key) {
        JsonElement el = obj.get(key);
        return (el != null && el.isJsonObject()) ? el.getAsJsonObject() : null;
    }

    private static HttpClient buildClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    private static void runDaemon(String name, Runnable task) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
    }

    private static String resolveCurrentVersion() {
        Optional<? extends ModContainer> container = ModList.get().getModContainerById(DeathDimension.MODID);
        return container.map(c -> c.getModInfo().getVersion().toString()).orElse("0.0.0");
    }

    private static String resolveMinecraftVersion() {
        return FMLLoader.versionInfo().mcVersion();
    }

    @Nullable
    private static Path resolveCurrentModJar() {
        IModFileInfo info = ModList.get().getModFileById(DeathDimension.MODID);
        if (info == null || info.getFile() == null) {
            return null;
        }
        Path path = info.getFile().getFilePath();
        return (path != null && Files.isRegularFile(path) && path.getFileName().toString().endsWith(".jar")) ? path : null;
    }

    private static boolean canModifyModsDir() {
        if (!FMLLoader.isProduction()) {
            return false;
        }
        Path currentJar = resolveCurrentModJar();
        return currentJar != null;
    }
}
