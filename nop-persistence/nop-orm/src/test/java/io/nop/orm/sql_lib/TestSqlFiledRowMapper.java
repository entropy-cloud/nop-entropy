/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.sql_lib;

import io.nop.core.lang.eval.IEvalScope;
import io.nop.dataset.IDataRow;
import io.nop.dataset.impl.BaseDataSetMeta;
import io.nop.dataset.impl.MapDataRow;
import io.nop.xlang.api.XLang;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestSqlFiledRowMapper {

    /**
     * computeExpr字段未配置as时，计算结果必须以字段名写入结果Map。
     * 历史版本误用field.getAs()（为null），计算结果被放入null键，按字段名读取永远取不到
     */
    @Test
    public void testComputeFieldWithoutAs() {
        NativeSqlItemModel sqlItem = new NativeSqlItemModel();
        SqlFieldModel field = new SqlFieldModel();
        field.setName("fullA");
        field.setComputeExpr((thisObj, args, scope) -> "computed-" + ((Map<?, ?>) args[0]).get("a"));
        sqlItem.addField(field);

        IEvalScope scope = XLang.newEvalScope();
        SqlFiledRowMapper mapper = new SqlFiledRowMapper(sqlItem, false, scope);

        IDataRow row = new MapDataRow(BaseDataSetMeta.fromColNames(new String[]{"a"}));
        row.setObject(0, 3);

        Map<String, Object> map = mapper.mapRow(row, 0, (r, i) -> r.getObject(i));

        assertEquals("computed-3", map.get("fullA"));
        assertEquals(3, map.get("a"));
    }

    /**
     * 配置了as的computeExpr字段按as写入
     */
    @Test
    public void testComputeFieldWithAs() {
        NativeSqlItemModel sqlItem = new NativeSqlItemModel();
        SqlFieldModel field = new SqlFieldModel();
        field.setName("fullA");
        field.setAs("aliasA");
        field.setComputeExpr((thisObj, args, scope) -> "computed-" + ((Map<?, ?>) args[0]).get("a"));
        sqlItem.addField(field);

        IEvalScope scope = XLang.newEvalScope();
        SqlFiledRowMapper mapper = new SqlFiledRowMapper(sqlItem, false, scope);

        IDataRow row = new MapDataRow(BaseDataSetMeta.fromColNames(new String[]{"a"}));
        row.setObject(0, 3);

        Map<String, Object> map = mapper.mapRow(row, 0, (r, i) -> r.getObject(i));

        assertEquals("computed-3", map.get("aliasA"));
    }
}
