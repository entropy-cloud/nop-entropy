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

import io.nop.ai.dao.entity.NopAiSessionMessage;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  会话消息: nop_ai_session_message
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopAiSessionMessage extends DynamicOrmEntity{
    
    /* 主键: id VARCHAR */
    public static final String PROP_NAME_id = "id";
    public static final int PROP_ID_id = 1;
    
    /* 会话ID: session_id VARCHAR */
    public static final String PROP_NAME_sessionId = "sessionId";
    public static final int PROP_ID_sessionId = 2;
    
    /* 消息角色: role INTEGER */
    public static final String PROP_NAME_role = "role";
    public static final int PROP_ID_role = 3;
    
    /* 序号: seq BIGINT */
    public static final String PROP_NAME_seq = "seq";
    public static final int PROP_ID_seq = 4;
    
    /* 消息内容: content CLOB */
    public static final String PROP_NAME_content = "content";
    public static final int PROP_ID_content = 5;
    
    /* 工具详情: tool_details CLOB */
    public static final String PROP_NAME_toolDetails = "toolDetails";
    public static final int PROP_ID_toolDetails = 6;
    
    /* 推理内容: reasoning CLOB */
    public static final String PROP_NAME_reasoning = "reasoning";
    public static final int PROP_ID_reasoning = 7;
    
    /* 元数据: metadata CLOB */
    public static final String PROP_NAME_metadata = "metadata";
    public static final int PROP_ID_metadata = 8;
    
    /* 父消息ID: parent_id VARCHAR */
    public static final String PROP_NAME_parentId = "parentId";
    public static final int PROP_ID_parentId = 9;
    
    /* 停止原因: finish_reason VARCHAR */
    public static final String PROP_NAME_finishReason = "finishReason";
    public static final int PROP_ID_finishReason = 10;
    
    /* 数据版本: version INTEGER */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 11;
    
    /* 创建人: created_by VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 12;
    
    /* 创建时间: create_time TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 13;
    
    /* 修改人: updated_by VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 14;
    
    /* 修改时间: update_time TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 15;
    

    private static int _PROP_ID_BOUND = 16;

    
    /* relation:  */
    public static final String PROP_NAME_session = "session";
    
    /* relation:  */
    public static final String PROP_NAME_parentMessage = "parentMessage";
    
    /* relation: 子消息 */
    public static final String PROP_NAME_childMessages = "childMessages";
    
    /* component:  */
    public static final String PROP_NAME_toolDetailsComponent = "toolDetailsComponent";
    
    /* component:  */
    public static final String PROP_NAME_metadataComponent = "metadataComponent";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_id);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_id};

    private static final String[] PROP_ID_TO_NAME = new String[16];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_id] = PROP_NAME_id;
          PROP_NAME_TO_ID.put(PROP_NAME_id, PROP_ID_id);
      
          PROP_ID_TO_NAME[PROP_ID_sessionId] = PROP_NAME_sessionId;
          PROP_NAME_TO_ID.put(PROP_NAME_sessionId, PROP_ID_sessionId);
      
          PROP_ID_TO_NAME[PROP_ID_role] = PROP_NAME_role;
          PROP_NAME_TO_ID.put(PROP_NAME_role, PROP_ID_role);
      
          PROP_ID_TO_NAME[PROP_ID_seq] = PROP_NAME_seq;
          PROP_NAME_TO_ID.put(PROP_NAME_seq, PROP_ID_seq);
      
          PROP_ID_TO_NAME[PROP_ID_content] = PROP_NAME_content;
          PROP_NAME_TO_ID.put(PROP_NAME_content, PROP_ID_content);
      
          PROP_ID_TO_NAME[PROP_ID_toolDetails] = PROP_NAME_toolDetails;
          PROP_NAME_TO_ID.put(PROP_NAME_toolDetails, PROP_ID_toolDetails);
      
          PROP_ID_TO_NAME[PROP_ID_reasoning] = PROP_NAME_reasoning;
          PROP_NAME_TO_ID.put(PROP_NAME_reasoning, PROP_ID_reasoning);
      
          PROP_ID_TO_NAME[PROP_ID_metadata] = PROP_NAME_metadata;
          PROP_NAME_TO_ID.put(PROP_NAME_metadata, PROP_ID_metadata);
      
          PROP_ID_TO_NAME[PROP_ID_parentId] = PROP_NAME_parentId;
          PROP_NAME_TO_ID.put(PROP_NAME_parentId, PROP_ID_parentId);
      
          PROP_ID_TO_NAME[PROP_ID_finishReason] = PROP_NAME_finishReason;
          PROP_NAME_TO_ID.put(PROP_NAME_finishReason, PROP_ID_finishReason);
      
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
    
    /* 消息角色: role */
    private java.lang.Integer _role;
    
    /* 序号: seq */
    private java.lang.Long _seq;
    
    /* 消息内容: content */
    private java.lang.String _content;
    
    /* 工具详情: tool_details */
    private java.lang.String _toolDetails;
    
    /* 推理内容: reasoning */
    private java.lang.String _reasoning;
    
    /* 元数据: metadata */
    private java.lang.String _metadata;
    
    /* 父消息ID: parent_id */
    private java.lang.String _parentId;
    
    /* 停止原因: finish_reason */
    private java.lang.String _finishReason;
    
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
    

    public _NopAiSessionMessage(){
        // for debug
    }

    protected NopAiSessionMessage newInstance(){
        NopAiSessionMessage entity = new NopAiSessionMessage();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopAiSessionMessage cloneInstance() {
        NopAiSessionMessage entity = newInstance();
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
      return "io.nop.ai.dao.entity.NopAiSessionMessage";
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
        
            case PROP_ID_role:
               return getRole();
        
            case PROP_ID_seq:
               return getSeq();
        
            case PROP_ID_content:
               return getContent();
        
            case PROP_ID_toolDetails:
               return getToolDetails();
        
            case PROP_ID_reasoning:
               return getReasoning();
        
            case PROP_ID_metadata:
               return getMetadata();
        
            case PROP_ID_parentId:
               return getParentId();
        
            case PROP_ID_finishReason:
               return getFinishReason();
        
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
        
            case PROP_ID_role:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_role));
               }
               setRole(typedValue);
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
        
            case PROP_ID_content:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_content));
               }
               setContent(typedValue);
               break;
            }
        
            case PROP_ID_toolDetails:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_toolDetails));
               }
               setToolDetails(typedValue);
               break;
            }
        
            case PROP_ID_reasoning:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_reasoning));
               }
               setReasoning(typedValue);
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
        
            case PROP_ID_parentId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_parentId));
               }
               setParentId(typedValue);
               break;
            }
        
            case PROP_ID_finishReason:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_finishReason));
               }
               setFinishReason(typedValue);
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
        
            case PROP_ID_role:{
               onInitProp(propId);
               this._role = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_seq:{
               onInitProp(propId);
               this._seq = (java.lang.Long)value;
               
               break;
            }
        
            case PROP_ID_content:{
               onInitProp(propId);
               this._content = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_toolDetails:{
               onInitProp(propId);
               this._toolDetails = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_reasoning:{
               onInitProp(propId);
               this._reasoning = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_metadata:{
               onInitProp(propId);
               this._metadata = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_parentId:{
               onInitProp(propId);
               this._parentId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_finishReason:{
               onInitProp(propId);
               this._finishReason = (java.lang.String)value;
               
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
     * 消息角色: role
     */
    public final java.lang.Integer getRole(){
         onPropGet(PROP_ID_role);
         return _role;
    }

    /**
     * 消息角色: role
     */
    public final void setRole(java.lang.Integer value){
        if(onPropSet(PROP_ID_role,value)){
            this._role = value;
            internalClearRefs(PROP_ID_role);
            
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
     * 消息内容: content
     */
    public final java.lang.String getContent(){
         onPropGet(PROP_ID_content);
         return _content;
    }

    /**
     * 消息内容: content
     */
    public final void setContent(java.lang.String value){
        if(onPropSet(PROP_ID_content,value)){
            this._content = value;
            internalClearRefs(PROP_ID_content);
            
        }
    }
    
    /**
     * 工具详情: tool_details
     */
    public final java.lang.String getToolDetails(){
         onPropGet(PROP_ID_toolDetails);
         return _toolDetails;
    }

    /**
     * 工具详情: tool_details
     */
    public final void setToolDetails(java.lang.String value){
        if(onPropSet(PROP_ID_toolDetails,value)){
            this._toolDetails = value;
            internalClearRefs(PROP_ID_toolDetails);
            
        }
    }
    
    /**
     * 推理内容: reasoning
     */
    public final java.lang.String getReasoning(){
         onPropGet(PROP_ID_reasoning);
         return _reasoning;
    }

    /**
     * 推理内容: reasoning
     */
    public final void setReasoning(java.lang.String value){
        if(onPropSet(PROP_ID_reasoning,value)){
            this._reasoning = value;
            internalClearRefs(PROP_ID_reasoning);
            
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
     * 父消息ID: parent_id
     */
    public final java.lang.String getParentId(){
         onPropGet(PROP_ID_parentId);
         return _parentId;
    }

    /**
     * 父消息ID: parent_id
     */
    public final void setParentId(java.lang.String value){
        if(onPropSet(PROP_ID_parentId,value)){
            this._parentId = value;
            internalClearRefs(PROP_ID_parentId);
            
        }
    }
    
    /**
     * 停止原因: finish_reason
     */
    public final java.lang.String getFinishReason(){
         onPropGet(PROP_ID_finishReason);
         return _finishReason;
    }

    /**
     * 停止原因: finish_reason
     */
    public final void setFinishReason(java.lang.String value){
        if(onPropSet(PROP_ID_finishReason,value)){
            this._finishReason = value;
            internalClearRefs(PROP_ID_finishReason);
            
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
       
    /**
     * 
     */
    public final io.nop.ai.dao.entity.NopAiSessionMessage getParentMessage(){
       return (io.nop.ai.dao.entity.NopAiSessionMessage)internalGetRefEntity(PROP_NAME_parentMessage);
    }

    public final void setParentMessage(io.nop.ai.dao.entity.NopAiSessionMessage refEntity){
   
           if(refEntity == null){
           
                   this.setParentId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_parentMessage, refEntity,()->{
           
                           this.setParentId(refEntity.getId());
                       
           });
           }
       
    }
       
    private final OrmEntitySet<io.nop.ai.dao.entity.NopAiSessionMessage> _childMessages = new OrmEntitySet<>(this, PROP_NAME_childMessages,
        io.nop.ai.dao.entity.NopAiSessionMessage.PROP_NAME_parentMessage, null,io.nop.ai.dao.entity.NopAiSessionMessage.class);

    /**
     * 子消息。 refPropName: parentMessage, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<io.nop.ai.dao.entity.NopAiSessionMessage> getChildMessages(){
       return _childMessages;
    }
       
   private io.nop.orm.component.JsonOrmComponent _toolDetailsComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_toolDetailsComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_toolDetailsComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_toolDetails);
      
   }

   public final io.nop.orm.component.JsonOrmComponent getToolDetailsComponent(){
      if(_toolDetailsComponent == null){
          _toolDetailsComponent = new io.nop.orm.component.JsonOrmComponent();
          _toolDetailsComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_toolDetailsComponent);
      }
      return _toolDetailsComponent;
   }

   private io.nop.orm.component.JsonOrmComponent _metadataComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_metadataComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_metadataComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_metadata);
      
   }

   public final io.nop.orm.component.JsonOrmComponent getMetadataComponent(){
      if(_metadataComponent == null){
          _metadataComponent = new io.nop.orm.component.JsonOrmComponent();
          _metadataComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_metadataComponent);
      }
      return _metadataComponent;
   }

}
// resume CPD analysis - CPD-ON
