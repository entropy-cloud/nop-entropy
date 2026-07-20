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

import io.nop.metadata.dao.entity.NopMetaDomain;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  域定义: nop_meta_domain
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopMetaDomain extends DynamicOrmEntity{
    
    /* 域ID: META_DOMAIN_ID VARCHAR */
    public static final String PROP_NAME_metaDomainId = "metaDomainId";
    public static final int PROP_ID_metaDomainId = 1;
    
    /* 模型ID: ORM_MODEL_ID VARCHAR */
    public static final String PROP_NAME_ormModelId = "ormModelId";
    public static final int PROP_ID_ormModelId = 2;
    
    /* 是否Delta: IS_DELTA TINYINT */
    public static final String PROP_NAME_isDelta = "isDelta";
    public static final int PROP_ID_isDelta = 3;
    
    /* 域名: DOMAIN_NAME VARCHAR */
    public static final String PROP_NAME_domainName = "domainName";
    public static final int PROP_ID_domainName = 4;
    
    /* 显示名: DISPLAY_NAME VARCHAR */
    public static final String PROP_NAME_displayName = "displayName";
    public static final int PROP_ID_displayName = 5;
    
    /* 描述: DESCRIPTION VARCHAR */
    public static final String PROP_NAME_description = "description";
    public static final int PROP_ID_description = 6;
    
    /* 标准域: STD_DOMAIN VARCHAR */
    public static final String PROP_NAME_stdDomain = "stdDomain";
    public static final int PROP_ID_stdDomain = 7;
    
    /* 数据类型: STD_DATA_TYPE VARCHAR */
    public static final String PROP_NAME_stdDataType = "stdDataType";
    public static final int PROP_ID_stdDataType = 8;
    
    /* SQL类型: STD_SQL_TYPE VARCHAR */
    public static final String PROP_NAME_stdSqlType = "stdSqlType";
    public static final int PROP_ID_stdSqlType = 9;
    
    /* 精度: PRECISION INTEGER */
    public static final String PROP_NAME_precision = "precision";
    public static final int PROP_ID_precision = 10;
    
    /* 标度: SCALE INTEGER */
    public static final String PROP_NAME_scale = "scale";
    public static final int PROP_ID_scale = 11;
    
    /* 校验正则: VALIDATION_PATTERN VARCHAR */
    public static final String PROP_NAME_validationPattern = "validationPattern";
    public static final int PROP_ID_validationPattern = 12;
    
    /* 默认值: DEFAULT_VALUE VARCHAR */
    public static final String PROP_NAME_defaultValue = "defaultValue";
    public static final int PROP_ID_defaultValue = 13;
    
    /* 全局通用域: IS_GLOBAL TINYINT */
    public static final String PROP_NAME_isGlobal = "isGlobal";
    public static final int PROP_ID_isGlobal = 14;
    
    /* 来源模块版本ID: SOURCE_MODULE_ID VARCHAR */
    public static final String PROP_NAME_sourceModuleId = "sourceModuleId";
    public static final int PROP_ID_sourceModuleId = 15;
    
    /* 标签集: TAG_SET VARCHAR */
    public static final String PROP_NAME_tagSet = "tagSet";
    public static final int PROP_ID_tagSet = 16;
    
    /* 扩展配置: EXT_CONFIG VARCHAR */
    public static final String PROP_NAME_extConfig = "extConfig";
    public static final int PROP_ID_extConfig = 17;
    
    /* 数据版本: VERSION BIGINT */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 18;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 19;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 20;
    
    /* 修改人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 21;
    
    /* 修改时间: UPDATE_TIME TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 22;
    
    /* 备注: REMARK VARCHAR */
    public static final String PROP_NAME_remark = "remark";
    public static final int PROP_ID_remark = 23;
    

    private static int _PROP_ID_BOUND = 24;

    
    /* relation: ORM模型 */
    public static final String PROP_NAME_ormModel = "ormModel";
    
    /* component:  */
    public static final String PROP_NAME_extConfigComponent = "extConfigComponent";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_metaDomainId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_metaDomainId};

    private static final String[] PROP_ID_TO_NAME = new String[24];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_metaDomainId] = PROP_NAME_metaDomainId;
          PROP_NAME_TO_ID.put(PROP_NAME_metaDomainId, PROP_ID_metaDomainId);
      
          PROP_ID_TO_NAME[PROP_ID_ormModelId] = PROP_NAME_ormModelId;
          PROP_NAME_TO_ID.put(PROP_NAME_ormModelId, PROP_ID_ormModelId);
      
          PROP_ID_TO_NAME[PROP_ID_isDelta] = PROP_NAME_isDelta;
          PROP_NAME_TO_ID.put(PROP_NAME_isDelta, PROP_ID_isDelta);
      
          PROP_ID_TO_NAME[PROP_ID_domainName] = PROP_NAME_domainName;
          PROP_NAME_TO_ID.put(PROP_NAME_domainName, PROP_ID_domainName);
      
          PROP_ID_TO_NAME[PROP_ID_displayName] = PROP_NAME_displayName;
          PROP_NAME_TO_ID.put(PROP_NAME_displayName, PROP_ID_displayName);
      
          PROP_ID_TO_NAME[PROP_ID_description] = PROP_NAME_description;
          PROP_NAME_TO_ID.put(PROP_NAME_description, PROP_ID_description);
      
          PROP_ID_TO_NAME[PROP_ID_stdDomain] = PROP_NAME_stdDomain;
          PROP_NAME_TO_ID.put(PROP_NAME_stdDomain, PROP_ID_stdDomain);
      
          PROP_ID_TO_NAME[PROP_ID_stdDataType] = PROP_NAME_stdDataType;
          PROP_NAME_TO_ID.put(PROP_NAME_stdDataType, PROP_ID_stdDataType);
      
          PROP_ID_TO_NAME[PROP_ID_stdSqlType] = PROP_NAME_stdSqlType;
          PROP_NAME_TO_ID.put(PROP_NAME_stdSqlType, PROP_ID_stdSqlType);
      
          PROP_ID_TO_NAME[PROP_ID_precision] = PROP_NAME_precision;
          PROP_NAME_TO_ID.put(PROP_NAME_precision, PROP_ID_precision);
      
          PROP_ID_TO_NAME[PROP_ID_scale] = PROP_NAME_scale;
          PROP_NAME_TO_ID.put(PROP_NAME_scale, PROP_ID_scale);
      
          PROP_ID_TO_NAME[PROP_ID_validationPattern] = PROP_NAME_validationPattern;
          PROP_NAME_TO_ID.put(PROP_NAME_validationPattern, PROP_ID_validationPattern);
      
          PROP_ID_TO_NAME[PROP_ID_defaultValue] = PROP_NAME_defaultValue;
          PROP_NAME_TO_ID.put(PROP_NAME_defaultValue, PROP_ID_defaultValue);
      
          PROP_ID_TO_NAME[PROP_ID_isGlobal] = PROP_NAME_isGlobal;
          PROP_NAME_TO_ID.put(PROP_NAME_isGlobal, PROP_ID_isGlobal);
      
          PROP_ID_TO_NAME[PROP_ID_sourceModuleId] = PROP_NAME_sourceModuleId;
          PROP_NAME_TO_ID.put(PROP_NAME_sourceModuleId, PROP_ID_sourceModuleId);
      
          PROP_ID_TO_NAME[PROP_ID_tagSet] = PROP_NAME_tagSet;
          PROP_NAME_TO_ID.put(PROP_NAME_tagSet, PROP_ID_tagSet);
      
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

    
    /* 域ID: META_DOMAIN_ID */
    private java.lang.String _metaDomainId;
    
    /* 模型ID: ORM_MODEL_ID */
    private java.lang.String _ormModelId;
    
    /* 是否Delta: IS_DELTA */
    private java.lang.Byte _isDelta;
    
    /* 域名: DOMAIN_NAME */
    private java.lang.String _domainName;
    
    /* 显示名: DISPLAY_NAME */
    private java.lang.String _displayName;
    
    /* 描述: DESCRIPTION */
    private java.lang.String _description;
    
    /* 标准域: STD_DOMAIN */
    private java.lang.String _stdDomain;
    
    /* 数据类型: STD_DATA_TYPE */
    private java.lang.String _stdDataType;
    
    /* SQL类型: STD_SQL_TYPE */
    private java.lang.String _stdSqlType;
    
    /* 精度: PRECISION */
    private java.lang.Integer _precision;
    
    /* 标度: SCALE */
    private java.lang.Integer _scale;
    
    /* 校验正则: VALIDATION_PATTERN */
    private java.lang.String _validationPattern;
    
    /* 默认值: DEFAULT_VALUE */
    private java.lang.String _defaultValue;
    
    /* 全局通用域: IS_GLOBAL */
    private java.lang.Byte _isGlobal;
    
    /* 来源模块版本ID: SOURCE_MODULE_ID */
    private java.lang.String _sourceModuleId;
    
    /* 标签集: TAG_SET */
    private java.lang.String _tagSet;
    
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
    

    public _NopMetaDomain(){
        // for debug
    }

    protected NopMetaDomain newInstance(){
        NopMetaDomain entity = new NopMetaDomain();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopMetaDomain cloneInstance() {
        NopMetaDomain entity = newInstance();
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
      return "io.nop.metadata.dao.entity.NopMetaDomain";
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
    
        return buildSimpleId(PROP_ID_metaDomainId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_metaDomainId;
          
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
        
            case PROP_ID_metaDomainId:
               return getMetaDomainId();
        
            case PROP_ID_ormModelId:
               return getOrmModelId();
        
            case PROP_ID_isDelta:
               return getIsDelta();
        
            case PROP_ID_domainName:
               return getDomainName();
        
            case PROP_ID_displayName:
               return getDisplayName();
        
            case PROP_ID_description:
               return getDescription();
        
            case PROP_ID_stdDomain:
               return getStdDomain();
        
            case PROP_ID_stdDataType:
               return getStdDataType();
        
            case PROP_ID_stdSqlType:
               return getStdSqlType();
        
            case PROP_ID_precision:
               return getPrecision();
        
            case PROP_ID_scale:
               return getScale();
        
            case PROP_ID_validationPattern:
               return getValidationPattern();
        
            case PROP_ID_defaultValue:
               return getDefaultValue();
        
            case PROP_ID_isGlobal:
               return getIsGlobal();
        
            case PROP_ID_sourceModuleId:
               return getSourceModuleId();
        
            case PROP_ID_tagSet:
               return getTagSet();
        
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
        
            case PROP_ID_metaDomainId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_metaDomainId));
               }
               setMetaDomainId(typedValue);
               break;
            }
        
            case PROP_ID_ormModelId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_ormModelId));
               }
               setOrmModelId(typedValue);
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
        
            case PROP_ID_domainName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_domainName));
               }
               setDomainName(typedValue);
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
        
            case PROP_ID_description:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_description));
               }
               setDescription(typedValue);
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
        
            case PROP_ID_validationPattern:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_validationPattern));
               }
               setValidationPattern(typedValue);
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
        
            case PROP_ID_isGlobal:{
               java.lang.Byte typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toByte(value,
                       err-> newTypeConversionError(PROP_NAME_isGlobal));
               }
               setIsGlobal(typedValue);
               break;
            }
        
            case PROP_ID_sourceModuleId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_sourceModuleId));
               }
               setSourceModuleId(typedValue);
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
        
            case PROP_ID_metaDomainId:{
               onInitProp(propId);
               this._metaDomainId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_ormModelId:{
               onInitProp(propId);
               this._ormModelId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_isDelta:{
               onInitProp(propId);
               this._isDelta = (java.lang.Byte)value;
               
               break;
            }
        
            case PROP_ID_domainName:{
               onInitProp(propId);
               this._domainName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_displayName:{
               onInitProp(propId);
               this._displayName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_description:{
               onInitProp(propId);
               this._description = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_stdDomain:{
               onInitProp(propId);
               this._stdDomain = (java.lang.String)value;
               
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
        
            case PROP_ID_validationPattern:{
               onInitProp(propId);
               this._validationPattern = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_defaultValue:{
               onInitProp(propId);
               this._defaultValue = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_isGlobal:{
               onInitProp(propId);
               this._isGlobal = (java.lang.Byte)value;
               
               break;
            }
        
            case PROP_ID_sourceModuleId:{
               onInitProp(propId);
               this._sourceModuleId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_tagSet:{
               onInitProp(propId);
               this._tagSet = (java.lang.String)value;
               
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
     * 域ID: META_DOMAIN_ID
     */
    public final java.lang.String getMetaDomainId(){
         onPropGet(PROP_ID_metaDomainId);
         return _metaDomainId;
    }

    /**
     * 域ID: META_DOMAIN_ID
     */
    public final void setMetaDomainId(java.lang.String value){
        if(onPropSet(PROP_ID_metaDomainId,value)){
            this._metaDomainId = value;
            internalClearRefs(PROP_ID_metaDomainId);
            orm_id();
        }
    }
    
    /**
     * 模型ID: ORM_MODEL_ID
     */
    public final java.lang.String getOrmModelId(){
         onPropGet(PROP_ID_ormModelId);
         return _ormModelId;
    }

    /**
     * 模型ID: ORM_MODEL_ID
     */
    public final void setOrmModelId(java.lang.String value){
        if(onPropSet(PROP_ID_ormModelId,value)){
            this._ormModelId = value;
            internalClearRefs(PROP_ID_ormModelId);
            
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
     * 域名: DOMAIN_NAME
     */
    public final java.lang.String getDomainName(){
         onPropGet(PROP_ID_domainName);
         return _domainName;
    }

    /**
     * 域名: DOMAIN_NAME
     */
    public final void setDomainName(java.lang.String value){
        if(onPropSet(PROP_ID_domainName,value)){
            this._domainName = value;
            internalClearRefs(PROP_ID_domainName);
            
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
     * 描述: DESCRIPTION
     */
    public final java.lang.String getDescription(){
         onPropGet(PROP_ID_description);
         return _description;
    }

    /**
     * 描述: DESCRIPTION
     */
    public final void setDescription(java.lang.String value){
        if(onPropSet(PROP_ID_description,value)){
            this._description = value;
            internalClearRefs(PROP_ID_description);
            
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
     * 校验正则: VALIDATION_PATTERN
     */
    public final java.lang.String getValidationPattern(){
         onPropGet(PROP_ID_validationPattern);
         return _validationPattern;
    }

    /**
     * 校验正则: VALIDATION_PATTERN
     */
    public final void setValidationPattern(java.lang.String value){
        if(onPropSet(PROP_ID_validationPattern,value)){
            this._validationPattern = value;
            internalClearRefs(PROP_ID_validationPattern);
            
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
     * 全局通用域: IS_GLOBAL
     */
    public final java.lang.Byte getIsGlobal(){
         onPropGet(PROP_ID_isGlobal);
         return _isGlobal;
    }

    /**
     * 全局通用域: IS_GLOBAL
     */
    public final void setIsGlobal(java.lang.Byte value){
        if(onPropSet(PROP_ID_isGlobal,value)){
            this._isGlobal = value;
            internalClearRefs(PROP_ID_isGlobal);
            
        }
    }
    
    /**
     * 来源模块版本ID: SOURCE_MODULE_ID
     */
    public final java.lang.String getSourceModuleId(){
         onPropGet(PROP_ID_sourceModuleId);
         return _sourceModuleId;
    }

    /**
     * 来源模块版本ID: SOURCE_MODULE_ID
     */
    public final void setSourceModuleId(java.lang.String value){
        if(onPropSet(PROP_ID_sourceModuleId,value)){
            this._sourceModuleId = value;
            internalClearRefs(PROP_ID_sourceModuleId);
            
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
     * ORM模型
     */
    public final io.nop.metadata.dao.entity.NopMetaOrmModel getOrmModel(){
       return (io.nop.metadata.dao.entity.NopMetaOrmModel)internalGetRefEntity(PROP_NAME_ormModel);
    }

    public final void setOrmModel(io.nop.metadata.dao.entity.NopMetaOrmModel refEntity){
   
           if(refEntity == null){
           
                   this.setOrmModelId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_ormModel, refEntity,()->{
           
                           this.setOrmModelId(refEntity.getOrmModelId());
                       
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
