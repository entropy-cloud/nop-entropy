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

import io.nop.ai.dao.entity.NopAiEvent;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  Agent事件: nop_ai_event
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopAiEvent extends DynamicOrmEntity{
    
    /* 主键: id VARCHAR */
    public static final String PROP_NAME_id = "id";
    public static final int PROP_ID_id = 1;
    
    /* 会话ID: session_id VARCHAR */
    public static final String PROP_NAME_sessionId = "sessionId";
    public static final int PROP_ID_sessionId = 2;
    
    /* 序号: seq BIGINT */
    public static final String PROP_NAME_seq = "seq";
    public static final int PROP_ID_seq = 3;
    
    /* 事件类型: event_type INTEGER */
    public static final String PROP_NAME_eventType = "eventType";
    public static final int PROP_ID_eventType = 4;
    
    /* 事件数据: data CLOB */
    public static final String PROP_NAME_data = "data";
    public static final int PROP_ID_data = 5;
    
    /* 创建人: created_by VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 6;
    
    /* 创建时间: create_time TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 7;
    

    private static int _PROP_ID_BOUND = 8;

    
    /* relation:  */
    public static final String PROP_NAME_session = "session";
    
    /* component:  */
    public static final String PROP_NAME_dataComponent = "dataComponent";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_id);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_id};

    private static final String[] PROP_ID_TO_NAME = new String[8];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_id] = PROP_NAME_id;
          PROP_NAME_TO_ID.put(PROP_NAME_id, PROP_ID_id);
      
          PROP_ID_TO_NAME[PROP_ID_sessionId] = PROP_NAME_sessionId;
          PROP_NAME_TO_ID.put(PROP_NAME_sessionId, PROP_ID_sessionId);
      
          PROP_ID_TO_NAME[PROP_ID_seq] = PROP_NAME_seq;
          PROP_NAME_TO_ID.put(PROP_NAME_seq, PROP_ID_seq);
      
          PROP_ID_TO_NAME[PROP_ID_eventType] = PROP_NAME_eventType;
          PROP_NAME_TO_ID.put(PROP_NAME_eventType, PROP_ID_eventType);
      
          PROP_ID_TO_NAME[PROP_ID_data] = PROP_NAME_data;
          PROP_NAME_TO_ID.put(PROP_NAME_data, PROP_ID_data);
      
          PROP_ID_TO_NAME[PROP_ID_createdBy] = PROP_NAME_createdBy;
          PROP_NAME_TO_ID.put(PROP_NAME_createdBy, PROP_ID_createdBy);
      
          PROP_ID_TO_NAME[PROP_ID_createTime] = PROP_NAME_createTime;
          PROP_NAME_TO_ID.put(PROP_NAME_createTime, PROP_ID_createTime);
      
    }

    
    /* 主键: id */
    private java.lang.String _id;
    
    /* 会话ID: session_id */
    private java.lang.String _sessionId;
    
    /* 序号: seq */
    private java.lang.Long _seq;
    
    /* 事件类型: event_type */
    private java.lang.Integer _eventType;
    
    /* 事件数据: data */
    private java.lang.String _data;
    
    /* 创建人: created_by */
    private java.lang.String _createdBy;
    
    /* 创建时间: create_time */
    private java.sql.Timestamp _createTime;
    

    public _NopAiEvent(){
        // for debug
    }

    protected NopAiEvent newInstance(){
        NopAiEvent entity = new NopAiEvent();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopAiEvent cloneInstance() {
        NopAiEvent entity = newInstance();
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
      return "io.nop.ai.dao.entity.NopAiEvent";
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
        
            case PROP_ID_seq:
               return getSeq();
        
            case PROP_ID_eventType:
               return getEventType();
        
            case PROP_ID_data:
               return getData();
        
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
        
            case PROP_ID_seq:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_seq));
               }
               setSeq(typedValue);
               break;
            }
        
            case PROP_ID_eventType:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_eventType));
               }
               setEventType(typedValue);
               break;
            }
        
            case PROP_ID_data:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_data));
               }
               setData(typedValue);
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
        
            case PROP_ID_seq:{
               onInitProp(propId);
               this._seq = (java.lang.Long)value;
               
               break;
            }
        
            case PROP_ID_eventType:{
               onInitProp(propId);
               this._eventType = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_data:{
               onInitProp(propId);
               this._data = (java.lang.String)value;
               
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
     * 序号: seq
     */
    public final java.lang.Long getSeq(){
         onPropGet(PROP_ID_seq);
         return _seq;
    }

    /**
     * 序号: seq
     */
    public final void setSeq(java.lang.Long value){
        if(onPropSet(PROP_ID_seq,value)){
            this._seq = value;
            internalClearRefs(PROP_ID_seq);
            
        }
    }
    
    /**
     * 事件类型: event_type
     */
    public final java.lang.Integer getEventType(){
         onPropGet(PROP_ID_eventType);
         return _eventType;
    }

    /**
     * 事件类型: event_type
     */
    public final void setEventType(java.lang.Integer value){
        if(onPropSet(PROP_ID_eventType,value)){
            this._eventType = value;
            internalClearRefs(PROP_ID_eventType);
            
        }
    }
    
    /**
     * 事件数据: data
     */
    public final java.lang.String getData(){
         onPropGet(PROP_ID_data);
         return _data;
    }

    /**
     * 事件数据: data
     */
    public final void setData(java.lang.String value){
        if(onPropSet(PROP_ID_data,value)){
            this._data = value;
            internalClearRefs(PROP_ID_data);
            
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
       
   private io.nop.orm.component.JsonOrmComponent _dataComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_dataComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_dataComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_data);
      
   }

   public final io.nop.orm.component.JsonOrmComponent getDataComponent(){
      if(_dataComponent == null){
          _dataComponent = new io.nop.orm.component.JsonOrmComponent();
          _dataComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_dataComponent);
      }
      return _dataComponent;
   }

}
// resume CPD analysis - CPD-ON
