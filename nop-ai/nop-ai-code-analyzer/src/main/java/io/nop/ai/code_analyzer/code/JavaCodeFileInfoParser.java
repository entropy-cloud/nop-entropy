package io.nop.ai.code_analyzer.code;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.AccessSpecifier;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.resolution.declarations.ResolvedFieldDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import com.github.javaparser.resolution.types.ResolvedType;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.FileHelper;
import io.nop.commons.util.IoHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static io.nop.commons.util.FileHelper.countLines;

public class JavaCodeFileInfoParser {
    static final Logger LOG = LoggerFactory.getLogger(JavaCodeFileInfoParser.class);

    private final JavaParser javaParser;
    private final Predicate<String> ignoredTypes;

    public JavaCodeFileInfoParser(JavaParser javaParser, Predicate<String> ignoredTypes) {
        this.javaParser = javaParser;
        this.ignoredTypes = ignoredTypes;
    }

    public JavaCodeFileInfoParser(JavaParser javaParser) {
        this(javaParser, JavaCodeFileInfoParser::defaultIgnoredType);
    }

    public static boolean defaultIgnoredType(String name) {
        return name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("jakarta.")
                || name.startsWith("org.springframework.")
                || name.startsWith("com.apache.commons.")
                || name.startsWith("org.slf4j.");
    }

    public CodeFileInfo parseFromFile(File file) {
        CodeFileInfo fileInfo = new CodeFileInfo();
        fileInfo.setFilePath(file.getAbsolutePath());
        fileInfo.setLanguage("java");
        fileInfo.setLastModified(file.lastModified());
        fileInfo.setLineCount(countLines(file));
        fileInfo.setMd5(FileHelper.calculateMD5(file));

        FileInputStream in = null;
        try {
            in = new FileInputStream(file);
            CompilationUnit cu = javaParser.parse(in).getResult().orElseThrow();

            // Set package name
            cu.getPackageDeclaration().ifPresent(pkg ->
                    fileInfo.setPackageName(pkg.getNameAsString()));

            // Set imports
            Set<String> imports = cu.getImports().stream()
                    .map(ImportDeclaration::getNameAsString)
                    .filter(name -> !ignoredTypes.test(name))
                    .collect(Collectors.toSet());
            fileInfo.setImports(imports);

            // Parse classes
            List<CodeFileInfo.CodeClassInfo> classes = new ArrayList<>();
            new ClassVisitor(fileInfo).visit(cu, classes);

            // For simplicity, we're not setting all fields of CodeFileInfo
            // You may want to add artifactId, md5, etc. as needed
            fileInfo.setClasses(classes);

            return fileInfo;
        } catch (IOException e) {
            throw NopException.adapt(e);
        } finally {
            IoHelper.safeCloseObject(in);
        }
    }

    private class ClassVisitor extends VoidVisitorAdapter<List<CodeFileInfo.CodeClassInfo>> {
        private final CodeFileInfo fileInfo;
        private String currentPackage;
        private String currentOuterClassName = "";

        public ClassVisitor(CodeFileInfo fileInfo) {
            this.fileInfo = fileInfo;
            this.currentPackage = fileInfo.getPackageName() != null ?
                    fileInfo.getPackageName() + "." : "";
        }

