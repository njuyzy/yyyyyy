package com.example.Japp.data;

import java.io.Serializable;

public class User implements Serializable {

    public User(){
        id=order.Generate_id();
    }

    public User(String name,String phone,String password){
        id=order.Generate_id();
        this.name=name;
        this.Phone=phone;
        this.password=password;
    }
    private String password,id,Phone,name;

    public String getUsername() {
        return name;
    }

    public String getId() {
        return id;
    }

    public String getPhone() {
        return Phone;
    }

    public void setUsername(String name) {
        this.name=name;
    }


    public String getPassword() {
        return password;
    }

    public Mode getMode() {
        return mode;
    }


    public enum Mode{LEADER,USER,NULL}
    private Mode mode=Mode.NULL;

    public void setMode(Mode mode){
        this.mode=mode;
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
}
