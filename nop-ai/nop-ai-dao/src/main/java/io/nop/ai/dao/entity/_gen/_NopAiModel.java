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

import io.nop.ai.dao.entity.NopAiModel;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  AI模型: nop_ai_model
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopAiModel extends DynamicOrmEntity{
    
    /* 主键: id VARCHAR */
    public static final String PROP_NAME_id = "id";
    public static final int PROP_ID_id = 1;
    
    /* 供应商: provider VARCHAR */
    public static final String PROP_NAME_provider = "provider";
    public static final int PROP_ID_provider = 2;
    
    /* 模型名称: model_name VARCHAR */
    public static final String PROP_NAME_modelName = "modelName";
    public static final int PROP_ID_modelName = 3;
    
    /* API地址: base_url VARCHAR */
    public static final String PROP_NAME_baseUrl = "baseUrl";
    public static final int PROP_ID_baseUrl = 4;
    
    /* API密钥: api_key VARCHAR */
    public static final String PROP_NAME_apiKey = "apiKey";
    public static final int PROP_ID_apiKey = 5;
    
    /* 数据版本: version INTEGER */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 6;
    
    /* 创建人: created_by VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 7;
    
    /* 创建时间: create_time TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 8;
    
    /* 修改人: updated_by VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 9;
    
    /* 修改时间: update_time TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 10;
    
    /* 输入单价: input_price_per_1m DECIMAL */
    public static final String PROP_NAME_inputPricePer1m = "inputPricePer1m";
    public static final int PROP_ID_inputPricePer1m = 11;
    
    /* 输出单价: output_price_per_1m DECIMAL */
    public static final String PROP_NAME_outputPricePer1m = "outputPricePer1m";
    public static final int PROP_ID_outputPricePer1m = 12;
    
    /* 推理单价: reasoning_price_per_1m DECIMAL */
    public static final String PROP_NAME_reasoningPricePer1m = "reasoningPricePer1m";
    public static final int PROP_ID_reasoningPricePer1m = 13;
    
    /* 缓存读单价: cache_read_price_per_1m DECIMAL */
    public static final String PROP_NAME_cacheReadPricePer1m = "cacheReadPricePer1m";
    public static final int PROP_ID_cacheReadPricePer1m = 14;
    
    /* 缓存写单价: cache_write_price_per_1m DECIMAL */
    public static final String PROP_NAME_cacheWritePricePer1m = "cacheWritePricePer1m";
    public static final int PROP_ID_cacheWritePricePer1m = 15;
    
    /* 币种: currency VARCHAR */
    public static final String PROP_NAME_currency = "currency";
    public static final int PROP_ID_currency = 16;
    

    private static int _PROP_ID_BOUND = 17;

    
    /* relation: 调用记录 */
    public static final String PROP_NAME_responses = "responses";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_id);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_id};

    private static final String[] PROP_ID_TO_NAME = new String[17];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_id] = PROP_NAME_id;
          PROP_NAME_TO_ID.put(PROP_NAME_id, PROP_ID_id);
      
          PROP_ID_TO_NAME[PROP_ID_provider] = PROP_NAME_provider;
          PROP_NAME_TO_ID.put(PROP_NAME_provider, PROP_ID_provider);
      
          PROP_ID_TO_NAME[PROP_ID_modelName] = PROP_NAME_modelName;
          PROP_NAME_TO_ID.put(PROP_NAME_modelName, PROP_ID_modelName);
      
          PROP_ID_TO_NAME[PROP_ID_baseUrl] = PROP_NAME_baseUrl;
          PROP_NAME_TO_ID.put(PROP_NAME_baseUrl, PROP_ID_baseUrl);
      
          PROP_ID_TO_NAME[PROP_ID_apiKey] = PROP_NAME_apiKey;
          PROP_NAME_TO_ID.put(PROP_NAME_apiKey, PROP_ID_apiKey);
      
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
      
          PROP_ID_TO_NAME[PROP_ID_inputPricePer1m] = PROP_NAME_inputPricePer1m;
          PROP_NAME_TO_ID.put(PROP_NAME_inputPricePer1m, PROP_ID_inputPricePer1m);
      
          PROP_ID_TO_NAME[PROP_ID_outputPricePer1m] = PROP_NAME_outputPricePer1m;
          PROP_NAME_TO_ID.put(PROP_NAME_outputPricePer1m, PROP_ID_outputPricePer1m);
      
          PROP_ID_TO_NAME[PROP_ID_reasoningPricePer1m] = PROP_NAME_reasoningPricePer1m;
          PROP_NAME_TO_ID.put(PROP_NAME_reasoningPricePer1m, PROP_ID_reasoningPricePer1m);
      
          PROP_ID_TO_NAME[PROP_ID_cacheReadPricePer1m] = PROP_NAME_cacheReadPricePer1m;
          PROP_NAME_TO_ID.put(PROP_NAME_cacheReadPricePer1m, PROP_ID_cacheReadPricePer1m);
      
          PROP_ID_TO_NAME[PROP_ID_cacheWritePricePer1m] = PROP_NAME_cacheWritePricePer1m;
          PROP_NAME_TO_ID.put(PROP_NAME_cacheWritePricePer1m, PROP_ID_cacheWritePricePer1m);
      
          PROP_ID_TO_NAME[PROP_ID_currency] = PROP_NAME_currency;
          PROP_NAME_TO_ID.put(PROP_NAME_currency, PROP_ID_currency);
      
    }

    
    /* 主键: id */
    private java.lang.String _id;
    
    /* 供应商: provider */
    private java.lang.String _provider;
    
    /* 模型名称: model_name */
    private java.lang.String _modelName;
    
    /* API地址: base_url */
    private java.lang.String _baseUrl;
    
    /* API密钥: api_key */
    private java.lang.String _apiKey;
    
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
    
    /* 输入单价: input_price_per_1m */
    private java.math.BigDecimal _inputPricePer1m;
    
    /* 输出单价: output_price_per_1m */
    private java.math.BigDecimal _outputPricePer1m;
    
    /* 推理单价: reasoning_price_per_1m */
    private java.math.BigDecimal _reasoningPricePer1m;
    
    /* 缓存读单价: cache_read_price_per_1m */
    private java.math.BigDecimal _cacheReadPricePer1m;
    
    /* 缓存写单价: cache_write_price_per_1m */
    private java.math.BigDecimal _cacheWritePricePer1m;
    
    /* 币种: currency */
    private java.lang.String _currency;
    

    public _NopAiModel(){
        // for debug
    }

    protected NopAiModel newInstance(){
        NopAiModel entity = new NopAiModel();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopAiModel cloneInstance() {
        NopAiModel entity = newInstance();
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
      return "io.nop.ai.dao.entity.NopAiModel";
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
        
            case PROP_ID_provider:
               return getProvider();
        
            case PROP_ID_modelName:
               return getModelName();
        
            case PROP_ID_baseUrl:
               return getBaseUrl();
        
            case PROP_ID_apiKey:
               return getApiKey();
        
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
        
            case PROP_ID_inputPricePer1m:
               return getInputPricePer1m();
        
            case PROP_ID_outputPricePer1m:
               return getOutputPricePer1m();
        
            case PROP_ID_reasoningPricePer1m:
               return getReasoningPricePer1m();
        
            case PROP_ID_cacheReadPricePer1m:
               return getCacheReadPricePer1m();
        
            case PROP_ID_cacheWritePricePer1m:
               return getCacheWritePricePer1m();
        
            case PROP_ID_currency:
               return getCurrency();
        
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
        
            case PROP_ID_provider:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_provider));
               }
               setProvider(typedValue);
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
        
            case PROP_ID_baseUrl:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_baseUrl));
               }
               setBaseUrl(typedValue);
               break;
            }
        
            case PROP_ID_apiKey:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_apiKey));
               }
               setApiKey(typedValue);
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
        
            case PROP_ID_inputPricePer1m:{
               java.math.BigDecimal typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toBigDecimal(value,
                       err-> newTypeConversionError(PROP_NAME_inputPricePer1m));
               }
               setInputPricePer1m(typedValue);
               break;
            }
        
            case PROP_ID_outputPricePer1m:{
               java.math.BigDecimal typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toBigDecimal(value,
                       err-> newTypeConversionError(PROP_NAME_outputPricePer1m));
               }
               setOutputPricePer1m(typedValue);
               break;
            }
        
            case PROP_ID_reasoningPricePer1m:{
               java.math.BigDecimal typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toBigDecimal(value,
                       err-> newTypeConversionError(PROP_NAME_reasoningPricePer1m));
               }
               setReasoningPricePer1m(typedValue);
               break;
            }
        
            case PROP_ID_cacheReadPricePer1m:{
               java.math.BigDecimal typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toBigDecimal(value,
                       err-> newTypeConversionError(PROP_NAME_cacheReadPricePer1m));
               }
               setCacheReadPricePer1m(typedValue);
               break;
            }
        
            case PROP_ID_cacheWritePricePer1m:{
               java.math.BigDecimal typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toBigDecimal(value,
                       err-> newTypeConversionError(PROP_NAME_cacheWritePricePer1m));
               }
               setCacheWritePricePer1m(typedValue);
               break;
            }
        
            case PROP_ID_currency:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_currency));
               }
               setCurrency(typedValue);
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
        
            case PROP_ID_provider:{
               onInitProp(propId);
               this._provider = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_modelName:{
               onInitProp(propId);
               this._modelName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_baseUrl:{
               onInitProp(propId);
               this._baseUrl = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_apiKey:{
               onInitProp(propId);
               this._apiKey = (java.lang.String)value;
               
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
        
            case PROP_ID_inputPricePer1m:{
               onInitProp(propId);
               this._inputPricePer1m = (java.math.BigDecimal)value;
               
               break;
            }
        
            case PROP_ID_outputPricePer1m:{
               onInitProp(propId);
               this._outputPricePer1m = (java.math.BigDecimal)value;
               
               break;
            }
        
            case PROP_ID_reasoningPricePer1m:{
               onInitProp(propId);
               this._reasoningPricePer1m = (java.math.BigDecimal)value;
               
               break;
            }
        
            case PROP_ID_cacheReadPricePer1m:{
               onInitProp(propId);
               this._cacheReadPricePer1m = (java.math.BigDecimal)value;
               
               break;
            }
        
            case PROP_ID_cacheWritePricePer1m:{
               onInitProp(propId);
               this._cacheWritePricePer1m = (java.math.BigDecimal)value;
               
               break;
            }
        
            case PROP_ID_currency:{
               onInitProp(propId);
               this._currency = (java.lang.String)value;
               
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
     * 供应商: provider
     */
    public final java.lang.String getProvider(){
         onPropGet(PROP_ID_provider);
         return _provider;
    }

    /**
     * 供应商: provider
     */
    public final void setProvider(java.lang.String value){
        if(onPropSet(PROP_ID_provider,value)){
            this._provider = value;
            internalClearRefs(PROP_ID_provider);
            
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
     * API地址: base_url
     */
    public final java.lang.String getBaseUrl(){
         onPropGet(PROP_ID_baseUrl);
         return _baseUrl;
    }

    /**
     * API地址: base_url
     */
    public final void setBaseUrl(java.lang.String value){
        if(onPropSet(PROP_ID_baseUrl,value)){
            this._baseUrl = value;
            internalClearRefs(PROP_ID_baseUrl);
            
        }
    }
    
    /**
     * API密钥: api_key
     */
    public final java.lang.String getApiKey(){
         onPropGet(PROP_ID_apiKey);
         return _apiKey;
    }

    /**
     * API密钥: api_key
     */
    public final void setApiKey(java.lang.String value){
        if(onPropSet(PROP_ID_apiKey,value)){
            this._apiKey = value;
            internalClearRefs(PROP_ID_apiKey);
            
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
     * 输入单价: input_price_per_1m
     */
    public final java.math.BigDecimal getInputPricePer1m(){
         onPropGet(PROP_ID_inputPricePer1m);
         return _inputPricePer1m;
    }

    /**
     * 输入单价: input_price_per_1m
     */
    public final void setInputPricePer1m(java.math.BigDecimal value){
        if(onPropSet(PROP_ID_inputPricePer1m,value)){
            this._inputPricePer1m = value;
            internalClearRefs(PROP_ID_inputPricePer1m);
            
        }
    }
    
    /**
     * 输出单价: output_price_per_1m
     */
    public final java.math.BigDecimal getOutputPricePer1m(){
         onPropGet(PROP_ID_outputPricePer1m);
         return _outputPricePer1m;
    }

    /**
     * 输出单价: output_price_per_1m
     */
    public final void setOutputPricePer1m(java.math.BigDecimal value){
        if(onPropSet(PROP_ID_outputPricePer1m,value)){
            this._outputPricePer1m = value;
            internalClearRefs(PROP_ID_outputPricePer1m);
            
        }
    }
    
    /**
     * 推理单价: reasoning_price_per_1m
     */
    public final java.math.BigDecimal getReasoningPricePer1m(){
         onPropGet(PROP_ID_reasoningPricePer1m);
         return _reasoningPricePer1m;
    }

    /**
     * 推理单价: reasoning_price_per_1m
     */
    public final void setReasoningPricePer1m(java.math.BigDecimal value){
        if(onPropSet(PROP_ID_reasoningPricePer1m,value)){
            this._reasoningPricePer1m = value;
            internalClearRefs(PROP_ID_reasoningPricePer1m);
            
        }
    }
    
    /**
     * 缓存读单价: cache_read_price_per_1m
     */
    public final java.math.BigDecimal getCacheReadPricePer1m(){
         onPropGet(PROP_ID_cacheReadPricePer1m);
         return _cacheReadPricePer1m;
    }

    /**
     * 缓存读单价: cache_read_price_per_1m
     */
    public final void setCacheReadPricePer1m(java.math.BigDecimal value){
        if(onPropSet(PROP_ID_cacheReadPricePer1m,value)){
            this._cacheReadPricePer1m = value;
            internalClearRefs(PROP_ID_cacheReadPricePer1m);
            
        }
    }
    
    /**
     * 缓存写单价: cache_write_price_per_1m
     */
    public final java.math.BigDecimal getCacheWritePricePer1m(){
         onPropGet(PROP_ID_cacheWritePricePer1m);
         return _cacheWritePricePer1m;
    }

    /**
     * 缓存写单价: cache_write_price_per_1m
     */
    public final void setCacheWritePricePer1m(java.math.BigDecimal value){
        if(onPropSet(PROP_ID_cacheWritePricePer1m,value)){
            this._cacheWritePricePer1m = value;
            internalClearRefs(PROP_ID_cacheWritePricePer1m);
            
        }
    }
    
    /**
     * 币种: currency
     */
    public final java.lang.String getCurrency(){
         onPropGet(PROP_ID_currency);
         return _currency;
    }

    /**
     * 币种: currency
     */
    public final void setCurrency(java.lang.String value){
        if(onPropSet(PROP_ID_currency,value)){
            this._currency = value;
            internalClearRefs(PROP_ID_currency);
            
        }
    }
    
    private final OrmEntitySet<io.nop.ai.dao.entity.NopAiChatResponse> _responses = new OrmEntitySet<>(this, PROP_NAME_responses,
        io.nop.ai.dao.entity.NopAiChatResponse.PROP_NAME_model, null,io.nop.ai.dao.entity.NopAiChatResponse.class);

    /**
     * 调用记录。 refPropName: model, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<io.nop.ai.dao.entity.NopAiChatResponse> getResponses(){
       return _responses;
    }
       
}
// resume CPD analysis - CPD-ON
