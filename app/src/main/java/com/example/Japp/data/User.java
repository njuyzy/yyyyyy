package com.example.Japp.data;

import androidx.annotation.NonNull;

import com.example.Japp.network.models.Account;

import java.io.Serializable;

import static com.example.Japp.data.ID.Generate_id;
import static com.example.Japp.data.ID.convertLocalIdToServerId;
import static com.example.Japp.data.ID.convertServerIdToLocalId;

public class User implements Serializable {

    public User(){
        id= Generate_id();
    }

    private String ImgUrl;
    private String memberRole;
    private Integer representedCount;
    public User(String name,String phone,String password){
        id= Generate_id();
        this.name=name;
        this.Phone=phone;
        this.password=password;
    }
    private String password="111111";
    private String id;
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
    public String getPhone() {
        return Phone;
    }

    public String getAvatarUrl() {
        return ImgUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.ImgUrl = avatarUrl;
    }

    public String getMemberRole() {
        return memberRole;
    }

    public void setMemberRole(String memberRole) {
        this.memberRole = memberRole;
    }

    public Integer getRepresentedCount() {
        return representedCount;
    }

    public void setRepresentedCount(Integer representedCount) {
        this.representedCount = representedCount;
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
    public void setId(String id) {
        this.id = id;
    }
    // ID转换方法
    public static User fromServerAccount(Account account) {
        if (account == null) return null;

        User user = new User();
        user.setId(convertServerIdToLocalId(account.getId()));
        user.setName(account.getUsername());
        user.setPhone(account.getPhone());
        user.setPassword(account.getPasswordHash());
        user.setAvatarUrl(account.getAvatarUrl());
        return user;
    }

    public static Account toServerAccount(User user) {
        if (user == null) return null;

        Account account = new Account();
        account.setId(convertLocalIdToServerId(user.getId()));
        account.setUsername(user.getUsername());
        account.setPhone(user.getPhone());
        account.setPasswordHash(user.getPassword());
        return account;
    }
}
