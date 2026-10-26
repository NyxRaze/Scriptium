
package com.scriptium.scripting.command;

import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.StringReader;

import net.minecraft.network.chat.Component;

import java.util.Collection;
import java.util.List;

public final class ScriptDecimalArgumentType implements ArgumentType<Double> {

    private final String name;

    private final boolean rejectTrailingInput;
    private final String usage;

    public ScriptDecimalArgumentType(String name, boolean rejectTrailingInput, String usage) {
        this.name = name;

        this.rejectTrailingInput = rejectTrailingInput;
        this.usage = usage;
    }

    @Override
    public Double parse(StringReader reader) throws CommandSyntaxException {
        String input = reader.readUnquotedString();

        try {
            double value = Double.parseDouble(input);

            if (!Double.isFinite(value)) {
                throw new NumberFormatException();
            }

            checkTrailingInput(reader);
            return value;
        } catch (NumberFormatException e) {
            throw new SimpleCommandExceptionType(Component.literal(name + " must be a number."))
                    .create();
        }
    }

    private void checkTrailingInput(StringReader reader) throws CommandSyntaxException {
        if (!rejectTrailingInput) {
            return;
        }

        while (reader.canRead() && Character.isWhitespace(reader.peek())) {
            reader.skip();
        }

        if (reader.canRead()) {
            throw new SimpleCommandExceptionType(Component.literal("Incorrect arguments. Usage: " + usage))
                    .create();
        }
    }

    @Override
    public Collection<String> getExamples() {
        return List.of("1", "2.5", "-0.75");
    }
}
