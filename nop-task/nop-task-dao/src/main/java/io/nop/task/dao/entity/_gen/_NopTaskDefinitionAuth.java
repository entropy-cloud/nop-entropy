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

import io.nop.task.dao.entity.NopTaskDefinitionAuth;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  逻辑流定义权限: nop_task_definition_auth
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopTaskDefinitionAuth extends DynamicOrmEntity{
    
    /* 主键: SID VARCHAR */
    public static final String PROP_NAME_sid = "sid";
    public static final int PROP_ID_sid = 1;
    
    /* 工作流定义ID: TASK_DEF_ID VARCHAR */
    public static final String PROP_NAME_taskDefId = "taskDefId";
    public static final int PROP_ID_taskDefId = 2;
    
    /* 参与者类型: ACTOR_TYPE VARCHAR */
    public static final String PROP_NAME_actorType = "actorType";
    public static final int PROP_ID_actorType = 3;
    
    /* 参与者ID: ACTOR_ID VARCHAR */
    public static final String PROP_NAME_actorId = "actorId";
    public static final int PROP_ID_actorId = 4;
    
    /* 参与者部门ID: ACTOR_DEPT_ID VARCHAR */
    public static final String PROP_NAME_actorDeptId = "actorDeptId";
    public static final int PROP_ID_actorDeptId = 5;
    
    /* 参与者名称: ACTOR_NAME VARCHAR */
    public static final String PROP_NAME_actorName = "actorName";
    public static final int PROP_ID_actorName = 6;
    
    /* 允许编辑: ALLOW_EDIT BOOLEAN */
    public static final String PROP_NAME_allowEdit = "allowEdit";
    public static final int PROP_ID_allowEdit = 7;
    
    /* 允许管理: ALLOW_MANAGE BOOLEAN */
    public static final String PROP_NAME_allowManage = "allowManage";
    public static final int PROP_ID_allowManage = 8;
    
    /* 允许启动: ALLOW_START BOOLEAN */
    public static final String PROP_NAME_allowStart = "allowStart";
    public static final int PROP_ID_allowStart = 9;
    
    /* 数据版本: VERSION INTEGER */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 10;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 11;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 12;
    
    /* 修改人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 13;
    
    /* 修改时间: UPDATE_TIME TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 14;
    
    /* 备注: REMARK VARCHAR */
    public static final String PROP_NAME_remark = "remark";
    public static final int PROP_ID_remark = 15;
    

    private static int _PROP_ID_BOUND = 16;

    
    /* relation: 逻辑流定义 */
    public static final String PROP_NAME_taskDefinition = "taskDefinition";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_sid);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_sid};

    private static final String[] PROP_ID_TO_NAME = new String[16];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_sid] = PROP_NAME_sid;
          PROP_NAME_TO_ID.put(PROP_NAME_sid, PROP_ID_sid);
      
          PROP_ID_TO_NAME[PROP_ID_taskDefId] = PROP_NAME_taskDefId;
          PROP_NAME_TO_ID.put(PROP_NAME_taskDefId, PROP_ID_taskDefId);
      
          PROP_ID_TO_NAME[PROP_ID_actorType] = PROP_NAME_actorType;
          PROP_NAME_TO_ID.put(PROP_NAME_actorType, PROP_ID_actorType);
      
          PROP_ID_TO_NAME[PROP_ID_actorId] = PROP_NAME_actorId;
          PROP_NAME_TO_ID.put(PROP_NAME_actorId, PROP_ID_actorId);
      
          PROP_ID_TO_NAME[PROP_ID_actorDeptId] = PROP_NAME_actorDeptId;
          PROP_NAME_TO_ID.put(PROP_NAME_actorDeptId, PROP_ID_actorDeptId);
      
          PROP_ID_TO_NAME[PROP_ID_actorName] = PROP_NAME_actorName;
          PROP_NAME_TO_ID.put(PROP_NAME_actorName, PROP_ID_actorName);
      
          PROP_ID_TO_NAME[PROP_ID_allowEdit] = PROP_NAME_allowEdit;
          PROP_NAME_TO_ID.put(PROP_NAME_allowEdit, PROP_ID_allowEdit);
      
          PROP_ID_TO_NAME[PROP_ID_allowManage] = PROP_NAME_allowManage;
          PROP_NAME_TO_ID.put(PROP_NAME_allowManage, PROP_ID_allowManage);
      
          PROP_ID_TO_NAME[PROP_ID_allowStart] = PROP_NAME_allowStart;
          PROP_NAME_TO_ID.put(PROP_NAME_allowStart, PROP_ID_allowStart);
      
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

    
    /* 主键: SID */
    private java.lang.String _sid;
    
    /* 工作流定义ID: TASK_DEF_ID */
    private java.lang.String _taskDefId;
    
    /* 参与者类型: ACTOR_TYPE */
    private java.lang.String _actorType;
    
    /* 参与者ID: ACTOR_ID */
    private java.lang.String _actorId;
    
    /* 参与者部门ID: ACTOR_DEPT_ID */
    private java.lang.String _actorDeptId;
    
    /* 参与者名称: ACTOR_NAME */
    private java.lang.String _actorName;
    
    /* 允许编辑: ALLOW_EDIT */
    private java.lang.Boolean _allowEdit;
    
    /* 允许管理: ALLOW_MANAGE */
    private java.lang.Boolean _allowManage;
    
    /* 允许启动: ALLOW_START */
    private java.lang.Boolean _allowStart;
    
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
    

    public _NopTaskDefinitionAuth(){
        // for debug
    }

    protected NopTaskDefinitionAuth newInstance(){
       return new NopTaskDefinitionAuth();
    }

    @Override
    public NopTaskDefinitionAuth cloneInstance() {
        NopTaskDefinitionAuth entity = newInstance();
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
      return "io.nop.task.dao.entity.NopTaskDefinitionAuth";
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
        
            case PROP_ID_taskDefId:
               return getTaskDefId();
        
            case PROP_ID_actorType:
               return getActorType();
        
            case PROP_ID_actorId:
               return getActorId();
        
            case PROP_ID_actorDeptId:
               return getActorDeptId();
        
            case PROP_ID_actorName:
               return getActorName();
        
            case PROP_ID_allowEdit:
               return getAllowEdit();
        
            case PROP_ID_allowManage:
               return getAllowManage();
        
            case PROP_ID_allowStart:
               return getAllowStart();
        
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
        
            case PROP_ID_taskDefId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_taskDefId));
               }
               setTaskDefId(typedValue);
               break;
            }
        
            case PROP_ID_actorType:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_actorType));
               }
               setActorType(typedValue);
               break;
            }
        
            case PROP_ID_actorId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_actorId));
               }
               setActorId(typedValue);
               break;
            }
        
            case PROP_ID_actorDeptId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_actorDeptId));
               }
               setActorDeptId(typedValue);
               break;
            }
        
            case PROP_ID_actorName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_actorName));
               }
               setActorName(typedValue);
               break;
            }
        
            case PROP_ID_allowEdit:{
               java.lang.Boolean typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toBoolean(value,
                       err-> newTypeConversionError(PROP_NAME_allowEdit));
               }
               setAllowEdit(typedValue);
               break;
            }
        
            case PROP_ID_allowManage:{
               java.lang.Boolean typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toBoolean(value,
                       err-> newTypeConversionError(PROP_NAME_allowManage));
               }
               setAllowManage(typedValue);
               break;
            }
        
            case PROP_ID_allowStart:{
               java.lang.Boolean typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toBoolean(value,
                       err-> newTypeConversionError(PROP_NAME_allowStart));
               }
               setAllowStart(typedValue);
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
        
            case PROP_ID_sid:{
               onInitProp(propId);
               this._sid = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_taskDefId:{
               onInitProp(propId);
               this._taskDefId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_actorType:{
               onInitProp(propId);
               this._actorType = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_actorId:{
               onInitProp(propId);
               this._actorId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_actorDeptId:{
               onInitProp(propId);
               this._actorDeptId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_actorName:{
               onInitProp(propId);
               this._actorName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_allowEdit:{
               onInitProp(propId);
               this._allowEdit = (java.lang.Boolean)value;
               
               break;
            }
        
            case PROP_ID_allowManage:{
               onInitProp(propId);
               this._allowManage = (java.lang.Boolean)value;
               
               break;
            }
        
            case PROP_ID_allowStart:{
               onInitProp(propId);
               this._allowStart = (java.lang.Boolean)value;
               
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
     * 工作流定义ID: TASK_DEF_ID
     */
    public java.lang.String getTaskDefId(){
         onPropGet(PROP_ID_taskDefId);
         return _taskDefId;
    }

    /**
     * 工作流定义ID: TASK_DEF_ID
     */
    public void setTaskDefId(java.lang.String value){
        if(onPropSet(PROP_ID_taskDefId,value)){
            this._taskDefId = value;
            internalClearRefs(PROP_ID_taskDefId);
            
        }
    }
    
    /**
     * 参与者类型: ACTOR_TYPE
     */
    public java.lang.String getActorType(){
         onPropGet(PROP_ID_actorType);
         return _actorType;
    }

    /**
     * 参与者类型: ACTOR_TYPE
     */
    public void setActorType(java.lang.String value){
        if(onPropSet(PROP_ID_actorType,value)){
            this._actorType = value;
            internalClearRefs(PROP_ID_actorType);
            
        }
    }
    
    /**
     * 参与者ID: ACTOR_ID
     */
    public java.lang.String getActorId(){
         onPropGet(PROP_ID_actorId);
         return _actorId;
    }

    /**
     * 参与者ID: ACTOR_ID
     */
    public void setActorId(java.lang.String value){
        if(onPropSet(PROP_ID_actorId,value)){
            this._actorId = value;
            internalClearRefs(PROP_ID_actorId);
            
        }
    }
    
    /**
     * 参与者部门ID: ACTOR_DEPT_ID
     */
    public java.lang.String getActorDeptId(){
         onPropGet(PROP_ID_actorDeptId);
         return _actorDeptId;
    }

    /**
     * 参与者部门ID: ACTOR_DEPT_ID
     */
    public void setActorDeptId(java.lang.String value){
        if(onPropSet(PROP_ID_actorDeptId,value)){
            this._actorDeptId = value;
            internalClearRefs(PROP_ID_actorDeptId);
            
        }
    }
    
    /**
     * 参与者名称: ACTOR_NAME
     */
    public java.lang.String getActorName(){
         onPropGet(PROP_ID_actorName);
         return _actorName;
    }

    /**
     * 参与者名称: ACTOR_NAME
     */
    public void setActorName(java.lang.String value){
        if(onPropSet(PROP_ID_actorName,value)){
            this._actorName = value;
            internalClearRefs(PROP_ID_actorName);
            
        }
    }
    
    /**
     * 允许编辑: ALLOW_EDIT
     */
    public java.lang.Boolean getAllowEdit(){
         onPropGet(PROP_ID_allowEdit);
         return _allowEdit;
    }

    /**
     * 允许编辑: ALLOW_EDIT
     */
    public void setAllowEdit(java.lang.Boolean value){
        if(onPropSet(PROP_ID_allowEdit,value)){
            this._allowEdit = value;
            internalClearRefs(PROP_ID_allowEdit);
            
        }
    }
    
    /**
     * 允许管理: ALLOW_MANAGE
     */
    public java.lang.Boolean getAllowManage(){
         onPropGet(PROP_ID_allowManage);
         return _allowManage;
    }

    /**
     * 允许管理: ALLOW_MANAGE
     */
    public void setAllowManage(java.lang.Boolean value){
        if(onPropSet(PROP_ID_allowManage,value)){
            this._allowManage = value;
            internalClearRefs(PROP_ID_allowManage);
            
        }
    }
    
    /**
     * 允许启动: ALLOW_START
     */
    public java.lang.Boolean getAllowStart(){
         onPropGet(PROP_ID_allowStart);
         return _allowStart;
    }

    /**
     * 允许启动: ALLOW_START
     */
    public void setAllowStart(java.lang.Boolean value){
        if(onPropSet(PROP_ID_allowStart,value)){
            this._allowStart = value;
            internalClearRefs(PROP_ID_allowStart);
            
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
     * 逻辑流定义
     */
    public io.nop.task.dao.entity.NopTaskDefinition getTaskDefinition(){
       return (io.nop.task.dao.entity.NopTaskDefinition)internalGetRefEntity(PROP_NAME_taskDefinition);
    }

    public void setTaskDefinition(io.nop.task.dao.entity.NopTaskDefinition refEntity){
   
           if(refEntity == null){
           
                   this.setTaskDefId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_taskDefinition, refEntity,()->{
           
                           this.setTaskDefId(refEntity.getTaskDefId());
                       
           });
           }
       
    }
       
}
// resume CPD analysis - CPD-ON
