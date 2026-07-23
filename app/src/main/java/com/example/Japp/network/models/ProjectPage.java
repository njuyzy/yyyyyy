package com.example.Japp.network.models;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class ProjectPage {
    @SerializedName("items")
    private List<Project> items;

    @SerializedName("total")
    private long total;

    @SerializedName("pageNum")
    private int pageNum;

    @SerializedName("pageSize")
    private int pageSize;

    @SerializedName("pages")
    private int pages;

    public List<Project> getItems() { return items; }
    public long getTotal() { return total; }
    public int getPageNum() { return pageNum; }
    public int getPageSize() { return pageSize; }
    public int getPages() { return pages; }
}
