package com.reno.deathdimension.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.reno.deathdimension.config.ModConfig;
import com.reno.deathdimension.config.ReturnMode;
import com.reno.deathdimension.cooldown.CooldownManager;
import com.reno.deathdimension.dimension.DeathDimensionSavedData;
import com.reno.deathdimension.network.ConfigSyncPayload;
import com.reno.deathdimension.player.PlayerDeathData;
import com.reno.deathdimension.player.PlayerTeleportHandler;
import com.reno.deathdimension.registry.ModRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class DeathDimensionCommands {
    private DeathDimensionCommands() {}

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("deathdimension")
                .executes(ctx -> showInfo(ctx.getSource()))
                .then(Commands.literal("return")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            return PlayerTeleportHandler.returnFromDeathDimension(player, false) ? 1 : 0;
                        })
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(src -> src.hasPermission(2))
                                .executes(ctx -> {
                                    if (!ModConfig.allowAdminReturn) {
                                        ctx.getSource().sendFailure(Component.translatable("message.deathdimension.admin_return_disabled"));
                                        return 0;
                                    }
                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                    boolean ok = PlayerTeleportHandler.returnFromDeathDimension(target, true);
                                    if (ok) {
                                        ctx.getSource().sendSuccess(
                                                () -> Component.translatable("message.deathdimension.admin_returned", target.getDisplayName()),
                                                true
                                        );
                                    }
                                    return ok ? 1 : 0;
                                })))
                .then(Commands.literal("tp")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            return PlayerTeleportHandler.enterDeathDimension(player, false) ? 1 : 0;
                        })
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> {
                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                    boolean ok = PlayerTeleportHandler.enterDeathDimension(target, false);
                                    if (ok) {
                                        ctx.getSource().sendSuccess(
                                                () -> Component.translatable("message.deathdimension.admin_teleported", target.getDisplayName()),
                                                true
                                        );
                                    }
                                    return ok ? 1 : 0;
                                })))
                .then(Commands.literal("setspawn")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            return setSpawn(ctx.getSource(), player.getX(), player.getY(), player.getZ());
                        })
                        .then(Commands.argument("x", DoubleArgumentType.doubleArg(-30_000_000.0, 30_000_000.0))
                                .then(Commands.argument("y", DoubleArgumentType.doubleArg(-64.0, 320.0))
                                        .then(Commands.argument("z", DoubleArgumentType.doubleArg(-30_000_000.0, 30_000_000.0))
                                                .executes(ctx -> setSpawn(
                                                        ctx.getSource(),
                                                        DoubleArgumentType.getDouble(ctx, "x"),
                                                        DoubleArgumentType.getDouble(ctx, "y"),
                                                        DoubleArgumentType.getDouble(ctx, "z")
                                                ))))))
                .then(Commands.literal("reload")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> {
                            if (ModConfig.reloadFromDisk()) {
                                PacketDistributor.sendToAllPlayers(new ConfigSyncPayload(ModConfig.captureSnapshot(), false));
                                ctx.getSource().sendSuccess(
                                        () -> Component.translatable("message.deathdimension.reloaded").withStyle(ChatFormatting.GREEN),
                                        true
                                );
                                return 1;
                            }
                            ctx.getSource().sendFailure(Component.translatable("message.deathdimension.reload_failed"));
                            return 0;
                        }))
                .then(Commands.literal("config")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            PacketDistributor.sendToPlayer(player, new ConfigSyncPayload(ModConfig.captureSnapshot(), true));
                            return 1;
                        }))
                .then(Commands.literal("returnmode")
                        .executes(ctx -> {
                            ctx.getSource().sendSuccess(
                                    () -> Component.translatable(
                                            "message.deathdimension.return_mode_current",
                                            Component.translatable(ModConfig.returnMode.translationKey())
                                    ).withStyle(ChatFormatting.AQUA),
                                    false
                            );
                            return 1;
                        })
                        .then(Commands.literal("auto")
                                .requires(src -> src.hasPermission(2))
                                .executes(ctx -> setReturnMode(ctx.getSource(), ReturnMode.AUTO)))
                        .then(Commands.literal("manual")
                                .requires(src -> src.hasPermission(2))
                                .executes(ctx -> setReturnMode(ctx.getSource(), ReturnMode.MANUAL))))
                .then(Commands.literal("cooldown")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            return showCooldown(ctx.getSource(), player);
                        })
                        .then(Commands.literal("set")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.argument("seconds", LongArgumentType.longArg(0L, 86400L * 30L))
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            long seconds = LongArgumentType.getLong(ctx, "seconds");
                                            return setCooldown(ctx.getSource(), player, seconds);
                                        })
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(ctx -> {
                                                    long seconds = LongArgumentType.getLong(ctx, "seconds");
                                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                                    return setCooldown(ctx.getSource(), target, seconds);
                                                }))))
                        .then(Commands.literal("reset")
                                .requires(src -> src.hasPermission(2))
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    return resetCooldown(ctx.getSource(), player);
                                })
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> {
                                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                            return resetCooldown(ctx.getSource(), target);
                                        })))
                        .then(Commands.literal("bypass")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> {
                                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                            return bypassCooldown(ctx.getSource(), target);
                                        }))))
        );
    }

    private static int showInfo(CommandSourceStack source) {
        Vec3 spawn = DeathDimensionSavedData.getSpawnPosition(source.getServer());
        source.sendSuccess(
                () -> Component.translatable(
                        "message.deathdimension.info",
                        ModConfig.enableMod ? "enabled" : "disabled",
                        ModConfig.enableCooldown ? CooldownManager.formatTime(ModConfig.cooldownDuration) : "disabled",
                        String.format("%.1f, %.1f, %.1f", spawn.x, spawn.y, spawn.z)
                ).withStyle(ChatFormatting.AQUA),
                false
        );
        return 1;
    }

    private static int setSpawn(CommandSourceStack source, double x, double y, double z) {
        DeathDimensionSavedData.get(source.getServer()).setCustomSpawn(x, y, z);
        source.sendSuccess(
                () -> Component.translatable(
                        "message.deathdimension.spawn_set",
                        String.format("%.1f", x),
                        String.format("%.1f", y),
                        String.format("%.1f", z)
                ).withStyle(ChatFormatting.GREEN),
                true
        );
        return 1;
    }

    private static int setReturnMode(CommandSourceStack source, ReturnMode mode) {
        ModConfig.setReturnMode(mode);
        PacketDistributor.sendToAllPlayers(new ConfigSyncPayload(ModConfig.captureSnapshot(), false));
        source.sendSuccess(
                () -> Component.translatable(
                        "message.deathdimension.return_mode_set",
                        Component.translatable(mode.translationKey())
                ).withStyle(ChatFormatting.GREEN),
                true
        );
        return 1;
    }

    private static int showCooldown(CommandSourceStack source, ServerPlayer player) {
        PlayerDeathData data = player.getData(ModRegistries.PLAYER_DEATH_DATA.get());
        if (!PlayerTeleportHandler.isInDeathDimension(player) && !data.isInDeathDimension()) {
            source.sendFailure(Component.translatable("message.deathdimension.not_in_dimension"));
            return 0;
        }
        long remaining = CooldownManager.getRemainingSeconds(data);
        if (remaining > 0L) {
            source.sendSuccess(
                    () -> Component.translatable("message.deathdimension.cooldown_status", CooldownManager.formatTime(remaining))
                            .withStyle(ChatFormatting.GOLD),
                    false
            );
        } else {
            source.sendSuccess(
                    () -> Component.translatable("message.deathdimension.cooldown_ready").withStyle(ChatFormatting.GREEN),
                    false
            );
        }
        return 1;
    }

    private static int setCooldown(CommandSourceStack source, ServerPlayer target, long seconds) {
        PlayerDeathData data = target.getData(ModRegistries.PLAYER_DEATH_DATA.get());
        CooldownManager.setCooldown(data, seconds);
        source.sendSuccess(
                () -> Component.translatable(
                        "message.deathdimension.cooldown_set",
                        target.getDisplayName(),
                        CooldownManager.formatTime(seconds)
                ).withStyle(ChatFormatting.GREEN),
                true
        );
        return 1;
    }

    private static int resetCooldown(CommandSourceStack source, ServerPlayer target) {
        PlayerDeathData data = target.getData(ModRegistries.PLAYER_DEATH_DATA.get());
        CooldownManager.resetCooldown(data);
        source.sendSuccess(
                () -> Component.translatable("message.deathdimension.cooldown_reset", target.getDisplayName())
                        .withStyle(ChatFormatting.GREEN),
                true
        );
        return 1;
    }

    private static int bypassCooldown(CommandSourceStack source, ServerPlayer target) {
        if (!ModConfig.allowAdminBypass) {
            source.sendFailure(Component.translatable("message.deathdimension.admin_bypass_disabled"));
            return 0;
        }
        PlayerDeathData data = target.getData(ModRegistries.PLAYER_DEATH_DATA.get());
        CooldownManager.resetCooldown(data);
        source.sendSuccess(
                () -> Component.translatable("message.deathdimension.cooldown_bypassed", target.getDisplayName())
                        .withStyle(ChatFormatting.GREEN),
                true
        );
        target.sendSystemMessage(Component.translatable("message.deathdimension.cooldown_ready").withStyle(ChatFormatting.GREEN));
        return 1;
    }
}
