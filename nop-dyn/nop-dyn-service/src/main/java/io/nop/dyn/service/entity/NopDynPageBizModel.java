
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
import io.nop.dyn.dao.entity.NopDynPage;
import io.nop.dyn.service.NopDynConstants;

import java.util.Map;

import static io.nop.dyn.service.NopDynErrors.ARG_PAGE_NAME;
import static io.nop.dyn.service.NopDynErrors.ARG_PATH;
import static io.nop.dyn.service.NopDynErrors.ERR_DYN_INVALID_PAGE_NAME;
import static io.nop.dyn.service.NopDynErrors.ERR_DYN_INVALID_PAGE_PATH;
import static io.nop.dyn.service.NopDynErrors.ERR_DYN_PAGE_NOT_EXISTS;

@BizModel("NopDynPage")
public class NopDynPageBizModel extends CrudBizModel<NopDynPage> {
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

        if (StringHelper.isEmpty(entityData.getEntity().getPageContent())) {
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
        if (!ResourceHelper.isNormalVirtualPath(path) || !path.endsWith(NopDynConstants.POSTFIX_PAGE_JSON))
            throw new NopException(ERR_DYN_INVALID_PAGE_PATH)
                    .param(ARG_PATH, path);

        String moduleId = ResourceHelper.getModuleId(path);
        String moduleName = ResourceHelper.getModuleNameFromModuleId(moduleId);

        int pos = moduleId.length() + 1;
        if (StringHelper.startsWithAt(path, "/pages/", pos))
            throw new NopException(ERR_DYN_INVALID_PAGE_PATH)
                    .param(ARG_PATH, path);

        String pageName = StringHelper.removeTail(path.substring(pos + "/pages/".length()), NopDynConstants.POSTFIX_PAGE_JSON);

        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq(NopDynPage.PROP_NAME_pageName, pageName));
        query.addFilter(FilterBeans.eq("module.moduleName", moduleName));

        NopDynPage entity = findFirst(query, null, context);
        if (entity == null)
            throw new NopException(ERR_DYN_PAGE_NOT_EXISTS)
                    .param(ARG_PATH, path);

        return entity.getPageContentComponent().get_jsonMap();
    }
}
