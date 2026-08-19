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

import io.nop.job.dao.entity.NopJobTaskLog;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  执行日志: nop_job_task_log
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopJobTaskLog extends DynamicOrmEntity{
    
    /* 日志ID: JOB_TASK_LOG_ID VARCHAR */
    public static final String PROP_NAME_jobTaskLogId = "jobTaskLogId";
    public static final int PROP_ID_jobTaskLogId = 1;
    
    /* 任务ID: JOB_TASK_ID VARCHAR */
    public static final String PROP_NAME_jobTaskId = "jobTaskId";
    public static final int PROP_ID_jobTaskId = 2;
    
    /* 批次ID: JOB_FIRE_ID VARCHAR */
    public static final String PROP_NAME_jobFireId = "jobFireId";
    public static final int PROP_ID_jobFireId = 3;
    
    /* 调度ID: JOB_SCHEDULE_ID VARCHAR */
    public static final String PROP_NAME_jobScheduleId = "jobScheduleId";
    public static final int PROP_ID_jobScheduleId = 4;
    
    /* 作业名: JOB_NAME VARCHAR */
    public static final String PROP_NAME_jobName = "jobName";
    public static final int PROP_ID_jobName = 5;
    
    /* 分组: GROUP_ID VARCHAR */
    public static final String PROP_NAME_groupId = "groupId";
    public static final int PROP_ID_groupId = 6;
    
    /* 日志时间: LOG_TIME TIMESTAMP */
    public static final String PROP_NAME_logTime = "logTime";
    public static final int PROP_ID_logTime = 7;
    
    /* 日志级别: LOG_LEVEL INTEGER */
    public static final String PROP_NAME_logLevel = "logLevel";
    public static final int PROP_ID_logLevel = 8;
    
    /* 日志内容: LOG_MESSAGE VARCHAR */
    public static final String PROP_NAME_logMessage = "logMessage";
    public static final int PROP_ID_logMessage = 9;
    
    /* 日志扩展信息: LOG_PAYLOAD VARCHAR */
    public static final String PROP_NAME_logPayload = "logPayload";
    public static final int PROP_ID_logPayload = 10;
    
    /* 数据版本: VERSION BIGINT */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 11;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 12;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 13;
    
    /* 修改人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 14;
    
    /* 修改时间: UPDATE_TIME TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 15;
    
    /* 备注: REMARK VARCHAR */
    public static final String PROP_NAME_remark = "remark";
    public static final int PROP_ID_remark = 16;
    

    private static int _PROP_ID_BOUND = 17;

    
    /* component:  */
    public static final String PROP_NAME_logPayloadComponent = "logPayloadComponent";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_jobTaskLogId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_jobTaskLogId};

    private static final String[] PROP_ID_TO_NAME = new String[17];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_jobTaskLogId] = PROP_NAME_jobTaskLogId;
          PROP_NAME_TO_ID.put(PROP_NAME_jobTaskLogId, PROP_ID_jobTaskLogId);
      
          PROP_ID_TO_NAME[PROP_ID_jobTaskId] = PROP_NAME_jobTaskId;
          PROP_NAME_TO_ID.put(PROP_NAME_jobTaskId, PROP_ID_jobTaskId);
      
          PROP_ID_TO_NAME[PROP_ID_jobFireId] = PROP_NAME_jobFireId;
          PROP_NAME_TO_ID.put(PROP_NAME_jobFireId, PROP_ID_jobFireId);
      
          PROP_ID_TO_NAME[PROP_ID_jobScheduleId] = PROP_NAME_jobScheduleId;
          PROP_NAME_TO_ID.put(PROP_NAME_jobScheduleId, PROP_ID_jobScheduleId);
      
          PROP_ID_TO_NAME[PROP_ID_jobName] = PROP_NAME_jobName;
          PROP_NAME_TO_ID.put(PROP_NAME_jobName, PROP_ID_jobName);
      
          PROP_ID_TO_NAME[PROP_ID_groupId] = PROP_NAME_groupId;
          PROP_NAME_TO_ID.put(PROP_NAME_groupId, PROP_ID_groupId);
      
          PROP_ID_TO_NAME[PROP_ID_logTime] = PROP_NAME_logTime;
          PROP_NAME_TO_ID.put(PROP_NAME_logTime, PROP_ID_logTime);
      
          PROP_ID_TO_NAME[PROP_ID_logLevel] = PROP_NAME_logLevel;
          PROP_NAME_TO_ID.put(PROP_NAME_logLevel, PROP_ID_logLevel);
      
          PROP_ID_TO_NAME[PROP_ID_logMessage] = PROP_NAME_logMessage;
          PROP_NAME_TO_ID.put(PROP_NAME_logMessage, PROP_ID_logMessage);
      
          PROP_ID_TO_NAME[PROP_ID_logPayload] = PROP_NAME_logPayload;
          PROP_NAME_TO_ID.put(PROP_NAME_logPayload, PROP_ID_logPayload);
      
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

    
    /* 日志ID: JOB_TASK_LOG_ID */
    private java.lang.String _jobTaskLogId;
    
    /* 任务ID: JOB_TASK_ID */
    private java.lang.String _jobTaskId;
    
    /* 批次ID: JOB_FIRE_ID */
    private java.lang.String _jobFireId;
    
    /* 调度ID: JOB_SCHEDULE_ID */
    private java.lang.String _jobScheduleId;
    
    /* 作业名: JOB_NAME */
    private java.lang.String _jobName;
    
    /* 分组: GROUP_ID */
    private java.lang.String _groupId;
    
    /* 日志时间: LOG_TIME */
    private java.sql.Timestamp _logTime;
    
    /* 日志级别: LOG_LEVEL */
    private java.lang.Integer _logLevel;
    
    /* 日志内容: LOG_MESSAGE */
    private java.lang.String _logMessage;
    
    /* 日志扩展信息: LOG_PAYLOAD */
    private java.lang.String _logPayload;
    
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
    

    public _NopJobTaskLog(){
        // for debug
    }

    protected NopJobTaskLog newInstance(){
        NopJobTaskLog entity = new NopJobTaskLog();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopJobTaskLog cloneInstance() {
        NopJobTaskLog entity = newInstance();
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
      return "io.nop.job.dao.entity.NopJobTaskLog";
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
    
        return buildSimpleId(PROP_ID_jobTaskLogId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_jobTaskLogId;
          
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
        
            case PROP_ID_jobTaskLogId:
               return getJobTaskLogId();
        
            case PROP_ID_jobTaskId:
               return getJobTaskId();
        
            case PROP_ID_jobFireId:
               return getJobFireId();
        
            case PROP_ID_jobScheduleId:
               return getJobScheduleId();
        
            case PROP_ID_jobName:
               return getJobName();
        
            case PROP_ID_groupId:
               return getGroupId();
        
            case PROP_ID_logTime:
               return getLogTime();
        
            case PROP_ID_logLevel:
               return getLogLevel();
        
            case PROP_ID_logMessage:
               return getLogMessage();
        
            case PROP_ID_logPayload:
               return getLogPayload();
        
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
        
            case PROP_ID_jobTaskLogId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_jobTaskLogId));
               }
               setJobTaskLogId(typedValue);
               break;
            }
        
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
        
            case PROP_ID_jobScheduleId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_jobScheduleId));
               }
               setJobScheduleId(typedValue);
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
        
            case PROP_ID_groupId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_groupId));
               }
               setGroupId(typedValue);
               break;
            }
        
            case PROP_ID_logTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_logTime));
               }
               setLogTime(typedValue);
               break;
            }
        
            case PROP_ID_logLevel:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_logLevel));
               }
               setLogLevel(typedValue);
               break;
            }
        
            case PROP_ID_logMessage:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_logMessage));
               }
               setLogMessage(typedValue);
               break;
            }
        
            case PROP_ID_logPayload:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_logPayload));
               }
               setLogPayload(typedValue);
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
        
            case PROP_ID_jobTaskLogId:{
               onInitProp(propId);
               this._jobTaskLogId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_jobTaskId:{
               onInitProp(propId);
               this._jobTaskId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_jobFireId:{
               onInitProp(propId);
               this._jobFireId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_jobScheduleId:{
               onInitProp(propId);
               this._jobScheduleId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_jobName:{
               onInitProp(propId);
               this._jobName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_groupId:{
               onInitProp(propId);
               this._groupId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_logTime:{
               onInitProp(propId);
               this._logTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_logLevel:{
               onInitProp(propId);
               this._logLevel = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_logMessage:{
               onInitProp(propId);
               this._logMessage = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_logPayload:{
               onInitProp(propId);
               this._logPayload = (java.lang.String)value;
               
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
     * 日志ID: JOB_TASK_LOG_ID
     */
    public final java.lang.String getJobTaskLogId(){
         onPropGet(PROP_ID_jobTaskLogId);
         return _jobTaskLogId;
    }

    /**
     * 日志ID: JOB_TASK_LOG_ID
     */
    public final void setJobTaskLogId(java.lang.String value){
        if(onPropSet(PROP_ID_jobTaskLogId,value)){
            this._jobTaskLogId = value;
            internalClearRefs(PROP_ID_jobTaskLogId);
            orm_id();
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
     * 日志时间: LOG_TIME
     */
    public final java.sql.Timestamp getLogTime(){
         onPropGet(PROP_ID_logTime);
         return _logTime;
    }

    /**
     * 日志时间: LOG_TIME
     */
    public final void setLogTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_logTime,value)){
            this._logTime = value;
            internalClearRefs(PROP_ID_logTime);
            
        }
    }
    
    /**
     * 日志级别: LOG_LEVEL
     */
    public final java.lang.Integer getLogLevel(){
         onPropGet(PROP_ID_logLevel);
         return _logLevel;
    }

    /**
     * 日志级别: LOG_LEVEL
     */
    public final void setLogLevel(java.lang.Integer value){
        if(onPropSet(PROP_ID_logLevel,value)){
            this._logLevel = value;
            internalClearRefs(PROP_ID_logLevel);
            
        }
    }
    
    /**
     * 日志内容: LOG_MESSAGE
     */
    public final java.lang.String getLogMessage(){
         onPropGet(PROP_ID_logMessage);
         return _logMessage;
    }

    /**
     * 日志内容: LOG_MESSAGE
     */
    public final void setLogMessage(java.lang.String value){
        if(onPropSet(PROP_ID_logMessage,value)){
            this._logMessage = value;
            internalClearRefs(PROP_ID_logMessage);
            
        }
    }
    
    /**
     * 日志扩展信息: LOG_PAYLOAD
     */
    public final java.lang.String getLogPayload(){
         onPropGet(PROP_ID_logPayload);
         return _logPayload;
    }

    /**
     * 日志扩展信息: LOG_PAYLOAD
     */
    public final void setLogPayload(java.lang.String value){
        if(onPropSet(PROP_ID_logPayload,value)){
            this._logPayload = value;
            internalClearRefs(PROP_ID_logPayload);
            
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
    
   private io.nop.orm.component.JsonOrmComponent _logPayloadComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_logPayloadComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_logPayloadComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_logPayload);
      
   }

   public final io.nop.orm.component.JsonOrmComponent getLogPayloadComponent(){
      if(_logPayloadComponent == null){
          _logPayloadComponent = new io.nop.orm.component.JsonOrmComponent();
          _logPayloadComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_logPayloadComponent);
      }
      return _logPayloadComponent;
   }

}
// resume CPD analysis - CPD-ON
