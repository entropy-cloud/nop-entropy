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

import io.nop.metadata.dao.entity.NopMetaTableMeasure;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  表指标: nop_meta_table_measure
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopMetaTableMeasure extends DynamicOrmEntity{
    
    /* 指标ID: MEASURE_ID VARCHAR */
    public static final String PROP_NAME_measureId = "measureId";
    public static final int PROP_ID_measureId = 1;
    
    /* 逻辑表ID: META_TABLE_ID VARCHAR */
    public static final String PROP_NAME_metaTableId = "metaTableId";
    public static final int PROP_ID_metaTableId = 2;
    
    /* 指标名: MEASURE_NAME VARCHAR */
    public static final String PROP_NAME_measureName = "measureName";
    public static final int PROP_ID_measureName = 3;
    
    /* 显示名: DISPLAY_NAME VARCHAR */
    public static final String PROP_NAME_displayName = "displayName";
    public static final int PROP_ID_displayName = 4;
    
    /* 实体字段ID: ENTITY_FIELD_ID VARCHAR */
    public static final String PROP_NAME_entityFieldId = "entityFieldId";
    public static final int PROP_ID_entityFieldId = 5;
    
    /* 聚合函数: AGG_FUNC VARCHAR */
    public static final String PROP_NAME_aggFunc = "aggFunc";
    public static final int PROP_ID_aggFunc = 6;
    
    /* 表达式: EXPRESSION VARCHAR */
    public static final String PROP_NAME_expression = "expression";
    public static final int PROP_ID_expression = 7;
    
    /* 显示格式: FORMAT VARCHAR */
    public static final String PROP_NAME_format = "format";
    public static final int PROP_ID_format = 8;
    
    /* 货币单位: CURRENCY_UNIT VARCHAR */
    public static final String PROP_NAME_currencyUnit = "currencyUnit";
    public static final int PROP_ID_currencyUnit = 9;
    
    /* 描述: DESCRIPTION VARCHAR */
    public static final String PROP_NAME_description = "description";
    public static final int PROP_ID_description = 10;
    
    /* 业务域ID: BUSINESS_DOMAIN_ID VARCHAR */
    public static final String PROP_NAME_businessDomainId = "businessDomainId";
    public static final int PROP_ID_businessDomainId = 11;
    
    /* 扩展配置: EXT_CONFIG VARCHAR */
    public static final String PROP_NAME_extConfig = "extConfig";
    public static final int PROP_ID_extConfig = 12;
    
    /* 数据版本: VERSION BIGINT */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 13;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 14;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 15;
    
    /* 修改人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 16;
    
    /* 修改时间: UPDATE_TIME TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 17;
    
    /* 备注: REMARK VARCHAR */
    public static final String PROP_NAME_remark = "remark";
    public static final int PROP_ID_remark = 18;
    
    /* 侧别: SIDE VARCHAR */
    public static final String PROP_NAME_side = "side";
    public static final int PROP_ID_side = 19;
    

    private static int _PROP_ID_BOUND = 20;

    
    /* relation: 逻辑表 */
    public static final String PROP_NAME_metaTable = "metaTable";
    
    /* component:  */
    public static final String PROP_NAME_extConfigComponent = "extConfigComponent";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_measureId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_measureId};

    private static final String[] PROP_ID_TO_NAME = new String[20];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_measureId] = PROP_NAME_measureId;
          PROP_NAME_TO_ID.put(PROP_NAME_measureId, PROP_ID_measureId);
      
          PROP_ID_TO_NAME[PROP_ID_metaTableId] = PROP_NAME_metaTableId;
          PROP_NAME_TO_ID.put(PROP_NAME_metaTableId, PROP_ID_metaTableId);
      
          PROP_ID_TO_NAME[PROP_ID_measureName] = PROP_NAME_measureName;
          PROP_NAME_TO_ID.put(PROP_NAME_measureName, PROP_ID_measureName);
      
          PROP_ID_TO_NAME[PROP_ID_displayName] = PROP_NAME_displayName;
          PROP_NAME_TO_ID.put(PROP_NAME_displayName, PROP_ID_displayName);
      
          PROP_ID_TO_NAME[PROP_ID_entityFieldId] = PROP_NAME_entityFieldId;
          PROP_NAME_TO_ID.put(PROP_NAME_entityFieldId, PROP_ID_entityFieldId);
      
          PROP_ID_TO_NAME[PROP_ID_aggFunc] = PROP_NAME_aggFunc;
          PROP_NAME_TO_ID.put(PROP_NAME_aggFunc, PROP_ID_aggFunc);
      
          PROP_ID_TO_NAME[PROP_ID_expression] = PROP_NAME_expression;
          PROP_NAME_TO_ID.put(PROP_NAME_expression, PROP_ID_expression);
      
          PROP_ID_TO_NAME[PROP_ID_format] = PROP_NAME_format;
          PROP_NAME_TO_ID.put(PROP_NAME_format, PROP_ID_format);
      
          PROP_ID_TO_NAME[PROP_ID_currencyUnit] = PROP_NAME_currencyUnit;
          PROP_NAME_TO_ID.put(PROP_NAME_currencyUnit, PROP_ID_currencyUnit);
      
          PROP_ID_TO_NAME[PROP_ID_description] = PROP_NAME_description;
          PROP_NAME_TO_ID.put(PROP_NAME_description, PROP_ID_description);
      
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
      
          PROP_ID_TO_NAME[PROP_ID_side] = PROP_NAME_side;
          PROP_NAME_TO_ID.put(PROP_NAME_side, PROP_ID_side);
      
    }

    
    /* 指标ID: MEASURE_ID */
    private java.lang.String _measureId;
    
    /* 逻辑表ID: META_TABLE_ID */
    private java.lang.String _metaTableId;
    
    /* 指标名: MEASURE_NAME */
    private java.lang.String _measureName;
    
    /* 显示名: DISPLAY_NAME */
    private java.lang.String _displayName;
    
    /* 实体字段ID: ENTITY_FIELD_ID */
    private java.lang.String _entityFieldId;
    
    /* 聚合函数: AGG_FUNC */
    private java.lang.String _aggFunc;
    
    /* 表达式: EXPRESSION */
    private java.lang.String _expression;
    
    /* 显示格式: FORMAT */
    private java.lang.String _format;
    
    /* 货币单位: CURRENCY_UNIT */
    private java.lang.String _currencyUnit;
    
    /* 描述: DESCRIPTION */
    private java.lang.String _description;
    
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
    
    /* 侧别: SIDE */
    private java.lang.String _side;
    

    public _NopMetaTableMeasure(){
        // for debug
    }

    protected NopMetaTableMeasure newInstance(){
        NopMetaTableMeasure entity = new NopMetaTableMeasure();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopMetaTableMeasure cloneInstance() {
        NopMetaTableMeasure entity = newInstance();
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
      return "io.nop.metadata.dao.entity.NopMetaTableMeasure";
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
    
        return buildSimpleId(PROP_ID_measureId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_measureId;
          
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
        
            case PROP_ID_measureId:
               return getMeasureId();
        
            case PROP_ID_metaTableId:
               return getMetaTableId();
        
            case PROP_ID_measureName:
               return getMeasureName();
        
            case PROP_ID_displayName:
               return getDisplayName();
        
            case PROP_ID_entityFieldId:
               return getEntityFieldId();
        
            case PROP_ID_aggFunc:
               return getAggFunc();
        
            case PROP_ID_expression:
               return getExpression();
        
            case PROP_ID_format:
               return getFormat();
        
            case PROP_ID_currencyUnit:
               return getCurrencyUnit();
        
            case PROP_ID_description:
               return getDescription();
        
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
        
            case PROP_ID_side:
               return getSide();
        
           default:
              return super.orm_propValue(propId);
        }
    }

    

    @Override
    public void orm_propValue(int propId, Object value){
        switch(propId){
        
            case PROP_ID_measureId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_measureId));
               }
               setMeasureId(typedValue);
               break;
            }
        
            case PROP_ID_metaTableId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_metaTableId));
               }
               setMetaTableId(typedValue);
               break;
            }
        
            case PROP_ID_measureName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_measureName));
               }
               setMeasureName(typedValue);
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
        
            case PROP_ID_entityFieldId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_entityFieldId));
               }
               setEntityFieldId(typedValue);
               break;
            }
        
            case PROP_ID_aggFunc:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_aggFunc));
               }
               setAggFunc(typedValue);
               break;
            }
        
            case PROP_ID_expression:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_expression));
               }
               setExpression(typedValue);
               break;
            }
        
            case PROP_ID_format:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_format));
               }
               setFormat(typedValue);
               break;
            }
        
            case PROP_ID_currencyUnit:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_currencyUnit));
               }
               setCurrencyUnit(typedValue);
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
        
            case PROP_ID_side:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_side));
               }
               setSide(typedValue);
               break;
            }
        
           default:
              super.orm_propValue(propId,value);
        }
    }

    @Override
    public void orm_internalSet(int propId, Object value) {
        switch(propId){
        
            case PROP_ID_measureId:{
               onInitProp(propId);
               this._measureId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_metaTableId:{
               onInitProp(propId);
               this._metaTableId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_measureName:{
               onInitProp(propId);
               this._measureName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_displayName:{
               onInitProp(propId);
               this._displayName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_entityFieldId:{
               onInitProp(propId);
               this._entityFieldId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_aggFunc:{
               onInitProp(propId);
               this._aggFunc = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_expression:{
               onInitProp(propId);
               this._expression = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_format:{
               onInitProp(propId);
               this._format = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_currencyUnit:{
               onInitProp(propId);
               this._currencyUnit = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_description:{
               onInitProp(propId);
               this._description = (java.lang.String)value;
               
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
        
            case PROP_ID_side:{
               onInitProp(propId);
               this._side = (java.lang.String)value;
               
               break;
            }
        
           default:
              super.orm_internalSet(propId,value);
        }
    }

    
    /**
     * 指标ID: MEASURE_ID
     */
    public final java.lang.String getMeasureId(){
         onPropGet(PROP_ID_measureId);
         return _measureId;
    }

    /**
     * 指标ID: MEASURE_ID
     */
    public final void setMeasureId(java.lang.String value){
        if(onPropSet(PROP_ID_measureId,value)){
            this._measureId = value;
            internalClearRefs(PROP_ID_measureId);
            orm_id();
        }
    }
    
    /**
     * 逻辑表ID: META_TABLE_ID
     */
    public final java.lang.String getMetaTableId(){
         onPropGet(PROP_ID_metaTableId);
         return _metaTableId;
    }

    /**
     * 逻辑表ID: META_TABLE_ID
     */
    public final void setMetaTableId(java.lang.String value){
        if(onPropSet(PROP_ID_metaTableId,value)){
            this._metaTableId = value;
            internalClearRefs(PROP_ID_metaTableId);
            
        }
    }
    
    /**
     * 指标名: MEASURE_NAME
     */
    public final java.lang.String getMeasureName(){
         onPropGet(PROP_ID_measureName);
         return _measureName;
    }

    /**
     * 指标名: MEASURE_NAME
     */
    public final void setMeasureName(java.lang.String value){
        if(onPropSet(PROP_ID_measureName,value)){
            this._measureName = value;
            internalClearRefs(PROP_ID_measureName);
            
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
     * 实体字段ID: ENTITY_FIELD_ID
     */
    public final java.lang.String getEntityFieldId(){
         onPropGet(PROP_ID_entityFieldId);
         return _entityFieldId;
    }

    /**
     * 实体字段ID: ENTITY_FIELD_ID
     */
    public final void setEntityFieldId(java.lang.String value){
        if(onPropSet(PROP_ID_entityFieldId,value)){
            this._entityFieldId = value;
            internalClearRefs(PROP_ID_entityFieldId);
            
        }
    }
    
    /**
     * 聚合函数: AGG_FUNC
     */
    public final java.lang.String getAggFunc(){
         onPropGet(PROP_ID_aggFunc);
         return _aggFunc;
    }

    /**
     * 聚合函数: AGG_FUNC
     */
    public final void setAggFunc(java.lang.String value){
        if(onPropSet(PROP_ID_aggFunc,value)){
            this._aggFunc = value;
            internalClearRefs(PROP_ID_aggFunc);
            
        }
    }
    
    /**
     * 表达式: EXPRESSION
     */
    public final java.lang.String getExpression(){
         onPropGet(PROP_ID_expression);
         return _expression;
    }

    /**
     * 表达式: EXPRESSION
     */
    public final void setExpression(java.lang.String value){
        if(onPropSet(PROP_ID_expression,value)){
            this._expression = value;
            internalClearRefs(PROP_ID_expression);
            
        }
    }
    
    /**
     * 显示格式: FORMAT
     */
    public final java.lang.String getFormat(){
         onPropGet(PROP_ID_format);
         return _format;
    }

    /**
     * 显示格式: FORMAT
     */
    public final void setFormat(java.lang.String value){
        if(onPropSet(PROP_ID_format,value)){
            this._format = value;
            internalClearRefs(PROP_ID_format);
            
        }
    }
    
    /**
     * 货币单位: CURRENCY_UNIT
     */
    public final java.lang.String getCurrencyUnit(){
         onPropGet(PROP_ID_currencyUnit);
         return _currencyUnit;
    }

    /**
     * 货币单位: CURRENCY_UNIT
     */
    public final void setCurrencyUnit(java.lang.String value){
        if(onPropSet(PROP_ID_currencyUnit,value)){
            this._currencyUnit = value;
            internalClearRefs(PROP_ID_currencyUnit);
            
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
     * 侧别: SIDE
     */
    public final java.lang.String getSide(){
         onPropGet(PROP_ID_side);
         return _side;
    }

    /**
     * 侧别: SIDE
     */
    public final void setSide(java.lang.String value){
        if(onPropSet(PROP_ID_side,value)){
            this._side = value;
            internalClearRefs(PROP_ID_side);
            
        }
    }
    
    /**
     * 逻辑表
     */
    public final io.nop.metadata.dao.entity.NopMetaTable getMetaTable(){
       return (io.nop.metadata.dao.entity.NopMetaTable)internalGetRefEntity(PROP_NAME_metaTable);
    }

    public final void setMetaTable(io.nop.metadata.dao.entity.NopMetaTable refEntity){
   
           if(refEntity == null){
           
                   this.setMetaTableId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_metaTable, refEntity,()->{
           
                           this.setMetaTableId(refEntity.getMetaTableId());
                       
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
