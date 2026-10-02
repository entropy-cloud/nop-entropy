package io.nop.xlang.delta;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.xlang.XLangErrors;
import io.nop.xlang.xdef.XDefOverride;
import io.nop.xlang.xdsl.XDslKeys;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * WI3 补强：DeltaDiffer 的 diff 语义（WI0 快照 0% 靶点，142 行）。
 * TestDeltaMerger 只覆盖了 Merger 侧，此处专测 Differ：对 xa（基准）/ xb（新版本）执行 diff 后，
 * 在 xa 上以 x:override 属性标记差异（remove/replace 等）。
 */
public class TestDeltaDiffer extends BaseTestCase {
    static final String OVERRIDE_ATTR = XDslKeys.DEFAULT.OVERRIDE;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private XNode parse(String xml) {
        return XNodeParser.instance().parseFromText(null, xml);
    }

    /**
     * 默认 MERGE 语义：xa 中存在而 xb 中不存在的子节点，被标记为 x:override="remove"，
     * 且 body 被清空、非唯一属性被清除（只保留唯一标识属性 id）。
     */
    @Test
    public void testMergeMarksChildMissingInNewAsRemoved() {
        DeltaDiffer differ = new DeltaDiffer(XDslKeys.DEFAULT);
        XNode xa = parse("<root><a id='1' x='keep'/><b id='2' y='gone'><c/></b></root>");
        XNode xb = parse("<root><a id='1' x='keep'/></root>");

        differ.diff(xa, xb, null, false);

        XNode a = xa.childByTag("a");
        assertNull(a.attrText(OVERRIDE_ATTR), "两边都存在的节点不应被标记");

        XNode b = xa.childByTag("b");
        assertEquals("remove", b.attrText(OVERRIDE_ATTR), "新版本中删除的节点必须标记 remove");
        assertEquals("2", b.attrText("id"), "remove 标记只保留唯一标识属性 id");
        assertNull(b.attrText("y"), "非唯一属性应被清除");
        assertNull(b.childByTag("c"), "remove 节点的 body 应被清空");
    }

    /**
     * MERGE 时两边都存在的子节点递归 diff：xa 子节点上与 xb 相同的属性被去除（未变化无需保留）。
     */
    @Test
    public void testMergeRemovesUnchangedAttrsOnSameTagChild() {
        DeltaDiffer differ = new DeltaDiffer(XDslKeys.DEFAULT);
        XNode xa = parse("<root><a id='1' same='v' changed='old'/></root>");
        XNode xb = parse("<root><a id='1' same='v' changed='new'/></root>");

        differ.diff(xa, xb, null, false);

        XNode a = xa.childByTag("a");
        assertEquals("old", a.attrText("changed"), "发生变化的属性必须保留");
        assertNull(a.attrText("same"), "与 xb 完全相同的属性应被去除");
        assertNull(a.attrText(OVERRIDE_ATTR), "仍有差异时不标记 replace");
    }

    /**
     * xa 上的 remove 标记（根节点级）：body 被清空、标记保持 remove。
     * 解析得到的节点未设置 uniqueAttr 字段时属性不清除（markRemoved 的保护分支）。
     */
    @Test
    public void testRemoveOverrideMarksNodeRemoved() {
        DeltaDiffer differ = new DeltaDiffer(XDslKeys.DEFAULT);
        XNode xa = parse("<root id='r' v='1' x:override='remove'><sub/></root>");
        XNode xb = parse("<root/>");

        differ.diff(xa, xb, null, false);

        assertEquals("remove", xa.attrText(OVERRIDE_ATTR));
        assertNull(xa.childByTag("sub"), "remove 节点的 body 应被清空");
        assertEquals("1", xa.attrText("v"), "未声明 uniqueAttr 时属性保持原样");
    }

    /**
     * 显式声明 uniqueAttr 的 remove 节点：清除全部属性后仅保留唯一标识属性。
     */
    @Test
    public void testRemoveOverrideClearsAttrsWhenUniqueAttrDeclared() {
        DeltaDiffer differ = new DeltaDiffer(XDslKeys.DEFAULT);
        XNode xa = parse("<root id='r' v='1' x:override='remove'/>");
        xa.uniqueAttr("id");
        XNode xb = parse("<root/>");

        differ.diff(xa, xb, null, false);

        assertEquals("remove", xa.attrText(OVERRIDE_ATTR));
        assertEquals("r", xa.attrText("id"), "唯一标识属性保留");
        assertNull(xa.attrText("v"), "非唯一属性被清除");
    }

    /**
     * xa 上的 replace 标记（根节点级）：diff 后标记保持为 replace。
     */
    @Test
    public void testReplaceOverrideKeptAsReplace() {
        DeltaDiffer differ = new DeltaDiffer(XDslKeys.DEFAULT);
        XNode xa = parse("<root x:override='replace' v='1'/>");
        XNode xb = parse("<root/>");

        differ.diff(xa, xb, null, false);
        assertEquals("replace", xa.attrText(OVERRIDE_ATTR));
        assertEquals("1", xa.attrText("v"), "replace 标记不清除属性");
    }

