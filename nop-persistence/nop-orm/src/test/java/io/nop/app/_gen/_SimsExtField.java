package io.nop.app._gen;

import io.nop.orm.model.IEntityModel;
import io.nop.orm.support.AbstractOrmKeyValueTable;
import io.nop.orm.support.OrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.orm.IOrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.api.core.convert.ConvertHelper;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;

import io.nop.app.SimsExtField;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  : SIMS_EXT_FIELD
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _SimsExtField extends AbstractOrmKeyValueTable{
    
    /* : ENTITY_NAME VARCHAR */
    public static final String PROP_NAME_entityName = "entityName";
    public static final int PROP_ID_entityName = 1;
    
    /* : ENTITY_ID VARCHAR */
    public static final String PROP_NAME_entityId = "entityId";
    public static final int PROP_ID_entityId = 2;
    
    /* : FIELD_NAME VARCHAR */
    public static final String PROP_NAME_fieldName = "fieldName";
    public static final int PROP_ID_fieldName = 3;
    
    /* : FIELD_TYPE INTEGER */
    public static final String PROP_NAME_fieldType = "fieldType";
    public static final int PROP_ID_fieldType = 4;
    
    /* : DECIMAL_SCALE TINYINT */
    public static final String PROP_NAME_decimalScale = "decimalScale";
    public static final int PROP_ID_decimalScale = 5;
    
    /* : DECIMAL_VALUE DECIMAL */
    public static final String PROP_NAME_decimalValue = "decimalValue";
    public static final int PROP_ID_decimalValue = 6;
    
    /* : DATE_VALUE DATE */
    public static final String PROP_NAME_dateValue = "dateValue";
    public static final int PROP_ID_dateValue = 7;
    
    /* : TIMESTAMP_VALUE TIMESTAMP */
    public static final String PROP_NAME_timestampValue = "timestampValue";
    public static final int PROP_ID_timestampValue = 8;
    
    /* : STRING_VALUE VARCHAR */
    public static final String PROP_NAME_stringValue = "stringValue";
    public static final int PROP_ID_stringValue = 9;
    
    /* : NOP_TENANT_ID VARCHAR */
    public static final String PROP_NAME_nopTenantId = "nopTenantId";
    public static final int PROP_ID_nopTenantId = 10;
    

    private static int _PROP_ID_BOUND = 11;

    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_entityName,PROP_NAME_entityId,PROP_NAME_fieldName);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_entityName,PROP_ID_entityId,PROP_ID_fieldName};

    private static final String[] PROP_ID_TO_NAME = new String[11];
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
      
          PROP_ID_TO_NAME[PROP_ID_nopTenantId] = PROP_NAME_nopTenantId;
          PROP_NAME_TO_ID.put(PROP_NAME_nopTenantId, PROP_ID_nopTenantId);
      
    }

    
    /* : ENTITY_NAME */
    private java.lang.String _entityName;
    
    /* : ENTITY_ID */
    private java.lang.String _entityId;
    
    /* : FIELD_NAME */
    private java.lang.String _fieldName;
    
    /* : FIELD_TYPE */
    private java.lang.Integer _fieldType;
    
    /* : DECIMAL_SCALE */
    private java.lang.Byte _decimalScale;
    
    /* : DECIMAL_VALUE */
    private java.math.BigDecimal _decimalValue;
    
    /* : DATE_VALUE */
    private java.time.LocalDate _dateValue;
    
    /* : TIMESTAMP_VALUE */
    private java.sql.Timestamp _timestampValue;
    
    /* : STRING_VALUE */
    private java.lang.String _stringValue;
    
    /* : NOP_TENANT_ID */
    private java.lang.String _nopTenantId;
    

    public _SimsExtField(){
        // for debug
    }

    protected SimsExtField newInstance(){
       return new SimsExtField();
    }

    @Override
    public SimsExtField cloneInstance() {
        SimsExtField entity = newInstance();
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
      return "io.nop.app.SimsExtField";
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
        
            case PROP_ID_nopTenantId:
               return getNopTenantId();
        
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
        
            case PROP_ID_nopTenantId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_nopTenantId));
               }
               setNopTenantId(typedValue);
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
        
            case PROP_ID_nopTenantId:{
               onInitProp(propId);
               this._nopTenantId = (java.lang.String)value;
               
               break;
            }
        
           default:
              super.orm_internalSet(propId,value);
        }
    }

    
    /**
     * : ENTITY_NAME
     */
    public java.lang.String getEntityName(){
         onPropGet(PROP_ID_entityName);
         return _entityName;
    }

    /**
     * : ENTITY_NAME
     */
    public void setEntityName(java.lang.String value){
        if(onPropSet(PROP_ID_entityName,value)){
            this._entityName = value;
            internalClearRefs(PROP_ID_entityName);
            orm_id();
        }
    }
    
    /**
     * : ENTITY_ID
     */
    public java.lang.String getEntityId(){
         onPropGet(PROP_ID_entityId);
         return _entityId;
    }

    /**
     * : ENTITY_ID
     */
    public void setEntityId(java.lang.String value){
        if(onPropSet(PROP_ID_entityId,value)){
            this._entityId = value;
            internalClearRefs(PROP_ID_entityId);
            orm_id();
        }
    }
    
    /**
     * : FIELD_NAME
     */
    public java.lang.String getFieldName(){
         onPropGet(PROP_ID_fieldName);
         return _fieldName;
    }

    /**
     * : FIELD_NAME
     */
    public void setFieldName(java.lang.String value){
        if(onPropSet(PROP_ID_fieldName,value)){
            this._fieldName = value;
            internalClearRefs(PROP_ID_fieldName);
            orm_id();
        }
    }
    
    /**
     * : FIELD_TYPE
     */
    public java.lang.Integer getFieldType(){
         onPropGet(PROP_ID_fieldType);
         return _fieldType;
    }

    /**
     * : FIELD_TYPE
     */
    public void setFieldType(java.lang.Integer value){
        if(onPropSet(PROP_ID_fieldType,value)){
            this._fieldType = value;
            internalClearRefs(PROP_ID_fieldType);
            
        }
    }
    
    /**
     * : DECIMAL_SCALE
     */
    public java.lang.Byte getDecimalScale(){
         onPropGet(PROP_ID_decimalScale);
         return _decimalScale;
    }

    /**
     * : DECIMAL_SCALE
     */
    public void setDecimalScale(java.lang.Byte value){
        if(onPropSet(PROP_ID_decimalScale,value)){
            this._decimalScale = value;
            internalClearRefs(PROP_ID_decimalScale);
            
        }
    }
    
    /**
     * : DECIMAL_VALUE
     */
    public java.math.BigDecimal getDecimalValue(){
         onPropGet(PROP_ID_decimalValue);
         return _decimalValue;
    }

    /**
     * : DECIMAL_VALUE
     */
    public void setDecimalValue(java.math.BigDecimal value){
        if(onPropSet(PROP_ID_decimalValue,value)){
            this._decimalValue = value;
            internalClearRefs(PROP_ID_decimalValue);
            
        }
    }
    
    /**
     * : DATE_VALUE
     */
    public java.time.LocalDate getDateValue(){
         onPropGet(PROP_ID_dateValue);
         return _dateValue;
    }

    /**
     * : DATE_VALUE
     */
    public void setDateValue(java.time.LocalDate value){
        if(onPropSet(PROP_ID_dateValue,value)){
            this._dateValue = value;
            internalClearRefs(PROP_ID_dateValue);
            
        }
    }
    
    /**
     * : TIMESTAMP_VALUE
     */
    public java.sql.Timestamp getTimestampValue(){
         onPropGet(PROP_ID_timestampValue);
         return _timestampValue;
    }

    /**
     * : TIMESTAMP_VALUE
     */
    public void setTimestampValue(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_timestampValue,value)){
            this._timestampValue = value;
            internalClearRefs(PROP_ID_timestampValue);
            
        }
    }
    
    /**
     * : STRING_VALUE
     */
    public java.lang.String getStringValue(){
         onPropGet(PROP_ID_stringValue);
         return _stringValue;
    }

    /**
     * : STRING_VALUE
     */
    public void setStringValue(java.lang.String value){
        if(onPropSet(PROP_ID_stringValue,value)){
            this._stringValue = value;
            internalClearRefs(PROP_ID_stringValue);
            
        }
    }
    
    /**
     * : NOP_TENANT_ID
     */
    public java.lang.String getNopTenantId(){
         onPropGet(PROP_ID_nopTenantId);
         return _nopTenantId;
    }

    /**
     * : NOP_TENANT_ID
     */
    public void setNopTenantId(java.lang.String value){
        if(onPropSet(PROP_ID_nopTenantId,value)){
            this._nopTenantId = value;
            internalClearRefs(PROP_ID_nopTenantId);
            
        }
    }
    
}
// resume CPD analysis - CPD-ON
