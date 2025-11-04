/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.json.handler;

import io.nop.api.core.ApiConstants;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.CoreConstants;
import io.nop.core.lang.xml.XNode;

import java.util.List;

import static io.nop.core.CoreErrors.ARG_ATTR_NAME;
import static io.nop.core.CoreErrors.ARG_NODE;
import static io.nop.core.CoreErrors.ERR_XML_DUPLICATE_ATTR_NAME;
import static io.nop.core.CoreErrors.ERR_XML_NOT_ALLOW_BOTH_CONTENT_AND_CHILD;

public class BuildXNodeJsonHandler extends BuildObjectJsonHandler {

    @Override
    protected Object newObject(SourceLocation loc) {
        XNode node = XNode.make(CoreConstants.DUMMY_TAG_NAME);
        node.setLocation(loc);
        return node;
    }

    @Override
    protected void addEntry(String key, SourceLocation loc, Object value) {
        XNode node = (XNode) getTopObject();
        if (ApiConstants.TREE_BEAN_PROP_TYPE.equals(value)) {
            node.setTagName((String) value);
        } else if (value instanceof XNode) {
            node.appendChild((XNode) value);
        } else if (value instanceof List) { //NOPMD - suppressed EmptyControlStatement
            // ignore
        } else if (ApiConstants.TREE_BEAN_PROP_BODY.equals(key)) {
            if (node.hasContent()) {
                throw new NopException(ERR_XML_NOT_ALLOW_BOTH_CONTENT_AND_CHILD).param(ARG_NODE, node);
            }
        } else {
            if (node.hasAttr(key))
                throw new NopException(ERR_XML_DUPLICATE_ATTR_NAME).param(ARG_NODE, node).param(ARG_ATTR_NAME, key);
            node.setAttr(loc, key, value);
        }
    }
}