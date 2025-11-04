/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.antlr4.tool.model;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.xml.XNode;

import java.util.Collections;
import java.util.List;

public abstract class GrammarElement {
    private SourceLocation location;

    /**
     * 在打印本token前是否增加缩进
     */
    private boolean indent;

    /**
     * 在打印本token前是否换行
     */
    private int br;

    private AstRule parentRule;

    private String propName;
    private String propLabel;

    private String altLabel;

    /**
     * 当AST节点属性为列表对象，而解析规则实际只解析到单一元素时，例如 declaration: kind=varModifier_ declarators_single=variableDeclarator;
     * Declaration的decorators属性类型为List<VariableDeclarator>, 但是此时语法规则只允许一个VariableDeclarator
     */
    private boolean single;

    private List<GrammarElement> children = Collections.emptyList();

    /**
     * 当语法规则解析得到的类型不是AST节点类型或者AST节点的列表类型时，需要明确指定解析得到的属性名
     */
    private String returnType;

    public AstRule getParentRule() {
        return parentRule;
    }

    public void setParentRule(AstRule parentRule) {
        this.parentRule = parentRule;
    }

    public abstract GrammarElementKind getKind();

    public boolean isRuleRef() {
        return false;
    }

    public String getType() {
        return getClass().getSimpleName();
    }

    public SourceLocation getLocation() {
        return location;
    }

    public void setLocation(SourceLocation location) {
        this.location = location;
    }

    public boolean isSingle() {
        return single;
    }

    public void setSingle(boolean single) {
        this.single = single;
    }

    public XNode toNode() {
        XNode node = XNode.make(getClass().getSimpleName());
        node.setLocation(location);
        if (propName != null) {
            node.setAttr("propName", propName);
        }
        if (indent) {
            node.setAttr("indent", true);
        }
        if (br > 0) {
            node.setAttr("br", br);
        }

        if (children != null) {
            for (GrammarElement child : children) {
                node.appendChild(child.toNode());
            }
        }
        return node;
    }

    public String getPropLabel() {
        return propLabel;
    }

    public void setPropLabel(String propLabel) {
        this.propLabel = propLabel;
    }

    public String getPropName() {
        return propName;
    }

    public void setPropName(String propName) {
        this.propName = propName;
    }

    public boolean isIndent() {
        return indent;
    }

    public void setIndent(boolean indent) {
        this.indent = indent;
    }

    public int getBr() {
        return br;
    }

    public void setBr(int br) {
        this.br = br;
    }

    /**
     * 只有特殊的RuleRef才允许返回列表，而且列表必须是AST节点列表
     */
    public boolean isReturnList() {
        return false;
    }

    public List<GrammarElement> getChildren() {
        return children;
    }

    public void setChildren(List<GrammarElement> children) {
        this.children = children;
    }

    public String getReturnType() {
        return returnType;
    }

    public void setReturnType(String returnType) {
        this.returnType = returnType;
    }

    public final String getAltLabel() {
        return altLabel;
    }

    public final void setAltLabel(String altLabel) {
        this.altLabel = altLabel;
    }

    /**
     * 从ParseTree解析到AST时的函数名
     */
    public String getParseFuncName() {
        if (parentRule == null)
            return null;

        return getParentRule().getAstNodeName() + "_" + getPropName();
    }

    public String getParseFuncDecl() {
        if (parentRule == null)
            return null;

        return returnType + " " + parentRule.getAstNodeName() + "_" + getPropName()
                + "(org.antlr.v4.runtime.Token token)";
    }
}