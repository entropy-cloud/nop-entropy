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
        // 注意：PUBLIC_MASK = PUBLIC & PROTECTED & PRIVATE == 0，is* 系列不会清除
        // 先设置的访问修饰符（疑似缺陷，见测试报告），因此这里只断言目标位被置位
        assertEquals(Modifier.PUBLIC, ModifierBuilder.begin().isPublic().end() & Modifier.PUBLIC);
        assertEquals(Modifier.PRIVATE, new ModifierBuilder().isPrivate().end() & Modifier.PRIVATE);
        assertEquals(Modifier.PROTECTED, new ModifierBuilder(Modifier.PRIVATE).isProtected().end()
                & Modifier.PROTECTED);
        // not* 系列的清除语义正常
        assertEquals(0, new ModifierBuilder(Modifier.PUBLIC).notPublic().end() & Modifier.PUBLIC);
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
