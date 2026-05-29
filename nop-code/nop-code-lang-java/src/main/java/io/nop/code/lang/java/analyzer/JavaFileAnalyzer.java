package io.nop.code.lang.java.analyzer;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.AnnotationMemberDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.comments.JavadocComment;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import io.nop.api.core.util.SourceLocation;
import io.nop.code.core.analyzer.ICodeFileAnalyzer;
import com.github.javaparser.ast.Modifier;
import io.nop.code.core.model.CodeAccessModifier;
import io.nop.code.core.model.CodeAnnotationUsage;
import io.nop.code.core.model.CodeFileAnalysisResult;
import io.nop.code.core.model.CodeInheritance;
import io.nop.code.core.model.CodeLanguage;
import io.nop.code.core.model.CodeMethodCall;
import io.nop.core.lang.json.JsonTool;
import io.nop.code.core.model.CodeRelationType;
import io.nop.code.core.model.CodeSymbol;
import io.nop.code.core.model.CodeSymbolKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Java文件分析器
 * 使用JavaParser解析Java源代码，提取符号信息、引用关系等
 * 支持符号解析，可获取方法调用的全限定名
 * 直接输出Code*模型，无需转换层
 */
public class JavaFileAnalyzer implements ICodeFileAnalyzer {

    private static final Logger LOG = LoggerFactory.getLogger(JavaFileAnalyzer.class);

    private final CombinedTypeSolver typeSolver;

    private final ParserConfiguration parserConfiguration;

    private MethodCallFilter methodCallFilter = MethodCallFilter.createDefault();

    private boolean enableSymbolResolution = true;

