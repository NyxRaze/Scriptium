package com.scriptium.scripting.api;

import org.graalvm.polyglot.Value;
import org.jspecify.annotations.NonNull;

import com.scriptium.logging.ScriptiumLogger;
import com.scriptium.scripting.gui.ScriptiumGuiScreen;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

public final class GuiAPI {

    /**
     * create({ title, subtitle, footer, width, height, columns,
     * background, panel, accent, padding, gap })
     */
    public GuiScreen create(Value options) {
        String title = getString(options, "title", "Scriptium");
        String subtitle = getString(options, "subtitle", "");
        String footer = getString(options, "footer", "");
        int width = getInt(options, "width", 620);
        int height = getInt(options, "height", 360);
        int columns = getInt(options, "columns", 2);
        int backdrop = getColor(options, "background", "#8C06070B");
        int panel = getColor(options, "panel", "#15171E");
        int accent = getColor(options, "accent", "#5B8CFF");
        int padding = getInt(options, "padding", 16);
        int gap = getInt(options, "gap", 8);

        return new GuiScreen(title, subtitle, footer, width, height, columns, backdrop, panel, accent, padding, gap);
    }

    public static final class GuiScreen {

        private final ScriptiumGuiScreen screen;
        private final int panel;
        private final int accent;

        private GuiScreen(
                String title,
                String subtitle,
                String footer,
                int width,
                int height,
                int columns,
                int backdrop,
                int panel,
                int accent,
                int padding,
                int gap) {
            this.panel = panel;
            this.accent = accent;
            this.screen = new ScriptiumGuiScreen(
                    title, subtitle, footer, width, height, columns, backdrop, panel, accent, padding, gap);
        }

        /**
         * tab({ name, description, icon }) or tab("Name").
         * Everything added afterwards goes into this tab.
         */
        public GuiScreen tab(Value options) {
            String name;

            if (options != null && options.isString()) {
                name = options.asString();
            } else {
                name = getString(options, "name", "Tab");
            }

            screen.addTab(
                    name,
                    getString(options, "description", ""),
                    getString(options, "icon", ""));
            return this;
        }

        /** label({ text, description, color }) */
        public GuiScreen label(Value options) {
            screen.addLabel(
                    getString(options, "text", ""),
                    getString(options, "description", ""),
                    getColor(options, "color", "#A8B2C5"),
                    getString(options, "id", ""),
                    getCallback(options, "visibleWhen"));
            return this;
        }

        /**
         * heading({ text, description, color }) - small caps section title with a rule.
         */
        public GuiScreen heading(Value options) {
            screen.addHeading(
                    getString(options, "text", ""),
                    getString(options, "description", ""),
                    getColor(options, "color", "#9AA6BA"),
                    getString(options, "id", ""),
                    getCallback(options, "visibleWhen"));
            return this;
        }

        /**
         * button({ text, description, variant, color, background, inline, onClick })
         * variant: "default" | "primary" | "danger" | "success" | "ghost"
         * inline: true places it in the card grid instead of full width.
         */
        public GuiScreen button(Value options) {
            String variant = getString(options, "variant", "default").toLowerCase(Locale.ROOT);

            int defaultBackground;
            int defaultText = 0xFFFFFFFF;

            switch (variant) {
                case "primary" -> defaultBackground = accent;
                case "danger" -> defaultBackground = 0xFFD94F5C;
                case "success" -> defaultBackground = 0xFF2FA37A;
                case "ghost" -> {
                    defaultBackground = 0x00000000;
                    defaultText = 0xFFDDE3EE;
                }
                default -> {
                    defaultBackground = blend(panel, 0xFFFFFFFF, 0.10f);
                    defaultText = 0xFFE6EAF2;
                }
            }

            screen.addButton(
                    getString(options, "text", "Button"),
                    getString(options, "description", ""),
                    getColor(options, "color", defaultText),
                    getColor(options, "background", defaultBackground),
                    getBoolean(options, "inline", false),
                    options == null ? null : options.getMember("onClick"),
                    getString(options, "id", ""),
                    getCallback(options, "visibleWhen"));
            return this;
        }

