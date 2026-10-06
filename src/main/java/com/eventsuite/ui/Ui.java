package com.eventsuite.ui;

import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.Window;
import java.util.List;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.Border;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;

/**
 * Builders for the widgets every screen uses.
 *
 * <p>Screens are written as arrangements rather than as Swing configuration, because
 * the configuration is where the visual inconsistency lives. If each screen sets its
 * own padding and its own renderer, the application drifts: one table has taller rows
 * than another and nobody can say why. Everything here reads from {@link Theme}, so
 * a change lands everywhere.
 *
 * <p>The button style is flat with no border until it is hovered, which is what stops
 * a window with forty controls looking like a form to be filled in rather than a tool
 * to be used.
 */
public final class Ui {

    private Ui() {
    }

    // ---------------------------------------------------------------- text

    /** A heading for a screen. */
    public static JLabel screenTitle(String text) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.Type.screenTitle());
        label.setForeground(Theme.current().text());
        return label;
    }

    /** A smaller heading, for a card or a group within a screen. */
    public static JLabel cardTitle(String text) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.Type.cardTitle());
        label.setForeground(Theme.current().text());
        return label;
    }

    /** A label in bold body type, for a name in a list. */
    public static JLabel bodyBold(String text) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.Type.bodyBold());
        label.setForeground(Theme.current().text());
        return label;
    }

    /** A label in the ordinary body size. */
    public static JLabel body(String text) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.Type.body());
        label.setForeground(Theme.current().text());
        return label;
    }

    /** A label in the quieter colour, for a hint or a secondary fact. */
    public static JLabel muted(String text) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.Type.small());
        label.setForeground(Theme.current().muted());
        return label;
    }

    /** A label showing a status, coloured to match what the status means. */
    public static JLabel status(Object status) {
        JLabel label = new JLabel(String.valueOf(status));
        label.setFont(Theme.Type.smallBold());
        label.setForeground(Theme.current().forState(status));
        return label;
    }

    /** A one line summary of a health figure, coloured by whether it is a problem. */
    public static JLabel health(String text, boolean problem) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.Type.small());
        label.setForeground(problem ? Theme.current().warning() : Theme.current().muted());
        return label;
    }

    /** A label whose value is a money figure. */
    public static JLabel money(BigDecimal amount) {
        JLabel label = new JLabel(com.eventsuite.finance.Money.format(amount));
        label.setFont(Theme.Type.bodyBold());
        label.setForeground(Theme.current().text());
        return label;
    }

    /** A label whose value is a money figure, coloured for a negative amount. */
    public static JLabel signedMoney(BigDecimal amount) {
        JLabel label = new JLabel(com.eventsuite.finance.Money.signed(amount));
        label.setFont(Theme.Type.bodyBold());
        label.setForeground(amount.signum() < 0
                ? Theme.current().danger() : Theme.current().text());
        return label;
    }

    // ---------------------------------------------------------------- layout

    /** A vertical stack with consistent spacing between its children. */
    public static JPanel column(Component... children) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (Component child : children) {
            if (child == null) {
                panel.add(Box.createVerticalStrut(8));
                continue;
            }
            // Every child is stretched to the width of the column. Without this a child
            // keeps its own preferred width, and something built around a control with a
            // small preferred size — a progress bar, a chart — ends up narrow and sits
            // to one side of the screen with a gap beside it. Stating it here means no
            // individual screen has to remember.
            if (child instanceof JComponent component) {
                component.setAlignmentX(Component.LEFT_ALIGNMENT);
                Dimension maximum = component.getMaximumSize();
                if (maximum == null || maximum.width < Integer.MAX_VALUE) {
                    component.setMaximumSize(new Dimension(Integer.MAX_VALUE,
                            Math.max(1, component.getPreferredSize().height)));
                }
            }
            panel.add(child);
        }
        return panel;
    }

    /** A horizontal row with consistent spacing, left aligned. */
    public static JPanel row(Component... children) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        panel.setOpaque(false);
        for (Component child : children) {
            if (child != null) {
                panel.add(child);
            }
        }
        return panel;
    }

    /** A row that stretches its last component to fill the width. */
    public static JPanel rowFill(Component last, Component... leading) {
        JPanel panel = new JPanel(new BorderLayout(8, 0));
        panel.setOpaque(false);
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        left.setOpaque(false);
        for (Component child : leading) {
            if (child != null) {
                left.add(child);
            }
        }
        panel.add(left, BorderLayout.WEST);
        if (last != null) {
            panel.add(last, BorderLayout.CENTER);
        }
        return panel;
    }

    /** A card with a title and an explanation of what it shows. */
    public static JPanel describedCard(String title, String explanation, Component content) {
        JPanel panel = card(title);
        if (explanation != null && !explanation.isBlank()) {
            JLabel note = muted(explanation);
            note.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(note);
            panel.add(Box.createVerticalStrut(10));
        }
        if (content != null) {
            panel.add(content);
        }
        return panel;
    }

    /** A card: a titled group with a background and a border. */
    public static JPanel card(String title, Component... children) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(Theme.current().card());
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.current().border()),
                BorderFactory.createEmptyBorder(14, 16, 14, 16)));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        // Stretched to the width the screen gives it, but free to grow as tall as its
        // contents. Without this a card inside a BoxLayout column keeps its preferred
        // width, which is narrower than the screen, so it sits to one side with a gap
        // beside it and the page looks ragged.
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        if (title != null && !title.isBlank()) {
            panel.add(cardTitle(title));
            panel.add(Box.createVerticalStrut(10));
        }
        for (Component child : children) {
            panel.add(child);
        }
        return panel;
    }

    /** A grid of equally sized cards. */
    public static JPanel cardGrid(int columns, Component... children) {
        JPanel grid = new JPanel(new GridLayout(0, Math.max(1, columns), 12, 12));
        grid.setOpaque(false);
        for (Component child : children) {
            grid.add(child);
        }
        return grid;
    }

    /** A scrollable container with no visible frame and no background. */
    public static JScrollPane scroll(Component content) {
        JScrollPane pane = new JScrollPane(content);
        pane.setBorder(BorderFactory.createEmptyBorder());
        pane.getViewport().setOpaque(false);
        pane.setOpaque(false);
        pane.getVerticalScrollBar().setUnitIncrement(16);
        return pane;
    }

    /** A screen: title, subtitle and content, padded consistently. */
    public static JPanel screen(String title, String subtitle, Component content) {
        JPanel panel = new JPanel(new BorderLayout(0, 14));
        panel.setBackground(Theme.current().page());
        panel.setBorder(BorderFactory.createEmptyBorder(22, 24, 22, 24));

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(screenTitle(title));
        if (subtitle != null && !subtitle.isBlank()) {
            JLabel sub = muted(subtitle);
            sub.setAlignmentX(Component.LEFT_ALIGNMENT);
            sub.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
            header.add(sub);
        }
        panel.add(header, BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);
        body.add(scroll(content), BorderLayout.CENTER);
        panel.add(body, BorderLayout.CENTER);
        return panel;
    }

    /** Empty space of a fixed height, for separating groups. */
    public static Component gap(int height) {
        return Box.createVerticalStrut(height);
    }

    // ---------------------------------------------------------------- controls

    /** The main action button: filled with the accent colour. */
    public static JButton primary(String text, Runnable action) {
        JButton button = new FlatButton(text, action, true);
        button.setFont(Theme.Type.bodyBold());
        return button;
    }

    /** An ordinary action. */
    public static JButton button(String text, Runnable action) {
        JButton button = new FlatButton(text, action, false);
        button.setFont(Theme.Type.body());
        return button;
    }

    /** A smaller button, for inside a card. */
    public static JButton smallButton(String text, Runnable action) {
        JButton button = new FlatButton(text, action, false);
        button.setFont(Theme.Type.small());
        return button;
    }

    /** A button that starts disabled, for an action that is not yet possible. */
    public static JButton disabledButton(String text, Runnable action) {
        JButton button = smallButton(text, action);
        button.setEnabled(false);
        return button;
    }

    /** A single-line text field, themed. */
    public static JTextField field(String initial) {
        JTextField field = new JTextField(initial == null ? "" : initial);
        field.setFont(Theme.Type.body());
        field.setBackground(Theme.current().field());
        field.setForeground(Theme.current().text());
        field.setCaretColor(Theme.current().accent());
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.current().fieldBorder()),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        field.setPreferredSize(new Dimension(180, 30));
        return field;
    }

    /** A search field, with room for a placeholder. */
    public static JTextField searchField(String placeholder) {
        JTextField field = field("");
        field.setPreferredSize(new Dimension(240, 30));
        field.putClientProperty("JTextField.placeholderText", placeholder);
        field.getAccessibleContext().setAccessibleName(placeholder);
        return field;
    }

    /** A multi-line area for comments and notes. */
    public static JTextArea textArea(int rows) {
        JTextArea area = new JTextArea(rows, 40);
        area.setFont(Theme.Type.body());
        area.setBackground(Theme.current().field());
        area.setForeground(Theme.current().text());
        area.setCaretColor(Theme.current().accent());
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.current().fieldBorder()),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        return area;
    }

    /** A drop-down, themed. */
    public static <T> JComboBox<T> combo(java.util.List<T> options, T selected) {
        JComboBox<T> box = new JComboBox<>();
        for (T option : options) {
            box.addItem(option);
        }
        if (selected != null) {
            box.setSelectedItem(selected);
        }
        box.setFont(Theme.Type.body());
        box.setBackground(Theme.current().field());
        box.setForeground(Theme.current().text());
        box.setBorder(BorderFactory.createLineBorder(Theme.current().fieldBorder()));
        return box;
    }

    /** A drop-down of enum values, starting at the given one. */
    public static <T extends Enum<T>> JComboBox<T> enumCombo(Class<T> type, T selected) {
        JComboBox<T> box = new JComboBox<>(type.getEnumConstants());
        if (selected != null) {
            box.setSelectedItem(selected);
        }
        box.setFont(Theme.Type.body());
        box.setBackground(Theme.current().field());
        box.setForeground(Theme.current().text());
        return box;
    }

    /** A checkbox. */
    public static JCheckBox check(String text, boolean selected) {
        JCheckBox box = new JCheckBox(text, selected);
        box.setFont(Theme.Type.body());
        box.setBackground(Theme.current().card());
        box.setForeground(Theme.current().text());
        box.setFocusPainted(false);
        return box;
    }

    /** A number spinner. */
    public static JSpinner number(int value, int minimum, int maximum) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, minimum, maximum, 1));
        spinner.setFont(Theme.Type.body());
        spinner.setBackground(Theme.current().field());
        spinner.setForeground(Theme.current().text());
        spinner.setBorder(BorderFactory.createLineBorder(Theme.current().fieldBorder()));
        spinner.setPreferredSize(new Dimension(90, 30));
        return spinner;
    }

    // ---------------------------------------------------------------- tables

    /**
     * A table with the application's row heights, borders and selection colours.
     *
     * <p>Row height is set explicitly because the default is derived from a font
     * Swing picks itself, which differs between machines and leaves the tables looking
     * inconsistent against the cards around them.
     */
    public static JTable table(TableModel model) {
        JTable table = new JTable(model);
        table.setFont(Theme.Type.body());
        table.setRowHeight(32);
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setSelectionBackground(Theme.current().tableSelection());
        table.setSelectionForeground(Theme.current().text());
        table.setBackground(Theme.current().card());
        table.setForeground(Theme.current().text());
        table.setFillsViewportHeight(true);
        table.setAutoCreateRowSorter(false);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        table.setFocusable(true);
        // Zebra rows. A table of forty identical rows is a wall of text; alternating
        // shading is what lets the eye follow one across without losing its place.
        table.setDefaultRenderer(Object.class, new ZebraRenderer());

        JTableHeader header = table.getTableHeader();
        header.setFont(Theme.Type.smallBold());
        header.setBackground(Theme.current().tableHeader());
        header.setForeground(Theme.current().muted());
        header.setReorderingAllowed(false);
        header.setPreferredSize(new Dimension(10, 34));
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0,
                Theme.current().border()));
        return table;
    }

    /**
     * The default cell renderer: zebra-shaded rows with a little padding.
     *
     * <p>Set as the renderer for the whole table rather than per column, so a column
     * that has not been given a special renderer — a status, money, or muted one —
     * still picks up the shading. The custom renderers layered on top inherit from it.
     */
    private static final class ZebraRenderer extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean selected, boolean focused,
                                                       int row, int column) {
            Component component = super.getTableCellRendererComponent(table, value,
                    selected, focused, row, column);
            setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
            if (!selected) {
                boolean even = row % 2 == 0;
                component.setBackground(even ? Theme.current().card()
                        : withAlpha(Theme.current().accentSoft(), 28));
                component.setForeground(Theme.current().text());
            }
            return component;
        }
    }

    /** A colour with an alpha applied, for subtle shading. */
    static Color withAlpha(Color colour, int alpha) {
        return new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), alpha);
    }

    /** A table wrapped in a scroll pane that has no border of its own. */
    public static JComponent scrollTable(JTable table) {
        return scroll(table);
    }

    /** A renderer that paints text in the muted colour. */
    public static TableCellRenderer mutedRenderer() {
        return new MutedRenderer();
    }

    /** A renderer that colours a cell by its status. */
    public static TableCellRenderer statusRenderer() {
        return new StatusRenderer();
    }

    /** A renderer that right-aligns, for money and counts. */
    public static TableCellRenderer rightRenderer() {
        return new RightRenderer();
    }

    // ---------------------------------------------------------------- tiles

    /**
     * A dashboard tile: a label, a big figure and an optional note under it.
     *
     * <p>Used for every headline number so they all read at the same weight. A figure
     * that matters more is given a larger tile rather than a bolder font, which keeps
     * the grid regular.
     */
    public static JPanel tile(String label, String value, String note) {
        return tile(label, value, note, Theme.current().text(), 0);
    }

    /** A tile whose figure is coloured, for good news and bad. */
    public static JPanel tile(String label, String value, String note, Color valueColour) {
        return tile(label, value, note, valueColour, 0);
    }

    /** A tile with a progress bar under it, for figures with a target. */
    public static JPanel tileWithBar(String label, String value, String note, int percent) {
        return tile(label, value, note, Theme.current().text(), Math.max(0, Math.min(100, percent)));
    }

    private static JPanel tile(String label, String value, String note, Color valueColour,
                               int barPercent) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(Theme.current().card());
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.current().border()),
                BorderFactory.createEmptyBorder(14, 16, 14, 16)));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel caption = muted(label);
        caption.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(caption);

        panel.add(Box.createVerticalStrut(6));
        JLabel figure = new JLabel(value);
        figure.setFont(Theme.Type.metric());
        figure.setForeground(valueColour);
        figure.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(figure);

        if (barPercent > 0) {
            panel.add(Box.createVerticalStrut(10));
            panel.add(new ProgressBar(barPercent));
        }

        if (note != null && !note.isBlank()) {
            panel.add(Box.createVerticalStrut(6));
            JLabel noteLabel = muted(note);
            noteLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(noteLabel);
        }
        return panel;
    }

    /** A one-line coloured bar showing how much of something has happened. */
    public static JPanel bar(String text, int percent, Color colour) {
        JPanel panel = new JPanel(new BorderLayout(8, 0));
        panel.setOpaque(false);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(muted(text), BorderLayout.WEST);
        panel.add(new ProgressBar(percent, colour), BorderLayout.CENTER);
        return panel;
    }

    /** A message shown where content would be, when there is nothing to show. */
    public static JPanel emptyState(String message, String hint) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(Box.createVerticalStrut(28));
        JLabel main = new JLabel(message);
        main.setFont(Theme.Type.bodyBold());
        main.setForeground(Theme.current().muted());
        main.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(main);
        if (hint != null && !hint.isBlank()) {
            panel.add(Box.createVerticalStrut(6));
            JLabel sub = muted(hint);
            sub.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(sub);
        }
        return panel;
    }

    /** A bordered section with a heading, an explanation and content. */
    public static JPanel titledGroup(String title, String explanation, Component content) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel heading = body(title);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(heading);
        if (explanation != null && !explanation.isBlank()) {
            JLabel note = muted(explanation);
            note.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(note);
        }
        panel.add(Box.createVerticalStrut(6));
        panel.add(content);
        return panel;
    }

    /** A bordered section with a heading and content. */
    public static JPanel titledGroup(String title, Component content) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel heading = body(title);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(heading);
        panel.add(Box.createVerticalStrut(6));
        panel.add(content);
        return panel;
    }

    // ---------------------------------------------------------------- renderers

    /** Flat button: no border until hovered, filled when primary. */
    private static final class FlatButton extends JButton {
        private static final long serialVersionUID = 1L;

        private final boolean primary;

        FlatButton(String text, Runnable action, boolean primary) {
            super(text);
            this.primary = primary;
            setFont(Theme.Type.body());
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(BorderFactory.createEmptyBorder(7, 14, 7, 14));
            if (action != null) {
                addActionListener(event -> action.run());
            }
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent event) {
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent event) {
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D pen = (Graphics2D) graphics.create();
            pen.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            Color fill = fillFor(getModel().isPressed(), getModel().isRollover(), isEnabled());
            int width = getWidth();
            int height = getHeight();
            if (fill != null) {
                pen.setColor(fill);
                pen.fillRoundRect(0, 0, width, height, 8, 8);
            }
            if (!primary && getModel().isRollover() && isEnabled()) {
                pen.setColor(Theme.current().border());
                pen.drawRoundRect(0, 0, width - 1, height - 1, 8, 8);
            }
            pen.dispose();
            super.paintComponent(graphics);
        }

        private Color fillFor(boolean pressed, boolean hovered, boolean enabled) {
            if (!enabled) {
                return null;
            }
            if (primary) {
                return pressed ? Theme.current().accent().darker()
                        : hovered ? Theme.current().accent().brighter()
                        : Theme.current().accent();
            }
            return hovered ? Theme.current().accentSoft() : null;
        }

        @Override
        public Color getForeground() {
            if (!isEnabled()) {
                return Theme.current().faint();
            }
            return primary ? Theme.current().onAccent() : Theme.current().accent();
        }
    }

    /** A horizontal bar showing a percentage. */
    private static final class ProgressBar extends JComponent {
        private static final long serialVersionUID = 1L;

        private final int percent;
        private final Color colour;

        ProgressBar(int percent) {
            this(percent, Theme.current().accent());
        }

        ProgressBar(int percent, Color colour) {
            this.percent = percent;
            this.colour = colour;
            setPreferredSize(new Dimension(10, 6));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 6));
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D pen = (Graphics2D) graphics.create();
            pen.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            int width = getWidth();
            int height = getHeight();
            pen.setColor(Theme.current().chartGrid());
            pen.fillRoundRect(0, 0, width, height, height, height);
            int filled = (int) Math.round(width * percent / 100.0);
            if (filled > 0) {
                pen.setColor(colour);
                pen.fillRoundRect(0, 0, Math.max(height, filled), height, height, height);
            }
            pen.dispose();
        }
    }

    /** Paints text in the muted colour. */
    private static final class MutedRenderer extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean selected, boolean focused,
                                                       int row, int column) {
            Component component = super.getTableCellRendererComponent(table, value, selected,
                    focused, row, column);
            if (!selected) {
                component.setForeground(Theme.current().muted());
            }
            setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
            return component;
        }
    }

    /** Colours a cell by what the text says about its status. */
    private static final class StatusRenderer extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean selected, boolean focused,
                                                       int row, int column) {
            Component component = super.getTableCellRendererComponent(table, value, selected,
                    focused, row, column);
            if (!selected && value != null) {
                component.setForeground(Theme.current().forState(value));
                Font font = Theme.Type.smallBold();
                component.setFont(font);
            }
            setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
            return component;
        }
    }

    /** Right-aligns a cell, for money and counts. */
    private static final class RightRenderer extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean selected, boolean focused,
                                                       int row, int column) {
            Component component = super.getTableCellRendererComponent(table, value, selected,
                    focused, row, column);
            setHorizontalAlignment(RIGHT);
            setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
            return component;
        }
    }

    // ---------------------------------------------------------------- dialogs

    /**
     * Says something went wrong, and why.
     *
     * <p>Every failure in the application ends here. The message is the one the
     * operation produced rather than a generic apology, because a person who cannot
     * tell what the problem was will do the same thing again and hit the same wall.
     */
    public static void dialog(Component parent, String title, String message) {
        JOptionPane.showMessageDialog(asWindow(parent),
                "<html><div style='width:380px'>" + html(message) + "</div></html>",
                title, JOptionPane.WARNING_MESSAGE);
    }

    /** Asks a yes or no question. Returns true only when yes was chosen. */
    public static boolean confirm(Component parent, String title, String message) {
        int answer = JOptionPane.showConfirmDialog(asWindow(parent),
                "<html><div='width:380px'>" + html(message) + "</div></html>",
                title, JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        return answer == JOptionPane.YES_OPTION;
    }

    /**
     * Shows a form and waits for a button.
     *
     * @return the text of the button that was pressed, or null when cancelled
     */
    public static String formDialog(Component parent, String title, Component form,
                                    String accept, String cancel) {
        FormDialog dialog = new FormDialog(asWindow(parent), title, form, accept, cancel);
        dialog.setVisible(true);
        return dialog.getResult();
    }

    /**
     * Asks somebody to pick one of a list.
     *
     * @return the chosen item, or null when cancelled
     */
    public static <T> T choose(Component parent, String title, String explanation,
                               List<T> options) {
        if (options == null || options.isEmpty()) {
            dialog(parent, title, "There is nothing to choose from yet.");
            return null;
        }
        Object answer = JOptionPane.showInputDialog(asWindow(parent),
                "<html><div style='width:360px'>" + html(explanation) + "</div></html>",
                title, JOptionPane.QUESTION_MESSAGE, null,
                options.toArray(), options.get(0));
        return answer == null ? null : options.get(options.indexOf(answer));
    }

    /** The frame a dialog should be centred on, or null to let Swing choose. */
    private static Window asWindow(Component parent) {
        if (parent instanceof Window window) {
            return window;
        }
        return parent == null ? null : javax.swing.SwingUtilities.getWindowAncestor(parent);
    }

    /** Escapes text so a stray angle bracket cannot break the HTML in a message. */
    private static String html(String message) {
        if (message == null) {
            return "";
        }
        return message.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\n", "<br>");
    }

    /**
     * A modal form that reports which button closed it.
     *
     * <p>A plain {@code JDialog} has no notion of a result, so the button that was
     * pressed is held here and read after the dialog closes. Holding it in the dialog
     * rather than in a field on the screen that opened it is what keeps the two
     * independent: a form can be shown from anywhere and still report correctly.
     */
    private static final class FormDialog extends JDialog {
        private static final long serialVersionUID = 1L;

        private String result;

        FormDialog(Window parent, String title, Component form, String accept, String cancel) {
            super(parent, title, ModalityType.APPLICATION_MODAL);
            JPanel body = new JPanel(new BorderLayout(0, 12));
            body.setBackground(Theme.current().card());
            body.setBorder(BorderFactory.createEmptyBorder(18, 20, 14, 20));
            body.add(scroll(form), BorderLayout.CENTER);

            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            buttons.setOpaque(false);
            buttons.add(button(cancel, () -> closeWith(null)));
            buttons.add(primary(accept, () -> closeWith(accept)));
            body.add(buttons, BorderLayout.SOUTH);

            setContentPane(body);
            setSize(600, 640);
            setMinimumSize(new Dimension(520, 400));
            setLocationRelativeTo(parent);
        }

        private void closeWith(String chosen) {
            this.result = chosen;
            setVisible(false);
            dispose();
        }

        String getResult() {
            return result;
        }
    }

    /** Turns anti-aliasing on for text and shapes, which Swing leaves off by default. */
    public static Graphics2D antialiased(Graphics graphics) {
        Graphics2D pen = (Graphics2D) graphics.create();
        pen.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        pen.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        pen.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                RenderingHints.VALUE_STROKE_PURE);
        return pen;
    }

    /** Trims text to a width, adding an ellipsis, for labels that must not grow. */
    public static String ellipsise(String text, int characters) {
        if (text == null) {
            return "";
        }
        return text.length() <= characters ? text
                : text.substring(0, Math.max(0, characters - 1)) + "\u2026";
    }
}
