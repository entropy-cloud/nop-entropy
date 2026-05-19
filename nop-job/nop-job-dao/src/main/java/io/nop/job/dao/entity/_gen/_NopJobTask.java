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

import io.nop.job.dao.entity.NopJobTask;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  执行任务: nop_job_task
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopJobTask extends DynamicOrmEntity{
    
    /* 任务ID: JOB_TASK_ID VARCHAR */
    public static final String PROP_NAME_jobTaskId = "jobTaskId";
    public static final int PROP_ID_jobTaskId = 1;
    
    /* 批次ID: JOB_FIRE_ID VARCHAR */
    public static final String PROP_NAME_jobFireId = "jobFireId";
    public static final int PROP_ID_jobFireId = 2;
    
    /* 任务序号: TASK_NO INTEGER */
    public static final String PROP_NAME_taskNo = "taskNo";
    public static final int PROP_ID_taskNo = 3;
    
    /* 任务状态: TASK_STATUS INTEGER */
    public static final String PROP_NAME_taskStatus = "taskStatus";
    public static final int PROP_ID_taskStatus = 4;
    
    /* 执行节点ID: WORKER_INSTANCE_ID VARCHAR */
    public static final String PROP_NAME_workerInstanceId = "workerInstanceId";
    public static final int PROP_ID_workerInstanceId = 5;
    
    /* 执行节点地址: WORKER_ADDRESS VARCHAR */
    public static final String PROP_NAME_workerAddress = "workerAddress";
    public static final int PROP_ID_workerAddress = 6;
    
    /* 投递参数: TASK_PAYLOAD VARCHAR */
    public static final String PROP_NAME_taskPayload = "taskPayload";
    public static final int PROP_ID_taskPayload = 7;
    
    /* 开始时间: START_TIME TIMESTAMP */
    public static final String PROP_NAME_startTime = "startTime";
    public static final int PROP_ID_startTime = 8;
    
    /* 结束时间: END_TIME TIMESTAMP */
    public static final String PROP_NAME_endTime = "endTime";
    public static final int PROP_ID_endTime = 9;
    
    /* 执行时长(毫秒): DURATION_MS BIGINT */
    public static final String PROP_NAME_durationMs = "durationMs";
    public static final int PROP_ID_durationMs = 10;
    
    /* 执行结果: RESULT_PAYLOAD VARCHAR */
    public static final String PROP_NAME_resultPayload = "resultPayload";
    public static final int PROP_ID_resultPayload = 11;
    
    /* 错误码: ERROR_CODE VARCHAR */
    public static final String PROP_NAME_errorCode = "errorCode";
    public static final int PROP_ID_errorCode = 12;
    
    /* 错误消息: ERROR_MESSAGE VARCHAR */
    public static final String PROP_NAME_errorMessage = "errorMessage";
    public static final int PROP_ID_errorMessage = 13;
    
    /* 分区索引: PARTITION_INDEX SMALLINT */
    public static final String PROP_NAME_partitionIndex = "partitionIndex";
    public static final int PROP_ID_partitionIndex = 14;
    
    /* 数据版本: VERSION BIGINT */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 15;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 16;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 17;
    
    /* 修改人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 18;
    
    /* 修改时间: UPDATE_TIME TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 19;
    
    /* 备注: REMARK VARCHAR */
    public static final String PROP_NAME_remark = "remark";
    public static final int PROP_ID_remark = 20;
    
    /* 执行进度: PROGRESS INTEGER */
    public static final String PROP_NAME_progress = "progress";
    public static final int PROP_ID_progress = 21;
    
    /* 进度消息: PROGRESS_MESSAGE VARCHAR */
    public static final String PROP_NAME_progressMessage = "progressMessage";
    public static final int PROP_ID_progressMessage = 22;
    
    /* 目标节点地址: TARGET_HOST VARCHAR */
    public static final String PROP_NAME_targetHost = "targetHost";
    public static final int PROP_ID_targetHost = 23;
    
    /* 分片索引: SHARDING_INDEX INTEGER */
    public static final String PROP_NAME_shardingIndex = "shardingIndex";
    public static final int PROP_ID_shardingIndex = 24;
    
    /* 总分片数: SHARDING_TOTAL INTEGER */
    public static final String PROP_NAME_shardingTotal = "shardingTotal";
    public static final int PROP_ID_shardingTotal = 25;
    

    private static int _PROP_ID_BOUND = 26;

    
    /* relation: 触发批次 */
    public static final String PROP_NAME_jobFire = "jobFire";
    
    /* component:  */
    public static final String PROP_NAME_taskPayloadComponent = "taskPayloadComponent";
    
    /* component:  */
    public static final String PROP_NAME_resultPayloadComponent = "resultPayloadComponent";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_jobTaskId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_jobTaskId};

    private static final String[] PROP_ID_TO_NAME = new String[26];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_jobTaskId] = PROP_NAME_jobTaskId;
          PROP_NAME_TO_ID.put(PROP_NAME_jobTaskId, PROP_ID_jobTaskId);
      
          PROP_ID_TO_NAME[PROP_ID_jobFireId] = PROP_NAME_jobFireId;
          PROP_NAME_TO_ID.put(PROP_NAME_jobFireId, PROP_ID_jobFireId);
      
          PROP_ID_TO_NAME[PROP_ID_taskNo] = PROP_NAME_taskNo;
          PROP_NAME_TO_ID.put(PROP_NAME_taskNo, PROP_ID_taskNo);
      
          PROP_ID_TO_NAME[PROP_ID_taskStatus] = PROP_NAME_taskStatus;
          PROP_NAME_TO_ID.put(PROP_NAME_taskStatus, PROP_ID_taskStatus);
      
          PROP_ID_TO_NAME[PROP_ID_workerInstanceId] = PROP_NAME_workerInstanceId;
          PROP_NAME_TO_ID.put(PROP_NAME_workerInstanceId, PROP_ID_workerInstanceId);
      
          PROP_ID_TO_NAME[PROP_ID_workerAddress] = PROP_NAME_workerAddress;
          PROP_NAME_TO_ID.put(PROP_NAME_workerAddress, PROP_ID_workerAddress);
      
          PROP_ID_TO_NAME[PROP_ID_taskPayload] = PROP_NAME_taskPayload;
          PROP_NAME_TO_ID.put(PROP_NAME_taskPayload, PROP_ID_taskPayload);
      
          PROP_ID_TO_NAME[PROP_ID_startTime] = PROP_NAME_startTime;
          PROP_NAME_TO_ID.put(PROP_NAME_startTime, PROP_ID_startTime);
      
          PROP_ID_TO_NAME[PROP_ID_endTime] = PROP_NAME_endTime;
          PROP_NAME_TO_ID.put(PROP_NAME_endTime, PROP_ID_endTime);
      
          PROP_ID_TO_NAME[PROP_ID_durationMs] = PROP_NAME_durationMs;
          PROP_NAME_TO_ID.put(PROP_NAME_durationMs, PROP_ID_durationMs);
      
          PROP_ID_TO_NAME[PROP_ID_resultPayload] = PROP_NAME_resultPayload;
          PROP_NAME_TO_ID.put(PROP_NAME_resultPayload, PROP_ID_resultPayload);
      
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
      
          PROP_ID_TO_NAME[PROP_ID_progress] = PROP_NAME_progress;
          PROP_NAME_TO_ID.put(PROP_NAME_progress, PROP_ID_progress);
      
          PROP_ID_TO_NAME[PROP_ID_progressMessage] = PROP_NAME_progressMessage;
          PROP_NAME_TO_ID.put(PROP_NAME_progressMessage, PROP_ID_progressMessage);
      
          PROP_ID_TO_NAME[PROP_ID_targetHost] = PROP_NAME_targetHost;
          PROP_NAME_TO_ID.put(PROP_NAME_targetHost, PROP_ID_targetHost);
      
          PROP_ID_TO_NAME[PROP_ID_shardingIndex] = PROP_NAME_shardingIndex;
          PROP_NAME_TO_ID.put(PROP_NAME_shardingIndex, PROP_ID_shardingIndex);
      
          PROP_ID_TO_NAME[PROP_ID_shardingTotal] = PROP_NAME_shardingTotal;
          PROP_NAME_TO_ID.put(PROP_NAME_shardingTotal, PROP_ID_shardingTotal);
      
    }

    
    /* 任务ID: JOB_TASK_ID */
    private java.lang.String _jobTaskId;
    
    /* 批次ID: JOB_FIRE_ID */
    private java.lang.String _jobFireId;
    
    /* 任务序号: TASK_NO */
    private java.lang.Integer _taskNo;
    
    /* 任务状态: TASK_STATUS */
    private java.lang.Integer _taskStatus;
    
    /* 执行节点ID: WORKER_INSTANCE_ID */
    private java.lang.String _workerInstanceId;
    
    /* 执行节点地址: WORKER_ADDRESS */
    private java.lang.String _workerAddress;
    
    /* 投递参数: TASK_PAYLOAD */
    private java.lang.String _taskPayload;
    
    /* 开始时间: START_TIME */
    private java.sql.Timestamp _startTime;
    
    /* 结束时间: END_TIME */
    private java.sql.Timestamp _endTime;
    
    /* 执行时长(毫秒): DURATION_MS */
    private java.lang.Long _durationMs;
    
    /* 执行结果: RESULT_PAYLOAD */
    private java.lang.String _resultPayload;
    
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
    
    /* 执行进度: PROGRESS */
    private java.lang.Integer _progress;
    
    /* 进度消息: PROGRESS_MESSAGE */
    private java.lang.String _progressMessage;
    
    /* 目标节点地址: TARGET_HOST */
    private java.lang.String _targetHost;
    
    /* 分片索引: SHARDING_INDEX */
    private java.lang.Integer _shardingIndex;
    
    /* 总分片数: SHARDING_TOTAL */
    private java.lang.Integer _shardingTotal;
    

    public _NopJobTask(){
        // for debug
    }

    protected NopJobTask newInstance(){
        NopJobTask entity = new NopJobTask();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopJobTask cloneInstance() {
        NopJobTask entity = newInstance();
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
      return "io.nop.job.dao.entity.NopJobTask";
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
    
        return buildSimpleId(PROP_ID_jobTaskId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_jobTaskId;
          
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
        
            case PROP_ID_jobTaskId:
               return getJobTaskId();
        
            case PROP_ID_jobFireId:
               return getJobFireId();
        
            case PROP_ID_taskNo:
               return getTaskNo();
        
            case PROP_ID_taskStatus:
               return getTaskStatus();
        
            case PROP_ID_workerInstanceId:
               return getWorkerInstanceId();
        
            case PROP_ID_workerAddress:
               return getWorkerAddress();
        
            case PROP_ID_taskPayload:
               return getTaskPayload();
        
            case PROP_ID_startTime:
               return getStartTime();
        
            case PROP_ID_endTime:
               return getEndTime();
        
            case PROP_ID_durationMs:
               return getDurationMs();
        
            case PROP_ID_resultPayload:
               return getResultPayload();
        
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
        
            case PROP_ID_progress:
               return getProgress();
        
            case PROP_ID_progressMessage:
               return getProgressMessage();
        
            case PROP_ID_targetHost:
               return getTargetHost();
        
            case PROP_ID_shardingIndex:
               return getShardingIndex();
        
            case PROP_ID_shardingTotal:
               return getShardingTotal();
        
           default:
              return super.orm_propValue(propId);
        }
    }

    

    @Override
    public void orm_propValue(int propId, Object value){
        switch(propId){
        
            case PROP_ID_jobTaskId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_jobTaskId));
               }
               setJobTaskId(typedValue);
               break;
            }
        
            case PROP_ID_jobFireId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_jobFireId));
               }
               setJobFireId(typedValue);
               break;
            }
        
            case PROP_ID_taskNo:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_taskNo));
               }
               setTaskNo(typedValue);
               break;
            }
        
            case PROP_ID_taskStatus:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_taskStatus));
               }
               setTaskStatus(typedValue);
               break;
            }
        
            case PROP_ID_workerInstanceId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_workerInstanceId));
               }
               setWorkerInstanceId(typedValue);
               break;
            }
        
            case PROP_ID_workerAddress:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_workerAddress));
               }
               setWorkerAddress(typedValue);
               break;
            }
        
            case PROP_ID_taskPayload:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_taskPayload));
               }
               setTaskPayload(typedValue);
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
        
            case PROP_ID_resultPayload:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_resultPayload));
               }
               setResultPayload(typedValue);
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
        
            case PROP_ID_progress:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_progress));
               }
               setProgress(typedValue);
               break;
            }
        
            case PROP_ID_progressMessage:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_progressMessage));
               }
               setProgressMessage(typedValue);
               break;
            }
        
            case PROP_ID_targetHost:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_targetHost));
               }
               setTargetHost(typedValue);
               break;
            }
        
            case PROP_ID_shardingIndex:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_shardingIndex));
               }
               setShardingIndex(typedValue);
               break;
            }
        
            case PROP_ID_shardingTotal:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_shardingTotal));
               }
               setShardingTotal(typedValue);
               break;
            }
        
           default:
              super.orm_propValue(propId,value);
        }
    }

    @Override
    public void orm_internalSet(int propId, Object value) {
        switch(propId){
        
            case PROP_ID_jobTaskId:{
               onInitProp(propId);
               this._jobTaskId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_jobFireId:{
               onInitProp(propId);
               this._jobFireId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_taskNo:{
               onInitProp(propId);
               this._taskNo = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_taskStatus:{
               onInitProp(propId);
               this._taskStatus = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_workerInstanceId:{
               onInitProp(propId);
               this._workerInstanceId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_workerAddress:{
               onInitProp(propId);
               this._workerAddress = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_taskPayload:{
               onInitProp(propId);
               this._taskPayload = (java.lang.String)value;
               
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
        
            case PROP_ID_resultPayload:{
               onInitProp(propId);
               this._resultPayload = (java.lang.String)value;
               
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
        
            case PROP_ID_progress:{
               onInitProp(propId);
               this._progress = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_progressMessage:{
               onInitProp(propId);
               this._progressMessage = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_targetHost:{
               onInitProp(propId);
               this._targetHost = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_shardingIndex:{
               onInitProp(propId);
               this._shardingIndex = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_shardingTotal:{
               onInitProp(propId);
               this._shardingTotal = (java.lang.Integer)value;
               
               break;
            }
        
           default:
              super.orm_internalSet(propId,value);
        }
    }

    
    /**
     * 任务ID: JOB_TASK_ID
     */
    public final java.lang.String getJobTaskId(){
         onPropGet(PROP_ID_jobTaskId);
         return _jobTaskId;
    }

    /**
     * 任务ID: JOB_TASK_ID
     */
    public final void setJobTaskId(java.lang.String value){
        if(onPropSet(PROP_ID_jobTaskId,value)){
            this._jobTaskId = value;
            internalClearRefs(PROP_ID_jobTaskId);
            orm_id();
        }
    }
    
    /**
     * 批次ID: JOB_FIRE_ID
     */
    public final java.lang.String getJobFireId(){
         onPropGet(PROP_ID_jobFireId);
         return _jobFireId;
    }

    /**
     * 批次ID: JOB_FIRE_ID
     */
    public final void setJobFireId(java.lang.String value){
        if(onPropSet(PROP_ID_jobFireId,value)){
            this._jobFireId = value;
            internalClearRefs(PROP_ID_jobFireId);
            
        }
    }
    
    /**
     * 任务序号: TASK_NO
     */
    public final java.lang.Integer getTaskNo(){
         onPropGet(PROP_ID_taskNo);
         return _taskNo;
    }

    /**
     * 任务序号: TASK_NO
     */
    public final void setTaskNo(java.lang.Integer value){
        if(onPropSet(PROP_ID_taskNo,value)){
            this._taskNo = value;
            internalClearRefs(PROP_ID_taskNo);
            
        }
    }
    
    /**
     * 任务状态: TASK_STATUS
     */
    public final java.lang.Integer getTaskStatus(){
         onPropGet(PROP_ID_taskStatus);
         return _taskStatus;
    }

    /**
     * 任务状态: TASK_STATUS
     */
    public final void setTaskStatus(java.lang.Integer value){
        if(onPropSet(PROP_ID_taskStatus,value)){
            this._taskStatus = value;
            internalClearRefs(PROP_ID_taskStatus);
            
        }
    }
    
    /**
     * 执行节点ID: WORKER_INSTANCE_ID
     */
    public final java.lang.String getWorkerInstanceId(){
         onPropGet(PROP_ID_workerInstanceId);
         return _workerInstanceId;
    }

    /**
     * 执行节点ID: WORKER_INSTANCE_ID
     */
    public final void setWorkerInstanceId(java.lang.String value){
        if(onPropSet(PROP_ID_workerInstanceId,value)){
            this._workerInstanceId = value;
            internalClearRefs(PROP_ID_workerInstanceId);
            
        }
    }
    
    /**
     * 执行节点地址: WORKER_ADDRESS
     */
    public final java.lang.String getWorkerAddress(){
         onPropGet(PROP_ID_workerAddress);
         return _workerAddress;
    }

    /**
     * 执行节点地址: WORKER_ADDRESS
     */
    public final void setWorkerAddress(java.lang.String value){
        if(onPropSet(PROP_ID_workerAddress,value)){
            this._workerAddress = value;
            internalClearRefs(PROP_ID_workerAddress);
            
        }
    }
    
    /**
     * 投递参数: TASK_PAYLOAD
     */
    public final java.lang.String getTaskPayload(){
         onPropGet(PROP_ID_taskPayload);
         return _taskPayload;
    }

    /**
     * 投递参数: TASK_PAYLOAD
     */
    public final void setTaskPayload(java.lang.String value){
        if(onPropSet(PROP_ID_taskPayload,value)){
            this._taskPayload = value;
            internalClearRefs(PROP_ID_taskPayload);
            
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
     * 执行结果: RESULT_PAYLOAD
     */
    public final java.lang.String getResultPayload(){
         onPropGet(PROP_ID_resultPayload);
         return _resultPayload;
    }

    /**
     * 执行结果: RESULT_PAYLOAD
     */
    public final void setResultPayload(java.lang.String value){
        if(onPropSet(PROP_ID_resultPayload,value)){
            this._resultPayload = value;
            internalClearRefs(PROP_ID_resultPayload);
            
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
     * 执行进度: PROGRESS
     */
    public final java.lang.Integer getProgress(){
         onPropGet(PROP_ID_progress);
         return _progress;
    }

    /**
     * 执行进度: PROGRESS
     */
    public final void setProgress(java.lang.Integer value){
        if(onPropSet(PROP_ID_progress,value)){
            this._progress = value;
            internalClearRefs(PROP_ID_progress);
            
        }
    }
    
    /**
     * 进度消息: PROGRESS_MESSAGE
     */
    public final java.lang.String getProgressMessage(){
         onPropGet(PROP_ID_progressMessage);
         return _progressMessage;
    }

    /**
     * 进度消息: PROGRESS_MESSAGE
     */
    public final void setProgressMessage(java.lang.String value){
        if(onPropSet(PROP_ID_progressMessage,value)){
            this._progressMessage = value;
            internalClearRefs(PROP_ID_progressMessage);
            
        }
    }
    
    /**
     * 目标节点地址: TARGET_HOST
     */
    public final java.lang.String getTargetHost(){
         onPropGet(PROP_ID_targetHost);
         return _targetHost;
    }

    /**
     * 目标节点地址: TARGET_HOST
     */
    public final void setTargetHost(java.lang.String value){
        if(onPropSet(PROP_ID_targetHost,value)){
            this._targetHost = value;
            internalClearRefs(PROP_ID_targetHost);
            
        }
    }
    
    /**
     * 分片索引: SHARDING_INDEX
     */
    public final java.lang.Integer getShardingIndex(){
         onPropGet(PROP_ID_shardingIndex);
         return _shardingIndex;
    }

    /**
     * 分片索引: SHARDING_INDEX
     */
    public final void setShardingIndex(java.lang.Integer value){
        if(onPropSet(PROP_ID_shardingIndex,value)){
            this._shardingIndex = value;
            internalClearRefs(PROP_ID_shardingIndex);
            
        }
    }
    
    /**
     * 总分片数: SHARDING_TOTAL
     */
    public final java.lang.Integer getShardingTotal(){
         onPropGet(PROP_ID_shardingTotal);
         return _shardingTotal;
    }

    /**
     * 总分片数: SHARDING_TOTAL
     */
    public final void setShardingTotal(java.lang.Integer value){
        if(onPropSet(PROP_ID_shardingTotal,value)){
            this._shardingTotal = value;
            internalClearRefs(PROP_ID_shardingTotal);
            
        }
    }
    
    /**
     * 触发批次
     */
    public final io.nop.job.dao.entity.NopJobFire getJobFire(){
       return (io.nop.job.dao.entity.NopJobFire)internalGetRefEntity(PROP_NAME_jobFire);
    }

    public final void setJobFire(io.nop.job.dao.entity.NopJobFire refEntity){
   
           if(refEntity == null){
           
                   this.setJobFireId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_jobFire, refEntity,()->{
           
                           this.setJobFireId(refEntity.getJobFireId());
                       
           });
           }
       
    }
       
   private io.nop.orm.component.JsonOrmComponent _taskPayloadComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_taskPayloadComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_taskPayloadComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_taskPayload);
      
   }

   public final io.nop.orm.component.JsonOrmComponent getTaskPayloadComponent(){
      if(_taskPayloadComponent == null){
          _taskPayloadComponent = new io.nop.orm.component.JsonOrmComponent();
          _taskPayloadComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_taskPayloadComponent);
      }
      return _taskPayloadComponent;
   }

   private io.nop.orm.component.JsonOrmComponent _resultPayloadComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_resultPayloadComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_resultPayloadComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_resultPayload);
      
   }

   public final io.nop.orm.component.JsonOrmComponent getResultPayloadComponent(){
      if(_resultPayloadComponent == null){
          _resultPayloadComponent = new io.nop.orm.component.JsonOrmComponent();
          _resultPayloadComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_resultPayloadComponent);
      }
      return _resultPayloadComponent;
   }

}
// resume CPD analysis - CPD-ON
