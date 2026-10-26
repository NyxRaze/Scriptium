package com.scriptium;

import com.scriptium.logging.ScriptiumLogger;
import com.scriptium.command.ScriptCommand;

import com.scriptium.scripting.ScriptManager;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.api.ModInitializer;

public class Scriptium implements ModInitializer {

	public static ScriptManager SCRIPTS;

	@Override
	public void onInitialize() {
		ScriptiumLogger.initialize(FabricLoader.getInstance().getGameDir());

		ScriptiumLogger.info("Starting Scriptium...");

		SCRIPTS = new ScriptManager();
		SCRIPTS.initialize();

		ScriptCommand.register();

		ScriptiumLogger.info("Scriptium initialized.");
	}
}