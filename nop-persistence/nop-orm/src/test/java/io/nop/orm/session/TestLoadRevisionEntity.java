/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.session;

import io.nop.core.lang.sql.SQL;
import io.nop.orm.AbstractOrmTestCase;
import io.nop.orm.IOrmEntity;
import io.nop.orm.IOrmSession;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestLoadRevisionEntity extends AbstractOrmTestCase {

    /**
     * 历史数据或手工插入的数据可能没有初始化nopRevType列(为null)。
     * 装载此类实体时不应因强制拆箱抛NPE，null按非删除版本处理
     */
    @Test
    public void testLoadRevisionEntityWithNullRevType() {
        // 直接通过SQL插入nopRevType为null的记录。NOP_REV_BEGIN_VER是主键的一部分，必须提供
        jdbc().executeUpdate(SQL.begin().sql(
                "insert into TEST_SIMS_REVISION(SID,NAME,NOP_REV_BEGIN_VER) values ('1','abc',0)").end());

        orm().runInSession((IOrmSession session) -> {
            // 复合主键: sid + nopRevBeginVer
            IOrmEntity entity = session.load("test.entity.SimsRevision", "1~0");
            // 触发装载
            assertEquals("abc", entity.orm_propValueByName("name"));
            return null;
        });
    }
}
