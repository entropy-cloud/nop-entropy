package io.nop.metadata.dao.entity._gen;

import io.nop.orm.model.IEntityModel;
import io.nop.orm.support.DynamicOrmEntity;
import io.nop.orm.support.OrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.orm.IOrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code

import io.nop.api.core.convert.ConvertHelper;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;

import io.nop.metadata.dao.entity.NopMetaEntityField;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  实体字段: nop_meta_entity_field
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopMetaEntityField extends DynamicOrmEntity{
    
    /* 字段ID: ENTITY_FIELD_ID VARCHAR */
    public static final String PROP_NAME_entityFieldId = "entityFieldId";
    public static final int PROP_ID_entityFieldId = 1;
    
    /* 实体ID: META_ENTITY_ID VARCHAR */
    public static final String PROP_NAME_metaEntityId = "metaEntityId";
    public static final int PROP_ID_metaEntityId = 2;
    
    /* 是否Delta: IS_DELTA TINYINT */
    public static final String PROP_NAME_isDelta = "isDelta";
    public static final int PROP_ID_isDelta = 3;
    
    /* 属性名: FIELD_NAME VARCHAR */
    public static final String PROP_NAME_fieldName = "fieldName";
    public static final int PROP_ID_fieldName = 4;
    
    /* 列名: COLUMN_CODE VARCHAR */
    public static final String PROP_NAME_columnCode = "columnCode";
    public static final int PROP_ID_columnCode = 5;
    
    /* 属性序号: PROP_ID INTEGER */
    public static final String PROP_NAME_propId = "propId";
    public static final int PROP_ID_propId = 6;
    
    /* 数据类型: STD_DATA_TYPE VARCHAR */
    public static final String PROP_NAME_stdDataType = "stdDataType";
    public static final int PROP_ID_stdDataType = 7;
    
    /* SQL类型: STD_SQL_TYPE VARCHAR */
    public static final String PROP_NAME_stdSqlType = "stdSqlType";
    public static final int PROP_ID_stdSqlType = 8;
    
    /* 精度: PRECISION INTEGER */
    public static final String PROP_NAME_precision = "precision";
    public static final int PROP_ID_precision = 9;
    
    /* 标度: SCALE INTEGER */
    public static final String PROP_NAME_scale = "scale";
    public static final int PROP_ID_scale = 10;
    
    /* 必填: MANDATORY TINYINT */
    public static final String PROP_NAME_mandatory = "mandatory";
    public static final int PROP_ID_mandatory = 11;
    
    /* 主键: PRIMARY TINYINT */
    public static final String PROP_NAME_primaryField = "primaryField";
    public static final int PROP_ID_primaryField = 12;
    
    /* 懒加载: LAZY TINYINT */
    public static final String PROP_NAME_lazy = "lazy";
    public static final int PROP_ID_lazy = 13;
    
    /* 可插入: INSERTABLE TINYINT */
    public static final String PROP_NAME_insertable = "insertable";
    public static final int PROP_ID_insertable = 14;
    
    /* 可更新: UPDATABLE TINYINT */
    public static final String PROP_NAME_updatable = "updatable";
    public static final int PROP_ID_updatable = 15;
    
    /* 域: DOMAIN VARCHAR */
    public static final String PROP_NAME_domain = "domain";
    public static final int PROP_ID_domain = 16;
    
    /* 标准域: STD_DOMAIN VARCHAR */
    public static final String PROP_NAME_stdDomain = "stdDomain";
    public static final int PROP_ID_stdDomain = 17;
    
    /* 固定值: FIXED_VALUE VARCHAR */
    public static final String PROP_NAME_fixedValue = "fixedValue";
    public static final int PROP_ID_fixedValue = 18;
    
    /* 默认值: DEFAULT_VALUE VARCHAR */
    public static final String PROP_NAME_defaultValue = "defaultValue";
    public static final int PROP_ID_defaultValue = 19;
    
    /* 语义类型: SEMANTIC_TYPE VARCHAR */
    public static final String PROP_NAME_semanticType = "semanticType";
    public static final int PROP_ID_semanticType = 20;
    
    /* 标签集: TAG_SET VARCHAR */
    public static final String PROP_NAME_tagSet = "tagSet";
    public static final int PROP_ID_tagSet = 21;
    
    /* 显示名: DISPLAY_NAME VARCHAR */
    public static final String PROP_NAME_displayName = "displayName";
    public static final int PROP_ID_displayName = 22;
    
    /* 注释: COMMENT VARCHAR */
    public static final String PROP_NAME_comment = "comment";
    public static final int PROP_ID_comment = 23;
    
    /* 原生SQL类型: NATIVE_SQL_TYPE VARCHAR */
    public static final String PROP_NAME_nativeSqlType = "nativeSqlType";
    public static final int PROP_ID_nativeSqlType = 24;
    
    /* 业务域ID: BUSINESS_DOMAIN_ID VARCHAR */
    public static final String PROP_NAME_businessDomainId = "businessDomainId";
    public static final int PROP_ID_businessDomainId = 25;
    
    /* 扩展配置: EXT_CONFIG VARCHAR */
    public static final String PROP_NAME_extConfig = "extConfig";
    public static final int PROP_ID_extConfig = 26;
    
    /* 数据版本: VERSION BIGINT */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 27;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 28;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 29;
    
    /* 修改人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 30;
    
    /* 修改时间: UPDATE_TIME TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 31;
    
    /* 备注: REMARK VARCHAR */
    public static final String PROP_NAME_remark = "remark";
    public static final int PROP_ID_remark = 32;
    

    private static int _PROP_ID_BOUND = 33;

    
    /* relation: 元数据实体 */
    public static final String PROP_NAME_metaEntity = "metaEntity";
    
    /* component:  */
    public static final String PROP_NAME_extConfigComponent = "extConfigComponent";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_entityFieldId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_entityFieldId};

    private static final String[] PROP_ID_TO_NAME = new String[33];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_entityFieldId] = PROP_NAME_entityFieldId;
          PROP_NAME_TO_ID.put(PROP_NAME_entityFieldId, PROP_ID_entityFieldId);
      
          PROP_ID_TO_NAME[PROP_ID_metaEntityId] = PROP_NAME_metaEntityId;
          PROP_NAME_TO_ID.put(PROP_NAME_metaEntityId, PROP_ID_metaEntityId);
      
          PROP_ID_TO_NAME[PROP_ID_isDelta] = PROP_NAME_isDelta;
          PROP_NAME_TO_ID.put(PROP_NAME_isDelta, PROP_ID_isDelta);
      
          PROP_ID_TO_NAME[PROP_ID_fieldName] = PROP_NAME_fieldName;
          PROP_NAME_TO_ID.put(PROP_NAME_fieldName, PROP_ID_fieldName);
      
          PROP_ID_TO_NAME[PROP_ID_columnCode] = PROP_NAME_columnCode;
          PROP_NAME_TO_ID.put(PROP_NAME_columnCode, PROP_ID_columnCode);
      
          PROP_ID_TO_NAME[PROP_ID_propId] = PROP_NAME_propId;
          PROP_NAME_TO_ID.put(PROP_NAME_propId, PROP_ID_propId);
      
          PROP_ID_TO_NAME[PROP_ID_stdDataType] = PROP_NAME_stdDataType;
          PROP_NAME_TO_ID.put(PROP_NAME_stdDataType, PROP_ID_stdDataType);
      
          PROP_ID_TO_NAME[PROP_ID_stdSqlType] = PROP_NAME_stdSqlType;
          PROP_NAME_TO_ID.put(PROP_NAME_stdSqlType, PROP_ID_stdSqlType);
      
          PROP_ID_TO_NAME[PROP_ID_precision] = PROP_NAME_precision;
          PROP_NAME_TO_ID.put(PROP_NAME_precision, PROP_ID_precision);
      
          PROP_ID_TO_NAME[PROP_ID_scale] = PROP_NAME_scale;
          PROP_NAME_TO_ID.put(PROP_NAME_scale, PROP_ID_scale);
      
          PROP_ID_TO_NAME[PROP_ID_mandatory] = PROP_NAME_mandatory;
          PROP_NAME_TO_ID.put(PROP_NAME_mandatory, PROP_ID_mandatory);
      
          PROP_ID_TO_NAME[PROP_ID_primaryField] = PROP_NAME_primaryField;
          PROP_NAME_TO_ID.put(PROP_NAME_primaryField, PROP_ID_primaryField);
      
          PROP_ID_TO_NAME[PROP_ID_lazy] = PROP_NAME_lazy;
          PROP_NAME_TO_ID.put(PROP_NAME_lazy, PROP_ID_lazy);
      
          PROP_ID_TO_NAME[PROP_ID_insertable] = PROP_NAME_insertable;
          PROP_NAME_TO_ID.put(PROP_NAME_insertable, PROP_ID_insertable);
      
          PROP_ID_TO_NAME[PROP_ID_updatable] = PROP_NAME_updatable;
          PROP_NAME_TO_ID.put(PROP_NAME_updatable, PROP_ID_updatable);
      
          PROP_ID_TO_NAME[PROP_ID_domain] = PROP_NAME_domain;
          PROP_NAME_TO_ID.put(PROP_NAME_domain, PROP_ID_domain);
      
          PROP_ID_TO_NAME[PROP_ID_stdDomain] = PROP_NAME_stdDomain;
          PROP_NAME_TO_ID.put(PROP_NAME_stdDomain, PROP_ID_stdDomain);
      
          PROP_ID_TO_NAME[PROP_ID_fixedValue] = PROP_NAME_fixedValue;
          PROP_NAME_TO_ID.put(PROP_NAME_fixedValue, PROP_ID_fixedValue);
      
          PROP_ID_TO_NAME[PROP_ID_defaultValue] = PROP_NAME_defaultValue;
          PROP_NAME_TO_ID.put(PROP_NAME_defaultValue, PROP_ID_defaultValue);
      
          PROP_ID_TO_NAME[PROP_ID_semanticType] = PROP_NAME_semanticType;
          PROP_NAME_TO_ID.put(PROP_NAME_semanticType, PROP_ID_semanticType);
      
          PROP_ID_TO_NAME[PROP_ID_tagSet] = PROP_NAME_tagSet;
          PROP_NAME_TO_ID.put(PROP_NAME_tagSet, PROP_ID_tagSet);
      
          PROP_ID_TO_NAME[PROP_ID_displayName] = PROP_NAME_displayName;
          PROP_NAME_TO_ID.put(PROP_NAME_displayName, PROP_ID_displayName);
      
          PROP_ID_TO_NAME[PROP_ID_comment] = PROP_NAME_comment;
          PROP_NAME_TO_ID.put(PROP_NAME_comment, PROP_ID_comment);
      
          PROP_ID_TO_NAME[PROP_ID_nativeSqlType] = PROP_NAME_nativeSqlType;
          PROP_NAME_TO_ID.put(PROP_NAME_nativeSqlType, PROP_ID_nativeSqlType);
      
          PROP_ID_TO_NAME[PROP_ID_businessDomainId] = PROP_NAME_businessDomainId;
          PROP_NAME_TO_ID.put(PROP_NAME_businessDomainId, PROP_ID_businessDomainId);
      
          PROP_ID_TO_NAME[PROP_ID_extConfig] = PROP_NAME_extConfig;
          PROP_NAME_TO_ID.put(PROP_NAME_extConfig, PROP_ID_extConfig);
      
          PROP_ID_TO_NAME[PROP_ID_version] = PROP_NAME_version;
          PROP_NAME_TO_ID.put(PROP_NAME_version, PROP_ID_version);
      
          PROP_ID_TO_NAME[PROP_ID_createdBy] = PROP_NAME_createdBy;
          PROP_NAME_TO_ID.put(PROP_NAME_createdBy, PROP_ID_createdBy);
      
          PROP_ID_TO_NAME[PROP_ID_createTime] = PROP_NAME_createTime;
          PROP_NAME_TO_ID.put(PROP_NAME_createTime, PROP_ID_createTime);
      
          PROP_ID_TO_NAME[PROP_ID_updatedBy] = PROP_NAME_updatedBy;
          PROP_NAME_TO_ID.put(PROP_NAME_updatedBy, PROP_ID_updatedBy);
      
          PROP_ID_TO_NAME[PROP_ID_updateTime] = PROP_NAME_updateTime;
          PROP_NAME_TO_ID.put(PROP_NAME_updateTime, PROP_ID_updateTime);
      
          PROP_ID_TO_NAME[PROP_ID_remark] = PROP_NAME_remark;
          PROP_NAME_TO_ID.put(PROP_NAME_remark, PROP_ID_remark);
      
    }

    
    /* 字段ID: ENTITY_FIELD_ID */
    private java.lang.String _entityFieldId;
    
    /* 实体ID: META_ENTITY_ID */
    private java.lang.String _metaEntityId;
    
    /* 是否Delta: IS_DELTA */
    private java.lang.Byte _isDelta;
    
    /* 属性名: FIELD_NAME */
    private java.lang.String _fieldName;
    
    /* 列名: COLUMN_CODE */
    private java.lang.String _columnCode;
    
    /* 属性序号: PROP_ID */
    private java.lang.Integer _propId;
    
    /* 数据类型: STD_DATA_TYPE */
    private java.lang.String _stdDataType;
    
    /* SQL类型: STD_SQL_TYPE */
    private java.lang.String _stdSqlType;
    
    /* 精度: PRECISION */
    private java.lang.Integer _precision;
    
    /* 标度: SCALE */
    private java.lang.Integer _scale;
    
    /* 必填: MANDATORY */
    private java.lang.Byte _mandatory;
    
    /* 主键: PRIMARY */
    private java.lang.Byte _primaryField;
    
    /* 懒加载: LAZY */
    private java.lang.Byte _lazy;
    
    /* 可插入: INSERTABLE */
    private java.lang.Byte _insertable;
    
    /* 可更新: UPDATABLE */
    private java.lang.Byte _updatable;
    
    /* 域: DOMAIN */
    private java.lang.String _domain;
    
    /* 标准域: STD_DOMAIN */
    private java.lang.String _stdDomain;
    
    /* 固定值: FIXED_VALUE */
    private java.lang.String _fixedValue;
    
    /* 默认值: DEFAULT_VALUE */
    private java.lang.String _defaultValue;
    
    /* 语义类型: SEMANTIC_TYPE */
    private java.lang.String _semanticType;
    
    /* 标签集: TAG_SET */
    private java.lang.String _tagSet;
    
    /* 显示名: DISPLAY_NAME */
    private java.lang.String _displayName;
    
    /* 注释: COMMENT */
    private java.lang.String _comment;
    
    /* 原生SQL类型: NATIVE_SQL_TYPE */
    private java.lang.String _nativeSqlType;
    
    /* 业务域ID: BUSINESS_DOMAIN_ID */
    private java.lang.String _businessDomainId;
    
    /* 扩展配置: EXT_CONFIG */
    private java.lang.String _extConfig;
    
    /* 数据版本: VERSION */
    private java.lang.Long _version;
    
    /* 创建人: CREATED_BY */
    private java.lang.String _createdBy;
    
    /* 创建时间: CREATE_TIME */
    private java.sql.Timestamp _createTime;
    
    /* 修改人: UPDATED_BY */
    private java.lang.String _updatedBy;
    
    /* 修改时间: UPDATE_TIME */
    private java.sql.Timestamp _updateTime;
    
    /* 备注: REMARK */
    private java.lang.String _remark;
    

    public _NopMetaEntityField(){
        // for debug
    }

    protected NopMetaEntityField newInstance(){
        NopMetaEntityField entity = new NopMetaEntityField();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopMetaEntityField cloneInstance() {
        NopMetaEntityField entity = newInstance();
        orm_forEachInitedProp((value, propId) -> {
            entity.orm_propValue(propId,value);
        });
        return entity;
    }

    @Override
    public String orm_entityName() {
      // 如果存在实体模型对象，则以模型对象上的设置为准
      IEntityModel entityModel = orm_entityModel();
      if(entityModel != null)
          return entityModel.getName();
      return "io.nop.metadata.dao.entity.NopMetaEntityField";
    }

    @Override
    public int orm_propIdBound(){
      IEntityModel entityModel = orm_entityModel();
      if(entityModel != null)
          return entityModel.getPropIdBound();
      return _PROP_ID_BOUND;
    }

    @Override
    public Object orm_id() {
    
        return buildSimpleId(PROP_ID_entityFieldId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_entityFieldId;
          
    }

    @Override
    public String orm_propName(int propId) {
        if(propId >= PROP_ID_TO_NAME.length)
            return super.orm_propName(propId);
        String propName = PROP_ID_TO_NAME[propId];
        if(propName == null)
           return super.orm_propName(propId);
        return propName;
    }

    @Override
    public int orm_propId(String propName) {
        Integer propId = PROP_NAME_TO_ID.get(propName);
        if(propId == null)
            return super.orm_propId(propName);
        return propId;
    }

    @Override
    public Object orm_propValue(int propId) {
        switch(propId){
        
            case PROP_ID_entityFieldId:
               return getEntityFieldId();
        
            case PROP_ID_metaEntityId:
               return getMetaEntityId();
        
            case PROP_ID_isDelta:
               return getIsDelta();
        
            case PROP_ID_fieldName:
               return getFieldName();
        
            case PROP_ID_columnCode:
               return getColumnCode();
        
            case PROP_ID_propId:
               return getPropId();
        
            case PROP_ID_stdDataType:
               return getStdDataType();
        
            case PROP_ID_stdSqlType:
               return getStdSqlType();
        
            case PROP_ID_precision:
               return getPrecision();
        
            case PROP_ID_scale:
               return getScale();
        
            case PROP_ID_mandatory:
               return getMandatory();
        
            case PROP_ID_primaryField:
               return getPrimaryField();
        
            case PROP_ID_lazy:
               return getLazy();
        
            case PROP_ID_insertable:
               return getInsertable();
        
            case PROP_ID_updatable:
               return getUpdatable();
        
            case PROP_ID_domain:
               return getDomain();
        
            case PROP_ID_stdDomain:
               return getStdDomain();
        
            case PROP_ID_fixedValue:
               return getFixedValue();
        
            case PROP_ID_defaultValue:
               return getDefaultValue();
        
            case PROP_ID_semanticType:
               return getSemanticType();
        
            case PROP_ID_tagSet:
               return getTagSet();
        
            case PROP_ID_displayName:
               return getDisplayName();
        
            case PROP_ID_comment:
               return getComment();
        
            case PROP_ID_nativeSqlType:
               return getNativeSqlType();
        
            case PROP_ID_businessDomainId:
               return getBusinessDomainId();
        
            case PROP_ID_extConfig:
               return getExtConfig();
        
            case PROP_ID_version:
               return getVersion();
        
            case PROP_ID_createdBy:
               return getCreatedBy();
        
            case PROP_ID_createTime:
               return getCreateTime();
        
            case PROP_ID_updatedBy:
               return getUpdatedBy();
        
            case PROP_ID_updateTime:
               return getUpdateTime();
        
            case PROP_ID_remark:
               return getRemark();
        
           default:
              return super.orm_propValue(propId);
        }
    }

    

    @Override
    public void orm_propValue(int propId, Object value){
        switch(propId){
        
            case PROP_ID_entityFieldId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_entityFieldId));
               }
               setEntityFieldId(typedValue);
               break;
            }
        
            case PROP_ID_metaEntityId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_metaEntityId));
               }
               setMetaEntityId(typedValue);
               break;
            }
        
            case PROP_ID_isDelta:{
               java.lang.Byte typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toByte(value,
                       err-> newTypeConversionError(PROP_NAME_isDelta));
               }
               setIsDelta(typedValue);
               break;
            }
        
            case PROP_ID_fieldName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_fieldName));
               }
               setFieldName(typedValue);
               break;
            }
        
            case PROP_ID_columnCode:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_columnCode));
               }
               setColumnCode(typedValue);
               break;
            }
        
            case PROP_ID_propId:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_propId));
               }
               setPropId(typedValue);
               break;
            }
        
            case PROP_ID_stdDataType:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_stdDataType));
               }
               setStdDataType(typedValue);
               break;
            }
        
            case PROP_ID_stdSqlType:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_stdSqlType));
               }
               setStdSqlType(typedValue);
               break;
            }
        
            case PROP_ID_precision:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_precision));
               }
               setPrecision(typedValue);
               break;
            }
        
            case PROP_ID_scale:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_scale));
               }
               setScale(typedValue);
               break;
            }
        
            case PROP_ID_mandatory:{
               java.lang.Byte typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toByte(value,
                       err-> newTypeConversionError(PROP_NAME_mandatory));
               }
               setMandatory(typedValue);
               break;
            }
        
            case PROP_ID_primaryField:{
               java.lang.Byte typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toByte(value,
                       err-> newTypeConversionError(PROP_NAME_primaryField));
               }
               setPrimaryField(typedValue);
               break;
            }
        
            case PROP_ID_lazy:{
               java.lang.Byte typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toByte(value,
                       err-> newTypeConversionError(PROP_NAME_lazy));
               }
               setLazy(typedValue);
               break;
            }
        
            case PROP_ID_insertable:{
               java.lang.Byte typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toByte(value,
                       err-> newTypeConversionError(PROP_NAME_insertable));
               }
               setInsertable(typedValue);
               break;
            }
        
            case PROP_ID_updatable:{
               java.lang.Byte typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toByte(value,
                       err-> newTypeConversionError(PROP_NAME_updatable));
               }
               setUpdatable(typedValue);
               break;
            }
        
            case PROP_ID_domain:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_domain));
               }
               setDomain(typedValue);
               break;
            }
        
            case PROP_ID_stdDomain:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_stdDomain));
               }
               setStdDomain(typedValue);
               break;
            }
        
            case PROP_ID_fixedValue:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_fixedValue));
               }
               setFixedValue(typedValue);
               break;
            }
        
            case PROP_ID_defaultValue:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_defaultValue));
               }
               setDefaultValue(typedValue);
               break;
            }
        
            case PROP_ID_semanticType:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_semanticType));
               }
               setSemanticType(typedValue);
               break;
            }
        
            case PROP_ID_tagSet:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_tagSet));
               }
               setTagSet(typedValue);
               break;
            }
        
            case PROP_ID_displayName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_displayName));
               }
               setDisplayName(typedValue);
               break;
            }
        
            case PROP_ID_comment:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_comment));
               }
               setComment(typedValue);
               break;
            }
        
            case PROP_ID_nativeSqlType:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_nativeSqlType));
               }
               setNativeSqlType(typedValue);
               break;
            }
        
            case PROP_ID_businessDomainId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_businessDomainId));
               }
               setBusinessDomainId(typedValue);
               break;
            }
        
            case PROP_ID_extConfig:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_extConfig));
               }
               setExtConfig(typedValue);
               break;
            }
        
            case PROP_ID_version:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_version));
               }
               setVersion(typedValue);
               break;
            }
        
            case PROP_ID_createdBy:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_createdBy));
               }
               setCreatedBy(typedValue);
               break;
            }
        
            case PROP_ID_createTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_createTime));
               }
               setCreateTime(typedValue);
               break;
            }
        
            case PROP_ID_updatedBy:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_updatedBy));
               }
               setUpdatedBy(typedValue);
               break;
            }
        
            case PROP_ID_updateTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_updateTime));
               }
               setUpdateTime(typedValue);
               break;
            }
        
            case PROP_ID_remark:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_remark));
               }
               setRemark(typedValue);
               break;
            }
        
           default:
              super.orm_propValue(propId,value);
        }
    }

    @Override
    public void orm_internalSet(int propId, Object value) {
        switch(propId){
        
            case PROP_ID_entityFieldId:{
               onInitProp(propId);
               this._entityFieldId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_metaEntityId:{
               onInitProp(propId);
               this._metaEntityId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_isDelta:{
               onInitProp(propId);
               this._isDelta = (java.lang.Byte)value;
               
               break;
            }
        
            case PROP_ID_fieldName:{
               onInitProp(propId);
               this._fieldName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_columnCode:{
               onInitProp(propId);
               this._columnCode = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_propId:{
               onInitProp(propId);
               this._propId = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_stdDataType:{
               onInitProp(propId);
               this._stdDataType = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_stdSqlType:{
               onInitProp(propId);
               this._stdSqlType = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_precision:{
               onInitProp(propId);
               this._precision = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_scale:{
               onInitProp(propId);
               this._scale = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_mandatory:{
               onInitProp(propId);
               this._mandatory = (java.lang.Byte)value;
               
               break;
            }
        
            case PROP_ID_primaryField:{
               onInitProp(propId);
               this._primaryField = (java.lang.Byte)value;
               
               break;
            }
        
            case PROP_ID_lazy:{
               onInitProp(propId);
               this._lazy = (java.lang.Byte)value;
               
               break;
            }
        
            case PROP_ID_insertable:{
               onInitProp(propId);
               this._insertable = (java.lang.Byte)value;
               
               break;
            }
        
            case PROP_ID_updatable:{
               onInitProp(propId);
               this._updatable = (java.lang.Byte)value;
               
               break;
            }
        
            case PROP_ID_domain:{
               onInitProp(propId);
               this._domain = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_stdDomain:{
               onInitProp(propId);
               this._stdDomain = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_fixedValue:{
               onInitProp(propId);
               this._fixedValue = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_defaultValue:{
               onInitProp(propId);
               this._defaultValue = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_semanticType:{
               onInitProp(propId);
               this._semanticType = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_tagSet:{
               onInitProp(propId);
               this._tagSet = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_displayName:{
               onInitProp(propId);
               this._displayName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_comment:{
               onInitProp(propId);
               this._comment = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_nativeSqlType:{
               onInitProp(propId);
               this._nativeSqlType = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_businessDomainId:{
               onInitProp(propId);
               this._businessDomainId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_extConfig:{
               onInitProp(propId);
               this._extConfig = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_version:{
               onInitProp(propId);
               this._version = (java.lang.Long)value;
               
               break;
            }
        
            case PROP_ID_createdBy:{
               onInitProp(propId);
               this._createdBy = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_createTime:{
               onInitProp(propId);
               this._createTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_updatedBy:{
               onInitProp(propId);
               this._updatedBy = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_updateTime:{
               onInitProp(propId);
               this._updateTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_remark:{
               onInitProp(propId);
               this._remark = (java.lang.String)value;
               
               break;
            }
        
           default:
              super.orm_internalSet(propId,value);
        }
    }

    
    /**
     * 字段ID: ENTITY_FIELD_ID
     */
    public final java.lang.String getEntityFieldId(){
         onPropGet(PROP_ID_entityFieldId);
         return _entityFieldId;
    }

    /**
     * 字段ID: ENTITY_FIELD_ID
     */
    public final void setEntityFieldId(java.lang.String value){
        if(onPropSet(PROP_ID_entityFieldId,value)){
            this._entityFieldId = value;
            internalClearRefs(PROP_ID_entityFieldId);
            orm_id();
        }
    }
    
    /**
     * 实体ID: META_ENTITY_ID
     */
    public final java.lang.String getMetaEntityId(){
         onPropGet(PROP_ID_metaEntityId);
         return _metaEntityId;
    }

    /**
     * 实体ID: META_ENTITY_ID
     */
    public final void setMetaEntityId(java.lang.String value){
        if(onPropSet(PROP_ID_metaEntityId,value)){
            this._metaEntityId = value;
            internalClearRefs(PROP_ID_metaEntityId);
            
        }
    }
    
    /**
     * 是否Delta: IS_DELTA
     */
    public final java.lang.Byte getIsDelta(){
         onPropGet(PROP_ID_isDelta);
         return _isDelta;
    }

    /**
     * 是否Delta: IS_DELTA
     */
    public final void setIsDelta(java.lang.Byte value){
        if(onPropSet(PROP_ID_isDelta,value)){
            this._isDelta = value;
            internalClearRefs(PROP_ID_isDelta);
            
        }
    }
    
    /**
     * 属性名: FIELD_NAME
     */
    public final java.lang.String getFieldName(){
         onPropGet(PROP_ID_fieldName);
         return _fieldName;
    }

    /**
     * 属性名: FIELD_NAME
     */
    public final void setFieldName(java.lang.String value){
        if(onPropSet(PROP_ID_fieldName,value)){
            this._fieldName = value;
            internalClearRefs(PROP_ID_fieldName);
            
        }
    }
    
    /**
     * 列名: COLUMN_CODE
     */
    public final java.lang.String getColumnCode(){
         onPropGet(PROP_ID_columnCode);
         return _columnCode;
    }

    /**
     * 列名: COLUMN_CODE
     */
    public final void setColumnCode(java.lang.String value){
        if(onPropSet(PROP_ID_columnCode,value)){
            this._columnCode = value;
            internalClearRefs(PROP_ID_columnCode);
            
        }
    }
    
    /**
     * 属性序号: PROP_ID
     */
    public final java.lang.Integer getPropId(){
         onPropGet(PROP_ID_propId);
         return _propId;
    }

    /**
     * 属性序号: PROP_ID
     */
    public final void setPropId(java.lang.Integer value){
        if(onPropSet(PROP_ID_propId,value)){
            this._propId = value;
            internalClearRefs(PROP_ID_propId);
            
        }
    }
    
    /**
     * 数据类型: STD_DATA_TYPE
     */
    public final java.lang.String getStdDataType(){
         onPropGet(PROP_ID_stdDataType);
         return _stdDataType;
    }

    /**
     * 数据类型: STD_DATA_TYPE
     */
    public final void setStdDataType(java.lang.String value){
        if(onPropSet(PROP_ID_stdDataType,value)){
            this._stdDataType = value;
            internalClearRefs(PROP_ID_stdDataType);
            
        }
    }
    
    /**
     * SQL类型: STD_SQL_TYPE
     */
    public final java.lang.String getStdSqlType(){
         onPropGet(PROP_ID_stdSqlType);
         return _stdSqlType;
    }

    /**
     * SQL类型: STD_SQL_TYPE
     */
    public final void setStdSqlType(java.lang.String value){
        if(onPropSet(PROP_ID_stdSqlType,value)){
            this._stdSqlType = value;
            internalClearRefs(PROP_ID_stdSqlType);
            
        }
    }
    
    /**
     * 精度: PRECISION
     */
    public final java.lang.Integer getPrecision(){
         onPropGet(PROP_ID_precision);
         return _precision;
    }

    /**
     * 精度: PRECISION
     */
    public final void setPrecision(java.lang.Integer value){
        if(onPropSet(PROP_ID_precision,value)){
            this._precision = value;
            internalClearRefs(PROP_ID_precision);
            
        }
    }
    
    /**
     * 标度: SCALE
     */
    public final java.lang.Integer getScale(){
         onPropGet(PROP_ID_scale);
         return _scale;
    }

    /**
     * 标度: SCALE
     */
    public final void setScale(java.lang.Integer value){
        if(onPropSet(PROP_ID_scale,value)){
            this._scale = value;
            internalClearRefs(PROP_ID_scale);
            
        }
    }
    
    /**
     * 必填: MANDATORY
     */
    public final java.lang.Byte getMandatory(){
         onPropGet(PROP_ID_mandatory);
         return _mandatory;
    }

    /**
     * 必填: MANDATORY
     */
    public final void setMandatory(java.lang.Byte value){
        if(onPropSet(PROP_ID_mandatory,value)){
            this._mandatory = value;
            internalClearRefs(PROP_ID_mandatory);
            
        }
    }
    
    /**
     * 主键: PRIMARY
     */
    public final java.lang.Byte getPrimaryField(){
         onPropGet(PROP_ID_primaryField);
         return _primaryField;
    }

    /**
     * 主键: PRIMARY
     */
    public final void setPrimaryField(java.lang.Byte value){
        if(onPropSet(PROP_ID_primaryField,value)){
            this._primaryField = value;
            internalClearRefs(PROP_ID_primaryField);
            
        }
    }
    
    /**
     * 懒加载: LAZY
     */
    public final java.lang.Byte getLazy(){
         onPropGet(PROP_ID_lazy);
         return _lazy;
    }

    /**
     * 懒加载: LAZY
     */
    public final void setLazy(java.lang.Byte value){
        if(onPropSet(PROP_ID_lazy,value)){
            this._lazy = value;
            internalClearRefs(PROP_ID_lazy);
            
        }
    }
    
    /**
     * 可插入: INSERTABLE
     */
    public final java.lang.Byte getInsertable(){
         onPropGet(PROP_ID_insertable);
         return _insertable;
    }

    /**
     * 可插入: INSERTABLE
     */
    public final void setInsertable(java.lang.Byte value){
        if(onPropSet(PROP_ID_insertable,value)){
            this._insertable = value;
            internalClearRefs(PROP_ID_insertable);
            
        }
    }
    
    /**
     * 可更新: UPDATABLE
     */
    public final java.lang.Byte getUpdatable(){
         onPropGet(PROP_ID_updatable);
         return _updatable;
    }

    /**
     * 可更新: UPDATABLE
     */
    public final void setUpdatable(java.lang.Byte value){
        if(onPropSet(PROP_ID_updatable,value)){
            this._updatable = value;
            internalClearRefs(PROP_ID_updatable);
            
        }
    }
    
    /**
     * 域: DOMAIN
     */
    public final java.lang.String getDomain(){
         onPropGet(PROP_ID_domain);
         return _domain;
    }

    /**
     * 域: DOMAIN
     */
    public final void setDomain(java.lang.String value){
        if(onPropSet(PROP_ID_domain,value)){
            this._domain = value;
            internalClearRefs(PROP_ID_domain);
            
        }
    }
    
    /**
     * 标准域: STD_DOMAIN
     */
    public final java.lang.String getStdDomain(){
         onPropGet(PROP_ID_stdDomain);
         return _stdDomain;
    }

    /**
     * 标准域: STD_DOMAIN
     */
    public final void setStdDomain(java.lang.String value){
        if(onPropSet(PROP_ID_stdDomain,value)){
            this._stdDomain = value;
            internalClearRefs(PROP_ID_stdDomain);
            
        }
    }
    
    /**
     * 固定值: FIXED_VALUE
     */
    public final java.lang.String getFixedValue(){
         onPropGet(PROP_ID_fixedValue);
         return _fixedValue;
    }

    /**
     * 固定值: FIXED_VALUE
     */
    public final void setFixedValue(java.lang.String value){
        if(onPropSet(PROP_ID_fixedValue,value)){
            this._fixedValue = value;
            internalClearRefs(PROP_ID_fixedValue);
            
        }
    }
    
    /**
     * 默认值: DEFAULT_VALUE
     */
    public final java.lang.String getDefaultValue(){
         onPropGet(PROP_ID_defaultValue);
         return _defaultValue;
    }

    /**
     * 默认值: DEFAULT_VALUE
     */
    public final void setDefaultValue(java.lang.String value){
        if(onPropSet(PROP_ID_defaultValue,value)){
            this._defaultValue = value;
            internalClearRefs(PROP_ID_defaultValue);
            
        }
    }
    
    /**
     * 语义类型: SEMANTIC_TYPE
     */
    public final java.lang.String getSemanticType(){
         onPropGet(PROP_ID_semanticType);
         return _semanticType;
    }

    /**
     * 语义类型: SEMANTIC_TYPE
     */
    public final void setSemanticType(java.lang.String value){
        if(onPropSet(PROP_ID_semanticType,value)){
            this._semanticType = value;
            internalClearRefs(PROP_ID_semanticType);
            
        }
    }
    
    /**
     * 标签集: TAG_SET
     */
    public final java.lang.String getTagSet(){
         onPropGet(PROP_ID_tagSet);
         return _tagSet;
    }

    /**
     * 标签集: TAG_SET
     */
    public final void setTagSet(java.lang.String value){
        if(onPropSet(PROP_ID_tagSet,value)){
            this._tagSet = value;
            internalClearRefs(PROP_ID_tagSet);
            
        }
    }
    
    /**
     * 显示名: DISPLAY_NAME
     */
    public final java.lang.String getDisplayName(){
         onPropGet(PROP_ID_displayName);
         return _displayName;
    }

    /**
     * 显示名: DISPLAY_NAME
     */
    public final void setDisplayName(java.lang.String value){
        if(onPropSet(PROP_ID_displayName,value)){
            this._displayName = value;
            internalClearRefs(PROP_ID_displayName);
            
        }
    }
    
    /**
     * 注释: COMMENT
     */
    public final java.lang.String getComment(){
         onPropGet(PROP_ID_comment);
         return _comment;
    }

    /**
     * 注释: COMMENT
     */
    public final void setComment(java.lang.String value){
        if(onPropSet(PROP_ID_comment,value)){
            this._comment = value;
            internalClearRefs(PROP_ID_comment);
            
        }
    }
    
    /**
     * 原生SQL类型: NATIVE_SQL_TYPE
     */
    public final java.lang.String getNativeSqlType(){
         onPropGet(PROP_ID_nativeSqlType);
         return _nativeSqlType;
    }

    /**
     * 原生SQL类型: NATIVE_SQL_TYPE
     */
    public final void setNativeSqlType(java.lang.String value){
        if(onPropSet(PROP_ID_nativeSqlType,value)){
            this._nativeSqlType = value;
            internalClearRefs(PROP_ID_nativeSqlType);
            
        }
    }
    
    /**
     * 业务域ID: BUSINESS_DOMAIN_ID
     */
    public final java.lang.String getBusinessDomainId(){
         onPropGet(PROP_ID_businessDomainId);
         return _businessDomainId;
    }

    /**
     * 业务域ID: BUSINESS_DOMAIN_ID
     */
    public final void setBusinessDomainId(java.lang.String value){
        if(onPropSet(PROP_ID_businessDomainId,value)){
            this._businessDomainId = value;
            internalClearRefs(PROP_ID_businessDomainId);
            
        }
    }
    
    /**
     * 扩展配置: EXT_CONFIG
     */
    public final java.lang.String getExtConfig(){
         onPropGet(PROP_ID_extConfig);
         return _extConfig;
    }

    /**
     * 扩展配置: EXT_CONFIG
     */
    public final void setExtConfig(java.lang.String value){
        if(onPropSet(PROP_ID_extConfig,value)){
            this._extConfig = value;
            internalClearRefs(PROP_ID_extConfig);
            
        }
    }
    
    /**
     * 数据版本: VERSION
     */
    public final java.lang.Long getVersion(){
         onPropGet(PROP_ID_version);
         return _version;
    }

    /**
     * 数据版本: VERSION
     */
    public final void setVersion(java.lang.Long value){
        if(onPropSet(PROP_ID_version,value)){
            this._version = value;
            internalClearRefs(PROP_ID_version);
            
        }
    }
    
    /**
     * 创建人: CREATED_BY
     */
    public final java.lang.String getCreatedBy(){
         onPropGet(PROP_ID_createdBy);
         return _createdBy;
    }

    /**
     * 创建人: CREATED_BY
     */
    public final void setCreatedBy(java.lang.String value){
        if(onPropSet(PROP_ID_createdBy,value)){
            this._createdBy = value;
            internalClearRefs(PROP_ID_createdBy);
            
        }
    }
    
    /**
     * 创建时间: CREATE_TIME
     */
    public final java.sql.Timestamp getCreateTime(){
         onPropGet(PROP_ID_createTime);
         return _createTime;
    }

    /**
     * 创建时间: CREATE_TIME
     */
    public final void setCreateTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_createTime,value)){
            this._createTime = value;
            internalClearRefs(PROP_ID_createTime);
            
        }
    }
    
    /**
     * 修改人: UPDATED_BY
     */
    public final java.lang.String getUpdatedBy(){
         onPropGet(PROP_ID_updatedBy);
         return _updatedBy;
    }

    /**
     * 修改人: UPDATED_BY
     */
    public final void setUpdatedBy(java.lang.String value){
        if(onPropSet(PROP_ID_updatedBy,value)){
            this._updatedBy = value;
            internalClearRefs(PROP_ID_updatedBy);
            
        }
    }
    
    /**
     * 修改时间: UPDATE_TIME
     */
    public final java.sql.Timestamp getUpdateTime(){
         onPropGet(PROP_ID_updateTime);
         return _updateTime;
    }

    /**
     * 修改时间: UPDATE_TIME
     */
    public final void setUpdateTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_updateTime,value)){
            this._updateTime = value;
            internalClearRefs(PROP_ID_updateTime);
            
        }
    }
    
    /**
     * 备注: REMARK
     */
    public final java.lang.String getRemark(){
         onPropGet(PROP_ID_remark);
         return _remark;
    }

    /**
     * 备注: REMARK
     */
    public final void setRemark(java.lang.String value){
        if(onPropSet(PROP_ID_remark,value)){
            this._remark = value;
            internalClearRefs(PROP_ID_remark);
            
        }
    }
    
    /**
     * 元数据实体
     */
    public final io.nop.metadata.dao.entity.NopMetaEntity getMetaEntity(){
       return (io.nop.metadata.dao.entity.NopMetaEntity)internalGetRefEntity(PROP_NAME_metaEntity);
    }

    public final void setMetaEntity(io.nop.metadata.dao.entity.NopMetaEntity refEntity){
   
           if(refEntity == null){
           
                   this.setMetaEntityId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_metaEntity, refEntity,()->{
           
                           this.setMetaEntityId(refEntity.getMetaEntityId());
                       
           });
           }
       
    }
       
   private io.nop.orm.component.JsonOrmComponent _extConfigComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_extConfigComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_extConfigComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_extConfig);
      
   }

   public final io.nop.orm.component.JsonOrmComponent getExtConfigComponent(){
      if(_extConfigComponent == null){
          _extConfigComponent = new io.nop.orm.component.JsonOrmComponent();
          _extConfigComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_extConfigComponent);
      }
      return _extConfigComponent;
   }

}
// resume CPD analysis - CPD-ON
