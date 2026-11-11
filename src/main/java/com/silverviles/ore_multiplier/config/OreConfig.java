package com.silverviles.ore_multiplier.config;

import java.util.Map;
import java.util.TreeMap;

/**
 * Root of the global ore configuration file.
 */
public final class OreConfig {
    public static final int CURRENT_VERSION = 1;

    public int configVersion = CURRENT_VERSION;
    public boolean enabled = true;
    public double countMultiplier = 1.0D;
    public double sizeMultiplier = 1.0D;
    public Map<String, OreGroup> ores = new TreeMap<>();
}
