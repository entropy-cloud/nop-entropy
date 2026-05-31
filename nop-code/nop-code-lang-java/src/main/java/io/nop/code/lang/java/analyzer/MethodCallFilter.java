package io.nop.code.lang.java.analyzer;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

import io.nop.code.core.model.CodeMethodCall;
/**
 * 方法调用过滤器
 * 用于过滤不需要记录的方法调用
 */
public class MethodCallFilter implements Predicate<CodeMethodCall> {

    private static final Set<String> DEFAULT_IGNORED_PACKAGES = new HashSet<>(Arrays.asList(
            "java.lang.",
            "java.util."
    ));

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

    public static MethodCallFilter createDefault() {
        return new MethodCallFilter();
    }

    public static MethodCallFilter createNoOp() {
        MethodCallFilter filter = new MethodCallFilter();
        filter.ignoreDefaultPackages = false;
        return filter;
    }

    public MethodCallFilter ignorePackage(String packagePrefix) {
        this.ignoredPackages.add(packagePrefix);
        return this;
    }

    public MethodCallFilter ignoreMethod(String methodName) {
        this.ignoredMethods.add(methodName);
        return this;
    }

    public MethodCallFilter ignoreContext(String context) {
        this.ignoredContexts.add(context);
        return this;
    }

    public MethodCallFilter setIgnoreDefaultPackages(boolean ignore) {
        this.ignoreDefaultPackages = ignore;
        return this;
    }

    public MethodCallFilter setIgnoreDefaultMethods(boolean ignore) {
        this.ignoreDefaultMethods = ignore;
        return this;
    }

    @Override
    public boolean test(CodeMethodCall call) {
        if (shouldIgnoreMethod(call.getMethodName())) {
            return false;
        }

        if (shouldIgnoreContext(call.getContext())) {
            return false;
        }

        return true;
    }

    private boolean shouldIgnoreMethod(String methodName) {
        if (ignoredMethods.contains(methodName)) {
            return true;
        }

        if (ignoreDefaultMethods && DEFAULT_IGNORED_METHODS.contains(methodName)) {
            return true;
        }

        return false;
    }

    private boolean shouldIgnoreContext(String context) {
        if (context == null) {
            return false;
        }

        if (ignoredContexts.contains(context)) {
            return true;
        }

        if (ignoreDefaultPackages) {
            for (String pkg : DEFAULT_IGNORED_PACKAGES) {
                if (context.startsWith(pkg) || context.equals(pkg.substring(0, pkg.length() - 1))) {
                    return true;
                }
            }
            if (isJavaLangType(context)) {
                return true;
            }
        }

        return false;
    }

    private boolean isJavaLangType(String context) {
        return JAVA_LANG_TYPES.contains(context);
    }

    private static final Set<String> JAVA_LANG_TYPES = Set.of(
            "String", "Integer", "Long", "Double", "Float", "Boolean",
            "Byte", "Short", "Character", "Object", "Class",
            "System", "Math", "StrictMath", "StringBuilder", "StringBuffer",
            "Throwable", "Exception", "Error", "RuntimeException"
    );
}
