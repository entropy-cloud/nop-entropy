/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.antlr4.tool;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface AntlrToolErrors {
    String ARG_RULE_NAME = "ruleName";
    String ARG_PROP_NAME = "propName";
    String ARG_PATH = "path";
    String ARG_RULE_TYPE = "ruleType";
    String ARG_AST_NODE_NAME = "astNodeName";
    String ARG_AST_ASSIGN = "astAssign";
    String ARG_ELEMENT_RULE = "elementRule";
    String ARG_CHILD_RULE_NAME = "childRuleName";

    ErrorCode ERR_GRAMMAR_INVALID_AST_NODE_NAME = define("nop.err.antlr4.grammar.invalid-ast-node-name",
            "未知的AST语法树节点: {astNodeName}", ARG_AST_NODE_NAME);

    ErrorCode ERR_GRAMMAR_INVALID_AST_PROP = define("nop.err.antlr4.grammar.invalid-ast-prop",
            "AST语法树节点[{astNodeName}]的属性[{propName}]未定义", ARG_AST_NODE_NAME, ARG_PROP_NAME);

    ErrorCode ERR_GRAMMAR_INVALID_LIST_ELEMENT = define("nop.err.antlr4.grammar.invalid-list-element",
            "列表元素只能是简单的RuleRef定义", ARG_RULE_NAME);

    ErrorCode ERR_GRAMMAR_LIST_ELEMENT_NOT_AST_NODE = define("nop.err.antlr4.grammar.list-element-not-ast-node",
            "列表规则[{ruleName}]只支持解析得到AST节点列表，规则名[{elementRule}]不能以_结尾", ARG_RULE_NAME, ARG_ELEMENT_RULE);

    ErrorCode ERR_GRAMMAR_LIST_RULE_NOT_ALLOW_PROP = define("nop.err.antlr4.grammar.list-rule-not-allow-prop",
            "规则[{ruleName}]不支持解析属性[{propName}]，作为列表规则，它只支持通过属性e来标记列表元素", ARG_RULE_NAME, ARG_PROP_NAME);

    ErrorCode ERR_GRAMMAR_INVALID_LIST_RULE_NAME = define("nop.err.antlr4.grammar.invalid-list-rule-name",
            "列表规则[{ruleName}]的规则名应该以_为后缀，所有不以_为后缀的规则都需要返回AST节点", ARG_RULE_NAME, ARG_PROP_NAME);

    ErrorCode ERR_GRAMMAR_DUPLICATE_PROP_LABEL = define("nop.err.antlr4.grammar.duplicate-prop-label",
            "属性名不唯一，不同的部分必须指定不同的altLabel", ARG_RULE_NAME);

    ErrorCode ERR_GRAMMAR_NOT_ALLOW_MULTIPLE_ALT = define("nop.err.antlr4.grammar.not-allow-multiple-alt",
            "规则内部不允许多个语法分支", ARG_RULE_NAME);

    ErrorCode ERR_GRAMMAR_UNKNOWN_RULE_REF = define("nop.err.antlr4.grammar.unknown-rule-ref", "未知的规则名:{ruleName}",
            ARG_RULE_NAME);

    ErrorCode ERR_GRAMMAR_RESOURCE_PATH_NOT_FILE = define("nop.err.antlr4.grammar.resource-path-not-file",
            "资源路径[{path}]必须对应于本地文件路径");

    ErrorCode ERR_GRAMMAR_RULE_WITH_AST_PROP_MUST_BE_SINGLE_TERMINAL = define(
            "nop.err.antlr4.grammar.rule-with-ast-prop-must-be-single-terminal",
            "指定了astProp属性的解析规则[{ruleName}]必须对应于单一终结符号");

    ErrorCode ERR_GRAMMAR_RULE_WITH_AST_PROP_MUST_BE_AST_NODE = define(
            "nop.err.antlr4.grammar.rule-with-ast-prop-must-be-ast-node",
            "指定了astProp属性的解析规则[{ruleName}]必须对应于抽象语法树节点，ruleName不能以_为后缀");

    ErrorCode ERR_GRAMMAR_UNSUPPORTED_RULE = define("nop.err.antlr4.grammar.unsupported-rule",
            "[{ruleName}]对应于不支持的规则类型");

    ErrorCode ERR_GRAMMAR_RULE_ALTERNATIVE_IS_NOT_AST_NODE = define(
            "nop.err.antlr4.grammar.rule-alternative-is-not-ast-node", "[{ruleName}]的所有子规则都必须解析得到AST节点", ARG_RULE_NAME);

    ErrorCode ERR_ANTLR_INVALID_GRAMMAR = define("nop.err.antlr4.invalid-grammar", "[{path}]语法定义文件中存在语法错误");

    ErrorCode ERR_GRAMMAR_INVALID_AST_ASSIGN_OPTION = define("nop.err.antlr4.grammar.invalid-ast-assign-option",
            "astAssign的格式必须为[属性名]或者[属性名]:[属性值]形式,astAssign={astAssign}", ARG_AST_ASSIGN);

}
