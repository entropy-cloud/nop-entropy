/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.ast.print;

import io.nop.commons.util.StringHelper;
import io.nop.core.model.query.FilterOp;
import io.nop.xlang.ast.ArrayBinding;
import io.nop.xlang.ast.ArrayElementBinding;
import io.nop.xlang.ast.ArrayExpression;
import io.nop.xlang.ast.ArrayTypeNode;
import io.nop.xlang.ast.ArrowFunctionExpression;
import io.nop.xlang.ast.AssertOpExpression;
import io.nop.xlang.ast.AssignmentExpression;
import io.nop.xlang.ast.AwaitExpression;
import io.nop.xlang.ast.BetweenOpExpression;
import io.nop.xlang.ast.BinaryExpression;
import io.nop.xlang.ast.BlockStatement;
import io.nop.xlang.ast.BraceExpression;
import io.nop.xlang.ast.BreakStatement;
import io.nop.xlang.ast.CallExpression;
import io.nop.xlang.ast.CastExpression;
import io.nop.xlang.ast.CatchClause;
import io.nop.xlang.ast.ChainExpression;
import io.nop.xlang.ast.ClassDefinition;
import io.nop.xlang.ast.CollectOutputExpression;
import io.nop.xlang.ast.CompareOpExpression;
import io.nop.xlang.ast.ConcatExpression;
import io.nop.xlang.ast.ContinueStatement;
import io.nop.xlang.ast.CustomExpression;
import io.nop.xlang.ast.Decorator;
import io.nop.xlang.ast.Decorators;
import io.nop.xlang.ast.DeleteStatement;
import io.nop.xlang.ast.DoWhileStatement;
import io.nop.xlang.ast.EnumDeclaration;
import io.nop.xlang.ast.EnumMember;
import io.nop.xlang.ast.EscapeOutputExpression;
import io.nop.xlang.ast.EvalExpression;
import io.nop.xlang.ast.ExportAllDeclaration;
import io.nop.xlang.ast.ExportDeclaration;
import io.nop.xlang.ast.ExportNamedDeclaration;
import io.nop.xlang.ast.ExportSpecifier;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.ast.ExpressionStatement;
import io.nop.xlang.ast.FieldDeclaration;
import io.nop.xlang.ast.ForInStatement;
import io.nop.xlang.ast.ForOfStatement;
import io.nop.xlang.ast.ForRangeStatement;
import io.nop.xlang.ast.ForStatement;
import io.nop.xlang.ast.FunctionArgTypeDef;
import io.nop.xlang.ast.FunctionDeclaration;
import io.nop.xlang.ast.FunctionTypeDef;
import io.nop.xlang.ast.GenNodeAttrExpression;
import io.nop.xlang.ast.GenNodeExpression;
import io.nop.xlang.ast.Identifier;
import io.nop.xlang.ast.IfStatement;
import io.nop.xlang.ast.ImportAsDeclaration;
import io.nop.xlang.ast.ImportDeclaration;
import io.nop.xlang.ast.ImportDefaultSpecifier;
import io.nop.xlang.ast.ImportNamespaceSpecifier;
import io.nop.xlang.ast.ImportSpecifier;
import io.nop.xlang.ast.InExpression;
import io.nop.xlang.ast.InstanceOfExpression;
import io.nop.xlang.ast.IntersectionTypeDef;
import io.nop.xlang.ast.Literal;
import io.nop.xlang.ast.LogicalExpression;
import io.nop.xlang.ast.MacroExpression;
import io.nop.xlang.ast.MemberExpression;
import io.nop.xlang.ast.MetaArray;
import io.nop.xlang.ast.MetaObject;
import io.nop.xlang.ast.MetaProperty;
import io.nop.xlang.ast.NewExpression;
import io.nop.xlang.ast.ObjectBinding;
import io.nop.xlang.ast.ObjectExpression;
import io.nop.xlang.ast.ObjectTypeDef;
import io.nop.xlang.ast.OutputXmlAttrExpression;
import io.nop.xlang.ast.OutputXmlExtAttrsExpression;
import io.nop.xlang.ast.ParameterDeclaration;
import io.nop.xlang.ast.ParameterizedTypeNode;
import io.nop.xlang.ast.Program;
import io.nop.xlang.ast.PropertyAssignment;
import io.nop.xlang.ast.PropertyBinding;
import io.nop.xlang.ast.PropertyTypeDef;
import io.nop.xlang.ast.QualifiedName;
import io.nop.xlang.ast.RegExpLiteral;
import io.nop.xlang.ast.RestBinding;
import io.nop.xlang.ast.ReturnStatement;
import io.nop.xlang.ast.SequenceExpression;
import io.nop.xlang.ast.SpreadElement;
import io.nop.xlang.ast.SuperExpression;
import io.nop.xlang.ast.SwitchCase;
import io.nop.xlang.ast.SwitchStatement;
import io.nop.xlang.ast.TemplateExpression;
import io.nop.xlang.ast.TemplateStringExpression;
import io.nop.xlang.ast.TemplateStringLiteral;
import io.nop.xlang.ast.TextOutputExpression;
import io.nop.xlang.ast.ThisExpression;
import io.nop.xlang.ast.ThrowStatement;
import io.nop.xlang.ast.TryStatement;
import io.nop.xlang.ast.TupleTypeDef;
import io.nop.xlang.ast.TypeAliasDeclaration;
import io.nop.xlang.ast.TypeNameNode;
import io.nop.xlang.ast.TypeOfExpression;
import io.nop.xlang.ast.TypeParameterNode;
import io.nop.xlang.ast.UnaryExpression;
import io.nop.xlang.ast.UnionTypeDef;
import io.nop.xlang.ast.UpdateExpression;
import io.nop.xlang.ast.UsingStatement;
import io.nop.xlang.ast.VariableDeclaration;
import io.nop.xlang.ast.VariableDeclarator;
import io.nop.xlang.ast.WhileStatement;
import io.nop.xlang.ast.XLangASTNode;
import io.nop.xlang.ast.XLangASTVisitor;
import io.nop.xlang.ast.XLangOperator;

