package io.nop.ai.core.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ai.core.model.LlmModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/ai/llm.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _LlmModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  备用账号链（plan 2026-08-01-1505-1，设计 §3.6）
     * xml name: accounts
     * provider 级有序备用账号清单。每个账号 = apiKey（直配值，生产经 Nop config 变量替换/secret 注入）
     * + 可选 baseUrl（per-account 覆盖）+ 可选额度元数据（quotaLimit/renewAt，仅声明/诊断用，不做主动熔断）。
     * 链语义：<accounts> 是备用账号链（不含主账号）。主账号 = 现有单个 resolveApiKey(provider)
     * （config 变量 nop.ai.llm.{provider}.api-key 或 secret 文件），与未配置 <accounts> 时完全一致（零回归）。
     * 首次调用用主账号；QUOTA_EXCEEDED/AUTH_INVALID 触发后从 accounts[0] 开始依次切换，链耗尽 fail-loud。
     * xdef:key-attr="id"（与 <errorMappings> 同款）：id 用于 x:extends 合并时区分条目，合并后顺序保持。
     */
    private KeyedList<io.nop.ai.core.model.LlmAccountModel> _accounts = KeyedList.emptyList();
    
    /**
     *  
     * xml name: aliasMap
     * 
     */
    private java.util.Map<java.lang.String,java.lang.String> _aliasMap ;
    
    /**
     *  
     * xml name: apiKeyHeader
     * 
     */
    private java.lang.String _apiKeyHeader ;
    
    /**
     *  
     * xml name: apiStyle
     * 
     */
    private io.nop.ai.core.model.ApiStyle _apiStyle ;
    
    /**
     *  
     * xml name: baseUrl
     * 服务的基础url，比如http://localhost:11342
     */
    private java.lang.String _baseUrl ;
    
    /**
     *  
     * xml name: buildHttpRequest
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _buildHttpRequest ;
    
    /**
     *  
     * xml name: chatUrl
     * 聊天功能的服务端点，比如 /api/chat
     */
    private java.lang.String _chatUrl ;
    
    /**
     *  可选全局逃生舱
     * xml name: classifyError
     * 覆盖配置表无法表达的 ~10% 硬场景（Azure 嵌套多拼写 inner_error.code vs innererror.code、
     * 负向排除等）。配置 <errorMappings> 优先，命中即用；未命中再走本函数；都未命中走默认启发式。
     * 风格与 <buildHttpRequest>/<parseHttpResponse> 的 xpl-fn 节点一致。
     */
    private io.nop.core.lang.eval.IEvalFunction _classifyError ;
    
    /**
     *  
     * xml name: defaultModel
     * 
     */
    private java.lang.String _defaultModel ;
    
    /**
     *  
     * xml name: defaultRequestTimeout
     * 
     */
    private java.lang.Long _defaultRequestTimeout ;
    
    /**
     *  
     * xml name: embedUrl
     * 
     */
    private java.lang.String _embedUrl ;
    
    /**
     *  错误映射：多条件合取 → 固定分类
     * xml name: errorMappings
     * 把错误字段值 + HTTP 状态映射到固定 ErrorClassification。有序规则表，首条匹配胜出
     * （first-match-wins）。
     * ⚠️ xdef:key-attr="id"（与 dialect.xdef <errorCodes xdef:key-attr="name"> 同款）：
     * id 用于 x:extends 合并时区分集合条目——子配置（如 azure.llm.xml extends default.llm.xml）
     * 可用相同 id 覆盖父配置的条目（replaceChild 在原位置替换），或新增 id 追加条目。
     * 合并后顺序保持：被覆盖条目保留父位置，新增条目追加末尾，故 first-match-wins 仍成立。
     * 每个 errorMapping 必须有唯一 id（如 openai-quota-exceeded / anthropic-billing）。
     * 匹配优先级（对每条 errorMapping 按合并后顺序）：
     * 1. httpStatus 未设 或 实际状态 ∈ httpStatus
     * 2. errorTypes 未设 或 抽到的 error.type ∈ errorTypes
     * 3. errorCodes 未设 或 抽到的 error.code ∈ errorCodes
     * 4. messagePattern 未设 或 消息匹配正则
     * 全部命中 → 取该条 classification；全部未命中 → 默认启发式（按 HTTP 状态码）
     * messagePattern 为必需（非可选）：Gemini 429 RESOURCE_EXHAUSTED 限流与配额同型，
     * 只有消息文本能区分。复刻 dialect.xdef 消息正则规则：空白替下划线、"." 跨行、
     * 大小写无关（参考 DialectSQLExceptionTranslator）。
     */
    private KeyedList<io.nop.ai.core.model.LlmErrorMappingModel> _errorMappings = KeyedList.emptyList();
    
    /**
     *  错误响应字段抽取
     * xml name: errorResponse
     * 告诉底层从错误响应体抽取哪些字段用于错误分类（对应 <response> 抽成功体字段）。
     * 沿用 prop-path（支持点号嵌套，如 OpenAI "error.type"）。
     */
    private io.nop.ai.core.model.LlmErrorResponseModel _errorResponse ;
    
    /**
     *  
     * xml name: generateUrl
     * 单次生成服务断点，比如 /api/generate
     */
    private java.lang.String _generateUrl ;
    
    /**
     *  
     * xml name: logMessage
     * 如果设置为true，则会打印出所有请求和响应消息
     */
    private boolean _logMessage  = true;
    
    /**
     *  
     * xml name: models
     * 
     */
    private KeyedList<io.nop.ai.core.model.LlmModelModel> _models = KeyedList.emptyList();
    
    /**
     *  
     * xml name: parseHttpResponse
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _parseHttpResponse ;
    
    /**
     *  
     * xml name: rateLimit
     * 为避免调用服务过于频繁，通过rateLimit指定每秒最多允许多少次请求。如果超过则会排队等待。
     */
    private java.lang.Double _rateLimit ;
    
    /**
     *  
     * xml name: request
     * 
     */
    private io.nop.ai.core.model.LlmRequestModel _request ;
    
    /**
     *  
     * xml name: response
     * 
     */
    private io.nop.ai.core.model.LlmResponseModel _response ;
    
    /**
     *  
     * xml name: supportModels
     * 大模型服务所支持的模型列表。通过defaultModel来指定缺省使用的模型
     */
    private java.util.Set<java.lang.String> _supportModels ;
    
    /**
     *  
     * xml name: supportToolCalls
     * 
     */
    private boolean _supportToolCalls ;
    
    /**
     * 备用账号链（plan 2026-08-01-1505-1，设计 §3.6）
     * xml name: accounts
     *  provider 级有序备用账号清单。每个账号 = apiKey（直配值，生产经 Nop config 变量替换/secret 注入）
     * + 可选 baseUrl（per-account 覆盖）+ 可选额度元数据（quotaLimit/renewAt，仅声明/诊断用，不做主动熔断）。
     * 链语义：<accounts> 是备用账号链（不含主账号）。主账号 = 现有单个 resolveApiKey(provider)
     * （config 变量 nop.ai.llm.{provider}.api-key 或 secret 文件），与未配置 <accounts> 时完全一致（零回归）。
     * 首次调用用主账号；QUOTA_EXCEEDED/AUTH_INVALID 触发后从 accounts[0] 开始依次切换，链耗尽 fail-loud。
     * xdef:key-attr="id"（与 <errorMappings> 同款）：id 用于 x:extends 合并时区分条目，合并后顺序保持。
     */
    
    public java.util.List<io.nop.ai.core.model.LlmAccountModel> getAccounts(){
      return _accounts;
    }

    
    public void setAccounts(java.util.List<io.nop.ai.core.model.LlmAccountModel> value){
        checkAllowChange();
        
        this._accounts = KeyedList.fromList(value, io.nop.ai.core.model.LlmAccountModel::getId);
           
    }

    
    public io.nop.ai.core.model.LlmAccountModel getAccount(String name){
        return this._accounts.getByKey(name);
    }

    public boolean hasAccount(String name){
        return this._accounts.containsKey(name);
    }

    public void addAccount(io.nop.ai.core.model.LlmAccountModel item) {
        checkAllowChange();
        java.util.List<io.nop.ai.core.model.LlmAccountModel> list = this.getAccounts();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.ai.core.model.LlmAccountModel::getId);
            setAccounts(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_accounts(){
        return this._accounts.keySet();
    }

    public boolean hasAccounts(){
        return !this._accounts.isEmpty();
    }
    
    /**
     * 
     * xml name: aliasMap
     *  
     */
    
    public java.util.Map<java.lang.String,java.lang.String> getAliasMap(){
      return _aliasMap;
    }

    
    public void setAliasMap(java.util.Map<java.lang.String,java.lang.String> value){
        checkAllowChange();
        
        this._aliasMap = value;
           
    }

    
    public boolean hasAliasMap(){
        return this._aliasMap != null && !this._aliasMap.isEmpty();
    }
    
    /**
     * 
     * xml name: apiKeyHeader
     *  
     */
    
    public java.lang.String getApiKeyHeader(){
      return _apiKeyHeader;
    }

    
    public void setApiKeyHeader(java.lang.String value){
        checkAllowChange();
        
        this._apiKeyHeader = value;
           
    }

    
    /**
     * 
     * xml name: apiStyle
     *  
     */
    
    public io.nop.ai.core.model.ApiStyle getApiStyle(){
      return _apiStyle;
    }

    
    public void setApiStyle(io.nop.ai.core.model.ApiStyle value){
        checkAllowChange();
        
        this._apiStyle = value;
           
    }

    
    /**
     * 
     * xml name: baseUrl
     *  服务的基础url，比如http://localhost:11342
     */
    
    public java.lang.String getBaseUrl(){
      return _baseUrl;
    }

    
    public void setBaseUrl(java.lang.String value){
        checkAllowChange();
        
        this._baseUrl = value;
           
    }

    
    /**
     * 
     * xml name: buildHttpRequest
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getBuildHttpRequest(){
      return _buildHttpRequest;
    }

    
    public void setBuildHttpRequest(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._buildHttpRequest = value;
           
    }

    
    /**
     * 
     * xml name: chatUrl
     *  聊天功能的服务端点，比如 /api/chat
     */
    
    public java.lang.String getChatUrl(){
      return _chatUrl;
    }

    
    public void setChatUrl(java.lang.String value){
        checkAllowChange();
        
        this._chatUrl = value;
           
    }

    
    /**
     * 可选全局逃生舱
     * xml name: classifyError
     *  覆盖配置表无法表达的 ~10% 硬场景（Azure 嵌套多拼写 inner_error.code vs innererror.code、
     * 负向排除等）。配置 <errorMappings> 优先，命中即用；未命中再走本函数；都未命中走默认启发式。
     * 风格与 <buildHttpRequest>/<parseHttpResponse> 的 xpl-fn 节点一致。
     */
    
    public io.nop.core.lang.eval.IEvalFunction getClassifyError(){
      return _classifyError;
    }

    
    public void setClassifyError(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._classifyError = value;
           
    }

    
    /**
     * 
     * xml name: defaultModel
     *  
     */
    
    public java.lang.String getDefaultModel(){
      return _defaultModel;
    }

    
    public void setDefaultModel(java.lang.String value){
        checkAllowChange();
        
        this._defaultModel = value;
           
    }

    
    /**
     * 
     * xml name: defaultRequestTimeout
     *  
     */
    
    public java.lang.Long getDefaultRequestTimeout(){
      return _defaultRequestTimeout;
    }

    
    public void setDefaultRequestTimeout(java.lang.Long value){
        checkAllowChange();
        
        this._defaultRequestTimeout = value;
           
    }

    
    /**
     * 
     * xml name: embedUrl
     *  
     */
    
    public java.lang.String getEmbedUrl(){
      return _embedUrl;
    }

    
    public void setEmbedUrl(java.lang.String value){
        checkAllowChange();
        
        this._embedUrl = value;
           
    }

    
    /**
     * 错误映射：多条件合取 → 固定分类
     * xml name: errorMappings
     *  把错误字段值 + HTTP 状态映射到固定 ErrorClassification。有序规则表，首条匹配胜出
     * （first-match-wins）。
     * ⚠️ xdef:key-attr="id"（与 dialect.xdef <errorCodes xdef:key-attr="name"> 同款）：
     * id 用于 x:extends 合并时区分集合条目——子配置（如 azure.llm.xml extends default.llm.xml）
     * 可用相同 id 覆盖父配置的条目（replaceChild 在原位置替换），或新增 id 追加条目。
     * 合并后顺序保持：被覆盖条目保留父位置，新增条目追加末尾，故 first-match-wins 仍成立。
     * 每个 errorMapping 必须有唯一 id（如 openai-quota-exceeded / anthropic-billing）。
     * 匹配优先级（对每条 errorMapping 按合并后顺序）：
     * 1. httpStatus 未设 或 实际状态 ∈ httpStatus
     * 2. errorTypes 未设 或 抽到的 error.type ∈ errorTypes
     * 3. errorCodes 未设 或 抽到的 error.code ∈ errorCodes
     * 4. messagePattern 未设 或 消息匹配正则
     * 全部命中 → 取该条 classification；全部未命中 → 默认启发式（按 HTTP 状态码）
     * messagePattern 为必需（非可选）：Gemini 429 RESOURCE_EXHAUSTED 限流与配额同型，
     * 只有消息文本能区分。复刻 dialect.xdef 消息正则规则：空白替下划线、"." 跨行、
     * 大小写无关（参考 DialectSQLExceptionTranslator）。
     */
    
    public java.util.List<io.nop.ai.core.model.LlmErrorMappingModel> getErrorMappings(){
      return _errorMappings;
    }

    
    public void setErrorMappings(java.util.List<io.nop.ai.core.model.LlmErrorMappingModel> value){
        checkAllowChange();
        
        this._errorMappings = KeyedList.fromList(value, io.nop.ai.core.model.LlmErrorMappingModel::getId);
           
    }

    
    public io.nop.ai.core.model.LlmErrorMappingModel getErrorMapping(String name){
        return this._errorMappings.getByKey(name);
    }

    public boolean hasErrorMapping(String name){
        return this._errorMappings.containsKey(name);
    }

    public void addErrorMapping(io.nop.ai.core.model.LlmErrorMappingModel item) {
        checkAllowChange();
        java.util.List<io.nop.ai.core.model.LlmErrorMappingModel> list = this.getErrorMappings();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.ai.core.model.LlmErrorMappingModel::getId);
            setErrorMappings(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_errorMappings(){
        return this._errorMappings.keySet();
    }

    public boolean hasErrorMappings(){
        return !this._errorMappings.isEmpty();
    }
    
    /**
     * 错误响应字段抽取
     * xml name: errorResponse
     *  告诉底层从错误响应体抽取哪些字段用于错误分类（对应 <response> 抽成功体字段）。
     * 沿用 prop-path（支持点号嵌套，如 OpenAI "error.type"）。
     */
    
    public io.nop.ai.core.model.LlmErrorResponseModel getErrorResponse(){
      return _errorResponse;
    }

    
    public void setErrorResponse(io.nop.ai.core.model.LlmErrorResponseModel value){
        checkAllowChange();
        
        this._errorResponse = value;
           
    }

    
    /**
     * 
     * xml name: generateUrl
     *  单次生成服务断点，比如 /api/generate
     */
    
    public java.lang.String getGenerateUrl(){
      return _generateUrl;
    }

    
    public void setGenerateUrl(java.lang.String value){
        checkAllowChange();
        
        this._generateUrl = value;
           
    }

    
    /**
     * 
     * xml name: logMessage
     *  如果设置为true，则会打印出所有请求和响应消息
     */
    
    public boolean isLogMessage(){
      return _logMessage;
    }

    
    public void setLogMessage(boolean value){
        checkAllowChange();
        
        this._logMessage = value;
           
    }

    
    /**
     * 
     * xml name: models
     *  
     */
    
    public java.util.List<io.nop.ai.core.model.LlmModelModel> getModels(){
      return _models;
    }

    
    public void setModels(java.util.List<io.nop.ai.core.model.LlmModelModel> value){
        checkAllowChange();
        
        this._models = KeyedList.fromList(value, io.nop.ai.core.model.LlmModelModel::getName);
           
    }

    
    public io.nop.ai.core.model.LlmModelModel getModel(String name){
        return this._models.getByKey(name);
    }

    public boolean hasModel(String name){
        return this._models.containsKey(name);
    }

    public void addModel(io.nop.ai.core.model.LlmModelModel item) {
        checkAllowChange();
        java.util.List<io.nop.ai.core.model.LlmModelModel> list = this.getModels();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.ai.core.model.LlmModelModel::getName);
            setModels(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_models(){
        return this._models.keySet();
    }

    public boolean hasModels(){
        return !this._models.isEmpty();
    }
    
    /**
     * 
     * xml name: parseHttpResponse
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getParseHttpResponse(){
      return _parseHttpResponse;
    }

    
    public void setParseHttpResponse(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._parseHttpResponse = value;
           
    }

    
    /**
     * 
     * xml name: rateLimit
     *  为避免调用服务过于频繁，通过rateLimit指定每秒最多允许多少次请求。如果超过则会排队等待。
     */
    
    public java.lang.Double getRateLimit(){
      return _rateLimit;
    }

    
    public void setRateLimit(java.lang.Double value){
        checkAllowChange();
        
        this._rateLimit = value;
           
    }

    
    /**
     * 
     * xml name: request
     *  
     */
    
    public io.nop.ai.core.model.LlmRequestModel getRequest(){
      return _request;
    }

    
    public void setRequest(io.nop.ai.core.model.LlmRequestModel value){
        checkAllowChange();
        
        this._request = value;
           
    }

    
    /**
     * 
     * xml name: response
     *  
     */
    
    public io.nop.ai.core.model.LlmResponseModel getResponse(){
      return _response;
    }

    
    public void setResponse(io.nop.ai.core.model.LlmResponseModel value){
        checkAllowChange();
        
        this._response = value;
           
    }

    
    /**
     * 
     * xml name: supportModels
     *  大模型服务所支持的模型列表。通过defaultModel来指定缺省使用的模型
     */
    
    public java.util.Set<java.lang.String> getSupportModels(){
      return _supportModels;
    }

    
    public void setSupportModels(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._supportModels = value;
           
    }

    
    /**
     * 
     * xml name: supportToolCalls
     *  
     */
    
    public boolean isSupportToolCalls(){
      return _supportToolCalls;
    }

    
    public void setSupportToolCalls(boolean value){
        checkAllowChange();
        
        this._supportToolCalls = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._accounts = io.nop.api.core.util.FreezeHelper.deepFreeze(this._accounts);
            
           this._errorMappings = io.nop.api.core.util.FreezeHelper.deepFreeze(this._errorMappings);
            
           this._errorResponse = io.nop.api.core.util.FreezeHelper.deepFreeze(this._errorResponse);
            
           this._models = io.nop.api.core.util.FreezeHelper.deepFreeze(this._models);
            
           this._request = io.nop.api.core.util.FreezeHelper.deepFreeze(this._request);
            
           this._response = io.nop.api.core.util.FreezeHelper.deepFreeze(this._response);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("accounts",this.getAccounts());
        out.putNotNull("aliasMap",this.getAliasMap());
        out.putNotNull("apiKeyHeader",this.getApiKeyHeader());
        out.putNotNull("apiStyle",this.getApiStyle());
        out.putNotNull("baseUrl",this.getBaseUrl());
        out.putNotNull("buildHttpRequest",this.getBuildHttpRequest());
        out.putNotNull("chatUrl",this.getChatUrl());
        out.putNotNull("classifyError",this.getClassifyError());
        out.putNotNull("defaultModel",this.getDefaultModel());
        out.putNotNull("defaultRequestTimeout",this.getDefaultRequestTimeout());
        out.putNotNull("embedUrl",this.getEmbedUrl());
        out.putNotNull("errorMappings",this.getErrorMappings());
        out.putNotNull("errorResponse",this.getErrorResponse());
        out.putNotNull("generateUrl",this.getGenerateUrl());
        out.putNotNull("logMessage",this.isLogMessage());
        out.putNotNull("models",this.getModels());
        out.putNotNull("parseHttpResponse",this.getParseHttpResponse());
        out.putNotNull("rateLimit",this.getRateLimit());
        out.putNotNull("request",this.getRequest());
        out.putNotNull("response",this.getResponse());
        out.putNotNull("supportModels",this.getSupportModels());
        out.putNotNull("supportToolCalls",this.isSupportToolCalls());
    }

    public LlmModel cloneInstance(){
        LlmModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(LlmModel instance){
        super.copyTo(instance);
        
        instance.setAccounts(this.getAccounts());
        instance.setAliasMap(this.getAliasMap());
        instance.setApiKeyHeader(this.getApiKeyHeader());
        instance.setApiStyle(this.getApiStyle());
        instance.setBaseUrl(this.getBaseUrl());
        instance.setBuildHttpRequest(this.getBuildHttpRequest());
        instance.setChatUrl(this.getChatUrl());
        instance.setClassifyError(this.getClassifyError());
        instance.setDefaultModel(this.getDefaultModel());
        instance.setDefaultRequestTimeout(this.getDefaultRequestTimeout());
        instance.setEmbedUrl(this.getEmbedUrl());
        instance.setErrorMappings(this.getErrorMappings());
        instance.setErrorResponse(this.getErrorResponse());
        instance.setGenerateUrl(this.getGenerateUrl());
        instance.setLogMessage(this.isLogMessage());
        instance.setModels(this.getModels());
        instance.setParseHttpResponse(this.getParseHttpResponse());
        instance.setRateLimit(this.getRateLimit());
        instance.setRequest(this.getRequest());
        instance.setResponse(this.getResponse());
        instance.setSupportModels(this.getSupportModels());
        instance.setSupportToolCalls(this.isSupportToolCalls());
    }

    protected LlmModel newInstance(){
        return (LlmModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
