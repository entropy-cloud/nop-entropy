/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.sql_lib.dict;

import io.nop.api.core.beans.DictBean;
import io.nop.api.core.beans.DictOptionBean;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.core.CoreConstants;
import io.nop.core.context.IEvalContext;
import io.nop.core.context.IServiceContext;
import io.nop.core.dict.DictProvider;
import io.nop.core.dict.IDictLoader;
import io.nop.core.dict.IDictProvider;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.orm.OrmConstants;
import io.nop.orm.sql_lib.ISqlLibManager;
import io.nop.orm.sql_lib.SqlItemModel;
import io.nop.xlang.api.XLang;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;

public class SqlLibDictLoader implements IDictLoader {

    private ISqlLibManager sqlLibManager;

    @Inject
    public void setSqlLibManager(ISqlLibManager sqlLibManager) {
        this.sqlLibManager = sqlLibManager;
    }

    @Override
    public boolean supportDict(String dictName) {
        return dictName.startsWith(OrmConstants.SQL_DICT_PREFIX);
    }

    @PostConstruct
    public void init() {
        IDictProvider dictProvider = DictProvider.instance();
        dictProvider.addDictLoader(OrmConstants.SQL_DICT_PREFIX, this);
    }

    @PreDestroy
    public void destroy() {
        IDictProvider dictProvider = DictProvider.instance();
        dictProvider.removeDictLoader(OrmConstants.SQL_DICT_PREFIX, this);
    }

    @Override
    public DictBean loadDict(String locale, String dictName, IEvalContext ctx) {
        String sqlName = StringHelper.removeHead(dictName, OrmConstants.SQL_DICT_PREFIX);
        checkDictSql(sqlName);

        SqlItemModel sqlModel = sqlLibManager.getSqlItemModel(sqlName);
        boolean isStatic = ConvertHelper.toPrimitiveBoolean(sqlModel.getExtProp(OrmConstants.EXT_PROP_DICT_STATIC));
        String dictValueType = ConvertHelper.toString(sqlModel.getExtProp(OrmConstants.EXT_PROP_DICT_VALUE_TYPE));
        boolean normalized = ConvertHelper
                .toPrimitiveBoolean(sqlModel.getExtProp(OrmConstants.EXT_PROP_DICT_NORMALIZED));

        DictBean dictBean = new DictBean();
        dictBean.setNormalized(normalized);
        dictBean.setLocale(locale);
        dictBean.setStatic(isStatic);
        dictBean.setDescription(sqlModel.getDescription());
        dictBean.setLabel(sqlModel.getDisplayName());
        dictBean.setName(dictName);
        dictBean.setValueType(dictValueType);

        IServiceContext context = IServiceContext.fromEvalContext(ctx);
        IEvalScope scope = XLang.newEvalScope();
        if (context != null)
            scope.setLocalValue(CoreConstants.VAR_SVC_CTX, context);

        scope.setLocalValue(null, OrmConstants.SQL_ARG_LOCALE, locale);
        List<Object> options = (List<Object>) sqlLibManager.invoke(sqlName, null, scope);
        List<DictOptionBean> optionBeans = options.stream()
                .map(v -> (DictOptionBean) BeanTool.castBeanToType(v, DictOptionBean.class))
                .collect(Collectors.toList());

        dictBean.setOptions(optionBeans);
        return dictBean;
    }

    @Override
    public boolean existsDict(String dictName) {
        String sqlName = StringHelper.removeHead(dictName, OrmConstants.SQL_DICT_PREFIX);
        checkDictSql(sqlName);
        try {
            return sqlLibManager.getSqlItemModel(dictName) != null;
        } catch (NopException e) {
            return false;
        }
    }

    void checkDictSql(String sqlName) {
        if (!sqlName.endsWith(OrmConstants.SQL_DICT_POSTFIX))
            throw new IllegalArgumentException(
                    "nop.sql.invalid-sql-name:sqlName must ends with '-sql',sqlName=" + sqlName);
    }
}