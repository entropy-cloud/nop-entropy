package io.nop.idea.plugin.reference;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import io.nop.idea.plugin.BaseXLangPluginTestCase;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-22
 */
public class TestXLangReferenceProvider extends BaseXLangPluginTestCase {
    private static final String XLANG_EXT = "xref";

    @Override
    protected String[] getXLangFileExtensions() {
        return new String[] { XLANG_EXT };
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        // Note: 提前将需要跳转的文件添加到 Project 中
        addVfsResourcesToProject("/nop/schema/xdef.xdef", "/nop/schema/xdsl.xdef", "/nop/schema/xmeta.xdef");
    }

    public void testGetReferencesFromXmlAttributeValue() {
        doTest(readVfsResource("/nop/schema/xdsl.xdef").replace("xmlns:xdef=\"/nop/schema/xdef.xdef\"",
                                                                "xmlns:xdef=\"/nop/sche<caret>ma/xdef.xdef\""));
        doTest(readVfsResource("/nop/schema/xdsl.xdef").replace("xdsl:schema=\"/nop/schema/xdef.xdef\"",
                                                                "xdsl:schema=\"/nop/sche<caret>ma/xdef.xdef\""));
    }

    /** 通过在 <code>text</code> 中插入 <code>&lt;caret&gt;</code> 代表光标位置 */
    private void doTest(String text) {
        myFixture.configureByText("example." + XLANG_EXT, text);

        // 实际有多个引用时，将构造返回 PsiMultiReference，
        // 其会按 PsiMultiReference#COMPARATOR 对引用排序得到优先引用，
        // 再调用该优先引用的 #resolve() 得到 PsiElement
        PsiReference ref = findReferenceAtCaret();
        assertNotNull(ref);

        PsiElement target = ref.resolve();
        assertNotNull(target);
    }
}
