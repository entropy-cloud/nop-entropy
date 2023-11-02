/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dao;

public interface DaoConstants {
    String XDSL_SCHEMA_DIALECT = "/nop/schema/orm/dialect.xdef";

    String DEFAULT_QUERY_SPACE = "default";

    String DEFAULT_TXN_GROUP = "default";

    String DEFAULT_TENANT_ID = "0";

    String SEQ_TXN_ID = "seq_txn_id";

    String SQL_TYPE_EQL = "eql";
    String SQL_TYPE_SQL = "sql";

    String DIALECT_MYSQL = "mysql";
    String DIALECT_ORACLE = "oracle";
    String DIALECT_POSTGRESQL = "postgresql";
    String DIALECT_MSSQL = "mssql";
    String DIALECT_H2 = "h2";
    String DIALECT_MARIADB = "mariadb";

    /**
     * 标记行的变更类型，A/D/U表示 Add, Delete, Update
     */
    String PROP_CHANGE_TYPE = "_chgType";

    String CHANGE_TYPE_ADD = "A";
    String CHANGE_TYPE_UPDATE = "U";
    String CHANGE_TYPE_DELETE = "D";

    String NULL_FIELD = "null";

    int ACTIVE_STATUS_ACTIVE = 1;
    int ACTIVE_STATUS_INACTIVE = 0;

    Byte YES_VALUE = 1;
    Byte NO_VALUE = 0;
}
