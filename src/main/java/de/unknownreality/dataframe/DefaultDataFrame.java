/*
 *
 *  * Copyright (c) 2017 Alexander Grün
 *  *
 *  * Permission is hereby granted, free of charge, to any person obtaining a copy
 *  * of this software and associated documentation files (the "Software"), to deal
 *  * in the Software without restriction, including without limitation the rights
 *  * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  * copies of the Software, and to permit persons to whom the Software is
 *  * furnished to do so, subject to the following conditions:
 *  *
 *  * The above copyright notice and this permission notice shall be included in all
 *  * copies or substantial portions of the Software.
 *  *
 *  * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  * SOFTWARE.
 *
 */

package de.unknownreality.dataframe;

import de.unknownreality.dataframe.column.*;
import de.unknownreality.dataframe.common.mapping.DataMapper;
import de.unknownreality.dataframe.filter.FilterPredicate;
import de.unknownreality.dataframe.filter.compile.PredicateCompiler;
import de.unknownreality.dataframe.group.DataGrouping;
import de.unknownreality.dataframe.group.GroupUtil;
import de.unknownreality.dataframe.group.impl.DefaultGroupUtil;
import de.unknownreality.dataframe.index.Indices;
import de.unknownreality.dataframe.join.JoinColumn;
import de.unknownreality.dataframe.join.JoinUtil;
import de.unknownreality.dataframe.join.JoinedDataFrame;
import de.unknownreality.dataframe.join.impl.DefaultJoinUtil;
import de.unknownreality.dataframe.sort.RowColumnComparator;
import de.unknownreality.dataframe.sort.SortColumn;
import de.unknownreality.dataframe.transform.DataFrameTransform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Created by Alex on 09.03.2016.
 */
public class DefaultDataFrame implements DataFrame {
    private static final Logger log = LoggerFactory.getLogger(DefaultDataFrame.class);
    private int size;
    private final Map<String, DataFrameColumn> columnsMap = new LinkedHashMap<>();
    private final List<DataFrameColumn> columnList = new ArrayList<>();
    private DataFrameHeader header = new DataFrameHeader();
    private final Indices indices = new Indices(this);
    private JoinUtil joinUtil = new DefaultJoinUtil();
    private GroupUtil groupUtil = new DefaultGroupUtil();

    public DefaultDataFrame() {

    }

    /**
     * Creates a new data frame using a data frame header and a collections of data rows
     *
     * @param header data frame header
     * @param rows   collections of data rows
     */
    public DefaultDataFrame(DataFrameHeader header, Collection<DataRow> rows) {
        set(header, rows);
    }


    /**
     * Sets the primary key columns using column names
     *
     * @param colNames primary key columns
     * @return <tt>self</tt> for method chaining
     */
    @Override
    public DataFrame setPrimaryKey(String... colNames) {
        DataFrameColumn[] columns = new DataFrameColumn[colNames.length];
        for (int i = 0; i < columns.length; i++) {
            columns[i] = getColumn(colNames[i]);
        }
        return setPrimaryKey(columns);
    }

    /**
     * Sets the primary key columns using column objects
     *
     * @param cols primary key columns
     * @return <tt>self</tt> for method chaining
     */
    @Override
    public DataFrame setPrimaryKey(DataFrameColumn... cols) {
        this.indices.setPrimaryKey(cols);
        return this;
    }

    /**
     * Removes the current primary key
     *
     * @return <tt>self</tt> for method chaining
     */
    @Override
    public DataFrame removePrimaryKey() {
        indices.removeIndex(Indices.PRIMARY_KEY_NAME);
        return this;
    }

    /**
     * Removes the index with the specified name
     *
     * @param name name of index
     * @return <tt>self</tt> for method chaining
     */
    @Override
    public DataFrame removeIndex(String name) {
        indices.removeIndex(name);
        return this;
    }

    /**
     * Renames a column
     *
     * @param name    current column name
     * @param newName new column name
     * @return <tt>self</tt> for method chaining
     */
    @Override
    public DataFrame renameColumn(String name, String newName) {
        DataFrameColumn column = columnsMap.get(name);
        if (column == null) {
            return this;
        }
        header.rename(name, newName);
        column.setName(newName);
        columnsMap.remove(name);
        columnsMap.put(newName, column);
        return this;
    }

    /**
     * Replaces an existing column with a replacement column
     *
     * @param existing    existing column name
     * @param replacement replacement column
     * @return <tt>self</tt> for method chaining
     */
    public DataFrame replaceColumn(String existing, DataFrameColumn replacement) {
        DataFrameColumn existingColumn = getColumn(existing);
        return replaceColumn(existingColumn, replacement);
    }

    /**
     * Replaces an existing column with a replacement column
     *
     * @param existing    existing column
     * @param replacement replacement column
     * @return <tt>self</tt> for method chaining
     */
    public DataFrame replaceColumn(DataFrameColumn existing, DataFrameColumn replacement) {
        int existingIndex = header.getIndex(existing.getName());
        columnList.set(existingIndex, replacement);
        header.replace(existing, replacement);
        columnsMap.remove(existing.getName());
        columnsMap.put(replacement.getName(), replacement);
        indices.replace(existing, replacement);
        return this;
    }

