package com.eventsuite.ui;

import java.awt.Color;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The colours and type sizes the whole interface is painted with.
 *
 * <p>Every screen reads from here rather than building colours of its own. That is
 * what makes dark mode a switch rather than a second application: there is one place
 * that knows what the interface looks like, and the screens are written once.
 *
 * <p>The semantic colours matter as much as the plain ones. Attendance, revenue,
 * budget and return each keep their own colour across every chart and every tile, so
 * that a blue figure on the dashboard means the same thing as a blue bar in the
 * finance screen. Learning "amber means a budget heading is close to its limit" once
 * is enough.
 */
public final class Theme {

    /** What a screen is painted with. */
    public static final class Palette {
        private final boolean dark;
        private final Color page;
        private final Color card;
        private final Color sidebar;
        private final Color sidebarText;
        private final Color sidebarMuted;
        private final Color sidebarSelected;
        private final Color text;
        private final Color muted;
        private final Color faint;
        private final Color border;
        private final Color accent;
        private final Color accentSoft;
        private final Color onAccent;
        private final Color success;
        private final Color warning;
        private final Color danger;
        private final Color info;
        private final Color tableHeader;
        private final Color tableGrid;
        private final Color tableSelection;
        private final Color field;
        private final Color fieldBorder;
        private final Color chartGrid;
        private final Color axis;

        private Palette(boolean dark, Color[] values) {
            int index = 0;
            this.dark = dark;
            this.page = values[index++];
            this.card = values[index++];
            this.sidebar = values[index++];
            this.sidebarText = values[index++];
            this.sidebarMuted = values[index++];
            this.sidebarSelected = values[index++];
            this.text = values[index++];
            this.muted = values[index++];
            this.faint = values[index++];
            this.border = values[index++];
            this.accent = values[index++];
            this.accentSoft = values[index++];
            this.onAccent = values[index++];
            this.success = values[index++];
            this.warning = values[index++];
            this.danger = values[index++];
            this.info = values[index++];
            this.tableHeader = values[index++];
            this.tableGrid = values[index++];
            this.tableSelection = values[index++];
            this.field = values[index++];
            this.fieldBorder = values[index++];
            this.chartGrid = values[index++];
            this.axis = values[index++];
        }

        public boolean isDark() {
            return dark;
        }

        public Color page() {
            return page;
        }

        public Color card() {
            return card;
        }

        public Color sidebar() {
            return sidebar;
        }

        public Color sidebarText() {
            return sidebarText;
        }

        public Color sidebarMuted() {
            return sidebarMuted;
        }

        public Color sidebarSelected() {
            return sidebarSelected;
        }

        public Color text() {
            return text;
        }

        public Color muted() {
            return muted;
        }

        /** For the least important text, such as a count or a unit. */
        public Color faint() {
            return faint;
        }

        public Color border() {
            return border;
        }

        public Color accent() {
            return accent;
        }

        public Color accentSoft() {
            return accentSoft;
        }

        /** Text drawn on top of the accent. A bright accent needs dark text. */
        public Color onAccent() {
            return onAccent;
        }

        public Color success() {
            return success;
        }

        public Color warning() {
            return warning;
        }

        public Color danger() {
            return danger;
        }

        public Color info() {
            return info;
        }

        public Color tableHeader() {
            return tableHeader;
        }

        public Color tableGrid() {
            return tableGrid;
        }

        public Color tableSelection() {
            return tableSelection;
        }

        public Color field() {
            return field;
        }

        public Color fieldBorder() {
            return fieldBorder;
        }

        public Color chartGrid() {
            return chartGrid;
        }

        public Color axis() {
            return axis;
        }

        /** The colour a chart should use for a series at a given position. */
        public Color series(int position) {
            Color[] series = {accent, info, success, warning, danger,
                    new Color(139, 92, 246), new Color(236, 72, 153)};
            return series[Math.floorMod(position, series.length)];
        }

        /** How many distinct series colours there are before they repeat. */
        public int seriesCount() {
            return 7;
        }

