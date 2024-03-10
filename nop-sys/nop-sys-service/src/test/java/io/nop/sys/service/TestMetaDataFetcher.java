/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.sys.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.util.FutureHelper;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.sys.dao.entity.NopSysDict;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

@NopTestConfig(localDb = true, initDatabaseSchema = true)
public class TestMetaDataFetcher extends JunitBaseTestCase {

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IDaoProvider daoProvider;

    @Test
    public void testTransformOut() {
        IEntityDao<NopSysDict> dao = daoProvider.daoFor(NopSysDict.class);
        NopSysDict dict = new NopSysDict();
        dict.setDictName("dict");
        dict.setDisplayName("test");
        dao.saveEntity(dict);

        ApiRequest<?> request = new ApiRequest<>();
        IGraphQLExecutionContext ctx = graphQLEngine.newRpcContext(null, "NopSysDict__findList", request);
        ApiResponse<?> response = FutureHelper.syncGet(graphQLEngine.executeRpcAsync(ctx));
        List<Map<String, Object>> data = (List<Map<String, Object>>) response.getData();
        String text = (String) data.get(0).get("displayName");
        assertTrue(text.contains("DISP"));
    }
}
