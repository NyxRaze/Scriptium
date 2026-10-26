package com.scriptium.scripting.api;

import com.scriptium.logging.ScriptiumLogger;

public final class ScriptLogger {

    private final ScriptiumLogger.ScriptLogger logger;

    public ScriptLogger(String scriptName) {
        this.logger = ScriptiumLogger.script(scriptName);
    }

    public void info(Object message) {
        logger.info(String.valueOf(message));
    }

    public void warn(Object message) {
        logger.warn(String.valueOf(message));
    }

    public void error(Object message) {
        logger.error(String.valueOf(message));
    }

    public void debug(Object message) {
        logger.debug(String.valueOf(message));
    }
}