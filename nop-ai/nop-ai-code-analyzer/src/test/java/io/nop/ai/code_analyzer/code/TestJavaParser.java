package io.nop.ai.code_analyzer.code;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.resolution.declarations.ResolvedFieldDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class TestJavaParser {

    @Test
    public void testSolve() {
        String javaCode = "import io.nop.api.core.exceptions.NopException;\n"
                + "public class Test {\n"
                + "    private int memberField;\n"
                + "    private static String staticField;\n"
                + "    \n"
                + "    public void test() {\n"
                + "        memberField = 1;                     // 直接访问成员变量\n"
                + "        this.memberField = 2;                // 通过this访问\n"
                + "        staticField = \"value\";             // 访问静态变量\n"
                + "        Test.staticField = \"newValue\";     // 通过类名访问静态变量\n"
                + "        NopException ex = NopException.adapt(null);\n"
                + "        String cause = ex.getCause().toString(); // 方法调用链\n"
                + "    }\n"
                + "}";

        // 1. 配置 TypeSolver
        CombinedTypeSolver typeSolver = new CombinedTypeSolver(new ReflectionTypeSolver());
        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);
        JavaParser javaParser = new JavaParser(new ParserConfiguration()
                .setSymbolResolver(symbolSolver)
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_11));

        // 2. 解析代码
        CompilationUnit cu = javaParser.parse(javaCode).getResult().orElseThrow(
                () -> new RuntimeException("Failed to parse code")
        );

        // 3. 找到目标方法
        Optional<MethodDeclaration> methodOpt = cu.findFirst(MethodDeclaration.class);
        if (!methodOpt.isPresent()) {
            throw new RuntimeException("No method found");
        }
        MethodDeclaration method = methodOpt.get();

        // 4. 收集所有字段访问信息
        Map<String, String> fieldAccessInfo = analyzeFieldAccess(method);

        // 5. 打印结果
        System.out.println("方法名: " + method.getNameAsString());
        System.out.println("字段访问信息:");
        fieldAccessInfo.forEach((name, type) -> System.out.println("  " + name + " : " + type));
    }

    private static Map<String, String> analyzeFieldAccess(MethodDeclaration method) {
        Map<String, String> fieldAccessInfo = new HashMap<>();

        // 1. 分析直接字段访问（包括 this.field 和 staticField）
        method.findAll(FieldAccessExpr.class).forEach(fieldAccess -> {
            try {
                ResolvedValueDeclaration resolved = fieldAccess.resolve();
                String fieldName = resolved.getName();
                ResolvedType type = resolved.getType();
                fieldAccessInfo.put(fieldName, type.describe());
            } catch (Exception e) {
                fieldAccessInfo.put(fieldAccess.toString(), "无法解析类型");
            }
        });

        // 2. 分析静态字段访问（如 Class.staticField）
        method.findAll(NameExpr.class).forEach(nameExpr -> {
            try {
                ResolvedValueDeclaration resolved = nameExpr.resolve();
                if (resolved.isField() && ((ResolvedFieldDeclaration) resolved).isStatic()) {
                    fieldAccessInfo.put(resolved.getName(), resolved.getType().describe());
                }
            } catch (Exception ignored) {
                // 忽略非字段的 NameExpr
            }
        });

        // 3. 分析方法调用链中的字段访问（如 obj.getField().subField）
        method.findAll(MethodCallExpr.class).forEach(methodCall -> {
            if (methodCall.getScope().isPresent()) {
                Expression scope = methodCall.getScope().get();
                if (scope instanceof FieldAccessExpr) {
                    try {
                        ResolvedValueDeclaration resolved = ((FieldAccessExpr) scope).resolve();
                        fieldAccessInfo.put(resolved.getName(), resolved.getType().describe());
                    } catch (Exception e) {
                        fieldAccessInfo.put(scope.toString(), "无法解析类型");
                    }
                }
            }
        });

        return fieldAccessInfo;
    }
}