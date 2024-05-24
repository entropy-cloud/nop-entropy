package io.nop.auth.dao.entity._gen;

import io.nop.orm.model.IEntityModel;
import io.nop.orm.support.DynamicOrmEntity;
import io.nop.orm.support.OrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.orm.IOrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.api.core.convert.ConvertHelper;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;

import io.nop.auth.dao.entity.NopAuthOpLog;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  操作日志: nop_auth_op_log
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopAuthOpLog extends DynamicOrmEntity{
    
    /* 主键: LOG_ID VARCHAR */
    public static final String PROP_NAME_logId = "logId";
    public static final int PROP_ID_logId = 1;
    
    /* 用户名: USER_NAME VARCHAR */
    public static final String PROP_NAME_userName = "userName";
    public static final int PROP_ID_userName = 2;
    
    /* 用户ID: USER_ID VARCHAR */
    public static final String PROP_NAME_userId = "userId";
    public static final int PROP_ID_userId = 3;
    
    /* 会话ID: SESSION_ID VARCHAR */
    public static final String PROP_NAME_sessionId = "sessionId";
    public static final int PROP_ID_sessionId = 4;
    
    /* 业务操作: OPERATION VARCHAR */
    public static final String PROP_NAME_operation = "operation";
    public static final int PROP_ID_operation = 5;
    
    /* 操作描述: DESCRIPTION VARCHAR */
    public static final String PROP_NAME_description = "description";
    public static final int PROP_ID_description = 6;
    
    /* 操作时间: ACTION_TIME TIMESTAMP */
    public static final String PROP_NAME_actionTime = "actionTime";
    public static final int PROP_ID_actionTime = 7;
    
    /* 操作时长: USED_TIME BIGINT */
    public static final String PROP_NAME_usedTime = "usedTime";
    public static final int PROP_ID_usedTime = 8;
    
    /* 操作状态: RESULT_STATUS INTEGER */
    public static final String PROP_NAME_resultStatus = "resultStatus";
    public static final int PROP_ID_resultStatus = 9;
    
    /* 错误码: ERROR_CODE VARCHAR */
    public static final String PROP_NAME_errorCode = "errorCode";
    public static final int PROP_ID_errorCode = 10;
    
    /* 返回消息: RET_MESSAGE VARCHAR */
    public static final String PROP_NAME_retMessage = "retMessage";
    public static final int PROP_ID_retMessage = 11;
    
    /* 请求参数: OP_REQUEST VARCHAR */
    public static final String PROP_NAME_opRequest = "opRequest";
    public static final int PROP_ID_opRequest = 12;
    
    /* 响应数据: OP_RESPONSE VARCHAR */
    public static final String PROP_NAME_opResponse = "opResponse";
    public static final int PROP_ID_opResponse = 13;
    

    private static int _PROP_ID_BOUND = 14;

    
    /* relation: 会话 */
    public static final String PROP_NAME_session = "session";
    
    /* relation: 用户 */
    public static final String PROP_NAME_user = "user";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_logId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_logId};

    private static final String[] PROP_ID_TO_NAME = new String[14];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_logId] = PROP_NAME_logId;
          PROP_NAME_TO_ID.put(PROP_NAME_logId, PROP_ID_logId);
      
          PROP_ID_TO_NAME[PROP_ID_userName] = PROP_NAME_userName;
          PROP_NAME_TO_ID.put(PROP_NAME_userName, PROP_ID_userName);
      
          PROP_ID_TO_NAME[PROP_ID_userId] = PROP_NAME_userId;
          PROP_NAME_TO_ID.put(PROP_NAME_userId, PROP_ID_userId);
      
          PROP_ID_TO_NAME[PROP_ID_sessionId] = PROP_NAME_sessionId;
          PROP_NAME_TO_ID.put(PROP_NAME_sessionId, PROP_ID_sessionId);
      
          PROP_ID_TO_NAME[PROP_ID_operation] = PROP_NAME_operation;
          PROP_NAME_TO_ID.put(PROP_NAME_operation, PROP_ID_operation);
      
          PROP_ID_TO_NAME[PROP_ID_description] = PROP_NAME_description;
          PROP_NAME_TO_ID.put(PROP_NAME_description, PROP_ID_description);
      
          PROP_ID_TO_NAME[PROP_ID_actionTime] = PROP_NAME_actionTime;
          PROP_NAME_TO_ID.put(PROP_NAME_actionTime, PROP_ID_actionTime);
      
          PROP_ID_TO_NAME[PROP_ID_usedTime] = PROP_NAME_usedTime;
          PROP_NAME_TO_ID.put(PROP_NAME_usedTime, PROP_ID_usedTime);
      
          PROP_ID_TO_NAME[PROP_ID_resultStatus] = PROP_NAME_resultStatus;
          PROP_NAME_TO_ID.put(PROP_NAME_resultStatus, PROP_ID_resultStatus);
      
          PROP_ID_TO_NAME[PROP_ID_errorCode] = PROP_NAME_errorCode;
          PROP_NAME_TO_ID.put(PROP_NAME_errorCode, PROP_ID_errorCode);
      
          PROP_ID_TO_NAME[PROP_ID_retMessage] = PROP_NAME_retMessage;
          PROP_NAME_TO_ID.put(PROP_NAME_retMessage, PROP_ID_retMessage);
      
          PROP_ID_TO_NAME[PROP_ID_opRequest] = PROP_NAME_opRequest;
          PROP_NAME_TO_ID.put(PROP_NAME_opRequest, PROP_ID_opRequest);
      
          PROP_ID_TO_NAME[PROP_ID_opResponse] = PROP_NAME_opResponse;
          PROP_NAME_TO_ID.put(PROP_NAME_opResponse, PROP_ID_opResponse);
      
    }

    
    /* 主键: LOG_ID */
    private java.lang.String _logId;
    
    /* 用户名: USER_NAME */
    private java.lang.String _userName;
    
    /* 用户ID: USER_ID */
    private java.lang.String _userId;
    
    /* 会话ID: SESSION_ID */
    private java.lang.String _sessionId;
    
    /* 业务操作: OPERATION */
    private java.lang.String _operation;
    
    /* 操作描述: DESCRIPTION */
    private java.lang.String _description;
    
    /* 操作时间: ACTION_TIME */
    private java.sql.Timestamp _actionTime;
    
    /* 操作时长: USED_TIME */
    private java.lang.Long _usedTime;
    
    /* 操作状态: RESULT_STATUS */
    private java.lang.Integer _resultStatus;
    
    /* 错误码: ERROR_CODE */
    private java.lang.String _errorCode;
    
    /* 返回消息: RET_MESSAGE */
    private java.lang.String _retMessage;
    
    /* 请求参数: OP_REQUEST */
    private java.lang.String _opRequest;
    
    /* 响应数据: OP_RESPONSE */
    private java.lang.String _opResponse;
    

    public _NopAuthOpLog(){
        // for debug
    }

    protected NopAuthOpLog newInstance(){
        NopAuthOpLog entity = new NopAuthOpLog();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopAuthOpLog cloneInstance() {
        NopAuthOpLog entity = newInstance();
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
      return "io.nop.auth.dao.entity.NopAuthOpLog";
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
    
        return buildSimpleId(PROP_ID_logId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_logId;
          
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
        
            case PROP_ID_logId:
               return getLogId();
        
            case PROP_ID_userName:
               return getUserName();
        
            case PROP_ID_userId:
               return getUserId();
        
            case PROP_ID_sessionId:
               return getSessionId();
        
            case PROP_ID_operation:
               return getOperation();
        
            case PROP_ID_description:
               return getDescription();
        
            case PROP_ID_actionTime:
               return getActionTime();
        
            case PROP_ID_usedTime:
               return getUsedTime();
        
            case PROP_ID_resultStatus:
               return getResultStatus();
        
            case PROP_ID_errorCode:
               return getErrorCode();
        
            case PROP_ID_retMessage:
               return getRetMessage();
        
            case PROP_ID_opRequest:
               return getOpRequest();
        
            case PROP_ID_opResponse:
               return getOpResponse();
        
           default:
              return super.orm_propValue(propId);
        }
    }

    

    @Override
    public void orm_propValue(int propId, Object value){
        switch(propId){
        
            case PROP_ID_logId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_logId));
               }
               setLogId(typedValue);
               break;
            }
        
            case PROP_ID_userName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_userName));
               }
               setUserName(typedValue);
               break;
            }
        
            case PROP_ID_userId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_userId));
               }
               setUserId(typedValue);
               break;
            }
        
            case PROP_ID_sessionId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_sessionId));
               }
               setSessionId(typedValue);
               break;
            }
        
            case PROP_ID_operation:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_operation));
               }
               setOperation(typedValue);
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
        
            case PROP_ID_actionTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_actionTime));
               }
               setActionTime(typedValue);
               break;
            }
        
            case PROP_ID_usedTime:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_usedTime));
               }
               setUsedTime(typedValue);
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
        
            case PROP_ID_errorCode:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_errorCode));
               }
               setErrorCode(typedValue);
               break;
            }
        
            case PROP_ID_retMessage:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_retMessage));
               }
               setRetMessage(typedValue);
               break;
            }
        
            case PROP_ID_opRequest:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_opRequest));
               }
               setOpRequest(typedValue);
               break;
            }
        
            case PROP_ID_opResponse:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_opResponse));
               }
               setOpResponse(typedValue);
               break;
            }
        
           default:
              super.orm_propValue(propId,value);
        }
    }

    @Override
    public void orm_internalSet(int propId, Object value) {
        switch(propId){
        
            case PROP_ID_logId:{
               onInitProp(propId);
               this._logId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_userName:{
               onInitProp(propId);
               this._userName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_userId:{
               onInitProp(propId);
               this._userId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_sessionId:{
               onInitProp(propId);
               this._sessionId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_operation:{
               onInitProp(propId);
               this._operation = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_description:{
               onInitProp(propId);
               this._description = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_actionTime:{
               onInitProp(propId);
               this._actionTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_usedTime:{
               onInitProp(propId);
               this._usedTime = (java.lang.Long)value;
               
               break;
            }
        
            case PROP_ID_resultStatus:{
               onInitProp(propId);
               this._resultStatus = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_errorCode:{
               onInitProp(propId);
               this._errorCode = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_retMessage:{
               onInitProp(propId);
               this._retMessage = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_opRequest:{
               onInitProp(propId);
               this._opRequest = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_opResponse:{
               onInitProp(propId);
               this._opResponse = (java.lang.String)value;
               
               break;
            }
        
           default:
              super.orm_internalSet(propId,value);
        }
    }

    
    /**
     * 主键: LOG_ID
     */
    public java.lang.String getLogId(){
         onPropGet(PROP_ID_logId);
         return _logId;
    }

    /**
     * 主键: LOG_ID
     */
    public void setLogId(java.lang.String value){
        if(onPropSet(PROP_ID_logId,value)){
            this._logId = value;
            internalClearRefs(PROP_ID_logId);
            orm_id();
        }
    }
    
    /**
     * 用户名: USER_NAME
     */
    public java.lang.String getUserName(){
         onPropGet(PROP_ID_userName);
         return _userName;
    }

    /**
     * 用户名: USER_NAME
     */
    public void setUserName(java.lang.String value){
        if(onPropSet(PROP_ID_userName,value)){
            this._userName = value;
            internalClearRefs(PROP_ID_userName);
            
        }
    }
    
    /**
     * 用户ID: USER_ID
     */
    public java.lang.String getUserId(){
         onPropGet(PROP_ID_userId);
         return _userId;
    }

    /**
     * 用户ID: USER_ID
     */
    public void setUserId(java.lang.String value){
        if(onPropSet(PROP_ID_userId,value)){
            this._userId = value;
            internalClearRefs(PROP_ID_userId);
            
        }
    }
    
    /**
     * 会话ID: SESSION_ID
     */
    public java.lang.String getSessionId(){
         onPropGet(PROP_ID_sessionId);
         return _sessionId;
    }

    /**
     * 会话ID: SESSION_ID
     */
    public void setSessionId(java.lang.String value){
        if(onPropSet(PROP_ID_sessionId,value)){
            this._sessionId = value;
            internalClearRefs(PROP_ID_sessionId);
            
        }
    }
    
    /**
     * 业务操作: OPERATION
     */
    public java.lang.String getOperation(){
         onPropGet(PROP_ID_operation);
         return _operation;
    }

    /**
     * 业务操作: OPERATION
     */
    public void setOperation(java.lang.String value){
        if(onPropSet(PROP_ID_operation,value)){
            this._operation = value;
            internalClearRefs(PROP_ID_operation);
            
        }
    }
    
    /**
     * 操作描述: DESCRIPTION
     */
    public java.lang.String getDescription(){
         onPropGet(PROP_ID_description);
         return _description;
    }

    /**
     * 操作描述: DESCRIPTION
     */
    public void setDescription(java.lang.String value){
        if(onPropSet(PROP_ID_description,value)){
            this._description = value;
            internalClearRefs(PROP_ID_description);
            
        }
    }
    
    /**
     * 操作时间: ACTION_TIME
     */
    public java.sql.Timestamp getActionTime(){
         onPropGet(PROP_ID_actionTime);
         return _actionTime;
    }

    /**
     * 操作时间: ACTION_TIME
     */
    public void setActionTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_actionTime,value)){
            this._actionTime = value;
            internalClearRefs(PROP_ID_actionTime);
            
        }
    }
    
    /**
     * 操作时长: USED_TIME
     */
    public java.lang.Long getUsedTime(){
         onPropGet(PROP_ID_usedTime);
         return _usedTime;
    }

    /**
     * 操作时长: USED_TIME
     */
    public void setUsedTime(java.lang.Long value){
        if(onPropSet(PROP_ID_usedTime,value)){
            this._usedTime = value;
            internalClearRefs(PROP_ID_usedTime);
            
        }
    }
    
    /**
     * 操作状态: RESULT_STATUS
     */
    public java.lang.Integer getResultStatus(){
         onPropGet(PROP_ID_resultStatus);
         return _resultStatus;
    }

    /**
     * 操作状态: RESULT_STATUS
     */
    public void setResultStatus(java.lang.Integer value){
        if(onPropSet(PROP_ID_resultStatus,value)){
            this._resultStatus = value;
            internalClearRefs(PROP_ID_resultStatus);
            
        }
    }
    
    /**
     * 错误码: ERROR_CODE
     */
    public java.lang.String getErrorCode(){
         onPropGet(PROP_ID_errorCode);
         return _errorCode;
    }

    /**
     * 错误码: ERROR_CODE
     */
    public void setErrorCode(java.lang.String value){
        if(onPropSet(PROP_ID_errorCode,value)){
            this._errorCode = value;
            internalClearRefs(PROP_ID_errorCode);
            
        }
    }
    
    /**
     * 返回消息: RET_MESSAGE
     */
    public java.lang.String getRetMessage(){
         onPropGet(PROP_ID_retMessage);
         return _retMessage;
    }

    /**
     * 返回消息: RET_MESSAGE
     */
    public void setRetMessage(java.lang.String value){
        if(onPropSet(PROP_ID_retMessage,value)){
            this._retMessage = value;
            internalClearRefs(PROP_ID_retMessage);
            
        }
    }
    
    /**
     * 请求参数: OP_REQUEST
     */
    public java.lang.String getOpRequest(){
         onPropGet(PROP_ID_opRequest);
         return _opRequest;
    }

    /**
     * 请求参数: OP_REQUEST
     */
    public void setOpRequest(java.lang.String value){
        if(onPropSet(PROP_ID_opRequest,value)){
            this._opRequest = value;
            internalClearRefs(PROP_ID_opRequest);
            
        }
    }
    
    /**
     * 响应数据: OP_RESPONSE
     */
    public java.lang.String getOpResponse(){
         onPropGet(PROP_ID_opResponse);
         return _opResponse;
    }

    /**
     * 响应数据: OP_RESPONSE
     */
    public void setOpResponse(java.lang.String value){
        if(onPropSet(PROP_ID_opResponse,value)){
            this._opResponse = value;
            internalClearRefs(PROP_ID_opResponse);
            
        }
    }
    
    /**
     * 会话
     */
    public io.nop.auth.dao.entity.NopAuthSession getSession(){
       return (io.nop.auth.dao.entity.NopAuthSession)internalGetRefEntity(PROP_NAME_session);
    }

    public void setSession(io.nop.auth.dao.entity.NopAuthSession refEntity){
   
           if(refEntity == null){
           
                   this.setSessionId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_session, refEntity,()->{
           
                           this.setSessionId(refEntity.getSessionId());
                       
           });
           }
       
    }
       
    /**
     * 用户
     */
    public io.nop.auth.dao.entity.NopAuthUser getUser(){
       return (io.nop.auth.dao.entity.NopAuthUser)internalGetRefEntity(PROP_NAME_user);
    }

    public void setUser(io.nop.auth.dao.entity.NopAuthUser refEntity){
   
           if(refEntity == null){
           
                   this.setUserId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_user, refEntity,()->{
           
                           this.setUserId(refEntity.getUserId());
                       
           });
           }
       
    }
       
}
// resume CPD analysis - CPD-ON
