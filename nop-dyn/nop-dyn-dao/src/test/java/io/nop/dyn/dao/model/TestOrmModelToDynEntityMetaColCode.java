package io.nop.dyn.dao.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 引用列code到relation属性名的推导规则：_id后缀必须优先于id结尾判断，小写user_id与大写USER_ID行为一致
 */
public class TestOrmModelToDynEntityMetaColCode {

    OrmModelToDynEntityMeta transformer = new OrmModelToDynEntityMeta(false);

    @Test
    public void testLowercaseIdSuffix() {
        assertEquals("user", transformer.getRefPropNameFromColCode("user_id", "User"));
    }

    @Test
    public void testUppercaseIdSuffix() {
        assertEquals("dept", transformer.getRefPropNameFromColCode("DEPT_ID", "Dept"));
    }

    @Test
    public void testExactIdColumn() {
        assertEquals("User", transformer.getRefPropNameFromColCode("_id", "User"));
    }

    @Test
    public void testCamelCaseIdEndingUnchanged() {
        // "userId"结尾是"Id"不是"id"，endsWith区分大小写，修复前后都不命中refEntityName分支
        assertEquals("useridObj", transformer.getRefPropNameFromColCode("userId", "User"));
    }

    @Test
    public void testNonIdColumn() {
        assertEquals("nameObj", transformer.getRefPropNameFromColCode("name", "Other"));
    }
}
