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

import io.nop.ai.dao.entity.NopAiSessionInput;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  会话输入: nop_ai_session_input
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopAiSessionInput extends DynamicOrmEntity{
    
    /* 主键: id VARCHAR */
    public static final String PROP_NAME_id = "id";
    public static final int PROP_ID_id = 1;
    
    /* 会话ID: session_id VARCHAR */
    public static final String PROP_NAME_sessionId = "sessionId";
    public static final int PROP_ID_sessionId = 2;
    
    /* 输入提示: prompt CLOB */
    public static final String PROP_NAME_prompt = "prompt";
    public static final int PROP_ID_prompt = 3;
    
    /* 投递方式: delivery INTEGER */
    public static final String PROP_NAME_delivery = "delivery";
    public static final int PROP_ID_delivery = 4;
    
    /* 接纳序号: admitted_seq BIGINT */
    public static final String PROP_NAME_admittedSeq = "admittedSeq";
    public static final int PROP_ID_admittedSeq = 5;
    
    /* 提升序号: promoted_seq BIGINT */
    public static final String PROP_NAME_promotedSeq = "promotedSeq";
    public static final int PROP_ID_promotedSeq = 6;
    
    /* 数据版本: version INTEGER */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 7;
    
    /* 创建人: created_by VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 8;
    
    /* 创建时间: create_time TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 9;
    
    /* 修改人: updated_by VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 10;
    
    /* 修改时间: update_time TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 11;
    

    private static int _PROP_ID_BOUND = 12;

    
    /* relation:  */
    public static final String PROP_NAME_session = "session";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_id);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_id};

    private static final String[] PROP_ID_TO_NAME = new String[12];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_id] = PROP_NAME_id;
          PROP_NAME_TO_ID.put(PROP_NAME_id, PROP_ID_id);
      
          PROP_ID_TO_NAME[PROP_ID_sessionId] = PROP_NAME_sessionId;
          PROP_NAME_TO_ID.put(PROP_NAME_sessionId, PROP_ID_sessionId);
      
          PROP_ID_TO_NAME[PROP_ID_prompt] = PROP_NAME_prompt;
          PROP_NAME_TO_ID.put(PROP_NAME_prompt, PROP_ID_prompt);
      
          PROP_ID_TO_NAME[PROP_ID_delivery] = PROP_NAME_delivery;
          PROP_NAME_TO_ID.put(PROP_NAME_delivery, PROP_ID_delivery);
      
          PROP_ID_TO_NAME[PROP_ID_admittedSeq] = PROP_NAME_admittedSeq;
          PROP_NAME_TO_ID.put(PROP_NAME_admittedSeq, PROP_ID_admittedSeq);
      
          PROP_ID_TO_NAME[PROP_ID_promotedSeq] = PROP_NAME_promotedSeq;
          PROP_NAME_TO_ID.put(PROP_NAME_promotedSeq, PROP_ID_promotedSeq);
      
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
    
    /* 输入提示: prompt */
    private java.lang.String _prompt;
    
    /* 投递方式: delivery */
    private java.lang.Integer _delivery;
    
    /* 接纳序号: admitted_seq */
    private java.lang.Long _admittedSeq;
    
    /* 提升序号: promoted_seq */
    private java.lang.Long _promotedSeq;
    
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
    

    public _NopAiSessionInput(){
        // for debug
    }

    protected NopAiSessionInput newInstance(){
        NopAiSessionInput entity = new NopAiSessionInput();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopAiSessionInput cloneInstance() {
        NopAiSessionInput entity = newInstance();
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
      return "io.nop.ai.dao.entity.NopAiSessionInput";
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
        
            case PROP_ID_prompt:
               return getPrompt();
        
            case PROP_ID_delivery:
               return getDelivery();
        
            case PROP_ID_admittedSeq:
               return getAdmittedSeq();
        
            case PROP_ID_promotedSeq:
               return getPromotedSeq();
        
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
        
            case PROP_ID_prompt:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_prompt));
               }
               setPrompt(typedValue);
               break;
            }
        
            case PROP_ID_delivery:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_delivery));
               }
               setDelivery(typedValue);
               break;
            }
        
            case PROP_ID_admittedSeq:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_admittedSeq));
               }
               setAdmittedSeq(typedValue);
               break;
            }
        
            case PROP_ID_promotedSeq:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_promotedSeq));
               }
               setPromotedSeq(typedValue);
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
        
            case PROP_ID_prompt:{
               onInitProp(propId);
               this._prompt = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_delivery:{
               onInitProp(propId);
               this._delivery = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_admittedSeq:{
               onInitProp(propId);
               this._admittedSeq = (java.lang.Long)value;
               
               break;
            }
        
            case PROP_ID_promotedSeq:{
               onInitProp(propId);
               this._promotedSeq = (java.lang.Long)value;
               
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
     * 输入提示: prompt
     */
    public final java.lang.String getPrompt(){
         onPropGet(PROP_ID_prompt);
         return _prompt;
    }

    /**
     * 输入提示: prompt
     */
    public final void setPrompt(java.lang.String value){
        if(onPropSet(PROP_ID_prompt,value)){
            this._prompt = value;
            internalClearRefs(PROP_ID_prompt);
            
        }
    }
    
    /**
     * 投递方式: delivery
     */
    public final java.lang.Integer getDelivery(){
         onPropGet(PROP_ID_delivery);
         return _delivery;
    }

    /**
     * 投递方式: delivery
     */
    public final void setDelivery(java.lang.Integer value){
        if(onPropSet(PROP_ID_delivery,value)){
            this._delivery = value;
            internalClearRefs(PROP_ID_delivery);
            
        }
    }
    
    /**
     * 接纳序号: admitted_seq
     */
    public final java.lang.Long getAdmittedSeq(){
         onPropGet(PROP_ID_admittedSeq);
         return _admittedSeq;
    }

    /**
     * 接纳序号: admitted_seq
     */
    public final void setAdmittedSeq(java.lang.Long value){
        if(onPropSet(PROP_ID_admittedSeq,value)){
            this._admittedSeq = value;
            internalClearRefs(PROP_ID_admittedSeq);
            
        }
    }
    
    /**
     * 提升序号: promoted_seq
     */
    public final java.lang.Long getPromotedSeq(){
         onPropGet(PROP_ID_promotedSeq);
         return _promotedSeq;
    }

    /**
     * 提升序号: promoted_seq
     */
    public final void setPromotedSeq(java.lang.Long value){
        if(onPropSet(PROP_ID_promotedSeq,value)){
            this._promotedSeq = value;
            internalClearRefs(PROP_ID_promotedSeq);
            
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
       
}
// resume CPD analysis - CPD-ON