        /**
         * toggle({ text, description, value, inline, onChange(newValue) })
         * A compact switch row.
         */
        public GuiScreen toggle(Value options) {
            screen.addCard(
                    getString(options, "text", "Toggle"),
                    getString(options, "description", ""),
                    getColor(options, "color", "#E6EAF2"),
                    false,
                    true,
                    getBoolean(options, "value", false),
                    getBoolean(options, "inline", false),
                    options == null ? null : options.getMember("onChange"),
                    false,
                    getString(options, "id", ""),
                    getCallback(options, "visibleWhen"));
            return this;
        }

        /**
         * card({ title, description, value, onChange, onClick, inline, settings })
         * With a boolean "value" it is a module card with a switch
         * (onChange(newValue)).
         * Without it, it is an action card (onClick). Cards flow in the grid by
         * default.
         *
         * settings (optional): a function (panel) => { ... } that fills the card's
         * settings page,
         * which opens on right click. The panel has heading, label, toggle, slider,
         * range,
         * button,
         * separator and spacer. IDs in the settings panel are prefixed with the
         * card ID when one is provided.
         */
        public GuiScreen card(Value options) {
            Value valueMember = member(options, "value");
            boolean toggleable = valueMember != null && valueMember.isBoolean();

            String title = getString(options, "title", getString(options, "text", "Card"));
            String id = getString(options, "id", "");

            @SuppressWarnings("null")
            @NonNull
            Value settingsFn = member(options, "settings");
            boolean hasSettings = settingsFn != null && settingsFn.canExecute();

            ScriptiumGuiScreen.Settings settings = screen.addCard(
                    title,
                    getString(options, "description", ""),
                    getColor(options, "color", "#FFFFFF"),
                    true,
                    toggleable,
                    getBoolean(options, "value", false),
                    getBoolean(options, "inline", true),
                    options == null ? null : options.getMember(toggleable ? "onChange" : "onClick"),
                    hasSettings,
                    id,
                    getCallback(options, "visibleWhen"));

            if (settings != null) {
                try {
                    settingsFn.execute(new SettingsPanel(settings, panel, accent, id));
                } catch (Exception e) {
                    ScriptiumLogger.error("GUI card settings failed: " + e.getMessage());
                }
            }

            return this;
        }

        /**
         * slider({ text, description, min, max, value, step, inline, onChange(newValue)
         * })
         */
        public GuiScreen slider(Value options) {
            double min = getDouble(options, "min", 0);
            double max = getDouble(options, "max", 100);

            screen.addSlider(
                    getString(options, "text", "Slider"),
                    getString(options, "description", ""),
                    getColor(options, "color", "#E6EAF2"),
                    min,
                    max,
                    getDouble(options, "value", min),
                    getDouble(options, "step", 1),
                    getBoolean(options, "inline", false),
                    options == null ? null : options.getMember("onChange"),
                    getString(options, "id", ""),
                    getCallback(options, "visibleWhen"));
            return this;
        }

        /**
         * setValue(id, value) updates an identified toggle, slider, range, enum or
         * keybind.
         */
        public GuiScreen setValue(String id, Value value) {
            screen.setValue(id, value);
            return this;
        }

        /**
         * getValue(id) reads an identified setting value. Range values are returned as
         * [low, high].
         */
        public Object getValue(String id) {
            return screen.getValue(id);
        }

        /**
         * getRandomValue(id) returns a random number within an identified range
         * setting, including its maximum.
         */
        public double getRandomValue(String id) {
            return screen.getRandomValue(id);
        }

        public GuiScreen separator() {
            screen.addSeparator("", null);
            return this;
        }

        public GuiScreen separator(Value options) {
            screen.addSeparator(
                    getString(options, "id", ""),
                    getCallback(options, "visibleWhen"));
            return this;
        }

        public GuiScreen spacer() {
            screen.addSpacer(8, "", null);
            return this;
        }

        public GuiScreen spacer(int height) {
            screen.addSpacer(height, "", null);
            return this;
        }

