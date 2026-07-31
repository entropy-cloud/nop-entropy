package io.nop.ai.code_analyzer.code;

import io.nop.core.lang.json.JsonTool;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 CodeFileInfo.java 拆分后的结构契约：
 * 1) 各符号类型为同包顶层类（非嵌套限定名）
 * 2) 继承关系与拆分前一致（CodeClassInfo/CodeFunctionInfo 继承 CodeSymbol）
 * 3) interning 去重语义与拆分前一致（引用相等性）
 */
public class TestCodeFileInfoStructure extends BaseTestCase {

    @Test
    public void testSymbolTypesAreTopLevel() {
        assertNull(CodeSymbol.class.getEnclosingClass(), "CodeSymbol should be a top-level class");
        assertNull(CodeClassInfo.class.getEnclosingClass(), "CodeClassInfo should be a top-level class");
        assertNull(CodeFunctionInfo.class.getEnclosingClass(), "CodeFunctionInfo should be a top-level class");
        assertNull(CodeCallInfo.class.getEnclosingClass(), "CodeCallInfo should be a top-level class");
        assertNull(CodeVariableInfo.class.getEnclosingClass(), "CodeVariableInfo should be a top-level class");
        assertNull(AccessModifier.class.getEnclosingClass(), "AccessModifier should be a top-level class");
        assertNull(CodeSymbolInterning.class.getEnclosingClass(), "CodeSymbolInterning should be a top-level class");
    }

    @Test
    public void testInheritanceRelationships() {
        assertEquals(CodeSymbol.class, CodeClassInfo.class.getSuperclass());
        assertEquals(CodeSymbol.class, CodeFunctionInfo.class.getSuperclass());
        assertTrue(CodeSymbol.class.isAssignableFrom(CodeClassInfo.class));
        assertTrue(CodeSymbol.class.isAssignableFrom(CodeFunctionInfo.class));
        assertEquals(4, AccessModifier.values().length);
        assertTrue(AccessModifier.valueOf("PUBLIC") == AccessModifier.PUBLIC);
    }

    @Test
    public void testInterningDeduplicatesTopLevelFields() {
        CodeFileInfo fileInfo = new CodeFileInfo();
        fileInfo.setFilePath(new String("src/main/java/Demo.java"));
        fileInfo.setPackageName(new String("demo"));
        fileInfo.setArtifactId(new String("demo-app"));
        fileInfo.setMd5(new String("abc123"));
        fileInfo.setLanguage(new String("java"));
        fileInfo.setSummary(new String("demo summary"));

        Set<String> imports = new LinkedHashSet<>();
        imports.add(new String("java.util.List"));
        imports.add(new String("java.util.Map"));
        fileInfo.setImports(imports);

        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put(new String("tag"), new String("value"));
        fileInfo.setMetadata(metadata);

        fileInfo.intern();

        assertSame("src/main/java/Demo.java".intern(), fileInfo.getFilePath());
        assertSame("demo".intern(), fileInfo.getPackageName());
        assertSame("demo-app".intern(), fileInfo.getArtifactId());
        assertSame("abc123".intern(), fileInfo.getMd5());
        assertSame("java".intern(), fileInfo.getLanguage());
        assertSame("demo summary".intern(), fileInfo.getSummary());
        assertTrue(fileInfo.getImports().contains("java.util.List".intern()));
        assertSame("tag".intern(), fileInfo.getMetadata().keySet().iterator().next());
        assertSame("value".intern(), fileInfo.getMetadata().values().iterator().next());
    }

