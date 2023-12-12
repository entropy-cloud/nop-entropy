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
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101"})
public class _NopAuthOpLog extends DynamicOrmEntity{
    
    /* 主键: LOG_ID VARCHAR */
    public static final String PROP_NAME_logId = "logId";
    public static final int PROP_ID_logId = 1;
    
    /* 用户名: USER_NAME VARCHAR */
    public static final String PROP_NAME_userName = "userName";
    public static final int PROP_ID_userName = 2;
    
    /* 会话ID: SESSION_ID VARCHAR */
    public static final String PROP_NAME_sessionId = "sessionId";
    public static final int PROP_ID_sessionId = 3;
    
    /* 标题: TITLE VARCHAR */
    public static final String PROP_NAME_title = "title";
    public static final int PROP_ID_title = 4;
    
    /* 业务对象: BIZ_OBJ_NAME VARCHAR */
    public static final String PROP_NAME_bizObjName = "bizObjName";
    public static final int PROP_ID_bizObjName = 5;
    
    /* 业务操作: BIZ_ACTION_NAME VARCHAR */
    public static final String PROP_NAME_bizActionName = "bizActionName";
    public static final int PROP_ID_bizActionName = 6;
    
    /* 请求参数: OP_REQUEST VARCHAR */
    public static final String PROP_NAME_opRequest = "opRequest";
    public static final int PROP_ID_opRequest = 7;
    
    /* 响应数据: OP_RESPONSE VARCHAR */
    public static final String PROP_NAME_opResponse = "opResponse";
    public static final int PROP_ID_opResponse = 8;
    
    /* 操作状态: RESULT_STATUS INTEGER */
    public static final String PROP_NAME_resultStatus = "resultStatus";
    public static final int PROP_ID_resultStatus = 9;
    
    /* 错误码: ERROR_CODE VARCHAR */
    public static final String PROP_NAME_errorCode = "errorCode";
    public static final int PROP_ID_errorCode = 10;
    
    /* 操作时长: USED_TIME BIGINT */
    public static final String PROP_NAME_usedTime = "usedTime";
    public static final int PROP_ID_usedTime = 11;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 13;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 14;
    

    private static int _PROP_ID_BOUND = 15;

    
    /* relation: 会话 */
    public static final String PROP_NAME_session = "session";
    

