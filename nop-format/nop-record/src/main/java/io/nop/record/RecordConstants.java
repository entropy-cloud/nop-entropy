package io.nop.record;

public interface RecordConstants {
    String XDEF_PACKET_CODEC = "/nop/schema/record/packet-codec.xdef";
    String XDEF_RECORD_FILE = "/nop/schema/record/record-file.xdef";

    String XDEF_RECORD_OBJECT = "/nop/schema/record/record-object.xdef";

    String XDEF_RECORD_DEFINITIONS = "/nop/schema/record/record-definitions.xdef";


    String HEADER_NAME = "header";

    String TRAILER_NAME = "trailer";

    String BODY_NAME = "body";

    String PAGE_FOOTER_NAME = "pageFooter";

    String PAGE_HEADER_NAME = "pageHeader";

    String VAR_NEXT_RECORD = "nextRecord";
    String VAR_LAST_RECORD = "lastRecord";

    String VAR_AGG_STATE = "aggState";

    String RECORD_FILE_XML_POSTFIX = ".record-file.xml";

    String RECORD_XML_POSTFIX = ".record.xml";

    String VAR_TOTAL_COUNT = "totalCount";

    String TYPE_DEFAULT = "default";

    String VAR_FILE_NAME = "fileName";

    String VAR_FILE_NAME_PREFIX = "fileNamePrefix";

    String VAR_FILE_NAME_SUFFIX = "fileNameSuffix";

    int DEFAULT_MAX_COLLECTION_SIZE = 1000000;

    /**
     * 数据驱动（lengthExpr）字段/对象长度上界：超出即在分配前拒绝。与 DynLV 变长字段 64MB 上界同族
     */
    int DEFAULT_MAX_FIELD_LENGTH = 64 * 1024 * 1024;

    /**
     * rawVarName 诊断变量最大字符数：只保留区域前缀，超出部分以截断标记代替
     */
    int MAX_RAW_DATA_DIAG_CHARS = 4096;

    String RAW_DATA_TRUNCATED_MARK = "...(truncated)";
}
