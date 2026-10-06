package com.eventsuite.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;

/**
 * A type-to-act overlay: everything the system can do, one keyboard command away.
 *
 * <p>This is the answer to "too many buttons". Rather than a toolbar of four or six
 * actions on every screen, there is one key combination that opens a single search box.
 * Type what you want to do — "new event", "sell a ticket", "open finance", "check in"
 * — and the matching action is at the top. Enter runs it, Escape closes it.
 *
 * <p>Actions are matched by fuzzy subsequence rather than by prefix, so "sck" finds
 * "Scan / check in" and "ne" finds "New event". That is what makes it fast to use with
 * one hand while the other holds a scanner or a coffee, which is precisely the
 * situation a check-in desk is in.
 */
public final class CommandPalette extends JDialog {

    private static final long serialVersionUID = 1L;

    /** One thing the system can do, with the words it is found by. */
    public static final class Action {
        private final String label;
        private final String category;
        private final String keywords;
        private final Runnable perform;

        public Action(String label, String category, String keywords, Runnable perform) {
            this.label = label;
            this.category = category;
            this.keywords = keywords == null ? "" : keywords.toLowerCase(java.util.Locale.ROOT);
            this.perform = perform;
        }

        /** The text shown in the list. */
        public String getLabel() {
            return label;
        }

        /** The small text beside it, such as "Ticketing" or "Go to". */
        public String getCategory() {
            return category;
        }

        /** Runs the action. */
        public void run() {
            if (perform != null) {
                perform.run();
            }
        }

        /**
         * Whether this action matches a query.
         *
         * <p>A query matches when every character appears in order somewhere in the
         * label or its keywords. "sell tick" finds "Sell a ticket"; "st" does too. The
         * keyword list is there so a match on meaning, not just on words, works: "door"
         * finds the check-in screen even though that word is not in its label.
         */
        public boolean matches(String query) {
            String needle = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
            if (needle.isEmpty()) {
                return true;
            }
            String hay = (label + " " + category + " " + keywords).toLowerCase(java.util.Locale.ROOT);
            // Every space-separated word of the query must match somewhere.
            for (String word : needle.split("\\s+")) {
                if (word.isEmpty()) {
                    continue;
                }
                if (!subsequence(word, hay)) {
                    return false;
                }
            }
            return true;
        }

        /** Whether the characters of one string appear, in order, in another. */
        private static boolean subsequence(String needle, String haystack) {
            int at = 0;
            for (char wanted : needle.toCharArray()) {
                at = haystack.indexOf(wanted, at);
                if (at < 0) {
                    return false;
                }
                at++;
            }
            return true;
        }

        /**
         * How close the match is, lower being better.
         *
         * <p>Shorter labels and matches near the start win, so "Finance" sorts above
         * "Financial reconciliation report" when somebody types "fin". This is what
         * makes the top hit almost always the one they meant.
         */
        public int scoreFor(String query) {
            String needle = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
            if (needle.isEmpty()) {
                return 0;
            }
            String hay = (label + " " + category).toLowerCase(java.util.Locale.ROOT);
            int position = hay.indexOf(needle.split("\\s+")[0]);
            return (position < 0 ? 100 : position) + label.length();
        }
    }

    private final JTextField query = Ui.field("");
    private final DefaultListModel<Action> model = new DefaultListModel<>();
    private final JList<Action> list = new JList<>(model);
    private final List<Action> all = new ArrayList<>();
    private final JLabel count = Ui.muted("");

