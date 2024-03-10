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
import io.nop.api.core.util.FreezeHelper;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.component.AbstractFreezable;
import io.nop.xlang.xdef.IXDefComment;
import io.nop.xlang.xdef.IXDefSubComment;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

@DataBean
public class XDefComment extends AbstractFreezable implements IXDefComment {
    private String mainDisplayName;
    private String mainDescription;

    private Map<String, XDefSubComment> subComments = Collections.emptyMap();

    public static XDefComment parseComment(XNode node) {
        String comment = node.getComment();
        if (StringHelper.isBlank(comment))
            return null;

        SourceLocation loc = node.getLocation();
        int lines = StringHelper.countChar(node.getComment(), '\n');
        if (loc != null)
            loc = loc.offset(-lines - 1, 0);
        return new XDefCommentParser().parseComment(loc, node.getComment());
    }

    @Override
    public void freeze(boolean cascade) {
        if (frozen())
            return;

        super.freeze(cascade);

        this.subComments = FreezeHelper.freezeMap(subComments, true);
    }

    @Override
    public IXDefComment applyOverride(IXDefComment comment) {
        XDefComment ret = new XDefComment();
        if (comment.getMainDisplayName() != null) {
            ret.setMainDisplayName(comment.getMainDisplayName());
        } else {
            ret.setMainDisplayName(mainDisplayName);
        }

        if (comment.getMainDescription() != null) {
            ret.setMainDescription(comment.getMainDescription());
        } else {
            ret.setMainDescription(mainDescription);
        }

        Map<String, XDefSubComment> subComments = new TreeMap<>(this.subComments);

        for (Map.Entry<String, ? extends IXDefSubComment> entry : comment.getSubComments().entrySet()) {
            XDefSubComment sub = subComments.computeIfAbsent(entry.getKey(), k -> new XDefSubComment());
            IXDefSubComment overrideSub = entry.getValue();
            subComments.put(entry.getKey(), sub.applyOverride(overrideSub));
        }

        ret.setSubComments(subComments);
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

    @JsonIgnore
    public boolean isEmpty() {
        if (mainDisplayName != null)
            return false;
        if (mainDescription != null)
            return false;

        return subComments.isEmpty();
    }

    public String toComment() {
        StringBuilder sb = new StringBuilder();
        if (mainDisplayName != null) {
            sb.append('[').append(mainDisplayName).append("]");
        }
        if (mainDescription != null) {
            if (mainDisplayName != null)
                sb.append(' ');
            sb.append(mainDescription);
        }

        if (!subComments.isEmpty()) {
            if (hasMainComment())
                sb.append("\n\n");
            for (String name : subComments.keySet()) {
                IXDefSubComment subComment = subComments.get(name);
                sb.append("@").append(name).append(' ');
                String displayName = subComment.getDisplayName();
                String desc = subComment.getDescription();
                if (displayName != null)
                    sb.append('[').append(displayName).append(']');
                if (desc != null) {
                    if (displayName != null)
                        sb.append(' ');
                    sb.append(desc);
                }
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    public boolean hasMainComment() {
        return !StringHelper.isEmpty(mainDisplayName) || !StringHelper.isEmpty(mainDescription);
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Override
    public String getMainDisplayName() {
        return mainDisplayName;
    }

    public void setMainDisplayName(String mainDisplayName) {
        checkAllowChange();
        this.mainDisplayName = StringHelper.strip(mainDisplayName);
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Override
    public Map<String, XDefSubComment> getSubComments() {
        return subComments;
    }

    public void setSubComments(Map<String, XDefSubComment> subComments) {
        checkAllowChange();
        this.subComments = subComments;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Override
    public String getMainDescription() {
        return mainDescription;
    }

    public void setMainDescription(String mainDescription) {
        checkAllowChange();
        this.mainDescription = StringHelper.strip(mainDescription);
    }

    public void addSubDisplayName(String subName, String displayName) {
        checkAllowChange();
        if (StringHelper.isEmpty(displayName))
            return;

        if (subComments.isEmpty())
            subComments = new TreeMap<>();

        subComments.computeIfAbsent(subName, k -> new XDefSubComment()).setDisplayName(displayName);
    }

    public void addSubDescription(String subName, String description) {
        checkAllowChange();
        if (StringHelper.isEmpty(description))
            return;

        if (subComments.isEmpty())
            subComments = new TreeMap<>();

        subComments.computeIfAbsent(subName, k -> new XDefSubComment()).setDescription(description);
    }
}