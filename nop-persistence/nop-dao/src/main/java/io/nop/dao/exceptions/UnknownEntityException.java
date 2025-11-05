/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.exceptions;

import static io.nop.dao.DaoErrors.ARG_ENTITY_ID;
import static io.nop.dao.DaoErrors.ARG_ENTITY_NAME;
import static io.nop.dao.DaoErrors.ERR_DAO_UNKNOWN_ENTITY;

public class UnknownEntityException extends DaoException {

    private static final long serialVersionUID = 1380449984920426468L;

    public UnknownEntityException(String entityName, Object entityId) {
        super(ERR_DAO_UNKNOWN_ENTITY);
        param(ARG_ENTITY_NAME, entityName).param(ARG_ENTITY_ID, entityId);
    }

    public String getEntityName() {
        return (String) getParam(ARG_ENTITY_NAME);
    }

    public String getEntityId() {
        return (String) getParam(ARG_ENTITY_ID);
    }
}