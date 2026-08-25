package test.io.entropy.beans;

/**
 * 多占位符配置表达式回归测试用 bean：保存拼接后的配置值。
 */
public class MyConfigVarBean {
    private String value;

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
