package io.nop.auth.service.biz;

import io.nop.api.core.annotations.txn.TransactionPropagation;
import io.nop.api.core.annotations.txn.Transactional;
import io.nop.auth.dao.entity.NopAuthOpLog;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.dao.txn.ITransactionTemplate;
import jakarta.inject.Inject;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestService {

    @Inject
    IDaoProvider daoProvider;

    @Inject
    ITransactionTemplate transactionTemplate;

    @Transactional
    public void methodA() {
        methodB();
        throw new IllegalStateException("error");
    }

    @Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
    public void methodB() {
        assertTrue(transactionTemplate.getRegisteredTransaction(null).isTransactionOpened());

        IEntityDao<NopAuthOpLog> dao = daoProvider.daoFor(NopAuthOpLog.class);
        NopAuthOpLog entity = new NopAuthOpLog();
        entity.setBizObjName("Test");
        entity.setBizActionName("test");
        entity.setOpRequest("request");
        entity.setTitle("测试日志");
        entity.setUsedTime(1000L);
        entity.setUserName("test");
        dao.saveEntity(entity);
    }
}
