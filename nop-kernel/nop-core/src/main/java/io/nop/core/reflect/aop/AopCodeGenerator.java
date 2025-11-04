/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.reflect.aop;

import io.nop.commons.type.StdDataType;
import io.nop.commons.util.StringHelper;
import io.nop.core.reflect.IFunctionArgument;
import io.nop.core.reflect.IFunctionModel;
import io.nop.core.reflect.impl.FunctionModel;
import io.nop.core.reflect.impl.MethodModelBuilder;
import io.nop.core.type.utils.JavaGenericTypeHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 生成Janino可编译的AopProxy类
 */
public class AopCodeGenerator {
    static final Logger LOG = LoggerFactory.getLogger(AopCodeGenerator.class);

    private Class<?> baseClass;
    private final StringBuilder buf = new StringBuilder();
    private MethodModelBuilder methodBuilder = new MethodModelBuilder();

    public String build(Class<?> clazz, Class<?>[] annClasses) {
        Set<Class<?>> usedAnnotations = new HashSet<>();
        List<Method> methods = findAopMethods(clazz, annClasses, usedAnnotations);
        if (methods.isEmpty())
            return null;

        List<Class<?>> annList = new ArrayList<>(usedAnnotations);
        annList.sort(Comparator.comparing(Class::getCanonicalName));
        return buildForMethods(clazz, methods, annList);
    }

    public String buildForMethods(Class<?> clazz, List<Method> methods, List<Class<?>> annClasses) {
        this.baseClass = clazz;
        String className = getAopClassName(clazz);
        String pkgName = StringHelper.packageName(className);
        className = StringHelper.simpleClassName(className);

        buf.append("package ").append(pkgName).append(";\n\n");

        buf.append("@io.nop.api.core.annotations.aop.AopProxy({");
        for (int i = 0, n = annClasses.size(); i < n; i++) {
            if (i != 0)
                buf.append(',');
            buf.append(annClasses.get(i).getCanonicalName() + ".class");
        }
        buf.append("})\n");
        buf.append("public class ").append(className).append(" extends ");
        buf.append(normalizeClassName(clazz)).append(" implements io.nop.core.reflect.aop.IAopProxy {\n");
        buf.append("    private io.nop.core.reflect.aop.IMethodInterceptor[] $$interceptors;\n" + "\n"
                + "    @Override\n"
                + "    public void $$aop_interceptors(io.nop.core.reflect.aop.IMethodInterceptor[] interceptors) {\n"
                + "        this.$$interceptors = interceptors;\n" + "    }\n\n");

        addMethodModels(clazz, methods);

        addConstructors(className, clazz);

        addInterceptedMethods(clazz, methods);

        buf.append("}");
        return buf.toString();
    }

    String normalizeClassName(Class<?> clazz) {
        return clazz.getCanonicalName().replace('$', '.');
    }

    void addMethodModels(Class<?> clazz, List<Method> methods) {
        for (int i = 0, n = methods.size(); i < n; i++) {
            Method method = methods.get(i);
            buf.append("    private static io.nop.core.reflect.IFunctionModel $$").append(method.getName()).append("_")
                    .append(i).append(";\n");
        }

        buf.append("\n    static {\n");
        buf.append("        try {\n");

        for (int i = 0, n = methods.size(); i < n; i++) {
            Method method = methods.get(i);
            buf.append("            $$" + method.getName() + "_" + i
                    + " = io.nop.core.reflect.impl.MethodModelBuilder.from(");
            buf.append(normalizeClassName(clazz)).append(".class, ");
            buf.append(normalizeClassName(method.getDeclaringClass())).append(".class.getDeclaredMethod(");
            buf.append("\"").append(method.getName()).append("\"");
            addParamClasses(method);
            buf.append("));\n");
        }

        buf.append("        } catch (Exception e) {\n" + "            e.printStackTrace();\n" + "        }\n");
        buf.append("    }\n\n");
    }

    void addConstructors(String className, Class<?> clazz) {
        Constructor<?>[] constructors = clazz.getConstructors();
        if (constructors.length == 0) {
            return;
        }

        for (Constructor<?> constructor : constructors) {
            if (!Modifier.isPublic(constructor.getModifiers()))
                continue;

            if (constructor.isSynthetic())
                continue;

            buf.append("    public ").append(className).append("(");
            IFunctionModel func = methodBuilder.buildMethodModel(clazz, constructor);
            addParamDecls(func);
            buf.append(")");
            addThrows(constructor);
            buf.append(" {\n");
            buf.append("        super(");
            addCallParams(func);
            buf.append(");\n");
            buf.append("    }\n\n");
        }
    }

