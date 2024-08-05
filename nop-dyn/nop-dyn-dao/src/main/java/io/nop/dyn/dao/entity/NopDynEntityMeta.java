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
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.lang.ITagSetSupport;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.TagsHelper;
import io.nop.dyn.dao.entity._gen._NopDynEntityMeta;
import io.nop.orm.model.IEntityModel;

import java.util.Set;

import static io.nop.dyn.dao.NopDynDaoErrors.ARG_ENTITY_NAME;
import static io.nop.dyn.dao.NopDynDaoErrors.ARG_PROP_NAME;
import static io.nop.dyn.dao.NopDynDaoErrors.ERR_DYN_ENTITY_NO_PROP;

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

    public String getFullEntityName() {
        String entityName = getEntityName();
        if (entityName.indexOf('.') > 0)
            return entityName;
        NopDynModule module = getModule();
        if (module == null)
            return "app." + entityName;
        if (!module.getBasePackageName().endsWith(".entity"))
            return module.getBasePackageName() + ".entity." + entityName;
        return module.getBasePackageName() + "." + entityName;
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

    public NopDynPropMeta getPropByName(String propName) {
        for (NopDynPropMeta propMeta : getPropMetas()) {
            if (propMeta.getPropName().equals(propName))
                return propMeta;
        }
        return null;
    }

    public NopDynPropMeta requirePropByName(String propName) {
        NopDynPropMeta propMeta = getPropByName(propName);
        if (propMeta == null)
            throw new NopException(ERR_DYN_ENTITY_NO_PROP)
                    .param(ARG_ENTITY_NAME, getEntityName())
                    .param(ARG_PROP_NAME, propName);
        return propMeta;
    }
}