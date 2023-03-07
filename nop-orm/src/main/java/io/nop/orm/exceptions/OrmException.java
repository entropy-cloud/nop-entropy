/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.exceptions;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.dao.exceptions.DaoException;
import io.nop.orm.IOrmEntity;

import static io.nop.orm.OrmErrors.ARG_ENTITY_ID;
import static io.nop.orm.OrmErrors.ARG_ENTITY_NAME;
import static io.nop.orm.OrmErrors.ARG_TENANT_ID;

public class OrmException extends DaoException {
    private static final long serialVersionUID = -6323620479140754045L;

    public OrmException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public OrmException(ErrorCode errorCode) {
        super(errorCode);
    }

    public static NopException newError(ErrorCode errorCode, IOrmEntity entity) {
        NopException e = new OrmException(errorCode).param(ARG_ENTITY_NAME, entity.orm_entityName())
                .param(ARG_ENTITY_ID, entity.orm_id());
        String tenantId = entity.orm_tenantId();
        if (tenantId != null)
            e.param(ARG_TENANT_ID, tenantId);
        return e;
    }
}
