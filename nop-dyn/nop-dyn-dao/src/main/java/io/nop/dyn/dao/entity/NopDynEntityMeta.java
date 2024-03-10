/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dyn.dao.entity;

import io.nop.api.core.annotations.biz.BizObjName;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.commons.lang.ITagSetSupport;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.TagsHelper;
import io.nop.dyn.dao.entity._gen._NopDynEntityMeta;
import io.nop.orm.model.IEntityModel;

import java.util.Set;

@BizObjName("NopDynEntityMeta")
public class NopDynEntityMeta extends _NopDynEntityMeta implements ITagSetSupport {

    private IEntityModel entityModel;

    public IEntityModel getEntityModel() {
        return entityModel;
    }

    public void setEntityModel(IEntityModel entityModel) {
        this.entityModel = entityModel;
    }

    /**
     * 是否定义实体属性。如果没有定义实体属性，则不需要生成实体对象和xmeta，仅生成xbiz
     */
    public boolean isHasProp() {
        return !getPropMetas().isEmpty();
    }

    public String forceGetTableName() {
        if (!StringHelper.isEmpty(getTableName()))
            return getTableName();
        String simpleName = StringHelper.simpleClassName(getEntityName());
        return StringHelper.camelCaseToUnderscore(simpleName, true);
    }

    public String getBizObjName() {
        return StringHelper.simpleClassName(getEntityName());
    }


    @Override
    public Set<String> getTagSet() {
        return ConvertHelper.toCsvSet(getTagsText());
    }

    public void setTagSet(Set<String> tagSet) {
        this.setTagsText(TagsHelper.toString(tagSet));
    }

    public String getMainPagePath() {
        NopDynModule module = getModule();
        String bizObjName = getBizObjName();
        return "/" + module.getNopModuleId() + "/pages/" + bizObjName + "/main.page.yaml";
    }
}