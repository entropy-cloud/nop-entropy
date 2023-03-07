/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xdsl;

import io.nop.core.lang.xml.XNode;
import io.nop.xlang.xdef.IXDefinition;

public class XDslExtendResult {
    private final XDslKeys keys;
    private IXDefinition xdef;
    private XNode base;
    private XNode node;
    private XNode config;
    private XNode postExtends;
    private XNode postParse;
    private boolean validated;

    public XDslExtendResult(XDslKeys keys) {
        this.keys = keys;
    }

    public boolean isValidated() {
        return validated;
    }

    public void setValidated(boolean validated) {
        this.validated = validated;
    }

    public XDslKeys getKeys() {
        return keys;
    }

    public IXDefinition getXdef() {
        return xdef;
    }

    public void setXdef(IXDefinition xdef) {
        this.xdef = xdef;
    }

    public XNode getNodeForDump() {
        XNode ret = node.cloneInstance();
        if (config != null) {
            XNode cfg = config.cloneInstance();
            ret.prependChild(cfg);
        }

        if (postParse != null) {
            ret.appendChild(postParse.cloneInstance());
        }
        if (validated)
            ret.setAttr(keys.VALIDATED, true);
        return ret;
    }

    public XNode getBase() {
        return base;
    }

    public void setBase(XNode base) {
        this.base = base;
    }

    public XNode getNode() {
        return node;
    }

    public void setNode(XNode node) {
        this.node = node;
    }

    public XNode getConfig() {
        return config;
    }

    public void setConfig(XNode config) {
        this.config = config;
    }

    public XNode getPostExtends() {
        return postExtends;
    }

    public void setPostExtends(XNode postExtends) {
        this.postExtends = postExtends;
    }

    public XNode getPostParse() {
        return postParse;
    }

    public void setPostParse(XNode postParse) {
        this.postParse = postParse;
    }
}
