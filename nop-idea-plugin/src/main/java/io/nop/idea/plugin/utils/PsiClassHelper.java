package io.nop.idea.plugin.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.intellij.openapi.project.Project;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.JavaRecursiveElementVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiConstantEvaluationHelper;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiReturnStatement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.PsiWildcardType;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceSet;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.PsiUtil;
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
    private static final JavaClassReferenceProvider javaClassRefProvider = new JavaClassReferenceProvider();

    private static final Map<PsiPrimitiveType, String> primitiveTypeWrapper = Map.of(PsiTypes.byteType(),
                                                                                     "java.lang.Byte",
                                                                                     PsiTypes.shortType(),
                                                                                     "java.lang.Short",
                                                                                     PsiTypes.intType(),
                                                                                     "java.lang.Integer",
                                                                                     PsiTypes.longType(),
                                                                                     "java.lang.Long",
                                                                                     PsiTypes.floatType(),
                                                                                     "java.lang.Float",
                                                                                     PsiTypes.doubleType(),
                                                                                     "java.lang.Double",
                                                                                     PsiTypes.charType(),
                                                                                     "java.lang.Character",
                                                                                     PsiTypes.booleanType(),
                                                                                     "java.lang.Boolean",
                                                                                     PsiTypes.voidType(),
                                                                                     "java.lang.Void");

    static {
        // 支持解析包名：JavaClassReference#advancedResolveInner
        javaClassRefProvider.setOption(JavaClassReferenceProvider.ADVANCED_RESOLVE, true);
    }

    public static PsiReference @NotNull [] createJavaClassReferences(
            String qualifiedName, PsiElement element, int startInElement
    ) {
        JavaClassReferenceSet refSet = new JavaClassReferenceSet(qualifiedName,
                                                                 element,
                                                                 startInElement,
                                                                 false,
                                                                 javaClassRefProvider);

        return refSet.getReferences();
    }

    /** 得到 {@link PsiType} 对应的 {@link PsiClass} */
    public static PsiClass getTypeClass(Project project, PsiType type) {
        if (type == null) {
            return null;
        }

        // 处理通配符泛型
        if (type instanceof PsiWildcardType t) {
            PsiType bound = t.getBound();

            return bound != null
                   ? getTypeClass(project, bound)
                   : PsiUtil.resolveClassInType(PsiType.getJavaLangObject(t.getManager(), t.getResolveScope()));
        }
        // 处理类型参数
        else if (type instanceof PsiTypeParameter t) {
            PsiClassType[] bounds = t.getExtendsListTypes();

            if (bounds.length > 0) {
                return getTypeClass(project, bounds[0]);
            }
            return PsiUtil.resolveClassInType(PsiType.getJavaLangObject(t.getManager(), t.getResolveScope()));
        }
        // 处理原始类型
        else if (type instanceof PsiPrimitiveType t) {
            String wrapperName = primitiveTypeWrapper.get(t);

            if (wrapperName != null) {
                return JavaPsiFacade.getInstance(project).findClass(wrapperName, GlobalSearchScope.allScope(project));
            }
            return null;
        }
        // 处理数组类型
        else if (type instanceof PsiArrayType t) {
            return getTypeClass(project, t.getComponentType());
        }
        // 处理类类型（包括泛型）
        else if (type instanceof PsiClassType t) {
            PsiClass clazz = t.resolve();
            // 泛型参数
            PsiType[] parameters = t.getParameters();

            if (clazz != null && parameters.length > 0) {
                // List<String> -> 返回 String.class
                if (CommonClassNames.JAVA_UTIL_LIST.equals(clazz.getQualifiedName())) {
                    return getTypeClass(project, parameters[0]);
                }

                // 自定义泛型类
                PsiTypeParameter[] typeParams = clazz.getTypeParameters();
                if (typeParams.length > 0) {
                    // 查找实际使用的类型参数
                    for (int i = 0; i < typeParams.length; i++) {
                        if (i < parameters.length) {
                            PsiClass resolved = getTypeClass(project, parameters[i]);

                            if (resolved != null) {
                                return resolved;
                            }
                        }
                    }
                }
            }
            return clazz;
        }

        return null;
    }

    /**
     * 查找项目中 {@link io.nop.xlang.xdef.IStdDomainHandler IStdDomainHandler} 的实现类，
     * 并以其{@link io.nop.xlang.xdef.IStdDomainHandler#getName() 名字}为 Map Key
     *
     * @deprecated 只能用于源码分析，不能从 class 字节码中得到方法的返回结果
     */
    @Deprecated
    public static Map<String, List<PsiClass>> findStdDomainHandlers(Project project) {
        Map<String, List<PsiClass>> map = new HashMap<>();

        Query<PsiClass> query = findInheritors(project, IStdDomainHandler.class.getName());
        query.filtering((clazz) -> !clazz.isInterface()
                                   && !clazz.isEnum()
                                   && !clazz.isAnnotationType()
                                   && !clazz.hasModifierProperty(PsiModifier.ABSTRACT) //
             ) //
             .forEach((clazz) -> {
                 Object name = getMethodReturnConstantValue(clazz, "getName");

                 if (name != null) {
                     map.computeIfAbsent(name.toString(), (k) -> new ArrayList<>()).add(clazz);
                 }
             });

        return map;
    }

    public static PsiClass findClass(Project project, String className) {
        return JavaPsiFacade.getInstance(project).findClass(className, GlobalSearchScope.allScope(project));
    }

    public static PsiPackage findPackage(Project project, String pkgName) {
        return JavaPsiFacade.getInstance(project).findPackage(pkgName);
    }

    /** 查找指定类的继承类 */
    public static @NotNull Query<PsiClass> findInheritors(Project project, String className) {
        PsiClass clazz = findClass(project, className);
        if (clazz == null) {
            return EmptyQuery.getEmptyQuery();
        }

        return ClassInheritorsSearch.search(clazz, true);
    }

    /** 获取指定方法返回的常量值 */
    public static Object getMethodReturnConstantValue(PsiClass clazz, String methodName) {
        PsiMethod[] methods = clazz.findMethodsByName(methodName, true);
        if (methods.length == 0) {
            return null;
        }

        PsiMethod method = methods[0];
        ReturnStatementAnalyzer analyzer = new ReturnStatementAnalyzer();
        method.getBody().accept(analyzer);

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

                return computeConstantExpression(initializer);
            }
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
            return computeConstantExpression(returnExpr);
        }
    }
}
