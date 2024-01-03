/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.model;

public interface OrmModelConstants {
    String XDSL_SCHEMA_ORM = "/nop/schema/orm/orm.xdef";

    String DEFAULT_QUERY_SPACE = "default";

    String PROP_ID = "id";

    String PROP_SID = "sid";

    String ENTITY_SET_CLASS_NAME = "io.nop.orm.IOrmEntitySet";

    String COMPOSITE_PK_CLASS_NAME = "io.nop.orm.support.OrmCompositePk";

    String DOMAIN_BOOL_FLAG = "boolFlag";

    /**
     * 标记专用于显示的字段
     */
    String TAG_DISP = "disp";

    /**
     * 标记需要掩码的字段
     */
    String TAG_MASKED = "masked";


    /**
     * 在to-one关联的tagSet可以通过ref-xxx来为一对多一侧的关联对象指定tagSet。在orm模型中，可以只在to-one一侧定义关联对象，在OrmModel
     * 的初始化过程中会自动生成一对多依次的关联对象，因此这里有可能需要为一对多一侧的对象指定tagSet。
     */
    String TAG_PREFIX_REF = "ref-";

    String PROP_NAME_nopRevType = "nopRevType";
    String PROP_NAME_nopRevBeginVer = "nopRevBeginVer";
    String PROP_NAME_nopRevEndVer = "nopRevEndVer";
    String PROP_NAME_nopRevExtChange = "nopRevExtChange";
    String PROP_NAME_nopShard = "nopShard";
    String PROP_NAME_nopTenantId = "nopTenantId";
    String PROP_NAME_nopFlowId = "nopFlowId";
    String PROP_NAME_nopFlowStatus = "nopFlowStatus";

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

    int MAX_PROP_ID = 2000;
    String EXT_PROP_MIN_PROP_ID = "ext:minPropId";

    String EXT_BASE_PACKAGE_NAME = "ext:basePackageName";
    String EXT_MAVEN_GROUP_ID = "ext:mavenGroupId";

    String EXT_APP_NAME = "ext:appName";

    String EXT_MAVEN_ARTIFACT_ID = "ext:mavenArtifactId";

    String EXT_UI_CONTROL = "ui:control";
    String EXT_UI_SHOW = "ui:show";

    String VAR_ENTITY = "entity";

    String VAR_VALUE = "value";
}
