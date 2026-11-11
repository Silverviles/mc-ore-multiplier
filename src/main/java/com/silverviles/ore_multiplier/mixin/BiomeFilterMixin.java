package com.silverviles.ore_multiplier.mixin;

import com.silverviles.ore_multiplier.gen.OreOverrides;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.placement.BiomeFilter;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@link BiomeFilter} checks whether the feature being placed is present in the
 * biome's feature list. Our generated replacements are structurally different
 * from the registry features, so they would always fail that check. Resolve a
 * replacement back to its original before the check runs.
 */
@Mixin(BiomeFilter.class)
public class BiomeFilterMixin {
    @Inject(method = "shouldPlace", at = @At("HEAD"), cancellable = true)
    @SuppressWarnings("deprecation")
    private void oreMultiplier$checkOriginalFeature(
            PlacementContext context,
            RandomSource random,
            BlockPos pos,
            CallbackInfoReturnable<Boolean> callback
    ) {
        PlacedFeature replacement = context.topFeature().orElse(null);
        if (replacement == null) {
            return;
        }

        PlacedFeature original = OreOverrides.originalOf(replacement);
        if (original == null) {
            return;
        }

        Holder<Biome> biome = context.getLevel().getBiome(pos);
        callback.setReturnValue(
                context.generator().getBiomeGenerationSettings(biome).hasFeature(original)
        );
    }
}
