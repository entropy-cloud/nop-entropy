package io.nop.xlang.xpl;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.xlang.XLangErrors;
import io.nop.xlang.api.AbstractEvalAction;
import io.nop.xlang.api.XLang;
import io.nop.xlang.ast.XLangOutputMode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI3 补强：XPL 模板求值边界语义（经 compileXpl + generateToWriter / invoke 同款求值入口）。
 * 覆盖条件/循环边界、输出模式转义、c:unit 返回值、错误路径（未知标签、缺失属性、c:choose 结构）。
 *
 * 输出锚点：c:unit 缺省输出模式为 none，模板内的文本内容必须挂在有 xpl:outputMode
 * 的载体标签下（对齐 c-for.test.md 的 xpl:outputMode="xml" 用法），否则编译期报
 * nop.err.xlang.xpl.not-allow-output。
 */
public class TestXplBoundarySemantics extends BaseTestCase {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void afterAll() {
        CoreInitialization.destroy();
    }

    private AbstractEvalAction compile(String xpl) {
        return XLang.newCompileTool().compileXpl(null, xpl);
    }

    private String generate(String xpl) {
        AbstractEvalAction action = compile(xpl);
        if (action == null) {
            // 模板全部内容被裁剪时编译结果为 null，语义即无输出
            return "";
        }
        StringWriter out = new StringWriter();
        action.generateToWriter(out, new ServiceContextImpl());
        return out.toString();
    }

    private Object invoke(String xpl) {
        return compile(xpl).invoke(new ServiceContextImpl());
    }

    /**
     * c:if 条件为 false 时输出为空；条件表达式经 ${} 求值。
     */
    @Test
    public void testIfFalseProducesNoOutput() {
        String xpl = "<c:unit><c:script>let x = 1;</c:script>"
                + "<c:if test='${x > 1}'><div xpl:outputMode=\"xml\">shown</div></c:if>"
                + "</c:unit>";
        assertEquals("", generate(xpl).trim(), "条件为 false 时必须无输出");
    }

    /**
     * c:for 空集合循环体不执行。
     */
    @Test
    public void testForOverEmptyCollection() {
        String xpl = "<c:unit>"
                + "<c:for items='${[]}' var='item' xpl:outputMode=\"xml\">X${item}</c:for>"
                + "</c:unit>";
        assertEquals("", generate(xpl).trim(), "空集合循环必须无输出");
    }

    /**
     * c:for 集合遍历与插值输出按序生成。
     */
    @Test
    public void testForInterpolationOrder() {
        String xpl = "<c:unit>"
                + "<c:for items='${[1,2,3]}' var='item' xpl:outputMode=\"xml\">${item}</c:for>"
                + "</c:unit>";
        assertEquals("123", generate(xpl).trim());
    }

    /**
     * c:for 的 index 变量从 0 起与元素位置对应（对齐 c-for.test.md index 用法）。
     */
    @Test
    public void testForIndexStartsAtZero() {
        String xpl = "<c:unit>"
                + "<c:for items='${[10,20]}' var='x' index='idx' xpl:outputMode=\"xml\">${idx}=${x};</c:for>"
                + "</c:unit>";
        assertEquals("0=10;1=20;", generate(xpl).trim());
    }

    /**
     * html 输出模式下特殊字符被转义。转义目标串经 c:script 在运行期构造，
     * 避免 XML 文本中出现裸 < 与 &（它们在模板源码层即被 XML 扫描拒绝）。
     */
    @Test
    public void testHtmlOutputEscapesSpecialChars() {
        String xpl = "<div xpl:outputMode=\"html\">&lt;x&gt;</div>";
        String out = generate(xpl);
        assertFalse(out.contains("<x>"), "html 模式不得输出裸标签字符，实际: " + out);
        assertTrue(out.contains("&lt;"), "html 模式保持实体转义输出，实际: " + out);
    }

    /**
     * c:unit 的 return 语义：脚本内变量修改经 invoke 返回（对齐 c-if.test.md return 用法）。
     */
    @Test
    public void testUnitScriptReturnValue() {
        String xpl = "<c:unit><c:script>let x = 1;</c:script>"
                + "<c:if test='${x == 1}'><c:script>x = 2;</c:script></c:if>"
                + "</c:unit>";
        assertEquals(2, invoke(xpl));
    }

