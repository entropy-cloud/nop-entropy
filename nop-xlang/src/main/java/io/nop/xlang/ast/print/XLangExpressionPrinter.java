package io.nop.xlang.ast.print;

import io.nop.commons.util.StringHelper;
import io.nop.xlang.ast.*;

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

    protected XLangExpressionPrinter print(Object text) {
        sb.append(text);
        return this;
    }

    void incIndent() {
        indentLevel++;
    }

    void decIndent() {
        indentLevel--;
    }

    void indent() {
        if (pretty) {
            for (int i = 0; i < this.indentLevel; ++i) {
                print("  ");
            }
        }
    }

    void println() {
        print("\n");
        indent();
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
        super.visitReturnStatement(node);
    }

    @Override
    public void visitBreakStatement(BreakStatement node) {
        super.visitBreakStatement(node);
    }

    @Override
    public void visitContinueStatement(ContinueStatement node) {
        super.visitContinueStatement(node);
    }

    @Override
    public void visitIfStatement(IfStatement node) {
        super.visitIfStatement(node);
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
    public void visitUnaryExpression(UnaryExpression node) {
        super.visitUnaryExpression(node);
    }

    @Override
    public void visitUpdateExpression(UpdateExpression node) {
        super.visitUpdateExpression(node);
    }

    @Override
    public void visitBinaryExpression(BinaryExpression node) {
        visit(node.getLeft());

        print(' ');
        print(node.getOperator());
        print(' ');

        visit(node.getRight());
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
    public void visitAssignmentExpression(AssignmentExpression node) {
        super.visitAssignmentExpression(node);
    }

    @Override
    public void visitLogicalExpression(LogicalExpression node) {
        visit(node.getLeft());
        print(' ');
        print(node.getOperator());
        print(' ');
        print(node.getRight());
    }

    @Override
    public void visitMemberExpression(MemberExpression node) {
        visit(node.getObject());
        if (node.getOptional()) {
            print("?.");
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
        super.visitQualifiedName(node);
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
        super.visitCompareOpExpression(node);
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
        super.visitCustomExpression(node);
    }
}