    public static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_logId);
    public static final int[] PK_PROP_IDS = new int[]{PROP_ID_logId};

    private static final String[] PROP_ID_TO_NAME = new String[15];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_logId] = PROP_NAME_logId;
          PROP_NAME_TO_ID.put(PROP_NAME_logId, PROP_ID_logId);
      
          PROP_ID_TO_NAME[PROP_ID_userName] = PROP_NAME_userName;
          PROP_NAME_TO_ID.put(PROP_NAME_userName, PROP_ID_userName);
      
          PROP_ID_TO_NAME[PROP_ID_sessionId] = PROP_NAME_sessionId;
          PROP_NAME_TO_ID.put(PROP_NAME_sessionId, PROP_ID_sessionId);
      
          PROP_ID_TO_NAME[PROP_ID_title] = PROP_NAME_title;
          PROP_NAME_TO_ID.put(PROP_NAME_title, PROP_ID_title);
      
          PROP_ID_TO_NAME[PROP_ID_bizObjName] = PROP_NAME_bizObjName;
          PROP_NAME_TO_ID.put(PROP_NAME_bizObjName, PROP_ID_bizObjName);
      
          PROP_ID_TO_NAME[PROP_ID_bizActionName] = PROP_NAME_bizActionName;
          PROP_NAME_TO_ID.put(PROP_NAME_bizActionName, PROP_ID_bizActionName);
      
          PROP_ID_TO_NAME[PROP_ID_opRequest] = PROP_NAME_opRequest;
          PROP_NAME_TO_ID.put(PROP_NAME_opRequest, PROP_ID_opRequest);
      
          PROP_ID_TO_NAME[PROP_ID_opResponse] = PROP_NAME_opResponse;
          PROP_NAME_TO_ID.put(PROP_NAME_opResponse, PROP_ID_opResponse);
      
          PROP_ID_TO_NAME[PROP_ID_resultStatus] = PROP_NAME_resultStatus;
          PROP_NAME_TO_ID.put(PROP_NAME_resultStatus, PROP_ID_resultStatus);
      
          PROP_ID_TO_NAME[PROP_ID_errorCode] = PROP_NAME_errorCode;
          PROP_NAME_TO_ID.put(PROP_NAME_errorCode, PROP_ID_errorCode);
      
          PROP_ID_TO_NAME[PROP_ID_usedTime] = PROP_NAME_usedTime;
          PROP_NAME_TO_ID.put(PROP_NAME_usedTime, PROP_ID_usedTime);
      
          PROP_ID_TO_NAME[PROP_ID_createdBy] = PROP_NAME_createdBy;
          PROP_NAME_TO_ID.put(PROP_NAME_createdBy, PROP_ID_createdBy);
      
          PROP_ID_TO_NAME[PROP_ID_createTime] = PROP_NAME_createTime;
          PROP_NAME_TO_ID.put(PROP_NAME_createTime, PROP_ID_createTime);
      
    }

    
    /* 主键: LOG_ID */
    private java.lang.String _logId;
    
    /* 用户名: USER_NAME */
    private java.lang.String _userName;
    
    /* 会话ID: SESSION_ID */
    private java.lang.String _sessionId;
    
    /* 标题: TITLE */
    private java.lang.String _title;
    
    /* 业务对象: BIZ_OBJ_NAME */
    private java.lang.String _bizObjName;
    
    /* 业务操作: BIZ_ACTION_NAME */
    private java.lang.String _bizActionName;
    
    /* 请求参数: OP_REQUEST */
    private java.lang.String _opRequest;
    
    /* 响应数据: OP_RESPONSE */
    private java.lang.String _opResponse;
    
    /* 操作状态: RESULT_STATUS */
    private java.lang.Integer _resultStatus;
    
    /* 错误码: ERROR_CODE */
    private java.lang.String _errorCode;
    
    /* 操作时长: USED_TIME */
    private java.lang.Long _usedTime;
    
    /* 创建人: CREATED_BY */
    private java.lang.String _createdBy;
    
    /* 创建时间: CREATE_TIME */
    private java.sql.Timestamp _createTime;
    

    public _NopAuthOpLog(){
    }

    protected NopAuthOpLog newInstance(){
       return new NopAuthOpLog();
    }

    @Override
    public NopAuthOpLog cloneInstance() {
        NopAuthOpLog entity = newInstance();
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
        
            case PROP_ID_sessionId:
               return getSessionId();
        
            case PROP_ID_title:
               return getTitle();
        
            case PROP_ID_bizObjName:
               return getBizObjName();
        
            case PROP_ID_bizActionName:
               return getBizActionName();
        
            case PROP_ID_opRequest:
               return getOpRequest();
        
            case PROP_ID_opResponse:
               return getOpResponse();
        
            case PROP_ID_resultStatus:
               return getResultStatus();
        
            case PROP_ID_errorCode:
               return getErrorCode();
        
            case PROP_ID_usedTime:
               return getUsedTime();
        
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
        
            case PROP_ID_sessionId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_sessionId));
               }
               setSessionId(typedValue);
               break;
            }
        
            case PROP_ID_title:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_title));
               }
               setTitle(typedValue);
               break;
            }
        
            case PROP_ID_bizObjName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_bizObjName));
               }
               setBizObjName(typedValue);
               break;
            }
        
            case PROP_ID_bizActionName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_bizActionName));
               }
               setBizActionName(typedValue);
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
        
            case PROP_ID_usedTime:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_usedTime));
               }
               setUsedTime(typedValue);
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
        
            case PROP_ID_sessionId:{
               onInitProp(propId);
               this._sessionId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_title:{
               onInitProp(propId);
               this._title = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_bizObjName:{
               onInitProp(propId);
               this._bizObjName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_bizActionName:{
               onInitProp(propId);
               this._bizActionName = (java.lang.String)value;
               
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
        
            case PROP_ID_usedTime:{
               onInitProp(propId);
               this._usedTime = (java.lang.Long)value;
               
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
     * 标题: TITLE
     */
    public java.lang.String getTitle(){
         onPropGet(PROP_ID_title);
         return _title;
    }

    /**
     * 标题: TITLE
     */
    public void setTitle(java.lang.String value){
        if(onPropSet(PROP_ID_title,value)){
            this._title = value;
            internalClearRefs(PROP_ID_title);
            
        }
    }
    
    /**
     * 业务对象: BIZ_OBJ_NAME
     */
    public java.lang.String getBizObjName(){
         onPropGet(PROP_ID_bizObjName);
         return _bizObjName;
    }

    /**
     * 业务对象: BIZ_OBJ_NAME
     */
    public void setBizObjName(java.lang.String value){
        if(onPropSet(PROP_ID_bizObjName,value)){
            this._bizObjName = value;
            internalClearRefs(PROP_ID_bizObjName);
            
        }
    }
    
    /**
     * 业务操作: BIZ_ACTION_NAME
     */
    public java.lang.String getBizActionName(){
         onPropGet(PROP_ID_bizActionName);
         return _bizActionName;
    }

    /**
     * 业务操作: BIZ_ACTION_NAME
     */
    public void setBizActionName(java.lang.String value){
        if(onPropSet(PROP_ID_bizActionName,value)){
            this._bizActionName = value;
            internalClearRefs(PROP_ID_bizActionName);
            
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
       
}
// resume CPD analysis - CPD-ON
