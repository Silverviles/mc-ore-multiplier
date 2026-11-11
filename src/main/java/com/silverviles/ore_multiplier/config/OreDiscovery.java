package com.silverviles.ore_multiplier.config;

import com.silverviles.ore_multiplier.mixin.CountPlacementAccessor;
import com.silverviles.ore_multiplier.mixin.HeightRangePlacementAccessor;
import com.silverviles.ore_multiplier.mixin.TrapezoidHeightAccessor;
import com.silverviles.ore_multiplier.mixin.UniformHeightAccessor;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.heightproviders.TrapezoidHeight;
import net.minecraft.world.level.levelgen.heightproviders.UniformHeight;
import net.minecraft.world.level.levelgen.placement.CountPlacement;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Scans the live worldgen registries and turns every placed ore feature into a
 * human-friendly default entry.
 */
public final class OreDiscovery {
    private static final Logger LOGGER = LoggerFactory.getLogger("ore_multiplier");

    /**
     * Suffixes that describe a variant of the same ore. They are stripped when
     * deriving the logical group name (coal_upper -> coal).
     */
    private static final List<String> VARIANT_SUFFIXES = List.of(
            "_upper", "_lower", "_small", "_middle", "_large", "_medium",
            "_buried", "_nether", "_deltas", "_extra"
    );

    private OreDiscovery() {
    }

    public static Map<String, DiscoveredOre> discover(MinecraftServer server) {
        RegistryAccess access = server.registryAccess();
        Registry<PlacedFeature> placedRegistry = access.lookupOrThrow(Registries.PLACED_FEATURE);

        Map<ResourceKey<PlacedFeature>, DimensionInfo> dimensionByFeature = new HashMap<>();
        DimensionInfo overworld = null;

        try {
            Registry<LevelStem> levelStems = access.lookupOrThrow(Registries.LEVEL_STEM);
            for (Map.Entry<ResourceKey<LevelStem>, LevelStem> entry : levelStems.entrySet()) {
                LevelStem stem = entry.getValue();
                DimensionType type = stem.type().value();
                int minY = Math.max(type.minY(), stem.generator().getMinY());
                int height = Math.min(type.height(), stem.generator().getGenDepth());
                DimensionInfo info = new DimensionInfo(
                        entry.getKey().identifier().toString(),
                        minY,
                        height,
                        stem.generator().getSeaLevel()
                );

                if (info.id().equals("minecraft:overworld")) {
                    overworld = info;
                }

                for (Holder<Biome> biome : stem.generator().getBiomeSource().possibleBiomes()) {
                    for (HolderSet<PlacedFeature> featureSet : stem.generator().getBiomeGenerationSettings(biome).features()) {
                        for (Holder<PlacedFeature> holder : featureSet) {
                            holder.unwrapKey().ifPresent(key -> dimensionByFeature.putIfAbsent(key, info));
                        }
                    }
                }
            }
        } catch (Exception exception) {
            LOGGER.warn("Could not inspect level stems while discovering ores", exception);
        }

        DimensionInfo fallback = overworld != null
                ? overworld
                : new DimensionInfo("minecraft:overworld", -64, 384, 63);

        Map<String, DiscoveredOre> result = new LinkedHashMap<>();
        for (Map.Entry<ResourceKey<PlacedFeature>, PlacedFeature> entry : placedRegistry.entrySet()) {
            PlacedFeature placed = entry.getValue();
            ConfiguredFeature<?, ?> configured = placed.feature().value();
            if (!(configured.config() instanceof OreConfiguration ore)) {
                continue;
            }

            String variantId = entry.getKey().identifier().toString();
            String groupKey = groupKey(variantId);
            String variantSuffix = variantSuffix(variantId, groupKey);
            DimensionInfo dimension = dimensionByFeature.getOrDefault(entry.getKey(), fallback);

            int count = -1;
            HeightProvider height = null;
            for (PlacementModifier modifier : placed.placement()) {
                if (count < 0 && modifier instanceof CountPlacement countPlacement) {
                    CountPlacementAccessor accessor = (CountPlacementAccessor) (Object) countPlacement;
                    count = accessor.getCount().maxInclusive();
                }
                if (height == null && modifier instanceof HeightRangePlacement heightPlacement) {
                    HeightRangePlacementAccessor accessor = (HeightRangePlacementAccessor) (Object) heightPlacement;
                    height = accessor.getHeight();
                }
            }

            int minY = dimension.minY();
            int maxY = dimension.maxY();
            String distribution = "uniform";
            int plateau = 0;

            if (height instanceof UniformHeight uniform) {
                UniformHeightAccessor accessor = (UniformHeightAccessor) (Object) uniform;
                minY = resolveAnchor(accessor.getMinInclusive(), dimension);
                maxY = resolveAnchor(accessor.getMaxInclusive(), dimension);
            } else if (height instanceof TrapezoidHeight trapezoid) {
                TrapezoidHeightAccessor accessor = (TrapezoidHeightAccessor) (Object) trapezoid;
                minY = resolveAnchor(accessor.getMinInclusive(), dimension);
                maxY = resolveAnchor(accessor.getMaxInclusive(), dimension);
                plateau = accessor.getPlateau();
                distribution = plateau > 0 ? "trapezoid" : "triangle";
            }

            minY = Math.clamp(minY, dimension.minY(), dimension.maxY());
            maxY = Math.clamp(maxY, dimension.minY(), dimension.maxY());
            plateau = Math.clamp(plateau, 0, maxY - minY);

            result.put(variantId, new DiscoveredOre(
                    groupKey,
                    variantId,
                    variantSuffix,
                    dimension,
                    count,
                    ore.size,
                    minY,
                    maxY,
                    distribution,
                    plateau
            ));
        }

        return result;
    }

