package io.nop.core.reflect.impl;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestModifierBuilder {

    @Test
    public void testBeginIsEmpty() {
        assertEquals(0, ModifierBuilder.begin().end());
        assertEquals(0, new ModifierBuilder().end());
    }

    @Test
    public void testAccessModifierBitsAreSet() {
        // 回归覆盖 wi1#3（plan 2306 项 10）：PUBLIC_MASK = PUBLIC|PROTECTED|PRIVATE，
        // is* 系列必须清除先前设置的访问修饰符，保证同一时刻只有一个访问位
        assertEquals(Modifier.PUBLIC, ModifierBuilder.begin().isPublic().end() & Modifier.PUBLIC);
        assertEquals(Modifier.PRIVATE, new ModifierBuilder().isPrivate().end() & Modifier.PRIVATE);
        assertEquals(Modifier.PROTECTED, new ModifierBuilder(Modifier.PRIVATE).isProtected().end()
                & Modifier.PROTECTED);
        // not* 系列的清除语义正常
        assertEquals(0, new ModifierBuilder(Modifier.PUBLIC).notPublic().end() & Modifier.PUBLIC);
    }

    @Test
    public void testAccessModifiersAreMutuallyExclusive() {
        // 回归覆盖 wi1#3：isPrivate 覆盖先前 isPublic，结果不能出现 PUBLIC|PRIVATE 非法组合
        int mod = ModifierBuilder.begin().isPublic().isPrivate().end();
        assertEquals(Modifier.PRIVATE, mod,
                "isPrivate must replace PUBLIC; PUBLIC|PRIVATE combination is illegal");

        int mod2 = new ModifierBuilder(Modifier.PRIVATE | Modifier.FINAL).isPublic().end();
        assertEquals(Modifier.PUBLIC | Modifier.FINAL, mod2,
                "isPublic must replace PRIVATE while keeping non-access flags");

        int mod3 = new ModifierBuilder(Modifier.PROTECTED).isPublic().end();
        assertEquals(Modifier.PUBLIC, mod3);
    }

    @Test
    public void testNotAccessModifiers() {
        int mod = new ModifierBuilder(Modifier.PROTECTED).notProtected().end();
        assertEquals(0, mod & Modifier.PROTECTED);

        int mod2 = new ModifierBuilder(Modifier.PRIVATE | Modifier.FINAL).notPrivate().end();
        assertEquals(Modifier.FINAL, mod2, "notPrivate should keep other flags");
        assertEquals(0, mod2 & Modifier.PRIVATE);

        int mod3 = new ModifierBuilder(Modifier.PUBLIC).notPublic().end();
        assertEquals(0, mod3 & Modifier.PUBLIC);
    }

    @Test
    public void testNonAccessFlagsAreAdditive() {
        int mod = ModifierBuilder.begin()
                .isPublic()
                .isStatic()
                .isFinal()
                .isAbstract()
                .isNative()
                .isVolatile()
                .isTransient()
                .end();

        assertEquals(Modifier.PUBLIC | Modifier.STATIC | Modifier.FINAL | Modifier.ABSTRACT
                | Modifier.NATIVE | Modifier.VOLATILE | Modifier.TRANSIENT, mod);
    }

    @Test
    public void testRemoveNonAccessFlags() {
        int base = Modifier.STATIC | Modifier.FINAL | Modifier.ABSTRACT | Modifier.NATIVE
                | Modifier.VOLATILE | Modifier.TRANSIENT;

        assertEquals(Modifier.STATIC, new ModifierBuilder(base)
                .notFinal().notAbstract().notNative().notVolatile().notTransient().end());

        assertEquals(Modifier.FINAL, new ModifierBuilder(base).notStatic().end() & Modifier.FINAL,
                "notStatic should keep FINAL flag intact");
        assertEquals(0, new ModifierBuilder(base).notStatic().end() & Modifier.STATIC);
    }

    @Test
    public void testChainedBuildMatchesReflectionModifiers() {
        // 模拟一个 public static final 字段的修饰符
        int mod = ModifierBuilder.begin().isPublic().isStatic().isFinal().end();
        assertEquals(Modifier.PUBLIC | Modifier.STATIC | Modifier.FINAL, mod);
        assertTrue(Modifier.isPublic(mod));
        assertTrue(Modifier.isStatic(mod));
        assertTrue(Modifier.isFinal(mod));
    }
}
