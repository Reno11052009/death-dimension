package com.reno.deathdimension.player;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.util.INBTSerializable;

public class PlayerDeathData implements INBTSerializable<CompoundTag> {
    private boolean inDeathDimension;
    private boolean pendingDeathTeleport;
    private boolean autoReturnEligible;
    private transient boolean internalTeleport;

    private boolean hasOriginPosition;
    private String originDimension = Level.OVERWORLD.location().toString();
    private double originX;
    private double originY;
    private double originZ;
    private float originYRot;
    private float originXRot;

    private double deathX;
    private double deathY;
    private double deathZ;

    private boolean hasDeathDimPosition;
    private double deathDimX;
    private double deathDimY;
    private double deathDimZ;
    private float deathDimYRot;
    private float deathDimXRot;
    private long lastLogoutEpoch;

    private long cooldownStart;
    private long cooldownDuration;
    private boolean cooldownCompleted = true;
    private boolean cooldownNotified = true;

    private boolean hasSavedAbilities;
    private boolean savedMayFly;
    private boolean savedFlying;
    private boolean savedInvulnerable;

    private boolean hasSavedInventory;
    private ListTag savedInventory = new ListTag();

    private boolean hasSavedExperience;
    private int savedExpLevel;
    private int savedTotalExp;
    private float savedExpProgress;

    public boolean isInDeathDimension() {
        return inDeathDimension;
    }

    public void setInDeathDimension(boolean inDeathDimension) {
        this.inDeathDimension = inDeathDimension;
    }

    public boolean isPendingDeathTeleport() {
        return pendingDeathTeleport;
    }

    public void setPendingDeathTeleport(boolean pendingDeathTeleport) {
        this.pendingDeathTeleport = pendingDeathTeleport;
    }

    public boolean isAutoReturnEligible() {
        return autoReturnEligible;
    }

    public void setAutoReturnEligible(boolean autoReturnEligible) {
        this.autoReturnEligible = autoReturnEligible;
    }

    public boolean isInternalTeleport() {
        return internalTeleport;
    }

    public void setInternalTeleport(boolean internalTeleport) {
        this.internalTeleport = internalTeleport;
    }

    public boolean hasOriginPosition() {
        return hasOriginPosition;
    }

    public String getOriginDimension() {
        return originDimension;
    }

    public double getOriginX() {
        return originX;
    }

    public double getOriginY() {
        return originY;
    }

    public double getOriginZ() {
        return originZ;
    }

    public float getOriginYRot() {
        return originYRot;
    }

    public float getOriginXRot() {
        return originXRot;
    }

    public double getDeathX() {
        return deathX;
    }

    public double getDeathY() {
        return deathY;
    }

    public double getDeathZ() {
        return deathZ;
    }

    public boolean hasDeathDimPosition() {
        return hasDeathDimPosition;
    }

    public double getDeathDimX() {
        return deathDimX;
    }

    public double getDeathDimY() {
        return deathDimY;
    }

    public double getDeathDimZ() {
        return deathDimZ;
    }

    public float getDeathDimYRot() {
        return deathDimYRot;
    }

    public float getDeathDimXRot() {
        return deathDimXRot;
    }

    public long getLastLogoutEpoch() {
        return lastLogoutEpoch;
    }

    public void setLastLogoutEpoch(long lastLogoutEpoch) {
        this.lastLogoutEpoch = lastLogoutEpoch;
    }

    public void recordDeathDimPosition(double x, double y, double z, float yRot, float xRot) {
        this.hasDeathDimPosition = true;
        this.deathDimX = x;
        this.deathDimY = y;
        this.deathDimZ = z;
        this.deathDimYRot = yRot;
        this.deathDimXRot = xRot;
    }

    public void clearDeathDimPosition() {
        this.hasDeathDimPosition = false;
        this.deathDimX = 0.0;
        this.deathDimY = 0.0;
        this.deathDimZ = 0.0;
        this.deathDimYRot = 0.0f;
        this.deathDimXRot = 0.0f;
    }

    public void recordOrigin(ResourceKey<Level> dimension, double x, double y, double z, float yRot, float xRot) {
        this.hasOriginPosition = true;
        this.originDimension = dimension.location().toString();
        this.originX = x;
        this.originY = y;
        this.originZ = z;
        this.originYRot = yRot;
        this.originXRot = xRot;
        this.deathX = x;
        this.deathY = y;
        this.deathZ = z;
    }

    public long getCooldownStart() {
        return cooldownStart;
    }

    public void setCooldownStart(long cooldownStart) {
        this.cooldownStart = cooldownStart;
    }

    public long getCooldownDuration() {
        return cooldownDuration;
    }

