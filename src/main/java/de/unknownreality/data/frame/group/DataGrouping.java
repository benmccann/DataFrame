package de.unknownreality.data.frame.group;

import de.unknownreality.data.frame.filter.FilterPredicate;

import java.util.*;

/**
 * Created by Alex on 10.03.2016.
 */
public class DataGrouping implements Iterable<DataGroup>{
    private DataGroup[] groups;
    private String[] groupColumns;
    private Map<GroupKey,DataGroup> groupMap = new HashMap<>();

    public DataGrouping(Collection<DataGroup> groups,String... groupColumns) {
        this.groupColumns = groupColumns;
        this.groups = new DataGroup[groups.size()];
        groups.toArray(this.groups);
        for(DataGroup g : groups){
            groupMap.put(new GroupKey(g.getGroupValues()),g);
        }
    }

    public DataGroup findByGroupValues(Object... values){
        if(values.length != groupColumns.length){
            throw new IllegalArgumentException("values must have same length as GroupColumns");
        }
        return groupMap.get(new GroupKey(values));
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
            if(predicate.valid(row)){
                return row;
            }
        }
        return null;
    }

    private List<DataGroup> findGroups(FilterPredicate predicate){
        List<DataGroup> groups = new ArrayList<>();
        for(DataGroup g : this) {
            if (predicate.valid(g)) {
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
