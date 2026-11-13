package io.nop.ioc.impl;

public class BrokenDestroyBean {
    public static boolean iocDestroyRan = false;

    public void markIocDestroyRan() {
        iocDestroyRan = true;
    }

    public void brokenDestroy() {
        throw new IllegalStateException("destroy-method boom");
    }
}
