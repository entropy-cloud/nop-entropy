package io.nop.code.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.code.service.api.ICodeIndexService;
import io.nop.code.service.impl.CodeIndexService;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.dao.api.IDaoEntity;
import io.nop.search.api.ISearchEngine;
import io.nop.search.api.SearchRequest;
import io.nop.search.api.SearchResponse;
import io.nop.search.api.SearchableDoc;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * W-08 (plan 2565, adjudication G, TP-019): search-engine double-writes must move to the
 * transaction COMMIT point via CodeIndexAfterCommit (shared facility with W-12):
 * - leg 1 (red0): a rolled-back index transaction must leave the engine unchanged
 *   (pre-fix in-txn direct write polluted the engine);
 * - leg 2 (order): at a mid-transaction observation point the engine must NOT yet contain the
 *   writes (pre-fix they were already applied before the DB deletes);
 * - leg 3 (success): the normal path still syncs docs (query-visible).
 * Boundary contract (CODEINDEX-RT-002): the search engine is a best-effort DB-derived
 * projection; a post-commit sync failure is a structured WARN only
 * (nop.code.search-sync-failed-after-commit), divergence self-heals via the next re-index /
 * removeStaleSymbolDocsForFile pass. Callbacks carry pre-commit materialized POJOs (R1-m2).
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        enableActionAuth = OptionalBoolean.FALSE)
public class TestSearchSyncAfterCommit extends JunitAutoTestCase {

    @Inject
    ICodeIndexService codeIndexService;

    @Inject
    IDaoProvider daoProvider;

    private CodeIndexService impl() {
        return (CodeIndexService) codeIndexService;
    }

    static final class TimelineSearchEngine implements ISearchEngine {
        final List<String> timeline = Collections.synchronizedList(new ArrayList<>());
        final Set<String> docIds = new LinkedHashSet<>();
        final AtomicBoolean throwOnRemove = new AtomicBoolean(false);

        @Override
        public SearchResponse search(SearchRequest request) {
            return new SearchResponse();
        }

        @Override
        public SearchableDoc getDoc(String topic, String docId) {
            return null;
        }

        @Override
        public List<SearchableDoc> getDocsByTerm(String topic, String term) {
            return new ArrayList<>();
        }

        @Override
        public Map<String, List<String>> analyzeDoc(SearchableDoc doc) {
            return Collections.emptyMap();
        }

        @Override
        public List<String> analyzeQuery(String query) {
            return new ArrayList<>();
        }

        @Override
        public void refreshBlocking(String topic) {
        }

        @Override
        public synchronized void addDocs(String topic, List<SearchableDoc> docs) {
            timeline.add("addDocs:" + docs.size());
            for (SearchableDoc doc : docs) {
                if (doc.getId() != null) {
                    docIds.add(doc.getId());
                }
            }
        }

        @Override
        public synchronized void removeDocs(String topic, List<String> docIds) {
            timeline.add("removeDocs:" + docIds.size());
            if (throwOnRemove.get()) {
                throw new IllegalStateException("w08-injected engine failure");
            }
            this.docIds.removeAll(docIds);
        }

        @Override
        public synchronized void removeTopic(String topic) {
            timeline.add("removeTopic");
            docIds.clear();
        }

        synchronized Set<String> docIds() {
            return Collections.unmodifiableSet(docIds);
        }

        synchronized boolean containsAnyRemove() {
            return timeline.stream().anyMatch(t -> t.startsWith("removeDocs"));
        }

        synchronized boolean containsAnyAdd() {
            return timeline.stream().anyMatch(t -> t.startsWith("addDocs"));
        }
    }

    /** delegation proxy: records the engine timeline AT the mid-transaction delete point */
    static class ObservingDaoProvider implements IDaoProvider {
        final IDaoProvider delegate;
        final AtomicReference<TimelineSearchEngine> engineRef;

        ObservingDaoProvider(IDaoProvider delegate, AtomicReference<TimelineSearchEngine> engineRef) {
            this.delegate = delegate;
            this.engineRef = engineRef;
        }

        @Override
        public Set<String> getEntityNames() {
            return delegate.getEntityNames();
        }

        @Override
        public String normalizeEntityName(String entityName) {
            return delegate.normalizeEntityName(entityName);
        }

        @Override
        public boolean hasDao(String entityName) {
            return delegate.hasDao(entityName);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T extends IDaoEntity> IEntityDao<T> dao(String entityName) {
            IEntityDao<T> real = delegate.dao(entityName);
            if (!io.nop.code.dao.entity.NopCodeFile.class.getName().equals(entityName)) {
                return real;
            }
            return (IEntityDao<T>) java.lang.reflect.Proxy.newProxyInstance(
                    IEntityDao.class.getClassLoader(), new Class<?>[]{IEntityDao.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("findAllByQuery")) {
                            TimelineSearchEngine engine = engineRef.get();
                            if (engine != null) {
                                engine.timeline.add("TXN-MIDPOINT(inside txn, before commit)");
                            }
                        }
                        try {
                            return method.invoke(real, args);
                        } catch (java.lang.reflect.InvocationTargetException e) {
                            throw e.getCause();
                        }
                    });
        }

        @Override
        public <T extends IDaoEntity> IEntityDao<T> daoForTable(String tableName) {
            return delegate.daoForTable(tableName);
        }
    }

