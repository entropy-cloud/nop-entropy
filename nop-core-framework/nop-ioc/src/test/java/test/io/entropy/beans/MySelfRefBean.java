package test.io.entropy.beans;

/**
 * prototype 构造器自引用回归测试用 bean：构造器引用自身类型。
 */
public class MySelfRefBean {
    public MySelfRefBean(MySelfRefBean other) {
    }
}
