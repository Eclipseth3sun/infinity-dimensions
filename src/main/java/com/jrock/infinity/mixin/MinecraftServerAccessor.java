package com.jrock.infinity.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.WorldData;
import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Exposes private MinecraftServer fields needed for runtime dimension creation.
 * Field names verified against MC 26.1.2 decompiled source.
 */
@Mixin(MinecraftServer.class)
public interface MinecraftServerAccessor {

    @Accessor("levels")
    Map<ResourceKey<Level>, ServerLevel> getLevels();

    @Accessor("storageSource")
    LevelStorageSource.LevelStorageAccess getStorageSource();

    @Accessor("worldData")
    WorldData getWorldData();

    @Accessor("executor")
    Executor getWorkerExecutor();
}
