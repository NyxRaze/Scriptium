package com.scriptium.logging;

import java.io.IOException;
import java.io.PrintWriter;

import java.nio.charset.StandardCharsets;

import java.nio.file.StandardOpenOption;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;

import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;

public final class ScriptiumLogger {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");

    /*
     * Number of previous session logs to keep.
     */
    private static final int MAX_ARCHIVED_LOGS = 7;

    private static final Object LOCK = new Object();

    private static Path logsDirectory;
    private static Path logFile;

    private static PrintWriter writer;

    private ScriptiumLogger() {
    }

    public static void initialize(Path gameDirectory) {
        synchronized (LOCK) {
            if (writer != null) {
                return;
            }

            try {
                logsDirectory = gameDirectory.resolve("logs");

                Files.createDirectories(logsDirectory);

                logFile = logsDirectory.resolve("scriptium-latest.log");

                rotateSession();

                info("========================================");
                info("Scriptium logger initialized.");
                info("========================================");

                cleanupOldLogs();

            } catch (IOException e) {
                throw new RuntimeException("Failed to initialize Scriptium logger", e);
            }
        }
    }

    private static void openWriter() throws IOException {
        writer = new PrintWriter(Files.newBufferedWriter(
                logFile,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND),
                true);
    }

    public static void info(String message) {
        log("INFO", "Scriptium", message);
    }

    public static void warn(String message) {
        log("WARN", "Scriptium", message);
    }

    public static void error(String message) {
        log("ERROR", "Scriptium", message);
    }

    public static void debug(String message) {
        log("DEBUG", "Scriptium", message);
    }

    public static ScriptLogger script(String scriptName) {
        return new ScriptLogger(scriptName);
    }

    private static void log(String level, String source, String message) {
        synchronized (LOCK) {
            if (writer == null) {
                return;
            }

            String timestamp = LocalDateTime.now().format(TIME_FORMAT);

            writer.printf("[%s] [%s] [%s] %s%n",
                    timestamp,
                    level,
                    source,
                    message);

            writer.flush();
        }
    }

    /*
     * Starts a new Scriptium log for the current Minecraft session.
     *
     * The previous scriptium-latest.log is archived.
     */
    private static void rotateSession() throws IOException {
        if (writer != null) {
            writer.flush();
            writer.close();
            writer = null;
        }

        if (Files.exists(logFile) && Files.size(logFile) > 0) {
            String timestamp = LocalDateTime.now().format(FILE_DATE_FORMAT);

            Path archivedFile = logsDirectory.resolve("scriptium-" + timestamp + ".log");

            int index = 1;

            while (Files.exists(archivedFile)) {
                archivedFile = logsDirectory.resolve("scriptium-" + timestamp + "-" + index + ".log");

                index++;
            }

            Files.move(logFile, archivedFile);
        }

        openWriter();
    }

    private static void cleanupOldLogs() {
        try {
            List<Path> archivedLogs = new ArrayList<>();

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(logsDirectory, "scriptium-*.log")) {
                for (Path path : stream) {
                    if (Files.isRegularFile(path)) {
                        archivedLogs.add(path);
                    }
                }
            }

            archivedLogs.sort(Comparator.comparing(ScriptiumLogger::getLastModified, Comparator.reverseOrder()));

            for (int i = MAX_ARCHIVED_LOGS; i < archivedLogs.size(); i++) {
                try {
                    Files.deleteIfExists(archivedLogs.get(i));
                } catch (IOException e) {
                    System.err.println("[Scriptium] Failed to delete old log: " + archivedLogs.get(i));
                }
            }
        } catch (IOException e) {
            System.err.println("[Scriptium] Failed to clean old logs: " + e.getMessage());
        }
    }

    private static long getLastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }

    public static void close() {
        synchronized (LOCK) {
            if (writer != null) {
                writer.flush();
                writer.close();

                writer = null;
            }
        }
    }

    public static Path getLogFile() {
        return logFile;
    }

    public static final class ScriptLogger {

        private final String scriptName;

        private ScriptLogger(String scriptName) {
            this.scriptName = scriptName;
        }

        public void info(String message) {
            log("INFO", "Script:" + scriptName, message);
        }

        public void warn(String message) {
            log("WARN", "Script:" + scriptName, message);
        }

        public void error(String message) {
            log("ERROR", "Script:" + scriptName, message);
        }

        public void debug(String message) {
            log("DEBUG", "Script:" + scriptName, message);
        }
    }
}