        @Override
        public void visit(EnumDeclaration enumDecl, List<CodeFileInfo.CodeClassInfo> classes) {
            CodeFileInfo.CodeClassInfo classInfo = new CodeFileInfo.CodeClassInfo();
            String className = getFullClassName(enumDecl);
            classInfo.setName(className);
            classInfo.setLine(enumDecl.getBegin().map(p -> p.line).orElse(-1));
            classInfo.setAccessModifier(getAccessModifier(enumDecl.getAccessSpecifier()));

            // ✅ 设置 signature
            classInfo.setSignature(buildEnumSignature(enumDecl));

            classes.add(classInfo);

            // 可选：处理 enum 常量、方法等
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration classDecl, List<CodeFileInfo.CodeClassInfo> classes) {
            String originalOuterClassName = currentOuterClassName;

            // Create class info
            CodeFileInfo.CodeClassInfo classInfo = new CodeFileInfo.CodeClassInfo();
            String className = getFullClassName(classDecl);
            classInfo.setName(className);
            classInfo.setLine(classDecl.getBegin().map(p -> p.line).orElse(-1));
            classInfo.setAccessModifier(getAccessModifier(classDecl.getAccessSpecifier()));
            classInfo.setSignature(buildClassSignature(classDecl));

            // 解析继承关系 (extends)
            classDecl.getExtendedTypes().stream()
                    .findFirst()
                    .ifPresent(extendedType -> {
                        try {
                            classInfo.setExtendsType(extendedType.resolve().describe());
                        } catch (Exception e) {
                            LOG.debug("nop.ai.code-analyzer.resolve-extends-type-fail:{}", extendedType, e);
                            classInfo.setExtendsType(extendedType.getNameAsString());
                        }
                    });

            // 解析实现接口 (implements)
            Set<String> implementsTypes = new LinkedHashSet<>();
            for (ClassOrInterfaceType implementedType : classDecl.getImplementedTypes()) {
                try {
                    implementsTypes.add(implementedType.resolve().describe());
                } catch (Exception e) {
                    LOG.debug("nop.ai.code-analyzer.resolve-implements-type-fail:{}", implementedType, e);
                    implementsTypes.add(implementedType.getNameAsString());
                }
            }
            classInfo.setImplementsTypes(implementsTypes);

            currentOuterClassName = className;

            // Parse fields - with fully qualified type names
            List<CodeFileInfo.CodeVariableInfo> fields = new ArrayList<>();
            for (FieldDeclaration field : classDecl.getFields()) {
                AccessSpecifier accessSpecifier = field.getAccessSpecifier();
                boolean isPublic = accessSpecifier == AccessSpecifier.PUBLIC;
                if (!isPublic)
                    continue;

                for (VariableDeclarator var : field.getVariables()) {
                    CodeFileInfo.CodeVariableInfo varInfo = new CodeFileInfo.CodeVariableInfo();
                    varInfo.setName(var.getNameAsString());

                    try {
                        // Resolve field type to fully qualified name
                        varInfo.setType(field.getElementType().resolve().describe());
                    } catch (Exception e) {
                        LOG.debug("nop.ai.code-analyzer.resolve-field-type-fail:{}", field.getElementType(), e);
                        varInfo.setType(field.getElementType().asString());
                    }

                    fields.add(varInfo);
                }
            }
            classInfo.setVariables(fields);

            // Parse methods
            List<CodeFileInfo.CodeFunctionInfo> methods = new ArrayList<>();
            for (MethodDeclaration method : classDecl.getMethods()) {
                CodeFileInfo.CodeFunctionInfo methodInfo = parseMethod(method, classInfo);
                methods.add(methodInfo);
            }
            classInfo.setFunctions(methods);

            classes.add(classInfo);

            // Visit nested classes
            for (BodyDeclaration<?> member : classDecl.getMembers()) {
                if (member.isClassOrInterfaceDeclaration()) {
                    member.asClassOrInterfaceDeclaration().accept(this, classes);
                }
            }

            // Restore outer class name
            currentOuterClassName = originalOuterClassName;
        }

        private CodeFileInfo.CodeFunctionInfo parseMethod(MethodDeclaration method, CodeFileInfo.CodeClassInfo classInfo) {
            String ownerClassName = classInfo.getName();
            String fnName = ownerClassName + "::" + method.getNameAsString() + "(" + method.getParameters().size() + ")";
            CodeFileInfo.AccessModifier specifier = getAccessModifier(method.getAccessSpecifier());

            CodeFileInfo.CodeFunctionInfo methodInfo = classInfo.makeFunction(fnName);
            methodInfo.setLine(method.getBegin().map(p -> p.line).orElse(-1));
            methodInfo.setSignature(buildMethodSignature(method));

            // Parse method body for used variables and functions
            if (method.getBody().isPresent()) {
                MethodBodyVisitor bodyVisitor = new MethodBodyVisitor(fileInfo, ownerClassName);
                bodyVisitor.visit(method.getBody().get(), methodInfo);
            }

            return methodInfo;
        }

