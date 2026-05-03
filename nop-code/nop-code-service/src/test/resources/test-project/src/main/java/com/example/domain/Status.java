package com.example.domain;

public enum Status {
    ACTIVE("Active user"),
    INACTIVE("Inactive user"),
    SUSPENDED("Suspended user");

    private final String description;

    Status(String description) {
        this.description = description;
    }

    public String getDescription() { return description; }
}
