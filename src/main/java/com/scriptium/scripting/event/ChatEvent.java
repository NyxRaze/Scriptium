package com.scriptium.scripting.event;

import net.minecraft.network.chat.Component;

public final class ChatEvent {

    private final Component message;

    public ChatEvent(Component message) {
        this.message = message;
    }

    public Component getMessage() {
        return message;
    }
}