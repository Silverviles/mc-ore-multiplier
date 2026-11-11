package com.silverviles.ore_multiplier.mixin;

import com.silverviles.ore_multiplier.gen.OreOverrides;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Swaps an ore placed feature for its configured replacement immediately before
 * its placement modifiers run. The replacement is a normal {@link PlacedFeature}
 * with a rebuilt configured feature and modified placement list, so the rest of
 * the vanilla generation path is untouched.
 */
@Mixin(PlacedFeature.class)
public class PlacedFeatureMixin {
    @Inject(method = "placeWithBiomeCheck", at = @At("HEAD"), cancellable = true)
    private void oreMultiplier$replaceWithBiomeCheck(
            WorldGenLevel level,
            ChunkGenerator generator,
            RandomSource random,
            BlockPos origin,
            CallbackInfoReturnable<Boolean> callback
    ) {
        PlacedFeature replacement = OreOverrides.effective((PlacedFeature) (Object) this);
        if (replacement != null) {
            callback.setReturnValue(replacement.placeWithBiomeCheck(level, generator, random, origin));
        }
    }

    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void oreMultiplier$replace(
            WorldGenLevel level,
            ChunkGenerator generator,
            RandomSource random,
            BlockPos origin,
            CallbackInfoReturnable<Boolean> callback
    ) {
        PlacedFeature replacement = OreOverrides.effective((PlacedFeature) (Object) this);
        if (replacement != null) {
            callback.setReturnValue(replacement.place(level, generator, random, origin));
        }
    }
}