        /** The colour for a lifecycle state. */
        public Color forState(Object status) {
            if (status == null) {
                return muted();
            }
            String name = status.toString().toLowerCase(java.util.Locale.ROOT);
            if (name.contains("sold") || name.contains("completed") || name.contains("confirmed")
                    || name.contains("settled") || name.contains("paid")
                    || name.contains("contracted") || name.contains("attended")) {
                return success();
            }
            if (name.contains("cancel") || name.contains("void") || name.contains("refund")
                    || name.contains("withdrawn") || name.contains("declined")
                    || name.contains("invalid") || name.contains("duplicate")) {
                return danger();
            }
            if (name.contains("live") || name.contains("progress") || name.contains("running")
                    || name.contains("overdue") || name.contains("waitlist")
                    || name.contains("pending")) {
                return warning();
            }
            return muted();
        }

        /** Whether text in this colour will be readable on the given background. */
        public boolean readable(Color foreground, Color background) {
            return luminance(foreground) - luminance(background) > 0.35;
        }

        /** The relative brightness of a colour, from zero to one. */
        public double luminance(Color colour) {
            return (0.2126 * colour.getRed() + 0.7152 * colour.getGreen()
                    + 0.0722 * colour.getBlue()) / 255.0;
        }

        /** Body text that stays readable on an arbitrary fill. */
        public Color readableOn(Color background) {
            return luminance(background) > 0.55 ? new Color(17, 24, 39) : new Color(241, 245, 249);
        }
    }

    /**
     * The dark palette, and the default.
     *
     * <p>A professional dashboard reads as dark first. The accent is indigo rather than
     * a pure blue, which is warmer under low light and reads less like a web link, and
     * the greys lean blue so the whole interface holds together as one family rather
     * than as several unrelated greys. The semantic colours are brighter than in light
     * mode because a dim green on a dark background looks broken rather than calm.
     */
    private static final Palette DARK = new Palette(true, new Color[]{
            rgb(11, 16, 32),          // page
            rgb(20, 27, 49),          // card
            rgb(7, 11, 24),           // sidebar
            rgb(208, 214, 232),       // sidebar text
            rgb(122, 131, 160),       // sidebar muted
            rgb(46, 57, 99),          // sidebar selected
            rgb(232, 235, 245),       // text
            rgb(139, 147, 176),       // muted
            rgb(97, 106, 138),        // faint
            rgb(38, 48, 79),          // border
            rgb(99, 102, 241),        // accent
            rgb(38, 41, 96),          // accent soft
            rgb(255, 255, 255),       // on accent
            rgb(52, 211, 153),        // success
            rgb(251, 191, 36),        // warning
            rgb(248, 113, 113),       // danger
            rgb(56, 189, 248),        // info
            rgb(24, 31, 56),          // table header
            rgb(38, 48, 79),          // table grid
            rgb(46, 57, 99),          // table selection
            rgb(16, 22, 42),          // field
            rgb(52, 62, 99),          // field border
            rgb(26, 33, 60),          // chart grid
            rgb(97, 106, 138),        // axis
    });

    /**
     * The light palette.
     *
     * <p>Kept deliberately softer than the usual near-white-on-white: the page is a
     * cool off-white, cards are pure white, and the accent is the same indigo so a
     * colour never changes meaning between the two themes.
     */
    private static final Palette LIGHT = new Palette(false, new Color[]{
            rgb(244, 246, 252),       // page
            rgb(255, 255, 255),       // card
            rgb(17, 24, 45),          // sidebar
            rgb(226, 231, 244),       // sidebar text
            rgb(138, 147, 175),       // sidebar muted
            rgb(46, 57, 99),          // sidebar selected
            rgb(23, 27, 44),          // text
            rgb(90, 99, 131),         // muted
            rgb(140, 148, 175),       // faint
            rgb(222, 227, 242),       // border
            rgb(79, 70, 229),         // accent
            rgb(224, 226, 255),       // accent soft
            rgb(255, 255, 255),       // on accent
            rgb(5, 150, 105),         // success
            rgb(217, 119, 6),         // warning
            rgb(220, 38, 38),         // danger
            rgb(2, 132, 199),         // info
            rgb(250, 251, 255),       // table header
            rgb(226, 230, 243),       // table grid
            rgb(224, 226, 255),       // table selection
            rgb(255, 255, 255),       // field
            rgb(198, 205, 226),       // field border
            rgb(238, 240, 249),       // chart grid
            rgb(140, 148, 175),       // axis
    });

