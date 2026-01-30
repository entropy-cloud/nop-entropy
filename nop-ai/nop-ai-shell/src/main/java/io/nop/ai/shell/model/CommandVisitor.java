package io.nop.ai.shell.model;

/**
 * 访问者模式接口，用于遍历表达式树
 *
 * @param <T> 返回类型
 */
public interface CommandVisitor<T> {

    /**
     * 访问简单命令
     */
    T visit(SimpleCommand cmd);

    /**
     * 访问管道表达式
     */
    T visit(PipelineExpr pipe);

    /**
     * 访问逻辑表达式
     */
    T visit(LogicalExpr logical);

    /**
     * 访问大括号分组
     */
    T visit(GroupExpr group);

    /**
     * 访问子shell表达式
     */
    T visit(SubshellExpr subshell);

    /**
     * 访问后台运行表达式
     */
    T visit(BackgroundExpr background);
}
