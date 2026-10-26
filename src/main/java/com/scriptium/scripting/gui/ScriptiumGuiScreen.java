package com.scriptium.scripting.gui;

import com.scriptium.logging.ScriptiumLogger;

import com.mojang.blaze3d.platform.InputConstants;
import org.graalvm.polyglot.Value;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Lunar-style window: sidebar with tabs on the left, header + scrollable
 * content (cards in a grid, sliders, buttons...) on the right.
 */
@SuppressWarnings("null")
public final class ScriptiumGuiScreen extends Screen {

    // ------------------------------------------------------------------ style

    private static final int RADIUS = 8;
    private static final int SIDEBAR_WIDTH = 140;
    private static final int TAB_HEIGHT = 26;
    private static final int TAB_GAP = 3;
    private static final int CLOSE_SIZE = 22;
    private static final int SCROLLBAR_GUTTER = 8;

    private static final int BUTTON_HEIGHT = 30;
    private static final int TOGGLE_HEIGHT = 30;
    private static final int CARD_HEIGHT = 54;
    private static final int SLIDER_HEIGHT = 40;

    private static final int WHITE = 0xFFFFFFFF;
    private static final int BLACK = 0xFF000000;
    private static final int MUTED = 0xFF8791A5;

    private static final int TL = 1;
    private static final int TR = 2;
    private static final int BL = 4;
    private static final int BR = 8;
    private static final int ALL = 15;

    // ----------------------------------------------------------------- config

    private final String screenTitle;
    private final String subtitle;
    private final String footer;
    private final int requestedWidth;
    private final int requestedHeight;
    private final int columns;
    private final int backdropColor;
    private final int panelColor;
    private final int accentColor;
    private final int padding;
    private final int gap;

    // derived colors
    private final int sidebarColor;
    private final int cardColor;
    private final int cardHoverColor;
    private final int borderColor;
    private final int softBorderColor;
    private final int offTrackColor;

    private final List<Tab> tabs = new ArrayList<>();
    private final Map<String, Element> elementsById = new HashMap<>();
    private int activeIndex;

    // ----------------------------------------------------------------- layout

    private int winX;
    private int winY;
    private int winW;
    private int winH;
    private int sidebarW;
    private int headerH;

    private int viewX;
    private int viewW;
    private int viewTop;
    private int viewBottom;

    private int closeX;
    private int closeY;

    // ------------------------------------------------------------------ state

    private float closeHover;
    private float fade = 1f;
    private float layerFade = 1f;
    private long openStart;
    private long tabStart;
    private long lastFrame;

    private int clipTop = Integer.MIN_VALUE;
    private int clipBottom = Integer.MAX_VALUE;

    private Element dragging;
    private boolean draggingRangeUpper;
    private Element keybindCapture;

    /**
     * The card settings page currently shown instead of the active tab (null =
     * normal view).
     */
    private Tab activeSettings;
    private float backHover;

    public ScriptiumGuiScreen(
            String title,
            String subtitle,
            String footer,
            int width,
            int height,
            int columns,
            int backdropColor,
            int panelColor,
            int accentColor,
            int padding,
            int gap) {
        super(Component.literal(title));

        this.screenTitle = title;
        this.subtitle = subtitle == null ? "" : subtitle;
        this.footer = footer == null ? "" : footer;
        this.requestedWidth = Math.max(260, width);
        this.requestedHeight = Math.max(160, height);
        this.columns = Math.max(1, Math.min(4, columns));
        this.backdropColor = backdropColor;
        this.panelColor = panelColor;
        this.accentColor = accentColor;
        this.padding = Math.max(4, padding);
        this.gap = Math.max(0, gap);

        this.sidebarColor = mix(panelColor, BLACK, 0.35f);
        this.cardColor = mix(panelColor, WHITE, 0.05f);
        this.cardHoverColor = mix(panelColor, WHITE, 0.09f);
        this.borderColor = mix(panelColor, WHITE, 0.10f);
        this.softBorderColor = mix(panelColor, WHITE, 0.20f);
        this.offTrackColor = mix(panelColor, WHITE, 0.18f);
    }

    // ------------------------------------------------------------ element API

    public void addTab(String name, String description, String icon) {
        tabs.add(new Tab(name, description, icon));
        activeIndex = Math.min(activeIndex, tabs.size() - 1);
    }

    private Tab currentTab() {
        if (tabs.isEmpty()) {
            addTab("General", "", "");
        }

        return tabs.get(tabs.size() - 1);
    }

    public void addLabel(String text, String description, int color, String id, Value visibleWhen) {
        Element e = new Element(ElementType.LABEL, text, color);
        e.desc = description == null ? "" : description;
        addElement(currentTab(), e, id, visibleWhen);
    }

    public void addHeading(String text, String description, int color, String id, Value visibleWhen) {
        Element e = new Element(ElementType.HEADING, text, color);
        e.desc = description == null ? "" : description;
        addElement(currentTab(), e, id, visibleWhen);
    }

    public void addButton(
            String text,
            String description,
            int color,
            int buttonColor,
            boolean inline,
            Value callback,
            String id,
            Value visibleWhen) {
        if (callback == null || !callback.canExecute()) {
            throw new IllegalArgumentException("Button onClick must be a function.");
        }

        Element e = new Element(ElementType.BUTTON, text, color);
        e.desc = description == null ? "" : description;
        e.buttonColor = buttonColor;
        e.callback = callback;
        e.inline = inline;
        addElement(currentTab(), e, id, visibleWhen);
    }

    /**
     * Switch row when toggleable, or an action card otherwise.
     * Returns a {@link Settings} builder when {@code withSettings} is true (right
     * click opens it),
     * otherwise null.
     */
    public Settings addCard(
            String title,
            String description,
            int color,
            boolean cardComponent,
            boolean toggleable,
            boolean initial,
            boolean inline,
            Value callback,
            boolean withSettings,
            String id,
            Value visibleWhen) {
        Element e = new Element(ElementType.CARD, title, color);
        e.desc = description == null ? "" : description;
        e.cardComponent = cardComponent;
        e.toggleable = toggleable;
        e.on = toggleable && initial;
        e.anim = e.on ? 1f : 0f;
        e.inline = inline;
        e.callback = callback != null && callback.canExecute() ? callback : null;

        if (withSettings) {
            e.settings = new Tab(title, "Settings", "");
        }

        addElement(currentTab(), e, id, visibleWhen);

        return withSettings ? new Settings(this, e.settings) : null;
    }

    private void addElement(Tab tab, Element element, String id, Value visibleWhen) {
        element.visibleWhen = visibleWhen;
        String normalizedId = id == null ? "" : id.trim();

        if (!normalizedId.isEmpty() && elementsById.putIfAbsent(normalizedId, element) != null) {
            throw new IllegalArgumentException("Duplicate GUI component id: " + normalizedId);
        }

        tab.elements.add(element);
    }

    public Object getValue(String id) {
        Element element = getValueElement(id);

        return switch (element.type) {
            case CARD -> element.on;
            case ENUM -> element.enumValue;
            case KEYBIND -> element.keyCode;
            case RANGE -> new double[] { element.rangeMin, element.rangeMax };
            default -> element.value;
        };
    }