    /**
     * Adds a column to the data frame.
     * If the column is already part of another data frame a {@link DataFrameRuntimeException} is thrown.
     *
     * @param column column to add
     * @return <tt>self</tt> for method chaining
     */
    @Override
    @SuppressWarnings("unchecked")
    public DataFrame addColumn(DataFrameColumn column) {
        if (!columnList.isEmpty() && column.size() != size) {
            throw new DataFrameRuntimeException("column lengths must be equal");
        }
        columnList.add(column);
        if (column.getDataFrame() != null && column.getDataFrame() != this) {
            throw new DataFrameRuntimeException("column can not be added to multiple data frames. use column.copy() first");
        }
        if (columnList.size() == 1) {
            this.size = column.size();
        }
        try {
            column.setDataFrame(this);
        } catch (DataFrameException e) {
            throw new DataFrameRuntimeException("error adding column", e);
        }
        header.add(column.getName(), column.getClass(), column.getType());
        columnsMap.put(column.getName(), column);
        return this;
    }


    /**
     * Adds a new {@link BooleanColumn} to the dataframe.
     *
     * @param name name of the column
     * @return <tt>self</tt> for method chaining
     */
    public DataFrame addBooleanColumn(String name) {
        BooleanColumn column = new BooleanColumn(name);
        return addColumn(column);
    }

    /**
     * Adds a new {@link ByteColumn} to the dataframe.
     *
     * @param name name of the column
     * @return <tt>self</tt> for method chaining
     */
    public DataFrame addByteColumn(String name) {
        ByteColumn column = new ByteColumn(name);
        return addColumn(column);
    }

    /**
     * Adds a new {@link DoubleColumn} to the dataframe.
     *
     * @param name name of the column
     * @return <tt>self</tt> for method chaining
     */
    public DataFrame addDoubleColumn(String name) {
        DoubleColumn column = new DoubleColumn(name);
        return addColumn(column);
    }

    /**
     * Adds a new {@link FloatColumn} to the dataframe.
     *
     * @param name name of the column
     * @return <tt>self</tt> for method chaining
     */
    public DataFrame addFloatColumn(String name) {
        FloatColumn column = new FloatColumn(name);
        return addColumn(column);
    }

    /**
     * Adds a new {@link IntegerColumn} to the dataframe.
     *
     * @param name name of the column
     * @return <tt>self</tt> for method chaining
     */
    public DataFrame addIntegerColumn(String name) {
        IntegerColumn column = new IntegerColumn(name);
        return addColumn(column);
    }

    /**
     * Adds a new {@link LongColumn} to the dataframe.
     *
     * @param name name of the column
     * @return <tt>self</tt> for method chaining
     */
    public DataFrame addLongColumn(String name) {
        LongColumn column = new LongColumn(name);
        return addColumn(column);
    }

    /**
     * Adds a new {@link ShortColumn} to the dataframe.
     *
     * @param name name of the column
     * @return <tt>self</tt> for method chaining
     */
    public DataFrame addShortColumn(String name) {
        ShortColumn column = new ShortColumn(name);
        return addColumn(column);
    }

    /**
     * Adds a new {@link StringColumn} to the dataframe.
     *
     * @param name name of the column
     * @return <tt>self</tt> for method chaining
     */
    public DataFrame addStringColumn(String name) {
        StringColumn column = new StringColumn(name);
        return addColumn(column);
    }


    /**
     * Creates a column for a specified column value type using the default {@link ColumnTypeMap}.
     *
     * @param type class of column values
     * @param name column name
     * @param <T>  type of column values
     * @return <tt>self</tt> for method chaining
     */
    @Override
    public <T extends Comparable<T>> DataFrame addColumn(Class<T> type, String name) {
        return addColumn(type, name, ColumnTypeMap.create());
    }


    /**
     * Creates a column for a specified column value type using the provided {@link ColumnTypeMap}.
     *
     * @param type          class of column values
     * @param name          column name
     * @param columnTypeMap provided column type map
     * @param <T>           type of column values
     * @return <tt>self</tt> for method chaining
     * @see #addColumn(Class, String, ColumnAppender)
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T extends Comparable<T>> DataFrame addColumn(Class<T> type, String name, ColumnTypeMap columnTypeMap) {
        return addColumn(type, name, columnTypeMap, null);
    }

    /**
     * Creates and adds a new column based on a specified column value type and a {@link ColumnTypeMap}.
     *
     * @param type          column value value type
     * @param name          name of new column
     * @param columnTypeMap column type map (value type / column class mapper)
     * @param appender      column appender (value generator)
     * @param <T>           type of column values
     * @param <C>           type of created column
     * @return <tt>self</tt> for method chaining
     * @see #addColumn(Class, String, ColumnAppender)
     */
    @Override
    public <T extends Comparable<T>, C extends DataFrameColumn<T, C>> DataFrame addColumn(Class<T> type, String name, ColumnTypeMap columnTypeMap, ColumnAppender<T> appender) {
        Class<C> columnType = columnTypeMap.getColumnType(type);
        if (columnType == null) {
            throw new DataFrameRuntimeException(String.format("no  column type found for %s", type.getName()));
        }

        return addColumn(columnType, name, appender);
    }

    /**
     * Creates and adds a column to this data frame based on a provided column class.
     * The values in the created column are generated by a {@link ColumnAppender}.
     * If no column appender is specified, the column is filled with {@link Values#NA NA} values.
     * If the column can not be created or added a {@link DataFrameRuntimeException} is thrown.
     *
     * @param type     class of created column
     * @param name     name of created column
     * @param appender column appender (value generator)
     * @param <T>      type of column values
     * @param <C>      type of created column
     * @return <tt>self</tt> for method chaining
     * @see #addColumn(DataFrameColumn)
     */
    @Override
    public <T extends Comparable<T>, C extends DataFrameColumn<T, C>> DataFrame addColumn(Class<C> type, String name, ColumnAppender<T> appender) {
        try {
            C col = type.newInstance();
            col.setName(name);
            if (appender != null) {
                for (DataRow row : this) {
                    T val = appender.createRowValue(row);
                    if (val == null || val == Values.NA) {
                        col.doAppendNA();
                    } else {
                        col.doAppend(val);
                    }
                }
            } else {
                for (int i = 0; i < size(); i++) {
                    col.doAppendNA();
                }
            }
            addColumn(col);
        } catch (InstantiationException e) {
            log.error("error creating instance of column [{}], empty constructor required", type, e);
            throw new DataFrameRuntimeException(String.format("error creating instance of column [%s], empty constructor required", type), e);

        } catch (IllegalAccessException e) {
            throw new DataFrameRuntimeException(String.format("error creating instance of column [%s], empty constructor required", type), e);
        }
        return this;
    }