    public void setCooldownDuration(long cooldownDuration) {
        this.cooldownDuration = cooldownDuration;
    }

    public boolean isCooldownCompleted() {
        return cooldownCompleted;
    }

    public void setCooldownCompleted(boolean cooldownCompleted) {
        this.cooldownCompleted = cooldownCompleted;
    }

    public boolean isCooldownNotified() {
        return cooldownNotified;
    }

    public void setCooldownNotified(boolean cooldownNotified) {
        this.cooldownNotified = cooldownNotified;
    }

    public boolean hasSavedAbilities() {
        return hasSavedAbilities;
    }

    public boolean isSavedMayFly() {
        return savedMayFly;
    }

    public boolean isSavedFlying() {
        return savedFlying;
    }

    public boolean isSavedInvulnerable() {
        return savedInvulnerable;
    }

    public void saveAbilities(ServerPlayer player) {
        saveAbilities(player.mayFly(), player.getAbilities().flying, player.getAbilities().invulnerable);
    }

    public void saveAbilities(boolean mayFly, boolean flying, boolean invulnerable) {
        this.hasSavedAbilities = true;
        this.savedMayFly = mayFly;
        this.savedFlying = flying;
        this.savedInvulnerable = invulnerable;
    }

    public void clearSavedAbilities() {
        this.hasSavedAbilities = false;
        this.savedMayFly = false;
        this.savedFlying = false;
        this.savedInvulnerable = false;
    }

    public boolean hasSavedInventory() {
        return hasSavedInventory;
    }

    public int getSavedInventoryCount() {
        return savedInventory.size();
    }

    public void snapshotInventory(ServerPlayer player) {
        this.savedInventory = new ListTag();
        player.getInventory().save(this.savedInventory);
        this.hasSavedInventory = true;
    }

    public void restoreInventoryAndClear(ServerPlayer player) {
        if (!this.hasSavedInventory) {
            return;
        }
        player.getInventory().load(this.savedInventory);
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        player.inventoryMenu.sendAllDataToRemote();
        this.savedInventory = new ListTag();
        this.hasSavedInventory = false;
    }

    public static void clearLiveInventory(ServerPlayer player) {
        player.getInventory().clearContent();
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        player.inventoryMenu.sendAllDataToRemote();
    }

    public boolean hasSavedExperience() {
        return hasSavedExperience;
    }

    public int getSavedExpLevel() {
        return savedExpLevel;
    }

    public int getSavedTotalExp() {
        return savedTotalExp;
    }

    public float getSavedExpProgress() {
        return savedExpProgress;
    }

    public void snapshotExperience(ServerPlayer player) {
        this.savedExpLevel = player.experienceLevel;
        this.savedTotalExp = player.totalExperience;
        this.savedExpProgress = player.experienceProgress;
        this.hasSavedExperience = true;
    }

    public void restoreExperienceAndClear(ServerPlayer player) {
        if (!this.hasSavedExperience) {
            return;
        }
        player.setExperienceLevels(this.savedExpLevel);
        player.totalExperience = this.savedTotalExp;
        player.experienceProgress = this.savedExpProgress;
        if (player.connection != null) {
            player.connection.send(new ClientboundSetExperiencePacket(
                    player.experienceProgress,
                    player.totalExperience,
                    player.experienceLevel
            ));
        }
        this.hasSavedExperience = false;
    }

    public static void clearLiveExperience(ServerPlayer player) {
        player.setExperienceLevels(0);
        player.totalExperience = 0;
        player.experienceProgress = 0.0f;
        if (player.connection != null) {
            player.connection.send(new ClientboundSetExperiencePacket(0.0f, 0, 0));
        }
    }

