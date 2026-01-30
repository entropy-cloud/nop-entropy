package io.nop.ai.shell.model;

/**
 * 命令行表达式基接口
 * 所有命令行元素都实现此接口，形成表达式树
 */
public interface CommandExpression {

    /**
     * 返回表达式的字符串表示
     * 应能重建原始命令行（可能格式化不同）
     */
    @Override
    String toString();

    /**
     * 接受访问者遍历
     *
     * @param visitor 访问者对象
     * @param <T> 返回类型
     * @return 访问结果
     */
    <T> T accept(CommandVisitor<T> visitor);
}
