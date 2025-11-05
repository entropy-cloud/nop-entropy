/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.docx;

import io.nop.ooxml.common.OfficeConstants;
import io.nop.ooxml.common.model.PackagePartName;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static io.nop.ooxml.common.model.PackagingURIHelper.createPartName;

public interface DocxConstants extends OfficeConstants {

    String LIB_DOCX_GEN = "/nop/ooxml/xlib/docx-gen.xlib";

    String TAG_LINK_EXPR = "wml:LinkExpr";
    String TAG_PARAGRAPH_EXPR = "wml:ParagraphExpr";

    String TABLE_WML_CONFIG = "WmlConfig";

    String PATH_WORD_RELS = "word/_rels/document.xml.rels";
    String PATH_WORD_DOCUMENT = "word/document.xml";

    String PATH_PREFIX_HEADER = "word/header";
    String PATH_PREFIX_FOOTER = "word/footer";

    String SRC_NAME = "src";

    String EXPR_IMG_ID = "${_imgId}";
    String EXPR_IMG_NAME = "${_imgName}";

    String TAG_WML_RENDER = "wml:Render";
    String TAG_DRAWING = "docx-gen:Drawing";

    String VAR_WORD_MODEL = "wordModel";
    String VAR_WORD_TEMPLATE = "wordTemplate";

    String VAR_RELS_FILE = "relsFile";

    String VAR_ZIP_OUT = "zipOut";

    Set<String> WML_NS_LIST = new HashSet<>(Arrays.asList("w", "mc"));

    String HEADER_XPL_GEN_CONFIG = "XplGenConfig";

    PackagePartName PART_NAME_STYLES = createPartName("/word/styles.xml");

    PackagePartName PART_NAME_COMMENTS = createPartName("/word/comments.xml");
    PackagePartName PART_NAME_COMMENTS_EXTENDED = createPartName("/word/commentsExtended.xml");
    PackagePartName PART_NAME_COMMENTS_EXTENSIBLE = createPartName("/word/commentsExtensible.xml");
    PackagePartName PART_NAME_COMMENTS_IDS = createPartName("/word/commentsIds.xml");
}
