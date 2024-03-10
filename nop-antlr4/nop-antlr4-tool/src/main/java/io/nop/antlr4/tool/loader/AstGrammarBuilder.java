/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.antlr4.tool.loader;

import io.nop.antlr4.tool.AntlrToolConstants;
import io.nop.antlr4.tool.model.AstGrammar;
import io.nop.antlr4.tool.model.AstRule;
import io.nop.antlr4.tool.model.GrammarElement;
import io.nop.antlr4.tool.model.OptionalBlock;
import io.nop.antlr4.tool.model.OrBlock;
import io.nop.antlr4.tool.model.PlusBlock;
import io.nop.antlr4.tool.model.RuleRef;
import io.nop.antlr4.tool.model.SeqBlock;
import io.nop.antlr4.tool.model.SetBlock;
import io.nop.antlr4.tool.model.StarBlock;
import io.nop.antlr4.tool.model.TerminalNode;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.StringHelper;
import org.antlr.runtime.CharStream;
import org.antlr.v4.parse.ANTLRParser;
import org.antlr.v4.tool.Grammar;
import org.antlr.v4.tool.LeftRecursiveRule;
import org.antlr.v4.tool.Rule;
import org.antlr.v4.tool.ast.AltAST;
import org.antlr.v4.tool.ast.GrammarAST;
import org.antlr.v4.tool.ast.RuleAST;
import org.antlr.v4.tool.ast.RuleRefAST;
import org.antlr.v4.tool.ast.TerminalAST;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static io.nop.antlr4.tool.AntlrToolErrors.ARG_AST_ASSIGN;
import static io.nop.antlr4.tool.AntlrToolErrors.ARG_CHILD_RULE_NAME;
import static io.nop.antlr4.tool.AntlrToolErrors.ARG_ELEMENT_RULE;
import static io.nop.antlr4.tool.AntlrToolErrors.ARG_PROP_NAME;
import static io.nop.antlr4.tool.AntlrToolErrors.ARG_RULE_NAME;
import static io.nop.antlr4.tool.AntlrToolErrors.ERR_GRAMMAR_DUPLICATE_PROP_LABEL;
import static io.nop.antlr4.tool.AntlrToolErrors.ERR_GRAMMAR_INVALID_AST_ASSIGN_OPTION;
import static io.nop.antlr4.tool.AntlrToolErrors.ERR_GRAMMAR_INVALID_LIST_ELEMENT;
import static io.nop.antlr4.tool.AntlrToolErrors.ERR_GRAMMAR_INVALID_LIST_RULE_NAME;
import static io.nop.antlr4.tool.AntlrToolErrors.ERR_GRAMMAR_LIST_ELEMENT_NOT_AST_NODE;
import static io.nop.antlr4.tool.AntlrToolErrors.ERR_GRAMMAR_LIST_RULE_NOT_ALLOW_PROP;
import static io.nop.antlr4.tool.AntlrToolErrors.ERR_GRAMMAR_RULE_ALTERNATIVE_IS_NOT_AST_NODE;
import static io.nop.antlr4.tool.AntlrToolErrors.ERR_GRAMMAR_RULE_WITH_AST_PROP_MUST_BE_AST_NODE;
import static io.nop.antlr4.tool.AntlrToolErrors.ERR_GRAMMAR_RULE_WITH_AST_PROP_MUST_BE_SINGLE_TERMINAL;
import static io.nop.antlr4.tool.AntlrToolErrors.ERR_GRAMMAR_UNKNOWN_RULE_REF;
import static io.nop.antlr4.tool.AntlrToolErrors.ERR_GRAMMAR_UNSUPPORTED_RULE;

/**
 * 分析g4语法定义，转换为AstGrammar模型结构。根据ParseTree自动构造AST语法树时会使用AstGrammar所提供的信息。
 */
public class AstGrammarBuilder {
    //private Map<String, AstRule> rules = new HashMap<>();
    private Grammar grammar;

