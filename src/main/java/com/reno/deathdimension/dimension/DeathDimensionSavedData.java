package com.reno.deathdimension.dimension;

import com.reno.deathdimension.DeathDimension;
import com.reno.deathdimension.config.ModConfig;
import com.reno.deathdimension.registry.ModRegistries;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

public class DeathDimensionSavedData extends SavedData {
    private static final String DATA_NAME = DeathDimension.MODID + "_dimension";

    private boolean hasCustomSpawn;
    private double spawnX;
    private double spawnY;
    private double spawnZ;

    public DeathDimensionSavedData() {
    }

    public static DeathDimensionSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
                new Factory<>(DeathDimensionSavedData::new, DeathDimensionSavedData::load),
                DATA_NAME
        );
    }

    @Nullable
    public static ServerLevel getDeathLevel(MinecraftServer server) {
        return server.getLevel(ModRegistries.DEATH_DIMENSION);
    }

    public static Vec3 getSpawnPosition(MinecraftServer server) {
        DeathDimensionSavedData data = get(server);
        if (data.hasCustomSpawn) {
            return new Vec3(data.spawnX, data.spawnY, data.spawnZ);
        }
        return new Vec3(ModConfig.spawnX, ModConfig.spawnY, ModConfig.spawnZ);
    }

    public void setCustomSpawn(double x, double y, double z) {
        this.hasCustomSpawn = true;
        this.spawnX = x;
        this.spawnY = y;
        this.spawnZ = z;
        this.setDirty();
    }

    private static DeathDimensionSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        DeathDimensionSavedData data = new DeathDimensionSavedData();
        data.hasCustomSpawn = tag.getBoolean("HasCustomSpawn");
        data.spawnX = tag.getDouble("SpawnX");
        data.spawnY = tag.getDouble("SpawnY");
        data.spawnZ = tag.getDouble("SpawnZ");
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("HasCustomSpawn", this.hasCustomSpawn);
        tag.putDouble("SpawnX", this.spawnX);
        tag.putDouble("SpawnY", this.spawnY);
        tag.putDouble("SpawnZ", this.spawnZ);
        return tag;
    }
}
