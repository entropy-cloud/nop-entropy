package io.nop.ioc.impl;

public class DestroyOrderBean {
    public static final java.util.List<String> DESTROY_ORDER = new java.util.ArrayList<>();

    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void destroy() {
        DESTROY_ORDER.add(name);
    }
}
