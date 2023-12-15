package io.nop.wf.dao.entity._gen;

import io.nop.orm.model.IEntityModel;
import io.nop.orm.support.DynamicOrmEntity;
import io.nop.orm.support.OrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.orm.IOrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.api.core.convert.ConvertHelper;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;

import io.nop.wf.dao.entity.NopWfLog;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  工作流日志: nop_wf_log
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101"})
public class _NopWfLog extends DynamicOrmEntity{
    
    /* 日志ID: SID VARCHAR */
    public static final String PROP_NAME_sid = "sid";
    public static final int PROP_ID_sid = 1;
    
    /* 工作流实例ID: WF_ID VARCHAR */
    public static final String PROP_NAME_wfId = "wfId";
    public static final int PROP_ID_wfId = 2;
    
    /* 工作流步骤ID: STEP_ID VARCHAR */
    public static final String PROP_NAME_stepId = "stepId";
    public static final int PROP_ID_stepId = 3;
    
    /* 动作ID: ACTION_ID VARCHAR */
    public static final String PROP_NAME_actionId = "actionId";
    public static final int PROP_ID_actionId = 4;
    
    /* 日志级别: LOG_LEVEL INTEGER */
    public static final String PROP_NAME_logLevel = "logLevel";
    public static final int PROP_ID_logLevel = 5;
    
    /* 日志消息: LOG_MSG VARCHAR */
    public static final String PROP_NAME_logMsg = "logMsg";
    public static final int PROP_ID_logMsg = 6;
    
    /* 错误码: ERR_CODE VARCHAR */
    public static final String PROP_NAME_errCode = "errCode";
    public static final int PROP_ID_errCode = 7;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 8;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 9;
    

    private static int _PROP_ID_BOUND = 10;

    
    /* relation: 工作流实例 */
    public static final String PROP_NAME_wfInstance = "wfInstance";
    
    /* relation: 工作流步骤实例 */
    public static final String PROP_NAME_wfStepInstance = "wfStepInstance";
    
    /* relation: 工作流动作 */
    public static final String PROP_NAME_wfAction = "wfAction";
    

