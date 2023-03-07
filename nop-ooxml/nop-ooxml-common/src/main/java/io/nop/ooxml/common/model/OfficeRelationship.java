/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.ooxml.common.model;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.xml.XNode;
import io.nop.ooxml.common.OfficeConstants;
import io.nop.ooxml.common.constants.TargetMode;

import java.io.Serializable;

public class OfficeRelationship implements Serializable {
    private static final long serialVersionUID = 7227171285891489949L;

    private final SourceLocation loc;
    private final String id;
    private final String type;
    private final String target;
    private final TargetMode targetMode;

    public OfficeRelationship(SourceLocation loc, String id, String type, String target, TargetMode targetMode) {
        this.loc = loc;
        this.id = id;
        this.type = type;
        this.target = target;
        this.targetMode = targetMode;
    }

    public SourceLocation getLocation() {
        return loc;
    }

    public XNode toNode() {
        XNode relNode = XNode.make("Relationship");
        relNode.setAttr("Id", getId());
        relNode.setAttr("Type", getType());
        relNode.setAttr("Target", getTarget());

        if (getTargetMode() == TargetMode.External) {
            relNode.setAttr("TargetMode", getTargetMode().toString());
        }
        return relNode;
    }

    public static OfficeRelationship parse(XNode node) {
        String id = node.attrText("Id");
        String type = node.attrText("Type");
        String target = node.attrText("Target");
        boolean external = TargetMode.External.name().equals(node.attrText("TargetMode"));
        return new OfficeRelationship(node.getLocation(), id, type, target,
                external ? TargetMode.External : TargetMode.Internal);
    }

    public boolean isExternalLink() {
        return targetMode == TargetMode.External && OfficeConstants.NS_LINK.equals(type);
    }

    public boolean isImage() {
        return OfficeConstants.NS_IMAGE.equals(type);
    }

    public String getId() {
        return id;
    }

    /**
     * 得到除去rId前缀部分后的字符，一般为数字类型
     */
    public String getIdNoPrefix() {
        if (id == null)
            return null;
        if (id.startsWith("rId"))
            return id.substring("rId".length());
        return id;
    }

    public String getType() {
        return type;
    }

    public String getTarget() {
        return target;
    }

    public TargetMode getTargetMode() {
        return targetMode;
    }

}