    /**
     * Returns a random value within the identified range, including both endpoints.
     */
    public double getRandomValue(String id) {
        Element element = getValueElement(id);

        if (element.type != ElementType.RANGE) {
            throw new IllegalArgumentException("GUI component '" + id + "' is not a range.");
        }

        if (element.rangeMin >= element.rangeMax) {
            return element.rangeMin;
        }

        return ThreadLocalRandom.current().nextDouble(
                element.rangeMin,
                Math.nextUp(element.rangeMax));
    }

    public void setValue(String id, Value value) {
        Element element = getValueElement(id);
        boolean changed;
        Object newValue;

        switch (element.type) {
            case CARD -> {
                if (value == null || !value.isBoolean()) {
                    throw new IllegalArgumentException("Value for GUI component '" + id + "' must be boolean.");
                }

                boolean next = value.asBoolean();
                changed = element.on != next;
                element.on = next;
                newValue = next;
            }
            case SLIDER -> {
                if (value == null || !value.isNumber() || !Double.isFinite(value.asDouble())) {
                    throw new IllegalArgumentException("Value for GUI component '" + id + "' must be a finite number.");
                }

                double next = Math.max(element.min, Math.min(element.max, value.asDouble()));

                if (element.step > 0) {
                    next = element.min + Math.round((next - element.min) / element.step) * element.step;
                }

                next = Math.max(element.min, Math.min(element.max, next));
                next = Math.round(next * 1_000_000d) / 1_000_000d;
                changed = element.value != next;
                element.value = next;
                newValue = next;
            }
            case RANGE -> {
                double[] next = getRangeValue(id, value);
                if (next[0] > next[1]) {
                    throw new IllegalArgumentException(
                            "Range value for GUI component '" + id + "' must have low <= high.");
                }

                next[0] = normalizeRangeValue(element, next[0]);
                next[1] = normalizeRangeValue(element, next[1]);
                changed = element.rangeMin != next[0] || element.rangeMax != next[1];
                element.rangeMin = next[0];
                element.rangeMax = next[1];
                newValue = next;
            }
            case ENUM -> {
                if (value == null || !value.isString()) {
                    throw new IllegalArgumentException("Value for GUI component '" + id + "' must be a string option.");
                }

                String next = value.asString();

                if (!element.enumOptions.contains(next)) {
                    throw new IllegalArgumentException("Unknown enum option for GUI component '" + id + "': " + next);
                }

                changed = !element.enumValue.equals(next);
                element.enumValue = next;
                newValue = next;
            }
            case KEYBIND -> {
                if (value == null || !value.isNumber() || !Double.isFinite(value.asDouble())
                        || value.asDouble() != Math.rint(value.asDouble())
                        || value.asDouble() < GLFW.GLFW_KEY_UNKNOWN || value.asDouble() > GLFW.GLFW_KEY_LAST) {
                    throw new IllegalArgumentException(
                            "Value for GUI component '" + id + "' must be a valid key code.");
                }

                int next = (int) value.asDouble();
                changed = element.keyCode != next;
                element.keyCode = next;
                newValue = next;
                if (keybindCapture == element) {
                    keybindCapture = null;
                }
            }
            default -> throw new IllegalArgumentException("GUI component '" + id + "' does not have a value.");
        }

        if (changed) {
            if (element.type == ElementType.RANGE) {
                notifyRangeChanged(element);
            } else {
                notifyValueChanged(element, newValue, "GUI callback failed: ");
            }
        }
    }

    private static double[] getRangeValue(String id, Value value) {
        if (value == null || !value.hasArrayElements() || value.getArraySize() != 2) {
            throw new IllegalArgumentException(
                    "Value for GUI component '" + id + "' must be an array of two finite numbers.");
        }

        Value low = value.getArrayElement(0);
        Value high = value.getArrayElement(1);
        if (low == null || high == null || !low.isNumber() || !high.isNumber()
                || !Double.isFinite(low.asDouble()) || !Double.isFinite(high.asDouble())) {
            throw new IllegalArgumentException(
                    "Value for GUI component '" + id + "' must be an array of two finite numbers.");
        }

        return new double[] { low.asDouble(), high.asDouble() };
    }

    private Element getValueElement(String id) {
        Element element = elementsById.get(id);

        if (element == null) {
            throw new IllegalArgumentException("Unknown GUI component id: " + id);
        }

        if (element.type != ElementType.CARD
                && element.type != ElementType.SLIDER
                && element.type != ElementType.RANGE
                && element.type != ElementType.ENUM
                && element.type != ElementType.KEYBIND) {
            throw new IllegalArgumentException("GUI component '" + id + "' does not have a value.");
        }

        if (element.type == ElementType.CARD && !element.toggleable) {
            throw new IllegalArgumentException("GUI component '" + id + "' is not a toggle.");
        }

        return element;
    }

    /** Builder for the elements inside a card's settings page. */
    public static final class Settings {
        private final ScriptiumGuiScreen owner;
        private final Tab tab;

        private Settings(ScriptiumGuiScreen owner, Tab tab) {
            this.owner = owner;
            this.tab = tab;
        }

        public void addLabel(String text, String description, int color, String id, Value visibleWhen) {
            Element e = new Element(ElementType.LABEL, text, color);
            e.desc = description == null ? "" : description;
            owner.addElement(tab, e, id, visibleWhen);
        }

        public void addRange(
                String text,
                String description,
                int color,
                double min,
                double max,
                double low,
                double high,
                double step,
                Value callback,
                String id,
                Value visibleWhen) {
            Element e = new Element(ElementType.RANGE, text, color);
            e.desc = description == null ? "" : description;
            e.min = Math.min(min, max);
            e.max = Math.max(min, max);
            e.step = step;
            e.rangeMin = normalizeRangeValue(e, low);
            e.rangeMax = normalizeRangeValue(e, high);
            e.callback = callback != null && callback.canExecute() ? callback : null;
            owner.addElement(tab, e, id, visibleWhen);
        }

        public void addHeading(String text, String description, int color, String id, Value visibleWhen) {
            Element e = new Element(ElementType.HEADING, text, color);
            e.desc = description == null ? "" : description;
            owner.addElement(tab, e, id, visibleWhen);
        }

        public void addButton(
                String text,
                String description,
                int color,
                int buttonColor,
                Value callback,
                String id,
                Value visibleWhen) {
            if (callback == null || !callback.canExecute()) {
                throw new IllegalArgumentException("Button onClick must be a function.");
            }

            Element e = new Element(ElementType.BUTTON, text, color);
            e.desc = description == null ? "" : description;
            e.buttonColor = buttonColor;
            e.callback = callback;
            owner.addElement(tab, e, id, visibleWhen);
        }

        public void addToggle(
                String text,
                String description,
                int color,
                boolean initial,
                Value callback,
                String id,
                Value visibleWhen) {
            Element e = new Element(ElementType.CARD, text, color);
            e.desc = description == null ? "" : description;
            e.toggleable = true;
            e.on = initial;
            e.anim = initial ? 1f : 0f;
            e.callback = callback != null && callback.canExecute() ? callback : null;
            owner.addElement(tab, e, id, visibleWhen);
        }

        public void addSlider(
                String text,
                String description,
                int color,
                double min,
                double max,
                double value,
                double step,
                Value callback,
                String id,
                Value visibleWhen) {
            Element e = new Element(ElementType.SLIDER, text, color);
            e.desc = description == null ? "" : description;
            e.min = Math.min(min, max);
            e.max = Math.max(min, max);
            e.step = Math.max(0, step);
            e.value = Math.max(e.min, Math.min(e.max, value));
            e.callback = callback != null && callback.canExecute() ? callback : null;
            owner.addElement(tab, e, id, visibleWhen);
        }