    /**
     * Adds a collection of columns to this data frame
     *
     * @param columns columns to add
     * @return <tt>self</tt> for method chaining
     */

    @Override
    public DataFrame addColumns(Collection<DataFrameColumn> columns) {
        for (DataFrameColumn column : columns) {
            addColumn(column);
        }
        return this;
    }

    /**
     * Adds an array of columns to this data frame
     *
     * @param columns columns to add
     * @return <tt>self</tt> for method chaining
     */
    @Override
    public DataFrame addColumns(DataFrameColumn... columns) {
        for (DataFrameColumn column : columns) {
            addColumn(column);
        }
        return this;
    }

    /**
     * Appends a new row based on {@link Comparable} values.
     * <p>There must be <b>exactly one value for each column</b>.</p>
     * <p><b>The object types have to match the column types</b>.</p>
     * If the wrong number of values or a wrong type is found a {@link DataFrameRuntimeException} is thrown.
     * <p>
     * <p>If the data frame contains:<br>
     * <code>StringColumn,DoubleColumn,IntegerColumn</code><br>
     * The only correct call to this method is:<br>
     * <code>append(String, Double, Integer)</code>
     * </p>
     * <p>empty column values must be provided as <tt>null</tt> or {@link Values#NA NA}</p>
     *
     * @param values values for the appended row
     * @return <tt>self</tt> for method chaining
     */
    @Override
    public DataFrame append(Comparable... values) {
        if (values.length != columnList.size()) {
            throw new DataFrameRuntimeException("value for each column required");
        }
        int i = 0;
        for (DataFrameColumn column : columnList) {
            if (values[i] != null && values[i] != Values.NA && !column.getType().isAssignableFrom(values[i].getClass())) {
                throw new DataFrameRuntimeException(
                        String.format("value %d has wrong type (%s != %s)", i,
                                values[i].getClass().getName(),
                                column.getType().getName()));
            }
            i++;
        }
        i = 0;
        for (DataFrameColumn<?, ?> column : columnList) {
            column.startDataFrameAppend();
            Comparable<?> value = values[i];
            if (value == null || value == Values.NA) {
                column.appendNA();
            } else {
                column.append(value);
            }
            column.endDataFrameAppend();
            i++;
        }
        size++;
        indices.update(getRow(size - 1));
        return this;
    }

    /**
     * Appends a new data row.
     * {@link Values#NA NA} is added for all columns with no value in the provided row.
     *
     * @param row row containing the new values
     * @return <tt>self</tt> for method chaining
     */
    @Override
    @SuppressWarnings("unchecked")
    public DataFrame append(DataRow row) {
        for (String h : header) {
            DataFrameColumn column = columnsMap.get(h);
            column.startDataFrameAppend();
            if (row.isNA(h)) {
                column.appendNA();
            } else {
                column.append(row.get(h));
            }
            column.endDataFrameAppend();

        }
        this.size++;
        indices.update(getRow(size - 1));
        return this;
    }

    /**
     * Persists the updated values of a data row.
     * <tt>null</tt> values are ignored. Use {@link Values#NA NA} instead-
     *
     * @param dataRow data row with updated values
     * @return <tt>self</tt> for method chaining
     */
    @Override
    public DataFrame update(DataRow dataRow) {
        for (String h : header) {
            DataFrameColumn column = getColumn(h);
            Comparable newValue = dataRow.get(h);
            if (newValue == null) {
                continue;
            }
            if (newValue == Values.NA) {
                column.setNA(dataRow.getIndex());
            } else {
                column.set(dataRow.getIndex(), newValue);
            }
        }
        return this;
    }

    /**
     * Clears all rows in this data frame and sets new rows using the provided {@link DataRow} collection.
     *
     * @param rows new collection of rows
     * @return <tt>self</tt> for method chaining
     */
    @Override
    public DataFrame set(Collection<DataRow> rows) {
        this.size = 0;
        this.indices.clearValues();
        for (DataFrameColumn column : columnsMap.values()) {
            column.clear();
        }
        for (DataRow row : rows) {
            append(row);
        }
        return this;
    }

    /**
     * Removes all columns and rows from this data frame.
     * New columns are created using the specified data frame header an populated with the provided data rows.
     *
     * @param header new header
     * @param rows   new rows
     * @return <tt>self</tt> for method chaining
     */
    @Override
    public DataFrame set(DataFrameHeader header, Collection<DataRow> rows) {
        return set(header, rows, null);
    }

    /**
     * Removes all columns and rows from this data frame.
     * New columns are created using the specified data frame header and populated with the provided data rows.
     * If indices are provided, they are copied to this data frame.
     *
     * @param header  new header
     * @param rows    new rows
     * @param indices indices to copy
     * @return <tt>self</tt> for method chaining
     */
    protected DataFrame set(DataFrameHeader header, Collection<DataRow> rows, Indices indices) {
        this.header = header;
        this.columnsMap.clear();
        this.columnList.clear();
        this.indices.clearValues();
        for (String h : header) {
            try {
                DataFrameColumn instance = header.getColumnType(h).newInstance();
                instance.setName(h);
                columnsMap.put(h, instance);
                columnList.add(instance);
                instance.setDataFrame(this);

            } catch (InstantiationException | IllegalAccessException | DataFrameException e) {
                log.error("error creating column instance", e);
                throw new DataFrameRuntimeException("error creating column instance", e);

            }
        }
        if (indices != null) {
            indices.copyTo(this);
        }
        set(rows);
        return this;
    }


