package io.nop.codegen.graalvm;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestReflectClass {

    private static ReflectMethod method(String name, String... paramTypes) {
        ReflectMethod m = new ReflectMethod();
        m.setName(name);
        m.setParameterTypes(List.of(paramTypes));
        return m;
    }

    @Test
    public void testRemoveClearsAllDeclaredMethodsFlag() {
        ReflectClass config = new ReflectClass();
        config.setAllDeclaredMethods(true);
        config.setAllPublicMethods(false);

        ReflectClass delta = new ReflectClass();
        delta.setAllDeclaredMethods(true);

        config.remove(delta);

        // 修复前：allDeclaredMethods 分支误写 allPublicMethods = false，标志未被扣除
        assertFalse(config.isAllDeclaredMethods());
        assertTrue(config.isEmpty());
    }

    @Test
    public void testRemoveDeletesMethodBySignature() {
        ReflectClass config = new ReflectClass();
        config.addMethod(method("run", "java.lang.String"));

        ReflectClass delta = new ReflectClass();
        delta.addMethod(method("run", "java.lang.String"));

        config.remove(delta);

        // 修复前：removeByKey 用 getName() 查键，而 methods 的键为 getSignature()，永远删除失败
        assertTrue(config.getMethods().isEmpty());
        assertTrue(config.isEmpty());
    }

    @Test
    public void testMergeMethodsWithSameSignatureNoDuplicate() {
        ReflectClass config = new ReflectClass();
        config.addMethod(method("run", "java.lang.String"));

        ReflectClass other = new ReflectClass();
        other.addMethod(method("run", "java.lang.String"));
        other.addMethod(method("stop"));

        config.merge(other);

        // 同签名方法按键合并为一条；不同方法（无参数）正常新增。
        // ReflectMethod.merge 为空实现，修复前后对象是否被替换无行为差异，
        // 此处断言键语义：methods 以 signature 为键，同签名不产生重复条目
        assertEquals(2, config.getMethods().size());
        assertTrue(containsNamed(config, "run"));
        assertTrue(containsNamed(config, "stop"));
    }

    private static boolean containsNamed(ReflectClass config, String name) {
        for (ReflectMethod m : config.getMethods()) {
            if (m.getName().equals(name))
                return true;
        }
        return false;
    }

    @Test
    public void testRemoveFieldByName() {
        ReflectClass config = new ReflectClass();
        ReflectField field = new ReflectField();
        field.setName("value");
        config.addField(field);

        ReflectClass delta = new ReflectClass();
        ReflectField deltaField = new ReflectField();
        deltaField.setName("value");
        delta.addField(deltaField);

        config.remove(delta);

        assertTrue(config.getFields().isEmpty());
    }
}
