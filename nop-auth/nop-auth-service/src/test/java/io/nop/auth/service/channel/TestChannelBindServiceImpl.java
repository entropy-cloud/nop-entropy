package io.nop.auth.service.channel;

import io.nop.api.core.exceptions.NopException;
import io.nop.auth.api.bind.BindStartResult;
import io.nop.auth.api.bind.ChannelBindingInfo;
import io.nop.auth.dao.entity.NopAuthExtLogin;
import io.nop.dao.api.IDaoEntity;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.integration.api.bind.BindTicket;
import io.nop.integration.api.bind.BindTicketStatus;
import io.nop.integration.api.bind.ChannelBindResult;
import io.nop.integration.api.bind.ChannelBindResultStatus;
import io.nop.integration.api.bind.ChannelScanCallback;
import io.nop.integration.api.bind.IChannelBindProvider;
import io.nop.integration.api.channel.ChannelTypeCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 3 unit tests for {@link ChannelBindServiceImpl}. Verifies the five
 * {@link io.nop.auth.api.bind.IChannelBindService} methods against an
 * in-memory mock DAO (JDK {@link Proxy} over {@link IEntityDao}, mirroring
 * the W2 {@code TestUserChannelResolver} pattern) and a stub
 * {@link IChannelBindProvider} that records calls and returns canned
 * {@link BindTicket}s.
 *
 * <p><b>Coverage map</b> (one test per Phase 3 Exit Criterion item):
 * <ul>
 *   <li>{@code completeBinding} write + {@code listBindings}/{@code findBinding}
 *       return it (effective) — {@link #completeBindingWritesAndListsAndFinds}</li>
 *   <li>same-user rescan idempotent — {@link #completeBindingSameUserRescanIsIdempotent}</li>
 *   <li>soft-deleted row physically removed before insert —
 *       {@link #completeBindingAfterUnbindPhysicallyDeletesAndReinserts}</li>
 *   <li>cross-user rebind physically deletes prior user's row —
 *       {@link #completeBindingCrossUserRebindPhysicallyDeletesAndInserts}</li>
 *   <li>{@code unbind} soft-deletes; later list/find no longer see it —
 *       {@link #unbindSoftDeletesAndHidesBinding}</li>
 *   <li>{@code findBinding} miss returns null (no exception) —
 *       {@link #findBindingMissReturnsNull}</li>
 *   <li>{@code startBinding} routes through provider and carries real QR
 *       payload — {@link #startBindingRoutesThroughProvider}</li>
 *   <li>{@code onChannelScanCallback} provider collaborator path —
 *       {@link #providerScanCallbackCollaboration}</li>
 *   <li>unknown channelType throws — {@link #unknownChannelTypeThrows}</li>
 *   <li>missing provider throws — {@link #startBindingWithoutProviderThrows}</li>
 * </ul>
 *
 * <p><b>Wiring verification (Minimum Rules #23)</b>: the service is
 * constructed, its {@code daoProvider} and {@code channelBindProviders}
 * dependencies are injected via setters (mirror Nop IoC), and the mock
 * DAO actually stores inserted rows so subsequent reads see them — proving
 * the service&rarr;DAO wiring is real at runtime, not just type-level.
 */
public class TestChannelBindServiceImpl {

    private static final String USER_A = "user-A";
    private static final String USER_B = "user-B";
    private static final String FEISHU_OPEN_ID = "feishu-open-id-1";

    private InMemoryDao dao;
    private ChannelBindServiceImpl service;
    private StubBindProvider feishuProvider;

    @BeforeEach
    void setUp() {
        dao = new InMemoryDao();
        service = new ChannelBindServiceImpl();
        service.setDaoProvider(daoProviderFor(dao));
        feishuProvider = new StubBindProvider(ChannelTypeCodes.CHANNEL_TYPE_FEISHU, "feishu-qr-payload");
        service.setChannelBindProviders(Collections.singletonList(feishuProvider));
    }

    @Test
    void completeBindingWritesAndListsAndFinds() {
        ChannelBindingInfo saved = service.completeBinding(
                ChannelTypeCodes.CHANNEL_TYPE_FEISHU, USER_A, FEISHU_OPEN_ID);

        assertNotNull(saved.getBindingId(), "completeBinding must populate bindingId (=NopAuthExtLogin.sid)");
        assertEquals(ChannelTypeCodes.CHANNEL_TYPE_FEISHU, saved.getChannelType());
        assertEquals(FEISHU_OPEN_ID, saved.getExtId());
        assertEquals(USER_A, saved.getPlatformUserId());

        // listBindings for the user returns exactly this effective binding
        List<ChannelBindingInfo> list = service.listBindings(USER_A);
        assertEquals(1, list.size());
        assertEquals(saved.getBindingId(), list.get(0).getBindingId());

        // findBinding by (channelType, extId) hits and carries platformUserId
        ChannelBindingInfo found = service.findBinding(ChannelTypeCodes.CHANNEL_TYPE_FEISHU, FEISHU_OPEN_ID);
        assertNotNull(found);
        assertEquals(USER_A, found.getPlatformUserId());
    }

    @Test
    void completeBindingSameUserRescanIsIdempotent() {
        ChannelBindingInfo first = service.completeBinding(
                ChannelTypeCodes.CHANNEL_TYPE_FEISHU, USER_A, FEISHU_OPEN_ID);

        // The user scans again; the existing effective binding is returned
        // unchanged — no second row, no row replacement.
        ChannelBindingInfo second = service.completeBinding(
                ChannelTypeCodes.CHANNEL_TYPE_FEISHU, USER_A, FEISHU_OPEN_ID);

        assertEquals(first.getBindingId(), second.getBindingId(),
                "same-user rescan must return the existing binding id");
        assertEquals(1, dao.rows.size(),
                "no duplicate row should be inserted for idempotent rescan");
    }

    @Test
    void completeBindingAfterUnbindPhysicallyDeletesAndReinserts() {
        // First binding + unbind (soft-delete)
        ChannelBindingInfo first = service.completeBinding(
                ChannelTypeCodes.CHANNEL_TYPE_FEISHU, USER_A, FEISHU_OPEN_ID);
        service.unbind(first.getBindingId());

        // The soft-deleted row occupies the unique-index slot; rebinding the
        // same extId (same user) must physically delete it before inserting.
        ChannelBindingInfo rebound = service.completeBinding(
                ChannelTypeCodes.CHANNEL_TYPE_FEISHU, USER_A, FEISHU_OPEN_ID);

        assertNotEquals(first.getBindingId(), rebound.getBindingId(),
                "rebind after unbind must produce a NEW row, not reuse the soft-deleted one");
        assertEquals(1, countRowsForExtId(FEISHU_OPEN_ID),
                "exactly one row for extId after rebind (old soft-deleted physically removed)");
        // New row is effective
        Byte delFlag = dao.findById(rebound.getBindingId()).getDelFlag();
        assertEquals((byte) 0, delFlag, "rebind row must be active (delFlag=0)");
    }

    @Test
    void completeBindingCrossUserRebindPhysicallyDeletesAndInserts() {
        // User A binds extId X
        ChannelBindingInfo first = service.completeBinding(
                ChannelTypeCodes.CHANNEL_TYPE_FEISHU, USER_A, FEISHU_OPEN_ID);
        service.unbind(first.getBindingId());
        // Now extId X has a soft-deleted row owned by A.

        // User B binds the same extId X. A's soft-deleted row must be
        // physically deleted so the unique index allows B's insert.
        ChannelBindingInfo bBinding = service.completeBinding(
                ChannelTypeCodes.CHANNEL_TYPE_FEISHU, USER_B, FEISHU_OPEN_ID);

        assertEquals(USER_B, bBinding.getPlatformUserId());
        assertEquals(1, countRowsForExtId(FEISHU_OPEN_ID),
                "exactly one row for extId after cross-user rebind");
        assertEquals(USER_B, dao.findById(bBinding.getBindingId()).getUserId(),
                "new row owned by user B, not A");
    }

    @Test
    void unbindSoftDeletesAndHidesBinding() {
        ChannelBindingInfo saved = service.completeBinding(
                ChannelTypeCodes.CHANNEL_TYPE_FEISHU, USER_A, FEISHU_OPEN_ID);

        service.unbind(saved.getBindingId());

        // listBindings no longer returns it
        assertTrue(service.listBindings(USER_A).isEmpty(),
                "listBindings must exclude soft-deleted bindings");
        // findBinding no longer hits
        assertNull(service.findBinding(ChannelTypeCodes.CHANNEL_TYPE_FEISHU, FEISHU_OPEN_ID),
                "findBinding must miss soft-deleted binding");
        // The row still exists in the table with delFlag=1 (audit)
        NopAuthExtLogin row = dao.findById(saved.getBindingId());
        assertNotNull(row);
        assertEquals((byte) 1, row.getDelFlag());
    }

    @Test
    void findBindingMissReturnsNull() {
        // findBinding on never-bound extId returns null (not exception)
        assertNull(service.findBinding(ChannelTypeCodes.CHANNEL_TYPE_FEISHU, "never-bound"));
        // findBinding on unknown channelType also returns null — wait: it
        // throws because the channelType cannot be mapped to loginType. We
        // assert the throw here separately to make the policy explicit.
        assertThrows(NopException.class,
                () -> service.findBinding("no-such-channel-type", "any"));
    }

    @Test
    void startBindingRoutesThroughProvider() {
        BindStartResult result = service.startBinding(ChannelTypeCodes.CHANNEL_TYPE_FEISHU, USER_A);

        // The provider was really called (count > 0)
        assertEquals(1, feishuProvider.createTicketCount.get(),
                "startBinding must invoke provider.createBindTicket");
        // The QR payload from the provider flows through to the result
        // (not a constant — proves the provider&rarr;service translation is real)
        assertEquals("feishu-qr-payload", result.getQrPayload());
        assertNotNull(result.getTicketId());
        assertNotNull(result.getExpiresAt());
    }

    @Test
    void providerScanCallbackCollaboration() {
        // Verify the stub provider's onChannelScanCallback returns extId,
        // which the service would then pass to completeBinding. We exercise
        // the full path: start binding -> simulate channel scan callback
        // -> complete binding using the returned extId.
        BindStartResult started = service.startBinding(
                ChannelTypeCodes.CHANNEL_TYPE_FEISHU, USER_A);

        // Simulate the channel scan callback arriving: build a callback with
        // the ticketId from startBinding and a vendor payload containing the
        // channel user id.
        ChannelScanCallback callback = new ChannelScanCallback();
        callback.setChannelType(ChannelTypeCodes.CHANNEL_TYPE_FEISHU);
        callback.setTicketId(started.getTicketId());
        Map<String, Object> payload = new HashMap<>();
        payload.put("open_id", FEISHU_OPEN_ID);
        callback.setRawPayload(payload);

        ChannelBindResult scanResult = feishuProvider.onChannelScanCallback(callback);
        assertEquals(ChannelBindResultStatus.BINDING_COMPLETED, scanResult.getStatus());
        assertEquals(FEISHU_OPEN_ID, scanResult.getExtId());

        // The platform would now call completeBinding with the extId from the
        // scan result (this is the design §3.4 ② two-step separation).
        ChannelBindingInfo bound = service.completeBinding(
                ChannelTypeCodes.CHANNEL_TYPE_FEISHU, USER_A, scanResult.getExtId());
        assertNotNull(bound);
        assertEquals(USER_A, bound.getPlatformUserId());
        assertEquals(1, feishuProvider.scanCallbackCount.get());
    }

    @Test
    void unknownChannelTypeThrows() {
        // completeBinding with a channelType that has no integer mapping
        // throws explicitly (no silent no-op)
        assertThrows(NopException.class,
                () -> service.completeBinding("no-such-channel-type", USER_A, FEISHU_OPEN_ID));
    }

    @Test
    void startBindingWithoutProviderThrows() {
        // A service with no provider for the requested channelType must
        // throw — no silent null return from startBinding.
        ChannelBindServiceImpl bareService = new ChannelBindServiceImpl();
        bareService.setDaoProvider(daoProviderFor(new InMemoryDao()));
        bareService.setChannelBindProviders(Collections.emptyList());

        assertThrows(NopException.class,
                () -> bareService.startBinding(ChannelTypeCodes.CHANNEL_TYPE_FEISHU, USER_A));
    }

    @Test
    void listBindingsExcludesNonChannelRows() {
        // A password row (loginType=1) should never appear in listBindings
        // — the toInfo mapper filters it via ChannelTypeCodes.channelType.
        NopAuthExtLogin password = new NopAuthExtLogin();
        password.setSid("pwd-row");
        password.setUserId(USER_A);
        password.setLoginType(1);
        password.setExtId("pwd-ext");
        password.setVerified(Boolean.TRUE);
        password.setDelFlag((byte) 0);
        dao.rows.add(password);

        // Add an effective feishu binding too so the list isn't empty
        service.completeBinding(ChannelTypeCodes.CHANNEL_TYPE_FEISHU, USER_A, FEISHU_OPEN_ID);

        List<ChannelBindingInfo> list = service.listBindings(USER_A);
        assertEquals(1, list.size(), "non-channel (password) row must be filtered out");
        assertEquals(ChannelTypeCodes.CHANNEL_TYPE_FEISHU, list.get(0).getChannelType());
    }

    @Test
    void listBindingsEmptyForUnknownUser() {
        assertTrue(service.listBindings("never-existed").isEmpty());
        assertTrue(service.listBindings(null).isEmpty());
        assertTrue(service.listBindings("").isEmpty());
    }

    // ---- helpers -----------------------------------------------------------

    private int countRowsForExtId(String extId) {
        int count = 0;
        for (NopAuthExtLogin row : dao.rows) {
            if (extId.equals(row.getExtId())) {
                count++;
            }
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private static IDaoProvider daoProviderFor(IEntityDao<NopAuthExtLogin> dao) {
        return new IDaoProvider() {
            @Override
            public Set<String> getEntityNames() {
                return Collections.singleton(NopAuthExtLogin.class.getName());
            }

            @Override
            public String normalizeEntityName(String entityName) {
                return entityName;
            }

            @Override
            public boolean hasDao(String entityName) {
                return NopAuthExtLogin.class.getName().equals(entityName);
            }

            @Override
            public <T extends IDaoEntity> IEntityDao<T> dao(String entityName) {
                return (IEntityDao<T>) dao;
            }

            @Override
            public <T extends IDaoEntity> IEntityDao<T> daoForTable(String tableName) {
                throw new UnsupportedOperationException("daoForTable not used");
            }
        };
    }

    /**
     * Minimal in-memory DAO that supports the methods used by
     * {@link ChannelBindServiceImpl}. Backed by a {@link List} of
     * {@link NopAuthExtLogin} rows. Example matching is a simple non-null
     * field-equality match, with one extra rule: if
     * {@code example.orm_disableLogicalDelete()} is {@code true}, the
     * {@code delFlag} filter is NOT applied (mirrors the real
     * {@code EntityPersisterImpl.addDeleteFlagToExample}); otherwise
     * soft-deleted rows ({@code delFlag != 0}) are excluded.
     */
    private static class InMemoryDao implements IEntityDao<NopAuthExtLogin> {
        final List<NopAuthExtLogin> rows = new ArrayList<>();
        private final AtomicInteger idSeq = new AtomicInteger();

        @Override
        public String getEntityName() {
            return NopAuthExtLogin.class.getName();
        }

        @Override
        public String getTableName() {
            return "nop_auth_ext_login";
        }

        @Override
        public String getDeleteFlagProp() {
            return "delFlag";
        }

        @Override
        public String getDeleteVersionProp() {
            return "version";
        }

        @Override
        public boolean isUseLogicalDelete() {
            return true;
        }

        @Override
        public boolean isUseTenant() {
            return false;
        }

        @Override
        public void resetToDefaultValues(NopAuthExtLogin entity) {
        }

        @Override
        public List<String> getPkColumnNames() {
            return Collections.singletonList("SID");
        }

        @Override
        public String getEntityClassName() {
            return NopAuthExtLogin.class.getName();
        }

        @Override
        public Object castId(Object id) {
            return id;
        }

        @Override
        public List<Object> castIdList(Collection<?> ids) {
            return new ArrayList<>(ids == null ? Collections.emptyList() : (Collection<?>) ids);
        }

        @Override
        public Object initEntityId(NopAuthExtLogin entity) {
            if (entity.getSid() == null) {
                entity.setSid("row-" + idSeq.incrementAndGet());
            }
            return entity.getSid();
        }

        @Override
        public Object getEntityId(NopAuthExtLogin entity) {
            return entity.getSid();
        }

        @Override
        public NopAuthExtLogin newEntity() {
            return new NopAuthExtLogin();
        }

        @Override
        public void saveEntity(NopAuthExtLogin entity) {
            if (entity.getSid() == null) {
                entity.setSid("row-" + idSeq.incrementAndGet());
            }
            rows.add(entity);
        }

        @Override
        public void updateEntity(NopAuthExtLogin entity) {
            // already in list (by reference); nothing to do
        }

        @Override
        public void saveEntityDirectly(NopAuthExtLogin entity) {
            saveEntity(entity);
        }

        @Override
        public void updateEntityDirectly(NopAuthExtLogin entity) {
        }

        @Override
        public void updateEntitiesDirectly(Collection<NopAuthExtLogin> entities) {
        }

        @Override
        public void deleteEntityDirectly(NopAuthExtLogin entity) {
            rows.removeIf(r -> r.getSid() != null && r.getSid().equals(entity.getSid()));
        }

        @Override
        public void saveOrUpdateEntity(NopAuthExtLogin entity) {
            saveEntity(entity);
        }

        @Override
        public void deleteEntity(NopAuthExtLogin entity) {
            entity.setDelFlag((byte) 1);
        }

        @Override
        public NopAuthExtLogin loadEntityById(Object id) {
            return findById(id);
        }

        @Override
        public void lockEntity(NopAuthExtLogin entity) {
        }

        @Override
        public NopAuthExtLogin getEntityById(Object id) {
            return findById(id);
        }

        NopAuthExtLogin findById(Object id) {
            for (NopAuthExtLogin row : rows) {
                if (id != null && id.equals(row.getSid())) {
                    return row;
                }
            }
            return null;
        }

        @Override
        public List<NopAuthExtLogin> batchGetEntitiesByIds(Collection<?> ids) {
            List<NopAuthExtLogin> result = new ArrayList<>();
            for (Object id : ids) {
                NopAuthExtLogin row = findById(id);
                if (row != null) {
                    result.add(row);
                }
            }
            return result;
        }

        @Override
        public List<NopAuthExtLogin> batchRequireEntitiesByIds(Collection<?> ids) {
            return batchGetEntitiesByIds(ids);
        }

        @Override
        public List<NopAuthExtLogin> tryBatchGetEntitiesByIds(Collection<?> ids) {
            return batchGetEntitiesByIds(ids);
        }

        @Override
        public Map<Object, NopAuthExtLogin> batchGetEntityMapByIds(Collection<?> ids) {
            Map<Object, NopAuthExtLogin> result = new HashMap<>();
            for (Object id : ids) {
                NopAuthExtLogin row = findById(id);
                if (row != null) {
                    result.put(id, row);
                }
            }
            return result;
        }

        @Override
        public Map<Object, NopAuthExtLogin> batchRequireEntityMapByIds(Collection<?> ids) {
            Map<Object, NopAuthExtLogin> result = new HashMap<>();
            for (Object id : ids) {
                NopAuthExtLogin row = findById(id);
                if (row == null) {
                    throw new IllegalStateException("Entity not found for id: " + id);
                }
                result.put(id, row);
            }
            return result;
        }

        @Override
        public List<NopAuthExtLogin> batchGetEntitiesByProp(String propName, Collection<?> propValues) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<NopAuthExtLogin> batchRequireEntitiesByProp(String propName, Collection<?> propValues) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<NopAuthExtLogin> tryBatchGetEntitiesByProp(String propName, Collection<?> propValues) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<Object, NopAuthExtLogin> batchGetEntityMapByProp(String propName, Collection<?> propValues) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void attachEntity(NopAuthExtLogin entity, boolean cascade) {
        }

        @Override
        public void batchFlush(Collection<NopAuthExtLogin> entities) {
        }

        @Override
        public void batchSaveEntities(Collection<NopAuthExtLogin> entities) {
            for (NopAuthExtLogin e : entities) {
                saveEntity(e);
            }
        }

        @Override
        public void batchUpdateEntities(Collection<NopAuthExtLogin> entities) {
        }

        @Override
        public void batchDeleteEntities(Collection<NopAuthExtLogin> entities) {
            for (NopAuthExtLogin e : entities) {
                deleteEntity(e);
            }
        }

        @Override
        public void batchGetEntities(Collection<NopAuthExtLogin> entities) {
        }

        @Override
        public long deleteByExample(NopAuthExtLogin example) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isEmpty() {
            return rows.isEmpty();
        }

        @Override
        public NopAuthExtLogin findFirstByExample(NopAuthExtLogin example) {
            for (NopAuthExtLogin row : rows) {
                if (matches(row, example)) {
                    return row;
                }
            }
            return null;
        }

        @Override
        public long countByExample(NopAuthExtLogin example) {
            return findAllByExample(example, null).size();
        }

        @Override
        public List<NopAuthExtLogin> findPageByExample(NopAuthExtLogin example, List<io.nop.api.core.beans.query.OrderFieldBean> orderBy, long offset, int limit) {
            return findAllByExample(example, orderBy);
        }

        @Override
        public List<NopAuthExtLogin> findAllByExample(NopAuthExtLogin example, List<io.nop.api.core.beans.query.OrderFieldBean> orderBy) {
            List<NopAuthExtLogin> result = new ArrayList<>();
            for (NopAuthExtLogin row : rows) {
                if (matches(row, example)) {
                    result.add(row);
                }
            }
            return result;
        }

        @Override
        public NopAuthExtLogin findFirstByQuery(io.nop.api.core.beans.query.QueryBean query) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long countByQuery(io.nop.api.core.beans.query.QueryBean query) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long deleteByQuery(io.nop.api.core.beans.query.QueryBean query) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<NopAuthExtLogin> findPageByQuery(io.nop.api.core.beans.query.QueryBean query) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void findPageAndReturnCursor(io.nop.api.core.beans.query.QueryBean query, io.nop.api.core.beans.PageBean<NopAuthExtLogin> page) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<NopAuthExtLogin> findAllByQuery(io.nop.api.core.beans.query.QueryBean query) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean existsByQuery(io.nop.api.core.beans.query.QueryBean query) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long updateByQuery(io.nop.api.core.beans.query.QueryBean query, Map<String, Object> props) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<NopAuthExtLogin> findAll() {
            return new ArrayList<>(rows);
        }

        @Override
        public List<Map<String, Object>> selectFieldsByQuery(io.nop.api.core.beans.query.QueryBean query) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Object> selectFieldByQuery(io.nop.api.core.beans.query.QueryBean query) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<NopAuthExtLogin> findNext(NopAuthExtLogin lastEntity, io.nop.api.core.beans.ITreeBean filter, List<io.nop.api.core.beans.query.OrderFieldBean> orderBy, int limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<NopAuthExtLogin> findPrev(NopAuthExtLogin lastEntity, io.nop.api.core.beans.ITreeBean filter, List<io.nop.api.core.beans.query.OrderFieldBean> orderBy, int limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public NopAuthExtLogin loadEntityByCursor(String cursor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <R extends IDaoEntity> IEntityDao<R> propDao(String propName) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void batchLoadProps(Collection<NopAuthExtLogin> entities, Collection<String> propNames) {
        }

        @Override
        public void batchLoadSelection(Collection<NopAuthExtLogin> entities, io.nop.api.core.beans.FieldSelectionBean selectionBean) {
        }

        @Override
        public void flushSession() {
        }

        @Override
        public void clearEntitySessionCache() {
        }

        @Override
        public void clearEntityGlobalCache() {
        }

        @Override
        public io.nop.api.core.time.IEstimatedClock getDbEstimatedClock() {
            throw new UnsupportedOperationException();
        }

        /**
         * Match row against example, mirroring
         * {@code EntityPersisterImpl.addDeleteFlagToExample} semantics: if
         * the example has {@code orm_disableLogicalDelete==true}, the
         * {@code delFlag} filter is NOT applied; otherwise rows with
         * {@code delFlag != 0} are excluded.
         */
        private boolean matches(NopAuthExtLogin row, NopAuthExtLogin example) {
            if (example == null) {
                return true;
            }
            if (!fieldMatches(row.getUserId(), example.getUserId())) {
                return false;
            }
            if (!fieldMatches(row.getLoginType(), example.getLoginType())) {
                return false;
            }
            if (!fieldMatches(row.getExtId(), example.getExtId())) {
                return false;
            }
            if (!fieldMatches(row.getVerified(), example.getVerified())) {
                return false;
            }
            // delFlag handling mirrors the real ORM
            boolean disableLogicalDelete = example.orm_disableLogicalDelete();
            if (!disableLogicalDelete) {
                Byte rowDel = row.getDelFlag();
                if (rowDel != null && rowDel != 0) {
                    return false;
                }
            }
            // If example has an explicit delFlag set, also match by it
            if (example.getDelFlag() != null && !example.getDelFlag().equals(row.getDelFlag())) {
                return false;
            }
            return true;
        }

        private boolean fieldMatches(Object rowValue, Object exampleValue) {
            if (exampleValue == null) {
                return true;
            }
            return exampleValue.equals(rowValue);
        }
    }

    /**
     * Stub {@link IChannelBindProvider} that returns a fixed QR payload and
     * counts invocations, so tests can assert the service really calls
     * through (Anti-Hollow).
     */
    private static class StubBindProvider implements IChannelBindProvider {
        private final String channelType;
        private final String qrPayload;
        final AtomicInteger createTicketCount = new AtomicInteger();
        final AtomicInteger scanCallbackCount = new AtomicInteger();

        StubBindProvider(String channelType, String qrPayload) {
            this.channelType = channelType;
            this.qrPayload = qrPayload;
        }

        @Override
        public String getChannelType() {
            return channelType;
        }

        @Override
        public BindTicket createBindTicket(String channelType, String platformUserId) {
            createTicketCount.incrementAndGet();
            BindTicket ticket = new BindTicket();
            ticket.setTicketId("ticket-" + createTicketCount.get());
            ticket.setQrPayload(qrPayload);
            ticket.setExpiresAt(java.sql.Timestamp.valueOf("2099-01-01 00:00:00"));
            ticket.setStatus(BindTicketStatus.PENDING);
            return ticket;
        }

        @Override
        public ChannelBindResult onChannelScanCallback(ChannelScanCallback callback) {
            scanCallbackCount.incrementAndGet();
            ChannelBindResult result = new ChannelBindResult();
            result.setTicketId(callback.getTicketId());
            result.setStatus(ChannelBindResultStatus.BINDING_COMPLETED);
            // Extract extId from the raw payload the way a real provider would
            Map<String, Object> payload = callback.getRawPayload();
            if (payload != null && payload.containsKey("open_id")) {
                result.setExtId(String.valueOf(payload.get("open_id")));
            }
            return result;
        }
    }

    // Suppress unused-import warnings for collections used only in lambdas
    @SuppressWarnings("unused")
    private static final Set<String> _refs = new HashSet<>(Arrays.asList("dummy"));
}
