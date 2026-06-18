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

import io.nop.job.dao.entity.NopJobSchedule;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  调度定义: nop_job_schedule
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopJobSchedule extends DynamicOrmEntity{
    
    /* 调度ID: JOB_SCHEDULE_ID VARCHAR */
    public static final String PROP_NAME_jobScheduleId = "jobScheduleId";
    public static final int PROP_ID_jobScheduleId = 1;
    
    /* 命名空间: NAMESPACE_ID VARCHAR */
    public static final String PROP_NAME_namespaceId = "namespaceId";
    public static final int PROP_ID_namespaceId = 2;
    
    /* 分组: GROUP_ID VARCHAR */
    public static final String PROP_NAME_groupId = "groupId";
    public static final int PROP_ID_groupId = 3;
    
    /* 作业名: JOB_NAME VARCHAR */
    public static final String PROP_NAME_jobName = "jobName";
    public static final int PROP_ID_jobName = 4;
    
    /* 显示名: DISPLAY_NAME VARCHAR */
    public static final String PROP_NAME_displayName = "displayName";
    public static final int PROP_ID_displayName = 5;
    
    /* 描述: DESCRIPTION VARCHAR */
    public static final String PROP_NAME_description = "description";
    public static final int PROP_ID_description = 6;
    
    /* 调度状态: SCHEDULE_STATUS INTEGER */
    public static final String PROP_NAME_scheduleStatus = "scheduleStatus";
    public static final int PROP_ID_scheduleStatus = 7;
    
    /* 执行器类型: EXECUTOR_KIND VARCHAR */
    public static final String PROP_NAME_executorKind = "executorKind";
    public static final int PROP_ID_executorKind = 8;
    
    /* 任务参数: JOB_PARAMS VARCHAR */
    public static final String PROP_NAME_jobParams = "jobParams";
    public static final int PROP_ID_jobParams = 9;
    
    /* 触发器类型: TRIGGER_TYPE INTEGER */
    public static final String PROP_NAME_triggerType = "triggerType";
    public static final int PROP_ID_triggerType = 10;
    
    /* CRON表达式: CRON_EXPR VARCHAR */
    public static final String PROP_NAME_cronExpr = "cronExpr";
    public static final int PROP_ID_cronExpr = 11;
    
    /* 重复间隔(毫秒): REPEAT_INTERVAL_MS BIGINT */
    public static final String PROP_NAME_repeatIntervalMs = "repeatIntervalMs";
    public static final int PROP_ID_repeatIntervalMs = 12;
    
    /* 最大执行次数: MAX_EXECUTION_COUNT INTEGER */
    public static final String PROP_NAME_maxExecutionCount = "maxExecutionCount";
    public static final int PROP_ID_maxExecutionCount = 13;
    
    /* 最早调度时间: MIN_SCHEDULE_TIME TIMESTAMP */
    public static final String PROP_NAME_minScheduleTime = "minScheduleTime";
    public static final int PROP_ID_minScheduleTime = 14;
    
    /* 最晚调度时间: MAX_SCHEDULE_TIME TIMESTAMP */
    public static final String PROP_NAME_maxScheduleTime = "maxScheduleTime";
    public static final int PROP_ID_maxScheduleTime = 15;
    
    /* Misfire阈值(毫秒): MISFIRE_THRESHOLD_MS INTEGER */
    public static final String PROP_NAME_misfireThresholdMs = "misfireThresholdMs";
    public static final int PROP_ID_misfireThresholdMs = 16;
    
    /* 使用默认日历: USE_DEFAULT_CALENDAR TINYINT */
    public static final String PROP_NAME_useDefaultCalendar = "useDefaultCalendar";
    public static final int PROP_ID_useDefaultCalendar = 17;
    
    /* 暂停日历配置: PAUSE_CALENDAR_SPEC VARCHAR */
    public static final String PROP_NAME_pauseCalendarSpec = "pauseCalendarSpec";
    public static final int PROP_ID_pauseCalendarSpec = 18;
    
    /* 阻塞策略: BLOCK_STRATEGY INTEGER */
    public static final String PROP_NAME_blockStrategy = "blockStrategy";
    public static final int PROP_ID_blockStrategy = 19;
    
    /* 超时时间(秒): TIMEOUT_SECONDS INTEGER */
    public static final String PROP_NAME_timeoutSeconds = "timeoutSeconds";
    public static final int PROP_ID_timeoutSeconds = 20;
    
    /* 重试策略ID: RETRY_POLICY_ID VARCHAR */
    public static final String PROP_NAME_retryPolicyId = "retryPolicyId";
    public static final int PROP_ID_retryPolicyId = 21;
    
    /* 分区索引: PARTITION_INDEX SMALLINT */
    public static final String PROP_NAME_partitionIndex = "partitionIndex";
    public static final int PROP_ID_partitionIndex = 22;
    
    /* 已触发次数: FIRE_COUNT BIGINT */
    public static final String PROP_NAME_fireCount = "fireCount";
    public static final int PROP_ID_fireCount = 23;
    
    /* 活跃触发数: ACTIVE_FIRE_COUNT INTEGER */
    public static final String PROP_NAME_activeFireCount = "activeFireCount";
    public static final int PROP_ID_activeFireCount = 24;
    
    /* 上次触发时间: LAST_FIRE_TIME TIMESTAMP */
    public static final String PROP_NAME_lastFireTime = "lastFireTime";
    public static final int PROP_ID_lastFireTime = 25;
    
    /* 上次结束时间: LAST_END_TIME TIMESTAMP */
    public static final String PROP_NAME_lastEndTime = "lastEndTime";
    public static final int PROP_ID_lastEndTime = 26;
    
    /* 下次触发时间: NEXT_FIRE_TIME TIMESTAMP */
    public static final String PROP_NAME_nextFireTime = "nextFireTime";
    public static final int PROP_ID_nextFireTime = 27;
    
    /* 上次触发状态: LAST_FIRE_STATUS INTEGER */
    public static final String PROP_NAME_lastFireStatus = "lastFireStatus";
    public static final int PROP_ID_lastFireStatus = 28;
    
    /* 数据版本: VERSION BIGINT */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 29;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 30;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 31;
    
    /* 修改人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 32;
    
    /* 修改时间: UPDATE_TIME TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 33;
    
    /* 备注: REMARK VARCHAR */
    public static final String PROP_NAME_remark = "remark";
    public static final int PROP_ID_remark = 34;
    
    /* 上次执行耗时(毫秒): LAST_DURATION_MS BIGINT */
    public static final String PROP_NAME_lastDurationMs = "lastDurationMs";
    public static final int PROP_ID_lastDurationMs = 35;
    
    /* 总触发次数: TOTAL_FIRE_COUNT BIGINT */
    public static final String PROP_NAME_totalFireCount = "totalFireCount";
    public static final int PROP_ID_totalFireCount = 36;
    
    /* 成功触发次数: SUCCESS_FIRE_COUNT BIGINT */
    public static final String PROP_NAME_successFireCount = "successFireCount";
    public static final int PROP_ID_successFireCount = 37;
    
    /* 失败触发次数: FAIL_FIRE_COUNT BIGINT */
    public static final String PROP_NAME_failFireCount = "failFireCount";
    public static final int PROP_ID_failFireCount = 38;
    
    /* 任务CPU开销(毫核): TASK_COST_CPU INTEGER */
    public static final String PROP_NAME_taskCostCpu = "taskCostCpu";
    public static final int PROP_ID_taskCostCpu = 39;
    
    /* 任务内存开销(MB): TASK_COST_MEMORY INTEGER */
    public static final String PROP_NAME_taskCostMemory = "taskCostMemory";
    public static final int PROP_ID_taskCostMemory = 40;
    
    /* 派发模式: DISPATCH_MODE VARCHAR */
    public static final String PROP_NAME_dispatchMode = "dispatchMode";
    public static final int PROP_ID_dispatchMode = 41;
    
    /* 分片数量: PARTITION_COUNT INTEGER */
    public static final String PROP_NAME_partitionCount = "partitionCount";
    public static final int PROP_ID_partitionCount = 42;
    
    /* 优先级: PRIORITY INTEGER */
    public static final String PROP_NAME_priority = "priority";
    public static final int PROP_ID_priority = 43;
    

    private static int _PROP_ID_BOUND = 44;

    
    /* component:  */
    public static final String PROP_NAME_jobParamsComponent = "jobParamsComponent";
    
    /* component:  */
    public static final String PROP_NAME_pauseCalendarSpecComponent = "pauseCalendarSpecComponent";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_jobScheduleId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_jobScheduleId};

    private static final String[] PROP_ID_TO_NAME = new String[44];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_jobScheduleId] = PROP_NAME_jobScheduleId;
          PROP_NAME_TO_ID.put(PROP_NAME_jobScheduleId, PROP_ID_jobScheduleId);
      
          PROP_ID_TO_NAME[PROP_ID_namespaceId] = PROP_NAME_namespaceId;
          PROP_NAME_TO_ID.put(PROP_NAME_namespaceId, PROP_ID_namespaceId);
      
          PROP_ID_TO_NAME[PROP_ID_groupId] = PROP_NAME_groupId;
          PROP_NAME_TO_ID.put(PROP_NAME_groupId, PROP_ID_groupId);
      
          PROP_ID_TO_NAME[PROP_ID_jobName] = PROP_NAME_jobName;
          PROP_NAME_TO_ID.put(PROP_NAME_jobName, PROP_ID_jobName);
      
          PROP_ID_TO_NAME[PROP_ID_displayName] = PROP_NAME_displayName;
          PROP_NAME_TO_ID.put(PROP_NAME_displayName, PROP_ID_displayName);
      
          PROP_ID_TO_NAME[PROP_ID_description] = PROP_NAME_description;
          PROP_NAME_TO_ID.put(PROP_NAME_description, PROP_ID_description);
      
          PROP_ID_TO_NAME[PROP_ID_scheduleStatus] = PROP_NAME_scheduleStatus;
          PROP_NAME_TO_ID.put(PROP_NAME_scheduleStatus, PROP_ID_scheduleStatus);
      
          PROP_ID_TO_NAME[PROP_ID_executorKind] = PROP_NAME_executorKind;
          PROP_NAME_TO_ID.put(PROP_NAME_executorKind, PROP_ID_executorKind);
      
          PROP_ID_TO_NAME[PROP_ID_jobParams] = PROP_NAME_jobParams;
          PROP_NAME_TO_ID.put(PROP_NAME_jobParams, PROP_ID_jobParams);
      
          PROP_ID_TO_NAME[PROP_ID_triggerType] = PROP_NAME_triggerType;
          PROP_NAME_TO_ID.put(PROP_NAME_triggerType, PROP_ID_triggerType);
      
          PROP_ID_TO_NAME[PROP_ID_cronExpr] = PROP_NAME_cronExpr;
          PROP_NAME_TO_ID.put(PROP_NAME_cronExpr, PROP_ID_cronExpr);
      
          PROP_ID_TO_NAME[PROP_ID_repeatIntervalMs] = PROP_NAME_repeatIntervalMs;
          PROP_NAME_TO_ID.put(PROP_NAME_repeatIntervalMs, PROP_ID_repeatIntervalMs);
      
          PROP_ID_TO_NAME[PROP_ID_maxExecutionCount] = PROP_NAME_maxExecutionCount;
          PROP_NAME_TO_ID.put(PROP_NAME_maxExecutionCount, PROP_ID_maxExecutionCount);
      
          PROP_ID_TO_NAME[PROP_ID_minScheduleTime] = PROP_NAME_minScheduleTime;
          PROP_NAME_TO_ID.put(PROP_NAME_minScheduleTime, PROP_ID_minScheduleTime);
      
          PROP_ID_TO_NAME[PROP_ID_maxScheduleTime] = PROP_NAME_maxScheduleTime;
          PROP_NAME_TO_ID.put(PROP_NAME_maxScheduleTime, PROP_ID_maxScheduleTime);
      
          PROP_ID_TO_NAME[PROP_ID_misfireThresholdMs] = PROP_NAME_misfireThresholdMs;
          PROP_NAME_TO_ID.put(PROP_NAME_misfireThresholdMs, PROP_ID_misfireThresholdMs);
      
          PROP_ID_TO_NAME[PROP_ID_useDefaultCalendar] = PROP_NAME_useDefaultCalendar;
          PROP_NAME_TO_ID.put(PROP_NAME_useDefaultCalendar, PROP_ID_useDefaultCalendar);
      
          PROP_ID_TO_NAME[PROP_ID_pauseCalendarSpec] = PROP_NAME_pauseCalendarSpec;
          PROP_NAME_TO_ID.put(PROP_NAME_pauseCalendarSpec, PROP_ID_pauseCalendarSpec);
      
          PROP_ID_TO_NAME[PROP_ID_blockStrategy] = PROP_NAME_blockStrategy;
          PROP_NAME_TO_ID.put(PROP_NAME_blockStrategy, PROP_ID_blockStrategy);
      
          PROP_ID_TO_NAME[PROP_ID_timeoutSeconds] = PROP_NAME_timeoutSeconds;
          PROP_NAME_TO_ID.put(PROP_NAME_timeoutSeconds, PROP_ID_timeoutSeconds);
      
          PROP_ID_TO_NAME[PROP_ID_retryPolicyId] = PROP_NAME_retryPolicyId;
          PROP_NAME_TO_ID.put(PROP_NAME_retryPolicyId, PROP_ID_retryPolicyId);
      
          PROP_ID_TO_NAME[PROP_ID_partitionIndex] = PROP_NAME_partitionIndex;
          PROP_NAME_TO_ID.put(PROP_NAME_partitionIndex, PROP_ID_partitionIndex);
      
          PROP_ID_TO_NAME[PROP_ID_fireCount] = PROP_NAME_fireCount;
          PROP_NAME_TO_ID.put(PROP_NAME_fireCount, PROP_ID_fireCount);
      
          PROP_ID_TO_NAME[PROP_ID_activeFireCount] = PROP_NAME_activeFireCount;
          PROP_NAME_TO_ID.put(PROP_NAME_activeFireCount, PROP_ID_activeFireCount);
      
          PROP_ID_TO_NAME[PROP_ID_lastFireTime] = PROP_NAME_lastFireTime;
          PROP_NAME_TO_ID.put(PROP_NAME_lastFireTime, PROP_ID_lastFireTime);
      
          PROP_ID_TO_NAME[PROP_ID_lastEndTime] = PROP_NAME_lastEndTime;
          PROP_NAME_TO_ID.put(PROP_NAME_lastEndTime, PROP_ID_lastEndTime);
      
          PROP_ID_TO_NAME[PROP_ID_nextFireTime] = PROP_NAME_nextFireTime;
          PROP_NAME_TO_ID.put(PROP_NAME_nextFireTime, PROP_ID_nextFireTime);
      
          PROP_ID_TO_NAME[PROP_ID_lastFireStatus] = PROP_NAME_lastFireStatus;
          PROP_NAME_TO_ID.put(PROP_NAME_lastFireStatus, PROP_ID_lastFireStatus);
      
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
      
          PROP_ID_TO_NAME[PROP_ID_lastDurationMs] = PROP_NAME_lastDurationMs;
          PROP_NAME_TO_ID.put(PROP_NAME_lastDurationMs, PROP_ID_lastDurationMs);
      
          PROP_ID_TO_NAME[PROP_ID_totalFireCount] = PROP_NAME_totalFireCount;
          PROP_NAME_TO_ID.put(PROP_NAME_totalFireCount, PROP_ID_totalFireCount);
      
          PROP_ID_TO_NAME[PROP_ID_successFireCount] = PROP_NAME_successFireCount;
          PROP_NAME_TO_ID.put(PROP_NAME_successFireCount, PROP_ID_successFireCount);
      
          PROP_ID_TO_NAME[PROP_ID_failFireCount] = PROP_NAME_failFireCount;
          PROP_NAME_TO_ID.put(PROP_NAME_failFireCount, PROP_ID_failFireCount);
      
          PROP_ID_TO_NAME[PROP_ID_taskCostCpu] = PROP_NAME_taskCostCpu;
          PROP_NAME_TO_ID.put(PROP_NAME_taskCostCpu, PROP_ID_taskCostCpu);
      
          PROP_ID_TO_NAME[PROP_ID_taskCostMemory] = PROP_NAME_taskCostMemory;
          PROP_NAME_TO_ID.put(PROP_NAME_taskCostMemory, PROP_ID_taskCostMemory);
      
          PROP_ID_TO_NAME[PROP_ID_dispatchMode] = PROP_NAME_dispatchMode;
          PROP_NAME_TO_ID.put(PROP_NAME_dispatchMode, PROP_ID_dispatchMode);
      
          PROP_ID_TO_NAME[PROP_ID_partitionCount] = PROP_NAME_partitionCount;
          PROP_NAME_TO_ID.put(PROP_NAME_partitionCount, PROP_ID_partitionCount);
      
          PROP_ID_TO_NAME[PROP_ID_priority] = PROP_NAME_priority;
          PROP_NAME_TO_ID.put(PROP_NAME_priority, PROP_ID_priority);
      
    }

    
    /* 调度ID: JOB_SCHEDULE_ID */
    private java.lang.String _jobScheduleId;
    
    /* 命名空间: NAMESPACE_ID */
    private java.lang.String _namespaceId;
    
    /* 分组: GROUP_ID */
    private java.lang.String _groupId;
    
    /* 作业名: JOB_NAME */
    private java.lang.String _jobName;
    
    /* 显示名: DISPLAY_NAME */
    private java.lang.String _displayName;
    
    /* 描述: DESCRIPTION */
    private java.lang.String _description;
    
    /* 调度状态: SCHEDULE_STATUS */
    private java.lang.Integer _scheduleStatus;
    
    /* 执行器类型: EXECUTOR_KIND */
    private java.lang.String _executorKind;
    
    /* 任务参数: JOB_PARAMS */
    private java.lang.String _jobParams;
    
    /* 触发器类型: TRIGGER_TYPE */
    private java.lang.Integer _triggerType;
    
    /* CRON表达式: CRON_EXPR */
    private java.lang.String _cronExpr;
    
    /* 重复间隔(毫秒): REPEAT_INTERVAL_MS */
    private java.lang.Long _repeatIntervalMs;
    
    /* 最大执行次数: MAX_EXECUTION_COUNT */
    private java.lang.Integer _maxExecutionCount;
    
    /* 最早调度时间: MIN_SCHEDULE_TIME */
    private java.sql.Timestamp _minScheduleTime;
    
    /* 最晚调度时间: MAX_SCHEDULE_TIME */
    private java.sql.Timestamp _maxScheduleTime;
    
    /* Misfire阈值(毫秒): MISFIRE_THRESHOLD_MS */
    private java.lang.Integer _misfireThresholdMs;
    
    /* 使用默认日历: USE_DEFAULT_CALENDAR */
    private java.lang.Byte _useDefaultCalendar;
    
    /* 暂停日历配置: PAUSE_CALENDAR_SPEC */
    private java.lang.String _pauseCalendarSpec;
    
    /* 阻塞策略: BLOCK_STRATEGY */
    private java.lang.Integer _blockStrategy;
    
    /* 超时时间(秒): TIMEOUT_SECONDS */
    private java.lang.Integer _timeoutSeconds;
    
    /* 重试策略ID: RETRY_POLICY_ID */
    private java.lang.String _retryPolicyId;
    
    /* 分区索引: PARTITION_INDEX */
    private java.lang.Short _partitionIndex;
    
    /* 已触发次数: FIRE_COUNT */
    private java.lang.Long _fireCount;
    
    /* 活跃触发数: ACTIVE_FIRE_COUNT */
    private java.lang.Integer _activeFireCount;
    
    /* 上次触发时间: LAST_FIRE_TIME */
    private java.sql.Timestamp _lastFireTime;
    
    /* 上次结束时间: LAST_END_TIME */
    private java.sql.Timestamp _lastEndTime;
    
    /* 下次触发时间: NEXT_FIRE_TIME */
    private java.sql.Timestamp _nextFireTime;
    
    /* 上次触发状态: LAST_FIRE_STATUS */
    private java.lang.Integer _lastFireStatus;
    
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
    
    /* 上次执行耗时(毫秒): LAST_DURATION_MS */
    private java.lang.Long _lastDurationMs;
    
    /* 总触发次数: TOTAL_FIRE_COUNT */
    private java.lang.Long _totalFireCount;
    
    /* 成功触发次数: SUCCESS_FIRE_COUNT */
    private java.lang.Long _successFireCount;
    
    /* 失败触发次数: FAIL_FIRE_COUNT */
    private java.lang.Long _failFireCount;
    
    /* 任务CPU开销(毫核): TASK_COST_CPU */
    private java.lang.Integer _taskCostCpu;
    
    /* 任务内存开销(MB): TASK_COST_MEMORY */
    private java.lang.Integer _taskCostMemory;
    
    /* 派发模式: DISPATCH_MODE */
    private java.lang.String _dispatchMode;
    
    /* 分片数量: PARTITION_COUNT */
    private java.lang.Integer _partitionCount;
    
    /* 优先级: PRIORITY */
    private java.lang.Integer _priority;
    

    public _NopJobSchedule(){
        // for debug
    }

    protected NopJobSchedule newInstance(){
        NopJobSchedule entity = new NopJobSchedule();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopJobSchedule cloneInstance() {
        NopJobSchedule entity = newInstance();
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
      return "io.nop.job.dao.entity.NopJobSchedule";
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
    
        return buildSimpleId(PROP_ID_jobScheduleId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_jobScheduleId;
          
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
        
            case PROP_ID_jobScheduleId:
               return getJobScheduleId();
        
            case PROP_ID_namespaceId:
               return getNamespaceId();
        
            case PROP_ID_groupId:
               return getGroupId();
        
            case PROP_ID_jobName:
               return getJobName();
        
            case PROP_ID_displayName:
               return getDisplayName();
        
            case PROP_ID_description:
               return getDescription();
        
            case PROP_ID_scheduleStatus:
               return getScheduleStatus();
        
            case PROP_ID_executorKind:
               return getExecutorKind();
        
            case PROP_ID_jobParams:
               return getJobParams();
        
            case PROP_ID_triggerType:
               return getTriggerType();
        
            case PROP_ID_cronExpr:
               return getCronExpr();
        
            case PROP_ID_repeatIntervalMs:
               return getRepeatIntervalMs();
        
            case PROP_ID_maxExecutionCount:
               return getMaxExecutionCount();
        
            case PROP_ID_minScheduleTime:
               return getMinScheduleTime();
        
            case PROP_ID_maxScheduleTime:
               return getMaxScheduleTime();
        
            case PROP_ID_misfireThresholdMs:
               return getMisfireThresholdMs();
        
            case PROP_ID_useDefaultCalendar:
               return getUseDefaultCalendar();
        
            case PROP_ID_pauseCalendarSpec:
               return getPauseCalendarSpec();
        
            case PROP_ID_blockStrategy:
               return getBlockStrategy();
        
            case PROP_ID_timeoutSeconds:
               return getTimeoutSeconds();
        
            case PROP_ID_retryPolicyId:
               return getRetryPolicyId();
        
            case PROP_ID_partitionIndex:
               return getPartitionIndex();
        
            case PROP_ID_fireCount:
               return getFireCount();
        
            case PROP_ID_activeFireCount:
               return getActiveFireCount();
        
            case PROP_ID_lastFireTime:
               return getLastFireTime();
        
            case PROP_ID_lastEndTime:
               return getLastEndTime();
        
            case PROP_ID_nextFireTime:
               return getNextFireTime();
        
            case PROP_ID_lastFireStatus:
               return getLastFireStatus();
        
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
        
            case PROP_ID_lastDurationMs:
               return getLastDurationMs();
        
            case PROP_ID_totalFireCount:
               return getTotalFireCount();
        
            case PROP_ID_successFireCount:
               return getSuccessFireCount();
        
            case PROP_ID_failFireCount:
               return getFailFireCount();
        
            case PROP_ID_taskCostCpu:
               return getTaskCostCpu();
        
            case PROP_ID_taskCostMemory:
               return getTaskCostMemory();
        
            case PROP_ID_dispatchMode:
               return getDispatchMode();
        
            case PROP_ID_partitionCount:
               return getPartitionCount();
        
            case PROP_ID_priority:
               return getPriority();
        
           default:
              return super.orm_propValue(propId);
        }
    }

    

    @Override
    public void orm_propValue(int propId, Object value){
        switch(propId){
        
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
        
            case PROP_ID_displayName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_displayName));
               }
               setDisplayName(typedValue);
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
        
            case PROP_ID_scheduleStatus:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_scheduleStatus));
               }
               setScheduleStatus(typedValue);
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
        
            case PROP_ID_jobParams:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_jobParams));
               }
               setJobParams(typedValue);
               break;
            }
        
            case PROP_ID_triggerType:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_triggerType));
               }
               setTriggerType(typedValue);
               break;
            }
        
            case PROP_ID_cronExpr:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_cronExpr));
               }
               setCronExpr(typedValue);
               break;
            }
        
            case PROP_ID_repeatIntervalMs:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_repeatIntervalMs));
               }
               setRepeatIntervalMs(typedValue);
               break;
            }
        
            case PROP_ID_maxExecutionCount:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_maxExecutionCount));
               }
               setMaxExecutionCount(typedValue);
               break;
            }
        
            case PROP_ID_minScheduleTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_minScheduleTime));
               }
               setMinScheduleTime(typedValue);
               break;
            }
        
            case PROP_ID_maxScheduleTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_maxScheduleTime));
               }
               setMaxScheduleTime(typedValue);
               break;
            }
        
            case PROP_ID_misfireThresholdMs:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_misfireThresholdMs));
               }
               setMisfireThresholdMs(typedValue);
               break;
            }
        
            case PROP_ID_useDefaultCalendar:{
               java.lang.Byte typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toByte(value,
                       err-> newTypeConversionError(PROP_NAME_useDefaultCalendar));
               }
               setUseDefaultCalendar(typedValue);
               break;
            }
        
            case PROP_ID_pauseCalendarSpec:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_pauseCalendarSpec));
               }
               setPauseCalendarSpec(typedValue);
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
        
            case PROP_ID_timeoutSeconds:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_timeoutSeconds));
               }
               setTimeoutSeconds(typedValue);
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
        
            case PROP_ID_partitionIndex:{
               java.lang.Short typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toShort(value,
                       err-> newTypeConversionError(PROP_NAME_partitionIndex));
               }
               setPartitionIndex(typedValue);
               break;
            }
        
            case PROP_ID_fireCount:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_fireCount));
               }
               setFireCount(typedValue);
               break;
            }
        
            case PROP_ID_activeFireCount:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_activeFireCount));
               }
               setActiveFireCount(typedValue);
               break;
            }
        
            case PROP_ID_lastFireTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_lastFireTime));
               }
               setLastFireTime(typedValue);
               break;
            }
        
            case PROP_ID_lastEndTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_lastEndTime));
               }
               setLastEndTime(typedValue);
               break;
            }
        
            case PROP_ID_nextFireTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_nextFireTime));
               }
               setNextFireTime(typedValue);
               break;
            }
        
            case PROP_ID_lastFireStatus:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_lastFireStatus));
               }
               setLastFireStatus(typedValue);
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
        
            case PROP_ID_lastDurationMs:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_lastDurationMs));
               }
               setLastDurationMs(typedValue);
               break;
            }
        
            case PROP_ID_totalFireCount:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_totalFireCount));
               }
               setTotalFireCount(typedValue);
               break;
            }
        
            case PROP_ID_successFireCount:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_successFireCount));
               }
               setSuccessFireCount(typedValue);
               break;
            }
        
            case PROP_ID_failFireCount:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_failFireCount));
               }
               setFailFireCount(typedValue);
               break;
            }
        
            case PROP_ID_taskCostCpu:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_taskCostCpu));
               }
               setTaskCostCpu(typedValue);
               break;
            }
        
            case PROP_ID_taskCostMemory:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_taskCostMemory));
               }
               setTaskCostMemory(typedValue);
               break;
            }
        
            case PROP_ID_dispatchMode:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_dispatchMode));
               }
               setDispatchMode(typedValue);
               break;
            }
        
            case PROP_ID_partitionCount:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_partitionCount));
               }
               setPartitionCount(typedValue);
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
        
           default:
              super.orm_propValue(propId,value);
        }
    }

    @Override
    public void orm_internalSet(int propId, Object value) {
        switch(propId){
        
            case PROP_ID_jobScheduleId:{
               onInitProp(propId);
               this._jobScheduleId = (java.lang.String)value;
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
        
            case PROP_ID_jobName:{
               onInitProp(propId);
               this._jobName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_displayName:{
               onInitProp(propId);
               this._displayName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_description:{
               onInitProp(propId);
               this._description = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_scheduleStatus:{
               onInitProp(propId);
               this._scheduleStatus = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_executorKind:{
               onInitProp(propId);
               this._executorKind = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_jobParams:{
               onInitProp(propId);
               this._jobParams = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_triggerType:{
               onInitProp(propId);
               this._triggerType = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_cronExpr:{
               onInitProp(propId);
               this._cronExpr = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_repeatIntervalMs:{
               onInitProp(propId);
               this._repeatIntervalMs = (java.lang.Long)value;
               
               break;
            }
        
            case PROP_ID_maxExecutionCount:{
               onInitProp(propId);
               this._maxExecutionCount = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_minScheduleTime:{
               onInitProp(propId);
               this._minScheduleTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_maxScheduleTime:{
               onInitProp(propId);
               this._maxScheduleTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_misfireThresholdMs:{
               onInitProp(propId);
               this._misfireThresholdMs = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_useDefaultCalendar:{
               onInitProp(propId);
               this._useDefaultCalendar = (java.lang.Byte)value;
               
               break;
            }
        
            case PROP_ID_pauseCalendarSpec:{
               onInitProp(propId);
               this._pauseCalendarSpec = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_blockStrategy:{
               onInitProp(propId);
               this._blockStrategy = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_timeoutSeconds:{
               onInitProp(propId);
               this._timeoutSeconds = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_retryPolicyId:{
               onInitProp(propId);
               this._retryPolicyId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_partitionIndex:{
               onInitProp(propId);
               this._partitionIndex = (java.lang.Short)value;
               
               break;
            }
        
            case PROP_ID_fireCount:{
               onInitProp(propId);
               this._fireCount = (java.lang.Long)value;
               
               break;
            }
        
            case PROP_ID_activeFireCount:{
               onInitProp(propId);
               this._activeFireCount = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_lastFireTime:{
               onInitProp(propId);
               this._lastFireTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_lastEndTime:{
               onInitProp(propId);
               this._lastEndTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_nextFireTime:{
               onInitProp(propId);
               this._nextFireTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_lastFireStatus:{
               onInitProp(propId);
               this._lastFireStatus = (java.lang.Integer)value;
               
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
        
            case PROP_ID_lastDurationMs:{
               onInitProp(propId);
               this._lastDurationMs = (java.lang.Long)value;
               
               break;
            }
        
            case PROP_ID_totalFireCount:{
               onInitProp(propId);
               this._totalFireCount = (java.lang.Long)value;
               
               break;
            }
        
            case PROP_ID_successFireCount:{
               onInitProp(propId);
               this._successFireCount = (java.lang.Long)value;
               
               break;
            }
        
            case PROP_ID_failFireCount:{
               onInitProp(propId);
               this._failFireCount = (java.lang.Long)value;
               
               break;
            }
        
            case PROP_ID_taskCostCpu:{
               onInitProp(propId);
               this._taskCostCpu = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_taskCostMemory:{
               onInitProp(propId);
               this._taskCostMemory = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_dispatchMode:{
               onInitProp(propId);
               this._dispatchMode = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_partitionCount:{
               onInitProp(propId);
               this._partitionCount = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_priority:{
               onInitProp(propId);
               this._priority = (java.lang.Integer)value;
               
               break;
            }
        
           default:
              super.orm_internalSet(propId,value);
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
            orm_id();
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
     * 显示名: DISPLAY_NAME
     */
    public final java.lang.String getDisplayName(){
         onPropGet(PROP_ID_displayName);
         return _displayName;
    }

    /**
     * 显示名: DISPLAY_NAME
     */
    public final void setDisplayName(java.lang.String value){
        if(onPropSet(PROP_ID_displayName,value)){
            this._displayName = value;
            internalClearRefs(PROP_ID_displayName);
            
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
     * 调度状态: SCHEDULE_STATUS
     */
    public final java.lang.Integer getScheduleStatus(){
         onPropGet(PROP_ID_scheduleStatus);
         return _scheduleStatus;
    }

    /**
     * 调度状态: SCHEDULE_STATUS
     */
    public final void setScheduleStatus(java.lang.Integer value){
        if(onPropSet(PROP_ID_scheduleStatus,value)){
            this._scheduleStatus = value;
            internalClearRefs(PROP_ID_scheduleStatus);
            
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
     * 任务参数: JOB_PARAMS
     */
    public final java.lang.String getJobParams(){
         onPropGet(PROP_ID_jobParams);
         return _jobParams;
    }

    /**
     * 任务参数: JOB_PARAMS
     */
    public final void setJobParams(java.lang.String value){
        if(onPropSet(PROP_ID_jobParams,value)){
            this._jobParams = value;
            internalClearRefs(PROP_ID_jobParams);
            
        }
    }
    
    /**
     * 触发器类型: TRIGGER_TYPE
     */
    public final java.lang.Integer getTriggerType(){
         onPropGet(PROP_ID_triggerType);
         return _triggerType;
    }

    /**
     * 触发器类型: TRIGGER_TYPE
     */
    public final void setTriggerType(java.lang.Integer value){
        if(onPropSet(PROP_ID_triggerType,value)){
            this._triggerType = value;
            internalClearRefs(PROP_ID_triggerType);
            
        }
    }
    
    /**
     * CRON表达式: CRON_EXPR
     */
    public final java.lang.String getCronExpr(){
         onPropGet(PROP_ID_cronExpr);
         return _cronExpr;
    }

    /**
     * CRON表达式: CRON_EXPR
     */
    public final void setCronExpr(java.lang.String value){
        if(onPropSet(PROP_ID_cronExpr,value)){
            this._cronExpr = value;
            internalClearRefs(PROP_ID_cronExpr);
            
        }
    }
    
    /**
     * 重复间隔(毫秒): REPEAT_INTERVAL_MS
     */
    public final java.lang.Long getRepeatIntervalMs(){
         onPropGet(PROP_ID_repeatIntervalMs);
         return _repeatIntervalMs;
    }

    /**
     * 重复间隔(毫秒): REPEAT_INTERVAL_MS
     */
    public final void setRepeatIntervalMs(java.lang.Long value){
        if(onPropSet(PROP_ID_repeatIntervalMs,value)){
            this._repeatIntervalMs = value;
            internalClearRefs(PROP_ID_repeatIntervalMs);
            
        }
    }
    
    /**
     * 最大执行次数: MAX_EXECUTION_COUNT
     */
    public final java.lang.Integer getMaxExecutionCount(){
         onPropGet(PROP_ID_maxExecutionCount);
         return _maxExecutionCount;
    }

    /**
     * 最大执行次数: MAX_EXECUTION_COUNT
     */
    public final void setMaxExecutionCount(java.lang.Integer value){
        if(onPropSet(PROP_ID_maxExecutionCount,value)){
            this._maxExecutionCount = value;
            internalClearRefs(PROP_ID_maxExecutionCount);
            
        }
    }
    
    /**
     * 最早调度时间: MIN_SCHEDULE_TIME
     */
    public final java.sql.Timestamp getMinScheduleTime(){
         onPropGet(PROP_ID_minScheduleTime);
         return _minScheduleTime;
    }

    /**
     * 最早调度时间: MIN_SCHEDULE_TIME
     */
    public final void setMinScheduleTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_minScheduleTime,value)){
            this._minScheduleTime = value;
            internalClearRefs(PROP_ID_minScheduleTime);
            
        }
    }
    
    /**
     * 最晚调度时间: MAX_SCHEDULE_TIME
     */
    public final java.sql.Timestamp getMaxScheduleTime(){
         onPropGet(PROP_ID_maxScheduleTime);
         return _maxScheduleTime;
    }

    /**
     * 最晚调度时间: MAX_SCHEDULE_TIME
     */
    public final void setMaxScheduleTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_maxScheduleTime,value)){
            this._maxScheduleTime = value;
            internalClearRefs(PROP_ID_maxScheduleTime);
            
        }
    }
    
    /**
     * Misfire阈值(毫秒): MISFIRE_THRESHOLD_MS
     */
    public final java.lang.Integer getMisfireThresholdMs(){
         onPropGet(PROP_ID_misfireThresholdMs);
         return _misfireThresholdMs;
    }

    /**
     * Misfire阈值(毫秒): MISFIRE_THRESHOLD_MS
     */
    public final void setMisfireThresholdMs(java.lang.Integer value){
        if(onPropSet(PROP_ID_misfireThresholdMs,value)){
            this._misfireThresholdMs = value;
            internalClearRefs(PROP_ID_misfireThresholdMs);
            
        }
    }
    
    /**
     * 使用默认日历: USE_DEFAULT_CALENDAR
     */
    public final java.lang.Byte getUseDefaultCalendar(){
         onPropGet(PROP_ID_useDefaultCalendar);
         return _useDefaultCalendar;
    }

    /**
     * 使用默认日历: USE_DEFAULT_CALENDAR
     */
    public final void setUseDefaultCalendar(java.lang.Byte value){
        if(onPropSet(PROP_ID_useDefaultCalendar,value)){
            this._useDefaultCalendar = value;
            internalClearRefs(PROP_ID_useDefaultCalendar);
            
        }
    }
    
    /**
     * 暂停日历配置: PAUSE_CALENDAR_SPEC
     */
    public final java.lang.String getPauseCalendarSpec(){
         onPropGet(PROP_ID_pauseCalendarSpec);
         return _pauseCalendarSpec;
    }

    /**
     * 暂停日历配置: PAUSE_CALENDAR_SPEC
     */
    public final void setPauseCalendarSpec(java.lang.String value){
        if(onPropSet(PROP_ID_pauseCalendarSpec,value)){
            this._pauseCalendarSpec = value;
            internalClearRefs(PROP_ID_pauseCalendarSpec);
            
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
     * 超时时间(秒): TIMEOUT_SECONDS
     */
    public final java.lang.Integer getTimeoutSeconds(){
         onPropGet(PROP_ID_timeoutSeconds);
         return _timeoutSeconds;
    }

    /**
     * 超时时间(秒): TIMEOUT_SECONDS
     */
    public final void setTimeoutSeconds(java.lang.Integer value){
        if(onPropSet(PROP_ID_timeoutSeconds,value)){
            this._timeoutSeconds = value;
            internalClearRefs(PROP_ID_timeoutSeconds);
            
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
     * 已触发次数: FIRE_COUNT
     */
    public final java.lang.Long getFireCount(){
         onPropGet(PROP_ID_fireCount);
         return _fireCount;
    }

    /**
     * 已触发次数: FIRE_COUNT
     */
    public final void setFireCount(java.lang.Long value){
        if(onPropSet(PROP_ID_fireCount,value)){
            this._fireCount = value;
            internalClearRefs(PROP_ID_fireCount);
            
        }
    }
    
    /**
     * 活跃触发数: ACTIVE_FIRE_COUNT
     */
    public final java.lang.Integer getActiveFireCount(){
         onPropGet(PROP_ID_activeFireCount);
         return _activeFireCount;
    }

    /**
     * 活跃触发数: ACTIVE_FIRE_COUNT
     */
    public final void setActiveFireCount(java.lang.Integer value){
        if(onPropSet(PROP_ID_activeFireCount,value)){
            this._activeFireCount = value;
            internalClearRefs(PROP_ID_activeFireCount);
            
        }
    }
    
    /**
     * 上次触发时间: LAST_FIRE_TIME
     */
    public final java.sql.Timestamp getLastFireTime(){
         onPropGet(PROP_ID_lastFireTime);
         return _lastFireTime;
    }

    /**
     * 上次触发时间: LAST_FIRE_TIME
     */
    public final void setLastFireTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_lastFireTime,value)){
            this._lastFireTime = value;
            internalClearRefs(PROP_ID_lastFireTime);
            
        }
    }
    
    /**
     * 上次结束时间: LAST_END_TIME
     */
    public final java.sql.Timestamp getLastEndTime(){
         onPropGet(PROP_ID_lastEndTime);
         return _lastEndTime;
    }

    /**
     * 上次结束时间: LAST_END_TIME
     */
    public final void setLastEndTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_lastEndTime,value)){
            this._lastEndTime = value;
            internalClearRefs(PROP_ID_lastEndTime);
            
        }
    }
    
    /**
     * 下次触发时间: NEXT_FIRE_TIME
     */
    public final java.sql.Timestamp getNextFireTime(){
         onPropGet(PROP_ID_nextFireTime);
         return _nextFireTime;
    }

    /**
     * 下次触发时间: NEXT_FIRE_TIME
     */
    public final void setNextFireTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_nextFireTime,value)){
            this._nextFireTime = value;
            internalClearRefs(PROP_ID_nextFireTime);
            
        }
    }
    
    /**
     * 上次触发状态: LAST_FIRE_STATUS
     */
    public final java.lang.Integer getLastFireStatus(){
         onPropGet(PROP_ID_lastFireStatus);
         return _lastFireStatus;
    }

    /**
     * 上次触发状态: LAST_FIRE_STATUS
     */
    public final void setLastFireStatus(java.lang.Integer value){
        if(onPropSet(PROP_ID_lastFireStatus,value)){
            this._lastFireStatus = value;
            internalClearRefs(PROP_ID_lastFireStatus);
            
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
     * 上次执行耗时(毫秒): LAST_DURATION_MS
     */
    public final java.lang.Long getLastDurationMs(){
         onPropGet(PROP_ID_lastDurationMs);
         return _lastDurationMs;
    }

    /**
     * 上次执行耗时(毫秒): LAST_DURATION_MS
     */
    public final void setLastDurationMs(java.lang.Long value){
        if(onPropSet(PROP_ID_lastDurationMs,value)){
            this._lastDurationMs = value;
            internalClearRefs(PROP_ID_lastDurationMs);
            
        }
    }
    
    /**
     * 总触发次数: TOTAL_FIRE_COUNT
     */
    public final java.lang.Long getTotalFireCount(){
         onPropGet(PROP_ID_totalFireCount);
         return _totalFireCount;
    }

    /**
     * 总触发次数: TOTAL_FIRE_COUNT
     */
    public final void setTotalFireCount(java.lang.Long value){
        if(onPropSet(PROP_ID_totalFireCount,value)){
            this._totalFireCount = value;
            internalClearRefs(PROP_ID_totalFireCount);
            
        }
    }
    
    /**
     * 成功触发次数: SUCCESS_FIRE_COUNT
     */
    public final java.lang.Long getSuccessFireCount(){
         onPropGet(PROP_ID_successFireCount);
         return _successFireCount;
    }

    /**
     * 成功触发次数: SUCCESS_FIRE_COUNT
     */
    public final void setSuccessFireCount(java.lang.Long value){
        if(onPropSet(PROP_ID_successFireCount,value)){
            this._successFireCount = value;
            internalClearRefs(PROP_ID_successFireCount);
            
        }
    }
    
    /**
     * 失败触发次数: FAIL_FIRE_COUNT
     */
    public final java.lang.Long getFailFireCount(){
         onPropGet(PROP_ID_failFireCount);
         return _failFireCount;
    }

    /**
     * 失败触发次数: FAIL_FIRE_COUNT
     */
    public final void setFailFireCount(java.lang.Long value){
        if(onPropSet(PROP_ID_failFireCount,value)){
            this._failFireCount = value;
            internalClearRefs(PROP_ID_failFireCount);
            
        }
    }
    
    /**
     * 任务CPU开销(毫核): TASK_COST_CPU
     */
    public final java.lang.Integer getTaskCostCpu(){
         onPropGet(PROP_ID_taskCostCpu);
         return _taskCostCpu;
    }

    /**
     * 任务CPU开销(毫核): TASK_COST_CPU
     */
    public final void setTaskCostCpu(java.lang.Integer value){
        if(onPropSet(PROP_ID_taskCostCpu,value)){
            this._taskCostCpu = value;
            internalClearRefs(PROP_ID_taskCostCpu);
            
        }
    }
    
    /**
     * 任务内存开销(MB): TASK_COST_MEMORY
     */
    public final java.lang.Integer getTaskCostMemory(){
         onPropGet(PROP_ID_taskCostMemory);
         return _taskCostMemory;
    }

    /**
     * 任务内存开销(MB): TASK_COST_MEMORY
     */
    public final void setTaskCostMemory(java.lang.Integer value){
        if(onPropSet(PROP_ID_taskCostMemory,value)){
            this._taskCostMemory = value;
            internalClearRefs(PROP_ID_taskCostMemory);
            
        }
    }
    
    /**
     * 派发模式: DISPATCH_MODE
     */
    public final java.lang.String getDispatchMode(){
         onPropGet(PROP_ID_dispatchMode);
         return _dispatchMode;
    }

    /**
     * 派发模式: DISPATCH_MODE
     */
    public final void setDispatchMode(java.lang.String value){
        if(onPropSet(PROP_ID_dispatchMode,value)){
            this._dispatchMode = value;
            internalClearRefs(PROP_ID_dispatchMode);
            
        }
    }
    
    /**
     * 分片数量: PARTITION_COUNT
     */
    public final java.lang.Integer getPartitionCount(){
         onPropGet(PROP_ID_partitionCount);
         return _partitionCount;
    }

    /**
     * 分片数量: PARTITION_COUNT
     */
    public final void setPartitionCount(java.lang.Integer value){
        if(onPropSet(PROP_ID_partitionCount,value)){
            this._partitionCount = value;
            internalClearRefs(PROP_ID_partitionCount);
            
        }
    }
    
    /**
     * 优先级: PRIORITY
     */
    public final java.lang.Integer getPriority(){
         onPropGet(PROP_ID_priority);
         return _priority;
    }

    /**
     * 优先级: PRIORITY
     */
    public final void setPriority(java.lang.Integer value){
        if(onPropSet(PROP_ID_priority,value)){
            this._priority = value;
            internalClearRefs(PROP_ID_priority);
            
        }
    }
    
   private io.nop.orm.component.JsonOrmComponent _jobParamsComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_jobParamsComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_jobParamsComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_jobParams);
      
   }

   public final io.nop.orm.component.JsonOrmComponent getJobParamsComponent(){
      if(_jobParamsComponent == null){
          _jobParamsComponent = new io.nop.orm.component.JsonOrmComponent();
          _jobParamsComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_jobParamsComponent);
      }
      return _jobParamsComponent;
   }

   private io.nop.orm.component.JsonOrmComponent _pauseCalendarSpecComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_pauseCalendarSpecComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_pauseCalendarSpecComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_pauseCalendarSpec);
      
   }

   public final io.nop.orm.component.JsonOrmComponent getPauseCalendarSpecComponent(){
      if(_pauseCalendarSpecComponent == null){
          _pauseCalendarSpecComponent = new io.nop.orm.component.JsonOrmComponent();
          _pauseCalendarSpecComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_pauseCalendarSpecComponent);
      }
      return _pauseCalendarSpecComponent;
   }

}
// resume CPD analysis - CPD-ON
