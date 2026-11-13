/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.flow.builder;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.stream.flow.builder.functions.XplFunctionSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * CRA 8.45 R4-05 (plan 2538) W-05 / 1.3-C1-002: {@code XplFunctionSupport} must not
 * retain a JVM-level static shared eval ROOT scope. Pre-fix the shared
 * {@code ROOT_SCOPE} is only protected by a structural invariant (all callers
 * self-discipline into child scopes) with zero runtime guard — a single-point misuse
 * (writing a variable directly into the root) becomes a global cross-thread
 * write-sharing point: the write is visible through EVERY later call scope (parent
 * chain lookup).
 *
 * <p>Driver 红⑤ legs:
 * <ul>
 *   <li>(a) structure: no static shared eval root field remains on
 *       {@code XplFunctionSupport} (pre-fix ROOT_SCOPE exists = red);</li>
 *   <li>(b) behavior: the misuse probe — if a static shared root is still reachable,
 *       a write into it must not be visible through a fresh call scope (pre-fix the
 *       write leaks through the parent chain = red).</li>
 * </ul>
 * Post-fix both legs are green: the field is gone and {@code newCallScope()} returns
 * a fresh root per call (writes never cross calls).
 */
public class TestXplRootScopeFreshRootR405 {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    /**
     * Leg (a) — structure pin: no JVM-level static shared eval root scope.
     */
    @Test
    public void noStaticSharedRootScopeFieldRemains() {
        for (Field field : XplFunctionSupport.class.getDeclaredFields()) {
            boolean isStaticScope = Modifier.isStatic(field.getModifiers())
                    && IEvalScope.class.isAssignableFrom(field.getType());
            assertTrue(!isStaticScope,
                    "XplFunctionSupport must not retain a static shared eval root scope, found: "
                            + field.getName());
        }
    }

    /**
     * Leg (b) — behavior pin: the single-point misuse (a write into whatever static
     * shared root scope exists) must not be visible through fresh call scopes.
     * Pre-fix the ROOT_SCOPE write leaks through the parent chain of every later
     * call scope (= red); post-fix there is no shared root to pollute and each
     * fresh root is empty (= green).
     */
    @Test
    public void misusedSharedRootWriteDoesNotLeakIntoCallScopes() throws Exception {
        IEvalScope sharedRoot = reflectiveStaticRootScopeOrNull();
        if (sharedRoot != null) {
            // THE exact single-point misuse the W-05 defect guards against: a direct
            // write into the shared root (no runtime guard pre-fix).
            sharedRoot.setLocalValue("r405LeakProbe", "polluted");
        }
        // fresh call scope: parent-chain resolution must NOT surface the leak
        IEvalScope callScope = XplFunctionSupport.newCallScope();
        assertNull(callScope.getValue("r405LeakProbe"),
                "a write into the shared root scope must never be visible through a later call scope");
        // and a second independent call scope must not see it either (fresh roots
        // are mutually invisible — guards the "fresh root but shared child chain" decay)
        IEvalScope secondCallScope = XplFunctionSupport.newCallScope();
        secondCallScope.setLocalValue("r405SecondProbe", "local");
        assertNull(callScope.getValue("r405SecondProbe"),
                "two call scopes must not share variables");
    }

    private static IEvalScope reflectiveStaticRootScopeOrNull() {
        for (Field field : XplFunctionSupport.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())
                    || !IEvalScope.class.isAssignableFrom(field.getType())) {
                continue;
            }
            field.setAccessible(true);
            try {
                Object value = field.get(null);
                if (value instanceof IEvalScope) {
                    return (IEvalScope) value;
                }
            } catch (IllegalAccessException e) {
                fail("cannot read static scope field " + field.getName() + ": " + e);
            }
        }
        return null;
    }
}
