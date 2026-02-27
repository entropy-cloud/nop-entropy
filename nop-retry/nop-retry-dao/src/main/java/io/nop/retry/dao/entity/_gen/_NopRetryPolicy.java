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

import io.nop.retry.dao.entity.NopRetryPolicy;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  重试策略: nop_retry_policy
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopRetryPolicy extends DynamicOrmEntity{
    
    /* 主键: SID VARCHAR */
    public static final String PROP_NAME_sid = "sid";
    public static final int PROP_ID_sid = 1;
    
    /* 命名空间ID: NAMESPACE_ID VARCHAR */
    public static final String PROP_NAME_namespaceId = "namespaceId";
    public static final int PROP_ID_namespaceId = 2;
    
    /* 组ID: GROUP_ID VARCHAR */
    public static final String PROP_NAME_groupId = "groupId";
    public static final int PROP_ID_groupId = 3;
    
    /* 策略名称: NAME VARCHAR */
    public static final String PROP_NAME_name = "name";
    public static final int PROP_ID_name = 4;
    
    /* 状态: STATUS VARCHAR */
    public static final String PROP_NAME_status = "status";
    public static final int PROP_ID_status = 5;
    
    /* 保存记录策略: SAVE_RECORD_STRATEGY INTEGER */
    public static final String PROP_NAME_saveRecordStrategy = "saveRecordStrategy";
    public static final int PROP_ID_saveRecordStrategy = 6;
    
    /* 立刻重试次数: IMMEDIATE_RETRY_COUNT INTEGER */
    public static final String PROP_NAME_immediateRetryCount = "immediateRetryCount";
    public static final int PROP_ID_immediateRetryCount = 7;
    
    /* 立刻重试间隔(毫秒): IMMEDIATE_RETRY_INTERVAL_MS BIGINT */
    public static final String PROP_NAME_immediateRetryIntervalMs = "immediateRetryIntervalMs";
    public static final int PROP_ID_immediateRetryIntervalMs = 8;
    
    /* 最大重试次数: MAX_RETRY_COUNT INTEGER */
    public static final String PROP_NAME_maxRetryCount = "maxRetryCount";
    public static final int PROP_ID_maxRetryCount = 9;
    
    /* 退避策略: BACKOFF_STRATEGY INTEGER */
    public static final String PROP_NAME_backoffStrategy = "backoffStrategy";
    public static final int PROP_ID_backoffStrategy = 10;
    
    /* 初始间隔(毫秒): INITIAL_INTERVAL_MS BIGINT */
    public static final String PROP_NAME_initialIntervalMs = "initialIntervalMs";
    public static final int PROP_ID_initialIntervalMs = 11;
    
    /* 最大间隔(毫秒): MAX_INTERVAL_MS BIGINT */
    public static final String PROP_NAME_maxIntervalMs = "maxIntervalMs";
    public static final int PROP_ID_maxIntervalMs = 12;
    
    /* 抖动比例: JITTER_RATIO DECIMAL */
    public static final String PROP_NAME_jitterRatio = "jitterRatio";
    public static final int PROP_ID_jitterRatio = 13;
    
    /* 执行超时(秒): EXECUTION_TIMEOUT_SECONDS INTEGER */
    public static final String PROP_NAME_executionTimeoutSeconds = "executionTimeoutSeconds";
    public static final int PROP_ID_executionTimeoutSeconds = 14;
    
    /* 截止超时(毫秒): DEADLINE_TIMEOUT_MS BIGINT */
    public static final String PROP_NAME_deadlineTimeoutMs = "deadlineTimeoutMs";
    public static final int PROP_ID_deadlineTimeoutMs = 15;
    
    /* 阻塞策略: BLOCK_STRATEGY INTEGER */
    public static final String PROP_NAME_blockStrategy = "blockStrategy";
    public static final int PROP_ID_blockStrategy = 16;
    
    /* 启用回调: CALLBACK_ENABLED VARCHAR */
    public static final String PROP_NAME_callbackEnabled = "callbackEnabled";
    public static final int PROP_ID_callbackEnabled = 17;
    
    /* 回调触发类型: CALLBACK_TRIGGER_TYPE INTEGER */
    public static final String PROP_NAME_callbackTriggerType = "callbackTriggerType";
    public static final int PROP_ID_callbackTriggerType = 18;
    
    /* 回调策略ID: CALLBACK_POLICY_ID VARCHAR */
    public static final String PROP_NAME_callbackPolicyId = "callbackPolicyId";
    public static final int PROP_ID_callbackPolicyId = 19;
    
    /* 所有者ID: OWNER_ID VARCHAR */
    public static final String PROP_NAME_ownerId = "ownerId";
    public static final int PROP_ID_ownerId = 20;
    
    /* 描述: DESCRIPTION VARCHAR */
    public static final String PROP_NAME_description = "description";
    public static final int PROP_ID_description = 21;
    
    /* 版本: VERSION INTEGER */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 22;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 23;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 24;
    
    /* 更新人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 25;
    
    /* 更新时间: UPDATE_TIME TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 26;
    
    /* 执行中锁定超时(毫秒): RETRYING_TIMEOUT_MS BIGINT */
    public static final String PROP_NAME_retryingTimeoutMs = "retryingTimeoutMs";
    public static final int PROP_ID_retryingTimeoutMs = 28;
    

    private static int _PROP_ID_BOUND = 29;

    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_sid);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_sid};

    private static final String[] PROP_ID_TO_NAME = new String[29];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_sid] = PROP_NAME_sid;
          PROP_NAME_TO_ID.put(PROP_NAME_sid, PROP_ID_sid);
      
          PROP_ID_TO_NAME[PROP_ID_namespaceId] = PROP_NAME_namespaceId;
          PROP_NAME_TO_ID.put(PROP_NAME_namespaceId, PROP_ID_namespaceId);
      
          PROP_ID_TO_NAME[PROP_ID_groupId] = PROP_NAME_groupId;
          PROP_NAME_TO_ID.put(PROP_NAME_groupId, PROP_ID_groupId);
      
          PROP_ID_TO_NAME[PROP_ID_name] = PROP_NAME_name;
          PROP_NAME_TO_ID.put(PROP_NAME_name, PROP_ID_name);
      
          PROP_ID_TO_NAME[PROP_ID_status] = PROP_NAME_status;
          PROP_NAME_TO_ID.put(PROP_NAME_status, PROP_ID_status);
      
          PROP_ID_TO_NAME[PROP_ID_saveRecordStrategy] = PROP_NAME_saveRecordStrategy;
          PROP_NAME_TO_ID.put(PROP_NAME_saveRecordStrategy, PROP_ID_saveRecordStrategy);
      
          PROP_ID_TO_NAME[PROP_ID_immediateRetryCount] = PROP_NAME_immediateRetryCount;
          PROP_NAME_TO_ID.put(PROP_NAME_immediateRetryCount, PROP_ID_immediateRetryCount);
      
          PROP_ID_TO_NAME[PROP_ID_immediateRetryIntervalMs] = PROP_NAME_immediateRetryIntervalMs;
          PROP_NAME_TO_ID.put(PROP_NAME_immediateRetryIntervalMs, PROP_ID_immediateRetryIntervalMs);
      
          PROP_ID_TO_NAME[PROP_ID_maxRetryCount] = PROP_NAME_maxRetryCount;
          PROP_NAME_TO_ID.put(PROP_NAME_maxRetryCount, PROP_ID_maxRetryCount);
      
          PROP_ID_TO_NAME[PROP_ID_backoffStrategy] = PROP_NAME_backoffStrategy;
          PROP_NAME_TO_ID.put(PROP_NAME_backoffStrategy, PROP_ID_backoffStrategy);
      
          PROP_ID_TO_NAME[PROP_ID_initialIntervalMs] = PROP_NAME_initialIntervalMs;
          PROP_NAME_TO_ID.put(PROP_NAME_initialIntervalMs, PROP_ID_initialIntervalMs);
      
          PROP_ID_TO_NAME[PROP_ID_maxIntervalMs] = PROP_NAME_maxIntervalMs;
          PROP_NAME_TO_ID.put(PROP_NAME_maxIntervalMs, PROP_ID_maxIntervalMs);
      
          PROP_ID_TO_NAME[PROP_ID_jitterRatio] = PROP_NAME_jitterRatio;
          PROP_NAME_TO_ID.put(PROP_NAME_jitterRatio, PROP_ID_jitterRatio);
      
          PROP_ID_TO_NAME[PROP_ID_executionTimeoutSeconds] = PROP_NAME_executionTimeoutSeconds;
          PROP_NAME_TO_ID.put(PROP_NAME_executionTimeoutSeconds, PROP_ID_executionTimeoutSeconds);
      
          PROP_ID_TO_NAME[PROP_ID_deadlineTimeoutMs] = PROP_NAME_deadlineTimeoutMs;
          PROP_NAME_TO_ID.put(PROP_NAME_deadlineTimeoutMs, PROP_ID_deadlineTimeoutMs);
      
          PROP_ID_TO_NAME[PROP_ID_blockStrategy] = PROP_NAME_blockStrategy;
          PROP_NAME_TO_ID.put(PROP_NAME_blockStrategy, PROP_ID_blockStrategy);
      
          PROP_ID_TO_NAME[PROP_ID_callbackEnabled] = PROP_NAME_callbackEnabled;
          PROP_NAME_TO_ID.put(PROP_NAME_callbackEnabled, PROP_ID_callbackEnabled);
      
          PROP_ID_TO_NAME[PROP_ID_callbackTriggerType] = PROP_NAME_callbackTriggerType;
          PROP_NAME_TO_ID.put(PROP_NAME_callbackTriggerType, PROP_ID_callbackTriggerType);
      
          PROP_ID_TO_NAME[PROP_ID_callbackPolicyId] = PROP_NAME_callbackPolicyId;
          PROP_NAME_TO_ID.put(PROP_NAME_callbackPolicyId, PROP_ID_callbackPolicyId);
      
          PROP_ID_TO_NAME[PROP_ID_ownerId] = PROP_NAME_ownerId;
          PROP_NAME_TO_ID.put(PROP_NAME_ownerId, PROP_ID_ownerId);
      
          PROP_ID_TO_NAME[PROP_ID_description] = PROP_NAME_description;
          PROP_NAME_TO_ID.put(PROP_NAME_description, PROP_ID_description);
      
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
      
          PROP_ID_TO_NAME[PROP_ID_retryingTimeoutMs] = PROP_NAME_retryingTimeoutMs;
          PROP_NAME_TO_ID.put(PROP_NAME_retryingTimeoutMs, PROP_ID_retryingTimeoutMs);
      
    }

    
    /* 主键: SID */
    private java.lang.String _sid;
    
    /* 命名空间ID: NAMESPACE_ID */
    private java.lang.String _namespaceId;
    
    /* 组ID: GROUP_ID */
    private java.lang.String _groupId;
    
    /* 策略名称: NAME */
    private java.lang.String _name;
    
    /* 状态: STATUS */
    private java.lang.String _status;
    
    /* 保存记录策略: SAVE_RECORD_STRATEGY */
    private java.lang.Integer _saveRecordStrategy;
    
    /* 立刻重试次数: IMMEDIATE_RETRY_COUNT */
    private java.lang.Integer _immediateRetryCount;
    
    /* 立刻重试间隔(毫秒): IMMEDIATE_RETRY_INTERVAL_MS */
    private java.lang.Long _immediateRetryIntervalMs;
    
    /* 最大重试次数: MAX_RETRY_COUNT */
    private java.lang.Integer _maxRetryCount;
    
    /* 退避策略: BACKOFF_STRATEGY */
    private java.lang.Integer _backoffStrategy;
    
    /* 初始间隔(毫秒): INITIAL_INTERVAL_MS */
    private java.lang.Long _initialIntervalMs;
    
    /* 最大间隔(毫秒): MAX_INTERVAL_MS */
    private java.lang.Long _maxIntervalMs;
    
    /* 抖动比例: JITTER_RATIO */
    private java.lang.Double _jitterRatio;
    
    /* 执行超时(秒): EXECUTION_TIMEOUT_SECONDS */
    private java.lang.Integer _executionTimeoutSeconds;
    
    /* 截止超时(毫秒): DEADLINE_TIMEOUT_MS */
    private java.lang.Long _deadlineTimeoutMs;
    
    /* 阻塞策略: BLOCK_STRATEGY */
    private java.lang.Integer _blockStrategy;
    
    /* 启用回调: CALLBACK_ENABLED */
    private java.lang.String _callbackEnabled;
    
    /* 回调触发类型: CALLBACK_TRIGGER_TYPE */
    private java.lang.Integer _callbackTriggerType;
    
    /* 回调策略ID: CALLBACK_POLICY_ID */
    private java.lang.String _callbackPolicyId;
    
    /* 所有者ID: OWNER_ID */
    private java.lang.String _ownerId;
    
    /* 描述: DESCRIPTION */
    private java.lang.String _description;
    
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
    
    /* 执行中锁定超时(毫秒): RETRYING_TIMEOUT_MS */
    private java.lang.Long _retryingTimeoutMs;
    

    public _NopRetryPolicy(){
        // for debug
    }

    protected NopRetryPolicy newInstance(){
        NopRetryPolicy entity = new NopRetryPolicy();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopRetryPolicy cloneInstance() {
        NopRetryPolicy entity = newInstance();
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
      return "io.nop.retry.dao.entity.NopRetryPolicy";
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
        
            case PROP_ID_name:
               return getName();
        
            case PROP_ID_status:
               return getStatus();
        
            case PROP_ID_saveRecordStrategy:
               return getSaveRecordStrategy();
        
            case PROP_ID_immediateRetryCount:
               return getImmediateRetryCount();
        
            case PROP_ID_immediateRetryIntervalMs:
               return getImmediateRetryIntervalMs();
        
            case PROP_ID_maxRetryCount:
               return getMaxRetryCount();
        
            case PROP_ID_backoffStrategy:
               return getBackoffStrategy();
        
            case PROP_ID_initialIntervalMs:
               return getInitialIntervalMs();
        
            case PROP_ID_maxIntervalMs:
               return getMaxIntervalMs();
        
            case PROP_ID_jitterRatio:
               return getJitterRatio();
        
            case PROP_ID_executionTimeoutSeconds:
               return getExecutionTimeoutSeconds();
        
            case PROP_ID_deadlineTimeoutMs:
               return getDeadlineTimeoutMs();
        
            case PROP_ID_blockStrategy:
               return getBlockStrategy();
        
            case PROP_ID_callbackEnabled:
               return getCallbackEnabled();
        
            case PROP_ID_callbackTriggerType:
               return getCallbackTriggerType();
        
            case PROP_ID_callbackPolicyId:
               return getCallbackPolicyId();
        
            case PROP_ID_ownerId:
               return getOwnerId();
        
            case PROP_ID_description:
               return getDescription();
        
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
        
            case PROP_ID_retryingTimeoutMs:
               return getRetryingTimeoutMs();
        
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
        
            case PROP_ID_name:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_name));
               }
               setName(typedValue);
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
        
            case PROP_ID_saveRecordStrategy:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_saveRecordStrategy));
               }
               setSaveRecordStrategy(typedValue);
               break;
            }
        
            case PROP_ID_immediateRetryCount:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_immediateRetryCount));
               }
               setImmediateRetryCount(typedValue);
               break;
            }
        
            case PROP_ID_immediateRetryIntervalMs:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_immediateRetryIntervalMs));
               }
               setImmediateRetryIntervalMs(typedValue);
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
        
            case PROP_ID_backoffStrategy:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_backoffStrategy));
               }
               setBackoffStrategy(typedValue);
               break;
            }
        
            case PROP_ID_initialIntervalMs:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_initialIntervalMs));
               }
               setInitialIntervalMs(typedValue);
               break;
            }
        
            case PROP_ID_maxIntervalMs:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_maxIntervalMs));
               }
               setMaxIntervalMs(typedValue);
               break;
            }
        
            case PROP_ID_jitterRatio:{
               java.lang.Double typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toDouble(value,
                       err-> newTypeConversionError(PROP_NAME_jitterRatio));
               }
               setJitterRatio(typedValue);
               break;
            }
        
            case PROP_ID_executionTimeoutSeconds:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_executionTimeoutSeconds));
               }
               setExecutionTimeoutSeconds(typedValue);
               break;
            }
        
            case PROP_ID_deadlineTimeoutMs:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_deadlineTimeoutMs));
               }
               setDeadlineTimeoutMs(typedValue);
               break;
            }
        
            case PROP_ID_blockStrategy:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_blockStrategy));
               }
               setBlockStrategy(typedValue);
               break;
            }
        
            case PROP_ID_callbackEnabled:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_callbackEnabled));
               }
               setCallbackEnabled(typedValue);
               break;
            }
        
            case PROP_ID_callbackTriggerType:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_callbackTriggerType));
               }
               setCallbackTriggerType(typedValue);
               break;
            }
        
            case PROP_ID_callbackPolicyId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_callbackPolicyId));
               }
               setCallbackPolicyId(typedValue);
               break;
            }
        
            case PROP_ID_ownerId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_ownerId));
               }
               setOwnerId(typedValue);
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
        
            case PROP_ID_retryingTimeoutMs:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_retryingTimeoutMs));
               }
               setRetryingTimeoutMs(typedValue);
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
        
            case PROP_ID_name:{
               onInitProp(propId);
               this._name = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_status:{
               onInitProp(propId);
               this._status = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_saveRecordStrategy:{
               onInitProp(propId);
               this._saveRecordStrategy = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_immediateRetryCount:{
               onInitProp(propId);
               this._immediateRetryCount = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_immediateRetryIntervalMs:{
               onInitProp(propId);
               this._immediateRetryIntervalMs = (java.lang.Long)value;
               
               break;
            }
        
            case PROP_ID_maxRetryCount:{
               onInitProp(propId);
               this._maxRetryCount = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_backoffStrategy:{
               onInitProp(propId);
               this._backoffStrategy = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_initialIntervalMs:{
               onInitProp(propId);
               this._initialIntervalMs = (java.lang.Long)value;
               
               break;
            }
        
            case PROP_ID_maxIntervalMs:{
               onInitProp(propId);
               this._maxIntervalMs = (java.lang.Long)value;
               
               break;
            }
        
            case PROP_ID_jitterRatio:{
               onInitProp(propId);
               this._jitterRatio = (java.lang.Double)value;
               
               break;
            }
        
            case PROP_ID_executionTimeoutSeconds:{
               onInitProp(propId);
               this._executionTimeoutSeconds = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_deadlineTimeoutMs:{
               onInitProp(propId);
               this._deadlineTimeoutMs = (java.lang.Long)value;
               
               break;
            }
        
            case PROP_ID_blockStrategy:{
               onInitProp(propId);
               this._blockStrategy = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_callbackEnabled:{
               onInitProp(propId);
               this._callbackEnabled = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_callbackTriggerType:{
               onInitProp(propId);
               this._callbackTriggerType = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_callbackPolicyId:{
               onInitProp(propId);
               this._callbackPolicyId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_ownerId:{
               onInitProp(propId);
               this._ownerId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_description:{
               onInitProp(propId);
               this._description = (java.lang.String)value;
               
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
        
            case PROP_ID_retryingTimeoutMs:{
               onInitProp(propId);
               this._retryingTimeoutMs = (java.lang.Long)value;
               
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
     * 策略名称: NAME
     */
    public final java.lang.String getName(){
         onPropGet(PROP_ID_name);
         return _name;
    }

    /**
     * 策略名称: NAME
     */
    public final void setName(java.lang.String value){
        if(onPropSet(PROP_ID_name,value)){
            this._name = value;
            internalClearRefs(PROP_ID_name);
            
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
     * 保存记录策略: SAVE_RECORD_STRATEGY
     */
    public final java.lang.Integer getSaveRecordStrategy(){
         onPropGet(PROP_ID_saveRecordStrategy);
         return _saveRecordStrategy;
    }

    /**
     * 保存记录策略: SAVE_RECORD_STRATEGY
     */
    public final void setSaveRecordStrategy(java.lang.Integer value){
        if(onPropSet(PROP_ID_saveRecordStrategy,value)){
            this._saveRecordStrategy = value;
            internalClearRefs(PROP_ID_saveRecordStrategy);
            
        }
    }
    
    /**
     * 立刻重试次数: IMMEDIATE_RETRY_COUNT
     */
    public final java.lang.Integer getImmediateRetryCount(){
         onPropGet(PROP_ID_immediateRetryCount);
         return _immediateRetryCount;
    }

    /**
     * 立刻重试次数: IMMEDIATE_RETRY_COUNT
     */
    public final void setImmediateRetryCount(java.lang.Integer value){
        if(onPropSet(PROP_ID_immediateRetryCount,value)){
            this._immediateRetryCount = value;
            internalClearRefs(PROP_ID_immediateRetryCount);
            
        }
    }
    
    /**
     * 立刻重试间隔(毫秒): IMMEDIATE_RETRY_INTERVAL_MS
     */
    public final java.lang.Long getImmediateRetryIntervalMs(){
         onPropGet(PROP_ID_immediateRetryIntervalMs);
         return _immediateRetryIntervalMs;
    }

    /**
     * 立刻重试间隔(毫秒): IMMEDIATE_RETRY_INTERVAL_MS
     */
    public final void setImmediateRetryIntervalMs(java.lang.Long value){
        if(onPropSet(PROP_ID_immediateRetryIntervalMs,value)){
            this._immediateRetryIntervalMs = value;
            internalClearRefs(PROP_ID_immediateRetryIntervalMs);
            
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
     * 退避策略: BACKOFF_STRATEGY
     */
    public final java.lang.Integer getBackoffStrategy(){
         onPropGet(PROP_ID_backoffStrategy);
         return _backoffStrategy;
    }

    /**
     * 退避策略: BACKOFF_STRATEGY
     */
    public final void setBackoffStrategy(java.lang.Integer value){
        if(onPropSet(PROP_ID_backoffStrategy,value)){
            this._backoffStrategy = value;
            internalClearRefs(PROP_ID_backoffStrategy);
            
        }
    }
    
    /**
     * 初始间隔(毫秒): INITIAL_INTERVAL_MS
     */
    public final java.lang.Long getInitialIntervalMs(){
         onPropGet(PROP_ID_initialIntervalMs);
         return _initialIntervalMs;
    }

    /**
     * 初始间隔(毫秒): INITIAL_INTERVAL_MS
     */
    public final void setInitialIntervalMs(java.lang.Long value){
        if(onPropSet(PROP_ID_initialIntervalMs,value)){
            this._initialIntervalMs = value;
            internalClearRefs(PROP_ID_initialIntervalMs);
            
        }
    }
    
    /**
     * 最大间隔(毫秒): MAX_INTERVAL_MS
     */
    public final java.lang.Long getMaxIntervalMs(){
         onPropGet(PROP_ID_maxIntervalMs);
         return _maxIntervalMs;
    }

    /**
     * 最大间隔(毫秒): MAX_INTERVAL_MS
     */
    public final void setMaxIntervalMs(java.lang.Long value){
        if(onPropSet(PROP_ID_maxIntervalMs,value)){
            this._maxIntervalMs = value;
            internalClearRefs(PROP_ID_maxIntervalMs);
            
        }
    }
    
    /**
     * 抖动比例: JITTER_RATIO
     */
    public final java.lang.Double getJitterRatio(){
         onPropGet(PROP_ID_jitterRatio);
         return _jitterRatio;
    }

    /**
     * 抖动比例: JITTER_RATIO
     */
    public final void setJitterRatio(java.lang.Double value){
        if(onPropSet(PROP_ID_jitterRatio,value)){
            this._jitterRatio = value;
            internalClearRefs(PROP_ID_jitterRatio);
            
        }
    }
    
    /**
     * 执行超时(秒): EXECUTION_TIMEOUT_SECONDS
     */
    public final java.lang.Integer getExecutionTimeoutSeconds(){
         onPropGet(PROP_ID_executionTimeoutSeconds);
         return _executionTimeoutSeconds;
    }

    /**
     * 执行超时(秒): EXECUTION_TIMEOUT_SECONDS
     */
    public final void setExecutionTimeoutSeconds(java.lang.Integer value){
        if(onPropSet(PROP_ID_executionTimeoutSeconds,value)){
            this._executionTimeoutSeconds = value;
            internalClearRefs(PROP_ID_executionTimeoutSeconds);
            
        }
    }
    
    /**
     * 截止超时(毫秒): DEADLINE_TIMEOUT_MS
     */
    public final java.lang.Long getDeadlineTimeoutMs(){
         onPropGet(PROP_ID_deadlineTimeoutMs);
         return _deadlineTimeoutMs;
    }

    /**
     * 截止超时(毫秒): DEADLINE_TIMEOUT_MS
     */
    public final void setDeadlineTimeoutMs(java.lang.Long value){
        if(onPropSet(PROP_ID_deadlineTimeoutMs,value)){
            this._deadlineTimeoutMs = value;
            internalClearRefs(PROP_ID_deadlineTimeoutMs);
            
        }
    }
    
    /**
     * 阻塞策略: BLOCK_STRATEGY
     */
    public final java.lang.Integer getBlockStrategy(){
         onPropGet(PROP_ID_blockStrategy);
         return _blockStrategy;
    }

    /**
     * 阻塞策略: BLOCK_STRATEGY
     */
    public final void setBlockStrategy(java.lang.Integer value){
        if(onPropSet(PROP_ID_blockStrategy,value)){
            this._blockStrategy = value;
            internalClearRefs(PROP_ID_blockStrategy);
            
        }
    }
    
    /**
     * 启用回调: CALLBACK_ENABLED
     */
    public final java.lang.String getCallbackEnabled(){
         onPropGet(PROP_ID_callbackEnabled);
         return _callbackEnabled;
    }

    /**
     * 启用回调: CALLBACK_ENABLED
     */
    public final void setCallbackEnabled(java.lang.String value){
        if(onPropSet(PROP_ID_callbackEnabled,value)){
            this._callbackEnabled = value;
            internalClearRefs(PROP_ID_callbackEnabled);
            
        }
    }
    
    /**
     * 回调触发类型: CALLBACK_TRIGGER_TYPE
     */
    public final java.lang.Integer getCallbackTriggerType(){
         onPropGet(PROP_ID_callbackTriggerType);
         return _callbackTriggerType;
    }

    /**
     * 回调触发类型: CALLBACK_TRIGGER_TYPE
     */
    public final void setCallbackTriggerType(java.lang.Integer value){
        if(onPropSet(PROP_ID_callbackTriggerType,value)){
            this._callbackTriggerType = value;
            internalClearRefs(PROP_ID_callbackTriggerType);
            
        }
    }
    
    /**
     * 回调策略ID: CALLBACK_POLICY_ID
     */
    public final java.lang.String getCallbackPolicyId(){
         onPropGet(PROP_ID_callbackPolicyId);
         return _callbackPolicyId;
    }

    /**
     * 回调策略ID: CALLBACK_POLICY_ID
     */
    public final void setCallbackPolicyId(java.lang.String value){
        if(onPropSet(PROP_ID_callbackPolicyId,value)){
            this._callbackPolicyId = value;
            internalClearRefs(PROP_ID_callbackPolicyId);
            
        }
    }
    
    /**
     * 所有者ID: OWNER_ID
     */
    public final java.lang.String getOwnerId(){
         onPropGet(PROP_ID_ownerId);
         return _ownerId;
    }

    /**
     * 所有者ID: OWNER_ID
     */
    public final void setOwnerId(java.lang.String value){
        if(onPropSet(PROP_ID_ownerId,value)){
            this._ownerId = value;
            internalClearRefs(PROP_ID_ownerId);
            
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
     * 执行中锁定超时(毫秒): RETRYING_TIMEOUT_MS
     */
    public final java.lang.Long getRetryingTimeoutMs(){
         onPropGet(PROP_ID_retryingTimeoutMs);
         return _retryingTimeoutMs;
    }

    /**
     * 执行中锁定超时(毫秒): RETRYING_TIMEOUT_MS
     */
    public final void setRetryingTimeoutMs(java.lang.Long value){
        if(onPropSet(PROP_ID_retryingTimeoutMs,value)){
            this._retryingTimeoutMs = value;
            internalClearRefs(PROP_ID_retryingTimeoutMs);
            
        }
    }
    
}
// resume CPD analysis - CPD-ON