    /**
     * Removes a column from this data frame
     *
     * @param header column header name
     * @return <tt>self</tt> for method chaining
     */
    @Override
    public DataFrame removeColumn(String header) {
        DataFrameColumn column = getColumn(header);
        if (column == null) {
            log.error("error column not found {}", header);
            return this;
        }
        return removeColumn(column);
    }

    /**
     * Removes a column from this data frame
     *
     * @param column column to remove
     * @return <tt>self</tt> for method chaining
     */
    @Override
    public DataFrame removeColumn(DataFrameColumn column) {
        try {
            column.setDataFrame(null);
        } catch (DataFrameException e) {
            throw new DataFrameRuntimeException("error removing column", e);

        }
        this.header.remove(column.getName());
        this.indices.removeColumn(column);
        this.columnsMap.remove(column.getName());
        this.columnList.remove(column);
        return this;
    }


    /**
     * Sorts the rows in this data frame by one or more {@link SortColumn}
     *
     * @param columns sort columns
     * @return <tt>self</tt> for method chaining
     */
    @Override
    public DataFrame sort(SortColumn... columns) {
        List<DataRow> rows = getRows(0, size);
        Collections.sort(rows, new RowColumnComparator(columns));
        set(rows);
        return this;
    }

    /**
     * Sorts the rows in this data frame using a custom {@link Comparator}
     *
     * @param comp comparator used to sort the rows
     * @return <tt>self</tt> for method chaining
     */
    @Override
    public DataFrame sort(Comparator<DataRow> comp) {
        List<DataRow> rows = getRows(0, size);
        Collections.sort(rows, comp);
        set(rows);
        return this;
    }

    /**
     * Sorts the rows in this data frame using one column and the default sort direction (<tt>ascending</tt>)
     *
     * @param name sort column
     * @return <tt>self</tt> for method chaining
     */
    @Override
    public DataFrame sort(String name) {
        return sort(name, SortColumn.Direction.Ascending);
    }

    /**
     * Sorts the rows in this data frame using one column and sort direction.
     *
     * @param name sort column
     * @param dir  sort direction
     * @return <tt>self</tt> for method chaining
     */
    @Override
    public DataFrame sort(String name, SortColumn.Direction dir) {
        List<DataRow> rows = getRows(0, size);
        Collections.sort(rows, new RowColumnComparator(new SortColumn[]{new SortColumn(name, dir)}));
        set(rows);
        return this;
    }


    /**
     * Shuffles all rows
     *
     * @return <tt>self</tt> for method chaining
     */
    @Override
    public DataFrame shuffle() {
        List<DataRow> rows = getRows(0, size);
        Collections.shuffle(rows);
        set(rows);
        return this;
    }


    /**
     * Returns a new data frame with all rows from this data frame where a specified column value equals
     * an input value.
     *
     * @param colName column name
     * @param value   input value
     * @return new data frame including the found rows
     */
    @Override
    public DataFrame select(String colName, Comparable value) {
        return select(FilterPredicate.eq(colName, value));
    }


    /**
     * Returns the first found data row from this data frame where a specified column value equals
     * an input value.
     *
     * @param colName column name
     * @param value   input value
     * @return first found data row
     */
    @Override
    public DataRow selectFirst(String colName, Comparable value) {
        return selectFirst(FilterPredicate.eq(colName, value));
    }


    /**
     * Returns the first found data row from this data frame matching an input predicate.
     *
     * @param predicateString input predicate string
     * @return first found data row
     * @see #select(FilterPredicate)
     */
    @Override
    public DataRow selectFirst(String predicateString) {
        return selectFirst(FilterPredicate.compile(predicateString));
    }

    /**
     * Returns the first found data row from this data frame matching an input predicate.
     *
     * @param predicate input predicate
     * @return first found data row
     * @see #select(FilterPredicate)
     */
    @Override
    public DataRow selectFirst(FilterPredicate predicate) {
        for (DataRow row : this) {
            if (predicate.valid(row)) {
                return row;
            }
        }
        return null;
    }


    /**
     * Returns a new data frame based on filtered rows from this data frame.<br>
     * Rows that are valid according to the input predicate remain in the new data frame.<br>
     * <p><code>if(predicate.valid(row)) -&gt; add(row)</code></p>
     *
     * @param predicate filter predicate
     * @return new data frame including the found row
     * @see #filter(FilterPredicate)
     */
    @Override
    public DataFrame select(FilterPredicate predicate) {
        List<DataRow> rows = selectRows(predicate);
        DefaultDataFrame df = new DefaultDataFrame();
        df.set(header.copy(), rows, indices);
        return df;
    }


    /**
     * Returns a new data frame based on filtered rows from this data frame.<br>
     * Rows that are valid according to the input predicate remain in the new data frame.<br>
     * The predicate is compiled from the input string.<br>
     * <p><code>if(predicate.valid(row)) -&gt; add(row)</code></p>
     *
     * @param predicateString predicate string
     * @return new data frame including the found row
     * @see #select(FilterPredicate)
     */
    @Override
    public DataFrame select(String predicateString) {
        return select(PredicateCompiler.compile(predicateString));
    }


