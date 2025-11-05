package test.io.entropy.beans;

import jakarta.annotation.PostConstruct;

public class MyCycleB {
    private MyCycleA propA;

    public MyCycleA getPropA() {
        return propA;
    }

    public void setPropA(MyCycleA propA) {
        this.propA = propA;
    }

    @PostConstruct
    public void init() {
        System.out.println("init MyCycleB");
    }
}
