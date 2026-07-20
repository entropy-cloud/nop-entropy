package io.nop.web.page;

import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.lang.xml.XNode;
import io.nop.xlang.api.XLang;
import io.nop.xlang.api.XLangCompileTool;
import io.nop.xlang.api.ExprEvalAction;
import io.nop.xlang.ast.XLangOutputMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestCPrintInPostExtends extends JunitBaseTestCase {

    /**
     * xpl:is="c:print" 等价于 c:print，输出时不包裹外层标签
     */
    @Test
    public void testXplIsCprintDoesNotWrap() {
        XLangCompileTool tool = XLang.newCompileTool();
        tool.getScope().setAllowUnknownTag(true);
        tool.getScope().setLocalValue("view", "shouldNotAppear");

        // source 通过 xpl:is 委托给 c:print
        XNode node = XNode.parse("<c:unit xmlns:c=\"c\">" +
                "<source xpl:is=\"c:print\">view = ${view}</source>" +
                "</c:unit>");
        ExprEvalAction action = tool.compileTagBody(node, XLangOutputMode.node);
        XNode result = action.generateNode(tool.getScope());

        // 输出不应该包含 <source> 标签，应直接是 text node
        assertFalse(result.xml().contains("<source"), "should NOT wrap in <source>");
        assertTrue(result.hasContent());
        String text = result.contentText();
        assertTrue(text.contains("${view}"), "${view} should be literal");

        // 对比直接 c:print 的结果，应该一致
        XNode node2 = XNode.parse("<c:unit xmlns:c=\"c\">" +
                "<c:print>view = ${view}</c:print>" +
                "</c:unit>");
        ExprEvalAction action2 = tool.compileTagBody(node2, XLangOutputMode.node);
        XNode result2 = action2.generateNode(tool.getScope());
        assertEquals(result.xml(), result2.xml(),
                "xpl:is='c:print' and direct c:print should produce same output");
    }

    /**
     * xpl:is="c:print" 带额外属性，额外属性不应出现在输出中
     */
    @Test
    public void testXplIsCprintIgnoresExtraAttrs() {
        XLangCompileTool tool = XLang.newCompileTool();
        tool.getScope().setAllowUnknownTag(true);
        tool.getScope().setLocalValue("x", "ignored");

        XNode node = XNode.parse("<c:unit xmlns:c=\"c\">" +
                "<anything xpl:is=\"c:print\" extraAttr=\"val\" another=\"${x}\">body text</anything>" +
                "</c:unit>");
        ExprEvalAction action = tool.compileTagBody(node, XLangOutputMode.node);
        XNode result = action.generateNode(tool.getScope());

        // 输出应该全是 body，不包含 anything 标签，也不包含 extraAttr
        String xml = result.xml();
        assertFalse(xml.contains("anything"), "should NOT wrap in <anything>");
        assertFalse(xml.contains("extraAttr"), "extra attributes should NOT appear in output");
        assertTrue(result.hasContent());
        assertEquals("body text", result.contentText().trim());
    }

    /**
     * xpl:is="c:print" 的 body 是 XML 子节点
     */
    @Test
    public void testXplIsCprintWithXmlBody() {
        XLangCompileTool tool = XLang.newCompileTool();
        tool.getScope().setAllowUnknownTag(true);
        tool.getScope().setLocalValue("expr", "shouldNotAppear");

        XNode node = XNode.parse("<c:unit xmlns:c=\"c\">" +
                "<source xpl:is=\"c:print\">" +
                "  <my:Comp x=\"${expr}\"/>" +
                "</source>" +
                "</c:unit>");
        ExprEvalAction action = tool.compileTagBody(node, XLangOutputMode.node);
        XNode result = action.generateNode(tool.getScope());

        // body 中的 XML 子节点应原样输出，不包裹 source
        String xml = result.xml();
        assertFalse(xml.contains("<source"), "should NOT wrap in <source>");
        assertTrue(xml.contains("my:Comp"), "should contain child as-is");
    }

    /**
     * outputMode=text 时 xpl:is="c:print" 输出纯文本
     */
    @Test
    public void testXplIsCprintTextMode() {
        XLangCompileTool tool = XLang.newCompileTool();
        tool.getScope().setAllowUnknownTag(true);

        XNode node = XNode.parse("<c:unit xpl:outputMode=\"text\" xmlns:c=\"c\">" +
                "<source xpl:is=\"c:print\">view = ${view}</source>" +
                "</c:unit>");
        ExprEvalAction action = tool.compileTagBody(node, XLangOutputMode.text);
        String result = (String) action.generateText(tool.getScope());

        assertTrue(result.contains("${view}"));
        assertFalse(result.contains("<source"));
        assertEquals("view = ${view}", result.trim());
    }

    /**
     * outputMode=xml 时 xpl:is="c:print" 输出原始 XML 文本
     */
    @Test
    public void testXplIsCprintXmlMode() {
        XLangCompileTool tool = XLang.newCompileTool();
        tool.getScope().setAllowUnknownTag(true);

        XNode node = XNode.parse("<c:unit xpl:outputMode=\"xml\" xmlns:c=\"c\">" +
                "<source xpl:is=\"c:print\">" +
                "  <child attr=\"${x}\"/>" +
                "</source>" +
                "</c:unit>");
        ExprEvalAction action = tool.compileTagBody(node, XLangOutputMode.xml);
        String result = (String) action.generateText(tool.getScope());

        // xml mode 下 c:print 调用 node.innerXml()，产出 body 的原始 xml 文本
        assertTrue(result.contains("<child"), "should contain child as xml text");
        assertTrue(result.contains("${x}"), "expression should be literal");
        assertFalse(result.contains("<source"), "should NOT wrap in <source>");
    }
}