import java.util.List;
import java.util.Objects;

public class XLangExpressionPrinter extends XLangASTVisitor {
    protected final StringBuilder sb;

    private int indentLevel;
    private boolean pretty;

    public XLangExpressionPrinter(StringBuilder sb) {
        this.sb = sb;
    }

    public XLangExpressionPrinter() {
        this(new StringBuilder());
    }

    public String toExprString(Expression expr) {
        visit(expr);
        return getResult();
    }

    public String toSource(XLangASTNode node) {
        visit(node);
        return getResult();
    }

    public String getResult() {
        return sb.toString();
    }

    public boolean isPretty() {
        return pretty;
    }

    public void setPretty(boolean pretty) {
        this.pretty = pretty;
    }

    protected XLangExpressionPrinter print(char c) {
        sb.append(c);
        return this;
    }

    protected XLangExpressionPrinter print(XLangOperator operator) {
        sb.append(operator);
        return this;
    }

    protected XLangExpressionPrinter print(Object text) {
        sb.append(text);
        return this;
    }

    protected XLangExpressionPrinter printList(List<? extends XLangASTNode> node, String separator) {
        if (node == null || node.isEmpty())
            return this;

        for (int i = 0, n = node.size(); i < n; i++) {
            if (i != 0)
                print(separator);
            visit(node.get(i));
        }
        return this;
    }

    XLangExpressionPrinter incIndent() {
        indentLevel++;
        return this;
    }

    XLangExpressionPrinter decIndent() {
        indentLevel--;
        return this;
    }

    void indent() {
        if (pretty) {
            for (int i = 0; i < this.indentLevel; ++i) {
                print("  ");
            }
        }
    }

    XLangExpressionPrinter println() {
        print("\n");
        indent();
        return this;
    }