        public void addEnum(
                String text,
                String description,
                int color,
                String[] options,
                String initial,
                Value callback,
                String id,
                Value visibleWhen) {
            if (options == null || options.length == 0 || !List.of(options).contains(initial)) {
                throw new IllegalArgumentException("Enum value must be one of its options.");
            }

            Element e = new Element(ElementType.ENUM, text, color);
            e.desc = description == null ? "" : description;
            e.enumOptions = List.of(options);
            e.enumValue = initial;
            e.callback = callback != null && callback.canExecute() ? callback : null;
            owner.addElement(tab, e, id, visibleWhen);
        }

        public void addKeybind(
                String text,
                String description,
                int color,
                int keyCode,
                Value callback,
                String id,
                Value visibleWhen) {
            validateKeyCode(keyCode);

            Element e = new Element(ElementType.KEYBIND, text, color);
            e.desc = description == null ? "" : description;
            e.keyCode = keyCode;
            e.callback = callback != null && callback.canExecute() ? callback : null;
            owner.addElement(tab, e, id, visibleWhen);
        }

        public void addSeparator(String id, Value visibleWhen) {
            owner.addElement(tab, new Element(ElementType.SEPARATOR, "", 0), id, visibleWhen);
        }

        public void addSpacer(int height, String id, Value visibleWhen) {
            Element e = new Element(ElementType.SPACER, "", 0);
            e.size = Math.max(0, height);
            owner.addElement(tab, e, id, visibleWhen);
        }
    }

    public void addSlider(
            String text,
            String description,
            int color,
            double min,
            double max,
            double value,
            double step,
            boolean inline,
            Value callback,
            String id,
            Value visibleWhen) {
        Element e = new Element(ElementType.SLIDER, text, color);
        e.desc = description == null ? "" : description;
        e.min = Math.min(min, max);
        e.max = Math.max(min, max);
        e.step = Math.max(0, step);
        e.value = Math.max(e.min, Math.min(e.max, value));
        e.inline = inline;
        e.callback = callback != null && callback.canExecute() ? callback : null;
        addElement(currentTab(), e, id, visibleWhen);
    }

    public void addSeparator(String id, Value visibleWhen) {
        addElement(currentTab(), new Element(ElementType.SEPARATOR, "", 0), id, visibleWhen);
    }

    public void addSpacer(int height, String id, Value visibleWhen) {
        Element e = new Element(ElementType.SPACER, "", 0);
        e.size = Math.max(0, height);
        addElement(currentTab(), e, id, visibleWhen);
    }

    // ------------------------------------------------------------------- init

    @Override
    protected void init() {
        winW = Math.min(requestedWidth, Math.max(260, width - 24));
        winH = Math.min(requestedHeight, Math.max(160, height - 24));
        sidebarW = Math.min(SIDEBAR_WIDTH, winW / 3);
        winX = (width - winW) / 2;
        winY = (height - winH) / 2;
        headerH = font.lineHeight * 2 + 27;

        viewX = winX + sidebarW + padding;
        viewW = winX + winW - padding - viewX;
        viewTop = winY + headerH + 8;
        viewBottom = winY + winH - 8;

        closeX = winX + winW - padding - CLOSE_SIZE;
        closeY = winY + (headerH - CLOSE_SIZE) / 2;

        for (Tab t : tabs) {
            layoutPage(t, columns);

            for (Element e : t.elements) {
                if (e.settings != null) {
                    layoutPage(e.settings, 1);
                }
            }
        }
    }

    private void layoutPage(Tab t, int cols) {
        int viewH = viewBottom - viewTop;
        int w = viewW;
        int total = layoutTab(t, w, cols);

        if (total > viewH) {
            w = viewW - SCROLLBAR_GUTTER;
            total = layoutTab(t, w, cols);
            t.maxScroll = Math.max(0, total - viewH);
        } else {
            t.maxScroll = 0;
        }

        t.total = total;
        t.contentW = w;
        t.scrollTarget = Math.max(0, Math.min(t.maxScroll, t.scrollTarget));
        t.scroll = Math.max(0, Math.min(t.maxScroll, t.scroll));
    }

    private int layoutTab(Tab t, int w, int cols) {
        int cellW = (w - gap * (cols - 1)) / cols;

        int y = 0;
        int col = 0;
        int rowH = 0;
        int visibleCount = 0;

        for (Element e : t.elements) {
            if (!e.visible) {
                e.lines = null;
                continue;
            }

            visibleCount++;
            e.lines = null;

            switch (e.type) {
                case LABEL -> {
                    e.lines = wrap(e.text, w);
                    e.descLines = e.desc.isEmpty() ? List.of() : wrap(e.desc, w);
                    e.h = (e.lines.size() + e.descLines.size()) * (font.lineHeight + 2);
                    e.inline = false;
                }
                case HEADING -> {
                    e.descLines = e.desc.isEmpty() ? List.of() : wrap(e.desc, w);
                    e.h = font.lineHeight + 6
                            + (e.descLines.isEmpty() ? 0 : e.descLines.size() * (font.lineHeight + 2));
                    e.inline = false;
                }
                case BUTTON -> e.h = BUTTON_HEIGHT + (e.desc.isEmpty() ? 0 : font.lineHeight + 4);
                case CARD -> e.h = e.desc.isEmpty() ? TOGGLE_HEIGHT : CARD_HEIGHT;
                case SLIDER -> e.h = SLIDER_HEIGHT + (e.desc.isEmpty() ? 0 : font.lineHeight + 2);
                case RANGE -> e.h = SLIDER_HEIGHT + (e.desc.isEmpty() ? 0 : font.lineHeight + 2);
                case ENUM, KEYBIND -> {
                    e.h = TOGGLE_HEIGHT + (e.desc.isEmpty() ? 0 : font.lineHeight + 4);
                    e.inline = false;
                }
                case SEPARATOR -> {
                    e.h = 1;
                    e.inline = false;
                }
                case SPACER -> {
                    e.h = e.size;
                    e.inline = false;
                }
            }

            if (e.inline && cols > 1) {
                e.ox = col * (cellW + gap);
                e.w = cellW;
                e.oy = y;

                rowH = Math.max(rowH, e.h);
                col++;

                if (col == cols) {
                    y += rowH + gap;
                    col = 0;
                    rowH = 0;
                }
            } else {
                if (col > 0) {
                    y += rowH + gap;
                    col = 0;
                    rowH = 0;
                }

                e.ox = 0;
                e.w = w;
                e.oy = y;
                y += e.h + gap;
            }
        }

        if (col > 0) {
            y += rowH + gap;
        }

        return visibleCount == 0 ? 0 : Math.max(0, y - gap);
    }

    private void refreshVisibility(Tab tab) {
        boolean changed = false;

        for (Element element : tab.elements) {
            if (element.visibleWhen == null) {
                continue;
            }

            try {
                Value result = element.visibleWhen.execute();

                if (result == null || !result.isBoolean()) {
                    throw new IllegalArgumentException("visibleWhen must return a boolean.");
                }

                boolean visible = result.asBoolean();
                changed |= element.visible != visible;
                element.visible = visible;
                element.visibilityErrorLogged = false;
            } catch (Exception e) {
                if (!element.visibilityErrorLogged) {
                    ScriptiumLogger.error("GUI visibility check failed: " + e.getMessage());
                    element.visibilityErrorLogged = true;
                }

                changed |= element.visible;
                element.visible = false;
            }
        }

        if (changed) {
            layoutPage(tab, activeSettings == null ? columns : 1);
        }
    }

