package io.nop.xlang.truffle;

import io.nop.api.core.exceptions.NopEvalException;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.xlang.exec.GenNodeExecutable;
import io.nop.xlang.exec.GetPropertyExecutable;
import io.nop.xlang.exec.IfExecutable;
import io.nop.xlang.exec.LiteralExecutable;
import io.nop.xlang.exec.OutputTextExecutable;
import io.nop.xlang.exec.SlotIdentifierExecutable;
import io.nop.xlang.truffle.translate.ExecToTruffleTranslator;
import io.nop.xlang.truffle.translate.TreeFingerprints;
import org.junit.jupiter.api.Test;

import static io.nop.xlang.XLangErrors.ARG_CLASS_NAME;
import static io.nop.xlang.XLangErrors.ARG_LOCATION;
import static io.nop.xlang.XLangErrors.ERR_EXEC_TRANSLATE_UNSUPPORTED_NODE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 支持集外节点 fail-fast 单测（Phase 2/3 Exit Criteria）：翻译失败必须报节点类名 + SourceLocation，
 * 禁止部分翻译。I7 边界收缩后反例迁移（闭环后无 pending 集——反证取"支持集外合成节点"与
 * "改判排除类"，附边界不回退守护）：
 *
 * <ul>
 * <li>支持集外节点类（测试域合成 {@code FutureExecutable}——模拟前端演进新增节点；
 * <li>改判排除类（I4 边缘裁定：{@code ReturnScopeValuesExecutable}/{@code ExecutableFunctionEvalAction}）；
 * <li>支持集内节点类的越域用法（slot 读写越出程序入口帧）；</li>
 * <li>边界不回退守护：I5 期反例 GetProperty（I6 收缩）、I6 期反例 OutputText/If（I7 收缩）
 *     均已可翻译。</li>
 * </ul>
 */
public class TestTranslatorFailFast {

    private static final SourceLocation LOC = SourceLocation.fromLine("/fail-fast-test.xpl", 3, 7);

    private final ExecToTruffleTranslator translator = new ExecToTruffleTranslator();

    @Test
    public void testOutOfSubsetNodeFailsFastWithClassNameAndLocation() {
        // I7 后既有 B 族已可翻译：反例迁移到测试域合成节点（未注册具体节点类）
        IExecutableExpression future = new CoverageNodes.FutureExecutable(LOC);

        NopEvalException e = assertThrows(NopEvalException.class,
                () -> translator.translate("/fail-fast-test.xpl", TreeFingerprints.fingerprint(future),
                        future, null));

        assertEquals(ERR_EXEC_TRANSLATE_UNSUPPORTED_NODE.getErrorCode(), e.getErrorCode());
        assertTrue(String.valueOf(e.getParam(ARG_CLASS_NAME)).contains("FutureExecutable"),
                "error must carry the offending node class name");
        Object location = e.getParam(ARG_LOCATION);
        assertNotNull(location, "error must carry the node SourceLocation");
        assertTrue(String.valueOf(location).contains("/fail-fast-test.xpl"),
                "location must map back to the source path: " + location);
        assertEquals("/fail-fast-test.xpl", e.getErrorLocation().getPath(), "loc() must be set for host-side visibility");
        assertEquals(3, e.getErrorLocation().getLine());
    }

    @Test
    public void testExcludedClassesFailFast() {
        // I4 改判排除类（边缘裁定）不在支持集：遇即 fail-fast（无第三态残留）
        for (String name : new String[]{"ReturnScopeValuesExecutable", "ExecutableFunctionEvalAction"}) {
            IExecutableExpression tree = CoverageNodes.excludedMinimalTree(name);
            NopEvalException e = assertThrows(NopEvalException.class,
                    () -> translator.translate("/fail-fast-test.xpl", TreeFingerprints.fingerprint(tree),
                            tree, null));
            assertEquals(ERR_EXEC_TRANSLATE_UNSUPPORTED_NODE.getErrorCode(), e.getErrorCode());
            assertTrue(String.valueOf(e.getParam(ARG_CLASS_NAME)).contains(name),
                    "error must report the excluded class name: " + name);
        }
    }

    @Test
    public void testCoverageFamiliesNowTranslatable() {
        // 边界收缩不回退守护：I5 期反例 GetProperty（I6 收缩）、I6 期反例 OutputText / If /
        // GenNode（I7 收缩）均已可翻译
        GetPropertyExecutable getProp = new GetPropertyExecutable(LOC,
                LiteralExecutable.build(LOC, "obj"), false, "length");
        assertNotNull(translator.translate("/fail-fast-test.xpl",
                TreeFingerprints.fingerprint(getProp), getProp, null).getRootNode().getBody());

        OutputTextExecutable output = new OutputTextExecutable(LOC, "text");
        assertNotNull(translator.translate("/fail-fast-test.xpl",
                TreeFingerprints.fingerprint(output), output, null).getRootNode().getBody());

        IfExecutable ifExpr = new IfExecutable(LOC, LiteralExecutable.build(LOC, Boolean.TRUE),
                LiteralExecutable.build(LOC, 1), LiteralExecutable.build(LOC, 2));
        assertNotNull(translator.translate("/fail-fast-test.xpl",
                TreeFingerprints.fingerprint(ifExpr), ifExpr, null).getRootNode().getBody());

        GenNodeExecutable genNode = new GenNodeExecutable(LOC, null,
                LiteralExecutable.build(LOC, "div"), new io.nop.xlang.exec.GenNodeAttrExecutable[0],
                null, null);
        assertNotNull(translator.translate("/fail-fast-test.xpl",
                TreeFingerprints.fingerprint(genNode), genNode, null).getRootNode().getBody());
    }

    @Test
    public void testSlotOutsideEntryFrameFailsFast() {
        // 纯表达式单元无程序入口帧：slot 读取无帧可落，帧映射层 fail-fast（报节点类名 + 位置）
        SlotIdentifierExecutable slotRead = new SlotIdentifierExecutable(LOC, "x", 0);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> translator.translate("/fail-fast-test.xpl", TreeFingerprints.fingerprint(slotRead),
                        slotRead, null));
        assertTrue(e.getMessage().contains(SlotIdentifierExecutable.class.getName()),
                "error must carry the offending node class name: " + e.getMessage());
        assertTrue(e.getMessage().contains("/fail-fast-test.xpl"),
                "error must carry the node SourceLocation: " + e.getMessage());
    }

    @Test
    public void testNestedOutOfSubsetNodeFailsFastWithoutPartialTranslation() {
        // 子集节点内嵌子集外节点：整体翻译失败（无部分翻译产物逃逸）
        IExecutableExpression tree = LiteralExecutable.build(LOC, 1);
        GetPropertyExecutable nested = new GetPropertyExecutable(LOC,
                new CoverageNodes.FutureExecutable(LOC), false, "prop");

        NopEvalException e = assertThrows(NopEvalException.class,
                () -> translator.translate("/fail-fast-test.xpl", TreeFingerprints.fingerprint(nested),
                        nested, null));
        // 深度优先翻译：最外层节点先注册自身，内嵌越界节点在递归中被拒
        assertNotNull(e.getParam(ARG_CLASS_NAME));
        assertEquals(ERR_EXEC_TRANSLATE_UNSUPPORTED_NODE.getErrorCode(), e.getErrorCode());
    }
}
