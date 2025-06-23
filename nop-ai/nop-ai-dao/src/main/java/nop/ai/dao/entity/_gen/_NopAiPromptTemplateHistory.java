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

import nop.ai.dao.entity.NopAiPromptTemplateHistory;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  模板历史: nop_ai_prompt_template_history
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopAiPromptTemplateHistory extends DynamicOrmEntity{
    
    /* 主键: id VARCHAR */
    public static final String PROP_NAME_id = "id";
    public static final int PROP_ID_id = 1;
    
    /* 模板ID: template_id VARCHAR */
    public static final String PROP_NAME_templateId = "templateId";
    public static final int PROP_ID_templateId = 2;
    
    /* 版本号: version VARCHAR */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 3;
    
    /* 模板内容: content VARCHAR */
    public static final String PROP_NAME_content = "content";
    public static final int PROP_ID_content = 4;
    

    private static int _PROP_ID_BOUND = 5;

    
    /* relation:  */
    public static final String PROP_NAME_template = "template";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_id);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_id};

    private static final String[] PROP_ID_TO_NAME = new String[5];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_id] = PROP_NAME_id;
          PROP_NAME_TO_ID.put(PROP_NAME_id, PROP_ID_id);
      
          PROP_ID_TO_NAME[PROP_ID_templateId] = PROP_NAME_templateId;
          PROP_NAME_TO_ID.put(PROP_NAME_templateId, PROP_ID_templateId);
      
          PROP_ID_TO_NAME[PROP_ID_version] = PROP_NAME_version;
          PROP_NAME_TO_ID.put(PROP_NAME_version, PROP_ID_version);
      
          PROP_ID_TO_NAME[PROP_ID_content] = PROP_NAME_content;
          PROP_NAME_TO_ID.put(PROP_NAME_content, PROP_ID_content);
      
    }

    
    /* 主键: id */
    private java.lang.String _id;
    
    /* 模板ID: template_id */
    private java.lang.String _templateId;
    
    /* 版本号: version */
    private java.lang.String _version;
    
    /* 模板内容: content */
    private java.lang.String _content;
    

    public _NopAiPromptTemplateHistory(){
        // for debug
    }

    protected NopAiPromptTemplateHistory newInstance(){
        NopAiPromptTemplateHistory entity = new NopAiPromptTemplateHistory();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopAiPromptTemplateHistory cloneInstance() {
        NopAiPromptTemplateHistory entity = newInstance();
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
      return "nop.ai.dao.entity.NopAiPromptTemplateHistory";
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
        
            case PROP_ID_version:
               return getVersion();
        
            case PROP_ID_content:
               return getContent();
        
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
        
            case PROP_ID_version:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_version));
               }
               setVersion(typedValue);
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
        
            case PROP_ID_version:{
               onInitProp(propId);
               this._version = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_content:{
               onInitProp(propId);
               this._content = (java.lang.String)value;
               
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
     * 版本号: version
     */
    public final java.lang.String getVersion(){
         onPropGet(PROP_ID_version);
         return _version;
    }

    /**
     * 版本号: version
     */
    public final void setVersion(java.lang.String value){
        if(onPropSet(PROP_ID_version,value)){
            this._version = value;
            internalClearRefs(PROP_ID_version);
            
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
       
}
// resume CPD analysis - CPD-ON
