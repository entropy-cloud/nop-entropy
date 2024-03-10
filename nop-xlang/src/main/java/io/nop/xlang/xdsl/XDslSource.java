/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xdsl;

import io.nop.core.lang.xml.XNode;
import io.nop.xlang.xdef.IXDefinition;

import java.util.ArrayList;
import java.util.List;

public class XDslSource {
    private final XNode node;
    private final IXDefinition def;
    private boolean ignoreDefaultExtends;

    private XDslSource parent;

    private final List<XDslSource> staticExtends = new ArrayList<>();
    private final List<XDslSource> dynamicExtends = new ArrayList<>();

    public XDslSource(XNode node, IXDefinition def) {
        this.node = node;
        this.def = def;
    }

    public boolean isIgnoreDefaultExtends() {
        return ignoreDefaultExtends;
    }

    public void setIgnoreDefaultExtends(boolean ignoreDefaultExtends) {
        this.ignoreDefaultExtends = ignoreDefaultExtends;
    }

    public IXDefinition getDef() {
        return def;
    }

    public IXDefinition getXDef() {
        return def;
    }

    public XNode getNode() {
        return node;
    }

    public XDslSource getParent() {
        return parent;
    }

    public void setParent(XDslSource parent) {
        this.parent = parent;
    }

    public void clearStaticExtends() {
        this.staticExtends.clear();
    }

    public void addStaticSource(XDslSource source) {
        source.setParent(this);
        staticExtends.add(source);
    }

    public void addDynamicSource(XDslSource source) {
        source.setParent(this);
        dynamicExtends.add(source);
    }

    /**
     * 将静态和动态基类整理为线性列表，不包含当前节点本身。
     *
     * @return
     */
    public List<XDslSource> getLinearizedExtends() {
        List<XDslSource> list = new ArrayList<>();
        _collectExtends(list);
        return list;
    }

    void _collectExtends(List<XDslSource> list) {
        for (XDslSource staticExtend : this.staticExtends) {
            staticExtend._collectExtends(list);
            list.add(staticExtend);
        }

        for (XDslSource dynamicExtend : this.dynamicExtends) {
            dynamicExtend._collectExtends(list);
            list.add(dynamicExtend);
        }
    }

    public static List<XDslSource> collectAllExtends(List<XDslSource> list) {
        List<XDslSource> ret = new ArrayList<>();
        for (XDslSource source : list) {
            source._collectExtends(ret);
            ret.add(source);
        }
        return ret;
    }
}
