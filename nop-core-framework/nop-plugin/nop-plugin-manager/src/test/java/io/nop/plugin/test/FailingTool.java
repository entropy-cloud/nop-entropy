package io.nop.plugin.test;

/**
 * 方法抛业务异常的工具实现（ServiceProxy 异常解包回归测试用）。
 */
public class FailingTool implements IFailingTool {
    @Override
    public String fail() {
        throw new IllegalStateException("tool boom");
    }
}