        private String getFullClassName(ClassOrInterfaceDeclaration classDecl) {
            String className = classDecl.getNameAsString();
            if (currentOuterClassName.isEmpty()) {
                return currentPackage + className;
            }
            return currentOuterClassName + "." + className;
        }

        private String getFullClassName(EnumDeclaration classDecl) {
            String className = classDecl.getNameAsString();
            if (currentOuterClassName.isEmpty()) {
                return currentPackage + className;
            }
            return currentOuterClassName + "." + className;
        }

        private CodeFileInfo.AccessModifier getAccessModifier(AccessSpecifier specifier) {
            switch (specifier) {
                case PUBLIC:
                    return CodeFileInfo.AccessModifier.PUBLIC;
                case PRIVATE:
                    return CodeFileInfo.AccessModifier.PRIVATE;
                case PROTECTED:
                    return CodeFileInfo.AccessModifier.PROTECTED;
                default:
                    return CodeFileInfo.AccessModifier.PACKAGE_PRIVATE;
            }
        }
    }

    public static String buildClassSignature(ClassOrInterfaceDeclaration classDecl) {
        StringBuilder sb = new StringBuilder();

        // 访问修饰符
        AccessSpecifier access = classDecl.getAccessSpecifier();
        if (access != AccessSpecifier.NONE) {
            sb.append(access.asString()).append(" ");
        }

        // 类型（class 或 interface）
        if (classDecl.isInterface()) {
            sb.append("interface ");
        }else if(classDecl.isEnumDeclaration()){
            sb.append("enum ");
        } else {
            sb.append("class ");
        }

        // 类名
        sb.append(classDecl.getNameAsString());

        // 继承
        if (!classDecl.getExtendedTypes().isEmpty()) {
            sb.append(" extends ");
            sb.append(classDecl.getExtendedTypes().stream()
                    .map(ClassOrInterfaceType::getNameAsString)
                    .collect(Collectors.joining(", ")));
        }

        // 实现接口
        if (!classDecl.getImplementedTypes().isEmpty()) {
            sb.append(" implements ");
            sb.append(classDecl.getImplementedTypes().stream()
                    .map(ClassOrInterfaceType::getNameAsString)
                    .collect(Collectors.joining(", ")));
        }

        return sb.toString();
    }

    public static String buildEnumSignature(EnumDeclaration enumDecl) {
        StringBuilder sb = new StringBuilder();

        AccessSpecifier access = enumDecl.getAccessSpecifier();
        if (access != AccessSpecifier.NONE) {
            sb.append(access.asString()).append(" ");
        }

        sb.append("enum ").append(enumDecl.getNameAsString());

        // enum 可以实现接口
        if (!enumDecl.getImplementedTypes().isEmpty()) {
            sb.append(" implements ");
            sb.append(enumDecl.getImplementedTypes().stream()
                    .map(ClassOrInterfaceType::getNameAsString)
                    .collect(Collectors.joining(", ")));
        }

        return sb.toString();
    }

    /**
     * 构建方法的完整签名
     *
     * @param method 方法声明
     * @return 完整的方法签名，例如："public static void main(String[] args)"
     */
    public static String buildMethodSignature(MethodDeclaration method) {
        StringBuilder signature = new StringBuilder();

        // 1. 添加访问修饰符
        AccessSpecifier specifier = method.getAccessSpecifier();
        if (specifier != AccessSpecifier.NONE)
            signature.append(specifier.asString()).append(" ");

        // 2. 添加static修饰符
        if (method.isStatic()) {
            signature.append("static ");
        }

        // 3. 添加返回类型
        signature.append(method.getType().asString()).append(" ");

        // 4. 添加方法名
        signature.append(method.getNameAsString());

        // 5. 添加参数列表
        signature.append("(");
        signature.append(method.getParameters().stream()
                .map(JavaCodeFileInfoParser::parameterToString)
                .collect(Collectors.joining(", ")));
        signature.append(")");

        return signature.toString();
    }

