package com.scriptium.scripting;

import org.graalvm.polyglot.Context;

import com.scriptium.logging.ScriptiumLogger;

import java.nio.file.Path;

public class Script {

    private final String name;
    private final Path file;
    private final Context context;
    private final ScriptiumLogger.ScriptLogger logger;

    public Script(String name, Path file, Context context) {
        this.name = name;
        this.file = file;
        this.context = context;
        this.logger = ScriptiumLogger.script(name);
    }

    public String getName() {
        return name;
    }

    public Path getFile() {
        return file;
    }

    public Context getContext() {
        return context;
    }

    public ScriptiumLogger.ScriptLogger getLogger() {
        return logger;
    }

    public void unload() {
        logger.info("Unloading script...");

        try {
            context.close();

            logger.info("Script unloaded.");
        } catch (Exception e) {
            logger.error("Failed to close script context: " + e.getMessage());
        }
    }
}