package com.scriptium.scripting.command;

import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

import java.util.Map;

public final class CommandContextAPI {

    private final FabricClientCommandSource source;
    private final Map<String, Object> arguments;

    public CommandContextAPI(FabricClientCommandSource source, Map<String, Object> arguments) {
        this.source = source;
        this.arguments = Map.copyOf(arguments);
    }

    @SuppressWarnings("null")
    public void reply(Object message) {
        source.sendFeedback(Component.literal(String.valueOf(message)));
    }

    public void error(Object message) {
        source.sendFeedback(Component.literal("§c" + message));
    }

    public void success(Object message) {
        source.sendFeedback(Component.literal("§a" + message));
    }

    public void warn(Object message) {
        source.sendFeedback(Component.literal("§e" + message));
    }

    public String getPlayerName() {
        return source.getPlayer().getGameProfile().name();
    }

    public Map<String, Object> getArgs() {
        return arguments;
    }
}