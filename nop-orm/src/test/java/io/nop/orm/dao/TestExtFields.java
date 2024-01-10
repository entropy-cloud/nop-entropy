/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.dao;

import io.nop.app.SimsCollege;
import io.nop.app.SimsExam;
import io.nop.core.lang.sql.SQL;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.AbstractOrmTestCase;
import io.nop.orm.IOrmKeyValueTable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 如果修改了app.orm.xml，需要重新运行OrmCodeGen来生成实体代码
 */
public class TestExtFields extends AbstractOrmTestCase {

    @DisplayName("为Exam表增加专用的扩展字段表")
    @Test
    public void testExamExtFields() {
        IEntityDao<SimsExam> dao = daoProvider().daoFor(SimsExam.class);

        SimsExam entity = dao.newEntity();
        entity.setExamId("100");
        BeanTool.setComplexProperty(entity, "ext.测试参数2.double", 2.0);
        // 在模型中定义，但是并没有生成实体代码的动态字段。模型中标注了notGenCode=true
        entity.prop_set("extField", 5);
        dao.saveEntity(entity);

        orm().runInSession(() -> {
            SimsExam exam = dao.getEntityById("100");
            assertEquals(2.0, BeanTool.getComplexProperty(exam, "ext.测试参数2.double"));
            BeanTool.setComplexProperty(exam, "ext.测试参数2.int", 3);
            assertEquals(5, entity.prop_get("extField"));
        });

        orm().runInSession(() -> {
            SimsExam exam = dao.getEntityById("100");
            assertEquals(3, BeanTool.getComplexProperty(exam, "ext.测试参数2.int"));
        });

    }

    @DisplayName("通过全局通用的扩展表为Exam实体提供扩展字段")
    @Test
    public void testGenericExtFields() {
        IEntityDao<SimsExam> dao = daoProvider().daoFor(SimsExam.class);

        SimsExam entity = new SimsExam();
        entity.setExamId("100");
        BeanTool.setComplexProperty(entity, "ext.test.value", "ss");
        BeanTool.setComplexProperty(entity, "ext.test2.value", 3);
        assertEquals("ss", BeanTool.getComplexProperty(entity, "ext.test.value"));
        assertEquals("ss", ((IOrmKeyValueTable) entity.getExt().prop_get("test")).getValue());

        dao.saveEntity(entity);

        SimsExam entity2 = new SimsExam();
        entity2.setExamId("101");
        BeanTool.setComplexProperty(entity2, "ext.test3.value", LocalDate.of(2000, 1, 1));
        entity2.getExt().prop_set("test4", new BigDecimal("4.20"));
        dao.saveEntity(entity2);

        orm().runInSession(() -> {
            SimsExam exam = dao.getEntityById("100");
            assertEquals("ss", ((IOrmKeyValueTable) exam.getExt().prop_get("test")).getValue());
            assertEquals(3, ((IOrmKeyValueTable) exam.getExt().prop_get("test2")).getValue());

        });

        orm().runInSession(() -> {
            SimsExam exam = dao.loadEntityById("100");
            SimsExam exam2 = dao.loadEntityById("101");
            dao.batchLoadProps(Arrays.asList(exam, exam2), Collections.singletonList("ext"));

            assertEquals(null, BeanTool.getComplexProperty(exam2, "ext.test2.value"));
            assertEquals(LocalDate.of(2000, 1, 1), BeanTool.getComplexProperty(exam2, "ext.test3.value"));
            assertEquals("4.20", BeanTool.getComplexProperty(exam2, "ext.test4.value").toString());
        });
    }

    @DisplayName("通过别名简化扩展字段访问")
    @Test
    public void testExtFieldAlias() {
        IEntityDao<SimsExam> dao = daoProvider().daoFor(SimsExam.class);

        SimsExam entity = dao.newEntity();
        entity.setExamId("100");
        entity.setExtFldA("fldAA");
        BeanTool.setComplexProperty(entity, "extFldB", "true");

        dao.saveEntity(entity);

        orm().runInSession(() -> {
            SimsExam exam = dao.getEntityById("100");
            assertEquals(true, exam.prop_get("extFldB"));
            assertEquals("fldAA", exam.getExtFldA());
        });
    }

    @Test
    public void testJoinOnExtField() {
        IEntityDao<SimsExam> dao = daoProvider().daoFor(SimsExam.class);

        SimsExam entity = dao.newEntity();
        entity.setExamId("100");
        entity.setExtFldA("fldAA");
        BeanTool.setComplexProperty(entity, "extFldB", "true");

        dao.saveEntity(entity);

        orm().runInSession(() -> {
            SimsExam exam = dao.getEntityById("100");
            SimsCollege college = (SimsCollege) exam.prop_get("extRefCollege");
            assertNull(college);

            college = (SimsCollege) exam.prop_get("extRefCollege2");
            assertEquals("fldAA", college.getCollegeId());

            SQL sql = SQL.begin().sql("select o.extRefCollege.collegeName from io.nop.app.SimsExam o").end();
            orm().findFirst(sql);
        });
    }
}
