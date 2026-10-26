package com.scriptium.scripting;

import com.scriptium.logging.ScriptiumLogger;

import com.scriptium.scripting.keybind.KeybindManager;
import com.scriptium.scripting.command.CommandManager;
import com.scriptium.scripting.input.InputManager;

import com.scriptium.scripting.event.ScriptEventBus;
import com.scriptium.scripting.event.EventManager;

import com.scriptium.scripting.api.ScriptManagerAPI;
import com.scriptium.scripting.api.ScriptLogger;
import com.scriptium.scripting.api.KeybindsAPI;
import com.scriptium.scripting.api.CommandsAPI;
import com.scriptium.scripting.api.EventsAPI;
import com.scriptium.scripting.api.InputAPI;
import com.scriptium.scripting.api.KeysAPI;
import com.scriptium.scripting.api.GuiAPI;
import com.scriptium.scripting.api.StorageAPI;
import com.scriptium.scripting.api.AimAPI;
import com.scriptium.scripting.aim.AimController;

import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ScriptManager {

        private final Map<String, Script> scripts = new HashMap<>();

        private final CommandManager commandManager = new CommandManager();
        private final KeybindManager keybindManager = new KeybindManager();
        private final InputManager inputManager = new InputManager();

        private final EventManager eventManager = new EventManager();

        /*
         * script -> scripts it imports
         */
        private final Map<String, Set<String>> dependencies = new HashMap<>();

        /*
         * script -> scripts depending on it
         */
        private final Map<String, Set<String>> dependents = new HashMap<>();

        private Path scriptsDirectory;

        public void initialize() {
                scriptsDirectory = Paths.get("scripts");

                try {
                        Files.createDirectories(scriptsDirectory);
                } catch (IOException e) {
                        throw new RuntimeException("Failed to create scripts directory", e);
                }

                commandManager.initialize();
                keybindManager.initialize();
                inputManager.initialize();

                ScriptEventBus.initialize(eventManager);
        }

        // ============================================================
        // Loading
        // ============================================================

        public boolean load(String name) {
                if (scripts.containsKey(name)) {
                        ScriptiumLogger.info("[Scriptium] Script already loaded: " + name);

                        return false;
                }

                Path file = getScriptFile(name);

                if (!Files.exists(file)) {
                        ScriptiumLogger.info("[Scriptium] Script not found: " + file);

                        return false;
                }

                try {
                        Context context = createContext(name);

                        ScriptLogger logger = new ScriptLogger(name);

                        context.getBindings("js").putMember("logger", logger);

                        ScriptManagerAPI scriptAPI = new ScriptManagerAPI(this, name);

                        KeybindsAPI keybindsAPI = new KeybindsAPI(keybindManager, name);
                        CommandsAPI commandsAPI = new CommandsAPI(commandManager, name);
                        InputAPI inputAPI = new InputAPI(name, inputManager);
                        AimAPI aimAPI = new AimAPI(name);
                        StorageAPI storageAPI = new StorageAPI(this, file);

                        EventsAPI eventsAPI = new EventsAPI(eventManager, name);

                        KeysAPI keysAPI = new KeysAPI();
                        GuiAPI guiAPI = new GuiAPI();

                        context.getBindings("js").putMember("__scriptium", scriptAPI);

                        context.getBindings("js").putMember("__keybinds", keybindsAPI);
                        context.getBindings("js").putMember("__commands", commandsAPI);
                        context.getBindings("js").putMember("__input", inputAPI);
                        context.getBindings("js").putMember("__aim", aimAPI);
                        context.getBindings("js").putMember("__storage", storageAPI);

                        context.getBindings("js").putMember("__events", eventsAPI);

                        context.getBindings("js").putMember("__keys", keysAPI);
                        context.getBindings("js").putMember("__gui", guiAPI);

                        context.eval("js", """
                                        globalThis.scripts = {
                                          load: (name) => __scriptium.load(name),
                                          unload: (name) => __scriptium.unload(name),
                                          reload: (name) => __scriptium.reload(name),
                                          isLoaded: (name) => __scriptium.isLoaded(name),
                                          list: () => __scriptium.list(),
                                          import: (name) => __scriptium.importScript(name),
                                        };

                                        function createStorageAPI(api) {
                                          return {
                                            load: (path, defaultValue) => {
                                              const json = api.load(path);
                                              return json === null ? defaultValue : JSON.parse(json);
                                            },
                                            save: (path, value) => {
                                              const json = JSON.stringify(value);
                                              if (json === undefined) {
                                                throw new TypeError("Settings must be JSON-serializable.");
                                              }
                                              api.save(path, json);
                                            },
                                          };
                                        }

                                        globalThis.storage = createStorageAPI(__storage);
                                        globalThis.__scriptiumStorageFactory = createStorageAPI;

                                        globalThis.keybinds = {
                                          register: (name, key, callback) => __keybinds.register(name, key, callback),
                                          changeKey: (keybind, key) => __keybinds.changeKey(keybind, key),
                                        };

                                        function wrapCommandCallback(callback) {
                                          return (javaCtx) => {
                                            const args = {};
                                            const entries = javaCtx.getArgs().entrySet().iterator();

                                            while (entries.hasNext()) {
                                              const entry = entries.next();
                                              args[entry.getKey()] = entry.getValue();
                                            }

                                            callback({
                                              args,
                                              reply: (message) => javaCtx.reply(message),
                                              error: (message) => javaCtx.error(message),
                                              success: (message) => javaCtx.success(message),
                                              warn: (message) => javaCtx.warn(message),
                                              getPlayerName: () => javaCtx.getPlayerName(),
                                            });
                                          };
                                        }

                                        globalThis.commands = {
                                          register: (name, definitionsOrCallback, maybeCallback) => {
                                            if (Array.isArray(definitionsOrCallback)) {
                                              return __commands.register(
                                                name,
                                                definitionsOrCallback,
                                                wrapCommandCallback(maybeCallback),
                                              );
                                            }

                                            return __commands.register(
                                              name,
                                              wrapCommandCallback(definitionsOrCallback),
                                            );
                                          },
                                        };

                                        function createInputAPI(api) {
                                          return {
                                            tap: (key, duration, restore) => {
                                              if (duration === undefined) api.tap(key);
                                              else if (restore === undefined) api.tap(key, duration);
                                              else api.tap(key, duration, restore);
                                            },
                                            press: (key) => api.press(key),
                                            release: (key) => api.release(key),
                                            isReallyDown: (key) => api.isReallyDown(key),
                                            isFiringSynthetic: () => api.isFiringSynthetic(),
                                            isDown: (key) => api.isDown(key),
                                            block: (key) => api.block(key),
                                            blockFor: (key, duration) => api.blockFor(key, duration),
                                            unblock: (key) => api.unblock(key),
                                            blockUntilRelease: (key) => api.blockUntilRelease(key),
                                            lockCursor: (x, y) => api.lockCursor(x, y),
                                            unlockCursor: () => api.unlockCursor(),
                                            releaseAll: () => api.releaseAll(),
                                            scope: (name) => createInputAPI(api.scope(name)),
                                          };
                                        }

                                        globalThis.input = createInputAPI(__input);
                                        globalThis.aim = __aim;

                                        globalThis.events = {
                                          on: (event, callback) => __events.on(event, callback),
                                          off: (event, callback) => __events.off(event, callback),
                                        };

                                        globalThis.gui = {
                                          create: (options) => __gui.create(options),
                                        };

                                        globalThis.keys = __keys;""");

                        Source source = Source.newBuilder("js", file.toFile()).build();

                        Script script = new Script(name, file, context);

                        scripts.put(name, script);
                        dependencies.put(name, new HashSet<>());

                        try {
                                context.eval(source);

                                Value onLoad = context.getBindings("js").getMember("onLoad");

                                if (onLoad != null && onLoad.canExecute()) {
                                        onLoad.execute();
                                }

                        } catch (Exception e) {
                                scripts.remove(name);
                                dependencies.remove(name);
                                removeDependencyInformation(name);

                                commandManager.removeScript(name);
                                keybindManager.removeScript(name);
                                inputManager.removeScript(name);
                                AimController.getInstance().releaseOwner(name);

                                eventManager.removeScript(name);

                                try {
                                        context.close();
                                } catch (Exception ignored) {
                                }

                                throw e;
                        }

                        ScriptiumLogger.info("[Scriptium] Loaded script: " + name);

                        return true;
                } catch (

                Exception e) {
                        ScriptiumLogger.error("[Scriptium] Failed to load script: " + name + ": " + e.getMessage());

                        return false;
                }
        }

        // ============================================================
        // Unloading
        // ============================================================

        public boolean unload(String name) {
                Script script = scripts.get(name);

                if (script == null) {
                        ScriptiumLogger.info("[Scriptium] Script is not loaded: " + name);

                        return false;
                }

                Set<String> users = dependents.getOrDefault(name, Collections.emptySet());

                if (!users.isEmpty()) {
                        ScriptiumLogger.info("[Scriptium] Cannot unload '" + name + "'.");
                        ScriptiumLogger.info("[Scriptium] Scripts depending on it:");

                        for (String user : users) {
                                ScriptiumLogger.info("  - " + user);
                        }

                        return false;
                }

                removeDependencyInformation(name);

                try {
                        Value onUnload = script.getContext().getBindings("js").getMember("onUnload");

                        if (onUnload != null && onUnload.canExecute()) {
                                onUnload.execute();
                        }

                } catch (Exception e) {
                        ScriptiumLogger.error("[Scriptium] onUnload() failed for " + name + ": " + e.getMessage());

                        e.printStackTrace();
                }

                commandManager.removeScript(name);
                keybindManager.removeScript(name);
                inputManager.removeScript(name);
                AimController.getInstance().releaseOwner(name);

                eventManager.removeScript(name);

                scripts.remove(name);
                script.unload();

                ScriptiumLogger.info("[Scriptium] Unloaded script: " + name);

                return true;
        }

        // ============================================================
        // Reloading
        // ============================================================

        public boolean reload(String name) {
                if (!scripts.containsKey(name)) {
                        return load(name);
                }

                Set<String> users = dependents.getOrDefault(name, Collections.emptySet());

                if (!users.isEmpty()) {
                        ScriptiumLogger.info(
                                        "[Scriptium] Cannot reload '" + name + "' while other scripts depend on it.");

                        return false;
                }

                if (!unload(name)) {
                        return false;
                }

                return load(name);
        }

        // ============================================================
        // Importing
        // ============================================================

        public Value importScript(String owner, String name) {
                Script ownerScript = scripts.get(owner);

                if (ownerScript == null) {
                        throw new RuntimeException("Owning script is not loaded: " + owner);
                }

                return importScript(owner, name, ownerScript.getFile().toString());
        }

        public Value importScript(String owner, String name, String importerPath) {
                Path file = getModuleFile(name, Path.of(importerPath));

                if (!Files.exists(file)) {
                        throw new RuntimeException("Script module not found: " + name);
                }

                Script ownerScript = scripts.get(owner);

                if (ownerScript == null) {
                        throw new RuntimeException("Owning script is not loaded: " + owner);
                }

                if (owner.equals(name)) {
                        throw new RuntimeException("Script cannot import itself: " + name);
                }

                try {
                        /*
                         * Create the CommonJS-style module object.
                         */
                        Value module = ownerScript.getContext().eval("js", "({ exports: {} })");

                        Value exports = module.getMember("exports");

                        /*
                         * JavaScript function used as require().
                         */
                        Value requireFactory = ownerScript.getContext().eval(
                                        "js",
                                        "(base) => (name) => __scriptium.importScriptFrom(name, base)");
                        Value require = requireFactory.execute(file.toAbsolutePath().normalize().toString());

                        /*
                         * Wrap the imported file inside:
                         *
                         * (module, exports, require, storage) => {
                         * ...script...
                         * }
                         */
                        String source = Files.readString(file);

                        String wrapped = """
                                        (function(module, exports, require, storage) {
                                        %s
                                        })
                                        """.formatted(source);

                        Value moduleFunction = ownerScript.getContext().eval("js", wrapped);

                        StorageAPI moduleStorage = new StorageAPI(this, file);
                        Value storageFactory = ownerScript.getContext().getBindings("js")
                                        .getMember("__scriptiumStorageFactory");
                        Value scopedStorage = storageFactory.execute(moduleStorage);

                        moduleFunction.execute(module, exports, require, scopedStorage);

                        addDependency(owner, name);

                        return module.getMember("exports");
                } catch (Exception e) {
                        throw new RuntimeException("Failed to import script module: " + name, e);
                }
        }

        // ============================================================
        // Dependencies
        // ============================================================

        private void addDependency(String owner, String dependency) {
                dependencies.computeIfAbsent(owner, ignored -> new HashSet<>()).add(dependency);
                dependents.computeIfAbsent(dependency, ignored -> new HashSet<>()).add(owner);
        }

        private void removeDependencyInformation(String script) {
                Set<String> scriptDependencies = dependencies.remove(script);

                if (scriptDependencies != null) {
                        for (String dependency : scriptDependencies) {
                                Set<String> users = dependents.get(dependency);

                                if (users != null) {
                                        users.remove(script);

                                        if (users.isEmpty()) {
                                                dependents.remove(dependency);
                                        }
                                }
                        }
                }

                dependents.remove(script);
        }

        // ============================================================
        // Queries
        // ============================================================

        public boolean isLoaded(String name) {
                return scripts.containsKey(name);
        }

        public Collection<Script> getScripts() {
                return Collections.unmodifiableCollection(scripts.values());
        }

        public List<String> getScriptNames() {
                return new ArrayList<>(scripts.keySet());
        }

        // ============================================================
        // Context
        // ============================================================

        private Context createContext(String scriptName) {
                Context context = Context.newBuilder("js")
                                .allowHostAccess(HostAccess.ALL)
                                .allowHostClassLookup(className -> true)
                                .build();

                return context;
        }

        // ============================================================
        // Files
        // ============================================================

        private Path getScriptFile(String name) {
                if (!name.endsWith(".js")) {
                        name = name + ".js";
                }

                return scriptsDirectory.resolve(name);
        }

        private Path getModuleFile(String name, Path importerFile) {
                if (!name.endsWith(".js")) {
                        name = name + ".js";
                }

                Path modulePath = Path.of(name);

                if (modulePath.isAbsolute()) {
                        return modulePath.normalize();
                }

                if (name.startsWith(".")) {
                        Path importerDirectory = importerFile.toAbsolutePath().normalize().getParent();
                        return importerDirectory.resolve(modulePath).normalize();
                }

                return scriptsDirectory.resolve(modulePath).normalize();
        }

        public Path resolveStorageFile(String name, Path importerFile) {
                Path storagePath = Path.of(name);

                if (storagePath.isAbsolute()) {
                        return storagePath.normalize();
                }

                if (name.startsWith(".")) {
                        Path importerDirectory = importerFile.toAbsolutePath().normalize().getParent();
                        return importerDirectory.resolve(storagePath).normalize();
                }

                return scriptsDirectory.resolve(storagePath).normalize();
        }
}