    public CommandPalette(java.awt.Window parent) {
        super(parent, "Commands", java.awt.Dialog.ModalityType.MODELESS);
        // Undecorated and opaque, not transparent: a fully transparent background makes
        // the whole window vanish on some compositors, which is exactly the failure
        // that looks like the palette not opening at all.
        setUndecorated(true);
        setBackground(Theme.current().card());
        setFocusableWindowState(true);

        JPanel card = new JPanel(new BorderLayout(0, 8));
        card.setBackground(Theme.current().card());
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.current().border(), 1, true),
                BorderFactory.createEmptyBorder(12, 14, 12, 14)));

        query.setPreferredSize(new Dimension(560, 40));
        query.setFont(Theme.Type.screenTitle());
        query.putClientProperty("JTextField.placeholderText", "Type a command or a screen");

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setBackground(Theme.current().card());
        list.setForeground(Theme.current().text());
        list.setFont(Theme.Type.body());
        list.setCellRenderer(new ActionRenderer());
        list.setBorder(BorderFactory.createEmptyBorder(6, 4, 6, 4));

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        bottom.add(Ui.muted("Enter to run  ·  Up/Down to choose  ·  Esc to close"), BorderLayout.WEST);
        bottom.add(count, BorderLayout.EAST);

        card.add(query, BorderLayout.NORTH);
        card.add(new JScrollPane(list) {
            {
                setBorder(BorderFactory.createEmptyBorder());
                setPreferredSize(new Dimension(560, 320));
                getViewport().setBackground(Theme.current().card());
            }
        }, BorderLayout.CENTER);
        card.add(bottom, BorderLayout.SOUTH);

        setContentPane(card);
        setSize(600, 460);

        query.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent event) {
                refresh();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent event) {
                refresh();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent event) {
                refresh();
            }
        });

        query.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                switch (event.getKeyCode()) {
                    case KeyEvent.VK_ENTER -> runSelected();
                    case KeyEvent.VK_ESCAPE -> close();
                    case KeyEvent.VK_DOWN -> move(1);
                    case KeyEvent.VK_UP -> move(-1);
                    default -> {
                        // Other keys are typed into the field as normal.
                    }
                }
            }
        });

        list.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent event) {
                if (event.getClickCount() == 2) {
                    runSelected();
                }
            }
        });

        // Focus is requested once the window is actually visible, because a
        // requestFocusInWindow issued before that fails silently and the first
        // keystrokes land on the screen behind instead of in the box.
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowOpened(java.awt.event.WindowEvent event) {
                query.requestFocusInWindow();
            }
        });
    }

    /** Replaces the set of things the palette can do. */
    public void setActions(List<Action> actions) {
        all.clear();
        if (actions != null) {
            all.addAll(actions);
        }
        refresh();
    }

    /** Opens the palette centred on its parent. */
    public void open() {
        query.setText("");
        refresh();
        if (!all.isEmpty()) {
            list.setSelectedIndex(0);
        }
        pack();
        setSize(600, 460);
        setLocationRelativeTo(getParent());
        // Always on top while it is open: a palette that can slide behind the window it
        // is helping you with is useless in the exact moment you want it.
        setAlwaysOnTop(true);
        setVisible(true);
        query.selectAll();
    }

    /** Closes the palette. */
    public void close() {
        setVisible(false);
    }

    private void move(int delta) {
        int index = list.getSelectedIndex();
        int next = Math.max(0, Math.min(model.getSize() - 1, index + delta));
        list.setSelectedIndex(next);
        list.ensureIndexIsVisible(next);
    }

    private void runSelected() {
        Action chosen = list.getSelectedValue();
        if (chosen == null && model.getSize() > 0) {
            list.setSelectedIndex(0);
            chosen = list.getSelectedValue();
        }
        close();
        if (chosen != null) {
            // Deferred: the palette must close before the action runs, or a dialog the
            // action opens would be parented behind it.
            final Action action = chosen;
            SwingUtilities.invokeLater(action::run);
        }
    }

    private void refresh() {
        String typed = query.getText();
        List<Action> matches = new ArrayList<>();
        for (Action action : all) {
            if (action.matches(typed)) {
                matches.add(action);
            }
        }
        matches.sort((left, right) -> {
            int byScore = Integer.compare(left.scoreFor(typed), right.scoreFor(typed));
            return byScore != 0 ? byScore : left.getLabel().compareTo(right.getLabel());
        });
        model.clear();
        for (Action action : matches) {
            model.addElement(action);
        }
        if (!matches.isEmpty()) {
            list.setSelectedIndex(0);
        }
        count.setText(matches.size() + " action" + (matches.size() == 1 ? "" : "s"));
    }

    /** Draws an action in the list: the label, its category beside it, and a match. */
    private static final class ActionRenderer extends DefaultListCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                                                      int index, boolean selected,
                                                      boolean focused) {
            JPanel panel = new JPanel(new BorderLayout(12, 0));
            panel.setOpaque(true);
            panel.setBackground(selected ? Theme.current().tableSelection()
                    : Theme.current().card());
            panel.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
            panel.setPreferredSize(new Dimension(10, 40));

            if (value instanceof Action action) {
                JLabel label = new JLabel(action.getLabel());
                label.setFont(Theme.Type.body());
                label.setForeground(Theme.current().text());
                panel.add(label, BorderLayout.CENTER);

                JLabel category = new JLabel(action.getCategory());
                category.setFont(Theme.Type.small());
                category.setForeground(selected ? Theme.current().accent()
                        : Theme.current().muted());
                panel.add(category, BorderLayout.EAST);
            }
            return panel;
        }
    }
}