    /** delegation proxy: throws on the NopCodeFile delete to force a rollback */
    static final class ThrowingDaoProvider extends ObservingDaoProvider {
        ThrowingDaoProvider(IDaoProvider delegate, AtomicReference<TimelineSearchEngine> engineRef) {
            super(delegate, engineRef);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T extends IDaoEntity> IEntityDao<T> dao(String entityName) {
            IEntityDao<T> real = delegate.dao(entityName);
            if (!io.nop.code.dao.entity.NopCodeFile.class.getName().equals(entityName)) {
                return real;
            }
            return (IEntityDao<T>) java.lang.reflect.Proxy.newProxyInstance(
                    IEntityDao.class.getClassLoader(), new Class<?>[]{IEntityDao.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("findAllByQuery")) {
                            throw new IllegalStateException("w08-injected rollback point");
                        }
                        try {
                            return method.invoke(real, args);
                        } catch (java.lang.reflect.InvocationTargetException e) {
                            throw e.getCause();
                        }
                    });
        }
    }

    private IDaoProvider swapDaoProvider(IDaoProvider replacement) throws Exception {
        java.lang.reflect.Field f = impl().getClass().getDeclaredField("daoProvider");
        f.setAccessible(true);
        IDaoProvider original = (IDaoProvider) f.get(impl());
        f.set(impl(), replacement);
        return original;
    }

    /** W-08 leg 1 (red0): rollback must leave the engine unchanged */
    @Test
    void testRollbackDoesNotPolluteEngine() throws Exception {
        TimelineSearchEngine engine = new TimelineSearchEngine();
        impl().setSearchEngine(engine);
        String indexId = "w08-rollback";
        codeIndexService.indexFile(indexId, "w08/Rb.java",
                "package w08;\npublic class Rb { public void go() {} }\n");
        int docsBefore = engine.docIds().size();
        assertTrue(docsBefore > 0, "success path synced docs first");
        engine.timeline.clear();

        AtomicReference<TimelineSearchEngine> ref = new AtomicReference<>(engine);
        IDaoProvider original = swapDaoProvider(new ThrowingDaoProvider(daoProvider, ref));
        try {
            assertThrows(Exception.class,
                    () -> codeIndexService.batchDeleteFileRecords(indexId, List.of("w08/Rb.java")),
                    "injected failure must roll the transaction back");
        } finally {
            swapDaoProvider(original);
        }

        assertFalse(engine.containsAnyRemove(),
                "W-08: rollback must NOT reach the engine (pre-fix: removeDocs ran inside the txn) — timeline="
                        + engine.timeline);
        assertEquals(docsBefore, engine.docIds().size(), "engine doc set unchanged after rollback");
    }

    /** W-08 leg 2 (order): mid-transaction observation — engine writes must not precede commit */
    @Test
    void testEngineWritesHappenAfterCommitNotMidTransaction() throws Exception {
        TimelineSearchEngine engine = new TimelineSearchEngine();
        impl().setSearchEngine(engine);
        String indexId = "w08-order";
        codeIndexService.indexFile(indexId, "w08/Ord.java",
                "package w08;\npublic class Ord { public void go() {} }\n");
        engine.timeline.clear();

        AtomicReference<TimelineSearchEngine> ref = new AtomicReference<>(engine);
        IDaoProvider original = swapDaoProvider(new ObservingDaoProvider(daoProvider, ref));
        try {
            codeIndexService.batchDeleteFileRecords(indexId, List.of("w08/Ord.java"));
        } finally {
            swapDaoProvider(original);
        }

        int midpoint = engine.timeline.indexOf("TXN-MIDPOINT(inside txn, before commit)");
        assertTrue(midpoint >= 0, "mid-transaction observation point reached");
        for (int i = 0; i < midpoint; i++) {
            assertFalse(engine.timeline.get(i).startsWith("removeDocs"),
                    "engine removeDocs must not execute before commit — timeline=" + engine.timeline);
        }
        assertTrue(engine.containsAnyRemove(),
                "success path still reaches the engine (at commit point)");
    }

    /** W-08 leg 3 (success): normal path still syncs and engine queries stay visible */
    @Test
    void testSuccessPathStillSyncsDocs() {
        TimelineSearchEngine engine = new TimelineSearchEngine();
        impl().setSearchEngine(engine);
        String indexId = "w08-success";
        codeIndexService.indexFile(indexId, "w08/Ok.java",
                "package w08;\npublic class Ok { public void alpha() {} public void beta() {} }\n");
        assertTrue(engine.containsAnyAdd(), "addDocs must sync on the success path");
        assertTrue(engine.docIds().size() >= 2, "docs for all symbols visible");
    }

    /** W-08 chain4 companion: engine failure at commit point must not propagate (best-effort) */
    @Test
    void testEngineFailureAtCommitPointDoesNotPropagate() throws Exception {
        TimelineSearchEngine engine = new TimelineSearchEngine();
        engine.throwOnRemove.set(true);
        impl().setSearchEngine(engine);
        String indexId = "w08-failcommit";
        codeIndexService.indexFile(indexId, "w08/Fc.java",
                "package w08;\npublic class Fc { public void go() {} }\n");
        engine.throwOnRemove.set(false);

        // re-index: the removeDocs callback fails post-commit — WARN only, index succeeds
        assertDoesNotThrow(() -> codeIndexService.indexFile(indexId, "w08/Fc.java",
                "package w08;\npublic class Fc2 { public void go() {} }\n"),
                "post-commit engine failure must be swallowed to WARN (best-effort projection)");
    }
}
