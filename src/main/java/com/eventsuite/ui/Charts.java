package com.eventsuite.ui;

import com.eventsuite.analytics.TrendPoint;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JComponent;

/**
 * The charts the dashboards are drawn with.
 *
 * <p>Painted directly rather than through a charting library, because there is no
 * build tool here and no library to depend on, and because the four charts needed are
 * simple enough that drawing them is less code than configuring one. Each is a
 * {@link JComponent} that repaints when the data is replaced, which is what lets the
 * dashboard refresh on a timer without rebuilding the panel.
 *
 * <p>Two decisions are worth stating because they are what make these readable rather
 * than merely correct. The vertical axis always starts at zero, because a chart that
 * exaggerates a small difference is the fastest way to lose a reader's trust. And a
 * chart with no data says so in words instead of drawing an empty frame, because an
 * empty axis looks like a fault.
 */
public final class Charts {

    private Charts() {
    }

    /**
     * The shortest a chart is allowed to be drawn.
     *
     * <p>Axis labels, a data label and a legend all need room. Below roughly this the
     * chart cannot be read at all, and a squashed chart is worse than no chart because
     * it looks like a card that has quietly failed.
     */
    private static final int MINIMUM_HEIGHT = 150;

    /** Space left around the plot for the axis labels. */
    private static final int MARGIN_LEFT = 52;

    private static final int MARGIN_RIGHT = 16;
    private static final int MARGIN_TOP = 14;
    private static final int MARGIN_BOTTOM = 28;

    // ---------------------------------------------------------------- line

    /**
     * A line chart over time, with the area under it filled.
     *
     * <p>Used for the arrival curve and the running ticket takings. The fill is there
     * to make a cumulative figure read as accumulating, which a bare line does not
     * convey.
     */
    public static final class Line extends JComponent {
        private static final long serialVersionUID = 1L;

        /** Declared as {@link ArrayList} so the field is concretely serialisable. */
        private final ArrayList<TrendPoint> points = new ArrayList<>();
        private String caption = "";

        public Line() {
            setPreferredSize(new Dimension(520, 190));
            // A minimum as well as a preferred size, because a chart nested inside two
            // GridLayouts gets squeezed by whatever space is left over. Without a floor
            // it ends up ten pixels tall and looks like a card with no data in it.
            setMinimumSize(new Dimension(200, MINIMUM_HEIGHT));
            setOpaque(false);
        }

        /** Replaces the data. Empty is allowed and drawn as "no data". */
        public Line with(List<TrendPoint> data) {
            points.clear();
            if (data != null) {
                points.addAll(data);
            }
            repaint();
            return this;
        }

        /** Sets the label under the chart, such as "arrivals per hour". */
        public Line captioned(String text) {
            this.caption = text == null ? "" : text;
            repaint();
            return this;
        }

        public boolean isEmpty() {
            return points.size() < 2;
        }

        /** How many points are loaded. Used when checking a rendered screen. */
        public int getPointCount() {
            return points.size();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D pen = Ui.antialiased(graphics);
            int width = getWidth();
            int height = getHeight();
            int plotWidth = width - MARGIN_LEFT - MARGIN_RIGHT;
            int plotHeight = height - MARGIN_TOP - MARGIN_BOTTOM;

            if (plotWidth <= 10 || plotHeight <= 10) {
                pen.dispose();
                return;
            }
            if (points.size() < 2) {
                drawNoData(pen, width, height);
                pen.dispose();
                return;
            }

            double maximum = 0;
            for (TrendPoint point : points) {
                maximum = Math.max(maximum, point.getValue());
            }
            // A flat line at zero would otherwise divide by zero when scaled. Rounding
            // the axis up to one gives a readable baseline instead.
            double top = maximum <= 0 ? 1 : niceCeiling(maximum);

            drawGrid(pen, plotWidth, plotHeight, top, formatValue(top));

            GeneralPath line = new GeneralPath();
            GeneralPath fill = new GeneralPath();
            for (int index = 0; index < points.size(); index++) {
                double x = MARGIN_LEFT + plotWidth * index / (double) (points.size() - 1);
                double y = MARGIN_TOP + plotHeight
                        * (1 - points.get(index).getValue() / top);
                if (index == 0) {
                    line.moveTo(x, y);
                    fill.moveTo(x, MARGIN_TOP + plotHeight);
                    fill.lineTo(x, y);
                } else {
                    line.lineTo(x, y);
                    fill.lineTo(x, y);
                }
            }
            fill.lineTo(MARGIN_LEFT + plotWidth, MARGIN_TOP + plotHeight);
            fill.closePath();

            pen.setColor(withAlpha(Theme.current().accent(), 40));
            pen.fill(fill);

            pen.setColor(Theme.current().accent());
            pen.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            pen.draw(line);

            drawPointMarkers(pen, points.size(), plotWidth, plotHeight, top);
            drawXLabels(pen, points, plotWidth, plotHeight);
            drawCaption(pen, width, height);
            pen.dispose();
        }