    /**
     * Filters data rows that are not valid according to an input predicate.<br>
     * Data rows are filtered by their column values. <br>
     * If a data row is <b>filtered</b> if it is <b>not valid</b> according to the predicate.<br>
     * The filtered data rows are removed from this data frame.<br>
     * <p><code>if(!predicate.valid(row)) -&gt; remove(row)</code></p>
     *
     * @param predicateString filter predicate string
     * @return <tt>self</tt> for method chaining
     */
    @Override
    public DataFrame filter(String predicateString) {
        filter(FilterPredicate.compile(predicateString));
        return this;
    }

    /**
     * Filters data rows that are not valid according to an input predicate.<br>
     * Data rows are filtered by their column values. <br>
     * If a data row is <b>filtered</b> if it is <b>not valid</b> according to the predicate.<br>
     * The filtered data rows are removed from this data frame.<br>
     * <p><code>if(!predicate.valid(row)) -&gt; remove(row)</code></p>
     *
     * @param predicate filter predicate
     * @return <tt>self</tt> for method chaining
     */
    @Override
    public DataFrame filter(FilterPredicate predicate) {
        set(selectRows(predicate));
        return this;
    }


    /**
     * Finds data rows using a {@link FilterPredicate}.
     *
     * @param predicateString input predicate string
     * @return list of found data rows
     */
    @Override
    public List<DataRow> selectRows(String predicateString) {
        return selectRows(FilterPredicate.compile(predicateString));
    }

    /**
     * Finds data rows using a {@link FilterPredicate}.
     *
     * @param predicate input predicate
     * @return list of found data rows
     */
    @Override
    public List<DataRow> selectRows(FilterPredicate predicate) {
        List<DataRow> rows = new ArrayList<>();
        for (DataRow row : this) {
            if (predicate.valid(row)) {
                rows.add(row);
            }
        }
        return rows;
    }


    /**
     * Converts this dataframe into another dataframe using a specified transformer
     *
     * @param transformer the applied transformer
     * @return resulting dataframe
     */
    @Override
    public DataFrame transform(DataFrameTransform transformer) {
        return transformer.transform(this);
    }

    /**
     * Finds a data row using the primary key
     *
     * @param keyValues input key values
     * @return found data row
     */
    @Override
    public DataRow findByPrimaryKey(Comparable... keyValues) {
        Integer index = this.indices.findByPrimaryKey(keyValues);
        if (index == null || index < 0) {
            return null;
        }
        return getRow(index);
    }

    /**
     * Reverses all columns
     *
     * @return <tt>self</tt> for method chaining
     */
    @Override
    public DataFrame reverse() {
        for (DataFrameColumn col : columnList) {
            col.doReverse();
        }
        return this;
    }

    /**
     * Adds a new index based on one or multiple index columns.
     * <p><b>Values in index columns must be unique for all rows</b></p>
     *
     * @param indexName   name of new index
     * @param columnNames index columns
     * @return <tt>self</tt> for method chaining
     */
    @Override
    public DataFrame addIndex(String indexName, String... columnNames) {
        DataFrameColumn[] columns = new DataFrameColumn[columnNames.length];
        for (int i = 0; i < columns.length; i++) {
            columns[i] = getColumn(columnNames[i]);
        }

        return addIndex(indexName, columns);
    }

    /**
     * Adds a new index based on one or multiple index columns.
     * <p><b>Values in index columns must be unique for all rows</b></p>
     *
     * @param indexName name of new index
     * @param columns   index columns
     * @return <tt>self</tt> for method chaining
     */
    @Override
    public DataFrame addIndex(String indexName, DataFrameColumn... columns) {
        indices.addIndex(indexName, columns);
        return this;
    }

    /**
     * Returns the number of rows in this data frame
     *
     * @return number of rows
     */
    @Override
    public int size() {
        return size;
    }


    /**
     * Sets this data frame to a subset of itself.
     * Only rows between <tt>from</tt> and <tt>to</tt> remain in this data frame
     *
     * @param from lowest remaining row index
     * @param to   highest remaining row index
     * @return <tt>self</tt> for method chaining
     */
    @Override
    public DataFrame subset(int from, int to) {
        set(getRows(from, to));
        return this;
    }

    /**
     * Creates a new data frame from a subset of this data frame.
     * Rows between <tt>from</tt> and <tt>to</tt> are added to the new data frame.
     *
     * @param from lowest row index
     * @param to   highest row index
     * @return created subset data frame
     */
    @Override
    public DataFrame createSubset(int from, int to) {
        DefaultDataFrame newFrame = new DefaultDataFrame();
        newFrame.set(header.copy(), getRows(from, to), indices);
        return newFrame;
    }

    /**
     * Returns a list the list of rows between <tt>from</tt> and <tt>to</tt>.
     *
     * @param from lowest row index
     * @param to   highest row index
     * @return list of rows between <tt>from</tt> and <tt>to</tt>
     */
    @Override
    public List<DataRow> getRows(int from, int to) {
        List<DataRow> rows = new ArrayList<>();
        for (int i = from; i < to; i++) {
            rows.add(getRow(i));
        }
        return rows;
    }

    /**
     * Returns all rows in this data frame
     *
     * @return list of all rows
     */
    @Override
    public List<DataRow> getRows() {
        return getRows(0, size);
    }

    /**
     * Returns the header of this data frame
     *
     * @return data frame header
     */
    @Override
    public DataFrameHeader getHeader() {
        return header;
    }


