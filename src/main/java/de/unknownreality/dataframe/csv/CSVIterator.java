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

package de.unknownreality.dataframe.csv;

import de.unknownreality.dataframe.DataFrame;
import de.unknownreality.dataframe.DataFrameBuilder;
import de.unknownreality.dataframe.common.StringUtil;
import de.unknownreality.dataframe.io.BufferedStreamIterator;
import de.unknownreality.dataframe.io.ColumnInformation;
import de.unknownreality.dataframe.io.DataIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.util.*;

/**
 * Created by Alex on 17.06.2017.
 */
public class CSVIterator extends BufferedStreamIterator<CSVRow> implements DataIterator<CSVRow> {
    private static final Logger log = LoggerFactory.getLogger(CSVIterator.class);

    private int lineNumber = 0;
    private CSVSettings csvSettings;
    private ColumnSettings columnSettings;
    private CSVHeader header = new CSVHeader();
    private int cols = -1;
    private Set<String> ignoredColumns;
    private Set<String> includedColumns;
    Map<String, Class<? extends Comparable>> colTypes;
    Map<String, Integer> selectedColumnsIndex = new HashMap<>();
    private List<ColumnInformation> columnInformations = new ArrayList<>();
    private CSVRow bufferedRow = null;
    public CSVIterator(BufferedReader reader, CSVSettings csvSettings, ColumnSettings columnSettings) {
        super(reader);
        this.csvSettings = csvSettings;
        this.columnSettings = columnSettings;
        ignoredColumns = new HashSet<>(columnSettings.getIgnoreColumns());
        includedColumns = new HashSet<>(columnSettings.getSelectColumns());
        colTypes = new HashMap<>(columnSettings.getColumnTypeMap());
        int j = 0;
        for (String col : columnSettings.getSelectColumns()) {
            selectedColumnsIndex.put(col, j++);
        }
        loadNext();
        initHeader();

    }

    /**
     * Creates a DataFrameBuilder using this CSV iterator
     * @return DataFrameBuilder {@link DataFrameBuilder}
     * @deprecated use {@link DataFrame#fromCSV} or {@link DataFrame#load} instead.
     */
    @Deprecated
    public DataFrameBuilder toDataFrame(){
        return DataFrameBuilder.createFrom(this);
    }

    /**
     * Reads and created the csv header
     */
    public void initHeader() {
        try {
            CSVRow row = next();
            if (csvSettings.isContainsHeader()) {
                if (!row.get(0).startsWith(csvSettings.getHeaderPrefix())) {
                    throw new CSVException("invalid header prefix in first line");
                }
                String name = row.get(0);
                name = csvSettings.getHeaderPrefix() == null ? name : name.substring(csvSettings.getHeaderPrefix().length());
                if (includeColumn(name)) {
                    header.add(name);
                }
                else{
                    header.incrementEmptyColumnIndex();
                }
                for (int i = 1; i < row.size(); i++) {
                    name = row.get(i);
                    if (!includeColumn(name)) {
                        continue;
                    }
                    header.add(name);
                }
            } else {
                for (int i = 0; i < row.size(); i++) {
                    if (!includeColumn(header.getNextEmptyColumnName())) {
                        header.incrementEmptyColumnIndex();
                        continue;
                    }
                    header.add();
                }
                bufferedRow = row;
            }
            for (int i = 0; i < header.size(); i++) {
                ColumnInformation columnInformation;
                String name = header.get(i);
                Class<? extends Comparable> type;
                if ((type = colTypes.get(name)) != null) {
                    columnInformation = new ColumnInformation(i, name, type);
                } else {
                    columnInformation = new ColumnInformation(i, name, true);
                }
                this.columnInformations.add(columnInformation);
            }
        } catch (Exception e) {
            throw new CSVRuntimeException("error creating csv header", e);
        }
    }

    private boolean includeColumn(String col) {
        if (includedColumns.isEmpty()) {
            return !ignoredColumns.contains(col);
        }
        return includedColumns.contains(col);
    }

    @Override
    public CSVRow next() {
        if(bufferedRow != null){
            CSVRow nextRow = bufferedRow;
            bufferedRow = null;
            return nextRow;
        }
        return super.next();
    }

    /**
     * Reads the csv input stream and returns a csv row
     *
     * @return next csv row
     */
    @Override
    protected CSVRow getNext() {

        try {
            lineNumber++;
            String line = getLine();
            while (line != null && "".equals(line.trim())) {
                line = getLine();
            }
            if (line == null) {
                return null;
            }
            for (String prefix : csvSettings.getIgnorePrefixes()) {
                if (prefix != null && !"".equals(prefix) && line.startsWith(prefix)) {
                    return getNext();
                }
            }
            String[] values = StringUtil.splitQuoted(line, csvSettings.getSeparator());
            if (cols == -1) {
                cols = values.length;
            } else {
                if (values.length != cols) {
                    throw new CSVException(String.format("unequal number of column %d != %d in line %d", values.length, cols, lineNumber));
                }
            }
            return new CSVRow(header, values, lineNumber);

        } catch (Exception e) {
            log.error("error reading file: {}:{}", lineNumber, e);
            close();
            throw new CSVRuntimeException(String.format("error reading csv row: %d", lineNumber),e);
        }
    }

    @Override
    public List<ColumnInformation> getColumnsInformation() {
        return columnInformations;
    }

    @Override
    public Iterator<CSVRow> iterator() {
        return this;
    }
}
