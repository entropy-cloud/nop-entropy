/*
 * Copyright (c) 2017-2025 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

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
