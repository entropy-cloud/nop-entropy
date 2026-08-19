package io.nop.xlang.truffle;

import com.oracle.truffle.api.RootCallTarget;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.EvalScopeImpl;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.DisabledEvalOutput;
import io.nop.xlang.exec.CallFuncExecutable;
import io.nop.xlang.exec.LiteralExecutable;
import io.nop.xlang.exec.SeqExecutable;
import io.nop.xlang.exec.SlotAssignExecutable;
import io.nop.xlang.exec.SlotIdentifierExecutable;
import io.nop.xlang.truffle.eval.EvalHandoff;
import io.nop.xlang.truffle.eval.XLangTruffleEval;
import io.nop.xlang.truffle.nodes.XLangRootNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.graalvm.polyglot.PolyglotException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 接线验证（Phase 2）：Language.parse → 翻译缓存查找/构建 → CallTarget 获取链路可被触发
 * （最小冒烟树；子集全翻译与对拍矩阵归 Phase 3）。
 */
public class TestLanguageWiringSmoke {

    private static final SourceLocation LOC = SourceLocation.fromPath("/wiring-smoke.xpl");

    private XLangTruffleEval eval;

    @BeforeEach
    public void setUp() {
        eval = new XLangTruffleEval();
    }

    @AfterEach
    public void tearDown() {
        eval.close();
    }

    @Test
    public void testParseCacheCallTargetChainLiteral() {
        IExecutableExpression tree = LiteralExecutable.build(LOC, 42);
        IEvalScope scope = new EvalScopeImpl();

        XLangTruffleEval.TranslatedEval result = eval.eval("/wiring-smoke.xpl", tree, scope,
                DisabledEvalOutput.INSTANCE);
        assertEquals(42, result.getReturnValue());

        XLangRootNode root = result.getUnit().getRootNode();
        assertNotNull(root, "parse must resolve the translated unit into the handoff record");
        assertNotSame(tree, root, "executed artifact must be the translated AST, not the interpreter tree");
        RootCallTarget callTarget = result.getUnit().getCallTarget();
        assertNotNull(callTarget);
        assertSame(root, callTarget.getRootNode(), "CallTarget must belong to the translated root node");
    }

    @Test
    public void testTranslationCacheIdentityAcrossEvals() {
        IExecutableExpression tree = LiteralExecutable.build(LOC, 42);
        IEvalScope scope = new EvalScopeImpl();

        XLangTruffleEval.TranslatedEval first = eval.eval("/wiring-smoke.xpl", tree, scope,
                DisabledEvalOutput.INSTANCE);
        XLangTruffleEval.TranslatedEval second = eval.eval("/wiring-smoke.xpl", tree, scope,
                DisabledEvalOutput.INSTANCE);
        assertEquals(42, first.getReturnValue());
        assertEquals(42, second.getReturnValue());
        assertSame(first.getUnit().getCallTarget(), second.getUnit().getCallTarget(),
                "same sourceKey + same tree fingerprint must hit the cached CallTarget");
        assertSame(first.getUnit(), second.getUnit());
    }

    @Test
    public void testProgramEntryFrameAndSlotsRoundTrip() {
        // let x = 5; x —— 程序入口帧 + slot 写读（kind 推断 Int 的 typed 通路）
        IExecutableExpression body = SeqExecutable.valueOf(LOC, new IExecutableExpression[]{
                new SlotAssignExecutable(LOC, "x", 0, LiteralExecutable.build(LOC, 5)),
                new SlotIdentifierExecutable(LOC, "x", 0)});
        IExecutableExpression tree = new CallFuncExecutable(LOC, "wiring", new String[]{"x"},
                new IExecutableExpression[0], body);

        XLangTruffleEval.TranslatedEval result = eval.eval("/wiring-smoke-entry.xpl", tree,
                new EvalScopeImpl(), DisabledEvalOutput.INSTANCE);
        assertEquals(5, result.getReturnValue());
    }

    @Test
    public void testEvalWithoutPendingTreeFailsFast() {
        try (XLangTruffleEval bare = new XLangTruffleEval()) {
            // 不经 facade handoff 直接 eval 合成 Source：parse 必须 fail-fast，不得静默空翻译
            PolyglotException e = assertThrows(PolyglotException.class,
                    () -> bare.getContext().eval(org.graalvm.polyglot.Source
                            .newBuilder("xl", "", "/no-pending.xpl").build()));
            assertTrue(e.getMessage().contains("no pending tree") || e.isHostException(),
                    "parse must fail fast on missing handoff: " + e.getMessage());
        }
    }

    @Test
    public void testHandoffWindowClearedAfterEval() {
        IExecutableExpression tree = LiteralExecutable.build(LOC, 1);
        eval.eval("/wiring-smoke-clear.xpl", tree, new EvalScopeImpl(), DisabledEvalOutput.INSTANCE);
        PolyglotException e = assertThrows(PolyglotException.class,
                () -> eval.getContext().eval(org.graalvm.polyglot.Source
                        .newBuilder("xl", "", "/stale.xpl").build()));
        assertTrue(e.getMessage().contains("no pending tree") || e.isHostException());
    }
}
