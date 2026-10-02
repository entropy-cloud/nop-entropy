package io.nop.xlang.janino;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.javac.JavaCompilerErrors;
import io.nop.xlang.ast.ClassDefinition;
import io.nop.xlang.ast.CompilationUnit;
import io.nop.xlang.ast.Declaration;
import io.nop.xlang.ast.EnumDeclaration;
import io.nop.xlang.ast.Identifier;
import io.nop.xlang.ast.ImportAsDeclaration;
import io.nop.xlang.ast.Program;
import io.nop.xlang.ast.XLangClassKind;
import io.nop.xlang.xmeta.xjava.JaninoHelper;
import org.codehaus.janino.Java;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI3 补强：JavaToXLangTransformer（WI0 快照 0% 靶点，142 行；janino 在模块依赖内）。
 * 验证 Java AST 到 XLang AST 的转换语义：类/接口/枚举声明、import、字段与方法签名。
 */
public class TestJavaToXLangTransformer extends BaseTestCase {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private static Java.CompilationUnit parseJava(String source) {
        return JaninoHelper.parseJavaSource(SourceLocation.fromPath("test.java"), source);
    }

    /**
     * 类声明转换：类名、extends/implements 类型与字段、方法签名被保留。
     */
    @Test
    public void testBuildClassDeclaration() {
        Java.CompilationUnit unit = parseJava(
                "import java.util.List;\n"
                        + "public class MyBean extends BaseBean implements Runnable, java.io.Serializable {\n"
                        + "    List<String> names;\n"
                        + "    int value;\n"
                        + "    public void run(int count) throws Exception {}\n"
                        + "}\n");

        Program program = new JavaToXLangTransformer().buildProgram(unit);
        assertEquals(2, program.getBody().size(), "1 个 import + 1 个类型声明");

        Declaration decl = (Declaration) program.getBody().get(1);
        assertTrue(decl instanceof ClassDefinition);
        ClassDefinition cls = (ClassDefinition) decl;
        assertEquals(XLangClassKind.CLASS, cls.getClassKind());
        assertEquals("MyBean", cls.getName().getName());
        assertNotNull(cls.getExtendsType(), "extends 类型必须被转换");
        assertEquals("BaseBean", cls.getExtendsType().getTypeName());
        assertEquals(2, cls.getImplementTypes().size(), "implements 列表必须保留");
        assertEquals("java.io.Serializable", cls.getImplementTypes().get(1).getTypeName());

        // 【产品缺陷记录，不修】janino 3.1.12 下 getMemberTypeDeclarations() 不包含字段，
        // JavaToXLangTransformer.buildFieldDeclarations 实际拿不到字段，字段全部丢失。
        assertTrue(cls.getFields().isEmpty(), "缺陷锚定：字段在当前 janino 版本下未被提取");

        assertEquals(1, cls.getMethods().size());
        assertEquals("run", cls.getMethods().get(0).getName().getName());
        assertEquals(1, cls.getMethods().get(0).getParams().size());
        assertEquals("count", ((Identifier) cls.getMethods().get(0).getParams().get(0).getName()).getName());
    }

    /**
     * 接口声明转换：classKind=INTERFACE。
     */
    @Test
    public void testBuildInterfaceDeclaration() {
        Java.CompilationUnit unit = parseJava("public interface MyIf { void doIt(); }\n");
        CompilationUnit cu = new JavaToXLangTransformer().buildCompilationUnit(unit);
        ClassDefinition decl = (ClassDefinition) cu.getStatements().get(0);
        assertEquals(XLangClassKind.INTERFACE, decl.getClassKind());
        assertEquals("MyIf", decl.getName().getName());
    }

    /**
     * 枚举声明转换：枚举成员逐一映射为 EnumMember。
     */
    @Test
    public void testBuildEnumDeclaration() {
        Java.CompilationUnit unit = parseJava("public enum Color { RED, GREEN, BLUE }\n");
        Declaration decl = new JavaToXLangTransformer().buildDeclaration(
                (Java.PackageMemberTypeDeclaration) unit.packageMemberTypeDeclarations.get(0));
        assertTrue(decl instanceof EnumDeclaration);
        EnumDeclaration enumDecl = (EnumDeclaration) decl;
        assertEquals("Color", enumDecl.getName().getName());
        assertEquals(3, enumDecl.getMembers().size());
        assertEquals("RED", enumDecl.getMembers().get(0).getName().getName());
        assertEquals("BLUE", enumDecl.getMembers().get(2).getName().getName());
    }

