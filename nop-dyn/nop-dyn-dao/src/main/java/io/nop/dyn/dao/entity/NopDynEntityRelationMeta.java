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
import io.nop.dyn.dao.entity._gen._NopDynEntityRelationMeta;
import io.nop.orm.model.OrmRelationType;

import java.util.Set;


@BizObjName("NopDynEntityRelationMeta")
public class NopDynEntityRelationMeta extends _NopDynEntityRelationMeta implements ITagSetSupport {

    public boolean isToMany() {
        return OrmRelationType.m2m.equals(getRelationType()) || OrmRelationType.o2m.equals(getRelationType());
    }

    @Override
    public Set<String> getTagSet() {
        return ConvertHelper.toCsvSet(getTagsText());
    }

    public OrmRelationType getOrmRelationType() {
        return OrmRelationType.valueOf(getRelationType());
    }
}