    private Tab activeTab() {
        return tabs.isEmpty() ? null : tabs.get(Math.min(activeIndex, tabs.size() - 1));
    }

    /**
     * The page currently shown in the content area: a card's settings, or the
     * active tab.
     */
    private Tab viewTab() {
        return activeSettings != null ? activeSettings : activeTab();
    }

    private void selectTab(int index) {
        if (index == activeIndex && activeSettings == null) {
            return;
        }

        activeIndex = index;
        activeSettings = null;
        tabStart = System.nanoTime();
        dragging = null;
        keybindCapture = null;
    }

    private void openSettings(Element card) {
        activeSettings = card.settings;
        tabStart = System.nanoTime();
        dragging = null;
    }

    private void closeSettings() {
        activeSettings = null;
        tabStart = System.nanoTime();
        dragging = null;
        keybindCapture = null;
    }

    private int tabY(int index) {
        return winY + font.lineHeight * 2 + 35 + index * (TAB_HEIGHT + TAB_GAP);
    }

    // ----------------------------------------------------------------- render

    @Override
    public void extractRenderState(
            GuiGraphicsExtractor g,
            int mouseX,
            int mouseY,
            float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);

        // Timing.
        long now = System.nanoTime();

        if (openStart == 0L) {
            openStart = now;
            lastFrame = now;
        }

        float dt = Math.min(0.1f, (now - lastFrame) / 1_000_000_000f);
        lastFrame = now;

        float k = Math.min(1f, dt * 16f);
        float openT = Math.min(1f, (now - openStart) / 200_000_000f);

        fade = easeOut(openT);
        layerFade = 1f;
        clipTop = Integer.MIN_VALUE;
        clipBottom = Integer.MAX_VALUE;

        float tabFade = tabStart == 0L ? 1f : easeOut(Math.min(1f, (now - tabStart) / 160_000_000f));
        int slide = Math.round((1f - fade) * 12f);
        int tabSlide = Math.round((1f - tabFade) * 6f);

        // Backdrop (the vanilla blur from super shows through when translucent).
        fill(g, 0, 0, width, height, backdropColor);

        int x1 = winX;
        int y1 = winY + slide;
        int x2 = winX + winW;
        int y2 = y1 + winH;
        int sx = x1 + sidebarW;

        // Window.
        drawShadow(g, x1, y1, x2, y2);
        fillRound(g, x1 - 1, y1 - 1, x2 + 1, y2 + 1, borderColor, RADIUS + 1, ALL);
        fillRound(g, x1, y1, sx, y2, sidebarColor, RADIUS, TL | BL);
        fillRound(g, sx, y1, x2, y2, panelColor, RADIUS, TR | BR);
        fill(g, sx, y1 + 1, sx + 1, y2 - 1, withAlpha(WHITE, 16));

        drawSidebar(g, mouseX, mouseY, k, slide, x1, y1, y2);

        Tab tab = viewTab();

        if (tab != null) {
            refreshVisibility(tab);
        }

        // Header.
        int hx = sx + padding;
        int titleX = hx;

        if (activeSettings != null) {
            boolean backHot = inside(mouseX, mouseY, viewX, closeY, CLOSE_SIZE, CLOSE_SIZE);
            backHover += ((backHot ? 1f : 0f) - backHover) * k;

            fillRound(g, viewX, closeY + slide, viewX + CLOSE_SIZE, closeY + slide + CLOSE_SIZE,
                    withAlpha(WHITE, 8 + Math.round(22 * backHover)), 5, ALL);

            String arrow = "<";

            text(g, arrow,
                    viewX + (CLOSE_SIZE - font.width(arrow)) / 2,
                    closeY + slide + (CLOSE_SIZE - font.lineHeight) / 2,
                    mix(MUTED, WHITE, backHover));

            titleX = hx + CLOSE_SIZE + 8;
        } else {
            backHover = 0f;
        }

        if (tab != null) {
            text(g, "§l" + fit(tab.name, closeX - titleX - 8), titleX, y1 + 12, WHITE);

            if (!tab.description.isEmpty()) {
                text(g, fit(tab.description, closeX - titleX - 8), titleX, y1 + 12 + font.lineHeight + 3, MUTED);
            }
        }

        fill(g, hx, y1 + headerH - 1, x2 - padding, y1 + headerH, withAlpha(WHITE, 18));

        // Close button.
        boolean closeHot = inside(mouseX, mouseY, closeX, closeY, CLOSE_SIZE, CLOSE_SIZE);
        closeHover += ((closeHot ? 1f : 0f) - closeHover) * k;

        if (closeHover > 0.01f) {
            fillRound(g, closeX, closeY + slide, closeX + CLOSE_SIZE, closeY + slide + CLOSE_SIZE,
                    withAlpha(0xFFE5484D, Math.round(220 * closeHover)), 5, ALL);
        }

        String cross = "×";

        text(g, cross,
                closeX + (CLOSE_SIZE - font.width(cross)) / 2,
                closeY + slide + (CLOSE_SIZE - font.lineHeight) / 2,
                mix(MUTED, WHITE, closeHover));

        if (tab == null) {
            return;
        }

        // Content.
        tab.scroll += (tab.scrollTarget - tab.scroll) * Math.min(1f, dt * 18f);

        if (Math.abs(tab.scrollTarget - tab.scroll) < 0.05f) {
            tab.scroll = tab.scrollTarget;
        }

        int scrollPx = Math.round(tab.scroll);
        boolean mouseInView = mouseY >= viewTop && mouseY < viewBottom;

        layerFade = tabFade;
        clipTop = viewTop + slide;
        clipBottom = viewBottom + slide;

        for (Element e : tab.elements) {
            if (!e.visible) {
                e.hover = 0f;
                continue;
            }

            int top = viewTop + e.oy - scrollPx;

            if (top + e.h < viewTop || top > viewBottom) {
                e.hover = 0f;
                continue;
            }

            int x = viewX + e.ox;

            boolean hot = mouseInView && e.interactive() && inside(mouseX, mouseY, x, top, e.w, e.h);

            e.hover += ((hot || dragging == e ? 1f : 0f) - e.hover) * k;
            e.press = Math.max(0f, e.press - dt * 5f);
            e.anim += ((e.on ? 1f : 0f) - e.anim) * k;

            int y = top + slide + tabSlide;

            switch (e.type) {
                case LABEL -> drawLabel(g, e, x, y);
                case HEADING -> drawHeading(g, e, x, y);
                case BUTTON -> drawButton(g, e, x, y);
                case CARD -> drawCard(g, e, x, y);
                case SLIDER -> drawSlider(g, e, x, y);
                case RANGE -> drawRange(g, e, x, y);
                case ENUM -> drawEnum(g, e, x, y);
                case KEYBIND -> drawKeybind(g, e, x, y);
                case SEPARATOR -> fill(g, x, y, x + e.w, y + 1, withAlpha(WHITE, 24));
                case SPACER -> {
                }
            }
        }

        clipTop = Integer.MIN_VALUE;
        clipBottom = Integer.MAX_VALUE;
        layerFade = 1f;

