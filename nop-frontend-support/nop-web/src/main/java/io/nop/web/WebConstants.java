/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.web;

import java.util.Set;

import static io.nop.commons.util.CollectionHelper.buildImmutableSet;

public interface WebConstants {
    String XDSL_SCHEMA_XVIEW = "/nop/schema/xui/xview.xdef";
    String XDSL_SCHEMA_XPAGE = "/nop/schema/xui/xpage.xdef";

    String MODEL_TYPE_XVIEW = "xview";
    String FILE_EXT_VIEW_XML = "view.xml";

    String MODEL_TYPE_JS = "js";

    String FILE_EXT_JS = "js";
    String FILE_EXT_MJS = "mjs";
    String FILE_EXT_XJS = "xjs";

    String FILE_EXT_CSS = "css";

    String FILE_EXT_XCSS = "xcss";

    String MODEL_TYPE_XPAGE = "xpage";

    String FILE_TYPE_PAGE_XML = "page.xml";

    String FILE_TYPE_PAGE_YAML = "page.yaml";

    String FILE_TYPE_PAGE_JSON5 = "page.json5";

    String FILE_TYPE_PAGE_JSON = "page.json";

    Set<String> PAGE_FILE_TYPES = buildImmutableSet(FILE_TYPE_PAGE_XML, FILE_TYPE_PAGE_YAML, FILE_TYPE_PAGE_JSON,
            FILE_TYPE_PAGE_JSON5);

    Set<String> JS_FILE_TYPES = buildImmutableSet(FILE_EXT_JS);

    Set<String> XJS_FILE_TYPES = buildImmutableSet(FILE_EXT_JS, FILE_EXT_MJS, FILE_EXT_XJS);

    String ATTR_XUI_ROLES = "xui:roles";

    String ATTR_XUI_PERMISSIONS = "xui:permissions";

    String FUNC_ROLLUP_TRANSFORM = "rollupTransform";

    String ATTR_XUI_IMPORT = "xui:import";

    String ATTR_XUI_AUTH = "xui:auth";

    String PREFIX_GENERATE = "@generate";

    String PREFIX_INLINE_BEGIN_MOCK = "//@begin-mock";

    String PREFIX_INLINE_END_MOCK = "//@end-mock";

    String PREFIX_MULTILINE_BEGIN_MOCK = "/*@begin-mock";

    String PREFIX_MULTILINE_END_MOCK = "/*@end-mock";
}
