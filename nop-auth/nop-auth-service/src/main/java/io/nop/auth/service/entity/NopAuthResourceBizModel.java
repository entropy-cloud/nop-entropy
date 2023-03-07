/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.auth.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.auth.core.sitemap.ISiteMapProvider;
import io.nop.auth.dao.entity.NopAuthResource;
import io.nop.auth.service.NopAuthConstants;
import io.nop.biz.crud.CrudBizModel;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IServiceContext;

import javax.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;

import static io.nop.auth.dao.entity._gen._NopAuthResource.PROP_NAME_children;
import static io.nop.orm.support.OrmEntityHelper.buildCollectionName;

@BizModel("NopAuthResource")
public class NopAuthResourceBizModel extends CrudBizModel<NopAuthResource> {
    @Inject
    ISiteMapProvider siteMapProvider;

    public NopAuthResourceBizModel() {
        setEntityName(NopAuthResource.class.getName());
    }

    @Override
    protected void afterEntityChange(NopAuthResource entity, IServiceContext context) {
        super.afterEntityChange(entity, context);
        siteMapProvider.refreshCache();
    }

    @BizQuery
    public void refreshSiteMapCache() {
        siteMapProvider.refreshCache();
    }

    @BizQuery
    public List<NopAuthResource> getMenuTree(@Name("siteId") String siteId, IServiceContext context) {
        if (StringHelper.isEmpty(siteId))
            siteId = NopAuthConstants.SITE_ID_MAIN;

        NopAuthResource example = new NopAuthResource();
        example.setSiteId(siteId);

        // 查找所有资源对象，并在内存中组织为Tree结构
        List<NopAuthResource> allResources = dao().findAllByExample(example);
        String collectionName = buildCollectionName(getEntityName(), PROP_NAME_children);
        orm().assembleAllCollectionInMemory(collectionName);

        List<NopAuthResource> topMenus = allResources.stream().filter(menu -> {
            if (menu.isTopMenu())
                return true;
            if (menu.getParentId() == null)
                return true;
            if (menu.getParent().orm_state().isMissing())
                return true;
            return false;
        }).collect(Collectors.toList());

        return topMenus;
    }
}