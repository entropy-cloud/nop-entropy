package io.nop.datav.dao.entity._gen;

import io.nop.orm.model.IEntityModel;
import io.nop.orm.support.DynamicOrmEntity;
import io.nop.orm.support.OrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.orm.IOrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code

import io.nop.api.core.convert.ConvertHelper;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;

import io.nop.datav.dao.entity.NopDatavReportTask;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  定时报告任务: nop_datav_report_task
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopDatavReportTask extends DynamicOrmEntity{
    
    /* 报告任务ID: REPORT_TASK_ID VARCHAR */
    public static final String PROP_NAME_reportTaskId = "reportTaskId";
    public static final int PROP_ID_reportTaskId = 1;
    
    /* 任务名: TASK_NAME VARCHAR */
    public static final String PROP_NAME_taskName = "taskName";
    public static final int PROP_ID_taskName = 2;
    
    /* 显示名: DISPLAY_NAME VARCHAR */
    public static final String PROP_NAME_displayName = "displayName";
    public static final int PROP_ID_displayName = 3;
    
    /* 看板ID: DASHBOARD_ID VARCHAR */
    public static final String PROP_NAME_dashboardId = "dashboardId";
    public static final int PROP_ID_dashboardId = 4;
    
    /* cron表达式: CRON_EXPR VARCHAR */
    public static final String PROP_NAME_cronExpr = "cronExpr";
    public static final int PROP_ID_cronExpr = 5;
    
    /* 导出格式: FORMAT VARCHAR */
    public static final String PROP_NAME_format = "format";
    public static final int PROP_ID_format = 6;
    
    /* 收件人: RECIPIENTS CLOB */
    public static final String PROP_NAME_recipients = "recipients";
    public static final int PROP_ID_recipients = 7;
    
    /* 通知渠道: NOTIFY_CHANNELS CLOB */
    public static final String PROP_NAME_notifyChannels = "notifyChannels";
    public static final int PROP_ID_notifyChannels = 8;
    
    /* 报告参数: PARAMS CLOB */
    public static final String PROP_NAME_params = "params";
    public static final int PROP_ID_params = 9;
    
    /* 任务状态: STATUS INTEGER */
    public static final String PROP_NAME_status = "status";
    public static final int PROP_ID_status = 10;
    
    /* grace期(分钟): GRACE_MINUTES INTEGER */
    public static final String PROP_NAME_graceMinutes = "graceMinutes";
    public static final int PROP_ID_graceMinutes = 11;
    
    /* 模板键: TEMPLATE_KEY VARCHAR */
    public static final String PROP_NAME_templateKey = "templateKey";
    public static final int PROP_ID_templateKey = 12;
    
    /* 最后执行时间: LAST_RUN_TIME TIMESTAMP */
    public static final String PROP_NAME_lastRunTime = "lastRunTime";
    public static final int PROP_ID_lastRunTime = 13;
    
    /* 最后执行状态: LAST_RUN_STATUS VARCHAR */
    public static final String PROP_NAME_lastRunStatus = "lastRunStatus";
    public static final int PROP_ID_lastRunStatus = 14;
    
    /* 最后执行错误: LAST_RUN_ERROR VARCHAR */
    public static final String PROP_NAME_lastRunError = "lastRunError";
    public static final int PROP_ID_lastRunError = 15;
    
    /* 删除标记: DEL_FLAG TINYINT */
    public static final String PROP_NAME_delFlag = "delFlag";
    public static final int PROP_ID_delFlag = 16;
    
    /* 数据版本: VERSION BIGINT */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 17;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 18;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 19;
    
    /* 修改人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 20;
    
    /* 修改时间: UPDATE_TIME TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 21;
    
    /* 备注: REMARK VARCHAR */
    public static final String PROP_NAME_remark = "remark";
    public static final int PROP_ID_remark = 22;
    

    private static int _PROP_ID_BOUND = 23;

    
    /* relation: 看板 */
    public static final String PROP_NAME_dashboard = "dashboard";
    
    /* component:  */
    public static final String PROP_NAME_recipientsComponent = "recipientsComponent";
    
    /* component:  */
    public static final String PROP_NAME_notifyChannelsComponent = "notifyChannelsComponent";
    
    /* component:  */
    public static final String PROP_NAME_paramsComponent = "paramsComponent";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_reportTaskId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_reportTaskId};

    private static final String[] PROP_ID_TO_NAME = new String[23];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_reportTaskId] = PROP_NAME_reportTaskId;
          PROP_NAME_TO_ID.put(PROP_NAME_reportTaskId, PROP_ID_reportTaskId);
      
          PROP_ID_TO_NAME[PROP_ID_taskName] = PROP_NAME_taskName;
          PROP_NAME_TO_ID.put(PROP_NAME_taskName, PROP_ID_taskName);
      
          PROP_ID_TO_NAME[PROP_ID_displayName] = PROP_NAME_displayName;
          PROP_NAME_TO_ID.put(PROP_NAME_displayName, PROP_ID_displayName);
      
          PROP_ID_TO_NAME[PROP_ID_dashboardId] = PROP_NAME_dashboardId;
          PROP_NAME_TO_ID.put(PROP_NAME_dashboardId, PROP_ID_dashboardId);
      
          PROP_ID_TO_NAME[PROP_ID_cronExpr] = PROP_NAME_cronExpr;
          PROP_NAME_TO_ID.put(PROP_NAME_cronExpr, PROP_ID_cronExpr);
      
          PROP_ID_TO_NAME[PROP_ID_format] = PROP_NAME_format;
          PROP_NAME_TO_ID.put(PROP_NAME_format, PROP_ID_format);
      
          PROP_ID_TO_NAME[PROP_ID_recipients] = PROP_NAME_recipients;
          PROP_NAME_TO_ID.put(PROP_NAME_recipients, PROP_ID_recipients);
      
          PROP_ID_TO_NAME[PROP_ID_notifyChannels] = PROP_NAME_notifyChannels;
          PROP_NAME_TO_ID.put(PROP_NAME_notifyChannels, PROP_ID_notifyChannels);
      
          PROP_ID_TO_NAME[PROP_ID_params] = PROP_NAME_params;
          PROP_NAME_TO_ID.put(PROP_NAME_params, PROP_ID_params);
      
          PROP_ID_TO_NAME[PROP_ID_status] = PROP_NAME_status;
          PROP_NAME_TO_ID.put(PROP_NAME_status, PROP_ID_status);
      
          PROP_ID_TO_NAME[PROP_ID_graceMinutes] = PROP_NAME_graceMinutes;
          PROP_NAME_TO_ID.put(PROP_NAME_graceMinutes, PROP_ID_graceMinutes);
      
          PROP_ID_TO_NAME[PROP_ID_templateKey] = PROP_NAME_templateKey;
          PROP_NAME_TO_ID.put(PROP_NAME_templateKey, PROP_ID_templateKey);
      
          PROP_ID_TO_NAME[PROP_ID_lastRunTime] = PROP_NAME_lastRunTime;
          PROP_NAME_TO_ID.put(PROP_NAME_lastRunTime, PROP_ID_lastRunTime);
      
          PROP_ID_TO_NAME[PROP_ID_lastRunStatus] = PROP_NAME_lastRunStatus;
          PROP_NAME_TO_ID.put(PROP_NAME_lastRunStatus, PROP_ID_lastRunStatus);
      
          PROP_ID_TO_NAME[PROP_ID_lastRunError] = PROP_NAME_lastRunError;
          PROP_NAME_TO_ID.put(PROP_NAME_lastRunError, PROP_ID_lastRunError);
      
          PROP_ID_TO_NAME[PROP_ID_delFlag] = PROP_NAME_delFlag;
          PROP_NAME_TO_ID.put(PROP_NAME_delFlag, PROP_ID_delFlag);
      
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

    
    /* 报告任务ID: REPORT_TASK_ID */
    private java.lang.String _reportTaskId;
    
    /* 任务名: TASK_NAME */
    private java.lang.String _taskName;
    
    /* 显示名: DISPLAY_NAME */
    private java.lang.String _displayName;
    
    /* 看板ID: DASHBOARD_ID */
    private java.lang.String _dashboardId;
    
    /* cron表达式: CRON_EXPR */
    private java.lang.String _cronExpr;
    
    /* 导出格式: FORMAT */
    private java.lang.String _format;
    
    /* 收件人: RECIPIENTS */
    private java.lang.String _recipients;
    
    /* 通知渠道: NOTIFY_CHANNELS */
    private java.lang.String _notifyChannels;
    
    /* 报告参数: PARAMS */
    private java.lang.String _params;
    
    /* 任务状态: STATUS */
    private java.lang.Integer _status;
    
    /* grace期(分钟): GRACE_MINUTES */
    private java.lang.Integer _graceMinutes;
    
    /* 模板键: TEMPLATE_KEY */
    private java.lang.String _templateKey;
    
    /* 最后执行时间: LAST_RUN_TIME */
    private java.sql.Timestamp _lastRunTime;
    
    /* 最后执行状态: LAST_RUN_STATUS */
    private java.lang.String _lastRunStatus;
    
    /* 最后执行错误: LAST_RUN_ERROR */
    private java.lang.String _lastRunError;
    
    /* 删除标记: DEL_FLAG */
    private java.lang.Byte _delFlag;
    
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
    

    public _NopDatavReportTask(){
        // for debug
    }

    protected NopDatavReportTask newInstance(){
        NopDatavReportTask entity = new NopDatavReportTask();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopDatavReportTask cloneInstance() {
        NopDatavReportTask entity = newInstance();
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
      return "io.nop.datav.dao.entity.NopDatavReportTask";
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
    
        return buildSimpleId(PROP_ID_reportTaskId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_reportTaskId;
          
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
        
            case PROP_ID_reportTaskId:
               return getReportTaskId();
        
            case PROP_ID_taskName:
               return getTaskName();
        
            case PROP_ID_displayName:
               return getDisplayName();
        
            case PROP_ID_dashboardId:
               return getDashboardId();
        
            case PROP_ID_cronExpr:
               return getCronExpr();
        
            case PROP_ID_format:
               return getFormat();
        
            case PROP_ID_recipients:
               return getRecipients();
        
            case PROP_ID_notifyChannels:
               return getNotifyChannels();
        
            case PROP_ID_params:
               return getParams();
        
            case PROP_ID_status:
               return getStatus();
        
            case PROP_ID_graceMinutes:
               return getGraceMinutes();
        
            case PROP_ID_templateKey:
               return getTemplateKey();
        
            case PROP_ID_lastRunTime:
               return getLastRunTime();
        
            case PROP_ID_lastRunStatus:
               return getLastRunStatus();
        
            case PROP_ID_lastRunError:
               return getLastRunError();
        
            case PROP_ID_delFlag:
               return getDelFlag();
        
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
        
            case PROP_ID_reportTaskId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_reportTaskId));
               }
               setReportTaskId(typedValue);
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
        
            case PROP_ID_displayName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_displayName));
               }
               setDisplayName(typedValue);
               break;
            }
        
            case PROP_ID_dashboardId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_dashboardId));
               }
               setDashboardId(typedValue);
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
        
            case PROP_ID_format:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_format));
               }
               setFormat(typedValue);
               break;
            }
        
            case PROP_ID_recipients:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_recipients));
               }
               setRecipients(typedValue);
               break;
            }
        
            case PROP_ID_notifyChannels:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_notifyChannels));
               }
               setNotifyChannels(typedValue);
               break;
            }
        
            case PROP_ID_params:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_params));
               }
               setParams(typedValue);
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
        
            case PROP_ID_graceMinutes:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_graceMinutes));
               }
               setGraceMinutes(typedValue);
               break;
            }
        
            case PROP_ID_templateKey:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_templateKey));
               }
               setTemplateKey(typedValue);
               break;
            }
        
            case PROP_ID_lastRunTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_lastRunTime));
               }
               setLastRunTime(typedValue);
               break;
            }
        
            case PROP_ID_lastRunStatus:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_lastRunStatus));
               }
               setLastRunStatus(typedValue);
               break;
            }
        
            case PROP_ID_lastRunError:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_lastRunError));
               }
               setLastRunError(typedValue);
               break;
            }
        
            case PROP_ID_delFlag:{
               java.lang.Byte typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toByte(value,
                       err-> newTypeConversionError(PROP_NAME_delFlag));
               }
               setDelFlag(typedValue);
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
        
            case PROP_ID_reportTaskId:{
               onInitProp(propId);
               this._reportTaskId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_taskName:{
               onInitProp(propId);
               this._taskName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_displayName:{
               onInitProp(propId);
               this._displayName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_dashboardId:{
               onInitProp(propId);
               this._dashboardId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_cronExpr:{
               onInitProp(propId);
               this._cronExpr = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_format:{
               onInitProp(propId);
               this._format = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_recipients:{
               onInitProp(propId);
               this._recipients = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_notifyChannels:{
               onInitProp(propId);
               this._notifyChannels = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_params:{
               onInitProp(propId);
               this._params = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_status:{
               onInitProp(propId);
               this._status = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_graceMinutes:{
               onInitProp(propId);
               this._graceMinutes = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_templateKey:{
               onInitProp(propId);
               this._templateKey = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_lastRunTime:{
               onInitProp(propId);
               this._lastRunTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_lastRunStatus:{
               onInitProp(propId);
               this._lastRunStatus = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_lastRunError:{
               onInitProp(propId);
               this._lastRunError = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_delFlag:{
               onInitProp(propId);
               this._delFlag = (java.lang.Byte)value;
               
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
     * 报告任务ID: REPORT_TASK_ID
     */
    public final java.lang.String getReportTaskId(){
         onPropGet(PROP_ID_reportTaskId);
         return _reportTaskId;
    }

    /**
     * 报告任务ID: REPORT_TASK_ID
     */
    public final void setReportTaskId(java.lang.String value){
        if(onPropSet(PROP_ID_reportTaskId,value)){
            this._reportTaskId = value;
            internalClearRefs(PROP_ID_reportTaskId);
            orm_id();
        }
    }
    
    /**
     * 任务名: TASK_NAME
     */
    public final java.lang.String getTaskName(){
         onPropGet(PROP_ID_taskName);
         return _taskName;
    }

    /**
     * 任务名: TASK_NAME
     */
    public final void setTaskName(java.lang.String value){
        if(onPropSet(PROP_ID_taskName,value)){
            this._taskName = value;
            internalClearRefs(PROP_ID_taskName);
            
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
     * 看板ID: DASHBOARD_ID
     */
    public final java.lang.String getDashboardId(){
         onPropGet(PROP_ID_dashboardId);
         return _dashboardId;
    }

    /**
     * 看板ID: DASHBOARD_ID
     */
    public final void setDashboardId(java.lang.String value){
        if(onPropSet(PROP_ID_dashboardId,value)){
            this._dashboardId = value;
            internalClearRefs(PROP_ID_dashboardId);
            
        }
    }
    
    /**
     * cron表达式: CRON_EXPR
     */
    public final java.lang.String getCronExpr(){
         onPropGet(PROP_ID_cronExpr);
         return _cronExpr;
    }

    /**
     * cron表达式: CRON_EXPR
     */
    public final void setCronExpr(java.lang.String value){
        if(onPropSet(PROP_ID_cronExpr,value)){
            this._cronExpr = value;
            internalClearRefs(PROP_ID_cronExpr);
            
        }
    }
    
    /**
     * 导出格式: FORMAT
     */
    public final java.lang.String getFormat(){
         onPropGet(PROP_ID_format);
         return _format;
    }

    /**
     * 导出格式: FORMAT
     */
    public final void setFormat(java.lang.String value){
        if(onPropSet(PROP_ID_format,value)){
            this._format = value;
            internalClearRefs(PROP_ID_format);
            
        }
    }
    
    /**
     * 收件人: RECIPIENTS
     */
    public final java.lang.String getRecipients(){
         onPropGet(PROP_ID_recipients);
         return _recipients;
    }

    /**
     * 收件人: RECIPIENTS
     */
    public final void setRecipients(java.lang.String value){
        if(onPropSet(PROP_ID_recipients,value)){
            this._recipients = value;
            internalClearRefs(PROP_ID_recipients);
            
        }
    }
    
    /**
     * 通知渠道: NOTIFY_CHANNELS
     */
    public final java.lang.String getNotifyChannels(){
         onPropGet(PROP_ID_notifyChannels);
         return _notifyChannels;
    }

    /**
     * 通知渠道: NOTIFY_CHANNELS
     */
    public final void setNotifyChannels(java.lang.String value){
        if(onPropSet(PROP_ID_notifyChannels,value)){
            this._notifyChannels = value;
            internalClearRefs(PROP_ID_notifyChannels);
            
        }
    }
    
    /**
     * 报告参数: PARAMS
     */
    public final java.lang.String getParams(){
         onPropGet(PROP_ID_params);
         return _params;
    }

    /**
     * 报告参数: PARAMS
     */
    public final void setParams(java.lang.String value){
        if(onPropSet(PROP_ID_params,value)){
            this._params = value;
            internalClearRefs(PROP_ID_params);
            
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
     * grace期(分钟): GRACE_MINUTES
     */
    public final java.lang.Integer getGraceMinutes(){
         onPropGet(PROP_ID_graceMinutes);
         return _graceMinutes;
    }

    /**
     * grace期(分钟): GRACE_MINUTES
     */
    public final void setGraceMinutes(java.lang.Integer value){
        if(onPropSet(PROP_ID_graceMinutes,value)){
            this._graceMinutes = value;
            internalClearRefs(PROP_ID_graceMinutes);
            
        }
    }
    
    /**
     * 模板键: TEMPLATE_KEY
     */
    public final java.lang.String getTemplateKey(){
         onPropGet(PROP_ID_templateKey);
         return _templateKey;
    }

    /**
     * 模板键: TEMPLATE_KEY
     */
    public final void setTemplateKey(java.lang.String value){
        if(onPropSet(PROP_ID_templateKey,value)){
            this._templateKey = value;
            internalClearRefs(PROP_ID_templateKey);
            
        }
    }
    
    /**
     * 最后执行时间: LAST_RUN_TIME
     */
    public final java.sql.Timestamp getLastRunTime(){
         onPropGet(PROP_ID_lastRunTime);
         return _lastRunTime;
    }

    /**
     * 最后执行时间: LAST_RUN_TIME
     */
    public final void setLastRunTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_lastRunTime,value)){
            this._lastRunTime = value;
            internalClearRefs(PROP_ID_lastRunTime);
            
        }
    }
    
    /**
     * 最后执行状态: LAST_RUN_STATUS
     */
    public final java.lang.String getLastRunStatus(){
         onPropGet(PROP_ID_lastRunStatus);
         return _lastRunStatus;
    }

    /**
     * 最后执行状态: LAST_RUN_STATUS
     */
    public final void setLastRunStatus(java.lang.String value){
        if(onPropSet(PROP_ID_lastRunStatus,value)){
            this._lastRunStatus = value;
            internalClearRefs(PROP_ID_lastRunStatus);
            
        }
    }
    
    /**
     * 最后执行错误: LAST_RUN_ERROR
     */
    public final java.lang.String getLastRunError(){
         onPropGet(PROP_ID_lastRunError);
         return _lastRunError;
    }

    /**
     * 最后执行错误: LAST_RUN_ERROR
     */
    public final void setLastRunError(java.lang.String value){
        if(onPropSet(PROP_ID_lastRunError,value)){
            this._lastRunError = value;
            internalClearRefs(PROP_ID_lastRunError);
            
        }
    }
    
    /**
     * 删除标记: DEL_FLAG
     */
    public final java.lang.Byte getDelFlag(){
         onPropGet(PROP_ID_delFlag);
         return _delFlag;
    }

    /**
     * 删除标记: DEL_FLAG
     */
    public final void setDelFlag(java.lang.Byte value){
        if(onPropSet(PROP_ID_delFlag,value)){
            this._delFlag = value;
            internalClearRefs(PROP_ID_delFlag);
            
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
     * 看板
     */
    public final io.nop.datav.dao.entity.NopDatavDashboard getDashboard(){
       return (io.nop.datav.dao.entity.NopDatavDashboard)internalGetRefEntity(PROP_NAME_dashboard);
    }

    public final void setDashboard(io.nop.datav.dao.entity.NopDatavDashboard refEntity){
   
           if(refEntity == null){
           
                   this.setDashboardId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_dashboard, refEntity,()->{
           
                           this.setDashboardId(refEntity.getDashboardId());
                       
           });
           }
       
    }
       
   private io.nop.orm.component.JsonOrmComponent _recipientsComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_recipientsComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_recipientsComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_recipients);
      
   }

   public final io.nop.orm.component.JsonOrmComponent getRecipientsComponent(){
      if(_recipientsComponent == null){
          _recipientsComponent = new io.nop.orm.component.JsonOrmComponent();
          _recipientsComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_recipientsComponent);
      }
      return _recipientsComponent;
   }

   private io.nop.orm.component.JsonOrmComponent _notifyChannelsComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_notifyChannelsComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_notifyChannelsComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_notifyChannels);
      
   }

   public final io.nop.orm.component.JsonOrmComponent getNotifyChannelsComponent(){
      if(_notifyChannelsComponent == null){
          _notifyChannelsComponent = new io.nop.orm.component.JsonOrmComponent();
          _notifyChannelsComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_notifyChannelsComponent);
      }
      return _notifyChannelsComponent;
   }

   private io.nop.orm.component.JsonOrmComponent _paramsComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_paramsComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_paramsComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_params);
      
   }

   public final io.nop.orm.component.JsonOrmComponent getParamsComponent(){
      if(_paramsComponent == null){
          _paramsComponent = new io.nop.orm.component.JsonOrmComponent();
          _paramsComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_paramsComponent);
      }
      return _paramsComponent;
   }

}
// resume CPD analysis - CPD-ON
