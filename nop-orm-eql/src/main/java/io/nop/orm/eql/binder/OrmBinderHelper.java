/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.eql.binder;

import io.nop.api.core.exceptions.NopException;
import io.nop.dao.dialect.IDialect;
import io.nop.dataset.binder.IDataParameterBinder;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityModel;

import java.util.List;

import static io.nop.orm.eql.OrmEqlErrors.ARG_COL_NAME;
import static io.nop.orm.eql.OrmEqlErrors.ARG_ENTITY_NAME;
import static io.nop.orm.eql.OrmEqlErrors.ARG_SQL_TYPE;
import static io.nop.orm.eql.OrmEqlErrors.ERR_ORM_NULL_BINDER_FOR_COLUMN;
import static io.nop.orm.model.OrmModelErrors.ARG_DATA_TYPE;

public class OrmBinderHelper {
    public static IDataParameterBinder[] buildBinders(IDialect dialect, IEntityModel entityModel,
                                                      IOrmColumnBinderEnhancer binderEnhancer) {
        List<? extends IColumnModel> cols = entityModel.getColumns();
        IDataParameterBinder[] binders = new IDataParameterBinder[entityModel.getPropIdBound()];
        for (int i = 0, n = cols.size(); i < n; i++) {
            IColumnModel col = cols.get(i);
            IDataParameterBinder binder = dialect.getDataParameterBinder(col.getStdDataType(), col.getStdSqlType());
            if (binder == null)
                throw new NopException(ERR_ORM_NULL_BINDER_FOR_COLUMN).source(col)
                        .param(ARG_ENTITY_NAME, entityModel.getName()).param(ARG_COL_NAME, col.getName())
                        .param(ARG_DATA_TYPE, col.getStdDataType()).param(ARG_SQL_TYPE, col.getStdSqlType());
            if (binderEnhancer != null)
                binder = binderEnhancer.enhanceBinder(entityModel, col, binder);
            binders[col.getPropId()] = binder;
        }
        return binders;
    }
}