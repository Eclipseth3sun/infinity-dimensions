package com.jrock.infinity.mixin;

import com.jrock.infinity.InfinityDimensions;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The server's WeatherData (rainTime/thunderTime/raining/thundering) is shared by every
 * ServerLevel — only the overworld is meant to drive it. Because our procedural and named
 * dimensions are derived with the overworld dimension type (for sky light / day-night),
 * each one independently calls advanceWeatherCycle() on its own tick, all mutating the same
 * shared state. With several Infinity dimensions loaded at once this races the random
 * rain/thunder rolls dozens of times per tick, producing the rapid on/off flicker. Only the
 * vanilla overworld should advance the cycle; Infinity dimensions just observe its result.
 */
@Mixin(ServerLevel.class)
public abstract class WeatherCycleMixin {

    @Inject(method = "advanceWeatherCycle", at = @At("HEAD"), cancellable = true)
    private void infinityDimensions$skipRedundantWeatherCycle(CallbackInfo ci) {
        ServerLevel self = (ServerLevel) (Object) this;
        if (self.dimension().identifier().getNamespace().equals(InfinityDimensions.MOD_ID)) {
            ci.cancel();
        }
    }
}
