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

import io.nop.metadata.dao.entity.NopMetaQualityRule;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  质量规则: nop_meta_quality_rule
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopMetaQualityRule extends DynamicOrmEntity{
    
    /* 规则ID: QUALITY_RULE_ID VARCHAR */
    public static final String PROP_NAME_qualityRuleId = "qualityRuleId";
    public static final int PROP_ID_qualityRuleId = 1;
    
    /* 规则名: RULE_NAME VARCHAR */
    public static final String PROP_NAME_ruleName = "ruleName";
    public static final int PROP_ID_ruleName = 2;
    
    /* 显示名: DISPLAY_NAME VARCHAR */
    public static final String PROP_NAME_displayName = "displayName";
    public static final int PROP_ID_displayName = 3;
    
    /* 规则类型: RULE_TYPE VARCHAR */
    public static final String PROP_NAME_ruleType = "ruleType";
    public static final int PROP_ID_ruleType = 4;
    
    /* 对象类型: ENTITY_TYPE VARCHAR */
    public static final String PROP_NAME_entityType = "entityType";
    public static final int PROP_ID_entityType = 5;
    
    /* 挂载对象ID: ENTITY_ID VARCHAR */
    public static final String PROP_NAME_entityId = "entityId";
    public static final int PROP_ID_entityId = 6;
    
    /* 严重级别: SEVERITY VARCHAR */
    public static final String PROP_NAME_severity = "severity";
    public static final int PROP_ID_severity = 7;
    
    /* SQL表达式: SQL_EXPRESSION VARCHAR */
    public static final String PROP_NAME_sqlExpression = "sqlExpression";
    public static final int PROP_ID_sqlExpression = 8;
    
    /* 阈值: THRESHOLD DOUBLE */
    public static final String PROP_NAME_threshold = "threshold";
    public static final int PROP_ID_threshold = 9;
    
    /* 参数: PARAMS VARCHAR */
    public static final String PROP_NAME_params = "params";
    public static final int PROP_ID_params = 10;
    
    /* 扩展配置: EXT_CONFIG VARCHAR */
    public static final String PROP_NAME_extConfig = "extConfig";
    public static final int PROP_ID_extConfig = 11;
    
    /* 数据版本: VERSION BIGINT */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 12;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 13;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 14;
    
    /* 修改人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 15;
    
    /* 修改时间: UPDATE_TIME TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 16;
    
    /* 备注: REMARK VARCHAR */
    public static final String PROP_NAME_remark = "remark";
    public static final int PROP_ID_remark = 17;
    

    private static int _PROP_ID_BOUND = 18;

    
    /* relation: 质量结果集 */
    public static final String PROP_NAME_results = "results";
    
    /* component:  */
    public static final String PROP_NAME_paramsComponent = "paramsComponent";
    
    /* component:  */
    public static final String PROP_NAME_extConfigComponent = "extConfigComponent";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_qualityRuleId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_qualityRuleId};

    private static final String[] PROP_ID_TO_NAME = new String[18];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_qualityRuleId] = PROP_NAME_qualityRuleId;
          PROP_NAME_TO_ID.put(PROP_NAME_qualityRuleId, PROP_ID_qualityRuleId);
      
          PROP_ID_TO_NAME[PROP_ID_ruleName] = PROP_NAME_ruleName;
          PROP_NAME_TO_ID.put(PROP_NAME_ruleName, PROP_ID_ruleName);
      
          PROP_ID_TO_NAME[PROP_ID_displayName] = PROP_NAME_displayName;
          PROP_NAME_TO_ID.put(PROP_NAME_displayName, PROP_ID_displayName);
      
          PROP_ID_TO_NAME[PROP_ID_ruleType] = PROP_NAME_ruleType;
          PROP_NAME_TO_ID.put(PROP_NAME_ruleType, PROP_ID_ruleType);
      
          PROP_ID_TO_NAME[PROP_ID_entityType] = PROP_NAME_entityType;
          PROP_NAME_TO_ID.put(PROP_NAME_entityType, PROP_ID_entityType);
      
          PROP_ID_TO_NAME[PROP_ID_entityId] = PROP_NAME_entityId;
          PROP_NAME_TO_ID.put(PROP_NAME_entityId, PROP_ID_entityId);
      
          PROP_ID_TO_NAME[PROP_ID_severity] = PROP_NAME_severity;
          PROP_NAME_TO_ID.put(PROP_NAME_severity, PROP_ID_severity);
      
          PROP_ID_TO_NAME[PROP_ID_sqlExpression] = PROP_NAME_sqlExpression;
          PROP_NAME_TO_ID.put(PROP_NAME_sqlExpression, PROP_ID_sqlExpression);
      
          PROP_ID_TO_NAME[PROP_ID_threshold] = PROP_NAME_threshold;
          PROP_NAME_TO_ID.put(PROP_NAME_threshold, PROP_ID_threshold);
      
          PROP_ID_TO_NAME[PROP_ID_params] = PROP_NAME_params;
          PROP_NAME_TO_ID.put(PROP_NAME_params, PROP_ID_params);
      
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

    
    /* 规则ID: QUALITY_RULE_ID */
    private java.lang.String _qualityRuleId;
    
    /* 规则名: RULE_NAME */
    private java.lang.String _ruleName;
    
    /* 显示名: DISPLAY_NAME */
    private java.lang.String _displayName;
    
    /* 规则类型: RULE_TYPE */
    private java.lang.String _ruleType;
    
    /* 对象类型: ENTITY_TYPE */
    private java.lang.String _entityType;
    
    /* 挂载对象ID: ENTITY_ID */
    private java.lang.String _entityId;
    
    /* 严重级别: SEVERITY */
    private java.lang.String _severity;
    
    /* SQL表达式: SQL_EXPRESSION */
    private java.lang.String _sqlExpression;
    
    /* 阈值: THRESHOLD */
    private java.lang.Double _threshold;
    
    /* 参数: PARAMS */
    private java.lang.String _params;
    
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
    

    public _NopMetaQualityRule(){
        // for debug
    }

    protected NopMetaQualityRule newInstance(){
        NopMetaQualityRule entity = new NopMetaQualityRule();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopMetaQualityRule cloneInstance() {
        NopMetaQualityRule entity = newInstance();
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
      return "io.nop.metadata.dao.entity.NopMetaQualityRule";
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
    
        return buildSimpleId(PROP_ID_qualityRuleId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_qualityRuleId;
          
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
        
            case PROP_ID_qualityRuleId:
               return getQualityRuleId();
        
            case PROP_ID_ruleName:
               return getRuleName();
        
            case PROP_ID_displayName:
               return getDisplayName();
        
            case PROP_ID_ruleType:
               return getRuleType();
        
            case PROP_ID_entityType:
               return getEntityType();
        
            case PROP_ID_entityId:
               return getEntityId();
        
            case PROP_ID_severity:
               return getSeverity();
        
            case PROP_ID_sqlExpression:
               return getSqlExpression();
        
            case PROP_ID_threshold:
               return getThreshold();
        
            case PROP_ID_params:
               return getParams();
        
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
        
            case PROP_ID_qualityRuleId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_qualityRuleId));
               }
               setQualityRuleId(typedValue);
               break;
            }
        
            case PROP_ID_ruleName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_ruleName));
               }
               setRuleName(typedValue);
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
        
            case PROP_ID_ruleType:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_ruleType));
               }
               setRuleType(typedValue);
               break;
            }
        
            case PROP_ID_entityType:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_entityType));
               }
               setEntityType(typedValue);
               break;
            }
        
            case PROP_ID_entityId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_entityId));
               }
               setEntityId(typedValue);
               break;
            }
        
            case PROP_ID_severity:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_severity));
               }
               setSeverity(typedValue);
               break;
            }
        
            case PROP_ID_sqlExpression:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_sqlExpression));
               }
               setSqlExpression(typedValue);
               break;
            }
        
            case PROP_ID_threshold:{
               java.lang.Double typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toDouble(value,
                       err-> newTypeConversionError(PROP_NAME_threshold));
               }
               setThreshold(typedValue);
               break;
            }
        
            case PROP_ID_params:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_params));
               }
               setParams(typedValue);
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
        
            case PROP_ID_qualityRuleId:{
               onInitProp(propId);
               this._qualityRuleId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_ruleName:{
               onInitProp(propId);
               this._ruleName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_displayName:{
               onInitProp(propId);
               this._displayName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_ruleType:{
               onInitProp(propId);
               this._ruleType = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_entityType:{
               onInitProp(propId);
               this._entityType = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_entityId:{
               onInitProp(propId);
               this._entityId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_severity:{
               onInitProp(propId);
               this._severity = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_sqlExpression:{
               onInitProp(propId);
               this._sqlExpression = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_threshold:{
               onInitProp(propId);
               this._threshold = (java.lang.Double)value;
               
               break;
            }
        
            case PROP_ID_params:{
               onInitProp(propId);
               this._params = (java.lang.String)value;
               
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
     * 规则ID: QUALITY_RULE_ID
     */
    public final java.lang.String getQualityRuleId(){
         onPropGet(PROP_ID_qualityRuleId);
         return _qualityRuleId;
    }

    /**
     * 规则ID: QUALITY_RULE_ID
     */
    public final void setQualityRuleId(java.lang.String value){
        if(onPropSet(PROP_ID_qualityRuleId,value)){
            this._qualityRuleId = value;
            internalClearRefs(PROP_ID_qualityRuleId);
            orm_id();
        }
    }
    
    /**
     * 规则名: RULE_NAME
     */
    public final java.lang.String getRuleName(){
         onPropGet(PROP_ID_ruleName);
         return _ruleName;
    }

    /**
     * 规则名: RULE_NAME
     */
    public final void setRuleName(java.lang.String value){
        if(onPropSet(PROP_ID_ruleName,value)){
            this._ruleName = value;
            internalClearRefs(PROP_ID_ruleName);
            
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
     * 规则类型: RULE_TYPE
     */
    public final java.lang.String getRuleType(){
         onPropGet(PROP_ID_ruleType);
         return _ruleType;
    }

    /**
     * 规则类型: RULE_TYPE
     */
    public final void setRuleType(java.lang.String value){
        if(onPropSet(PROP_ID_ruleType,value)){
            this._ruleType = value;
            internalClearRefs(PROP_ID_ruleType);
            
        }
    }
    
    /**
     * 对象类型: ENTITY_TYPE
     */
    public final java.lang.String getEntityType(){
         onPropGet(PROP_ID_entityType);
         return _entityType;
    }

    /**
     * 对象类型: ENTITY_TYPE
     */
    public final void setEntityType(java.lang.String value){
        if(onPropSet(PROP_ID_entityType,value)){
            this._entityType = value;
            internalClearRefs(PROP_ID_entityType);
            
        }
    }
    
    /**
     * 挂载对象ID: ENTITY_ID
     */
    public final java.lang.String getEntityId(){
         onPropGet(PROP_ID_entityId);
         return _entityId;
    }

    /**
     * 挂载对象ID: ENTITY_ID
     */
    public final void setEntityId(java.lang.String value){
        if(onPropSet(PROP_ID_entityId,value)){
            this._entityId = value;
            internalClearRefs(PROP_ID_entityId);
            
        }
    }
    
    /**
     * 严重级别: SEVERITY
     */
    public final java.lang.String getSeverity(){
         onPropGet(PROP_ID_severity);
         return _severity;
    }

    /**
     * 严重级别: SEVERITY
     */
    public final void setSeverity(java.lang.String value){
        if(onPropSet(PROP_ID_severity,value)){
            this._severity = value;
            internalClearRefs(PROP_ID_severity);
            
        }
    }
    
    /**
     * SQL表达式: SQL_EXPRESSION
     */
    public final java.lang.String getSqlExpression(){
         onPropGet(PROP_ID_sqlExpression);
         return _sqlExpression;
    }

    /**
     * SQL表达式: SQL_EXPRESSION
     */
    public final void setSqlExpression(java.lang.String value){
        if(onPropSet(PROP_ID_sqlExpression,value)){
            this._sqlExpression = value;
            internalClearRefs(PROP_ID_sqlExpression);
            
        }
    }
    
    /**
     * 阈值: THRESHOLD
     */
    public final java.lang.Double getThreshold(){
         onPropGet(PROP_ID_threshold);
         return _threshold;
    }

    /**
     * 阈值: THRESHOLD
     */
    public final void setThreshold(java.lang.Double value){
        if(onPropSet(PROP_ID_threshold,value)){
            this._threshold = value;
            internalClearRefs(PROP_ID_threshold);
            
        }
    }
    
    /**
     * 参数: PARAMS
     */
    public final java.lang.String getParams(){
         onPropGet(PROP_ID_params);
         return _params;
    }

    /**
     * 参数: PARAMS
     */
    public final void setParams(java.lang.String value){
        if(onPropSet(PROP_ID_params,value)){
            this._params = value;
            internalClearRefs(PROP_ID_params);
            
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
    
    private final OrmEntitySet<io.nop.metadata.dao.entity.NopMetaQualityResult> _results = new OrmEntitySet<>(this, PROP_NAME_results,
        io.nop.metadata.dao.entity.NopMetaQualityResult.PROP_NAME_qualityRule, null,io.nop.metadata.dao.entity.NopMetaQualityResult.class);

    /**
     * 质量结果集。 refPropName: qualityRule, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<io.nop.metadata.dao.entity.NopMetaQualityResult> getResults(){
       return _results;
    }
       
   private io.nop.orm.component.JsonOrmComponent _paramsComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_paramsComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_paramsComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_params);
      
   }

   public final io.nop.orm.component.JsonOrmComponent getParamsComponent(){
      if(_paramsComponent == null){
          _paramsComponent = new io.nop.orm.component.JsonOrmComponent();
          _paramsComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_paramsComponent);
      }
      return _paramsComponent;
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
