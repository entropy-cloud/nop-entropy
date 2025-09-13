package io.nop.javaparser.simplifier;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class JavaFileSimplifier {

    private final Set<String> methodNames;
    private final boolean keepMethodBody;
    private final boolean keepFields;
    private final boolean keepImports;

    /**
     * 创建默认的简化器：只保留公开方法，移除方法体、字段和imports
     */
    public JavaFileSimplifier() {
        this(null, false, false, false);
    }

    /**
     * 创建自定义配置的简化器
     *
     * @param methodNames    指定要保留的方法名集合，如果为null则只保留公开方法
     * @param keepMethodBody 是否保留方法体
     */
    public JavaFileSimplifier(Set<String> methodNames, boolean keepMethodBody) {
        this(methodNames, keepMethodBody, false, false);
    }

    /**
     * 创建自定义配置的简化器
     *
     * @param methodNames    指定要保留的方法名集合，如果为null则只保留公开方法
     * @param keepMethodBody 是否保留方法体
     * @param keepFields     是否保留字段
     */
    public JavaFileSimplifier(Set<String> methodNames, boolean keepMethodBody, boolean keepFields) {
        this(methodNames, keepMethodBody, keepFields, false);
    }

    /**
     * 创建完全自定义配置的简化器
     *
     * @param methodNames    指定要保留的方法名集合，如果为null则只保留公开方法
     * @param keepMethodBody 是否保留方法体
     * @param keepFields     是否保留字段
     * @param keepImports    是否保留import语句
     */
    public JavaFileSimplifier(Set<String> methodNames, boolean keepMethodBody, boolean keepFields, boolean keepImports) {
        this.methodNames = methodNames;
        this.keepMethodBody = keepMethodBody;
        this.keepFields = keepFields;
        this.keepImports = keepImports;
    }

    /**
     * 简化Java文件
     *
     * @param cu 编译单元
     */
    public void simplify(CompilationUnit cu) {
        cu.accept(new ClassSimplifierVisitor(), null);
    }

    private class ClassSimplifierVisitor extends ModifierVisitor<Void> {

        @Override
        public Visitable visit(CompilationUnit n, Void arg) {
            // 处理imports - 创建新的集合
            if (!keepImports) {
                n.setImports(new NodeList<>());
            }

            super.visit(n, arg);
            return n;
        }

        @Override
        public Visitable visit(ClassOrInterfaceDeclaration n, Void arg) {
            processClassOrInterface(n);
            super.visit(n, arg);
            return n;
        }

        @Override
        public Visitable visit(EnumDeclaration n, Void arg) {
            processEnum(n);
            super.visit(n, arg);
            return n;
        }

        private void processClassOrInterface(ClassOrInterfaceDeclaration n) {
            // 处理所有成员（字段、方法等）
            NodeList<BodyDeclaration<?>> newMembers = new NodeList<>();

            for (BodyDeclaration<?> member : n.getMembers()) {
                if (member instanceof FieldDeclaration) {
                    // 字段处理：根据keepFields决定是否保留
                    if (keepFields) {
                        newMembers.add(member);
                    }
                } else if (member instanceof MethodDeclaration) {
                    // 方法处理
                    MethodDeclaration method = (MethodDeclaration) member;
                    if (shouldKeepMethod(method)) {
                        if (!keepMethodBody) {
                            method.removeBody();
                        }
                        newMembers.add(method);
                    }
                } else {
                    // 其他成员（如内部类等）保留
                    newMembers.add(member);
                }
            }

            // 替换成员列表
            n.setMembers(newMembers);

            // 处理构造方法 - 使用移除方式而不是清空重建
            List<ConstructorDeclaration> constructors = n.getConstructors();

            // 收集需要移除的构造方法
            List<ConstructorDeclaration> toRemove = new ArrayList<>();

            for (ConstructorDeclaration constructor : constructors) {
                if (!shouldKeepConstructor(constructor.getNameAsString(), constructor.isPublic())) {
                    toRemove.add(constructor);
                } else {
                    // 对保留的构造方法处理方法体
                    if (!keepMethodBody) {
                        constructor.setBody(new BlockStmt());
                    }
                }
            }

            // 移除不需要的构造方法
            for (ConstructorDeclaration constructor : toRemove) {
                constructors.remove(constructor);
            }
        }

        private void processEnum(EnumDeclaration n) {
            // 处理所有成员（字段、方法等）
            NodeList<BodyDeclaration<?>> newMembers = new NodeList<>();

            for (BodyDeclaration<?> member : n.getMembers()) {
                if (member instanceof FieldDeclaration) {
                    // 字段处理：根据keepFields决定是否保留
                    if (keepFields) {
                        newMembers.add(member);
                    }
                } else if (member instanceof MethodDeclaration) {
                    // 方法处理
                    MethodDeclaration method = (MethodDeclaration) member;
                    if (shouldKeepMethod(method)) {
                        if (!keepMethodBody) {
                            method.removeBody();
                        }
                        newMembers.add(method);
                    }
                } else {
                    // 其他成员保留
                    newMembers.add(member);
                }
            }

            // 替换成员列表
            n.setMembers(newMembers);

            // 处理构造方法 - 使用移除方式而不是清空重建
            List<ConstructorDeclaration> constructors = n.getConstructors();

            // 收集需要移除的构造方法
            List<ConstructorDeclaration> toRemove = new ArrayList<>();

            for (ConstructorDeclaration constructor : constructors) {
                if (!shouldKeepConstructor(constructor.getNameAsString(), constructor.isPublic())) {
                    toRemove.add(constructor);
                } else {
                    // 对保留的构造方法处理方法体
                    if (!keepMethodBody) {
                        constructor.setBody(new BlockStmt());
                    }
                }
            }

            // 移除不需要的构造方法
            for (ConstructorDeclaration constructor : toRemove) {
                n.remove(constructor);
            }
        }

        /**
         * 判断是否应该保留指定的方法
         */
        private boolean shouldKeepMethod(MethodDeclaration method) {
            if (methodNames != null && !methodNames.isEmpty()) {
                // 如果指定了方法名列表，只保留指定名称的方法（无论是否私有）
                return methodNames.contains(method.getNameAsString());
            } else {
                // 如果没有指定方法名列表，只保留公开方法
                return method.isPublic();
            }
        }

        /**
         * 判断是否应该保留指定的构造方法
         */
        private boolean shouldKeepConstructor(String constructorName, boolean isPublic) {
            if (methodNames != null && !methodNames.isEmpty()) {
                // 如果指定了方法名列表，检查是否包含构造方法名
                return methodNames.contains(constructorName);
            } else {
                // 如果没有指定方法名列表，只保留公开构造方法
                return isPublic;
            }
        }
    }
}