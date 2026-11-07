/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.sql.eval;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.commons.util.StringHelper;
import io.nop.stream.core.exceptions.NopStreamErrors;
import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ARG_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;

/**
 * Column access over stream records (WI9): {@link Map} records resolve by column key,
 * bean records resolve via property getters (cached per class). A missing column fails
 * fast with {@code ERR_STREAM_INVALID_ARG} — silently returning null would hide
 * schema/SQL drift.
 *
 * <p>The column key is the LAST segment of a (possibly qualified) name, matching
 * {@code StreamSqlExprCompiler}'s compiled form.
 *
 * <p>CRA 8.45 R4-05 W-04 (1.3-MA-05) bound declaration for {@code GETTER_CACHE}: the
 * entry universe is the set of record classes whose getters the SQL compiler resolves
 * at compile time — one entry per class, insert-only, no eviction; the cache lives for
 * the JVM/classloader domain. Rejected usage: dynamic classloader redeploy of record
 * classes is a declared-unsupported scenario (stale entries would pin the old
 * classloader); restarting the job's JVM is the sanctioned cache reset.
 */
public final class RecordColumnAccess implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final ConcurrentHashMap<Class<?>, Map<String, java.lang.reflect.Method>> GETTER_CACHE =
            new ConcurrentHashMap<>();

    private final String column;

    public RecordColumnAccess(String qualifiedName) {
        this.column = lastSegment(qualifiedName);
    }

    public String getColumn() {
        return column;
    }

    public Object get(Object record) {
        if (record == null) {
            throw invalidArg("record is null while resolving column '" + column + "'");
        }
        if (record instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) record;
            if (map.containsKey(column)) {
                return map.get(column);
            }
            throw invalidArg("column '" + column + "' not present on record map (keys=" + map.keySet() + ")");
        }
        java.lang.reflect.Method getter = getterFor(record.getClass(), column);
        if (getter == null) {
            throw invalidArg("column '" + column + "' has no getter on record class " + record.getClass().getName());
        }
        try {
            return getter.invoke(record);
        } catch (Exception e) {
            throw new StreamException(NopStreamErrors.ERR_STREAM_INVALID_ARG, e)
                    .param(ARG_ARG_NAME, "record")
                    .param(ARG_DETAIL, "failed to read column '" + column + "' from "
                            + record.getClass().getName() + ": " + e.getMessage());
        }
    }

    static String lastSegment(String qualifiedName) {
        if (StringHelper.isEmpty(qualifiedName)) {
            throw invalidArg("empty column name");
        }
        int dot = qualifiedName.lastIndexOf('.');
        return dot < 0 ? qualifiedName : qualifiedName.substring(dot + 1);
    }

    private static java.lang.reflect.Method getterFor(Class<?> clazz, String column) {
        // CRA 8.45 R4-05 W-08 (1.3-C3-005): two-phase lookup — the reflective
        // introspection (collectGetters triggers lazy loading of parameter/return
        // types) must never run while holding a ConcurrentHashMap bin lock (the
        // method-reference callback form), or a class-loading lock inversion
        // (JDK-8161372 family) becomes reachable. A miss computes OUTSIDE the lock
        // and backfills with putIfAbsent; a concurrent double computation is
        // idempotent (same public getter set) and the loser adopts the winner.
        Map<String, java.lang.reflect.Method> getters = GETTER_CACHE.get(clazz);
        if (getters == null) {
            getters = collectGetters(clazz);
            Map<String, java.lang.reflect.Method> existing =
                    GETTER_CACHE.putIfAbsent(clazz, getters);
            if (existing != null) {
                getters = existing;
            }
        }
        return getters.get(column);
    }

    private static Map<String, java.lang.reflect.Method> collectGetters(Class<?> clazz) {
        Map<String, java.lang.reflect.Method> result = new java.util.HashMap<>();
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                if (!java.lang.reflect.Modifier.isPublic(m.getModifiers())
                        || m.getParameterCount() != 0
                        || m.getReturnType() == void.class) {
                    continue;
                }
                String name = m.getName();
                String prop = null;
                if (name.startsWith("get") && name.length() > 3) {
                    prop = decapitalize(name.substring(3));
                } else if (name.startsWith("is") && name.length() > 2
                        && (m.getReturnType() == boolean.class || m.getReturnType() == Boolean.class)) {
                    prop = decapitalize(name.substring(2));
                }
                if (prop != null) {
                    result.putIfAbsent(prop, m);
                }
            }
        }
        return result;
    }

    static StreamException invalidArg(String detail) {
        // param() is declared on NopException; the chain returns this, so the cast is safe
        return (StreamException) new StreamException(NopStreamErrors.ERR_STREAM_INVALID_ARG)
                .param(ARG_ARG_NAME, "record")
                .param(ARG_DETAIL, detail);
    }

    static long toLong(Object v, String context) {
        Long l = ConvertHelper.toLong(v, err -> invalidArg("cannot coerce " + context + " to long: " + v));
        if (l == null) {
            throw invalidArg("cannot coerce " + context + " to long: " + v);
        }
        return l;
    }

    static double toDouble(Object v, String context) {
        Double d = ConvertHelper.toDouble(v, err -> invalidArg("cannot coerce " + context + " to double: " + v));
        if (d == null) {
            throw invalidArg("cannot coerce " + context + " to double: " + v);
        }
        return d;
    }

    static boolean isFloating(Object v) {
        return v instanceof Double || v instanceof Float;
    }

    static Number toNumber(Object v, String context) {
        Number n = ConvertHelper.toNumber(v, err -> invalidArg("cannot coerce " + context + " to number: " + v));
        if (n == null) {
            throw invalidArg("cannot coerce " + context + " to number: " + v);
        }
        return n;
    }

    private static String decapitalize(String s) {
        return s.isEmpty() || Character.isLowerCase(s.charAt(0))
                ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    public static StreamException unsupported(Object node) {
        return (StreamException) new StreamException(NopStreamErrors.ERR_STREAM_INVALID_ARG)
                .param(ARG_ARG_NAME, "sqlExpr")
                .param(ARG_DETAIL, "expression construct is outside the stream SQL v1 subset: "
                        + (node == null ? "null AST" : node.getClass().getSimpleName()
                        + (node instanceof io.nop.orm.eql.ast.SqlExpr
                        ? " (" + node + ")" : "")));
    }

}