    /**
     * Concatenates two data frames. The rows from the other data frame are appended to this data frame.
     * Throws a {@link DataFrameRuntimeException} if the data frames are not compatible.
     *
     * @param other other data frame
     * @return <tt>self</tt> for method chaining
     */
    @Override
    public DataFrame concat(DataFrame other) {
        if (!header.equals(other.getHeader())) {
            throw new DataFrameRuntimeException("data frames not compatible");
        }
        for (DataRow row : other) {
            append(row);
        }
        return this;
    }

    /**
     * Appends the rows from a collection of data frames to this data frame.
     * Throws a {@link DataFrameRuntimeException} if the data frames are not compatible.
     *
     * @param dataFrames other data frames
     * @return <tt>self</tt> for method chaining
     */
    @Override
    public DataFrame concat(Collection<DataFrame> dataFrames) {
        for (DataFrame dataFrame : dataFrames) {
            if (!header.equals(dataFrame.getHeader())) {
                throw new DataFrameRuntimeException("data frames not compatible");
            }
            for (DataRow row : dataFrame) {
                append(row);
            }
        }
        return this;
    }

    /**
     * Appends the rows from an array of data frames to this data frame.
     * Throws a {@link DataFrameRuntimeException} if the data frames are not compatible.
     *
     * @param dataFrames other data frames
     * @return <tt>self</tt> for method chaining
     */
    @Override
    public DataFrame concat(DataFrame... dataFrames) {
        return concat(Arrays.asList(dataFrames));
    }

    /**
     * Returns <tt>true</tt> if the header of an input data frame equals the header of this data frame.
     *
     * @param input input data frame
     * @return <tt>true</tt> if the other data frame is compatible with this data frame.
     * @see DataFrameHeader#equals(Object)
     */
    @Override
    public boolean isCompatible(DataFrame input) {
        return header.equals(input.getHeader());
    }

    /**
     * Returns the data row at a specified index
     *
     * @param i index of data row
     * @return data row at  specified index
     */
    @Override
    public DataRow getRow(int i) {
        return new DataRow(header, getRowValues(i), i);
    }

    /**
     * Returns the values of a row at a specified index
     *
     * @param i index of data row
     * @return values in data row
     */
    @Override
    public Comparable[] getRowValues(int i) {
        if (i >= size) {
            throw new DataFrameRuntimeException("index out of bounds");
        }
        Comparable[] values = new Comparable[columnList.size()];
        int j = 0;
        for (DataFrameColumn column : columnList) {
            if (column.isNA(i)) {
                values[j++] = Values.NA;
            } else {
                values[j++] = column.get(i);
            }
        }
        return values;
    }

    /**
     * Returns a collection of the column names in this data frame
     *
     * @return column names
     */
    @Override
    public Collection<String> getColumnNames() {
        return new ArrayList<>(columnsMap.keySet());
    }

    /**
     * Returns a column based on its name
     *
     * @param name column name
     * @return column
     */
    @SuppressWarnings("unchecked")
    @Override
    public <T extends Comparable<T>, C extends DataFrameColumn<T, C>> DataFrameColumn<T, C> getColumn(String name) {
        return columnsMap.get(name);
    }

    /**
     * Returns a column as a specified column type.
     * If the column is not found or has the wrong type a {@link DataFrameRuntimeException} is thrown.
     *
     * @param name column name
     * @param cl   class of column
     * @param <T>  type of column
     * @return found column
     */
    @Override
    public <T extends DataFrameColumn> T getColumn(String name, Class<T> cl) {
        DataFrameColumn column = columnsMap.get(name);
        if (column == null) {
            throw new DataFrameRuntimeException(String.format("column '%s' not found", name));
        }
        if (!cl.isInstance(column)) {
            throw new DataFrameRuntimeException(String.format("column '%s' has wrong type", name));
        }
        return cl.cast(column);
    }

    /**
     * Returns a {@link NumberColumn}
     * If the column is not found or has the wrong type a {@link DataFrameRuntimeException} is thrown.
     *
     * @param name column name
     * @return found column
     */
    @SuppressWarnings("unchecked")
    @Override
    public <T extends Number & Comparable<T>, C extends NumberColumn<T, C>> NumberColumn<T, C> getNumberColumn(String name) {
        return getColumn(name, NumberColumn.class);
    }

    /**
     * Returns a {@link StringColumn}
     * If the column is not found or has the wrong type a {@link DataFrameRuntimeException} is thrown.
     *
     * @param name column name
     * @return found column
     */
    @Override
    public StringColumn getStringColumn(String name) {
        return getColumn(name, StringColumn.class);
    }

    /**
     * Returns a {@link DoubleColumn}
     * If the column is not found or has the wrong type a {@link DataFrameRuntimeException} is thrown.
     *
     * @param name column name
     * @return found column
     */
    @Override
    public DoubleColumn getDoubleColumn(String name) {
        return getColumn(name, DoubleColumn.class);
    }

    /**
     * Returns a {@link IntegerColumn}
     * If the column is not found or has the wrong type a {@link DataFrameRuntimeException} is thrown.
     *
     * @param name column name
     * @return found column
     */
    @Override
    public IntegerColumn getIntegerColumn(String name) {
        return getColumn(name, IntegerColumn.class);
    }

    /**
     * Returns a {@link FloatColumn}
     * If the column is not found or has the wrong type a {@link DataFrameRuntimeException} is thrown.
     *
     * @param name column name
     * @return found column
     */
    @Override
    public FloatColumn getFloatColumn(String name) {
        return getColumn(name, FloatColumn.class);
    }

    /**
     * Returns a {@link BooleanColumn}
     * If the column is not found or has the wrong type a {@link DataFrameRuntimeException} is thrown.
     *
     * @param name column name
     * @return found column
     */
    @Override
    public BooleanColumn getBooleanColumn(String name) {
        return getColumn(name, BooleanColumn.class);
    }