    public void clearDeathState() {
        this.inDeathDimension = false;
        this.pendingDeathTeleport = false;
        this.autoReturnEligible = false;
        this.clearDeathDimPosition();
        this.lastLogoutEpoch = 0L;
        this.cooldownStart = 0L;
        this.cooldownDuration = 0L;
        this.cooldownCompleted = true;
        this.cooldownNotified = true;
        this.clearSavedAbilities();
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("InDeathDimension", inDeathDimension);
        tag.putBoolean("PendingDeathTeleport", pendingDeathTeleport);
        tag.putBoolean("AutoReturnEligible", autoReturnEligible);

        tag.putBoolean("HasOriginPosition", hasOriginPosition);
        tag.putString("OriginDimension", originDimension);
        tag.putDouble("OriginX", originX);
        tag.putDouble("OriginY", originY);
        tag.putDouble("OriginZ", originZ);
        tag.putFloat("OriginYRot", originYRot);
        tag.putFloat("OriginXRot", originXRot);

        tag.putDouble("DeathX", deathX);
        tag.putDouble("DeathY", deathY);
        tag.putDouble("DeathZ", deathZ);

        tag.putBoolean("HasDeathDimPosition", hasDeathDimPosition);
        if (hasDeathDimPosition) {
            tag.putDouble("DeathDimX", deathDimX);
            tag.putDouble("DeathDimY", deathDimY);
            tag.putDouble("DeathDimZ", deathDimZ);
            tag.putFloat("DeathDimYRot", deathDimYRot);
            tag.putFloat("DeathDimXRot", deathDimXRot);
        }
        tag.putLong("LastLogoutEpoch", lastLogoutEpoch);

        tag.putLong("CooldownStart", cooldownStart);
        tag.putLong("CooldownDuration", cooldownDuration);
        tag.putBoolean("CooldownCompleted", cooldownCompleted);
        tag.putBoolean("CooldownNotified", cooldownNotified);

        tag.putBoolean("HasSavedAbilities", hasSavedAbilities);
        tag.putBoolean("SavedMayFly", savedMayFly);
        tag.putBoolean("SavedFlying", savedFlying);
        tag.putBoolean("SavedInvulnerable", savedInvulnerable);

        tag.putBoolean("HasSavedInventory", hasSavedInventory);
        if (hasSavedInventory) {
            tag.put("SavedInventory", savedInventory.copy());
        }

        tag.putBoolean("HasSavedExperience", hasSavedExperience);
        if (hasSavedExperience) {
            tag.putInt("SavedExpLevel", savedExpLevel);
            tag.putInt("SavedTotalExp", savedTotalExp);
            tag.putFloat("SavedExpProgress", savedExpProgress);
        }

        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        this.inDeathDimension = tag.getBoolean("InDeathDimension");
        this.pendingDeathTeleport = tag.getBoolean("PendingDeathTeleport");
        this.autoReturnEligible = tag.getBoolean("AutoReturnEligible");

        this.hasOriginPosition = tag.getBoolean("HasOriginPosition");
        String dim = tag.getString("OriginDimension");
        this.originDimension = dim.isEmpty() ? Level.OVERWORLD.location().toString() : dim;
        this.originX = tag.getDouble("OriginX");
        this.originY = tag.getDouble("OriginY");
        this.originZ = tag.getDouble("OriginZ");
        this.originYRot = tag.getFloat("OriginYRot");
        this.originXRot = tag.getFloat("OriginXRot");

        this.deathX = tag.getDouble("DeathX");
        this.deathY = tag.getDouble("DeathY");
        this.deathZ = tag.getDouble("DeathZ");

        this.hasDeathDimPosition = tag.getBoolean("HasDeathDimPosition");
        if (this.hasDeathDimPosition) {
            this.deathDimX = tag.getDouble("DeathDimX");
            this.deathDimY = tag.getDouble("DeathDimY");
            this.deathDimZ = tag.getDouble("DeathDimZ");
            this.deathDimYRot = tag.getFloat("DeathDimYRot");
            this.deathDimXRot = tag.getFloat("DeathDimXRot");
        } else {
            this.clearDeathDimPosition();
        }
        this.lastLogoutEpoch = tag.getLong("LastLogoutEpoch");

        this.cooldownStart = tag.getLong("CooldownStart");
        this.cooldownDuration = tag.getLong("CooldownDuration");
        this.cooldownCompleted = tag.contains("CooldownCompleted") ? tag.getBoolean("CooldownCompleted") : true;
        this.cooldownNotified = tag.contains("CooldownNotified") ? tag.getBoolean("CooldownNotified") : true;

        this.hasSavedAbilities = tag.getBoolean("HasSavedAbilities");
        this.savedMayFly = tag.getBoolean("SavedMayFly");
        this.savedFlying = tag.getBoolean("SavedFlying");
        this.savedInvulnerable = tag.getBoolean("SavedInvulnerable");

        this.hasSavedInventory = tag.getBoolean("HasSavedInventory");
        if (this.hasSavedInventory && tag.contains("SavedInventory", Tag.TAG_LIST)) {
            this.savedInventory = tag.getList("SavedInventory", Tag.TAG_COMPOUND).copy();
        } else {
            this.savedInventory = new ListTag();
            this.hasSavedInventory = false;
        }

        this.hasSavedExperience = tag.getBoolean("HasSavedExperience");
        this.savedExpLevel = tag.getInt("SavedExpLevel");
        this.savedTotalExp = tag.getInt("SavedTotalExp");
        this.savedExpProgress = tag.getFloat("SavedExpProgress");
    }
}
