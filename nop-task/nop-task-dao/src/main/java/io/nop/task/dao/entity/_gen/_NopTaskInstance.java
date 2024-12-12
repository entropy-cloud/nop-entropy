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

import io.nop.task.dao.entity.NopTaskInstance;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  逻辑流实例: nop_task_instance
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopTaskInstance extends DynamicOrmEntity{
    
    /* 主键: TASK_INSTANCE_ID VARCHAR */
    public static final String PROP_NAME_taskInstanceId = "taskInstanceId";
    public static final int PROP_ID_taskInstanceId = 1;
    
    /* 逻辑流名称: TASK_NAME VARCHAR */
    public static final String PROP_NAME_taskName = "taskName";
    public static final int PROP_ID_taskName = 2;
    
    /* 逻辑流版本: TASK_VERSION BIGINT */
    public static final String PROP_NAME_taskVersion = "taskVersion";
    public static final int PROP_ID_taskVersion = 3;
    
    /* 逻辑流参数: TASK_INPUTS VARCHAR */
    public static final String PROP_NAME_taskInputs = "taskInputs";
    public static final int PROP_ID_taskInputs = 4;
    
    /* 逻辑流分组: TASK_GROUP VARCHAR */
    public static final String PROP_NAME_taskGroup = "taskGroup";
    public static final int PROP_ID_taskGroup = 5;
    
    /* 状态: STATUS INTEGER */
    public static final String PROP_NAME_status = "status";
    public static final int PROP_ID_status = 6;
    
    /* 启动时间: START_TIME TIMESTAMP */
    public static final String PROP_NAME_startTime = "startTime";
    public static final int PROP_ID_startTime = 7;
    
    /* 结束时间: END_TIME TIMESTAMP */
    public static final String PROP_NAME_endTime = "endTime";
    public static final int PROP_ID_endTime = 8;
    
    /* 完成时限: DUE_TIME TIMESTAMP */
    public static final String PROP_NAME_dueTime = "dueTime";
    public static final int PROP_ID_dueTime = 9;
    
    /* 业务唯一键: BIZ_KEY VARCHAR */
    public static final String PROP_NAME_bizKey = "bizKey";
    public static final int PROP_ID_bizKey = 10;
    
    /* 业务对象名: BIZ_OBJ_NAME VARCHAR */
    public static final String PROP_NAME_bizObjName = "bizObjName";
    public static final int PROP_ID_bizObjName = 11;
    
    /* 业务对象ID: BIZ_OBJ_ID VARCHAR */
    public static final String PROP_NAME_bizObjId = "bizObjId";
    public static final int PROP_ID_bizObjId = 12;
    
    /* 父流程名称: PARENT_TASK_NAME VARCHAR */
    public static final String PROP_NAME_parentTaskName = "parentTaskName";
    public static final int PROP_ID_parentTaskName = 13;
    
    /* 父流程版本: PARENT_TASK_VERSION BIGINT */
    public static final String PROP_NAME_parentTaskVersion = "parentTaskVersion";
    public static final int PROP_ID_parentTaskVersion = 14;
    
    /* 父流程ID: PARENT_TASK_ID VARCHAR */
    public static final String PROP_NAME_parentTaskId = "parentTaskId";
    public static final int PROP_ID_parentTaskId = 15;
    
    /* 父流程步骤ID: PARENT_STEP_ID VARCHAR */
    public static final String PROP_NAME_parentStepId = "parentStepId";
    public static final int PROP_ID_parentStepId = 16;
    
    /* 启动人ID: STARTER_ID VARCHAR */
    public static final String PROP_NAME_starterId = "starterId";
    public static final int PROP_ID_starterId = 17;
    
    /* 启动人: STARTER_NAME VARCHAR */
    public static final String PROP_NAME_starterName = "starterName";
    public static final int PROP_ID_starterName = 18;
    
    /* 启动人单位ID: STARTER_DEPT_ID VARCHAR */
    public static final String PROP_NAME_starterDeptId = "starterDeptId";
    public static final int PROP_ID_starterDeptId = 19;
    
    /* 管理者类型: MANAGER_TYPE VARCHAR */
    public static final String PROP_NAME_managerType = "managerType";
    public static final int PROP_ID_managerType = 20;
    
    /* 管理者单位ID: MANAGER_DEPT_ID VARCHAR */
    public static final String PROP_NAME_managerDeptId = "managerDeptId";
    public static final int PROP_ID_managerDeptId = 21;
    
    /* 管理者: MANAGER_NAME VARCHAR */
    public static final String PROP_NAME_managerName = "managerName";
    public static final int PROP_ID_managerName = 22;
    
    /* 管理者ID: MANAGER_ID VARCHAR */
    public static final String PROP_NAME_managerId = "managerId";
    public static final int PROP_ID_managerId = 23;
    
    /* 优先级: PRIORITY INTEGER */
    public static final String PROP_NAME_priority = "priority";
    public static final int PROP_ID_priority = 24;
    
    /* 信号集合: SIGNAL_TEXT VARCHAR */
    public static final String PROP_NAME_signalText = "signalText";
    public static final int PROP_ID_signalText = 25;
    
    /* 标签: TAG_TEXT VARCHAR */
    public static final String PROP_NAME_tagText = "tagText";
    public static final int PROP_ID_tagText = 26;
    
    /* Job ID: JOB_INSTANCE_ID VARCHAR */
    public static final String PROP_NAME_jobInstanceId = "jobInstanceId";
    public static final int PROP_ID_jobInstanceId = 27;
    
    /* 错误码: ERR_CODE VARCHAR */
    public static final String PROP_NAME_errCode = "errCode";
    public static final int PROP_ID_errCode = 28;
    
    /* 错误消息: ERR_MSG VARCHAR */
    public static final String PROP_NAME_errMsg = "errMsg";
    public static final int PROP_ID_errMsg = 29;
    
    /* Worker ID: WORKER_ID VARCHAR */
    public static final String PROP_NAME_workerId = "workerId";
    public static final int PROP_ID_workerId = 30;
    
    /* 数据版本: VERSION INTEGER */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 31;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 32;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 33;
    
    /* 修改人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 34;
    
    /* 修改时间: UPDATE_TIME TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 35;
    
    /* 备注: REMARK VARCHAR */
    public static final String PROP_NAME_remark = "remark";
    public static final int PROP_ID_remark = 37;
    

    private static int _PROP_ID_BOUND = 38;

    
    /* relation: 父流程 */
    public static final String PROP_NAME_parentTaskInstance = "parentTaskInstance";
    
    /* relation:  */
    public static final String PROP_NAME_steps = "steps";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_taskInstanceId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_taskInstanceId};

    private static final String[] PROP_ID_TO_NAME = new String[38];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_taskInstanceId] = PROP_NAME_taskInstanceId;
          PROP_NAME_TO_ID.put(PROP_NAME_taskInstanceId, PROP_ID_taskInstanceId);
      
          PROP_ID_TO_NAME[PROP_ID_taskName] = PROP_NAME_taskName;
          PROP_NAME_TO_ID.put(PROP_NAME_taskName, PROP_ID_taskName);
      
          PROP_ID_TO_NAME[PROP_ID_taskVersion] = PROP_NAME_taskVersion;
          PROP_NAME_TO_ID.put(PROP_NAME_taskVersion, PROP_ID_taskVersion);
      
          PROP_ID_TO_NAME[PROP_ID_taskInputs] = PROP_NAME_taskInputs;
          PROP_NAME_TO_ID.put(PROP_NAME_taskInputs, PROP_ID_taskInputs);
      
          PROP_ID_TO_NAME[PROP_ID_taskGroup] = PROP_NAME_taskGroup;
          PROP_NAME_TO_ID.put(PROP_NAME_taskGroup, PROP_ID_taskGroup);
      
          PROP_ID_TO_NAME[PROP_ID_status] = PROP_NAME_status;
          PROP_NAME_TO_ID.put(PROP_NAME_status, PROP_ID_status);
      
          PROP_ID_TO_NAME[PROP_ID_startTime] = PROP_NAME_startTime;
          PROP_NAME_TO_ID.put(PROP_NAME_startTime, PROP_ID_startTime);
      
          PROP_ID_TO_NAME[PROP_ID_endTime] = PROP_NAME_endTime;
          PROP_NAME_TO_ID.put(PROP_NAME_endTime, PROP_ID_endTime);
      
          PROP_ID_TO_NAME[PROP_ID_dueTime] = PROP_NAME_dueTime;
          PROP_NAME_TO_ID.put(PROP_NAME_dueTime, PROP_ID_dueTime);
      
          PROP_ID_TO_NAME[PROP_ID_bizKey] = PROP_NAME_bizKey;
          PROP_NAME_TO_ID.put(PROP_NAME_bizKey, PROP_ID_bizKey);
      
          PROP_ID_TO_NAME[PROP_ID_bizObjName] = PROP_NAME_bizObjName;
          PROP_NAME_TO_ID.put(PROP_NAME_bizObjName, PROP_ID_bizObjName);
      
          PROP_ID_TO_NAME[PROP_ID_bizObjId] = PROP_NAME_bizObjId;
          PROP_NAME_TO_ID.put(PROP_NAME_bizObjId, PROP_ID_bizObjId);
      
          PROP_ID_TO_NAME[PROP_ID_parentTaskName] = PROP_NAME_parentTaskName;
          PROP_NAME_TO_ID.put(PROP_NAME_parentTaskName, PROP_ID_parentTaskName);
      
          PROP_ID_TO_NAME[PROP_ID_parentTaskVersion] = PROP_NAME_parentTaskVersion;
          PROP_NAME_TO_ID.put(PROP_NAME_parentTaskVersion, PROP_ID_parentTaskVersion);
      
          PROP_ID_TO_NAME[PROP_ID_parentTaskId] = PROP_NAME_parentTaskId;
          PROP_NAME_TO_ID.put(PROP_NAME_parentTaskId, PROP_ID_parentTaskId);
      
          PROP_ID_TO_NAME[PROP_ID_parentStepId] = PROP_NAME_parentStepId;
          PROP_NAME_TO_ID.put(PROP_NAME_parentStepId, PROP_ID_parentStepId);
      
          PROP_ID_TO_NAME[PROP_ID_starterId] = PROP_NAME_starterId;
          PROP_NAME_TO_ID.put(PROP_NAME_starterId, PROP_ID_starterId);
      
          PROP_ID_TO_NAME[PROP_ID_starterName] = PROP_NAME_starterName;
          PROP_NAME_TO_ID.put(PROP_NAME_starterName, PROP_ID_starterName);
      
          PROP_ID_TO_NAME[PROP_ID_starterDeptId] = PROP_NAME_starterDeptId;
          PROP_NAME_TO_ID.put(PROP_NAME_starterDeptId, PROP_ID_starterDeptId);
      
          PROP_ID_TO_NAME[PROP_ID_managerType] = PROP_NAME_managerType;
          PROP_NAME_TO_ID.put(PROP_NAME_managerType, PROP_ID_managerType);
      
          PROP_ID_TO_NAME[PROP_ID_managerDeptId] = PROP_NAME_managerDeptId;
          PROP_NAME_TO_ID.put(PROP_NAME_managerDeptId, PROP_ID_managerDeptId);
      
          PROP_ID_TO_NAME[PROP_ID_managerName] = PROP_NAME_managerName;
          PROP_NAME_TO_ID.put(PROP_NAME_managerName, PROP_ID_managerName);
      
          PROP_ID_TO_NAME[PROP_ID_managerId] = PROP_NAME_managerId;
          PROP_NAME_TO_ID.put(PROP_NAME_managerId, PROP_ID_managerId);
      
          PROP_ID_TO_NAME[PROP_ID_priority] = PROP_NAME_priority;
          PROP_NAME_TO_ID.put(PROP_NAME_priority, PROP_ID_priority);
      
          PROP_ID_TO_NAME[PROP_ID_signalText] = PROP_NAME_signalText;
          PROP_NAME_TO_ID.put(PROP_NAME_signalText, PROP_ID_signalText);
      
          PROP_ID_TO_NAME[PROP_ID_tagText] = PROP_NAME_tagText;
          PROP_NAME_TO_ID.put(PROP_NAME_tagText, PROP_ID_tagText);
      
          PROP_ID_TO_NAME[PROP_ID_jobInstanceId] = PROP_NAME_jobInstanceId;
          PROP_NAME_TO_ID.put(PROP_NAME_jobInstanceId, PROP_ID_jobInstanceId);
      
          PROP_ID_TO_NAME[PROP_ID_errCode] = PROP_NAME_errCode;
          PROP_NAME_TO_ID.put(PROP_NAME_errCode, PROP_ID_errCode);
      
          PROP_ID_TO_NAME[PROP_ID_errMsg] = PROP_NAME_errMsg;
          PROP_NAME_TO_ID.put(PROP_NAME_errMsg, PROP_ID_errMsg);
      
          PROP_ID_TO_NAME[PROP_ID_workerId] = PROP_NAME_workerId;
          PROP_NAME_TO_ID.put(PROP_NAME_workerId, PROP_ID_workerId);
      
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

    
    /* 主键: TASK_INSTANCE_ID */
    private java.lang.String _taskInstanceId;
    
    /* 逻辑流名称: TASK_NAME */
    private java.lang.String _taskName;
    
    /* 逻辑流版本: TASK_VERSION */
    private java.lang.Long _taskVersion;
    
    /* 逻辑流参数: TASK_INPUTS */
    private java.lang.String _taskInputs;
    
    /* 逻辑流分组: TASK_GROUP */
    private java.lang.String _taskGroup;
    
    /* 状态: STATUS */
    private java.lang.Integer _status;
    
    /* 启动时间: START_TIME */
    private java.sql.Timestamp _startTime;
    
    /* 结束时间: END_TIME */
    private java.sql.Timestamp _endTime;
    
    /* 完成时限: DUE_TIME */
    private java.sql.Timestamp _dueTime;
    
    /* 业务唯一键: BIZ_KEY */
    private java.lang.String _bizKey;
    
    /* 业务对象名: BIZ_OBJ_NAME */
    private java.lang.String _bizObjName;
    
    /* 业务对象ID: BIZ_OBJ_ID */
    private java.lang.String _bizObjId;
    
    /* 父流程名称: PARENT_TASK_NAME */
    private java.lang.String _parentTaskName;
    
    /* 父流程版本: PARENT_TASK_VERSION */
    private java.lang.Long _parentTaskVersion;
    
    /* 父流程ID: PARENT_TASK_ID */
    private java.lang.String _parentTaskId;
    
    /* 父流程步骤ID: PARENT_STEP_ID */
    private java.lang.String _parentStepId;
    
    /* 启动人ID: STARTER_ID */
    private java.lang.String _starterId;
    
    /* 启动人: STARTER_NAME */
    private java.lang.String _starterName;
    
    /* 启动人单位ID: STARTER_DEPT_ID */
    private java.lang.String _starterDeptId;
    
    /* 管理者类型: MANAGER_TYPE */
    private java.lang.String _managerType;
    
    /* 管理者单位ID: MANAGER_DEPT_ID */
    private java.lang.String _managerDeptId;
    
    /* 管理者: MANAGER_NAME */
    private java.lang.String _managerName;
    
    /* 管理者ID: MANAGER_ID */
    private java.lang.String _managerId;
    
    /* 优先级: PRIORITY */
    private java.lang.Integer _priority;
    
    /* 信号集合: SIGNAL_TEXT */
    private java.lang.String _signalText;
    
    /* 标签: TAG_TEXT */
    private java.lang.String _tagText;
    
    /* Job ID: JOB_INSTANCE_ID */
    private java.lang.String _jobInstanceId;
    
    /* 错误码: ERR_CODE */
    private java.lang.String _errCode;
    
    /* 错误消息: ERR_MSG */
    private java.lang.String _errMsg;
    
    /* Worker ID: WORKER_ID */
    private java.lang.String _workerId;
    
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
    

    public _NopTaskInstance(){
        // for debug
    }

    protected NopTaskInstance newInstance(){
        NopTaskInstance entity = new NopTaskInstance();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopTaskInstance cloneInstance() {
        NopTaskInstance entity = newInstance();
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
      return "io.nop.task.dao.entity.NopTaskInstance";
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
    
        return buildSimpleId(PROP_ID_taskInstanceId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_taskInstanceId;
          
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
        
            case PROP_ID_taskInstanceId:
               return getTaskInstanceId();
        
            case PROP_ID_taskName:
               return getTaskName();
        
            case PROP_ID_taskVersion:
               return getTaskVersion();
        
            case PROP_ID_taskInputs:
               return getTaskInputs();
        
            case PROP_ID_taskGroup:
               return getTaskGroup();
        
            case PROP_ID_status:
               return getStatus();
        
            case PROP_ID_startTime:
               return getStartTime();
        
            case PROP_ID_endTime:
               return getEndTime();
        
            case PROP_ID_dueTime:
               return getDueTime();
        
            case PROP_ID_bizKey:
               return getBizKey();
        
            case PROP_ID_bizObjName:
               return getBizObjName();
        
            case PROP_ID_bizObjId:
               return getBizObjId();
        
            case PROP_ID_parentTaskName:
               return getParentTaskName();
        
            case PROP_ID_parentTaskVersion:
               return getParentTaskVersion();
        
            case PROP_ID_parentTaskId:
               return getParentTaskId();
        
            case PROP_ID_parentStepId:
               return getParentStepId();
        
            case PROP_ID_starterId:
               return getStarterId();
        
            case PROP_ID_starterName:
               return getStarterName();
        
            case PROP_ID_starterDeptId:
               return getStarterDeptId();
        
            case PROP_ID_managerType:
               return getManagerType();
        
            case PROP_ID_managerDeptId:
               return getManagerDeptId();
        
            case PROP_ID_managerName:
               return getManagerName();
        
            case PROP_ID_managerId:
               return getManagerId();
        
            case PROP_ID_priority:
               return getPriority();
        
            case PROP_ID_signalText:
               return getSignalText();
        
            case PROP_ID_tagText:
               return getTagText();
        
            case PROP_ID_jobInstanceId:
               return getJobInstanceId();
        
            case PROP_ID_errCode:
               return getErrCode();
        
            case PROP_ID_errMsg:
               return getErrMsg();
        
            case PROP_ID_workerId:
               return getWorkerId();
        
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
        
            case PROP_ID_taskInstanceId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_taskInstanceId));
               }
               setTaskInstanceId(typedValue);
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
        
            case PROP_ID_taskVersion:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_taskVersion));
               }
               setTaskVersion(typedValue);
               break;
            }
        
            case PROP_ID_taskInputs:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_taskInputs));
               }
               setTaskInputs(typedValue);
               break;
            }
        
            case PROP_ID_taskGroup:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_taskGroup));
               }
               setTaskGroup(typedValue);
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
        
            case PROP_ID_dueTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_dueTime));
               }
               setDueTime(typedValue);
               break;
            }
        
            case PROP_ID_bizKey:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_bizKey));
               }
               setBizKey(typedValue);
               break;
            }
        
            case PROP_ID_bizObjName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_bizObjName));
               }
               setBizObjName(typedValue);
               break;
            }
        
            case PROP_ID_bizObjId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_bizObjId));
               }
               setBizObjId(typedValue);
               break;
            }
        
            case PROP_ID_parentTaskName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_parentTaskName));
               }
               setParentTaskName(typedValue);
               break;
            }
        
            case PROP_ID_parentTaskVersion:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_parentTaskVersion));
               }
               setParentTaskVersion(typedValue);
               break;
            }
        
            case PROP_ID_parentTaskId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_parentTaskId));
               }
               setParentTaskId(typedValue);
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
        
            case PROP_ID_starterId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_starterId));
               }
               setStarterId(typedValue);
               break;
            }
        
            case PROP_ID_starterName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_starterName));
               }
               setStarterName(typedValue);
               break;
            }
        
            case PROP_ID_starterDeptId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_starterDeptId));
               }
               setStarterDeptId(typedValue);
               break;
            }
        
            case PROP_ID_managerType:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_managerType));
               }
               setManagerType(typedValue);
               break;
            }
        
            case PROP_ID_managerDeptId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_managerDeptId));
               }
               setManagerDeptId(typedValue);
               break;
            }
        
            case PROP_ID_managerName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_managerName));
               }
               setManagerName(typedValue);
               break;
            }
        
            case PROP_ID_managerId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_managerId));
               }
               setManagerId(typedValue);
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
        
            case PROP_ID_signalText:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_signalText));
               }
               setSignalText(typedValue);
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
        
            case PROP_ID_jobInstanceId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_jobInstanceId));
               }
               setJobInstanceId(typedValue);
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
        
            case PROP_ID_workerId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_workerId));
               }
               setWorkerId(typedValue);
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
        
            case PROP_ID_taskInstanceId:{
               onInitProp(propId);
               this._taskInstanceId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_taskName:{
               onInitProp(propId);
               this._taskName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_taskVersion:{
               onInitProp(propId);
               this._taskVersion = (java.lang.Long)value;
               
               break;
            }
        
            case PROP_ID_taskInputs:{
               onInitProp(propId);
               this._taskInputs = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_taskGroup:{
               onInitProp(propId);
               this._taskGroup = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_status:{
               onInitProp(propId);
               this._status = (java.lang.Integer)value;
               
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
        
            case PROP_ID_dueTime:{
               onInitProp(propId);
               this._dueTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_bizKey:{
               onInitProp(propId);
               this._bizKey = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_bizObjName:{
               onInitProp(propId);
               this._bizObjName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_bizObjId:{
               onInitProp(propId);
               this._bizObjId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_parentTaskName:{
               onInitProp(propId);
               this._parentTaskName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_parentTaskVersion:{
               onInitProp(propId);
               this._parentTaskVersion = (java.lang.Long)value;
               
               break;
            }
        
            case PROP_ID_parentTaskId:{
               onInitProp(propId);
               this._parentTaskId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_parentStepId:{
               onInitProp(propId);
               this._parentStepId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_starterId:{
               onInitProp(propId);
               this._starterId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_starterName:{
               onInitProp(propId);
               this._starterName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_starterDeptId:{
               onInitProp(propId);
               this._starterDeptId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_managerType:{
               onInitProp(propId);
               this._managerType = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_managerDeptId:{
               onInitProp(propId);
               this._managerDeptId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_managerName:{
               onInitProp(propId);
               this._managerName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_managerId:{
               onInitProp(propId);
               this._managerId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_priority:{
               onInitProp(propId);
               this._priority = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_signalText:{
               onInitProp(propId);
               this._signalText = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_tagText:{
               onInitProp(propId);
               this._tagText = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_jobInstanceId:{
               onInitProp(propId);
               this._jobInstanceId = (java.lang.String)value;
               
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
        
            case PROP_ID_workerId:{
               onInitProp(propId);
               this._workerId = (java.lang.String)value;
               
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
     * 主键: TASK_INSTANCE_ID
     */
    public java.lang.String getTaskInstanceId(){
         onPropGet(PROP_ID_taskInstanceId);
         return _taskInstanceId;
    }

    /**
     * 主键: TASK_INSTANCE_ID
     */
    public void setTaskInstanceId(java.lang.String value){
        if(onPropSet(PROP_ID_taskInstanceId,value)){
            this._taskInstanceId = value;
            internalClearRefs(PROP_ID_taskInstanceId);
            orm_id();
        }
    }
    
    /**
     * 逻辑流名称: TASK_NAME
     */
    public java.lang.String getTaskName(){
         onPropGet(PROP_ID_taskName);
         return _taskName;
    }

    /**
     * 逻辑流名称: TASK_NAME
     */
    public void setTaskName(java.lang.String value){
        if(onPropSet(PROP_ID_taskName,value)){
            this._taskName = value;
            internalClearRefs(PROP_ID_taskName);
            
        }
    }
    
    /**
     * 逻辑流版本: TASK_VERSION
     */
    public java.lang.Long getTaskVersion(){
         onPropGet(PROP_ID_taskVersion);
         return _taskVersion;
    }

    /**
     * 逻辑流版本: TASK_VERSION
     */
    public void setTaskVersion(java.lang.Long value){
        if(onPropSet(PROP_ID_taskVersion,value)){
            this._taskVersion = value;
            internalClearRefs(PROP_ID_taskVersion);
            
        }
    }
    
    /**
     * 逻辑流参数: TASK_INPUTS
     */
    public java.lang.String getTaskInputs(){
         onPropGet(PROP_ID_taskInputs);
         return _taskInputs;
    }

    /**
     * 逻辑流参数: TASK_INPUTS
     */
    public void setTaskInputs(java.lang.String value){
        if(onPropSet(PROP_ID_taskInputs,value)){
            this._taskInputs = value;
            internalClearRefs(PROP_ID_taskInputs);
            
        }
    }
    
    /**
     * 逻辑流分组: TASK_GROUP
     */
    public java.lang.String getTaskGroup(){
         onPropGet(PROP_ID_taskGroup);
         return _taskGroup;
    }

    /**
     * 逻辑流分组: TASK_GROUP
     */
    public void setTaskGroup(java.lang.String value){
        if(onPropSet(PROP_ID_taskGroup,value)){
            this._taskGroup = value;
            internalClearRefs(PROP_ID_taskGroup);
            
        }
    }
    
    /**
     * 状态: STATUS
     */
    public java.lang.Integer getStatus(){
         onPropGet(PROP_ID_status);
         return _status;
    }

    /**
     * 状态: STATUS
     */
    public void setStatus(java.lang.Integer value){
        if(onPropSet(PROP_ID_status,value)){
            this._status = value;
            internalClearRefs(PROP_ID_status);
            
        }
    }
    
    /**
     * 启动时间: START_TIME
     */
    public java.sql.Timestamp getStartTime(){
         onPropGet(PROP_ID_startTime);
         return _startTime;
    }

    /**
     * 启动时间: START_TIME
     */
    public void setStartTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_startTime,value)){
            this._startTime = value;
            internalClearRefs(PROP_ID_startTime);
            
        }
    }
    
    /**
     * 结束时间: END_TIME
     */
    public java.sql.Timestamp getEndTime(){
         onPropGet(PROP_ID_endTime);
         return _endTime;
    }

    /**
     * 结束时间: END_TIME
     */
    public void setEndTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_endTime,value)){
            this._endTime = value;
            internalClearRefs(PROP_ID_endTime);
            
        }
    }
    
    /**
     * 完成时限: DUE_TIME
     */
    public java.sql.Timestamp getDueTime(){
         onPropGet(PROP_ID_dueTime);
         return _dueTime;
    }

    /**
     * 完成时限: DUE_TIME
     */
    public void setDueTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_dueTime,value)){
            this._dueTime = value;
            internalClearRefs(PROP_ID_dueTime);
            
        }
    }
    
    /**
     * 业务唯一键: BIZ_KEY
     */
    public java.lang.String getBizKey(){
         onPropGet(PROP_ID_bizKey);
         return _bizKey;
    }

    /**
     * 业务唯一键: BIZ_KEY
     */
    public void setBizKey(java.lang.String value){
        if(onPropSet(PROP_ID_bizKey,value)){
            this._bizKey = value;
            internalClearRefs(PROP_ID_bizKey);
            
        }
    }
    
    /**
     * 业务对象名: BIZ_OBJ_NAME
     */
    public java.lang.String getBizObjName(){
         onPropGet(PROP_ID_bizObjName);
         return _bizObjName;
    }

    /**
     * 业务对象名: BIZ_OBJ_NAME
     */
    public void setBizObjName(java.lang.String value){
        if(onPropSet(PROP_ID_bizObjName,value)){
            this._bizObjName = value;
            internalClearRefs(PROP_ID_bizObjName);
            
        }
    }
    
    /**
     * 业务对象ID: BIZ_OBJ_ID
     */
    public java.lang.String getBizObjId(){
         onPropGet(PROP_ID_bizObjId);
         return _bizObjId;
    }

    /**
     * 业务对象ID: BIZ_OBJ_ID
     */
    public void setBizObjId(java.lang.String value){
        if(onPropSet(PROP_ID_bizObjId,value)){
            this._bizObjId = value;
            internalClearRefs(PROP_ID_bizObjId);
            
        }
    }
    
    /**
     * 父流程名称: PARENT_TASK_NAME
     */
    public java.lang.String getParentTaskName(){
         onPropGet(PROP_ID_parentTaskName);
         return _parentTaskName;
    }

    /**
     * 父流程名称: PARENT_TASK_NAME
     */
    public void setParentTaskName(java.lang.String value){
        if(onPropSet(PROP_ID_parentTaskName,value)){
            this._parentTaskName = value;
            internalClearRefs(PROP_ID_parentTaskName);
            
        }
    }
    
    /**
     * 父流程版本: PARENT_TASK_VERSION
     */
    public java.lang.Long getParentTaskVersion(){
         onPropGet(PROP_ID_parentTaskVersion);
         return _parentTaskVersion;
    }

    /**
     * 父流程版本: PARENT_TASK_VERSION
     */
    public void setParentTaskVersion(java.lang.Long value){
        if(onPropSet(PROP_ID_parentTaskVersion,value)){
            this._parentTaskVersion = value;
            internalClearRefs(PROP_ID_parentTaskVersion);
            
        }
    }
    
    /**
     * 父流程ID: PARENT_TASK_ID
     */
    public java.lang.String getParentTaskId(){
         onPropGet(PROP_ID_parentTaskId);
         return _parentTaskId;
    }

    /**
     * 父流程ID: PARENT_TASK_ID
     */
    public void setParentTaskId(java.lang.String value){
        if(onPropSet(PROP_ID_parentTaskId,value)){
            this._parentTaskId = value;
            internalClearRefs(PROP_ID_parentTaskId);
            
        }
    }
    
    /**
     * 父流程步骤ID: PARENT_STEP_ID
     */
    public java.lang.String getParentStepId(){
         onPropGet(PROP_ID_parentStepId);
         return _parentStepId;
    }

    /**
     * 父流程步骤ID: PARENT_STEP_ID
     */
    public void setParentStepId(java.lang.String value){
        if(onPropSet(PROP_ID_parentStepId,value)){
            this._parentStepId = value;
            internalClearRefs(PROP_ID_parentStepId);
            
        }
    }
    
    /**
     * 启动人ID: STARTER_ID
     */
    public java.lang.String getStarterId(){
         onPropGet(PROP_ID_starterId);
         return _starterId;
    }

    /**
     * 启动人ID: STARTER_ID
     */
    public void setStarterId(java.lang.String value){
        if(onPropSet(PROP_ID_starterId,value)){
            this._starterId = value;
            internalClearRefs(PROP_ID_starterId);
            
        }
    }
    
    /**
     * 启动人: STARTER_NAME
     */
    public java.lang.String getStarterName(){
         onPropGet(PROP_ID_starterName);
         return _starterName;
    }

    /**
     * 启动人: STARTER_NAME
     */
    public void setStarterName(java.lang.String value){
        if(onPropSet(PROP_ID_starterName,value)){
            this._starterName = value;
            internalClearRefs(PROP_ID_starterName);
            
        }
    }
    
    /**
     * 启动人单位ID: STARTER_DEPT_ID
     */
    public java.lang.String getStarterDeptId(){
         onPropGet(PROP_ID_starterDeptId);
         return _starterDeptId;
    }

    /**
     * 启动人单位ID: STARTER_DEPT_ID
     */
    public void setStarterDeptId(java.lang.String value){
        if(onPropSet(PROP_ID_starterDeptId,value)){
            this._starterDeptId = value;
            internalClearRefs(PROP_ID_starterDeptId);
            
        }
    }
    
    /**
     * 管理者类型: MANAGER_TYPE
     */
    public java.lang.String getManagerType(){
         onPropGet(PROP_ID_managerType);
         return _managerType;
    }

    /**
     * 管理者类型: MANAGER_TYPE
     */
    public void setManagerType(java.lang.String value){
        if(onPropSet(PROP_ID_managerType,value)){
            this._managerType = value;
            internalClearRefs(PROP_ID_managerType);
            
        }
    }
    
    /**
     * 管理者单位ID: MANAGER_DEPT_ID
     */
    public java.lang.String getManagerDeptId(){
         onPropGet(PROP_ID_managerDeptId);
         return _managerDeptId;
    }

    /**
     * 管理者单位ID: MANAGER_DEPT_ID
     */
    public void setManagerDeptId(java.lang.String value){
        if(onPropSet(PROP_ID_managerDeptId,value)){
            this._managerDeptId = value;
            internalClearRefs(PROP_ID_managerDeptId);
            
        }
    }
    
    /**
     * 管理者: MANAGER_NAME
     */
    public java.lang.String getManagerName(){
         onPropGet(PROP_ID_managerName);
         return _managerName;
    }

    /**
     * 管理者: MANAGER_NAME
     */
    public void setManagerName(java.lang.String value){
        if(onPropSet(PROP_ID_managerName,value)){
            this._managerName = value;
            internalClearRefs(PROP_ID_managerName);
            
        }
    }
    
    /**
     * 管理者ID: MANAGER_ID
     */
    public java.lang.String getManagerId(){
         onPropGet(PROP_ID_managerId);
         return _managerId;
    }

    /**
     * 管理者ID: MANAGER_ID
     */
    public void setManagerId(java.lang.String value){
        if(onPropSet(PROP_ID_managerId,value)){
            this._managerId = value;
            internalClearRefs(PROP_ID_managerId);
            
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
     * 信号集合: SIGNAL_TEXT
     */
    public java.lang.String getSignalText(){
         onPropGet(PROP_ID_signalText);
         return _signalText;
    }

    /**
     * 信号集合: SIGNAL_TEXT
     */
    public void setSignalText(java.lang.String value){
        if(onPropSet(PROP_ID_signalText,value)){
            this._signalText = value;
            internalClearRefs(PROP_ID_signalText);
            
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
     * Job ID: JOB_INSTANCE_ID
     */
    public java.lang.String getJobInstanceId(){
         onPropGet(PROP_ID_jobInstanceId);
         return _jobInstanceId;
    }

    /**
     * Job ID: JOB_INSTANCE_ID
     */
    public void setJobInstanceId(java.lang.String value){
        if(onPropSet(PROP_ID_jobInstanceId,value)){
            this._jobInstanceId = value;
            internalClearRefs(PROP_ID_jobInstanceId);
            
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
     * Worker ID: WORKER_ID
     */
    public java.lang.String getWorkerId(){
         onPropGet(PROP_ID_workerId);
         return _workerId;
    }

    /**
     * Worker ID: WORKER_ID
     */
    public void setWorkerId(java.lang.String value){
        if(onPropSet(PROP_ID_workerId,value)){
            this._workerId = value;
            internalClearRefs(PROP_ID_workerId);
            
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
     * 父流程
     */
    public io.nop.task.dao.entity.NopTaskInstance getParentTaskInstance(){
       return (io.nop.task.dao.entity.NopTaskInstance)internalGetRefEntity(PROP_NAME_parentTaskInstance);
    }

    public void setParentTaskInstance(io.nop.task.dao.entity.NopTaskInstance refEntity){
   
           if(refEntity == null){
           
                   this.setParentTaskId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_parentTaskInstance, refEntity,()->{
           
                           this.setParentTaskId(refEntity.getTaskInstanceId());
                       
           });
           }
       
    }
       
    private final OrmEntitySet<io.nop.task.dao.entity.NopTaskStepInstance> _steps = new OrmEntitySet<>(this, PROP_NAME_steps,
        io.nop.task.dao.entity.NopTaskStepInstance.PROP_NAME_taskInstance, null,io.nop.task.dao.entity.NopTaskStepInstance.class);

    /**
     * 。 refPropName: taskInstance, keyProp: {rel.keyProp}
     */
    public IOrmEntitySet<io.nop.task.dao.entity.NopTaskStepInstance> getSteps(){
       return _steps;
    }
       
}
// resume CPD analysis - CPD-ON
