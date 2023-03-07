/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.lang.xml.parse;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.io.stream.ICharReader;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.core.lang.xml.IXNodeHandler;
import io.nop.core.lang.xml.XNode;

import java.util.Map;

/**
 * 仅解析根节点的属性
 */
public class XRootNodeParser extends XNodeParser {

    public XRootNodeParser() {
        this.handler(new ParseRootHandler());
    }

    @Override
    protected XNode doParse(SourceLocation loc, ICharReader in) {
        try {
            return super.doParse(loc, in);
        } catch (RootNodeException e) {
            return e.getNode();
        }
    }

    static class RootNodeException extends RuntimeException {
        private final XNode node;

        public RootNodeException(XNode node) {
            super("root", null, false, false);
            this.node = node;
        }

        public XNode getNode() {
            return node;
        }
    }

    static class ParseRootHandler implements IXNodeHandler {
        @Override
        public void beginDoc(String s, String s1, String s2) {

        }

        @Override
        public void comment(String s) {

        }

        @Override
        public void simpleNode(SourceLocation loc, String tagName, Map<String, ValueWithLocation> map) {
            XNode node = XNode.make(tagName);
            node.setLocation(loc);
            node._assignAttrs(map);
            throw new RootNodeException(node);
        }

        @Override
        public void beginNode(SourceLocation loc, String tagName, Map<String, ValueWithLocation> map) {
            XNode node = XNode.make(tagName);
            node.setLocation(loc);
            node._assignAttrs(map);
            throw new RootNodeException(node);
        }

        @Override
        public void value(SourceLocation sourceLocation, Object o) {

        }

        @Override
        public void text(SourceLocation sourceLocation, String s) {

        }

        @Override
        public void endNode(String s) {

        }

        @Override
        public XNode endDoc() {
            return null;
        }
    }
}
