package io.nop.converter;

import io.nop.api.core.exceptions.ErrorCode;

public interface DocConvertErrors {
    String ARG_FILE_TYPE = "fileType";
    String ARG_FROM_FILE_TYPE = "fromFileType";
    String ARG_TO_FILE_TYPE = "toFileType";

    ErrorCode ERR_NO_DOCUMENT_OBJECT_BUILDER = ErrorCode.define("nop.err.convert.no-document-object-builder-for-file-type",
            "没有为文件类型定义文档对象构建器：{fileType}", ARG_FILE_TYPE);

    ErrorCode ERR_NO_CONVERTER_FROM_TYPE_TO_TYPE = ErrorCode.define("nop.err.convert.no-converter-from-type-to-type",
            "没有从类型 {fromFileType} 转换到类型 {toFileType} 的转换器", ARG_FROM_FILE_TYPE, ARG_TO_FILE_TYPE);
}
