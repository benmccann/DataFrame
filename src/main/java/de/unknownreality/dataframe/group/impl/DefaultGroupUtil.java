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

package de.unknownreality.dataframe.group.impl;

import de.unknownreality.dataframe.*;
import de.unknownreality.dataframe.group.DataGroup;
import de.unknownreality.dataframe.group.DataGrouping;
import de.unknownreality.dataframe.group.GroupUtil;
import de.unknownreality.dataframe.sort.SortColumn;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Alex on 10.03.2016.
 */
public class DefaultGroupUtil implements GroupUtil {
    /**
     * Groups a {@link DefaultDataFrame} using one or more columns.
     *
     * @param df      input data frame
     * @param columns grouping columns
     * @return data grouping
     */
    public DataGrouping groupBy(DataFrame df, String... columns) {
        SortColumn[] sortColumns = new SortColumn[columns.length];
        for (int i = 0; i < columns.length; i++) {
            sortColumns[i] = new SortColumn(columns[i]);
        }
        DataFrame sortedFrame = df.copy().sort(sortColumns);
        List<DataRow> currentList = new ArrayList<>();
        Comparable[] lastValues = null;
        List<DataGroup> groupList = new ArrayList<>();
        for (DataRow row : sortedFrame) {
            if (lastValues == null || equals(lastValues, row, columns)) {
                currentList.add(row);
                if (lastValues == null) {
                    lastValues = new Comparable[columns.length];
                    set(lastValues, row, columns);
                }
                continue;
            }
            if (!currentList.isEmpty()) {
                DataGroup group = new DataGroup(columns, lastValues);
                group.set(createHeader(df.getHeader()), currentList);
                groupList.add(group);
            }
            currentList.clear();
            currentList.add(row);
            set(lastValues, row, columns);
        }
        if (!currentList.isEmpty()) {
            DataGroup group = new DataGroup(columns, lastValues);
            group.set(createHeader(df.getHeader()), currentList);
            groupList.add(group);
        }
        return new DataGrouping(groupList, createGroupColumns(df, columns));
    }

    private static DataFrameColumn[] createGroupColumns(DataFrame df, String... columns){
        DataFrameColumn[] groupColumns = new DataFrameColumn[columns.length];
        for(int i = 0; i < columns.length; i++){
            DataFrameColumn orgCol = df.getColumn(columns[i]);
            groupColumns[i] = orgCol.copyEmpty();
        }
        return groupColumns;
    }

    private static DataFrameHeader createHeader(DataFrameHeader header){
        if(header instanceof DataFrameHeader){
            return ((DataFrameHeader)header).copy();
        }
        else{
            DataFrameHeader basicDataFrameHeader = new DataFrameHeader();
            for(String h : header){
                basicDataFrameHeader.add(h, header.getColumnType(h), header.getType(h));
            }
            return basicDataFrameHeader;
        }
    }


    private static boolean equals(Object[] values, DataRow row, String[] columns) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == null && !row.isNA(i)) {
                return false;
            }
            if (values[i] != null && row.isNA(i)) {
                return false;
            }
            if (values[i] == null && row.isNA(i)) {
                continue;
            }
            if (!values[i].equals(row.get(columns[i]))) {
                return false;
            }
        }
        return true;
    }

    private static void set(Object[] values, DataRow row, String[] columns) {
        for (int i = 0; i < values.length; i++) {
            values[i] = row.get(columns[i]);
        }
    }
}
