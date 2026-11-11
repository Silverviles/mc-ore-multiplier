package com.silverviles.ore_multiplier.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.silverviles.ore_multiplier.config.ConfigManager;
import com.silverviles.ore_multiplier.config.DiscoveredOre;
import com.silverviles.ore_multiplier.config.OreConfig;
import com.silverviles.ore_multiplier.config.OreDiscovery;
import com.silverviles.ore_multiplier.config.OreGroup;
import com.silverviles.ore_multiplier.config.OreLimits;
import com.silverviles.ore_multiplier.config.VariantConfig;
import net.fabricmc.fabric.api.permission.v1.PermissionPredicates;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.server.permissions.PermissionLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class OreConfigCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger("ore_multiplier");
    private static final Identifier PERMISSION =
            Identifier.fromNamespaceAndPath("ore_multiplier", "command/config");
    private static final List<String> DISTRIBUTIONS = List.of("uniform", "triangle", "trapezoid");

    private OreConfigCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("oreconfig")
                        .requires(PermissionPredicates.require(PERMISSION, PermissionLevel.GAMEMASTERS))
                        .executes(context -> execute(context, OreConfigCommand::summary))
                        .then(Commands.literal("list")
                                .executes(context -> execute(context, command -> list(command, null)))
                                .then(oreArgument()
                                        .executes(context -> execute(context, command ->
                                                list(command, StringArgumentType.getString(command, "ore"))))))
                        .then(Commands.literal("show")
                                .then(oreArgument()
                                        .executes(context -> execute(context, command ->
                                                show(command, StringArgumentType.getString(command, "ore"))))))
                        .then(Commands.literal("set")
                                .then(oreArgument()
                                        .then(variantArgument()
                                                .then(Commands.literal("count")
                                                        .then(Commands.argument("value", IntegerArgumentType.integer())
                                                                .executes(context -> execute(context, OreConfigCommand::setCount))))
                                                .then(Commands.literal("size")
                                                        .then(Commands.argument("value", IntegerArgumentType.integer())
                                                                .executes(context -> execute(context, OreConfigCommand::setSize))))
                                                .then(Commands.literal("min-y")
                                                        .then(Commands.argument("value", IntegerArgumentType.integer(-2048, 2048))
                                                                .executes(context -> execute(context, OreConfigCommand::setMinY))))
                                                .then(Commands.literal("max-y")
                                                        .then(Commands.argument("value", IntegerArgumentType.integer(-2048, 2048))
                                                                .executes(context -> execute(context, OreConfigCommand::setMaxY))))
                                                .then(Commands.literal("distribution")
                                                        .then(Commands.argument("value", StringArgumentType.word())
                                                                .suggests((context, builder) ->
                                                                        SharedSuggestionProvider.suggest(DISTRIBUTIONS, builder))
                                                                .executes(context -> execute(context, OreConfigCommand::setDistribution)))))))
                        .then(Commands.literal("enable")
                                .then(oreArgument()
                                        .executes(context -> execute(context, command -> setEnabled(command, true, "all")))
                                        .then(variantArgument()
                                                .executes(context -> execute(context, command ->
                                                        setEnabled(command, true, StringArgumentType.getString(command, "variant")))))))
                        .then(Commands.literal("disable")
                                .then(oreArgument()
                                        .executes(context -> execute(context, command -> setEnabled(command, false, "all")))
                                        .then(variantArgument()
                                                .executes(context -> execute(context, command ->
                                                        setEnabled(command, false, StringArgumentType.getString(command, "variant")))))))
                        .then(Commands.literal("multiplier")
                                .then(Commands.literal("count")
                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(OreLimits.MIN_MULTIPLIER))
                                                .executes(context -> execute(context, OreConfigCommand::setCountMultiplier))))
                                .then(Commands.literal("size")
                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(OreLimits.MIN_MULTIPLIER))
                                                .executes(context -> execute(context, OreConfigCommand::setSizeMultiplier)))))
                        .then(Commands.literal("reset")
                                .then(Commands.argument("ore", StringArgumentType.word())
                                        .suggests(OreConfigCommand::suggestResetOres)
                                        .executes(context -> execute(context, command ->
                                                reset(command, StringArgumentType.getString(command, "ore"), "all")))
                                        .then(Commands.argument("variant", StringArgumentType.word())
                                                .suggests(OreConfigCommand::suggestVariants)
                                                .executes(context -> execute(context, command ->
                                                        reset(command,
                                                                StringArgumentType.getString(command, "ore"),
                                                                StringArgumentType.getString(command, "variant")))))))
                        .then(Commands.literal("reload")
                                .executes(context -> execute(context, OreConfigCommand::reload)))
        );
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> oreArgument() {
        return Commands.argument("ore", StringArgumentType.word())
                .suggests(OreConfigCommand::suggestOres);
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> variantArgument() {
        return Commands.argument("variant", StringArgumentType.word())
                .suggests(OreConfigCommand::suggestVariants);
    }

    private static int summary(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ConfigManager manager = manager(source);
        if (manager == null) {
            return 0;
        }

        OreConfig config = manager.config();
        source.sendSuccess(() -> header("Ore configuration"), false);
        source.sendSuccess(() -> Component.literal("File: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(manager.path().toString()).withStyle(ChatFormatting.WHITE)), false);
        source.sendSuccess(() -> Component.literal(
                "State: " + (config.enabled ? "enabled" : "disabled")
                        + " | count x" + format(config.countMultiplier)
                        + " | size x" + format(config.sizeMultiplier)
                        + " | " + manager.discovery().size() + " ore features"
        ).withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal(
                "Try /oreconfig list, /oreconfig show <ore>, or /oreconfig set <ore> <variant> <property> <value>."
        ).withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> context, String oreArgument) {
        CommandSourceStack source = context.getSource();
        ConfigManager manager = manager(source);
        if (manager == null) {
            return 0;
        }

        OreConfig config = manager.config();
        List<String> keys;
        if (oreArgument == null) {
            keys = new ArrayList<>(config.ores.keySet());
        } else {
            keys = List.of(resolveOreKey(config, oreArgument));
        }

        if (keys.isEmpty()) {
            source.sendSuccess(() -> header("No ore features have been discovered."), false);
            return 1;
        }

        source.sendSuccess(() -> Component.literal(
                "Ore config | " + (config.enabled ? "enabled" : "disabled")
                        + " | count x" + format(config.countMultiplier)
                        + " | size x" + format(config.sizeMultiplier)
        ).withStyle(ChatFormatting.DARK_GRAY), false);

        for (String key : keys) {
            printGroup(source, manager, key, false);
        }
        return keys.size();
    }

    private static int show(CommandContext<CommandSourceStack> context, String oreArgument) {
        CommandSourceStack source = context.getSource();
        ConfigManager manager = manager(source);
        if (manager == null) {
            return 0;
        }

        String key = resolveOreKey(manager.config(), oreArgument);
        printGroup(source, manager, key, true);
        return 1;
    }

    private static void printGroup(CommandSourceStack source, ConfigManager manager, String oreKey, boolean detailed) {
        OreConfig config = manager.config();
        OreGroup group = config.ores.get(oreKey);
        if (group == null) {
            return;
        }

        MutableComponent title = Component.literal(OreDiscovery.displayName(oreKey))
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal("  (" + oreKey + ")").withStyle(ChatFormatting.DARK_GRAY));
        source.sendSuccess(() -> title, false);

        for (Map.Entry<String, VariantConfig> entry : group.variants.entrySet()) {
            String variantId = entry.getKey();
            VariantConfig variant = entry.getValue();
            DiscoveredOre discovered = manager.discovery().get(variantId);
            String suffix = discovered != null
                    ? discovered.variantSuffix()
                    : OreDiscovery.variantSuffix(variantId, oreKey);
            String dimension = discovered != null ? discovered.dimension().id() : "unknown";

            int effectiveCount = !variant.enabled
                    ? 0
                    : (variant.count >= 0 ? OreLimits.effectiveCount(variant.count, config.countMultiplier) : -1);
            String countText = variant.count < 0 ? "none" : Integer.toString(variant.count);
            if (variant.count >= 0 && effectiveCount != variant.count) {
                countText += " (" + effectiveCount + ")";
            }

            int effectiveSize = OreLimits.effectiveSize(variant.size, config.sizeMultiplier);
            String sizeText = Integer.toString(variant.size);
            if (effectiveSize != variant.size) {
                sizeText += " (" + effectiveSize + ")";
            }

            MutableComponent line = Component.literal("  " + pad(suffix, 8)).withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal("count " + pad(countText, 12)).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal("size " + pad(sizeText, 5)).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal("Y " + variant.minY + ".." + variant.maxY + "  ").withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(variant.distribution).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal("  " + (variant.enabled ? "on" : "off"))
                            .withStyle(variant.enabled ? ChatFormatting.GREEN : ChatFormatting.RED));

            if (detailed) {
                line.append(Component.literal("  " + dimension).withStyle(ChatFormatting.DARK_GRAY));
            }

            line.append(chip("count", "/oreconfig set " + oreKey + " " + suffix + " count " + variant.count));
            line.append(chip("size", "/oreconfig set " + oreKey + " " + suffix + " size " + variant.size));
            line.append(chip("min-y", "/oreconfig set " + oreKey + " " + suffix + " min-y " + variant.minY));
            line.append(chip("max-y", "/oreconfig set " + oreKey + " " + suffix + " max-y " + variant.maxY));
            line.append(chip(variant.enabled ? "off" : "on",
                    "/oreconfig " + (variant.enabled ? "disable" : "enable") + " " + oreKey + " " + suffix));
            line.append(chip("reset", "/oreconfig reset " + oreKey + " " + suffix));

            source.sendSuccess(() -> line, false);
        }
    }

    private static int setCount(CommandContext<CommandSourceStack> context) throws Exception {
        CommandSourceStack source = context.getSource();
        ConfigManager manager = manager(source);
        if (manager == null) {
            return 0;
        }

        String oreKey = resolveOreKey(manager.config(), StringArgumentType.getString(context, "ore"));
        List<String> variants = resolveVariants(manager, oreKey, StringArgumentType.getString(context, "variant"));
        int requested = IntegerArgumentType.getInteger(context, "value");
        int value = OreLimits.clampCount(requested);

        for (String variant : variants) {
            manager.config().ores.get(oreKey).variants.get(variant).count = value;
        }
        String message = "Set count to " + value + " for " + variants.size()
                + " variant(s) of " + oreKey + ".";
        if (requested != value) {
            message += " Requested " + requested + " was clamped to the allowed range "
                    + OreLimits.MIN_COUNT + ".." + OreLimits.MAX_COUNT + ".";
        }
        if (value > 0 && value * manager.config().countMultiplier > OreLimits.MAX_COUNT) {
            message += " With the current count multiplier, effective count is capped at "
                    + OreLimits.MAX_COUNT + ".";
        }
        return apply(source, manager, message);
    }

    private static int setSize(CommandContext<CommandSourceStack> context) throws Exception {
        CommandSourceStack source = context.getSource();
        ConfigManager manager = manager(source);
        if (manager == null) {
            return 0;
        }

        String oreKey = resolveOreKey(manager.config(), StringArgumentType.getString(context, "ore"));
        List<String> variants = resolveVariants(manager, oreKey, StringArgumentType.getString(context, "variant"));
        int requested = IntegerArgumentType.getInteger(context, "value");
        int value = OreLimits.clampSize(requested);

        for (String variant : variants) {
            manager.config().ores.get(oreKey).variants.get(variant).size = value;
        }
        String message = "Set vein size to " + value + " for " + variants.size()
                + " variant(s) of " + oreKey + ".";
        if (requested != value) {
            message += " Requested " + requested + " was clamped to the allowed range "
                    + OreLimits.MIN_SIZE + ".." + OreLimits.MAX_SIZE
                    + " (the hard limit that keeps veins inside Minecraft's chunk write radius).";
        }
        if (value > 0 && value * manager.config().sizeMultiplier > OreLimits.MAX_SIZE) {
            message += " With the current size multiplier, effective size is capped at "
                    + OreLimits.MAX_SIZE + ".";
        }
        return apply(source, manager, message);
    }

    private static int setMinY(CommandContext<CommandSourceStack> context) throws Exception {
        return setY(context, true);
    }

    private static int setMaxY(CommandContext<CommandSourceStack> context) throws Exception {
        return setY(context, false);
    }

    private static int setY(CommandContext<CommandSourceStack> context, boolean minimum) throws Exception {
        CommandSourceStack source = context.getSource();
        ConfigManager manager = manager(source);
        if (manager == null) {
            return 0;
        }

        String oreKey = resolveOreKey(manager.config(), StringArgumentType.getString(context, "ore"));
        List<String> variants = resolveVariants(manager, oreKey, StringArgumentType.getString(context, "variant"));
        int value = IntegerArgumentType.getInteger(context, "value");

        for (String variant : variants) {
            VariantConfig config = manager.config().ores.get(oreKey).variants.get(variant);
            if (minimum) {
                config.minY = value;
            } else {
                config.maxY = value;
            }
        }
        return apply(source, manager, "Set " + (minimum ? "min" : "max") + " Y to " + value + " for "
                + variants.size() + " variant(s) of " + oreKey + ".");
    }

    private static int setDistribution(CommandContext<CommandSourceStack> context) throws Exception {
        CommandSourceStack source = context.getSource();
        ConfigManager manager = manager(source);
        if (manager == null) {
            return 0;
        }

        String oreKey = resolveOreKey(manager.config(), StringArgumentType.getString(context, "ore"));
        List<String> variants = resolveVariants(manager, oreKey, StringArgumentType.getString(context, "variant"));
        String value = StringArgumentType.getString(context, "value").toLowerCase(Locale.ROOT);
        if (!DISTRIBUTIONS.contains(value)) {
            throw new IllegalArgumentException("Unknown distribution '" + value + "'. Use one of " + DISTRIBUTIONS + ".");
        }

        for (String variant : variants) {
            VariantConfig config = manager.config().ores.get(oreKey).variants.get(variant);
            config.distribution = value;
            if (value.equals("triangle")) {
                config.plateau = 0;
            }
        }
        return apply(source, manager, "Set height distribution to " + value + " for " + variants.size()
                + " variant(s) of " + oreKey + ".");
    }

    private static int setEnabled(CommandContext<CommandSourceStack> context, boolean enabled, String variantArgument) throws Exception {
        CommandSourceStack source = context.getSource();
        ConfigManager manager = manager(source);
        if (manager == null) {
            return 0;
        }

        String oreKey = resolveOreKey(manager.config(), StringArgumentType.getString(context, "ore"));
        List<String> variants = resolveVariants(manager, oreKey, variantArgument);
        for (String variant : variants) {
            manager.config().ores.get(oreKey).variants.get(variant).enabled = enabled;
        }
        return apply(source, manager, (enabled ? "Enabled " : "Disabled ") + variants.size()
                + " variant(s) of " + oreKey + ".");
    }

    private static int setCountMultiplier(CommandContext<CommandSourceStack> context) throws Exception {
        CommandSourceStack source = context.getSource();
        ConfigManager manager = manager(source);
        if (manager == null) {
            return 0;
        }

        double requested = DoubleArgumentType.getDouble(context, "value");
        double value = OreLimits.clampMultiplier(requested);
        manager.config().countMultiplier = value;
        String message = "Global count multiplier set to x" + format(value) + ".";
        if (requested != value) {
            message += " Requested x" + format(requested) + " exceeds the maximum of x"
                    + format(OreLimits.MAX_MULTIPLIER) + " and was clamped.";
        }
        if (hasCountCappedByMultiplier(manager, value)) {
            message += " Effective counts are capped at " + OreLimits.MAX_COUNT + ".";
        }
        return apply(source, manager, message);
    }

    private static int setSizeMultiplier(CommandContext<CommandSourceStack> context) throws Exception {
        CommandSourceStack source = context.getSource();
        ConfigManager manager = manager(source);
        if (manager == null) {
            return 0;
        }

        double requested = DoubleArgumentType.getDouble(context, "value");
        double value = OreLimits.clampMultiplier(requested);
        manager.config().sizeMultiplier = value;
        String message = "Global size multiplier set to x" + format(value) + ".";
        if (requested != value) {
            message += " Requested x" + format(requested) + " exceeds the maximum of x"
                    + format(OreLimits.MAX_MULTIPLIER) + " and was clamped.";
        }
        if (hasSizeCappedByMultiplier(manager, value)) {
            message += " Effective vein sizes are capped at " + OreLimits.MAX_SIZE + ".";
        }
        return apply(source, manager, message);
    }

    private static boolean hasSizeCappedByMultiplier(ConfigManager manager, double sizeMultiplier) {
        for (OreGroup group : manager.config().ores.values()) {
            for (VariantConfig variant : group.variants.values()) {
                if (variant.size * sizeMultiplier > OreLimits.MAX_SIZE) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasCountCappedByMultiplier(ConfigManager manager, double countMultiplier) {
        for (OreGroup group : manager.config().ores.values()) {
            for (VariantConfig variant : group.variants.values()) {
                if (variant.count >= 0 && variant.count * countMultiplier > OreLimits.MAX_COUNT) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int reset(CommandContext<CommandSourceStack> context, String oreArgument, String variantArgument) throws Exception {
        CommandSourceStack source = context.getSource();
        ConfigManager manager = manager(source);
        if (manager == null) {
            return 0;
        }

        List<String> variants;
        if (oreArgument.equalsIgnoreCase("all")) {
            variants = new ArrayList<>(manager.discovery().keySet());
        } else {
            String oreKey = resolveOreKey(manager.config(), oreArgument);
            OreGroup group = manager.config().ores.get(oreKey);
            if (group == null) {
                throw new IllegalArgumentException("Unknown ore '" + oreArgument + "'.");
            }

            if (variantArgument.equalsIgnoreCase("all")) {
                variants = new ArrayList<>(group.variants.keySet());
                variants.removeIf(variant -> !manager.discovery().containsKey(variant));
            } else {
                variants = resolveVariants(manager, oreKey, variantArgument);
            }
        }

        if (variants.isEmpty()) {
            throw new IllegalArgumentException("There are no discovered variants to reset.");
        }

        manager.resetVariants(variants);
        source.sendSuccess(() -> header("Reset " + variants.size() + " variant(s) to vanilla defaults."), true);
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> context) throws Exception {
        CommandSourceStack source = context.getSource();
        ConfigManager manager = manager(source);
        if (manager == null) {
            return 0;
        }

        int count = manager.reload();
        source.sendSuccess(() -> header("Reloaded " + count + " ore features from " + manager.path() + "."), true);
        return 1;
    }

    private static int apply(CommandSourceStack source, ConfigManager manager, String message) throws Exception {
        manager.saveAndApply();
        source.sendSuccess(() -> header(message), true);
        return 1;
    }

    private static ConfigManager manager(CommandSourceStack source) {
        ConfigManager manager = ConfigManager.get();
        if (!manager.isLoaded()) {
            source.sendFailure(Component.literal("Ore configuration is not loaded yet. Try again in a moment."));
            return null;
        }
        return manager;
    }

    private static String resolveOreKey(OreConfig config, String input) {
        String raw = input.toLowerCase(Locale.ROOT);
        String namespace = "minecraft";
        String path = raw;

        int colon = raw.indexOf(':');
        if (colon >= 0) {
            namespace = raw.substring(0, colon);
            path = raw.substring(colon + 1);
        }
        if (path.startsWith("ore_")) {
            path = path.substring("ore_".length());
        }
        if (path.equals("debris")) {
            path = "ancient_debris";
        }

        String normalized = namespace + ":" + path;
        if (config.ores.containsKey(normalized)) {
            return normalized;
        }

        List<String> matches = new ArrayList<>();
        for (String candidate : config.ores.keySet()) {
            String candidatePath = candidate.substring(candidate.indexOf(':') + 1);
            if (candidatePath.equals(path) || candidatePath.endsWith("_" + path)) {
                matches.add(candidate);
            }
        }

        if (matches.size() == 1) {
            return matches.get(0);
        }
        if (matches.size() > 1) {
            throw new IllegalArgumentException("Ambiguous ore '" + input + "': " + String.join(", ", matches));
        }
        throw new IllegalArgumentException("Unknown ore '" + input + "'. Use /oreconfig list.");
    }

    private static List<String> resolveVariants(ConfigManager manager, String oreKey, String input) {
        OreGroup group = manager.config().ores.get(oreKey);
        if (group == null) {
            throw new IllegalArgumentException("Unknown ore '" + oreKey + "'.");
        }
        if (input.equalsIgnoreCase("all")) {
            return new ArrayList<>(group.variants.keySet());
        }

        String raw = input.toLowerCase(Locale.ROOT);
        String namespace = "minecraft";
        String path = raw;
        int colon = raw.indexOf(':');
        if (colon >= 0) {
            namespace = raw.substring(0, colon);
            path = raw.substring(colon + 1);
        }
        if (path.startsWith("ore_")) {
            path = path.substring("ore_".length());
        }

        String normalized = namespace + ":" + path;
        for (String variant : group.variants.keySet()) {
            String variantPath = variant.substring(variant.indexOf(':') + 1);
            String stripped = variantPath.startsWith("ore_") ? variantPath.substring("ore_".length()) : variantPath;
            String suffix = OreDiscovery.variantSuffix(variant, oreKey);
            if (variant.equals(normalized) || stripped.equals(path) || suffix.equalsIgnoreCase(input)) {
                return List.of(variant);
            }
        }

        throw new IllegalArgumentException("Unknown variant '" + input + "' for " + oreKey
                + ". Use /oreconfig list " + oreKey + ".");
    }

    private static CompletableFuture<Suggestions> suggestOres(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder
    ) {
        return suggestOres(builder, false);
    }

    private static CompletableFuture<Suggestions> suggestResetOres(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder
    ) {
        return suggestOres(builder, true);
    }

    private static CompletableFuture<Suggestions> suggestOres(SuggestionsBuilder builder, boolean includeAll) {
        ConfigManager manager = ConfigManager.get();
        if (!manager.isLoaded()) {
            return builder.buildFuture();
        }

        LinkedHashSet<String> suggestions = new LinkedHashSet<>();
        if (includeAll) {
            suggestions.add("all");
        }
        for (String key : manager.config().ores.keySet()) {
            suggestions.add(key);
            String path = key.substring(key.indexOf(':') + 1);
            suggestions.add(path);
            suggestions.add("ore_" + path);
            if (path.equals("ancient_debris")) {
                suggestions.add("debris");
            }
        }
        return SharedSuggestionProvider.suggest(suggestions, builder);
    }

    private static CompletableFuture<Suggestions> suggestVariants(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder
    ) {
        ConfigManager manager = ConfigManager.get();
        if (!manager.isLoaded()) {
            return builder.buildFuture();
        }

        String oreArgument;
        try {
            oreArgument = StringArgumentType.getString(context, "ore");
        } catch (IllegalArgumentException exception) {
            return builder.buildFuture();
        }

        try {
            String oreKey = resolveOreKey(manager.config(), oreArgument);
            OreGroup group = manager.config().ores.get(oreKey);
            if (group == null) {
                return builder.buildFuture();
            }

            LinkedHashSet<String> suggestions = new LinkedHashSet<>();
            suggestions.add("all");
            for (String variant : group.variants.keySet()) {
                suggestions.add(OreDiscovery.variantSuffix(variant, oreKey));
                suggestions.add(variant);
                suggestions.add(variant.substring(variant.indexOf(':') + 1));
            }
            return SharedSuggestionProvider.suggest(suggestions, builder);
        } catch (IllegalArgumentException exception) {
            return builder.buildFuture();
        }
    }

    private static MutableComponent chip(String label, String command) {
        return Component.literal(" [" + label + "]").withStyle(Style.EMPTY
                .withColor(ChatFormatting.AQUA)
                .withClickEvent(new ClickEvent.SuggestCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(command))));
    }

    private static MutableComponent header(String message) {
        return Component.literal("[OreConfig] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(message).withStyle(ChatFormatting.WHITE));
    }

    private static String pad(String value, int width) {
        if (value.length() >= width) {
            return value + " ";
        }
        return String.format(Locale.ROOT, "%-" + width + "s", value);
    }

    private static String format(double value) {
        if (value == Math.rint(value)) {
            return Long.toString((long) value);
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    @FunctionalInterface
    private interface CommandBody {
        int run(CommandContext<CommandSourceStack> context) throws Exception;
    }

    private static int execute(CommandContext<CommandSourceStack> context, CommandBody body) {
        try {
            return body.run(context);
        } catch (IllegalArgumentException exception) {
            context.getSource().sendFailure(Component.literal(exception.getMessage()));
            return 0;
        } catch (Exception exception) {
            LOGGER.error("Ore config command failed", exception);
            context.getSource().sendFailure(Component.literal("Ore config error: " + exception.getMessage()));
            return 0;
        }
    }
}
