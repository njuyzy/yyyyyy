package com.example.Japp.data;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

public class Route {

    List<String> attractions=new ArrayList<>();

    public void addAttraction(String name) {
        attractions.add(name);
    }

    @NonNull
    public String toString(){
        if(attractions.isEmpty())
            return "";
        StringBuilder sb=new StringBuilder();
        for(int i=0;i<attractions.size()-1;i++){
            sb.append(attractions.get(i)).append("→");
        }
        sb.append(attractions.get(attractions.size()-1));
        return sb.toString();
  }
}
