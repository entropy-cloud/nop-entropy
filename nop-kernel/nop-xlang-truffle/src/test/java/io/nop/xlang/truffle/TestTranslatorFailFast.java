package io.nop.xlang.truffle;

import io.nop.api.core.exceptions.NopEvalException;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IExecutableExpression;
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
 * 禁止部分翻译。覆盖两类越界形态：
 *
 * <ul>
 * <li>支持集外节点类（I6 后 A 族已并入支持集，反例取 B 族：输出族 OutputTextExecutable /
 * 控制流族 IfExecutable——全量落实归 I7）；</li>
 * <li>支持集内节点类的越域用法（slot 读写越出程序入口帧——slot 下标不在帧布局内）。</li>
 * </ul>
 */
public class TestTranslatorFailFast {

    private static final SourceLocation LOC = SourceLocation.fromLine("/fail-fast-test.xpl", 3, 7);

    private final ExecToTruffleTranslator translator = new ExecToTruffleTranslator();

    @Test
    public void testOutOfSubsetNodeFailsFastWithClassNameAndLocation() {
        OutputTextExecutable output = new OutputTextExecutable(LOC, "text");

        NopEvalException e = assertThrows(NopEvalException.class,
                () -> translator.translate("/fail-fast-test.xpl", TreeFingerprints.fingerprint(output),
                        output, null));

        assertEquals(ERR_EXEC_TRANSLATE_UNSUPPORTED_NODE.getErrorCode(), e.getErrorCode());
        assertEquals(OutputTextExecutable.class.getName(), e.getParam(ARG_CLASS_NAME),
                "error must carry the offending node class name");
        Object location = e.getParam(ARG_LOCATION);
        assertNotNull(location, "error must carry the node SourceLocation");
        assertTrue(String.valueOf(location).contains("/fail-fast-test.xpl"),
                "location must map back to the source path: " + location);
        assertEquals("/fail-fast-test.xpl", e.getErrorLocation().getPath(), "loc() must be set for host-side visibility");
        assertEquals(3, e.getErrorLocation().getLine());
    }

    @Test
    public void testControlFlowFamilyFailsFastToo() {
        // ExitMode 控制流族不在支持集内（全量落实归 I7），遇到即 fail-fast
        IfExecutable ifExpr = new IfExecutable(LOC, LiteralExecutable.build(LOC, Boolean.TRUE),
                LiteralExecutable.build(LOC, 1), LiteralExecutable.build(LOC, 2));

        NopEvalException e = assertThrows(NopEvalException.class,
                () -> translator.translate("/fail-fast-test.xpl", TreeFingerprints.fingerprint(ifExpr),
                        ifExpr, null));
        assertEquals(ERR_EXEC_TRANSLATE_UNSUPPORTED_NODE.getErrorCode(), e.getErrorCode());
        assertEquals(IfExecutable.class.getName(), e.getParam(ARG_CLASS_NAME));
    }

    @Test
    public void testCoverageAFamilyNowTranslatable() {
        // I6 边界收缩反证：A 族代表节点（属性访问 GetProperty）已可翻译（I5 期以本类为 fail-fast
        // 反例，I6 后反例移交 B 族，本用例守护边界收缩不回退）
        GetPropertyExecutable getProp = new GetPropertyExecutable(LOC,
                LiteralExecutable.build(LOC, "obj"), false, "length");
        io.nop.xlang.truffle.translate.TranslatedUnit unit = translator.translate("/fail-fast-test.xpl",
                TreeFingerprints.fingerprint(getProp), getProp, null);
        assertNotNull(unit.getRootNode().getBody());
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
                new IfExecutable(LOC, tree, tree, tree), false, "prop");

        NopEvalException e = assertThrows(NopEvalException.class,
                () -> translator.translate("/fail-fast-test.xpl", TreeFingerprints.fingerprint(nested),
                        nested, null));
        // 深度优先翻译：最外层节点先注册自身，内嵌越界节点在递归中被拒
        assertNotNull(e.getParam(ARG_CLASS_NAME));
        assertEquals(ERR_EXEC_TRANSLATE_UNSUPPORTED_NODE.getErrorCode(), e.getErrorCode());
    }
}
