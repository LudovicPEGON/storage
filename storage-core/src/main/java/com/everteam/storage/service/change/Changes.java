package com.everteam.storage.service.change;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Changes  {
    List<Change> items; 
    String token;

    public Changes(String token) {
        super();
        this.items = new ArrayList<>();
        this.token = token;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public List<Change> getItems() {
        return items;
    }

    public void setItems(List<Change> items) {
        this.items = items;
    }

    public int size() {
        return items.size();
    }

    public void limit(int maxSize) {
        sort();
        if (items.size()>maxSize) {
            items = items.subList(0, maxSize);
        }
        
    }

    

    private void sort() {
        items.sort(new Comparator<Change>() {

            @Override
            public int compare(Change o1, Change o2) {
                return o1.getDate().compareTo(o2.getDate());
            }
            
        });
        
    }

    public void add(Change change) {
        items.add(change);
    }

    public OffsetDateTime getLastItemDate() {
        OffsetDateTime date = null;
        if (items.size()>0) {
            date = items.get(items.size()-1).getDate();
        }
        return date;
    }

    



}