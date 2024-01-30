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
