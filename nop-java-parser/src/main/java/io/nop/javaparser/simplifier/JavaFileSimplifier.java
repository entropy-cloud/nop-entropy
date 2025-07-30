package io.nop.javaparser.simplifier;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.util.Iterator;
import java.util.stream.Collectors;

public class JavaFileSimplifier {

    public static void simplify(CompilationUnit cu) {
        cu.accept(new ClassSimplifierVisitor(), null);
    }

    private static class ClassSimplifierVisitor extends ModifierVisitor<Void> {
        @Override
        public Visitable visit(ClassOrInterfaceDeclaration n, Void arg) {
            // 处理字段 - 创建新集合替换
            n.setMembers(new NodeList<>(n.getMembers().stream()
                    .filter(m -> !(m instanceof FieldDeclaration))
                    .collect(Collectors.toList())));

            // 处理方法 - 使用迭代器
            Iterator<BodyDeclaration<?>> it = n.getMembers().iterator();
            while (it.hasNext()) {
                BodyDeclaration<?> member = it.next();
                if (member instanceof MethodDeclaration) {
                    MethodDeclaration m = (MethodDeclaration) member;
                    if (!m.isPublic()) {
                        it.remove();
                    } else {
                        m.removeBody();
                    }
                }
            }

            // 处理构造方法
            n.getConstructors().forEach(c -> {
                if (!c.isPublic()) {
                    c.remove();
                } else {
                    c.setBody(new BlockStmt()); // 构造方法需要空块
                }
            });

            super.visit(n, arg);

            return n;
        }

        @Override
        public Visitable visit(EnumDeclaration n, Void arg) {

            // 处理字段 - 创建新集合替换
            n.setMembers(new NodeList<>(n.getMembers().stream()
                    .filter(m -> !(m instanceof FieldDeclaration))
                    .collect(Collectors.toList())));

            // 处理方法 - 使用迭代器
            Iterator<BodyDeclaration<?>> it = n.getMembers().iterator();
            while (it.hasNext()) {
                BodyDeclaration<?> member = it.next();
                if (member instanceof MethodDeclaration) {
                    MethodDeclaration m = (MethodDeclaration) member;
                    if (!m.isPublic()) {
                        it.remove();
                    } else {
                        m.removeBody();
                    }
                }
            }

            // 处理构造方法
            n.getConstructors().forEach(c -> {
                if (!c.isPublic()) {
                    c.remove();
                } else {
                    c.setBody(new BlockStmt()); // 构造方法需要空块
                }
            });

            super.visit(n, arg);

            return n;
        }
    }
}