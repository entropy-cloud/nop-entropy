/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.beans;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.nop.api.core.util.ISourceLocationGetter;
import io.nop.api.core.util.ISourceLocationSetter;

import java.util.List;
import java.util.Map;

public interface ITreeBean extends ISourceLocationGetter, ISourceLocationSetter {
    String getTagName();

    void setTagName(String tagName);

    Map<String, Object> getAttrs();

    @JsonIgnore
    default int getAttrCount() {
        Map<String, Object> attrs = getAttrs();
        return attrs == null ? 0 : attrs.size();
    }

    Object getAttr(String name);

    Object getContentValue();

    List<? extends ITreeBean> getChildren();

    @JsonIgnore
    default int getChildCount() {
        List<?> children = getChildren();
        return children == null ? 0 : children.size();
    }

    TreeBean toTreeBean();

    ITreeBean cloneInstance();

    Object toJsonObject();

    ITreeBean childWithAttr(String attrName, Object attrValue);

    ITreeBean nodeWithAttr(String attrName, Object attrValue);

    default Object cascadeGetAttr(String attrName) {
        Object value = getAttr(attrName);
        if (value != null)
            return value;
        List<? extends ITreeBean> children = getChildren();
        if (children == null)
            return null;
        for (ITreeBean child : children) {
            value = child.cascadeGetAttr(attrName);
            if (value != null)
                return value;
        }
        return null;
    }
}