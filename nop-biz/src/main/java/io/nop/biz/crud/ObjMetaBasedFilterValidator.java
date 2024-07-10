/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.biz.crud;

import io.nop.api.core.beans.ITreeBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.IVariableScope;
import io.nop.biz.BizConstants;
import io.nop.biz.api.IBizObjectManager;
import io.nop.commons.util.CollectionHelper;
import io.nop.core.model.query.FilterBeanValidator;
import io.nop.core.model.query.FilterOp;
import io.nop.orm.OrmConstants;
import io.nop.xlang.xmeta.IObjMeta;
import io.nop.xlang.xmeta.IObjPropMeta;

import java.util.Collection;
import java.util.Set;

import static io.nop.biz.BizConfigs.CFG_BIZ_QUERY_IN_OP_MAX_ALLOW_VALUE_SIZE;
import static io.nop.biz.BizErrors.ARG_ALLOW_FILTER_OP;
import static io.nop.biz.BizErrors.ARG_BIZ_OBJ_NAME;
import static io.nop.biz.BizErrors.ARG_COUNT;
import static io.nop.biz.BizErrors.ARG_FILTER_OP;
import static io.nop.biz.BizErrors.ARG_MAX_COUNT;
import static io.nop.biz.BizErrors.ARG_PROP_NAME;
import static io.nop.biz.BizErrors.ARG_VALUE_NAME;
import static io.nop.biz.BizErrors.ERR_BIZ_PROP_NOT_SUPPORT_FILTER_OP;
import static io.nop.biz.BizErrors.ERR_BIZ_PROP_NOT_SUPPORT_QUERY;
import static io.nop.biz.BizErrors.ERR_BIZ_QUERY_IN_OP_TOO_MANY_VALUE;
import static io.nop.biz.BizErrors.ERR_BIZ_QUERY_NOT_SUPPORT_COMPARE_WITH_VALUE_PROP;
import static io.nop.biz.BizErrors.ERR_BIZ_UNKNOWN_QUERY_PROP;

public class ObjMetaBasedFilterValidator extends FilterBeanValidator {
    static final Set<String> DEFAULT_ALLOW_FILTER_OP = CollectionHelper.buildImmutableSet(FilterOp.EQ.name(),
            FilterOp.IN.name(), FilterOp.DATE_BETWEEN.name(), FilterOp.DATETIME_BETWEEN.name());

    private final IObjMeta objMeta;
    private final IBizObjectManager bizObjectManager;

    public ObjMetaBasedFilterValidator(IObjMeta objMeta, IBizObjectManager bizObjectManager) {
        this.objMeta = objMeta;
        this.bizObjectManager = bizObjectManager;
    }

    @Override
    protected void validateVarFilter(FilterOp filterOp, String name, ITreeBean filter, IVariableScope scope) {
        IObjPropMeta propMeta = BizObjMetaHelper.getPropMeta(objMeta, name, BizConstants.TAG_QUERYABLE, bizObjectManager);
        if (propMeta == null) {
            if (OrmConstants.PROP_ID.equals(name)) {
                propMeta = objMeta.getIdProp();
            }
        }
        if (propMeta == null)
            throw new NopException(ERR_BIZ_UNKNOWN_QUERY_PROP).param(ARG_BIZ_OBJ_NAME, objMeta.getBizObjName())
                    .param(ARG_PROP_NAME, name).param(ARG_FILTER_OP, filterOp.name());

        if (!propMeta.isQueryable()) {
            throw new NopException(ERR_BIZ_PROP_NOT_SUPPORT_QUERY).param(ARG_BIZ_OBJ_NAME, objMeta.getBizObjName())
                    .param(ARG_PROP_NAME, name).param(ARG_FILTER_OP, filterOp.name());
        }

        Set<String> allowFilterOps = propMeta.getAllowFilterOp();
        // 缺省情况下只允许按照等于条件进行查询
        if (CollectionHelper.isEmpty(allowFilterOps))
            allowFilterOps = DEFAULT_ALLOW_FILTER_OP;

        if (!allowFilterOps.contains(filterOp.name()))
            throw new NopException(ERR_BIZ_PROP_NOT_SUPPORT_FILTER_OP).param(ARG_BIZ_OBJ_NAME, objMeta.getBizObjName())
                    .param(ARG_PROP_NAME, name).param(ARG_FILTER_OP, filterOp.name())
                    .param(ARG_ALLOW_FILTER_OP, allowFilterOps);

        if (filterOp == FilterOp.IN) {
            Object value = getValue(filter);
            if (value != null) {
                Collection<?> c = CollectionHelper.toCollection(value, true);
                if (c != null && c.size() > CFG_BIZ_QUERY_IN_OP_MAX_ALLOW_VALUE_SIZE.get())
                    throw new NopException(ERR_BIZ_QUERY_IN_OP_TOO_MANY_VALUE)
                            .param(ARG_BIZ_OBJ_NAME, objMeta.getBizObjName()).param(ARG_PROP_NAME, name)
                            .param(ARG_COUNT, c.size())
                            .param(ARG_MAX_COUNT, CFG_BIZ_QUERY_IN_OP_MAX_ALLOW_VALUE_SIZE.get());
            }
        }
    }

    @Override
    protected void validateValueVarName(String name, ITreeBean filter, IVariableScope scope) {
        throw new NopException(ERR_BIZ_QUERY_NOT_SUPPORT_COMPARE_WITH_VALUE_PROP).param(ARG_PROP_NAME, getName(filter))
                .param(ARG_VALUE_NAME, name);
    }
}