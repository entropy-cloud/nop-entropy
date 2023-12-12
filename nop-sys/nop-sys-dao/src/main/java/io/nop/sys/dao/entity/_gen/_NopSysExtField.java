package io.nop.sys.dao.entity._gen;

import io.nop.orm.model.IEntityModel;
import io.nop.orm.support.AbstractOrmKeyValueTable;
import io.nop.orm.support.OrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.orm.IOrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.api.core.convert.ConvertHelper;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;

import io.nop.sys.dao.entity.NopSysExtField;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  扩展字段: nop_sys_ext_field
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101"})
public class _NopSysExtField extends AbstractOrmKeyValueTable{
    
    /* 实体名: ENTITY_NAME VARCHAR */
    public static final String PROP_NAME_entityName = "entityName";
    public static final int PROP_ID_entityName = 1;
    
    /* 实体ID: ENTITY_ID VARCHAR */
    public static final String PROP_NAME_entityId = "entityId";
    public static final int PROP_ID_entityId = 2;
    
    /* 字段名: FIELD_NAME VARCHAR */
    public static final String PROP_NAME_fieldName = "fieldName";
    public static final int PROP_ID_fieldName = 3;
    
    /* 字段类型: FIELD_TYPE INTEGER */
    public static final String PROP_NAME_fieldType = "fieldType";
    public static final int PROP_ID_fieldType = 4;
    
    /* 浮点精度: DECIMAL_SCALE TINYINT */
    public static final String PROP_NAME_decimalScale = "decimalScale";
    public static final int PROP_ID_decimalScale = 5;
    
    /* 浮点值: DECIMAL_VALUE DECIMAL */
    public static final String PROP_NAME_decimalValue = "decimalValue";
    public static final int PROP_ID_decimalValue = 6;
    
    /* 日期值: DATE_VALUE DATE */
    public static final String PROP_NAME_dateValue = "dateValue";
    public static final int PROP_ID_dateValue = 7;
    
    /* 时间点值: TIMESTAMP_VALUE TIMESTAMP */
    public static final String PROP_NAME_timestampValue = "timestampValue";
    public static final int PROP_ID_timestampValue = 8;
    
    /* 字符串值: STRING_VALUE VARCHAR */
    public static final String PROP_NAME_stringValue = "stringValue";
    public static final int PROP_ID_stringValue = 9;
    
    /* 数据版本: VERSION INTEGER */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 10;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 11;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 12;
    
    /* 修改人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 13;
    
    /* 修改时间: UPDATE_TIME TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 14;
    
    /* 备注: REMARK VARCHAR */
    public static final String PROP_NAME_remark = "remark";
    public static final int PROP_ID_remark = 15;
    

    private static int _PROP_ID_BOUND = 16;

    

