/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.idea.plugin.lang;

import java.util.List;

import io.nop.idea.plugin.BaseXLangPluginTestCase;
import io.nop.idea.plugin.lang.script.XLangScriptFileType;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-28
 */
public class TestXLangScriptCompletions extends BaseXLangPluginTestCase {
    private static final String ext = XLangScriptFileType.INSTANCE.getDefaultExtension();

    public void testImportCompletion() {
        // Note: 不能构造仅匹配唯一结果的样本，以避免 myFixture.getLookupElementStrings() 返回 null 或空结果
        String[] samples = new String[] {
                // 对包的补全
                "io.nop.xlang.", //
                "io.nop.x", //
                // 对类的补全
                "io.nop.xlang.xdef.XD", //
        };

        for (String sample : samples) {
            myFixture.configureByText("sample." + ext, "import " + sample + "<caret>;");
            myFixture.completeBasic();

            // Note: 在仅有唯一匹配时，得到的结果为 null 或空
            List<String> items = myFixture.getLookupElementStrings();
            assertNotNull(items);
            assertFalse(items.isEmpty());

            String expected = "import " + sample.replaceAll("\\.[^.]+$", ".") + items.get(0) + ";";
            assertCompletion(expected);
        }
    }

    public void testObjectMemberCompletion() {
        assertCompletion("""
                                 let s = "abc";
                                 s.toCharA<caret>
                                 """, //
                         """
                                 let s = "abc";
                                 s.toCharArray
                                 """ //
        );

        assertCompletion("""
                                 const handler = new io.nop.xlang.xdef.domain.XJsonDomai<caret>
                                 """, //
                         """
                                 const handler = new io.nop.xlang.xdef.domain.XJsonDomainHandler
                                 """ //
        );
        assertCompletion("""
                                 const handler = new io.nop.xlang.xdef.d<caret>
                                 """, //
                         """
                                 const handler = new io.nop.xlang.xdef.domain
                                 """ //
        );

        assertCompletion("""
                                 import io.nop.xlang.xdef.domain.XJsonDomainHandler;
                                 const handler = XJsonDomainHandler.INST<caret>
                                 """, //
                         """
                                 import io.nop.xlang.xdef.domain.XJsonDomainHandler;
                                 const handler = XJsonDomainHandler.INSTANCE
                                 """ //
        );
        assertCompletion("""
                                 import io.nop.xlang.xdef.domain.XJsonDomainHandler;
                                 const handler = new XJsonDomainHandler();
                                 handler.instan<caret>
                                 """, //
                         """
                                 import io.nop.xlang.xdef.domain.XJsonDomainHandler;
                                 const handler = new XJsonDomainHandler();
                                 handler.instance
                                 """ //
        );

        assertCompletion("""
                                 import io.nop.xlang.xdef.domain.XJsonDomainHandler;
                                 const handler = new XJsonDomainHandler.Su<caret>
                                 """, //
                         """
                                 import io.nop.xlang.xdef.domain.XJsonDomainHandler;
                                 const handler = new XJsonDomainHandler.Sub
                                 """ //
        );
        assertCompletion("""
                                 import io.nop.xlang.xdef.domain.XJsonDomainHandler;
                                 const handler = new XJsonDomainHandler.Sub();
                                 handler.ag<caret>
                                 """, //
                         """
                                 import io.nop.xlang.xdef.domain.XJsonDomainHandler;
                                 const handler = new XJsonDomainHandler.Sub();
                                 handler.age
                                 """ //
        );
    }

    public void testCompletionInXLib() {
        assertCompletionInXLib("""
                                       <lib xmlns:x="/nop/schema/xdsl.xdef" xmlns:c="c"
                                            x:schema="/nop/schema/xlib.xdef">
                                           <tags>
                                               <DoTest>
                                                   <source>
                                                       <c:script><![CDATA[
                                                           import io.nop.xlang.xdef.domain.XJsonDomainHandler;
                                                           const handler = new XJsonDomainHandler();
                                                           handler.getN<caret>();
                                                       ]]></c:script>
                                                   </source>
                                               </DoTest>
                                           </tags>
                                       </lib>
                                       """, //
                               """
                                       <lib xmlns:x="/nop/schema/xdsl.xdef" xmlns:c="c"
                                            x:schema="/nop/schema/xlib.xdef">
                                           <tags>
                                               <DoTest>
                                                   <source>
                                                       <c:script><![CDATA[
                                                           import io.nop.xlang.xdef.domain.XJsonDomainHandler;
                                                           const handler = new XJsonDomainHandler();
                                                           handler.getName();
                                                       ]]></c:script>
                                                   </source>
                                               </DoTest>
                                           </tags>
                                       </lib>
                                       """ //
        );
        assertCompletionInXLib("""
                                       <lib xmlns:x="/nop/schema/xdsl.xdef" xmlns:c="c"
                                            x:schema="/nop/schema/xlib.xdef">
                                           <tags>
                                               <DoTest>
                                                   <source>
                                                       import io.nop.xlang.xdef.domain.XJsonDomainHandler;
                                                       const handler = new XJsonDomainHandler();
                                                       handler.getN<caret>();
                                                   </source>
                                               </DoTest>
                                           </tags>
                                       </lib>
                                       """, //
                               """
                                       <lib xmlns:x="/nop/schema/xdsl.xdef" xmlns:c="c"
                                            x:schema="/nop/schema/xlib.xdef">
                                           <tags>
                                               <DoTest>
                                                   <source>
                                                       import io.nop.xlang.xdef.domain.XJsonDomainHandler;
                                                       const handler = new XJsonDomainHandler();
                                                       handler.getName();
                                                   </source>
                                               </DoTest>
                                           </tags>
                                       </lib>
                                       """ //
        );
    }

    /** 需确保仅有唯一一项自动填充项：匹配是模糊匹配，需增加输入长度才能做唯一匹配 */
    protected void assertCompletion(String text, String expectedText) {
        myFixture.configureByText("sample." + ext, text);
        myFixture.completeBasic();

        myFixture.checkResult(expectedText);
    }

    protected void assertCompletionInXLib(String text, String expectedText) {
        configureByXLangText(text);
        myFixture.completeBasic();

        myFixture.checkResult(expectedText);
    }
}