    /**
     * 错误路径：c: 名字空间下不存在的标签 → nop.err.xlang.xpl.not-allow-unknown-tag
     * （缺省不允许未知 c: 标签）。
     */
    @Test
    public void testUnknownLibTagThrows() {
        NopException e = assertThrows(NopException.class,
                () -> compile("<c:unit><c:noSuchTag/></c:unit>"));
        assertEquals("nop.err.xlang.xpl.not-allow-unknown-tag", e.getErrorCode());
    }

    /**
     * 错误路径：c:if 缺少 test 属性 → nop.err.xlang.xpl.missing-attr，并携带属性名参数。
     */
    @Test
    public void testIfMissingTestAttrThrows() {
        NopException e = assertThrows(NopException.class,
                () -> compile("<c:unit><c:if><div xpl:outputMode=\"xml\">t</div></c:if></c:unit>"));
        assertEquals(XLangErrors.ERR_XPL_MISSING_ATTR.getErrorCode(), e.getErrorCode());
        assertEquals("test", e.getParams().get("attrName"));
    }

    /**
     * 错误路径：c:choose 的子节点不是 when/otherwise 条件节点 →
     * nop.err.xlang.xpl.choose-child-not-conditional-expr。
     */
    @Test
    public void testChooseInvalidChildThrows() {
        NopException e = assertThrows(NopException.class,
                () -> compile("<c:unit><c:choose>"
                        + "<plain xpl:outputMode=\"xml\">x</plain>"
                        + "</c:choose></c:unit>"));
        assertEquals(XLangErrors.ERR_XPL_CHOOSE_CHILD_NOT_CONDITIONAL_EXPR.getErrorCode(),
                e.getErrorCode());
    }

    /**
     * c:choose / when / otherwise 分支边界：仅第一个满足条件的分支输出。
     */
    @Test
    public void testChooseFirstMatchOnly() {
        String xpl = "<c:unit>"
                + "<c:script>let x = 2;</c:script>"
                + "<c:choose>"
                + "<when test='${x == 1}'><div xpl:outputMode=\"xml\">one</div></when>"
                + "<when test='${x == 2}'><div xpl:outputMode=\"xml\">two</div></when>"
                + "<otherwise><div xpl:outputMode=\"xml\">other</div></otherwise>"
                + "</c:choose>"
                + "</c:unit>";
        String out = generate(xpl);
        assertTrue(out.contains("two"), "满足条件的分支必须输出，实际: " + out);
        assertFalse(out.contains("one<") || out.contains(">one"), "未命中分支不得输出");
        assertFalse(out.contains("other"), "命中 when 后 otherwise 不得输出");
    }

    /**
     * c:choose 全部分支不满足时走 otherwise；无 otherwise 则无输出。
     */
    @Test
    public void testChooseOtherwiseFallback() {
        String xpl = "<c:unit>"
                + "<c:choose>"
                + "<when test='${false}'><div xpl:outputMode=\"xml\">yes</div></when>"
                + "<otherwise><div xpl:outputMode=\"xml\">fallback</div></otherwise>"
                + "</c:choose>"
                + "</c:unit>";
        String out = generate(xpl);
        assertTrue(out.contains("fallback"), "otherwise 兜底分支必须输出，实际: " + out);

        String noOtherwise = "<c:unit><c:choose>"
                + "<when test='${false}'><div xpl:outputMode=\"xml\">yes</div></when>"
                + "</c:choose></c:unit>";
        assertEquals("", generate(noOtherwise).trim());
    }

    /**
     * 非法输出模式名的 API 边界：fromText 返回 null 宽容处理，requireFromText 抛
     * nop.err.xlang.xpl.unknown-output-mode。
     */
    @Test
    public void testUnknownOutputModeTextReturnsNull() {
        assertNull(XLangOutputMode.fromText("no-such-mode"));
        NopException e = assertThrows(NopException.class,
                () -> XLangOutputMode.requireFromText("no-such-mode"));
        assertEquals(XLangErrors.ERR_XPL_UNKNOWN_OUTPUT_MODE.getErrorCode(), e.getErrorCode());
    }

    /**
     * xml 输出模式下标签结构保留、插值求值，空元素保持自闭合形式。
     */
    @Test
    public void testXmlOutputKeepsStructure() {
        String xpl = "<div xpl:outputMode=\"xml\"><a v='${1+1}'/></div>";
        String out = generate(xpl);
        assertTrue(out.contains("<a v=\"2\"/>"), "xml 模式保留自闭合标签并求值插值，实际: " + out);
        assertTrue(out.replace("\n", "").startsWith("<div"), "xml 模式保留根标签结构");
    }
}
