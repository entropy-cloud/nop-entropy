package io.nop.auth.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.context.ContextProvider;
import io.nop.auth.dao.entity.NopAuthGroup;
import io.nop.auth.service.mock.MockShardSelector;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.jdbc.datasource.SimpleDataSource;
import io.nop.dao.shard.ShardSelection;
import io.nop.dao.txn.ITransactionTemplate;
import io.nop.dao.txn.impl.DefaultTransactionManager;
import io.nop.orm.IOrmTemplate;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled
@NopTestConfig
public class PrepareDataTestCase extends JunitBaseTestCase {

    @Inject
    MockShardSelector shardSelector;

    @Inject
    DefaultTransactionManager transactionManager;

    @Inject
    IOrmTemplate ormTemplate;

    @Inject
    ITransactionTemplate transactionTemplate;

    @Test
    public void insertTestData() {
        SimpleDataSource ds = new SimpleDataSource();
        ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
        ds.setUrl("jdbc:mysql://127.0.0.1:3306/dev?useUnicode=true&characterEncoding=utf-8&serverTimezone=UTC");
        ds.setUsername("nop");
        ds.setPassword("nop-test");
        transactionManager.addDataSource("test", ds);

        ContextProvider.getOrCreateContext().setTenantId("test");

        shardSelector.addShardSelection(NopAuthGroup.class.getName(), new ShardSelection("test", null));

        int n = 10 * 1000 * 1000;
        int batch = 1000;
        for (int i = 0; i < n / batch; i++) {
            int index = i;
            transactionTemplate.runInTransaction("test", null, txn -> {
                return ormTemplate.runInSession(session -> {
                    for (int j = 0; j < batch; j++) {
                        NopAuthGroup group = new NopAuthGroup();
                        int fullIndex = index * batch + j;
                        group.setGroupId(String.valueOf(fullIndex));
                        group.setName("group" + fullIndex);
                        ormTemplate.save(group);
                    }
                    return null;
                });
            });
        }
    }
}
