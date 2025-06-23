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

import nop.ai.dao.entity.NopAiProjectConfig;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  项目配置: nop_ai_project_config
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopAiProjectConfig extends DynamicOrmEntity{
    
    /* 主键: id VARCHAR */
    public static final String PROP_NAME_id = "id";
    public static final int PROP_ID_id = 1;
    
    /* 项目ID: project_id VARCHAR */
    public static final String PROP_NAME_projectId = "projectId";
    public static final int PROP_ID_projectId = 2;
    
    /* 配置名称: config_name VARCHAR */
    public static final String PROP_NAME_configName = "configName";
    public static final int PROP_ID_configName = 3;
    
    /* 配置值: config_value VARCHAR */
    public static final String PROP_NAME_configValue = "configValue";
    public static final int PROP_ID_configValue = 4;
    
    /* 配置类型: config_type VARCHAR */
    public static final String PROP_NAME_configType = "configType";
    public static final int PROP_ID_configType = 5;
    

    private static int _PROP_ID_BOUND = 6;

    
    /* relation:  */
    public static final String PROP_NAME_project = "project";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_id);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_id};

    private static final String[] PROP_ID_TO_NAME = new String[6];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_id] = PROP_NAME_id;
          PROP_NAME_TO_ID.put(PROP_NAME_id, PROP_ID_id);
      
          PROP_ID_TO_NAME[PROP_ID_projectId] = PROP_NAME_projectId;
          PROP_NAME_TO_ID.put(PROP_NAME_projectId, PROP_ID_projectId);
      
          PROP_ID_TO_NAME[PROP_ID_configName] = PROP_NAME_configName;
          PROP_NAME_TO_ID.put(PROP_NAME_configName, PROP_ID_configName);
      
          PROP_ID_TO_NAME[PROP_ID_configValue] = PROP_NAME_configValue;
          PROP_NAME_TO_ID.put(PROP_NAME_configValue, PROP_ID_configValue);
      
          PROP_ID_TO_NAME[PROP_ID_configType] = PROP_NAME_configType;
          PROP_NAME_TO_ID.put(PROP_NAME_configType, PROP_ID_configType);
      
    }

    
    /* 主键: id */
    private java.lang.String _id;
    
    /* 项目ID: project_id */
    private java.lang.String _projectId;
    
    /* 配置名称: config_name */
    private java.lang.String _configName;
    
    /* 配置值: config_value */
    private java.lang.String _configValue;
    
    /* 配置类型: config_type */
    private java.lang.String _configType;
    

    public _NopAiProjectConfig(){
        // for debug
    }

    protected NopAiProjectConfig newInstance(){
        NopAiProjectConfig entity = new NopAiProjectConfig();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopAiProjectConfig cloneInstance() {
        NopAiProjectConfig entity = newInstance();
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
      return "nop.ai.dao.entity.NopAiProjectConfig";
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
        
            case PROP_ID_configName:
               return getConfigName();
        
            case PROP_ID_configValue:
               return getConfigValue();
        
            case PROP_ID_configType:
               return getConfigType();
        
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
        
            case PROP_ID_configName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_configName));
               }
               setConfigName(typedValue);
               break;
            }
        
            case PROP_ID_configValue:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_configValue));
               }
               setConfigValue(typedValue);
               break;
            }
        
            case PROP_ID_configType:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_configType));
               }
               setConfigType(typedValue);
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
        
            case PROP_ID_configName:{
               onInitProp(propId);
               this._configName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_configValue:{
               onInitProp(propId);
               this._configValue = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_configType:{
               onInitProp(propId);
               this._configType = (java.lang.String)value;
               
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
     * 配置名称: config_name
     */
    public final java.lang.String getConfigName(){
         onPropGet(PROP_ID_configName);
         return _configName;
    }

    /**
     * 配置名称: config_name
     */
    public final void setConfigName(java.lang.String value){
        if(onPropSet(PROP_ID_configName,value)){
            this._configName = value;
            internalClearRefs(PROP_ID_configName);
            
        }
    }
    
    /**
     * 配置值: config_value
     */
    public final java.lang.String getConfigValue(){
         onPropGet(PROP_ID_configValue);
         return _configValue;
    }

    /**
     * 配置值: config_value
     */
    public final void setConfigValue(java.lang.String value){
        if(onPropSet(PROP_ID_configValue,value)){
            this._configValue = value;
            internalClearRefs(PROP_ID_configValue);
            
        }
    }
    
    /**
     * 配置类型: config_type
     */
    public final java.lang.String getConfigType(){
         onPropGet(PROP_ID_configType);
         return _configType;
    }

    /**
     * 配置类型: config_type
     */
    public final void setConfigType(java.lang.String value){
        if(onPropSet(PROP_ID_configType,value)){
            this._configType = value;
            internalClearRefs(PROP_ID_configType);
            
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
       
}
// resume CPD analysis - CPD-ON