        public GuiScreen spacer(Value options) {
            screen.addSpacer(
                    getInt(options, "height", 8),
                    getString(options, "id", ""),
                    getCallback(options, "visibleWhen"));
            return this;
        }

        public void open() {
            Minecraft.getInstance().execute(() -> Minecraft.getInstance().gui.setScreen(screen));
        }

        public void close() {
            Minecraft.getInstance().execute(() -> {
                if (Minecraft.getInstance().gui.screen() == screen) {
                    Minecraft.getInstance().gui.setScreen(null);
                }
            });
        }
    }

    /**
     * The object a card's settings function receives. Every method returns the
     * panel for chaining.
     */
    public static final class SettingsPanel {

        private final ScriptiumGuiScreen.Settings settings;
        private final int panel;
        private final int accent;
        private final String cardId;

        private SettingsPanel(ScriptiumGuiScreen.Settings settings, int panel, int accent, String cardId) {
            this.settings = settings;
            this.panel = panel;
            this.accent = accent;
            this.cardId = cardId == null ? "" : cardId.trim();
        }

        private String componentId(Value options) {
            String id = getString(options, "id", "").trim();
            return id.isEmpty() || cardId.isEmpty() ? id : cardId + "_" + id;
        }

        /** heading({ text, description, color }) */
        public SettingsPanel heading(Value options) {
            settings.addHeading(
                    getString(options, "text", ""),
                    getString(options, "description", ""),
                    getColor(options, "color", "#9AA6BA"),
                    componentId(options),
                    getCallback(options, "visibleWhen"));
            return this;
        }

        /** label({ text, description, color }) */
        public SettingsPanel label(Value options) {
            settings.addLabel(
                    getString(options, "text", ""),
                    getString(options, "description", ""),
                    getColor(options, "color", "#A8B2C5"),
                    componentId(options),
                    getCallback(options, "visibleWhen"));
            return this;
        }

        /** toggle({ text, description, value, onChange(newValue) }) */
        public SettingsPanel toggle(Value options) {
            settings.addToggle(
                    getString(options, "text", "Toggle"),
                    getString(options, "description", ""),
                    getColor(options, "color", "#E6EAF2"),
                    getBoolean(options, "value", false),
                    options == null ? null : options.getMember("onChange"),
                    componentId(options),
                    getCallback(options, "visibleWhen"));
            return this;
        }

        /** slider({ text, description, min, max, value, step, onChange(newValue) }) */
        public SettingsPanel slider(Value options) {
            double min = getDouble(options, "min", 0);
            double max = getDouble(options, "max", 100);

            settings.addSlider(
                    getString(options, "text", "Slider"),
                    getString(options, "description", ""),
                    getColor(options, "color", "#E6EAF2"),
                    min,
                    max,
                    getDouble(options, "value", min),
                    getDouble(options, "step", 1),
                    options == null ? null : options.getMember("onChange"),
                    componentId(options),
                    getCallback(options, "visibleWhen"));
            return this;
        }

        /**
         * range({ text, description, min, max, value: [low, high], step, onChange(low,
         * high) })
         */
        public SettingsPanel range(Value options) {
            double min = getDouble(options, "min", 0);
            double max = getDouble(options, "max", 100);
            Value initial = member(options, "value");
            double low = Math.min(min, max);
            double high = Math.max(min, max);

            if (initial != null) {
                if (!initial.hasArrayElements() || initial.getArraySize() != 2) {
                    throw new IllegalArgumentException("Range value must be an array of two finite numbers.");
                }

                Value lowValue = initial.getArrayElement(0);
                Value highValue = initial.getArrayElement(1);
                if (lowValue == null || highValue == null || !lowValue.isNumber() || !highValue.isNumber()
                        || !Double.isFinite(lowValue.asDouble()) || !Double.isFinite(highValue.asDouble())) {
                    throw new IllegalArgumentException("Range value must be an array of two finite numbers.");
                }

                low = lowValue.asDouble();
                high = highValue.asDouble();
            }

            if (!Double.isFinite(min) || !Double.isFinite(max)
                    || !Double.isFinite(low) || !Double.isFinite(high) || low > high) {
                throw new IllegalArgumentException("Range bounds and values must be finite, with low <= high.");
            }

            double step = getDouble(options, "step", 1);
            if (!Double.isFinite(step) || step < 0) {
                throw new IllegalArgumentException("Range step must be a finite non-negative number.");
            }

            settings.addRange(
                    getString(options, "text", "Range"),
                    getString(options, "description", ""),
                    getColor(options, "color", "#E6EAF2"),
                    min,
                    max,
                    low,
                    high,
                    step,
                    options == null ? null : options.getMember("onChange"),
                    componentId(options),
                    getCallback(options, "visibleWhen"));
            return this;
        }

