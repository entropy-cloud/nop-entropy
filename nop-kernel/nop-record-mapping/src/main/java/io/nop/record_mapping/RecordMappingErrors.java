package io.nop.record_mapping;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface RecordMappingErrors {
    String ARG_FIELD_NAME = "fieldName";
    String ARG_VALUE = "value";
    String ARG_DICT = "dict";

    String ARG_MAPPING_NAME = "mappingName";

    ErrorCode ERR_RECORD_FIELD_VALUE_NOT_IN_DICT = define("nop.err.record.field-value-not-in-dict",
            "字段[{fieldName}]的值不在字典中:{value}", ARG_FIELD_NAME, ARG_VALUE);

    ErrorCode ERR_RECORD_FIELD_MAPPING_NOT_FOUND = define("nop.err.record.field-mapping-not-found",
            "未找到字段映射:{mappingName}", ARG_MAPPING_NAME, ARG_FIELD_NAME);

    ErrorCode ERR_RECORD_MAPPING_NOT_FOUND = define("nop.err.record.mapping-not-found",
            "未找到映射:{mappingName}", ARG_MAPPING_NAME);

    ErrorCode ERR_RECORD_FIELD_NOT_COLLECTION_TYPE = define("nop.err.record.field-not-collection-type",
            "字段[{fieldName}]的值不是合法的集合类型", ARG_FIELD_NAME);

    ErrorCode ERR_RECORD_FIELD_IS_MANDATORY = define("nop.err.record.field-is-mandatory",
            "字段[{fieldName}]的值不允许为空", ARG_FIELD_NAME);
}
