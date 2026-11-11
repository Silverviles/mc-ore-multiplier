package com.silverviles.ore_multiplier.config;

import java.util.Map;
import java.util.TreeMap;

/**
 * A logical ore type (for example {@code minecraft:coal}) containing one or
 * more placed-feature variants (upper, lower, buried, nether, ...).
 */
public final class OreGroup {
    public Map<String, VariantConfig> variants = new TreeMap<>();
}
