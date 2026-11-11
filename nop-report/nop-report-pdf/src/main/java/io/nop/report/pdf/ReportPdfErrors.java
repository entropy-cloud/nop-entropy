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
    String ARG_FAILED_CHAR = "failedChar";
    String ARG_PRINT_TITLE_RANGE = "printTitleRange";
    String ARG_SCOPE = "scope";
    String ARG_BLOCK_TYPE = "blockType";
    String ARG_ASSET_REF = "assetRef";

    ErrorCode ERR_PDF_FONT_MISSING_GLYPH = define("nop.err.report.pdf.font-missing-glyph",
            "字体[{fontName}]无法编码文本[{textSample}]中的字符[{failedChar}]，且没有可用的回退字体。请在VFS的/fonts/目录、nop.report.pdf.font-dirs配置的目录或系统字体目录中提供覆盖该字符的CJK字体文件",
            ARG_FONT_NAME, ARG_TEXT_SAMPLE, ARG_FAILED_CHAR);

    ErrorCode ERR_PDF_FALLBACK_FONT_NOT_FOUND = define("nop.err.report.pdf.fallback-font-not-found",
            "未找到可用的CJK回退字体，无法渲染包含中文字符的文本[{textSample}]。请在VFS的/fonts/default.ttf、nop.report.pdf.font-dirs配置的目录或系统字体目录中提供CJK字体文件",
            ARG_TEXT_SAMPLE);

    ErrorCode ERR_PDF_UNSUPPORTED_PRINT_TITLES = define("nop.err.report.pdf.unsupported-print-titles",
            "PDF导出仅支持从首行/首列开始的前缀Print_Titles声明(如1:2或A:B):printTitleRange={printTitleRange}",
            ARG_PRINT_TITLE_RANGE);

    ErrorCode ERR_PDF_INVALID_PAGE_NUMBER_SCOPE = define("nop.err.report.pdf.invalid-page-number-scope",
            "无效的页码作用域配置(仅支持global/sheet):scope={scope}", ARG_SCOPE);

    // ---- OfficeDocModel -> PDF renderer (io.nop.report.pdf.doc) ----

    ErrorCode ERR_PDF_DOC_UNKNOWN_BLOCK = define("nop.err.report.pdf.doc.unknown-block",
            "Unknown OfficeDocModel block type [{blockType}]; the PDF renderer cannot dispatch it",
            ARG_BLOCK_TYPE);

    ErrorCode ERR_PDF_DOC_UNKNOWN_RUN = define("nop.err.report.pdf.doc.unknown-run",
            "Unknown OfficeDocModel inline run type [{blockType}]; the PDF renderer cannot dispatch it",
            ARG_BLOCK_TYPE);

    ErrorCode ERR_PDF_DOC_IMAGE_ASSET_NOT_FOUND = define("nop.err.report.pdf.doc.image-asset-not-found",
            "Image block references asset [{assetRef}] which is not present in doc assets",
            ARG_ASSET_REF);

    ErrorCode ERR_PDF_DOC_INVALID_PAGE_GEOMETRY = define("nop.err.report.pdf.doc.invalid-page-geometry",
            "Section page geometry is invalid (non-positive content area) for section [{blockType}]",
            ARG_BLOCK_TYPE);

    ErrorCode ERR_PDF_DOC_RENDER_MODEL_REQUIRED = define("nop.err.report.pdf.doc.render-model-required",
            "renderPdf requires a non-null, already expanded OfficeDocModel; template input must be "
                    + "expanded through the docx outlet model stage (WordDocTemplates.expandToModel) first");
}
