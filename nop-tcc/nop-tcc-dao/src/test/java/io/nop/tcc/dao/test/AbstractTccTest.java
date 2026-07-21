package io.nop.tcc.dao.test;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.rpc.api.DefaultApiResponseNormalizer;
import io.nop.tcc.core.impl.TccEngine;
import io.nop.tcc.dao.store.TccRecordStore;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public abstract class AbstractTccTest extends JunitBaseTestCase {

    @Inject
    protected IDaoProvider daoProvider;

    @Inject
    protected TccEngine tccEngine;

    protected TccRecordStore tccRecordStore;

    protected MockRpcServiceInvoker mockRpcServiceInvoker;

    @BeforeEach
    public void setUp() {
        tccRecordStore = new TccRecordStore();
        tccRecordStore.setDaoProvider(daoProvider);
        tccRecordStore.setDefaultBranchTimeout(10000);
        tccRecordStore.setDefaultTxnTimeout(60000);
        tccRecordStore.setDefaultMaxRetryTimes(100);

        mockRpcServiceInvoker = new MockRpcServiceInvoker();

        tccEngine.setTccRecordStore(tccRecordStore);
        tccEngine.setServiceInvoker(mockRpcServiceInvoker);
        tccEngine.setApiResponseNormalizer(DefaultApiResponseNormalizer.INSTANCE);
    }
}
