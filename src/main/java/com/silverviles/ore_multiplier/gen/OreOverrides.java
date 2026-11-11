package com.silverviles.ore_multiplier.gen;

import com.silverviles.ore_multiplier.config.DiscoveredOre;
import com.silverviles.ore_multiplier.config.DimensionInfo;
import com.silverviles.ore_multiplier.config.OreConfig;
import com.silverviles.ore_multiplier.config.OreGroup;
import com.silverviles.ore_multiplier.config.VariantConfig;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.feature.AbstractOreFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.OreFeature;
import net.minecraft.world.level.levelgen.feature.ScatteredOreFeature;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.heightproviders.TrapezoidHeight;
import net.minecraft.world.level.levelgen.heightproviders.UniformHeight;
import net.minecraft.world.level.levelgen.placement.BiomeFilter;
import net.minecraft.world.level.levelgen.placement.CountPlacement;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Holds the immutable set of effective placed features used by the chunk
 * generator. The whole snapshot is swapped atomically when the config changes.
 */
public final class OreOverrides {
    /**
     * Forward and reverse lookups are kept together so they always describe the
     * same set of replacements, even when chunks generate while the config is
     * reloaded.
     */
    private record Snapshot(
            Map<PlacedFeature, PlacedFeature> forward,
            Map<PlacedFeature, PlacedFeature> reverse
    ) {
        static final Snapshot EMPTY = new Snapshot(Map.of(), Map.of());
    }

    private static volatile Snapshot current = Snapshot.EMPTY;

    private OreOverrides() {
    }

    public static void install(Map<PlacedFeature, PlacedFeature> replacements) {
        IdentityHashMap<PlacedFeature, PlacedFeature> reverse = new IdentityHashMap<>(replacements.size());
        replacements.forEach((original, replacement) -> reverse.put(replacement, original));
        current = new Snapshot(replacements, Collections.unmodifiableMap(reverse));
    }

    public static void clear() {
        current = Snapshot.EMPTY;
    }

    /**
     * @return the replacement placed feature, or {@code null} when the given
     * feature is not overridden
     */
    public static PlacedFeature effective(PlacedFeature original) {
        return current.forward().get(original);
    }

    /**
     * @return the registry feature a replacement was built from, or
     * {@code null} when the given feature is not a replacement
     */
    public static PlacedFeature originalOf(PlacedFeature replacement) {
        return current.reverse().get(replacement);
    }

    public static Map<PlacedFeature, PlacedFeature> build(
            MinecraftServer server,
            OreConfig config,
            Map<String, DiscoveredOre> discovery
    ) {
        if (!config.enabled) {
            return Map.of();
        }

        Registry<PlacedFeature> registry = server.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE);
        IdentityHashMap<PlacedFeature, PlacedFeature> replacements = new IdentityHashMap<>();

        for (OreGroup group : config.ores.values()) {
            for (Map.Entry<String, VariantConfig> entry : group.variants.entrySet()) {
                Identifier identifier = Identifier.tryParse(entry.getKey());
                if (identifier == null) {
                    continue;
                }

                Optional<Holder.Reference<PlacedFeature>> holder = registry.get(identifier);
                if (holder.isEmpty()) {
                    continue;
                }

                PlacedFeature original = holder.get().value();
                DiscoveredOre discovered = discovery.get(entry.getKey());
                DimensionInfo dimension = discovered != null ? discovered.dimension() : null;
                PlacedFeature replacement = buildEffective(original, entry.getValue(), config, dimension);
                if (replacement != null) {
                    replacements.put(original, replacement);
                }
            }
        }

        return Collections.unmodifiableMap(replacements);
    }

    private static PlacedFeature buildEffective(
            PlacedFeature original,
            VariantConfig variant,
            OreConfig config,
            DimensionInfo dimension
    ) {
        Feature feature = original.feature().value();
        if (!(feature instanceof AbstractOreFeature ore)) {
            return null;
        }

        int effectiveSize = clamp(
                (int) Math.round(variant.size * config.sizeMultiplier),
                1,
                256
        );

        Holder<Feature> replacementHolder = original.feature();
        if (effectiveSize != ore.size()) {
            AbstractOreFeature rebuilt = rebuildOre(ore, effectiveSize);
            if (rebuilt != null) {
                replacementHolder = Holder.direct(rebuilt);
            }
        }

        int effectiveCount;
        if (!variant.enabled) {
            effectiveCount = 0;
        } else if (variant.count < 0) {
            effectiveCount = -1;
        } else {
            effectiveCount = clamp(
                    (int) Math.round(variant.count * config.countMultiplier),
                    0,
                    100_000
            );
        }

        int minY = variant.minY;
        int maxY = variant.maxY;
        if (dimension != null) {
            minY = clamp(minY, dimension.minY(), dimension.maxY());
            maxY = clamp(maxY, dimension.minY(), dimension.maxY());
        }
        if (minY > maxY) {
            int swap = minY;
            minY = maxY;
            maxY = swap;
        }

        HeightProvider heightProvider = buildHeightProvider(variant, minY, maxY);
        List<PlacementModifier> placement = new ArrayList<>(original.placement());

        int countIndex = -1;
        int heightIndex = -1;
        for (int index = 0; index < placement.size(); index++) {
            PlacementModifier modifier = placement.get(index);
            if (countIndex < 0 && modifier instanceof CountPlacement) {
                countIndex = index;
            }
            if (heightIndex < 0 && modifier instanceof HeightRangePlacement) {
                heightIndex = index;
            }
        }

        if (countIndex >= 0) {
            if (effectiveCount >= 0) {
                placement.set(countIndex, CountPlacement.of(effectiveCount));
            }
        } else if (effectiveCount >= 0) {
            placement.add(0, CountPlacement.of(effectiveCount));
        }

        if (heightIndex >= 0) {
            placement.set(heightIndex, HeightRangePlacement.of(heightProvider));
        } else {
            int insertionIndex = placement.size();
            for (int index = 0; index < placement.size(); index++) {
                if (placement.get(index) instanceof BiomeFilter) {
                    insertionIndex = index;
                    break;
                }
            }
            placement.add(insertionIndex, HeightRangePlacement.of(heightProvider));
        }

        return new PlacedFeature(replacementHolder, List.copyOf(placement));
    }

    private static HeightProvider buildHeightProvider(VariantConfig variant, int minY, int maxY) {
        VerticalAnchor min = VerticalAnchor.absolute(minY);
        VerticalAnchor max = VerticalAnchor.absolute(maxY);

        return switch (variant.distribution) {
            case "triangle" -> TrapezoidHeight.of(min, max, 0);
            case "trapezoid" -> TrapezoidHeight.of(
                    min,
                    max,
                    clamp(variant.plateau, 0, maxY - minY)
            );
            default -> UniformHeight.of(min, max);
        };
    }

    private static AbstractOreFeature rebuildOre(AbstractOreFeature ore, int size) {
        if (ore instanceof ScatteredOreFeature) {
            return new ScatteredOreFeature(ore.targetStates(), size, ore.discardChanceOnAirExposure());
        }
        if (ore instanceof OreFeature) {
            return new OreFeature(ore.targetStates(), size, ore.discardChanceOnAirExposure());
        }
        // Unknown third-party subclass; leave the feature instance untouched.
        return null;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
