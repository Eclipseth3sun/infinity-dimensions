package com.jrock.infinity;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Client-side initialiser.
 *
 * <p>Portal appearance is handled entirely through block state variants (color=0–8)
 * and per-color texture models — no runtime tinting needed.
 */
@Environment(EnvType.CLIENT)
public class InfinityDimensionsClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Nothing to register on the client side currently.
        // Portal colors are driven by block state variants; see blockstates/infinity_portal.json.
    }
}
