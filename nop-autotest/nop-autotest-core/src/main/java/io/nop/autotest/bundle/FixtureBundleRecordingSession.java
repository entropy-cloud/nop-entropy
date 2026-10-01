package io.nop.autotest.bundle;

import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.autotest.core.execute.AutoTestOrmHook;
import io.nop.autotest.core.execute.EntityRow;
import io.nop.orm.IOrmEntity;
import io.nop.orm.IOrmSessionFactory;
import io.nop.orm.IOrmTemplate;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityModel;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import static io.nop.autotest.bundle.FixtureBundleErrors.ERR_FIXTURE_BUNDLE_SESSION_BUSY;

/**
 * Observed full-row recording session for fixture bundle export (M1.1; upgraded from
 * the M0.1 spike prototype per its Decision ② residual: JVM-exclusive via tryLock with
 * a semantic error code, guaranteed unregistration via try/finally).
 * <p>
 * The construction body runs inside the hook window; full-row reload happens AFTER the
 * window closes, in a fresh ORM session (Decision B reload semantics: fresh session
 * reads the DB state, avoiding in-session entity-cache hits that would miss DB-default
 * columns).
 */
public class FixtureBundleRecordingSession {
    private static final ReentrantLock EXCLUSIVE_LOCK = new ReentrantLock();

    private final AutoTestOrmHook hook = new AutoTestOrmHook();
    private IOrmSessionFactory sessionFactory;

    public void run(Consumer<AutoTestOrmHook> body) {
        if (!EXCLUSIVE_LOCK.tryLock()) {
            throw new NopException(ERR_FIXTURE_BUNDLE_SESSION_BUSY);
        }
        try {
            sessionFactory = (IOrmSessionFactory) BeanContainer.tryGetBean("nopOrmSessionFactory");
            try {
                sessionFactory.addDaoListener(hook);
                sessionFactory.addInterceptor(hook);

                // same context stamping as AutoTestCase.initDao (audit fields need a user)
                ContextProvider.getOrCreateContext().setUserId("autotest");

                body.accept(hook);
            } finally {
                sessionFactory.removeDaoListener(hook);
                sessionFactory.removeInterceptor(hook);
            }
        } finally {
            EXCLUSIVE_LOCK.unlock();
        }
    }

    /**
     * Collected rows grouped by entity model, keyed by {@code orm_idString()}
     * (composite PKs yield the escaped composite string; reload via
     * {@code orm.get(name, id)} is the exact inverse per OrmEntityHelper.castId).
     */
    public Map<IEntityModel, Map<String, EntityRow>> data() {
        return hook.getDataMap();
    }

    /**
     * Reload one row's full column image in a fresh ORM session. Returns null when the
     * row no longer exists in the DB (deleted within the construction session) — the
     * caller records it as a capture gap.
     */
    public Map<String, Object> reloadFullRow(IOrmTemplate orm, IEntityModel entityModel, String id) {
        IOrmEntity entity = orm.runInSession(session -> session.get(entityModel.getName(), id));
        if (entity == null)
            return null;

        Map<String, Object> row = new LinkedHashMap<>();
        for (IColumnModel col : entityModel.getColumns()) {
            row.put(col.getCode(), entity.orm_propValue(col.getPropId()));
        }
        return row;
    }
}
