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

import io.nop.ai.dao.entity.NopAiSession;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  Agent会话: nop_ai_session
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopAiSession extends DynamicOrmEntity{
    
    /* 主键: id VARCHAR */
    public static final String PROP_NAME_id = "id";
    public static final int PROP_ID_id = 1;
    
    /* 项目ID: project_id VARCHAR */
    public static final String PROP_NAME_projectId = "projectId";
    public static final int PROP_ID_projectId = 2;
    
    /* 父会话ID: parent_session_id VARCHAR */
    public static final String PROP_NAME_parentSessionId = "parentSessionId";
    public static final int PROP_ID_parentSessionId = 3;
    
    /* Agent名称: agent_name VARCHAR */
    public static final String PROP_NAME_agentName = "agentName";
    public static final int PROP_ID_agentName = 4;
    
    /* 模型供应商: model_provider VARCHAR */
    public static final String PROP_NAME_modelProvider = "modelProvider";
    public static final int PROP_ID_modelProvider = 5;
    
    /* 模型名称: model_name VARCHAR */
    public static final String PROP_NAME_modelName = "modelName";
    public static final int PROP_ID_modelName = 6;
    
    /* 会话Slug: slug VARCHAR */
    public static final String PROP_NAME_slug = "slug";
    public static final int PROP_ID_slug = 7;
    
    /* 会话标题: title VARCHAR */
    public static final String PROP_NAME_title = "title";
    public static final int PROP_ID_title = 8;
    
    /* 工作目录: directory VARCHAR */
    public static final String PROP_NAME_directory = "directory";
    public static final int PROP_ID_directory = 9;
    
    /* 会话状态: status INTEGER */
    public static final String PROP_NAME_status = "status";
    public static final int PROP_ID_status = 10;
    
    /* 数据版本: version INTEGER */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 11;
    
    /* 费用: cost DECIMAL */
    public static final String PROP_NAME_cost = "cost";
    public static final int PROP_ID_cost = 12;
    
    /* 输入Token数: tokens_input INTEGER */
    public static final String PROP_NAME_tokensInput = "tokensInput";
    public static final int PROP_ID_tokensInput = 13;
    
    /* 输出Token数: tokens_output INTEGER */
    public static final String PROP_NAME_tokensOutput = "tokensOutput";
    public static final int PROP_ID_tokensOutput = 14;
    
    /* 推理Token数: tokens_reasoning INTEGER */
    public static final String PROP_NAME_tokensReasoning = "tokensReasoning";
    public static final int PROP_ID_tokensReasoning = 15;
    
    /* 缓存读取Token数: tokens_cache_read INTEGER */
    public static final String PROP_NAME_tokensCacheRead = "tokensCacheRead";
    public static final int PROP_ID_tokensCacheRead = 16;
    
    /* 缓存写入Token数: tokens_cache_write INTEGER */
    public static final String PROP_NAME_tokensCacheWrite = "tokensCacheWrite";
    public static final int PROP_ID_tokensCacheWrite = 17;
    
    /* 总字节数: total_bytes BIGINT */
    public static final String PROP_NAME_totalBytes = "totalBytes";
    public static final int PROP_ID_totalBytes = 18;
    
    /* 上下文元数据: context_metadata CLOB */
    public static final String PROP_NAME_contextMetadata = "contextMetadata";
    public static final int PROP_ID_contextMetadata = 19;
    
    /* 元数据: metadata CLOB */
    public static final String PROP_NAME_metadata = "metadata";
    public static final int PROP_ID_metadata = 20;
    
    /* 创建人: created_by VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 21;
    
    /* 创建时间: create_time TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 22;
    
    /* 修改人: updated_by VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 23;
    
    /* 修改时间: update_time TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 24;
    
    /* 压缩时间: compacted_at TIMESTAMP */
    public static final String PROP_NAME_compactedAt = "compactedAt";
    public static final int PROP_ID_compactedAt = 25;
    
    /* 归档时间: archived_at TIMESTAMP */
    public static final String PROP_NAME_archivedAt = "archivedAt";
    public static final int PROP_ID_archivedAt = 26;
    

    private static int _PROP_ID_BOUND = 27;

    
    /* relation:  */
    public static final String PROP_NAME_project = "project";
    
    /* relation:  */
    public static final String PROP_NAME_parentSession = "parentSession";
    
    /* relation: 子会话 */
    public static final String PROP_NAME_childSessions = "childSessions";
    
    /* relation: 消息列表 */
    public static final String PROP_NAME_messages = "messages";
    
    /* relation: 上下文快照 */
    public static final String PROP_NAME_contexts = "contexts";
    
    /* relation: 输入队列 */
    public static final String PROP_NAME_inputs = "inputs";
    
    /* relation: 待办列表 */
    public static final String PROP_NAME_todos = "todos";
    
    /* relation: 事件日志 */
    public static final String PROP_NAME_events = "events";
    
    /* relation:  */
    public static final String PROP_NAME_context = "context";
    
    /* component:  */
    public static final String PROP_NAME_contextMetadataComponent = "contextMetadataComponent";
    
    /* component:  */
    public static final String PROP_NAME_metadataComponent = "metadataComponent";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_id);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_id};

    private static final String[] PROP_ID_TO_NAME = new String[27];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_id] = PROP_NAME_id;
          PROP_NAME_TO_ID.put(PROP_NAME_id, PROP_ID_id);
      
          PROP_ID_TO_NAME[PROP_ID_projectId] = PROP_NAME_projectId;
          PROP_NAME_TO_ID.put(PROP_NAME_projectId, PROP_ID_projectId);
      
          PROP_ID_TO_NAME[PROP_ID_parentSessionId] = PROP_NAME_parentSessionId;
          PROP_NAME_TO_ID.put(PROP_NAME_parentSessionId, PROP_ID_parentSessionId);
      
          PROP_ID_TO_NAME[PROP_ID_agentName] = PROP_NAME_agentName;
          PROP_NAME_TO_ID.put(PROP_NAME_agentName, PROP_ID_agentName);
      
          PROP_ID_TO_NAME[PROP_ID_modelProvider] = PROP_NAME_modelProvider;
          PROP_NAME_TO_ID.put(PROP_NAME_modelProvider, PROP_ID_modelProvider);
      
          PROP_ID_TO_NAME[PROP_ID_modelName] = PROP_NAME_modelName;
          PROP_NAME_TO_ID.put(PROP_NAME_modelName, PROP_ID_modelName);
      
          PROP_ID_TO_NAME[PROP_ID_slug] = PROP_NAME_slug;
          PROP_NAME_TO_ID.put(PROP_NAME_slug, PROP_ID_slug);
      
          PROP_ID_TO_NAME[PROP_ID_title] = PROP_NAME_title;
          PROP_NAME_TO_ID.put(PROP_NAME_title, PROP_ID_title);
      
          PROP_ID_TO_NAME[PROP_ID_directory] = PROP_NAME_directory;
          PROP_NAME_TO_ID.put(PROP_NAME_directory, PROP_ID_directory);
      
          PROP_ID_TO_NAME[PROP_ID_status] = PROP_NAME_status;
          PROP_NAME_TO_ID.put(PROP_NAME_status, PROP_ID_status);
      
          PROP_ID_TO_NAME[PROP_ID_version] = PROP_NAME_version;
          PROP_NAME_TO_ID.put(PROP_NAME_version, PROP_ID_version);
      
          PROP_ID_TO_NAME[PROP_ID_cost] = PROP_NAME_cost;
          PROP_NAME_TO_ID.put(PROP_NAME_cost, PROP_ID_cost);
      
          PROP_ID_TO_NAME[PROP_ID_tokensInput] = PROP_NAME_tokensInput;
          PROP_NAME_TO_ID.put(PROP_NAME_tokensInput, PROP_ID_tokensInput);
      
          PROP_ID_TO_NAME[PROP_ID_tokensOutput] = PROP_NAME_tokensOutput;
          PROP_NAME_TO_ID.put(PROP_NAME_tokensOutput, PROP_ID_tokensOutput);
      
          PROP_ID_TO_NAME[PROP_ID_tokensReasoning] = PROP_NAME_tokensReasoning;
          PROP_NAME_TO_ID.put(PROP_NAME_tokensReasoning, PROP_ID_tokensReasoning);
      
          PROP_ID_TO_NAME[PROP_ID_tokensCacheRead] = PROP_NAME_tokensCacheRead;
          PROP_NAME_TO_ID.put(PROP_NAME_tokensCacheRead, PROP_ID_tokensCacheRead);
      
          PROP_ID_TO_NAME[PROP_ID_tokensCacheWrite] = PROP_NAME_tokensCacheWrite;
          PROP_NAME_TO_ID.put(PROP_NAME_tokensCacheWrite, PROP_ID_tokensCacheWrite);
      
          PROP_ID_TO_NAME[PROP_ID_totalBytes] = PROP_NAME_totalBytes;
          PROP_NAME_TO_ID.put(PROP_NAME_totalBytes, PROP_ID_totalBytes);
      
          PROP_ID_TO_NAME[PROP_ID_contextMetadata] = PROP_NAME_contextMetadata;
          PROP_NAME_TO_ID.put(PROP_NAME_contextMetadata, PROP_ID_contextMetadata);
      
          PROP_ID_TO_NAME[PROP_ID_metadata] = PROP_NAME_metadata;
          PROP_NAME_TO_ID.put(PROP_NAME_metadata, PROP_ID_metadata);
      
          PROP_ID_TO_NAME[PROP_ID_createdBy] = PROP_NAME_createdBy;
          PROP_NAME_TO_ID.put(PROP_NAME_createdBy, PROP_ID_createdBy);
      
          PROP_ID_TO_NAME[PROP_ID_createTime] = PROP_NAME_createTime;
          PROP_NAME_TO_ID.put(PROP_NAME_createTime, PROP_ID_createTime);
      
          PROP_ID_TO_NAME[PROP_ID_updatedBy] = PROP_NAME_updatedBy;
          PROP_NAME_TO_ID.put(PROP_NAME_updatedBy, PROP_ID_updatedBy);
      
          PROP_ID_TO_NAME[PROP_ID_updateTime] = PROP_NAME_updateTime;
          PROP_NAME_TO_ID.put(PROP_NAME_updateTime, PROP_ID_updateTime);
      
          PROP_ID_TO_NAME[PROP_ID_compactedAt] = PROP_NAME_compactedAt;
          PROP_NAME_TO_ID.put(PROP_NAME_compactedAt, PROP_ID_compactedAt);
      
          PROP_ID_TO_NAME[PROP_ID_archivedAt] = PROP_NAME_archivedAt;
          PROP_NAME_TO_ID.put(PROP_NAME_archivedAt, PROP_ID_archivedAt);
      
    }

    
    /* 主键: id */
    private java.lang.String _id;
    
    /* 项目ID: project_id */
    private java.lang.String _projectId;
    
    /* 父会话ID: parent_session_id */
    private java.lang.String _parentSessionId;
    
    /* Agent名称: agent_name */
    private java.lang.String _agentName;
    
    /* 模型供应商: model_provider */
    private java.lang.String _modelProvider;
    
    /* 模型名称: model_name */
    private java.lang.String _modelName;
    
    /* 会话Slug: slug */
    private java.lang.String _slug;
    
    /* 会话标题: title */
    private java.lang.String _title;
    
    /* 工作目录: directory */
    private java.lang.String _directory;
    
    /* 会话状态: status */
    private java.lang.Integer _status;
    
    /* 数据版本: version */
    private java.lang.Integer _version;
    
    /* 费用: cost */
    private java.math.BigDecimal _cost;
    
    /* 输入Token数: tokens_input */
    private java.lang.Integer _tokensInput;
    
    /* 输出Token数: tokens_output */
    private java.lang.Integer _tokensOutput;
    
    /* 推理Token数: tokens_reasoning */
    private java.lang.Integer _tokensReasoning;
    
    /* 缓存读取Token数: tokens_cache_read */
    private java.lang.Integer _tokensCacheRead;
    
    /* 缓存写入Token数: tokens_cache_write */
    private java.lang.Integer _tokensCacheWrite;
    
    /* 总字节数: total_bytes */
    private java.lang.Long _totalBytes;
    
    /* 上下文元数据: context_metadata */
    private java.lang.String _contextMetadata;
    
    /* 元数据: metadata */
    private java.lang.String _metadata;
    
    /* 创建人: created_by */
    private java.lang.String _createdBy;
    
    /* 创建时间: create_time */
    private java.sql.Timestamp _createTime;
    
    /* 修改人: updated_by */
    private java.lang.String _updatedBy;
    
    /* 修改时间: update_time */
    private java.sql.Timestamp _updateTime;
    
    /* 压缩时间: compacted_at */
    private java.sql.Timestamp _compactedAt;
    
    /* 归档时间: archived_at */
    private java.sql.Timestamp _archivedAt;
    

    public _NopAiSession(){
        // for debug
    }

    protected NopAiSession newInstance(){
        NopAiSession entity = new NopAiSession();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopAiSession cloneInstance() {
        NopAiSession entity = newInstance();
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
      return "io.nop.ai.dao.entity.NopAiSession";
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
        
            case PROP_ID_projectId:
               return getProjectId();
        
            case PROP_ID_parentSessionId:
               return getParentSessionId();
        
            case PROP_ID_agentName:
               return getAgentName();
        
            case PROP_ID_modelProvider:
               return getModelProvider();
        
            case PROP_ID_modelName:
               return getModelName();
        
            case PROP_ID_slug:
               return getSlug();
        
            case PROP_ID_title:
               return getTitle();
        
            case PROP_ID_directory:
               return getDirectory();
        
            case PROP_ID_status:
               return getStatus();
        
            case PROP_ID_version:
               return getVersion();
        
            case PROP_ID_cost:
               return getCost();
        
            case PROP_ID_tokensInput:
               return getTokensInput();
        
            case PROP_ID_tokensOutput:
               return getTokensOutput();
        
            case PROP_ID_tokensReasoning:
               return getTokensReasoning();
        
            case PROP_ID_tokensCacheRead:
               return getTokensCacheRead();
        
            case PROP_ID_tokensCacheWrite:
               return getTokensCacheWrite();
        
            case PROP_ID_totalBytes:
               return getTotalBytes();
        
            case PROP_ID_contextMetadata:
               return getContextMetadata();
        
            case PROP_ID_metadata:
               return getMetadata();
        
            case PROP_ID_createdBy:
               return getCreatedBy();
        
            case PROP_ID_createTime:
               return getCreateTime();
        
            case PROP_ID_updatedBy:
               return getUpdatedBy();
        
            case PROP_ID_updateTime:
               return getUpdateTime();
        
            case PROP_ID_compactedAt:
               return getCompactedAt();
        
            case PROP_ID_archivedAt:
               return getArchivedAt();
        
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
        
            case PROP_ID_projectId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_projectId));
               }
               setProjectId(typedValue);
               break;
            }
        
            case PROP_ID_parentSessionId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_parentSessionId));
               }
               setParentSessionId(typedValue);
               break;
            }
        
            case PROP_ID_agentName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_agentName));
               }
               setAgentName(typedValue);
               break;
            }
        
            case PROP_ID_modelProvider:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_modelProvider));
               }
               setModelProvider(typedValue);
               break;
            }
        
            case PROP_ID_modelName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_modelName));
               }
               setModelName(typedValue);
               break;
            }
        
            case PROP_ID_slug:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_slug));
               }
               setSlug(typedValue);
               break;
            }
        
            case PROP_ID_title:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_title));
               }
               setTitle(typedValue);
               break;
            }
        
            case PROP_ID_directory:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_directory));
               }
               setDirectory(typedValue);
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
        
            case PROP_ID_version:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_version));
               }
               setVersion(typedValue);
               break;
            }
        
            case PROP_ID_cost:{
               java.math.BigDecimal typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toBigDecimal(value,
                       err-> newTypeConversionError(PROP_NAME_cost));
               }
               setCost(typedValue);
               break;
            }
        
            case PROP_ID_tokensInput:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_tokensInput));
               }
               setTokensInput(typedValue);
               break;
            }
        
            case PROP_ID_tokensOutput:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_tokensOutput));
               }
               setTokensOutput(typedValue);
               break;
            }
        
            case PROP_ID_tokensReasoning:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_tokensReasoning));
               }
               setTokensReasoning(typedValue);
               break;
            }
        
            case PROP_ID_tokensCacheRead:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_tokensCacheRead));
               }
               setTokensCacheRead(typedValue);
               break;
            }
        
            case PROP_ID_tokensCacheWrite:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_tokensCacheWrite));
               }
               setTokensCacheWrite(typedValue);
               break;
            }
        
            case PROP_ID_totalBytes:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_totalBytes));
               }
               setTotalBytes(typedValue);
               break;
            }
        
            case PROP_ID_contextMetadata:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_contextMetadata));
               }
               setContextMetadata(typedValue);
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
        
            case PROP_ID_compactedAt:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_compactedAt));
               }
               setCompactedAt(typedValue);
               break;
            }
        
            case PROP_ID_archivedAt:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_archivedAt));
               }
               setArchivedAt(typedValue);
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
        
            case PROP_ID_projectId:{
               onInitProp(propId);
               this._projectId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_parentSessionId:{
               onInitProp(propId);
               this._parentSessionId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_agentName:{
               onInitProp(propId);
               this._agentName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_modelProvider:{
               onInitProp(propId);
               this._modelProvider = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_modelName:{
               onInitProp(propId);
               this._modelName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_slug:{
               onInitProp(propId);
               this._slug = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_title:{
               onInitProp(propId);
               this._title = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_directory:{
               onInitProp(propId);
               this._directory = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_status:{
               onInitProp(propId);
               this._status = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_version:{
               onInitProp(propId);
               this._version = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_cost:{
               onInitProp(propId);
               this._cost = (java.math.BigDecimal)value;
               
               break;
            }
        
            case PROP_ID_tokensInput:{
               onInitProp(propId);
               this._tokensInput = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_tokensOutput:{
               onInitProp(propId);
               this._tokensOutput = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_tokensReasoning:{
               onInitProp(propId);
               this._tokensReasoning = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_tokensCacheRead:{
               onInitProp(propId);
               this._tokensCacheRead = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_tokensCacheWrite:{
               onInitProp(propId);
               this._tokensCacheWrite = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_totalBytes:{
               onInitProp(propId);
               this._totalBytes = (java.lang.Long)value;
               
               break;
            }
        
            case PROP_ID_contextMetadata:{
               onInitProp(propId);
               this._contextMetadata = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_metadata:{
               onInitProp(propId);
               this._metadata = (java.lang.String)value;
               
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
        
            case PROP_ID_compactedAt:{
               onInitProp(propId);
               this._compactedAt = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_archivedAt:{
               onInitProp(propId);
               this._archivedAt = (java.sql.Timestamp)value;
               
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
     * 项目ID: project_id
     */
    public final java.lang.String getProjectId(){
         onPropGet(PROP_ID_projectId);
         return _projectId;
    }

    /**
     * 项目ID: project_id
     */
    public final void setProjectId(java.lang.String value){
        if(onPropSet(PROP_ID_projectId,value)){
            this._projectId = value;
            internalClearRefs(PROP_ID_projectId);
            
        }
    }
    
    /**
     * 父会话ID: parent_session_id
     */
    public final java.lang.String getParentSessionId(){
         onPropGet(PROP_ID_parentSessionId);
         return _parentSessionId;
    }

    /**
     * 父会话ID: parent_session_id
     */
    public final void setParentSessionId(java.lang.String value){
        if(onPropSet(PROP_ID_parentSessionId,value)){
            this._parentSessionId = value;
            internalClearRefs(PROP_ID_parentSessionId);
            
        }
    }
    
    /**
     * Agent名称: agent_name
     */
    public final java.lang.String getAgentName(){
         onPropGet(PROP_ID_agentName);
         return _agentName;
    }

    /**
     * Agent名称: agent_name
     */
    public final void setAgentName(java.lang.String value){
        if(onPropSet(PROP_ID_agentName,value)){
            this._agentName = value;
            internalClearRefs(PROP_ID_agentName);
            
        }
    }
    
    /**
     * 模型供应商: model_provider
     */
    public final java.lang.String getModelProvider(){
         onPropGet(PROP_ID_modelProvider);
         return _modelProvider;
    }

    /**
     * 模型供应商: model_provider
     */
    public final void setModelProvider(java.lang.String value){
        if(onPropSet(PROP_ID_modelProvider,value)){
            this._modelProvider = value;
            internalClearRefs(PROP_ID_modelProvider);
            
        }
    }
    
    /**
     * 模型名称: model_name
     */
    public final java.lang.String getModelName(){
         onPropGet(PROP_ID_modelName);
         return _modelName;
    }

    /**
     * 模型名称: model_name
     */
    public final void setModelName(java.lang.String value){
        if(onPropSet(PROP_ID_modelName,value)){
            this._modelName = value;
            internalClearRefs(PROP_ID_modelName);
            
        }
    }
    
    /**
     * 会话Slug: slug
     */
    public final java.lang.String getSlug(){
         onPropGet(PROP_ID_slug);
         return _slug;
    }

    /**
     * 会话Slug: slug
     */
    public final void setSlug(java.lang.String value){
        if(onPropSet(PROP_ID_slug,value)){
            this._slug = value;
            internalClearRefs(PROP_ID_slug);
            
        }
    }
    
    /**
     * 会话标题: title
     */
    public final java.lang.String getTitle(){
         onPropGet(PROP_ID_title);
         return _title;
    }

    /**
     * 会话标题: title
     */
    public final void setTitle(java.lang.String value){
        if(onPropSet(PROP_ID_title,value)){
            this._title = value;
            internalClearRefs(PROP_ID_title);
            
        }
    }
    
    /**
     * 工作目录: directory
     */
    public final java.lang.String getDirectory(){
         onPropGet(PROP_ID_directory);
         return _directory;
    }

    /**
     * 工作目录: directory
     */
    public final void setDirectory(java.lang.String value){
        if(onPropSet(PROP_ID_directory,value)){
            this._directory = value;
            internalClearRefs(PROP_ID_directory);
            
        }
    }
    
    /**
     * 会话状态: status
     */
    public final java.lang.Integer getStatus(){
         onPropGet(PROP_ID_status);
         return _status;
    }

    /**
     * 会话状态: status
     */
    public final void setStatus(java.lang.Integer value){
        if(onPropSet(PROP_ID_status,value)){
            this._status = value;
            internalClearRefs(PROP_ID_status);
            
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
     * 费用: cost
     */
    public final java.math.BigDecimal getCost(){
         onPropGet(PROP_ID_cost);
         return _cost;
    }

    /**
     * 费用: cost
     */
    public final void setCost(java.math.BigDecimal value){
        if(onPropSet(PROP_ID_cost,value)){
            this._cost = value;
            internalClearRefs(PROP_ID_cost);
            
        }
    }
    
    /**
     * 输入Token数: tokens_input
     */
    public final java.lang.Integer getTokensInput(){
         onPropGet(PROP_ID_tokensInput);
         return _tokensInput;
    }

    /**
     * 输入Token数: tokens_input
     */
    public final void setTokensInput(java.lang.Integer value){
        if(onPropSet(PROP_ID_tokensInput,value)){
            this._tokensInput = value;
            internalClearRefs(PROP_ID_tokensInput);
            
        }
    }
    
    /**
     * 输出Token数: tokens_output
     */
    public final java.lang.Integer getTokensOutput(){
         onPropGet(PROP_ID_tokensOutput);
         return _tokensOutput;
    }

    /**
     * 输出Token数: tokens_output
     */
    public final void setTokensOutput(java.lang.Integer value){
        if(onPropSet(PROP_ID_tokensOutput,value)){
            this._tokensOutput = value;
            internalClearRefs(PROP_ID_tokensOutput);
            
        }
    }
    
    /**
     * 推理Token数: tokens_reasoning
     */
    public final java.lang.Integer getTokensReasoning(){
         onPropGet(PROP_ID_tokensReasoning);
         return _tokensReasoning;
    }

    /**
     * 推理Token数: tokens_reasoning
     */
    public final void setTokensReasoning(java.lang.Integer value){
        if(onPropSet(PROP_ID_tokensReasoning,value)){
            this._tokensReasoning = value;
            internalClearRefs(PROP_ID_tokensReasoning);
            
        }
    }
    
    /**
     * 缓存读取Token数: tokens_cache_read
     */
    public final java.lang.Integer getTokensCacheRead(){
         onPropGet(PROP_ID_tokensCacheRead);
         return _tokensCacheRead;
    }

    /**
     * 缓存读取Token数: tokens_cache_read
     */
    public final void setTokensCacheRead(java.lang.Integer value){
        if(onPropSet(PROP_ID_tokensCacheRead,value)){
            this._tokensCacheRead = value;
            internalClearRefs(PROP_ID_tokensCacheRead);
            
        }
    }
    
    /**
     * 缓存写入Token数: tokens_cache_write
     */
    public final java.lang.Integer getTokensCacheWrite(){
         onPropGet(PROP_ID_tokensCacheWrite);
         return _tokensCacheWrite;
    }

    /**
     * 缓存写入Token数: tokens_cache_write
     */
    public final void setTokensCacheWrite(java.lang.Integer value){
        if(onPropSet(PROP_ID_tokensCacheWrite,value)){
            this._tokensCacheWrite = value;
            internalClearRefs(PROP_ID_tokensCacheWrite);
            
        }
    }
    
    /**
     * 总字节数: total_bytes
     */
    public final java.lang.Long getTotalBytes(){
         onPropGet(PROP_ID_totalBytes);
         return _totalBytes;
    }

    /**
     * 总字节数: total_bytes
     */
    public final void setTotalBytes(java.lang.Long value){
        if(onPropSet(PROP_ID_totalBytes,value)){
            this._totalBytes = value;
            internalClearRefs(PROP_ID_totalBytes);
            
        }
    }
    
    /**
     * 上下文元数据: context_metadata
     */
    public final java.lang.String getContextMetadata(){
         onPropGet(PROP_ID_contextMetadata);
         return _contextMetadata;
    }

    /**
     * 上下文元数据: context_metadata
     */
    public final void setContextMetadata(java.lang.String value){
        if(onPropSet(PROP_ID_contextMetadata,value)){
            this._contextMetadata = value;
            internalClearRefs(PROP_ID_contextMetadata);
            
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
     * 压缩时间: compacted_at
     */
    public final java.sql.Timestamp getCompactedAt(){
         onPropGet(PROP_ID_compactedAt);
         return _compactedAt;
    }

    /**
     * 压缩时间: compacted_at
     */
    public final void setCompactedAt(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_compactedAt,value)){
            this._compactedAt = value;
            internalClearRefs(PROP_ID_compactedAt);
            
        }
    }
    
    /**
     * 归档时间: archived_at
     */
    public final java.sql.Timestamp getArchivedAt(){
         onPropGet(PROP_ID_archivedAt);
         return _archivedAt;
    }

    /**
     * 归档时间: archived_at
     */
    public final void setArchivedAt(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_archivedAt,value)){
            this._archivedAt = value;
            internalClearRefs(PROP_ID_archivedAt);
            
        }
    }
    
    /**
     * 
     */
    public final io.nop.ai.dao.entity.NopAiProject getProject(){
       return (io.nop.ai.dao.entity.NopAiProject)internalGetRefEntity(PROP_NAME_project);
    }

    public final void setProject(io.nop.ai.dao.entity.NopAiProject refEntity){
   
           if(refEntity == null){
           
                   this.setProjectId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_project, refEntity,()->{
           
                           this.setProjectId(refEntity.getId());
                       
           });
           }
       
    }
       
    /**
     * 
     */
    public final io.nop.ai.dao.entity.NopAiSession getParentSession(){
       return (io.nop.ai.dao.entity.NopAiSession)internalGetRefEntity(PROP_NAME_parentSession);
    }

    public final void setParentSession(io.nop.ai.dao.entity.NopAiSession refEntity){
   
           if(refEntity == null){
           
                   this.setParentSessionId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_parentSession, refEntity,()->{
           
                           this.setParentSessionId(refEntity.getId());
                       
           });
           }
       
    }
       
    private final OrmEntitySet<io.nop.ai.dao.entity.NopAiSession> _childSessions = new OrmEntitySet<>(this, PROP_NAME_childSessions,
        io.nop.ai.dao.entity.NopAiSession.PROP_NAME_parentSession, null,io.nop.ai.dao.entity.NopAiSession.class);

    /**
     * 子会话。 refPropName: parentSession, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<io.nop.ai.dao.entity.NopAiSession> getChildSessions(){
       return _childSessions;
    }
       
    private final OrmEntitySet<io.nop.ai.dao.entity.NopAiSessionMessage> _messages = new OrmEntitySet<>(this, PROP_NAME_messages,
        io.nop.ai.dao.entity.NopAiSessionMessage.PROP_NAME_session, null,io.nop.ai.dao.entity.NopAiSessionMessage.class);

    /**
     * 消息列表。 refPropName: session, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<io.nop.ai.dao.entity.NopAiSessionMessage> getMessages(){
       return _messages;
    }
       
    private final OrmEntitySet<io.nop.ai.dao.entity.NopAiSessionContext> _contexts = new OrmEntitySet<>(this, PROP_NAME_contexts,
        io.nop.ai.dao.entity.NopAiSessionContext.PROP_NAME_session, null,io.nop.ai.dao.entity.NopAiSessionContext.class);

    /**
     * 上下文快照。 refPropName: session, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<io.nop.ai.dao.entity.NopAiSessionContext> getContexts(){
       return _contexts;
    }
       
    private final OrmEntitySet<io.nop.ai.dao.entity.NopAiSessionInput> _inputs = new OrmEntitySet<>(this, PROP_NAME_inputs,
        io.nop.ai.dao.entity.NopAiSessionInput.PROP_NAME_session, null,io.nop.ai.dao.entity.NopAiSessionInput.class);

    /**
     * 输入队列。 refPropName: session, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<io.nop.ai.dao.entity.NopAiSessionInput> getInputs(){
       return _inputs;
    }
       
    private final OrmEntitySet<io.nop.ai.dao.entity.NopAiTodo> _todos = new OrmEntitySet<>(this, PROP_NAME_todos,
        io.nop.ai.dao.entity.NopAiTodo.PROP_NAME_session, null,io.nop.ai.dao.entity.NopAiTodo.class);

    /**
     * 待办列表。 refPropName: session, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<io.nop.ai.dao.entity.NopAiTodo> getTodos(){
       return _todos;
    }
       
    private final OrmEntitySet<io.nop.ai.dao.entity.NopAiEvent> _events = new OrmEntitySet<>(this, PROP_NAME_events,
        io.nop.ai.dao.entity.NopAiEvent.PROP_NAME_session, null,io.nop.ai.dao.entity.NopAiEvent.class);

    /**
     * 事件日志。 refPropName: session, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<io.nop.ai.dao.entity.NopAiEvent> getEvents(){
       return _events;
    }
       
    private final OrmEntitySet<io.nop.ai.dao.entity.NopAiSessionContext> _context = new OrmEntitySet<>(this, PROP_NAME_context,
        io.nop.ai.dao.entity.NopAiSessionContext.PROP_NAME_session, null,io.nop.ai.dao.entity.NopAiSessionContext.class);

    /**
     * 。 refPropName: session, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<io.nop.ai.dao.entity.NopAiSessionContext> getContext(){
       return _context;
    }
       
   private io.nop.orm.component.JsonOrmComponent _contextMetadataComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_contextMetadataComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_contextMetadataComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_contextMetadata);
      
   }

   public final io.nop.orm.component.JsonOrmComponent getContextMetadataComponent(){
      if(_contextMetadataComponent == null){
          _contextMetadataComponent = new io.nop.orm.component.JsonOrmComponent();
          _contextMetadataComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_contextMetadataComponent);
      }
      return _contextMetadataComponent;
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