    /**
     * Returns a {@link ByteColumn}
     * If the column is not found or has the wrong type a {@link DataFrameRuntimeException} is thrown.
     *
     * @param name column name
     * @return found column
     */
    @Override
    public ByteColumn getByteColumn(String name) {
        return getColumn(name, ByteColumn.class);
    }

    /**
     * Returns a {@link LongColumn}
     * If the column is not found or has the wrong type a {@link DataFrameRuntimeException} is thrown.
     *
     * @param name column name
     * @return found column
     */
    @Override
    public LongColumn getLongColumn(String name) {
        return getColumn(name, LongColumn.class);
    }

    /**
     * Returns a {@link ShortColumn}
     * If the column is not found or has the wrong type a {@link DataFrameRuntimeException} is thrown.
     *
     * @param name column name
     * @return found column
     */
    @Override
    public ShortColumn getShortColumn(String name) {
        return getColumn(name, ShortColumn.class);
    }


    /**
     * Groups this data frame using one or more columns
     *
     * @param column group columns
     * @return {@link DataGrouping data grouping}
     * @see GroupUtil#groupBy(DataFrame, String...)
     */
    @Override
    public DataGrouping groupBy(String... column) {
        return groupUtil.groupBy(this, column);
    }

    /**
     * Joins this data frame with another data frame using the <tt>LEFT JOIN</tt> method.
     *
     * @param dataFrame   other data frame
     * @param joinColumns join columns
     * @return joined data frame
     * @see JoinUtil#leftJoin(DataFrame, DataFrame, JoinColumn...)
     */
    @Override
    public JoinedDataFrame joinLeft(DataFrame dataFrame, String... joinColumns) {
        JoinColumn[] joinColumnsArray = new JoinColumn[joinColumns.length];
        for (int i = 0; i < joinColumns.length; i++) {
            joinColumnsArray[i] = new JoinColumn(joinColumns[i]);
        }
        return joinLeft(dataFrame, joinColumnsArray);
    }

    /**
     * Joins this data frame with another data frame using the <tt>LEFT JOIN</tt> method.
     *
     * @param dataFrame   other data frame
     * @param joinColumns join columns
     * @return joined data frame
     * @see JoinUtil#leftJoin(DataFrame, DataFrame, JoinColumn...)
     */
    @Override
    public JoinedDataFrame joinLeft(DataFrame dataFrame, JoinColumn... joinColumns) {
        return joinUtil.leftJoin(this, dataFrame, joinColumns);
    }

    /**
     * Joins this data frame with another data frame using the <tt>LEFT JOIN</tt> method.
     * Column names are altered using the provided suffixes.
     *
     * @param dataFrame   other data frame
     * @param suffixA     suffixes for columns from this data frame
     * @param suffixB     suffixes for columns from the other data frame
     * @param joinColumns join columns
     * @return joined data frame
     * @see JoinUtil#leftJoin(DataFrame, DataFrame, String, String, JoinColumn...)
     */
    @Override
    public JoinedDataFrame joinLeft(DataFrame dataFrame, String suffixA, String suffixB, JoinColumn... joinColumns) {
        return joinUtil.leftJoin(this, dataFrame, suffixA, suffixB, joinColumns);
    }

    /**
     * Joins this data frame with another data frame using the <tt>RIGHT JOIN</tt> method.
     *
     * @param dataFrame   other data frame
     * @param joinColumns join columns
     * @return joined data frame
     * @see JoinUtil#rightJoin(DataFrame, DataFrame, JoinColumn...)
     */
    @Override
    public JoinedDataFrame joinRight(DataFrame dataFrame, String... joinColumns) {
        JoinColumn[] joinColumnsArray = new JoinColumn[joinColumns.length];
        for (int i = 0; i < joinColumns.length; i++) {
            joinColumnsArray[i] = new JoinColumn(joinColumns[i]);
        }
        return joinRight(dataFrame, joinColumnsArray);
    }

    /**
     * Joins this data frame with another data frame using the <tt>LEFT JOIN</tt> method.
     *
     * @param dataFrame   other data frame
     * @param joinColumns join columns
     * @return joined data frame
     * @see JoinUtil#leftJoin(DataFrame, DataFrame, JoinColumn...)
     */
    @Override
    public JoinedDataFrame joinRight(DataFrame dataFrame, JoinColumn... joinColumns) {
        return joinUtil.rightJoin(this, dataFrame, joinColumns);
    }

    /**
     * Joins this data frame with another data frame using the <tt>RIGHT JOIN</tt> method.
     * Column names are altered using the provided suffixes.
     *
     * @param dataFrame   other data frame
     * @param suffixA     suffixes for columns from this data frame
     * @param suffixB     suffixes for columns from the other data frame
     * @param joinColumns join columns
     * @return joined data frame
     * @see JoinUtil#rightJoin(DataFrame, DataFrame, String, String, JoinColumn...)
     */
    @Override
    public JoinedDataFrame joinRight(DataFrame dataFrame, String suffixA, String suffixB, JoinColumn... joinColumns) {
        return joinUtil.rightJoin(this, dataFrame, suffixA, suffixB, joinColumns);
    }

    /**
     * Joins this data frame with another data frame using the <tt>INNER JOIN</tt> method.
     *
     * @param dataFrame   other data frame
     * @param joinColumns join columns
     * @return joined data frame
     * @see JoinUtil#innerJoin(DataFrame, DataFrame, JoinColumn...)
     */
    @Override
    public JoinedDataFrame joinInner(DataFrame dataFrame, String... joinColumns) {
        JoinColumn[] joinColumnsArray = new JoinColumn[joinColumns.length];
        for (int i = 0; i < joinColumns.length; i++) {
            joinColumnsArray[i] = new JoinColumn(joinColumns[i]);
        }
        return joinInner(dataFrame, joinColumnsArray);
    }