    @Override
    public void visitIdentifier(Identifier node) {
        print(node.getName());
    }

    @Override
    public void visitLiteral(Literal node) {
        Object value = node.getValue();
        if (value instanceof String) {
            print("\"");
            print(StringHelper.escapeJava(value.toString()));
            print("\"");
        } else {
            print(value);
        }
    }

    @Override
    public void visitTemplateStringLiteral(TemplateStringLiteral node) {
        print(StringHelper.quoteDupEscapeString(node.getStringValue(), '`'));
    }

    @Override
    public void visitRegExpLiteral(RegExpLiteral node) {
        super.visitRegExpLiteral(node);
    }

    @Override
    public void visitBlockStatement(BlockStatement node) {
        super.visitBlockStatement(node);
    }

    @Override
    public void visitReturnStatement(ReturnStatement node) {
        print("return ");
        if (node.getArgument() != null) {
            visit(node.getArgument());
        }
        print('\n');
    }

    @Override
    public void visitBreakStatement(BreakStatement node) {
        print("break;");
        println();
    }

    @Override
    public void visitContinueStatement(ContinueStatement node) {
        print("continue;");
        println();
    }

    @Override
    public void visitIfStatement(IfStatement node) {
        if (node.getTernaryExpr()) {
            visit(node.getTest());
            print('?');
            if (node.getConsequent() == null) {
                print("null");
            } else {
                visit(node.getConsequent());
            }

            print(':');

            if (node.getAlternate() == null) {
                print("null");
            } else {
                visit(node.getAlternate());
            }
            print(' ');
        } else {
            print("if(");
            visit(node.getTest());
            print("){");
            println();
            visit(node.getConsequent());
            print("}\n");
            if (node.getAlternate() != null) {
                print("else{");
                visit(node.getAlternate());
                print("}");
            }
        }
    }

    @Override
    public void visitSwitchStatement(SwitchStatement node) {
        super.visitSwitchStatement(node);
    }

    @Override
    public void visitSwitchCase(SwitchCase node) {
        super.visitSwitchCase(node);
    }

    @Override
    public void visitThrowStatement(ThrowStatement node) {
        super.visitThrowStatement(node);
    }

    @Override
    public void visitTryStatement(TryStatement node) {
        super.visitTryStatement(node);
    }

    @Override
    public void visitCatchClause(CatchClause node) {
        super.visitCatchClause(node);
    }

    @Override
    public void visitWhileStatement(WhileStatement node) {
        super.visitWhileStatement(node);
    }

    @Override
    public void visitDoWhileStatement(DoWhileStatement node) {
        super.visitDoWhileStatement(node);
    }

    @Override
    public void visitVariableDeclarator(VariableDeclarator node) {
        super.visitVariableDeclarator(node);
    }

    @Override
    public void visitVariableDeclaration(VariableDeclaration node) {
        super.visitVariableDeclaration(node);
    }

    @Override
    public void visitForStatement(ForStatement node) {
        super.visitForStatement(node);
    }

    @Override
    public void visitForOfStatement(ForOfStatement node) {
        super.visitForOfStatement(node);
    }

    @Override
    public void visitForRangeStatement(ForRangeStatement node) {
        super.visitForRangeStatement(node);
    }

    @Override
    public void visitForInStatement(ForInStatement node) {
        super.visitForInStatement(node);
    }

    @Override
    public void visitDeleteStatement(DeleteStatement node) {
        super.visitDeleteStatement(node);
    }

    @Override
    public void visitChainExpression(ChainExpression node) {
        super.visitChainExpression(node);
    }

    @Override
    public void visitThisExpression(ThisExpression node) {
        super.visitThisExpression(node);
    }

    @Override
    public void visitSuperExpression(SuperExpression node) {
        super.visitSuperExpression(node);
    }

    @Override
    public void visitTemplateStringExpression(TemplateStringExpression node) {
        super.visitTemplateStringExpression(node);
    }

