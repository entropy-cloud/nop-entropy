package io.nop.xlang.xdsl;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.xlang.api.XLang;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.nop.xlang.XLangErrors.ERR_XDSL_NO_SCHEMA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 无 x:schema 定义的节点 x:extends 指向 yaml 文件时应报带上下文的 NopException，而不是裸 NPE
 */
public class TestXDslExtenderYaml {
    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testYamlExtendsWithoutSchemaThrowsNopException() {
        String xml = "<root x:extends='/test/ext-data.yaml'/>";
        XNode node = XNodeParser.instance().parseFromText(SourceLocation.fromPath("/test/ext-yaml-no-schema.xml"), xml);

        XDslExtender extender = new XDslExtender(XDslKeys.DEFAULT);

        NopException e = assertThrows(NopException.class,
                () -> extender.xtend(null, null, node, XDslExtendPhase.mergeBase, XLang.newEvalScope()));
        assertEquals(ERR_XDSL_NO_SCHEMA.getErrorCode(), e.getErrorCode());
        assertEquals("/test/ext-data.yaml", e.getParam("path"));
    }
}
