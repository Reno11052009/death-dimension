package com.reno.deathdimension.registry;

import com.mojang.serialization.MapCodec;
import com.reno.deathdimension.DeathDimension;
import com.reno.deathdimension.dimension.VoidChunkGenerator;
import com.reno.deathdimension.player.PlayerDeathData;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public final class ModRegistries {
    public static final ResourceLocation DEATH_DIMENSION_ID =
            ResourceLocation.fromNamespaceAndPath(DeathDimension.MODID, "death_dimension");

    public static final ResourceKey<Level> DEATH_DIMENSION =
            ResourceKey.create(Registries.DIMENSION, DEATH_DIMENSION_ID);

    public static final ResourceKey<DimensionType> DEATH_DIMENSION_TYPE =
            ResourceKey.create(Registries.DIMENSION_TYPE, DEATH_DIMENSION_ID);

    public static final ResourceKey<Biome> DEATH_VOID_BIOME =
            ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(DeathDimension.MODID, "death_void"));

    public static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, DeathDimension.MODID);

    public static final DeferredHolder<MapCodec<? extends ChunkGenerator>, MapCodec<VoidChunkGenerator>> VOID_CHUNK_GENERATOR =
            CHUNK_GENERATORS.register("void", () -> VoidChunkGenerator.CODEC);

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, DeathDimension.MODID);

    public static final Supplier<AttachmentType<PlayerDeathData>> PLAYER_DEATH_DATA =
            ATTACHMENTS.register("player_death_data",
                    () -> AttachmentType.serializable(PlayerDeathData::new).copyOnDeath().build());

    private ModRegistries() {}

    public static void register(IEventBus modEventBus) {
        CHUNK_GENERATORS.register(modEventBus);
        ATTACHMENTS.register(modEventBus);
    }
}
