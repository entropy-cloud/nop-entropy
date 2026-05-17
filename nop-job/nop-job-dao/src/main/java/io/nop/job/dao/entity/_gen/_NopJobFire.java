package io.nop.job.dao.entity._gen;

import io.nop.orm.model.IEntityModel;
import io.nop.orm.support.DynamicOrmEntity;
import io.nop.orm.support.OrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.orm.IOrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.api.core.convert.ConvertHelper;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;

import io.nop.job.dao.entity.NopJobFire;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  触发批次: nop_job_fire
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopJobFire extends DynamicOrmEntity{
    
    /* 触发批次ID: JOB_FIRE_ID VARCHAR */
    public static final String PROP_NAME_jobFireId = "jobFireId";
    public static final int PROP_ID_jobFireId = 1;
    
    /* 调度ID: JOB_SCHEDULE_ID VARCHAR */
    public static final String PROP_NAME_jobScheduleId = "jobScheduleId";
    public static final int PROP_ID_jobScheduleId = 2;
    
    /* 命名空间: NAMESPACE_ID VARCHAR */
    public static final String PROP_NAME_namespaceId = "namespaceId";
    public static final int PROP_ID_namespaceId = 3;
    
    /* 分组: GROUP_ID VARCHAR */
    public static final String PROP_NAME_groupId = "groupId";
    public static final int PROP_ID_groupId = 4;
    
    /* 作业名: JOB_NAME VARCHAR */
    public static final String PROP_NAME_jobName = "jobName";
    public static final int PROP_ID_jobName = 5;
    
    /* 触发来源: TRIGGER_SOURCE INTEGER */
    public static final String PROP_NAME_triggerSource = "triggerSource";
    public static final int PROP_ID_triggerSource = 6;
    
    /* 计划触发时间: SCHEDULED_FIRE_TIME TIMESTAMP */
    public static final String PROP_NAME_scheduledFireTime = "scheduledFireTime";
    public static final int PROP_ID_scheduledFireTime = 7;
    
    /* 触发人: TRIGGERED_BY VARCHAR */
    public static final String PROP_NAME_triggeredBy = "triggeredBy";
    public static final int PROP_ID_triggeredBy = 8;
    
    /* 批次状态: FIRE_STATUS INTEGER */
    public static final String PROP_NAME_fireStatus = "fireStatus";
    public static final int PROP_ID_fireStatus = 9;
    
    /* 计划节点ID: PLANNER_INSTANCE_ID VARCHAR */
    public static final String PROP_NAME_plannerInstanceId = "plannerInstanceId";
    public static final int PROP_ID_plannerInstanceId = 10;
    
    /* 分发节点ID: DISPATCH_INSTANCE_ID VARCHAR */
    public static final String PROP_NAME_dispatchInstanceId = "dispatchInstanceId";
    public static final int PROP_ID_dispatchInstanceId = 11;
    
    /* 开始时间: START_TIME TIMESTAMP */
    public static final String PROP_NAME_startTime = "startTime";
    public static final int PROP_ID_startTime = 12;
    
    /* 结束时间: END_TIME TIMESTAMP */
    public static final String PROP_NAME_endTime = "endTime";
    public static final int PROP_ID_endTime = 13;
    
    /* 执行时长(毫秒): DURATION_MS BIGINT */
    public static final String PROP_NAME_durationMs = "durationMs";
    public static final int PROP_ID_durationMs = 14;
    
    /* 参数快照: JOB_PARAMS_SNAPSHOT VARCHAR */
    public static final String PROP_NAME_jobParamsSnapshot = "jobParamsSnapshot";
    public static final int PROP_ID_jobParamsSnapshot = 15;
    
    /* 执行器类型: EXECUTOR_KIND VARCHAR */
    public static final String PROP_NAME_executorKind = "executorKind";
    public static final int PROP_ID_executorKind = 16;
    
    /* 重试策略ID: RETRY_POLICY_ID VARCHAR */
    public static final String PROP_NAME_retryPolicyId = "retryPolicyId";
    public static final int PROP_ID_retryPolicyId = 17;
    
    /* 重试记录ID: RETRY_RECORD_ID VARCHAR */
    public static final String PROP_NAME_retryRecordId = "retryRecordId";
    public static final int PROP_ID_retryRecordId = 18;
    
    /* 错误码: ERROR_CODE VARCHAR */
    public static final String PROP_NAME_errorCode = "errorCode";
    public static final int PROP_ID_errorCode = 19;
    
    /* 错误消息: ERROR_MESSAGE VARCHAR */
    public static final String PROP_NAME_errorMessage = "errorMessage";
    public static final int PROP_ID_errorMessage = 20;
    
    /* 分区索引: PARTITION_INDEX SMALLINT */
    public static final String PROP_NAME_partitionIndex = "partitionIndex";
    public static final int PROP_ID_partitionIndex = 21;
    
    /* 数据版本: VERSION BIGINT */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 22;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 23;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 24;
    
    /* 修改人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 25;
    
    /* 修改时间: UPDATE_TIME TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 26;
    
    /* 备注: REMARK VARCHAR */
    public static final String PROP_NAME_remark = "remark";
    public static final int PROP_ID_remark = 27;
    

    private static int _PROP_ID_BOUND = 28;

    
    /* relation: 调度定义 */
    public static final String PROP_NAME_jobSchedule = "jobSchedule";
    
    /* component:  */
    public static final String PROP_NAME_jobParamsSnapshotComponent = "jobParamsSnapshotComponent";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_jobFireId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_jobFireId};

    private static final String[] PROP_ID_TO_NAME = new String[28];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_jobFireId] = PROP_NAME_jobFireId;
          PROP_NAME_TO_ID.put(PROP_NAME_jobFireId, PROP_ID_jobFireId);
      
          PROP_ID_TO_NAME[PROP_ID_jobScheduleId] = PROP_NAME_jobScheduleId;
          PROP_NAME_TO_ID.put(PROP_NAME_jobScheduleId, PROP_ID_jobScheduleId);
      
          PROP_ID_TO_NAME[PROP_ID_namespaceId] = PROP_NAME_namespaceId;
          PROP_NAME_TO_ID.put(PROP_NAME_namespaceId, PROP_ID_namespaceId);
      
          PROP_ID_TO_NAME[PROP_ID_groupId] = PROP_NAME_groupId;
          PROP_NAME_TO_ID.put(PROP_NAME_groupId, PROP_ID_groupId);
      
          PROP_ID_TO_NAME[PROP_ID_jobName] = PROP_NAME_jobName;
          PROP_NAME_TO_ID.put(PROP_NAME_jobName, PROP_ID_jobName);
      
          PROP_ID_TO_NAME[PROP_ID_triggerSource] = PROP_NAME_triggerSource;
          PROP_NAME_TO_ID.put(PROP_NAME_triggerSource, PROP_ID_triggerSource);
      
          PROP_ID_TO_NAME[PROP_ID_scheduledFireTime] = PROP_NAME_scheduledFireTime;
          PROP_NAME_TO_ID.put(PROP_NAME_scheduledFireTime, PROP_ID_scheduledFireTime);
      
          PROP_ID_TO_NAME[PROP_ID_triggeredBy] = PROP_NAME_triggeredBy;
          PROP_NAME_TO_ID.put(PROP_NAME_triggeredBy, PROP_ID_triggeredBy);
      
          PROP_ID_TO_NAME[PROP_ID_fireStatus] = PROP_NAME_fireStatus;
          PROP_NAME_TO_ID.put(PROP_NAME_fireStatus, PROP_ID_fireStatus);
      
          PROP_ID_TO_NAME[PROP_ID_plannerInstanceId] = PROP_NAME_plannerInstanceId;
          PROP_NAME_TO_ID.put(PROP_NAME_plannerInstanceId, PROP_ID_plannerInstanceId);
      
          PROP_ID_TO_NAME[PROP_ID_dispatchInstanceId] = PROP_NAME_dispatchInstanceId;
          PROP_NAME_TO_ID.put(PROP_NAME_dispatchInstanceId, PROP_ID_dispatchInstanceId);
      
          PROP_ID_TO_NAME[PROP_ID_startTime] = PROP_NAME_startTime;
          PROP_NAME_TO_ID.put(PROP_NAME_startTime, PROP_ID_startTime);
      
          PROP_ID_TO_NAME[PROP_ID_endTime] = PROP_NAME_endTime;
          PROP_NAME_TO_ID.put(PROP_NAME_endTime, PROP_ID_endTime);
      
          PROP_ID_TO_NAME[PROP_ID_durationMs] = PROP_NAME_durationMs;
          PROP_NAME_TO_ID.put(PROP_NAME_durationMs, PROP_ID_durationMs);
      
          PROP_ID_TO_NAME[PROP_ID_jobParamsSnapshot] = PROP_NAME_jobParamsSnapshot;
          PROP_NAME_TO_ID.put(PROP_NAME_jobParamsSnapshot, PROP_ID_jobParamsSnapshot);
      
          PROP_ID_TO_NAME[PROP_ID_executorKind] = PROP_NAME_executorKind;
          PROP_NAME_TO_ID.put(PROP_NAME_executorKind, PROP_ID_executorKind);
      
          PROP_ID_TO_NAME[PROP_ID_retryPolicyId] = PROP_NAME_retryPolicyId;
          PROP_NAME_TO_ID.put(PROP_NAME_retryPolicyId, PROP_ID_retryPolicyId);
      
          PROP_ID_TO_NAME[PROP_ID_retryRecordId] = PROP_NAME_retryRecordId;
          PROP_NAME_TO_ID.put(PROP_NAME_retryRecordId, PROP_ID_retryRecordId);
      
          PROP_ID_TO_NAME[PROP_ID_errorCode] = PROP_NAME_errorCode;
          PROP_NAME_TO_ID.put(PROP_NAME_errorCode, PROP_ID_errorCode);
      
          PROP_ID_TO_NAME[PROP_ID_errorMessage] = PROP_NAME_errorMessage;
          PROP_NAME_TO_ID.put(PROP_NAME_errorMessage, PROP_ID_errorMessage);
      
          PROP_ID_TO_NAME[PROP_ID_partitionIndex] = PROP_NAME_partitionIndex;
          PROP_NAME_TO_ID.put(PROP_NAME_partitionIndex, PROP_ID_partitionIndex);
      
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

    
    /* 触发批次ID: JOB_FIRE_ID */
    private java.lang.String _jobFireId;
    
    /* 调度ID: JOB_SCHEDULE_ID */
    private java.lang.String _jobScheduleId;
    
    /* 命名空间: NAMESPACE_ID */
    private java.lang.String _namespaceId;
    
    /* 分组: GROUP_ID */
    private java.lang.String _groupId;
    
    /* 作业名: JOB_NAME */
    private java.lang.String _jobName;
    
    /* 触发来源: TRIGGER_SOURCE */
    private java.lang.Integer _triggerSource;
    
    /* 计划触发时间: SCHEDULED_FIRE_TIME */
    private java.sql.Timestamp _scheduledFireTime;
    
    /* 触发人: TRIGGERED_BY */
    private java.lang.String _triggeredBy;
    
    /* 批次状态: FIRE_STATUS */
    private java.lang.Integer _fireStatus;
    
    /* 计划节点ID: PLANNER_INSTANCE_ID */
    private java.lang.String _plannerInstanceId;
    
    /* 分发节点ID: DISPATCH_INSTANCE_ID */
    private java.lang.String _dispatchInstanceId;
    
    /* 开始时间: START_TIME */
    private java.sql.Timestamp _startTime;
    
    /* 结束时间: END_TIME */
    private java.sql.Timestamp _endTime;
    
    /* 执行时长(毫秒): DURATION_MS */
    private java.lang.Long _durationMs;
    
    /* 参数快照: JOB_PARAMS_SNAPSHOT */
    private java.lang.String _jobParamsSnapshot;
    
    /* 执行器类型: EXECUTOR_KIND */
    private java.lang.String _executorKind;
    
    /* 重试策略ID: RETRY_POLICY_ID */
    private java.lang.String _retryPolicyId;
    
    /* 重试记录ID: RETRY_RECORD_ID */
    private java.lang.String _retryRecordId;
    
    /* 错误码: ERROR_CODE */
    private java.lang.String _errorCode;
    
    /* 错误消息: ERROR_MESSAGE */
    private java.lang.String _errorMessage;
    
    /* 分区索引: PARTITION_INDEX */
    private java.lang.Short _partitionIndex;
    
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
    

    public _NopJobFire(){
        // for debug
    }

    protected NopJobFire newInstance(){
        NopJobFire entity = new NopJobFire();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopJobFire cloneInstance() {
        NopJobFire entity = newInstance();
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
      return "io.nop.job.dao.entity.NopJobFire";
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
    
        return buildSimpleId(PROP_ID_jobFireId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_jobFireId;
          
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
        
            case PROP_ID_jobFireId:
               return getJobFireId();
        
            case PROP_ID_jobScheduleId:
               return getJobScheduleId();
        
            case PROP_ID_namespaceId:
               return getNamespaceId();
        
            case PROP_ID_groupId:
               return getGroupId();
        
            case PROP_ID_jobName:
               return getJobName();
        
            case PROP_ID_triggerSource:
               return getTriggerSource();
        
            case PROP_ID_scheduledFireTime:
               return getScheduledFireTime();
        
            case PROP_ID_triggeredBy:
               return getTriggeredBy();
        
            case PROP_ID_fireStatus:
               return getFireStatus();
        
            case PROP_ID_plannerInstanceId:
               return getPlannerInstanceId();
        
            case PROP_ID_dispatchInstanceId:
               return getDispatchInstanceId();
        
            case PROP_ID_startTime:
               return getStartTime();
        
            case PROP_ID_endTime:
               return getEndTime();
        
            case PROP_ID_durationMs:
               return getDurationMs();
        
            case PROP_ID_jobParamsSnapshot:
               return getJobParamsSnapshot();
        
            case PROP_ID_executorKind:
               return getExecutorKind();
        
            case PROP_ID_retryPolicyId:
               return getRetryPolicyId();
        
            case PROP_ID_retryRecordId:
               return getRetryRecordId();
        
            case PROP_ID_errorCode:
               return getErrorCode();
        
            case PROP_ID_errorMessage:
               return getErrorMessage();
        
            case PROP_ID_partitionIndex:
               return getPartitionIndex();
        
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
        
            case PROP_ID_jobFireId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_jobFireId));
               }
               setJobFireId(typedValue);
               break;
            }
        
            case PROP_ID_jobScheduleId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_jobScheduleId));
               }
               setJobScheduleId(typedValue);
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
        
            case PROP_ID_jobName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_jobName));
               }
               setJobName(typedValue);
               break;
            }
        
            case PROP_ID_triggerSource:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_triggerSource));
               }
               setTriggerSource(typedValue);
               break;
            }
        
            case PROP_ID_scheduledFireTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_scheduledFireTime));
               }
               setScheduledFireTime(typedValue);
               break;
            }
        
            case PROP_ID_triggeredBy:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_triggeredBy));
               }
               setTriggeredBy(typedValue);
               break;
            }
        
            case PROP_ID_fireStatus:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_fireStatus));
               }
               setFireStatus(typedValue);
               break;
            }
        
            case PROP_ID_plannerInstanceId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_plannerInstanceId));
               }
               setPlannerInstanceId(typedValue);
               break;
            }
        
            case PROP_ID_dispatchInstanceId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_dispatchInstanceId));
               }
               setDispatchInstanceId(typedValue);
               break;
            }
        
            case PROP_ID_startTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_startTime));
               }
               setStartTime(typedValue);
               break;
            }
        
            case PROP_ID_endTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_endTime));
               }
               setEndTime(typedValue);
               break;
            }
        
            case PROP_ID_durationMs:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_durationMs));
               }
               setDurationMs(typedValue);
               break;
            }
        
            case PROP_ID_jobParamsSnapshot:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_jobParamsSnapshot));
               }
               setJobParamsSnapshot(typedValue);
               break;
            }
        
            case PROP_ID_executorKind:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_executorKind));
               }
               setExecutorKind(typedValue);
               break;
            }
        
            case PROP_ID_retryPolicyId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_retryPolicyId));
               }
               setRetryPolicyId(typedValue);
               break;
            }
        
            case PROP_ID_retryRecordId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_retryRecordId));
               }
               setRetryRecordId(typedValue);
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
        
            case PROP_ID_errorMessage:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_errorMessage));
               }
               setErrorMessage(typedValue);
               break;
            }
        
            case PROP_ID_partitionIndex:{
               java.lang.Short typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toShort(value,
                       err-> newTypeConversionError(PROP_NAME_partitionIndex));
               }
               setPartitionIndex(typedValue);
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
        
           default:
              super.orm_propValue(propId,value);
        }
    }

    @Override
    public void orm_internalSet(int propId, Object value) {
        switch(propId){
        
            case PROP_ID_jobFireId:{
               onInitProp(propId);
               this._jobFireId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_jobScheduleId:{
               onInitProp(propId);
               this._jobScheduleId = (java.lang.String)value;
               
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
        
            case PROP_ID_jobName:{
               onInitProp(propId);
               this._jobName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_triggerSource:{
               onInitProp(propId);
               this._triggerSource = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_scheduledFireTime:{
               onInitProp(propId);
               this._scheduledFireTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_triggeredBy:{
               onInitProp(propId);
               this._triggeredBy = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_fireStatus:{
               onInitProp(propId);
               this._fireStatus = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_plannerInstanceId:{
               onInitProp(propId);
               this._plannerInstanceId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_dispatchInstanceId:{
               onInitProp(propId);
               this._dispatchInstanceId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_startTime:{
               onInitProp(propId);
               this._startTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_endTime:{
               onInitProp(propId);
               this._endTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_durationMs:{
               onInitProp(propId);
               this._durationMs = (java.lang.Long)value;
               
               break;
            }
        
            case PROP_ID_jobParamsSnapshot:{
               onInitProp(propId);
               this._jobParamsSnapshot = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_executorKind:{
               onInitProp(propId);
               this._executorKind = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_retryPolicyId:{
               onInitProp(propId);
               this._retryPolicyId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_retryRecordId:{
               onInitProp(propId);
               this._retryRecordId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_errorCode:{
               onInitProp(propId);
               this._errorCode = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_errorMessage:{
               onInitProp(propId);
               this._errorMessage = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_partitionIndex:{
               onInitProp(propId);
               this._partitionIndex = (java.lang.Short)value;
               
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
        
           default:
              super.orm_internalSet(propId,value);
        }
    }

    
    /**
     * 触发批次ID: JOB_FIRE_ID
     */
    public final java.lang.String getJobFireId(){
         onPropGet(PROP_ID_jobFireId);
         return _jobFireId;
    }

    /**
     * 触发批次ID: JOB_FIRE_ID
     */
    public final void setJobFireId(java.lang.String value){
        if(onPropSet(PROP_ID_jobFireId,value)){
            this._jobFireId = value;
            internalClearRefs(PROP_ID_jobFireId);
            orm_id();
        }
    }
    
    /**
     * 调度ID: JOB_SCHEDULE_ID
     */
    public final java.lang.String getJobScheduleId(){
         onPropGet(PROP_ID_jobScheduleId);
         return _jobScheduleId;
    }

    /**
     * 调度ID: JOB_SCHEDULE_ID
     */
    public final void setJobScheduleId(java.lang.String value){
        if(onPropSet(PROP_ID_jobScheduleId,value)){
            this._jobScheduleId = value;
            internalClearRefs(PROP_ID_jobScheduleId);
            
        }
    }
    
    /**
     * 命名空间: NAMESPACE_ID
     */
    public final java.lang.String getNamespaceId(){
         onPropGet(PROP_ID_namespaceId);
         return _namespaceId;
    }

    /**
     * 命名空间: NAMESPACE_ID
     */
    public final void setNamespaceId(java.lang.String value){
        if(onPropSet(PROP_ID_namespaceId,value)){
            this._namespaceId = value;
            internalClearRefs(PROP_ID_namespaceId);
            
        }
    }
    
    /**
     * 分组: GROUP_ID
     */
    public final java.lang.String getGroupId(){
         onPropGet(PROP_ID_groupId);
         return _groupId;
    }

    /**
     * 分组: GROUP_ID
     */
    public final void setGroupId(java.lang.String value){
        if(onPropSet(PROP_ID_groupId,value)){
            this._groupId = value;
            internalClearRefs(PROP_ID_groupId);
            
        }
    }
    
    /**
     * 作业名: JOB_NAME
     */
    public final java.lang.String getJobName(){
         onPropGet(PROP_ID_jobName);
         return _jobName;
    }

    /**
     * 作业名: JOB_NAME
     */
    public final void setJobName(java.lang.String value){
        if(onPropSet(PROP_ID_jobName,value)){
            this._jobName = value;
            internalClearRefs(PROP_ID_jobName);
            
        }
    }
    
    /**
     * 触发来源: TRIGGER_SOURCE
     */
    public final java.lang.Integer getTriggerSource(){
         onPropGet(PROP_ID_triggerSource);
         return _triggerSource;
    }

    /**
     * 触发来源: TRIGGER_SOURCE
     */
    public final void setTriggerSource(java.lang.Integer value){
        if(onPropSet(PROP_ID_triggerSource,value)){
            this._triggerSource = value;
            internalClearRefs(PROP_ID_triggerSource);
            
        }
    }
    
    /**
     * 计划触发时间: SCHEDULED_FIRE_TIME
     */
    public final java.sql.Timestamp getScheduledFireTime(){
         onPropGet(PROP_ID_scheduledFireTime);
         return _scheduledFireTime;
    }

    /**
     * 计划触发时间: SCHEDULED_FIRE_TIME
     */
    public final void setScheduledFireTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_scheduledFireTime,value)){
            this._scheduledFireTime = value;
            internalClearRefs(PROP_ID_scheduledFireTime);
            
        }
    }
    
    /**
     * 触发人: TRIGGERED_BY
     */
    public final java.lang.String getTriggeredBy(){
         onPropGet(PROP_ID_triggeredBy);
         return _triggeredBy;
    }

    /**
     * 触发人: TRIGGERED_BY
     */
    public final void setTriggeredBy(java.lang.String value){
        if(onPropSet(PROP_ID_triggeredBy,value)){
            this._triggeredBy = value;
            internalClearRefs(PROP_ID_triggeredBy);
            
        }
    }
    
    /**
     * 批次状态: FIRE_STATUS
     */
    public final java.lang.Integer getFireStatus(){
         onPropGet(PROP_ID_fireStatus);
         return _fireStatus;
    }

    /**
     * 批次状态: FIRE_STATUS
     */
    public final void setFireStatus(java.lang.Integer value){
        if(onPropSet(PROP_ID_fireStatus,value)){
            this._fireStatus = value;
            internalClearRefs(PROP_ID_fireStatus);
            
        }
    }
    
    /**
     * 计划节点ID: PLANNER_INSTANCE_ID
     */
    public final java.lang.String getPlannerInstanceId(){
         onPropGet(PROP_ID_plannerInstanceId);
         return _plannerInstanceId;
    }

    /**
     * 计划节点ID: PLANNER_INSTANCE_ID
     */
    public final void setPlannerInstanceId(java.lang.String value){
        if(onPropSet(PROP_ID_plannerInstanceId,value)){
            this._plannerInstanceId = value;
            internalClearRefs(PROP_ID_plannerInstanceId);
            
        }
    }
    
    /**
     * 分发节点ID: DISPATCH_INSTANCE_ID
     */
    public final java.lang.String getDispatchInstanceId(){
         onPropGet(PROP_ID_dispatchInstanceId);
         return _dispatchInstanceId;
    }

    /**
     * 分发节点ID: DISPATCH_INSTANCE_ID
     */
    public final void setDispatchInstanceId(java.lang.String value){
        if(onPropSet(PROP_ID_dispatchInstanceId,value)){
            this._dispatchInstanceId = value;
            internalClearRefs(PROP_ID_dispatchInstanceId);
            
        }
    }
    
    /**
     * 开始时间: START_TIME
     */
    public final java.sql.Timestamp getStartTime(){
         onPropGet(PROP_ID_startTime);
         return _startTime;
    }

    /**
     * 开始时间: START_TIME
     */
    public final void setStartTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_startTime,value)){
            this._startTime = value;
            internalClearRefs(PROP_ID_startTime);
            
        }
    }
    
    /**
     * 结束时间: END_TIME
     */
    public final java.sql.Timestamp getEndTime(){
         onPropGet(PROP_ID_endTime);
         return _endTime;
    }

    /**
     * 结束时间: END_TIME
     */
    public final void setEndTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_endTime,value)){
            this._endTime = value;
            internalClearRefs(PROP_ID_endTime);
            
        }
    }
    
    /**
     * 执行时长(毫秒): DURATION_MS
     */
    public final java.lang.Long getDurationMs(){
         onPropGet(PROP_ID_durationMs);
         return _durationMs;
    }

    /**
     * 执行时长(毫秒): DURATION_MS
     */
    public final void setDurationMs(java.lang.Long value){
        if(onPropSet(PROP_ID_durationMs,value)){
            this._durationMs = value;
            internalClearRefs(PROP_ID_durationMs);
            
        }
    }
    
    /**
     * 参数快照: JOB_PARAMS_SNAPSHOT
     */
    public final java.lang.String getJobParamsSnapshot(){
         onPropGet(PROP_ID_jobParamsSnapshot);
         return _jobParamsSnapshot;
    }

    /**
     * 参数快照: JOB_PARAMS_SNAPSHOT
     */
    public final void setJobParamsSnapshot(java.lang.String value){
        if(onPropSet(PROP_ID_jobParamsSnapshot,value)){
            this._jobParamsSnapshot = value;
            internalClearRefs(PROP_ID_jobParamsSnapshot);
            
        }
    }
    
    /**
     * 执行器类型: EXECUTOR_KIND
     */
    public final java.lang.String getExecutorKind(){
         onPropGet(PROP_ID_executorKind);
         return _executorKind;
    }

    /**
     * 执行器类型: EXECUTOR_KIND
     */
    public final void setExecutorKind(java.lang.String value){
        if(onPropSet(PROP_ID_executorKind,value)){
            this._executorKind = value;
            internalClearRefs(PROP_ID_executorKind);
            
        }
    }
    
    /**
     * 重试策略ID: RETRY_POLICY_ID
     */
    public final java.lang.String getRetryPolicyId(){
         onPropGet(PROP_ID_retryPolicyId);
         return _retryPolicyId;
    }

    /**
     * 重试策略ID: RETRY_POLICY_ID
     */
    public final void setRetryPolicyId(java.lang.String value){
        if(onPropSet(PROP_ID_retryPolicyId,value)){
            this._retryPolicyId = value;
            internalClearRefs(PROP_ID_retryPolicyId);
            
        }
    }
    
    /**
     * 重试记录ID: RETRY_RECORD_ID
     */
    public final java.lang.String getRetryRecordId(){
         onPropGet(PROP_ID_retryRecordId);
         return _retryRecordId;
    }

    /**
     * 重试记录ID: RETRY_RECORD_ID
     */
    public final void setRetryRecordId(java.lang.String value){
        if(onPropSet(PROP_ID_retryRecordId,value)){
            this._retryRecordId = value;
            internalClearRefs(PROP_ID_retryRecordId);
            
        }
    }
    
    /**
     * 错误码: ERROR_CODE
     */
    public final java.lang.String getErrorCode(){
         onPropGet(PROP_ID_errorCode);
         return _errorCode;
    }

    /**
     * 错误码: ERROR_CODE
     */
    public final void setErrorCode(java.lang.String value){
        if(onPropSet(PROP_ID_errorCode,value)){
            this._errorCode = value;
            internalClearRefs(PROP_ID_errorCode);
            
        }
    }
    
    /**
     * 错误消息: ERROR_MESSAGE
     */
    public final java.lang.String getErrorMessage(){
         onPropGet(PROP_ID_errorMessage);
         return _errorMessage;
    }

    /**
     * 错误消息: ERROR_MESSAGE
     */
    public final void setErrorMessage(java.lang.String value){
        if(onPropSet(PROP_ID_errorMessage,value)){
            this._errorMessage = value;
            internalClearRefs(PROP_ID_errorMessage);
            
        }
    }
    
    /**
     * 分区索引: PARTITION_INDEX
     */
    public final java.lang.Short getPartitionIndex(){
         onPropGet(PROP_ID_partitionIndex);
         return _partitionIndex;
    }

    /**
     * 分区索引: PARTITION_INDEX
     */
    public final void setPartitionIndex(java.lang.Short value){
        if(onPropSet(PROP_ID_partitionIndex,value)){
            this._partitionIndex = value;
            internalClearRefs(PROP_ID_partitionIndex);
            
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
     * 调度定义
     */
    public final io.nop.job.dao.entity.NopJobSchedule getJobSchedule(){
       return (io.nop.job.dao.entity.NopJobSchedule)internalGetRefEntity(PROP_NAME_jobSchedule);
    }

    public final void setJobSchedule(io.nop.job.dao.entity.NopJobSchedule refEntity){
   
           if(refEntity == null){
           
                   this.setJobScheduleId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_jobSchedule, refEntity,()->{
           
                           this.setJobScheduleId(refEntity.getJobScheduleId());
                       
           });
           }
       
    }
       
   private io.nop.orm.component.JsonOrmComponent _jobParamsSnapshotComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_jobParamsSnapshotComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_jobParamsSnapshotComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_jobParamsSnapshot);
      
   }

   public final io.nop.orm.component.JsonOrmComponent getJobParamsSnapshotComponent(){
      if(_jobParamsSnapshotComponent == null){
          _jobParamsSnapshotComponent = new io.nop.orm.component.JsonOrmComponent();
          _jobParamsSnapshotComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_jobParamsSnapshotComponent);
      }
      return _jobParamsSnapshotComponent;
   }

}
// resume CPD analysis - CPD-ON