        private void drawPointMarkers(Graphics2D pen, int count, int plotWidth,
                                      int plotHeight, double top) {
            // Markers on every point would be noise past about twenty of them, so they
            // are thinned out and the last point is always marked: that is the one
            // somebody reading the chart actually wants.
            int step = Math.max(1, count / 16);
            for (int index = 0; index < count; index++) {
                if (index % step != 0 && index != count - 1) {
                    continue;
                }
                double x = MARGIN_LEFT + plotWidth * index / (double) (count - 1);
                double y = MARGIN_TOP + plotHeight * (1 - points.get(index).getValue() / top);
                pen.setColor(Theme.current().card());
                pen.fill(new Ellipse2D.Double(x - 3.5, y - 3.5, 7, 7));
                pen.setColor(Theme.current().accent());
                pen.setStroke(new BasicStroke(1.8f));
                pen.draw(new Ellipse2D.Double(x - 3.5, y - 3.5, 7, 7));
            }
        }

        private void drawGrid(Graphics2D pen, int plotWidth, int plotHeight, double top,
                              String topLabel) {
            pen.setFont(Theme.Type.tiny());
            pen.setColor(Theme.current().chartGrid());
            pen.setStroke(new BasicStroke(1f));
            for (int step = 0; step <= 4; step++) {
                int y = MARGIN_TOP + plotHeight * step / 4;
                pen.drawLine(MARGIN_LEFT, y, MARGIN_LEFT + plotWidth, y);
                String label = step == 4 ? topLabel : formatValue(top * (4 - step) / 4.0);
                pen.setColor(Theme.current().axis());
                FontMetrics metrics = pen.getFontMetrics();
                pen.drawString(label, MARGIN_LEFT - 8 - metrics.stringWidth(label), y + 4);
            }
            pen.drawLine(MARGIN_LEFT, MARGIN_TOP, MARGIN_LEFT, MARGIN_TOP + plotHeight);
            pen.drawLine(MARGIN_LEFT, MARGIN_TOP + plotHeight,
                    MARGIN_LEFT + plotWidth, MARGIN_TOP + plotHeight);
        }

        private void drawXLabels(Graphics2D pen, List<TrendPoint> data, int plotWidth,
                                 int plotHeight) {
            pen.setFont(Theme.Type.tiny());
            pen.setColor(Theme.current().axis());
            FontMetrics metrics = pen.getFontMetrics();
            int shown = Math.min(5, data.size());
            for (int index = 0; index < shown; index++) {
                int position = shown == 1 ? 0
                        : (int) Math.round(index * (data.size() - 1.0) / (shown - 1));
                String label = data.get(position).getLabel();
                double x = MARGIN_LEFT + plotWidth * position / (double) (data.size() - 1);
                int textWidth = metrics.stringWidth(label);
                int textX = (int) Math.round(x - textWidth / 2.0);
                // Clipped at both ends so the first and last labels are not cut off by
                // the panel edge.
                textX = Math.max(2, Math.min(textX, plotWidth + MARGIN_LEFT - textWidth - 2));
                pen.drawString(label, textX,
                        MARGIN_TOP + plotHeight + 16);
            }
        }

        private void drawCaption(Graphics2D pen, int width, int height) {
            if (caption.isEmpty()) {
                return;
            }
            pen.setFont(Theme.Type.tiny());
            pen.setColor(Theme.current().faint());
            FontMetrics metrics = pen.getFontMetrics();
            pen.drawString(caption, width - metrics.stringWidth(caption) - MARGIN_RIGHT, 12);
        }
    }

    // ---------------------------------------------------------------- bars

    /**
     * A horizontal bar chart.
     *
     * <p>Horizontal rather than vertical because the labels are words — campaign
     * names, budget headings — and words rotated on a vertical axis are unreadable.
     */
    public static final class Bars extends JComponent {
        private static final long serialVersionUID = 1L;

