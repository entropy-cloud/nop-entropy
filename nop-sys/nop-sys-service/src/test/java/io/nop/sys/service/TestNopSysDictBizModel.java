package io.nop.sys.service;

import io.nop.api.core.annotations.autotest.EnableSnapshot;
import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.sys.dao.entity.NopSysDict;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopSysDictBizModel extends JunitAutoTestCase {

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IJdbcTemplate jdbcTemplate;

    @Test
    public void testSubTableError() {
        jdbcTemplate.executeUpdate(new SQL("alter table nop_sys_dict_option alter column label set not null"));
        IEntityDao<NopSysDict> dao = daoProvider.daoFor(NopSysDict.class);
        assertEquals(0, dao.findAll().size());

        ApiRequest<?> request = request("request.json5", Map.class);
        IGraphQLExecutionContext ctx = graphQLEngine.newRpcContext(null, "NopSysDict__save", request);
        ApiResponse<?> response = graphQLEngine.executeRpc(ctx);
        output("response.json5", response);

        assertEquals(0, dao.findAll().size());
    }
}
