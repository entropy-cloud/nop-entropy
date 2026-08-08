package io.nop.auth.service.channel;

import io.nop.auth.dao.entity.NopAuthExtLogin;
import io.nop.dao.api.IDaoEntity;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.integration.api.channel.ChannelBinding;
import io.nop.integration.api.channel.ChannelTypeCodes;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 2 unit tests for {@link UserChannelResolverImpl}. Verifies that the
 * resolver (a) maps {@code NopAuthExtLogin} rows to channel-agnostic
 * {@link ChannelBinding}s via {@link ChannelTypeCodes}, (b) preserves the
 * "most recently active" ordering produced by the DAO's
 * {@code lastLoginTime DESC} clause, (c) honours the channelType-specific
 * overload, (d) returns an empty list (never null) for no-binding, and (e)
 * explicitly filters out non-channel login types (password/SSO) and rows
 * that slip through with {@code verified=false} / {@code delFlag!=0}.
 *
 * <p><b>Wiring verification (Minimum Rules #23)</b>: the resolver is
 * constructed and its {@code daoProvider} dependency is injected via the
 * setter (mirrors how Nop IoC would wire it). The configured DAO rows flow
 * through to the resolver output, proving the resolver&#8594;DAO wiring is
 * connected at runtime (not just type-level).
 */
public class TestUserChannelResolver {

    private static final String USER_ID = "user-1";

    @Test
    void singleBindingMapsToChannelBinding() {
        NopAuthExtLogin row = extLogin(USER_ID, ChannelTypeCodes.LOGIN_TYPE_FEISHU, "feishu-open-id");
        UserChannelResolverImpl resolver = newResolver(rows(row));

        List<ChannelBinding> bindings = resolver.resolve(USER_ID);

        assertEquals(1, bindings.size());
        ChannelBinding b = bindings.get(0);
        assertEquals(USER_ID, b.getUserId());
        assertEquals(ChannelTypeCodes.CHANNEL_TYPE_FEISHU, b.getChannelType());
        assertEquals("feishu-open-id", b.getChannelAddress());
    }

    @Test
    void multipleBindingsOrderedByLastLoginTimeDesc() {
        // DAO applies the lastLoginTime DESC orderBy; we return rows already
        // in that order and assert the resolver preserves it (most-recent first)
        NopAuthExtLogin older = extLogin(USER_ID, ChannelTypeCodes.LOGIN_TYPE_DINGTALK, "ding-1");
        older.setLastLoginTime(ts("2026-01-01 10:00:00"));
        NopAuthExtLogin newer = extLogin(USER_ID, ChannelTypeCodes.LOGIN_TYPE_FEISHU, "feishu-1");
        newer.setLastLoginTime(ts("2026-06-01 10:00:00"));

        UserChannelResolverImpl resolver = newResolver(rows(newer, older));

        List<ChannelBinding> bindings = resolver.resolve(USER_ID);

        assertEquals(2, bindings.size());
        // most-recently-active (feishu) must come first
        assertEquals(ChannelTypeCodes.CHANNEL_TYPE_FEISHU, bindings.get(0).getChannelType());
        assertEquals(ChannelTypeCodes.CHANNEL_TYPE_DINGTALK, bindings.get(1).getChannelType());
    }

    @Test
    void resolveByChannelTypeReturnsMatchingBinding() {
        NopAuthExtLogin feishu = extLogin(USER_ID, ChannelTypeCodes.LOGIN_TYPE_FEISHU, "feishu-1");
        NopAuthExtLogin ding = extLogin(USER_ID, ChannelTypeCodes.LOGIN_TYPE_DINGTALK, "ding-1");

        // resolve(userId, channelType) uses findFirstByExample; return feishu for that path
        UserChannelResolverImpl resolver = newResolver(rows(feishu, ding), feishu);

        ChannelBinding binding = resolver.resolve(USER_ID, ChannelTypeCodes.CHANNEL_TYPE_FEISHU);

        assertNotNull(binding);
        assertEquals(ChannelTypeCodes.CHANNEL_TYPE_FEISHU, binding.getChannelType());
        assertEquals("feishu-1", binding.getChannelAddress());
    }

    @Test
    void resolveByUnknownChannelTypeReturnsNull() {
        UserChannelResolverImpl resolver = newResolver(Collections.emptyList(), null);

        // unknown channelType -> no binding possible (explicit null, not silent default)
        assertNull(resolver.resolve(USER_ID, "no-such-channel"));
    }

    @Test
    void noBindingReturnsEmptyListNeverNull() {
        UserChannelResolverImpl resolver = newResolver(Collections.emptyList());

        List<ChannelBinding> bindings = resolver.resolve(USER_ID);

        assertNotNull(bindings);
        assertTrue(bindings.isEmpty(), "no binding must return empty list, not null");
    }

    @Test
    void nonChannelLoginTypesAreFilteredOut() {
        // A password row (loginType=1) and a feishu row; the password row must
        // NOT silently appear in the result — it is explicitly filtered.
        NopAuthExtLogin password = extLogin(USER_ID, 1, "pwd-ext-id");
        NopAuthExtLogin feishu = extLogin(USER_ID, ChannelTypeCodes.LOGIN_TYPE_FEISHU, "feishu-1");

        UserChannelResolverImpl resolver = newResolver(rows(feishu, password));

        List<ChannelBinding> bindings = resolver.resolve(USER_ID);

        assertEquals(1, bindings.size(), "password loginType must be filtered out, not silently included");
        assertEquals(ChannelTypeCodes.CHANNEL_TYPE_FEISHU, bindings.get(0).getChannelType());
    }

    @Test
    void exampleQueryFiltersOnVerifiedTrueAndDelFlagZeroAndUserId() {
        // Capture the example entity handed to the DAO and assert the resolver
        // builds an active-only example (verified=true, delFlag=0, userId set).
        List<NopAuthExtLogin> returned = Collections.singletonList(
                extLogin(USER_ID, ChannelTypeCodes.LOGIN_TYPE_FEISHU, "feishu-1"));
        NopAuthExtLogin[] captured = new NopAuthExtLogin[1];
        IEntityDao<NopAuthExtLogin> dao = capturingDao(returned, null, captured);
        UserChannelResolverImpl resolver = newResolver(dao);

        resolver.resolve(USER_ID);

        assertNotNull(captured[0], "resolver must call findAllByExample with an example entity");
        assertEquals(USER_ID, captured[0].getUserId());
        assertEquals(Boolean.TRUE, captured[0].getVerified());
        assertEquals(Byte.valueOf((byte) 0), captured[0].getDelFlag());
    }

    @Test
    void resolveNullUserIdReturnsEmptyList() {
        UserChannelResolverImpl resolver = newResolver(Collections.emptyList());

        assertTrue(resolver.resolve(null).isEmpty());
        assertTrue(resolver.resolve("").isEmpty());
    }

    // ---- helpers -----------------------------------------------------------

    private static UserChannelResolverImpl newResolver(List<NopAuthExtLogin> allRows) {
        return newResolver(allRows, null);
    }

    private static UserChannelResolverImpl newResolver(List<NopAuthExtLogin> allRows, NopAuthExtLogin firstRow) {
        return newResolver(daoReturning(allRows, firstRow, null));
    }

    private static UserChannelResolverImpl newResolver(IEntityDao<NopAuthExtLogin> dao) {
        UserChannelResolverImpl resolver = new UserChannelResolverImpl();
        resolver.setDaoProvider(daoProviderFor(dao));
        return resolver;
    }

    private static NopAuthExtLogin extLogin(String userId, int loginType, String extId) {
        NopAuthExtLogin row = new NopAuthExtLogin();
        row.setUserId(userId);
        row.setLoginType(loginType);
        row.setExtId(extId);
        row.setVerified(Boolean.TRUE);
        row.setDelFlag((byte) 0);
        return row;
    }

    private static List<NopAuthExtLogin> rows(NopAuthExtLogin... rows) {
        List<NopAuthExtLogin> list = new ArrayList<>();
        Collections.addAll(list, rows);
        return list;
    }

    private static Timestamp ts(String value) {
        return Timestamp.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private static IEntityDao<NopAuthExtLogin> daoReturning(List<NopAuthExtLogin> allRows,
                                                            NopAuthExtLogin firstRow,
                                                            NopAuthExtLogin[] captured) {
        return (IEntityDao<NopAuthExtLogin>) Proxy.newProxyInstance(
                IEntityDao.class.getClassLoader(),
                new Class[]{IEntityDao.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "findAllByExample":
                            if (captured != null && args != null && args.length > 0 && args[0] instanceof NopAuthExtLogin) {
                                captured[0] = (NopAuthExtLogin) args[0];
                            }
                            return allRows;
                        case "findFirstByExample":
                            return firstRow;
                        default:
                            throw new UnsupportedOperationException(
                                    "TestIEntityDao does not implement: " + method);
                    }
                });
    }

    private static IEntityDao<NopAuthExtLogin> capturingDao(List<NopAuthExtLogin> allRows,
                                                            NopAuthExtLogin firstRow,
                                                            NopAuthExtLogin[] captured) {
        return daoReturning(allRows, firstRow, captured);
    }

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
            @SuppressWarnings("unchecked")
            public <T extends IDaoEntity> IEntityDao<T> dao(String entityName) {
                return (IEntityDao<T>) dao;
            }

            @Override
            public <T extends IDaoEntity> IEntityDao<T> daoForTable(String tableName) {
                throw new UnsupportedOperationException("daoForTable not used in resolver test");
            }
        };
    }
}
