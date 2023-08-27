package io.nop.stream.cep;

public class SubEvent extends Event {
    private double volume;

    public SubEvent(int id, String name, double volume) {
        super(id, name);
        this.volume = volume;
    }

    public double getVolume() {
        return volume;
    }

    public void setVolume(double volume) {
        this.volume = volume;
    }
}
