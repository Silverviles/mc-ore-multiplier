package com.silverviles.ore_multiplier.mixin;

import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.heightproviders.TrapezoidHeight;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TrapezoidHeight.class)
public interface TrapezoidHeightAccessor {
    @Accessor("minInclusive")
    VerticalAnchor getMinInclusive();

    @Accessor("maxInclusive")
    VerticalAnchor getMaxInclusive();

    @Accessor("plateau")
    int getPlateau();
}
