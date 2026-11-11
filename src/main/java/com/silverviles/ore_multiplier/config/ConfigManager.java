package com.silverviles.ore_multiplier.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.silverviles.ore_multiplier.gen.OreOverrides;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Loads, saves and applies the global ore configuration.
 */
public final class ConfigManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("ore_multiplier");
    private static final ConfigManager INSTANCE = new ConfigManager();

    private final Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    private final Path configPath = FabricLoader.getInstance().getConfigDir().resolve("ore_multiplier.json");

    private MinecraftServer server;
    private OreConfig config;
    private Map<String, DiscoveredOre> discovery = Map.of();

    private ConfigManager() {
    }

    public static ConfigManager get() {
        return INSTANCE;
    }

    public boolean isLoaded() {
        return server != null && config != null;
    }

    public OreConfig config() {
        return config;
    }

    public Map<String, DiscoveredOre> discovery() {
        return discovery;
    }

    public Path path() {
        return configPath;
    }

    public void onServerStarting(MinecraftServer server) {
        this.server = server;
        try {
            loadFromDisk();
            discovery = OreDiscovery.discover(server);
            mergeDiscovered();
            validate();
            save();
            rebuild();
            LOGGER.info("Loaded {} ore variants from {}", discovery.size(), configPath);
        } catch (Exception exception) {
            LOGGER.error("Could not load ore configuration; ore generation will be left untouched", exception);
            OreOverrides.clear();
        }
    }

    public void onServerStopping() {
        server = null;
        config = null;
        discovery = Map.of();
        OreOverrides.clear();
    }

    /**
     * Re-reads the file from disk, discovers missing ores and applies the result.
     *
     * @return number of discovered ore variants
     */
    public int reload() throws IOException {
        requireServer();
        loadFromDisk();
        discovery = OreDiscovery.discover(server);
        mergeDiscovered();
        validate();
        save();
        rebuild();
        return discovery.size();
    }

    public void saveAndApply() throws IOException {
        requireServer();
        validate();
        save();
        rebuild();
    }

    public void resetVariants(Collection<String> variantIds) throws IOException {
        requireServer();
        for (String variantId : variantIds) {
            DiscoveredOre discovered = discovery.get(variantId);
            if (discovered == null) {
                continue;
            }
            removeVariantFromOtherGroups(discovered.groupKey(), variantId);
            OreGroup group = config.ores.computeIfAbsent(discovered.groupKey(), key -> new OreGroup());
            group.variants.put(variantId, discovered.defaults());
        }
        saveAndApply();
    }

    private void rebuild() {
        OreOverrides.install(OreOverrides.build(server, config, discovery));
    }

    private void mergeDiscovered() {
        for (DiscoveredOre discovered : discovery.values()) {
            removeVariantFromOtherGroups(discovered.groupKey(), discovered.variantId());
            OreGroup group = config.ores.computeIfAbsent(discovered.groupKey(), key -> new OreGroup());
            group.variants.putIfAbsent(discovered.variantId(), discovered.defaults());
        }
    }

    private void removeVariantFromOtherGroups(String keepGroup, String variantId) {
        for (Map.Entry<String, OreGroup> entry : config.ores.entrySet()) {
            if (!entry.getKey().equals(keepGroup)) {
                entry.getValue().variants.remove(variantId);
            }
        }
    }

    private void validate() {
        config.configVersion = OreConfig.CURRENT_VERSION;
        double requestedCountMultiplier = config.countMultiplier;
        double requestedSizeMultiplier = config.sizeMultiplier;
        config.countMultiplier = OreLimits.clampMultiplier(config.countMultiplier);
        config.sizeMultiplier = OreLimits.clampMultiplier(config.sizeMultiplier);
        int clampedVariants = 0;

        for (OreGroup group : config.ores.values()) {
            if (group == null) {
                continue;
            }
            if (group.variants == null) {
                group.variants = new TreeMap<>();
            }
            for (VariantConfig variant : group.variants.values()) {
                if (variant == null) {
                    continue;
                }
                int clampedCount = OreLimits.clampCount(variant.count);
                int clampedSize = OreLimits.clampSize(variant.size);
                if (clampedCount != variant.count || clampedSize != variant.size) {
                    clampedVariants++;
                }
                variant.count = clampedCount;
                variant.size = clampedSize;
                if (variant.minY > variant.maxY) {
                    int swap = variant.minY;
                    variant.minY = variant.maxY;
                    variant.maxY = swap;
                }
                String distribution = variant.distribution == null
                        ? "uniform"
                        : variant.distribution.toLowerCase(Locale.ROOT);
                if (!distribution.equals("uniform")
                        && !distribution.equals("triangle")
                        && !distribution.equals("trapezoid")) {
                    distribution = "uniform";
                }
                variant.distribution = distribution;
                variant.plateau = Math.max(0, Math.min(variant.maxY - variant.minY, variant.plateau));
            }
        }

        if (requestedCountMultiplier != config.countMultiplier
                || requestedSizeMultiplier != config.sizeMultiplier) {
            LOGGER.warn("Clamped global ore multipliers to x{}..x{} (count was x{}, size was x{}).",
                    OreLimits.MIN_MULTIPLIER, OreLimits.MAX_MULTIPLIER,
                    requestedCountMultiplier, requestedSizeMultiplier);
        }
        if (clampedVariants > 0) {
            LOGGER.warn("Clamped {} ore variant value(s) to the allowed ranges (count {}..{}, size {}..{}).",
                    clampedVariants, OreLimits.MIN_COUNT, OreLimits.MAX_COUNT,
                    OreLimits.MIN_SIZE, OreLimits.MAX_SIZE);
        }
    }

    private void loadFromDisk() throws IOException {
        if (!Files.exists(configPath)) {
            config = new OreConfig();
            return;
        }

        try (Reader reader = Files.newBufferedReader(configPath)) {
            OreConfig loaded = gson.fromJson(reader, OreConfig.class);
            config = loaded != null ? loaded : new OreConfig();
            normalize();
        } catch (JsonParseException exception) {
            LOGGER.error("Invalid JSON in {}; moving it aside and starting with defaults", configPath, exception);
            Path broken = configPath.resolveSibling(configPath.getFileName() + ".broken");
            Files.move(configPath, broken, StandardCopyOption.REPLACE_EXISTING);
            config = new OreConfig();
        }
    }

    private void normalize() {
        if (config.ores == null) {
            config.ores = new TreeMap<>();
        }
        config.ores.entrySet().removeIf(entry -> entry.getKey() == null || entry.getValue() == null);
        for (OreGroup group : config.ores.values()) {
            if (group.variants == null) {
                group.variants = new TreeMap<>();
            }
            group.variants.entrySet().removeIf(entry -> entry.getKey() == null || entry.getValue() == null);
        }
    }

    private void save() throws IOException {
        Path parent = configPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path temporary = configPath.resolveSibling(configPath.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(temporary)) {
            gson.toJson(config, writer);
        }

        try {
            Files.move(temporary, configPath,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, configPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void requireServer() {
        if (server == null || config == null) {
            throw new IllegalStateException("Ore configuration is not loaded yet");
        }
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
