/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.unittest;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.core.CoreConstants;
import io.nop.core.lang.json.JsonTool;

import java.util.Map;
import java.util.Objects;

public class MarkdownTestSection {
    private String fileName;
    private String title;
    private String type;

    private SourceLocation location;
    private String source;

    private Map<String, MarkdownCodeBlock> attributes;

    public String toString() {
        return fileName + "/" + title;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public SourceLocation getLocation() {
        return location;
    }

    public void setLocation(SourceLocation location) {
        this.location = location;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Map<String, MarkdownCodeBlock> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, MarkdownCodeBlock> attributes) {
        this.attributes = attributes;
    }

    public MarkdownCodeBlock getAttribute(String name) {
        return attributes == null ? null : attributes.get(name);
    }

    public String getStringAttribute(String name) {
        MarkdownCodeBlock block = getAttribute(name);
        if (block == null)
            return null;
        return block.getSource();
    }

    public boolean matchErrorCode(String errorCode) {
        String err = getStringAttribute(CoreConstants.MD_ATTR_ERROR_CODE);
        return Objects.equals(err, errorCode);
    }

    public String getErrorCodeAttr() {
        return getStringAttribute(CoreConstants.MD_ATTR_ERROR_CODE);
    }

    public String getExceptionAttr() {
        return getStringAttribute(CoreConstants.MD_ATTR_EXCEPTION);
    }

    public boolean matchMessage(String message) {
        String tpl = getStringAttribute(CoreConstants.MD_ATTR_EXCEPTION);
        return StringHelper.matchSimplePattern(message, tpl);
    }

    public String getReturnAttr() {
        return getStringAttribute(CoreConstants.MD_ATTR_RETURN);
    }

    public boolean matchReturn(Object value) {
        MarkdownCodeBlock attr = getAttribute(CoreConstants.MD_ATTR_RETURN);
        if (attr == null)
            return true;

        String type = attr.getType();
        if (CoreConstants.VALUE_TYPE_JSON.equals(type)) {
            value = JsonTool.instance().stringify(value, null, "  ");
        }
        return Objects.equals(attr.getSource(), StringHelper.toString(value, null));
    }
}