        /** Declared as {@link LinkedHashMap} so the field is concretely serialisable. */
        private final LinkedHashMap<String, Double> values = new LinkedHashMap<>();
        private String unit = "";
        private Color singleColour;

        public Bars() {
            setPreferredSize(new Dimension(360, 180));
            setMinimumSize(new Dimension(180, MINIMUM_HEIGHT));
            setOpaque(false);
        }

        /** Replaces the data, keeping the order it was given in. */
        public Bars with(Map<String, Double> data) {
            values.clear();
            if (data != null) {
                values.putAll(data);
            }
            repaint();
            return this;
        }

        /** Sets the unit shown beside each value, such as "tickets" or "£". */
        public Bars measuredIn(String text) {
            this.unit = text == null ? "" : text;
            repaint();
            return this;
        }

        /** Draws every bar in one colour instead of a series of them. */
        public Bars monochrome(Color colour) {
            this.singleColour = colour;
            repaint();
            return this;
        }

        public boolean isEmpty() {
            return values.isEmpty();
        }

        /** How many bars are loaded. Used when checking a rendered screen. */
        public int getPointCount() {
            return values.size();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D pen = Ui.antialiased(graphics);
            int width = getWidth();
            int height = getHeight();
            if (values.isEmpty() || width < 60 || height < 40) {
                drawNoData(pen, width, height);
                pen.dispose();
                return;
            }

            int labelWidth = Math.min(150, width / 3);
            int valueWidth = 62;
            int plotWidth = width - labelWidth - valueWidth - 12;
            double maximum = 0;
            for (double value : values.values()) {
                maximum = Math.max(maximum, value);
            }
            if (maximum <= 0) {
                drawNoData(pen, width, height);
                pen.dispose();
                return;
            }

            int count = values.size();
            int rowHeight = Math.max(18, height / count);
            int barHeight = Math.max(8, rowHeight - 9);

            pen.setFont(Theme.Type.small());
            int row = 0;
            for (Map.Entry<String, Double> entry : values.entrySet()) {
                int y = row * rowHeight + (rowHeight - barHeight) / 2;
                double fraction = entry.getValue() / maximum;

                pen.setColor(Theme.current().text());
                FontMetrics metrics = pen.getFontMetrics();
                pen.drawString(Ui.ellipsise(entry.getKey(), labelWidth / 7), 2,
                        y + barHeight / 2 + metrics.getAscent() / 2 - 2);

                pen.setColor(Theme.current().chartGrid());
                pen.fillRoundRect(labelWidth, y, plotWidth, barHeight, 6, 6);
                int filled = (int) Math.max(2, Math.round(plotWidth * fraction));
                pen.setColor(singleColour != null ? singleColour
                        : Theme.current().series(row));
                pen.fillRoundRect(labelWidth, y, filled, barHeight, 6, 6);

                pen.setColor(Theme.current().muted());
                String valueLabel = formatValue(entry.getValue()) + (unit.isEmpty() ? "" : " " + unit);
                pen.drawString(valueLabel, labelWidth + plotWidth + 6,
                        y + barHeight / 2 + metrics.getAscent() / 2 - 2);
                row++;
            }
            pen.dispose();
        }
    }

    // ---------------------------------------------------------------- columns

    /**
     * A vertical column chart, for a series with many points.
     *
     * <p>The attendance curve during an event has a point every fifteen minutes, which
     * is far too many to draw as a line with markers. Columns read the shape of a
     * busy period at a glance and label cleanly underneath.
     */
    public static final class Columns extends JComponent {
        private static final long serialVersionUID = 1L;

        /** Declared as {@link ArrayList} so the field is concretely serialisable. */
        private final ArrayList<TrendPoint> points = new ArrayList<>();
        private Color colour;
        private String caption = "";

        public Columns() {
            setPreferredSize(new Dimension(520, 190));
            setMinimumSize(new Dimension(200, MINIMUM_HEIGHT));
            setOpaque(false);
        }

        public Columns with(List<TrendPoint> data) {
            points.clear();
            if (data != null) {
                points.addAll(data);
            }
            repaint();
            return this;
        }

        /** Draws every column in one colour. */
        public Columns coloured(Color value) {
            this.colour = value;
            repaint();
            return this;
        }

        public Columns captioned(String text) {
            this.caption = text == null ? "" : text;
            repaint();
            return this;
        }

