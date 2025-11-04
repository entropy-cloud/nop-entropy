/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xdsl;

import io.nop.commons.util.CollectionHelper;
import io.nop.core.lang.xml.XNode;

import java.io.Serializable;
import java.util.Set;

public final class XDslKeys implements Serializable {

    private static final long serialVersionUID = 2459153858498072901L;

    public static final XDslKeys DEFAULT = new XDslKeys("x");

    public static final XDslKeys XPL = new XDslKeys("xpl");

    public final String NS;
    public final String X_NS_PREFIX;

    public final String SCHEMA;

    public final String ID;

    public final String VALIDATED;

    // public final String DYNAMIC;
    public final String DUMP;

    public final String EXTENDS;
    public final String CONFIG;
    public final String GEN_EXTENDS;
    public final String POST_EXTENDS;
    public final String PRE_PARSE;
    public final String POST_PARSE;

    public final String PROTOTYPE;
    public final String PROTOTYPE_SUPER;
    public final String PROTOTYPE_OVERRIDE;
    public final String DEFAULT_OVERRIDE;

    public final String ABSTRACT;
    public final String FINAL;
    public final String OVERRIDE;
    public final String BEFORE;
    public final String AFTER;
    public final String VIRTUAL;
    public final String INHERIT;
    public final String ORDER;
    public final String ARGS;

    public final String SUPER;

    public final String KEY_ATTR;

    public final Set<String> ATTR_NAMES;
    public final Set<String> CHILD_NAMES;

    public XDslKeys(String ns) {
        this.NS = ns;
        this.X_NS_PREFIX = ns + ':';
        this.SCHEMA = getFullName(ns, "schema");
        this.ID = getFullName(ns, "id");
        this.KEY_ATTR = getFullName(ns, "key-attr");
        // this.DYNAMIC = getFullName(ns, "dynamic");
        this.EXTENDS = getFullName(ns, "extends");
        this.CONFIG = getFullName(ns, "config");
        this.GEN_EXTENDS = getFullName(ns, "gen-extends");
        this.POST_EXTENDS = getFullName(ns, "post-extends");
        this.PRE_PARSE = getFullName(ns, "pre-parse");
        this.POST_PARSE = getFullName(ns, "post-parse");
        this.PROTOTYPE = getFullName(ns, "prototype");
        this.PROTOTYPE_SUPER = getFullName(ns, "prototype-super");
        this.PROTOTYPE_OVERRIDE = getFullName(ns, "prototype-override");
        this.DEFAULT_OVERRIDE = getFullName(ns,"default-override");

        this.ABSTRACT = getFullName(ns, "abstract");
        this.FINAL = getFullName(ns, "final");
        this.OVERRIDE = getFullName(ns, "override");
        this.BEFORE = getFullName(ns, "before");
        this.AFTER = getFullName(ns, "after");
        this.VIRTUAL = getFullName(ns, "virtual");
        this.INHERIT = getFullName(ns, "inherit");
        this.ORDER = getFullName(ns, "order");
        this.DUMP = getFullName(ns, "dump");
        this.SUPER = getFullName(ns, "super");
        this.VALIDATED = getFullName(ns, "validated");
        this.ARGS = getFullName(ns, "args");

        this.ATTR_NAMES = CollectionHelper.buildImmutableSet(ID, EXTENDS, PROTOTYPE, PROTOTYPE_OVERRIDE, ABSTRACT,
                SCHEMA, KEY_ATTR, VALIDATED, VIRTUAL, INHERIT, FINAL, OVERRIDE, DEFAULT_OVERRIDE, BEFORE, AFTER, ORDER, DUMP);

        this.CHILD_NAMES = CollectionHelper.buildImmutableSet(ARGS, GEN_EXTENDS, CONFIG, POST_EXTENDS,
                PRE_PARSE, POST_PARSE,
                SUPER, PROTOTYPE_SUPER);
    }

    public String getOverride(boolean forPrototype) {
        return forPrototype ? PROTOTYPE_OVERRIDE : OVERRIDE;
    }

    public String getSuper(boolean forPrototype) {
        return forPrototype ? PROTOTYPE_SUPER : SUPER;
    }

    private static String getFullName(String ns, String name) {
        return ns + ":" + name;
    }

    public static XDslKeys of(XNode node) {
        String ns = node.getXmlnsForUrl(XDslConstants.XDSL_SCHEMA_XDSL);
        return of(ns);
    }

    public static XDslKeys of(String ns) {
        if (ns == null || ns.equals(DEFAULT.NS))
            return DEFAULT;
        return new XDslKeys(ns);
    }
}
