/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.idea.plugin.lang;

import io.nop.idea.plugin.BaseXLangPluginTestCase;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-23
 */
public class TestXLangRename extends BaseXLangPluginTestCase {

    public void testRenameXdefName() {
    }

    public void testRenameTag() {
    }

    public void testRenameXlibTag() {
        // 从定义侧更名
        assertRename("NewCall", //
                     """
                             <lib xmlns:x="/nop/schema/xdsl.xdef" x:schema="/nop/schema/xlib.xdef">
                                <tags>
                                    <Call></Ca<caret>ll>
                                    <DoCall>
                                        <source>
                                            <thisLib:Call></thisLib:Call>
                                        </source>
                                    </DoCall>
                                </tags>
                             </lib>
                             """, //
                     """
                             <lib xmlns:x="/nop/schema/xdsl.xdef" x:schema="/nop/schema/xlib.xdef">
                                <tags>
                                    <NewCall></NewCall>
                                    <DoCall>
                                        <source>
                                            <thisLib:NewCall></thisLib:NewCall>
                                        </source>
                                    </DoCall>
                                </tags>
                             </lib>
                             """ //
        );

        // 从调用侧更名
        assertRename("NewCall", //
                     """
                             <lib xmlns:x="/nop/schema/xdsl.xdef" x:schema="/nop/schema/xlib.xdef">
                                <tags>
                                    <Call></Call>
                                    <DoCall>
                                        <source>
                                            <thisLib:Ca<caret>ll/>
                                        </source>
                                    </DoCall>
                                </tags>
                             </lib>
                             """, //
                     """
                             <lib xmlns:x="/nop/schema/xdsl.xdef" x:schema="/nop/schema/xlib.xdef">
                                <tags>
                                    <NewCall></NewCall>
                                    <DoCall>
                                        <source>
                                            <thisLib:NewCall/>
                                        </source>
                                    </DoCall>
                                </tags>
                             </lib>
                             """ //
        );
    }

    public void testRenameXlibAttr() {
        // 从定义侧更名
        assertRename("newArg", //
                     """
                             <lib xmlns:x="/nop/schema/xdsl.xdef" x:schema="/nop/schema/xlib.xdef">
                                <tags>
                                    <Call>
                                        <arg name="ar<caret>g1"/>
                                    </Call>
                                    <DoCall>
                                        <source>
                                            <thisLib:Call arg1="abc"/>
                                        </source>
                                    </DoCall>
                                </tags>
                             </lib>
                             """, //
                     """
                             <lib xmlns:x="/nop/schema/xdsl.xdef" x:schema="/nop/schema/xlib.xdef">
                                <tags>
                                    <Call>
                                        <arg name="newArg"/>
                                    </Call>
                                    <DoCall>
                                        <source>
                                            <thisLib:Call newArg="abc"/>
                                        </source>
                                    </DoCall>
                                </tags>
                             </lib>
                             """ //
        );

        // 从调用侧更名
        assertRename("newArg", //
                     """
                             <lib xmlns:x="/nop/schema/xdsl.xdef" x:schema="/nop/schema/xlib.xdef">
                                <tags>
                                    <Call>
                                        <arg name="arg1"/>
                                    </Call>
                                    <DoCall>
                                        <source>
                                            <thisLib:Call ar<caret>g1="abc"/>
                                        </source>
                                    </DoCall>
                                </tags>
                             </lib>
                             """, //
                     """
                             <lib xmlns:x="/nop/schema/xdsl.xdef" x:schema="/nop/schema/xlib.xdef">
                                <tags>
                                    <Call>
                                        <arg name="newArg"/>
                                    </Call>
                                    <DoCall>
                                        <source>
                                            <thisLib:Call newArg="abc"/>
                                        </source>
                                    </DoCall>
                                </tags>
                             </lib>
                             """ //
        );
    }

    protected void assertRename(String newName, String text, String expectedText) {
        configureByXLangText(text);
        myFixture.renameElementAtCaret(newName);

        myFixture.checkResult(expectedText);
    }
}