    @Override
    public void visitArrayExpression(ArrayExpression node) {
        print('[');
        boolean first = true;
        for (XLangASTNode elm : node.getElements()) {
            if (!first) {
                print(',');
            }
            first = false;
            visit(elm);
        }
        print(']');
    }

    @Override
    public void visitObjectExpression(ObjectExpression node) {
        print('{');
        int i = 0;
        for (XLangASTNode prop : node.getProperties()) {
            if (i != 0)
                print(',');
            visit(prop);
            i++;
        }
        print('}');
    }

    @Override
    public void visitPropertyAssignment(PropertyAssignment node) {
        print(node.getKey());
        print(" : ");
        print(node.getValue());
    }

    @Override
    public void visitAssignmentExpression(AssignmentExpression node) {

        this.visitChild(node.getLeft());
        print(node.getOperator());
        this.visitChild(node.getRight());
    }

    @Override
    public void visitUpdateExpression(UpdateExpression node) {

        if (node.getPrefix())
            print(node.getOperator());
        this.visitChild(node.getArgument());

        if (!node.getPrefix())
            print(node.getOperator());
    }

    @Override
    public void visitUnaryExpression(UnaryExpression node) {
        print(node.getOperator());
        visit(node.getArgument());
    }

    @Override
    public void visitParameterDeclaration(ParameterDeclaration node) {
        super.visitParameterDeclaration(node);
    }

    @Override
    public void visitFunctionDeclaration(FunctionDeclaration node) {
        super.visitFunctionDeclaration(node);
    }

    @Override
    public void visitArrowFunctionExpression(ArrowFunctionExpression node) {
        super.visitArrowFunctionExpression(node);
    }

    @Override
    public void visitBinaryExpression(BinaryExpression node) {
        printLeft(node.getLeft(), node.getOperator());

        print(' ');
        print(node.getOperator());
        print(' ');

        printRight(node.getRight(), node.getOperator());
    }

    protected void printLeft(Expression expr, XLangOperator op) {
        if (expr instanceof BinaryExpression || expr instanceof LogicalExpression) {
            print('(');
            visit(expr);
            print(')');
        } else {
            visit(expr);
        }
    }

    protected void printRight(Expression expr, XLangOperator op) {
        if (expr instanceof BinaryExpression || expr instanceof LogicalExpression) {
            print('(');
            visit(expr);
            print(')');
        } else {
            visit(expr);
        }
    }

    @Override
    public void visitInExpression(InExpression node) {
        super.visitInExpression(node);
    }

    @Override
    public void visitExpressionStatement(ExpressionStatement node) {
        super.visitExpressionStatement(node);
    }

    @Override
    public void visitLogicalExpression(LogicalExpression node) {
        printLeft(node.getLeft(), node.getOperator());
        print(' ');
        print(node.getOperator());
        print(' ');
        printRight(node.getRight(), node.getOperator());
    }

    @Override
    public void visitMemberExpression(MemberExpression node) {
        visit(node.getObject());
        if (node.getOptional()) {
            print("?.");
        } else {
            print(".");
        }
        visit(node.getProperty());
    }

    @Override
    public void visitEvalExpression(EvalExpression node) {
        super.visitEvalExpression(node);
    }

    @Override
    public void visitCallExpression(CallExpression node) {
        visit(node.getCallee());
        if (node.getOptional())
            print("?.");
        print('(');
        for (int i = 0, n = node.getArguments().size(); i < n; i++) {
            if (i != 0)
                print(',');
            visit(node.getArgument(i));
        }
        print(')');
    }

    @Override
    public void visitNewExpression(NewExpression node) {
        super.visitNewExpression(node);
    }

    @Override
    public void visitSpreadElement(SpreadElement node) {
        print("...");
        visit(node.getArgument());
    }

    @Override
    public void visitSequenceExpression(SequenceExpression node) {
        super.visitSequenceExpression(node);
    }

