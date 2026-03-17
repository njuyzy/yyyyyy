package com.example.Japp.data;

import java.io.Serializable;

public class User implements Serializable {

    public User(){
        id=order.Generate_id();
    }
    private String password,id,Phone,name,mail;

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

    public void setEmail(String mail) {
        this.mail=mail;
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
