
package com.scriptium.scripting.command;

import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.StringReader;

import net.minecraft.network.chat.Component;

import java.util.Collection;
import java.util.List;

public final class ScriptWordArgumentType implements ArgumentType<String> {

    private final String name;

    private final boolean rejectTrailingInput;
    private final String usage;

    public ScriptWordArgumentType(String name, boolean rejectTrailingInput, String usage) {
        this.name = name;

        this.rejectTrailingInput = rejectTrailingInput;
        this.usage = usage;
    }

    @Override
    public String parse(StringReader reader) throws CommandSyntaxException {
        String value = reader.readUnquotedString();

        if (value.isEmpty()) {
            throw new SimpleCommandExceptionType(Component.literal(name + " cannot be empty."))
                    .create();
        }

        if (rejectTrailingInput) {
            while (reader.canRead() && Character.isWhitespace(reader.peek())) {
                reader.skip();
            }

            if (reader.canRead()) {
                throw new SimpleCommandExceptionType(Component.literal("Incorrect arguments. Usage: " + usage))
                        .create();
            }
        }

        return value;
    }

    @Override
    public Collection<String> getExamples() {
        return List.of("hello", "example", "value_1");
    }
}
