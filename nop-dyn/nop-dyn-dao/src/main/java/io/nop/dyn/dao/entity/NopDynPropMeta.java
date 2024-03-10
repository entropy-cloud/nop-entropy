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
import io.nop.commons.util.TagsHelper;
import io.nop.dyn.dao.entity._gen._NopDynPropMeta;

import java.util.Set;


@BizObjName("NopDynPropMeta")
public class NopDynPropMeta extends _NopDynPropMeta implements ITagSetSupport {

    @Override
    public Set<String> getTagSet() {
        return ConvertHelper.toCsvSet(getTagsText());
    }

    public void setTagSet(Set<String> tagSet) {
        this.setTagsText(TagsHelper.toString(tagSet));
    }
}
