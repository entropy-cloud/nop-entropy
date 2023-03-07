package io.nop.batch.dao.entity._gen;

import io.nop.orm.model.IEntityModel;
import io.nop.orm.support.DynamicOrmEntity;
import io.nop.orm.support.OrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.orm.IOrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.api.core.convert.ConvertHelper;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;

import io.nop.batch.dao.entity.NopBatchRecordResult;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  批处理记录结果: nop_batch_record_result
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public class _NopBatchRecordResult extends DynamicOrmEntity{
    
    /* 主键: TASK_ID VARCHAR */
    public static final String PROP_NAME_taskId = "taskId";
    public static final int PROP_ID_taskId = 1;
    
    /* 记录唯一键: RECORD_KEY VARCHAR */
    public static final String PROP_NAME_recordKey = "recordKey";
    public static final int PROP_ID_recordKey = 2;
    
    /* 返回状态码: RESULT_STATUS INTEGER */
    public static final String PROP_NAME_resultStatus = "resultStatus";
    public static final int PROP_ID_resultStatus = 3;
    
    /* 返回码: RESULT_CODE VARCHAR */
    public static final String PROP_NAME_resultCode = "resultCode";
    public static final int PROP_ID_resultCode = 4;
    
    /* 返回消息: RESULT_MSG VARCHAR */
    public static final String PROP_NAME_resultMsg = "resultMsg";
    public static final int PROP_ID_resultMsg = 5;
    
    /* 错误堆栈: ERROR_STACK VARCHAR */
    public static final String PROP_NAME_errorStack = "errorStack";
    public static final int PROP_ID_errorStack = 6;
    
    /* 数据版本: VERSION BIGINT */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 7;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 8;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 9;
    
    /* 修改人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 10;
    
    /* 修改时间: UPDATE_TIME TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 11;
    

    private static int _PROP_ID_BOUND = 12;

    
    /* relation: 批处理任务 */
    public static final String PROP_NAME_task = "task";
    

    public static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_taskId,PROP_NAME_recordKey);
    public static final int[] PK_PROP_IDS = new int[]{PROP_ID_taskId,PROP_ID_recordKey};

    private static final String[] PROP_ID_TO_NAME = new String[12];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_taskId] = PROP_NAME_taskId;
          PROP_NAME_TO_ID.put(PROP_NAME_taskId, PROP_ID_taskId);
      
          PROP_ID_TO_NAME[PROP_ID_recordKey] = PROP_NAME_recordKey;
          PROP_NAME_TO_ID.put(PROP_NAME_recordKey, PROP_ID_recordKey);
      
          PROP_ID_TO_NAME[PROP_ID_resultStatus] = PROP_NAME_resultStatus;
          PROP_NAME_TO_ID.put(PROP_NAME_resultStatus, PROP_ID_resultStatus);
      
          PROP_ID_TO_NAME[PROP_ID_resultCode] = PROP_NAME_resultCode;
          PROP_NAME_TO_ID.put(PROP_NAME_resultCode, PROP_ID_resultCode);
      
          PROP_ID_TO_NAME[PROP_ID_resultMsg] = PROP_NAME_resultMsg;
          PROP_NAME_TO_ID.put(PROP_NAME_resultMsg, PROP_ID_resultMsg);
      
          PROP_ID_TO_NAME[PROP_ID_errorStack] = PROP_NAME_errorStack;
          PROP_NAME_TO_ID.put(PROP_NAME_errorStack, PROP_ID_errorStack);
      
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
      
    }

    
    /* 主键: TASK_ID */
    private java.lang.String _taskId;
    
    /* 记录唯一键: RECORD_KEY */
    private java.lang.String _recordKey;
    
    /* 返回状态码: RESULT_STATUS */
    private java.lang.Integer _resultStatus;
    
    /* 返回码: RESULT_CODE */
    private java.lang.String _resultCode;
    
    /* 返回消息: RESULT_MSG */
    private java.lang.String _resultMsg;
    
    /* 错误堆栈: ERROR_STACK */
    private java.lang.String _errorStack;
    
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
    

    public _NopBatchRecordResult(){
    }

    protected NopBatchRecordResult newInstance(){
       return new NopBatchRecordResult();
    }

    @Override
    public NopBatchRecordResult cloneInstance() {
        NopBatchRecordResult entity = newInstance();
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
      return "io.nop.batch.dao.entity.NopBatchRecordResult";
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
        
            return propId == PROP_ID_taskId || propId == PROP_ID_recordKey;
          
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
        
            case PROP_ID_taskId:
               return getTaskId();
        
            case PROP_ID_recordKey:
               return getRecordKey();
        
            case PROP_ID_resultStatus:
               return getResultStatus();
        
            case PROP_ID_resultCode:
               return getResultCode();
        
            case PROP_ID_resultMsg:
               return getResultMsg();
        
            case PROP_ID_errorStack:
               return getErrorStack();
        
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
        
           default:
              return super.orm_propValue(propId);
        }
    }

    

    @Override
    public void orm_propValue(int propId, Object value){
        switch(propId){
        
            case PROP_ID_taskId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_taskId));
               }
               setTaskId(typedValue);
               break;
            }
        
            case PROP_ID_recordKey:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_recordKey));
               }
               setRecordKey(typedValue);
               break;
            }
        
            case PROP_ID_resultStatus:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_resultStatus));
               }
               setResultStatus(typedValue);
               break;
            }
        
            case PROP_ID_resultCode:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_resultCode));
               }
               setResultCode(typedValue);
               break;
            }
        
            case PROP_ID_resultMsg:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_resultMsg));
               }
               setResultMsg(typedValue);
               break;
            }
        
            case PROP_ID_errorStack:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_errorStack));
               }
               setErrorStack(typedValue);
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
        
           default:
              super.orm_propValue(propId,value);
        }
    }

    @Override
    public void orm_internalSet(int propId, Object value) {
        switch(propId){
        
            case PROP_ID_taskId:{
               onInitProp(propId);
               this._taskId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_recordKey:{
               onInitProp(propId);
               this._recordKey = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_resultStatus:{
               onInitProp(propId);
               this._resultStatus = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_resultCode:{
               onInitProp(propId);
               this._resultCode = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_resultMsg:{
               onInitProp(propId);
               this._resultMsg = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_errorStack:{
               onInitProp(propId);
               this._errorStack = (java.lang.String)value;
               
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
        
           default:
              super.orm_internalSet(propId,value);
        }
    }

    
    /**
     * 主键: TASK_ID
     */
    public java.lang.String getTaskId(){
         onPropGet(PROP_ID_taskId);
         return _taskId;
    }

    /**
     * 主键: TASK_ID
     */
    public void setTaskId(java.lang.String value){
        if(onPropSet(PROP_ID_taskId,value)){
            this._taskId = value;
            internalClearRefs(PROP_ID_taskId);
            orm_id();
        }
    }
    
    /**
     * 记录唯一键: RECORD_KEY
     */
    public java.lang.String getRecordKey(){
         onPropGet(PROP_ID_recordKey);
         return _recordKey;
    }

    /**
     * 记录唯一键: RECORD_KEY
     */
    public void setRecordKey(java.lang.String value){
        if(onPropSet(PROP_ID_recordKey,value)){
            this._recordKey = value;
            internalClearRefs(PROP_ID_recordKey);
            orm_id();
        }
    }
    
    /**
     * 返回状态码: RESULT_STATUS
     */
    public java.lang.Integer getResultStatus(){
         onPropGet(PROP_ID_resultStatus);
         return _resultStatus;
    }

    /**
     * 返回状态码: RESULT_STATUS
     */
    public void setResultStatus(java.lang.Integer value){
        if(onPropSet(PROP_ID_resultStatus,value)){
            this._resultStatus = value;
            internalClearRefs(PROP_ID_resultStatus);
            
        }
    }
    
    /**
     * 返回码: RESULT_CODE
     */
    public java.lang.String getResultCode(){
         onPropGet(PROP_ID_resultCode);
         return _resultCode;
    }

    /**
     * 返回码: RESULT_CODE
     */
    public void setResultCode(java.lang.String value){
        if(onPropSet(PROP_ID_resultCode,value)){
            this._resultCode = value;
            internalClearRefs(PROP_ID_resultCode);
            
        }
    }
    
    /**
     * 返回消息: RESULT_MSG
     */
    public java.lang.String getResultMsg(){
         onPropGet(PROP_ID_resultMsg);
         return _resultMsg;
    }

    /**
     * 返回消息: RESULT_MSG
     */
    public void setResultMsg(java.lang.String value){
        if(onPropSet(PROP_ID_resultMsg,value)){
            this._resultMsg = value;
            internalClearRefs(PROP_ID_resultMsg);
            
        }
    }
    
    /**
     * 错误堆栈: ERROR_STACK
     */
    public java.lang.String getErrorStack(){
         onPropGet(PROP_ID_errorStack);
         return _errorStack;
    }

    /**
     * 错误堆栈: ERROR_STACK
     */
    public void setErrorStack(java.lang.String value){
        if(onPropSet(PROP_ID_errorStack,value)){
            this._errorStack = value;
            internalClearRefs(PROP_ID_errorStack);
            
        }
    }
    
    /**
     * 数据版本: VERSION
     */
    public java.lang.Long getVersion(){
         onPropGet(PROP_ID_version);
         return _version;
    }

    /**
     * 数据版本: VERSION
     */
    public void setVersion(java.lang.Long value){
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
     * 批处理任务
     */
    public io.nop.batch.dao.entity.NopBatchTask getTask(){
       return (io.nop.batch.dao.entity.NopBatchTask)internalGetRefEntity(PROP_NAME_task);
    }

    public void setTask(io.nop.batch.dao.entity.NopBatchTask refEntity){
       if(refEntity == null){
         
         this.setTaskId(null);
         
       }else{
          internalSetRefEntity(PROP_NAME_task, refEntity,()->{
             
                    this.setTaskId(refEntity.getSid());
                 
          });
       }
    }
       
}
// resume CPD analysis - CPD-ON
