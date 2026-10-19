package io.nop.pdf.core;

import io.nop.api.core.exceptions.ErrorCode;

/**
 * WI12：nop-pdf 生成/装配 API 错误码。i18n 登记于
 * nop-runner/nop-cli-core/src/main/resources/_vfs/i18n/zh-CN/nop-cli-errors.i18n.yaml（zh-CN 单点，en 不登记）。
 * 注：define 用 ErrorCode.define 全限定形式（check-error-param-consistency.mjs 的解析口径）。
 */
public interface PdfErrors {
    String ARG_PAGE = "page";
    String ARG_PAGE_COUNT = "pageCount";
    String ARG_TEXT_SAMPLE = "textSample";
    String ARG_FAILED_CHAR = "failedChar";
    String ARG_FFFD_COUNT = "fffdCount";

    ErrorCode ERR_PDF_PAGE_OUT_OF_RANGE = ErrorCode.define("nop.err.pdf.page-out-of-range",
            "页码越界(有效范围1..{pageCount}):page={page}",
            ARG_PAGE, ARG_PAGE_COUNT);

    ErrorCode ERR_PDF_CJK_REQUIRES_FONT = ErrorCode.define("nop.err.pdf.cjk-requires-font",
            "文本包含CJK字符且未提供字体文件:failedChar={failedChar},textSample={textSample}。请通过PdfStampOptions.setFontFile提供覆盖该字符的TTF/OTF字体文件",
            ARG_FAILED_CHAR, ARG_TEXT_SAMPLE);

    ErrorCode ERR_PDF_TEXT_NOT_ENCODABLE = ErrorCode.define("nop.err.pdf.text-not-encodable",
            "缺省字体无法编码文本中的字符:failedChar={failedChar},textSample={textSample}。请通过PdfStampOptions.setFontFile提供覆盖该字符的字体文件",
            ARG_FAILED_CHAR, ARG_TEXT_SAMPLE);

    ErrorCode ERR_PDF_IMAGE_INVALID = ErrorCode.define("nop.err.pdf.image-invalid",
            "无效的图片数据(null/空字节数组或无法解码的图片格式)");

    ErrorCode ERR_PDF_SCANNED_PAGE = ErrorCode.define("nop.err.pdf.scanned-page",
            "第{page}页无可抽取文本层(疑似扫描件)。请提供含文本层的PDF或先做OCR处理",
            ARG_PAGE);

    ErrorCode ERR_PDF_GARBLED_TEXT = ErrorCode.define("nop.err.pdf.garbled-text",
            "第{page}页乱码字符(U+FFFD)数超过阈值:fffdCount={fffdCount}。PDF可能缺少正确的ToUnicode映射",
            ARG_PAGE, ARG_FFFD_COUNT);
}
