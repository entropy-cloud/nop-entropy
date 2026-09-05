/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.pdf;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface ReportPdfErrors {
    String ARG_FONT_NAME = "fontName";
    String ARG_TEXT_SAMPLE = "textSample";

    ErrorCode ERR_PDF_FONT_MISSING_GLYPH = define("nop.err.report.pdf.font-missing-glyph",
            "字体[{fontName}]无法编码文本[{textSample}]中的部分字符，且没有可用的回退字体。请在VFS的/fonts/目录、nop.report.pdf.font-dirs配置的目录或系统字体目录中提供CJK字体文件",
            ARG_FONT_NAME, ARG_TEXT_SAMPLE);

    ErrorCode ERR_PDF_FALLBACK_FONT_NOT_FOUND = define("nop.err.report.pdf.fallback-font-not-found",
            "未找到可用的CJK回退字体，无法渲染包含中文字符的文本[{textSample}]。请在VFS的/fonts/default.ttf、nop.report.pdf.font-dirs配置的目录或系统字体目录中提供CJK字体文件",
            ARG_TEXT_SAMPLE);
}