    @Test
    public void testInterningRecursesSymbolTypes() {
        CodeVariableInfo varInfo = new CodeVariableInfo();
        varInfo.setName(new String("field"));
        varInfo.setType(new String("java.lang.String"));

        CodeCallInfo callInfo = new CodeCallInfo();
        callInfo.setOwnerClassName(new String("demo.Demo"));
        callInfo.setFnName(new String("demo.Demo::run(0)"));
        callInfo.setParams(new ArrayList<>(List.of(varInfo)));

        CodeFunctionInfo fnInfo = new CodeFunctionInfo();
        fnInfo.setName(new String("demo.Demo::run(0)"));
        fnInfo.setSummary(new String("runs"));
        fnInfo.addUsedVar(new String("demo.Demo::field"));
        fnInfo.addUsedFn(new String("demo.Other::call(0)"));

        CodeClassInfo classInfo = new CodeClassInfo();
        classInfo.setName(new String("demo.Demo"));
        classInfo.setSignature(new String("public class Demo"));
        classInfo.setExtendsType(new String("java.lang.Object"));
        classInfo.setImplementsTypes(new LinkedHashSet<>(Set.of(new String("java.io.Serializable"))));
        classInfo.setSummary(new String("demo class"));
        classInfo.setFunctions(new ArrayList<>(List.of(fnInfo)));
        classInfo.setVariables(new ArrayList<>(List.of(varInfo)));

        callInfo.intern();
        classInfo.intern();

        assertSame("demo.Demo".intern(), classInfo.getName());
        assertSame("demo class".intern(), classInfo.getSummary());
        assertSame("java.lang.Object".intern(), classInfo.getExtendsType());
        assertSame("java.io.Serializable".intern(), classInfo.getImplementsTypes().iterator().next());
        assertSame("demo.Demo::run(0)".intern(), fnInfo.getName());
        assertSame("runs".intern(), fnInfo.getSummary());
        assertSame("demo.Demo::field".intern(), fnInfo.getUsedVars().iterator().next());
        assertSame("demo.Other::call(0)".intern(), fnInfo.getUsedFns().iterator().next());
        assertSame("field".intern(), varInfo.getName());
        assertSame("java.lang.String".intern(), varInfo.getType());
        assertSame("demo.Demo".intern(), callInfo.getOwnerClassName());
        assertSame("demo.Demo::run(0)".intern(), callInfo.getFnName());
    }

    @Test
    public void testInternSemanticsUnchangedForNullAndEmpty() {
        CodeFunctionInfo fnInfo = new CodeFunctionInfo();
        fnInfo.intern();
        assertNull(fnInfo.getName());
        assertNull(fnInfo.getSummary());
        assertNull(fnInfo.getUsedFns());

        CodeFileInfo fileInfo = new CodeFileInfo();
        fileInfo.intern();
        assertNull(fileInfo.getFilePath());
    }

    @Test
    public void testGetClassInfoAndFunctionInfoResolveTopLevelTypes() {
        CodeFileInfo fileInfo = new CodeFileInfo();

        CodeFunctionInfo fnInfo = new CodeFunctionInfo();
        fnInfo.setName("demo.Demo::run(0)");

        CodeClassInfo classInfo = new CodeClassInfo();
        classInfo.setName("demo.Demo");
        classInfo.setFunctions(new ArrayList<>(List.of(fnInfo)));
        fileInfo.setClasses(new ArrayList<>(List.of(classInfo)));

        assertSame(classInfo, fileInfo.getClassInfo("demo.Demo"));
        assertSame(classInfo, fileInfo.getClassInfo("Demo"));
        assertSame(fnInfo, fileInfo.getFunctionInfo("demo.Demo::run(0)"));
        assertSame(fnInfo, fileInfo.getFunctionInfo("run"));
    }

    @Test
    public void testSerializationShapeUnchanged() {
        CodeVariableInfo varInfo = new CodeVariableInfo();
        varInfo.setName("field");
        varInfo.setType("String");

        CodeFunctionInfo fnInfo = new CodeFunctionInfo();
        fnInfo.setName("demo.Demo::run(0)");
        fnInfo.setSummary("runs");

        CodeClassInfo classInfo = new CodeClassInfo();
        classInfo.setName("demo.Demo");
        classInfo.setAccessModifier(AccessModifier.PUBLIC);
        classInfo.setFunctions(new ArrayList<>(List.of(fnInfo)));
        classInfo.setVariables(new ArrayList<>(List.of(varInfo)));

        CodeFileInfo fileInfo = new CodeFileInfo();
        fileInfo.setFilePath("Demo.java");
        fileInfo.setLanguage("java");
        fileInfo.setClasses(new ArrayList<>(List.of(classInfo)));

        String json = JsonTool.serialize(fileInfo, false);
        assertTrue(json.contains("\"classes\":["), "classes array should be serialized");
        assertTrue(json.contains("\"name\":\"demo.Demo\""), "class name should be serialized");
        assertTrue(json.contains("\"accessModifier\":\"PUBLIC\""), "access modifier should be serialized");
        assertTrue(json.contains("\"summary\":\"runs\""), "function summary should be serialized");
        assertTrue(json.contains("\"functions\":["), "functions array should be serialized");
        assertTrue(json.contains("\"variables\":["), "variables array should be serialized");
        assertTrue(json.contains("\"name\":\"field\""), "variable name should be serialized");

        CodeFileInfo parsed = JsonTool.parseBeanFromText(json, CodeFileInfo.class);
        assertEquals(1, parsed.getClasses().size());
        assertEquals("demo.Demo", parsed.getClasses().get(0).getName());
    }
}
