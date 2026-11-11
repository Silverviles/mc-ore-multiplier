package com.silverviles.ore_multiplier.config;

/**
 * Settings for one placed feature (one ore variant).
 *
 * <p>{@code count == -1} means the placed feature has no count modifier at all
 * and the mod should not add one. {@code enabled == false} is implemented as a
 * count of zero, which suppresses generation without changing anything else.</p>
 */
public final class VariantConfig {
    public boolean enabled = true;
    public int count = -1;
    public int size = 1;
    public int minY = 0;
    public int maxY = 0;
    public String distribution = "uniform";
    public int plateau = 0;

    public VariantConfig copy() {
        VariantConfig copy = new VariantConfig();
        copy.enabled = enabled;
        copy.count = count;
        copy.size = size;
        copy.minY = minY;
        copy.maxY = maxY;
        copy.distribution = distribution;
        copy.plateau = plateau;
        return copy;
    }
}
