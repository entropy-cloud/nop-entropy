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

import nop.ai.dao.entity.NopAiChatResponse;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  响应结果: nop_ai_chat_response
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopAiChatResponse extends DynamicOrmEntity{
    
    /* 主键: id VARCHAR */
    public static final String PROP_NAME_id = "id";
    public static final int PROP_ID_id = 1;
    
    /* 请求ID: request_id VARCHAR */
    public static final String PROP_NAME_requestId = "requestId";
    public static final int PROP_ID_requestId = 2;
    
    /* 会话ID: session_id VARCHAR */
    public static final String PROP_NAME_sessionId = "sessionId";
    public static final int PROP_ID_sessionId = 3;
    
    /* 模型ID: model_id VARCHAR */
    public static final String PROP_NAME_modelId = "modelId";
    public static final int PROP_ID_modelId = 4;
    
    /* 供应商: ai_provider VARCHAR */
    public static final String PROP_NAME_aiProvider = "aiProvider";
    public static final int PROP_ID_aiProvider = 5;
    
    /* 模型名称: ai_model VARCHAR */
    public static final String PROP_NAME_aiModel = "aiModel";
    public static final int PROP_ID_aiModel = 6;
    
    /* 响应内容: response_content VARCHAR */
    public static final String PROP_NAME_responseContent = "responseContent";
    public static final int PROP_ID_responseContent = 7;
    
    /* 响应时间戳: response_timestamp TIMESTAMP */
    public static final String PROP_NAME_responseTimestamp = "responseTimestamp";
    public static final int PROP_ID_responseTimestamp = 8;
    
    /* 请求Token数: prompt_tokens INTEGER */
    public static final String PROP_NAME_promptTokens = "promptTokens";
    public static final int PROP_ID_promptTokens = 9;
    
    /* 响应Token数: completion_tokens INTEGER */
    public static final String PROP_NAME_completionTokens = "completionTokens";
    public static final int PROP_ID_completionTokens = 10;
    
    /* 响应耗时(毫秒): response_duration_ms INTEGER */
    public static final String PROP_NAME_responseDurationMs = "responseDurationMs";
    public static final int PROP_ID_responseDurationMs = 11;
    
    /* 正确性分: correctness_score DECIMAL */
    public static final String PROP_NAME_correctnessScore = "correctnessScore";
    public static final int PROP_ID_correctnessScore = 12;
    
    /* 性能分: performance_score DECIMAL */
    public static final String PROP_NAME_performanceScore = "performanceScore";
    public static final int PROP_ID_performanceScore = 13;
    
    /* 可读性分: readability_score DECIMAL */
    public static final String PROP_NAME_readabilityScore = "readabilityScore";
    public static final int PROP_ID_readabilityScore = 14;
    
    /* 合规性分: compliance_score DECIMAL */
    public static final String PROP_NAME_complianceScore = "complianceScore";
    public static final int PROP_ID_complianceScore = 15;
    

    private static int _PROP_ID_BOUND = 16;

    
    /* relation:  */
    public static final String PROP_NAME_request = "request";
    
    /* relation:  */
    public static final String PROP_NAME_model = "model";
    
    /* relation: 生成产物 */
    public static final String PROP_NAME_generatedFiles = "generatedFiles";
    
    /* relation: 生成用例 */
    public static final String PROP_NAME_testCases = "testCases";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_id);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_id};

    private static final String[] PROP_ID_TO_NAME = new String[16];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_id] = PROP_NAME_id;
          PROP_NAME_TO_ID.put(PROP_NAME_id, PROP_ID_id);
      
          PROP_ID_TO_NAME[PROP_ID_requestId] = PROP_NAME_requestId;
          PROP_NAME_TO_ID.put(PROP_NAME_requestId, PROP_ID_requestId);
      
          PROP_ID_TO_NAME[PROP_ID_sessionId] = PROP_NAME_sessionId;
          PROP_NAME_TO_ID.put(PROP_NAME_sessionId, PROP_ID_sessionId);
      
          PROP_ID_TO_NAME[PROP_ID_modelId] = PROP_NAME_modelId;
          PROP_NAME_TO_ID.put(PROP_NAME_modelId, PROP_ID_modelId);
      
          PROP_ID_TO_NAME[PROP_ID_aiProvider] = PROP_NAME_aiProvider;
          PROP_NAME_TO_ID.put(PROP_NAME_aiProvider, PROP_ID_aiProvider);
      
          PROP_ID_TO_NAME[PROP_ID_aiModel] = PROP_NAME_aiModel;
          PROP_NAME_TO_ID.put(PROP_NAME_aiModel, PROP_ID_aiModel);
      
          PROP_ID_TO_NAME[PROP_ID_responseContent] = PROP_NAME_responseContent;
          PROP_NAME_TO_ID.put(PROP_NAME_responseContent, PROP_ID_responseContent);
      
          PROP_ID_TO_NAME[PROP_ID_responseTimestamp] = PROP_NAME_responseTimestamp;
          PROP_NAME_TO_ID.put(PROP_NAME_responseTimestamp, PROP_ID_responseTimestamp);
      
          PROP_ID_TO_NAME[PROP_ID_promptTokens] = PROP_NAME_promptTokens;
          PROP_NAME_TO_ID.put(PROP_NAME_promptTokens, PROP_ID_promptTokens);
      
          PROP_ID_TO_NAME[PROP_ID_completionTokens] = PROP_NAME_completionTokens;
          PROP_NAME_TO_ID.put(PROP_NAME_completionTokens, PROP_ID_completionTokens);
      
          PROP_ID_TO_NAME[PROP_ID_responseDurationMs] = PROP_NAME_responseDurationMs;
          PROP_NAME_TO_ID.put(PROP_NAME_responseDurationMs, PROP_ID_responseDurationMs);
      
          PROP_ID_TO_NAME[PROP_ID_correctnessScore] = PROP_NAME_correctnessScore;
          PROP_NAME_TO_ID.put(PROP_NAME_correctnessScore, PROP_ID_correctnessScore);
      
          PROP_ID_TO_NAME[PROP_ID_performanceScore] = PROP_NAME_performanceScore;
          PROP_NAME_TO_ID.put(PROP_NAME_performanceScore, PROP_ID_performanceScore);
      
          PROP_ID_TO_NAME[PROP_ID_readabilityScore] = PROP_NAME_readabilityScore;
          PROP_NAME_TO_ID.put(PROP_NAME_readabilityScore, PROP_ID_readabilityScore);
      
          PROP_ID_TO_NAME[PROP_ID_complianceScore] = PROP_NAME_complianceScore;
          PROP_NAME_TO_ID.put(PROP_NAME_complianceScore, PROP_ID_complianceScore);
      
    }

    
    /* 主键: id */
    private java.lang.String _id;
    
    /* 请求ID: request_id */
    private java.lang.String _requestId;
    
    /* 会话ID: session_id */
    private java.lang.String _sessionId;
    
    /* 模型ID: model_id */
    private java.lang.String _modelId;
    
    /* 供应商: ai_provider */
    private java.lang.String _aiProvider;
    
    /* 模型名称: ai_model */
    private java.lang.String _aiModel;
    
    /* 响应内容: response_content */
    private java.lang.String _responseContent;
    
    /* 响应时间戳: response_timestamp */
    private java.sql.Timestamp _responseTimestamp;
    
    /* 请求Token数: prompt_tokens */
    private java.lang.Integer _promptTokens;
    
    /* 响应Token数: completion_tokens */
    private java.lang.Integer _completionTokens;
    
    /* 响应耗时(毫秒): response_duration_ms */
    private java.lang.Integer _responseDurationMs;
    
    /* 正确性分: correctness_score */
    private java.math.BigDecimal _correctnessScore;
    
    /* 性能分: performance_score */
    private java.math.BigDecimal _performanceScore;
    
    /* 可读性分: readability_score */
    private java.math.BigDecimal _readabilityScore;
    
    /* 合规性分: compliance_score */
    private java.math.BigDecimal _complianceScore;
    

    public _NopAiChatResponse(){
        // for debug
    }

    protected NopAiChatResponse newInstance(){
        NopAiChatResponse entity = new NopAiChatResponse();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopAiChatResponse cloneInstance() {
        NopAiChatResponse entity = newInstance();
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
      return "nop.ai.dao.entity.NopAiChatResponse";
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
        
            case PROP_ID_requestId:
               return getRequestId();
        
            case PROP_ID_sessionId:
               return getSessionId();
        
            case PROP_ID_modelId:
               return getModelId();
        
            case PROP_ID_aiProvider:
               return getAiProvider();
        
            case PROP_ID_aiModel:
               return getAiModel();
        
            case PROP_ID_responseContent:
               return getResponseContent();
        
            case PROP_ID_responseTimestamp:
               return getResponseTimestamp();
        
            case PROP_ID_promptTokens:
               return getPromptTokens();
        
            case PROP_ID_completionTokens:
               return getCompletionTokens();
        
            case PROP_ID_responseDurationMs:
               return getResponseDurationMs();
        
            case PROP_ID_correctnessScore:
               return getCorrectnessScore();
        
            case PROP_ID_performanceScore:
               return getPerformanceScore();
        
            case PROP_ID_readabilityScore:
               return getReadabilityScore();
        
            case PROP_ID_complianceScore:
               return getComplianceScore();
        
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
        
            case PROP_ID_requestId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_requestId));
               }
               setRequestId(typedValue);
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
        
            case PROP_ID_modelId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_modelId));
               }
               setModelId(typedValue);
               break;
            }
        
            case PROP_ID_aiProvider:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_aiProvider));
               }
               setAiProvider(typedValue);
               break;
            }
        
            case PROP_ID_aiModel:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_aiModel));
               }
               setAiModel(typedValue);
               break;
            }
        
            case PROP_ID_responseContent:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_responseContent));
               }
               setResponseContent(typedValue);
               break;
            }
        
            case PROP_ID_responseTimestamp:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_responseTimestamp));
               }
               setResponseTimestamp(typedValue);
               break;
            }
        
            case PROP_ID_promptTokens:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_promptTokens));
               }
               setPromptTokens(typedValue);
               break;
            }
        
            case PROP_ID_completionTokens:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_completionTokens));
               }
               setCompletionTokens(typedValue);
               break;
            }
        
            case PROP_ID_responseDurationMs:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_responseDurationMs));
               }
               setResponseDurationMs(typedValue);
               break;
            }
        
            case PROP_ID_correctnessScore:{
               java.math.BigDecimal typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toBigDecimal(value,
                       err-> newTypeConversionError(PROP_NAME_correctnessScore));
               }
               setCorrectnessScore(typedValue);
               break;
            }
        
            case PROP_ID_performanceScore:{
               java.math.BigDecimal typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toBigDecimal(value,
                       err-> newTypeConversionError(PROP_NAME_performanceScore));
               }
               setPerformanceScore(typedValue);
               break;
            }
        
            case PROP_ID_readabilityScore:{
               java.math.BigDecimal typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toBigDecimal(value,
                       err-> newTypeConversionError(PROP_NAME_readabilityScore));
               }
               setReadabilityScore(typedValue);
               break;
            }
        
            case PROP_ID_complianceScore:{
               java.math.BigDecimal typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toBigDecimal(value,
                       err-> newTypeConversionError(PROP_NAME_complianceScore));
               }
               setComplianceScore(typedValue);
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
        
            case PROP_ID_requestId:{
               onInitProp(propId);
               this._requestId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_sessionId:{
               onInitProp(propId);
               this._sessionId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_modelId:{
               onInitProp(propId);
               this._modelId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_aiProvider:{
               onInitProp(propId);
               this._aiProvider = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_aiModel:{
               onInitProp(propId);
               this._aiModel = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_responseContent:{
               onInitProp(propId);
               this._responseContent = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_responseTimestamp:{
               onInitProp(propId);
               this._responseTimestamp = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_promptTokens:{
               onInitProp(propId);
               this._promptTokens = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_completionTokens:{
               onInitProp(propId);
               this._completionTokens = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_responseDurationMs:{
               onInitProp(propId);
               this._responseDurationMs = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_correctnessScore:{
               onInitProp(propId);
               this._correctnessScore = (java.math.BigDecimal)value;
               
               break;
            }
        
            case PROP_ID_performanceScore:{
               onInitProp(propId);
               this._performanceScore = (java.math.BigDecimal)value;
               
               break;
            }
        
            case PROP_ID_readabilityScore:{
               onInitProp(propId);
               this._readabilityScore = (java.math.BigDecimal)value;
               
               break;
            }
        
            case PROP_ID_complianceScore:{
               onInitProp(propId);
               this._complianceScore = (java.math.BigDecimal)value;
               
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
     * 请求ID: request_id
     */
    public final java.lang.String getRequestId(){
         onPropGet(PROP_ID_requestId);
         return _requestId;
    }

    /**
     * 请求ID: request_id
     */
    public final void setRequestId(java.lang.String value){
        if(onPropSet(PROP_ID_requestId,value)){
            this._requestId = value;
            internalClearRefs(PROP_ID_requestId);
            
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
     * 模型ID: model_id
     */
    public final java.lang.String getModelId(){
         onPropGet(PROP_ID_modelId);
         return _modelId;
    }

    /**
     * 模型ID: model_id
     */
    public final void setModelId(java.lang.String value){
        if(onPropSet(PROP_ID_modelId,value)){
            this._modelId = value;
            internalClearRefs(PROP_ID_modelId);
            
        }
    }
    
    /**
     * 供应商: ai_provider
     */
    public final java.lang.String getAiProvider(){
         onPropGet(PROP_ID_aiProvider);
         return _aiProvider;
    }

    /**
     * 供应商: ai_provider
     */
    public final void setAiProvider(java.lang.String value){
        if(onPropSet(PROP_ID_aiProvider,value)){
            this._aiProvider = value;
            internalClearRefs(PROP_ID_aiProvider);
            
        }
    }
    
    /**
     * 模型名称: ai_model
     */
    public final java.lang.String getAiModel(){
         onPropGet(PROP_ID_aiModel);
         return _aiModel;
    }

    /**
     * 模型名称: ai_model
     */
    public final void setAiModel(java.lang.String value){
        if(onPropSet(PROP_ID_aiModel,value)){
            this._aiModel = value;
            internalClearRefs(PROP_ID_aiModel);
            
        }
    }
    
    /**
     * 响应内容: response_content
     */
    public final java.lang.String getResponseContent(){
         onPropGet(PROP_ID_responseContent);
         return _responseContent;
    }

    /**
     * 响应内容: response_content
     */
    public final void setResponseContent(java.lang.String value){
        if(onPropSet(PROP_ID_responseContent,value)){
            this._responseContent = value;
            internalClearRefs(PROP_ID_responseContent);
            
        }
    }
    
    /**
     * 响应时间戳: response_timestamp
     */
    public final java.sql.Timestamp getResponseTimestamp(){
         onPropGet(PROP_ID_responseTimestamp);
         return _responseTimestamp;
    }

    /**
     * 响应时间戳: response_timestamp
     */
    public final void setResponseTimestamp(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_responseTimestamp,value)){
            this._responseTimestamp = value;
            internalClearRefs(PROP_ID_responseTimestamp);
            
        }
    }
    
    /**
     * 请求Token数: prompt_tokens
     */
    public final java.lang.Integer getPromptTokens(){
         onPropGet(PROP_ID_promptTokens);
         return _promptTokens;
    }

    /**
     * 请求Token数: prompt_tokens
     */
    public final void setPromptTokens(java.lang.Integer value){
        if(onPropSet(PROP_ID_promptTokens,value)){
            this._promptTokens = value;
            internalClearRefs(PROP_ID_promptTokens);
            
        }
    }
    
    /**
     * 响应Token数: completion_tokens
     */
    public final java.lang.Integer getCompletionTokens(){
         onPropGet(PROP_ID_completionTokens);
         return _completionTokens;
    }

    /**
     * 响应Token数: completion_tokens
     */
    public final void setCompletionTokens(java.lang.Integer value){
        if(onPropSet(PROP_ID_completionTokens,value)){
            this._completionTokens = value;
            internalClearRefs(PROP_ID_completionTokens);
            
        }
    }
    
    /**
     * 响应耗时(毫秒): response_duration_ms
     */
    public final java.lang.Integer getResponseDurationMs(){
         onPropGet(PROP_ID_responseDurationMs);
         return _responseDurationMs;
    }

    /**
     * 响应耗时(毫秒): response_duration_ms
     */
    public final void setResponseDurationMs(java.lang.Integer value){
        if(onPropSet(PROP_ID_responseDurationMs,value)){
            this._responseDurationMs = value;
            internalClearRefs(PROP_ID_responseDurationMs);
            
        }
    }
    
    /**
     * 正确性分: correctness_score
     */
    public final java.math.BigDecimal getCorrectnessScore(){
         onPropGet(PROP_ID_correctnessScore);
         return _correctnessScore;
    }

    /**
     * 正确性分: correctness_score
     */
    public final void setCorrectnessScore(java.math.BigDecimal value){
        if(onPropSet(PROP_ID_correctnessScore,value)){
            this._correctnessScore = value;
            internalClearRefs(PROP_ID_correctnessScore);
            
        }
    }
    
    /**
     * 性能分: performance_score
     */
    public final java.math.BigDecimal getPerformanceScore(){
         onPropGet(PROP_ID_performanceScore);
         return _performanceScore;
    }

    /**
     * 性能分: performance_score
     */
    public final void setPerformanceScore(java.math.BigDecimal value){
        if(onPropSet(PROP_ID_performanceScore,value)){
            this._performanceScore = value;
            internalClearRefs(PROP_ID_performanceScore);
            
        }
    }
    
    /**
     * 可读性分: readability_score
     */
    public final java.math.BigDecimal getReadabilityScore(){
         onPropGet(PROP_ID_readabilityScore);
         return _readabilityScore;
    }

    /**
     * 可读性分: readability_score
     */
    public final void setReadabilityScore(java.math.BigDecimal value){
        if(onPropSet(PROP_ID_readabilityScore,value)){
            this._readabilityScore = value;
            internalClearRefs(PROP_ID_readabilityScore);
            
        }
    }
    
    /**
     * 合规性分: compliance_score
     */
    public final java.math.BigDecimal getComplianceScore(){
         onPropGet(PROP_ID_complianceScore);
         return _complianceScore;
    }

    /**
     * 合规性分: compliance_score
     */
    public final void setComplianceScore(java.math.BigDecimal value){
        if(onPropSet(PROP_ID_complianceScore,value)){
            this._complianceScore = value;
            internalClearRefs(PROP_ID_complianceScore);
            
        }
    }
    
    /**
     * 
     */
    public final nop.ai.dao.entity.NopAiChatRequest getRequest(){
       return (nop.ai.dao.entity.NopAiChatRequest)internalGetRefEntity(PROP_NAME_request);
    }

    public final void setRequest(nop.ai.dao.entity.NopAiChatRequest refEntity){
   
           if(refEntity == null){
           
                   this.setRequestId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_request, refEntity,()->{
           
                           this.setRequestId(refEntity.getId());
                       
           });
           }
       
    }
       
    /**
     * 
     */
    public final nop.ai.dao.entity.NopAiModel getModel(){
       return (nop.ai.dao.entity.NopAiModel)internalGetRefEntity(PROP_NAME_model);
    }

    public final void setModel(nop.ai.dao.entity.NopAiModel refEntity){
   
           if(refEntity == null){
           
                   this.setModelId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_model, refEntity,()->{
           
                           this.setModelId(refEntity.getId());
                       
           });
           }
       
    }
       
    private final OrmEntitySet<nop.ai.dao.entity.NopAiGenFile> _generatedFiles = new OrmEntitySet<>(this, PROP_NAME_generatedFiles,
        nop.ai.dao.entity.NopAiGenFile.PROP_NAME_chatResponse, null,nop.ai.dao.entity.NopAiGenFile.class);

    /**
     * 生成产物。 refPropName: chatResponse, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<nop.ai.dao.entity.NopAiGenFile> getGeneratedFiles(){
       return _generatedFiles;
    }
       
    private final OrmEntitySet<nop.ai.dao.entity.NopAiTestCase> _testCases = new OrmEntitySet<>(this, PROP_NAME_testCases,
        nop.ai.dao.entity.NopAiTestCase.PROP_NAME_chatResponse, null,nop.ai.dao.entity.NopAiTestCase.class);

    /**
     * 生成用例。 refPropName: chatResponse, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<nop.ai.dao.entity.NopAiTestCase> getTestCases(){
       return _testCases;
    }
       
}
// resume CPD analysis - CPD-ON
