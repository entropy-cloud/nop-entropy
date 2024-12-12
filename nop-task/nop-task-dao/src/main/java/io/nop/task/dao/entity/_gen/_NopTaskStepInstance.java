package io.nop.task.dao.entity._gen;

import io.nop.orm.model.IEntityModel;
import io.nop.orm.support.DynamicOrmEntity;
import io.nop.orm.support.OrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.orm.IOrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.api.core.convert.ConvertHelper;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;

import io.nop.task.dao.entity.NopTaskStepInstance;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  逻辑流步骤实例: nop_task_step_instance
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopTaskStepInstance extends DynamicOrmEntity{
    
    /* 步骤ID: STEP_INSTANCE_ID VARCHAR */
    public static final String PROP_NAME_stepInstanceId = "stepInstanceId";
    public static final int PROP_ID_stepInstanceId = 1;
    
    /* 逻辑流实例ID: TASK_INSTANCE_ID VARCHAR */
    public static final String PROP_NAME_taskInstanceId = "taskInstanceId";
    public static final int PROP_ID_taskInstanceId = 2;
    
    /* 步骤类型: STEP_TYPE VARCHAR */
    public static final String PROP_NAME_stepType = "stepType";
    public static final int PROP_ID_stepType = 3;
    
    /* 步骤名称: STEP_NAME VARCHAR */
    public static final String PROP_NAME_stepName = "stepName";
    public static final int PROP_ID_stepName = 4;
    
    /* 步骤显示名称: DISPLAY_NAME VARCHAR */
    public static final String PROP_NAME_displayName = "displayName";
    public static final int PROP_ID_displayName = 5;
    
    /* 状态: STEP_STATUS INTEGER */
    public static final String PROP_NAME_stepStatus = "stepStatus";
    public static final int PROP_ID_stepStatus = 6;
    
    /* 子流程ID: SUB_TASK_ID VARCHAR */
    public static final String PROP_NAME_subTaskId = "subTaskId";
    public static final int PROP_ID_subTaskId = 8;
    
    /* 子流程名称: SUB_TASK_NAME VARCHAR */
    public static final String PROP_NAME_subTaskName = "subTaskName";
    public static final int PROP_ID_subTaskName = 9;
    
    /* 子流程版本: SUB_TASK_VERSION BIGINT */
    public static final String PROP_NAME_subTaskVersion = "subTaskVersion";
    public static final int PROP_ID_subTaskVersion = 10;
    
    /* 开始时间: START_TIME TIMESTAMP */
    public static final String PROP_NAME_startTime = "startTime";
    public static final int PROP_ID_startTime = 29;
    
    /* 结束时间: FINISH_TIME TIMESTAMP */
    public static final String PROP_NAME_finishTime = "finishTime";
    public static final int PROP_ID_finishTime = 30;
    
    /* 到期时间: DUE_TIME TIMESTAMP */
    public static final String PROP_NAME_dueTime = "dueTime";
    public static final int PROP_ID_dueTime = 31;
    
    /* 下次重试时间: NEXT_RETRY_TIME TIMESTAMP */
    public static final String PROP_NAME_nextRetryTime = "nextRetryTime";
    public static final int PROP_ID_nextRetryTime = 35;
    
    /* 已重试次数: RETRY_COUNT INTEGER */
    public static final String PROP_NAME_retryCount = "retryCount";
    public static final int PROP_ID_retryCount = 36;
    
    /* 是否内部: INTERNAL BOOLEAN */
    public static final String PROP_NAME_internal = "internal";
    public static final int PROP_ID_internal = 37;
    
    /* 错误码: ERR_CODE VARCHAR */
    public static final String PROP_NAME_errCode = "errCode";
    public static final int PROP_ID_errCode = 38;
    
    /* 错误消息: ERR_MSG VARCHAR */
    public static final String PROP_NAME_errMsg = "errMsg";
    public static final int PROP_ID_errMsg = 39;
    
    /* 优先级: PRIORITY INTEGER */
    public static final String PROP_NAME_priority = "priority";
    public static final int PROP_ID_priority = 40;
    
    /* 标签: TAG_TEXT VARCHAR */
    public static final String PROP_NAME_tagText = "tagText";
    public static final int PROP_ID_tagText = 41;
    
    /* 父步骤ID: PARENT_STEP_ID VARCHAR */
    public static final String PROP_NAME_parentStepId = "parentStepId";
    public static final int PROP_ID_parentStepId = 42;
    
    /* 工作者ID: WORKER_ID VARCHAR */
    public static final String PROP_NAME_workerId = "workerId";
    public static final int PROP_ID_workerId = 43;
    
    /* 步骤路径: STEP_PATH VARCHAR */
    public static final String PROP_NAME_stepPath = "stepPath";
    public static final int PROP_ID_stepPath = 44;
    
    /* 运行ID: RUN_ID INTEGER */
    public static final String PROP_NAME_runId = "runId";
    public static final int PROP_ID_runId = 45;
    
    /* 步骤下标: BODY_STEP_INDEX INTEGER */
    public static final String PROP_NAME_bodyStepIndex = "bodyStepIndex";
    public static final int PROP_ID_bodyStepIndex = 46;
    
    /* 状态数据: STATE_BEAN_DATA VARCHAR */
    public static final String PROP_NAME_stateBeanData = "stateBeanData";
    public static final int PROP_ID_stateBeanData = 47;
    
    /* 数据版本: VERSION INTEGER */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 48;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 49;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 50;
    
    /* 修改人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 51;
    
    /* 修改时间: UPDATE_TIME TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 52;
    
    /* 备注: REMARK VARCHAR */
    public static final String PROP_NAME_remark = "remark";
    public static final int PROP_ID_remark = 53;
    

    private static int _PROP_ID_BOUND = 54;

    
    /* relation: 逻辑流实例 */
    public static final String PROP_NAME_taskInstance = "taskInstance";
    
    /* relation: 子流程实例 */
    public static final String PROP_NAME_subTaskInstance = "subTaskInstance";
    
    /* relation: 父步骤实例 */
    public static final String PROP_NAME_parentStepInstance = "parentStepInstance";
    
    /* relation: 子步骤集合 */
    public static final String PROP_NAME_childSteps = "childSteps";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_stepInstanceId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_stepInstanceId};

    private static final String[] PROP_ID_TO_NAME = new String[54];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_stepInstanceId] = PROP_NAME_stepInstanceId;
          PROP_NAME_TO_ID.put(PROP_NAME_stepInstanceId, PROP_ID_stepInstanceId);
      
          PROP_ID_TO_NAME[PROP_ID_taskInstanceId] = PROP_NAME_taskInstanceId;
          PROP_NAME_TO_ID.put(PROP_NAME_taskInstanceId, PROP_ID_taskInstanceId);
      
          PROP_ID_TO_NAME[PROP_ID_stepType] = PROP_NAME_stepType;
          PROP_NAME_TO_ID.put(PROP_NAME_stepType, PROP_ID_stepType);
      
          PROP_ID_TO_NAME[PROP_ID_stepName] = PROP_NAME_stepName;
          PROP_NAME_TO_ID.put(PROP_NAME_stepName, PROP_ID_stepName);
      
          PROP_ID_TO_NAME[PROP_ID_displayName] = PROP_NAME_displayName;
          PROP_NAME_TO_ID.put(PROP_NAME_displayName, PROP_ID_displayName);
      
          PROP_ID_TO_NAME[PROP_ID_stepStatus] = PROP_NAME_stepStatus;
          PROP_NAME_TO_ID.put(PROP_NAME_stepStatus, PROP_ID_stepStatus);
      
          PROP_ID_TO_NAME[PROP_ID_subTaskId] = PROP_NAME_subTaskId;
          PROP_NAME_TO_ID.put(PROP_NAME_subTaskId, PROP_ID_subTaskId);
      
          PROP_ID_TO_NAME[PROP_ID_subTaskName] = PROP_NAME_subTaskName;
          PROP_NAME_TO_ID.put(PROP_NAME_subTaskName, PROP_ID_subTaskName);
      
          PROP_ID_TO_NAME[PROP_ID_subTaskVersion] = PROP_NAME_subTaskVersion;
          PROP_NAME_TO_ID.put(PROP_NAME_subTaskVersion, PROP_ID_subTaskVersion);
      
          PROP_ID_TO_NAME[PROP_ID_startTime] = PROP_NAME_startTime;
          PROP_NAME_TO_ID.put(PROP_NAME_startTime, PROP_ID_startTime);
      
          PROP_ID_TO_NAME[PROP_ID_finishTime] = PROP_NAME_finishTime;
          PROP_NAME_TO_ID.put(PROP_NAME_finishTime, PROP_ID_finishTime);
      
          PROP_ID_TO_NAME[PROP_ID_dueTime] = PROP_NAME_dueTime;
          PROP_NAME_TO_ID.put(PROP_NAME_dueTime, PROP_ID_dueTime);
      
          PROP_ID_TO_NAME[PROP_ID_nextRetryTime] = PROP_NAME_nextRetryTime;
          PROP_NAME_TO_ID.put(PROP_NAME_nextRetryTime, PROP_ID_nextRetryTime);
      
          PROP_ID_TO_NAME[PROP_ID_retryCount] = PROP_NAME_retryCount;
          PROP_NAME_TO_ID.put(PROP_NAME_retryCount, PROP_ID_retryCount);
      
          PROP_ID_TO_NAME[PROP_ID_internal] = PROP_NAME_internal;
          PROP_NAME_TO_ID.put(PROP_NAME_internal, PROP_ID_internal);
      
          PROP_ID_TO_NAME[PROP_ID_errCode] = PROP_NAME_errCode;
          PROP_NAME_TO_ID.put(PROP_NAME_errCode, PROP_ID_errCode);
      
          PROP_ID_TO_NAME[PROP_ID_errMsg] = PROP_NAME_errMsg;
          PROP_NAME_TO_ID.put(PROP_NAME_errMsg, PROP_ID_errMsg);
      
          PROP_ID_TO_NAME[PROP_ID_priority] = PROP_NAME_priority;
          PROP_NAME_TO_ID.put(PROP_NAME_priority, PROP_ID_priority);
      
          PROP_ID_TO_NAME[PROP_ID_tagText] = PROP_NAME_tagText;
          PROP_NAME_TO_ID.put(PROP_NAME_tagText, PROP_ID_tagText);
      
          PROP_ID_TO_NAME[PROP_ID_parentStepId] = PROP_NAME_parentStepId;
          PROP_NAME_TO_ID.put(PROP_NAME_parentStepId, PROP_ID_parentStepId);
      
          PROP_ID_TO_NAME[PROP_ID_workerId] = PROP_NAME_workerId;
          PROP_NAME_TO_ID.put(PROP_NAME_workerId, PROP_ID_workerId);
      
          PROP_ID_TO_NAME[PROP_ID_stepPath] = PROP_NAME_stepPath;
          PROP_NAME_TO_ID.put(PROP_NAME_stepPath, PROP_ID_stepPath);
      
          PROP_ID_TO_NAME[PROP_ID_runId] = PROP_NAME_runId;
          PROP_NAME_TO_ID.put(PROP_NAME_runId, PROP_ID_runId);
      
          PROP_ID_TO_NAME[PROP_ID_bodyStepIndex] = PROP_NAME_bodyStepIndex;
          PROP_NAME_TO_ID.put(PROP_NAME_bodyStepIndex, PROP_ID_bodyStepIndex);
      
          PROP_ID_TO_NAME[PROP_ID_stateBeanData] = PROP_NAME_stateBeanData;
          PROP_NAME_TO_ID.put(PROP_NAME_stateBeanData, PROP_ID_stateBeanData);
      
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

    
    /* 步骤ID: STEP_INSTANCE_ID */
    private java.lang.String _stepInstanceId;
    
    /* 逻辑流实例ID: TASK_INSTANCE_ID */
    private java.lang.String _taskInstanceId;
    
    /* 步骤类型: STEP_TYPE */
    private java.lang.String _stepType;
    
    /* 步骤名称: STEP_NAME */
    private java.lang.String _stepName;
    
    /* 步骤显示名称: DISPLAY_NAME */
    private java.lang.String _displayName;
    
    /* 状态: STEP_STATUS */
    private java.lang.Integer _stepStatus;
    
    /* 子流程ID: SUB_TASK_ID */
    private java.lang.String _subTaskId;
    
    /* 子流程名称: SUB_TASK_NAME */
    private java.lang.String _subTaskName;
    
    /* 子流程版本: SUB_TASK_VERSION */
    private java.lang.Long _subTaskVersion;
    
    /* 开始时间: START_TIME */
    private java.sql.Timestamp _startTime;
    
    /* 结束时间: FINISH_TIME */
    private java.sql.Timestamp _finishTime;
    
    /* 到期时间: DUE_TIME */
    private java.sql.Timestamp _dueTime;
    
    /* 下次重试时间: NEXT_RETRY_TIME */
    private java.sql.Timestamp _nextRetryTime;
    
    /* 已重试次数: RETRY_COUNT */
    private java.lang.Integer _retryCount;
    
    /* 是否内部: INTERNAL */
    private java.lang.Boolean _internal;
    
    /* 错误码: ERR_CODE */
    private java.lang.String _errCode;
    
    /* 错误消息: ERR_MSG */
    private java.lang.String _errMsg;
    
    /* 优先级: PRIORITY */
    private java.lang.Integer _priority;
    
    /* 标签: TAG_TEXT */
    private java.lang.String _tagText;
    
    /* 父步骤ID: PARENT_STEP_ID */
    private java.lang.String _parentStepId;
    
    /* 工作者ID: WORKER_ID */
    private java.lang.String _workerId;
    
    /* 步骤路径: STEP_PATH */
    private java.lang.String _stepPath;
    
    /* 运行ID: RUN_ID */
    private java.lang.Integer _runId;
    
    /* 步骤下标: BODY_STEP_INDEX */
    private java.lang.Integer _bodyStepIndex;
    
    /* 状态数据: STATE_BEAN_DATA */
    private java.lang.String _stateBeanData;
    
    /* 数据版本: VERSION */
    private java.lang.Integer _version;
    
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
    

    public _NopTaskStepInstance(){
        // for debug
    }

    protected NopTaskStepInstance newInstance(){
        NopTaskStepInstance entity = new NopTaskStepInstance();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopTaskStepInstance cloneInstance() {
        NopTaskStepInstance entity = newInstance();
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
      return "io.nop.task.dao.entity.NopTaskStepInstance";
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
    
        return buildSimpleId(PROP_ID_stepInstanceId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_stepInstanceId;
          
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
        
            case PROP_ID_stepInstanceId:
               return getStepInstanceId();
        
            case PROP_ID_taskInstanceId:
               return getTaskInstanceId();
        
            case PROP_ID_stepType:
               return getStepType();
        
            case PROP_ID_stepName:
               return getStepName();
        
            case PROP_ID_displayName:
               return getDisplayName();
        
            case PROP_ID_stepStatus:
               return getStepStatus();
        
            case PROP_ID_subTaskId:
               return getSubTaskId();
        
            case PROP_ID_subTaskName:
               return getSubTaskName();
        
            case PROP_ID_subTaskVersion:
               return getSubTaskVersion();
        
            case PROP_ID_startTime:
               return getStartTime();
        
            case PROP_ID_finishTime:
               return getFinishTime();
        
            case PROP_ID_dueTime:
               return getDueTime();
        
            case PROP_ID_nextRetryTime:
               return getNextRetryTime();
        
            case PROP_ID_retryCount:
               return getRetryCount();
        
            case PROP_ID_internal:
               return getInternal();
        
            case PROP_ID_errCode:
               return getErrCode();
        
            case PROP_ID_errMsg:
               return getErrMsg();
        
            case PROP_ID_priority:
               return getPriority();
        
            case PROP_ID_tagText:
               return getTagText();
        
            case PROP_ID_parentStepId:
               return getParentStepId();
        
            case PROP_ID_workerId:
               return getWorkerId();
        
            case PROP_ID_stepPath:
               return getStepPath();
        
            case PROP_ID_runId:
               return getRunId();
        
            case PROP_ID_bodyStepIndex:
               return getBodyStepIndex();
        
            case PROP_ID_stateBeanData:
               return getStateBeanData();
        
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
        
            case PROP_ID_stepInstanceId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_stepInstanceId));
               }
               setStepInstanceId(typedValue);
               break;
            }
        
            case PROP_ID_taskInstanceId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_taskInstanceId));
               }
               setTaskInstanceId(typedValue);
               break;
            }
        
            case PROP_ID_stepType:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_stepType));
               }
               setStepType(typedValue);
               break;
            }
        
            case PROP_ID_stepName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_stepName));
               }
               setStepName(typedValue);
               break;
            }
        
            case PROP_ID_displayName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_displayName));
               }
               setDisplayName(typedValue);
               break;
            }
        
            case PROP_ID_stepStatus:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_stepStatus));
               }
               setStepStatus(typedValue);
               break;
            }
        
            case PROP_ID_subTaskId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_subTaskId));
               }
               setSubTaskId(typedValue);
               break;
            }
        
            case PROP_ID_subTaskName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_subTaskName));
               }
               setSubTaskName(typedValue);
               break;
            }
        
            case PROP_ID_subTaskVersion:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_subTaskVersion));
               }
               setSubTaskVersion(typedValue);
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
        
            case PROP_ID_finishTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_finishTime));
               }
               setFinishTime(typedValue);
               break;
            }
        
            case PROP_ID_dueTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_dueTime));
               }
               setDueTime(typedValue);
               break;
            }
        
            case PROP_ID_nextRetryTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_nextRetryTime));
               }
               setNextRetryTime(typedValue);
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
        
            case PROP_ID_internal:{
               java.lang.Boolean typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toBoolean(value,
                       err-> newTypeConversionError(PROP_NAME_internal));
               }
               setInternal(typedValue);
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
        
            case PROP_ID_errMsg:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_errMsg));
               }
               setErrMsg(typedValue);
               break;
            }
        
            case PROP_ID_priority:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_priority));
               }
               setPriority(typedValue);
               break;
            }
        
            case PROP_ID_tagText:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_tagText));
               }
               setTagText(typedValue);
               break;
            }
        
            case PROP_ID_parentStepId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_parentStepId));
               }
               setParentStepId(typedValue);
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
        
            case PROP_ID_stepPath:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_stepPath));
               }
               setStepPath(typedValue);
               break;
            }
        
            case PROP_ID_runId:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_runId));
               }
               setRunId(typedValue);
               break;
            }
        
            case PROP_ID_bodyStepIndex:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_bodyStepIndex));
               }
               setBodyStepIndex(typedValue);
               break;
            }
        
            case PROP_ID_stateBeanData:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_stateBeanData));
               }
               setStateBeanData(typedValue);
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
        
            case PROP_ID_stepInstanceId:{
               onInitProp(propId);
               this._stepInstanceId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_taskInstanceId:{
               onInitProp(propId);
               this._taskInstanceId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_stepType:{
               onInitProp(propId);
               this._stepType = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_stepName:{
               onInitProp(propId);
               this._stepName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_displayName:{
               onInitProp(propId);
               this._displayName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_stepStatus:{
               onInitProp(propId);
               this._stepStatus = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_subTaskId:{
               onInitProp(propId);
               this._subTaskId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_subTaskName:{
               onInitProp(propId);
               this._subTaskName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_subTaskVersion:{
               onInitProp(propId);
               this._subTaskVersion = (java.lang.Long)value;
               
               break;
            }
        
            case PROP_ID_startTime:{
               onInitProp(propId);
               this._startTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_finishTime:{
               onInitProp(propId);
               this._finishTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_dueTime:{
               onInitProp(propId);
               this._dueTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_nextRetryTime:{
               onInitProp(propId);
               this._nextRetryTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_retryCount:{
               onInitProp(propId);
               this._retryCount = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_internal:{
               onInitProp(propId);
               this._internal = (java.lang.Boolean)value;
               
               break;
            }
        
            case PROP_ID_errCode:{
               onInitProp(propId);
               this._errCode = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_errMsg:{
               onInitProp(propId);
               this._errMsg = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_priority:{
               onInitProp(propId);
               this._priority = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_tagText:{
               onInitProp(propId);
               this._tagText = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_parentStepId:{
               onInitProp(propId);
               this._parentStepId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_workerId:{
               onInitProp(propId);
               this._workerId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_stepPath:{
               onInitProp(propId);
               this._stepPath = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_runId:{
               onInitProp(propId);
               this._runId = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_bodyStepIndex:{
               onInitProp(propId);
               this._bodyStepIndex = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_stateBeanData:{
               onInitProp(propId);
               this._stateBeanData = (java.lang.String)value;
               
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
     * 步骤ID: STEP_INSTANCE_ID
     */
    public java.lang.String getStepInstanceId(){
         onPropGet(PROP_ID_stepInstanceId);
         return _stepInstanceId;
    }

    /**
     * 步骤ID: STEP_INSTANCE_ID
     */
    public void setStepInstanceId(java.lang.String value){
        if(onPropSet(PROP_ID_stepInstanceId,value)){
            this._stepInstanceId = value;
            internalClearRefs(PROP_ID_stepInstanceId);
            orm_id();
        }
    }
    
    /**
     * 逻辑流实例ID: TASK_INSTANCE_ID
     */
    public java.lang.String getTaskInstanceId(){
         onPropGet(PROP_ID_taskInstanceId);
         return _taskInstanceId;
    }

    /**
     * 逻辑流实例ID: TASK_INSTANCE_ID
     */
    public void setTaskInstanceId(java.lang.String value){
        if(onPropSet(PROP_ID_taskInstanceId,value)){
            this._taskInstanceId = value;
            internalClearRefs(PROP_ID_taskInstanceId);
            
        }
    }
    
    /**
     * 步骤类型: STEP_TYPE
     */
    public java.lang.String getStepType(){
         onPropGet(PROP_ID_stepType);
         return _stepType;
    }

    /**
     * 步骤类型: STEP_TYPE
     */
    public void setStepType(java.lang.String value){
        if(onPropSet(PROP_ID_stepType,value)){
            this._stepType = value;
            internalClearRefs(PROP_ID_stepType);
            
        }
    }
    
    /**
     * 步骤名称: STEP_NAME
     */
    public java.lang.String getStepName(){
         onPropGet(PROP_ID_stepName);
         return _stepName;
    }

    /**
     * 步骤名称: STEP_NAME
     */
    public void setStepName(java.lang.String value){
        if(onPropSet(PROP_ID_stepName,value)){
            this._stepName = value;
            internalClearRefs(PROP_ID_stepName);
            
        }
    }
    
    /**
     * 步骤显示名称: DISPLAY_NAME
     */
    public java.lang.String getDisplayName(){
         onPropGet(PROP_ID_displayName);
         return _displayName;
    }

    /**
     * 步骤显示名称: DISPLAY_NAME
     */
    public void setDisplayName(java.lang.String value){
        if(onPropSet(PROP_ID_displayName,value)){
            this._displayName = value;
            internalClearRefs(PROP_ID_displayName);
            
        }
    }
    
    /**
     * 状态: STEP_STATUS
     */
    public java.lang.Integer getStepStatus(){
         onPropGet(PROP_ID_stepStatus);
         return _stepStatus;
    }

    /**
     * 状态: STEP_STATUS
     */
    public void setStepStatus(java.lang.Integer value){
        if(onPropSet(PROP_ID_stepStatus,value)){
            this._stepStatus = value;
            internalClearRefs(PROP_ID_stepStatus);
            
        }
    }
    
    /**
     * 子流程ID: SUB_TASK_ID
     */
    public java.lang.String getSubTaskId(){
         onPropGet(PROP_ID_subTaskId);
         return _subTaskId;
    }

    /**
     * 子流程ID: SUB_TASK_ID
     */
    public void setSubTaskId(java.lang.String value){
        if(onPropSet(PROP_ID_subTaskId,value)){
            this._subTaskId = value;
            internalClearRefs(PROP_ID_subTaskId);
            
        }
    }
    
    /**
     * 子流程名称: SUB_TASK_NAME
     */
    public java.lang.String getSubTaskName(){
         onPropGet(PROP_ID_subTaskName);
         return _subTaskName;
    }

    /**
     * 子流程名称: SUB_TASK_NAME
     */
    public void setSubTaskName(java.lang.String value){
        if(onPropSet(PROP_ID_subTaskName,value)){
            this._subTaskName = value;
            internalClearRefs(PROP_ID_subTaskName);
            
        }
    }
    
    /**
     * 子流程版本: SUB_TASK_VERSION
     */
    public java.lang.Long getSubTaskVersion(){
         onPropGet(PROP_ID_subTaskVersion);
         return _subTaskVersion;
    }

    /**
     * 子流程版本: SUB_TASK_VERSION
     */
    public void setSubTaskVersion(java.lang.Long value){
        if(onPropSet(PROP_ID_subTaskVersion,value)){
            this._subTaskVersion = value;
            internalClearRefs(PROP_ID_subTaskVersion);
            
        }
    }
    
    /**
     * 开始时间: START_TIME
     */
    public java.sql.Timestamp getStartTime(){
         onPropGet(PROP_ID_startTime);
         return _startTime;
    }

    /**
     * 开始时间: START_TIME
     */
    public void setStartTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_startTime,value)){
            this._startTime = value;
            internalClearRefs(PROP_ID_startTime);
            
        }
    }
    
    /**
     * 结束时间: FINISH_TIME
     */
    public java.sql.Timestamp getFinishTime(){
         onPropGet(PROP_ID_finishTime);
         return _finishTime;
    }

    /**
     * 结束时间: FINISH_TIME
     */
    public void setFinishTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_finishTime,value)){
            this._finishTime = value;
            internalClearRefs(PROP_ID_finishTime);
            
        }
    }
    
    /**
     * 到期时间: DUE_TIME
     */
    public java.sql.Timestamp getDueTime(){
         onPropGet(PROP_ID_dueTime);
         return _dueTime;
    }

    /**
     * 到期时间: DUE_TIME
     */
    public void setDueTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_dueTime,value)){
            this._dueTime = value;
            internalClearRefs(PROP_ID_dueTime);
            
        }
    }
    
    /**
     * 下次重试时间: NEXT_RETRY_TIME
     */
    public java.sql.Timestamp getNextRetryTime(){
         onPropGet(PROP_ID_nextRetryTime);
         return _nextRetryTime;
    }

    /**
     * 下次重试时间: NEXT_RETRY_TIME
     */
    public void setNextRetryTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_nextRetryTime,value)){
            this._nextRetryTime = value;
            internalClearRefs(PROP_ID_nextRetryTime);
            
        }
    }
    
    /**
     * 已重试次数: RETRY_COUNT
     */
    public java.lang.Integer getRetryCount(){
         onPropGet(PROP_ID_retryCount);
         return _retryCount;
    }

    /**
     * 已重试次数: RETRY_COUNT
     */
    public void setRetryCount(java.lang.Integer value){
        if(onPropSet(PROP_ID_retryCount,value)){
            this._retryCount = value;
            internalClearRefs(PROP_ID_retryCount);
            
        }
    }
    
    /**
     * 是否内部: INTERNAL
     */
    public java.lang.Boolean getInternal(){
         onPropGet(PROP_ID_internal);
         return _internal;
    }

    /**
     * 是否内部: INTERNAL
     */
    public void setInternal(java.lang.Boolean value){
        if(onPropSet(PROP_ID_internal,value)){
            this._internal = value;
            internalClearRefs(PROP_ID_internal);
            
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
     * 错误消息: ERR_MSG
     */
    public java.lang.String getErrMsg(){
         onPropGet(PROP_ID_errMsg);
         return _errMsg;
    }

    /**
     * 错误消息: ERR_MSG
     */
    public void setErrMsg(java.lang.String value){
        if(onPropSet(PROP_ID_errMsg,value)){
            this._errMsg = value;
            internalClearRefs(PROP_ID_errMsg);
            
        }
    }
    
    /**
     * 优先级: PRIORITY
     */
    public java.lang.Integer getPriority(){
         onPropGet(PROP_ID_priority);
         return _priority;
    }

    /**
     * 优先级: PRIORITY
     */
    public void setPriority(java.lang.Integer value){
        if(onPropSet(PROP_ID_priority,value)){
            this._priority = value;
            internalClearRefs(PROP_ID_priority);
            
        }
    }
    
    /**
     * 标签: TAG_TEXT
     */
    public java.lang.String getTagText(){
         onPropGet(PROP_ID_tagText);
         return _tagText;
    }

    /**
     * 标签: TAG_TEXT
     */
    public void setTagText(java.lang.String value){
        if(onPropSet(PROP_ID_tagText,value)){
            this._tagText = value;
            internalClearRefs(PROP_ID_tagText);
            
        }
    }
    
    /**
     * 父步骤ID: PARENT_STEP_ID
     */
    public java.lang.String getParentStepId(){
         onPropGet(PROP_ID_parentStepId);
         return _parentStepId;
    }

    /**
     * 父步骤ID: PARENT_STEP_ID
     */
    public void setParentStepId(java.lang.String value){
        if(onPropSet(PROP_ID_parentStepId,value)){
            this._parentStepId = value;
            internalClearRefs(PROP_ID_parentStepId);
            
        }
    }
    
    /**
     * 工作者ID: WORKER_ID
     */
    public java.lang.String getWorkerId(){
         onPropGet(PROP_ID_workerId);
         return _workerId;
    }

    /**
     * 工作者ID: WORKER_ID
     */
    public void setWorkerId(java.lang.String value){
        if(onPropSet(PROP_ID_workerId,value)){
            this._workerId = value;
            internalClearRefs(PROP_ID_workerId);
            
        }
    }
    
    /**
     * 步骤路径: STEP_PATH
     */
    public java.lang.String getStepPath(){
         onPropGet(PROP_ID_stepPath);
         return _stepPath;
    }

    /**
     * 步骤路径: STEP_PATH
     */
    public void setStepPath(java.lang.String value){
        if(onPropSet(PROP_ID_stepPath,value)){
            this._stepPath = value;
            internalClearRefs(PROP_ID_stepPath);
            
        }
    }
    
    /**
     * 运行ID: RUN_ID
     */
    public java.lang.Integer getRunId(){
         onPropGet(PROP_ID_runId);
         return _runId;
    }

    /**
     * 运行ID: RUN_ID
     */
    public void setRunId(java.lang.Integer value){
        if(onPropSet(PROP_ID_runId,value)){
            this._runId = value;
            internalClearRefs(PROP_ID_runId);
            
        }
    }
    
    /**
     * 步骤下标: BODY_STEP_INDEX
     */
    public java.lang.Integer getBodyStepIndex(){
         onPropGet(PROP_ID_bodyStepIndex);
         return _bodyStepIndex;
    }

    /**
     * 步骤下标: BODY_STEP_INDEX
     */
    public void setBodyStepIndex(java.lang.Integer value){
        if(onPropSet(PROP_ID_bodyStepIndex,value)){
            this._bodyStepIndex = value;
            internalClearRefs(PROP_ID_bodyStepIndex);
            
        }
    }
    
    /**
     * 状态数据: STATE_BEAN_DATA
     */
    public java.lang.String getStateBeanData(){
         onPropGet(PROP_ID_stateBeanData);
         return _stateBeanData;
    }

    /**
     * 状态数据: STATE_BEAN_DATA
     */
    public void setStateBeanData(java.lang.String value){
        if(onPropSet(PROP_ID_stateBeanData,value)){
            this._stateBeanData = value;
            internalClearRefs(PROP_ID_stateBeanData);
            
        }
    }
    
    /**
     * 数据版本: VERSION
     */
    public java.lang.Integer getVersion(){
         onPropGet(PROP_ID_version);
         return _version;
    }

    /**
     * 数据版本: VERSION
     */
    public void setVersion(java.lang.Integer value){
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
     * 逻辑流实例
     */
    public io.nop.task.dao.entity.NopTaskInstance getTaskInstance(){
       return (io.nop.task.dao.entity.NopTaskInstance)internalGetRefEntity(PROP_NAME_taskInstance);
    }

    public void setTaskInstance(io.nop.task.dao.entity.NopTaskInstance refEntity){
   
           if(refEntity == null){
           
                   this.setTaskInstanceId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_taskInstance, refEntity,()->{
           
                           this.setTaskInstanceId(refEntity.getTaskInstanceId());
                       
           });
           }
       
    }
       
    /**
     * 子流程实例
     */
    public io.nop.task.dao.entity.NopTaskInstance getSubTaskInstance(){
       return (io.nop.task.dao.entity.NopTaskInstance)internalGetRefEntity(PROP_NAME_subTaskInstance);
    }

    public void setSubTaskInstance(io.nop.task.dao.entity.NopTaskInstance refEntity){
   
           if(refEntity == null){
           
                   this.setSubTaskId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_subTaskInstance, refEntity,()->{
           
                           this.setSubTaskId(refEntity.getTaskInstanceId());
                       
           });
           }
       
    }
       
    /**
     * 父步骤实例
     */
    public io.nop.task.dao.entity.NopTaskStepInstance getParentStepInstance(){
       return (io.nop.task.dao.entity.NopTaskStepInstance)internalGetRefEntity(PROP_NAME_parentStepInstance);
    }

    public void setParentStepInstance(io.nop.task.dao.entity.NopTaskStepInstance refEntity){
   
           if(refEntity == null){
           
                   this.setParentStepId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_parentStepInstance, refEntity,()->{
           
                           this.setParentStepId(refEntity.getStepInstanceId());
                       
           });
           }
       
    }
       
    private final OrmEntitySet<io.nop.task.dao.entity.NopTaskStepInstance> _childSteps = new OrmEntitySet<>(this, PROP_NAME_childSteps,
        io.nop.task.dao.entity.NopTaskStepInstance.PROP_NAME_parentStepInstance, null,io.nop.task.dao.entity.NopTaskStepInstance.class);

    /**
     * 子步骤集合。 refPropName: parentStepInstance, keyProp: {rel.keyProp}
     */
    public IOrmEntitySet<io.nop.task.dao.entity.NopTaskStepInstance> getChildSteps(){
       return _childSteps;
    }
       
}
// resume CPD analysis - CPD-ON
