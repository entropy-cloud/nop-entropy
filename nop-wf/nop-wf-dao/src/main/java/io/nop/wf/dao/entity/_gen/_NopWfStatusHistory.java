package io.nop.wf.dao.entity._gen;

import io.nop.orm.model.IEntityModel;
import io.nop.orm.support.DynamicOrmEntity;
import io.nop.orm.support.OrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.orm.IOrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.api.core.convert.ConvertHelper;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;

import io.nop.wf.dao.entity.NopWfStatusHistory;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  工作流状态变迁历史: nop_wf_status_history
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopWfStatusHistory extends DynamicOrmEntity{
    
    /* 主键: SID VARCHAR */
    public static final String PROP_NAME_sid = "sid";
    public static final int PROP_ID_sid = 1;
    
    /* 主键: WF_ID VARCHAR */
    public static final String PROP_NAME_wfId = "wfId";
    public static final int PROP_ID_wfId = 2;
    
    /* 源状态: FROM_STATUS INTEGER */
    public static final String PROP_NAME_fromStatus = "fromStatus";
    public static final int PROP_ID_fromStatus = 3;
    
    /* 目标状态: TO_STATUS INTEGER */
    public static final String PROP_NAME_toStatus = "toStatus";
    public static final int PROP_ID_toStatus = 4;
    
    /* 目标应用状态: TO_APP_STATE VARCHAR */
    public static final String PROP_NAME_toAppState = "toAppState";
    public static final int PROP_ID_toAppState = 6;
    
    /* 状态变动时间: CHANGE_TIME TIMESTAMP */
    public static final String PROP_NAME_changeTime = "changeTime";
    public static final int PROP_ID_changeTime = 7;
    
    /* 操作者ID: OPERATOR_ID VARCHAR */
    public static final String PROP_NAME_operatorId = "operatorId";
    public static final int PROP_ID_operatorId = 8;
    
    /* 操作者: OPERATOR_NAME VARCHAR */
    public static final String PROP_NAME_operatorName = "operatorName";
    public static final int PROP_ID_operatorName = 9;
    
    /* 操作者部门ID: OPERATOR_DEPT_ID VARCHAR */
    public static final String PROP_NAME_operatorDeptId = "operatorDeptId";
    public static final int PROP_ID_operatorDeptId = 10;
    
    /* 数据版本: VERSION INTEGER */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 11;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 12;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 13;
    

    private static int _PROP_ID_BOUND = 14;

    
    /* relation: 工作流实例 */
    public static final String PROP_NAME_wfInstance = "wfInstance";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_sid);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_sid};

    private static final String[] PROP_ID_TO_NAME = new String[14];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_sid] = PROP_NAME_sid;
          PROP_NAME_TO_ID.put(PROP_NAME_sid, PROP_ID_sid);
      
          PROP_ID_TO_NAME[PROP_ID_wfId] = PROP_NAME_wfId;
          PROP_NAME_TO_ID.put(PROP_NAME_wfId, PROP_ID_wfId);
      
          PROP_ID_TO_NAME[PROP_ID_fromStatus] = PROP_NAME_fromStatus;
          PROP_NAME_TO_ID.put(PROP_NAME_fromStatus, PROP_ID_fromStatus);
      
          PROP_ID_TO_NAME[PROP_ID_toStatus] = PROP_NAME_toStatus;
          PROP_NAME_TO_ID.put(PROP_NAME_toStatus, PROP_ID_toStatus);
      
          PROP_ID_TO_NAME[PROP_ID_toAppState] = PROP_NAME_toAppState;
          PROP_NAME_TO_ID.put(PROP_NAME_toAppState, PROP_ID_toAppState);
      
          PROP_ID_TO_NAME[PROP_ID_changeTime] = PROP_NAME_changeTime;
          PROP_NAME_TO_ID.put(PROP_NAME_changeTime, PROP_ID_changeTime);
      
          PROP_ID_TO_NAME[PROP_ID_operatorId] = PROP_NAME_operatorId;
          PROP_NAME_TO_ID.put(PROP_NAME_operatorId, PROP_ID_operatorId);
      
          PROP_ID_TO_NAME[PROP_ID_operatorName] = PROP_NAME_operatorName;
          PROP_NAME_TO_ID.put(PROP_NAME_operatorName, PROP_ID_operatorName);
      
          PROP_ID_TO_NAME[PROP_ID_operatorDeptId] = PROP_NAME_operatorDeptId;
          PROP_NAME_TO_ID.put(PROP_NAME_operatorDeptId, PROP_ID_operatorDeptId);
      
          PROP_ID_TO_NAME[PROP_ID_version] = PROP_NAME_version;
          PROP_NAME_TO_ID.put(PROP_NAME_version, PROP_ID_version);
      
          PROP_ID_TO_NAME[PROP_ID_createdBy] = PROP_NAME_createdBy;
          PROP_NAME_TO_ID.put(PROP_NAME_createdBy, PROP_ID_createdBy);
      
          PROP_ID_TO_NAME[PROP_ID_createTime] = PROP_NAME_createTime;
          PROP_NAME_TO_ID.put(PROP_NAME_createTime, PROP_ID_createTime);
      
    }

    
    /* 主键: SID */
    private java.lang.String _sid;
    
    /* 主键: WF_ID */
    private java.lang.String _wfId;
    
    /* 源状态: FROM_STATUS */
    private java.lang.Integer _fromStatus;
    
    /* 目标状态: TO_STATUS */
    private java.lang.Integer _toStatus;
    
    /* 目标应用状态: TO_APP_STATE */
    private java.lang.String _toAppState;
    
    /* 状态变动时间: CHANGE_TIME */
    private java.sql.Timestamp _changeTime;
    
    /* 操作者ID: OPERATOR_ID */
    private java.lang.String _operatorId;
    
    /* 操作者: OPERATOR_NAME */
    private java.lang.String _operatorName;
    
    /* 操作者部门ID: OPERATOR_DEPT_ID */
    private java.lang.String _operatorDeptId;
    
    /* 数据版本: VERSION */
    private java.lang.Integer _version;
    
    /* 创建人: CREATED_BY */
    private java.lang.String _createdBy;
    
    /* 创建时间: CREATE_TIME */
    private java.sql.Timestamp _createTime;
    

    public _NopWfStatusHistory(){
        // for debug
    }

    protected NopWfStatusHistory newInstance(){
       return new NopWfStatusHistory();
    }

    @Override
    public NopWfStatusHistory cloneInstance() {
        NopWfStatusHistory entity = newInstance();
        orm_forEachInitedProp((value, propId) -> {
            entity.onInitProp(propId);
        });
        return entity;
    }

    @Override
    public String orm_entityName() {
      // 如果存在实体模型对象，则以模型对象上的设置为准
      IEntityModel entityModel = orm_entityModel();
      if(entityModel != null)
          return entityModel.getName();
      return "io.nop.wf.dao.entity.NopWfStatusHistory";
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
        
            case PROP_ID_wfId:
               return getWfId();
        
            case PROP_ID_fromStatus:
               return getFromStatus();
        
            case PROP_ID_toStatus:
               return getToStatus();
        
            case PROP_ID_toAppState:
               return getToAppState();
        
            case PROP_ID_changeTime:
               return getChangeTime();
        
            case PROP_ID_operatorId:
               return getOperatorId();
        
            case PROP_ID_operatorName:
               return getOperatorName();
        
            case PROP_ID_operatorDeptId:
               return getOperatorDeptId();
        
            case PROP_ID_version:
               return getVersion();
        
            case PROP_ID_createdBy:
               return getCreatedBy();
        
            case PROP_ID_createTime:
               return getCreateTime();
        
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
        
            case PROP_ID_wfId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_wfId));
               }
               setWfId(typedValue);
               break;
            }
        
            case PROP_ID_fromStatus:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_fromStatus));
               }
               setFromStatus(typedValue);
               break;
            }
        
            case PROP_ID_toStatus:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_toStatus));
               }
               setToStatus(typedValue);
               break;
            }
        
            case PROP_ID_toAppState:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_toAppState));
               }
               setToAppState(typedValue);
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
        
            case PROP_ID_operatorId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_operatorId));
               }
               setOperatorId(typedValue);
               break;
            }
        
            case PROP_ID_operatorName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_operatorName));
               }
               setOperatorName(typedValue);
               break;
            }
        
            case PROP_ID_operatorDeptId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_operatorDeptId));
               }
               setOperatorDeptId(typedValue);
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
        
            case PROP_ID_wfId:{
               onInitProp(propId);
               this._wfId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_fromStatus:{
               onInitProp(propId);
               this._fromStatus = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_toStatus:{
               onInitProp(propId);
               this._toStatus = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_toAppState:{
               onInitProp(propId);
               this._toAppState = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_changeTime:{
               onInitProp(propId);
               this._changeTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_operatorId:{
               onInitProp(propId);
               this._operatorId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_operatorName:{
               onInitProp(propId);
               this._operatorName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_operatorDeptId:{
               onInitProp(propId);
               this._operatorDeptId = (java.lang.String)value;
               
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
        
           default:
              super.orm_internalSet(propId,value);
        }
    }

    
    /**
     * 主键: SID
     */
    public java.lang.String getSid(){
         onPropGet(PROP_ID_sid);
         return _sid;
    }

    /**
     * 主键: SID
     */
    public void setSid(java.lang.String value){
        if(onPropSet(PROP_ID_sid,value)){
            this._sid = value;
            internalClearRefs(PROP_ID_sid);
            orm_id();
        }
    }
    
    /**
     * 主键: WF_ID
     */
    public java.lang.String getWfId(){
         onPropGet(PROP_ID_wfId);
         return _wfId;
    }

    /**
     * 主键: WF_ID
     */
    public void setWfId(java.lang.String value){
        if(onPropSet(PROP_ID_wfId,value)){
            this._wfId = value;
            internalClearRefs(PROP_ID_wfId);
            
        }
    }
    
    /**
     * 源状态: FROM_STATUS
     */
    public java.lang.Integer getFromStatus(){
         onPropGet(PROP_ID_fromStatus);
         return _fromStatus;
    }

    /**
     * 源状态: FROM_STATUS
     */
    public void setFromStatus(java.lang.Integer value){
        if(onPropSet(PROP_ID_fromStatus,value)){
            this._fromStatus = value;
            internalClearRefs(PROP_ID_fromStatus);
            
        }
    }
    
    /**
     * 目标状态: TO_STATUS
     */
    public java.lang.Integer getToStatus(){
         onPropGet(PROP_ID_toStatus);
         return _toStatus;
    }

    /**
     * 目标状态: TO_STATUS
     */
    public void setToStatus(java.lang.Integer value){
        if(onPropSet(PROP_ID_toStatus,value)){
            this._toStatus = value;
            internalClearRefs(PROP_ID_toStatus);
            
        }
    }
    
    /**
     * 目标应用状态: TO_APP_STATE
     */
    public java.lang.String getToAppState(){
         onPropGet(PROP_ID_toAppState);
         return _toAppState;
    }

    /**
     * 目标应用状态: TO_APP_STATE
     */
    public void setToAppState(java.lang.String value){
        if(onPropSet(PROP_ID_toAppState,value)){
            this._toAppState = value;
            internalClearRefs(PROP_ID_toAppState);
            
        }
    }
    
    /**
     * 状态变动时间: CHANGE_TIME
     */
    public java.sql.Timestamp getChangeTime(){
         onPropGet(PROP_ID_changeTime);
         return _changeTime;
    }

    /**
     * 状态变动时间: CHANGE_TIME
     */
    public void setChangeTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_changeTime,value)){
            this._changeTime = value;
            internalClearRefs(PROP_ID_changeTime);
            
        }
    }
    
    /**
     * 操作者ID: OPERATOR_ID
     */
    public java.lang.String getOperatorId(){
         onPropGet(PROP_ID_operatorId);
         return _operatorId;
    }

    /**
     * 操作者ID: OPERATOR_ID
     */
    public void setOperatorId(java.lang.String value){
        if(onPropSet(PROP_ID_operatorId,value)){
            this._operatorId = value;
            internalClearRefs(PROP_ID_operatorId);
            
        }
    }
    
    /**
     * 操作者: OPERATOR_NAME
     */
    public java.lang.String getOperatorName(){
         onPropGet(PROP_ID_operatorName);
         return _operatorName;
    }

    /**
     * 操作者: OPERATOR_NAME
     */
    public void setOperatorName(java.lang.String value){
        if(onPropSet(PROP_ID_operatorName,value)){
            this._operatorName = value;
            internalClearRefs(PROP_ID_operatorName);
            
        }
    }
    
    /**
     * 操作者部门ID: OPERATOR_DEPT_ID
     */
    public java.lang.String getOperatorDeptId(){
         onPropGet(PROP_ID_operatorDeptId);
         return _operatorDeptId;
    }

    /**
     * 操作者部门ID: OPERATOR_DEPT_ID
     */
    public void setOperatorDeptId(java.lang.String value){
        if(onPropSet(PROP_ID_operatorDeptId,value)){
            this._operatorDeptId = value;
            internalClearRefs(PROP_ID_operatorDeptId);
            
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
     * 工作流实例
     */
    public io.nop.wf.dao.entity.NopWfInstance getWfInstance(){
       return (io.nop.wf.dao.entity.NopWfInstance)internalGetRefEntity(PROP_NAME_wfInstance);
    }

    public void setWfInstance(io.nop.wf.dao.entity.NopWfInstance refEntity){
   
           if(refEntity == null){
           
                   this.setWfId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_wfInstance, refEntity,()->{
           
                           this.setWfId(refEntity.getWfId());
                       
           });
           }
       
    }
       
}
// resume CPD analysis - CPD-ON