        /** enum({ text, description, options, value, onChange(newValue) }) */
        public SettingsPanel enumeration(Value options) {
            Value optionsValue = member(options, "options");

            if (optionsValue == null || !optionsValue.hasArrayElements()
                    || optionsValue.getArraySize() == 0 || optionsValue.getArraySize() > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Enum options must be a non-empty array of strings.");
            }

            String[] values = new String[(int) optionsValue.getArraySize()];

            for (int i = 0; i < values.length; i++) {
                Value option = optionsValue.getArrayElement(i);

                if (option == null || !option.isString()) {
                    throw new IllegalArgumentException("Enum options must be a non-empty array of strings.");
                }

                values[i] = option.asString();

                for (int previous = 0; previous < i; previous++) {
                    if (values[previous].equals(values[i])) {
                        throw new IllegalArgumentException("Enum options must be unique.");
                    }
                }
            }

            Value initialValue = member(options, "value");
            String selected = initialValue == null ? values[0]
                    : initialValue.isString() ? initialValue.asString() : null;

            if (selected == null) {
                throw new IllegalArgumentException("Enum value must be one of its options.");
            }

            settings.addEnum(
                    getString(options, "text", "Enum"),
                    getString(options, "description", ""),
                    getColor(options, "color", "#E6EAF2"),
                    values,
                    selected,
                    options == null ? null : options.getMember("onChange"),
                    componentId(options),
                    getCallback(options, "visibleWhen"));
            return this;
        }

        /** keybind({ text, description, value, onChange(newKeyCode) }) */
        public SettingsPanel keybind(Value options) {
            settings.addKeybind(
                    getString(options, "text", "Keybind"),
                    getString(options, "description", ""),
                    getColor(options, "color", "#E6EAF2"),
                    getKeyCode(options, "value", -1),
                    options == null ? null : options.getMember("onChange"),
                    componentId(options),
                    getCallback(options, "visibleWhen"));
            return this;
        }

        /** button({ text, description, variant, color, background, onClick }) */
        public SettingsPanel button(Value options) {
            String variant = getString(options, "variant", "default").toLowerCase(Locale.ROOT);

            int background;
            int text = 0xFFFFFFFF;

            switch (variant) {
                case "primary" -> background = accent;
                case "danger" -> background = 0xFFD94F5C;
                case "success" -> background = 0xFF2FA37A;
                case "ghost" -> {
                    background = 0x00000000;
                    text = 0xFFDDE3EE;
                }
                default -> {
                    background = blend(panel, 0xFFFFFFFF, 0.10f);
                    text = 0xFFE6EAF2;
                }
            }

            settings.addButton(
                    getString(options, "text", "Button"),
                    getString(options, "description", ""),
                    getColor(options, "color", text),
                    getColor(options, "background", background),
                    options == null ? null : options.getMember("onClick"),
                    componentId(options),
                    getCallback(options, "visibleWhen"));
            return this;
        }

        public SettingsPanel separator() {
            settings.addSeparator("", null);
            return this;
        }

        public SettingsPanel separator(Value options) {
            settings.addSeparator(
                    componentId(options),
                    getCallback(options, "visibleWhen"));
            return this;
        }

