package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

/**
 * truffle 翻译 AST 的表达式节点基类：程序化构造（非 DSL 派生），子节点由翻译器经构造器注入。
 *
 * <p>SourceSection 支持按 Truffle 官方推荐形态实现：节点持有翻译期固化的 section 字段并覆写
 * {@link #getSourceSection()}（Node 无公开 setter，字段自持是文档化模式）。
 */
public abstract class XExprNode extends Node {

    private SourceSection section;

    public abstract Object execute(VirtualFrame frame);

    @Override
    public SourceSection getSourceSection() {
        return section;
    }

    public void setSourceSection(SourceSection section) {
        this.section = section;
    }
}
