package io.nop.retry.dao.entity._gen;

import io.nop.orm.model.IEntityModel;
import io.nop.orm.support.DynamicOrmEntity;
import io.nop.orm.support.OrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.orm.IOrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.api.core.convert.ConvertHelper;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;

import io.nop.retry.dao.entity.NopRetryDeadLetter;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  重试死信: nop_retry_dead_letter
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopRetryDeadLetter extends DynamicOrmEntity{
    
    /* 主键: SID VARCHAR */
    public static final String PROP_NAME_sid = "sid";
    public static final int PROP_ID_sid = 1;
    
    /* 命名空间ID: NAMESPACE_ID VARCHAR */
    public static final String PROP_NAME_namespaceId = "namespaceId";
    public static final int PROP_ID_namespaceId = 2;
    
    /* 组ID: GROUP_ID VARCHAR */
    public static final String PROP_NAME_groupId = "groupId";
    public static final int PROP_ID_groupId = 3;
    
    /* 策略ID: POLICY_ID VARCHAR */
    public static final String PROP_NAME_policyId = "policyId";
    public static final int PROP_ID_policyId = 4;
    
    /* 记录ID: RECORD_ID VARCHAR */
    public static final String PROP_NAME_recordId = "recordId";
    public static final int PROP_ID_recordId = 5;
    
    /* 幂等ID: IDEMPOTENT_ID VARCHAR */
    public static final String PROP_NAME_idempotentId = "idempotentId";
    public static final int PROP_ID_idempotentId = 6;
    
    /* 业务号: BIZ_NO VARCHAR */
    public static final String PROP_NAME_bizNo = "bizNo";
    public static final int PROP_ID_bizNo = 7;
    
    /* 执行器名称: EXECUTOR_NAME VARCHAR */
    public static final String PROP_NAME_executorName = "executorName";
    public static final int PROP_ID_executorName = 8;
    
    /* 请求参数: REQUEST_PAYLOAD VARCHAR */
    public static final String PROP_NAME_requestPayload = "requestPayload";
    public static final int PROP_ID_requestPayload = 9;
    
    /* 失败码: FAILURE_CODE VARCHAR */
    public static final String PROP_NAME_failureCode = "failureCode";
    public static final int PROP_ID_failureCode = 10;
    
    /* 失败消息: FAILURE_MESSAGE VARCHAR */
    public static final String PROP_NAME_failureMessage = "failureMessage";
    public static final int PROP_ID_failureMessage = 11;
    
    /* 错误堆栈: ERROR_STACK VARCHAR */
    public static final String PROP_NAME_errorStack = "errorStack";
    public static final int PROP_ID_errorStack = 12;
    
    /* 最终状态: FINAL_STATUS INTEGER */
    public static final String PROP_NAME_finalStatus = "finalStatus";
    public static final int PROP_ID_finalStatus = 13;
    
    /* 版本: VERSION INTEGER */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 14;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 15;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 16;
    
    /* 更新人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 17;
    
    /* 更新时间: UPDATE_TIME TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 18;
    
    /* 服务名: SERVICE_NAME VARCHAR */
    public static final String PROP_NAME_serviceName = "serviceName";
    public static final int PROP_ID_serviceName = 19;
    
    /* 服务方法: SERVICE_METHOD VARCHAR */
    public static final String PROP_NAME_serviceMethod = "serviceMethod";
    public static final int PROP_ID_serviceMethod = 20;
    

    private static int _PROP_ID_BOUND = 21;

    
    /* relation: 记录 */
    public static final String PROP_NAME_record = "record";
    
    /* component:  */
    public static final String PROP_NAME_requestPayloadComponent = "requestPayloadComponent";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_sid);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_sid};

    private static final String[] PROP_ID_TO_NAME = new String[21];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_sid] = PROP_NAME_sid;
          PROP_NAME_TO_ID.put(PROP_NAME_sid, PROP_ID_sid);
      
          PROP_ID_TO_NAME[PROP_ID_namespaceId] = PROP_NAME_namespaceId;
          PROP_NAME_TO_ID.put(PROP_NAME_namespaceId, PROP_ID_namespaceId);
      
          PROP_ID_TO_NAME[PROP_ID_groupId] = PROP_NAME_groupId;
          PROP_NAME_TO_ID.put(PROP_NAME_groupId, PROP_ID_groupId);
      
          PROP_ID_TO_NAME[PROP_ID_policyId] = PROP_NAME_policyId;
          PROP_NAME_TO_ID.put(PROP_NAME_policyId, PROP_ID_policyId);
      
          PROP_ID_TO_NAME[PROP_ID_recordId] = PROP_NAME_recordId;
          PROP_NAME_TO_ID.put(PROP_NAME_recordId, PROP_ID_recordId);
      
          PROP_ID_TO_NAME[PROP_ID_idempotentId] = PROP_NAME_idempotentId;
          PROP_NAME_TO_ID.put(PROP_NAME_idempotentId, PROP_ID_idempotentId);
      
          PROP_ID_TO_NAME[PROP_ID_bizNo] = PROP_NAME_bizNo;
          PROP_NAME_TO_ID.put(PROP_NAME_bizNo, PROP_ID_bizNo);
      
          PROP_ID_TO_NAME[PROP_ID_executorName] = PROP_NAME_executorName;
          PROP_NAME_TO_ID.put(PROP_NAME_executorName, PROP_ID_executorName);
      
          PROP_ID_TO_NAME[PROP_ID_requestPayload] = PROP_NAME_requestPayload;
          PROP_NAME_TO_ID.put(PROP_NAME_requestPayload, PROP_ID_requestPayload);
      
          PROP_ID_TO_NAME[PROP_ID_failureCode] = PROP_NAME_failureCode;
          PROP_NAME_TO_ID.put(PROP_NAME_failureCode, PROP_ID_failureCode);
      
          PROP_ID_TO_NAME[PROP_ID_failureMessage] = PROP_NAME_failureMessage;
          PROP_NAME_TO_ID.put(PROP_NAME_failureMessage, PROP_ID_failureMessage);
      
          PROP_ID_TO_NAME[PROP_ID_errorStack] = PROP_NAME_errorStack;
          PROP_NAME_TO_ID.put(PROP_NAME_errorStack, PROP_ID_errorStack);
      
          PROP_ID_TO_NAME[PROP_ID_finalStatus] = PROP_NAME_finalStatus;
          PROP_NAME_TO_ID.put(PROP_NAME_finalStatus, PROP_ID_finalStatus);
      
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
      
          PROP_ID_TO_NAME[PROP_ID_serviceName] = PROP_NAME_serviceName;
          PROP_NAME_TO_ID.put(PROP_NAME_serviceName, PROP_ID_serviceName);
      
          PROP_ID_TO_NAME[PROP_ID_serviceMethod] = PROP_NAME_serviceMethod;
          PROP_NAME_TO_ID.put(PROP_NAME_serviceMethod, PROP_ID_serviceMethod);
      
    }

    
    /* 主键: SID */
    private java.lang.String _sid;
    
    /* 命名空间ID: NAMESPACE_ID */
    private java.lang.String _namespaceId;
    
    /* 组ID: GROUP_ID */
    private java.lang.String _groupId;
    
    /* 策略ID: POLICY_ID */
    private java.lang.String _policyId;
    
    /* 记录ID: RECORD_ID */
    private java.lang.String _recordId;
    
    /* 幂等ID: IDEMPOTENT_ID */
    private java.lang.String _idempotentId;
    
    /* 业务号: BIZ_NO */
    private java.lang.String _bizNo;
    
    /* 执行器名称: EXECUTOR_NAME */
    private java.lang.String _executorName;
    
    /* 请求参数: REQUEST_PAYLOAD */
    private java.lang.String _requestPayload;
    
    /* 失败码: FAILURE_CODE */
    private java.lang.String _failureCode;
    
    /* 失败消息: FAILURE_MESSAGE */
    private java.lang.String _failureMessage;
    
    /* 错误堆栈: ERROR_STACK */
    private java.lang.String _errorStack;
    
    /* 最终状态: FINAL_STATUS */
    private java.lang.Integer _finalStatus;
    
    /* 版本: VERSION */
    private java.lang.Integer _version;
    
    /* 创建人: CREATED_BY */
    private java.lang.String _createdBy;
    
    /* 创建时间: CREATE_TIME */
    private java.sql.Timestamp _createTime;
    
    /* 更新人: UPDATED_BY */
    private java.lang.String _updatedBy;
    
    /* 更新时间: UPDATE_TIME */
    private java.sql.Timestamp _updateTime;
    
    /* 服务名: SERVICE_NAME */
    private java.lang.String _serviceName;
    
    /* 服务方法: SERVICE_METHOD */
    private java.lang.String _serviceMethod;
    

    public _NopRetryDeadLetter(){
        // for debug
    }

    protected NopRetryDeadLetter newInstance(){
        NopRetryDeadLetter entity = new NopRetryDeadLetter();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopRetryDeadLetter cloneInstance() {
        NopRetryDeadLetter entity = newInstance();
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
      return "io.nop.retry.dao.entity.NopRetryDeadLetter";
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
        
            case PROP_ID_namespaceId:
               return getNamespaceId();
        
            case PROP_ID_groupId:
               return getGroupId();
        
            case PROP_ID_policyId:
               return getPolicyId();
        
            case PROP_ID_recordId:
               return getRecordId();
        
            case PROP_ID_idempotentId:
               return getIdempotentId();
        
            case PROP_ID_bizNo:
               return getBizNo();
        
            case PROP_ID_executorName:
               return getExecutorName();
        
            case PROP_ID_requestPayload:
               return getRequestPayload();
        
            case PROP_ID_failureCode:
               return getFailureCode();
        
            case PROP_ID_failureMessage:
               return getFailureMessage();
        
            case PROP_ID_errorStack:
               return getErrorStack();
        
            case PROP_ID_finalStatus:
               return getFinalStatus();
        
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
        
            case PROP_ID_serviceName:
               return getServiceName();
        
            case PROP_ID_serviceMethod:
               return getServiceMethod();
        
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
        
            case PROP_ID_namespaceId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_namespaceId));
               }
               setNamespaceId(typedValue);
               break;
            }
        
            case PROP_ID_groupId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_groupId));
               }
               setGroupId(typedValue);
               break;
            }
        
            case PROP_ID_policyId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_policyId));
               }
               setPolicyId(typedValue);
               break;
            }
        
            case PROP_ID_recordId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_recordId));
               }
               setRecordId(typedValue);
               break;
            }
        
            case PROP_ID_idempotentId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_idempotentId));
               }
               setIdempotentId(typedValue);
               break;
            }
        
            case PROP_ID_bizNo:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_bizNo));
               }
               setBizNo(typedValue);
               break;
            }
        
            case PROP_ID_executorName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_executorName));
               }
               setExecutorName(typedValue);
               break;
            }
        
            case PROP_ID_requestPayload:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_requestPayload));
               }
               setRequestPayload(typedValue);
               break;
            }
        
            case PROP_ID_failureCode:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_failureCode));
               }
               setFailureCode(typedValue);
               break;
            }
        
            case PROP_ID_failureMessage:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_failureMessage));
               }
               setFailureMessage(typedValue);
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
        
            case PROP_ID_finalStatus:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_finalStatus));
               }
               setFinalStatus(typedValue);
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
        
            case PROP_ID_serviceName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_serviceName));
               }
               setServiceName(typedValue);
               break;
            }
        
            case PROP_ID_serviceMethod:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_serviceMethod));
               }
               setServiceMethod(typedValue);
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
        
            case PROP_ID_namespaceId:{
               onInitProp(propId);
               this._namespaceId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_groupId:{
               onInitProp(propId);
               this._groupId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_policyId:{
               onInitProp(propId);
               this._policyId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_recordId:{
               onInitProp(propId);
               this._recordId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_idempotentId:{
               onInitProp(propId);
               this._idempotentId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_bizNo:{
               onInitProp(propId);
               this._bizNo = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_executorName:{
               onInitProp(propId);
               this._executorName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_requestPayload:{
               onInitProp(propId);
               this._requestPayload = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_failureCode:{
               onInitProp(propId);
               this._failureCode = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_failureMessage:{
               onInitProp(propId);
               this._failureMessage = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_errorStack:{
               onInitProp(propId);
               this._errorStack = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_finalStatus:{
               onInitProp(propId);
               this._finalStatus = (java.lang.Integer)value;
               
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
        
            case PROP_ID_serviceName:{
               onInitProp(propId);
               this._serviceName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_serviceMethod:{
               onInitProp(propId);
               this._serviceMethod = (java.lang.String)value;
               
               break;
            }
        
           default:
              super.orm_internalSet(propId,value);
        }
    }

    
    /**
     * 主键: SID
     */
    public final java.lang.String getSid(){
         onPropGet(PROP_ID_sid);
         return _sid;
    }

    /**
     * 主键: SID
     */
    public final void setSid(java.lang.String value){
        if(onPropSet(PROP_ID_sid,value)){
            this._sid = value;
            internalClearRefs(PROP_ID_sid);
            orm_id();
        }
    }
    
    /**
     * 命名空间ID: NAMESPACE_ID
     */
    public final java.lang.String getNamespaceId(){
         onPropGet(PROP_ID_namespaceId);
         return _namespaceId;
    }

    /**
     * 命名空间ID: NAMESPACE_ID
     */
    public final void setNamespaceId(java.lang.String value){
        if(onPropSet(PROP_ID_namespaceId,value)){
            this._namespaceId = value;
            internalClearRefs(PROP_ID_namespaceId);
            
        }
    }
    
    /**
     * 组ID: GROUP_ID
     */
    public final java.lang.String getGroupId(){
         onPropGet(PROP_ID_groupId);
         return _groupId;
    }

    /**
     * 组ID: GROUP_ID
     */
    public final void setGroupId(java.lang.String value){
        if(onPropSet(PROP_ID_groupId,value)){
            this._groupId = value;
            internalClearRefs(PROP_ID_groupId);
            
        }
    }
    
    /**
     * 策略ID: POLICY_ID
     */
    public final java.lang.String getPolicyId(){
         onPropGet(PROP_ID_policyId);
         return _policyId;
    }

    /**
     * 策略ID: POLICY_ID
     */
    public final void setPolicyId(java.lang.String value){
        if(onPropSet(PROP_ID_policyId,value)){
            this._policyId = value;
            internalClearRefs(PROP_ID_policyId);
            
        }
    }
    
    /**
     * 记录ID: RECORD_ID
     */
    public final java.lang.String getRecordId(){
         onPropGet(PROP_ID_recordId);
         return _recordId;
    }

    /**
     * 记录ID: RECORD_ID
     */
    public final void setRecordId(java.lang.String value){
        if(onPropSet(PROP_ID_recordId,value)){
            this._recordId = value;
            internalClearRefs(PROP_ID_recordId);
            
        }
    }
    
    /**
     * 幂等ID: IDEMPOTENT_ID
     */
    public final java.lang.String getIdempotentId(){
         onPropGet(PROP_ID_idempotentId);
         return _idempotentId;
    }

    /**
     * 幂等ID: IDEMPOTENT_ID
     */
    public final void setIdempotentId(java.lang.String value){
        if(onPropSet(PROP_ID_idempotentId,value)){
            this._idempotentId = value;
            internalClearRefs(PROP_ID_idempotentId);
            
        }
    }
    
    /**
     * 业务号: BIZ_NO
     */
    public final java.lang.String getBizNo(){
         onPropGet(PROP_ID_bizNo);
         return _bizNo;
    }

    /**
     * 业务号: BIZ_NO
     */
    public final void setBizNo(java.lang.String value){
        if(onPropSet(PROP_ID_bizNo,value)){
            this._bizNo = value;
            internalClearRefs(PROP_ID_bizNo);
            
        }
    }
    
    /**
     * 执行器名称: EXECUTOR_NAME
     */
    public final java.lang.String getExecutorName(){
         onPropGet(PROP_ID_executorName);
         return _executorName;
    }

    /**
     * 执行器名称: EXECUTOR_NAME
     */
    public final void setExecutorName(java.lang.String value){
        if(onPropSet(PROP_ID_executorName,value)){
            this._executorName = value;
            internalClearRefs(PROP_ID_executorName);
            
        }
    }
    
    /**
     * 请求参数: REQUEST_PAYLOAD
     */
    public final java.lang.String getRequestPayload(){
         onPropGet(PROP_ID_requestPayload);
         return _requestPayload;
    }

    /**
     * 请求参数: REQUEST_PAYLOAD
     */
    public final void setRequestPayload(java.lang.String value){
        if(onPropSet(PROP_ID_requestPayload,value)){
            this._requestPayload = value;
            internalClearRefs(PROP_ID_requestPayload);
            
        }
    }
    
    /**
     * 失败码: FAILURE_CODE
     */
    public final java.lang.String getFailureCode(){
         onPropGet(PROP_ID_failureCode);
         return _failureCode;
    }

    /**
     * 失败码: FAILURE_CODE
     */
    public final void setFailureCode(java.lang.String value){
        if(onPropSet(PROP_ID_failureCode,value)){
            this._failureCode = value;
            internalClearRefs(PROP_ID_failureCode);
            
        }
    }
    
    /**
     * 失败消息: FAILURE_MESSAGE
     */
    public final java.lang.String getFailureMessage(){
         onPropGet(PROP_ID_failureMessage);
         return _failureMessage;
    }

    /**
     * 失败消息: FAILURE_MESSAGE
     */
    public final void setFailureMessage(java.lang.String value){
        if(onPropSet(PROP_ID_failureMessage,value)){
            this._failureMessage = value;
            internalClearRefs(PROP_ID_failureMessage);
            
        }
    }
    
    /**
     * 错误堆栈: ERROR_STACK
     */
    public final java.lang.String getErrorStack(){
         onPropGet(PROP_ID_errorStack);
         return _errorStack;
    }

    /**
     * 错误堆栈: ERROR_STACK
     */
    public final void setErrorStack(java.lang.String value){
        if(onPropSet(PROP_ID_errorStack,value)){
            this._errorStack = value;
            internalClearRefs(PROP_ID_errorStack);
            
        }
    }
    
    /**
     * 最终状态: FINAL_STATUS
     */
    public final java.lang.Integer getFinalStatus(){
         onPropGet(PROP_ID_finalStatus);
         return _finalStatus;
    }

    /**
     * 最终状态: FINAL_STATUS
     */
    public final void setFinalStatus(java.lang.Integer value){
        if(onPropSet(PROP_ID_finalStatus,value)){
            this._finalStatus = value;
            internalClearRefs(PROP_ID_finalStatus);
            
        }
    }
    
    /**
     * 版本: VERSION
     */
    public final java.lang.Integer getVersion(){
         onPropGet(PROP_ID_version);
         return _version;
    }

    /**
     * 版本: VERSION
     */
    public final void setVersion(java.lang.Integer value){
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
     * 更新人: UPDATED_BY
     */
    public final java.lang.String getUpdatedBy(){
         onPropGet(PROP_ID_updatedBy);
         return _updatedBy;
    }

    /**
     * 更新人: UPDATED_BY
     */
    public final void setUpdatedBy(java.lang.String value){
        if(onPropSet(PROP_ID_updatedBy,value)){
            this._updatedBy = value;
            internalClearRefs(PROP_ID_updatedBy);
            
        }
    }
    
    /**
     * 更新时间: UPDATE_TIME
     */
    public final java.sql.Timestamp getUpdateTime(){
         onPropGet(PROP_ID_updateTime);
         return _updateTime;
    }

    /**
     * 更新时间: UPDATE_TIME
     */
    public final void setUpdateTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_updateTime,value)){
            this._updateTime = value;
            internalClearRefs(PROP_ID_updateTime);
            
        }
    }
    
    /**
     * 服务名: SERVICE_NAME
     */
    public final java.lang.String getServiceName(){
         onPropGet(PROP_ID_serviceName);
         return _serviceName;
    }

    /**
     * 服务名: SERVICE_NAME
     */
    public final void setServiceName(java.lang.String value){
        if(onPropSet(PROP_ID_serviceName,value)){
            this._serviceName = value;
            internalClearRefs(PROP_ID_serviceName);
            
        }
    }
    
    /**
     * 服务方法: SERVICE_METHOD
     */
    public final java.lang.String getServiceMethod(){
         onPropGet(PROP_ID_serviceMethod);
         return _serviceMethod;
    }

    /**
     * 服务方法: SERVICE_METHOD
     */
    public final void setServiceMethod(java.lang.String value){
        if(onPropSet(PROP_ID_serviceMethod,value)){
            this._serviceMethod = value;
            internalClearRefs(PROP_ID_serviceMethod);
            
        }
    }
    
    /**
     * 记录
     */
    public final io.nop.retry.dao.entity.NopRetryRecord getRecord(){
       return (io.nop.retry.dao.entity.NopRetryRecord)internalGetRefEntity(PROP_NAME_record);
    }

    public final void setRecord(io.nop.retry.dao.entity.NopRetryRecord refEntity){
   
           if(refEntity == null){
           
                   this.setRecordId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_record, refEntity,()->{
           
                           this.setRecordId(refEntity.getSid());
                       
           });
           }
       
    }
       
   private io.nop.orm.component.JsonOrmComponent _requestPayloadComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_requestPayloadComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_requestPayloadComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_requestPayload);
      
   }

   public final io.nop.orm.component.JsonOrmComponent getRequestPayloadComponent(){
      if(_requestPayloadComponent == null){
          _requestPayloadComponent = new io.nop.orm.component.JsonOrmComponent();
          _requestPayloadComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_requestPayloadComponent);
      }
      return _requestPayloadComponent;
   }

}
// resume CPD analysis - CPD-ON
