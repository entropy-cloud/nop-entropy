package io.nop.xlang.backend;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.nop.api.core.util.SourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Set;

import static io.nop.xlang.backend.BackendTestFakes.FAKE_DYNAMIC_ID;
import static io.nop.xlang.backend.BackendTestFakes.FakeDynamicBackend;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 降级观测命名契约测试：WARN 日志断言（logback ListAppender，仓内先例载体）+ 指标计数断言
 * （GlobalMeterRegistry counter + tags）+ 不可用条目查询断言。
 */
public class TestEvalBackendObservation {

    private ListAppender<ILoggingEvent> appender;

    private Logger observationLogger;

    @BeforeEach
    public void setUp() {
        observationLogger = (Logger) LoggerFactory.getLogger(EvalBackendObservation.class);
        appender = new ListAppender<>();
        appender.start();
        observationLogger.addAppender(appender);
    }

    @AfterEach
    public void tearDown() {
        observationLogger.detachAppender(appender);
        appender.stop();
        EvalBackendRegistry registry = EvalBackendRegistry.instance();
        for (String id : Set.copyOf(registry.getBackendIds()))
            registry.unregister(registry.getBackend(id));
    }

    @Test
    public void testWarnLogContractOnDegradation() {
        SourceLocation loc = SourceLocation.fromPath("t:/obs/degrade.expr");
        double before = EvalBackendObservation.degradationCount(FAKE_DYNAMIC_ID,
                EvalBackendObservation.REASON_UNIT_TRANSLATION_FAILURE);

        EvalBackendObservation.onDegradation(FAKE_DYNAMIC_ID,
                EvalBackendObservation.REASON_UNIT_TRANSLATION_FAILURE, "t:/obs/degrade.expr", loc);

        assertEquals(1.0, EvalBackendObservation.degradationCount(FAKE_DYNAMIC_ID,
                EvalBackendObservation.REASON_UNIT_TRANSLATION_FAILURE) - before, 1e-9);

        assertEquals(1, appender.list.size());
        ILoggingEvent event = appender.list.get(0);
        assertEquals(Level.WARN, event.getLevel());
        String message = event.getFormattedMessage();
        // 命名契约：消息键 + backend/reason/sourceKey 三段格式
        assertTrue(message.contains(EvalBackendObservation.LOG_MESSAGE_KEY), message);
        assertTrue(message.contains("backend=" + FAKE_DYNAMIC_ID), message);
        assertTrue(message.contains("reason=" + EvalBackendObservation.REASON_UNIT_TRANSLATION_FAILURE), message);
        assertTrue(message.contains("sourceKey=t:/obs/degrade.expr"), message);
    }

    @Test
    public void testMetricTagsSeparateReasons() {
        double disabledBefore = EvalBackendObservation.degradationCount(FAKE_DYNAMIC_ID,
                EvalBackendObservation.REASON_CONFIG_DISABLED);
        double unavailableBefore = EvalBackendObservation.degradationCount(FAKE_DYNAMIC_ID,
                EvalBackendObservation.REASON_UNAVAILABLE);

        EvalBackendObservation.onDegradation(FAKE_DYNAMIC_ID, EvalBackendObservation.REASON_CONFIG_DISABLED,
                "k1", null);
        EvalBackendObservation.onDegradation(FAKE_DYNAMIC_ID, EvalBackendObservation.REASON_CONFIG_DISABLED,
                "k2", null);

        assertEquals(2.0, EvalBackendObservation.degradationCount(FAKE_DYNAMIC_ID,
                EvalBackendObservation.REASON_CONFIG_DISABLED) - disabledBefore, 1e-9);
        assertEquals(0.0, EvalBackendObservation.degradationCount(FAKE_DYNAMIC_ID,
                EvalBackendObservation.REASON_UNAVAILABLE) - unavailableBefore, 1e-9);
    }

    @Test
    public void testUnavailableEntryQueriedThroughRegistry() {
        FakeDynamicBackend unavailable = new FakeDynamicBackend();
        unavailable.markUnavailable("engine-init-failed: injected");
        EvalBackendRegistry.instance().register(unavailable);

        assertEquals(Collections.singletonMap(FAKE_DYNAMIC_ID, "engine-init-failed: injected"),
                EvalBackendRegistry.instance().getUnavailableBackends());
        assertFalse(EvalBackendRegistry.instance().getUnavailableBackends().isEmpty());
    }
}
