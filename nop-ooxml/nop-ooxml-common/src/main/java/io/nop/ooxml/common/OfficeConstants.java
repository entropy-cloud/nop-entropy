/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.common;

public interface OfficeConstants {
    String NS_LINK = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink";

    String NS_IMAGE = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/image";

    String NS_STYLES = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles";

    String NS_FONT_TABLE = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/fontTable";

    String NS_HEADER = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/header";
    String NS_FOOTER = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/footer";

    String NS_RELATIONSHIPS = "http://schemas.openxmlformats.org/package/2006/relationships";

    String EXPR_PREFIX = "expr:";

    String NS_EXT_PREFIX = "ext:";

    /**
     * 对应于officePackage对象
     */
    String VAR_OFC_PKG = "ofcPkg";

    String VAR_FILE_PASSWORD = "filePassword";

    String VAR_XPL_GEN_CONFIG = "xplGenConfig";

    String VAR_HEADERS = "headers";
}