package io.nop.javaparser;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.resolution.types.ResolvedType;
import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.javaparser.parse.JavaParserParseResult;
import org.junit.jupiter.api.Test;

@NopTestConfig(localDb = true)
public class TestJavaParseTool extends JunitBaseTestCase {

    @Test
    public void testParse() {
        String source = attachmentText("DailyMenuBizModel.java");
        JavaParserParseResult result = JavaParseTool.instance().parseJavaSource(null, source);
        String code = result.getFormattedSource();
        System.out.println(code);

        CompilationUnit cu = result.getCompilationUnit();
        checkVariables(cu);
        checkMethodCalls(cu);
        checkFieldAccess(cu);
        checkGenericTypes(cu);
    }

    void checkVariables(CompilationUnit cu) {
        cu.findAll(com.github.javaparser.ast.body.VariableDeclarator.class).forEach(variable -> {
            try {
                ResolvedType declaredType = variable.getType().resolve();
                Expression initializer = variable.getInitializer().orElse(null);

                if (initializer != null) {
                    ResolvedType initializerType = initializer.calculateResolvedType();

                    if (!declaredType.isAssignableBy(initializerType)) {
                        System.out.println("类型不匹配: " + variable.getName() +
                                " 声明为 " + declaredType +
                                " 但初始化为 " + initializerType);
                    }
                }
            } catch (Exception e) {
                System.out.println("解析变量类型时出错: " + variable.getName() + " - " + e.getMessage());
            }
        });
    }

    public void checkMethodCalls(CompilationUnit cu) {
        cu.findAll(MethodCallExpr.class).forEach(methodCall -> {
            try {
                // 解析方法调用
                methodCall.resolve();
                // 如果没有抛出异常，说明方法调用是合法的
            } catch (Exception e) {
                System.out.println("非法方法调用: " + methodCall +
                        " 在位置 " + methodCall.getRange().orElse(null) +
                        " - 错误: " + e.getMessage());
            }
        });
    }

    public void checkFieldAccess(CompilationUnit cu) {
        cu.findAll(FieldAccessExpr.class).forEach(fieldAccess -> {
            try {
                fieldAccess.resolve();
            } catch (Exception e) {
                System.out.println("非法字段访问: " + fieldAccess +
                        " 在位置 " + fieldAccess.getRange().orElse(null) +
                        " - 错误: " + e.getMessage());
            }
        });
    }

    public void checkGenericTypes(CompilationUnit cu) {
        cu.findAll(FieldDeclaration.class).forEach(field -> {
            field.getVariables().forEach(variable -> {
                try {
                    ResolvedType fieldType = variable.getType().resolve();
                    if (fieldType.isReferenceType() && fieldType.asReferenceType().getTypeParametersMap().size() > 0) {
                        System.out.println("泛型字段: " + variable.getName() +
                                " 类型: " + fieldType);
                        // 可以添加更详细的泛型类型检查
                    }
                } catch (Exception e) {
                    System.out.println("解析泛型类型时出错: " + e.getMessage());
                }
            });
        });
    }

//    public void checkMethodOverrides(CompilationUnit cu) {
//        cu.findAll(MethodDeclaration.class).forEach(method -> {
//            cu.findAll(MethodDeclaration.class).forEach(m -> {
//                try {
//                    if (m.getAnnotationByName("Override").isPresent()) {
//                        ResolvedMethodDeclaration resolvedMethod = m.resolve();
//                        if (!resolvedMethod.getSignature().isOverriding()) {
//                            System.err.println("方法没有正确重写: " + m.getName());
//                        }
//                    }
//                } catch (Exception e) {
//                    System.err.println("方法解析失败: " + m.getName() + " -> " + e.getMessage());
//                }
//            });
//        });
//    }
}
