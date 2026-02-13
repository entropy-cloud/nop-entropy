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

import io.nop.retry.dao.entity.NopRetryRecord;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  重试记录: nop_retry_record
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopRetryRecord extends DynamicOrmEntity{
    
    /* 主键: SID VARCHAR */
    public static final String PROP_NAME_sid = "sid";
    public static final int PROP_ID_sid = 1;
    
    /* 命名空间ID: NAMESPACE_ID VARCHAR */
    public static final String PROP_NAME_namespaceId = "namespaceId";
    public static final int PROP_ID_namespaceId = 2;
    
    /* 组ID: GROUP_ID VARCHAR */
    public static final String PROP_NAME_groupId = "groupId";
    public static final int PROP_ID_groupId = 3;
    
    /* 模板ID: TEMPLATE_ID VARCHAR */
    public static final String PROP_NAME_templateId = "templateId";
    public static final int PROP_ID_templateId = 4;
    
    /* 幂等ID: IDEMPOTENT_ID VARCHAR */
    public static final String PROP_NAME_idempotentId = "idempotentId";
    public static final int PROP_ID_idempotentId = 5;
    
    /* 业务号: BIZ_NO VARCHAR */
    public static final String PROP_NAME_bizNo = "bizNo";
    public static final int PROP_ID_bizNo = 6;
    
    /* 任务类型: TASK_TYPE INTEGER */
    public static final String PROP_NAME_taskType = "taskType";
    public static final int PROP_ID_taskType = 7;
    
    /* 状态: STATUS INTEGER */
    public static final String PROP_NAME_status = "status";
    public static final int PROP_ID_status = 8;
    
    /* 重试次数: RETRY_COUNT INTEGER */
    public static final String PROP_NAME_retryCount = "retryCount";
    public static final int PROP_ID_retryCount = 9;
    
    /* 最大重试次数: MAX_RETRY_COUNT INTEGER */
    public static final String PROP_NAME_maxRetryCount = "maxRetryCount";
    public static final int PROP_ID_maxRetryCount = 10;
    
    /* 下次触发时间: NEXT_TRIGGER_TIME TIMESTAMP */
    public static final String PROP_NAME_nextTriggerTime = "nextTriggerTime";
    public static final int PROP_ID_nextTriggerTime = 11;
    
    /* 分区索引: PARTITION_INDEX INTEGER */
    public static final String PROP_NAME_partitionIndex = "partitionIndex";
    public static final int PROP_ID_partitionIndex = 12;
    
    /* 执行器名称: EXECUTOR_NAME VARCHAR */
    public static final String PROP_NAME_executorName = "executorName";
    public static final int PROP_ID_executorName = 13;
    
    /* 序列化器名称: SERIALIZER_NAME VARCHAR */
    public static final String PROP_NAME_serializerName = "serializerName";
    public static final int PROP_ID_serializerName = 14;
    
    /* 请求参数: REQUEST_PAYLOAD VARCHAR */
    public static final String PROP_NAME_requestPayload = "requestPayload";
    public static final int PROP_ID_requestPayload = 15;
    
    /* 上下文参数: CONTEXT_PAYLOAD VARCHAR */
    public static final String PROP_NAME_contextPayload = "contextPayload";
    public static final int PROP_ID_contextPayload = 16;
    
    /* 版本: VERSION INTEGER */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 17;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 18;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 19;
    
    /* 更新人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 20;
    
    /* 更新时间: UPDATE_TIME TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 21;
    

    private static int _PROP_ID_BOUND = 22;

    
    /* relation: 模板 */
    public static final String PROP_NAME_template = "template";
    
    /* relation: 重试尝试 */
    public static final String PROP_NAME_attempts = "attempts";
    
    /* relation:  */
    public static final String PROP_NAME_deadLetter = "deadLetter";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_sid);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_sid};

    private static final String[] PROP_ID_TO_NAME = new String[22];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_sid] = PROP_NAME_sid;
          PROP_NAME_TO_ID.put(PROP_NAME_sid, PROP_ID_sid);
      
          PROP_ID_TO_NAME[PROP_ID_namespaceId] = PROP_NAME_namespaceId;
          PROP_NAME_TO_ID.put(PROP_NAME_namespaceId, PROP_ID_namespaceId);
      
          PROP_ID_TO_NAME[PROP_ID_groupId] = PROP_NAME_groupId;
          PROP_NAME_TO_ID.put(PROP_NAME_groupId, PROP_ID_groupId);
      
          PROP_ID_TO_NAME[PROP_ID_templateId] = PROP_NAME_templateId;
          PROP_NAME_TO_ID.put(PROP_NAME_templateId, PROP_ID_templateId);
      
          PROP_ID_TO_NAME[PROP_ID_idempotentId] = PROP_NAME_idempotentId;
          PROP_NAME_TO_ID.put(PROP_NAME_idempotentId, PROP_ID_idempotentId);
      
          PROP_ID_TO_NAME[PROP_ID_bizNo] = PROP_NAME_bizNo;
          PROP_NAME_TO_ID.put(PROP_NAME_bizNo, PROP_ID_bizNo);
      
          PROP_ID_TO_NAME[PROP_ID_taskType] = PROP_NAME_taskType;
          PROP_NAME_TO_ID.put(PROP_NAME_taskType, PROP_ID_taskType);
      
          PROP_ID_TO_NAME[PROP_ID_status] = PROP_NAME_status;
          PROP_NAME_TO_ID.put(PROP_NAME_status, PROP_ID_status);
      
          PROP_ID_TO_NAME[PROP_ID_retryCount] = PROP_NAME_retryCount;
          PROP_NAME_TO_ID.put(PROP_NAME_retryCount, PROP_ID_retryCount);
      
          PROP_ID_TO_NAME[PROP_ID_maxRetryCount] = PROP_NAME_maxRetryCount;
          PROP_NAME_TO_ID.put(PROP_NAME_maxRetryCount, PROP_ID_maxRetryCount);
      
          PROP_ID_TO_NAME[PROP_ID_nextTriggerTime] = PROP_NAME_nextTriggerTime;
          PROP_NAME_TO_ID.put(PROP_NAME_nextTriggerTime, PROP_ID_nextTriggerTime);
      
          PROP_ID_TO_NAME[PROP_ID_partitionIndex] = PROP_NAME_partitionIndex;
          PROP_NAME_TO_ID.put(PROP_NAME_partitionIndex, PROP_ID_partitionIndex);
      
          PROP_ID_TO_NAME[PROP_ID_executorName] = PROP_NAME_executorName;
          PROP_NAME_TO_ID.put(PROP_NAME_executorName, PROP_ID_executorName);
      
          PROP_ID_TO_NAME[PROP_ID_serializerName] = PROP_NAME_serializerName;
          PROP_NAME_TO_ID.put(PROP_NAME_serializerName, PROP_ID_serializerName);
      
          PROP_ID_TO_NAME[PROP_ID_requestPayload] = PROP_NAME_requestPayload;
          PROP_NAME_TO_ID.put(PROP_NAME_requestPayload, PROP_ID_requestPayload);
      
          PROP_ID_TO_NAME[PROP_ID_contextPayload] = PROP_NAME_contextPayload;
          PROP_NAME_TO_ID.put(PROP_NAME_contextPayload, PROP_ID_contextPayload);
      
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

    
    /* 主键: SID */
    private java.lang.String _sid;
    
    /* 命名空间ID: NAMESPACE_ID */
    private java.lang.String _namespaceId;
    
    /* 组ID: GROUP_ID */
    private java.lang.String _groupId;
    
    /* 模板ID: TEMPLATE_ID */
    private java.lang.String _templateId;
    
    /* 幂等ID: IDEMPOTENT_ID */
    private java.lang.String _idempotentId;
    
    /* 业务号: BIZ_NO */
    private java.lang.String _bizNo;
    
    /* 任务类型: TASK_TYPE */
    private java.lang.Integer _taskType;
    
    /* 状态: STATUS */
    private java.lang.Integer _status;
    
    /* 重试次数: RETRY_COUNT */
    private java.lang.Integer _retryCount;
    
    /* 最大重试次数: MAX_RETRY_COUNT */
    private java.lang.Integer _maxRetryCount;
    
    /* 下次触发时间: NEXT_TRIGGER_TIME */
    private java.sql.Timestamp _nextTriggerTime;
    
    /* 分区索引: PARTITION_INDEX */
    private java.lang.Integer _partitionIndex;
    
    /* 执行器名称: EXECUTOR_NAME */
    private java.lang.String _executorName;
    
    /* 序列化器名称: SERIALIZER_NAME */
    private java.lang.String _serializerName;
    
    /* 请求参数: REQUEST_PAYLOAD */
    private java.lang.String _requestPayload;
    
    /* 上下文参数: CONTEXT_PAYLOAD */
    private java.lang.String _contextPayload;
    
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
    

    public _NopRetryRecord(){
        // for debug
    }

    protected NopRetryRecord newInstance(){
        NopRetryRecord entity = new NopRetryRecord();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopRetryRecord cloneInstance() {
        NopRetryRecord entity = newInstance();
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
      return "io.nop.retry.dao.entity.NopRetryRecord";
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
        
            case PROP_ID_templateId:
               return getTemplateId();
        
            case PROP_ID_idempotentId:
               return getIdempotentId();
        
            case PROP_ID_bizNo:
               return getBizNo();
        
            case PROP_ID_taskType:
               return getTaskType();
        
            case PROP_ID_status:
               return getStatus();
        
            case PROP_ID_retryCount:
               return getRetryCount();
        
            case PROP_ID_maxRetryCount:
               return getMaxRetryCount();
        
            case PROP_ID_nextTriggerTime:
               return getNextTriggerTime();
        
            case PROP_ID_partitionIndex:
               return getPartitionIndex();
        
            case PROP_ID_executorName:
               return getExecutorName();
        
            case PROP_ID_serializerName:
               return getSerializerName();
        
            case PROP_ID_requestPayload:
               return getRequestPayload();
        
            case PROP_ID_contextPayload:
               return getContextPayload();
        
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
        
            case PROP_ID_templateId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_templateId));
               }
               setTemplateId(typedValue);
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
        
            case PROP_ID_taskType:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_taskType));
               }
               setTaskType(typedValue);
               break;
            }
        
            case PROP_ID_status:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_status));
               }
               setStatus(typedValue);
               break;
            }
        
            case PROP_ID_retryCount:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_retryCount));
               }
               setRetryCount(typedValue);
               break;
            }
        
            case PROP_ID_maxRetryCount:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_maxRetryCount));
               }
               setMaxRetryCount(typedValue);
               break;
            }
        
            case PROP_ID_nextTriggerTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_nextTriggerTime));
               }
               setNextTriggerTime(typedValue);
               break;
            }
        
            case PROP_ID_partitionIndex:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_partitionIndex));
               }
               setPartitionIndex(typedValue);
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
        
            case PROP_ID_serializerName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_serializerName));
               }
               setSerializerName(typedValue);
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
        
            case PROP_ID_contextPayload:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_contextPayload));
               }
               setContextPayload(typedValue);
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
        
            case PROP_ID_templateId:{
               onInitProp(propId);
               this._templateId = (java.lang.String)value;
               
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
        
            case PROP_ID_taskType:{
               onInitProp(propId);
               this._taskType = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_status:{
               onInitProp(propId);
               this._status = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_retryCount:{
               onInitProp(propId);
               this._retryCount = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_maxRetryCount:{
               onInitProp(propId);
               this._maxRetryCount = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_nextTriggerTime:{
               onInitProp(propId);
               this._nextTriggerTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_partitionIndex:{
               onInitProp(propId);
               this._partitionIndex = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_executorName:{
               onInitProp(propId);
               this._executorName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_serializerName:{
               onInitProp(propId);
               this._serializerName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_requestPayload:{
               onInitProp(propId);
               this._requestPayload = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_contextPayload:{
               onInitProp(propId);
               this._contextPayload = (java.lang.String)value;
               
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
     * 模板ID: TEMPLATE_ID
     */
    public final java.lang.String getTemplateId(){
         onPropGet(PROP_ID_templateId);
         return _templateId;
    }

    /**
     * 模板ID: TEMPLATE_ID
     */
    public final void setTemplateId(java.lang.String value){
        if(onPropSet(PROP_ID_templateId,value)){
            this._templateId = value;
            internalClearRefs(PROP_ID_templateId);
            
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
     * 任务类型: TASK_TYPE
     */
    public final java.lang.Integer getTaskType(){
         onPropGet(PROP_ID_taskType);
         return _taskType;
    }

    /**
     * 任务类型: TASK_TYPE
     */
    public final void setTaskType(java.lang.Integer value){
        if(onPropSet(PROP_ID_taskType,value)){
            this._taskType = value;
            internalClearRefs(PROP_ID_taskType);
            
        }
    }
    
    /**
     * 状态: STATUS
     */
    public final java.lang.Integer getStatus(){
         onPropGet(PROP_ID_status);
         return _status;
    }

    /**
     * 状态: STATUS
     */
    public final void setStatus(java.lang.Integer value){
        if(onPropSet(PROP_ID_status,value)){
            this._status = value;
            internalClearRefs(PROP_ID_status);
            
        }
    }
    
    /**
     * 重试次数: RETRY_COUNT
     */
    public final java.lang.Integer getRetryCount(){
         onPropGet(PROP_ID_retryCount);
         return _retryCount;
    }

    /**
     * 重试次数: RETRY_COUNT
     */
    public final void setRetryCount(java.lang.Integer value){
        if(onPropSet(PROP_ID_retryCount,value)){
            this._retryCount = value;
            internalClearRefs(PROP_ID_retryCount);
            
        }
    }
    
    /**
     * 最大重试次数: MAX_RETRY_COUNT
     */
    public final java.lang.Integer getMaxRetryCount(){
         onPropGet(PROP_ID_maxRetryCount);
         return _maxRetryCount;
    }

    /**
     * 最大重试次数: MAX_RETRY_COUNT
     */
    public final void setMaxRetryCount(java.lang.Integer value){
        if(onPropSet(PROP_ID_maxRetryCount,value)){
            this._maxRetryCount = value;
            internalClearRefs(PROP_ID_maxRetryCount);
            
        }
    }
    
    /**
     * 下次触发时间: NEXT_TRIGGER_TIME
     */
    public final java.sql.Timestamp getNextTriggerTime(){
         onPropGet(PROP_ID_nextTriggerTime);
         return _nextTriggerTime;
    }

    /**
     * 下次触发时间: NEXT_TRIGGER_TIME
     */
    public final void setNextTriggerTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_nextTriggerTime,value)){
            this._nextTriggerTime = value;
            internalClearRefs(PROP_ID_nextTriggerTime);
            
        }
    }
    
    /**
     * 分区索引: PARTITION_INDEX
     */
    public final java.lang.Integer getPartitionIndex(){
         onPropGet(PROP_ID_partitionIndex);
         return _partitionIndex;
    }

    /**
     * 分区索引: PARTITION_INDEX
     */
    public final void setPartitionIndex(java.lang.Integer value){
        if(onPropSet(PROP_ID_partitionIndex,value)){
            this._partitionIndex = value;
            internalClearRefs(PROP_ID_partitionIndex);
            
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
     * 序列化器名称: SERIALIZER_NAME
     */
    public final java.lang.String getSerializerName(){
         onPropGet(PROP_ID_serializerName);
         return _serializerName;
    }

    /**
     * 序列化器名称: SERIALIZER_NAME
     */
    public final void setSerializerName(java.lang.String value){
        if(onPropSet(PROP_ID_serializerName,value)){
            this._serializerName = value;
            internalClearRefs(PROP_ID_serializerName);
            
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
     * 上下文参数: CONTEXT_PAYLOAD
     */
    public final java.lang.String getContextPayload(){
         onPropGet(PROP_ID_contextPayload);
         return _contextPayload;
    }

    /**
     * 上下文参数: CONTEXT_PAYLOAD
     */
    public final void setContextPayload(java.lang.String value){
        if(onPropSet(PROP_ID_contextPayload,value)){
            this._contextPayload = value;
            internalClearRefs(PROP_ID_contextPayload);
            
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
     * 模板
     */
    public final io.nop.retry.dao.entity.NopRetryTemplate getTemplate(){
       return (io.nop.retry.dao.entity.NopRetryTemplate)internalGetRefEntity(PROP_NAME_template);
    }

    public final void setTemplate(io.nop.retry.dao.entity.NopRetryTemplate refEntity){
   
           if(refEntity == null){
           
                   this.setTemplateId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_template, refEntity,()->{
           
                           this.setTemplateId(refEntity.getSid());
                       
           });
           }
       
    }
       
    private final OrmEntitySet<io.nop.retry.dao.entity.NopRetryAttempt> _attempts = new OrmEntitySet<>(this, PROP_NAME_attempts,
        io.nop.retry.dao.entity.NopRetryAttempt.PROP_NAME_record, null,io.nop.retry.dao.entity.NopRetryAttempt.class);

    /**
     * 重试尝试。 refPropName: record, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<io.nop.retry.dao.entity.NopRetryAttempt> getAttempts(){
       return _attempts;
    }
       
    private final OrmEntitySet<io.nop.retry.dao.entity.NopRetryDeadLetter> _deadLetter = new OrmEntitySet<>(this, PROP_NAME_deadLetter,
        io.nop.retry.dao.entity.NopRetryDeadLetter.PROP_NAME_record, null,io.nop.retry.dao.entity.NopRetryDeadLetter.class);

    /**
     * 。 refPropName: record, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<io.nop.retry.dao.entity.NopRetryDeadLetter> getDeadLetter(){
       return _deadLetter;
    }
       
}
// resume CPD analysis - CPD-ON
