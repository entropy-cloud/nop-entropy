package io.nop.datav.biz;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

/**
 * nop-datav-dao 层值对象校验错误码（service 层 NopDatavErrors 不可反向依赖，
 * dao 层独立声明——2026-09-12 合规审计 plan 352 closure audit 补救）。
 */
public interface NopDatavDaoErrors {
    String ARG_FIELD = "field";

    ErrorCode ERR_DATAV_DAO_META_FIELD_MISSING = define("nop.err.datav.dao.meta-field-missing",
            "面板组件元数据字段 {field} 不能为空", ARG_FIELD);
}
