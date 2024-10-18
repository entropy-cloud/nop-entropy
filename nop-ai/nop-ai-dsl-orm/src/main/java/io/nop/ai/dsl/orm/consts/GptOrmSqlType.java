/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ai.dsl.orm.consts;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.collections.CaseInsensitiveMap;
import io.nop.commons.type.StdSqlType;

import java.util.Map;

import static io.nop.ai.dsl.orm.GptOrmErrors.ARG_SQL_TYPE;
import static io.nop.ai.dsl.orm.GptOrmErrors.ERR_GPT_ORM_UNKNOWN_SQL_TYPE;

public enum GptOrmSqlType {
    BOOLEAN("BOOLEAN", StdSqlType.BOOLEAN),
    TINYINT("TINYINT", StdSqlType.TINYINT),
    SMALLINT("SMALLINT", StdSqlType.SMALLINT),
    INTEGER("INTEGER", StdSqlType.INTEGER),

    INT("INT", StdSqlType.INTEGER),

    BIGINT("BIGINT", StdSqlType.BIGINT),
    DECIMAL("DECIMAL", StdSqlType.DECIMAL),
    FLOAT("FLOAT", StdSqlType.FLOAT),
    REAL("REAL", StdSqlType.FLOAT),
    DOUBLE("DOUBLE", StdSqlType.DOUBLE),

    NUMERIC("NUMERIC", StdSqlType.DECIMAL),

    DATE("DATE", StdSqlType.DATE),

    TIME("TIME", StdSqlType.TIME),

    DATETIME("DATETIME", StdSqlType.DATETIME),

    TIMESTAMP("TIMESTAMP", StdSqlType.TIMESTAMP),
    CHAR("CHAR", StdSqlType.VARCHAR),

    VARCHAR("VARCHAR", StdSqlType.VARCHAR),

    TEXT("TEXT", StdSqlType.VARCHAR);

    private final String text;
    private final StdSqlType stdSqlType;

    GptOrmSqlType(String text, StdSqlType stdSqlType) {
        this.text = text;
        this.stdSqlType = stdSqlType;
    }

    public String toString() {
        return text;
    }

    public String getText() {
        return text;
    }

    public StdSqlType getStdSqlType() {
        return stdSqlType;
    }

    static final Map<String, GptOrmSqlType> textMap = new CaseInsensitiveMap<>();

    static {
        for (GptOrmSqlType sqlType : values()) {
            textMap.put(sqlType.getText(), sqlType);
        }
    }

    public static StdSqlType getStdSqlType(String text) {
        GptOrmSqlType sqlType = textMap.get(text);
        if (sqlType == null)
            throw new NopException(ERR_GPT_ORM_UNKNOWN_SQL_TYPE)
                    .param(ARG_SQL_TYPE, text);
        return sqlType.getStdSqlType();
    }
}
