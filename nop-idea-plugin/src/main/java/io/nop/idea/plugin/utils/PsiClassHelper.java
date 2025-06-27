package io.nop.idea.plugin.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.JavaRecursiveElementVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiConstantEvaluationHelper;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiReturnStatement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.util.EmptyQuery;
import com.intellij.util.Query;
import io.nop.commons.util.StringHelper;
import io.nop.xlang.xdef.IStdDomainHandler;
import org.jetbrains.annotations.NotNull;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-26
 */
public class PsiClassHelper {

    /**
     * 查找项目中 {@link io.nop.xlang.xdef.IStdDomainHandler IStdDomainHandler} 的实现类，
     * 并以其{@link io.nop.xlang.xdef.IStdDomainHandler#getName() 名字}为 Map Key
     */
    public static Map<String, List<PsiClass>> findStdDomainHandlers(Project project) {
        Map<String, List<PsiClass>> map = new HashMap<>();

        Query<PsiClass> query = findInheritors(project, IStdDomainHandler.class.getName());
        query.filtering((cls) -> !cls.isInterface()
                                 && !cls.isEnum()
                                 && !cls.isAnnotationType()
                                 && !cls.hasModifierProperty(PsiModifier.ABSTRACT) //
             ) //
             .forEach((cls) -> {
                 Object name = getMethodReturnConstantValue(cls, "getName");

                 if (name != null) {
                     map.computeIfAbsent(name.toString(), (k) -> new ArrayList<>()).add(cls);
                 }
             });

        return map;
    }

    public static PsiClass findClass(Project project, String clsName) {
        return JavaPsiFacade.getInstance(project).findClass(clsName, GlobalSearchScope.allScope(project));
    }

    /** 查找指定类的继承类 */
    public static @NotNull Query<PsiClass> findInheritors(Project project, String clsName) {
        PsiClass cls = findClass(project, clsName);
        if (cls == null) {
            return EmptyQuery.getEmptyQuery();
        }

        return ClassInheritorsSearch.search(cls, true);
    }

    /** 获取指定方法返回的常量值 */
    public static Object getMethodReturnConstantValue(PsiClass cls, String methodName) {
        PsiMethod[] methods = cls.findMethodsByName(methodName, true);
        if (methods.length == 0) {
            return null;
        }

        PsiMethod method = methods[0];
        ReturnStatementAnalyzer analyzer = new ReturnStatementAnalyzer();
        method.accept(analyzer);

        return analyzer.getReturnValue();
    }

    /**
     * 获取 {@link PsiField} 上指定注解的 <code>value</code> 值
     */
    public static Object getAnnotationValue(PsiField field, String annName) {
        return getAnnotationValue(field, annName, null);
    }

    /**
     * 获取 {@link PsiField} 上指定注解的属性值
     *
     * @param annAttrName
     *         若为 <code>null</code>，则取注解的 <code>value</code> 值
     */
    public static Object getAnnotationValue(PsiField field, String annName, String annAttrName) {
        PsiAnnotation ann = field.getAnnotation(annName);
        if (ann == null) {
            // Note: 单元测试中只能根据 simple class name 得到
            ann = field.getAnnotation(StringHelper.simpleClassName(annName));
        }

        if (ann == null) {
            return null;
        }

        for (PsiNameValuePair pair : ann.getParameterList().getAttributes()) {
            String name = pair.getName();

            if (Objects.equals(name, annAttrName)) {
                return computeConstantExpression(pair.getValue());
            }
        }
        return null;
    }

    /**
     * 计算常量表达式：
     * <pre>
     * - 1 + 2 * 3
     * - "IDEA" + " Plugin"
     * - (2 + 3) * (4 - 1)
     * - (double) 10 / 3
     * - 5 > 3 ? "yes" : "no"
     * </pre>
     */
    public static Object computeConstantExpression(Project project, String expression) {
        PsiExpression expr = PsiElementFactory.getInstance(project).createExpressionFromText(expression, null);

        return computeConstantExpression(expr);
    }

    /** 计算常量表达式 */
    public static Object computeConstantExpression(PsiElement expression) {
        if (expression == null) {
            return null;
        }

        PsiConstantEvaluationHelper helper = JavaPsiFacade.getInstance(expression.getProject())
                                                          .getConstantEvaluationHelper();
        return helper.computeConstantExpression(expression);
    }

    private static class ReturnStatementAnalyzer extends JavaRecursiveElementVisitor {
        private PsiExpression returnExpr;

        @Override
        public void visitReturnStatement(@NotNull PsiReturnStatement statement) {
            // 只捕获第一个返回语句
            if (returnExpr == null) {
                returnExpr = statement.getReturnValue();
            }
        }

        public Object getReturnValue() {
            return evaluateExpression(returnExpr);
        }

        private Object evaluateExpression(PsiExpression expression) {
            // 处理字面量表达式
            if (expression instanceof PsiLiteralExpression) {
                return ((PsiLiteralExpression) expression).getValue();
            }
            // 处理引用表达式（字段引用）
            else if (expression instanceof PsiReferenceExpression) {
                PsiElement resolved = ((PsiReferenceExpression) expression).resolve();

                // 解析到常量字段（包含接口上的常量）
                if (resolved instanceof PsiField field //
                    && field.hasModifierProperty(PsiModifier.STATIC) //
                    && field.hasModifierProperty(PsiModifier.FINAL) //
                ) {
                    // 递归解析字段的初始化表达式
                    PsiExpression initializer = field.getInitializer();

                    return evaluateExpression(initializer);
                }
            }

            return computeConstantExpression(expression);
        }
    }
}
