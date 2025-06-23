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

import nop.ai.dao.entity.NopAiPromptTemplate;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  提示词模板: nop_ai_prompt_template
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopAiPromptTemplate extends DynamicOrmEntity{
    
    /* 主键: id VARCHAR */
    public static final String PROP_NAME_id = "id";
    public static final int PROP_ID_id = 1;
    
    /* 模板名称: name VARCHAR */
    public static final String PROP_NAME_name = "name";
    public static final int PROP_ID_name = 2;
    
    /* 模板内容: content VARCHAR */
    public static final String PROP_NAME_content = "content";
    public static final int PROP_ID_content = 3;
    
    /* 分类: category VARCHAR */
    public static final String PROP_NAME_category = "category";
    public static final int PROP_ID_category = 4;
    
    /* 输入规范: inputs VARCHAR */
    public static final String PROP_NAME_inputs = "inputs";
    public static final int PROP_ID_inputs = 5;
    
    /* 输出规范: outputs VARCHAR */
    public static final String PROP_NAME_outputs = "outputs";
    public static final int PROP_ID_outputs = 6;
    

    private static int _PROP_ID_BOUND = 7;

    
    /* relation: 历史版本 */
    public static final String PROP_NAME_historyRecords = "historyRecords";
    
    /* relation: 测试请求 */
    public static final String PROP_NAME_requests = "requests";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_id);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_id};

    private static final String[] PROP_ID_TO_NAME = new String[7];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_id] = PROP_NAME_id;
          PROP_NAME_TO_ID.put(PROP_NAME_id, PROP_ID_id);
      
          PROP_ID_TO_NAME[PROP_ID_name] = PROP_NAME_name;
          PROP_NAME_TO_ID.put(PROP_NAME_name, PROP_ID_name);
      
          PROP_ID_TO_NAME[PROP_ID_content] = PROP_NAME_content;
          PROP_NAME_TO_ID.put(PROP_NAME_content, PROP_ID_content);
      
          PROP_ID_TO_NAME[PROP_ID_category] = PROP_NAME_category;
          PROP_NAME_TO_ID.put(PROP_NAME_category, PROP_ID_category);
      
          PROP_ID_TO_NAME[PROP_ID_inputs] = PROP_NAME_inputs;
          PROP_NAME_TO_ID.put(PROP_NAME_inputs, PROP_ID_inputs);
      
          PROP_ID_TO_NAME[PROP_ID_outputs] = PROP_NAME_outputs;
          PROP_NAME_TO_ID.put(PROP_NAME_outputs, PROP_ID_outputs);
      
    }

    
    /* 主键: id */
    private java.lang.String _id;
    
    /* 模板名称: name */
    private java.lang.String _name;
    
    /* 模板内容: content */
    private java.lang.String _content;
    
    /* 分类: category */
    private java.lang.String _category;
    
    /* 输入规范: inputs */
    private java.lang.String _inputs;
    
    /* 输出规范: outputs */
    private java.lang.String _outputs;
    

    public _NopAiPromptTemplate(){
        // for debug
    }

    protected NopAiPromptTemplate newInstance(){
        NopAiPromptTemplate entity = new NopAiPromptTemplate();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopAiPromptTemplate cloneInstance() {
        NopAiPromptTemplate entity = newInstance();
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
      return "nop.ai.dao.entity.NopAiPromptTemplate";
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
        
            case PROP_ID_name:
               return getName();
        
            case PROP_ID_content:
               return getContent();
        
            case PROP_ID_category:
               return getCategory();
        
            case PROP_ID_inputs:
               return getInputs();
        
            case PROP_ID_outputs:
               return getOutputs();
        
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
        
            case PROP_ID_name:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_name));
               }
               setName(typedValue);
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
        
            case PROP_ID_category:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_category));
               }
               setCategory(typedValue);
               break;
            }
        
            case PROP_ID_inputs:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_inputs));
               }
               setInputs(typedValue);
               break;
            }
        
            case PROP_ID_outputs:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_outputs));
               }
               setOutputs(typedValue);
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
        
            case PROP_ID_name:{
               onInitProp(propId);
               this._name = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_content:{
               onInitProp(propId);
               this._content = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_category:{
               onInitProp(propId);
               this._category = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_inputs:{
               onInitProp(propId);
               this._inputs = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_outputs:{
               onInitProp(propId);
               this._outputs = (java.lang.String)value;
               
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
     * 模板名称: name
     */
    public final java.lang.String getName(){
         onPropGet(PROP_ID_name);
         return _name;
    }

    /**
     * 模板名称: name
     */
    public final void setName(java.lang.String value){
        if(onPropSet(PROP_ID_name,value)){
            this._name = value;
            internalClearRefs(PROP_ID_name);
            
        }
    }
    
    /**
     * 模板内容: content
     */
    public final java.lang.String getContent(){
         onPropGet(PROP_ID_content);
         return _content;
    }

    /**
     * 模板内容: content
     */
    public final void setContent(java.lang.String value){
        if(onPropSet(PROP_ID_content,value)){
            this._content = value;
            internalClearRefs(PROP_ID_content);
            
        }
    }
    
    /**
     * 分类: category
     */
    public final java.lang.String getCategory(){
         onPropGet(PROP_ID_category);
         return _category;
    }

    /**
     * 分类: category
     */
    public final void setCategory(java.lang.String value){
        if(onPropSet(PROP_ID_category,value)){
            this._category = value;
            internalClearRefs(PROP_ID_category);
            
        }
    }
    
    /**
     * 输入规范: inputs
     */
    public final java.lang.String getInputs(){
         onPropGet(PROP_ID_inputs);
         return _inputs;
    }

    /**
     * 输入规范: inputs
     */
    public final void setInputs(java.lang.String value){
        if(onPropSet(PROP_ID_inputs,value)){
            this._inputs = value;
            internalClearRefs(PROP_ID_inputs);
            
        }
    }
    
    /**
     * 输出规范: outputs
     */
    public final java.lang.String getOutputs(){
         onPropGet(PROP_ID_outputs);
         return _outputs;
    }

    /**
     * 输出规范: outputs
     */
    public final void setOutputs(java.lang.String value){
        if(onPropSet(PROP_ID_outputs,value)){
            this._outputs = value;
            internalClearRefs(PROP_ID_outputs);
            
        }
    }
    
    private final OrmEntitySet<nop.ai.dao.entity.NopAiPromptTemplateHistory> _historyRecords = new OrmEntitySet<>(this, PROP_NAME_historyRecords,
        nop.ai.dao.entity.NopAiPromptTemplateHistory.PROP_NAME_template, null,nop.ai.dao.entity.NopAiPromptTemplateHistory.class);

    /**
     * 历史版本。 refPropName: template, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<nop.ai.dao.entity.NopAiPromptTemplateHistory> getHistoryRecords(){
       return _historyRecords;
    }
       
    private final OrmEntitySet<nop.ai.dao.entity.NopAiChatRequest> _requests = new OrmEntitySet<>(this, PROP_NAME_requests,
        nop.ai.dao.entity.NopAiChatRequest.PROP_NAME_template, null,nop.ai.dao.entity.NopAiChatRequest.class);

    /**
     * 测试请求。 refPropName: template, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<nop.ai.dao.entity.NopAiChatRequest> getRequests(){
       return _requests;
    }
       
}
// resume CPD analysis - CPD-ON
