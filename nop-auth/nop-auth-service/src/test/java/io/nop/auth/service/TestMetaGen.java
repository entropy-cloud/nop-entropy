package io.nop.auth.service;

import io.nop.api.core.annotations.autotest.EnableSnapshot;
import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.time.CoreMetrics;
import io.nop.auth.dao.entity.NopAuthOpLog;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.engine.IGraphQLEngine;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestMetaGen extends JunitAutoTestCase {

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IDaoProvider daoProvider;

    @EnableSnapshot
    @Test
    public void testCsvList() {
        saveData();
        ApiRequest<Void> request = new ApiRequest<>();
        IGraphQLExecutionContext gqlCtx = graphQLEngine.newRpcContext(null, "NopAuthOpLog__findList", request);
        ApiResponse<?> response = graphQLEngine.executeRpc(gqlCtx);
        output("response.json5", response);
    }

    private void saveData() {
        IEntityDao<NopAuthOpLog> dao = daoProvider.daoFor(NopAuthOpLog.class);
        NopAuthOpLog log = new NopAuthOpLog();
        log.setSessionId("123");
        log.setActionTime(CoreMetrics.currentTimestamp());
        log.setUserId("a");
        log.setUsedTime(0L);
        log.setUserName("aaa");
        log.setOpResponse("a,b,c");
        log.setResultStatus(1);
        dao.saveEntity(log);
    }
}