    @Override
    public void visitConcatExpression(ConcatExpression node) {
        super.visitConcatExpression(node);
    }

    @Override
    public void visitTemplateExpression(TemplateExpression node) {
        for (Expression expr : node.getExpressions()) {
            if (expr instanceof Literal) {
                Object value = ((Literal) expr).getValue();
                if (value instanceof String) {
                    print(value);
                    continue;
                }
            }
            print(node.getPrefix());
            visit(expr);
            print(node.getPostfix());
        }
    }

    @Override
    public void visitBraceExpression(BraceExpression node) {
        print("(");
        visit(node.getExpr());
        print(')');
    }

    @Override
    public void visitObjectBinding(ObjectBinding node) {
        super.visitObjectBinding(node);
    }

    @Override
    public void visitPropertyBinding(PropertyBinding node) {
        super.visitPropertyBinding(node);
    }

    @Override
    public void visitRestBinding(RestBinding node) {
        super.visitRestBinding(node);
    }

    @Override
    public void visitArrayBinding(ArrayBinding node) {
        super.visitArrayBinding(node);
    }

    @Override
    public void visitArrayElementBinding(ArrayElementBinding node) {
        super.visitArrayElementBinding(node);
    }

    @Override
    public void visitExportDeclaration(ExportDeclaration node) {
        super.visitExportDeclaration(node);
    }

    @Override
    public void visitExportNamedDeclaration(ExportNamedDeclaration node) {
        super.visitExportNamedDeclaration(node);
    }

    @Override
    public void visitExportAllDeclaration(ExportAllDeclaration node) {
        super.visitExportAllDeclaration(node);
    }

    @Override
    public void visitExportSpecifier(ExportSpecifier node) {
        super.visitExportSpecifier(node);
    }

    @Override
    public void visitImportDeclaration(ImportDeclaration node) {
        super.visitImportDeclaration(node);
    }

    @Override
    public void visitImportAsDeclaration(ImportAsDeclaration node) {
        super.visitImportAsDeclaration(node);
    }

    @Override
    public void visitImportSpecifier(ImportSpecifier node) {
        super.visitImportSpecifier(node);
    }

    @Override
    public void visitImportDefaultSpecifier(ImportDefaultSpecifier node) {
        super.visitImportDefaultSpecifier(node);
    }

    @Override
    public void visitImportNamespaceSpecifier(ImportNamespaceSpecifier node) {
        super.visitImportNamespaceSpecifier(node);
    }

    @Override
    public void visitAwaitExpression(AwaitExpression node) {
        super.visitAwaitExpression(node);
    }

    @Override
    public void visitDecorators(Decorators node) {
        super.visitDecorators(node);
    }

    @Override
    public void visitQualifiedName(QualifiedName node) {
        print(node.getFullName());
    }

    @Override
    public void visitDecorator(Decorator node) {
        super.visitDecorator(node);
    }

    @Override
    public void visitMetaObject(MetaObject node) {
        super.visitMetaObject(node);
    }

    @Override
    public void visitMetaProperty(MetaProperty node) {
        super.visitMetaProperty(node);
    }

    @Override
    public void visitMetaArray(MetaArray node) {
        super.visitMetaArray(node);
    }

    @Override
    public void visitUsingStatement(UsingStatement node) {
        super.visitUsingStatement(node);
    }

    @Override
    public void visitMacroExpression(MacroExpression node) {
        super.visitMacroExpression(node);
    }

    @Override
    public void visitTextOutputExpression(TextOutputExpression node) {
        super.visitTextOutputExpression(node);
    }

    @Override
    public void visitEscapeOutputExpression(EscapeOutputExpression node) {
        super.visitEscapeOutputExpression(node);
    }

    @Override
    public void visitCollectOutputExpression(CollectOutputExpression node) {
        super.visitCollectOutputExpression(node);
    }