    public AstGrammar buildFromAntlrGrammar(Grammar grammar) {
        this.grammar = grammar;

        AstGrammar model = new AstGrammar();
        model.setName(grammar.name);

        String imports = grammar.getOptionString(AntlrToolConstants.PARSER_OPTION_AST_IMPORTS);
        model.setAstImports(StringHelper.stripedSplit(imports, ','));

        for (Rule rule : grammar.indexToRule) {
            // 如果是经过改写的左递归规则，则需要查找到原始规则
            RuleAST ast = rule instanceof LeftRecursiveRule ? ((LeftRecursiveRule) rule).originalAST : rule.ast;

            // System.out.println(toNode(ast).getDumpString("node"));
            processRuleAST(ast, model);
        }

        analyzeGrammar(model);
        return model;
    }

    private void analyzeGrammar(AstGrammar grammar) {
        for (AstRule rule : grammar.getRules()) {
            analyzeRule(grammar, rule);
        }
    }

    private void analyzeRule(AstGrammar grammar, GrammarElement rule) {
        if (rule instanceof RuleRef) {
            resolveRule(grammar, (RuleRef) rule);
        }
        for (GrammarElement child : rule.getChildren()) {
            analyzeRule(grammar, child);
        }
    }

    private void resolveRule(AstGrammar grammar, RuleRef ruleRef) {
        String ruleName = ruleRef.getRuleName();

        AstRule refRule = grammar.getRule(ruleName);
        if (refRule == null)
            throw new NopException(ERR_GRAMMAR_UNKNOWN_RULE_REF).loc(ruleRef.getLocation()).param(ARG_RULE_NAME,
                    ruleName);
        ruleRef.setResolvedRule(refRule);
    }

    private List<GrammarAST> getAltChildren(RuleAST ast) {
        GrammarAST blk = (GrammarAST) ast.getFirstChildWithType(ANTLRParser.BLOCK);
        if (blk == null)
            return Collections.emptyList();

        List<GrammarAST> children = blk.getAllChildrenWithType(ANTLRParser.ALT);
        return children;
    }

    private String getOption(RuleAST ast, String optionName) {
        GrammarAST options = (GrammarAST) ast.getFirstChildWithType(ANTLRParser.OPTIONS);
        if (options == null)
            return null;

        return getOptionFromChildren(options, optionName);
    }

    private String getOptionFromChildren(GrammarAST options, String optionName) {
        for (GrammarAST child : getChildren(options)) {
            if (child.getType() == ANTLRParser.ASSIGN) {
                String name = child.getChild(0).getText();
                if (name.equals(optionName)) {
                    String value = child.getChild(1).getText();
                    return value;
                }
            }
        }
        return null;
    }

