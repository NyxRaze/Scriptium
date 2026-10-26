package com.scriptium.command;

import com.scriptium.scripting.Script;
import com.scriptium.Scriptium;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.network.chat.Component;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.*;

public final class ScriptCommand {

        private ScriptCommand() {
        }

        public static void register() {
                ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                                literal("script")
                                                .then(loadCommand())
                                                .then(unloadCommand())
                                                .then(reloadCommand())
                                                .then(listCommand())));
        }

        // /script load <name>
        @SuppressWarnings("null")
        private static LiteralArgumentBuilder<FabricClientCommandSource> loadCommand() {
                return literal("load").then(argument("name", StringArgumentType.word())
                                .executes(ctx -> {
                                        String name = StringArgumentType.getString(ctx, "name");

                                        if (Scriptium.SCRIPTS.load(name)) {
                                                ctx.getSource().sendFeedback(Component
                                                                .literal("§aSuccessfully loaded script: §f" + name));
                                        } else {
                                                ctx.getSource().sendFeedback(Component
                                                                .literal("§cFailed to load script: §f" + name));
                                        }

                                        return 1;
                                }))
                                .executes(ctx -> {
                                        ctx.getSource().sendFeedback(Component.literal("§cUsage: /script load <name>"));

                                        return 1;
                                });
        }

        // /script unload <name>
        @SuppressWarnings("null")
        private static LiteralArgumentBuilder<FabricClientCommandSource> unloadCommand() {
                return literal("unload").then(argument("name", StringArgumentType.word())
                                .executes(ctx -> {
                                        String name = StringArgumentType.getString(ctx, "name");

                                        if (Scriptium.SCRIPTS.unload(name)) {
                                                ctx.getSource().sendFeedback(Component
                                                                .literal("§aSuccessfully unloaded script: §f" + name));
                                        } else {
                                                ctx.getSource().sendFeedback(Component
                                                                .literal("§cFailed to unload script: §f" + name));
                                        }

                                        return 1;
                                }))
                                .executes(ctx -> {
                                        ctx.getSource().sendFeedback(
                                                        Component.literal("§cUsage: /script unload <name>"));

                                        return 1;
                                });
        }

        // /script reload <name>
        @SuppressWarnings("null")
        private static LiteralArgumentBuilder<FabricClientCommandSource> reloadCommand() {
                return literal("reload").then(argument("name", StringArgumentType.word())
                                .executes(ctx -> {
                                        String name = StringArgumentType.getString(ctx, "name");

                                        if (Scriptium.SCRIPTS.reload(name)) {
                                                ctx.getSource().sendFeedback(Component
                                                                .literal("§aSuccessfully reloaded script: §f" + name));
                                        } else {
                                                ctx.getSource().sendFeedback(Component
                                                                .literal("§cFailed to reload script: §f" + name));
                                        }

                                        return 1;
                                }))
                                .executes(ctx -> {
                                        ctx.getSource().sendFeedback(
                                                        Component.literal("§cUsage: /script reload <name>"));

                                        return 1;
                                });
        }

        // /script list
        private static LiteralArgumentBuilder<FabricClientCommandSource> listCommand() {
                return literal("list")
                                .executes(ctx -> {
                                        var scripts = Scriptium.SCRIPTS.getScripts();

                                        if (scripts.isEmpty()) {
                                                ctx.getSource().sendFeedback(Component
                                                                .literal("§cNo scripts are currently loaded."));

                                                return 1;
                                        }

                                        ctx.getSource().sendFeedback(Component
                                                        .literal("§aLoaded scripts (" + scripts.size() + "):"));

                                        for (Script script : scripts) {
                                                ctx.getSource().sendFeedback(
                                                                Component.literal("§f- " + script.getName()));
                                        }

                                        return 1;
                                });
        }
}