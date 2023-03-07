/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.compile;

import io.nop.commons.util.StringHelper;
import io.nop.xlang.ast.ArrowFunctionExpression;
import io.nop.xlang.ast.BlockStatement;
import io.nop.xlang.ast.FunctionDeclaration;
import io.nop.xlang.ast.Identifier;
import io.nop.xlang.ast.Program;
import io.nop.xlang.ast.XLangASTVisitor;
import io.nop.xlang.ast.definition.ClosureRefDefinition;
import io.nop.xlang.scope.LexicalScope;

import java.util.List;

public class PrintResolvedIdentifier extends XLangASTVisitor {
    private final StringBuilder buf;
    private int indent;

    public PrintResolvedIdentifier(StringBuilder buf) {
        this.buf = buf;
    }

    public PrintResolvedIdentifier() {
        this(new StringBuilder());
    }

    public String getOutput() {
        return buf.toString();
    }

    @Override
    public void visitProgram(Program node) {
        enterBlock();
        printClosure(node.getLexicalScope());
        super.visitProgram(node);
        leaveBlock();
    }

    void enterBlock() {
        printIndent();
        buf.append("{\n");
        indent++;
    }

    void leaveBlock() {
        indent--;
        printIndent();
        buf.append("}\n");
    }

    void printIndent() {
        if (indent > 0)
            buf.append(StringHelper.repeat("  ", indent));
    }

    void print(Identifier id) {
        printIndent();
        buf.append(id.getName()).append(" => ");
        if (id.getVarDeclaration() != null) {
            buf.append(id.getVarDeclaration()).append("\n");
        } else {
            buf.append(id.getResolvedDefinition()).append("\n");
        }
    }

    @Override
    public void visitIdentifier(Identifier node) {
        print(node);
    }

    @Override
    public void visitBlockStatement(BlockStatement node) {
        enterBlock();
        super.visitBlockStatement(node);
        leaveBlock();
    }

    @Override
    public void visitFunctionDeclaration(FunctionDeclaration node) {
        printIndent();
        buf.append("function ").append(node.getFuncName()).append("(){\n");
        indent++;

        printClosure(node.getLexicalScope());
        super.visitFunctionDeclaration(node);
        leaveBlock();
    }

    public void visitArrowFunctionExpression(ArrowFunctionExpression node) {
        printIndent();
        buf.append("lambda {\n");
        indent++;
        printClosure(node.getLexicalScope());
        super.visitArrowFunctionExpression(node);
        leaveBlock();
    }

    void printClosure(LexicalScope scope) {
        if (scope == null)
            return;

        List<ClosureRefDefinition> list = scope.getAllClosureVars();
        if (list != null) {
            for (ClosureRefDefinition ref : list) {
                printIndent();
                buf.append("&").append(ref.getIdentifierName()).append(",slot=").append(ref.getVarSlot()).append("\n");
            }
        }
    }
}
