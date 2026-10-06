package com.eventsuite.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.table.AbstractTableModel;

/**
 * A table built from rows of text.
 *
 * <p>Most of the tables in this application show a handful of columns of text and
 * nothing else, and {@code AbstractTableModel} is long enough that writing one class
 * per table is mostly boilerplate. This takes the column names and the rows and does
 * the rest.
 *
 * <p>Rows are held by reference to the list they were given, so a caller can sort or
 * filter the list and tell the table to redraw rather than rebuilding the model. That
 * matters for the searchable tables, where filtering happens constantly.
 */
public final class TableData extends AbstractTableModel {

    private static final long serialVersionUID = 1L;

    private final String[] columns;
    /**
     * The rows, and a parallel untyped list.
     *
     * <p>Declared as {@link ArrayList} rather than {@code List} so the fields are
     * concretely serialisable.
     */
    private final ArrayList<Object[]> rows;
    private final ArrayList<Object> backing;
    private Class<?>[] types = new Class<?>[0];

    /** A table with the given columns and no rows. */
    public TableData(String... columnNames) {
        this.columns = columnNames == null ? new String[0] : columnNames.clone();
        this.rows = new ArrayList<>();
        this.backing = new ArrayList<>();
    }

    /** A table with the given columns and rows. */
    public TableData(String[] columnNames, List<?> sourceRows) {
        this.columns = columnNames == null ? new String[0] : columnNames.clone();
        this.rows = new ArrayList<>();
        this.backing = new ArrayList<>();
        if (sourceRows != null) {
            for (Object row : sourceRows) {
                add(row instanceof Object[] ? (Object[]) row
                        : new Object[]{String.valueOf(row)});
            }
        }
    }

    /** A table with string columns, filled by {@link #add}. */
    public static TableData with(String... columnNames) {
        return new TableData(columnNames);
    }

    /**
     * Sets the renderer class for each column.
     *
     * <p>Lets a screen say which columns are money and which are statuses, so the
     * colouring is declared once alongside the data rather than applied afterwards by
     * walking the table looking for strings.
     */
    public TableData types(Class<?>... columnTypes) {
        this.types = columnTypes == null ? new Class<?>[0] : columnTypes.clone();
        return this;
    }

    private int[] moneyColumns = new int[0];

    /** Marks columns as holding money, so they are right-aligned. */
    public TableData withMoney(int... columnIndexes) {
        this.moneyColumns = columnIndexes == null ? new int[0] : columnIndexes.clone();
        return this;
    }

    /** Marks columns as statuses, so they are coloured by meaning. */
    public TableData withStatus(int... columnIndexes) {
        this.statusColumns = columnIndexes == null ? new int[0] : columnIndexes.clone();
        return this;
    }

    private int[] statusColumns = new int[0];

    /** Marks columns to be drawn in the muted colour. */
    public TableData withMuted(int... columnIndexes) {
        this.mutedColumns = columnIndexes == null ? new int[0] : columnIndexes.clone();
        return this;
    }

    private int[] mutedColumns = new int[0];

    /** Adds a row of any values. They are shown through their own {@code toString}. */
    public TableData add(Object... values) {
        backing.add(values == null ? new Object[]{} : values);
        rows.add(values == null ? new Object[]{} : values);
        return this;
    }

    /** The value at a cell, for reading a selected row. */
    public Object valueAt(int row, int column) {
        if (row < 0 || row >= rows.size() || column < 0 || column >= columns.length) {
            return null;
        }
        Object[] values = rows.get(row);
        return column < values.length ? values[column] : null;
    }

    /** The row values at a row, for building a detail view. */
    public Object[] rowAt(int row) {
        return row < 0 || row >= rows.size() ? new Object[0] : rows.get(row);
    }

    public int getRowCount() {
        return rows.size();
    }

    public int getColumnCount() {
        return columns.length;
    }

    @Override
    public String getColumnName(int column) {
        return column >= 0 && column < columns.length ? columns[column] : "";
    }

    @Override
    public Object getValueAt(int row, int column) {
        return valueAt(row, column);
    }

    @Override
    public Class<?> getColumnClass(int column) {
        if (column < types.length && types[column] != null) {
            return types[column];
        }
        Object value = rows.isEmpty() ? null
                : (column < rows.get(0).length ? rows.get(0)[column] : null);
        return value == null ? String.class : value.getClass();
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        // Every table here is a record of something that happened or a list of
        // something that exists. Editing is done through a form, not in the grid.
        return false;
    }

    /** Whether a column was marked as holding money. */
    public boolean isMoneyColumn(int column) {
        return contains(moneyColumns, column);
    }

    /** Whether a column was marked as holding a status. */
    public boolean isStatusColumn(int column) {
        return contains(statusColumns, column);
    }

    /** Whether a column was marked as secondary. */
    public boolean isMutedColumn(int column) {
        return contains(mutedColumns, column);
    }

    private static boolean contains(int[] indexes, int column) {
        for (int index : indexes) {
            if (index == column) {
                return true;
            }
        }
        return false;
    }

    /** Applies the declared column styles to a table built from this model. */
    public void applyRenderers(javax.swing.JTable table) {
        if (table == null) {
            return;
        }
        for (int column = 0; column < columns.length; column++) {
            if (isStatusColumn(column)) {
                table.getColumnModel().getColumn(column).setCellRenderer(Ui.statusRenderer());
            } else if (isMoneyColumn(column)) {
                table.getColumnModel().getColumn(column).setCellRenderer(Ui.rightRenderer());
            } else if (isMutedColumn(column)) {
                table.getColumnModel().getColumn(column).setCellRenderer(Ui.mutedRenderer());
            }
        }
    }

    /** An empty-state message for a table with nothing in it. */
    public boolean isEmpty() {
        return rows.isEmpty();
    }

    /** The rows as a list, for filtering into a new table. */
    public List<Object> rowsAsList() {
        return backing;
    }

    /** Convenience for building a table from a header and a list of domain objects. */
    public static TableData from(String[] columnNames, List<?> items,
                                 java.util.function.Function<Object, Object[]> mapper) {
        TableData table = new TableData(columnNames);
        if (items != null) {
            for (Object item : items) {
                table.add(mapper.apply(item));
            }
        }
        return table;
    }

    /** The column names as a list, for building a table that grows later. */
    public static List<String> columnNames(String... names) {
        return new ArrayList<>(Arrays.asList(names));
    }
}
