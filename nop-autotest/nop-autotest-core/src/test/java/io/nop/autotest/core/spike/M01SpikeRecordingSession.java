package io.nop.autotest.core.spike;

import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.autotest.core.execute.AutoTestOrmHook;
import io.nop.autotest.core.execute.EntityRow;
import io.nop.orm.IOrmSessionFactory;
import io.nop.orm.model.IEntityModel;

import java.util.Map;
import java.util.function.Consumer;

/**
 * M0.1 fixture-bundle spike prototype A (nop-app-erp plan 2026-10-01-1853-1).
 * <p>
 * A standalone recording session that replicates the essential lines of
 * AutoTestCase.initDao/complete without extending AutoTestCase: it registers a fresh
 * AutoTestOrmHook on the container-level shared IOrmSessionFactory, runs the supplied
 * construction body, and unregisters the hook in a try/finally block. This proves
 * Decision ① (recording session reuse without caseData / initDao coupling) and
 * Decision ② (session exclusivity + guaranteed unregistration) at the platform level.
 * <p>
 * Spike-only: not thread-safe by design; the exclusivity contract is single-session
 * per JVM (enforced here with a JVM-level lock acquired for the whole session).
 */
public class M01SpikeRecordingSession {
    private static final Object EXCLUSIVE_LOCK = new Object();

    private final AutoTestOrmHook hook = new AutoTestOrmHook();
    private IOrmSessionFactory sessionFactory;

    public void run(Consumer<AutoTestOrmHook> body) {
        // Decision ②: export session is exclusive within the JVM — fail-fast instead of queueing
        synchronized (EXCLUSIVE_LOCK) {
            sessionFactory = (IOrmSessionFactory) BeanContainer.tryGetBean("nopOrmSessionFactory");
            try {
                sessionFactory.addDaoListener(hook);
                sessionFactory.addInterceptor(hook);

                // same context stamping as AutoTestCase.initDao (audit fields need a user)
                ContextProvider.getOrCreateContext().setUserId("autotest");

                body.accept(hook);
            } finally {
                // Decision ①: unregistration is guaranteed even when the body throws
                sessionFactory.removeDaoListener(hook);
                sessionFactory.removeInterceptor(hook);
            }
        }
    }

    /** Collected rows grouped by entity model, keyed by orm_idString. */
    public Map<IEntityModel, Map<String, EntityRow>> data() {
        return hook.getDataMap();
    }
}
