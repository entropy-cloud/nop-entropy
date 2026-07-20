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

import io.nop.metadata.dao.entity.NopMetaQualityResult;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  质量结果: nop_meta_quality_result
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopMetaQualityResult extends DynamicOrmEntity{
    
    /* 结果ID: QUALITY_RESULT_ID VARCHAR */
    public static final String PROP_NAME_qualityResultId = "qualityResultId";
    public static final int PROP_ID_qualityResultId = 1;
    
    /* 规则ID: QUALITY_RULE_ID VARCHAR */
    public static final String PROP_NAME_qualityRuleId = "qualityRuleId";
    public static final int PROP_ID_qualityRuleId = 2;
    
    /* 执行时间: EXECUTE_TIME TIMESTAMP */
    public static final String PROP_NAME_executeTime = "executeTime";
    public static final int PROP_ID_executeTime = 3;
    
    /* 状态: STATUS VARCHAR */
    public static final String PROP_NAME_status = "status";
    public static final int PROP_ID_status = 4;
    
    /* 实际值: ACTUAL_VALUE DOUBLE */
    public static final String PROP_NAME_actualValue = "actualValue";
    public static final int PROP_ID_actualValue = 5;
    
    /* 期望值: EXPECTED_VALUE DOUBLE */
    public static final String PROP_NAME_expectedValue = "expectedValue";
    public static final int PROP_ID_expectedValue = 6;
    
    /* 结果描述: MESSAGE VARCHAR */
    public static final String PROP_NAME_message = "message";
    public static final int PROP_ID_message = 7;
    
    /* 详情: DETAILS VARCHAR */
    public static final String PROP_NAME_details = "details";
    public static final int PROP_ID_details = 8;
    
    /* 数据版本: VERSION BIGINT */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 9;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 10;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 11;
    
    /* 修改人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 12;
    
    /* 修改时间: UPDATE_TIME TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 13;
    
    /* 备注: REMARK VARCHAR */
    public static final String PROP_NAME_remark = "remark";
    public static final int PROP_ID_remark = 14;
    
    /* 是否误报: IS_FALSE_POSITIVE TINYINT */
    public static final String PROP_NAME_isFalsePositive = "isFalsePositive";
    public static final int PROP_ID_isFalsePositive = 15;
    

    private static int _PROP_ID_BOUND = 16;

    
    /* relation: 质量规则 */
    public static final String PROP_NAME_qualityRule = "qualityRule";
    
    /* component:  */
    public static final String PROP_NAME_detailsComponent = "detailsComponent";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_qualityResultId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_qualityResultId};

    private static final String[] PROP_ID_TO_NAME = new String[16];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_qualityResultId] = PROP_NAME_qualityResultId;
          PROP_NAME_TO_ID.put(PROP_NAME_qualityResultId, PROP_ID_qualityResultId);
      
          PROP_ID_TO_NAME[PROP_ID_qualityRuleId] = PROP_NAME_qualityRuleId;
          PROP_NAME_TO_ID.put(PROP_NAME_qualityRuleId, PROP_ID_qualityRuleId);
      
          PROP_ID_TO_NAME[PROP_ID_executeTime] = PROP_NAME_executeTime;
          PROP_NAME_TO_ID.put(PROP_NAME_executeTime, PROP_ID_executeTime);
      
          PROP_ID_TO_NAME[PROP_ID_status] = PROP_NAME_status;
          PROP_NAME_TO_ID.put(PROP_NAME_status, PROP_ID_status);
      
          PROP_ID_TO_NAME[PROP_ID_actualValue] = PROP_NAME_actualValue;
          PROP_NAME_TO_ID.put(PROP_NAME_actualValue, PROP_ID_actualValue);
      
          PROP_ID_TO_NAME[PROP_ID_expectedValue] = PROP_NAME_expectedValue;
          PROP_NAME_TO_ID.put(PROP_NAME_expectedValue, PROP_ID_expectedValue);
      
          PROP_ID_TO_NAME[PROP_ID_message] = PROP_NAME_message;
          PROP_NAME_TO_ID.put(PROP_NAME_message, PROP_ID_message);
      
          PROP_ID_TO_NAME[PROP_ID_details] = PROP_NAME_details;
          PROP_NAME_TO_ID.put(PROP_NAME_details, PROP_ID_details);
      
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
      
          PROP_ID_TO_NAME[PROP_ID_isFalsePositive] = PROP_NAME_isFalsePositive;
          PROP_NAME_TO_ID.put(PROP_NAME_isFalsePositive, PROP_ID_isFalsePositive);
      
    }

    
    /* 结果ID: QUALITY_RESULT_ID */
    private java.lang.String _qualityResultId;
    
    /* 规则ID: QUALITY_RULE_ID */
    private java.lang.String _qualityRuleId;
    
    /* 执行时间: EXECUTE_TIME */
    private java.sql.Timestamp _executeTime;
    
    /* 状态: STATUS */
    private java.lang.String _status;
    
    /* 实际值: ACTUAL_VALUE */
    private java.lang.Double _actualValue;
    
    /* 期望值: EXPECTED_VALUE */
    private java.lang.Double _expectedValue;
    
    /* 结果描述: MESSAGE */
    private java.lang.String _message;
    
    /* 详情: DETAILS */
    private java.lang.String _details;
    
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
    
    /* 是否误报: IS_FALSE_POSITIVE */
    private java.lang.Byte _isFalsePositive;
    

    public _NopMetaQualityResult(){
        // for debug
    }

    protected NopMetaQualityResult newInstance(){
        NopMetaQualityResult entity = new NopMetaQualityResult();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopMetaQualityResult cloneInstance() {
        NopMetaQualityResult entity = newInstance();
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
      return "io.nop.metadata.dao.entity.NopMetaQualityResult";
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
    
        return buildSimpleId(PROP_ID_qualityResultId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_qualityResultId;
          
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
        
            case PROP_ID_qualityResultId:
               return getQualityResultId();
        
            case PROP_ID_qualityRuleId:
               return getQualityRuleId();
        
            case PROP_ID_executeTime:
               return getExecuteTime();
        
            case PROP_ID_status:
               return getStatus();
        
            case PROP_ID_actualValue:
               return getActualValue();
        
            case PROP_ID_expectedValue:
               return getExpectedValue();
        
            case PROP_ID_message:
               return getMessage();
        
            case PROP_ID_details:
               return getDetails();
        
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
        
            case PROP_ID_isFalsePositive:
               return getIsFalsePositive();
        
           default:
              return super.orm_propValue(propId);
        }
    }

    

    @Override
    public void orm_propValue(int propId, Object value){
        switch(propId){
        
            case PROP_ID_qualityResultId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_qualityResultId));
               }
               setQualityResultId(typedValue);
               break;
            }
        
            case PROP_ID_qualityRuleId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_qualityRuleId));
               }
               setQualityRuleId(typedValue);
               break;
            }
        
            case PROP_ID_executeTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_executeTime));
               }
               setExecuteTime(typedValue);
               break;
            }
        
            case PROP_ID_status:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_status));
               }
               setStatus(typedValue);
               break;
            }
        
            case PROP_ID_actualValue:{
               java.lang.Double typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toDouble(value,
                       err-> newTypeConversionError(PROP_NAME_actualValue));
               }
               setActualValue(typedValue);
               break;
            }
        
            case PROP_ID_expectedValue:{
               java.lang.Double typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toDouble(value,
                       err-> newTypeConversionError(PROP_NAME_expectedValue));
               }
               setExpectedValue(typedValue);
               break;
            }
        
            case PROP_ID_message:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_message));
               }
               setMessage(typedValue);
               break;
            }
        
            case PROP_ID_details:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_details));
               }
               setDetails(typedValue);
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
        
            case PROP_ID_isFalsePositive:{
               java.lang.Byte typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toByte(value,
                       err-> newTypeConversionError(PROP_NAME_isFalsePositive));
               }
               setIsFalsePositive(typedValue);
               break;
            }
        
           default:
              super.orm_propValue(propId,value);
        }
    }

    @Override
    public void orm_internalSet(int propId, Object value) {
        switch(propId){
        
            case PROP_ID_qualityResultId:{
               onInitProp(propId);
               this._qualityResultId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_qualityRuleId:{
               onInitProp(propId);
               this._qualityRuleId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_executeTime:{
               onInitProp(propId);
               this._executeTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_status:{
               onInitProp(propId);
               this._status = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_actualValue:{
               onInitProp(propId);
               this._actualValue = (java.lang.Double)value;
               
               break;
            }
        
            case PROP_ID_expectedValue:{
               onInitProp(propId);
               this._expectedValue = (java.lang.Double)value;
               
               break;
            }
        
            case PROP_ID_message:{
               onInitProp(propId);
               this._message = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_details:{
               onInitProp(propId);
               this._details = (java.lang.String)value;
               
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
        
            case PROP_ID_isFalsePositive:{
               onInitProp(propId);
               this._isFalsePositive = (java.lang.Byte)value;
               
               break;
            }
        
           default:
              super.orm_internalSet(propId,value);
        }
    }

    
    /**
     * 结果ID: QUALITY_RESULT_ID
     */
    public final java.lang.String getQualityResultId(){
         onPropGet(PROP_ID_qualityResultId);
         return _qualityResultId;
    }

    /**
     * 结果ID: QUALITY_RESULT_ID
     */
    public final void setQualityResultId(java.lang.String value){
        if(onPropSet(PROP_ID_qualityResultId,value)){
            this._qualityResultId = value;
            internalClearRefs(PROP_ID_qualityResultId);
            orm_id();
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
            
        }
    }
    
    /**
     * 执行时间: EXECUTE_TIME
     */
    public final java.sql.Timestamp getExecuteTime(){
         onPropGet(PROP_ID_executeTime);
         return _executeTime;
    }

    /**
     * 执行时间: EXECUTE_TIME
     */
    public final void setExecuteTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_executeTime,value)){
            this._executeTime = value;
            internalClearRefs(PROP_ID_executeTime);
            
        }
    }
    
    /**
     * 状态: STATUS
     */
    public final java.lang.String getStatus(){
         onPropGet(PROP_ID_status);
         return _status;
    }

    /**
     * 状态: STATUS
     */
    public final void setStatus(java.lang.String value){
        if(onPropSet(PROP_ID_status,value)){
            this._status = value;
            internalClearRefs(PROP_ID_status);
            
        }
    }
    
    /**
     * 实际值: ACTUAL_VALUE
     */
    public final java.lang.Double getActualValue(){
         onPropGet(PROP_ID_actualValue);
         return _actualValue;
    }

    /**
     * 实际值: ACTUAL_VALUE
     */
    public final void setActualValue(java.lang.Double value){
        if(onPropSet(PROP_ID_actualValue,value)){
            this._actualValue = value;
            internalClearRefs(PROP_ID_actualValue);
            
        }
    }
    
    /**
     * 期望值: EXPECTED_VALUE
     */
    public final java.lang.Double getExpectedValue(){
         onPropGet(PROP_ID_expectedValue);
         return _expectedValue;
    }

    /**
     * 期望值: EXPECTED_VALUE
     */
    public final void setExpectedValue(java.lang.Double value){
        if(onPropSet(PROP_ID_expectedValue,value)){
            this._expectedValue = value;
            internalClearRefs(PROP_ID_expectedValue);
            
        }
    }
    
    /**
     * 结果描述: MESSAGE
     */
    public final java.lang.String getMessage(){
         onPropGet(PROP_ID_message);
         return _message;
    }

    /**
     * 结果描述: MESSAGE
     */
    public final void setMessage(java.lang.String value){
        if(onPropSet(PROP_ID_message,value)){
            this._message = value;
            internalClearRefs(PROP_ID_message);
            
        }
    }
    
    /**
     * 详情: DETAILS
     */
    public final java.lang.String getDetails(){
         onPropGet(PROP_ID_details);
         return _details;
    }

    /**
     * 详情: DETAILS
     */
    public final void setDetails(java.lang.String value){
        if(onPropSet(PROP_ID_details,value)){
            this._details = value;
            internalClearRefs(PROP_ID_details);
            
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
     * 是否误报: IS_FALSE_POSITIVE
     */
    public final java.lang.Byte getIsFalsePositive(){
         onPropGet(PROP_ID_isFalsePositive);
         return _isFalsePositive;
    }

    /**
     * 是否误报: IS_FALSE_POSITIVE
     */
    public final void setIsFalsePositive(java.lang.Byte value){
        if(onPropSet(PROP_ID_isFalsePositive,value)){
            this._isFalsePositive = value;
            internalClearRefs(PROP_ID_isFalsePositive);
            
        }
    }
    
    /**
     * 质量规则
     */
    public final io.nop.metadata.dao.entity.NopMetaQualityRule getQualityRule(){
       return (io.nop.metadata.dao.entity.NopMetaQualityRule)internalGetRefEntity(PROP_NAME_qualityRule);
    }

    public final void setQualityRule(io.nop.metadata.dao.entity.NopMetaQualityRule refEntity){
   
           if(refEntity == null){
           
                   this.setQualityRuleId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_qualityRule, refEntity,()->{
           
                           this.setQualityRuleId(refEntity.getQualityRuleId());
                       
           });
           }
       
    }
       
   private io.nop.orm.component.JsonOrmComponent _detailsComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_detailsComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_detailsComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_details);
      
   }

   public final io.nop.orm.component.JsonOrmComponent getDetailsComponent(){
      if(_detailsComponent == null){
          _detailsComponent = new io.nop.orm.component.JsonOrmComponent();
          _detailsComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_detailsComponent);
      }
      return _detailsComponent;
   }

}
// resume CPD analysis - CPD-ON
