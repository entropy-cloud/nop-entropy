/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.persister;

import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.context.IContext;
import io.nop.api.core.exceptions.NopException;
import io.nop.app.SimsClass;
import io.nop.core.lang.sql.SQL;
import io.nop.orm.AbstractOrmTestCase;
import io.nop.orm.OrmErrors;
import io.nop.orm.model.OrmEntityModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestOrmPersisterHelpers extends AbstractOrmTestCase {

    /**
     * 更新时补填creater：有当前用户必须记录当前用户，没有当前用户才用系统用户兜底
     */
    @Test
    public void testOnUpdateFillsCreaterWithCurrentUser() {
        IContext ctx = ContextProvider.getOrCreateContext();
        String oldUserName = ctx.getUserName();
        ctx.setUserName("userA");
        try {
            // 历史记录createdBy为空
            jdbc().executeUpdate(new SQL("update sims_class set CREATED_BY=null where CLASS_ID='11'"));

            orm().runInSession(() -> {
                SimsClass entity = (SimsClass) orm().get(SimsClass.class.getName(), "11");
                // 修改任意属性触发update
                entity.setAdviser("newAdviser");
                orm().flushSession();
            });

            // 修复前：creater被覆盖为系统用户sys
            Object creater = jdbc().findFirst(new SQL("select CREATED_BY as V from sims_class where CLASS_ID='11'"));
            assertEquals("userA", creater);
        } finally {
            ctx.setUserName(oldUserName);
        }
    }

    /**
     * newError必须使用调用方传入的errorCode，而不是恒报"非当前版本"
     */
    @Test
    public void testNewErrorUsesPassedErrorCode() {
        NopException err = OrmRevisionHelper.newError(new OrmEntityModel(),
                OrmErrors.ERR_ORM_ENTITY_ALREADY_EXISTS, new SimsClass());
        assertEquals(OrmErrors.ERR_ORM_ENTITY_ALREADY_EXISTS.getErrorCode(), err.getErrorCode());

        NopException err2 = OrmRevisionHelper.newError(new OrmEntityModel(),
                OrmErrors.ERR_ORM_ENTITY_REV_VER_IS_LESS_THAN_HIS_VER, new SimsClass());
        assertEquals(OrmErrors.ERR_ORM_ENTITY_REV_VER_IS_LESS_THAN_HIS_VER.getErrorCode(), err2.getErrorCode());
    }
}
