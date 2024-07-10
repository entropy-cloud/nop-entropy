/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xpl;

import java.util.List;
import java.util.Set;

import static io.nop.commons.util.CollectionHelper.buildImmutableList;
import static io.nop.commons.util.CollectionHelper.buildImmutableSet;

public interface XplConstants {
    String POSTFIX_XLIB = ".xlib";

    String TAG_C_IMPORT = "c:import";
    String TAG_C_UNIT = "c:unit";
    String TAG_C_SCRIPT = "c:script";

    String XPL_DEFAULT_NS = "default";
    String XPL_THIS_LIB_NS = "thisLib";
    String XPL_NS = "xpl";
    String XPL_NS_PREFIX = "xpl:";
    String XPL_CORE_NS = "c";
    String XPL_MACRO_NS = "macro";
    String XPL_INFO_NS = "info";

    String X_NS = "x";
    String XDSL_NS = "xdsl";

    String XPL_ALL_NS = "*";

    String TAG_XPL_DECORATOR = "xpl:decorator";
    String TAG_XPL_DECORATED = "xpl:decorated";

    // String ATTR_XPL_OVERRIDE = "xpl:override";

    String ATTR_XPL_IS = "xpl:is";
    String ATTR_XPL_RETURN = "xpl:return";
    String ATTR_XPL_IF = "xpl:if";
    String ATTR_XPL_INVERT = "xpl:invert";
    String ATTR_XPL_ENABLE_NS = "xpl:enableNs";
    String ATTR_XPL_DISABLE_NS = "xpl:disableNs";
    String ATTR_XPL_IGNORE_EXPR = "xpl:ignoreExpr";
    String ATTR_XPL_IGNORE_TAG = "xpl:ignoreTag"; // 将本节点对应的标签当作是普通待输出节点看待
    String ATTR_XPL_OUTPUT_MODE = "xpl:outputMode";
    String ATTR_XPL_ALLOW_UNKNOWN_TAG = "xpl:allowUnknownTag";
    String ATTR_XPL_ATTRS = "xpl:attrs";
    String ATTR_XPL_LIB = "xpl:lib";
    String ATTR_XPL_SKIP_IF = "xpl:skipIf";
    String ATTR_XPL_DUMP = "xpl:dump";

    String ATTR_XPL_SLOT = "xpl:slot";
    /**
     * 声明xpl:slot时传入的参数，格式为Map，例如
     *
     * <pre>{@code
     *      <xui:Buttons xpl:slot="buttons" xpl:slotArgs="{a:1,b:x+y,c:'xxx'}">
     *         <Button name="ss" />
     *      </xui:Buttons>
     *  }</pre>
     */
    String ATTR_XPL_SLOT_ARGS = "xpl:slotArgs";

    /**
     * 调用自定义标签时，在slot上标记的参数列表，例如
     *
     * <pre>{@code
     *     <ui:Dialog>
     *         <!-- xpl:slotScope指定传入的参数名为a和b，对应于自定义标签的实现代码中通过xpl:slotBinding传入的变量名 -->
     *         <buttons xpl:slotScope="a,b">
     *            ...
     *         </buttons>
     *     </ui:Dialog>
     * }</pre>
     */
    String ATTR_XPL_SLOT_SCOPE = "xpl:slotScope";

    List<String> XPL_ATTRS = buildImmutableList(ATTR_XPL_IS, ATTR_XPL_ENABLE_NS, ATTR_XPL_DISABLE_NS, ATTR_XPL_RETURN,
            ATTR_XPL_IF, ATTR_XPL_SLOT, ATTR_XPL_SLOT_SCOPE, ATTR_XPL_SLOT_ARGS, ATTR_XPL_INVERT, ATTR_XPL_OUTPUT_MODE,
            ATTR_XPL_IGNORE_EXPR, ATTR_XPL_IGNORE_TAG, ATTR_XPL_ALLOW_UNKNOWN_TAG, ATTR_XPL_ATTRS, ATTR_XPL_LIB,
            ATTR_XPL_SKIP_IF, ATTR_XPL_DUMP);

    String ALIAS_NAME = "alias";
    String VAR_NAME = "var";
    String ITEMS_NAME = "items";
    // String VAR_STATUS_NAME = "varStatus";
    String INDEX_NAME = "index"; // 为便于与js语法兼容，不使用java的iterator语义，直接引入index变量
    String BEGIN_NAME = "begin";
    String END_NAME = "end";
    String STEP_NAME = "step";

    String EXCEPTION_NAME = "exception";

    String WALKER_NAME = "walker";
    String ROOT_NAME = "root";

    String TEST_NAME = "test";
    String WHEN_NAME = "when";
    String OTHERWISE_NAME = "otherwise";

    String OUTPUT_MODE_NAME = "outputMode";
    String SINGLE_NODE_NAME = "singleNode";

    String BODY_NAME = "body";
    String CATCH_NAME = "catch";
    String FINALLY_NAME = "finally";

    String OBJECT_NAME = "object";
    String INFO_NAME = "info";
    String DEBUG_NAME = "debug";
    String ERROR_NAME = "error";

    String ESCAPE_NAME = "escape";
    String VALUE_NAME = "value";
    String NONE_NAME = "none";
    // String EXPR_LEADING_CHAR_NAME = "exprLeadingChar"; 统一使用xpl:exprLeadingChar

    String PREFIX_NAME = "prefix";
    String SUFFIX_NAME = "suffix";
    String SUFFIX_OVERRIDE_NAME = "suffixOverride";

    String SRC_NAME = "src";
    String TAG_NAME_NAME = "tagName";
    String CHILD_TAG_NAME = "childTag";
    String ONLY_BODY_NAME = "onlyBody";
    // String MODE_NAME = "mode"; 使用outputMode
    String TYPE_NAME = "type";
    String ENCODING_NAME = "encoding";
    String KEY_ATTR_NAME = "keyAttr";

    String NAME_NAME = "name";
    String OUT_NAME = "out";

    String XPL_NAME = "xpl";
    String TEXT_NAME = "text";
    String NODE_NAME = "node";
    String JSON_NAME = "json";

    String DUMP_NAME = "dump";

    String BEAN_NAME = "bean";

    String INVERSE_NAME = "inverse";

    String ERROR_CODE_NAME = "errorCode";
    String PARAMS_NAME = "params";
    String CAUSE_NAME = "cause";
    String ERROR_DESCRIPTION_NAME = "errorDescription";
    String BIZ_FATAL_NAME = "bizFatal";
    String ERROR_STATUS_NAME = "errorStatus";

    String LIB_NAME = "lib";
    String NAMESPACE_NAME = "namespace";

    String LANG_NAME = "lang";

    String FROM_NAME = "from";
    String AS_NAME = "as";
    String CLASS_NAME = "class";
    String ID_NAME = "id";

    Set<String> HTML_INLINE_TAG_NAMES = buildImmutableSet("img", "span", "button");
    Set<String> HTML_SHORT_TAG_NAMES = buildImmutableSet("hr", "br", "input", "img", "meta", "col", "link", "base",
            "embed");
}