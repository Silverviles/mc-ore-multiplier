package com.silverviles.ore_multiplier.config;

/**
 * Height information for the dimension a placed feature belongs to.
 *
 * @param id       level stem identifier, for example {@code minecraft:overworld}
 * @param minY     lowest buildable Y
 * @param height   total build height
 * @param seaLevel sea level reported by the chunk generator
 */
public record DimensionInfo(String id, int minY, int height, int seaLevel) {
    public int maxY() {
        return minY + height - 1;
    }
}
