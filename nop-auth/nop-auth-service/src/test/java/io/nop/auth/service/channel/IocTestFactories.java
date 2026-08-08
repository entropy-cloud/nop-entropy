package io.nop.auth.service.channel;

import io.nop.dao.api.IDaoEntity;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;

import java.util.Collections;
import java.util.Set;

/**
 * Stub {@link IDaoProvider} for the {@link TestChannelBindServiceIoC} test.
 * Returns an unsupported DAO for any entity lookup. Real DAO access is not
 * exercised in the IoC wiring test — we only verify that
 * {@link ChannelBindServiceImpl} constructs, its
 * {@code channelBindProviders} collection wires (with an empty collected
 * list), and the bean is reachable from the container.
 *
 * <p>Kept as a concrete top-level class (not an anonymous inner class or a
 * lambda) so the Nop IoC container can construct it directly via its
 * no-arg constructor in the test beans.xml.
 */
public class IocTestFactories {

    public static IDaoProvider stubDaoProvider() {
        return new IDaoProvider() {
            @Override
            public Set<String> getEntityNames() {
                return Collections.emptySet();
            }

            @Override
            public String normalizeEntityName(String entityName) {
                return entityName;
            }

            @Override
            public boolean hasDao(String entityName) {
                return false;
            }

            @Override
            public <T extends IDaoEntity> IEntityDao<T> dao(String entityName) {
                throw new UnsupportedOperationException("stub IDaoProvider — not used in IoC wiring test");
            }

            @Override
            public <T extends IDaoEntity> IEntityDao<T> daoForTable(String tableName) {
                throw new UnsupportedOperationException("stub IDaoProvider — not used in IoC wiring test");
            }
        };
    }
}
