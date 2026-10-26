package com.scriptium.scripting.command;

import com.scriptium.logging.ScriptiumLogger;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.RootCommandNode;
import com.mojang.brigadier.CommandDispatcher;

import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.network.chat.Component;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;

public final class CommandManager {

        private final Map<String, List<ScriptCommand>> commands = new HashMap<>();
        private CommandDispatcher<FabricClientCommandSource> dispatcher;

        public void initialize() {
                ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
                        this.dispatcher = dispatcher;
                        registerCommands(dispatcher);
                });
        }

        public ScriptCommand register(String scriptName, String name, List<CommandArgument> arguments,
                        org.graalvm.polyglot.Value callback) {
                if (name == null || !name.matches("[a-zA-Z0-9_-]+")) {
                        throw new IllegalArgumentException("Invalid command name: " + name);
                }

                if (!callback.canExecute()) {
                        throw new IllegalArgumentException("Command callback must be a function.");
                }

                if (findCommand(name) != null) {
                        throw new IllegalArgumentException("A command named '" + name + "' is already registered.");
                }

                ScriptCommand command = new ScriptCommand(this, scriptName, name, callback, arguments);

                commands.computeIfAbsent(scriptName, ignored -> new ArrayList<>()).add(command);

                if (dispatcher != null) {
                        rebuildCommands();
                }

                return command;
        }

        public void unregister(ScriptCommand command) {
                List<ScriptCommand> scriptCommands = commands.get(command.getScriptName());

                if (scriptCommands == null || !scriptCommands.remove(command)) {
                        return;
                }

                if (scriptCommands.isEmpty()) {
                        commands.remove(command.getScriptName());
                }

                rebuildCommands();
        }

        public void removeScript(String scriptName) {
                if (commands.remove(scriptName) != null) {
                        rebuildCommands();
                }
        }

        private void rebuildCommands() {
                if (dispatcher == null) {
                        return;
                }

                RootCommandNode<FabricClientCommandSource> root = dispatcher.getRoot();

                root.getChildren().clear();

                registerCommands(dispatcher);
        }

        private ScriptCommand findCommand(String name) {
                for (List<ScriptCommand> scriptCommands : commands.values()) {
                        for (ScriptCommand command : scriptCommands) {
                                if (command.getName().equalsIgnoreCase(name)) {
                                        return command;
                                }
                        }
                }

                return null;
        }

        private void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
                for (List<ScriptCommand> scriptCommands : new ArrayList<>(commands.values())) {
                        for (ScriptCommand command : new ArrayList<>(scriptCommands)) {
                                registerCommand(dispatcher, command);
                        }
                }
        }

        @SuppressWarnings("null")
        private void registerCommand(CommandDispatcher<FabricClientCommandSource> dispatcher, ScriptCommand command) {
                var root = ClientCommands.literal(command.getName());
                ArgumentBuilder<FabricClientCommandSource, ?> current = root;
                List<CommandArgument> definitions = command.getArguments();

                if (definitions.isEmpty()) {
                        root.executes(context -> executeCommand(command, context.getSource(), context));
                } else {
                        root.executes(context -> {
                                context.getSource().sendFeedback(Component.literal("§eUsage: " + getUsage(command)));

                                return 0;
                        });
                }

                for (int i = 0; i < definitions.size(); i++) {
                        CommandArgument definition = definitions.get(i);

                        var argumentNode = switch (definition.type()) {
                                case "integer" ->
                                        argument(definition.name(), new ScriptIntegerArgumentType(definition.name(),
                                                        i == definitions.size() - 1, getUsage(command)));

                                case "decimal" ->
                                        argument(definition.name(), new ScriptDecimalArgumentType(definition.name(),
                                                        i == definitions.size() - 1, getUsage(command)));

                                case "word", "string" ->
                                        argument(definition.name(), new ScriptWordArgumentType(definition.name(),
                                                        i == definitions.size() - 1, getUsage(command)));

                                case "greedyString" -> {
                                        if (i != definitions.size() - 1) {
                                                throw new IllegalArgumentException(
                                                                "greedyString must be the final argument.");
                                        }

                                        yield argument(definition.name(), StringArgumentType.greedyString());
                                }

                                default -> throw new IllegalArgumentException(
                                                "Unsupported argument type: " + definition.type());
                        };

                        if (!definition.suggestions().isEmpty()) {
                                argumentNode.suggests((context, builder) -> {
                                        for (String suggestion : definition.suggestions()) {
                                                builder.suggest(suggestion);
                                        }

                                        return builder.buildFuture();
                                });
                        }

                        if (i == definitions.size() - 1) {
                                argumentNode.executes(context -> executeCommand(command, context.getSource(), context));
                        } else {
                                argumentNode.executes(context -> {
                                        context.getSource().sendFeedback(
                                                        Component.literal("§eUsage: " + getUsage(command)));

                                        return 0;
                                });
                        }

                        current.then(argumentNode);
                        current = argumentNode;
                }

                dispatcher.register(root);
        }

        @SuppressWarnings("null")
        private int executeCommand(ScriptCommand command, FabricClientCommandSource source,
                        CommandContext<FabricClientCommandSource> context) {
                try {
                        Map<String, Object> args = new LinkedHashMap<>();

                        for (CommandArgument definition : command.getArguments()) {
                                Object value = switch (definition.type()) {
                                        case "integer" -> context.getArgument(definition.name(), Integer.class);

                                        case "decimal" -> context.getArgument(definition.name(), Double.class);

                                        case "word", "string" -> context.getArgument(definition.name(), String.class);

                                        case "greedyString" -> StringArgumentType.getString(context, definition.name());

                                        default -> throw new IllegalArgumentException(
                                                        "Unsupported argument type: " + definition.type());
                                };

                                if (value instanceof Number number) {
                                        double numericValue = number.doubleValue();

                                        if (definition.min() != null && numericValue < definition.min()) {
                                                source.sendFeedback(Component.literal("§c" + definition.name()
                                                                + " must be at least "
                                                                + (definition.type().equals("integer")
                                                                                ? definition.min().intValue()
                                                                                : definition.min())
                                                                + "."));

                                                return 0;
                                        }

                                        if (definition.max() != null && numericValue > definition.max()) {
                                                source.sendFeedback(Component.literal("§c" + definition.name()
                                                                + " must be at most "
                                                                + (definition.type().equals("integer")
                                                                                ? definition.max().intValue()
                                                                                : definition.max())
                                                                + "."));

                                                return 0;
                                        }
                                }

                                if (!definition.suggestions().isEmpty() && (definition.type().equals("word")
                                                || definition.type().equals("string"))) {

                                        String input = String.valueOf(value);

                                        if (!definition.suggestions().contains(input)) {
                                                source.sendFeedback(Component.literal("§cInvalid " + definition.name()
                                                                + ". Choose: "
                                                                + String.join(", ", definition.suggestions()) + "."));
                                                return 0;
                                        }
                                }

                                args.put(definition.name(), value);
                        }

                        command.getCallback().execute(new CommandContextAPI(source, args));

                        return 1;
                } catch (Exception e) {
                        ScriptiumLogger.error("Command '" + command.getName() + "' failed: " + e.getMessage());

                        source.sendFeedback(Component.literal("§cCommand failed: " + command.getName()));

                        return 0;
                }
        }

        private String getUsage(ScriptCommand command) {
                StringBuilder usage = new StringBuilder("/" + command.getName());

                for (CommandArgument argument : command.getArguments()) {
                        usage.append(" <").append(argument.name()).append(">");
                }

                return usage.toString();
        }
}