        // Scrollbar.
        if (tab.maxScroll > 0) {
            int viewH = viewBottom - viewTop;
            int trackX = viewX + tab.contentW + 4;
            int thumbH = Math.max(16, (int) ((long) viewH * viewH / Math.max(1, tab.total)));
            int thumbY = viewTop + Math.round((viewH - thumbH) * (tab.scroll / tab.maxScroll)) + slide;

            fillRound(g, trackX, viewTop + slide, trackX + 3, viewTop + slide + viewH,
                    withAlpha(WHITE, 14), 1, ALL);
            fillRound(g, trackX, thumbY, trackX + 3, thumbY + thumbH,
                    withAlpha(accentColor, 210), 1, ALL);
        }
    }

    private void drawSidebar(
            GuiGraphicsExtractor g,
            int mouseX,
            int mouseY,
            float k,
            int slide,
            int x1,
            int y1,
            int y2) {
        int innerW = sidebarW - 28;

        text(g, "§l" + fit(screenTitle, innerW - 6), x1 + 14, y1 + 14, WHITE);

        if (!subtitle.isEmpty()) {
            text(g, fit(subtitle, innerW), x1 + 14, y1 + 14 + font.lineHeight + 3, MUTED);
        }

        int tabX = x1 + 10;
        int tabW = sidebarW - 20;

        for (int i = 0; i < tabs.size(); i++) {
            Tab t = tabs.get(i);

            int ry = tabY(i);
            int dy = ry + slide;

            boolean hot = inside(mouseX, mouseY, tabX, ry, tabW, TAB_HEIGHT);

            t.hover += ((hot ? 1f : 0f) - t.hover) * k;
            t.active += ((i == activeIndex ? 1f : 0f) - t.active) * k;

            if (t.active > 0.01f) {
                fillRound(g, tabX, dy, tabX + tabW, dy + TAB_HEIGHT,
                        withAlpha(accentColor, Math.round(48 * t.active)), 5, ALL);
                fillRound(g, tabX, dy + 6, tabX + 3, dy + TAB_HEIGHT - 6,
                        withAlpha(accentColor, Math.round(255 * t.active)), 1, ALL);
            } else if (t.hover > 0.01f) {
                fillRound(g, tabX, dy, tabX + tabW, dy + TAB_HEIGHT,
                        withAlpha(WHITE, Math.round(12 * t.hover)), 5, ALL);
            }

            int color = mix(MUTED, WHITE, Math.max(t.active, t.hover * 0.6f));
            int textX = tabX + 12;
            int textY = dy + (TAB_HEIGHT - font.lineHeight) / 2 + 1;

            if (!t.icon.isEmpty()) {
                text(g, t.icon, textX, textY, mix(MUTED, accentColor, Math.max(t.active, 0.35f)));
                textX += font.width(t.icon) + 6;
            }

            text(g, fit(t.name, tabX + tabW - textX - 6), textX, textY, color);
        }

        if (!footer.isEmpty()) {
            text(g, fit(footer, innerW), x1 + 14, y2 - 14 - font.lineHeight, withAlpha(MUTED, 170));
        }
    }

    private void drawLabel(GuiGraphicsExtractor g, Element e, int x, int y) {
        int step = font.lineHeight + 2;

        for (int i = 0; i < e.lines.size(); i++) {
            text(g, e.lines.get(i), x, y + i * step + 1, e.color);
        }

        for (int i = 0; i < e.descLines.size(); i++) {
            text(g, e.descLines.get(i), x, y + (e.lines.size() + i) * step + 1, MUTED);
        }
    }

    private void drawHeading(GuiGraphicsExtractor g, Element e, int x, int y) {
        String label = fit(e.text.toUpperCase(Locale.ROOT), e.w - 10);

        text(g, "§l" + label, x, y + 2, e.color);

        int lineX = x + font.width("§l" + label) + 8;

        if (lineX < x + e.w) {
            fill(g, lineX, y + font.lineHeight / 2 + 2, x + e.w, y + font.lineHeight / 2 + 3,
                    withAlpha(WHITE, 20));
        }

        for (int i = 0; i < e.descLines.size(); i++) {
            text(g, e.descLines.get(i), x, y + font.lineHeight + 6 + i * (font.lineHeight + 2), MUTED);
        }
    }

    private void drawButton(GuiGraphicsExtractor g, Element e, int x, int y) {
        int w = e.w;
        int h = e.h;

        if (e.press > 0.4f) {
            y += 1;
        }

        int base = e.buttonColor;
        boolean ghost = alpha(base) < 128;

        int hoverTarget = ghost ? withAlpha(accentColor, 70) : brighten(base, 20);
        int fillColor = mix(base, hoverTarget, e.hover);
        int edge = ghost
                ? mix(softBorderColor, accentColor, e.hover)
                : mix(brighten(base, 14), accentColor, e.hover * 0.9f);

        fillRound(g, x, y, x + w, y + h, edge, 6, ALL);

        if (ghost) {
            fillRound(g, x + 1, y + 1, x + w - 1, y + h - 1, panelColor, 5, ALL);
        }

        fillRound(g, x + 1, y + 1, x + w - 1, y + h - 1, fillColor, 5, ALL);

        if (e.press > 0f) {
            fillRound(g, x + 1, y + 1, x + w - 1, y + h - 1,
                    withAlpha(WHITE, Math.round(40 * e.press)), 5, ALL);
        }

        String label = fit(e.text, w - 16);

        if (e.desc.isEmpty()) {
            text(g, label,
                    x + (w - font.width(label)) / 2,
                    y + (h - font.lineHeight) / 2 + 1,
                    e.color);
        } else {
            text(g, label,
                    x + (w - font.width(label)) / 2,
                    y + 5,
                    e.color);
            String description = fit(e.desc, w - 16);
            text(g, description,
                    x + (w - font.width(description)) / 2,
                    y + 5 + font.lineHeight + 2,
                    mix(e.color, MUTED, 0.35f));
        }
    }

    private void drawCard(GuiGraphicsExtractor g, Element e, int x, int y) {
        int w = e.w;
        int h = e.h;

        float edgeMix = Math.min(1f,
                e.hover * 0.7f + (e.cardComponent && e.toggleable ? e.anim * 0.3f : 0f));

        fillRound(g, x, y, x + w, y + h, mix(borderColor, accentColor, edgeMix), 6, ALL);
        fillRound(g, x + 1, y + 1, x + w - 1, y + h - 1,
                mix(cardColor, cardHoverColor, e.hover), 5, ALL);

        int textX = x + 14;
        int rightW = (e.toggleable ? 28 + 14 : 20) + (e.settings != null ? 12 : 0);
        int maxText = w - 14 - rightW - 4;

        boolean hasDesc = !e.desc.isEmpty();
        int blockH = hasDesc ? font.lineHeight * 2 + 3 : font.lineHeight;
        int ty = y + (h - blockH) / 2 + (hasDesc ? 0 : 1);

        String title = fit(e.text, maxText - 6);
        text(g, e.cardComponent ? "§l" + title : title, textX, ty, e.color);

        if (hasDesc) {
            text(g, fit(e.desc, maxText), textX, ty + font.lineHeight + 3, MUTED);
        }

        int rightEdge;

        if (e.toggleable) {
            rightEdge = x + w - 12 - 28;
            drawSwitch(g, rightEdge, y + (h - 14) / 2, e.anim);
        } else {
            rightEdge = x + w - 14 - font.width(">");
            text(g, ">", rightEdge, y + (h - font.lineHeight) / 2 + 1,
                    mix(MUTED, WHITE, e.hover));
        }

        // Three dots hint that right click opens this card's settings.
        if (e.settings != null) {
            int dotX = rightEdge - 10;
            int dotY = y + (h - 10) / 2;
            int dotColor = mix(MUTED, WHITE, e.hover);

            for (int i = 0; i < 3; i++) {
                fill(g, dotX, dotY + i * 4, dotX + 2, dotY + i * 4 + 2, dotColor);
            }
        }
    }

    private void drawSwitch(GuiGraphicsExtractor g, int x, int y, float anim) {
        int trackW = 28;
        int trackH = 14;

        fillRound(g, x, y, x + trackW, y + trackH, mix(offTrackColor, accentColor, anim), trackH / 2, ALL);

        int knobX = x + 2 + Math.round(anim * (trackW - trackH));

        fillRound(g, knobX, y + 2, knobX + trackH - 4, y + trackH - 2, WHITE, (trackH - 4) / 2, ALL);
    }

    private void drawSlider(GuiGraphicsExtractor g, Element e, int x, int y) {
        int w = e.w;
        int h = e.h;

        fillRound(g, x, y, x + w, y + h, mix(borderColor, accentColor, e.hover * 0.7f), 6, ALL);
        fillRound(g, x + 1, y + 1, x + w - 1, y + h - 1,
                mix(cardColor, cardHoverColor, e.hover), 5, ALL);

        String value = formatValue(e, e.value);

        int labelY = y + (e.desc.isEmpty() ? 8 : 5);
        text(g, fit(e.text, w - 28 - font.width(value)), x + 12, labelY, e.color);
        text(g, value, x + w - 12 - font.width(value), labelY, mix(MUTED, WHITE, 0.7f));

        if (!e.desc.isEmpty()) {
            text(g, fit(e.desc, w - 24), x + 12, labelY + font.lineHeight + 2, MUTED);
        }

        int trackX = x + 12;
        int trackW = w - 24;
        int trackY = y + h - 14;

        float frac = rangeFraction(e.value, e);
        int fx = Math.round(trackW * frac);

        fillRound(g, trackX, trackY, trackX + trackW, trackY + 4, offTrackColor, 2, ALL);

        if (fx > 0) {
            fillRound(g, trackX, trackY, trackX + fx, trackY + 4, accentColor, 2, ALL);
        }

        int kr = 5 + (e.hover > 0.5f ? 1 : 0);
        int kx = trackX + fx;

        fillRound(g, kx - kr, trackY + 2 - kr, kx + kr, trackY + 2 + kr, WHITE, kr, ALL);
    }

    private void drawRange(GuiGraphicsExtractor g, Element e, int x, int y) {
        int w = e.w;
        int h = e.h;

        fillRound(g, x, y, x + w, y + h, mix(borderColor, accentColor, e.hover * 0.7f), 6, ALL);
        fillRound(g, x + 1, y + 1, x + w - 1, y + h - 1,
                mix(cardColor, cardHoverColor, e.hover), 5, ALL);

        String value = formatValue(e, e.rangeMin) + " - " + formatValue(e, e.rangeMax);
        int labelY = y + (e.desc.isEmpty() ? 8 : 5);
        text(g, fit(e.text, w - 28 - font.width(value)), x + 12, labelY, e.color);
        text(g, value, x + w - 12 - font.width(value), labelY, mix(MUTED, WHITE, 0.7f));

        if (!e.desc.isEmpty()) {
            text(g, fit(e.desc, w - 24), x + 12, labelY + font.lineHeight + 2, MUTED);
        }

        int trackX = x + 12;
        int trackW = w - 24;
        int trackY = y + h - 14;
        int lowX = trackX + Math.round(trackW * rangeFraction(e.rangeMin, e));
        int highX = trackX + Math.round(trackW * rangeFraction(e.rangeMax, e));

        fillRound(g, trackX, trackY, trackX + trackW, trackY + 4, offTrackColor, 2, ALL);
        if (highX > lowX) {
            fillRound(g, lowX, trackY, highX, trackY + 4, accentColor, 2, ALL);
        }

        int kr = 5 + (e.hover > 0.5f ? 1 : 0);
        fillRound(g, lowX - kr, trackY + 2 - kr, lowX + kr, trackY + 2 + kr, WHITE, kr, ALL);
        fillRound(g, highX - kr, trackY + 2 - kr, highX + kr, trackY + 2 + kr, WHITE, kr, ALL);
    }

    private static float rangeFraction(double value, Element e) {
        double range = e.max - e.min;
        return range <= 0 ? 0f : (float) ((value - e.min) / range);
    }

    private void drawEnum(GuiGraphicsExtractor g, Element e, int x, int y) {
        drawValueSetting(g, e, x, y, "< " + e.enumValue + " >", false);
    }

    private void drawKeybind(GuiGraphicsExtractor g, Element e, int x, int y) {
        String value = keybindCapture == e
                ? "Press a key..."
                : e.keyCode == GLFW.GLFW_KEY_UNKNOWN
                        ? "None"
                        : InputConstants.Type.KEYSYM.getOrCreate(e.keyCode).getDisplayName().getString();
        drawValueSetting(g, e, x, y, value, keybindCapture == e);
    }

    private void drawValueSetting(GuiGraphicsExtractor g, Element e, int x, int y, String value, boolean capturing) {
        int w = e.w;
        int h = e.h;
        int edge = mix(borderColor, accentColor, e.hover * 0.7f);

        fillRound(g, x, y, x + w, y + h, edge, 6, ALL);
        fillRound(g, x + 1, y + 1, x + w - 1, y + h - 1,
                mix(cardColor, cardHoverColor, e.hover), 5, ALL);

        int controlW = Math.min(Math.max(64, font.width(value) + 20), Math.max(64, w / 2));
        int controlX = x + w - controlW - 10;
        int controlY = y + 5;
        int controlH = e.desc.isEmpty() ? h - 10 : 20;
        int controlBackground = capturing ? withAlpha(accentColor, 80) : mix(panelColor, WHITE, 0.08f);

        fillRound(g, controlX, controlY, controlX + controlW, controlY + controlH,
                withAlpha(capturing ? accentColor : softBorderColor, 190), 5, ALL);
        fillRound(g, controlX + 1, controlY + 1, controlX + controlW - 1, controlY + controlH - 1,
                controlBackground, 4, ALL);

        String shownValue = fit(value, controlW - 12);
        text(g, shownValue,
                controlX + (controlW - font.width(shownValue)) / 2,
                controlY + (controlH - font.lineHeight) / 2 + 1,
                capturing ? WHITE : mix(MUTED, WHITE, 0.8f));

        int labelW = Math.max(0, controlX - x - 22);
        int labelY = y + (e.desc.isEmpty() ? (h - font.lineHeight) / 2 + 1 : 5);
        text(g, fit(e.text, labelW), x + 12, labelY, e.color);

        if (!e.desc.isEmpty()) {
            text(g, fit(e.desc, labelW), x + 12, labelY + font.lineHeight + 2, MUTED);
        }
    }

    private void drawShadow(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2) {
        for (int i = 1; i <= 10; i++) {
            int alpha = Math.max(1, 24 - i * 2);

            fillRound(g, x1 - i, y1 - i + 6, x2 + i, y2 + i + 6,
                    withAlpha(BLACK, alpha), RADIUS + i, ALL);
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(
            MouseButtonEvent event,
            boolean doubleClick) {
        if (event.button() == 1) {
            // Right click: leave the settings page, or open a card's settings.
            if (activeSettings != null) {
                closeSettings();
                return true;
            }

            Tab rightTab = activeTab();
            double rx = event.x();
            double ry = event.y();

            if (rightTab != null && ry >= viewTop && ry < viewBottom) {
                refreshVisibility(rightTab);
                int rightScroll = Math.round(rightTab.scroll);

                for (Element e : rightTab.elements) {
                    if (!e.visible || e.settings == null) {
                        continue;
                    }

                    int top = viewTop + e.oy - rightScroll;

                    if (inside(rx, ry, viewX + e.ox, top, e.w, e.h)) {
                        openSettings(e);
                        return true;
                    }
                }
            }

            return super.mouseClicked(event, doubleClick);
        }

        if (event.button() != 0) {
            return super.mouseClicked(event, doubleClick);
        }

        double mx = event.x();
        double my = event.y();

        if (inside(mx, my, closeX, closeY, CLOSE_SIZE, CLOSE_SIZE)) {
            closeScreen();
            return true;
        }

        if (activeSettings != null && inside(mx, my, viewX, closeY, CLOSE_SIZE, CLOSE_SIZE)) {
            closeSettings();
            return true;
        }

        for (int i = 0; i < tabs.size(); i++) {
            if (inside(mx, my, winX + 10, tabY(i), sidebarW - 20, TAB_HEIGHT)) {
                selectTab(i);
                return true;
            }
        }

        Tab tab = viewTab();

        if (tab != null) {
            refreshVisibility(tab);
        }

        if (tab != null && my >= viewTop && my < viewBottom) {
            int scrollPx = Math.round(tab.scroll);

            for (Element e : tab.elements) {
                if (!e.visible || !e.interactive()) {
                    continue;
                }

                int top = viewTop + e.oy - scrollPx;

                if (!inside(mx, my, viewX + e.ox, top, e.w, e.h)) {
                    continue;
                }

                activate(e, mx);
                return true;
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (keybindCapture == null) {
            return super.keyPressed(event);
        }

        int keyCode = event.key();

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            keybindCapture = null;
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            updateKeybind(keybindCapture, GLFW.GLFW_KEY_UNKNOWN);
            return true;
        }

        if (keyCode < 0 || keyCode > GLFW.GLFW_KEY_LAST) {
            return true;
        }

        updateKeybind(keybindCapture, keyCode);
        return true;
    }

    private void activate(Element e, double mouseX) {
        e.press = 1f;

        try {
            switch (e.type) {
                case BUTTON -> e.callback.execute();
                case CARD -> {
                    if (e.toggleable) {
                        e.on = !e.on;

                        if (e.callback != null) {
                            e.callback.execute(e.on);
                        }
                    } else if (e.callback != null) {
                        e.callback.execute();
                    }
                }
                case SLIDER -> {
                    dragging = e;
                    updateSlider(e, mouseX);
                }
                case RANGE -> {
                    dragging = e;
                    double trackX = viewX + e.ox + 12;
                    double trackW = Math.max(1, e.w - 24);
                    double lowX = trackX + rangeFraction(e.rangeMin, e) * trackW;
                    double highX = trackX + rangeFraction(e.rangeMax, e) * trackW;
                    draggingRangeUpper = Math.abs(mouseX - highX) < Math.abs(mouseX - lowX);
                    updateRange(e, mouseX);
                }
                case ENUM -> cycleEnum(e);
                case KEYBIND -> keybindCapture = e;
                default -> {
                }
            }
        } catch (Exception ex) {
            ScriptiumLogger.error("GUI callback failed: " + ex.getMessage());
        }
    }

    private void cycleEnum(Element e) {
        int index = e.enumOptions.indexOf(e.enumValue);
        String next = e.enumOptions.get((index + 1) % e.enumOptions.size());

        if (!e.enumValue.equals(next)) {
            e.enumValue = next;
            notifyValueChanged(e, next, "GUI enum callback failed: ");
        }
    }

    private void updateKeybind(Element e, int keyCode) {
        validateKeyCode(keyCode);
        keybindCapture = null;

        if (e.keyCode != keyCode) {
            e.keyCode = keyCode;
            notifyValueChanged(e, keyCode, "GUI keybind callback failed: ");
        }
    }

    private static void validateKeyCode(int keyCode) {
        if (keyCode != GLFW.GLFW_KEY_UNKNOWN && (keyCode < 0 || keyCode > GLFW.GLFW_KEY_LAST)) {
            throw new IllegalArgumentException("Keybind value must be a valid key code.");
        }
    }

    private static void notifyValueChanged(Element element, Object value, String errorPrefix) {
        if (element.callback == null) {
            return;
        }

        Value callback = element.callback;
        Runnable notify = () -> {
            try {
                callback.execute(value);
            } catch (Exception e) {
                ScriptiumLogger.error(errorPrefix + e.getMessage());
            }
        };
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.isSameThread()) {
            notify.run();
        } else {
            minecraft.execute(notify);
        }
    }

    private static void notifyRangeChanged(Element element) {
        if (element.callback == null) {
            return;
        }

        Value callback = element.callback;
        Runnable notify = () -> {
            try {
                callback.execute(element.rangeMin, element.rangeMax);
            } catch (Exception e) {
                ScriptiumLogger.error("GUI range callback failed: " + e.getMessage());
            }
        };
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.isSameThread()) {
            notify.run();
        } else {
            minecraft.execute(notify);
        }
    }

    private void updateSlider(Element e, double mouseX) {
        double trackX = viewX + e.ox + 12;
        double trackW = Math.max(1, e.w - 24);

        double f = Math.max(0, Math.min(1, (mouseX - trackX) / trackW));
        double v = e.min + f * (e.max - e.min);

        if (e.step > 0) {
            v = e.min + Math.round((v - e.min) / e.step) * e.step;
        }

        v = Math.max(e.min, Math.min(e.max, v));
        v = Math.round(v * 1_000_000d) / 1_000_000d;

        if (v == e.value) {
            return;
        }

        e.value = v;

        if (e.callback != null) {
            try {
                e.callback.execute(v);
            } catch (Exception ex) {
                ScriptiumLogger.error("GUI slider callback failed: " + ex.getMessage());
            }
        }
    }

    private void updateRange(Element e, double mouseX) {
        double trackX = viewX + e.ox + 12;
        double trackW = Math.max(1, e.w - 24);
        double fraction = Math.max(0, Math.min(1, (mouseX - trackX) / trackW));
        double next = normalizeRangeValue(e, e.min + fraction * (e.max - e.min));

        if (draggingRangeUpper) {
            next = Math.max(e.rangeMin, next);
            if (next == e.rangeMax) {
                return;
            }
            e.rangeMax = next;
        } else {
            next = Math.min(e.rangeMax, next);
            if (next == e.rangeMin) {
                return;
            }
            e.rangeMin = next;
        }

        if (e.callback != null) {
            try {
                e.callback.execute(e.rangeMin, e.rangeMax);
            } catch (Exception ex) {
                ScriptiumLogger.error("GUI range callback failed: " + ex.getMessage());
            }
        }
    }

    private static double normalizeRangeValue(Element e, double value) {
        double next = Math.max(e.min, Math.min(e.max, value));

        if (e.step > 0) {
            next = e.min + Math.round((next - e.min) / e.step) * e.step;
        }

        next = Math.max(e.min, Math.min(e.max, next));
        return Math.round(next * 1_000_000d) / 1_000_000d;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (dragging != null && event.button() == 0) {
            if (dragging.type == ElementType.RANGE) {
                updateRange(dragging, event.x());
            } else {
                updateSlider(dragging, event.x());
            }
            return true;
        }

        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = null;
        draggingRangeUpper = false;

        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        Tab tab = viewTab();

        if (tab != null && tab.maxScroll > 0) {
            tab.scrollTarget = (float) Math.max(0, Math.min(tab.maxScroll, tab.scrollTarget - scrollY * 28));
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    /**
     * ESC calls this: inside a settings page it goes back, otherwise it closes the
     * screen.
     */
    @Override
    public void onClose() {
        if (activeSettings != null) {
            closeSettings();
            return;
        }

        closeScreen();
    }

    private void closeScreen() {
        keybindCapture = null;

        if (minecraft != null) {
            minecraft.gui.setScreen(null);
        }
    }

    // ---------------------------------------------------------------- drawing

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private int faded(int color) {
        int a = Math.round(alpha(color) * fade * layerFade);

        return (color & 0x00FFFFFF) | (a << 24);
    }

    /** Fill with fade + software vertical clipping (clipTop/clipBottom). */
    private void fill(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color) {
        int c = faded(color);

        y1 = Math.max(y1, clipTop);
        y2 = Math.min(y2, clipBottom);

        if (alpha(c) == 0 || x2 <= x1 || y2 <= y1) {
            return;
        }

        g.fill(x1, y1, x2, y2, c);
    }

    private void fillRound(
            GuiGraphicsExtractor g,
            int x1,
            int y1,
            int x2,
            int y2,
            int color,
            int radius,
            int corners) {
        int w = x2 - x1;
        int h = y2 - y1;
        int r = Math.min(radius, Math.min(w, h) / 2);

        if (r <= 0) {
            fill(g, x1, y1, x2, y2, color);
            return;
        }

        fill(g, x1, y1 + r, x2, y2 - r, color);

        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = r - (int) Math.round(Math.sqrt(r * r - dy * dy));

            int tl = (corners & TL) != 0 ? inset : 0;
            int tr = (corners & TR) != 0 ? inset : 0;
            int bl = (corners & BL) != 0 ? inset : 0;
            int br = (corners & BR) != 0 ? inset : 0;

            fill(g, x1 + tl, y1 + i, x2 - tr, y1 + i + 1, color);
            fill(g, x1 + bl, y2 - i - 1, x2 - br, y2 - i, color);
        }
    }

    private void text(GuiGraphicsExtractor g, String s, int x, int y, int color) {
        int c = faded(color);

        // Vanilla treats very low alpha as opaque, and text can't be partially clipped.
        if (alpha(c) < 8 || y < clipTop || y + font.lineHeight > clipBottom) {
            return;
        }

        g.text(font, s, x, y, c, false);
    }

    private String fit(String s, int maxWidth) {
        if (font.width(s) <= maxWidth) {
            return s;
        }

        int end = s.length();

        while (end > 0 && font.width(s.substring(0, end) + "...") > maxWidth) {
            end--;
        }

        return s.substring(0, end) + "...";
    }

    private List<String> wrap(String text, int maxWidth) {
        List<String> out = new ArrayList<>();

        for (String para : text.split("\n", -1)) {
            if (para.isEmpty()) {
                out.add("");
                continue;
            }

            StringBuilder line = new StringBuilder();

            for (String word : para.split(" ")) {
                String candidate = line.length() == 0 ? word : line + " " + word;

                if (font.width(candidate) <= maxWidth) {
                    line = new StringBuilder(candidate);
                    continue;
                }

                if (line.length() > 0) {
                    out.add(line.toString());
                    line = new StringBuilder();
                }

                String rest = word;

                while (font.width(rest) > maxWidth && rest.length() > 1) {
                    int cut = rest.length() - 1;

                    while (cut > 1 && font.width(rest.substring(0, cut)) > maxWidth) {
                        cut--;
                    }

                    out.add(rest.substring(0, cut));
                    rest = rest.substring(cut);
                }

                line.append(rest);
            }

            out.add(line.toString());
        }

        return out;
    }

    private static String formatValue(Element e, double value) {
        if (e.step >= 1 || (e.step == 0 && e.max - e.min >= 10)) {
            return Long.toString(Math.round(value));
        }

        if (e.step >= 0.1) {
            return String.format(Locale.ROOT, "%.1f", value);
        }

        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static float easeOut(float t) {
        float inv = 1f - t;

        return 1f - inv * inv * inv;
    }

    private static int alpha(int color) {
        return (color >>> 24) & 0xFF;
    }

    private static int mix(int from, int to, float t) {
        t = Math.max(0f, Math.min(1f, t));

        int a = Math.round(((from >>> 24) & 0xFF) * (1f - t) + ((to >>> 24) & 0xFF) * t);
        int r = Math.round(((from >>> 16) & 0xFF) * (1f - t) + ((to >>> 16) & 0xFF) * t);
        int g = Math.round(((from >>> 8) & 0xFF) * (1f - t) + ((to >>> 8) & 0xFF) * t);
        int b = Math.round((from & 0xFF) * (1f - t) + (to & 0xFF) * t);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int brighten(int color, int amount) {
        int a = (color >>> 24) & 0xFF;
        int r = Math.min(255, ((color >>> 16) & 0xFF) + amount);
        int g = Math.min(255, ((color >>> 8) & 0xFF) + amount);
        int b = Math.min(255, (color & 0xFF) + amount);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    // ------------------------------------------------------------------ model

    private enum ElementType {
        LABEL,
        HEADING,
        BUTTON,
        CARD,
        SLIDER,
        RANGE,
        ENUM,
        KEYBIND,
        SEPARATOR,
        SPACER
    }

    private static final class Tab {
        private final String name;
        private final String description;
        private final String icon;
        private final List<Element> elements = new ArrayList<>();

        private float scroll;
        private float scrollTarget;
        private int maxScroll;
        private int total;
        private int contentW;

        private float hover;
        private float active;

        private Tab(String name, String description, String icon) {
            this.name = name == null ? "" : name;
            this.description = description == null ? "" : description;
            this.icon = icon == null ? "" : icon;
        }
    }

    private static final class Element {
        private final ElementType type;
        private final String text;
        private final int color;

        private Value visibleWhen;
        private boolean visible = true;
        private boolean visibilityErrorLogged;
        private String desc = "";
        private List<String> descLines = List.of();
        private int buttonColor;
        private Value callback;
        private boolean toggleable;
        private boolean cardComponent;
        private volatile boolean on;
        private boolean inline;
        private int size;
        private Tab settings;

        private double min;
        private double max;
        private double step;
        private volatile double value;
        private volatile double rangeMin;
        private volatile double rangeMax;
        private List<String> enumOptions = List.of();
        private volatile String enumValue = "";
        private volatile int keyCode = GLFW.GLFW_KEY_UNKNOWN;

        // layout (relative to the viewport)
        private int ox;
        private int oy;
        private int w;
        private int h;
        private List<String> lines;

        // animation
        private float hover;
        private float press;
        private float anim;

        private Element(ElementType type, String text, int color) {
            this.type = type;
            this.text = text == null ? "" : text;
            this.color = color;
        }

        private boolean interactive() {
            return type == ElementType.BUTTON
                    || type == ElementType.CARD
                    || type == ElementType.SLIDER
                    || type == ElementType.RANGE
                    || type == ElementType.ENUM
                    || type == ElementType.KEYBIND;
        }
    }
}