    private static Color rgb(int red, int green, int blue) {
        return new Color(red, green, blue);
    }

    /**
     * The palette in force at startup.
     *
     * <p>Dark is the default because that is the state most professional dashboards are
     * judged on, and because it is the one that shows up sloppy colour choices most
     * honestly. The toggle still exists; it is just no longer the thing being tested
     * first.
     */
    private static Palette current = DARK;
    private static final List<Consumer<Palette>> LISTENERS = new ArrayList<>();

    private Theme() {
    }

    /** The palette in force right now. */
    public static Palette current() {
        return current;
    }

    public static boolean isDark() {
        return current.dark;
    }

    /**
     * Switches between light and dark and tells everything that cares.
     *
     * @return true when the theme actually changed
     */
    public static boolean setDark(boolean dark) {
        if (current.dark == dark) {
            return false;
        }
        current = dark ? DARK : LIGHT;
        for (Consumer<Palette> listener : new ArrayList<>(LISTENERS)) {
            listener.accept(current);
        }
        return true;
    }

    public static void toggle() {
        setDark(!current.dark);
    }

    /** Registers a callback run whenever the theme changes. */
    public static void onChange(Consumer<Palette> listener) {
        LISTENERS.add(listener);
    }

    /** Used by the tests to put the theme back. */
    static void resetForTesting() {
        current = DARK;
    }

    // ---------------------------------------------------------------- type

    /**
     * The type the interface is set in, named by what it is for.
     *
     * <p>Fonts are chosen by family name with fallbacks, because the machine a
     * dashboard runs on cannot be assumed to have any particular one. The family is
     * chosen once here and every size is derived from it, so a system that happens to
     * have Inter or Segoe UI uses it everywhere rather than some places using it and
     * some falling back to a noticeably different face.
     */
    public static final class Type {

        /**
         * The font family in order of preference. Inter and Segoe UI are clean modern
         * interface faces; the others are what a Linux machine without them is likely
         * to have. {@code GraphicsEnvironment} is not consulted for every label, so the
         * family is resolved once and reused.
         */
        private static final String FAMILY = chooseFamily();

        private static String chooseFamily() {
            String[] wanted = {"Inter", "Segoe UI", "Helvetica Neue", "Noto Sans",
                    "Ubuntu", "DejaVu Sans", "Liberation Sans", "SansSerif"};
            String[] available = java.awt.GraphicsEnvironment
                    .getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
            for (String preference : wanted) {
                for (String family : available) {
                    if (family.equalsIgnoreCase(preference)) {
                        return family;
                    }
                }
            }
            return Font.SANS_SERIF;
        }

        private Type() {
        }

        /** A font of the resolved family. */
        public static Font of(int style, int size) {
            return new Font(FAMILY, style, size);
        }

        public static Font windowTitle() {
            return of(Font.BOLD, 19);
        }

        public static Font screenTitle() {
            return of(Font.BOLD, 22);
        }

        public static Font cardTitle() {
            return of(Font.BOLD, 13);
        }

        /** The large figure on a dashboard tile. */
        public static Font metric() {
            return of(Font.BOLD, 28);
        }

        public static Font metricSmall() {
            return of(Font.BOLD, 20);
        }

        public static Font body() {
            return of(Font.PLAIN, 13);
        }

        public static Font bodyBold() {
            return of(Font.BOLD, 13);
        }

        public static Font small() {
            return of(Font.PLAIN, 11);
        }

        public static Font smallBold() {
            return of(Font.BOLD, 11);
        }

        public static Font mono() {
            return new Font("JetBrains Mono", Font.PLAIN, 12);
        }

        public static Font sidebar() {
            return of(Font.PLAIN, 13);
        }

        public static Font sidebarSelected() {
            return of(Font.BOLD, 13);
        }

        public static Font tiny() {
            return of(Font.PLAIN, 10);
        }
    }
}
