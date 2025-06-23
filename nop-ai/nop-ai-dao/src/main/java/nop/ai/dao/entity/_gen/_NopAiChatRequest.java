package nop.ai.dao.entity._gen;

import io.nop.orm.model.IEntityModel;
import io.nop.orm.support.DynamicOrmEntity;
import io.nop.orm.support.OrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.orm.IOrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.api.core.convert.ConvertHelper;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;

import nop.ai.dao.entity.NopAiChatRequest;

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
    
    /* 消息类型: message_type VARCHAR */
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
    

    private static int _PROP_ID_BOUND = 10;

    
    /* relation:  */
    public static final String PROP_NAME_template = "template";
    
    /* relation: 响应列表 */
    public static final String PROP_NAME_responses = "responses";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_id);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_id};

    private static final String[] PROP_ID_TO_NAME = new String[10];
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
    private java.lang.String _messageType;
    
    /* 请求时间戳: request_timestamp */
    private java.sql.Timestamp _requestTimestamp;
    
    /* 内容哈希: hash */
    private java.lang.String _hash;
    
    /* 元数据: metadata */
    private java.lang.String _metadata;
    

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
      return "nop.ai.dao.entity.NopAiChatRequest";
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
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
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
               this._messageType = (java.lang.String)value;
               
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
    public final java.lang.String getMessageType(){
         onPropGet(PROP_ID_messageType);
         return _messageType;
    }

    /**
     * 消息类型: message_type
     */
    public final void setMessageType(java.lang.String value){
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
     * 
     */
    public final nop.ai.dao.entity.NopAiPromptTemplate getTemplate(){
       return (nop.ai.dao.entity.NopAiPromptTemplate)internalGetRefEntity(PROP_NAME_template);
    }

    public final void setTemplate(nop.ai.dao.entity.NopAiPromptTemplate refEntity){
   
           if(refEntity == null){
           
                   this.setTemplateId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_template, refEntity,()->{
           
                           this.setTemplateId(refEntity.getId());
                       
           });
           }
       
    }
       
    private final OrmEntitySet<nop.ai.dao.entity.NopAiChatResponse> _responses = new OrmEntitySet<>(this, PROP_NAME_responses,
        nop.ai.dao.entity.NopAiChatResponse.PROP_NAME_request, null,nop.ai.dao.entity.NopAiChatResponse.class);

    /**
     * 响应列表。 refPropName: request, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<nop.ai.dao.entity.NopAiChatResponse> getResponses(){
       return _responses;
    }
       
}
// resume CPD analysis - CPD-ON