        public boolean isEmpty() {
            return points.isEmpty();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D pen = Ui.antialiased(graphics);
            int width = getWidth();
            int height = getHeight();
            int plotWidth = width - MARGIN_LEFT - MARGIN_RIGHT;
            int plotHeight = height - MARGIN_TOP - MARGIN_BOTTOM;

            if (points.isEmpty() || plotWidth <= 10 || plotHeight <= 10) {
                drawNoData(pen, width, height);
                pen.dispose();
                return;
            }

            double maximum = 0;
            for (TrendPoint point : points) {
                maximum = Math.max(maximum, point.getValue());
            }
            double top = maximum <= 0 ? 1 : niceCeiling(maximum);

            pen.setFont(Theme.Type.tiny());
            pen.setStroke(new BasicStroke(1f));
            for (int step = 0; step <= 4; step++) {
                int y = MARGIN_TOP + plotHeight * step / 4;
                pen.setColor(Theme.current().chartGrid());
                pen.drawLine(MARGIN_LEFT, y, MARGIN_LEFT + plotWidth, y);
                pen.setColor(Theme.current().axis());
                String label = step == 4 ? formatValue(top)
                        : formatValue(top * (4 - step) / 4.0);
                FontMetrics metrics = pen.getFontMetrics();
                pen.drawString(label, MARGIN_LEFT - 8 - metrics.stringWidth(label), y + 4);
            }

            int slot = Math.max(2, plotWidth / points.size());
            int barWidth = Math.max(2, (int) (slot * 0.7));
            Color barColour = colour != null ? colour : Theme.current().accent();

            pen.setColor(barColour);
            for (int index = 0; index < points.size(); index++) {
                double value = points.get(index).getValue();
                int barHeight = (int) Math.round(plotHeight * (value / top));
                if (barHeight <= 0) {
                    continue;
                }
                int x = MARGIN_LEFT + slot * index + (slot - barWidth) / 2;
                int y = MARGIN_TOP + plotHeight - barHeight;
                pen.fillRoundRect(x, y, barWidth, barHeight, 3, 3);
            }

            pen.setColor(Theme.current().axis());
            pen.drawLine(MARGIN_LEFT, MARGIN_TOP + plotHeight, MARGIN_LEFT + plotWidth,
                    MARGIN_TOP + plotHeight);

            // Every label would overlap past about ten columns, so they are thinned and
            // the last one is always drawn, since that is where the eye goes.
            pen.setFont(Theme.Type.tiny());
            FontMetrics metrics = pen.getFontMetrics();
            int step = Math.max(1, points.size() / 8);
            for (int index = 0; index < points.size(); index += step) {
                String label = points.get(index).getLabel();
                int x = MARGIN_LEFT + slot * index;
                pen.drawString(label, Math.max(2, x), MARGIN_TOP + plotHeight + 16);
            }
            String lastLabel = points.get(points.size() - 1).getLabel();
            int lastX = MARGIN_LEFT + slot * (points.size() - 1);
            pen.drawString(lastLabel,
                    Math.min(getWidth() - metrics.stringWidth(lastLabel) - 2, lastX),
                    MARGIN_TOP + plotHeight + 16);

            if (!caption.isEmpty()) {
                pen.setColor(Theme.current().faint());
                pen.drawString(caption, width - metrics.stringWidth(caption) - MARGIN_RIGHT, 12);
            }
            pen.dispose();
        }
    }

    // ---------------------------------------------------------------- donut

    /**
     * A ring chart for a breakdown into parts.
     *
     * <p>A donut rather than a pie because the centre can carry the total, which is the
     * figure somebody usually came to the chart for. Slices are separated by a
     * background-coloured gap so that adjacent similar colours remain tellable apart.
     */
    public static final class Donut extends JComponent {
        private static final long serialVersionUID = 1L;

        /** Declared as {@link LinkedHashMap} so the field is concretely serialisable. */
        private final LinkedHashMap<String, Double> slices = new LinkedHashMap<>();
        private String centreLabel = "";

        public Donut() {
            setPreferredSize(new Dimension(240, 200));
            setMinimumSize(new Dimension(180, MINIMUM_HEIGHT));
            setOpaque(false);
        }

        public Donut with(Map<String, Double> data) {
            slices.clear();
            if (data != null) {
                slices.putAll(data);
            }
            repaint();
            return this;
        }

        /** Puts a figure in the middle of the ring. */
        public Donut centredOn(String text) {
            this.centreLabel = text == null ? "" : text;
            repaint();
            return this;
        }

