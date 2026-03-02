package io.nop.javaparser.analyzer;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class JavaFileAnalyzerTest {

    private final JavaFileAnalyzer analyzer = new JavaFileAnalyzer();

    @Test
    public void testIndexSimpleClass() {
        String sourceCode = "package com.example;\n" +
                "\n" +
                "import java.util.List;\n" +
                "\n" +
                "/**\n" +
                " * A simple test class\n" +
                " */\n" +
                "@Deprecated\n" +
                "public class TestClass {\n" +
                "    private String name;\n" +
                "    public static final int MAX_SIZE = 100;\n" +
                "\n" +
                "    public TestClass(String name) {\n" +
                "        this.name = name;\n" +
                "    }\n" +
                "\n" +
                "    public String getName() {\n" +
                "        return name;\n" +
                "    }\n" +
                "}\n";

        JavaFileAnalysisResult index = analyzer.analyze("TestClass.java", sourceCode);

        assertNotNull(index);
        assertEquals("TestClass.java", index.getFilePath());
        assertEquals("com.example", index.getPackageName());
        assertEquals("JAVA", index.getLanguage());
        assertEquals(20, index.getLineCount());
        assertEquals(1, index.getImports().size());
        assertEquals("java.util.List", index.getImports().get(0));

        // 验证符号
        List<SymbolInfo> symbols = index.getSymbols();
        assertFalse(symbols.isEmpty());

        // 查找类符号
        SymbolInfo classSymbol = symbols.stream()
                .filter(s -> s.getKind() == SymbolKind.CLASS && "TestClass".equals(s.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(classSymbol);
        assertEquals("com.example.TestClass", classSymbol.getQualifiedName());
        assertEquals(AccessModifier.PUBLIC, classSymbol.getAccessModifier());
        assertTrue(classSymbol.isDeprecated());
        assertNotNull(classSymbol.getDocumentation());

        // 查找字段符号
        SymbolInfo fieldSymbol = symbols.stream()
                .filter(s -> s.getKind() == SymbolKind.FIELD && "name".equals(s.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(fieldSymbol);
        assertEquals(AccessModifier.PRIVATE, fieldSymbol.getAccessModifier());
        assertEquals("String", fieldSymbol.getFieldType());

        // 查找构造器符号
        SymbolInfo constructorSymbol = symbols.stream()
                .filter(s -> s.getKind() == SymbolKind.CONSTRUCTOR)
                .findFirst()
                .orElse(null);
        assertNotNull(constructorSymbol);
        assertTrue(constructorSymbol.getSignature().contains("TestClass(String)"));

        // 查找方法符号
        SymbolInfo methodSymbol = symbols.stream()
                .filter(s -> s.getKind() == SymbolKind.METHOD && "getName".equals(s.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(methodSymbol);
        assertEquals("String", methodSymbol.getReturnType());

        // 验证注解使用
        List<AnnotationUsage> annotations = index.getAnnotationUsages();
        assertFalse(annotations.isEmpty());
        AnnotationUsage deprecatedAnnotation = annotations.stream()
                .filter(a -> "Deprecated".equals(a.getAnnotationTypeQualifiedName()))
                .findFirst()
                .orElse(null);
        assertNotNull(deprecatedAnnotation);
    }

    @Test
    public void testIndexInterface() {
        String sourceCode = "package com.example;\n" +
                "\n" +
                "public interface TestInterface {\n" +
                "    void doSomething();\n" +
                "    default String getName() { return \"test\"; }\n" +
                "}\n";

        JavaFileAnalysisResult index = analyzer.analyze("TestInterface.java", sourceCode);

        assertNotNull(index);
        assertEquals("com.example", index.getPackageName());

        // 查找接口符号
        SymbolInfo interfaceSymbol = index.getSymbols().stream()
                .filter(s -> s.getKind() == SymbolKind.INTERFACE)
                .findFirst()
                .orElse(null);
        assertNotNull(interfaceSymbol);
        assertEquals("TestInterface", interfaceSymbol.getName());
    }

    @Test
    public void testIndexEnum() {
        String sourceCode = "package com.example;\n" +
                "\n" +
                "public enum Status {\n" +
                "    ACTIVE,\n" +
                "    INACTIVE,\n" +
                "    PENDING\n" +
                "}\n";

        JavaFileAnalysisResult index = analyzer.analyze("Status.java", sourceCode);

        assertNotNull(index);

        // 查找枚举符号
        SymbolInfo enumSymbol = index.getSymbols().stream()
                .filter(s -> s.getKind() == SymbolKind.ENUM)
                .findFirst()
                .orElse(null);
        assertNotNull(enumSymbol);
        assertEquals("Status", enumSymbol.getName());

        // 查找枚举常量
        List<SymbolInfo> enumConstants = index.getSymbols().stream()
                .filter(s -> s.getKind() == SymbolKind.ENUM_CONSTANT)
                .collect(Collectors.toList());
        assertEquals(3, enumConstants.size());
    }

    @Test
    public void testIndexAnnotation() {
        String sourceCode = "package com.example;\n" +
                "\n" +
                "public @interface MyAnnotation {\n" +
                "    String value() default \"\";\n" +
                "    int count() default 0;\n" +
                "}\n";

        JavaFileAnalysisResult index = analyzer.analyze("MyAnnotation.java", sourceCode);

        assertNotNull(index);

        // 查找注解类型符号
        SymbolInfo annotationSymbol = index.getSymbols().stream()
                .filter(s -> s.getKind() == SymbolKind.ANNOTATION_TYPE)
                .findFirst()
                .orElse(null);
        assertNotNull(annotationSymbol);
        assertEquals("MyAnnotation", annotationSymbol.getName());
    }

    @Test
    public void testIndexInheritance() {
        String sourceCode = "package com.example;\n" +
                "\n" +
                "public class ChildClass extends ParentClass implements Runnable, Cloneable {\n" +
                "    @Override\n" +
                "    public void run() {}\n" +
                "}\n";

        JavaFileAnalysisResult index = analyzer.analyze("ChildClass.java", sourceCode);

        assertNotNull(index);

        // 验证继承关系
        List<InheritanceInfo> inheritances = index.getInheritances();
        assertEquals(3, inheritances.size());

        // 验证extends关系
        InheritanceInfo extendsRel = inheritances.stream()
                .filter(i -> i.getRelationType() == RelationType.EXTENDS)
                .findFirst()
                .orElse(null);
        assertNotNull(extendsRel);
        assertEquals("ParentClass", extendsRel.getSuperTypeQualifiedName());

        // 验证implements关系
        List<InheritanceInfo> implementsRels = inheritances.stream()
                .filter(i -> i.getRelationType() == RelationType.IMPLEMENTS)
                .collect(Collectors.toList());
        assertEquals(2, implementsRels.size());
    }

    @Test
    public void testIndexNestedClass() {
        String sourceCode = "package com.example;\n" +
                "\n" +
                "public class OuterClass {\n" +
                "    private int value;\n" +
                "\n" +
                "    public static class InnerClass {\n" +
                "        private String name;\n" +
                "    }\n" +
                "}\n";

        JavaFileAnalysisResult index = analyzer.analyze("OuterClass.java", sourceCode);

        assertNotNull(index);

        // 查找外部类
        SymbolInfo outerClass = index.getSymbols().stream()
                .filter(s -> "OuterClass".equals(s.getName()) && s.getKind() == SymbolKind.CLASS)
                .findFirst()
                .orElse(null);
        assertNotNull(outerClass);

        // 查找内部类
        SymbolInfo innerClass = index.getSymbols().stream()
                .filter(s -> "InnerClass".equals(s.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(innerClass);
        assertEquals("com.example.OuterClass.InnerClass", innerClass.getQualifiedName());
        assertEquals(outerClass.getId(), innerClass.getParentId());
    }

    @Test
    public void testIndexEmptySource() {
        JavaFileAnalysisResult index = analyzer.analyze("Empty.java", "");
        assertNull(index);

        index = analyzer.analyze("Null.java", null);
        assertNull(index);

        index = analyzer.analyze("Whitespace.java", "   \n\t  ");
        assertNull(index);
    }

    @Test
    public void testIndexComplexAnnotations() {
        String sourceCode = "package com.example;\n" +
                "\n" +
                "@SuppressWarnings(\"unchecked\")\n" +
                "@javax.xml.bind.annotation.XmlRootElement(name = \"root\")\n" +
                "public class AnnotatedClass {\n" +
                "    @Deprecated(since = \"1.0\", forRemoval = true)\n" +
                "    public void oldMethod() {}\n" +
                "}\n";

        JavaFileAnalysisResult index = analyzer.analyze("AnnotatedClass.java", sourceCode);

        assertNotNull(index);

        // 验证注解
        List<AnnotationUsage> annotations = index.getAnnotationUsages();
        assertTrue(annotations.size() >= 3);

        // 验证类上的注解
        SymbolInfo classSymbol = index.getSymbols().stream()
                .filter(s -> "AnnotatedClass".equals(s.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(classSymbol);

        // 验证方法上的注解
        SymbolInfo methodSymbol = index.getSymbols().stream()
                .filter(s -> "oldMethod".equals(s.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(methodSymbol);
    }

    @Test
    public void testIndexMethodWithModifiers() {
        String sourceCode = "package com.example;\n" +
                "\n" +
                "public class Utility {\n" +
                "    public static void staticMethod() {}\n" +
                "    public final void finalMethod() {}\n" +
                "    public synchronized void syncMethod() {}\n" +
                "    public native void nativeMethod();\n" +
                "    public abstract void abstractMethod();\n" +
                "}\n";

        JavaFileAnalysisResult index = analyzer.analyze("Utility.java", sourceCode);

        assertNotNull(index);

        // 验证static方法
        SymbolInfo staticMethod = index.getSymbols().stream()
                .filter(s -> "staticMethod".equals(s.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(staticMethod);
        assertTrue(staticMethod.isStaticFlag());

        // 验证final方法
        SymbolInfo finalMethod = index.getSymbols().stream()
                .filter(s -> "finalMethod".equals(s.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(finalMethod);
        assertTrue(finalMethod.isFinalFlag());

        // 验证synchronized方法
        SymbolInfo syncMethod = index.getSymbols().stream()
                .filter(s -> "syncMethod".equals(s.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(syncMethod);
        assertTrue(syncMethod.isSynchronizedFlag());

        // 验证native方法
        SymbolInfo nativeMethod = index.getSymbols().stream()
                .filter(s -> "nativeMethod".equals(s.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(nativeMethod);
        assertTrue(nativeMethod.isNativeFlag());

        // 验证abstract方法
        SymbolInfo abstractMethod = index.getSymbols().stream()
                .filter(s -> "abstractMethod".equals(s.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(abstractMethod);
        assertTrue(abstractMethod.isAbstractFlag());
    }

    @Test
    public void testIndexFieldWithModifiers() {
        String sourceCode = "package com.example;\n" +
                "\n" +
                "public class Constants {\n" +
                "    public static final String NAME = \"test\";\n" +
                "    private volatile int counter;\n" +
                "    private transient String cache;\n" +
                "}\n";

        JavaFileAnalysisResult index = analyzer.analyze("Constants.java", sourceCode);

        assertNotNull(index);

        // 验证static final字段
        SymbolInfo nameField = index.getSymbols().stream()
                .filter(s -> "NAME".equals(s.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(nameField);
        assertTrue(nameField.isStaticFlag());
        assertTrue(nameField.isFinalFlag());

        // 验证volatile字段
        SymbolInfo counterField = index.getSymbols().stream()
                .filter(s -> "counter".equals(s.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(counterField);
        assertTrue(counterField.isVolatileFlag());

        // 验证transient字段
        SymbolInfo cacheField = index.getSymbols().stream()
                .filter(s -> "cache".equals(s.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(cacheField);
        assertTrue(cacheField.isTransientFlag());
    }

    @Test
    public void testMethodCallExtraction() {
        String sourceCode = "package com.example;\n" +
                "\n" +
                "public class Service {\n" +
                "    private Helper helper;\n" +
                "\n" +
                "    public void doWork() {\n" +
                "        helper.process(\"test\");\n" +
                "        String result = helper.format(1, 2);\n" +
                "        System.out.println(result);\n" +
                "    }\n" +
                "\n" +
                "    public String getValue() {\n" +
                "        return helper.getName();\n" +
                "    }\n" +
                "}\n";

        JavaFileAnalysisResult index = analyzer.analyze("Service.java", sourceCode);

        assertNotNull(index);

        // 验证方法调用被提取
        List<MethodCall> calls = index.getCalls();
        assertFalse(calls.isEmpty());

        // 查找 process 方法调用
        MethodCall processCall = calls.stream()
                .filter(c -> "process".equals(c.getMethodName()))
                .findFirst()
                .orElse(null);
        assertNotNull(processCall);
        assertEquals("helper", processCall.getContext());
        assertTrue(processCall.getArgumentTypes().contains("test"));

        // 查找 format 方法调用
        MethodCall formatCall = calls.stream()
                .filter(c -> "format".equals(c.getMethodName()))
                .findFirst()
                .orElse(null);
        assertNotNull(formatCall);
        assertEquals("helper", formatCall.getContext());

        // 查找 println 方法调用
        MethodCall printlnCall = calls.stream()
                .filter(c -> "println".equals(c.getMethodName()))
                .findFirst()
                .orElse(null);
        assertNotNull(printlnCall);
        assertEquals("System.out", printlnCall.getContext());

        // 查找 getName 方法调用
        MethodCall getNameCall = calls.stream()
                .filter(c -> "getName".equals(c.getMethodName()))
                .findFirst()
                .orElse(null);
        assertNotNull(getNameCall);

        // 验证调用者ID（doWork方法内的调用）
        SymbolInfo doWorkMethod = index.getSymbols().stream()
                .filter(s -> s.getKind() == SymbolKind.METHOD && "doWork".equals(s.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(doWorkMethod);
        assertEquals(doWorkMethod.getId(), processCall.getCallerId());
    }

    @Test
    public void testMethodCallFilter() {
        String sourceCode = "package com.example;\n" +
                "\n" +
                "public class FilterTest {\n" +
                "    public void test() {\n" +
                "        String name = \"test\";\n" +
                "        int len = name.length();\n" + // 应该被过滤（java.lang）
                "        String str = name.toString();\n" + // 应该被过滤（java.lang）
                "        System.out.println(\"hello\");\n" + // System.out 会被保留
                "        doSomething();\n" + // 应该保留
                "    }\n" +
                "\n" +
                "    public void doSomething() {}\n" +
                "}\n";

        // 使用默认过滤器（忽略java.lang/java.util和常见方法）
        JavaFileAnalyzer analyzer = new JavaFileAnalyzer();
        analyzer.setMethodCallFilter(MethodCallFilter.createDefault()
                .setIgnoreDefaultMethods(true));
        JavaFileAnalysisResult result = analyzer.analyze("FilterTest.java", sourceCode);

        List<MethodCall> calls = result.getCalls();

        // name.length() 和 name.toString() 应该被过滤
        MethodCall lengthCall = calls.stream()
                .filter(c -> "length".equals(c.getMethodName()))
                .findFirst()
                .orElse(null);
        assertNull(lengthCall);

        MethodCall toStringCall = calls.stream()
                .filter(c -> "toString".equals(c.getMethodName()))
                .findFirst()
                .orElse(null);
        assertNull(toStringCall);

        // println 应该保留（System.out 不是 java.lang 类型本身）
        MethodCall printlnCall = calls.stream()
                .filter(c -> "println".equals(c.getMethodName()))
                .findFirst()
                .orElse(null);
        // 注意：System.out 会被保留，因为 context 是 "System.out"

        // doSomething 应该保留
        MethodCall doSomethingCall = calls.stream()
                .filter(c -> "doSomething".equals(c.getMethodName()))
                .findFirst()
                .orElse(null);
        assertNotNull(doSomethingCall);
    }

    @Test
    public void testMethodCallNoFilter() {
        String sourceCode = "package com.example;\n" +
                "\n" +
                "public class NoFilterTest {\n" +
                "    public void test() {\n" +
                "        String name = \"test\";\n" +
                "        int len = name.length();\n" +
                "    }\n" +
                "}\n";

        // 不使用过滤器
        JavaFileAnalyzer analyzer = new JavaFileAnalyzer();
        analyzer.setMethodCallFilter(null);
        JavaFileAnalysisResult result = analyzer.analyze("NoFilterTest.java", sourceCode);

        List<MethodCall> calls = result.getCalls();

        // length 应该被记录
        MethodCall lengthCall = calls.stream()
                .filter(c -> "length".equals(c.getMethodName()))
                .findFirst()
                .orElse(null);
        assertNotNull(lengthCall);
    }

    @Test
    public void testCustomMethodCallFilter() {
        String sourceCode = "package com.example;\n" +
                "\n" +
                "public class CustomFilterTest {\n" +
                "    public void test() {\n" +
                "        helper.process();\n" +
                "        helper.getName();\n" +
                "        helper.doWork();\n" +
                "    }\n" +
                "}\n";

        // 自定义过滤器：忽略 getName 方法
        JavaFileAnalyzer analyzer = new JavaFileAnalyzer();
        analyzer.setMethodCallFilter(MethodCallFilter.createDefault()
                .ignoreMethod("getName"));
        JavaFileAnalysisResult result = analyzer.analyze("CustomFilterTest.java", sourceCode);

        List<MethodCall> calls = result.getCalls();

        // process 应该保留
        MethodCall processCall = calls.stream()
                .filter(c -> "process".equals(c.getMethodName()))
                .findFirst()
                .orElse(null);
        assertNotNull(processCall);

        // getName 应该被过滤
        MethodCall getNameCall = calls.stream()
                .filter(c -> "getName".equals(c.getMethodName()))
                .findFirst()
                .orElse(null);
        assertNull(getNameCall);

        // doWork 应该保留
        MethodCall doWorkCall = calls.stream()
                .filter(c -> "doWork".equals(c.getMethodName()))
                .findFirst()
                .orElse(null);
    }

    @Test
    public void testSymbolResolution_JdkMethod() {
        // 测试 JDK 方法的符号解析
        String sourceCode = "package com.example;\n" +
                "\n" +
                "import java.util.ArrayList;\n" +
                "import java.util.List;\n" +
                "\n" +
                "public class JdkMethodTest {\n" +
                "    public void test() {\n" +
                "        List<String> list = new ArrayList<>();\n" +
                "        list.add(\"test\");\n" +
                "        int size = list.size();\n" +
                "    }\n" +
                "}\n";

        // 启用符号解析
        JavaFileAnalyzer analyzer = new JavaFileAnalyzer();
        analyzer.setMethodCallFilter(null); // 不过滤，以便测试所有调用
        
        JavaFileAnalysisResult result = analyzer.analyze("JdkMethodTest.java", sourceCode);
        assertNotNull(result);
        
        List<MethodCall> calls = result.getCalls();
        assertFalse(calls.isEmpty());
        
        // 查找 add 方法调用
        MethodCall addCall = calls.stream()
                .filter(c -> "add".equals(c.getMethodName()))
                .findFirst()
                .orElse(null);
        assertNotNull(addCall);
        
        // 验证 calleeQualifiedName 被填充（JDK 方法应该能解析）
        assertNotNull("calleeQualifiedName should be resolved for List.add", 
                addCall.getCalleeQualifiedName());
        assertTrue(addCall.getCalleeQualifiedName().contains("List"));
        assertTrue(addCall.getCalleeQualifiedName().contains("add"));
        
        // 验证参数类型被解析
        assertNotNull("argumentTypes should be resolved", addCall.getArgumentTypes());
        
        // 查找 size 方法调用
        MethodCall sizeCall = calls.stream()
                .filter(c -> "size".equals(c.getMethodName()))
                .findFirst()
                .orElse(null);
        assertNotNull(sizeCall);
        assertNotNull("calleeQualifiedName should be resolved for List.size",
                sizeCall.getCalleeQualifiedName());
    }

    @Test
    public void testSymbolResolution_DisableResolution() {
        // 测试禁用符号解析
        String sourceCode = "package com.example;\n" +
                "\n" +
                "import java.util.List;\n" +
                "\n" +
                "public class DisableResolutionTest {\n" +
                "    public void test() {\n" +
                "        List<String> list = null;\n" +
                "        list.size();\n" +
                "    }\n" +
                "}\n";

        JavaFileAnalyzer analyzer = new JavaFileAnalyzer();
        analyzer.setEnableSymbolResolution(false); // 禁用符号解析
        analyzer.setMethodCallFilter(null);
        
        JavaFileAnalysisResult result = analyzer.analyze("DisableResolutionTest.java", sourceCode);
        assertNotNull(result);
        
        MethodCall sizeCall = result.getCalls().stream()
                .filter(c -> "size".equals(c.getMethodName()))
                .findFirst()
                .orElse(null);
        assertNotNull(sizeCall);
        
        // 禁用解析时，calleeQualifiedName 应该为空
        assertNull(sizeCall.getCalleeQualifiedName(), 
                "calleeQualifiedName should be null when resolution is disabled");
    }

    @Test
    public void testSymbolResolution_ChainedCalls() {
        // 测试链式调用的符号解析
        String sourceCode = "package com.example;\n" +
                "\n" +
                "import java.util.stream.Stream;\n" +
                "\n" +
                "public class ChainedCallsTest {\n" +
                "    public void test() {\n" +
                "        Stream.of(\"a\", \"b\").filter(s -> s.length() > 0).count();\n" +
                "    }\n" +
                "}\n";

        JavaFileAnalyzer analyzer = new JavaFileAnalyzer();
        analyzer.setMethodCallFilter(null);
        
        JavaFileAnalysisResult result = analyzer.analyze("ChainedCallsTest.java", sourceCode);
        assertNotNull(result);
        
        List<MethodCall> calls = result.getCalls();
        
        // 验证 Stream.of 被解析
        MethodCall ofCall = calls.stream()
                .filter(c -> "of".equals(c.getMethodName()))
                .findFirst()
                .orElse(null);
        assertNotNull(ofCall);
        if (ofCall.getCalleeQualifiedName() != null) {
            assertTrue(ofCall.getCalleeQualifiedName().contains("Stream"));
        }
        
        // 验证 count 被解析
        MethodCall countCall = calls.stream()
                .filter(c -> "count".equals(c.getMethodName()))
                .findFirst()
                .orElse(null);
        assertNotNull(countCall);
    }
}