    private boolean isOptionEnabled(GrammarAST options, String optionName) {
        for (GrammarAST child : getChildren(options)) {
            if (child.getType() == ANTLRParser.ID) {
                String name = child.getText();
                if (name.equals(optionName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void processRuleAST(RuleAST ast, AstGrammar model) {
        List<GrammarAST> children = getAltChildren(ast);

        String ruleName = ast.getRuleName();

        String astProp = getOption(ast, AntlrToolConstants.RULE_OPTION_AST_PROP);
        if (astProp != null) {
            // 多个终端节点都对应于同一种抽象语法树节点
            if (!isAllSingleTerminal(children))
                throw newError(ERR_GRAMMAR_RULE_WITH_AST_PROP_MUST_BE_SINGLE_TERMINAL, ast).param(ARG_RULE_NAME,
                        ruleName);
            if (ast.getRuleName().endsWith("_"))
                throw newError(ERR_GRAMMAR_RULE_WITH_AST_PROP_MUST_BE_AST_NODE, ast).param(ARG_RULE_NAME, ruleName);

            AstRule rule = new AstRule();
            rule.setAstProp(astProp);
            rule.setLocation(getLocation(ast));
            rule.setRuleName(ruleName);
            rule.setAstNodeName(getAstNodeName(ruleName));
            rule.setSingleTerminal(true);
            model.addRule(rule);
            return;
        }

        // 不属于抽象语法树的语法成分
        if (ruleName.endsWith("__")) {
            AstRule rule = new AstRule();
            rule.setLocation(getLocation(ast));
            rule.setRuleName(ruleName);
            model.addRule(rule);
            return;
        }

        if (children.size() == 1) {
            AstRule nodeRule = buildAstNode(ast, (AltAST) children.get(0));
            model.addRule(nodeRule);
            if (getAltLabel((AltAST) children.get(0)) != null) {
                AstRule groupRule = new AstRule();
                groupRule.setLocation(getLocation(ast));
                groupRule.setGroup(true);
                groupRule.setChildHasAltLabel(true);
                groupRule.setChildren(Arrays.asList(nodeRule));
                groupRule.setRuleName(ast.getRuleName());
                if (!ast.getRuleName().endsWith("_")) {
                    groupRule.setAstNodeName(getAstNodeName(ast.getRuleName()));
                }
                if (groupRule.getAstNodeName() != null) {
                    if (nodeRule.getAstNodeName() != null)
                        throw newError(ERR_GRAMMAR_RULE_ALTERNATIVE_IS_NOT_AST_NODE, ast).param(ARG_RULE_NAME,
                                ruleName);
                }
                model.addRule(groupRule);
            }
        } else {
            List<GrammarElement> list = new ArrayList<>();

            boolean allAstNode = true;
            boolean childHasAltLabel = false;
            String subRuleName = null;

            // 每一个分支都具有altLabel
            for (GrammarAST child : children) {

                AltAST altAst = (AltAST) child;

                if (!isAstNode(altAst)) {
                    allAstNode = false;
                    subRuleName = altAst.getText();
                }

                if (getAltLabel(altAst) == null)
                    continue;

                childHasAltLabel = true;

                AstRule nodeRule = buildAstNode(ast, altAst);
                model.addRule(nodeRule);

                list.add(nodeRule);
            }

            AstRule groupRule = new AstRule();
            groupRule.setLocation(getLocation(ast));
            groupRule.setGroup(true);
            groupRule.setChildHasAltLabel(childHasAltLabel);
            groupRule.setChildren(list);
            groupRule.setRuleName(ast.getRuleName());
            if (!ast.getRuleName().endsWith("_")) {
                groupRule.setAstNodeName(getAstNodeName(ast.getRuleName()));
            }

            // 如果规则返回AST节点，则它的每个子规则必须都返回AST节点
            if (groupRule.getAstNodeName() != null) {
                if (!allAstNode)
                    throw newError(ERR_GRAMMAR_RULE_ALTERNATIVE_IS_NOT_AST_NODE, ast).param(ARG_RULE_NAME, ruleName)
                            .param(ARG_CHILD_RULE_NAME, subRuleName);
            }
            model.addRule(groupRule);
        }
    }

    private String getAstNodeName(String name) {
        String firstPart = StringHelper.firstPart(name, '_');
        return StringHelper.capitalize(firstPart);
    }

    private boolean isAstNode(AltAST ast) {
        String label = getAltLabel(ast);
        if (label != null && !label.endsWith("_"))
            return true;

        RuleRefAST ref = getUniqueRef(getChildren(ast));
        if (ref == null)
            return false;

        return !ref.getText().endsWith("_");
    }

    private RuleRefAST getUniqueRef(List<GrammarAST> children) {
        if (children == null)
            return null;

        RuleRefAST ref = null;
        for (GrammarAST child : children) {
            switch (child.getType()) {
                case ANTLRParser.ACTION:
                case ANTLRParser.SEMPRED:
                    continue;
                case ANTLRParser.RULE_REF:
                    if (ref != null)
                        return null;
                    ref = (RuleRefAST) child;
                    break;
                default:
                    return null;
            }
        }
        return ref;
    }

    private List<GrammarAST> getChildren(GrammarAST ast) {
        return (List<GrammarAST>) ast.getChildren();
    }

    private AstRule buildAstNode(RuleAST ruleAst, AltAST altAst) {
        String ruleName = ruleAst.getRuleName();
        String altLabel = getAltLabel(altAst);

        AstRule rule = new AstRule();
        rule.setLocation(getLocation(ruleAst));

        rule.setRuleName(ruleName);
        rule.setAltLabel(altLabel);

        String nodeName = ruleName;
        if (altLabel != null)
            nodeName = altLabel;
        if (nodeName != null && !nodeName.endsWith("_")) {
            rule.setAstNodeName(getAstNodeName(nodeName));
        }

        List<GrammarAST> children = getChildren(altAst);
        rule.setChildren(buildChildren(children, rule));

        if (rule.getElement() != null) {
            rule.setElementAstNodeName(getOption(ruleAst, AntlrToolConstants.RULE_OPTION_ELEMENT_AST_NODE_NAME));
        } else if (!rule.hasProperty() && getAstRuleCount(rule.getChildren()) == 1) {
            rule.setInnerRule(getInnerRule(rule.getChildren()));
        }
//        else if (rule.hasProperty()) {
//
//        }
        return rule;
    }

    private int getAstRuleCount(List<GrammarElement> children) {
        int count = 0;
        for (GrammarElement child : children) {
            if (child instanceof OptionalBlock) {
                count += getAstRuleCount(child.getChildren());
            }
            if (child instanceof RuleRef) {
                if (!((RuleRef) child).getRuleName().endsWith("_"))
                    count++;
            }
        }
        return count;
    }

    private RuleRef getInnerRule(List<GrammarElement> children) {
        for (GrammarElement child : children) {
            if (child instanceof OptionalBlock) {
                RuleRef innerRule = getInnerRule(child.getChildren());
                if (innerRule != null)
                    return innerRule;
            }
            if (child instanceof RuleRef) {
                if (((RuleRef) child).notInAst())
                    continue;
                return (RuleRef) child;
            }
        }
        return null;
    }

    private boolean isAllSingleTerminal(List<GrammarAST> list) {
        for (GrammarAST ast : list) {
            if (!isSingleTerminal(ast))
                return false;
        }
        return true;
    }

    /**
     * 是否对应于单一终结符号
     */
    private boolean isSingleTerminal(GrammarAST ast) {
        if (ast instanceof TerminalAST)
            return true;

        if (ast.getType() == ANTLRParser.SET)
            return true;

        if (ast instanceof RuleRefAST) {
            String ruleName = ast.getText();
            Rule rule = grammar.getRule(ruleName);
            if (rule == null)
                return false;

            List<GrammarAST> children = getAltChildren(rule.ast);
            return isAllSingleTerminal(children);
        }

        if (ast instanceof AltAST) {
            List<GrammarAST> children = getChildren(ast);
            if (children.size() != 1)
                return false;
            return isSingleTerminal(children.get(0));
        }
        return false;
    }

    private List<GrammarElement> buildChildren(List<GrammarAST> children, AstRule rule) {
        if (children == null || children.isEmpty())
            return Collections.emptyList();

        List<GrammarElement> nodes = new ArrayList<>();
        for (GrammarAST child : children) {
            GrammarElement node = processAstChild(child, rule);
            if (node != null)
                nodes.add(node);
        }
        return nodes;
    }

    private List<GrammarElement> buildChildren(GrammarAST ast, AstRule rule) {
        return buildChildren(getChildren(ast), rule);
    }

    private NopException newError(ErrorCode errorCode, GrammarAST ast) {
        return new NopException(errorCode).loc(getLocation(ast));
    }

    private GrammarElement processAstChild(GrammarAST child, AstRule rule) {
        int br = 0;
        boolean indent = false;

        if (child.childIndex > 0) {
            // 判断前一个option节点是否包含了indent和br指令，它表示在输出本节点之前需要先输出缩进字符。
            GrammarAST prev = (GrammarAST) child.getParent().getChild(child.childIndex - 1);
            if (prev.getType() == ANTLRParser.ELEMENT_OPTIONS) {
                String astAssign = getOptionFromChildren(prev, AntlrToolConstants.ELEMENT_OPTION_AST_ASSIGN);
                rule.setAstAssigns(parseAstAssign(astAssign, child));
                indent = isOptionEnabled(prev, AntlrToolConstants.ELEMENT_OPTION_INDENT);
                if (isOptionEnabled(prev, AntlrToolConstants.ELEMENT_OPTION_BR)) {
                    br = 1;
                } else {
                    br = getBr(getOptionFromChildren(prev, AntlrToolConstants.ELEMENT_OPTION_BR));
                }
            }
        }

        // if (child.childIndex > 1) {
        // // 判断前一个action节点是否包含了indent和br指令，它表示在输出本节点之前需要先输出缩进字符。
        // GrammarAST prev = (GrammarAST) child.getParent().getChild(child.childIndex - 1);
        // if (prev.getType() == ANTLRParser.ACTION) {
        // String text = prev.getText();
        // indent = text.indexOf("indent()") >= 0;
        // br = getBr(text);
        // }
        // }

        String altLabel = null;
        String propName = null;
        switch (child.getType()) {
            case ANTLRParser.ACTION:
            case ANTLRParser.ELEMENT_OPTIONS:
                return null;
            case ANTLRParser.ASSIGN: {
                altLabel = child.getChild(0).getText();
                propName = StringHelper.firstPart(altLabel, '_');
                child = (GrammarAST) child.getChild(1);
                break;
            }
            // case ANTLRParser.RULE_REF: {
            // propName = child.getText();
            // break;
            // }
        }

        // 列表元素
        if (AntlrToolConstants.GRAMMAR_ELEMENT_LABEL.equals(propName)) {
            // 列表元素为RuleRef
            if (child.getType() != ANTLRParser.RULE_REF) {
                throw newError(ERR_GRAMMAR_INVALID_LIST_ELEMENT, child).param(ARG_RULE_NAME,
                        rule.getAltLabelOrRuleName());
            }

            if (rule.hasProperty()) {
                throw newError(ERR_GRAMMAR_LIST_RULE_NOT_ALLOW_PROP, child)
                        .param(ARG_RULE_NAME, rule.getAltLabelOrRuleName())
                        .param(ARG_PROP_NAME, CollectionHelper.first(rule.getProperties().keySet()));
            }

            if (rule.getAstNodeName() != null) {
                throw new NopException(ERR_GRAMMAR_INVALID_LIST_RULE_NAME)
                        .param(ARG_RULE_NAME, rule.getAltLabelOrRuleName()).loc(rule.getLocation());
            }

            RuleRefAST ruleRefAST = (RuleRefAST) child;
            RuleRef ref = buildRuleRef(ruleRefAST);
            ref.setBr(br);
            ref.setIndent(indent);
            if (ref.getRuleName().endsWith("_"))
                throw newError(ERR_GRAMMAR_LIST_ELEMENT_NOT_AST_NODE, child)
                        .param(ARG_RULE_NAME, rule.getAltLabelOrRuleName()).param(ARG_ELEMENT_RULE, ref.getRuleName());

            // e标签指向的多个元素必须都是同样的RuleRef规则
            // 例如 columnNames: e=sqlColumnName (COMMA_ e=sqlColumnName)*;
            if (rule.getElement() != null) {
                if (!ruleRefAST.getText().equals(rule.getElement().getRuleName())) {
                    throw newError(ERR_GRAMMAR_INVALID_LIST_ELEMENT, child).param(ARG_RULE_NAME,
                            rule.getAltLabelOrRuleName());
                }
            } else {
                rule.setElement(ref);
            }
            return ref;
        }

        GrammarElement node = buildChildNode(child, rule);
        if (node != null) {
            node.setLocation(getLocation(child));
            node.setIndent(indent);
            node.setBr(br);

            if (propName != null) {
                node.setPropName(propName);
                node.setPropLabel(altLabel);
                node.setSingle(altLabel.endsWith("_single"));
                if (rule.getElement() != null) {
                    throw new NopException(ERR_GRAMMAR_LIST_RULE_NOT_ALLOW_PROP).loc(rule.getLocation())
                            .param(ARG_RULE_NAME, rule.getAltLabelOrRuleName()).param(ARG_PROP_NAME, propName);
                }

                if (rule.getProperty(propName) != null) {
                    throw newError(ERR_GRAMMAR_DUPLICATE_PROP_LABEL, child).param(ARG_PROP_NAME, propName)
                            .param(ARG_RULE_NAME, rule.getAltLabelOrRuleName());
                }
                rule.addProperty(propName, node);
            }
        }

        return node;
    }

    private Map<String, String> parseAstAssign(String astAssign, GrammarAST ast) {
        if (astAssign == null || astAssign.isEmpty())
            return Collections.emptyMap();

        if (astAssign.startsWith("'") || astAssign.startsWith("\""))
            astAssign = astAssign.substring(1);
        if (astAssign.endsWith("'") || astAssign.endsWith("\""))
            astAssign = astAssign.substring(0, astAssign.length() - 1);

        Map<String, String> ret = new TreeMap<>();
        List<String> list = StringHelper.stripedSplit(astAssign, ',');
        for (String item : list) {
            int pos = item.indexOf(':');
            if (pos < 0) {
                if (!StringHelper.isValidSimpleVarName(item))
                    throw newError(ERR_GRAMMAR_INVALID_AST_ASSIGN_OPTION, ast).param(ARG_AST_ASSIGN, astAssign);

                ret.put(item, "true");
            } else {
                String key = item.substring(0, pos).trim();
                String value = item.substring(pos + 1).trim();

                if (!StringHelper.isValidSimpleVarName(key))
                    throw newError(ERR_GRAMMAR_INVALID_AST_ASSIGN_OPTION, ast).param(ARG_AST_ASSIGN, astAssign);
                ret.put(key, value);
            }
        }
        return ret;
    }

    private int getBr(String text) {
        if (StringHelper.isEmpty(text))
            return 0;
        return ConvertHelper.toInt(text);
    }

    private GrammarElement buildChildNode(GrammarAST child, AstRule rule) {
        if (child instanceof TerminalAST) {
            TerminalNode node = new TerminalNode();
            TerminalAST ast = (TerminalAST) child;
            int tokenType = getTokenType(ast);
            String name = grammar.getTokenName(tokenType);
            if (!Grammar.INVALID_TOKEN_NAME.equals(name)) {
                String text = grammar.getTokenDisplayName(tokenType);
                node.setName(name);
                node.setText(text);
            } else {
                node.setText(ast.getText());
            }
            return node;
        }
        switch (child.getType()) {
            case ANTLRParser.RULE_REF: {
                RuleRef node = new RuleRef();
                node.setRuleName(child.getText());
                return node;
            }
            case ANTLRParser.SET: {
                SetBlock node = new SetBlock();
                node.setChildren(buildChildren(child, rule));
                return node;
            }
            case ANTLRParser.BLOCK: {
                return buildOrBlock(getChildren(child), rule);
            }
            case ANTLRParser.ALT: {
                return buildSeqBlock(child, rule);
            }
            case ANTLRParser.CLOSURE: {
                StarBlock node = new StarBlock();
                node.setChildren(buildChildren(child, rule));
                return node;
            }
            case ANTLRParser.OPTIONAL: {
                OptionalBlock node = new OptionalBlock();
                node.setChildren(buildChildren(child, rule));
                return node;
            }
            case ANTLRParser.POSITIVE_CLOSURE: {
                PlusBlock node = new PlusBlock();
                node.setChildren(buildChildren(child, rule));
                return node;
            }
            case ANTLRParser.ACTION:
            case ANTLRParser.OPTIONS:
            case ANTLRParser.SEMPRED:
                return null;
            default:
                throw new NopException(ERR_GRAMMAR_UNSUPPORTED_RULE).loc(getLocation(child)).param(ARG_RULE_NAME,
                        child.getText());
        }
    }

    private int getTokenType(TerminalAST ast) {
        return grammar.getTokenType(ast.getText());
    }

    private GrammarElement buildSeqBlock(GrammarAST child, AstRule rule) {
        List<GrammarElement> children = buildChildren(child, rule);
        if (children.isEmpty())
            return null;
        if (children.size() == 1)
            return children.get(0);
        SeqBlock node = new SeqBlock();
        node.setChildren(children);
        return node;
    }

    private GrammarElement buildOrBlock(List<GrammarAST> childAst, AstRule rule) {
        List<GrammarElement> children = buildChildren(childAst, rule);
        if (children.isEmpty())
            return null;
        if (children.size() == 1)
            return children.get(0);
        OrBlock node = new OrBlock();
        node.setChildren(children);
        return node;
    }

    private RuleRef buildRuleRef(RuleRefAST ast) {
        RuleRef node = new RuleRef();
        node.setLocation(getLocation(ast));
        node.setRuleName(ast.getText());
        return node;
    }

    private SourceLocation getLocation(GrammarAST ast) {
        int line = ast.token.getLine();
        int col = ast.token.getCharPositionInLine();

        String sourceName = getSourceName(ast);
        if (StringHelper.isEmpty(sourceName))
            sourceName = GrammarAST.class.getName();

        return SourceLocation.fromLine(sourceName, line, col);
    }

    private String getSourceName(GrammarAST ast) {
        CharStream s = ast.token.getInputStream();
        return s == null ? null : s.getSourceName();
    }

    private String getAltLabel(AltAST ast) {
        if (ast.altLabel == null)
            return null;
        return ast.altLabel.getText();
    }
}
