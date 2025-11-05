package test.io.entropy.beans;

import jakarta.annotation.PostConstruct;

public class MyCycleA {
    private MyCycleB propB;

    public MyCycleB getPropB() {
        return propB;
    }

    public void setPropB(MyCycleB propB) {
        this.propB = propB;
    }

    @PostConstruct
    public void init() {
        System.out.println("init MyCycleA");
    }
}
