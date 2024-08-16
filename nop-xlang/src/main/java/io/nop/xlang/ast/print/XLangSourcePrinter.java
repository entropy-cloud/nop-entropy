package io.nop.xlang.ast.print;

import io.nop.xlang.ast.ClassDefinition;
import io.nop.xlang.ast.CompilationUnit;
import io.nop.xlang.ast.CustomExpression;
import io.nop.xlang.ast.EnumDeclaration;
import io.nop.xlang.ast.EnumMember;
import io.nop.xlang.ast.FieldDeclaration;
import io.nop.xlang.ast.FunctionDeclaration;
import io.nop.xlang.ast.ImportAsDeclaration;
import io.nop.xlang.ast.ParameterDeclaration;
import io.nop.xlang.ast.ParameterizedTypeNode;
import io.nop.xlang.ast.XLangClassKind;

public class XLangSourcePrinter extends XLangExpressionPrinter {

    public XLangSourcePrinter(){
        setPretty(true);
    }

    @Override
    public void visitCompilationUnit(CompilationUnit node) {
        print("package ");
        print(node.getPackageName());
        print(';');
        println();
        println();

        visitChildren(node.getStatements());
    }

    @Override
    public void visitImportAsDeclaration(ImportAsDeclaration node) {
        print("import ");
        if(node.getStaticImport()){
            print(" static ");
        }
        if (node.getSource() != null)
            visit(node.getSource());
        if (node.getLocal() != null) {
            print(" as ");
            visit(node.getLocal());
        }
        print(';');
        println();
    }

    @Override
    public void visitClassDefinition(ClassDefinition node) {
        println();
        if (node.getClassKind() == XLangClassKind.INTERFACE) {
            print("interface ");
        } else {
            print("class ");
        }
        visit(node.getName());
        if (node.getExtendsType() != null) {
            print(" extends ");
            visit(node.getExtendsType());
        }

        if (node.getImplementTypes() != null) {
            print(" implements ");
            printList(node.getImplementTypes(), " , ");
        }
        print("{").incIndent().println();

        if (node.getMethods() != null) {
            for (FunctionDeclaration fn : node.getMethods()) {
                visit(fn);
            }
        }

        decIndent().println().print("}").println();
    }

    @Override
    public void visitParameterizedTypeNode(ParameterizedTypeNode node) {
        print(node.getTypeName());
        if (node.getTypeArgs() != null && node.getTypeArgs().size() > 0) {
            print('<');
            printList(node.getTypeArgs(), " , ");
            print('>');
        }
    }

    @Override
    public void visitFunctionDeclaration(FunctionDeclaration node) {
        println();
        visit(node.getName());
        print('(');
        printList(node.getParams(), " , ");
        print(')');
        if (node.getReturnType() != null) {
            print(':');
            visit(node.getReturnType());
        }
        print(' ').print('{').incIndent().println();
        decIndent().println().print('}').println();
    }

    @Override
    public void visitParameterDeclaration(ParameterDeclaration node) {
        visit(node.getName());
        if (node.getType() != null) {
            print(':');
            visit(node.getType());
        }
    }

    @Override
    public void visitFieldDeclaration(FieldDeclaration node) {
        super.visitFieldDeclaration(node);
    }

    @Override
    public void visitCustomExpression(CustomExpression node) {
        super.visitCustomExpression(node);
    }

    @Override
    public void visitEnumMember(EnumMember node) {
        super.visitEnumMember(node);
    }

    @Override
    public void visitEnumDeclaration(EnumDeclaration node) {
        super.visitEnumDeclaration(node);
    }
}
