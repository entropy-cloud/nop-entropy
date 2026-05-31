package com.example.service;

import com.example.domain.User;
public class UserService {
    private final User user;

    public UserService(User user) {
        this.user = user;
    }

    public void changeName(String newName) {
        user.setName(newName);
        user.updateEmail(user.getEmail());
    }

    public String getInfo() {
        return user.getDisplayName();
    }
}
