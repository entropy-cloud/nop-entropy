package io.nop.idea.plugin.utils;

import io.nop.idea.plugin.BaseXLangPluginTestCase;
import io.nop.xlang.xdef.IXDefinition;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-18
 */
public class TestXDefPsiHelper extends BaseXLangPluginTestCase {

    public void testGetXdefDef() {
        IXDefinition xdef = XDefPsiHelper.getXdefDef();

        assertNotNull(xdef);
    }
}
