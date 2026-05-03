package com.example.domain;

import com.example.service.IUserService;
import com.example.annotation.Audited;

/**
 * User entity representing a system user.
 */
@Audited
public class User extends BaseEntity implements IUserService {
    private String name;
    private String email;
    private int age;

    public User() {}

    public User(String name, String email) {
        this.name = name;
        this.email = email;
    }

    @Override
    public String getDisplayName() {
        return name + " <" + email + ">";
    }

    public void updateEmail(String newEmail) {
        this.email = newEmail;
        validate();
    }

    private void validate() {
        if (email == null || email.isEmpty()) {
            throw new IllegalArgumentException("Email cannot be null");
        }
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public int getAge() { return age; }
}
