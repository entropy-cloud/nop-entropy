package io.nop.ai.dao.entity._gen;

import io.nop.orm.model.IEntityModel;
import io.nop.orm.support.DynamicOrmEntity;
import io.nop.orm.support.OrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.orm.IOrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code

import io.nop.api.core.convert.ConvertHelper;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;

import io.nop.ai.dao.entity.NopAiTodo;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  Agent待办: nop_ai_todo
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopAiTodo extends DynamicOrmEntity{
    
    /* 主键: id VARCHAR */
    public static final String PROP_NAME_id = "id";
    public static final int PROP_ID_id = 1;
    
    /* 会话ID: session_id VARCHAR */
    public static final String PROP_NAME_sessionId = "sessionId";
    public static final int PROP_ID_sessionId = 2;
    
    /* 计划ID: plan_id VARCHAR */
    public static final String PROP_NAME_planId = "planId";
    public static final int PROP_ID_planId = 3;
    
    /* 待办内容: content VARCHAR */
    public static final String PROP_NAME_content = "content";
    public static final int PROP_ID_content = 4;
    
    /* 状态: status INTEGER */
    public static final String PROP_NAME_status = "status";
    public static final int PROP_ID_status = 5;
    
    /* 优先级: priority INTEGER */
    public static final String PROP_NAME_priority = "priority";
    public static final int PROP_ID_priority = 6;
    
    /* 位置: position INTEGER */
    public static final String PROP_NAME_position = "position";
    public static final int PROP_ID_position = 7;
    
    /* 依赖项: depends_on CLOB */
    public static final String PROP_NAME_dependsOn = "dependsOn";
    public static final int PROP_ID_dependsOn = 8;
    
    /* 数据版本: version INTEGER */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 9;
    
    /* 创建人: created_by VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 10;
    
    /* 创建时间: create_time TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 11;
    
    /* 修改人: updated_by VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 12;
    
    /* 修改时间: update_time TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 13;
    

    private static int _PROP_ID_BOUND = 14;

    
    /* relation:  */
    public static final String PROP_NAME_session = "session";
    
    /* component:  */
    public static final String PROP_NAME_dependsOnComponent = "dependsOnComponent";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_id);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_id};

    private static final String[] PROP_ID_TO_NAME = new String[14];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_id] = PROP_NAME_id;
          PROP_NAME_TO_ID.put(PROP_NAME_id, PROP_ID_id);
      
          PROP_ID_TO_NAME[PROP_ID_sessionId] = PROP_NAME_sessionId;
          PROP_NAME_TO_ID.put(PROP_NAME_sessionId, PROP_ID_sessionId);
      
          PROP_ID_TO_NAME[PROP_ID_planId] = PROP_NAME_planId;
          PROP_NAME_TO_ID.put(PROP_NAME_planId, PROP_ID_planId);
      
          PROP_ID_TO_NAME[PROP_ID_content] = PROP_NAME_content;
          PROP_NAME_TO_ID.put(PROP_NAME_content, PROP_ID_content);
      
          PROP_ID_TO_NAME[PROP_ID_status] = PROP_NAME_status;
          PROP_NAME_TO_ID.put(PROP_NAME_status, PROP_ID_status);
      
          PROP_ID_TO_NAME[PROP_ID_priority] = PROP_NAME_priority;
          PROP_NAME_TO_ID.put(PROP_NAME_priority, PROP_ID_priority);
      
          PROP_ID_TO_NAME[PROP_ID_position] = PROP_NAME_position;
          PROP_NAME_TO_ID.put(PROP_NAME_position, PROP_ID_position);
      
          PROP_ID_TO_NAME[PROP_ID_dependsOn] = PROP_NAME_dependsOn;
          PROP_NAME_TO_ID.put(PROP_NAME_dependsOn, PROP_ID_dependsOn);
      
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
      
    }

    
    /* 主键: id */
    private java.lang.String _id;
    
    /* 会话ID: session_id */
    private java.lang.String _sessionId;
    
    /* 计划ID: plan_id */
    private java.lang.String _planId;
    
    /* 待办内容: content */
    private java.lang.String _content;
    
    /* 状态: status */
    private java.lang.Integer _status;
    
    /* 优先级: priority */
    private java.lang.Integer _priority;
    
    /* 位置: position */
    private java.lang.Integer _position;
    
    /* 依赖项: depends_on */
    private java.lang.String _dependsOn;
    
    /* 数据版本: version */
    private java.lang.Integer _version;
    
    /* 创建人: created_by */
    private java.lang.String _createdBy;
    
    /* 创建时间: create_time */
    private java.sql.Timestamp _createTime;
    
    /* 修改人: updated_by */
    private java.lang.String _updatedBy;
    
    /* 修改时间: update_time */
    private java.sql.Timestamp _updateTime;
    

    public _NopAiTodo(){
        // for debug
    }

    protected NopAiTodo newInstance(){
        NopAiTodo entity = new NopAiTodo();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopAiTodo cloneInstance() {
        NopAiTodo entity = newInstance();
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
      return "io.nop.ai.dao.entity.NopAiTodo";
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
    
        return buildSimpleId(PROP_ID_id);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_id;
          
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
        
            case PROP_ID_id:
               return getId();
        
            case PROP_ID_sessionId:
               return getSessionId();
        
            case PROP_ID_planId:
               return getPlanId();
        
            case PROP_ID_content:
               return getContent();
        
            case PROP_ID_status:
               return getStatus();
        
            case PROP_ID_priority:
               return getPriority();
        
            case PROP_ID_position:
               return getPosition();
        
            case PROP_ID_dependsOn:
               return getDependsOn();
        
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
        
           default:
              return super.orm_propValue(propId);
        }
    }

    

    @Override
    public void orm_propValue(int propId, Object value){
        switch(propId){
        
            case PROP_ID_id:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_id));
               }
               setId(typedValue);
               break;
            }
        
            case PROP_ID_sessionId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_sessionId));
               }
               setSessionId(typedValue);
               break;
            }
        
            case PROP_ID_planId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_planId));
               }
               setPlanId(typedValue);
               break;
            }
        
            case PROP_ID_content:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_content));
               }
               setContent(typedValue);
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
        
            case PROP_ID_priority:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_priority));
               }
               setPriority(typedValue);
               break;
            }
        
            case PROP_ID_position:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_position));
               }
               setPosition(typedValue);
               break;
            }
        
            case PROP_ID_dependsOn:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_dependsOn));
               }
               setDependsOn(typedValue);
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
        
           default:
              super.orm_propValue(propId,value);
        }
    }

    @Override
    public void orm_internalSet(int propId, Object value) {
        switch(propId){
        
            case PROP_ID_id:{
               onInitProp(propId);
               this._id = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_sessionId:{
               onInitProp(propId);
               this._sessionId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_planId:{
               onInitProp(propId);
               this._planId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_content:{
               onInitProp(propId);
               this._content = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_status:{
               onInitProp(propId);
               this._status = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_priority:{
               onInitProp(propId);
               this._priority = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_position:{
               onInitProp(propId);
               this._position = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_dependsOn:{
               onInitProp(propId);
               this._dependsOn = (java.lang.String)value;
               
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
        
           default:
              super.orm_internalSet(propId,value);
        }
    }

    
    /**
     * 主键: id
     */
    public final java.lang.String getId(){
         onPropGet(PROP_ID_id);
         return _id;
    }

    /**
     * 主键: id
     */
    public final void setId(java.lang.String value){
        if(onPropSet(PROP_ID_id,value)){
            this._id = value;
            internalClearRefs(PROP_ID_id);
            orm_id();
        }
    }
    
    /**
     * 会话ID: session_id
     */
    public final java.lang.String getSessionId(){
         onPropGet(PROP_ID_sessionId);
         return _sessionId;
    }

    /**
     * 会话ID: session_id
     */
    public final void setSessionId(java.lang.String value){
        if(onPropSet(PROP_ID_sessionId,value)){
            this._sessionId = value;
            internalClearRefs(PROP_ID_sessionId);
            
        }
    }
    
    /**
     * 计划ID: plan_id
     */
    public final java.lang.String getPlanId(){
         onPropGet(PROP_ID_planId);
         return _planId;
    }

    /**
     * 计划ID: plan_id
     */
    public final void setPlanId(java.lang.String value){
        if(onPropSet(PROP_ID_planId,value)){
            this._planId = value;
            internalClearRefs(PROP_ID_planId);
            
        }
    }
    
    /**
     * 待办内容: content
     */
    public final java.lang.String getContent(){
         onPropGet(PROP_ID_content);
         return _content;
    }

    /**
     * 待办内容: content
     */
    public final void setContent(java.lang.String value){
        if(onPropSet(PROP_ID_content,value)){
            this._content = value;
            internalClearRefs(PROP_ID_content);
            
        }
    }
    
    /**
     * 状态: status
     */
    public final java.lang.Integer getStatus(){
         onPropGet(PROP_ID_status);
         return _status;
    }

    /**
     * 状态: status
     */
    public final void setStatus(java.lang.Integer value){
        if(onPropSet(PROP_ID_status,value)){
            this._status = value;
            internalClearRefs(PROP_ID_status);
            
        }
    }
    
    /**
     * 优先级: priority
     */
    public final java.lang.Integer getPriority(){
         onPropGet(PROP_ID_priority);
         return _priority;
    }

    /**
     * 优先级: priority
     */
    public final void setPriority(java.lang.Integer value){
        if(onPropSet(PROP_ID_priority,value)){
            this._priority = value;
            internalClearRefs(PROP_ID_priority);
            
        }
    }
    
    /**
     * 位置: position
     */
    public final java.lang.Integer getPosition(){
         onPropGet(PROP_ID_position);
         return _position;
    }

    /**
     * 位置: position
     */
    public final void setPosition(java.lang.Integer value){
        if(onPropSet(PROP_ID_position,value)){
            this._position = value;
            internalClearRefs(PROP_ID_position);
            
        }
    }
    
    /**
     * 依赖项: depends_on
     */
    public final java.lang.String getDependsOn(){
         onPropGet(PROP_ID_dependsOn);
         return _dependsOn;
    }

    /**
     * 依赖项: depends_on
     */
    public final void setDependsOn(java.lang.String value){
        if(onPropSet(PROP_ID_dependsOn,value)){
            this._dependsOn = value;
            internalClearRefs(PROP_ID_dependsOn);
            
        }
    }
    
    /**
     * 数据版本: version
     */
    public final java.lang.Integer getVersion(){
         onPropGet(PROP_ID_version);
         return _version;
    }

    /**
     * 数据版本: version
     */
    public final void setVersion(java.lang.Integer value){
        if(onPropSet(PROP_ID_version,value)){
            this._version = value;
            internalClearRefs(PROP_ID_version);
            
        }
    }
    
    /**
     * 创建人: created_by
     */
    public final java.lang.String getCreatedBy(){
         onPropGet(PROP_ID_createdBy);
         return _createdBy;
    }

    /**
     * 创建人: created_by
     */
    public final void setCreatedBy(java.lang.String value){
        if(onPropSet(PROP_ID_createdBy,value)){
            this._createdBy = value;
            internalClearRefs(PROP_ID_createdBy);
            
        }
    }
    
    /**
     * 创建时间: create_time
     */
    public final java.sql.Timestamp getCreateTime(){
         onPropGet(PROP_ID_createTime);
         return _createTime;
    }

    /**
     * 创建时间: create_time
     */
    public final void setCreateTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_createTime,value)){
            this._createTime = value;
            internalClearRefs(PROP_ID_createTime);
            
        }
    }
    
    /**
     * 修改人: updated_by
     */
    public final java.lang.String getUpdatedBy(){
         onPropGet(PROP_ID_updatedBy);
         return _updatedBy;
    }

    /**
     * 修改人: updated_by
     */
    public final void setUpdatedBy(java.lang.String value){
        if(onPropSet(PROP_ID_updatedBy,value)){
            this._updatedBy = value;
            internalClearRefs(PROP_ID_updatedBy);
            
        }
    }
    
    /**
     * 修改时间: update_time
     */
    public final java.sql.Timestamp getUpdateTime(){
         onPropGet(PROP_ID_updateTime);
         return _updateTime;
    }

    /**
     * 修改时间: update_time
     */
    public final void setUpdateTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_updateTime,value)){
            this._updateTime = value;
            internalClearRefs(PROP_ID_updateTime);
            
        }
    }
    
    /**
     * 
     */
    public final io.nop.ai.dao.entity.NopAiSession getSession(){
       return (io.nop.ai.dao.entity.NopAiSession)internalGetRefEntity(PROP_NAME_session);
    }

    public final void setSession(io.nop.ai.dao.entity.NopAiSession refEntity){
   
           if(refEntity == null){
           
                   this.setSessionId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_session, refEntity,()->{
           
                           this.setSessionId(refEntity.getId());
                       
           });
           }
       
    }
       
   private io.nop.orm.component.JsonOrmComponent _dependsOnComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_dependsOnComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_dependsOnComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_dependsOn);
      
   }

   public final io.nop.orm.component.JsonOrmComponent getDependsOnComponent(){
      if(_dependsOnComponent == null){
          _dependsOnComponent = new io.nop.orm.component.JsonOrmComponent();
          _dependsOnComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_dependsOnComponent);
      }
      return _dependsOnComponent;
   }

}
// resume CPD analysis - CPD-ON
