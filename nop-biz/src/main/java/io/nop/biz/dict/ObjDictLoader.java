/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.biz.dict;

import io.nop.api.core.beans.DictBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.biz.BizConstants;
import io.nop.biz.api.IBizObject;
import io.nop.biz.api.IBizObjectManager;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IEvalContext;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.dict.DictProvider;
import io.nop.core.dict.IDictLoader;
import io.nop.core.dict.IDictProvider;
import io.nop.graphql.core.GraphQLConstants;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;

import static io.nop.biz.BizErrors.ARG_DICT_NAME;
import static io.nop.biz.BizErrors.ERR_BIZ_INVALID_OBJ_DICT_NAME;

public class ObjDictLoader implements IDictLoader {
    private IBizObjectManager bizObjectManager;

    @Inject
    public void setBizObjectManager(IBizObjectManager bizObjectManager) {
        this.bizObjectManager = bizObjectManager;
    }

    @Override
    public boolean supportDict(String dictName) {
        return dictName.startsWith(BizConstants.OBJ_DICT_PREFIX);
    }

    @PostConstruct
    public void init() {
        IDictProvider dictProvider = DictProvider.instance();
        dictProvider.addDictLoader(BizConstants.OBJ_DICT_PREFIX, this);
    }

    @PreDestroy
    public void destroy() {
        IDictProvider dictProvider = DictProvider.instance();
        dictProvider.removeDictLoader(BizConstants.OBJ_DICT_PREFIX, this);
    }

    @Override
    public DictBean loadDict(String locale, String dictName, IEvalContext ctx) {
        String operationName = StringHelper.removeHead(dictName, BizConstants.OBJ_DICT_PREFIX);
        if (!StringHelper.isValidJavaVarName(operationName))
            throw new NopException(ERR_BIZ_INVALID_OBJ_DICT_NAME).param(ARG_DICT_NAME, dictName);

        String bizObjName, action;
        int pos = operationName.indexOf(GraphQLConstants.OBJ_ACTION_SEPARATOR);
        if (pos < 0) {
            bizObjName = operationName;
            action = BizConstants.METHOD_AS_DICT;
        } else {
            bizObjName = operationName.substring(0, pos);
            action = operationName.substring(pos + GraphQLConstants.OBJ_ACTION_SEPARATOR.length());
        }

        IBizObject bizObj = bizObjectManager.getBizObject(bizObjName);

        IServiceContext context = IServiceContext.fromEvalContext(ctx);
        if (context == null) {
            context = new ServiceContextImpl();
        }
        return (DictBean) FutureHelper.getResult(bizObj.invoke(action, null, null, context));
    }

    @Override
    public boolean existsDict(String dictName) {
        String operationName = StringHelper.removeHead(dictName, BizConstants.OBJ_DICT_PREFIX);
        String bizObjName, action;
        int pos = operationName.indexOf(GraphQLConstants.OBJ_ACTION_SEPARATOR);
        if (pos < 0) {
            bizObjName = operationName;
            action = BizConstants.METHOD_AS_DICT;
        } else {
            bizObjName = operationName.substring(0, pos);
            action = operationName.substring(pos + GraphQLConstants.OBJ_ACTION_SEPARATOR.length());
        }

        IBizObject bizObj = bizObjectManager.getBizObject(bizObjName);
        if (bizObj.getAction(action) == null)
            return false;

        return true;
    }
}