    /**
     * Joins this data frame with another data frame using the <tt>INNER JOIN</tt> method.
     *
     * @param dataFrame   other data frame
     * @param joinColumns join columns
     * @return joined data frame
     * @see JoinUtil#innerJoin(DataFrame, DataFrame, JoinColumn...)
     */
    @Override
    public JoinedDataFrame joinInner(DataFrame dataFrame, JoinColumn... joinColumns) {
        return joinUtil.innerJoin(this, dataFrame, joinColumns);
    }

    /**
     * Joins this data frame with another data frame using the <tt>INNER JOIN</tt> method.
     * Column names are altered using the provided suffixes.
     *
     * @param dataFrame   other data frame
     * @param suffixA     suffixes for columns from this data frame
     * @param suffixB     suffixes for columns from the other data frame
     * @param joinColumns join columns
     * @return joined data frame
     * @see JoinUtil#innerJoin(DataFrame, DataFrame, String, String, JoinColumn...)
     */
    @Override
    public JoinedDataFrame joinInner(DataFrame dataFrame, String suffixA, String suffixB, JoinColumn... joinColumns) {
        return joinUtil.innerJoin(this, dataFrame, suffixA, suffixB, joinColumns);
    }

    /**
     * Returns a copy of this data frame.
     * Header, columns, rows and indices are copied.
     *
     * @return copy of data frame
     */
    @Override
    public DataFrame copy() {
        List<DataRow> rows = getRows(0, size);
        DefaultDataFrame copy = new DefaultDataFrame();
        copy.set(header.copy(), rows, indices);
        return copy;
    }

    /**
     * Returns <tt>true</tt> if this data frame contains the input column
     *
     * @param column input column
     * @return <tt>true</tt> if this data frame contains the input column
     */
    @Override
    public boolean containsColumn(DataFrameColumn column) {
        return this.columnList.contains(column);
    }

    /**
     * Notifies this data frame about a changed value in a column.
     * Used to update indices.
     *
     * @param column changed column
     * @param index  changed index
     * @param value  new value
     */
    protected void notifyColumnValueChanged(DataFrameColumn column, int index, Comparable value) {
        if (indices.isIndexColumn(column)) {
            indices.updateValue(column, getRow(index));
        }
    }

    /**
     * Notifies this data frame about a changed  column.
     * Used to update indices.
     *
     * @param column changed column
     */
    protected void notifyColumnChanged(DataFrameColumn column) {
        if (indices.isIndexColumn(column)) {
            indices.updateColumn(column);
        }
    }

    /**
     * Returns <tt>true</tt> if the input column is part of at least one index
     *
     * @param column input column
     * @return <tt>true</tt> if column is part of index
     */
    @Override
    public boolean isIndexColumn(DataFrameColumn column) {
        return indices.isIndexColumn(column);
    }

    /**
     * Finds matching data rows using an index and the corresponding index values
     *
     * @param name   name of index
     * @param values index values
     * @return rows found
     */
    @Override
    public List<DataRow> findByIndex(String name, Comparable... values) {
        Collection<Integer> rowIndices = indices.find(name, values);
        if (!rowIndices.isEmpty()) {
            List<DataRow> rows = new ArrayList<>();
            for (Integer i : rowIndices) {
                rows.add(getRow(i));
            }
            return rows;
        }
        return new ArrayList<>(0);
    }

    /**
     * Finds the first data row matching an index and the corresponding index values
     *
     * @param name   name of index
     * @param values index values
     * @return rows found
     */
    @Override
    public DataRow findFirstByIndex(String name, Comparable... values) {
        Integer idx = indices.findFirst(name, values);
        return idx == null ? null : getRow(idx);
    }

    /**
     * Returns a collection of all columns in this data frame
     *
     * @return collection of columns
     */
    @Override
    public Collection<DataFrameColumn> getColumns() {
        return columnList;
    }

    @Override
    public Iterable<? extends DataRow> rows() {
        return this;
    }

    /**
     * Returns the indices of this data frame
     *
     * @return data frame indices
     */
    protected Indices getIndices() {
        return indices;
    }

    public GroupUtil getGroupUtil() {
        return groupUtil;
    }

    public JoinUtil getJoinUtil() {
        return joinUtil;
    }

    public void setGroupUtil(GroupUtil groupUtil) {
        this.groupUtil = groupUtil;
    }

    public void setJoinUtil(JoinUtil joinUtil) {
        this.joinUtil = joinUtil;
    }

    @Override
    public <T> List<T> map(Class<T> cl) {
        return DataMapper.map(this, cl);
    }

    /**
     * Returns an iterator over the rows in this data frame.
     * {@link Iterator#remove()} is not supported.
     *
     * @return row iterator
     */
    @Override
    public Iterator<DataRow> iterator() {
        return new Iterator<DataRow>() {
            private int index = 0;

            @Override
            public boolean hasNext() {
                return index < size;
            }

            @Override
            public DataRow next() {
                if (index == size()) {
                    throw new NoSuchElementException("index out of bounds");
                }
                return getRow(index++);
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException("remove is not supported for data frames");
            }
        };
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || !(o instanceof DefaultDataFrame)) {
            return false;
        }
        if (o == this) {
            return true;
        }
        DataFrame d = (DataFrame) o;
        if (size() != d.size()) {
            return false;
        }

        for (int i = 0; i < size(); i++) {
            if (!getRow(i).equals(d.getRow(i))) {
                return false;
            }
        }
        return true;
    }
}