    @Override
    public void visitCompareOpExpression(CompareOpExpression node) {

        this.visitChild(node.getLeft());
        FilterOp filterOp = FilterOp.fromName(node.getOp());
        String op = filterOp == null ? node.getOp() : Objects.toString(filterOp.getMathSymbol(), node.getOp());
        sb.append(' ').append(op).append(' ');
        this.visitChild(node.getRight());
    }


    @Override
    public void visitAssertOpExpression(AssertOpExpression node) {
        super.visitAssertOpExpression(node);
    }

    @Override
    public void visitBetweenOpExpression(BetweenOpExpression node) {
        super.visitBetweenOpExpression(node);
    }

    @Override
    public void visitGenNodeExpression(GenNodeExpression node) {
        super.visitGenNodeExpression(node);
    }

    @Override
    public void visitGenNodeAttrExpression(GenNodeAttrExpression node) {
        super.visitGenNodeAttrExpression(node);
    }

    @Override
    public void visitOutputXmlAttrExpression(OutputXmlAttrExpression node) {
        super.visitOutputXmlAttrExpression(node);
    }

    @Override
    public void visitOutputXmlExtAttrsExpression(OutputXmlExtAttrsExpression node) {
        super.visitOutputXmlExtAttrsExpression(node);
    }

    @Override
    public void visitTypeOfExpression(TypeOfExpression node) {
        super.visitTypeOfExpression(node);
    }

    @Override
    public void visitInstanceOfExpression(InstanceOfExpression node) {
        super.visitInstanceOfExpression(node);
    }

    @Override
    public void visitCastExpression(CastExpression node) {
        super.visitCastExpression(node);
    }

    @Override
    public void visitArrayTypeNode(ArrayTypeNode node) {
        super.visitArrayTypeNode(node);
    }

    @Override
    public void visitParameterizedTypeNode(ParameterizedTypeNode node) {
        super.visitParameterizedTypeNode(node);
    }

    @Override
    public void visitTypeNameNode(TypeNameNode node) {
        super.visitTypeNameNode(node);
    }

    @Override
    public void visitUnionTypeDef(UnionTypeDef node) {
        super.visitUnionTypeDef(node);
    }

    @Override
    public void visitIntersectionTypeDef(IntersectionTypeDef node) {
        super.visitIntersectionTypeDef(node);
    }

    @Override
    public void visitObjectTypeDef(ObjectTypeDef node) {
        super.visitObjectTypeDef(node);
    }

    @Override
    public void visitPropertyTypeDef(PropertyTypeDef node) {
        super.visitPropertyTypeDef(node);
    }

    @Override
    public void visitTupleTypeDef(TupleTypeDef node) {
        super.visitTupleTypeDef(node);
    }

    @Override
    public void visitTypeParameterNode(TypeParameterNode node) {
        super.visitTypeParameterNode(node);
    }

    @Override
    public void visitTypeAliasDeclaration(TypeAliasDeclaration node) {
        super.visitTypeAliasDeclaration(node);
    }

    @Override
    public void visitFunctionTypeDef(FunctionTypeDef node) {
        super.visitFunctionTypeDef(node);
    }

    @Override
    public void visitFunctionArgTypeDef(FunctionArgTypeDef node) {
        super.visitFunctionArgTypeDef(node);
    }

    @Override
    public void visitEnumDeclaration(EnumDeclaration node) {
        super.visitEnumDeclaration(node);
    }

    @Override
    public void visitEnumMember(EnumMember node) {
        super.visitEnumMember(node);
    }

    @Override
    public void visitClassDefinition(ClassDefinition node) {
        super.visitClassDefinition(node);
    }

    @Override
    public void visitFieldDeclaration(FieldDeclaration node) {
        super.visitFieldDeclaration(node);
    }

    @Override
    public void visitCustomExpression(CustomExpression node) {
        print(node.getSource());
    }

    @Override
    public void visitProgram(Program node) {
        if (node.getBody() != null) {
            for (XLangASTNode n : node.getBody()) {
                visit(n);
                print(";\n");
            }
        }
    }
}