    public static String groupKey(String variantId) {
        Identifier identifier = Identifier.parse(variantId);
        String path = identifier.getPath();

        if (path.startsWith("ore_")) {
            path = path.substring("ore_".length());
        }

        for (String suffix : VARIANT_SUFFIXES) {
            if (path.length() > suffix.length() && path.endsWith(suffix)) {
                path = path.substring(0, path.length() - suffix.length());
                break;
            }
        }

        // "ore_debris_small" and "ore_ancient_debris_large" both belong together.
        if (path.equals("debris")) {
            path = "ancient_debris";
        }

        return identifier.getNamespace() + ":" + path;
    }

    public static String variantSuffix(String variantId, String groupKey) {
        Identifier identifier = Identifier.parse(variantId);
        String path = identifier.getPath();
        String groupPath = groupKey.substring(groupKey.indexOf(':') + 1);
        String stripped = path.startsWith("ore_") ? path.substring("ore_".length()) : path;

        if (stripped.equals(groupPath)) {
            return "main";
        }
        if (stripped.startsWith(groupPath + "_")) {
            return stripped.substring(groupPath.length() + 1);
        }
        if (groupPath.equals("ancient_debris") && stripped.startsWith("debris_")) {
            return stripped.substring("debris_".length());
        }
        for (String suffix : VARIANT_SUFFIXES) {
            if (stripped.endsWith(suffix)) {
                return suffix.substring(1);
            }
        }
        return stripped;
    }

    public static String displayName(String key) {
        String path = key.contains(":") ? key.substring(key.indexOf(':') + 1) : key;
        String[] parts = path.split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return builder.isEmpty() ? key : builder.toString();
    }

    private static int resolveAnchor(VerticalAnchor anchor, DimensionInfo dimension) {
        if (anchor instanceof VerticalAnchor.Absolute(int y)) {
            return y;
        }
        if (anchor instanceof VerticalAnchor.AboveBottom(int offset)) {
            return dimension.minY() + offset;
        }
        if (anchor instanceof VerticalAnchor.BelowTop(int offset)) {
            return dimension.minY() + dimension.height() - 1 - offset;
        }
        return dimension.minY();
    }

    public static List<String> variantSuffixes() {
        List<String> suffixes = new ArrayList<>(VARIANT_SUFFIXES.size());
        for (String suffix : VARIANT_SUFFIXES) {
            suffixes.add(suffix.substring(1));
        }
        return suffixes;
    }
}
