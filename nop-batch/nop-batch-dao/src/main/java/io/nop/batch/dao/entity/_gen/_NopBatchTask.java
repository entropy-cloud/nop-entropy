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
    
    /* 执行次数: EXEC_COUNT INTEGER */
    public static final String PROP_NAME_execCount = "execCount";
    public static final int PROP_ID_execCount = 8;
    
    /* 执行者: WORKER_ID VARCHAR */
    public static final String PROP_NAME_workerId = "workerId";
    public static final int PROP_ID_workerId = 9;
    
    /* 输入文件: INPUT_FILE_ID VARCHAR */
    public static final String PROP_NAME_inputFileId = "inputFileId";
    public static final int PROP_ID_inputFileId = 10;
    
    /* 关联流程步骤ID: FLOW_STEP_ID VARCHAR */
    public static final String PROP_NAME_flowStepId = "flowStepId";
    public static final int PROP_ID_flowStepId = 11;
    
    /* 重启时间: RESTART_TIME TIMESTAMP */
    public static final String PROP_NAME_restartTime = "restartTime";
    public static final int PROP_ID_restartTime = 12;
    
    /* 返回状态码: RESULT_STATUS INTEGER */
    public static final String PROP_NAME_resultStatus = "resultStatus";
    public static final int PROP_ID_resultStatus = 13;
    
    /* 返回码: RESULT_CODE VARCHAR */
    public static final String PROP_NAME_resultCode = "resultCode";
    public static final int PROP_ID_resultCode = 14;
    
    /* 返回消息: RESULT_MSG VARCHAR */
    public static final String PROP_NAME_resultMsg = "resultMsg";
    public static final int PROP_ID_resultMsg = 15;
    
    /* 错误堆栈: ERROR_STACK VARCHAR */
    public static final String PROP_NAME_errorStack = "errorStack";
    public static final int PROP_ID_errorStack = 16;
    
    /* 已完成记录下标: COMPLETED_INDEX BIGINT */
    public static final String PROP_NAME_completedIndex = "completedIndex";
    public static final int PROP_ID_completedIndex = 17;
    
    /* 完成条目数量: COMPLETE_ITEM_COUNT BIGINT */
    public static final String PROP_NAME_completeItemCount = "completeItemCount";
    public static final int PROP_ID_completeItemCount = 18;
    
    /* 重试加载次数: LOAD_RETRY_COUNT INTEGER */
    public static final String PROP_NAME_loadRetryCount = "loadRetryCount";
    public static final int PROP_ID_loadRetryCount = 19;
    
    /* 加载跳过数量: LOAD_SKIP_COUNT BIGINT */
    public static final String PROP_NAME_loadSkipCount = "loadSkipCount";
    public static final int PROP_ID_loadSkipCount = 20;
    
    /* 重试条目次数: RETRY_ITEM_COUNT INTEGER */
    public static final String PROP_NAME_retryItemCount = "retryItemCount";
    public static final int PROP_ID_retryItemCount = 21;
    
    /* 处理条目数量: PROCESS_ITEM_COUNT BIGINT */
    public static final String PROP_NAME_processItemCount = "processItemCount";
    public static final int PROP_ID_processItemCount = 22;
    
    /* 跳过条目数量: SKIP_ITEM_COUNT BIGINT */
    public static final String PROP_NAME_skipItemCount = "skipItemCount";
    public static final int PROP_ID_skipItemCount = 23;
    
    /* 写入条目数量: WRITE_ITEM_COUNT BIGINT */
    public static final String PROP_NAME_writeItemCount = "writeItemCount";
    public static final int PROP_ID_writeItemCount = 24;
    
    /* 数据版本: VERSION BIGINT */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 25;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 26;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 27;
    
    /* 修改人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 28;
    
    /* 修改时间: UPDATE_TIME TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 29;
    
    /* 备注: REMARK VARCHAR */
    public static final String PROP_NAME_remark = "remark";
    public static final int PROP_ID_remark = 30;
    

    private static int _PROP_ID_BOUND = 31;

    
    /* relation: 批处理文件 */
    public static final String PROP_NAME_inputFile = "inputFile";
    
    /* relation: 任务状态变量 */
    public static final String PROP_NAME_taskVars = "taskVars";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_sid);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_sid};

    private static final String[] PROP_ID_TO_NAME = new String[31];
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
      
          PROP_ID_TO_NAME[PROP_ID_execCount] = PROP_NAME_execCount;
          PROP_NAME_TO_ID.put(PROP_NAME_execCount, PROP_ID_execCount);
      
          PROP_ID_TO_NAME[PROP_ID_workerId] = PROP_NAME_workerId;
          PROP_NAME_TO_ID.put(PROP_NAME_workerId, PROP_ID_workerId);
      
          PROP_ID_TO_NAME[PROP_ID_inputFileId] = PROP_NAME_inputFileId;
          PROP_NAME_TO_ID.put(PROP_NAME_inputFileId, PROP_ID_inputFileId);
      
          PROP_ID_TO_NAME[PROP_ID_flowStepId] = PROP_NAME_flowStepId;
          PROP_NAME_TO_ID.put(PROP_NAME_flowStepId, PROP_ID_flowStepId);
      
          PROP_ID_TO_NAME[PROP_ID_restartTime] = PROP_NAME_restartTime;
          PROP_NAME_TO_ID.put(PROP_NAME_restartTime, PROP_ID_restartTime);
      
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
      
          PROP_ID_TO_NAME[PROP_ID_completeItemCount] = PROP_NAME_completeItemCount;
          PROP_NAME_TO_ID.put(PROP_NAME_completeItemCount, PROP_ID_completeItemCount);
      
          PROP_ID_TO_NAME[PROP_ID_loadRetryCount] = PROP_NAME_loadRetryCount;
          PROP_NAME_TO_ID.put(PROP_NAME_loadRetryCount, PROP_ID_loadRetryCount);
      
          PROP_ID_TO_NAME[PROP_ID_loadSkipCount] = PROP_NAME_loadSkipCount;
          PROP_NAME_TO_ID.put(PROP_NAME_loadSkipCount, PROP_ID_loadSkipCount);
      
          PROP_ID_TO_NAME[PROP_ID_retryItemCount] = PROP_NAME_retryItemCount;
          PROP_NAME_TO_ID.put(PROP_NAME_retryItemCount, PROP_ID_retryItemCount);
      
          PROP_ID_TO_NAME[PROP_ID_processItemCount] = PROP_NAME_processItemCount;
          PROP_NAME_TO_ID.put(PROP_NAME_processItemCount, PROP_ID_processItemCount);
      
          PROP_ID_TO_NAME[PROP_ID_skipItemCount] = PROP_NAME_skipItemCount;
          PROP_NAME_TO_ID.put(PROP_NAME_skipItemCount, PROP_ID_skipItemCount);
      
          PROP_ID_TO_NAME[PROP_ID_writeItemCount] = PROP_NAME_writeItemCount;
          PROP_NAME_TO_ID.put(PROP_NAME_writeItemCount, PROP_ID_writeItemCount);
      
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
    
    /* 执行次数: EXEC_COUNT */
    private java.lang.Integer _execCount;
    
    /* 执行者: WORKER_ID */
    private java.lang.String _workerId;
    
    /* 输入文件: INPUT_FILE_ID */
    private java.lang.String _inputFileId;
    
    /* 关联流程步骤ID: FLOW_STEP_ID */
    private java.lang.String _flowStepId;
    
    /* 重启时间: RESTART_TIME */
    private java.sql.Timestamp _restartTime;
    
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
    
    /* 完成条目数量: COMPLETE_ITEM_COUNT */
    private java.lang.Long _completeItemCount;
    
    /* 重试加载次数: LOAD_RETRY_COUNT */
    private java.lang.Integer _loadRetryCount;
    
    /* 加载跳过数量: LOAD_SKIP_COUNT */
    private java.lang.Long _loadSkipCount;
    
    /* 重试条目次数: RETRY_ITEM_COUNT */
    private java.lang.Integer _retryItemCount;
    
    /* 处理条目数量: PROCESS_ITEM_COUNT */
    private java.lang.Long _processItemCount;
    
    /* 跳过条目数量: SKIP_ITEM_COUNT */
    private java.lang.Long _skipItemCount;
    
    /* 写入条目数量: WRITE_ITEM_COUNT */
    private java.lang.Long _writeItemCount;
    
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
        NopBatchTask entity = new NopBatchTask();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopBatchTask cloneInstance() {
        NopBatchTask entity = newInstance();
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
        
            case PROP_ID_execCount:
               return getExecCount();
        
            case PROP_ID_workerId:
               return getWorkerId();
        
            case PROP_ID_inputFileId:
               return getInputFileId();
        
            case PROP_ID_flowStepId:
               return getFlowStepId();
        
            case PROP_ID_restartTime:
               return getRestartTime();
        
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
        
            case PROP_ID_completeItemCount:
               return getCompleteItemCount();
        
            case PROP_ID_loadRetryCount:
               return getLoadRetryCount();
        
            case PROP_ID_loadSkipCount:
               return getLoadSkipCount();
        
            case PROP_ID_retryItemCount:
               return getRetryItemCount();
        
            case PROP_ID_processItemCount:
               return getProcessItemCount();
        
            case PROP_ID_skipItemCount:
               return getSkipItemCount();
        
            case PROP_ID_writeItemCount:
               return getWriteItemCount();
        
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
        
            case PROP_ID_execCount:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_execCount));
               }
               setExecCount(typedValue);
               break;
            }
        
            case PROP_ID_workerId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_workerId));
               }
               setWorkerId(typedValue);
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
        
            case PROP_ID_flowStepId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_flowStepId));
               }
               setFlowStepId(typedValue);
               break;
            }
        
            case PROP_ID_restartTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_restartTime));
               }
               setRestartTime(typedValue);
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
        
            case PROP_ID_completeItemCount:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_completeItemCount));
               }
               setCompleteItemCount(typedValue);
               break;
            }
        
            case PROP_ID_loadRetryCount:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_loadRetryCount));
               }
               setLoadRetryCount(typedValue);
               break;
            }
        
            case PROP_ID_loadSkipCount:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_loadSkipCount));
               }
               setLoadSkipCount(typedValue);
               break;
            }
        
            case PROP_ID_retryItemCount:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_retryItemCount));
               }
               setRetryItemCount(typedValue);
               break;
            }
        
            case PROP_ID_processItemCount:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_processItemCount));
               }
               setProcessItemCount(typedValue);
               break;
            }
        
            case PROP_ID_skipItemCount:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_skipItemCount));
               }
               setSkipItemCount(typedValue);
               break;
            }
        
            case PROP_ID_writeItemCount:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_writeItemCount));
               }
               setWriteItemCount(typedValue);
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
        
            case PROP_ID_execCount:{
               onInitProp(propId);
               this._execCount = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_workerId:{
               onInitProp(propId);
               this._workerId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_inputFileId:{
               onInitProp(propId);
               this._inputFileId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_flowStepId:{
               onInitProp(propId);
               this._flowStepId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_restartTime:{
               onInitProp(propId);
               this._restartTime = (java.sql.Timestamp)value;
               
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
        
            case PROP_ID_completeItemCount:{
               onInitProp(propId);
               this._completeItemCount = (java.lang.Long)value;
               
               break;
            }
        
            case PROP_ID_loadRetryCount:{
               onInitProp(propId);
               this._loadRetryCount = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_loadSkipCount:{
               onInitProp(propId);
               this._loadSkipCount = (java.lang.Long)value;
               
               break;
            }
        
            case PROP_ID_retryItemCount:{
               onInitProp(propId);
               this._retryItemCount = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_processItemCount:{
               onInitProp(propId);
               this._processItemCount = (java.lang.Long)value;
               
               break;
            }
        
            case PROP_ID_skipItemCount:{
               onInitProp(propId);
               this._skipItemCount = (java.lang.Long)value;
               
               break;
            }
        
            case PROP_ID_writeItemCount:{
               onInitProp(propId);
               this._writeItemCount = (java.lang.Long)value;
               
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
     * 执行次数: EXEC_COUNT
     */
    public java.lang.Integer getExecCount(){
         onPropGet(PROP_ID_execCount);
         return _execCount;
    }

    /**
     * 执行次数: EXEC_COUNT
     */
    public void setExecCount(java.lang.Integer value){
        if(onPropSet(PROP_ID_execCount,value)){
            this._execCount = value;
            internalClearRefs(PROP_ID_execCount);
            
        }
    }
    
    /**
     * 执行者: WORKER_ID
     */
    public java.lang.String getWorkerId(){
         onPropGet(PROP_ID_workerId);
         return _workerId;
    }

    /**
     * 执行者: WORKER_ID
     */
    public void setWorkerId(java.lang.String value){
        if(onPropSet(PROP_ID_workerId,value)){
            this._workerId = value;
            internalClearRefs(PROP_ID_workerId);
            
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
     * 关联流程步骤ID: FLOW_STEP_ID
     */
    public java.lang.String getFlowStepId(){
         onPropGet(PROP_ID_flowStepId);
         return _flowStepId;
    }

    /**
     * 关联流程步骤ID: FLOW_STEP_ID
     */
    public void setFlowStepId(java.lang.String value){
        if(onPropSet(PROP_ID_flowStepId,value)){
            this._flowStepId = value;
            internalClearRefs(PROP_ID_flowStepId);
            
        }
    }
    
    /**
     * 重启时间: RESTART_TIME
     */
    public java.sql.Timestamp getRestartTime(){
         onPropGet(PROP_ID_restartTime);
         return _restartTime;
    }

    /**
     * 重启时间: RESTART_TIME
     */
    public void setRestartTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_restartTime,value)){
            this._restartTime = value;
            internalClearRefs(PROP_ID_restartTime);
            
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
     * 完成条目数量: COMPLETE_ITEM_COUNT
     */
    public java.lang.Long getCompleteItemCount(){
         onPropGet(PROP_ID_completeItemCount);
         return _completeItemCount;
    }

    /**
     * 完成条目数量: COMPLETE_ITEM_COUNT
     */
    public void setCompleteItemCount(java.lang.Long value){
        if(onPropSet(PROP_ID_completeItemCount,value)){
            this._completeItemCount = value;
            internalClearRefs(PROP_ID_completeItemCount);
            
        }
    }
    
    /**
     * 重试加载次数: LOAD_RETRY_COUNT
     */
    public java.lang.Integer getLoadRetryCount(){
         onPropGet(PROP_ID_loadRetryCount);
         return _loadRetryCount;
    }

    /**
     * 重试加载次数: LOAD_RETRY_COUNT
     */
    public void setLoadRetryCount(java.lang.Integer value){
        if(onPropSet(PROP_ID_loadRetryCount,value)){
            this._loadRetryCount = value;
            internalClearRefs(PROP_ID_loadRetryCount);
            
        }
    }
    
    /**
     * 加载跳过数量: LOAD_SKIP_COUNT
     */
    public java.lang.Long getLoadSkipCount(){
         onPropGet(PROP_ID_loadSkipCount);
         return _loadSkipCount;
    }

    /**
     * 加载跳过数量: LOAD_SKIP_COUNT
     */
    public void setLoadSkipCount(java.lang.Long value){
        if(onPropSet(PROP_ID_loadSkipCount,value)){
            this._loadSkipCount = value;
            internalClearRefs(PROP_ID_loadSkipCount);
            
        }
    }
    
    /**
     * 重试条目次数: RETRY_ITEM_COUNT
     */
    public java.lang.Integer getRetryItemCount(){
         onPropGet(PROP_ID_retryItemCount);
         return _retryItemCount;
    }

    /**
     * 重试条目次数: RETRY_ITEM_COUNT
     */
    public void setRetryItemCount(java.lang.Integer value){
        if(onPropSet(PROP_ID_retryItemCount,value)){
            this._retryItemCount = value;
            internalClearRefs(PROP_ID_retryItemCount);
            
        }
    }
    
    /**
     * 处理条目数量: PROCESS_ITEM_COUNT
     */
    public java.lang.Long getProcessItemCount(){
         onPropGet(PROP_ID_processItemCount);
         return _processItemCount;
    }

    /**
     * 处理条目数量: PROCESS_ITEM_COUNT
     */
    public void setProcessItemCount(java.lang.Long value){
        if(onPropSet(PROP_ID_processItemCount,value)){
            this._processItemCount = value;
            internalClearRefs(PROP_ID_processItemCount);
            
        }
    }
    
    /**
     * 跳过条目数量: SKIP_ITEM_COUNT
     */
    public java.lang.Long getSkipItemCount(){
         onPropGet(PROP_ID_skipItemCount);
         return _skipItemCount;
    }

    /**
     * 跳过条目数量: SKIP_ITEM_COUNT
     */
    public void setSkipItemCount(java.lang.Long value){
        if(onPropSet(PROP_ID_skipItemCount,value)){
            this._skipItemCount = value;
            internalClearRefs(PROP_ID_skipItemCount);
            
        }
    }
    
    /**
     * 写入条目数量: WRITE_ITEM_COUNT
     */
    public java.lang.Long getWriteItemCount(){
         onPropGet(PROP_ID_writeItemCount);
         return _writeItemCount;
    }

    /**
     * 写入条目数量: WRITE_ITEM_COUNT
     */
    public void setWriteItemCount(java.lang.Long value){
        if(onPropSet(PROP_ID_writeItemCount,value)){
            this._writeItemCount = value;
            internalClearRefs(PROP_ID_writeItemCount);
            
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
       
    private final OrmEntitySet<io.nop.batch.dao.entity.NopBatchTaskVar> _taskVars = new OrmEntitySet<>(this, PROP_NAME_taskVars,
        io.nop.batch.dao.entity.NopBatchTaskVar.PROP_NAME_task, null,io.nop.batch.dao.entity.NopBatchTaskVar.class);

    /**
     * 任务状态变量。 refPropName: task, keyProp: {rel.keyProp}
     */
    public IOrmEntitySet<io.nop.batch.dao.entity.NopBatchTaskVar> getTaskVars(){
       return _taskVars;
    }
       
}
// resume CPD analysis - CPD-ON
