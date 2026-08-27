/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dyn.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.biz.crud.CrudBizModel;
import io.nop.biz.crud.EntityData;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IServiceContext;
import io.nop.core.resource.ResourceHelper;
import io.nop.dao.api.DaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.dyn.dao.entity.NopDynModule;
import io.nop.dyn.dao.entity.NopDynPage;
import io.nop.dyn.service.NopDynConstants;
import io.nop.dyn.biz.INopDynPageBiz;

import java.util.Map;

import static io.nop.dyn.service.NopDynErrors.ARG_PAGE_NAME;
import static io.nop.dyn.service.NopDynErrors.ARG_PATH;
import static io.nop.dyn.service.NopDynErrors.ERR_DYN_INVALID_PAGE_NAME;
import static io.nop.dyn.service.NopDynErrors.ERR_DYN_INVALID_PAGE_PATH;
import static io.nop.dyn.service.NopDynErrors.ERR_DYN_PAGE_NOT_EXISTS;

@BizModel("NopDynPage")
public class NopDynPageBizModel extends CrudBizModel<NopDynPage> implements INopDynPageBiz {

    public NopDynPageBizModel() {
        setEntityName(NopDynPage.class.getName());
    }


    @Override
    protected void defaultPrepareUpdate(EntityData<NopDynPage> entityData, IServiceContext context) {
        super.defaultPrepareUpdate(entityData, context);

        checkValidPage(entityData.getEntity());
    }

    @Override
    protected void defaultPrepareSave(EntityData<NopDynPage> entityData, IServiceContext context) {
        super.defaultPrepareSave(entityData, context);

        checkValidPage(entityData.getEntity());

        if (StringHelper.isBlank(entityData.getEntity().getPageContent())) {
            entityData.getEntity().setPageContent("{\"type\":\"page\"}");
        }
    }

    protected void checkValidPage(NopDynPage page) {
        if (!ResourceHelper.isValidRelativeName(page.getPageName()))
            throw new NopException(ERR_DYN_INVALID_PAGE_NAME)
                    .param(ARG_PAGE_NAME, page.getPageName());

        page.setPageName(StringHelper.removeTail(page.getPageName(), NopDynConstants.POSTFIX_PAGE_JSON));
    }

    @BizQuery
    public Map<String, Object> getPageJson(@Name("id") String id, IServiceContext context) {
        NopDynPage page = get(id, false, context);
        return page.getPageContentComponent().get_jsonMap();
    }

    @BizMutation
    public void savePageJson(@Name("id") String id, @Name("data") Map<String, Object> data, IServiceContext context) {
        NopDynPage page = get(id, false, context);
        page.getPageContentComponent().set_jsonValue(data);
    }

    @BizQuery
    public Map<String, Object> getPage(@Name("path") String path, IServiceContext context) {
        // 兼容.page.json（历史契约）与.page.yaml（getPagePath/genPageFile的实际后缀）两种路径
        if (!ResourceHelper.isNormalVirtualPath(path)
                || (!path.endsWith(NopDynConstants.POSTFIX_PAGE_JSON) && !path.endsWith(NopDynConstants.POSTFIX_PAGE_YAML)))
            throw new NopException(ERR_DYN_INVALID_PAGE_PATH)
                    .param(ARG_PATH, path);

        String moduleId = ResourceHelper.getModuleId(path);

        // 页面可能位于任意pageGroup分组（getPagePath按pageGroup构造路径，列缺省值为"pages"），
        // 不能硬编码匹配"/pages/"段，否则非默认分组的页面无法经该API访问。
        // pos指向moduleId后的第一个'/'分隔符，分组段位于pos+1到下一个'/'之间
        int pos = moduleId.length() + 1;
        int groupPos = path.indexOf('/', pos + 1);
        if (groupPos < 0 || groupPos == pos + 1)
            throw new NopException(ERR_DYN_INVALID_PAGE_PATH)
                    .param(ARG_PATH, path);
        String pageGroup = path.substring(pos + 1, groupPos);

        String pageFullName = path.substring(groupPos + 1);
        String postfix = pageFullName.endsWith(NopDynConstants.POSTFIX_PAGE_JSON)
                ? NopDynConstants.POSTFIX_PAGE_JSON : NopDynConstants.POSTFIX_PAGE_YAML;
        String pageName = StringHelper.removeTail(pageFullName, postfix);
        if (StringHelper.isEmpty(pageName))
            throw new NopException(ERR_DYN_INVALID_PAGE_PATH)
                    .param(ARG_PATH, path);

        // 路径中是nopModuleId，而NopDynPage外键引用的是NopDynModule的主键moduleId，需要先解析模块
        IEntityDao<NopDynModule> moduleDao = DaoProvider.instance().daoFor(NopDynModule.class);
        NopDynModule moduleExample = moduleDao.newEntity();
        moduleExample.setNopModuleId(moduleId);
        NopDynModule module = moduleDao.findFirstByExample(moduleExample);

        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq(NopDynPage.PROP_NAME_pageName, pageName));
        query.addFilter(FilterBeans.eq(NopDynPage.PROP_NAME_pageGroup, pageGroup));
        if (module != null)
            query.addFilter(FilterBeans.eq(NopDynPage.PROP_NAME_moduleId, module.getModuleId()));

        NopDynPage entity = findFirst(query, null, context);
        if (entity == null)
            throw new NopException(ERR_DYN_PAGE_NOT_EXISTS)
                    .param(ARG_PATH, path);

        return entity.getPageContentComponent().get_jsonMap();
    }
}