        public SettingsPanel spacer() {
            settings.addSpacer(8, "", null);
            return this;
        }

        public SettingsPanel spacer(int height) {
            settings.addSpacer(height, "", null);
            return this;
        }

        public SettingsPanel spacer(Value options) {
            settings.addSpacer(
                    getInt(options, "height", 8),
                    componentId(options),
                    getCallback(options, "visibleWhen"));
            return this;
        }
    }

    // ---------------------------------------------------------------- options

    private static Value member(Value options, String key) {
        if (options == null || !options.hasMembers() || !options.hasMember(key)) {
            return null;
        }

        Value value = options.getMember(key);

        return value == null || value.isNull() ? null : value;
    }

    private static Value getCallback(Value options, String key) {
        Value value = member(options, key);

        if (value != null && !value.canExecute()) {
            throw new IllegalArgumentException(key + " must be a function.");
        }

        return value;
    }

    private static String getString(Value options, String key, String fallback) {
        Value value = member(options, key);

        if (value == null) {
            return fallback;
        }

        return value.isString() ? value.asString() : value.toString();
    }

    private static int getInt(Value options, String key, int fallback) {
        Value value = member(options, key);

        return value != null && value.isNumber() ? (int) value.asDouble() : fallback;
    }

    private static int getKeyCode(Value options, String key, int fallback) {
        Value value = member(options, key);

        if (value == null) {
            return fallback;
        }

        if (!value.isNumber() || !Double.isFinite(value.asDouble())
                || value.asDouble() != Math.rint(value.asDouble())
                || value.asDouble() < GLFW.GLFW_KEY_UNKNOWN || value.asDouble() > GLFW.GLFW_KEY_LAST) {
            throw new IllegalArgumentException("Keybind value must be an integer key code.");
        }

        return (int) value.asDouble();
    }

    private static double getDouble(Value options, String key, double fallback) {
        Value value = member(options, key);

        return value != null && value.isNumber() ? value.asDouble() : fallback;
    }

    private static boolean getBoolean(Value options, String key, boolean fallback) {
        Value value = member(options, key);

        return value != null && value.isBoolean() ? value.asBoolean() : fallback;
    }

    /**
     * Accepts "#RGB", "#RRGGBB", "#AARRGGBB" strings or numbers (0xRRGGBB /
     * 0xAARRGGBB).
     */
    private static int getColor(Value options, String key, int fallback) {
        Value value = member(options, key);

        if (value == null) {
            return fallback;
        }

        if (value.isNumber()) {
            long n = value.asLong();

            if ((n >>> 24) == 0) {
                n |= 0xFF000000L;
            }

            return (int) n;
        }

        if (value.isString()) {
            Integer parsed = parseColor(value.asString());

            if (parsed != null) {
                return parsed;
            }
        }

        return fallback;
    }

    private static int getColor(Value options, String key, String fallbackHex) {
        Integer fallback = parseColor(fallbackHex);

        return getColor(options, key, fallback == null ? 0xFFFFFFFF : fallback);
    }

    private static Integer parseColor(String value) {
        if (value == null) {
            return null;
        }

        String hex = value.trim();

        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }

        try {
            if (hex.length() == 3) {
                hex = "" + hex.charAt(0) + hex.charAt(0)
                        + hex.charAt(1) + hex.charAt(1)
                        + hex.charAt(2) + hex.charAt(2);
            }

            if (hex.length() == 6) {
                return 0xFF000000 | Integer.parseInt(hex, 16);
            }

            if (hex.length() == 8) {
                return (int) Long.parseLong(hex, 16);
            }
        } catch (NumberFormatException ignored) {
            // Fall through.
        }

        return null;
    }

    private static int blend(int from, int to, float t) {
        int r = Math.round(((from >>> 16) & 0xFF) * (1f - t) + ((to >>> 16) & 0xFF) * t);
        int g = Math.round(((from >>> 8) & 0xFF) * (1f - t) + ((to >>> 8) & 0xFF) * t);
        int b = Math.round((from & 0xFF) * (1f - t) + (to & 0xFF) * t);

        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}