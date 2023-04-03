/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm;

public interface OrmConstants {
    String XDSL_SCHEMA_ORM = "/nop/schema/orm/orm.xdef";
    String XDSL_SCHEMA_SQL_LIB = "/nop/schema/orm/sql-lib.xdef";
    String XDSL_SCHEMA_ORM_INTERCEPTOR = "/nop/schema/orm/orm-interceptor.xdef";

    String ORM_IMP_MODEL_PATH = "/nop/orm/imp/orm.imp.xml";

    String FILE_TYPE_ORM_XML = "orm.xml";
    String FILE_TYPE_PDM = "pdm";

    String FILE_TYPE_ORM_XLSX = "orm.xlsx";

    char COMPOSITE_PK_SEPARATOR = '~';

    String FUNC_COUNT = "count";

    String SQL_TYPE_SQL = "sql";
    String SQL_TYPE_EQL = "eql";

    String SQL_TYPE_QUERY = "query";

    String MODEL_TYPE_SQL_LIB = "sql-lib";
    String FILE_TYPE_SQL_LIB = "sql-lib.xml";

    /**
     * 实体主键固定为id。GraphQL的常见标准以及Hibernate都假定了id为主键名
     */
    String PROP_ID = "id";

    String FOR_ADD = "_forAdd";

    String PROP_NAME_nopRevType = "nopRevType";
    String PROP_NAME_nopRevBeginVer = "nopRevBeginVer";
    String PROP_NAME_nopRevEndVer = "nopRevEndVer";
    String PROP_NAME_nopRevExtChange = "nopRevExtChange";
    String PROP_NAME_nopShard = "nopShard";
    String PROP_NAME_nopTenant = "nopTenant";
    String PROP_NAME_nopFlowId = "nopFlowId";
    String PROP_NAME_nopFlowStatus = "nopFlowStatus";

    String MARKER_TENANT_ID = "tenantId";

    /**
     * 标记字段需要加密存储
     */
    String TAG_ENC = "enc";

    /**
     * 标记字段为敏感字段，打印到log文件中时需要做mask处理，不能直接输出
     */
    String TAG_MASK = "mask";

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

    int MAX_PROP_ID = 2000;

    String PERSIST_DRIVER_JDBC = "jdbc";

    String ENTITY_PERSIST_DRIVER_PREFIX = "entityPersistDriver_";
    String COLLECTION_PERSIST_DRIVER_PREFIX = "collectionPersistDriver";

    /**
     * 在to-one关联的tagSet可以通过ref-xxx来为一对多一侧的关联对象指定tagSet。在orm模型中，可以只在to-one一侧定义关联对象，在OrmModel
     * 的初始化过程中会自动生成一对多依次的关联对象，因此这里有可能需要为一对多一侧的对象指定tagSet。
     */
    String TAG_PREFIX_REF = "ref-";

    String PREFIX_PLACEHOLDER = "{prefix}";

    String PROP_NAME_fieldName = "fieldName";
    String PROP_NAME_entityName = "entityName";
    String PROP_NAME_entityId = "entityId";
    String PROP_NAME_fieldType = "fieldType";
    String PROP_NAME_value = "value";

    String PROP_NAME_booleanValue = "booleanValue";
    String PROP_NAME_byteValue = "byteValue";
    String PROP_NAME_charValue = "charValue";
    String PROP_NAME_shortValue = "shortValue";
    String PROP_NAME_intValue = "intValue";
    String PROP_NAME_longValue = "longValue";
    String PROP_NAME_floatValue = "floatValue";
    String PROP_NAME_doubleValue = "doubleValue";
    String PROP_NAME_decimalValue = "decimalValue";
    String PROP_NAME_decimalScale = "decimalScale";
    String PROP_NAME_bigIntValue = "bigIntValue";
    String PROP_NAME_dateValue = "dateValue";
    String PROP_NAME_dateTimeValue = "dateTimeValue";
    String PROP_NAME_timestampValue = "timestampValue";
    String PROP_NAME_stringValue = "stringValue";

    String DECORATOR_DUMP = "dump";
    String DECORATOR_QUERY_SPACE = "querySpace";

    String PARAM_DIALECT = "dialect";
    String PARAM_SQL_ITEM_MODEL = "sqlItemModel";

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

    String DOMAIN_BOOL_FLAG = "boolFlag";
}