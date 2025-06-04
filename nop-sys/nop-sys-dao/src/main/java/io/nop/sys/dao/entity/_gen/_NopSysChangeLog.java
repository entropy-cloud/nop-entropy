package io.nop.sys.dao.entity._gen;

import io.nop.orm.model.IEntityModel;
import io.nop.orm.support.DynamicOrmEntity;
import io.nop.orm.support.OrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.orm.IOrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.api.core.convert.ConvertHelper;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;

import io.nop.sys.dao.entity.NopSysChangeLog;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  变更跟踪日志: nop_sys_change_log
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopSysChangeLog extends DynamicOrmEntity{
    
    /* 主键: SID VARCHAR */
    public static final String PROP_NAME_sid = "sid";
    public static final int PROP_ID_sid = 1;
    
    /* 业务对象: BIZ_OBJ_NAME VARCHAR */
    public static final String PROP_NAME_bizObjName = "bizObjName";
    public static final int PROP_ID_bizObjName = 2;
    
    /* 对象ID: OBJ_ID VARCHAR */
    public static final String PROP_NAME_objId = "objId";
    public static final int PROP_ID_objId = 3;
    
    /* 业务键: BIZ_KEY VARCHAR */
    public static final String PROP_NAME_bizKey = "bizKey";
    public static final int PROP_ID_bizKey = 4;
    
    /* 业务操作: OPERATION_NAME VARCHAR */
    public static final String PROP_NAME_operationName = "operationName";
    public static final int PROP_ID_operationName = 5;
    
    /* 属性名: PROP_NAME VARCHAR */
    public static final String PROP_NAME_propName = "propName";
    public static final int PROP_ID_propName = 6;
    
    /* 旧值: OLD_VALUE VARCHAR */
    public static final String PROP_NAME_oldValue = "oldValue";
    public static final int PROP_ID_oldValue = 7;
    
    /* 新值: NEW_VALUE VARCHAR */
    public static final String PROP_NAME_newValue = "newValue";
    public static final int PROP_ID_newValue = 8;
    
    /* 变更时间: CHANGE_TIME TIMESTAMP */
    public static final String PROP_NAME_changeTime = "changeTime";
    public static final int PROP_ID_changeTime = 9;
    
    /* 应用ID: APP_ID VARCHAR */
    public static final String PROP_NAME_appId = "appId";
    public static final int PROP_ID_appId = 10;
    
    /* 操作人: OPERATOR_ID VARCHAR */
    public static final String PROP_NAME_operatorId = "operatorId";
    public static final int PROP_ID_operatorId = 11;
    
    /* 审核人: APPROVER_ID VARCHAR */
    public static final String PROP_NAME_approverId = "approverId";
    public static final int PROP_ID_approverId = 12;
    

    private static int _PROP_ID_BOUND = 13;

    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_sid);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_sid};

    private static final String[] PROP_ID_TO_NAME = new String[13];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_sid] = PROP_NAME_sid;
          PROP_NAME_TO_ID.put(PROP_NAME_sid, PROP_ID_sid);
      
          PROP_ID_TO_NAME[PROP_ID_bizObjName] = PROP_NAME_bizObjName;
          PROP_NAME_TO_ID.put(PROP_NAME_bizObjName, PROP_ID_bizObjName);
      
          PROP_ID_TO_NAME[PROP_ID_objId] = PROP_NAME_objId;
          PROP_NAME_TO_ID.put(PROP_NAME_objId, PROP_ID_objId);
      
          PROP_ID_TO_NAME[PROP_ID_bizKey] = PROP_NAME_bizKey;
          PROP_NAME_TO_ID.put(PROP_NAME_bizKey, PROP_ID_bizKey);
      
          PROP_ID_TO_NAME[PROP_ID_operationName] = PROP_NAME_operationName;
          PROP_NAME_TO_ID.put(PROP_NAME_operationName, PROP_ID_operationName);
      
          PROP_ID_TO_NAME[PROP_ID_propName] = PROP_NAME_propName;
          PROP_NAME_TO_ID.put(PROP_NAME_propName, PROP_ID_propName);
      
          PROP_ID_TO_NAME[PROP_ID_oldValue] = PROP_NAME_oldValue;
          PROP_NAME_TO_ID.put(PROP_NAME_oldValue, PROP_ID_oldValue);
      
          PROP_ID_TO_NAME[PROP_ID_newValue] = PROP_NAME_newValue;
          PROP_NAME_TO_ID.put(PROP_NAME_newValue, PROP_ID_newValue);
      
          PROP_ID_TO_NAME[PROP_ID_changeTime] = PROP_NAME_changeTime;
          PROP_NAME_TO_ID.put(PROP_NAME_changeTime, PROP_ID_changeTime);
      
          PROP_ID_TO_NAME[PROP_ID_appId] = PROP_NAME_appId;
          PROP_NAME_TO_ID.put(PROP_NAME_appId, PROP_ID_appId);
      
          PROP_ID_TO_NAME[PROP_ID_operatorId] = PROP_NAME_operatorId;
          PROP_NAME_TO_ID.put(PROP_NAME_operatorId, PROP_ID_operatorId);
      
          PROP_ID_TO_NAME[PROP_ID_approverId] = PROP_NAME_approverId;
          PROP_NAME_TO_ID.put(PROP_NAME_approverId, PROP_ID_approverId);
      
    }

    
    /* 主键: SID */
    private java.lang.String _sid;
    
    /* 业务对象: BIZ_OBJ_NAME */
    private java.lang.String _bizObjName;
    
    /* 对象ID: OBJ_ID */
    private java.lang.String _objId;
    
    /* 业务键: BIZ_KEY */
    private java.lang.String _bizKey;
    
    /* 业务操作: OPERATION_NAME */
    private java.lang.String _operationName;
    
    /* 属性名: PROP_NAME */
    private java.lang.String _propName;
    
    /* 旧值: OLD_VALUE */
    private java.lang.String _oldValue;
    
    /* 新值: NEW_VALUE */
    private java.lang.String _newValue;
    
    /* 变更时间: CHANGE_TIME */
    private java.sql.Timestamp _changeTime;
    
    /* 应用ID: APP_ID */
    private java.lang.String _appId;
    
    /* 操作人: OPERATOR_ID */
    private java.lang.String _operatorId;
    
    /* 审核人: APPROVER_ID */
    private java.lang.String _approverId;
    

    public _NopSysChangeLog(){
        // for debug
    }

    protected NopSysChangeLog newInstance(){
        NopSysChangeLog entity = new NopSysChangeLog();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopSysChangeLog cloneInstance() {
        NopSysChangeLog entity = newInstance();
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
      return "io.nop.sys.dao.entity.NopSysChangeLog";
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
        
            case PROP_ID_bizObjName:
               return getBizObjName();
        
            case PROP_ID_objId:
               return getObjId();
        
            case PROP_ID_bizKey:
               return getBizKey();
        
            case PROP_ID_operationName:
               return getOperationName();
        
            case PROP_ID_propName:
               return getPropName();
        
            case PROP_ID_oldValue:
               return getOldValue();
        
            case PROP_ID_newValue:
               return getNewValue();
        
            case PROP_ID_changeTime:
               return getChangeTime();
        
            case PROP_ID_appId:
               return getAppId();
        
            case PROP_ID_operatorId:
               return getOperatorId();
        
            case PROP_ID_approverId:
               return getApproverId();
        
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
        
            case PROP_ID_bizObjName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_bizObjName));
               }
               setBizObjName(typedValue);
               break;
            }
        
            case PROP_ID_objId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_objId));
               }
               setObjId(typedValue);
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
        
            case PROP_ID_operationName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_operationName));
               }
               setOperationName(typedValue);
               break;
            }
        
            case PROP_ID_propName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_propName));
               }
               setPropName(typedValue);
               break;
            }
        
            case PROP_ID_oldValue:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_oldValue));
               }
               setOldValue(typedValue);
               break;
            }
        
            case PROP_ID_newValue:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_newValue));
               }
               setNewValue(typedValue);
               break;
            }
        
            case PROP_ID_changeTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_changeTime));
               }
               setChangeTime(typedValue);
               break;
            }
        
            case PROP_ID_appId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_appId));
               }
               setAppId(typedValue);
               break;
            }
        
            case PROP_ID_operatorId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_operatorId));
               }
               setOperatorId(typedValue);
               break;
            }
        
            case PROP_ID_approverId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_approverId));
               }
               setApproverId(typedValue);
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
        
            case PROP_ID_bizObjName:{
               onInitProp(propId);
               this._bizObjName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_objId:{
               onInitProp(propId);
               this._objId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_bizKey:{
               onInitProp(propId);
               this._bizKey = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_operationName:{
               onInitProp(propId);
               this._operationName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_propName:{
               onInitProp(propId);
               this._propName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_oldValue:{
               onInitProp(propId);
               this._oldValue = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_newValue:{
               onInitProp(propId);
               this._newValue = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_changeTime:{
               onInitProp(propId);
               this._changeTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_appId:{
               onInitProp(propId);
               this._appId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_operatorId:{
               onInitProp(propId);
               this._operatorId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_approverId:{
               onInitProp(propId);
               this._approverId = (java.lang.String)value;
               
               break;
            }
        
           default:
              super.orm_internalSet(propId,value);
        }
    }

    
    /**
     * 主键: SID
     */
    public final java.lang.String getSid(){
         onPropGet(PROP_ID_sid);
         return _sid;
    }

    /**
     * 主键: SID
     */
    public final void setSid(java.lang.String value){
        if(onPropSet(PROP_ID_sid,value)){
            this._sid = value;
            internalClearRefs(PROP_ID_sid);
            orm_id();
        }
    }
    
    /**
     * 业务对象: BIZ_OBJ_NAME
     */
    public final java.lang.String getBizObjName(){
         onPropGet(PROP_ID_bizObjName);
         return _bizObjName;
    }

    /**
     * 业务对象: BIZ_OBJ_NAME
     */
    public final void setBizObjName(java.lang.String value){
        if(onPropSet(PROP_ID_bizObjName,value)){
            this._bizObjName = value;
            internalClearRefs(PROP_ID_bizObjName);
            
        }
    }
    
    /**
     * 对象ID: OBJ_ID
     */
    public final java.lang.String getObjId(){
         onPropGet(PROP_ID_objId);
         return _objId;
    }

    /**
     * 对象ID: OBJ_ID
     */
    public final void setObjId(java.lang.String value){
        if(onPropSet(PROP_ID_objId,value)){
            this._objId = value;
            internalClearRefs(PROP_ID_objId);
            
        }
    }
    
    /**
     * 业务键: BIZ_KEY
     */
    public final java.lang.String getBizKey(){
         onPropGet(PROP_ID_bizKey);
         return _bizKey;
    }

    /**
     * 业务键: BIZ_KEY
     */
    public final void setBizKey(java.lang.String value){
        if(onPropSet(PROP_ID_bizKey,value)){
            this._bizKey = value;
            internalClearRefs(PROP_ID_bizKey);
            
        }
    }
    
    /**
     * 业务操作: OPERATION_NAME
     */
    public final java.lang.String getOperationName(){
         onPropGet(PROP_ID_operationName);
         return _operationName;
    }

    /**
     * 业务操作: OPERATION_NAME
     */
    public final void setOperationName(java.lang.String value){
        if(onPropSet(PROP_ID_operationName,value)){
            this._operationName = value;
            internalClearRefs(PROP_ID_operationName);
            
        }
    }
    
    /**
     * 属性名: PROP_NAME
     */
    public final java.lang.String getPropName(){
         onPropGet(PROP_ID_propName);
         return _propName;
    }

    /**
     * 属性名: PROP_NAME
     */
    public final void setPropName(java.lang.String value){
        if(onPropSet(PROP_ID_propName,value)){
            this._propName = value;
            internalClearRefs(PROP_ID_propName);
            
        }
    }
    
    /**
     * 旧值: OLD_VALUE
     */
    public final java.lang.String getOldValue(){
         onPropGet(PROP_ID_oldValue);
         return _oldValue;
    }

    /**
     * 旧值: OLD_VALUE
     */
    public final void setOldValue(java.lang.String value){
        if(onPropSet(PROP_ID_oldValue,value)){
            this._oldValue = value;
            internalClearRefs(PROP_ID_oldValue);
            
        }
    }
    
    /**
     * 新值: NEW_VALUE
     */
    public final java.lang.String getNewValue(){
         onPropGet(PROP_ID_newValue);
         return _newValue;
    }

    /**
     * 新值: NEW_VALUE
     */
    public final void setNewValue(java.lang.String value){
        if(onPropSet(PROP_ID_newValue,value)){
            this._newValue = value;
            internalClearRefs(PROP_ID_newValue);
            
        }
    }
    
    /**
     * 变更时间: CHANGE_TIME
     */
    public final java.sql.Timestamp getChangeTime(){
         onPropGet(PROP_ID_changeTime);
         return _changeTime;
    }

    /**
     * 变更时间: CHANGE_TIME
     */
    public final void setChangeTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_changeTime,value)){
            this._changeTime = value;
            internalClearRefs(PROP_ID_changeTime);
            
        }
    }
    
    /**
     * 应用ID: APP_ID
     */
    public final java.lang.String getAppId(){
         onPropGet(PROP_ID_appId);
         return _appId;
    }

    /**
     * 应用ID: APP_ID
     */
    public final void setAppId(java.lang.String value){
        if(onPropSet(PROP_ID_appId,value)){
            this._appId = value;
            internalClearRefs(PROP_ID_appId);
            
        }
    }
    
    /**
     * 操作人: OPERATOR_ID
     */
    public final java.lang.String getOperatorId(){
         onPropGet(PROP_ID_operatorId);
         return _operatorId;
    }

    /**
     * 操作人: OPERATOR_ID
     */
    public final void setOperatorId(java.lang.String value){
        if(onPropSet(PROP_ID_operatorId,value)){
            this._operatorId = value;
            internalClearRefs(PROP_ID_operatorId);
            
        }
    }
    
    /**
     * 审核人: APPROVER_ID
     */
    public final java.lang.String getApproverId(){
         onPropGet(PROP_ID_approverId);
         return _approverId;
    }

    /**
     * 审核人: APPROVER_ID
     */
    public final void setApproverId(java.lang.String value){
        if(onPropSet(PROP_ID_approverId,value)){
            this._approverId = value;
            internalClearRefs(PROP_ID_approverId);
            
        }
    }
    
}
// resume CPD analysis - CPD-ON
