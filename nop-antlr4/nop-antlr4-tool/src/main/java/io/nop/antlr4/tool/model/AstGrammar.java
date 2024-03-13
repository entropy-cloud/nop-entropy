/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.antlr4.tool.model;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import static io.nop.antlr4.tool.AntlrToolErrors.ARG_AST_NODE_NAME;
import static io.nop.antlr4.tool.AntlrToolErrors.ARG_PROP_NAME;
import static io.nop.antlr4.tool.AntlrToolErrors.ARG_RULE_NAME;
import static io.nop.antlr4.tool.AntlrToolErrors.ERR_GRAMMAR_INVALID_AST_NODE_NAME;
import static io.nop.antlr4.tool.AntlrToolErrors.ERR_GRAMMAR_INVALID_AST_PROP;

/**
 * 分析g4文件得到ParseGrammar模型，它描述了Antlr解析语法模型(ParseTree)与抽象语法树模型(AST)之间的对应关系。
 */
public class AstGrammar {
    private String name;
    private List<String> astImports;

    /**
     * 除了直接定义的解析规则之外， 还包含解析规则中通过altLabel标记的多个分支
     */
    private Map<String, AstRule> rules = new TreeMap<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getAstImports() {
        return astImports;
    }

    public void setAstImports(List<String> astImports) {
        this.astImports = astImports;
    }

    public String getBaseName() {
        if (name == null)
            return null;
        if (name.endsWith("Parser"))
            return StringHelper.removeTail(name, "Parser");
        if (name.endsWith("Lexer"))
            return StringHelper.removeTail(name, "Lexer");
        return name;
    }

    public boolean isParserGrammar() {
        return name != null && name.endsWith("Parser");
    }

    public boolean containsRule(String name) {
        return rules.containsKey(name);
    }

    public void addRule(AstRule rule) {
        rules.put(rule.getAltLabelOrRuleName(), rule);
    }

    public Collection<AstRule> getRules() {
        return rules.values();
    }

    public AstRule getRule(String name) {
        return rules.get(name);
    }

    public XNode toNode() {
        XNode node = XNode.make(getClass().getSimpleName());
        for (AstRule rule : rules.values()) {
            node.appendChild(rule.toNode());
        }
        return node;
    }

    /**
     * 收集所有需要单独定义的属性解析函数
     */
    public Map<String, List<String>> getPropParseFuncDecls() {
        Map<String, List<String>> fns = new TreeMap<>();
        for (AstRule rule : rules.values()) {
            if (rule.hasProperty() && rule.getAstNodeName() != null) {
                for (GrammarElement prop : rule.getProperties().values()) {
                    if (prop instanceof RuleRef) {
                        RuleRef ruleRef = (RuleRef) prop;
                        if (ruleRef.getReturnAstNodeName() != null || ruleRef.isReturnList())
                            continue;
                    }
                    Guard.notNull(prop.getReturnType(), "returnType");
                    fns.computeIfAbsent(prop.getParseFuncDecl(), k -> new ArrayList<>())
                            .add(rule.getAltLabelOrRuleName());
                }
            } else if (rule.getAstProp() != null) {
                fns.computeIfAbsent(rule.getAstPropParseFuncDecl(), k -> new ArrayList<>())
                        .add(rule.getAltLabelOrRuleName());
            }
        }
        return fns;
    }

    public void validateAstNodeName(Predicate<String> predicate) {
        for (AstRule rule : rules.values()) {
            if (rule.notInAst())
                continue;

            if (rule.getAstNodeName() != null) {
                if (!isDefaultAstNode(rule.getAstNodeName()) && !predicate.test(rule.getAstNodeName()))
                    throw new NopException(ERR_GRAMMAR_INVALID_AST_NODE_NAME).loc(rule.getLocation())
                            .param(ARG_AST_NODE_NAME, rule.getAstNodeName()).param(ARG_RULE_NAME, rule.getRuleName());
            }

            if (rule.isReturnList()) {
                if (!isDefaultAstNode(rule.getElementAstNodeName()) && !predicate.test(rule.getElementAstNodeName()))
                    throw new NopException(ERR_GRAMMAR_INVALID_AST_NODE_NAME).loc(rule.getLocation())
                            .param(ARG_AST_NODE_NAME, rule.getElementAstNodeName())
                            .param(ARG_RULE_NAME, rule.getRuleName());
            } else if (rule.getReturnAstNodeName() != null) {
                if (!isDefaultAstNode(rule.getReturnAstNodeName()) && !predicate.test(rule.getReturnAstNodeName()))
                    throw new NopException(ERR_GRAMMAR_INVALID_AST_NODE_NAME).loc(rule.getLocation())
                            .param(ARG_AST_NODE_NAME, rule.getReturnAstNodeName())
                            .param(ARG_RULE_NAME, rule.getRuleName());
            }
        }
    }

    private boolean isDefaultAstNode(String astNodeName) {
        return "ast".equalsIgnoreCase(astNodeName);
    }

    /**
     * 设置语法解析规则的returnType属性
     *
     * @param astPackage AST语法树节点所在的java包
     * @param fn         根据astNodeName和propName得到属性类型
     */
    public void initRuleReturnType(String astPackage, String baseAstNode, BiFunction<String, String, String> fn) {
        for (AstRule rule : rules.values()) {
            if (rule.getAstNodeName() == null)
                continue;

            if (isDefaultAstNode(rule.getAstNodeName())) {
                rule.setReturnType(astPackage + "." + baseAstNode);
                rule.setAstNodeName(baseAstNode);
            }
        }

        for (AstRule rule : rules.values()) {
            if (rule.notInAst())
                continue;

            if (rule.isReturnList()) {
                rule.setReturnType("java.util.List<" + astPackage + "." + rule.getElementAstNodeName() + ">");
            } else if (rule.getReturnAstNodeName() != null) {
                rule.setReturnType(astPackage + "." + rule.getReturnAstNodeName());
            }

            try {
                if (rule.getAstProp() != null) {
                    String returnType = fn.apply(rule.getAstNodeName(), rule.getAstProp());
                    if (returnType == null)
                        throw new NopException(ERR_GRAMMAR_INVALID_AST_PROP).loc(rule.getLocation())
                                .param(ARG_AST_NODE_NAME, rule.getAstNodeName()).param(ARG_PROP_NAME, rule.getAstProp());
                    rule.setAstPropType(returnType);
                } else if (rule.hasProperty() && rule.getAstNodeName() != null) {
                    for (GrammarElement prop : rule.getProperties().values()) {
                        String returnType = fn.apply(rule.getAstNodeName(), prop.getPropName());
                        if (returnType == null)
                            throw new NopException(ERR_GRAMMAR_INVALID_AST_PROP).loc(rule.getLocation())
                                    .param(ARG_AST_NODE_NAME, rule.getAstNodeName())
                                    .param(ARG_RULE_NAME, rule.getRuleName()).param(ARG_PROP_NAME, prop.getPropName());
                        prop.setReturnType(returnType);
                    }
                }
            } catch (NopException e) {
                e.addXplStack(rule);
                throw e;
            }
        }
    }
}
