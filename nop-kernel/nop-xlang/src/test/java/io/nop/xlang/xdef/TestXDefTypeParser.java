package io.nop.xlang.xdef;

import io.nop.commons.type.StdDataType;
import io.nop.xlang.xdsl.XDslParseHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestXDefTypeParser {
    @Test
    public void testParseDouble() {
        XDefTypeDecl defType = XDslParseHelper.parseDefType(null, "a", "double=0.3");
        assertEquals("double=0.3", defType.toString());
        assertEquals(0.3, defType.getDefaultValue());
    }

    @Test
    public void testInitializer() {
        String str = StdDataType.toInitializer("0.3", Double.class);
        assertEquals(" = 0.3", str);
    }
}
