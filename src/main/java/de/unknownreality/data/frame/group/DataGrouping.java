package de.unknownreality.data.frame.group;

import de.unknownreality.data.frame.filter.FilterPredicate;
import de.unknownreality.data.frame.sort.SortColumn;

import java.util.*;

/**
 * Created by Alex on 10.03.2016.
 */
public class DataGrouping implements Iterable<DataGroup>{
    private DataGroup[] groups;
    private String[] groupColumns;
    private Map<List,DataGroup> groupMap = new HashMap<>();

    public DataGrouping(Collection<DataGroup> groups,String... groupColumns) {
        this.groupColumns = groupColumns;
        this.groups = new DataGroup[groups.size()];
        groups.toArray(this.groups);
        for(DataGroup g : groups){
            groupMap.put(Arrays.asList(g.getGroupValues().getValues()),g);
        }
    }

    public DataGroup findByGroupValues(Object... values){
        if(values.length != groupColumns.length){
            throw new IllegalArgumentException("values must have same length as GroupColumns");
        }
        return groupMap.get(Arrays.asList(values));
    }
    public String[] getGroupColumns() {
        return groupColumns;
    }

    public int size(){
        return groups.length;
    }


    public DataGrouping filter(FilterPredicate predicate){
        List<DataGroup> groups = findGroups(predicate);
        this.groups = new DataGroup[groups.size()];
        groups.toArray(this.groups);
        return this;
    }

    public DataGrouping find(FilterPredicate predicate){
        List<DataGroup> groups = findGroups(predicate);
        return new DataGrouping(groups,groupColumns);
    }

    public DataGrouping find(String colName,Comparable value){
        return find(FilterPredicate.eq(colName,value));
    }

    public DataGroup findFirst(String colName,Comparable value){
        return findFirst(FilterPredicate.eq(colName,value));

    }

    public DataGroup findFirst(FilterPredicate predicate){
        for(DataGroup row : this){
            if(predicate.valid(row.getGroupValues())){
                return row;
            }
        }
        return null;
    }

    public DataGrouping sort(SortColumn... columns){
        Arrays.sort(groups,new GroupValueComparator(columns));
        return this;
    }

    public DataGrouping sort(String name){
        return sort(name, SortColumn.Direction.Ascending);
    }
    public DataGrouping sort(String name, SortColumn.Direction dir){
        Arrays.sort(groups,new GroupValueComparator(new SortColumn[]{new SortColumn(name,dir)}));
        return this;
    }

    public DataGrouping sort(Comparator<DataGroup> comp){
        Arrays.sort(groups,comp);
        return this;
    }

    private List<DataGroup> findGroups(FilterPredicate predicate){
        List<DataGroup> groups = new ArrayList<>();
        for(DataGroup g : this) {
            if (predicate.valid(g.getGroupValues())) {
                groups.add(g);
            }
        }
        return groups;
    }
    @Override
    public Iterator<DataGroup> iterator() {
        return new Iterator<DataGroup>() {
            int index = 0;
            @Override
            public boolean hasNext() {
                return index < groups.length;
            }

            @Override
            public DataGroup next() {
                return groups[index++];
            }

            @Override
            public void remove() {

            }
        };
    }

    private static class GroupKey{
        int hash;
        private GroupKey(Object[] values){
            hash = Arrays.hashCode(values);
        }

        public boolean equals(Object o){
            if(o == this){
                return true;
            }
            if(!(o instanceof  GroupKey)){
                return false;
            }
            return hashCode() == ((GroupKey)o).hashCode();
        }
        @Override
        public int hashCode() {
            return hash;
        }
    }
}
