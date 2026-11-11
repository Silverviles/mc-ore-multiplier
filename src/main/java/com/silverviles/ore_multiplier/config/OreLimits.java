package com.silverviles.ore_multiplier.config;

/**
 * Hard limits for values that feed into world generation.
 *
 * <p>The size limit is a safety limit, not a cosmetic one. Minecraft's
 * {@code FEATURES} chunk step only allows a feature to write inside a
 * one-chunk radius. A vanilla {@code OreFeature} with size {@code 81} can
 * reach 17 blocks away from its placement origin, which is far enough to touch
 * a chunk outside that radius and make {@code WorldGenRegion} log
 * "Detected setBlock in a far chunk". Size {@code 80} is the largest value
 * whose generated bounding box always stays inside the write radius.</p>
 */
public final class OreLimits {
    public static final int MIN_COUNT = -1;
    public static final int MAX_COUNT = 100_000;

    public static final int MIN_SIZE = 1;
    public static final int MAX_SIZE = 80;

    public static final double MIN_MULTIPLIER = 0.0D;
    public static final double MAX_MULTIPLIER = 100.0D;

    private OreLimits() {
    }

    public static int clampCount(int count) {
        return clamp(count, MIN_COUNT, MAX_COUNT);
    }

    public static int clampSize(int size) {
        return clamp(size, MIN_SIZE, MAX_SIZE);
    }

    public static double clampMultiplier(double multiplier) {
        return clamp(multiplier, MIN_MULTIPLIER, MAX_MULTIPLIER);
    }

    public static int effectiveSize(int size, double sizeMultiplier) {
        return clampSize((int) Math.round(size * sizeMultiplier));
    }

    public static int effectiveCount(int count, double countMultiplier) {
        return clamp((int) Math.round(count * countMultiplier), 0, MAX_COUNT);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