    void addInterceptedMethods(Class<?> clazz, List<Method> methods) {
        for (int i = 0, n = methods.size(); i < n; i++) {
            Method method = methods.get(i);
            IFunctionModel javaMethod = methodBuilder.buildMethodModel(clazz, method);
            Class<?> returnType = javaMethod.getReturnClass();
            // 改用JDK compiler之后不再需要default method
            // addDefaultMethod(method, returnType);

            buf.append("    @Override\n" + "    ")
                    .append(Modifier.isProtected(method.getModifiers()) ? "protected" : "public").append(" ")
                    .append(returnType.getCanonicalName()).append(" ").append(method.getName()).append("(");

            addParamDecls(javaMethod);
            buf.append(")");
            addThrows(method);
            buf.append("{\n");

            buf.append(
                    "        if (this.$$interceptors == null || this.$$interceptors.length == 0) {\n" + "            ");
            invokeSuper(javaMethod, method, returnType, false);
            buf.append("        }\n");

            buf.append("\n" + "        io.nop.core.reflect.aop.CallableMethodInvocation $$methodInv = "
                    + "new io.nop.core.reflect.aop.CallableMethodInvocation(this,\n"
                    + "                new java.lang.Object[]{");
            addCallParams(javaMethod);
            buf.append("}, $$").append(method.getName()).append('_').append(i)
//                    .append(", new java.util.concurrent.Callable(){\n" + "            @Override\n"
//                            + "            public Object call() throws Exception {\n" + "                ");
                    .append(",() -> {\n            ");
            invokeSuper(javaMethod, method, returnType, true);
            //invokeDefault(method, returnType);
            // buf.append("            }\n");
            buf.append("        });\n" + "\n"
                            + "        io.nop.core.reflect.aop.AopMethodInvocation $$inv = new io.nop.core.reflect.aop.AopMethodInvocation($$methodInv, this.$$interceptors);\n"
                            + "        try {\n" + "            ")
                    .append(returnType == void.class ? "" : "return (" + getCastType(returnType) + ") ")
                    .append("$$inv.proceed();\n");
            addCatch(method);
            buf.append("        } catch (java.lang.Exception e) {\n"
                    + "            throw io.nop.api.core.exceptions.NopException.adapt(e);\n" + "        }\n");
            buf.append("    }\n\n");
        }
    }

    private String getCastType(Class<?> returnType) {
        if (returnType.isPrimitive()) {
            return StdDataType.fromJavaClass(returnType).getJavaTypeName();
        }
        return returnType.getCanonicalName();
    }

//    void addDefaultMethod(Method method, Class<?> returnType) {
//        // Janino目前不支持MyClass.super.myMethod这种调用方式, 所以需要生成一个辅助函数用于调用父类的原始实现方法
//        buf.append("    private ").append(returnType.getCanonicalName());
//        buf.append(" __default_").append(method.getName()).append("(");
//        addParamDecls(method);
//        buf.append(")");
//        addThrows(method);
//        buf.append("{\n");
//        buf.append("        ");
//        invokeSuper(method, returnType, false);
//        buf.append("    }\n\n");
//    }

    void invokeSuper(IFunctionModel method, Method mtd, Class<?> returnType, boolean inCallable) {
        boolean returnVoid = returnType == void.class;
        if (!returnVoid) {
            buf.append("return ");
            if (isGenericVariable(mtd)) {
                buf.append("(" + returnType.getCanonicalName() + ")");
            }
        }
        buf.append("super.").append(method.getName()).append('(');
        addCallParams(method);
        buf.append(");");
        if (returnVoid) {
            buf.append(inCallable ? " return null;\n" : " return;\n");
        } else {
            buf.append("\n");
        }
    }

//    void invokeDefault(Method method, Class<?> returnType) {
//        boolean returnVoid = returnType == void.class;
//        if (!returnVoid) {
//            buf.append("return ");
//            if (isGenericVariable(method)) {
//                buf.append("(" + returnType.getCanonicalName() + ")");
//            }
//        }
//        buf.append("__default_").append(method.getName()).append('(');
//        addCallParams(method);
//        buf.append(");");
//        if (returnVoid) {
//            buf.append(" return null;\n");
//        }
//    }

    private boolean isGenericVariable(Method method) {
        Type type = method.getGenericReturnType();
        return type instanceof TypeVariable<?>;
    }

    void addCatch(Method method) {
        Class<?>[] types = method.getExceptionTypes();
        if (types.length <= 0)
            return;

        for (Class<?> type : types) {
            buf.append("        } catch (");
            buf.append(type.getCanonicalName()).append(" e){\n");
            buf.append("            throw e;\n");
        }
    }

