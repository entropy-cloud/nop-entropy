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

import io.nop.datav.dao.entity.NopDatavReportDelivery;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  报告交付历史: nop_datav_report_delivery
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopDatavReportDelivery extends DynamicOrmEntity{
    
    /* 交付ID: DELIVERY_ID VARCHAR */
    public static final String PROP_NAME_deliveryId = "deliveryId";
    public static final int PROP_ID_deliveryId = 1;
    
    /* 报告任务ID: REPORT_TASK_ID VARCHAR */
    public static final String PROP_NAME_reportTaskId = "reportTaskId";
    public static final int PROP_ID_reportTaskId = 2;
    
    /* 交付状态: STATUS INTEGER */
    public static final String PROP_NAME_status = "status";
    public static final int PROP_ID_status = 3;
    
    /* 生成文件记录ID: GENERATED_FILE_RECORD_ID VARCHAR */
    public static final String PROP_NAME_generatedFileRecordId = "generatedFileRecordId";
    public static final int PROP_ID_generatedFileRecordId = 4;
    
    /* 已送达渠道: DELIVERED_CHANNELS VARCHAR */
    public static final String PROP_NAME_deliveredChannels = "deliveredChannels";
    public static final int PROP_ID_deliveredChannels = 5;
    
    /* 导出行数: ROW_COUNT BIGINT */
    public static final String PROP_NAME_rowCount = "rowCount";
    public static final int PROP_ID_rowCount = 6;
    
    /* 错误信息: ERROR_MSG VARCHAR */
    public static final String PROP_NAME_errorMsg = "errorMsg";
    public static final int PROP_ID_errorMsg = 7;
    
    /* 触发来源: TRIGGERED_BY VARCHAR */
    public static final String PROP_NAME_triggeredBy = "triggeredBy";
    public static final int PROP_ID_triggeredBy = 8;
    
    /* 开始时间: START_TIME TIMESTAMP */
    public static final String PROP_NAME_startTime = "startTime";
    public static final int PROP_ID_startTime = 9;
    
    /* 结束时间: END_TIME TIMESTAMP */
    public static final String PROP_NAME_endTime = "endTime";
    public static final int PROP_ID_endTime = 10;
    
    /* 删除标记: DEL_FLAG TINYINT */
    public static final String PROP_NAME_delFlag = "delFlag";
    public static final int PROP_ID_delFlag = 11;
    
    /* 数据版本: VERSION BIGINT */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 12;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 13;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 14;
    
    /* 修改人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 15;
    
    /* 修改时间: UPDATE_TIME TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 16;
    
    /* 备注: REMARK VARCHAR */
    public static final String PROP_NAME_remark = "remark";
    public static final int PROP_ID_remark = 17;
    

    private static int _PROP_ID_BOUND = 18;

    
    /* relation: 报告任务 */
    public static final String PROP_NAME_reportTask = "reportTask";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_deliveryId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_deliveryId};

    private static final String[] PROP_ID_TO_NAME = new String[18];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_deliveryId] = PROP_NAME_deliveryId;
          PROP_NAME_TO_ID.put(PROP_NAME_deliveryId, PROP_ID_deliveryId);
      
          PROP_ID_TO_NAME[PROP_ID_reportTaskId] = PROP_NAME_reportTaskId;
          PROP_NAME_TO_ID.put(PROP_NAME_reportTaskId, PROP_ID_reportTaskId);
      
          PROP_ID_TO_NAME[PROP_ID_status] = PROP_NAME_status;
          PROP_NAME_TO_ID.put(PROP_NAME_status, PROP_ID_status);
      
          PROP_ID_TO_NAME[PROP_ID_generatedFileRecordId] = PROP_NAME_generatedFileRecordId;
          PROP_NAME_TO_ID.put(PROP_NAME_generatedFileRecordId, PROP_ID_generatedFileRecordId);
      
          PROP_ID_TO_NAME[PROP_ID_deliveredChannels] = PROP_NAME_deliveredChannels;
          PROP_NAME_TO_ID.put(PROP_NAME_deliveredChannels, PROP_ID_deliveredChannels);
      
          PROP_ID_TO_NAME[PROP_ID_rowCount] = PROP_NAME_rowCount;
          PROP_NAME_TO_ID.put(PROP_NAME_rowCount, PROP_ID_rowCount);
      
          PROP_ID_TO_NAME[PROP_ID_errorMsg] = PROP_NAME_errorMsg;
          PROP_NAME_TO_ID.put(PROP_NAME_errorMsg, PROP_ID_errorMsg);
      
          PROP_ID_TO_NAME[PROP_ID_triggeredBy] = PROP_NAME_triggeredBy;
          PROP_NAME_TO_ID.put(PROP_NAME_triggeredBy, PROP_ID_triggeredBy);
      
          PROP_ID_TO_NAME[PROP_ID_startTime] = PROP_NAME_startTime;
          PROP_NAME_TO_ID.put(PROP_NAME_startTime, PROP_ID_startTime);
      
          PROP_ID_TO_NAME[PROP_ID_endTime] = PROP_NAME_endTime;
          PROP_NAME_TO_ID.put(PROP_NAME_endTime, PROP_ID_endTime);
      
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

    
    /* 交付ID: DELIVERY_ID */
    private java.lang.String _deliveryId;
    
    /* 报告任务ID: REPORT_TASK_ID */
    private java.lang.String _reportTaskId;
    
    /* 交付状态: STATUS */
    private java.lang.Integer _status;
    
    /* 生成文件记录ID: GENERATED_FILE_RECORD_ID */
    private java.lang.String _generatedFileRecordId;
    
    /* 已送达渠道: DELIVERED_CHANNELS */
    private java.lang.String _deliveredChannels;
    
    /* 导出行数: ROW_COUNT */
    private java.lang.Long _rowCount;
    
    /* 错误信息: ERROR_MSG */
    private java.lang.String _errorMsg;
    
    /* 触发来源: TRIGGERED_BY */
    private java.lang.String _triggeredBy;
    
    /* 开始时间: START_TIME */
    private java.sql.Timestamp _startTime;
    
    /* 结束时间: END_TIME */
    private java.sql.Timestamp _endTime;
    
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
    

    public _NopDatavReportDelivery(){
        // for debug
    }

    protected NopDatavReportDelivery newInstance(){
        NopDatavReportDelivery entity = new NopDatavReportDelivery();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopDatavReportDelivery cloneInstance() {
        NopDatavReportDelivery entity = newInstance();
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
      return "io.nop.datav.dao.entity.NopDatavReportDelivery";
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
    
        return buildSimpleId(PROP_ID_deliveryId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_deliveryId;
          
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
        
            case PROP_ID_deliveryId:
               return getDeliveryId();
        
            case PROP_ID_reportTaskId:
               return getReportTaskId();
        
            case PROP_ID_status:
               return getStatus();
        
            case PROP_ID_generatedFileRecordId:
               return getGeneratedFileRecordId();
        
            case PROP_ID_deliveredChannels:
               return getDeliveredChannels();
        
            case PROP_ID_rowCount:
               return getRowCount();
        
            case PROP_ID_errorMsg:
               return getErrorMsg();
        
            case PROP_ID_triggeredBy:
               return getTriggeredBy();
        
            case PROP_ID_startTime:
               return getStartTime();
        
            case PROP_ID_endTime:
               return getEndTime();
        
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
        
            case PROP_ID_deliveryId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_deliveryId));
               }
               setDeliveryId(typedValue);
               break;
            }
        
            case PROP_ID_reportTaskId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_reportTaskId));
               }
               setReportTaskId(typedValue);
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
        
            case PROP_ID_generatedFileRecordId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_generatedFileRecordId));
               }
               setGeneratedFileRecordId(typedValue);
               break;
            }
        
            case PROP_ID_deliveredChannels:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_deliveredChannels));
               }
               setDeliveredChannels(typedValue);
               break;
            }
        
            case PROP_ID_rowCount:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_rowCount));
               }
               setRowCount(typedValue);
               break;
            }
        
            case PROP_ID_errorMsg:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_errorMsg));
               }
               setErrorMsg(typedValue);
               break;
            }
        
            case PROP_ID_triggeredBy:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_triggeredBy));
               }
               setTriggeredBy(typedValue);
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
        
            case PROP_ID_deliveryId:{
               onInitProp(propId);
               this._deliveryId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_reportTaskId:{
               onInitProp(propId);
               this._reportTaskId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_status:{
               onInitProp(propId);
               this._status = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_generatedFileRecordId:{
               onInitProp(propId);
               this._generatedFileRecordId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_deliveredChannels:{
               onInitProp(propId);
               this._deliveredChannels = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_rowCount:{
               onInitProp(propId);
               this._rowCount = (java.lang.Long)value;
               
               break;
            }
        
            case PROP_ID_errorMsg:{
               onInitProp(propId);
               this._errorMsg = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_triggeredBy:{
               onInitProp(propId);
               this._triggeredBy = (java.lang.String)value;
               
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
     * 交付ID: DELIVERY_ID
     */
    public final java.lang.String getDeliveryId(){
         onPropGet(PROP_ID_deliveryId);
         return _deliveryId;
    }

    /**
     * 交付ID: DELIVERY_ID
     */
    public final void setDeliveryId(java.lang.String value){
        if(onPropSet(PROP_ID_deliveryId,value)){
            this._deliveryId = value;
            internalClearRefs(PROP_ID_deliveryId);
            orm_id();
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
            
        }
    }
    
    /**
     * 交付状态: STATUS
     */
    public final java.lang.Integer getStatus(){
         onPropGet(PROP_ID_status);
         return _status;
    }

    /**
     * 交付状态: STATUS
     */
    public final void setStatus(java.lang.Integer value){
        if(onPropSet(PROP_ID_status,value)){
            this._status = value;
            internalClearRefs(PROP_ID_status);
            
        }
    }
    
    /**
     * 生成文件记录ID: GENERATED_FILE_RECORD_ID
     */
    public final java.lang.String getGeneratedFileRecordId(){
         onPropGet(PROP_ID_generatedFileRecordId);
         return _generatedFileRecordId;
    }

    /**
     * 生成文件记录ID: GENERATED_FILE_RECORD_ID
     */
    public final void setGeneratedFileRecordId(java.lang.String value){
        if(onPropSet(PROP_ID_generatedFileRecordId,value)){
            this._generatedFileRecordId = value;
            internalClearRefs(PROP_ID_generatedFileRecordId);
            
        }
    }
    
    /**
     * 已送达渠道: DELIVERED_CHANNELS
     */
    public final java.lang.String getDeliveredChannels(){
         onPropGet(PROP_ID_deliveredChannels);
         return _deliveredChannels;
    }

    /**
     * 已送达渠道: DELIVERED_CHANNELS
     */
    public final void setDeliveredChannels(java.lang.String value){
        if(onPropSet(PROP_ID_deliveredChannels,value)){
            this._deliveredChannels = value;
            internalClearRefs(PROP_ID_deliveredChannels);
            
        }
    }
    
    /**
     * 导出行数: ROW_COUNT
     */
    public final java.lang.Long getRowCount(){
         onPropGet(PROP_ID_rowCount);
         return _rowCount;
    }

    /**
     * 导出行数: ROW_COUNT
     */
    public final void setRowCount(java.lang.Long value){
        if(onPropSet(PROP_ID_rowCount,value)){
            this._rowCount = value;
            internalClearRefs(PROP_ID_rowCount);
            
        }
    }
    
    /**
     * 错误信息: ERROR_MSG
     */
    public final java.lang.String getErrorMsg(){
         onPropGet(PROP_ID_errorMsg);
         return _errorMsg;
    }

    /**
     * 错误信息: ERROR_MSG
     */
    public final void setErrorMsg(java.lang.String value){
        if(onPropSet(PROP_ID_errorMsg,value)){
            this._errorMsg = value;
            internalClearRefs(PROP_ID_errorMsg);
            
        }
    }
    
    /**
     * 触发来源: TRIGGERED_BY
     */
    public final java.lang.String getTriggeredBy(){
         onPropGet(PROP_ID_triggeredBy);
         return _triggeredBy;
    }

    /**
     * 触发来源: TRIGGERED_BY
     */
    public final void setTriggeredBy(java.lang.String value){
        if(onPropSet(PROP_ID_triggeredBy,value)){
            this._triggeredBy = value;
            internalClearRefs(PROP_ID_triggeredBy);
            
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
     * 报告任务
     */
    public final io.nop.datav.dao.entity.NopDatavReportTask getReportTask(){
       return (io.nop.datav.dao.entity.NopDatavReportTask)internalGetRefEntity(PROP_NAME_reportTask);
    }

    public final void setReportTask(io.nop.datav.dao.entity.NopDatavReportTask refEntity){
   
           if(refEntity == null){
           
                   this.setReportTaskId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_reportTask, refEntity,()->{
           
                           this.setReportTaskId(refEntity.getReportTaskId());
                       
           });
           }
       
    }
       
}
// resume CPD analysis - CPD-ON
