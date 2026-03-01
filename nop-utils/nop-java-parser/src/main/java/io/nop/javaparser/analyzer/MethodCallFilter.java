package io.nop.javaparser.analyzer;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 方法调用过滤器
 * 用于过滤不需要记录的方法调用
 */
public class MethodCallFilter implements Predicate<MethodCall> {

    /**
     * 默认忽略的包前缀
     */
    private static final Set<String> DEFAULT_IGNORED_PACKAGES = new HashSet<>(Arrays.asList(
            "java.lang.",
            "java.util."
    ));

    /**
     * 默认忽略的方法名（常见的不需要跟踪的方法）
     */
    private static final Set<String> DEFAULT_IGNORED_METHODS = new HashSet<>(Arrays.asList(
            // Object methods
            "toString",
            "hashCode",
            "equals",
            "getClass",
            "clone",
            "finalize",
            "notify",
            "notifyAll",
            "wait",
            // Common getters
            "getName",
            "getValue",
            "getType",
            "length",
            "size",
            "isEmpty",
            // Common conversions
            "intValue",
            "longValue",
            "doubleValue",
            "floatValue",
            "booleanValue",
            "byteValue",
            "shortValue",
            "charValue"
    ));

    private final Set<String> ignoredPackages;
    private final Set<String> ignoredMethods;
    private final Set<String> ignoredContexts;
    private boolean ignoreDefaultPackages = true;
    private boolean ignoreDefaultMethods = false;

    public MethodCallFilter() {
        this.ignoredPackages = new HashSet<>();
        this.ignoredMethods = new HashSet<>();
        this.ignoredContexts = new HashSet<>();
    }

    /**
     * 创建默认过滤器（忽略java.lang和java.util包的调用）
     */
    public static MethodCallFilter createDefault() {
        return new MethodCallFilter();
    }

    /**
     * 创建不进行任何过滤的过滤器
     */
    public static MethodCallFilter createNoOp() {
        MethodCallFilter filter = new MethodCallFilter();
        filter.ignoreDefaultPackages = false;
        return filter;
    }

    /**
     * 添加要忽略的包前缀
     */
    public MethodCallFilter ignorePackage(String packagePrefix) {
        this.ignoredPackages.add(packagePrefix);
        return this;
    }

    /**
     * 添加要忽略的方法名
     */
    public MethodCallFilter ignoreMethod(String methodName) {
        this.ignoredMethods.add(methodName);
        return this;
    }

    /**
     * 添加要忽略的调用上下文（scope）
     */
    public MethodCallFilter ignoreContext(String context) {
        this.ignoredContexts.add(context);
        return this;
    }

    /**
     * 设置是否忽略默认包（java.lang, java.util）
     */
    public MethodCallFilter setIgnoreDefaultPackages(boolean ignore) {
        this.ignoreDefaultPackages = ignore;
        return this;
    }

    /**
     * 设置是否忽略默认方法（toString, hashCode等）
     */
    public MethodCallFilter setIgnoreDefaultMethods(boolean ignore) {
        this.ignoreDefaultMethods = ignore;
        return this;
    }

    @Override
    public boolean test(MethodCall call) {
        // 检查方法名是否在忽略列表中
        if (shouldIgnoreMethod(call.getMethodName())) {
            return false;
        }

        // 检查上下文是否在忽略列表中
        if (shouldIgnoreContext(call.getContext())) {
            return false;
        }

        return true;
    }

    /**
     * 判断是否应该忽略该方法
     */
    private boolean shouldIgnoreMethod(String methodName) {
        // 检查自定义忽略的方法
        if (ignoredMethods.contains(methodName)) {
            return true;
        }

        // 检查默认忽略的方法
        if (ignoreDefaultMethods && DEFAULT_IGNORED_METHODS.contains(methodName)) {
            return true;
        }

        return false;
    }

    /**
     * 判断是否应该忽略该上下文
     */
    private boolean shouldIgnoreContext(String context) {
        if (context == null) {
            return false;
        }

        // 检查自定义忽略的上下文
        if (ignoredContexts.contains(context)) {
            return true;
        }

        // 检查默认忽略的包
        if (ignoreDefaultPackages) {
            for (String pkg : DEFAULT_IGNORED_PACKAGES) {
                if (context.startsWith(pkg) || context.equals(pkg.substring(0, pkg.length() - 1))) {
                    return true;
                }
            }
            // 也检查简单类名匹配（如 String, Integer 等）
            if (isJavaLangType(context)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 检查是否是java.lang中的类型
     */
    private boolean isJavaLangType(String context) {
        // 常见的java.lang类型
        Set<String> javaLangTypes = new HashSet<>(Arrays.asList(
                "String", "Integer", "Long", "Double", "Float", "Boolean",
                "Byte", "Short", "Character", "Object", "Class",
                "System", "Math", "StrictMath", "StringBuilder", "StringBuffer",
                "Throwable", "Exception", "Error", "RuntimeException"
        ));
        return javaLangTypes.contains(context);
    }
}
