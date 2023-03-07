/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.antlr4.tool.model;

import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;

public class RuleRef extends GrammarElement {
    private String ruleName;

    private AstRule resolvedRule;

    @Override
    public GrammarElementKind getKind() {
        return GrammarElementKind.RULE_REF;
    }

    public boolean isRuleRef() {
        return true;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(RuleRef.class.getSimpleName());
        sb.append('[');
        if (getPropName() != null) {
            sb.append(getPropName());
            sb.append('=');
        }
        sb.append(ruleName);
        sb.append(']');
        return sb.toString();
    }

    public void setResolvedRule(AstRule resolvedRule) {
        this.resolvedRule = resolvedRule;
    }

    public AstRule getResolvedRule() {
        return resolvedRule;
    }

    public boolean isReturnList() {
        if (resolvedRule == null)
            return false;

        return resolvedRule.isReturnList();
    }

    public boolean notInAst() {
        return ruleName.endsWith("__");
    }

    public String getReturnAstNodeName() {
        if (resolvedRule != null)
            return resolvedRule.getReturnAstNodeName();
        return null;
    }

    public String getAstNodeName() {
        if (resolvedRule != null) {
            return resolvedRule.getAstNodeName();
        }
        return null;
    }

    public String getContextName() {
        return StringHelper.capitalize(ruleName) + "Context";
    }

    public XNode toNode() {
        XNode node = super.toNode();
        node.setAttr("ruleName", ruleName);
        if (isReturnList())
            node.setAttr("needList", true);
        return node;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public String getReturnType() {
        if (super.getReturnType() != null)
            return super.getReturnType();
        if (resolvedRule != null)
            return resolvedRule.getReturnType();
        return null;
    }

    @Override
    public String getParseFuncName() {
        if (getReturnAstNodeName() != null || isReturnList()) {
            return resolvedRule.getParseFuncName();
        }

        if (getParentRule() == null)
            return null;

        return getParentRule().getAstNodeName() + "_" + getPropName();
    }

    @Override
    public String getParseFuncDecl() {
        if (getReturnAstNodeName() != null || isReturnList()) {
            return resolvedRule.getParseFuncDecl();
        }

        if (getParentRule() == null)
            return null;

        return getReturnType() + " " + getParentRule().getAstNodeName() + "_" + getPropName() + "(ParseTree node)";
    }
}