        public boolean isEmpty() {
            return slices.isEmpty();
        }

        /** How many slices are loaded. Used when checking a rendered screen. */
        public int getSliceCount() {
            return slices.size();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D pen = Ui.antialiased(graphics);
            int size = Math.min(getWidth(), getHeight() - 18);
            if (slices.isEmpty() || size < 60) {
                drawNoData(pen, getWidth(), getHeight());
                pen.dispose();
                return;
            }

            double total = 0;
            for (double value : slices.values()) {
                total += Math.max(0, value);
            }

            int legendWidth = getWidth() - size - 20;
            if (legendWidth < 90) {
                // Too narrow for a legend beside it, so the ring takes the width and the
                // legend is not drawn rather than being drawn on top of the chart.
                size = Math.min(getWidth(), getHeight() - 18);
                legendWidth = 0;
            }

            int thickness = Math.max(14, size / 6);
            int outer = size - 14;
            double innerRadius = outer / 2.0 - thickness;
            double centreX = outer / 2.0 + 7;
            double centreY = outer / 2.0 + 7;
            double radius = outer / 2.0 - 1;

            if (total <= 0) {
                // Every slice zero: draw the empty ring rather than a full one, so a
                // chart of nothing cannot be mistaken for a chart of everything.
                pen.setColor(Theme.current().chartGrid());
                pen.setStroke(new BasicStroke(thickness));
                pen.drawOval((int) (centreX - radius), (int) (centreY - radius),
                        (int) (radius * 2), (int) (radius * 2));
            } else {
                double start = 90;
                int index = 0;
                for (Map.Entry<String, Double> entry : slices.entrySet()) {
                    double fraction = Math.max(0, entry.getValue()) / total;
                    double extent = -360 * fraction;
                    pen.setColor(Theme.current().series(index));
                    pen.setStroke(new BasicStroke(thickness, BasicStroke.CAP_BUTT,
                            BasicStroke.JOIN_MITER));
                    pen.drawArc((int) (centreX - radius), (int) (centreY - radius),
                            (int) (radius * 2), (int) (radius * 2),
                            (int) Math.round(start), (int) Math.round(extent));
                    start += extent;
                    index++;
                }
            }

            if (!centreLabel.isEmpty()) {
                pen.setFont(Theme.Type.metricSmall());
                pen.setColor(Theme.current().text());
                FontMetrics metrics = pen.getFontMetrics();
                pen.drawString(centreLabel, (int) (centreX - metrics.stringWidth(centreLabel) / 2.0),
                        (int) (centreY + metrics.getAscent() / 2.0 - 2));
            }

            if (legendWidth >= 90) {
                drawLegend(pen, size + 16, total);
            }
            pen.dispose();
        }

        private void drawLegend(Graphics2D pen, int left, double total) {
            pen.setFont(Theme.Type.small());
            FontMetrics metrics = pen.getFontMetrics();
            int line = 0;
            int rowHeight = 17;
            int maxRows = Math.max(1, (getHeight() - 20) / rowHeight);
            int shown = 0;
            for (Map.Entry<String, Double> entry : slices.entrySet()) {
                if (shown >= maxRows) {
                    // Rather than truncating silently, the remainder is stated. A legend
                    // that quietly omits slices is worse than a short one.
                    pen.setColor(Theme.current().faint());
                    pen.drawString("and " + (slices.size() - shown) + " more", left, line * rowHeight + 12);
                    break;
                }
                pen.setColor(Theme.current().series(shown));
                pen.fill(new Rectangle2D.Double(left, line * rowHeight + 3, 9, 9));
                pen.setColor(Theme.current().text());
                String name = Ui.ellipsise(entry.getKey(), 16);
                pen.drawString(name, left + 15, line * rowHeight + 12);
                pen.setColor(Theme.current().muted());
                double share = total <= 0 ? 0 : entry.getValue() / total * 100;
                String shareLabel = String.format(java.util.Locale.UK, "%.0f%%", share);
                pen.drawString(shareLabel, left + 15 + metrics.stringWidth(name) + 6,
                        line * rowHeight + 12);
                line++;
                shown++;
            }
        }
    }

    // ---------------------------------------------------------------- gauge

    /**
     * A single-value dial, for a return figure.
     *
     * <p>A proportion of something can be read off a bar perfectly well, so a dial is
     * used only where the number has a natural zero and a natural target — return on
     * spend, where zero is a loss and one is break-even, and both mean something.
     */
    public static final class Gauge extends JComponent {
        private static final long serialVersionUID = 1L;