    void addParamClasses(Method method) {
        Class<?>[] types = method.getParameterTypes();
        for (Class<?> type : types) {
            buf.append(", ").append(type.getCanonicalName()).append(".class");
        }
    }

    void addParamDecls(IFunctionModel method) {
        List<? extends IFunctionArgument> args = method.getArgs();
        for (int i = 0, n = args.size(); i < n; i++) {
            if (i != 0)
                buf.append(',').append(' ');
            // Janino只允许明确标记为final的参数在匿名函数中使用
            buf.append("final ");
            Class<?> type = args.get(i).getRawClass();
            String name = args.get(i).getName();
            buf.append(normalizeClassName(type)).append(" ").append(name);
        }
    }

    void addCallParams(IFunctionModel method) {
        List<? extends IFunctionArgument> args = method.getArgs();
        for (int i = 0, n = args.size(); i < n; i++) {
            if (i != 0)
                buf.append(',').append(' ');
            String name = args.get(i).getName();
            buf.append(name);
        }
    }

    void addThrows(Executable method) {
        Class<?>[] classes = method.getExceptionTypes();
        if (classes.length > 0) {
            buf.append(" throws ");
            for (int i = 0, n = classes.length; i < n; i++) {
                if (i != 0)
                    buf.append(",");
                buf.append(classes[i].getCanonicalName());
            }
        }
    }

    Class<?> resolveType(Type type, Class<?> declClass) {
        if (type instanceof TypeVariable) {
            TypeVariable var = (TypeVariable) type;
            if (declClass != baseClass) {
                TypeVariable[] vars = declClass.getTypeParameters();
                if (vars.length > 0) {
                    for (int i = 0; i < vars.length; i++) {
                        if (vars[i] == var) {
                            // 泛型
                            ParameterizedType pt = (ParameterizedType) JavaGenericTypeHelper.getSupertype(baseClass,
                                    baseClass, declClass);
                            Type realType = pt.getActualTypeArguments()[i];
                            return JavaGenericTypeHelper.getRawType(realType);
                        }
                    }
                }
            }
        }
        return JavaGenericTypeHelper.getRawType(type);
    }

    public static List<Method> findAopMethods(Class<?> clazz, Class<?>[] annClasses, Set<Class<?>> usedAnnotations) {
        Method[] methods = clazz.getMethods();
        List<Method> ret = new ArrayList<>();
        for (Method method : methods) {
            if (method.isSynthetic())
                continue;

            Class<?> annClass = findAnnotation(method, annClasses);
            if (annClass != null) {
                if (Modifier.isFinal(method.getModifiers())) {
                    LOG.warn("nop.ioc.final-method-not-allow-intercepted:method={}", method);
                    continue;
                }
                usedAnnotations.add(annClass);
                ret.add(method);
            }
        }

        // 查找所有protected的method
        for (Method method : getProtectedMethods(clazz)) {
            if (method.isSynthetic())
                continue;
            Class<?> annClass = findAnnotation(method, annClasses);
            if (annClass != null) {
                if (Modifier.isFinal(method.getModifiers())) {
                    LOG.warn("nop.ioc.final-method-not-allow-intercepted:method={}", method);
                    continue;
                }
                usedAnnotations.add(annClass);
                ret.add(method);
            }
        }

        return ret;
    }

    static Collection<Method> getProtectedMethods(Class<?> clazz) {
        Map<String, Method> protectedMethods = new HashMap<>();

        while (clazz != Object.class) {
            Method[] methods = clazz.getDeclaredMethods();
            for (Method method : methods) {
                if (Modifier.isProtected(method.getModifiers())) {
                    String key = buildMethodKey(method);
                    protectedMethods.putIfAbsent(key, method);
                }
            }
            clazz = clazz.getSuperclass();
        }
        return protectedMethods.values();
    }

    static String buildMethodKey(Method method) {
        StringBuilder sb = new StringBuilder();
        sb.append(method.getName());
        for (Class<?> param : method.getParameterTypes()) {
            sb.append('|');
            sb.append(param.getName());
        }
        return sb.toString();
    }

    private static Class<?> findAnnotation(Method method, Class<?>[] annClasses) {
        for (Class clazz : annClasses) {
            if (method.isAnnotationPresent(clazz)) {
                return clazz;
            }
        }
        return null;
    }

    public static String getAopPackageName(Class<?> clazz) {
        return StringHelper.packageName(clazz.getName());
    }

    public static String getAopClassName(Class<?> clazz) {
        String className = StringHelper.replace(clazz.getSimpleName(), "$", "__");
        return getAopPackageName(clazz) + "." + className + "__aop";
    }
}