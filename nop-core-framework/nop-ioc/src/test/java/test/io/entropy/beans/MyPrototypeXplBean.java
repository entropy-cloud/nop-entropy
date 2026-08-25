package test.io.entropy.beans;

/**
 * prototype 作用域 + ioc:init xpl 回归测试用 bean：xpl 内调用 this.markInitialized()。
 */
public class MyPrototypeXplBean {
    private boolean initialized;

    public boolean isInitialized() {
        return initialized;
    }

    public void markInitialized() {
        this.initialized = true;
    }
}