    /**
     * 将参数转换为字符串表示
     *
     * @param param 方法参数
     * @return 参数的字符串表示，例如："String[] args"
     */
    static String parameterToString(Parameter param) {
        return param.getType().asString() + " " + param.getNameAsString();
    }

    private class MethodBodyVisitor extends VoidVisitorAdapter<CodeFileInfo.CodeFunctionInfo> {
        private final CodeFileInfo fileInfo;
        private final String ownerClassName;

        public MethodBodyVisitor(CodeFileInfo fileInfo, String ownerClassName) {
            this.fileInfo = fileInfo;
            this.ownerClassName = ownerClassName;
        }

        @Override
        public void visit(MethodCallExpr call, CodeFileInfo.CodeFunctionInfo methodInfo) {
            super.visit(call, methodInfo);

            try {
                ResolvedMethodDeclaration resolved = call.resolve();
                String methodName = resolved.getName();

                // 获取调用者的实际类型
                String declaringClassName = getMostSpecificDeclaringType(call, resolved);

                if (!ignoredTypes.test(declaringClassName)) {
                    methodInfo.addUsedFn(declaringClassName + "::" + methodName + "(" + resolved.getNumberOfParams() + ")");
                }
            } catch (Exception e) {
                LOG.debug("nop.ai.code-analyzer.resolve-method-call-fail:{}", call, e);
            }
        }

        private String getMostSpecificDeclaringType(MethodCallExpr call, ResolvedMethodDeclaration resolvedMethod) {
            try {
                // 首先尝试获取调用表达式的范围（如果有的话）
                if (call.getScope().isPresent()) {
                    ResolvedType scopeType = call.getScope().get().calculateResolvedType();
                    // 检查这个类型是否实现了该方法（可能是派生类或接口实现）
                    if (scopeType.isReferenceType()) {
                        ResolvedReferenceTypeDeclaration typeDecl = scopeType.asReferenceType().getTypeDeclaration().orElse(null);
                        if (typeDecl != null) {
                            return typeDecl.getQualifiedName();
                        }
                    }
                }

                // 如果没有特定范围或范围类型没有重写方法，则返回方法原始定义的类
                return resolvedMethod.declaringType().getQualifiedName();
            } catch (Exception e) {
                LOG.debug("nop.ai.code-analyzer.resolve-caller-type-fail:{}", call, e);
                return resolvedMethod.declaringType().getQualifiedName();
            }
        }

        @Override
        public void visit(NameExpr nameExpr, CodeFileInfo.CodeFunctionInfo methodInfo) {
            super.visit(nameExpr, methodInfo);

            try {
                // 首先尝试解析为变量/字段
                ResolvedValueDeclaration resolved = nameExpr.resolve();
                if (resolved.isField()) {
                    addField(methodInfo, resolved.asField());
                }
            } catch (UnsolvedSymbolException | UnsupportedOperationException e) {
                // 忽略解析失败的情况
            }
        }

        @Override
        public void visit(FieldAccessExpr fieldAccess, CodeFileInfo.CodeFunctionInfo methodInfo) {
            super.visit(fieldAccess, methodInfo);

            try {
                ResolvedValueDeclaration resolved = fieldAccess.resolve();
                if (resolved.isField()) {
                    ResolvedFieldDeclaration field = resolved.asField();
                    addField(methodInfo, field);
                }
            } catch (UnsolvedSymbolException | UnsupportedOperationException e) {
                LOG.debug("nop.ai.code-analyzer.resolve-field-access-fail:{}", fieldAccess, e);
            }
        }

        private void addField(CodeFileInfo.CodeFunctionInfo methodInfo, ResolvedFieldDeclaration field) {
            boolean isPublic = field.accessSpecifier() == AccessSpecifier.PUBLIC;
            if (isPublic) {
                // 获取字段详细信息
                String fieldName = field.getName();
                String declaringClassName = field.declaringType().getQualifiedName();
                if (!ignoredTypes.test(declaringClassName))
                    methodInfo.addUsedVar(declaringClassName + "::" + fieldName);
            }
        }
    }
}