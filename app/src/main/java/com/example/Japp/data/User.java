package com.example.Japp.data;

import androidx.annotation.NonNull;

import java.io.Serializable;

public class User implements Serializable {

    public User(){
        id=ID.Generate_id();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return id != null && id.equals(user.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }

    private String ImgUrl;
    public User(String name,String phone,String password){
        id=ID.Generate_id();
        this.name=name;
        this.Phone=phone;
        this.password=password;
    }
    private String password="111111";
    private final String id;
    private String Phone;
    private String name="未定义";

    public String getId(String inf) {
        String[] list=inf.split(" ");
        return list[0].split(":")[1];
    }
    public String getUsername(String inf) {
        String[] list=inf.split(" ");
        return list[1].split(":")[1];
    }
    public String getPhone(String inf) {
        String[] list=inf.split(" ");
        return list[2].split(":")[1];
    }


    public String getPassword(String inf) {
        String[] list=inf.split(" ");
        return list[3].split(":")[1];
    }

    public String getId() {
        return id;
    }
    public String getUsername() {return name;}

    public void setUsername(String username) {
        this.name = username;
    }
    public String getPhone() {
        return Phone;
    }


    public String getPassword() {
        return password;
    }


    public void setName(String name){
        this.name=name;
    }

    public void setPhone(String Phone){
        this.Phone=Phone;
    }
    public void setPassword(String password){
        this.password=password;
    }

    @NonNull
    public String toString(){
        return "id:"+id+" Username:"+name+" phoneNumber:"+Phone+" password:"+password;
    }

    public void setId(String currentUserId) {

    }
}