    public JavaFileAnalyzer() {
        this.typeSolver = new CombinedTypeSolver();
        this.typeSolver.add(new ReflectionTypeSolver(true));

        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);
        this.parserConfiguration = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21)
                .setSymbolResolver(symbolSolver);
    }

    public void setMethodCallFilter(MethodCallFilter filter) {
        this.methodCallFilter = filter;
    }

    public MethodCallFilter getMethodCallFilter() {
        return methodCallFilter;
    }

    public void setEnableSymbolResolution(boolean enable) {
        this.enableSymbolResolution = enable;
    }

    public CombinedTypeSolver getTypeSolver() {
        return typeSolver;
    }

    @Override
    public CodeLanguage getLanguage() {
        return CodeLanguage.JAVA;
    }

    @Override
    public CodeFileAnalysisResult analyze(String filePath, String sourceCode) {
        return analyze(SourceLocation.fromPath(filePath), sourceCode);
    }

    @Override
    public List<String> getFileExtensions() {
        return Collections.singletonList(".java");
    }

    /**
     * 解析Java源代码文件
     *
     * @param loc        源码位置
     * @param sourceCode 源代码内容
     * @return CodeFileAnalysisResult 包含所有解析出的信息
     */
    public CodeFileAnalysisResult analyze(SourceLocation loc, String sourceCode) {
        if (sourceCode == null || sourceCode.isBlank()) {
            return null;
        }

        ParseResult<CompilationUnit> parseResult = new JavaParser(parserConfiguration).parse(sourceCode);
        if (!parseResult.isSuccessful() || !parseResult.getResult().isPresent()) {
            return null;
        }

        CompilationUnit cu = parseResult.getResult().get();
        CodeFileAnalysisResult result = new CodeFileAnalysisResult();
        result.setFilePath(loc.getPath());
        result.setSourceCode(sourceCode);
        result.setLineCount(countLines(sourceCode));
        result.setLanguage(CodeLanguage.JAVA);

        // 提取包名
        cu.getPackageDeclaration().ifPresent(pkg ->
                result.setPackageName(pkg.getNameAsString()));

        // 提取导入
        for (ImportDeclaration imp : cu.getImports()) {
            result.getImports().add(imp.getNameAsString());
        }

        // 使用Visitor遍历AST提取符号
        IndexVisitor visitor = new IndexVisitor(result, methodCallFilter, enableSymbolResolution);
        visitor.visit(cu, null);

        return result;
    }

    private class IndexVisitor extends VoidVisitorAdapter<Void> {
        private final CodeFileAnalysisResult result;
        private final MethodCallFilter methodCallFilter;
        private final boolean enableSymbolResolution;
        private final Map<String, CodeSymbol> symbolMap = new HashMap<>();
        private CodeSymbol currentTypeSymbol;
        private CodeSymbol currentMethodSymbol;

        public IndexVisitor(CodeFileAnalysisResult result, MethodCallFilter methodCallFilter, boolean enableSymbolResolution) {
            this.result = result;
            this.methodCallFilter = methodCallFilter;
            this.enableSymbolResolution = enableSymbolResolution;
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration decl, Void arg) {
            CodeSymbol symbol = createSymbolFromTypeDecl(decl, decl.isInterface() ? CodeSymbolKind.INTERFACE : CodeSymbolKind.CLASS);

            // 处理继承
            if (decl.getExtendedTypes().isNonEmpty()) {
                for (ClassOrInterfaceType superType : decl.getExtendedTypes()) {
                    CodeInheritance inheritance = new CodeInheritance();
                    inheritance.setId(UUID.randomUUID().toString());
                    inheritance.setSubTypeId(symbol.getId());
                    inheritance.setSuperTypeQualifiedName(superType.getNameAsString());
                    inheritance.setRelationType(CodeRelationType.EXTENDS);
                    result.getInheritances().add(inheritance);
                }
            }

            // 处理实现
            for (ClassOrInterfaceType implType : decl.getImplementedTypes()) {
                CodeInheritance inheritance = new CodeInheritance();
                inheritance.setId(UUID.randomUUID().toString());
                inheritance.setSubTypeId(symbol.getId());
                inheritance.setSuperTypeQualifiedName(implType.getNameAsString());
                inheritance.setRelationType(CodeRelationType.IMPLEMENTS);
                result.getInheritances().add(inheritance);
            }

            // 处理注解
            processAnnotations(decl, symbol);

            boolean isSealed = decl.getModifiers().stream()
                    .anyMatch(m -> m.getKeyword() == Modifier.Keyword.SEALED);
            if (isSealed) {
                String permitsList = decl.getPermittedTypes().stream()
                        .map(n -> n.getNameAsString())
                        .reduce((a, b) -> a + "," + b)
                        .orElse("");
                String existingExtData = symbol.getExtData();
                String permitsExtData = permitsList.isEmpty()
                        ? "{\"sealed\":true}"
                        : "{\"sealed\":true,\"permits\":\"" + permitsList + "\"}";
                symbol.setExtData(existingExtData != null
                        ? existingExtData.substring(0, existingExtData.length() - 1) + ",\"sealed\":true,\"permits\":\"" + permitsList + "\"}"
                        : permitsExtData);
            }

            CodeSymbol parentType = currentTypeSymbol;
            currentTypeSymbol = symbol;
            super.visit(decl, arg);
            currentTypeSymbol = parentType;
        }

        @Override
        public void visit(EnumDeclaration decl, Void arg) {
            CodeSymbol symbol = createSymbolFromTypeDecl(decl, CodeSymbolKind.ENUM);

            // 处理实现的接口
            for (ClassOrInterfaceType implType : decl.getImplementedTypes()) {
                CodeInheritance inheritance = new CodeInheritance();
                inheritance.setId(UUID.randomUUID().toString());
                inheritance.setSubTypeId(symbol.getId());
                inheritance.setSuperTypeQualifiedName(implType.getNameAsString());
                inheritance.setRelationType(CodeRelationType.IMPLEMENTS);
                result.getInheritances().add(inheritance);
            }

            // 处理注解
            processAnnotations(decl, symbol);

            CodeSymbol parentType = currentTypeSymbol;
            currentTypeSymbol = symbol;
            super.visit(decl, arg);
            currentTypeSymbol = parentType;
        }

        @Override
        public void visit(AnnotationDeclaration decl, Void arg) {
            CodeSymbol symbol = createSymbolFromTypeDecl(decl, CodeSymbolKind.ANNOTATION_TYPE);

            // 处理注解
            processAnnotations(decl, symbol);

            CodeSymbol parentType = currentTypeSymbol;
            currentTypeSymbol = symbol;
            super.visit(decl, arg);
            currentTypeSymbol = parentType;
        }

        @Override
        public void visit(RecordDeclaration decl, Void arg) {
            CodeSymbol symbol = new CodeSymbol();
            symbol.setId(UUID.randomUUID().toString());
            symbol.setName(decl.getNameAsString());
            if (currentTypeSymbol != null) {
                symbol.setDeclaringSymbolId(currentTypeSymbol.getId());
                symbol.setQualifiedName(currentTypeSymbol.getQualifiedName() + "." + decl.getNameAsString());
            } else {
                symbol.setQualifiedName(decl.getNameAsString());
            }
            symbol.setKind(CodeSymbolKind.CLASS);
            symbol.setAccessModifier(getAccessModifier(decl.getModifiers()));
            symbol.setAbstractFlag(false);
            symbol.setFinalFlag(decl.isFinal());
            result.getSymbols().add(symbol);

            CodeSymbol parentType = currentTypeSymbol;
            currentTypeSymbol = symbol;

            // Record implements listed types
            for (ClassOrInterfaceType implType : decl.getImplementedTypes()) {
                CodeInheritance inh = new CodeInheritance();
                inh.setId(UUID.randomUUID().toString());
                inh.setSubTypeId(symbol.getId());
                inh.setSuperTypeQualifiedName(implType.getNameAsString());
                inh.setRelationType(CodeRelationType.IMPLEMENTS);
                result.getInheritances().add(inh);
            }

            processAnnotations(decl, symbol);

            super.visit(decl, arg);
            currentTypeSymbol = parentType;
        }

        @Override
        public void visit(MethodDeclaration decl, Void arg) {
            CodeSymbol symbol = new CodeSymbol();
            symbol.setId(UUID.randomUUID().toString());
            symbol.setKind(CodeSymbolKind.METHOD);
            symbol.setName(decl.getNameAsString());
            symbol.setAccessModifier(getAccessModifier(decl.getModifiers()));
            symbol.setDeprecated(hasDeprecatedAnnotation(decl));
            symbol.setDocumentation(getJavadoc(decl.getComment()));

            // 位置信息
            decl.getRange().ifPresent(range -> {
                symbol.setLine(range.begin.line);
                symbol.setColumn(range.begin.column);
                symbol.setEndLine(range.end.line);
                symbol.setEndColumn(range.end.column);
            });

            // 方法特有信息
            symbol.setSignature(buildMethodSignature(decl));
            String methodReturnType = decl.getType().asString();
            symbol.setReturnType(methodReturnType);
            symbol.setRawReturnType(extractRawType(methodReturnType));
            symbol.setStaticFlag(decl.isStatic());
            buildExtData(decl.isSynchronized(), decl.isNative(), false, false, symbol);
            symbol.setAbstractFlag(decl.isAbstract());
            symbol.setFinalFlag(decl.isFinal());

            // 设置所属类型
            if (currentTypeSymbol != null) {
                symbol.setDeclaringSymbolId(currentTypeSymbol.getId());
                symbol.setQualifiedName(currentTypeSymbol.getQualifiedName() + "." + decl.getNameAsString());
            } else {
                symbol.setQualifiedName(decl.getNameAsString());
            }

            // 处理注解
            processAnnotations(decl, symbol);

            result.getSymbols().add(symbol);
            symbolMap.put(symbol.getQualifiedName(), symbol);

            CodeSymbol parentMethod = currentMethodSymbol;
            currentMethodSymbol = symbol;
            super.visit(decl, arg);
            currentMethodSymbol = parentMethod;
        }

        @Override
        public void visit(ConstructorDeclaration decl, Void arg) {
            CodeSymbol symbol = new CodeSymbol();
            symbol.setId(UUID.randomUUID().toString());
            symbol.setKind(CodeSymbolKind.CONSTRUCTOR);
            symbol.setName(decl.getNameAsString());
            symbol.setAccessModifier(getAccessModifier(decl.getModifiers()));
            symbol.setDeprecated(hasDeprecatedAnnotation(decl));
            symbol.setDocumentation(getJavadoc(decl.getComment()));

            // 位置信息
            decl.getRange().ifPresent(range -> {
                symbol.setLine(range.begin.line);
                symbol.setColumn(range.begin.column);
                symbol.setEndLine(range.end.line);
                symbol.setEndColumn(range.end.column);
            });

            // 构造器签名
            symbol.setSignature(buildConstructorSignature(decl));
            String ctorReturnType = decl.getNameAsString();
            symbol.setReturnType(ctorReturnType);
            symbol.setRawReturnType(extractRawType(ctorReturnType));

            // 设置所属类型
            if (currentTypeSymbol != null) {
                symbol.setDeclaringSymbolId(currentTypeSymbol.getId());
                symbol.setQualifiedName(currentTypeSymbol.getQualifiedName() + "." + decl.getNameAsString());
            } else {
                symbol.setQualifiedName(decl.getNameAsString());
            }

            // 处理注解
            processAnnotations(decl, symbol);

            result.getSymbols().add(symbol);
            symbolMap.put(symbol.getQualifiedName(), symbol);

            CodeSymbol parentMethod = currentMethodSymbol;
            currentMethodSymbol = symbol;
            super.visit(decl, arg);
            currentMethodSymbol = parentMethod;
        }

        @Override
        public void visit(FieldDeclaration decl, Void arg) {
            for (VariableDeclarator var : decl.getVariables()) {
                CodeSymbol symbol = new CodeSymbol();
                symbol.setId(UUID.randomUUID().toString());
                symbol.setKind(CodeSymbolKind.FIELD);
                symbol.setName(var.getNameAsString());
                symbol.setAccessModifier(getAccessModifier(decl.getModifiers()));
                symbol.setDeprecated(hasDeprecatedAnnotation(decl));
                symbol.setDocumentation(getJavadoc(decl.getComment()));

                // 位置信息
                var.getRange().ifPresent(range -> {
                    symbol.setLine(range.begin.line);
                    symbol.setColumn(range.begin.column);
                    symbol.setEndLine(range.end.line);
                    symbol.setEndColumn(range.end.column);
                });

                // 字段特有信息
                String fieldTypeName = decl.getElementType().asString();
                symbol.setFieldType(fieldTypeName);
                symbol.setRawFieldType(extractRawType(fieldTypeName));
                symbol.setStaticFlag(decl.isStatic());
                symbol.setFinalFlag(decl.isFinal());
                buildExtData(false, false, decl.isVolatile(), decl.isTransient(), symbol);

                // 设置所属类型
                if (currentTypeSymbol != null) {
                    symbol.setDeclaringSymbolId(currentTypeSymbol.getId());
                    symbol.setQualifiedName(currentTypeSymbol.getQualifiedName() + "." + var.getNameAsString());
                } else {
                    symbol.setQualifiedName(var.getNameAsString());
                }

                // 处理注解
                processAnnotations(decl, symbol);

                result.getSymbols().add(symbol);
                symbolMap.put(symbol.getQualifiedName(), symbol);
            }
            super.visit(decl, arg);
        }

        @Override
        public void visit(EnumConstantDeclaration decl, Void arg) {
            CodeSymbol symbol = new CodeSymbol();
            symbol.setId(UUID.randomUUID().toString());
            symbol.setKind(CodeSymbolKind.CONSTANT);
            symbol.setName(decl.getNameAsString());
            symbol.setAccessModifier(CodeAccessModifier.PUBLIC);
            symbol.setDeprecated(hasDeprecatedAnnotation(decl));
            symbol.setDocumentation(getJavadoc(decl.getComment()));

            // 位置信息
            decl.getRange().ifPresent(range -> {
                symbol.setLine(range.begin.line);
                symbol.setColumn(range.begin.column);
                symbol.setEndLine(range.end.line);
                symbol.setEndColumn(range.end.column);
            });

            // 设置所属类型
            if (currentTypeSymbol != null) {
                symbol.setDeclaringSymbolId(currentTypeSymbol.getId());
                symbol.setQualifiedName(currentTypeSymbol.getQualifiedName() + "." + decl.getNameAsString());
            } else {
                symbol.setQualifiedName(decl.getNameAsString());
            }

            // 处理注解
            processAnnotations(decl, symbol);

            result.getSymbols().add(symbol);
            symbolMap.put(symbol.getQualifiedName(), symbol);

            super.visit(decl, arg);
        }

        @Override
        public void visit(AnnotationMemberDeclaration decl, Void arg) {
            CodeSymbol symbol = new CodeSymbol();
            symbol.setId(UUID.randomUUID().toString());
            symbol.setKind(CodeSymbolKind.FIELD); // 注解成员作为字段处理
            symbol.setName(decl.getNameAsString());
            symbol.setAccessModifier(CodeAccessModifier.PUBLIC);
            symbol.setDeprecated(hasDeprecatedAnnotation(decl));
            symbol.setDocumentation(getJavadoc(decl.getComment()));

            // 位置信息
            decl.getRange().ifPresent(range -> {
                symbol.setLine(range.begin.line);
                symbol.setColumn(range.begin.column);
                symbol.setEndLine(range.end.line);
                symbol.setEndColumn(range.end.column);
            });

            String enumFieldType = decl.getType().asString();
            symbol.setFieldType(enumFieldType);
            symbol.setRawFieldType(extractRawType(enumFieldType));

            // 设置所属类型
            if (currentTypeSymbol != null) {
                symbol.setDeclaringSymbolId(currentTypeSymbol.getId());
                symbol.setQualifiedName(currentTypeSymbol.getQualifiedName() + "." + decl.getNameAsString());
            } else {
                symbol.setQualifiedName(decl.getNameAsString());
            }

            result.getSymbols().add(symbol);
            symbolMap.put(symbol.getQualifiedName(), symbol);

            super.visit(decl, arg);
        }

        @Override
        public void visit(MethodCallExpr expr, Void arg) {
            // 提取方法调用信息
            CodeMethodCall call = new CodeMethodCall();
            call.setId(UUID.randomUUID().toString());
            call.setMethodName(expr.getNameAsString());

            // 位置信息
            expr.getRange().ifPresent(range -> {
                call.setLine(range.begin.line);
                call.setColumn(range.begin.column);
            });

            // 设置调用方（当前所在的方法）
            if (currentMethodSymbol != null) {
                call.setCallerId(currentMethodSymbol.getId());
            }

            // 提取调用上下文（scope）
            expr.getScope().ifPresent(scope -> {
                call.setContext(scope.toString());
            });

            // ========== 符号解析：获取被调用方法的全限定名 ==========
            if (enableSymbolResolution) {
                try {
                    ResolvedMethodDeclaration resolved = expr.resolve();

                    // 获取被调用方法的全限定名
                    String qualifiedName = resolved.getQualifiedName();
                    call.setCalleeQualifiedName(qualifiedName);

                    // 获取精确的参数类型
                    if (resolved.getNumberOfParams() > 0) {
                        String paramTypes = java.util.stream.IntStream.range(0, resolved.getNumberOfParams())
                                .mapToObj(i -> resolved.getParam(i).getType().describe())
                                .collect(java.util.stream.Collectors.joining(", "));
                        call.setArgumentTypes(paramTypes);
                    }

                    // 获取返回类型
                    call.setCallType(resolved.getReturnType().describe());

                } catch (Exception e) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Failed to resolve method call: {} at {}",
                                expr.getNameAsString(),
                                expr.getRange().map(r -> r.begin.line + ":" + r.begin.column).orElse("unknown"),
                                e);
                    }

                    // 降级处理：提取参数表达式作为参数类型
                    if (!expr.getArguments().isEmpty()) {
                        String args = expr.getArguments().stream()
                                .map(a -> a.toString())
                                .reduce((a, b) -> a + ", " + b)
                                .orElse("");
                        call.setArgumentTypes(args);
                    }
                }
            } else {
                // 未启用符号解析
                if (!expr.getArguments().isEmpty()) {
                    String args = expr.getArguments().stream()
                            .map(a -> a.toString())
                            .reduce((a, b) -> a + ", " + b)
                            .orElse("");
                    call.setArgumentTypes(args);
                }
            }

            // 应用过滤器，只有通过过滤的调用才记录
            if (methodCallFilter == null || methodCallFilter.test(call)) {
                result.getCalls().add(call);
            }

            super.visit(expr, arg);
        }

        /**
         * 从类型声明创建符号信息
         */
        private CodeSymbol createSymbolFromTypeDecl(TypeDeclaration<?> decl, CodeSymbolKind kind) {
            CodeSymbol symbol = new CodeSymbol();
            symbol.setId(UUID.randomUUID().toString());
            symbol.setKind(kind);
            symbol.setName(decl.getNameAsString());
            symbol.setAccessModifier(getAccessModifier(decl.getModifiers()));
            symbol.setDeprecated(hasDeprecatedAnnotation(decl));
            symbol.setDocumentation(getJavadoc(decl.getComment()));

            // 位置信息
            decl.getRange().ifPresent(range -> {
                symbol.setLine(range.begin.line);
                symbol.setColumn(range.begin.column);
                symbol.setEndLine(range.end.line);
                symbol.setEndColumn(range.end.column);
            });

            // 构建全限定名
            String qualifiedName = decl.getFullyQualifiedName().orElse(decl.getNameAsString());
            symbol.setQualifiedName(qualifiedName);

            // 类/接口特有属性
            if (decl instanceof ClassOrInterfaceDeclaration) {
                ClassOrInterfaceDeclaration cid = (ClassOrInterfaceDeclaration) decl;
                symbol.setAbstractFlag(cid.isAbstract());
                symbol.setFinalFlag(cid.isFinal());

                // 父类（仅对类）
                if (!cid.isInterface() && cid.getExtendedTypes().isNonEmpty()) {
                    symbol.setSuperClassName(cid.getExtendedTypes().get(0).getNameAsString());
                }
            }

            // 设置父符号（嵌套类）
            if (currentTypeSymbol != null) {
                symbol.setParentId(currentTypeSymbol.getId());
            }

            result.getSymbols().add(symbol);
            symbolMap.put(qualifiedName, symbol);

            return symbol;
        }

        /**
         * 处理注解
         */
        private void processAnnotations(NodeWithAnnotations<?> node, CodeSymbol annotatedSymbol) {
            for (AnnotationExpr annotation : node.getAnnotations()) {
                CodeAnnotationUsage usage = new CodeAnnotationUsage();
                usage.setId(UUID.randomUUID().toString());
                usage.setAnnotationTypeQualifiedName(annotation.getNameAsString());
                usage.setAnnotatedSymbolId(annotatedSymbol.getId());

                annotation.getRange().ifPresent(range -> {
                    usage.setLine(range.begin.line);
                    usage.setColumn(range.begin.column);
                });

                // 提取注解属性
                if (annotation instanceof NormalAnnotationExpr) {
                    NormalAnnotationExpr nae = (NormalAnnotationExpr) annotation;
                    Map<String, Object> attrs = new HashMap<>();
                    nae.getPairs().forEach(pair -> attrs.put(pair.getNameAsString(), pair.getValue().toString()));
                    usage.setAttributes(toJson(attrs));
                } else if (annotation instanceof SingleMemberAnnotationExpr) {
                    SingleMemberAnnotationExpr smae = (SingleMemberAnnotationExpr) annotation;
                    usage.setAttributes(toJson(Map.of("value", smae.getMemberValue().toString())));
                }

                result.getAnnotationUsages().add(usage);
            }
        }

        /**
         * 获取访问修饰符
         */
        private CodeAccessModifier getAccessModifier(List<com.github.javaparser.ast.Modifier> modifiers) {
            for (com.github.javaparser.ast.Modifier mod : modifiers) {
                switch (mod.getKeyword()) {
                    case PUBLIC:
                        return CodeAccessModifier.PUBLIC;
                    case PROTECTED:
                        return CodeAccessModifier.PROTECTED;
                    case PRIVATE:
                        return CodeAccessModifier.PRIVATE;
                    default:
                        break;
                }
            }
            return CodeAccessModifier.PACKAGE_PRIVATE;
        }

        /**
         * 检查是否有@Deprecated注解
         */
        private boolean hasDeprecatedAnnotation(NodeWithAnnotations<?> node) {
            return node.getAnnotations().stream()
                    .anyMatch(a -> a.getNameAsString().equals("Deprecated")
                            || a.getNameAsString().equals("java.lang.Deprecated"));
        }

        /**
         * 获取Javadoc注释
         */
        private String getJavadoc(Optional<com.github.javaparser.ast.comments.Comment> comment) {
            return comment.filter(c -> c instanceof JavadocComment)
                    .map(c -> c.getContent())
                    .orElse(null);
        }

        /**
         * 构建方法签名
         */
        private String buildMethodSignature(MethodDeclaration decl) {
            StringBuilder sb = new StringBuilder();
            sb.append(decl.getNameAsString()).append("(");
            List<com.github.javaparser.ast.body.Parameter> params = decl.getParameters();
            for (int i = 0; i < params.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(params.get(i).getType().asString());
                if (params.get(i).isVarArgs()) {
                    sb.append("...");
                }
            }
            sb.append(")");
            return sb.toString();
        }

        /**
         * 构建构造器签名
         */
        private String buildConstructorSignature(ConstructorDeclaration decl) {
            StringBuilder sb = new StringBuilder();
            sb.append(decl.getNameAsString()).append("(");
            List<com.github.javaparser.ast.body.Parameter> params = decl.getParameters();
            for (int i = 0; i < params.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(params.get(i).getType().asString());
                if (params.get(i).isVarArgs()) {
                    sb.append("...");
                }
            }
            sb.append(")");
            return sb.toString();
        }

        /**
         * 将Java特定标志存储到extData
         */
        private void buildExtData(boolean synchronizedFlag, boolean nativeFlag, boolean volatileFlag, boolean transientFlag, CodeSymbol symbol) {
            if (synchronizedFlag || nativeFlag || volatileFlag || transientFlag) {
                StringBuilder json = new StringBuilder("{");
                boolean first = true;
                if (synchronizedFlag) {
                    if (!first) json.append(",");
                    json.append("\"synchronized\":true");
                    first = false;
                }
                if (nativeFlag) {
                    if (!first) json.append(",");
                    json.append("\"native\":true");
                    first = false;
                }
                if (volatileFlag) {
                    if (!first) json.append(",");
                    json.append("\"volatile\":true");
                    first = false;
                }
                if (transientFlag) {
                    if (!first) json.append(",");
                    json.append("\"transient\":true");
                    first = false;
                }
                json.append("}");
                symbol.setExtData(json.toString());
            }
        }

        /**
         * 简单的JSON转换
         */
        private String toJson(Map<String, Object> map) {
            return JsonTool.stringify(map);
        }
    }

    private static String extractRawType(String typeText) {
        if (typeText == null) return null;
        int idx = typeText.indexOf('<');
        return idx >= 0 ? typeText.substring(0, idx) : typeText;
    }
}
