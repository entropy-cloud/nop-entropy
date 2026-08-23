/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.geo.dialect.h2gis;

import io.nop.core.lang.sql.SQL;
import io.nop.dao.jdbc.IJdbcTemplate;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestH2GisInitializer {

    @Test
    public void testInitSetsQuerySpaceAttribute() {
        IJdbcTemplate jdbcTemplate = mock(IJdbcTemplate.class);
        AtomicReference<SQL> captured = new AtomicReference<>();
        when(jdbcTemplate.runWithConnection(any(SQL.class), any())).thenAnswer(inv -> {
            captured.set(inv.getArgument(0, SQL.class));
            // 不真正执行回调：H2GISFunctions.load 需要真实 Connection，本用例只验证 querySpace 属性
            return null;
        });

        H2GisInitializer initializer = new H2GisInitializer();
        initializer.jdbcTemplate = jdbcTemplate;
        initializer.setQuerySpaceToDialectConfig("qs1=h2gis,qs2=mysql");
        initializer.init();

        // 修复前 querySpace 被拼进 SQL 文本，属性为 null，连接会回落到默认数据源
        assertEquals("qs1", captured.get().getQuerySpace());
    }
}
