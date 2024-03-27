/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xdef;

import io.nop.commons.util.CollectionHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.xlang.xdsl.XDslConstants;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class XDefKeys implements Serializable {
    public static final XDefKeys DEFAULT = new XDefKeys("xdef");

    public static XDefKeys of(XNode node) {
        String ns = node.getXmlnsForUrl(XDslConstants.XDSL_SCHEMA_XDEF);
        if (ns == null || ns.equals("xdef"))
            return DEFAULT;
        return new XDefKeys(ns);
    }

    public final String NS;

    public final String VERSION;

    public final String MODEL_NAME_PROP;

    public final String MODEL_VERSION_PROP;

    public final String PARSE_KEEP_COMMENT;
    public final String PARSE_FOR_HTML;
    public final String PARSER_CLASS;
    public final String DEFAULT_EXTENDS;
    public final String CHECK_NS;
    public final String PROP_NS;
    public final String BEAN_PACKAGE;

    public final String SUPPORT_EXTENDS;
    public final String BEAN_CLASS;
    public final String BEAN_TAG_PROP;
    public final String BEAN_BODY_PROP;
    public final String BEAN_COMMENT_PROP;
    public final String BEAN_SUB_TYPE_PROP;
    public final String BEAN_UNKNOWN_ATTRS_PROP;
    public final String BEAN_UNKNOWN_CHILDREN_PROP;
    public final String BEAN_CHILD_NAME;
    public final String BEAN_PROP;
    public final String BEAN_INTERFACES;
    public final String BEAN_BODY_TYPE;
    public final String BEAN_EXTENDS_TYPE;
    public final String BEAN_IMPLEMENTS_TYPES;

    public final String ID;

    public final String BODY_TYPE;
    public final String KEY_ATTR;
    public final String UNIQUE_ATTR;
    public final String ORDER_ATTR;
    public final String NAME;
    public final String REF;
    public final String REF_RESOLVED;
    public final String BASE;
    public final String ALLOW_MULTIPLE;
    public final String MANDATORY;
    public final String VALUE;
    public final String UNKNOWN_ATTR;
    public final String UNKNOWN_TAG;
    public final String DEFAULT_OVERRIDE;
    public final String INTERNAL;
    public final String DEPRECATED;

    public final String DEFINE;

    public final String GETTER;
    public final String SETTER;
    public final String EXPORT_EXPR;
    public final String IMPORT_EXPR;

    public final String PROP;

    public final String UNIT;
    // public final String SIMPLE;

    public final Set<String> ATTR_NAMES;
    public final Set<String> CHILD_NAMES;
    public final Set<String> ROOT_ATTR_NAMES;
    public final Set<String> ROOT_CHILD_NAMES;

    public final String POST_PARSE;

    public XDefKeys(String ns) {
        NS = ns;
        VERSION = getFullName(ns, "version");
        MODEL_NAME_PROP = getFullName(ns, "model-name-prop");
        MODEL_VERSION_PROP = getFullName(ns, "model-version-prop");
        PARSE_KEEP_COMMENT = getFullName(ns, "parse-keep-comment");
        PARSE_FOR_HTML = getFullName(ns, "parse-for-html");
        PARSER_CLASS = getFullName(ns, "parser-class");
        DEFAULT_EXTENDS = getFullName(ns, "default-extends");
        BEAN_PACKAGE = getFullName(ns, "bean-package");
        CHECK_NS = getFullName(ns, "check-ns");
        PROP_NS = getFullName(ns, "prop-ns");

        SUPPORT_EXTENDS = getFullName(ns, "support-extends");
        BEAN_CLASS = getFullName(ns, "bean-class");
        BEAN_INTERFACES = getFullName(ns, "bean-interfaces");
        BEAN_TAG_PROP = getFullName(ns, "bean-tag-prop");
        BEAN_BODY_PROP = getFullName(ns, "bean-body-prop");
        BEAN_COMMENT_PROP = getFullName(ns, "bean-comment-prop");
        BEAN_SUB_TYPE_PROP = getFullName(ns, "bean-sub-type-prop");
        BEAN_UNKNOWN_CHILDREN_PROP = getFullName(ns, "bean-unknown-children-prop");
        BEAN_UNKNOWN_ATTRS_PROP = getFullName(ns, "bean-unknown-attrs-prop");
        BEAN_CHILD_NAME = getFullName(ns, "bean-child-name");
        BEAN_PROP = getFullName(ns, "bean-prop");
        BEAN_BODY_TYPE = getFullName(ns, "bean-body-type");
        BEAN_EXTENDS_TYPE = getFullName(ns, "bean-extends-type");
        BEAN_IMPLEMENTS_TYPES = getFullName(ns, "bean-implements-types");

        BODY_TYPE = getFullName(ns, "body-type");
        KEY_ATTR = getFullName(ns, "key-attr");
        UNIQUE_ATTR = getFullName(ns, "unique-attr");
        ORDER_ATTR = getFullName(ns, "order-attr");
        NAME = getFullName(ns, "name");
        REF = getFullName(ns, "ref");
        REF_RESOLVED = getFullName(ns, "ref-resolved");
        BASE = getFullName(ns, "base");
        ALLOW_MULTIPLE = getFullName(ns, "allow-multiple");
        MANDATORY = getFullName(ns, "mandatory");
        VALUE = getFullName(ns, "value");
        UNKNOWN_ATTR = getFullName(ns, "unknown-attr");
        UNKNOWN_TAG = getFullName(ns, "unknown-tag");
        DEFAULT_OVERRIDE = getFullName(ns, "default-override");
        INTERNAL = getFullName(ns, "internal");
        DEPRECATED = getFullName(ns, "deprecated");
        DEFINE = getFullName(ns, "define");
        // SIMPLE = getFullName(ns, "simple");

        ID = getFullName(ns, "id");

        PROP = getFullName(ns, "prop");

        GETTER = getFullName(ns, "getter");
        SETTER = getFullName(ns, "setter");
        IMPORT_EXPR = getFullName(ns, "import-expr");
        EXPORT_EXPR = getFullName(ns, "export-expr");

        UNIT = getFullName(ns, "unit");

        POST_PARSE = getFullName(ns, "post-parse");

        ATTR_NAMES = CollectionHelper.buildImmutableSet(SUPPORT_EXTENDS, BEAN_CLASS, BEAN_INTERFACES, BEAN_TAG_PROP,
                BEAN_BODY_PROP, BEAN_COMMENT_PROP, BEAN_CHILD_NAME, BEAN_PROP, ID, REF_RESOLVED, BEAN_IMPLEMENTS_TYPES,
                BEAN_EXTENDS_TYPE, BEAN_SUB_TYPE_PROP, BEAN_UNKNOWN_ATTRS_PROP, BEAN_UNKNOWN_CHILDREN_PROP, BODY_TYPE,
                BEAN_BODY_TYPE, KEY_ATTR, UNIQUE_ATTR, ORDER_ATTR, NAME, REF, ALLOW_MULTIPLE, MANDATORY, VALUE,
                UNKNOWN_ATTR, DEFAULT_OVERRIDE, INTERNAL, DEPRECATED);

        List<String> rootAttrs = new ArrayList<>(Arrays.asList(VERSION, PARSE_KEEP_COMMENT, PARSE_FOR_HTML,
                PARSER_CLASS, DEFAULT_EXTENDS, BEAN_PACKAGE, CHECK_NS, PROP_NS, BASE, MODEL_NAME_PROP, MODEL_VERSION_PROP));
        rootAttrs.addAll(ATTR_NAMES);

        ROOT_ATTR_NAMES = CollectionHelper.immutableSet(rootAttrs);

        CHILD_NAMES = CollectionHelper.buildImmutableSet(DEFINE, UNIT, UNKNOWN_TAG, GETTER, SETTER, IMPORT_EXPR,
                EXPORT_EXPR, PROP);
        ROOT_CHILD_NAMES = CollectionHelper.buildImmutableSet(DEFINE, UNKNOWN_TAG, GETTER, SETTER, IMPORT_EXPR,
                EXPORT_EXPR, POST_PARSE, PROP);
    }

    private static String getFullName(String ns, String name) {
        return ns + ":" + name;
    }
}