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

import io.nop.datav.dao.entity.NopDatavAlertState;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  告警状态: nop_datav_alert_state
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopDatavAlertState extends DynamicOrmEntity{
    
    /* 状态ID: ALERT_STATE_ID VARCHAR */
    public static final String PROP_NAME_alertStateId = "alertStateId";
    public static final int PROP_ID_alertStateId = 1;
    
    /* 告警规则ID: ALERT_RULE_ID VARCHAR */
    public static final String PROP_NAME_alertRuleId = "alertRuleId";
    public static final int PROP_ID_alertRuleId = 2;
    
    /* 告警状态: STATE VARCHAR */
    public static final String PROP_NAME_state = "state";
    public static final int PROP_ID_state = 3;
    
    /* 最后评估时间: LAST_EVAL_TIME TIMESTAMP */
    public static final String PROP_NAME_lastEvalTime = "lastEvalTime";
    public static final int PROP_ID_lastEvalTime = 4;
    
    /* 最后触发时间: LAST_TRIGGERED_TIME TIMESTAMP */
    public static final String PROP_NAME_lastTriggeredTime = "lastTriggeredTime";
    public static final int PROP_ID_lastTriggeredTime = 5;
    
    /* 最后恢复时间: LAST_RESOLVED_TIME TIMESTAMP */
    public static final String PROP_NAME_lastResolvedTime = "lastResolvedTime";
    public static final int PROP_ID_lastResolvedTime = 6;
    
    /* 最后通知时间: LAST_NOTIFIED_TIME TIMESTAMP */
    public static final String PROP_NAME_lastNotifiedTime = "lastNotifiedTime";
    public static final int PROP_ID_lastNotifiedTime = 7;
    
    /* 连续评估满足次数: CONSECUTIVE_EVAL_COUNT INTEGER */
    public static final String PROP_NAME_consecutiveEvalCount = "consecutiveEvalCount";
    public static final int PROP_ID_consecutiveEvalCount = 8;
    
    /* 错误信息: ERROR_MSG VARCHAR */
    public static final String PROP_NAME_errorMsg = "errorMsg";
    public static final int PROP_ID_errorMsg = 9;
    
    /* 删除标记: DEL_FLAG TINYINT */
    public static final String PROP_NAME_delFlag = "delFlag";
    public static final int PROP_ID_delFlag = 10;
    
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

    
    /* relation: 告警规则 */
    public static final String PROP_NAME_alertRule = "alertRule";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_alertStateId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_alertStateId};

    private static final String[] PROP_ID_TO_NAME = new String[17];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_alertStateId] = PROP_NAME_alertStateId;
          PROP_NAME_TO_ID.put(PROP_NAME_alertStateId, PROP_ID_alertStateId);
      
          PROP_ID_TO_NAME[PROP_ID_alertRuleId] = PROP_NAME_alertRuleId;
          PROP_NAME_TO_ID.put(PROP_NAME_alertRuleId, PROP_ID_alertRuleId);
      
          PROP_ID_TO_NAME[PROP_ID_state] = PROP_NAME_state;
          PROP_NAME_TO_ID.put(PROP_NAME_state, PROP_ID_state);
      
          PROP_ID_TO_NAME[PROP_ID_lastEvalTime] = PROP_NAME_lastEvalTime;
          PROP_NAME_TO_ID.put(PROP_NAME_lastEvalTime, PROP_ID_lastEvalTime);
      
          PROP_ID_TO_NAME[PROP_ID_lastTriggeredTime] = PROP_NAME_lastTriggeredTime;
          PROP_NAME_TO_ID.put(PROP_NAME_lastTriggeredTime, PROP_ID_lastTriggeredTime);
      
          PROP_ID_TO_NAME[PROP_ID_lastResolvedTime] = PROP_NAME_lastResolvedTime;
          PROP_NAME_TO_ID.put(PROP_NAME_lastResolvedTime, PROP_ID_lastResolvedTime);
      
          PROP_ID_TO_NAME[PROP_ID_lastNotifiedTime] = PROP_NAME_lastNotifiedTime;
          PROP_NAME_TO_ID.put(PROP_NAME_lastNotifiedTime, PROP_ID_lastNotifiedTime);
      
          PROP_ID_TO_NAME[PROP_ID_consecutiveEvalCount] = PROP_NAME_consecutiveEvalCount;
          PROP_NAME_TO_ID.put(PROP_NAME_consecutiveEvalCount, PROP_ID_consecutiveEvalCount);
      
          PROP_ID_TO_NAME[PROP_ID_errorMsg] = PROP_NAME_errorMsg;
          PROP_NAME_TO_ID.put(PROP_NAME_errorMsg, PROP_ID_errorMsg);
      
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

    
    /* 状态ID: ALERT_STATE_ID */
    private java.lang.String _alertStateId;
    
    /* 告警规则ID: ALERT_RULE_ID */
    private java.lang.String _alertRuleId;
    
    /* 告警状态: STATE */
    private java.lang.String _state;
    
    /* 最后评估时间: LAST_EVAL_TIME */
    private java.sql.Timestamp _lastEvalTime;
    
    /* 最后触发时间: LAST_TRIGGERED_TIME */
    private java.sql.Timestamp _lastTriggeredTime;
    
    /* 最后恢复时间: LAST_RESOLVED_TIME */
    private java.sql.Timestamp _lastResolvedTime;
    
    /* 最后通知时间: LAST_NOTIFIED_TIME */
    private java.sql.Timestamp _lastNotifiedTime;
    
    /* 连续评估满足次数: CONSECUTIVE_EVAL_COUNT */
    private java.lang.Integer _consecutiveEvalCount;
    
    /* 错误信息: ERROR_MSG */
    private java.lang.String _errorMsg;
    
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
    

    public _NopDatavAlertState(){
        // for debug
    }

    protected NopDatavAlertState newInstance(){
        NopDatavAlertState entity = new NopDatavAlertState();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopDatavAlertState cloneInstance() {
        NopDatavAlertState entity = newInstance();
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
      return "io.nop.datav.dao.entity.NopDatavAlertState";
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
    
        return buildSimpleId(PROP_ID_alertStateId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_alertStateId;
          
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
        
            case PROP_ID_alertStateId:
               return getAlertStateId();
        
            case PROP_ID_alertRuleId:
               return getAlertRuleId();
        
            case PROP_ID_state:
               return getState();
        
            case PROP_ID_lastEvalTime:
               return getLastEvalTime();
        
            case PROP_ID_lastTriggeredTime:
               return getLastTriggeredTime();
        
            case PROP_ID_lastResolvedTime:
               return getLastResolvedTime();
        
            case PROP_ID_lastNotifiedTime:
               return getLastNotifiedTime();
        
            case PROP_ID_consecutiveEvalCount:
               return getConsecutiveEvalCount();
        
            case PROP_ID_errorMsg:
               return getErrorMsg();
        
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
        
            case PROP_ID_alertStateId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_alertStateId));
               }
               setAlertStateId(typedValue);
               break;
            }
        
            case PROP_ID_alertRuleId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_alertRuleId));
               }
               setAlertRuleId(typedValue);
               break;
            }
        
            case PROP_ID_state:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_state));
               }
               setState(typedValue);
               break;
            }
        
            case PROP_ID_lastEvalTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_lastEvalTime));
               }
               setLastEvalTime(typedValue);
               break;
            }
        
            case PROP_ID_lastTriggeredTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_lastTriggeredTime));
               }
               setLastTriggeredTime(typedValue);
               break;
            }
        
            case PROP_ID_lastResolvedTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_lastResolvedTime));
               }
               setLastResolvedTime(typedValue);
               break;
            }
        
            case PROP_ID_lastNotifiedTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_lastNotifiedTime));
               }
               setLastNotifiedTime(typedValue);
               break;
            }
        
            case PROP_ID_consecutiveEvalCount:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_consecutiveEvalCount));
               }
               setConsecutiveEvalCount(typedValue);
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
        
            case PROP_ID_alertStateId:{
               onInitProp(propId);
               this._alertStateId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_alertRuleId:{
               onInitProp(propId);
               this._alertRuleId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_state:{
               onInitProp(propId);
               this._state = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_lastEvalTime:{
               onInitProp(propId);
               this._lastEvalTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_lastTriggeredTime:{
               onInitProp(propId);
               this._lastTriggeredTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_lastResolvedTime:{
               onInitProp(propId);
               this._lastResolvedTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_lastNotifiedTime:{
               onInitProp(propId);
               this._lastNotifiedTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_consecutiveEvalCount:{
               onInitProp(propId);
               this._consecutiveEvalCount = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_errorMsg:{
               onInitProp(propId);
               this._errorMsg = (java.lang.String)value;
               
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
     * 状态ID: ALERT_STATE_ID
     */
    public final java.lang.String getAlertStateId(){
         onPropGet(PROP_ID_alertStateId);
         return _alertStateId;
    }

    /**
     * 状态ID: ALERT_STATE_ID
     */
    public final void setAlertStateId(java.lang.String value){
        if(onPropSet(PROP_ID_alertStateId,value)){
            this._alertStateId = value;
            internalClearRefs(PROP_ID_alertStateId);
            orm_id();
        }
    }
    
    /**
     * 告警规则ID: ALERT_RULE_ID
     */
    public final java.lang.String getAlertRuleId(){
         onPropGet(PROP_ID_alertRuleId);
         return _alertRuleId;
    }

    /**
     * 告警规则ID: ALERT_RULE_ID
     */
    public final void setAlertRuleId(java.lang.String value){
        if(onPropSet(PROP_ID_alertRuleId,value)){
            this._alertRuleId = value;
            internalClearRefs(PROP_ID_alertRuleId);
            
        }
    }
    
    /**
     * 告警状态: STATE
     */
    public final java.lang.String getState(){
         onPropGet(PROP_ID_state);
         return _state;
    }

    /**
     * 告警状态: STATE
     */
    public final void setState(java.lang.String value){
        if(onPropSet(PROP_ID_state,value)){
            this._state = value;
            internalClearRefs(PROP_ID_state);
            
        }
    }
    
    /**
     * 最后评估时间: LAST_EVAL_TIME
     */
    public final java.sql.Timestamp getLastEvalTime(){
         onPropGet(PROP_ID_lastEvalTime);
         return _lastEvalTime;
    }

    /**
     * 最后评估时间: LAST_EVAL_TIME
     */
    public final void setLastEvalTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_lastEvalTime,value)){
            this._lastEvalTime = value;
            internalClearRefs(PROP_ID_lastEvalTime);
            
        }
    }
    
    /**
     * 最后触发时间: LAST_TRIGGERED_TIME
     */
    public final java.sql.Timestamp getLastTriggeredTime(){
         onPropGet(PROP_ID_lastTriggeredTime);
         return _lastTriggeredTime;
    }

    /**
     * 最后触发时间: LAST_TRIGGERED_TIME
     */
    public final void setLastTriggeredTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_lastTriggeredTime,value)){
            this._lastTriggeredTime = value;
            internalClearRefs(PROP_ID_lastTriggeredTime);
            
        }
    }
    
    /**
     * 最后恢复时间: LAST_RESOLVED_TIME
     */
    public final java.sql.Timestamp getLastResolvedTime(){
         onPropGet(PROP_ID_lastResolvedTime);
         return _lastResolvedTime;
    }

    /**
     * 最后恢复时间: LAST_RESOLVED_TIME
     */
    public final void setLastResolvedTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_lastResolvedTime,value)){
            this._lastResolvedTime = value;
            internalClearRefs(PROP_ID_lastResolvedTime);
            
        }
    }
    
    /**
     * 最后通知时间: LAST_NOTIFIED_TIME
     */
    public final java.sql.Timestamp getLastNotifiedTime(){
         onPropGet(PROP_ID_lastNotifiedTime);
         return _lastNotifiedTime;
    }

    /**
     * 最后通知时间: LAST_NOTIFIED_TIME
     */
    public final void setLastNotifiedTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_lastNotifiedTime,value)){
            this._lastNotifiedTime = value;
            internalClearRefs(PROP_ID_lastNotifiedTime);
            
        }
    }
    
    /**
     * 连续评估满足次数: CONSECUTIVE_EVAL_COUNT
     */
    public final java.lang.Integer getConsecutiveEvalCount(){
         onPropGet(PROP_ID_consecutiveEvalCount);
         return _consecutiveEvalCount;
    }

    /**
     * 连续评估满足次数: CONSECUTIVE_EVAL_COUNT
     */
    public final void setConsecutiveEvalCount(java.lang.Integer value){
        if(onPropSet(PROP_ID_consecutiveEvalCount,value)){
            this._consecutiveEvalCount = value;
            internalClearRefs(PROP_ID_consecutiveEvalCount);
            
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
     * 告警规则
     */
    public final io.nop.datav.dao.entity.NopDatavAlertRule getAlertRule(){
       return (io.nop.datav.dao.entity.NopDatavAlertRule)internalGetRefEntity(PROP_NAME_alertRule);
    }

    public final void setAlertRule(io.nop.datav.dao.entity.NopDatavAlertRule refEntity){
   
           if(refEntity == null){
           
                   this.setAlertRuleId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_alertRule, refEntity,()->{
           
                           this.setAlertRuleId(refEntity.getAlertRuleId());
                       
           });
           }
       
    }
       
}
// resume CPD analysis - CPD-ON