    /**
     * bounded-merge 模式：xa 中存在而 xb 中不存在的子节点表示"不存在即删除"，
     * 不需要显式 remove 标记（与非 bounded 的 MERGE 行为相反）。
     */
    @Test
    public void testBoundedMergeDoesNotMarkRemoved() {
        DeltaDiffer differ = new DeltaDiffer(XDslKeys.DEFAULT, XDefOverride.BOUNDED_MERGE);
        XNode xa = parse("<root><a id='1'/><b id='2'/></root>");
        XNode xb = parse("<root><a id='1'/></root>");

        differ.diff(xa, xb, null, false);

        assertNull(xa.childByTag("b").attrText(OVERRIDE_ATTR),
                "bounded-merge 下缺失即删除，不产生 remove 标记");

        // 同样结构在默认 MERGE 下会标记 remove
        DeltaDiffer mergeDiffer = new DeltaDiffer(XDslKeys.DEFAULT);
        XNode xa2 = parse("<root><a id='1'/><b id='2'/></root>");
        // 注意：diff 会 detachChildren 消费 xb（副作用），必须用新解析的 xb
        XNode xb2 = parse("<root><a id='1'/></root>");
        mergeDiffer.diff(xa2, xb2, null, false);
        assertEquals("remove", xa2.childByTag("b").attrText(OVERRIDE_ATTR));
    }

    /**
     * PREPEND 消除：xa（prepend）子节点序列以 xb（prepend）子节点序列结尾时，
     * 公共后缀可以从 xa 中删去；xa 的与 xb 相同的属性也被去除。
     */
    @Test
    public void testPrependDedupCommonSuffix() {
        DeltaDiffer differ = new DeltaDiffer(XDslKeys.DEFAULT);
        XNode xa = parse("<root x:override='prepend'><a/><b/></root>");
        XNode xb = parse("<root x:override='prepend'><b/></root>");

        differ.diff(xa, xb, null, false);

        // removeDuplicateAttr 会将与 xb 相同值的属性（含同值的 x:override）视为冗余去除
        assertNull(xa.attrText(OVERRIDE_ATTR), "公共后缀消除后 prepend 标记作为冗余属性被去除");
        assertNull(xa.childByTag("b"), "公共后缀应被删去");
        assertNotNull(xa.childByTag("a"), "新增节点保留");
    }

    /**
     * PREPEND 不匹配（xa 子节点不以 xb 子节点结尾）时退化为 replace 标记。
     */
    @Test
    public void testPrependNoMatchFallsBackToReplace() {
        DeltaDiffer differ = new DeltaDiffer(XDslKeys.DEFAULT);
        XNode xa = parse("<root x:override='prepend'><a/></root>");
        XNode xb = parse("<root x:override='prepend'><b/></root>");

        differ.diff(xa, xb, null, false);
        assertEquals("replace", xa.attrText(OVERRIDE_ATTR), "无法消除 prepend 时必须退化为 replace");
        assertNotNull(xa.childByTag("a"), "无法消除时子节点保留");
    }

    /**
     * APPEND 消除：xa（append）子节点序列以 xb（append）子节点序列开头时，公共前缀被删去。
     */
    @Test
    public void testAppendDedupCommonPrefix() {
        DeltaDiffer differ = new DeltaDiffer(XDslKeys.DEFAULT);
        XNode xa = parse("<root x:override='append'><a/><b/></root>");
        XNode xb = parse("<root x:override='append'><a/></root>");

        differ.diff(xa, xb, null, false);

        // removeDuplicateAttr 会将与 xb 相同值的属性（含同值的 x:override）视为冗余去除
        assertNull(xa.attrText(OVERRIDE_ATTR), "公共前缀消除后 append 标记作为冗余属性被去除");
        assertNull(xa.childByTag("a"), "公共前缀应被删去");
        assertNotNull(xa.childByTag("b"), "新增节点保留");
    }

    /**
     * xa 无内容而 xb 有内容时，标记 merge-replace（内容必须整体替换）。
     */
    @Test
    public void testMergeContentOnlyInNewMarksMergeReplace() {
        DeltaDiffer differ = new DeltaDiffer(XDslKeys.DEFAULT);
        XNode xa = parse("<root><a id='1'/></root>");
        XNode xb = parse("<root><a id='1'>text</a></root>");

        differ.diff(xa, xb, null, false);
        assertEquals("merge-replace", xa.childByTag("a").attrText(OVERRIDE_ATTR),
                "xa 无内容而 xb 有内容时必须标记 merge-replace");
    }

    /**
     * 内容相同的节点：内容被清除（未变化无需保留）。
     */
    @Test
    public void testMergeEqualContentCleared() {
        DeltaDiffer differ = new DeltaDiffer(XDslKeys.DEFAULT);
        XNode xa = parse("<root><a>same</a></root>");
        XNode xb = parse("<root><a>same</a></root>");

        differ.diff(xa, xb, null, false);
        assertNull(xa.childByTag("a").contentText(), "相同内容应被清除");
        assertNull(xa.childByTag("a").attrText(OVERRIDE_ATTR));
    }

    /**
     * MERGE_SUPER：xa 中没有 x:super 子节点时退化为 replace 标记。
     */
    @Test
    public void testMergeSuperWithoutSuperChildFallsBackToReplace() {
        DeltaDiffer differ = new DeltaDiffer(XDslKeys.DEFAULT, XDefOverride.MERGE_SUPER);
        XNode xa = parse("<root><a/></root>");
        XNode xb = parse("<root/>");

        differ.diff(xa, xb, null, false);
        assertEquals("replace", xa.attrText(OVERRIDE_ATTR),
                "缺少 super 子节点时 merge-super 退化为 replace");
    }

    /**
     * 非法的 x:override 属性值必须报 nop.err.xlang.xdsl.invalid-override-attr。
     */
    @Test
    public void testInvalidOverrideAttrThrows() {
        DeltaDiffer differ = new DeltaDiffer(XDslKeys.DEFAULT);
        XNode xa = parse("<root x:override='not-exist'/>");
        XNode xb = parse("<root/>");

        NopException e = assertThrows(NopException.class, () -> differ.diff(xa, xb, null, false));
        assertEquals(XLangErrors.ERR_XDSL_INVALID_OVERRIDE_ATTR.getErrorCode(), e.getErrorCode());
    }
}