    public static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_entityName,PROP_NAME_entityId,PROP_NAME_fieldName);
    public static final int[] PK_PROP_IDS = new int[]{PROP_ID_entityName,PROP_ID_entityId,PROP_ID_fieldName};

    private static final String[] PROP_ID_TO_NAME = new String[16];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_entityName] = PROP_NAME_entityName;
          PROP_NAME_TO_ID.put(PROP_NAME_entityName, PROP_ID_entityName);
      
          PROP_ID_TO_NAME[PROP_ID_entityId] = PROP_NAME_entityId;
          PROP_NAME_TO_ID.put(PROP_NAME_entityId, PROP_ID_entityId);
      
          PROP_ID_TO_NAME[PROP_ID_fieldName] = PROP_NAME_fieldName;
          PROP_NAME_TO_ID.put(PROP_NAME_fieldName, PROP_ID_fieldName);
      
          PROP_ID_TO_NAME[PROP_ID_fieldType] = PROP_NAME_fieldType;
          PROP_NAME_TO_ID.put(PROP_NAME_fieldType, PROP_ID_fieldType);
      
          PROP_ID_TO_NAME[PROP_ID_decimalScale] = PROP_NAME_decimalScale;
          PROP_NAME_TO_ID.put(PROP_NAME_decimalScale, PROP_ID_decimalScale);
      
          PROP_ID_TO_NAME[PROP_ID_decimalValue] = PROP_NAME_decimalValue;
          PROP_NAME_TO_ID.put(PROP_NAME_decimalValue, PROP_ID_decimalValue);
      
          PROP_ID_TO_NAME[PROP_ID_dateValue] = PROP_NAME_dateValue;
          PROP_NAME_TO_ID.put(PROP_NAME_dateValue, PROP_ID_dateValue);
      
          PROP_ID_TO_NAME[PROP_ID_timestampValue] = PROP_NAME_timestampValue;
          PROP_NAME_TO_ID.put(PROP_NAME_timestampValue, PROP_ID_timestampValue);
      
          PROP_ID_TO_NAME[PROP_ID_stringValue] = PROP_NAME_stringValue;
          PROP_NAME_TO_ID.put(PROP_NAME_stringValue, PROP_ID_stringValue);
      
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

    
    /* 实体名: ENTITY_NAME */
    private java.lang.String _entityName;
    
    /* 实体ID: ENTITY_ID */
    private java.lang.String _entityId;
    
    /* 字段名: FIELD_NAME */
    private java.lang.String _fieldName;
    
    /* 字段类型: FIELD_TYPE */
    private java.lang.Integer _fieldType;
    
    /* 浮点精度: DECIMAL_SCALE */
    private java.lang.Byte _decimalScale;
    
    /* 浮点值: DECIMAL_VALUE */
    private java.math.BigDecimal _decimalValue;
    
    /* 日期值: DATE_VALUE */
    private java.time.LocalDate _dateValue;
    
    /* 时间点值: TIMESTAMP_VALUE */
    private java.sql.Timestamp _timestampValue;
    
    /* 字符串值: STRING_VALUE */
    private java.lang.String _stringValue;
    
    /* 数据版本: VERSION */
    private java.lang.Integer _version;
    
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
    

    public _NopSysExtField(){
    }

    protected NopSysExtField newInstance(){
       return new NopSysExtField();
    }

    @Override
    public NopSysExtField cloneInstance() {
        NopSysExtField entity = newInstance();
        orm_forEachInitedProp((value, propId) -> {
            entity.onInitProp(propId);
        });
        return entity;
    }

    @Override
    public String orm_entityName() {
      // 如果存在实体模型对象，则以模型对象上的设置为准
      IEntityModel entityModel = orm_entityModel();
      if(entityModel != null)
          return entityModel.getName();
      return "io.nop.sys.dao.entity.NopSysExtField";
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
    
        return buildCompositeId(PK_PROP_NAMES,PK_PROP_IDS);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_entityName || propId == PROP_ID_entityId || propId == PROP_ID_fieldName;
          
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
        
            case PROP_ID_entityName:
               return getEntityName();
        
            case PROP_ID_entityId:
               return getEntityId();
        
            case PROP_ID_fieldName:
               return getFieldName();
        
            case PROP_ID_fieldType:
               return getFieldType();
        
            case PROP_ID_decimalScale:
               return getDecimalScale();
        
            case PROP_ID_decimalValue:
               return getDecimalValue();
        
            case PROP_ID_dateValue:
               return getDateValue();
        
            case PROP_ID_timestampValue:
               return getTimestampValue();
        
            case PROP_ID_stringValue:
               return getStringValue();
        
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
        
            case PROP_ID_entityName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_entityName));
               }
               setEntityName(typedValue);
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
        
            case PROP_ID_fieldName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_fieldName));
               }
               setFieldName(typedValue);
               break;
            }
        
            case PROP_ID_fieldType:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_fieldType));
               }
               setFieldType(typedValue);
               break;
            }
        
            case PROP_ID_decimalScale:{
               java.lang.Byte typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toByte(value,
                       err-> newTypeConversionError(PROP_NAME_decimalScale));
               }
               setDecimalScale(typedValue);
               break;
            }
        
            case PROP_ID_decimalValue:{
               java.math.BigDecimal typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toBigDecimal(value,
                       err-> newTypeConversionError(PROP_NAME_decimalValue));
               }
               setDecimalValue(typedValue);
               break;
            }
        
            case PROP_ID_dateValue:{
               java.time.LocalDate typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLocalDate(value,
                       err-> newTypeConversionError(PROP_NAME_dateValue));
               }
               setDateValue(typedValue);
               break;
            }
        
            case PROP_ID_timestampValue:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_timestampValue));
               }
               setTimestampValue(typedValue);
               break;
            }
        
            case PROP_ID_stringValue:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_stringValue));
               }
               setStringValue(typedValue);
               break;
            }
        
            case PROP_ID_version:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
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
        
            case PROP_ID_entityName:{
               onInitProp(propId);
               this._entityName = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_entityId:{
               onInitProp(propId);
               this._entityId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_fieldName:{
               onInitProp(propId);
               this._fieldName = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_fieldType:{
               onInitProp(propId);
               this._fieldType = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_decimalScale:{
               onInitProp(propId);
               this._decimalScale = (java.lang.Byte)value;
               
               break;
            }
        
            case PROP_ID_decimalValue:{
               onInitProp(propId);
               this._decimalValue = (java.math.BigDecimal)value;
               
               break;
            }
        
            case PROP_ID_dateValue:{
               onInitProp(propId);
               this._dateValue = (java.time.LocalDate)value;
               
               break;
            }
        
            case PROP_ID_timestampValue:{
               onInitProp(propId);
               this._timestampValue = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_stringValue:{
               onInitProp(propId);
               this._stringValue = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_version:{
               onInitProp(propId);
               this._version = (java.lang.Integer)value;
               
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
     * 实体名: ENTITY_NAME
     */
    public java.lang.String getEntityName(){
         onPropGet(PROP_ID_entityName);
         return _entityName;
    }

    /**
     * 实体名: ENTITY_NAME
     */
    public void setEntityName(java.lang.String value){
        if(onPropSet(PROP_ID_entityName,value)){
            this._entityName = value;
            internalClearRefs(PROP_ID_entityName);
            orm_id();
        }
    }
    
    /**
     * 实体ID: ENTITY_ID
     */
    public java.lang.String getEntityId(){
         onPropGet(PROP_ID_entityId);
         return _entityId;
    }

    /**
     * 实体ID: ENTITY_ID
     */
    public void setEntityId(java.lang.String value){
        if(onPropSet(PROP_ID_entityId,value)){
            this._entityId = value;
            internalClearRefs(PROP_ID_entityId);
            orm_id();
        }
    }
    
    /**
     * 字段名: FIELD_NAME
     */
    public java.lang.String getFieldName(){
         onPropGet(PROP_ID_fieldName);
         return _fieldName;
    }

    /**
     * 字段名: FIELD_NAME
     */
    public void setFieldName(java.lang.String value){
        if(onPropSet(PROP_ID_fieldName,value)){
            this._fieldName = value;
            internalClearRefs(PROP_ID_fieldName);
            orm_id();
        }
    }
    
    /**
     * 字段类型: FIELD_TYPE
     */
    public java.lang.Integer getFieldType(){
         onPropGet(PROP_ID_fieldType);
         return _fieldType;
    }

    /**
     * 字段类型: FIELD_TYPE
     */
    public void setFieldType(java.lang.Integer value){
        if(onPropSet(PROP_ID_fieldType,value)){
            this._fieldType = value;
            internalClearRefs(PROP_ID_fieldType);
            
        }
    }
    
    /**
     * 浮点精度: DECIMAL_SCALE
     */
    public java.lang.Byte getDecimalScale(){
         onPropGet(PROP_ID_decimalScale);
         return _decimalScale;
    }

    /**
     * 浮点精度: DECIMAL_SCALE
     */
    public void setDecimalScale(java.lang.Byte value){
        if(onPropSet(PROP_ID_decimalScale,value)){
            this._decimalScale = value;
            internalClearRefs(PROP_ID_decimalScale);
            
        }
    }
    
    /**
     * 浮点值: DECIMAL_VALUE
     */
    public java.math.BigDecimal getDecimalValue(){
         onPropGet(PROP_ID_decimalValue);
         return _decimalValue;
    }

    /**
     * 浮点值: DECIMAL_VALUE
     */
    public void setDecimalValue(java.math.BigDecimal value){
        if(onPropSet(PROP_ID_decimalValue,value)){
            this._decimalValue = value;
            internalClearRefs(PROP_ID_decimalValue);
            
        }
    }
    
    /**
     * 日期值: DATE_VALUE
     */
    public java.time.LocalDate getDateValue(){
         onPropGet(PROP_ID_dateValue);
         return _dateValue;
    }

    /**
     * 日期值: DATE_VALUE
     */
    public void setDateValue(java.time.LocalDate value){
        if(onPropSet(PROP_ID_dateValue,value)){
            this._dateValue = value;
            internalClearRefs(PROP_ID_dateValue);
            
        }
    }
    
    /**
     * 时间点值: TIMESTAMP_VALUE
     */
    public java.sql.Timestamp getTimestampValue(){
         onPropGet(PROP_ID_timestampValue);
         return _timestampValue;
    }

    /**
     * 时间点值: TIMESTAMP_VALUE
     */
    public void setTimestampValue(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_timestampValue,value)){
            this._timestampValue = value;
            internalClearRefs(PROP_ID_timestampValue);
            
        }
    }
    
    /**
     * 字符串值: STRING_VALUE
     */
    public java.lang.String getStringValue(){
         onPropGet(PROP_ID_stringValue);
         return _stringValue;
    }

    /**
     * 字符串值: STRING_VALUE
     */
    public void setStringValue(java.lang.String value){
        if(onPropSet(PROP_ID_stringValue,value)){
            this._stringValue = value;
            internalClearRefs(PROP_ID_stringValue);
            
        }
    }
    
    /**
     * 数据版本: VERSION
     */
    public java.lang.Integer getVersion(){
         onPropGet(PROP_ID_version);
         return _version;
    }

    /**
     * 数据版本: VERSION
     */
    public void setVersion(java.lang.Integer value){
        if(onPropSet(PROP_ID_version,value)){
            this._version = value;
            internalClearRefs(PROP_ID_version);
            
        }
    }
    
    /**
     * 创建人: CREATED_BY
     */
    public java.lang.String getCreatedBy(){
         onPropGet(PROP_ID_createdBy);
         return _createdBy;
    }

    /**
     * 创建人: CREATED_BY
     */
    public void setCreatedBy(java.lang.String value){
        if(onPropSet(PROP_ID_createdBy,value)){
            this._createdBy = value;
            internalClearRefs(PROP_ID_createdBy);
            
        }
    }
    
    /**
     * 创建时间: CREATE_TIME
     */
    public java.sql.Timestamp getCreateTime(){
         onPropGet(PROP_ID_createTime);
         return _createTime;
    }

    /**
     * 创建时间: CREATE_TIME
     */
    public void setCreateTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_createTime,value)){
            this._createTime = value;
            internalClearRefs(PROP_ID_createTime);
            
        }
    }
    
    /**
     * 修改人: UPDATED_BY
     */
    public java.lang.String getUpdatedBy(){
         onPropGet(PROP_ID_updatedBy);
         return _updatedBy;
    }

    /**
     * 修改人: UPDATED_BY
     */
    public void setUpdatedBy(java.lang.String value){
        if(onPropSet(PROP_ID_updatedBy,value)){
            this._updatedBy = value;
            internalClearRefs(PROP_ID_updatedBy);
            
        }
    }
    
    /**
     * 修改时间: UPDATE_TIME
     */
    public java.sql.Timestamp getUpdateTime(){
         onPropGet(PROP_ID_updateTime);
         return _updateTime;
    }

    /**
     * 修改时间: UPDATE_TIME
     */
    public void setUpdateTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_updateTime,value)){
            this._updateTime = value;
            internalClearRefs(PROP_ID_updateTime);
            
        }
    }
    
    /**
     * 备注: REMARK
     */
    public java.lang.String getRemark(){
         onPropGet(PROP_ID_remark);
         return _remark;
    }

    /**
     * 备注: REMARK
     */
    public void setRemark(java.lang.String value){
        if(onPropSet(PROP_ID_remark,value)){
            this._remark = value;
            internalClearRefs(PROP_ID_remark);
            
        }
    }
    
}
// resume CPD analysis - CPD-ON