    public static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_sid);
    public static final int[] PK_PROP_IDS = new int[]{PROP_ID_sid};

    private static final String[] PROP_ID_TO_NAME = new String[10];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_sid] = PROP_NAME_sid;
          PROP_NAME_TO_ID.put(PROP_NAME_sid, PROP_ID_sid);
      
          PROP_ID_TO_NAME[PROP_ID_wfId] = PROP_NAME_wfId;
          PROP_NAME_TO_ID.put(PROP_NAME_wfId, PROP_ID_wfId);
      
          PROP_ID_TO_NAME[PROP_ID_stepId] = PROP_NAME_stepId;
          PROP_NAME_TO_ID.put(PROP_NAME_stepId, PROP_ID_stepId);
      
          PROP_ID_TO_NAME[PROP_ID_actionId] = PROP_NAME_actionId;
          PROP_NAME_TO_ID.put(PROP_NAME_actionId, PROP_ID_actionId);
      
          PROP_ID_TO_NAME[PROP_ID_logLevel] = PROP_NAME_logLevel;
          PROP_NAME_TO_ID.put(PROP_NAME_logLevel, PROP_ID_logLevel);
      
          PROP_ID_TO_NAME[PROP_ID_logMsg] = PROP_NAME_logMsg;
          PROP_NAME_TO_ID.put(PROP_NAME_logMsg, PROP_ID_logMsg);
      
          PROP_ID_TO_NAME[PROP_ID_errCode] = PROP_NAME_errCode;
          PROP_NAME_TO_ID.put(PROP_NAME_errCode, PROP_ID_errCode);
      
          PROP_ID_TO_NAME[PROP_ID_createdBy] = PROP_NAME_createdBy;
          PROP_NAME_TO_ID.put(PROP_NAME_createdBy, PROP_ID_createdBy);
      
          PROP_ID_TO_NAME[PROP_ID_createTime] = PROP_NAME_createTime;
          PROP_NAME_TO_ID.put(PROP_NAME_createTime, PROP_ID_createTime);
      
    }

    
    /* 日志ID: SID */
    private java.lang.String _sid;
    
    /* 工作流实例ID: WF_ID */
    private java.lang.String _wfId;
    
    /* 工作流步骤ID: STEP_ID */
    private java.lang.String _stepId;
    
    /* 动作ID: ACTION_ID */
    private java.lang.String _actionId;
    
    /* 日志级别: LOG_LEVEL */
    private java.lang.Integer _logLevel;
    
    /* 日志消息: LOG_MSG */
    private java.lang.String _logMsg;
    
    /* 错误码: ERR_CODE */
    private java.lang.String _errCode;
    
    /* 创建人: CREATED_BY */
    private java.lang.String _createdBy;
    
    /* 创建时间: CREATE_TIME */
    private java.sql.Timestamp _createTime;
    

    public _NopWfLog(){
    }

    protected NopWfLog newInstance(){
       return new NopWfLog();
    }

    @Override
    public NopWfLog cloneInstance() {
        NopWfLog entity = newInstance();
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
      return "io.nop.wf.dao.entity.NopWfLog";
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
    
        return buildSimpleId(PROP_ID_sid);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_sid;
          
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
        
            case PROP_ID_sid:
               return getSid();
        
            case PROP_ID_wfId:
               return getWfId();
        
            case PROP_ID_stepId:
               return getStepId();
        
            case PROP_ID_actionId:
               return getActionId();
        
            case PROP_ID_logLevel:
               return getLogLevel();
        
            case PROP_ID_logMsg:
               return getLogMsg();
        
            case PROP_ID_errCode:
               return getErrCode();
        
            case PROP_ID_createdBy:
               return getCreatedBy();
        
            case PROP_ID_createTime:
               return getCreateTime();
        
           default:
              return super.orm_propValue(propId);
        }
    }

    

    @Override
    public void orm_propValue(int propId, Object value){
        switch(propId){
        
            case PROP_ID_sid:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_sid));
               }
               setSid(typedValue);
               break;
            }
        
            case PROP_ID_wfId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_wfId));
               }
               setWfId(typedValue);
               break;
            }
        
            case PROP_ID_stepId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_stepId));
               }
               setStepId(typedValue);
               break;
            }
        
            case PROP_ID_actionId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_actionId));
               }
               setActionId(typedValue);
               break;
            }
        
            case PROP_ID_logLevel:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_logLevel));
               }
               setLogLevel(typedValue);
               break;
            }
        
            case PROP_ID_logMsg:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_logMsg));
               }
               setLogMsg(typedValue);
               break;
            }
        
            case PROP_ID_errCode:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_errCode));
               }
               setErrCode(typedValue);
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
        
           default:
              super.orm_propValue(propId,value);
        }
    }

    @Override
    public void orm_internalSet(int propId, Object value) {
        switch(propId){
        
            case PROP_ID_sid:{
               onInitProp(propId);
               this._sid = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_wfId:{
               onInitProp(propId);
               this._wfId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_stepId:{
               onInitProp(propId);
               this._stepId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_actionId:{
               onInitProp(propId);
               this._actionId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_logLevel:{
               onInitProp(propId);
               this._logLevel = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_logMsg:{
               onInitProp(propId);
               this._logMsg = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_errCode:{
               onInitProp(propId);
               this._errCode = (java.lang.String)value;
               
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
        
           default:
              super.orm_internalSet(propId,value);
        }
    }

    
    /**
     * 日志ID: SID
     */
    public java.lang.String getSid(){
         onPropGet(PROP_ID_sid);
         return _sid;
    }

    /**
     * 日志ID: SID
     */
    public void setSid(java.lang.String value){
        if(onPropSet(PROP_ID_sid,value)){
            this._sid = value;
            internalClearRefs(PROP_ID_sid);
            orm_id();
        }
    }
    
    /**
     * 工作流实例ID: WF_ID
     */
    public java.lang.String getWfId(){
         onPropGet(PROP_ID_wfId);
         return _wfId;
    }

    /**
     * 工作流实例ID: WF_ID
     */
    public void setWfId(java.lang.String value){
        if(onPropSet(PROP_ID_wfId,value)){
            this._wfId = value;
            internalClearRefs(PROP_ID_wfId);
            
        }
    }
    
    /**
     * 工作流步骤ID: STEP_ID
     */
    public java.lang.String getStepId(){
         onPropGet(PROP_ID_stepId);
         return _stepId;
    }

    /**
     * 工作流步骤ID: STEP_ID
     */
    public void setStepId(java.lang.String value){
        if(onPropSet(PROP_ID_stepId,value)){
            this._stepId = value;
            internalClearRefs(PROP_ID_stepId);
            
        }
    }
    
    /**
     * 动作ID: ACTION_ID
     */
    public java.lang.String getActionId(){
         onPropGet(PROP_ID_actionId);
         return _actionId;
    }

    /**
     * 动作ID: ACTION_ID
     */
    public void setActionId(java.lang.String value){
        if(onPropSet(PROP_ID_actionId,value)){
            this._actionId = value;
            internalClearRefs(PROP_ID_actionId);
            
        }
    }
    
    /**
     * 日志级别: LOG_LEVEL
     */
    public java.lang.Integer getLogLevel(){
         onPropGet(PROP_ID_logLevel);
         return _logLevel;
    }

    /**
     * 日志级别: LOG_LEVEL
     */
    public void setLogLevel(java.lang.Integer value){
        if(onPropSet(PROP_ID_logLevel,value)){
            this._logLevel = value;
            internalClearRefs(PROP_ID_logLevel);
            
        }
    }
    
    /**
     * 日志消息: LOG_MSG
     */
    public java.lang.String getLogMsg(){
         onPropGet(PROP_ID_logMsg);
         return _logMsg;
    }

    /**
     * 日志消息: LOG_MSG
     */
    public void setLogMsg(java.lang.String value){
        if(onPropSet(PROP_ID_logMsg,value)){
            this._logMsg = value;
            internalClearRefs(PROP_ID_logMsg);
            
        }
    }
    
    /**
     * 错误码: ERR_CODE
     */
    public java.lang.String getErrCode(){
         onPropGet(PROP_ID_errCode);
         return _errCode;
    }

    /**
     * 错误码: ERR_CODE
     */
    public void setErrCode(java.lang.String value){
        if(onPropSet(PROP_ID_errCode,value)){
            this._errCode = value;
            internalClearRefs(PROP_ID_errCode);
            
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
     * 工作流实例
     */
    public io.nop.wf.dao.entity.NopWfInstance getWfInstance(){
       return (io.nop.wf.dao.entity.NopWfInstance)internalGetRefEntity(PROP_NAME_wfInstance);
    }

    public void setWfInstance(io.nop.wf.dao.entity.NopWfInstance refEntity){
       if(refEntity == null){
         
         this.setWfId(null);
         
       }else{
          internalSetRefEntity(PROP_NAME_wfInstance, refEntity,()->{
             
                    this.setWfId(refEntity.getWfId());
                 
          });
       }
    }
       
    /**
     * 工作流步骤实例
     */
    public io.nop.wf.dao.entity.NopWfStepInstance getWfStepInstance(){
       return (io.nop.wf.dao.entity.NopWfStepInstance)internalGetRefEntity(PROP_NAME_wfStepInstance);
    }

    public void setWfStepInstance(io.nop.wf.dao.entity.NopWfStepInstance refEntity){
       if(refEntity == null){
         
         this.setStepId(null);
         
       }else{
          internalSetRefEntity(PROP_NAME_wfStepInstance, refEntity,()->{
             
                    this.setStepId(refEntity.getStepId());
                 
          });
       }
    }
       
    /**
     * 工作流动作
     */
    public io.nop.wf.dao.entity.NopWfAction getWfAction(){
       return (io.nop.wf.dao.entity.NopWfAction)internalGetRefEntity(PROP_NAME_wfAction);
    }

    public void setWfAction(io.nop.wf.dao.entity.NopWfAction refEntity){
       if(refEntity == null){
         
         this.setActionId(null);
         
       }else{
          internalSetRefEntity(PROP_NAME_wfAction, refEntity,()->{
             
                    this.setActionId(refEntity.getSid());
                 
          });
       }
    }
       
}
// resume CPD analysis - CPD-ON
