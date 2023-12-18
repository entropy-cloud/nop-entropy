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

import io.nop.batch.dao.entity.NopBatchTask;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  批处理任务: nop_batch_task
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopBatchTask extends DynamicOrmEntity{
    
    /* SID: SID VARCHAR */
    public static final String PROP_NAME_sid = "sid";
    public static final int PROP_ID_sid = 1;
    
    /* 任务名: TASK_NAME VARCHAR */
    public static final String PROP_NAME_taskName = "taskName";
    public static final int PROP_ID_taskName = 2;
    
    /* 唯一Key: TASK_KEY VARCHAR */
    public static final String PROP_NAME_taskKey = "taskKey";
    public static final int PROP_ID_taskKey = 3;
    
    /* 任务状态: TASK_STATUS INTEGER */
    public static final String PROP_NAME_taskStatus = "taskStatus";
    public static final int PROP_ID_taskStatus = 4;
    
    /* 任务启动时间: START_TIME TIMESTAMP */
    public static final String PROP_NAME_startTime = "startTime";
    public static final int PROP_ID_startTime = 5;
    
    /* 任务结束时间: END_TIME TIMESTAMP */
    public static final String PROP_NAME_endTime = "endTime";
    public static final int PROP_ID_endTime = 6;
    
    /* 任务参数: TASK_PARAMS VARCHAR */
    public static final String PROP_NAME_taskParams = "taskParams";
    public static final int PROP_ID_taskParams = 7;
    
    /* 执行者: WORKER VARCHAR */
    public static final String PROP_NAME_worker = "worker";
    public static final int PROP_ID_worker = 8;
    
    /* 输入文件: INPUT_FILE_ID VARCHAR */
    public static final String PROP_NAME_inputFileId = "inputFileId";
    public static final int PROP_ID_inputFileId = 9;
    
    /* 重试次数: RETRY_COUNT INTEGER */
    public static final String PROP_NAME_retryCount = "retryCount";
    public static final int PROP_ID_retryCount = 10;
    
    /* 返回状态码: RESULT_STATUS INTEGER */
    public static final String PROP_NAME_resultStatus = "resultStatus";
    public static final int PROP_ID_resultStatus = 11;
    
    /* 返回码: RESULT_CODE VARCHAR */
    public static final String PROP_NAME_resultCode = "resultCode";
    public static final int PROP_ID_resultCode = 12;
    
    /* 返回消息: RESULT_MSG VARCHAR */
    public static final String PROP_NAME_resultMsg = "resultMsg";
    public static final int PROP_ID_resultMsg = 13;
    
    /* 错误堆栈: ERROR_STACK VARCHAR */
    public static final String PROP_NAME_errorStack = "errorStack";
    public static final int PROP_ID_errorStack = 14;
    
    /* 已完成记录下标: COMPLETED_INDEX BIGINT */
    public static final String PROP_NAME_completedIndex = "completedIndex";
    public static final int PROP_ID_completedIndex = 15;
    
    /* 读数量: READ_COUNT BIGINT */
    public static final String PROP_NAME_readCount = "readCount";
    public static final int PROP_ID_readCount = 16;
    
    /* 写数量: WRITE_COUNT BIGINT */
    public static final String PROP_NAME_writeCount = "writeCount";
    public static final int PROP_ID_writeCount = 17;
    
    /* 处理数量: PROCESS_COUNT BIGINT */
    public static final String PROP_NAME_processCount = "processCount";
    public static final int PROP_ID_processCount = 18;
    
    /* 跳过数量: SKIP_COUNT BIGINT */
    public static final String PROP_NAME_skipCount = "skipCount";
    public static final int PROP_ID_skipCount = 19;
    
    /* 数据版本: VERSION BIGINT */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 20;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 21;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 22;
    
    /* 修改人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 23;
    
    /* 修改时间: UPDATE_TIME TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 24;
    
    /* 备注: REMARK VARCHAR */
    public static final String PROP_NAME_remark = "remark";
    public static final int PROP_ID_remark = 25;
    

    private static int _PROP_ID_BOUND = 26;

    
    /* relation: 批处理文件 */
    public static final String PROP_NAME_inputFile = "inputFile";
    
    /* relation: 任务状态变量 */
    public static final String PROP_NAME_taskStates = "taskStates";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_sid);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_sid};

    private static final String[] PROP_ID_TO_NAME = new String[26];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_sid] = PROP_NAME_sid;
          PROP_NAME_TO_ID.put(PROP_NAME_sid, PROP_ID_sid);
      
          PROP_ID_TO_NAME[PROP_ID_taskName] = PROP_NAME_taskName;
          PROP_NAME_TO_ID.put(PROP_NAME_taskName, PROP_ID_taskName);
      
          PROP_ID_TO_NAME[PROP_ID_taskKey] = PROP_NAME_taskKey;
          PROP_NAME_TO_ID.put(PROP_NAME_taskKey, PROP_ID_taskKey);
      
          PROP_ID_TO_NAME[PROP_ID_taskStatus] = PROP_NAME_taskStatus;
          PROP_NAME_TO_ID.put(PROP_NAME_taskStatus, PROP_ID_taskStatus);
      
          PROP_ID_TO_NAME[PROP_ID_startTime] = PROP_NAME_startTime;
          PROP_NAME_TO_ID.put(PROP_NAME_startTime, PROP_ID_startTime);
      
          PROP_ID_TO_NAME[PROP_ID_endTime] = PROP_NAME_endTime;
          PROP_NAME_TO_ID.put(PROP_NAME_endTime, PROP_ID_endTime);
      
          PROP_ID_TO_NAME[PROP_ID_taskParams] = PROP_NAME_taskParams;
          PROP_NAME_TO_ID.put(PROP_NAME_taskParams, PROP_ID_taskParams);
      
          PROP_ID_TO_NAME[PROP_ID_worker] = PROP_NAME_worker;
          PROP_NAME_TO_ID.put(PROP_NAME_worker, PROP_ID_worker);
      
          PROP_ID_TO_NAME[PROP_ID_inputFileId] = PROP_NAME_inputFileId;
          PROP_NAME_TO_ID.put(PROP_NAME_inputFileId, PROP_ID_inputFileId);
      
          PROP_ID_TO_NAME[PROP_ID_retryCount] = PROP_NAME_retryCount;
          PROP_NAME_TO_ID.put(PROP_NAME_retryCount, PROP_ID_retryCount);
      
          PROP_ID_TO_NAME[PROP_ID_resultStatus] = PROP_NAME_resultStatus;
          PROP_NAME_TO_ID.put(PROP_NAME_resultStatus, PROP_ID_resultStatus);
      
          PROP_ID_TO_NAME[PROP_ID_resultCode] = PROP_NAME_resultCode;
          PROP_NAME_TO_ID.put(PROP_NAME_resultCode, PROP_ID_resultCode);
      
          PROP_ID_TO_NAME[PROP_ID_resultMsg] = PROP_NAME_resultMsg;
          PROP_NAME_TO_ID.put(PROP_NAME_resultMsg, PROP_ID_resultMsg);
      
          PROP_ID_TO_NAME[PROP_ID_errorStack] = PROP_NAME_errorStack;
          PROP_NAME_TO_ID.put(PROP_NAME_errorStack, PROP_ID_errorStack);
      
          PROP_ID_TO_NAME[PROP_ID_completedIndex] = PROP_NAME_completedIndex;
          PROP_NAME_TO_ID.put(PROP_NAME_completedIndex, PROP_ID_completedIndex);
      
          PROP_ID_TO_NAME[PROP_ID_readCount] = PROP_NAME_readCount;
          PROP_NAME_TO_ID.put(PROP_NAME_readCount, PROP_ID_readCount);
      
          PROP_ID_TO_NAME[PROP_ID_writeCount] = PROP_NAME_writeCount;
          PROP_NAME_TO_ID.put(PROP_NAME_writeCount, PROP_ID_writeCount);
      
          PROP_ID_TO_NAME[PROP_ID_processCount] = PROP_NAME_processCount;
          PROP_NAME_TO_ID.put(PROP_NAME_processCount, PROP_ID_processCount);
      
          PROP_ID_TO_NAME[PROP_ID_skipCount] = PROP_NAME_skipCount;
          PROP_NAME_TO_ID.put(PROP_NAME_skipCount, PROP_ID_skipCount);
      
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

    
    /* SID: SID */
    private java.lang.String _sid;
    
    /* 任务名: TASK_NAME */
    private java.lang.String _taskName;
    
    /* 唯一Key: TASK_KEY */
    private java.lang.String _taskKey;
    
    /* 任务状态: TASK_STATUS */
    private java.lang.Integer _taskStatus;
    
    /* 任务启动时间: START_TIME */
    private java.sql.Timestamp _startTime;
    
    /* 任务结束时间: END_TIME */
    private java.sql.Timestamp _endTime;
    
    /* 任务参数: TASK_PARAMS */
    private java.lang.String _taskParams;
    
    /* 执行者: WORKER */
    private java.lang.String _worker;
    
    /* 输入文件: INPUT_FILE_ID */
    private java.lang.String _inputFileId;
    
    /* 重试次数: RETRY_COUNT */
    private java.lang.Integer _retryCount;
    
    /* 返回状态码: RESULT_STATUS */
    private java.lang.Integer _resultStatus;
    
    /* 返回码: RESULT_CODE */
    private java.lang.String _resultCode;
    
    /* 返回消息: RESULT_MSG */
    private java.lang.String _resultMsg;
    
    /* 错误堆栈: ERROR_STACK */
    private java.lang.String _errorStack;
    
    /* 已完成记录下标: COMPLETED_INDEX */
    private java.lang.Long _completedIndex;
    
    /* 读数量: READ_COUNT */
    private java.lang.Long _readCount;
    
    /* 写数量: WRITE_COUNT */
    private java.lang.Long _writeCount;
    
    /* 处理数量: PROCESS_COUNT */
    private java.lang.Long _processCount;
    
    /* 跳过数量: SKIP_COUNT */
    private java.lang.Long _skipCount;
    
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
    

    public _NopBatchTask(){
        // for debug
    }

    protected NopBatchTask newInstance(){
       return new NopBatchTask();
    }

    @Override
    public NopBatchTask cloneInstance() {
        NopBatchTask entity = newInstance();
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
      return "io.nop.batch.dao.entity.NopBatchTask";
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
        
            case PROP_ID_taskName:
               return getTaskName();
        
            case PROP_ID_taskKey:
               return getTaskKey();
        
            case PROP_ID_taskStatus:
               return getTaskStatus();
        
            case PROP_ID_startTime:
               return getStartTime();
        
            case PROP_ID_endTime:
               return getEndTime();
        
            case PROP_ID_taskParams:
               return getTaskParams();
        
            case PROP_ID_worker:
               return getWorker();
        
            case PROP_ID_inputFileId:
               return getInputFileId();
        
            case PROP_ID_retryCount:
               return getRetryCount();
        
            case PROP_ID_resultStatus:
               return getResultStatus();
        
            case PROP_ID_resultCode:
               return getResultCode();
        
            case PROP_ID_resultMsg:
               return getResultMsg();
        
            case PROP_ID_errorStack:
               return getErrorStack();
        
            case PROP_ID_completedIndex:
               return getCompletedIndex();
        
            case PROP_ID_readCount:
               return getReadCount();
        
            case PROP_ID_writeCount:
               return getWriteCount();
        
            case PROP_ID_processCount:
               return getProcessCount();
        
            case PROP_ID_skipCount:
               return getSkipCount();
        
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
        
            case PROP_ID_sid:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_sid));
               }
               setSid(typedValue);
               break;
            }
        
            case PROP_ID_taskName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_taskName));
               }
               setTaskName(typedValue);
               break;
            }
        
            case PROP_ID_taskKey:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_taskKey));
               }
               setTaskKey(typedValue);
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
        
            case PROP_ID_taskParams:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_taskParams));
               }
               setTaskParams(typedValue);
               break;
            }
        
            case PROP_ID_worker:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_worker));
               }
               setWorker(typedValue);
               break;
            }
        
            case PROP_ID_inputFileId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_inputFileId));
               }
               setInputFileId(typedValue);
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
        
            case PROP_ID_completedIndex:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_completedIndex));
               }
               setCompletedIndex(typedValue);
               break;
            }
        
            case PROP_ID_readCount:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_readCount));
               }
               setReadCount(typedValue);
               break;
            }
        
            case PROP_ID_writeCount:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_writeCount));
               }
               setWriteCount(typedValue);
               break;
            }
        
            case PROP_ID_processCount:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_processCount));
               }
               setProcessCount(typedValue);
               break;
            }
        
            case PROP_ID_skipCount:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_skipCount));
               }
               setSkipCount(typedValue);
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
        
            case PROP_ID_sid:{
               onInitProp(propId);
               this._sid = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_taskName:{
               onInitProp(propId);
               this._taskName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_taskKey:{
               onInitProp(propId);
               this._taskKey = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_taskStatus:{
               onInitProp(propId);
               this._taskStatus = (java.lang.Integer)value;
               
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
        
            case PROP_ID_taskParams:{
               onInitProp(propId);
               this._taskParams = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_worker:{
               onInitProp(propId);
               this._worker = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_inputFileId:{
               onInitProp(propId);
               this._inputFileId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_retryCount:{
               onInitProp(propId);
               this._retryCount = (java.lang.Integer)value;
               
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
        
            case PROP_ID_completedIndex:{
               onInitProp(propId);
               this._completedIndex = (java.lang.Long)value;
               
               break;
            }
        
            case PROP_ID_readCount:{
               onInitProp(propId);
               this._readCount = (java.lang.Long)value;
               
               break;
            }
        
            case PROP_ID_writeCount:{
               onInitProp(propId);
               this._writeCount = (java.lang.Long)value;
               
               break;
            }
        
            case PROP_ID_processCount:{
               onInitProp(propId);
               this._processCount = (java.lang.Long)value;
               
               break;
            }
        
            case PROP_ID_skipCount:{
               onInitProp(propId);
               this._skipCount = (java.lang.Long)value;
               
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
     * SID: SID
     */
    public java.lang.String getSid(){
         onPropGet(PROP_ID_sid);
         return _sid;
    }

    /**
     * SID: SID
     */
    public void setSid(java.lang.String value){
        if(onPropSet(PROP_ID_sid,value)){
            this._sid = value;
            internalClearRefs(PROP_ID_sid);
            orm_id();
        }
    }
    
    /**
     * 任务名: TASK_NAME
     */
    public java.lang.String getTaskName(){
         onPropGet(PROP_ID_taskName);
         return _taskName;
    }

    /**
     * 任务名: TASK_NAME
     */
    public void setTaskName(java.lang.String value){
        if(onPropSet(PROP_ID_taskName,value)){
            this._taskName = value;
            internalClearRefs(PROP_ID_taskName);
            
        }
    }
    
    /**
     * 唯一Key: TASK_KEY
     */
    public java.lang.String getTaskKey(){
         onPropGet(PROP_ID_taskKey);
         return _taskKey;
    }

    /**
     * 唯一Key: TASK_KEY
     */
    public void setTaskKey(java.lang.String value){
        if(onPropSet(PROP_ID_taskKey,value)){
            this._taskKey = value;
            internalClearRefs(PROP_ID_taskKey);
            
        }
    }
    
    /**
     * 任务状态: TASK_STATUS
     */
    public java.lang.Integer getTaskStatus(){
         onPropGet(PROP_ID_taskStatus);
         return _taskStatus;
    }

    /**
     * 任务状态: TASK_STATUS
     */
    public void setTaskStatus(java.lang.Integer value){
        if(onPropSet(PROP_ID_taskStatus,value)){
            this._taskStatus = value;
            internalClearRefs(PROP_ID_taskStatus);
            
        }
    }
    
    /**
     * 任务启动时间: START_TIME
     */
    public java.sql.Timestamp getStartTime(){
         onPropGet(PROP_ID_startTime);
         return _startTime;
    }

    /**
     * 任务启动时间: START_TIME
     */
    public void setStartTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_startTime,value)){
            this._startTime = value;
            internalClearRefs(PROP_ID_startTime);
            
        }
    }
    
    /**
     * 任务结束时间: END_TIME
     */
    public java.sql.Timestamp getEndTime(){
         onPropGet(PROP_ID_endTime);
         return _endTime;
    }

    /**
     * 任务结束时间: END_TIME
     */
    public void setEndTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_endTime,value)){
            this._endTime = value;
            internalClearRefs(PROP_ID_endTime);
            
        }
    }
    
    /**
     * 任务参数: TASK_PARAMS
     */
    public java.lang.String getTaskParams(){
         onPropGet(PROP_ID_taskParams);
         return _taskParams;
    }

    /**
     * 任务参数: TASK_PARAMS
     */
    public void setTaskParams(java.lang.String value){
        if(onPropSet(PROP_ID_taskParams,value)){
            this._taskParams = value;
            internalClearRefs(PROP_ID_taskParams);
            
        }
    }
    
    /**
     * 执行者: WORKER
     */
    public java.lang.String getWorker(){
         onPropGet(PROP_ID_worker);
         return _worker;
    }

    /**
     * 执行者: WORKER
     */
    public void setWorker(java.lang.String value){
        if(onPropSet(PROP_ID_worker,value)){
            this._worker = value;
            internalClearRefs(PROP_ID_worker);
            
        }
    }
    
    /**
     * 输入文件: INPUT_FILE_ID
     */
    public java.lang.String getInputFileId(){
         onPropGet(PROP_ID_inputFileId);
         return _inputFileId;
    }

    /**
     * 输入文件: INPUT_FILE_ID
     */
    public void setInputFileId(java.lang.String value){
        if(onPropSet(PROP_ID_inputFileId,value)){
            this._inputFileId = value;
            internalClearRefs(PROP_ID_inputFileId);
            
        }
    }
    
    /**
     * 重试次数: RETRY_COUNT
     */
    public java.lang.Integer getRetryCount(){
         onPropGet(PROP_ID_retryCount);
         return _retryCount;
    }

    /**
     * 重试次数: RETRY_COUNT
     */
    public void setRetryCount(java.lang.Integer value){
        if(onPropSet(PROP_ID_retryCount,value)){
            this._retryCount = value;
            internalClearRefs(PROP_ID_retryCount);
            
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
     * 已完成记录下标: COMPLETED_INDEX
     */
    public java.lang.Long getCompletedIndex(){
         onPropGet(PROP_ID_completedIndex);
         return _completedIndex;
    }

    /**
     * 已完成记录下标: COMPLETED_INDEX
     */
    public void setCompletedIndex(java.lang.Long value){
        if(onPropSet(PROP_ID_completedIndex,value)){
            this._completedIndex = value;
            internalClearRefs(PROP_ID_completedIndex);
            
        }
    }
    
    /**
     * 读数量: READ_COUNT
     */
    public java.lang.Long getReadCount(){
         onPropGet(PROP_ID_readCount);
         return _readCount;
    }

    /**
     * 读数量: READ_COUNT
     */
    public void setReadCount(java.lang.Long value){
        if(onPropSet(PROP_ID_readCount,value)){
            this._readCount = value;
            internalClearRefs(PROP_ID_readCount);
            
        }
    }
    
    /**
     * 写数量: WRITE_COUNT
     */
    public java.lang.Long getWriteCount(){
         onPropGet(PROP_ID_writeCount);
         return _writeCount;
    }

    /**
     * 写数量: WRITE_COUNT
     */
    public void setWriteCount(java.lang.Long value){
        if(onPropSet(PROP_ID_writeCount,value)){
            this._writeCount = value;
            internalClearRefs(PROP_ID_writeCount);
            
        }
    }
    
    /**
     * 处理数量: PROCESS_COUNT
     */
    public java.lang.Long getProcessCount(){
         onPropGet(PROP_ID_processCount);
         return _processCount;
    }

    /**
     * 处理数量: PROCESS_COUNT
     */
    public void setProcessCount(java.lang.Long value){
        if(onPropSet(PROP_ID_processCount,value)){
            this._processCount = value;
            internalClearRefs(PROP_ID_processCount);
            
        }
    }
    
    /**
     * 跳过数量: SKIP_COUNT
     */
    public java.lang.Long getSkipCount(){
         onPropGet(PROP_ID_skipCount);
         return _skipCount;
    }

    /**
     * 跳过数量: SKIP_COUNT
     */
    public void setSkipCount(java.lang.Long value){
        if(onPropSet(PROP_ID_skipCount,value)){
            this._skipCount = value;
            internalClearRefs(PROP_ID_skipCount);
            
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
     * 备注: REMARK
     */
    public java.lang.String getRemark(){
         onPropGet(PROP_ID_remark);
         return _remark;
    }

    /**
     * 备注: REMARK
     */
    public void setRemark(java.lang.String value){
        if(onPropSet(PROP_ID_remark,value)){
            this._remark = value;
            internalClearRefs(PROP_ID_remark);
            
        }
    }
    
    /**
     * 批处理文件
     */
    public io.nop.batch.dao.entity.NopBatchFile getInputFile(){
       return (io.nop.batch.dao.entity.NopBatchFile)internalGetRefEntity(PROP_NAME_inputFile);
    }

    public void setInputFile(io.nop.batch.dao.entity.NopBatchFile refEntity){
       if(refEntity == null){
         
         this.setInputFileId(null);
         
       }else{
          internalSetRefEntity(PROP_NAME_inputFile, refEntity,()->{
             
                    this.setInputFileId(refEntity.getSid());
                 
          });
       }
    }
       
    private final OrmEntitySet<io.nop.batch.dao.entity.NopBatchTaskState> _taskStates = new OrmEntitySet<>(this, PROP_NAME_taskStates,
        io.nop.batch.dao.entity.NopBatchTaskState.PROP_NAME_task, null,io.nop.batch.dao.entity.NopBatchTaskState.class);

    /**
     * 任务状态变量。 refPropName: task, keyProp: {rel.keyProp}
     */
    public IOrmEntitySet<io.nop.batch.dao.entity.NopBatchTaskState> getTaskStates(){
       return _taskStates;
    }
       
}
// resume CPD analysis - CPD-ON
