package com.silverviles.ore_multiplier.config;

/**
 * Vanilla defaults discovered for one placed feature.
 */
public record DiscoveredOre(
        String groupKey,
        String variantId,
        String variantSuffix,
        DimensionInfo dimension,
        int count,
        int size,
        int minY,
        int maxY,
        String distribution,
        int plateau
) {
    public VariantConfig defaults() {
        VariantConfig config = new VariantConfig();
        config.enabled = true;
        config.count = count;
        config.size = size;
        config.minY = minY;
        config.maxY = maxY;
        config.distribution = distribution;
        config.plateau = plateau;
        return config;
    }
}
