package io.nop.core.lang.utils;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.xml.XNode;
import org.junit.jupiter.api.Test;

import static io.nop.core.CoreErrors.ERR_XML_NOT_ALLOW_COMPILE_PHASE_EXPR;
import static io.nop.core.CoreErrors.ERR_XML_NOT_ALLOW_CUSTOM_NAMESPACE;
import static io.nop.core.CoreErrors.ERR_XML_NOT_ALLOW_EXPR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestXNodeHelper {

    @Test
    public void testCheckSafeXplAllowsPlainNode() {
        XNode node = XNode.make("div");
        node.setAttr("class", "c1");
        node.appendChild(XNode.make("span"));
        // 无自定义名字空间、无表达式时应通过检查
        XNodeHelper.checkSafeXpl(node, "c", true);
    }

    @Test
    public void testCheckSafeXplRejectsCustomNamespace() {
        XNode node = XNode.make("div");
        node.appendChild(XNode.make("web:Control"));

        NopException e = assertThrows(NopException.class, () -> XNodeHelper.checkSafeXpl(node, "c", true));
        assertEquals(ERR_XML_NOT_ALLOW_CUSTOM_NAMESPACE.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testCheckSafeXplAllowsNsInAllowedList() {
        XNode node = XNode.make("div");
        node.appendChild(XNode.make("c:Control"));
        XNodeHelper.checkSafeXpl(node, "c", true);
    }

    @Test
    public void testCheckSafeXplRejectsCompilePhaseExpr() {
        XNode node = XNode.make("div");
        node.setAttr("a", "#{1+1}");

        NopException e = assertThrows(NopException.class, () -> XNodeHelper.checkSafeXpl(node, "c", true));
        assertEquals(ERR_XML_NOT_ALLOW_COMPILE_PHASE_EXPR.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testCheckSafeXplRejectsElExprWhenDisallowed() {
        XNode node = XNode.make("div");
        node.setAttr("a", "${var.x}");

        // allowExpr=false 时拒绝 EL 表达式
        NopException e = assertThrows(NopException.class, () -> XNodeHelper.checkSafeXpl(node, "c", false));
        assertEquals(ERR_XML_NOT_ALLOW_EXPR.getErrorCode(), e.getErrorCode());

        // allowExpr=true 时允许
        XNodeHelper.checkSafeXpl(node, "c", true);
    }

    @Test
    public void testMoveAttrWithNs() {
        XNode ret = XNode.make("root");
        ret.setAttr("plain", "1");
        ret.setAttr("c:name", "value");

        XNode body = XNode.make("body");
        XNodeHelper.moveAttrWithNs(ret, body, "c", true);

        assertNull(ret.getAttr("c:name"), "ns attr should be moved off source node");
        assertEquals("value", body.getAttr("name"), "removeNs=true should strip the ns prefix");
        assertEquals("1", ret.getAttr("plain"), "plain attrs should stay");
    }

    @Test
    public void testMoveAttrWithNsKeepPrefix() {
        XNode ret = XNode.make("root");
        ret.setAttr("c:name", "value");

        XNode body = XNode.make("body");
        XNodeHelper.moveAttrWithNs(ret, body, "c", false);
        assertEquals("value", body.getAttr("c:name"), "removeNs=false should keep the ns prefix");
    }

    @Test
    public void testMoveChildWithNs() {
        XNode ret = XNode.make("root");
        ret.appendChild(XNode.make("plain"));
        ret.appendChild(XNode.make("c:Control"));
        ret.appendChild(XNode.make("other:Thing"));

        XNode body = XNode.make("body");
        XNodeHelper.moveChildWithNs(ret, body, "c", true);

        assertEquals(1, body.getChildCount());
        assertEquals("Control", body.child(0).getTagName(), "removeNs=true should strip tag prefix");
        assertEquals(2, ret.getChildCount(), "only c: children should be moved");
    }

    @Test
    public void testMoveChildWithNsKeepPrefix() {
        XNode ret = XNode.make("root");
        ret.appendChild(XNode.make("c:Control"));

        XNode body = XNode.make("body");
        XNodeHelper.moveChildWithNs(ret, body, "c", false);
        assertEquals("c:Control", body.child(0).getTagName());
        assertTrue(ret.getChildCount() == 0);
    }
}
