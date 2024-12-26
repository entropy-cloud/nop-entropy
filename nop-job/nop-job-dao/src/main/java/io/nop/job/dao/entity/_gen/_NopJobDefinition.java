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

import io.nop.job.dao.entity.NopJobDefinition;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  作业定义: nop_job_definition
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopJobDefinition extends DynamicOrmEntity{
    
    /* SID: SID VARCHAR */
    public static final String PROP_NAME_sid = "sid";
    public static final int PROP_ID_sid = 1;
    
    /* 显示名: DISPLAY_NAME VARCHAR */
    public static final String PROP_NAME_displayName = "displayName";
    public static final int PROP_ID_displayName = 2;
    
    /* 任务名: JOB_NAME VARCHAR */
    public static final String PROP_NAME_jobName = "jobName";
    public static final int PROP_ID_jobName = 3;
    
    /* 任务组: JOB_GROUP VARCHAR */
    public static final String PROP_NAME_jobGroup = "jobGroup";
    public static final int PROP_ID_jobGroup = 4;
    
    /* 任务参数: JOB_PARAMS VARCHAR */
    public static final String PROP_NAME_jobParams = "jobParams";
    public static final int PROP_ID_jobParams = 5;
    
    /* 任务执行函数: JOB_INVOKER VARCHAR */
    public static final String PROP_NAME_jobInvoker = "jobInvoker";
    public static final int PROP_ID_jobInvoker = 6;
    
    /* 任务描述: DESCRIPTION VARCHAR */
    public static final String PROP_NAME_description = "description";
    public static final int PROP_ID_description = 7;
    
    /* 任务状态: STATUS INTEGER */
    public static final String PROP_NAME_status = "status";
    public static final int PROP_ID_status = 8;
    
    /* 定时表达式: CRON_EXPR VARCHAR */
    public static final String PROP_NAME_cronExpr = "cronExpr";
    public static final int PROP_ID_cronExpr = 9;
    
    /* 定时执行间隔: REPEAT_INTERVAL INTEGER */
    public static final String PROP_NAME_repeatInterval = "repeatInterval";
    public static final int PROP_ID_repeatInterval = 10;
    
    /* 是否固定延时: IS_FIXED_DELAY TINYINT */
    public static final String PROP_NAME_isFixedDelay = "isFixedDelay";
    public static final int PROP_ID_isFixedDelay = 11;
    
    /* 最多执行次数: MAX_EXECUTION_COUNT INTEGER */
    public static final String PROP_NAME_maxExecutionCount = "maxExecutionCount";
    public static final int PROP_ID_maxExecutionCount = 12;
    
    /* 最近调度时间: MIN_SCHEDULE_TIME TIMESTAMP */
    public static final String PROP_NAME_minScheduleTime = "minScheduleTime";
    public static final int PROP_ID_minScheduleTime = 13;
    
    /* 最大调度时间: MAX_SCHEDULE_TIME TIMESTAMP */
    public static final String PROP_NAME_maxScheduleTime = "maxScheduleTime";
    public static final int PROP_ID_maxScheduleTime = 14;
    
    /* 超时阈值: MISFIRE_THRESHOLD INTEGER */
    public static final String PROP_NAME_misfireThreshold = "misfireThreshold";
    public static final int PROP_ID_misfireThreshold = 15;
    
    /* 最大允许失败次数: MAX_FAILED_COUNT INTEGER */
    public static final String PROP_NAME_maxFailedCount = "maxFailedCount";
    public static final int PROP_ID_maxFailedCount = 16;
    
    /* 使用系统内置日历: IS_USE_DEFAULT_CALENDAR TINYINT */
    public static final String PROP_NAME_isUseDefaultCalendar = "isUseDefaultCalendar";
    public static final int PROP_ID_isUseDefaultCalendar = 17;
    
    /* 暂停日历: PAUSE_CALENDARS VARCHAR */
    public static final String PROP_NAME_pauseCalendars = "pauseCalendars";
    public static final int PROP_ID_pauseCalendars = 18;
    
    /* 调度器分组: SCHEDULER_GROUP VARCHAR */
    public static final String PROP_NAME_schedulerGroup = "schedulerGroup";
    public static final int PROP_ID_schedulerGroup = 19;
    
    /* 调度器ID: SCHEDULER_ID VARCHAR */
    public static final String PROP_NAME_schedulerId = "schedulerId";
    public static final int PROP_ID_schedulerId = 20;
    
    /* 调度器世代: SCHEDULER_EPOCH BIGINT */
    public static final String PROP_NAME_schedulerEpoch = "schedulerEpoch";
    public static final int PROP_ID_schedulerEpoch = 21;
    
    /* 调度器加载时间: SCHEDULER_LOAD_TIME TIMESTAMP */
    public static final String PROP_NAME_schedulerLoadTime = "schedulerLoadTime";
    public static final int PROP_ID_schedulerLoadTime = 22;
    
    /* 数据版本: VERSION BIGINT */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 23;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 24;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 25;
    
    /* 修改人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 26;
    
    /* 修改时间: UPDATE_TIME TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 27;
    
    /* 备注: REMARK VARCHAR */
    public static final String PROP_NAME_remark = "remark";
    public static final int PROP_ID_remark = 28;
    

    private static int _PROP_ID_BOUND = 29;

    
    /* component:  */
    public static final String PROP_NAME_jobParamsComponent = "jobParamsComponent";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_sid);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_sid};

    private static final String[] PROP_ID_TO_NAME = new String[29];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_sid] = PROP_NAME_sid;
          PROP_NAME_TO_ID.put(PROP_NAME_sid, PROP_ID_sid);
      
          PROP_ID_TO_NAME[PROP_ID_displayName] = PROP_NAME_displayName;
          PROP_NAME_TO_ID.put(PROP_NAME_displayName, PROP_ID_displayName);
      
          PROP_ID_TO_NAME[PROP_ID_jobName] = PROP_NAME_jobName;
          PROP_NAME_TO_ID.put(PROP_NAME_jobName, PROP_ID_jobName);
      
          PROP_ID_TO_NAME[PROP_ID_jobGroup] = PROP_NAME_jobGroup;
          PROP_NAME_TO_ID.put(PROP_NAME_jobGroup, PROP_ID_jobGroup);
      
          PROP_ID_TO_NAME[PROP_ID_jobParams] = PROP_NAME_jobParams;
          PROP_NAME_TO_ID.put(PROP_NAME_jobParams, PROP_ID_jobParams);
      
          PROP_ID_TO_NAME[PROP_ID_jobInvoker] = PROP_NAME_jobInvoker;
          PROP_NAME_TO_ID.put(PROP_NAME_jobInvoker, PROP_ID_jobInvoker);
      
          PROP_ID_TO_NAME[PROP_ID_description] = PROP_NAME_description;
          PROP_NAME_TO_ID.put(PROP_NAME_description, PROP_ID_description);
      
          PROP_ID_TO_NAME[PROP_ID_status] = PROP_NAME_status;
          PROP_NAME_TO_ID.put(PROP_NAME_status, PROP_ID_status);
      
          PROP_ID_TO_NAME[PROP_ID_cronExpr] = PROP_NAME_cronExpr;
          PROP_NAME_TO_ID.put(PROP_NAME_cronExpr, PROP_ID_cronExpr);
      
          PROP_ID_TO_NAME[PROP_ID_repeatInterval] = PROP_NAME_repeatInterval;
          PROP_NAME_TO_ID.put(PROP_NAME_repeatInterval, PROP_ID_repeatInterval);
      
          PROP_ID_TO_NAME[PROP_ID_isFixedDelay] = PROP_NAME_isFixedDelay;
          PROP_NAME_TO_ID.put(PROP_NAME_isFixedDelay, PROP_ID_isFixedDelay);
      
          PROP_ID_TO_NAME[PROP_ID_maxExecutionCount] = PROP_NAME_maxExecutionCount;
          PROP_NAME_TO_ID.put(PROP_NAME_maxExecutionCount, PROP_ID_maxExecutionCount);
      
          PROP_ID_TO_NAME[PROP_ID_minScheduleTime] = PROP_NAME_minScheduleTime;
          PROP_NAME_TO_ID.put(PROP_NAME_minScheduleTime, PROP_ID_minScheduleTime);
      
          PROP_ID_TO_NAME[PROP_ID_maxScheduleTime] = PROP_NAME_maxScheduleTime;
          PROP_NAME_TO_ID.put(PROP_NAME_maxScheduleTime, PROP_ID_maxScheduleTime);
      
          PROP_ID_TO_NAME[PROP_ID_misfireThreshold] = PROP_NAME_misfireThreshold;
          PROP_NAME_TO_ID.put(PROP_NAME_misfireThreshold, PROP_ID_misfireThreshold);
      
          PROP_ID_TO_NAME[PROP_ID_maxFailedCount] = PROP_NAME_maxFailedCount;
          PROP_NAME_TO_ID.put(PROP_NAME_maxFailedCount, PROP_ID_maxFailedCount);
      
          PROP_ID_TO_NAME[PROP_ID_isUseDefaultCalendar] = PROP_NAME_isUseDefaultCalendar;
          PROP_NAME_TO_ID.put(PROP_NAME_isUseDefaultCalendar, PROP_ID_isUseDefaultCalendar);
      
          PROP_ID_TO_NAME[PROP_ID_pauseCalendars] = PROP_NAME_pauseCalendars;
          PROP_NAME_TO_ID.put(PROP_NAME_pauseCalendars, PROP_ID_pauseCalendars);
      
          PROP_ID_TO_NAME[PROP_ID_schedulerGroup] = PROP_NAME_schedulerGroup;
          PROP_NAME_TO_ID.put(PROP_NAME_schedulerGroup, PROP_ID_schedulerGroup);
      
          PROP_ID_TO_NAME[PROP_ID_schedulerId] = PROP_NAME_schedulerId;
          PROP_NAME_TO_ID.put(PROP_NAME_schedulerId, PROP_ID_schedulerId);
      
          PROP_ID_TO_NAME[PROP_ID_schedulerEpoch] = PROP_NAME_schedulerEpoch;
          PROP_NAME_TO_ID.put(PROP_NAME_schedulerEpoch, PROP_ID_schedulerEpoch);
      
          PROP_ID_TO_NAME[PROP_ID_schedulerLoadTime] = PROP_NAME_schedulerLoadTime;
          PROP_NAME_TO_ID.put(PROP_NAME_schedulerLoadTime, PROP_ID_schedulerLoadTime);
      
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
    
    /* 显示名: DISPLAY_NAME */
    private java.lang.String _displayName;
    
    /* 任务名: JOB_NAME */
    private java.lang.String _jobName;
    
    /* 任务组: JOB_GROUP */
    private java.lang.String _jobGroup;
    
    /* 任务参数: JOB_PARAMS */
    private java.lang.String _jobParams;
    
    /* 任务执行函数: JOB_INVOKER */
    private java.lang.String _jobInvoker;
    
    /* 任务描述: DESCRIPTION */
    private java.lang.String _description;
    
    /* 任务状态: STATUS */
    private java.lang.Integer _status;
    
    /* 定时表达式: CRON_EXPR */
    private java.lang.String _cronExpr;
    
    /* 定时执行间隔: REPEAT_INTERVAL */
    private java.lang.Integer _repeatInterval;
    
    /* 是否固定延时: IS_FIXED_DELAY */
    private java.lang.Byte _isFixedDelay;
    
    /* 最多执行次数: MAX_EXECUTION_COUNT */
    private java.lang.Integer _maxExecutionCount;
    
    /* 最近调度时间: MIN_SCHEDULE_TIME */
    private java.sql.Timestamp _minScheduleTime;
    
    /* 最大调度时间: MAX_SCHEDULE_TIME */
    private java.sql.Timestamp _maxScheduleTime;
    
    /* 超时阈值: MISFIRE_THRESHOLD */
    private java.lang.Integer _misfireThreshold;
    
    /* 最大允许失败次数: MAX_FAILED_COUNT */
    private java.lang.Integer _maxFailedCount;
    
    /* 使用系统内置日历: IS_USE_DEFAULT_CALENDAR */
    private java.lang.Byte _isUseDefaultCalendar;
    
    /* 暂停日历: PAUSE_CALENDARS */
    private java.lang.String _pauseCalendars;
    
    /* 调度器分组: SCHEDULER_GROUP */
    private java.lang.String _schedulerGroup;
    
    /* 调度器ID: SCHEDULER_ID */
    private java.lang.String _schedulerId;
    
    /* 调度器世代: SCHEDULER_EPOCH */
    private java.lang.Long _schedulerEpoch;
    
    /* 调度器加载时间: SCHEDULER_LOAD_TIME */
    private java.sql.Timestamp _schedulerLoadTime;
    
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
    

    public _NopJobDefinition(){
        // for debug
    }

    protected NopJobDefinition newInstance(){
        NopJobDefinition entity = new NopJobDefinition();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopJobDefinition cloneInstance() {
        NopJobDefinition entity = newInstance();
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
      return "io.nop.job.dao.entity.NopJobDefinition";
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
        
            case PROP_ID_displayName:
               return getDisplayName();
        
            case PROP_ID_jobName:
               return getJobName();
        
            case PROP_ID_jobGroup:
               return getJobGroup();
        
            case PROP_ID_jobParams:
               return getJobParams();
        
            case PROP_ID_jobInvoker:
               return getJobInvoker();
        
            case PROP_ID_description:
               return getDescription();
        
            case PROP_ID_status:
               return getStatus();
        
            case PROP_ID_cronExpr:
               return getCronExpr();
        
            case PROP_ID_repeatInterval:
               return getRepeatInterval();
        
            case PROP_ID_isFixedDelay:
               return getIsFixedDelay();
        
            case PROP_ID_maxExecutionCount:
               return getMaxExecutionCount();
        
            case PROP_ID_minScheduleTime:
               return getMinScheduleTime();
        
            case PROP_ID_maxScheduleTime:
               return getMaxScheduleTime();
        
            case PROP_ID_misfireThreshold:
               return getMisfireThreshold();
        
            case PROP_ID_maxFailedCount:
               return getMaxFailedCount();
        
            case PROP_ID_isUseDefaultCalendar:
               return getIsUseDefaultCalendar();
        
            case PROP_ID_pauseCalendars:
               return getPauseCalendars();
        
            case PROP_ID_schedulerGroup:
               return getSchedulerGroup();
        
            case PROP_ID_schedulerId:
               return getSchedulerId();
        
            case PROP_ID_schedulerEpoch:
               return getSchedulerEpoch();
        
            case PROP_ID_schedulerLoadTime:
               return getSchedulerLoadTime();
        
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
        
            case PROP_ID_displayName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_displayName));
               }
               setDisplayName(typedValue);
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
        
            case PROP_ID_jobGroup:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_jobGroup));
               }
               setJobGroup(typedValue);
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
        
            case PROP_ID_jobInvoker:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_jobInvoker));
               }
               setJobInvoker(typedValue);
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
        
            case PROP_ID_status:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_status));
               }
               setStatus(typedValue);
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
        
            case PROP_ID_repeatInterval:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_repeatInterval));
               }
               setRepeatInterval(typedValue);
               break;
            }
        
            case PROP_ID_isFixedDelay:{
               java.lang.Byte typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toByte(value,
                       err-> newTypeConversionError(PROP_NAME_isFixedDelay));
               }
               setIsFixedDelay(typedValue);
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
        
            case PROP_ID_misfireThreshold:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_misfireThreshold));
               }
               setMisfireThreshold(typedValue);
               break;
            }
        
            case PROP_ID_maxFailedCount:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_maxFailedCount));
               }
               setMaxFailedCount(typedValue);
               break;
            }
        
            case PROP_ID_isUseDefaultCalendar:{
               java.lang.Byte typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toByte(value,
                       err-> newTypeConversionError(PROP_NAME_isUseDefaultCalendar));
               }
               setIsUseDefaultCalendar(typedValue);
               break;
            }
        
            case PROP_ID_pauseCalendars:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_pauseCalendars));
               }
               setPauseCalendars(typedValue);
               break;
            }
        
            case PROP_ID_schedulerGroup:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_schedulerGroup));
               }
               setSchedulerGroup(typedValue);
               break;
            }
        
            case PROP_ID_schedulerId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_schedulerId));
               }
               setSchedulerId(typedValue);
               break;
            }
        
            case PROP_ID_schedulerEpoch:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_schedulerEpoch));
               }
               setSchedulerEpoch(typedValue);
               break;
            }
        
            case PROP_ID_schedulerLoadTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_schedulerLoadTime));
               }
               setSchedulerLoadTime(typedValue);
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
        
            case PROP_ID_displayName:{
               onInitProp(propId);
               this._displayName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_jobName:{
               onInitProp(propId);
               this._jobName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_jobGroup:{
               onInitProp(propId);
               this._jobGroup = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_jobParams:{
               onInitProp(propId);
               this._jobParams = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_jobInvoker:{
               onInitProp(propId);
               this._jobInvoker = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_description:{
               onInitProp(propId);
               this._description = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_status:{
               onInitProp(propId);
               this._status = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_cronExpr:{
               onInitProp(propId);
               this._cronExpr = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_repeatInterval:{
               onInitProp(propId);
               this._repeatInterval = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_isFixedDelay:{
               onInitProp(propId);
               this._isFixedDelay = (java.lang.Byte)value;
               
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
        
            case PROP_ID_misfireThreshold:{
               onInitProp(propId);
               this._misfireThreshold = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_maxFailedCount:{
               onInitProp(propId);
               this._maxFailedCount = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_isUseDefaultCalendar:{
               onInitProp(propId);
               this._isUseDefaultCalendar = (java.lang.Byte)value;
               
               break;
            }
        
            case PROP_ID_pauseCalendars:{
               onInitProp(propId);
               this._pauseCalendars = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_schedulerGroup:{
               onInitProp(propId);
               this._schedulerGroup = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_schedulerId:{
               onInitProp(propId);
               this._schedulerId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_schedulerEpoch:{
               onInitProp(propId);
               this._schedulerEpoch = (java.lang.Long)value;
               
               break;
            }
        
            case PROP_ID_schedulerLoadTime:{
               onInitProp(propId);
               this._schedulerLoadTime = (java.sql.Timestamp)value;
               
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
    public final java.lang.String getSid(){
         onPropGet(PROP_ID_sid);
         return _sid;
    }

    /**
     * SID: SID
     */
    public final void setSid(java.lang.String value){
        if(onPropSet(PROP_ID_sid,value)){
            this._sid = value;
            internalClearRefs(PROP_ID_sid);
            orm_id();
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
     * 任务名: JOB_NAME
     */
    public final java.lang.String getJobName(){
         onPropGet(PROP_ID_jobName);
         return _jobName;
    }

    /**
     * 任务名: JOB_NAME
     */
    public final void setJobName(java.lang.String value){
        if(onPropSet(PROP_ID_jobName,value)){
            this._jobName = value;
            internalClearRefs(PROP_ID_jobName);
            
        }
    }
    
    /**
     * 任务组: JOB_GROUP
     */
    public final java.lang.String getJobGroup(){
         onPropGet(PROP_ID_jobGroup);
         return _jobGroup;
    }

    /**
     * 任务组: JOB_GROUP
     */
    public final void setJobGroup(java.lang.String value){
        if(onPropSet(PROP_ID_jobGroup,value)){
            this._jobGroup = value;
            internalClearRefs(PROP_ID_jobGroup);
            
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
     * 任务执行函数: JOB_INVOKER
     */
    public final java.lang.String getJobInvoker(){
         onPropGet(PROP_ID_jobInvoker);
         return _jobInvoker;
    }

    /**
     * 任务执行函数: JOB_INVOKER
     */
    public final void setJobInvoker(java.lang.String value){
        if(onPropSet(PROP_ID_jobInvoker,value)){
            this._jobInvoker = value;
            internalClearRefs(PROP_ID_jobInvoker);
            
        }
    }
    
    /**
     * 任务描述: DESCRIPTION
     */
    public final java.lang.String getDescription(){
         onPropGet(PROP_ID_description);
         return _description;
    }

    /**
     * 任务描述: DESCRIPTION
     */
    public final void setDescription(java.lang.String value){
        if(onPropSet(PROP_ID_description,value)){
            this._description = value;
            internalClearRefs(PROP_ID_description);
            
        }
    }
    
    /**
     * 任务状态: STATUS
     */
    public final java.lang.Integer getStatus(){
         onPropGet(PROP_ID_status);
         return _status;
    }

    /**
     * 任务状态: STATUS
     */
    public final void setStatus(java.lang.Integer value){
        if(onPropSet(PROP_ID_status,value)){
            this._status = value;
            internalClearRefs(PROP_ID_status);
            
        }
    }
    
    /**
     * 定时表达式: CRON_EXPR
     */
    public final java.lang.String getCronExpr(){
         onPropGet(PROP_ID_cronExpr);
         return _cronExpr;
    }

    /**
     * 定时表达式: CRON_EXPR
     */
    public final void setCronExpr(java.lang.String value){
        if(onPropSet(PROP_ID_cronExpr,value)){
            this._cronExpr = value;
            internalClearRefs(PROP_ID_cronExpr);
            
        }
    }
    
    /**
     * 定时执行间隔: REPEAT_INTERVAL
     */
    public final java.lang.Integer getRepeatInterval(){
         onPropGet(PROP_ID_repeatInterval);
         return _repeatInterval;
    }

    /**
     * 定时执行间隔: REPEAT_INTERVAL
     */
    public final void setRepeatInterval(java.lang.Integer value){
        if(onPropSet(PROP_ID_repeatInterval,value)){
            this._repeatInterval = value;
            internalClearRefs(PROP_ID_repeatInterval);
            
        }
    }
    
    /**
     * 是否固定延时: IS_FIXED_DELAY
     */
    public final java.lang.Byte getIsFixedDelay(){
         onPropGet(PROP_ID_isFixedDelay);
         return _isFixedDelay;
    }

    /**
     * 是否固定延时: IS_FIXED_DELAY
     */
    public final void setIsFixedDelay(java.lang.Byte value){
        if(onPropSet(PROP_ID_isFixedDelay,value)){
            this._isFixedDelay = value;
            internalClearRefs(PROP_ID_isFixedDelay);
            
        }
    }
    
    /**
     * 最多执行次数: MAX_EXECUTION_COUNT
     */
    public final java.lang.Integer getMaxExecutionCount(){
         onPropGet(PROP_ID_maxExecutionCount);
         return _maxExecutionCount;
    }

    /**
     * 最多执行次数: MAX_EXECUTION_COUNT
     */
    public final void setMaxExecutionCount(java.lang.Integer value){
        if(onPropSet(PROP_ID_maxExecutionCount,value)){
            this._maxExecutionCount = value;
            internalClearRefs(PROP_ID_maxExecutionCount);
            
        }
    }
    
    /**
     * 最近调度时间: MIN_SCHEDULE_TIME
     */
    public final java.sql.Timestamp getMinScheduleTime(){
         onPropGet(PROP_ID_minScheduleTime);
         return _minScheduleTime;
    }

    /**
     * 最近调度时间: MIN_SCHEDULE_TIME
     */
    public final void setMinScheduleTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_minScheduleTime,value)){
            this._minScheduleTime = value;
            internalClearRefs(PROP_ID_minScheduleTime);
            
        }
    }
    
    /**
     * 最大调度时间: MAX_SCHEDULE_TIME
     */
    public final java.sql.Timestamp getMaxScheduleTime(){
         onPropGet(PROP_ID_maxScheduleTime);
         return _maxScheduleTime;
    }

    /**
     * 最大调度时间: MAX_SCHEDULE_TIME
     */
    public final void setMaxScheduleTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_maxScheduleTime,value)){
            this._maxScheduleTime = value;
            internalClearRefs(PROP_ID_maxScheduleTime);
            
        }
    }
    
    /**
     * 超时阈值: MISFIRE_THRESHOLD
     */
    public final java.lang.Integer getMisfireThreshold(){
         onPropGet(PROP_ID_misfireThreshold);
         return _misfireThreshold;
    }

    /**
     * 超时阈值: MISFIRE_THRESHOLD
     */
    public final void setMisfireThreshold(java.lang.Integer value){
        if(onPropSet(PROP_ID_misfireThreshold,value)){
            this._misfireThreshold = value;
            internalClearRefs(PROP_ID_misfireThreshold);
            
        }
    }
    
    /**
     * 最大允许失败次数: MAX_FAILED_COUNT
     */
    public final java.lang.Integer getMaxFailedCount(){
         onPropGet(PROP_ID_maxFailedCount);
         return _maxFailedCount;
    }

    /**
     * 最大允许失败次数: MAX_FAILED_COUNT
     */
    public final void setMaxFailedCount(java.lang.Integer value){
        if(onPropSet(PROP_ID_maxFailedCount,value)){
            this._maxFailedCount = value;
            internalClearRefs(PROP_ID_maxFailedCount);
            
        }
    }
    
    /**
     * 使用系统内置日历: IS_USE_DEFAULT_CALENDAR
     */
    public final java.lang.Byte getIsUseDefaultCalendar(){
         onPropGet(PROP_ID_isUseDefaultCalendar);
         return _isUseDefaultCalendar;
    }

    /**
     * 使用系统内置日历: IS_USE_DEFAULT_CALENDAR
     */
    public final void setIsUseDefaultCalendar(java.lang.Byte value){
        if(onPropSet(PROP_ID_isUseDefaultCalendar,value)){
            this._isUseDefaultCalendar = value;
            internalClearRefs(PROP_ID_isUseDefaultCalendar);
            
        }
    }
    
    /**
     * 暂停日历: PAUSE_CALENDARS
     */
    public final java.lang.String getPauseCalendars(){
         onPropGet(PROP_ID_pauseCalendars);
         return _pauseCalendars;
    }

    /**
     * 暂停日历: PAUSE_CALENDARS
     */
    public final void setPauseCalendars(java.lang.String value){
        if(onPropSet(PROP_ID_pauseCalendars,value)){
            this._pauseCalendars = value;
            internalClearRefs(PROP_ID_pauseCalendars);
            
        }
    }
    
    /**
     * 调度器分组: SCHEDULER_GROUP
     */
    public final java.lang.String getSchedulerGroup(){
         onPropGet(PROP_ID_schedulerGroup);
         return _schedulerGroup;
    }

    /**
     * 调度器分组: SCHEDULER_GROUP
     */
    public final void setSchedulerGroup(java.lang.String value){
        if(onPropSet(PROP_ID_schedulerGroup,value)){
            this._schedulerGroup = value;
            internalClearRefs(PROP_ID_schedulerGroup);
            
        }
    }
    
    /**
     * 调度器ID: SCHEDULER_ID
     */
    public final java.lang.String getSchedulerId(){
         onPropGet(PROP_ID_schedulerId);
         return _schedulerId;
    }

    /**
     * 调度器ID: SCHEDULER_ID
     */
    public final void setSchedulerId(java.lang.String value){
        if(onPropSet(PROP_ID_schedulerId,value)){
            this._schedulerId = value;
            internalClearRefs(PROP_ID_schedulerId);
            
        }
    }
    
    /**
     * 调度器世代: SCHEDULER_EPOCH
     */
    public final java.lang.Long getSchedulerEpoch(){
         onPropGet(PROP_ID_schedulerEpoch);
         return _schedulerEpoch;
    }

    /**
     * 调度器世代: SCHEDULER_EPOCH
     */
    public final void setSchedulerEpoch(java.lang.Long value){
        if(onPropSet(PROP_ID_schedulerEpoch,value)){
            this._schedulerEpoch = value;
            internalClearRefs(PROP_ID_schedulerEpoch);
            
        }
    }
    
    /**
     * 调度器加载时间: SCHEDULER_LOAD_TIME
     */
    public final java.sql.Timestamp getSchedulerLoadTime(){
         onPropGet(PROP_ID_schedulerLoadTime);
         return _schedulerLoadTime;
    }

    /**
     * 调度器加载时间: SCHEDULER_LOAD_TIME
     */
    public final void setSchedulerLoadTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_schedulerLoadTime,value)){
            this._schedulerLoadTime = value;
            internalClearRefs(PROP_ID_schedulerLoadTime);
            
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

}
// resume CPD analysis - CPD-ON
