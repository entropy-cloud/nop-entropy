/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.web.biz;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.context.IServiceContext;
import io.nop.web.page.PageProvider;

import jakarta.inject.Inject;
import java.util.Map;

import static io.nop.web.WebErrors.ERR_WEB_PAGE_NOT_ALLOW_EDIT;
import static io.nop.web.page.WebPageHelper.checkPageFile;
import static io.nop.web.page.WebPageHelper.removeGeneratedId;

@BizModel("PageProvider")
public class PageProviderBizModel {

    @Inject
    PageProvider pageProvider;

    @InjectValue("@cfg:nop.web.page-provider.edit-enabled|true")
    boolean editEnabled;

    @BizQuery
    public Map<String, Object> getPage(@Name("path") String path, IServiceContext context) {
        checkPageFile(path);
        String locale = ContextProvider.currentLocale();
        Map<String, Object> page = pageProvider.getPage(path, locale);
        return page;
    }

    @BizQuery
    public Map<String, Object> getPageSource(@Name("path") String path) {
        if (!editEnabled)
            throw new NopException(ERR_WEB_PAGE_NOT_ALLOW_EDIT);

        checkPageFile(path);
        Map<String, Object> data = pageProvider.getPageSource(path);
        return data;
    }

    @BizMutation
    public void savePageSource(@Name("path") String path, @Name("data") Map<String, Object> data) {
        if (!editEnabled)
            throw new NopException(ERR_WEB_PAGE_NOT_ALLOW_EDIT);

        checkPageFile(path);
        removeGeneratedId(data);
        pageProvider.savePageSource(path, data);
    }

    @BizMutation
    public void rollbackPageSource(@Name("path") String path) {
        if (!editEnabled)
            throw new NopException(ERR_WEB_PAGE_NOT_ALLOW_EDIT);

        checkPageFile(path);
        pageProvider.rollback(path, null);
    }
}