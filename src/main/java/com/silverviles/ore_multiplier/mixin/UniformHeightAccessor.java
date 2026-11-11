package com.silverviles.ore_multiplier.mixin;

import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.heightproviders.UniformHeight;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(UniformHeight.class)
public interface UniformHeightAccessor {
    @Accessor("minInclusive")
    VerticalAnchor getMinInclusive();

    @Accessor("maxInclusive")
    VerticalAnchor getMaxInclusive();
}
