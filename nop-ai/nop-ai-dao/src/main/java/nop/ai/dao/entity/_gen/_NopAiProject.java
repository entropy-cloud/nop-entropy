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

import nop.ai.dao.entity.NopAiProject;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  AI项目: nop_ai_project
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopAiProject extends DynamicOrmEntity{
    
    /* 主键: id VARCHAR */
    public static final String PROP_NAME_id = "id";
    public static final int PROP_ID_id = 1;
    
    /* 项目语言: language VARCHAR */
    public static final String PROP_NAME_language = "language";
    public static final int PROP_ID_language = 2;
    
    /* 项目名称: name VARCHAR */
    public static final String PROP_NAME_name = "name";
    public static final int PROP_ID_name = 3;
    
    /* 模板项目ID: prototype_id VARCHAR */
    public static final String PROP_NAME_prototypeId = "prototypeId";
    public static final int PROP_ID_prototypeId = 4;
    
    /* 项目目录: project_dir VARCHAR */
    public static final String PROP_NAME_projectDir = "projectDir";
    public static final int PROP_ID_projectDir = 5;
    

    private static int _PROP_ID_BOUND = 6;

    
    /* relation:  */
    public static final String PROP_NAME_prototype = "prototype";
    
    /* relation: 项目规则 */
    public static final String PROP_NAME_projectRules = "projectRules";
    
    /* relation: 配置项 */
    public static final String PROP_NAME_configs = "configs";
    
    /* relation: 需求列表 */
    public static final String PROP_NAME_requirements = "requirements";
    
    /* relation: 生成文件 */
    public static final String PROP_NAME_generatedFiles = "generatedFiles";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_id);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_id};

    private static final String[] PROP_ID_TO_NAME = new String[6];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_id] = PROP_NAME_id;
          PROP_NAME_TO_ID.put(PROP_NAME_id, PROP_ID_id);
      
          PROP_ID_TO_NAME[PROP_ID_language] = PROP_NAME_language;
          PROP_NAME_TO_ID.put(PROP_NAME_language, PROP_ID_language);
      
          PROP_ID_TO_NAME[PROP_ID_name] = PROP_NAME_name;
          PROP_NAME_TO_ID.put(PROP_NAME_name, PROP_ID_name);
      
          PROP_ID_TO_NAME[PROP_ID_prototypeId] = PROP_NAME_prototypeId;
          PROP_NAME_TO_ID.put(PROP_NAME_prototypeId, PROP_ID_prototypeId);
      
          PROP_ID_TO_NAME[PROP_ID_projectDir] = PROP_NAME_projectDir;
          PROP_NAME_TO_ID.put(PROP_NAME_projectDir, PROP_ID_projectDir);
      
    }

    
    /* 主键: id */
    private java.lang.String _id;
    
    /* 项目语言: language */
    private java.lang.String _language;
    
    /* 项目名称: name */
    private java.lang.String _name;
    
    /* 模板项目ID: prototype_id */
    private java.lang.String _prototypeId;
    
    /* 项目目录: project_dir */
    private java.lang.String _projectDir;
    

    public _NopAiProject(){
        // for debug
    }

    protected NopAiProject newInstance(){
        NopAiProject entity = new NopAiProject();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopAiProject cloneInstance() {
        NopAiProject entity = newInstance();
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
      return "nop.ai.dao.entity.NopAiProject";
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
        
            case PROP_ID_language:
               return getLanguage();
        
            case PROP_ID_name:
               return getName();
        
            case PROP_ID_prototypeId:
               return getPrototypeId();
        
            case PROP_ID_projectDir:
               return getProjectDir();
        
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
        
            case PROP_ID_language:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_language));
               }
               setLanguage(typedValue);
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
        
            case PROP_ID_prototypeId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_prototypeId));
               }
               setPrototypeId(typedValue);
               break;
            }
        
            case PROP_ID_projectDir:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_projectDir));
               }
               setProjectDir(typedValue);
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
        
            case PROP_ID_language:{
               onInitProp(propId);
               this._language = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_name:{
               onInitProp(propId);
               this._name = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_prototypeId:{
               onInitProp(propId);
               this._prototypeId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_projectDir:{
               onInitProp(propId);
               this._projectDir = (java.lang.String)value;
               
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
     * 项目语言: language
     */
    public final java.lang.String getLanguage(){
         onPropGet(PROP_ID_language);
         return _language;
    }

    /**
     * 项目语言: language
     */
    public final void setLanguage(java.lang.String value){
        if(onPropSet(PROP_ID_language,value)){
            this._language = value;
            internalClearRefs(PROP_ID_language);
            
        }
    }
    
    /**
     * 项目名称: name
     */
    public final java.lang.String getName(){
         onPropGet(PROP_ID_name);
         return _name;
    }

    /**
     * 项目名称: name
     */
    public final void setName(java.lang.String value){
        if(onPropSet(PROP_ID_name,value)){
            this._name = value;
            internalClearRefs(PROP_ID_name);
            
        }
    }
    
    /**
     * 模板项目ID: prototype_id
     */
    public final java.lang.String getPrototypeId(){
         onPropGet(PROP_ID_prototypeId);
         return _prototypeId;
    }

    /**
     * 模板项目ID: prototype_id
     */
    public final void setPrototypeId(java.lang.String value){
        if(onPropSet(PROP_ID_prototypeId,value)){
            this._prototypeId = value;
            internalClearRefs(PROP_ID_prototypeId);
            
        }
    }
    
    /**
     * 项目目录: project_dir
     */
    public final java.lang.String getProjectDir(){
         onPropGet(PROP_ID_projectDir);
         return _projectDir;
    }

    /**
     * 项目目录: project_dir
     */
    public final void setProjectDir(java.lang.String value){
        if(onPropSet(PROP_ID_projectDir,value)){
            this._projectDir = value;
            internalClearRefs(PROP_ID_projectDir);
            
        }
    }
    
    /**
     * 
     */
    public final nop.ai.dao.entity.NopAiProject getPrototype(){
       return (nop.ai.dao.entity.NopAiProject)internalGetRefEntity(PROP_NAME_prototype);
    }

    public final void setPrototype(nop.ai.dao.entity.NopAiProject refEntity){
   
           if(refEntity == null){
           
                   this.setPrototypeId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_prototype, refEntity,()->{
           
                           this.setPrototypeId(refEntity.getId());
                       
           });
           }
       
    }
       
    private final OrmEntitySet<nop.ai.dao.entity.NopAiProjectRule> _projectRules = new OrmEntitySet<>(this, PROP_NAME_projectRules,
        nop.ai.dao.entity.NopAiProjectRule.PROP_NAME_project, null,nop.ai.dao.entity.NopAiProjectRule.class);

    /**
     * 项目规则。 refPropName: project, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<nop.ai.dao.entity.NopAiProjectRule> getProjectRules(){
       return _projectRules;
    }
       
    private final OrmEntitySet<nop.ai.dao.entity.NopAiProjectConfig> _configs = new OrmEntitySet<>(this, PROP_NAME_configs,
        nop.ai.dao.entity.NopAiProjectConfig.PROP_NAME_project, null,nop.ai.dao.entity.NopAiProjectConfig.class);

    /**
     * 配置项。 refPropName: project, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<nop.ai.dao.entity.NopAiProjectConfig> getConfigs(){
       return _configs;
    }
       
    private final OrmEntitySet<nop.ai.dao.entity.NopAiRequirement> _requirements = new OrmEntitySet<>(this, PROP_NAME_requirements,
        nop.ai.dao.entity.NopAiRequirement.PROP_NAME_project, null,nop.ai.dao.entity.NopAiRequirement.class);

    /**
     * 需求列表。 refPropName: project, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<nop.ai.dao.entity.NopAiRequirement> getRequirements(){
       return _requirements;
    }
       
    private final OrmEntitySet<nop.ai.dao.entity.NopAiGenFile> _generatedFiles = new OrmEntitySet<>(this, PROP_NAME_generatedFiles,
        nop.ai.dao.entity.NopAiGenFile.PROP_NAME_project, null,nop.ai.dao.entity.NopAiGenFile.class);

    /**
     * 生成文件。 refPropName: project, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<nop.ai.dao.entity.NopAiGenFile> getGeneratedFiles(){
       return _generatedFiles;
    }
       
}
// resume CPD analysis - CPD-ON
