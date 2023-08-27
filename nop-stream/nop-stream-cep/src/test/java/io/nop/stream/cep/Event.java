package io.nop.stream.cep;

public class Event {
    private int id;
    private String name;

    public Event(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public String toString() {
        return "Event[id=" + id + ",name=" + name + "]";
    }

    public Event() {
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}