/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm;

import io.nop.orm.model.OrmModelConstants;

public interface OrmConstants {
    String XDSL_SCHEMA_ORM = "/nop/schema/orm/orm.xdef";
    String XDSL_SCHEMA_SQL_LIB = "/nop/schema/orm/sql-lib.xdef";
    String XDSL_SCHEMA_ORM_INTERCEPTOR = "/nop/schema/orm/orm-interceptor.xdef";

    String ORM_IMP_MODEL_PATH = "/nop/orm/imp/orm.imp.xml";

    String FILE_TYPE_ORM_XML = "orm.xml";
    String FILE_TYPE_PDM = "pdm";

    String FILE_TYPE_ORM_XLSX = "orm.xlsx";

    String BEAN_ORM_ENTITY_FILE_STORE = "nopOrmEntityFileStore";

    char COMPOSITE_PK_SEPARATOR = '~';


    String SQL_TYPE_SQL = "sql";
    String SQL_TYPE_EQL = "eql";

    String SQL_TYPE_QUERY = "query";

    String MODEL_TYPE_SQL_LIB = "sql-lib";
    String FILE_TYPE_SQL_LIB = "sql-lib.xml";

    /**
     * 实体主键固定为id。GraphQL的常见标准以及Hibernate都假定了id为主键名
     */
    String PROP_ID = "id";

    String ID_NULL = "__null";

    /**
     * 标记字段需要加密存储
     */
    String TAG_ENC = "enc";

    /**
     * 存储源码、模型定义等的文本字段
     */
    String TAG_CONTENT = "content";

    /**
     * 标记字段为敏感字段，打印到log文件中时需要做mask处理，不能直接输出
     */
    String TAG_MASKED = OrmModelConstants.TAG_MASKED;

    /**
     * 标记字段允许使用ISequenceGenerator机制来生成
     */
    String TAG_SEQ = "seq";

    /**
     * 标记字段的值会被赋予随机值，在autotest录制中需要被替换为@var:varName形式
     */
    String TAG_VAR = "var";

    /**
     * 显示属性
     */
    String TAG_DISP = "disp";

    String TAG_JSON = "json";

    String TAG_MANY_TO_MANY = "many-to-many";

    String TAG_ONE_TO_ONE = "one-to-one";

    /**
     * 新建实体
     */
    byte REV_TYPE_SAVE = 1;

    /**
     * 实体属性被修改
     */
    byte REV_TYPE_UPDATE = 2;

    /**
     * 实体被删除
     */
    byte REV_TYPE_DELETE = 3;

    long NOP_VER_MAX_VALUE = Long.MAX_VALUE;

    String PERSIST_DRIVER_JDBC = "jdbc";

    String ENTITY_PERSIST_DRIVER_PREFIX = "entityPersistDriver_";
    String COLLECTION_PERSIST_DRIVER_PREFIX = "collectionPersistDriver";


    String PARAM_DIALECT = "dialect";
    String PARAM_SQL_ITEM_MODEL = "sqlItemModel";

    String PARAM_SQL_LIB_MODEL = "sqlLibModel";

    String VAR_ENTITY = "entity";

    String VAR_EXCEPTION = "exception";

    String VAR_VALUE = "value";

    String VAR_PROP_META = "propMeta";

    String VALUE_PREFIX_PROP_REF = "@prop-ref:";

    String SQL_DICT_PREFIX = "sql/";

    String SQL_DICT_POSTFIX = "_dict";

    String EXT_PROP_DICT_STATIC = "dict:static";
    String EXT_PROP_DICT_VALUE_TYPE = "dict:valueType";
    String EXT_PROP_DICT_NORMALIZED = "dict:normalized";

    String SQL_ARG_LOCALE = "locale";

    String USER_NAME_SYS = "sys";

    String NAMESPACE_DAO = "dao";

    String PROP_NAME_fieldName = "fieldName"; //NOSONAR

    String PROP_NAME_fieldType = "fieldType"; //NOSONAR

    String PROP_NAME_decimalScale = "decimalScale"; //NOSONAR

    String PROP_NAME_decimalValue = "decimalValue"; //NOSONAR

    String PROP_NAME_stringValue = "stringValue"; //NOSONAR

    String PROP_NAME_dateValue = "dateValue"; //NOSONAR

    String PROP_NAME_timestampValue = "timestampValue"; //NOSONAR

}