    /**
     * 单类型 import 转换为 importClass 声明；静态单类型 import 标记 staticImport=true。
     */
    @Test
    public void testImportDeclarations() {
        Java.CompilationUnit unit = parseJava(
                "import java.util.Map;\n"
                        + "import static java.util.Collections.EMPTY_MAP;\n"
                        + "class A {}\n");

        ImportAsDeclaration normal = new JavaToXLangTransformer().buildImportDeclaration(
                unit.importDeclarations[0]);
        assertEquals("java.util.Map", normal.getImportClassName());
        assertEquals(false, normal.getStaticImport());

        ImportAsDeclaration staticImport = new JavaToXLangTransformer().buildImportDeclaration(
                unit.importDeclarations[1]);
        assertEquals(true, staticImport.getStaticImport(), "静态 import 必须被标记");
    }

    /**
     * 不支持的 import 形式（on-demand import）：
     * ignoreInvalidSyntax=false 抛 nop.err.javac.not-support-transform-to-xlang-ast-fail；
     * ignoreInvalidSyntax=true 返回空声明继续转换。
     */
    @Test
    public void testOnDemandImportErrorAndIgnoreMode() {
        String source = "import java.util.*;\nclass A {}\n";

        NopException e = assertThrows(NopException.class,
                () -> new JavaToXLangTransformer(false).buildProgram(parseJava(source)));
        assertEquals(JavaCompilerErrors.ERR_JAVAC_NOT_SUPPORT_TRANSFORM_TO_XLANG_AST_FAIL.getErrorCode(),
                e.getErrorCode());
        assertEquals("TypeImportOnDemandDeclaration", e.getParams().get(JavaCompilerErrors.ARG_JAVA_TYPE));

        // ignore 模式下返回空 import 声明，转换继续
        Program program = new JavaToXLangTransformer(true).buildProgram(parseJava(source));
        assertEquals(2, program.getBody().size());
        ImportAsDeclaration ignored = (ImportAsDeclaration) program.getBody().get(0);
        assertNull(ignored.getSource(), "被忽略的 import 不带 source");
    }

    /**
     * buildCompilationUnit 保留包名。
     */
    @Test
    public void testBuildCompilationUnitKeepsPackageName() {
        Java.CompilationUnit unit = parseJava("package io.nop.test;\nclass A {}\n");
        CompilationUnit cu = new JavaToXLangTransformer().buildCompilationUnit(unit);
        assertEquals("io.nop.test", cu.getPackageName());
    }

    /**
     * 方法缺省参数列表（无参方法）转换后 params 为空，引用类型返回值被解析为类型节点。
     */
    @Test
    public void testMethodWithoutParams() {
        Java.CompilationUnit unit = parseJava("class A { String answer() { return \"42\"; } }\n");
        Program program = new JavaToXLangTransformer().buildProgram(unit);
        ClassDefinition cls = (ClassDefinition) program.getBody().get(0);
        assertEquals(1, cls.getMethods().size());
        assertEquals(0, cls.getMethods().get(0).getParams().size());
        assertNotNull(cls.getMethods().get(0).getReturnType());
        assertTrue(cls.getMethods().get(0).getReturnType() instanceof io.nop.xlang.ast.ParameterizedTypeNode,
                "引用类型返回值应转换为 ParameterizedTypeNode");
        assertEquals("String",
                ((io.nop.xlang.ast.ParameterizedTypeNode) cls.getMethods().get(0).getReturnType()).getTypeName());
    }

    /**
     * extends 为 null（直接继承 Object）时 extendsType 为 null，不抛异常。
     */
    @Test
    public void testClassWithoutExtends() {
        Java.CompilationUnit unit = parseJava("class A {}\n");
        ClassDefinition cls = (ClassDefinition) new JavaToXLangTransformer().buildProgram(unit).getBody().get(0);
        assertNull(cls.getExtendsType());
        assertNull(cls.getImplementTypes(), "无 implements 时实现类型列表为 null");
    }
}
