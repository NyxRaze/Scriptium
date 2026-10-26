package com.scriptium.scripting.api;

import com.scriptium.scripting.ScriptManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class StorageAPI {

    private final ScriptManager manager;
    private final Path importerFile;

    public StorageAPI(ScriptManager manager, Path importerFile) {
        this.manager = manager;
        this.importerFile = importerFile;
    }

    public String load(String path) {
        Path file = resolve(path);

        if (!Files.exists(file)) {
            return null;
        }

        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load settings from " + file, e);
        }
    }

    public void save(String path, String json) {
        if (json == null) {
            throw new IllegalArgumentException("Settings must be JSON-serializable.");
        }

        Path file = resolve(path);
        Path parent = file.getParent();

        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(file, json);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to save settings to " + file, e);
        }
    }

    private Path resolve(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Settings path must be non-blank.");
        }

        return manager.resolveStorageFile(path, importerFile);
    }
}
