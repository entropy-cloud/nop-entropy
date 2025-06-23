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

import nop.ai.dao.entity.NopAiProjectRule;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  项目规则: nop_ai_project_rule
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopAiProjectRule extends DynamicOrmEntity{
    
    /* 主键: id VARCHAR */
    public static final String PROP_NAME_id = "id";
    public static final int PROP_ID_id = 1;
    
    /* 项目ID: project_id VARCHAR */
    public static final String PROP_NAME_projectId = "projectId";
    public static final int PROP_ID_projectId = 2;
    
    /* 知识库ID: knowledge_id VARCHAR */
    public static final String PROP_NAME_knowledgeId = "knowledgeId";
    public static final int PROP_ID_knowledgeId = 3;
    
    /* 规则名称: rule_name VARCHAR */
    public static final String PROP_NAME_ruleName = "ruleName";
    public static final int PROP_ID_ruleName = 4;
    
    /* 规则内容: rule_content VARCHAR */
    public static final String PROP_NAME_ruleContent = "ruleContent";
    public static final int PROP_ID_ruleContent = 5;
    
    /* 规则类型: rule_type VARCHAR */
    public static final String PROP_NAME_ruleType = "ruleType";
    public static final int PROP_ID_ruleType = 6;
    
    /* 是否启用: is_active BOOLEAN */
    public static final String PROP_NAME_isActive = "isActive";
    public static final int PROP_ID_isActive = 7;
    

    private static int _PROP_ID_BOUND = 8;

    
    /* relation:  */
    public static final String PROP_NAME_project = "project";
    
    /* relation:  */
    public static final String PROP_NAME_knowledge = "knowledge";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_id);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_id};

    private static final String[] PROP_ID_TO_NAME = new String[8];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_id] = PROP_NAME_id;
          PROP_NAME_TO_ID.put(PROP_NAME_id, PROP_ID_id);
      
          PROP_ID_TO_NAME[PROP_ID_projectId] = PROP_NAME_projectId;
          PROP_NAME_TO_ID.put(PROP_NAME_projectId, PROP_ID_projectId);
      
          PROP_ID_TO_NAME[PROP_ID_knowledgeId] = PROP_NAME_knowledgeId;
          PROP_NAME_TO_ID.put(PROP_NAME_knowledgeId, PROP_ID_knowledgeId);
      
          PROP_ID_TO_NAME[PROP_ID_ruleName] = PROP_NAME_ruleName;
          PROP_NAME_TO_ID.put(PROP_NAME_ruleName, PROP_ID_ruleName);
      
          PROP_ID_TO_NAME[PROP_ID_ruleContent] = PROP_NAME_ruleContent;
          PROP_NAME_TO_ID.put(PROP_NAME_ruleContent, PROP_ID_ruleContent);
      
          PROP_ID_TO_NAME[PROP_ID_ruleType] = PROP_NAME_ruleType;
          PROP_NAME_TO_ID.put(PROP_NAME_ruleType, PROP_ID_ruleType);
      
          PROP_ID_TO_NAME[PROP_ID_isActive] = PROP_NAME_isActive;
          PROP_NAME_TO_ID.put(PROP_NAME_isActive, PROP_ID_isActive);
      
    }

    
    /* 主键: id */
    private java.lang.String _id;
    
    /* 项目ID: project_id */
    private java.lang.String _projectId;
    
    /* 知识库ID: knowledge_id */
    private java.lang.String _knowledgeId;
    
    /* 规则名称: rule_name */
    private java.lang.String _ruleName;
    
    /* 规则内容: rule_content */
    private java.lang.String _ruleContent;
    
    /* 规则类型: rule_type */
    private java.lang.String _ruleType;
    
    /* 是否启用: is_active */
    private java.lang.Boolean _isActive;
    

    public _NopAiProjectRule(){
        // for debug
    }

    protected NopAiProjectRule newInstance(){
        NopAiProjectRule entity = new NopAiProjectRule();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopAiProjectRule cloneInstance() {
        NopAiProjectRule entity = newInstance();
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
      return "nop.ai.dao.entity.NopAiProjectRule";
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
        
            case PROP_ID_knowledgeId:
               return getKnowledgeId();
        
            case PROP_ID_ruleName:
               return getRuleName();
        
            case PROP_ID_ruleContent:
               return getRuleContent();
        
            case PROP_ID_ruleType:
               return getRuleType();
        
            case PROP_ID_isActive:
               return getIsActive();
        
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
        
            case PROP_ID_knowledgeId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_knowledgeId));
               }
               setKnowledgeId(typedValue);
               break;
            }
        
            case PROP_ID_ruleName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_ruleName));
               }
               setRuleName(typedValue);
               break;
            }
        
            case PROP_ID_ruleContent:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_ruleContent));
               }
               setRuleContent(typedValue);
               break;
            }
        
            case PROP_ID_ruleType:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_ruleType));
               }
               setRuleType(typedValue);
               break;
            }
        
            case PROP_ID_isActive:{
               java.lang.Boolean typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toBoolean(value,
                       err-> newTypeConversionError(PROP_NAME_isActive));
               }
               setIsActive(typedValue);
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
        
            case PROP_ID_knowledgeId:{
               onInitProp(propId);
               this._knowledgeId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_ruleName:{
               onInitProp(propId);
               this._ruleName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_ruleContent:{
               onInitProp(propId);
               this._ruleContent = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_ruleType:{
               onInitProp(propId);
               this._ruleType = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_isActive:{
               onInitProp(propId);
               this._isActive = (java.lang.Boolean)value;
               
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
     * 知识库ID: knowledge_id
     */
    public final java.lang.String getKnowledgeId(){
         onPropGet(PROP_ID_knowledgeId);
         return _knowledgeId;
    }

    /**
     * 知识库ID: knowledge_id
     */
    public final void setKnowledgeId(java.lang.String value){
        if(onPropSet(PROP_ID_knowledgeId,value)){
            this._knowledgeId = value;
            internalClearRefs(PROP_ID_knowledgeId);
            
        }
    }
    
    /**
     * 规则名称: rule_name
     */
    public final java.lang.String getRuleName(){
         onPropGet(PROP_ID_ruleName);
         return _ruleName;
    }

    /**
     * 规则名称: rule_name
     */
    public final void setRuleName(java.lang.String value){
        if(onPropSet(PROP_ID_ruleName,value)){
            this._ruleName = value;
            internalClearRefs(PROP_ID_ruleName);
            
        }
    }
    
    /**
     * 规则内容: rule_content
     */
    public final java.lang.String getRuleContent(){
         onPropGet(PROP_ID_ruleContent);
         return _ruleContent;
    }

    /**
     * 规则内容: rule_content
     */
    public final void setRuleContent(java.lang.String value){
        if(onPropSet(PROP_ID_ruleContent,value)){
            this._ruleContent = value;
            internalClearRefs(PROP_ID_ruleContent);
            
        }
    }
    
    /**
     * 规则类型: rule_type
     */
    public final java.lang.String getRuleType(){
         onPropGet(PROP_ID_ruleType);
         return _ruleType;
    }

    /**
     * 规则类型: rule_type
     */
    public final void setRuleType(java.lang.String value){
        if(onPropSet(PROP_ID_ruleType,value)){
            this._ruleType = value;
            internalClearRefs(PROP_ID_ruleType);
            
        }
    }
    
    /**
     * 是否启用: is_active
     */
    public final java.lang.Boolean getIsActive(){
         onPropGet(PROP_ID_isActive);
         return _isActive;
    }

    /**
     * 是否启用: is_active
     */
    public final void setIsActive(java.lang.Boolean value){
        if(onPropSet(PROP_ID_isActive,value)){
            this._isActive = value;
            internalClearRefs(PROP_ID_isActive);
            
        }
    }
    
    /**
     * 
     */
    public final nop.ai.dao.entity.NopAiProject getProject(){
       return (nop.ai.dao.entity.NopAiProject)internalGetRefEntity(PROP_NAME_project);
    }

    public final void setProject(nop.ai.dao.entity.NopAiProject refEntity){
   
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
    public final nop.ai.dao.entity.NopAiKnowledge getKnowledge(){
       return (nop.ai.dao.entity.NopAiKnowledge)internalGetRefEntity(PROP_NAME_knowledge);
    }

    public final void setKnowledge(nop.ai.dao.entity.NopAiKnowledge refEntity){
   
           if(refEntity == null){
           
                   this.setKnowledgeId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_knowledge, refEntity,()->{
           
                           this.setKnowledgeId(refEntity.getId());
                       
           });
           }
       
    }
       
}
// resume CPD analysis - CPD-ON
