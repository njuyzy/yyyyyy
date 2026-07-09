package com.example.Japp.network.models;

import com.google.gson.annotations.SerializedName;

public class Region {
    @SerializedName("adcode")
    private String adcode;

    @SerializedName("name")
    private String name;

    @SerializedName("level")
    private int level;

    @SerializedName("parentAdcode")
    private String parentAdcode;

    @SerializedName("citycode")
    private String citycode;

    @SerializedName("isVirtual")
    private int isVirtual;

    @SerializedName("hasChildren")
    private int hasChildren;

    public String getAdcode() { return adcode; }
    public void setAdcode(String adcode) { this.adcode = adcode; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }

    public String getParentAdcode() { return parentAdcode; }
    public void setParentAdcode(String parentAdcode) { this.parentAdcode = parentAdcode; }

    public String getCitycode() { return citycode; }
    public void setCitycode(String citycode) { this.citycode = citycode; }

    public int getIsVirtual() { return isVirtual; }
    public void setIsVirtual(int isVirtual) { this.isVirtual = isVirtual; }

    public int getHasChildren() { return hasChildren; }
    public void setHasChildren(int hasChildren) { this.hasChildren = hasChildren; }
}