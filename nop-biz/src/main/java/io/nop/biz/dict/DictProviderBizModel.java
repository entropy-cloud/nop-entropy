/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.biz.dict;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.directive.Auth;
import io.nop.api.core.beans.DictBean;
import io.nop.api.core.context.ContextProvider;
import io.nop.core.context.IServiceContext;
import io.nop.core.dict.DictProvider;

@Locale("zh-CN")
@BizModel("DictProvider")
public class DictProviderBizModel {

    @BizQuery
    @Auth(roles = "user")
    @Description("获取字典")
    public DictBean getDict(@Name("dictName") String dictName, IServiceContext context) {
        String locale = ContextProvider.currentLocale();
        return DictProvider.instance().requireDict(locale, dictName, context.getCache(), context);
    }
}
