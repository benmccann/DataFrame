package de.unknownreality.dataframe.frame;

/**
 * Created by Alex on 13.03.2016.
 */
public interface ColumnAppender<T extends Comparable<T>> {
    public T createRowValue(DataRow row);
}
