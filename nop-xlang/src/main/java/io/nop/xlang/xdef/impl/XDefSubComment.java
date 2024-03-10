/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xdef.impl;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.component.AbstractFreezable;
import io.nop.xlang.xdef.IXDefSubComment;

@DataBean
public class XDefSubComment extends AbstractFreezable implements IXDefSubComment {
    private String displayName;
    private String description;

    public XDefSubComment applyOverride(IXDefSubComment comment) {
        XDefSubComment ret = new XDefSubComment();
        if (comment.getDisplayName() != null) {
            ret.setDisplayName(comment.getDisplayName());
        } else {
            ret.setDisplayName(displayName);
        }

        if (comment.getDescription() != null) {
            ret.setDescription(comment.getDescription());
        } else {
            ret.setDescription(description);
        }
        return ret;
    }

    @JsonIgnore
    @Override
    public SourceLocation getLocation() {
        return super.getLocation();
    }

    @JsonIgnore
    public void setLocation(SourceLocation loc) {
        super.setLocation(loc);
    }

    public void setDisplayName(String displayName) {
        checkAllowChange();
        this.displayName = StringHelper.emptyAsNull(displayName);
    }

    public void setDescription(String description) {
        checkAllowChange();
        this.description = StringHelper.strip(description);
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Override
    public String getDisplayName() {
        return displayName;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Override
    public String getDescription() {
        return description;
    }
}
