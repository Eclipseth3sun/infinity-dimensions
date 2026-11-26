package com.jrock.infinity;

import com.jrock.infinity.block.ModBlocks;
import com.jrock.infinity.block.PortalBreakHandler;
import com.jrock.infinity.dimension.InfinityDimensionManager;
import com.jrock.infinity.item.ModItems;
import com.jrock.infinity.worldgen.InfinityBiomeSource;
import com.jrock.infinity.worldgen.InfinityChunkGenerator;
import com.jrock.infinity.worldgen.NamedChunkGenerator;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EndlessPortals implements ModInitializer {

    public static final String MOD_ID = "endless_portals";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ModBlocks.register();
        ModItems.register();

        // Breaking any Endless Portal block (or its frame) removes the whole portal at once.
        PortalBreakHandler.register();

        InfinityBiomeSource.register();
        InfinityChunkGenerator.register();
        NamedChunkGenerator.register();

        ServerLifecycleEvents.SERVER_STARTED.register(InfinityDimensionManager::onServerStart);
        ServerLifecycleEvents.SERVER_STOPPING.register(InfinityDimensionManager::onServerStop);

        // Drain pending dimension-creation queue after tickChildren() finishes each tick.
        // Using END_SERVER_TICK rather than server.execute() because execute() can run
        // synchronously on the server thread, mutating the levels map mid-iteration → CME.
        ServerTickEvents.END_SERVER_TICK.register(srv -> InfinityDimensionManager.processPendingCreations());

        LOGGER.info("Endless Portals loaded — throw a book into a Nether Portal.");
    }
}
