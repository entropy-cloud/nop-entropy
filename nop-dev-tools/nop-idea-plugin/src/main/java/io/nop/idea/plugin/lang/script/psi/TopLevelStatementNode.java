/*
 * Copyright (c) 2017-2025 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.idea.plugin.lang.script.psi;

import java.util.Map;

import com.intellij.lang.ASTNode;
import io.nop.idea.plugin.lang.XLangVarDecl;
import org.jetbrains.annotations.NotNull;

import static io.nop.idea.plugin.lang.script.XLangScriptTokenTypes.RULE_moduleDeclaration_import;

/**
 * 各种语句的根节点
 * <p/>
 * 赋值、函数、导入、try 等语句，各自均有唯一的根节点：
 * <pre>
 * TopLevelStatementNode(ast_topLevelStatement)
 *   RuleSpecNode(moduleDeclaration_import)
 *     ImportDeclarationNode(importAsDeclaration)
 * </pre>
 * <pre>
 * TopLevelStatementNode(ast_topLevelStatement)
 *   StatementNode(statement)
 *     VariableDeclarationNode(variableDeclaration)
 * </pre>
 * <pre>
 * TopLevelStatementNode(ast_topLevelStatement)
 *   StatementNode(statement)
 *     FunctionDeclarationNode(functionDeclaration)
 * </pre>
 * <pre>
 * TopLevelStatementNode(ast_topLevelStatement)
 *   StatementNode(statement)
 *     RuleSpecNode(ifStatement)
 * </pre>
 * <pre>
 * TopLevelStatementNode(ast_topLevelStatement)
 *   StatementNode(statement)
 *     RuleSpecNode(expressionStatement)
 * </pre>
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-30
 */
public class TopLevelStatementNode extends RuleSpecNode {

    public TopLevelStatementNode(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    public @NotNull Map<String, XLangVarDecl> getVars() {
        RuleSpecNode firstChild = (RuleSpecNode) getFirstChild();

        if (firstChild instanceof StatementNode s) {
            return s.getVars();
        } //
        else if (firstChild.isRuleType(RULE_moduleDeclaration_import)) {
            return ((ImportDeclarationNode) firstChild.getFirstChild()).getVars();
        }
        return Map.of();
    }
}
