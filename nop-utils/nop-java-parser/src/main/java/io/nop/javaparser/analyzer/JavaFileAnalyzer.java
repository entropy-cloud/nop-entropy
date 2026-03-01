package io.nop.javaparser.analyzer;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.AnnotationMemberDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.comments.JavadocComment;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MarkerAnnotationExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.ast.nodeTypes.NodeWithName;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
/**
 * Java文件分析器
 * 使用JavaParser解析Java源代码，提取符号信息、引用关系等
 * 支持符号解析，可获取方法调用的全限定名
 */
public class JavaFileAnalyzer {
    
    private static final Logger LOG = LoggerFactory.getLogger(JavaFileAnalyzer.class);

    /**
     * JavaParser实例，配置了SymbolSolver用于符号解析
     */
    private final JavaParser javaParser;
    
    /**
     * TypeSolver组合器
     */
    private final CombinedTypeSolver typeSolver;

    /**
     * 方法调用过滤器，默认使用默认过滤器（忽略java.lang/java.util的调用）
     */
    private MethodCallFilter methodCallFilter = MethodCallFilter.createDefault();
    
    /**
     * 是否启用符号解析
     */
    private boolean enableSymbolResolution = true;

    /**
     * 默认构造函数，使用ReflectionTypeSolver解析JDK类型
     */
    public JavaFileAnalyzer() {
        this.typeSolver = new CombinedTypeSolver();
        this.typeSolver.add(new ReflectionTypeSolver(true));
        
        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);
        ParserConfiguration config = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17)
                .setSymbolResolver(symbolSolver);
        
        this.javaParser = new JavaParser(config);
    }
    
    /**
     * 设置方法调用过滤器
     *
     * @param filter 过滤器，设为null表示不过滤
     */
    public void setMethodCallFilter(MethodCallFilter filter) {
        this.methodCallFilter = filter;
    }

    /**
     * 获取当前的方法调用过滤器
     */
    public MethodCallFilter getMethodCallFilter() {
        return methodCallFilter;
    }
    
    /**
     * 设置是否启用符号解析
     * @param enable true启用，false禁用
     */
    public void setEnableSymbolResolution(boolean enable) {
        this.enableSymbolResolution = enable;
    }
    
    /**
     * 获取TypeSolver，可用于添加自定义类型解析器
     */
    public CombinedTypeSolver getTypeSolver() {
        return typeSolver;
    }

    /**
     * 解析Java源代码文件
     *
     * @param filePath   文件路径（相对路径）
     * @param sourceCode 源代码内容
     * @return JavaFileAnalysisResult 包含所有解析出的信息
     */
    public JavaFileAnalysisResult analyze(String filePath, String sourceCode) {
        return analyze(SourceLocation.fromPath(filePath), sourceCode);
    }

    /**
     * 解析Java源代码文件
     *
     * @param loc        源码位置
     * @param sourceCode 源代码内容
     * @return JavaFileAnalysisResult 包含所有解析出的信息
     */
    public JavaFileAnalysisResult analyze(SourceLocation loc, String sourceCode) {
        if (StringHelper.isBlank(sourceCode)) {
            return null;
        }

        ParseResult<CompilationUnit> parseResult = javaParser.parse(sourceCode);
        if (!parseResult.isSuccessful() || !parseResult.getResult().isPresent()) {
            return null;
        }

        CompilationUnit cu = parseResult.getResult().get();
        JavaFileAnalysisResult result = new JavaFileAnalysisResult();
        result.setFilePath(loc.getPath());
        result.setSourceCode(sourceCode);
        result.setLineCount(countLines(sourceCode));
        result.setLanguage("JAVA");

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

    /**
     * 统计源代码行数
     */
    private int countLines(String source) {
        if (StringHelper.isBlank(source)) {
            return 0;
        }
        return source.split("\r?\n").length;
    }

    private class IndexVisitor extends VoidVisitorAdapter<Void> {
        private final JavaFileAnalysisResult result;
        private final MethodCallFilter methodCallFilter;
        private final boolean enableSymbolResolution;
        private final Map<String, SymbolInfo> symbolMap = new HashMap<>();
        // 当前所在的类型声明
        private SymbolInfo currentTypeSymbol;
        // 当前所在的方法声明
        private SymbolInfo currentMethodSymbol;

        public IndexVisitor(JavaFileAnalysisResult result, MethodCallFilter methodCallFilter, boolean enableSymbolResolution) {
            this.result = result;
            this.methodCallFilter = methodCallFilter;
            this.enableSymbolResolution = enableSymbolResolution;
        }
        @Override
        public void visit(CompilationUnit cu, Void arg) {
            super.visit(cu, arg);
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration decl, Void arg) {
            SymbolInfo symbol = createSymbolFromTypeDecl(decl, decl.isInterface() ? SymbolKind.INTERFACE : SymbolKind.CLASS);
            
            // 处理继承
            if (decl.getExtendedTypes().isNonEmpty()) {
                for (ClassOrInterfaceType superType : decl.getExtendedTypes()) {
                    InheritanceInfo inheritance = new InheritanceInfo();
                    inheritance.setId(UUID.randomUUID().toString());
                    inheritance.setSubTypeId(symbol.getId());
                    inheritance.setSuperTypeQualifiedName(superType.getNameAsString());
            inheritance.setRelationType(RelationType.EXTENDS);
                    result.addInheritance(inheritance);
                }
            }
            
            // 处理实现
            for (ClassOrInterfaceType implType : decl.getImplementedTypes()) {
                InheritanceInfo inheritance = new InheritanceInfo();
                inheritance.setId(UUID.randomUUID().toString());
                inheritance.setSubTypeId(symbol.getId());
                inheritance.setSuperTypeQualifiedName(implType.getNameAsString());
            inheritance.setRelationType(RelationType.IMPLEMENTS);
                    result.addInheritance(inheritance);
            }

            // 处理注解
            processAnnotations(decl, symbol);

            SymbolInfo parentType = currentTypeSymbol;
            currentTypeSymbol = symbol;
            super.visit(decl, arg);
            currentTypeSymbol = parentType;
        }

        @Override
        public void visit(EnumDeclaration decl, Void arg) {
            SymbolInfo symbol = createSymbolFromTypeDecl(decl, SymbolKind.ENUM);

            // 处理实现的接口
            for (ClassOrInterfaceType implType : decl.getImplementedTypes()) {
                InheritanceInfo inheritance = new InheritanceInfo();
                inheritance.setId(UUID.randomUUID().toString());
                inheritance.setSubTypeId(symbol.getId());
                inheritance.setSuperTypeQualifiedName(implType.getNameAsString());
            inheritance.setRelationType(RelationType.IMPLEMENTS);
                    result.addInheritance(inheritance);
            }

            // 处理注解
            processAnnotations(decl, symbol);

            SymbolInfo parentType = currentTypeSymbol;
            currentTypeSymbol = symbol;
            super.visit(decl, arg);
            currentTypeSymbol = parentType;
        }

        @Override
        public void visit(AnnotationDeclaration decl, Void arg) {
            SymbolInfo symbol = createSymbolFromTypeDecl(decl, SymbolKind.ANNOTATION_TYPE);

            // 处理注解
            processAnnotations(decl, symbol);

            SymbolInfo parentType = currentTypeSymbol;
            currentTypeSymbol = symbol;
            super.visit(decl, arg);
            currentTypeSymbol = parentType;
        }

        @Override
        public void visit(MethodDeclaration decl, Void arg) {
            SymbolInfo symbol = new SymbolInfo();
            symbol.setId(UUID.randomUUID().toString());
            symbol.setKind(SymbolKind.METHOD);
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
            symbol.setReturnType(decl.getType().asString());
            symbol.setStaticFlag(decl.isStatic());
            symbol.setSynchronizedFlag(decl.isSynchronized());
            symbol.setNativeFlag(decl.isNative());
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

            result.addSymbol(symbol);
            symbolMap.put(symbol.getQualifiedName(), symbol);

            SymbolInfo parentMethod = currentMethodSymbol;
            currentMethodSymbol = symbol;
            super.visit(decl, arg);
            currentMethodSymbol = parentMethod;
        }

        @Override
        public void visit(ConstructorDeclaration decl, Void arg) {
            SymbolInfo symbol = new SymbolInfo();
            symbol.setId(UUID.randomUUID().toString());
            symbol.setKind(SymbolKind.CONSTRUCTOR);
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
            symbol.setReturnType(decl.getNameAsString());

            // 设置所属类型
            if (currentTypeSymbol != null) {
                symbol.setDeclaringSymbolId(currentTypeSymbol.getId());
                symbol.setQualifiedName(currentTypeSymbol.getQualifiedName() + "." + decl.getNameAsString());
            } else {
                symbol.setQualifiedName(decl.getNameAsString());
            }

            // 处理注解
            processAnnotations(decl, symbol);

            result.addSymbol(symbol);
            symbolMap.put(symbol.getQualifiedName(), symbol);

            SymbolInfo parentMethod = currentMethodSymbol;
            currentMethodSymbol = symbol;
            super.visit(decl, arg);
            currentMethodSymbol = parentMethod;
        }

        @Override
        public void visit(FieldDeclaration decl, Void arg) {
            for (VariableDeclarator var : decl.getVariables()) {
                SymbolInfo symbol = new SymbolInfo();
                symbol.setId(UUID.randomUUID().toString());
                symbol.setKind(SymbolKind.FIELD);
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
                symbol.setFieldType(decl.getElementType().asString());
                symbol.setStaticFlag(decl.isStatic());
                symbol.setFinalFlag(decl.isFinal());
                symbol.setVolatileFlag(decl.isVolatile());
                symbol.setTransientFlag(decl.isTransient());

                // 设置所属类型
                if (currentTypeSymbol != null) {
                    symbol.setDeclaringSymbolId(currentTypeSymbol.getId());
                    symbol.setQualifiedName(currentTypeSymbol.getQualifiedName() + "." + var.getNameAsString());
                } else {
                    symbol.setQualifiedName(var.getNameAsString());
                }

                // 处理注解
                processAnnotations(decl, symbol);

                result.addSymbol(symbol);
                symbolMap.put(symbol.getQualifiedName(), symbol);
            }
            super.visit(decl, arg);
        }

        @Override
        public void visit(EnumConstantDeclaration decl, Void arg) {
            SymbolInfo symbol = new SymbolInfo();
            symbol.setId(UUID.randomUUID().toString());
            symbol.setKind(SymbolKind.ENUM_CONSTANT);
            symbol.setName(decl.getNameAsString());
            symbol.setAccessModifier(AccessModifier.PUBLIC);
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

            result.addSymbol(symbol);
            symbolMap.put(symbol.getQualifiedName(), symbol);

            super.visit(decl, arg);
        }

        @Override
        public void visit(AnnotationMemberDeclaration decl, Void arg) {
            SymbolInfo symbol = new SymbolInfo();
            symbol.setId(UUID.randomUUID().toString());
            symbol.setKind(SymbolKind.FIELD); // 注解成员作为字段处理
            symbol.setName(decl.getNameAsString());
            symbol.setAccessModifier(AccessModifier.PUBLIC);
            symbol.setDeprecated(hasDeprecatedAnnotation(decl));
            symbol.setDocumentation(getJavadoc(decl.getComment()));

            // 位置信息
            decl.getRange().ifPresent(range -> {
                symbol.setLine(range.begin.line);
                symbol.setColumn(range.begin.column);
                symbol.setEndLine(range.end.line);
                symbol.setEndColumn(range.end.column);
            });

            symbol.setFieldType(decl.getType().asString());

            // 设置所属类型
            if (currentTypeSymbol != null) {
                symbol.setDeclaringSymbolId(currentTypeSymbol.getId());
                symbol.setQualifiedName(currentTypeSymbol.getQualifiedName() + "." + decl.getNameAsString());
            } else {
                symbol.setQualifiedName(decl.getNameAsString());
            }

            result.addSymbol(symbol);
            symbolMap.put(symbol.getQualifiedName(), symbol);

            super.visit(decl, arg);
        }

        @Override
        public void visit(MethodCallExpr expr, Void arg) {
            // 提取方法调用信息
            MethodCall call = new MethodCall();
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
                    
                    // 获取被调用方法的全限定名，例如: "com.example.service.UserService.save"
                    String qualifiedName = resolved.getQualifiedName();
                    call.setCalleeQualifiedName(qualifiedName);
                    
                    // 获取精确的参数类型（不是表达式字符串）
                    if (resolved.getNumberOfParams() > 0) {
                        String paramTypes = java.util.stream.IntStream.range(0, resolved.getNumberOfParams())
                                .mapToObj(i -> resolved.getParam(i).getType().describe())
                                .collect(java.util.stream.Collectors.joining(", "));
                        call.setArgumentTypes(paramTypes);
                    }
                    
                    // 获取返回类型
                    call.setCallType(resolved.getReturnType().describe());
                    
                } catch (Exception e) {
                    // resolve() 可能失败的原因：
                    // 1. 缺少类型依赖（第三方库未注册）
                    // 2. 动态类型（反射调用）
                    // 3. 未注册的项目内类型
                    // 降级处理：保持原有逻辑，通过 context + methodName 推断
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Failed to resolve method call: {} at {} - {}", 
                                expr.getNameAsString(), 
                                expr.getRange().map(r -> r.begin.line + ":" + r.begin.column).orElse("unknown"),
                                e.getMessage());
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
                // 未启用符号解析，使用原有逻辑
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
                result.addCall(call);
            }

            super.visit(expr, arg);
        }

        /**
         * 从类型声明创建符号信息
         */
        private SymbolInfo createSymbolFromTypeDecl(TypeDeclaration<?> decl, SymbolKind kind) {
            SymbolInfo symbol = new SymbolInfo();
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

            result.addSymbol(symbol);
            symbolMap.put(qualifiedName, symbol);

            return symbol;
        }

        /**
         * 处理注解
         */
        private void processAnnotations(NodeWithAnnotations<?> node, SymbolInfo annotatedSymbol) {
            for (AnnotationExpr annotation : node.getAnnotations()) {
                AnnotationUsage usage = new AnnotationUsage();
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

                result.addAnnotationUsage(usage);
            }
        }

        /**
         * 获取访问修饰符
         */
        private AccessModifier getAccessModifier(List<com.github.javaparser.ast.Modifier> modifiers) {
            for (com.github.javaparser.ast.Modifier mod : modifiers) {
                switch (mod.getKeyword()) {
                    case PUBLIC:
                        return AccessModifier.PUBLIC;
                    case PROTECTED:
                        return AccessModifier.PROTECTED;
                    case PRIVATE:
                        return AccessModifier.PRIVATE;
                    default:
                        break;
                }
            }
            return AccessModifier.PACKAGE_PRIVATE;
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
         * 简单的JSON转换
         */
        private String toJson(Map<String, Object> map) {
            if (map == null || map.isEmpty()) {
                return "{}";
            }
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"").append(entry.getKey()).append("\":");
                sb.append("\"").append(entry.getValue().toString().replace("\"", "\\\"")).append("\"");
                first = false;
            }
            sb.append("}");
            return sb.toString();
        }
    }
}
