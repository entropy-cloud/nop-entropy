package io.nop.ai.code_analyzer.code;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.AccessSpecifier;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.resolution.declarations.ResolvedFieldDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.IoHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

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
                || name.startsWith("com.apache.commons.");
    }

    public CodeFileInfo parseFromFile(File file) {
        FileInputStream in = null;
        try {
            in = new FileInputStream(file);
            CompilationUnit cu = javaParser.parse(in).getResult().orElseThrow();

            CodeFileInfo fileInfo = new CodeFileInfo();
            fileInfo.setFilePath(file.getAbsolutePath());
            fileInfo.setLanguage("java");
            fileInfo.setLastModified(file.lastModified());
            fileInfo.setLineCount(countLines(file));

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

    private int countLines(File file) throws IOException {
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int count = 0;
            int n;
            while ((n = in.read(buffer)) != -1) {
                for (int i = 0; i < n; i++) {
                    if (buffer[i] == '\n') {
                        count++;
                    }
                }
            }
            return count;
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
        public void visit(ClassOrInterfaceDeclaration classDecl, List<CodeFileInfo.CodeClassInfo> classes) {
            String originalOuterClassName = currentOuterClassName;

            // Create class info
            CodeFileInfo.CodeClassInfo classInfo = new CodeFileInfo.CodeClassInfo();
            classInfo.setName(getFullClassName(classDecl));
            classInfo.setLine(classDecl.getBegin().map(p -> p.line).orElse(-1));
            classInfo.setAccessModifier(getAccessModifier(classDecl.getAccessSpecifier()));

            // Handle inner classes
            if (classDecl.isInnerClass()) {
                currentOuterClassName = getFullClassName(classDecl);
            }

            // Parse fields - with fully qualified type names
            List<CodeFileInfo.CodeVariableInfo> fields = new ArrayList<>();
            for (FieldDeclaration field : classDecl.getFields()) {
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
                CodeFileInfo.CodeFunctionInfo methodInfo = parseMethod(method, classInfo.getName());
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

        private CodeFileInfo.CodeFunctionInfo parseMethod(MethodDeclaration method, String ownerClassName) {
            CodeFileInfo.CodeFunctionInfo methodInfo = new CodeFileInfo.CodeFunctionInfo();
            methodInfo.setName(method.getNameAsString());
            methodInfo.setLine(method.getBegin().map(p -> p.line).orElse(-1));
            methodInfo.setAccessModifier(getAccessModifier(method.getAccessSpecifier()));
            methodInfo.setOwnerClassName(ownerClassName);
            methodInfo.setStatic(method.isStatic());

            // Return type - resolve to fully qualified name
            try {
                methodInfo.setReturnType(method.getType().resolve().describe());
            } catch (Exception e) {
                LOG.debug("nop.ai.code-analyzer.resolve-return-type-fail:{}", method.getType(), e);
                methodInfo.setReturnType(method.getType().asString());
            }

            // Parameters - resolve to fully qualified names
            List<CodeFileInfo.CodeVariableInfo> params = new ArrayList<>();
            for (Parameter param : method.getParameters()) {
                CodeFileInfo.CodeVariableInfo paramInfo = new CodeFileInfo.CodeVariableInfo();
                paramInfo.setName(param.getNameAsString());

                try {
                    paramInfo.setType(param.getType().resolve().describe());
                } catch (Exception e) {
                    LOG.debug("nop.ai.code-analyzer.resolve-param-type-fail:{}", param.getType(), e);
                    paramInfo.setType(param.getType().asString());
                }

                params.add(paramInfo);

                // Check for varargs
                if (param.isVarArgs()) {
                    methodInfo.setVarArgs(true);
                }
            }
            methodInfo.setParams(params);

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
                String declaringClassName = resolved.declaringType().getQualifiedName();
                if (!ignoredTypes.test(declaringClassName))
                    methodInfo.addUsedFn(declaringClassName + "::" + methodName);
            } catch (Exception e) {
                LOG.debug("nop.ai.code-analyzer.resolve-method-call-fail:{}", call, e);
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