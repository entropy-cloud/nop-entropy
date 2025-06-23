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

import nop.ai.dao.entity.NopAiModel;

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
    

    private static int _PROP_ID_BOUND = 6;

    
    /* relation: 调用记录 */
    public static final String PROP_NAME_responses = "responses";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_id);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_id};

    private static final String[] PROP_ID_TO_NAME = new String[6];
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
      return "nop.ai.dao.entity.NopAiModel";
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
    
    private final OrmEntitySet<nop.ai.dao.entity.NopAiChatResponse> _responses = new OrmEntitySet<>(this, PROP_NAME_responses,
        nop.ai.dao.entity.NopAiChatResponse.PROP_NAME_model, null,nop.ai.dao.entity.NopAiChatResponse.class);

    /**
     * 调用记录。 refPropName: model, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<nop.ai.dao.entity.NopAiChatResponse> getResponses(){
       return _responses;
    }
       
}
// resume CPD analysis - CPD-ON
