package com.silverviles.ore_multiplier.mixin;

import com.silverviles.ore_multiplier.gen.OreOverrides;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.FeaturePlacer;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Swaps an ore placed feature for its configured replacement immediately before
 * the placement modifiers run. The replacement is a normal {@link PlacedFeature}
 * with a rebuilt feature holder and modified placement list, so the rest of the
 * vanilla generation path is untouched.
 */
@Mixin(FeaturePlacer.class)
public class FeaturePlacerMixin {
    @ModifyVariable(
            method = "place(Lnet/minecraft/world/level/levelgen/placement/PlacedFeature;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;Z)Z",
            at = @At("HEAD"),
            argsOnly = true,
            index = 1
    )
    private PlacedFeature oreMultiplier$replacePlacedFeature(PlacedFeature original) {
        PlacedFeature replacement = OreOverrides.effective(original);
        return replacement != null ? replacement : original;
    }
}
