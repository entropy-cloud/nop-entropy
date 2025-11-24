package io.nop.record_mapping;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface RecordMappingErrors {
    String ARG_FIELD_NAME = "fieldName";
    String ARG_VALUE = "value";
    String ARG_DICT = "dict";

    String ARG_ALLOWED_FIELD_NAMES = "allowFieldNames";

    String ARG_MAPPING_NAME = "mappingName";

    String ARG_KEY_PROP = "keyProp";
    String ARG_KEY_VALUE = "keyValue";

    String ARG_FROM_NAME = "from";

    String ARG_SOURCE_LOC = "sourceLoc";

    String ARG_MD_FORMAT = "md:format";

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

    ErrorCode ERR_RECORD_LIST_DUPLICATE_ITEM =
            define("nop.err.record.list-duplicate-item", "字段[{fieldName}]的条目不允许重复:key={keyValue}",
                    ARG_FIELD_NAME, ARG_KEY_PROP, ARG_KEY_VALUE);

    ErrorCode ERR_RECORD_UNKNOWN_FIELD =
            define("nop.err.record.unknown-field", "未知的字段[{fieldName}]", ARG_FIELD_NAME);

    ErrorCode ERR_RECORD_UNKNOWN_FROM_FIELD =
            define("nop.err.record.unknown-from-field", "未知的字段[{fieldName}]", ARG_FIELD_NAME);

    ErrorCode ERR_RECORD_MD_LIST_ITEM_NOT_SIMPLE_VALUE =
            define("nop.err.record.md-list-item-not-simple-value", "列表条目不是简单类型");

    ErrorCode ERR_RECORD_MD_SECTION_NOT_ALLOW_SUB_SECTION =
            define("nop.err.record.md-section-not-allow-sub-section", "Markdown段落不支持子段落");

    ErrorCode ERR_RECORD_MD_SECTION_CONTENT_NOT_TABLE =
            define("nop.err.record.md-section-content-not-table", "Markdown段落内容必须是表格");
}
