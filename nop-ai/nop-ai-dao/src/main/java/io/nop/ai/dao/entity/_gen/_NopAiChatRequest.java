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

import io.nop.ai.dao.entity.NopAiChatRequest;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  对话请求: nop_ai_chat_request
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopAiChatRequest extends DynamicOrmEntity{
    
    /* 主键: id VARCHAR */
    public static final String PROP_NAME_id = "id";
    public static final int PROP_ID_id = 1;
    
    /* 模板ID: template_id VARCHAR */
    public static final String PROP_NAME_templateId = "templateId";
    public static final int PROP_ID_templateId = 2;
    
    /* 会话ID: session_id VARCHAR */
    public static final String PROP_NAME_sessionId = "sessionId";
    public static final int PROP_ID_sessionId = 3;
    
    /* 系统提示词: system_prompt VARCHAR */
    public static final String PROP_NAME_systemPrompt = "systemPrompt";
    public static final int PROP_ID_systemPrompt = 4;
    
    /* 用户提示词: user_prompt VARCHAR */
    public static final String PROP_NAME_userPrompt = "userPrompt";
    public static final int PROP_ID_userPrompt = 5;
    
    /* 消息类型: message_type INTEGER */
    public static final String PROP_NAME_messageType = "messageType";
    public static final int PROP_ID_messageType = 6;
    
    /* 请求时间戳: request_timestamp TIMESTAMP */
    public static final String PROP_NAME_requestTimestamp = "requestTimestamp";
    public static final int PROP_ID_requestTimestamp = 7;
    
    /* 内容哈希: hash VARCHAR */
    public static final String PROP_NAME_hash = "hash";
    public static final int PROP_ID_hash = 8;
    
    /* 元数据: metadata VARCHAR */
    public static final String PROP_NAME_metadata = "metadata";
    public static final int PROP_ID_metadata = 9;
    
    /* 数据版本: version INTEGER */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 10;
    
    /* 创建人: created_by VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 11;
    
    /* 创建时间: create_time TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 12;
    
    /* 修改人: updated_by VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 13;
    
    /* 修改时间: update_time TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 14;
    

    private static int _PROP_ID_BOUND = 15;

    
    /* relation:  */
    public static final String PROP_NAME_template = "template";
    
    /* relation: 响应列表 */
    public static final String PROP_NAME_responses = "responses";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_id);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_id};

    private static final String[] PROP_ID_TO_NAME = new String[15];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_id] = PROP_NAME_id;
          PROP_NAME_TO_ID.put(PROP_NAME_id, PROP_ID_id);
      
          PROP_ID_TO_NAME[PROP_ID_templateId] = PROP_NAME_templateId;
          PROP_NAME_TO_ID.put(PROP_NAME_templateId, PROP_ID_templateId);
      
          PROP_ID_TO_NAME[PROP_ID_sessionId] = PROP_NAME_sessionId;
          PROP_NAME_TO_ID.put(PROP_NAME_sessionId, PROP_ID_sessionId);
      
          PROP_ID_TO_NAME[PROP_ID_systemPrompt] = PROP_NAME_systemPrompt;
          PROP_NAME_TO_ID.put(PROP_NAME_systemPrompt, PROP_ID_systemPrompt);
      
          PROP_ID_TO_NAME[PROP_ID_userPrompt] = PROP_NAME_userPrompt;
          PROP_NAME_TO_ID.put(PROP_NAME_userPrompt, PROP_ID_userPrompt);
      
          PROP_ID_TO_NAME[PROP_ID_messageType] = PROP_NAME_messageType;
          PROP_NAME_TO_ID.put(PROP_NAME_messageType, PROP_ID_messageType);
      
          PROP_ID_TO_NAME[PROP_ID_requestTimestamp] = PROP_NAME_requestTimestamp;
          PROP_NAME_TO_ID.put(PROP_NAME_requestTimestamp, PROP_ID_requestTimestamp);
      
          PROP_ID_TO_NAME[PROP_ID_hash] = PROP_NAME_hash;
          PROP_NAME_TO_ID.put(PROP_NAME_hash, PROP_ID_hash);
      
          PROP_ID_TO_NAME[PROP_ID_metadata] = PROP_NAME_metadata;
          PROP_NAME_TO_ID.put(PROP_NAME_metadata, PROP_ID_metadata);
      
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
    
    /* 模板ID: template_id */
    private java.lang.String _templateId;
    
    /* 会话ID: session_id */
    private java.lang.String _sessionId;
    
    /* 系统提示词: system_prompt */
    private java.lang.String _systemPrompt;
    
    /* 用户提示词: user_prompt */
    private java.lang.String _userPrompt;
    
    /* 消息类型: message_type */
    private java.lang.Integer _messageType;
    
    /* 请求时间戳: request_timestamp */
    private java.sql.Timestamp _requestTimestamp;
    
    /* 内容哈希: hash */
    private java.lang.String _hash;
    
    /* 元数据: metadata */
    private java.lang.String _metadata;
    
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
    

    public _NopAiChatRequest(){
        // for debug
    }

    protected NopAiChatRequest newInstance(){
        NopAiChatRequest entity = new NopAiChatRequest();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopAiChatRequest cloneInstance() {
        NopAiChatRequest entity = newInstance();
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
      return "io.nop.ai.dao.entity.NopAiChatRequest";
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
        
            case PROP_ID_templateId:
               return getTemplateId();
        
            case PROP_ID_sessionId:
               return getSessionId();
        
            case PROP_ID_systemPrompt:
               return getSystemPrompt();
        
            case PROP_ID_userPrompt:
               return getUserPrompt();
        
            case PROP_ID_messageType:
               return getMessageType();
        
            case PROP_ID_requestTimestamp:
               return getRequestTimestamp();
        
            case PROP_ID_hash:
               return getHash();
        
            case PROP_ID_metadata:
               return getMetadata();
        
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
        
            case PROP_ID_templateId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_templateId));
               }
               setTemplateId(typedValue);
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
        
            case PROP_ID_systemPrompt:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_systemPrompt));
               }
               setSystemPrompt(typedValue);
               break;
            }
        
            case PROP_ID_userPrompt:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_userPrompt));
               }
               setUserPrompt(typedValue);
               break;
            }
        
            case PROP_ID_messageType:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_messageType));
               }
               setMessageType(typedValue);
               break;
            }
        
            case PROP_ID_requestTimestamp:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_requestTimestamp));
               }
               setRequestTimestamp(typedValue);
               break;
            }
        
            case PROP_ID_hash:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_hash));
               }
               setHash(typedValue);
               break;
            }
        
            case PROP_ID_metadata:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_metadata));
               }
               setMetadata(typedValue);
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
        
            case PROP_ID_templateId:{
               onInitProp(propId);
               this._templateId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_sessionId:{
               onInitProp(propId);
               this._sessionId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_systemPrompt:{
               onInitProp(propId);
               this._systemPrompt = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_userPrompt:{
               onInitProp(propId);
               this._userPrompt = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_messageType:{
               onInitProp(propId);
               this._messageType = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_requestTimestamp:{
               onInitProp(propId);
               this._requestTimestamp = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_hash:{
               onInitProp(propId);
               this._hash = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_metadata:{
               onInitProp(propId);
               this._metadata = (java.lang.String)value;
               
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
     * 模板ID: template_id
     */
    public final java.lang.String getTemplateId(){
         onPropGet(PROP_ID_templateId);
         return _templateId;
    }

    /**
     * 模板ID: template_id
     */
    public final void setTemplateId(java.lang.String value){
        if(onPropSet(PROP_ID_templateId,value)){
            this._templateId = value;
            internalClearRefs(PROP_ID_templateId);
            
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
     * 系统提示词: system_prompt
     */
    public final java.lang.String getSystemPrompt(){
         onPropGet(PROP_ID_systemPrompt);
         return _systemPrompt;
    }

    /**
     * 系统提示词: system_prompt
     */
    public final void setSystemPrompt(java.lang.String value){
        if(onPropSet(PROP_ID_systemPrompt,value)){
            this._systemPrompt = value;
            internalClearRefs(PROP_ID_systemPrompt);
            
        }
    }
    
    /**
     * 用户提示词: user_prompt
     */
    public final java.lang.String getUserPrompt(){
         onPropGet(PROP_ID_userPrompt);
         return _userPrompt;
    }

    /**
     * 用户提示词: user_prompt
     */
    public final void setUserPrompt(java.lang.String value){
        if(onPropSet(PROP_ID_userPrompt,value)){
            this._userPrompt = value;
            internalClearRefs(PROP_ID_userPrompt);
            
        }
    }
    
    /**
     * 消息类型: message_type
     */
    public final java.lang.Integer getMessageType(){
         onPropGet(PROP_ID_messageType);
         return _messageType;
    }

    /**
     * 消息类型: message_type
     */
    public final void setMessageType(java.lang.Integer value){
        if(onPropSet(PROP_ID_messageType,value)){
            this._messageType = value;
            internalClearRefs(PROP_ID_messageType);
            
        }
    }
    
    /**
     * 请求时间戳: request_timestamp
     */
    public final java.sql.Timestamp getRequestTimestamp(){
         onPropGet(PROP_ID_requestTimestamp);
         return _requestTimestamp;
    }

    /**
     * 请求时间戳: request_timestamp
     */
    public final void setRequestTimestamp(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_requestTimestamp,value)){
            this._requestTimestamp = value;
            internalClearRefs(PROP_ID_requestTimestamp);
            
        }
    }
    
    /**
     * 内容哈希: hash
     */
    public final java.lang.String getHash(){
         onPropGet(PROP_ID_hash);
         return _hash;
    }

    /**
     * 内容哈希: hash
     */
    public final void setHash(java.lang.String value){
        if(onPropSet(PROP_ID_hash,value)){
            this._hash = value;
            internalClearRefs(PROP_ID_hash);
            
        }
    }
    
    /**
     * 元数据: metadata
     */
    public final java.lang.String getMetadata(){
         onPropGet(PROP_ID_metadata);
         return _metadata;
    }

    /**
     * 元数据: metadata
     */
    public final void setMetadata(java.lang.String value){
        if(onPropSet(PROP_ID_metadata,value)){
            this._metadata = value;
            internalClearRefs(PROP_ID_metadata);
            
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
    public final io.nop.ai.dao.entity.NopAiPromptTemplate getTemplate(){
       return (io.nop.ai.dao.entity.NopAiPromptTemplate)internalGetRefEntity(PROP_NAME_template);
    }

    public final void setTemplate(io.nop.ai.dao.entity.NopAiPromptTemplate refEntity){
   
           if(refEntity == null){
           
                   this.setTemplateId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_template, refEntity,()->{
           
                           this.setTemplateId(refEntity.getId());
                       
           });
           }
       
    }
       
    private final OrmEntitySet<io.nop.ai.dao.entity.NopAiChatResponse> _responses = new OrmEntitySet<>(this, PROP_NAME_responses,
        io.nop.ai.dao.entity.NopAiChatResponse.PROP_NAME_request, null,io.nop.ai.dao.entity.NopAiChatResponse.class);

    /**
     * 响应列表。 refPropName: request, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<io.nop.ai.dao.entity.NopAiChatResponse> getResponses(){
       return _responses;
    }
       
}
// resume CPD analysis - CPD-ON
