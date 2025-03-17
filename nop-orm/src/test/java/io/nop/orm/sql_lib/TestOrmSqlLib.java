/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.sql_lib;

import io.nop.api.core.beans.DictBean;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.LongRangeBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.beans.query.QueryFieldBean;
import io.nop.app.SimsClass;
import io.nop.core.dict.DictProvider;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.sql.SQL;
import io.nop.orm.AbstractOrmTestCase;
import io.nop.xlang.api.XLang;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestOrmSqlLib extends AbstractOrmTestCase {

    @Test
    public void testSqlFilterTag() {
        orm().runInSession(() -> {
            IEvalScope scope = XLang.newEvalScope();
            scope.setLocalValue(null, "ids", Arrays.asList(11, 1112));
            scope.setLocalValue(null, "unknownParam", null);
            List<SimsClass> list = (List<SimsClass>) sqlLibManager.invoke("test.findBySqlFilter",
                    LongRangeBean.of(0, 1), scope);
            assertEquals(1, list.size());
            SimsClass entity = list.get(0);
            assertEquals("11", entity.getClassId());
        });
    }

    @Test
    public void testComplexParams() {
        orm().runInSession(() -> {
            IEvalScope scope = XLang.newEvalScope();
            Map<String, Object> data = new HashMap<>();
            data.put("ids", Arrays.asList(11, 1112));
            data.put("unknownParam", null);
            scope.setLocalValue("data", data);
            List<SimsClass> list = (List<SimsClass>) sqlLibManager.invoke("test.testComplexParams",
                    LongRangeBean.of(0, 1), scope);
            assertEquals(1, list.size());
            SimsClass entity = list.get(0);
            assertEquals("11", entity.getClassId());

            MyMapper mapper = sqlLibManager.createProxy(MyMapper.class);
            list = mapper.testComplexParams(data);
            assertEquals(1, list.size());
        });
    }

    @Test
    public void checkDialectCondition() {
        sqlLibManager.invoke("test2.findWithDialect",
                LongRangeBean.of(0, 1), XLang.newEvalScope());
    }

    @Test
    public void testFragment() {
        SQL sql = sqlLibManager.buildSql("test2.generateWithFragment", XLang.newEvalScope());
        assertEquals("select\n" +
                "                a, b, c\n" +
                "                from MyTable o", normalizeCRLF(sql.getText().trim()));
    }

    @Test
    public void testOrmEntityRowMapper() {
        orm().runInSession(() -> {
            SimsClass entity = (SimsClass) sqlLibManager.invoke("test2.testOrmEntityRowMapper", null, XLang.newEvalScope());
            assertNotNull(entity);
            assertTrue(!entity.orm_proxy());
            assertTrue(entity.getSimsCollege().orm_proxy());
            assertEquals(3, entity.orm_initedValues().size());
            assertEquals("CollegeA", entity.getSimsCollege().getCollegeName());
        });
    }

    @Test
    public void testDict() {
        IEvalScope scope = XLang.newEvalScope();
        DictBean dict = DictProvider.instance().getDict(null, "sql/test.demo_dict", null, scope);
        assertNotNull(dict);
        assertFalse(dict.getOptions().isEmpty());
        assertNotNull(dict.getOptions().get(0).getValue());
    }

    @Test
    public void testQueryBean() {
        orm().runInSession(() -> {
            List<Map<String, Object>> ret = (List<Map<String, Object>>) sqlLibManager.invoke("test2.testQueryBean", null, XLang.newEvalScope());
            assertEquals(0, ret.size());
        });

        QueryBean query = new QueryBean();
        query.addField(QueryFieldBean.forField("course.courseName"));
        query.addField(QueryFieldBean.forField("studentId").aggFunc("count").alias("cnt"));
        SQL sql = SQL.begin().sql("o.studentId in (select t.studentId from StudentFollow t where followerId = 1)").end();
        query.addFilter(FilterBeans.sql(sql));
        query.setSourceName("CourseSelection");

        orm().findListByQuery(query);
    }
}
