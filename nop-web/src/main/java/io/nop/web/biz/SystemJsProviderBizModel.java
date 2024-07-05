/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.web.biz;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.directive.Auth;
import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.beans.WebContentBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IServiceContext;
import io.nop.web.page.DynamicWebFileProvider;
import jakarta.inject.Inject;

import static io.nop.web.WebErrors.ERR_WEB_PAGE_NOT_ALLOW_EDIT;
import static io.nop.web.page.WebPageHelper.checkJsFile;
import static io.nop.web.page.WebPageHelper.checkXjsFile;

@BizModel("SystemJsProvider")
public class SystemJsProviderBizModel {

    @Inject
    DynamicWebFileProvider jsProvider;

    @InjectValue("@cfg:nop.web.js-provider.edit-enabled|true")
    boolean editEnabled;

    @BizQuery
    @Auth(publicAccess = true)
    public WebContentBean getJs(@Name("path") String path, IServiceContext context) {
        path = StringHelper.removeUrlQuery(path);
        checkJsFile(path);
        return WebContentBean.js(jsProvider.getJs(path));
    }

    @BizQuery
    public WebContentBean getJsSource(@Name("path") String path) {
        if (!editEnabled)
            throw new NopException(ERR_WEB_PAGE_NOT_ALLOW_EDIT);

        path = StringHelper.removeUrlQuery(path);
        checkXjsFile(path);
        return WebContentBean.js(jsProvider.getJsSource(path));
    }

    @BizMutation
    public void saveJsSource(@Name("path") String path, @Name("source") String source) {
        if (!editEnabled)
            throw new NopException(ERR_WEB_PAGE_NOT_ALLOW_EDIT);
        path = StringHelper.removeUrlQuery(path);
        checkXjsFile(path);
        jsProvider.saveJsSource(path, source);
    }

    @BizMutation
    public void rollbackJsSource(@Name("path") String path) {
        if (!editEnabled)
            throw new NopException(ERR_WEB_PAGE_NOT_ALLOW_EDIT);

        path = StringHelper.removeUrlQuery(path);
        checkXjsFile(path);
        jsProvider.rollback(path, null);
    }
}