/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.sql_lib;

import io.nop.orm.AbstractJdbcTestCase;
import io.nop.orm.sql_lib.dict.SqlLibDictLoader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestSqlLibDictLoader extends AbstractJdbcTestCase {

    /**
     * existsDict必须使用去除sql/前缀后的sqlName查询sql-lib条目。
     * 历史版本误传原始dictName，导致任何sql/字典的存在性判断恒为false
     */
    @Test
    public void testExistsDict() {
        SqlLibDictLoader loader = new SqlLibDictLoader();
        loader.setSqlLibManager(sqlLibManager);

        // test.sql-lib.xml中定义了demo_dict条目
        assertTrue(loader.existsDict("sql/test.demo_dict"));

        // 不存在的字典仍然返回false
        assertFalse(loader.existsDict("sql/test.unknown_dict"));
    }
}
