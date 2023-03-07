/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.lang.xml.handler;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.text.CDataText;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.core.lang.xml.XNode;

import java.util.Map;

import static io.nop.core.CoreErrors.ARG_NODE;
import static io.nop.core.CoreErrors.ARG_TAG_NAME;
import static io.nop.core.CoreErrors.ERR_XML_HANDLER_BEGIN_END_MISMATCH;
import static io.nop.core.CoreErrors.ERR_XML_NOT_ALLOW_EMPTY_PROP_NAME;
import static io.nop.core.CoreErrors.ERR_XML_NOT_ALLOW_MULTIPLE_ROOT;

public class CollectXNodeHandler extends XNodeHandlerAdapter {
    /**
     * docType只能被设置一次，且在根节点前
     */
    private String docType;

    /**
     * 只有根节点前设置的第一个instruction会被保存，其他instruction会被忽略
     */
    private String instruction;

    /**
     * 节点前的comment会被汇总到一起设置到节点上。根节点后的comment会被忽略
     */
    private String comment;

    private XNode root;

    private XNode current;

    public CollectXNodeHandler() {
    }

    public void reset() {
        docType = null;
        instruction = null;
        comment = null;
        root = null;
        current = null;
    }

    @Override
    public void beginDoc(String encoding, String docType, String instruction) {
        this.docType = docType;
        this.instruction = instruction;
    }

    @Override
    public void comment(String comment) {
        if (this.comment != null) {
            this.comment += "\n" + comment;
        } else {
            this.comment = comment;
        }
    }

    @Override
    public void beginNode(SourceLocation loc, String tagName, Map<String, ValueWithLocation> attrs) {
        XNode node;
        if (root == null) {
            if (docType != null || instruction != null) {
                root = XNode.makeDocNode(tagName);
                root.setDocType(docType);
                root.setInstruction(instruction);
            } else {
                root = XNode.make(tagName);
            }
            node = root;
        } else if (current == null) {
            throw new NopException(ERR_XML_NOT_ALLOW_EMPTY_PROP_NAME).loc(loc).param(ARG_TAG_NAME, tagName);
        } else {
            node = XNode.make(tagName);
            this.current.appendChild(node);
        }
        node.setLocation(loc);
        node.setComment(comment);
        node._assignAttrs(attrs);

        this.comment = null;
        this.current = node;
    }

    @Override
    public void appendChild(XNode child) {
        if (current == null) {
            throw new NopException(ERR_XML_NOT_ALLOW_MULTIPLE_ROOT).param(ARG_NODE, child);
        } else {
            this.current.appendChild(child);
        }
    }

    @Override
    public void value(SourceLocation loc, Object value) {
        if (value == null)
            return;

        if (current == null) {
            throw new NopException(ERR_XML_HANDLER_BEGIN_END_MISMATCH).loc(loc);
        }

        if (value instanceof XNode) {
            current.normalizeContent();
            XNode node = (XNode) value;
            if (node.isDummyNode()) {
                if (node.hasContent()) {
                    current.appendContent(node.content());
                } else {
                    current.appendChildren(node.cloneChildren());
                }
            } else {
                current.appendChild(node.cloneInstance());
            }
            return;
        }

        ValueWithLocation contentValue = ValueWithLocation.of(loc, value);
        if (current.hasChild()) {
            XNode textNode = XNode.makeTextNode();
            textNode.setLocation(loc);
            textNode.content(contentValue);
            current.appendChild(textNode);
        } else {
            ValueWithLocation content = current.content();
            if (content.isNull()) {
                current.content(contentValue);
            } else {
                String s = content.asString("");
                s += value;
                if (value instanceof CDataText || content.isCDataText()) {
                    current.content(content.getLocation(), new CDataText(s));
                } else {
                    current.content(content.getLocation(), s);
                }
            }
        }
    }

    @Override
    public void endNode(String tagName) {
        if (current == null)
            throw new NopException(ERR_XML_HANDLER_BEGIN_END_MISMATCH).param(ARG_TAG_NAME, tagName);
        this.current = current.getParent();
    }

    public XNode current() {
        return current;
    }

    @Override
    public XNode endDoc() {
        return root;
    }

    public XNode root() {
        return root;
    }

    @Override
    public void simpleNode(SourceLocation loc, String tagName, Map<String, ValueWithLocation> attrs) {
        this.beginNode(loc, tagName, attrs);
        this.endNode(tagName);
    }
}