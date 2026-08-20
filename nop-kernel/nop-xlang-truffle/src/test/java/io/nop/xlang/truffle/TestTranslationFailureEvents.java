package io.nop.xlang.truffle;

import io.nop.api.core.exceptions.NopEvalException;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.DisabledEvalOutput;
import io.nop.core.lang.eval.EvalScopeImpl;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.xlang.exec.LiteralExecutable;
import io.nop.xlang.truffle.eval.XLangTruffleEval;
import io.nop.xlang.truffle.lang.XLangLanguage;
import io.nop.xlang.truffle.runtime.XLangContextPool;
import io.nop.xlang.truffle.translate.TranslationCache;
import io.nop.xlang.truffle.translate.TranslationFailureEvent;
import io.nop.xlang.truffle.translate.TranslationFailureListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static io.nop.xlang.XLangErrors.ERR_EXEC_TRANSLATE_UNSUPPORTED_NODE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 单元级翻译失败观测事件单测（plan I8 Phase 2 Exit Criteria）：fail-fast 抛错路径同时记事件
 * （载荷 = sourceKey/节点类名/SourceLocation/原因）、已注册消费者被通知、无消费者时不静默
 * （内置记录器显式可查询）、池句柄注册通道可用（I9 truffle 侧 SPI 适配器消费路径）、
 * 直构缓存（language=null）路径维持纯 fail-fast 无记录。
 */
public class TestTranslationFailureEvents {

    private static final SourceLocation LOC = SourceLocation.fromLine("/fail-event.xpl", 3);

    private XLangTruffleEval eval;

    private XLangLanguage language;

    @BeforeEach
    public void setUp() {
        eval = new XLangTruffleEval();
        // 语言实例捕获：经一次成功求值的翻译产物根节点（parse 失败无产物，需先成功一次）
        XLangTruffleEval.TranslatedEval ok = eval.eval("/fail-event-ok.xpl",
                LiteralExecutable.build(LOC, 1), new EvalScopeImpl(), DisabledEvalOutput.INSTANCE);
        language = ok.getUnit().getRootNode().getXLangLanguage();
    }

    @AfterEach
    public void tearDown() {
        eval.close();
    }

    private XLangTruffleEval.TranslatedEval triggerFailure() {
        IExecutableExpression unsupported = new CoverageNodes.FutureExecutable(LOC);
        return eval.eval("/fail-event.xpl", unsupported, new EvalScopeImpl(), DisabledEvalOutput.INSTANCE);
    }

    @Test
    public void testFailFastStillThrownAndEventRecorded() {
        language.getTranslationFailures().clear();
        XLangTruffleEval.TranslatedEval result = triggerFailure();

        // fail-fast 主语义不变（观测增量不是降级）
        Throwable thrown = result.getThrown();
        assertNotNull(thrown, "unsupported node must fail fast");
        assertTrue(thrown instanceof NopEvalException, "thrown must be the fail-fast NopEvalException: " + thrown);
        assertEquals(ERR_EXEC_TRANSLATE_UNSUPPORTED_NODE.getErrorCode(), ((NopEvalException) thrown).getErrorCode());

        // 事件已记录且载荷完整（无消费者时不静默——内置记录器恒记）
        assertTrue(language.getTranslationFailures().hasEvents(),
                "failure must be recorded even without registered consumers");
        TranslationFailureEvent event = language.getTranslationFailures().getEvents().get(0);
        assertEquals("/fail-event.xpl", event.getSourceKey());
        assertNotNull(event.getNodeClassName(), "event must carry the node class name");
        assertTrue(event.getNodeClassName().contains("FutureExecutable"),
                "node class name must identify the unsupported node: " + event.getNodeClassName());
        assertNotNull(event.getSourceLocation(), "event must carry the node SourceLocation");
        assertEquals("/fail-event.xpl", event.getSourceLocation().getPath());
        assertNotNull(event.getReason(), "event must carry a reason");
        assertNotNull(event.getCause(), "event must carry the original exception");
    }

    @Test
    public void testRegisteredConsumerNotifiedWithPayload() {
        language.getTranslationFailures().clear();
        AtomicReference<TranslationFailureEvent> received = new AtomicReference<>();
        TranslationFailureListener listener = received::set;
        language.addTranslationFailureListener(listener);

        triggerFailure();

        TranslationFailureEvent event = received.get();
        assertNotNull(event, "registered consumer must be notified");
        assertEquals("/fail-event.xpl", event.getSourceKey());
        assertTrue(event.getNodeClassName().contains("FutureExecutable"));
        // 内置记录器同时记录（消费者与记录器并存）
        assertEquals(1, language.getTranslationFailures().getEvents().size());
    }

    @Test
    public void testPoolLanguageHandleRegistersConsumer() {
        try (XLangContextPool pool = XLangContextPool.open(1)) {
            XLangLanguage poolLanguage = pool.getLanguage();
            poolLanguage.getTranslationFailures().clear();
            AtomicReference<TranslationFailureEvent> received = new AtomicReference<>();
            poolLanguage.addTranslationFailureListener(received::set);

            IExecutableExpression unsupported = new CoverageNodes.FutureExecutable(LOC);
            try (XLangContextPool.Lease lease = pool.lease()) {
                XLangTruffleEval.TranslatedEval result = lease.eval("/fail-event-pool.xpl", unsupported,
                        new EvalScopeImpl(), DisabledEvalOutput.INSTANCE);
                assertNotNull(result.getThrown(), "pool path must fail fast too");
            }
            // I9 truffle 侧 SPI 适配器消费路径：经池句柄取得语言实例注册消费者 → 收到事件
            assertNotNull(received.get(), "pool language handle must support consumer registration");
            assertEquals("/fail-event-pool.xpl", received.get().getSourceKey());
            assertEquals(poolLanguage, pool.getLanguage(), "language instance is stable on the pool");
        }
    }

    @Test
    public void testDirectCacheWithoutLanguageStaysPureFailFast() {
        TranslationCache cache = new TranslationCache(4);
        IExecutableExpression unsupported = new CoverageNodes.FutureExecutable(LOC);
        assertThrows(NopEvalException.class,
                () -> cache.getOrBuild("/fail-event-direct.xpl", unsupported, null));
        assertNull(cache.peek("/fail-event-direct.xpl", 0),
                "failed translation must not be cached");
    }
}
