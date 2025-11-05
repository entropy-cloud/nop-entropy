/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.svg.ext;

import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.IXNodeExtension;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.ext.StyleMap;
import io.nop.svg.model.SVGTransform;

import java.util.List;

public class SVGTransformExtension implements IXNodeExtension {
    private final XNode node;

    private SVGTransform transform;

    public SVGTransformExtension(XNode node) {
        this.node = node;
        this.syncFromNode(node);
    }

    public static SVGTransformExtension getFromNode(XNode node) {
        return (SVGTransformExtension) node.getExtension(SVGTransformExtension.class.getName());
    }

    public static SVGTransformExtension makeFromNode(XNode node) {
        SVGTransformExtension extension = (SVGTransformExtension) node
                .getExtension(SVGTransformExtension.class.getName());
        if (extension == null) {
            extension = new SVGTransformExtension(node);
            node.setExtension(SVGTransformExtension.class.getName(), extension);
        }
        return extension;
    }

    @Override
    public void syncFromNode(XNode node) {
        transform = SVGTransform.parse(node.attrText("transform"));
    }

    public void syncToNode(XNode node) {
        if (transform != null) {
            node.setAttr("transform", transform.toSVGString());
        }
    }

    public SVGTransform getTransform() {
        return transform;
    }

    /**
     * 得到所有父节点累积到当前节点的transform
     *
     * @return
     */
    public SVGTransform getConcatenatedParentTransform() {
        List<XNode> parents = node.parents();
        SVGTransform trans = null;
        for (XNode parent : parents) {
            SVGTransformExtension extension = SVGTransformExtension.getFromNode(parent);
            if (extension == null || extension.getTransform() == null) {
                continue;
            }
            if (trans == null) {
                trans = extension.getTransform().clone();
            } else {
                trans.multiply(extension.getTransform());
            }
        }
        return trans;
    }

    /**
     * 转换为绝对坐标系。
     */
    public SVGTransform getAbsoluteTransform() {
        SVGTransform trans = getConcatenatedParentTransform();
        if (trans != null) {
            if (transform != null) {
                trans.multiply(transform);
            }
        } else {
            trans = transform;
        }
        return trans;
    }

    Number getNumber(String propName) {
        String s = node.attrText(propName);
        if (s == null)
            return null;
        if (s.endsWith("px"))
            s = s.substring(0, s.length() - 2);
        if (StringHelper.isBlank(s))
            return null;
        return StringHelper.parseNumber(s);
    }

    public Number getX() {
        return getNumber("x");
    }

    public Number getY() {
        return getNumber("y");
    }

    public Number getCx() {
        return getNumber("cx");
    }

    public Number getCy() {
        return getNumber("cy");
    }

    public Number getWidth() {
        Number ret = getNumber("width");
        if (ret == null) {
            ret = StyleMap.makeFromNode(node).getNumber("width");
        }
        return ret;
    }

    public Number getHeight() {
        Number ret = getNumber("height");
        if (ret == null)
            ret = StyleMap.makeFromNode(node).getNumber("height");
        return ret;
    }

    public int getExtGap(int defaultValue) {
        Number num = StyleMap.makeFromNode(node).getNumber("x-gap");
        return num == null ? defaultValue : num.intValue();
    }
}