        private double value;
        private double maximum = 2.0;
        private String label = "";
        private String caption = "";

        public Gauge() {
            setPreferredSize(new Dimension(200, 130));
            setMinimumSize(new Dimension(160, 110));
            setOpaque(false);
        }

        /** Sets the figure and the scale it is measured against. */
        public Gauge showing(double newValue, double scale) {
            this.value = newValue;
            this.maximum = scale <= 0 ? 1 : scale;
            repaint();
            return this;
        }

        public Gauge labelled(String text) {
            this.label = text == null ? "" : text;
            repaint();
            return this;
        }

        public Gauge captioned(String text) {
            this.caption = text == null ? "" : text;
            repaint();
            return this;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D pen = Ui.antialiased(graphics);
            int width = getWidth();
            int height = getHeight();
            int thickness = Math.max(10, height / 8);
            int diameter = Math.min(width, height - 24) - thickness;
            int left = (width - diameter) / 2;
            int top = (height - diameter) / 2 - 4;

            double fraction = Math.max(0, Math.min(1, value / maximum));
            Color colour = value >= 1.0 ? Theme.current().success()
                    : value >= 0.8 ? Theme.current().warning() : Theme.current().danger();

            pen.setStroke(new BasicStroke(thickness, BasicStroke.CAP_BUTT,
                    BasicStroke.JOIN_MITER));
            pen.setColor(Theme.current().chartGrid());
            pen.drawArc(left, top, diameter, diameter, 0, 180);
            pen.setColor(colour);
            pen.drawArc(left, top, diameter, diameter, 180, (int) Math.round(-180 * fraction));

            pen.setFont(Theme.Type.metricSmall());
            pen.setColor(colour);
            FontMetrics metrics = pen.getFontMetrics();
            String shown = String.format(java.util.Locale.UK, "%.2fx", value);
            pen.drawString(shown, (width - metrics.stringWidth(shown)) / 2,
                    top + diameter / 2 + metrics.getAscent() / 2);

            if (!label.isEmpty()) {
                pen.setFont(Theme.Type.small());
                pen.setColor(Theme.current().muted());
                pen.drawString(label, (width - metrics.stringWidth(label)) / 2,
                        top + diameter / 2 + metrics.getAscent() / 2 + 16);
            }
            if (!caption.isEmpty()) {
                pen.setFont(Theme.Type.tiny());
                pen.setColor(Theme.current().faint());
                pen.drawString(caption, (width - metrics.stringWidth(caption)) / 2, height - 4);
            }
            pen.dispose();
        }
    }

    // ---------------------------------------------------------------- shared

    /**
     * Writes "Nothing to show yet" rather than drawing an empty frame.
     *
     * <p>An empty chart frame reads as a fault in the application. Words read as an
     * answer: there is nothing yet, and it is not broken.
     */
    private static void drawNoData(Graphics2D pen, int width, int height) {
        pen.setFont(Theme.Type.body());
        pen.setColor(Theme.current().faint());
        String message = "Nothing to show yet";
        FontMetrics metrics = pen.getFontMetrics();
        pen.drawString(message, Math.max(8, (width - metrics.stringWidth(message)) / 2),
                height / 2);
    }

    /** Rounds an axis maximum up to a round number, so the labels are readable. */
    private static double niceCeiling(double value) {
        if (value <= 0) {
            return 1;
        }
        double magnitude = Math.pow(10, Math.floor(Math.log10(value)));
        double normalised = value / magnitude;
        double step;
        if (normalised <= 1) {
            step = 1;
        } else if (normalised <= 2) {
            step = 2;
        } else if (normalised <= 5) {
            step = 5;
        } else {
            step = 10;
        }
        return step * magnitude;
    }

    /** Formats a value for an axis or a legend, without noise digits. */
    static String formatValue(double value) {
        if (Math.abs(value) >= 1_000_000) {
            return String.format(java.util.Locale.UK, "%.1fM", value / 1_000_000);
        }
        if (Math.abs(value) >= 1_000) {
            return String.format(java.util.Locale.UK, "%.1fk", value / 1_000);
        }
        if (value == Math.floor(value)) {
            return String.valueOf((long) value);
        }
        return String.format(java.util.Locale.UK, "%.1f", value);
    }

    /** A colour with an alpha applied, for the fills under a line. */
    static Color withAlpha(Color colour, int alpha) {
        return new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), alpha);
    }
}
