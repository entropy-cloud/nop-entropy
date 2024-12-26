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

import io.nop.job.dao.entity.NopJobInstance;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  任务实例: nop_job_instance
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopJobInstance extends DynamicOrmEntity{
    
    /* Job ID: JOB_ID VARCHAR */
    public static final String PROP_NAME_jobId = "jobId";
    public static final int PROP_ID_jobId = 1;
    
    /* 任务定义ID: JOB_DEF_ID VARCHAR */
    public static final String PROP_NAME_jobDefId = "jobDefId";
    public static final int PROP_ID_jobDefId = 2;
    
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
    
    /* 任务状态: STATUS INTEGER */
    public static final String PROP_NAME_status = "status";
    public static final int PROP_ID_status = 7;
    
    /* 调度执行时间: SCHEDULED_EXEC_TIME TIMESTAMP */
    public static final String PROP_NAME_scheduledExecTime = "scheduledExecTime";
    public static final int PROP_ID_scheduledExecTime = 8;
    
    /* 实际执行时间: EXEC_TIME TIMESTAMP */
    public static final String PROP_NAME_execTime = "execTime";
    public static final int PROP_ID_execTime = 9;
    
    /* 是否只执行一次: ONCE_TASK TINYINT */
    public static final String PROP_NAME_onceTask = "onceTask";
    public static final int PROP_ID_onceTask = 10;
    
    /* 错误码: ERR_CODE VARCHAR */
    public static final String PROP_NAME_errCode = "errCode";
    public static final int PROP_ID_errCode = 11;
    
    /* 错误消息: ERR_MSG VARCHAR */
    public static final String PROP_NAME_errMsg = "errMsg";
    public static final int PROP_ID_errMsg = 12;
    
    /* 数据版本: VERSION BIGINT */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 13;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 14;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 15;
    
    /* 修改人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 16;
    
    /* 修改时间: UPDATE_TIME TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 17;
    
    /* 备注: REMARK VARCHAR */
    public static final String PROP_NAME_remark = "remark";
    public static final int PROP_ID_remark = 18;
    

    private static int _PROP_ID_BOUND = 19;

    
    /* relation: 作业计划 */
    public static final String PROP_NAME_jobDefinition = "jobDefinition";
    
    /* component:  */
    public static final String PROP_NAME_jobParamsComponent = "jobParamsComponent";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_jobId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_jobId};

    private static final String[] PROP_ID_TO_NAME = new String[19];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_jobId] = PROP_NAME_jobId;
          PROP_NAME_TO_ID.put(PROP_NAME_jobId, PROP_ID_jobId);
      
          PROP_ID_TO_NAME[PROP_ID_jobDefId] = PROP_NAME_jobDefId;
          PROP_NAME_TO_ID.put(PROP_NAME_jobDefId, PROP_ID_jobDefId);
      
          PROP_ID_TO_NAME[PROP_ID_jobName] = PROP_NAME_jobName;
          PROP_NAME_TO_ID.put(PROP_NAME_jobName, PROP_ID_jobName);
      
          PROP_ID_TO_NAME[PROP_ID_jobGroup] = PROP_NAME_jobGroup;
          PROP_NAME_TO_ID.put(PROP_NAME_jobGroup, PROP_ID_jobGroup);
      
          PROP_ID_TO_NAME[PROP_ID_jobParams] = PROP_NAME_jobParams;
          PROP_NAME_TO_ID.put(PROP_NAME_jobParams, PROP_ID_jobParams);
      
          PROP_ID_TO_NAME[PROP_ID_jobInvoker] = PROP_NAME_jobInvoker;
          PROP_NAME_TO_ID.put(PROP_NAME_jobInvoker, PROP_ID_jobInvoker);
      
          PROP_ID_TO_NAME[PROP_ID_status] = PROP_NAME_status;
          PROP_NAME_TO_ID.put(PROP_NAME_status, PROP_ID_status);
      
          PROP_ID_TO_NAME[PROP_ID_scheduledExecTime] = PROP_NAME_scheduledExecTime;
          PROP_NAME_TO_ID.put(PROP_NAME_scheduledExecTime, PROP_ID_scheduledExecTime);
      
          PROP_ID_TO_NAME[PROP_ID_execTime] = PROP_NAME_execTime;
          PROP_NAME_TO_ID.put(PROP_NAME_execTime, PROP_ID_execTime);
      
          PROP_ID_TO_NAME[PROP_ID_onceTask] = PROP_NAME_onceTask;
          PROP_NAME_TO_ID.put(PROP_NAME_onceTask, PROP_ID_onceTask);
      
          PROP_ID_TO_NAME[PROP_ID_errCode] = PROP_NAME_errCode;
          PROP_NAME_TO_ID.put(PROP_NAME_errCode, PROP_ID_errCode);
      
          PROP_ID_TO_NAME[PROP_ID_errMsg] = PROP_NAME_errMsg;
          PROP_NAME_TO_ID.put(PROP_NAME_errMsg, PROP_ID_errMsg);
      
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

    
    /* Job ID: JOB_ID */
    private java.lang.String _jobId;
    
    /* 任务定义ID: JOB_DEF_ID */
    private java.lang.String _jobDefId;
    
    /* 任务名: JOB_NAME */
    private java.lang.String _jobName;
    
    /* 任务组: JOB_GROUP */
    private java.lang.String _jobGroup;
    
    /* 任务参数: JOB_PARAMS */
    private java.lang.String _jobParams;
    
    /* 任务执行函数: JOB_INVOKER */
    private java.lang.String _jobInvoker;
    
    /* 任务状态: STATUS */
    private java.lang.Integer _status;
    
    /* 调度执行时间: SCHEDULED_EXEC_TIME */
    private java.sql.Timestamp _scheduledExecTime;
    
    /* 实际执行时间: EXEC_TIME */
    private java.sql.Timestamp _execTime;
    
    /* 是否只执行一次: ONCE_TASK */
    private java.lang.Byte _onceTask;
    
    /* 错误码: ERR_CODE */
    private java.lang.String _errCode;
    
    /* 错误消息: ERR_MSG */
    private java.lang.String _errMsg;
    
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
    

    public _NopJobInstance(){
        // for debug
    }

    protected NopJobInstance newInstance(){
        NopJobInstance entity = new NopJobInstance();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopJobInstance cloneInstance() {
        NopJobInstance entity = newInstance();
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
      return "io.nop.job.dao.entity.NopJobInstance";
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
    
        return buildSimpleId(PROP_ID_jobId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_jobId;
          
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
        
            case PROP_ID_jobId:
               return getJobId();
        
            case PROP_ID_jobDefId:
               return getJobDefId();
        
            case PROP_ID_jobName:
               return getJobName();
        
            case PROP_ID_jobGroup:
               return getJobGroup();
        
            case PROP_ID_jobParams:
               return getJobParams();
        
            case PROP_ID_jobInvoker:
               return getJobInvoker();
        
            case PROP_ID_status:
               return getStatus();
        
            case PROP_ID_scheduledExecTime:
               return getScheduledExecTime();
        
            case PROP_ID_execTime:
               return getExecTime();
        
            case PROP_ID_onceTask:
               return getOnceTask();
        
            case PROP_ID_errCode:
               return getErrCode();
        
            case PROP_ID_errMsg:
               return getErrMsg();
        
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
        
            case PROP_ID_jobId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_jobId));
               }
               setJobId(typedValue);
               break;
            }
        
            case PROP_ID_jobDefId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_jobDefId));
               }
               setJobDefId(typedValue);
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
        
            case PROP_ID_status:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_status));
               }
               setStatus(typedValue);
               break;
            }
        
            case PROP_ID_scheduledExecTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_scheduledExecTime));
               }
               setScheduledExecTime(typedValue);
               break;
            }
        
            case PROP_ID_execTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_execTime));
               }
               setExecTime(typedValue);
               break;
            }
        
            case PROP_ID_onceTask:{
               java.lang.Byte typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toByte(value,
                       err-> newTypeConversionError(PROP_NAME_onceTask));
               }
               setOnceTask(typedValue);
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
        
            case PROP_ID_jobId:{
               onInitProp(propId);
               this._jobId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_jobDefId:{
               onInitProp(propId);
               this._jobDefId = (java.lang.String)value;
               
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
        
            case PROP_ID_status:{
               onInitProp(propId);
               this._status = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_scheduledExecTime:{
               onInitProp(propId);
               this._scheduledExecTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_execTime:{
               onInitProp(propId);
               this._execTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_onceTask:{
               onInitProp(propId);
               this._onceTask = (java.lang.Byte)value;
               
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
     * Job ID: JOB_ID
     */
    public final java.lang.String getJobId(){
         onPropGet(PROP_ID_jobId);
         return _jobId;
    }

    /**
     * Job ID: JOB_ID
     */
    public final void setJobId(java.lang.String value){
        if(onPropSet(PROP_ID_jobId,value)){
            this._jobId = value;
            internalClearRefs(PROP_ID_jobId);
            orm_id();
        }
    }
    
    /**
     * 任务定义ID: JOB_DEF_ID
     */
    public final java.lang.String getJobDefId(){
         onPropGet(PROP_ID_jobDefId);
         return _jobDefId;
    }

    /**
     * 任务定义ID: JOB_DEF_ID
     */
    public final void setJobDefId(java.lang.String value){
        if(onPropSet(PROP_ID_jobDefId,value)){
            this._jobDefId = value;
            internalClearRefs(PROP_ID_jobDefId);
            
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
     * 调度执行时间: SCHEDULED_EXEC_TIME
     */
    public final java.sql.Timestamp getScheduledExecTime(){
         onPropGet(PROP_ID_scheduledExecTime);
         return _scheduledExecTime;
    }

    /**
     * 调度执行时间: SCHEDULED_EXEC_TIME
     */
    public final void setScheduledExecTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_scheduledExecTime,value)){
            this._scheduledExecTime = value;
            internalClearRefs(PROP_ID_scheduledExecTime);
            
        }
    }
    
    /**
     * 实际执行时间: EXEC_TIME
     */
    public final java.sql.Timestamp getExecTime(){
         onPropGet(PROP_ID_execTime);
         return _execTime;
    }

    /**
     * 实际执行时间: EXEC_TIME
     */
    public final void setExecTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_execTime,value)){
            this._execTime = value;
            internalClearRefs(PROP_ID_execTime);
            
        }
    }
    
    /**
     * 是否只执行一次: ONCE_TASK
     */
    public final java.lang.Byte getOnceTask(){
         onPropGet(PROP_ID_onceTask);
         return _onceTask;
    }

    /**
     * 是否只执行一次: ONCE_TASK
     */
    public final void setOnceTask(java.lang.Byte value){
        if(onPropSet(PROP_ID_onceTask,value)){
            this._onceTask = value;
            internalClearRefs(PROP_ID_onceTask);
            
        }
    }
    
    /**
     * 错误码: ERR_CODE
     */
    public final java.lang.String getErrCode(){
         onPropGet(PROP_ID_errCode);
         return _errCode;
    }

    /**
     * 错误码: ERR_CODE
     */
    public final void setErrCode(java.lang.String value){
        if(onPropSet(PROP_ID_errCode,value)){
            this._errCode = value;
            internalClearRefs(PROP_ID_errCode);
            
        }
    }
    
    /**
     * 错误消息: ERR_MSG
     */
    public final java.lang.String getErrMsg(){
         onPropGet(PROP_ID_errMsg);
         return _errMsg;
    }

    /**
     * 错误消息: ERR_MSG
     */
    public final void setErrMsg(java.lang.String value){
        if(onPropSet(PROP_ID_errMsg,value)){
            this._errMsg = value;
            internalClearRefs(PROP_ID_errMsg);
            
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
     * 作业计划
     */
    public final io.nop.job.dao.entity.NopJobDefinition getJobDefinition(){
       return (io.nop.job.dao.entity.NopJobDefinition)internalGetRefEntity(PROP_NAME_jobDefinition);
    }

    public final void setJobDefinition(io.nop.job.dao.entity.NopJobDefinition refEntity){
   
           if(refEntity == null){
           
                   this.setJobDefId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_jobDefinition, refEntity,()->{
           
                           this.setJobDefId(refEntity.getSid());
                       
           });
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
