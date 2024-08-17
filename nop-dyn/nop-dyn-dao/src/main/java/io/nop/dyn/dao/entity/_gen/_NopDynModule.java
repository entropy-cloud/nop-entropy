package io.nop.dyn.dao.entity._gen;

import io.nop.orm.model.IEntityModel;
import io.nop.orm.support.DynamicOrmEntity;
import io.nop.orm.support.OrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.orm.IOrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.api.core.convert.ConvertHelper;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;

import io.nop.dyn.dao.entity.NopDynModule;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  模块定义: nop_dyn_module
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopDynModule extends DynamicOrmEntity{
    
    /* 模块ID: MODULE_ID VARCHAR */
    public static final String PROP_NAME_moduleId = "moduleId";
    public static final int PROP_ID_moduleId = 1;
    
    /* 模块名: MODULE_NAME VARCHAR */
    public static final String PROP_NAME_moduleName = "moduleName";
    public static final int PROP_ID_moduleName = 2;
    
    /* 模块版本: MODULE_VERSION INTEGER */
    public static final String PROP_NAME_moduleVersion = "moduleVersion";
    public static final int PROP_ID_moduleVersion = 3;
    
    /* 显示名: DISPLAY_NAME VARCHAR */
    public static final String PROP_NAME_displayName = "displayName";
    public static final int PROP_ID_displayName = 4;
    
    /* 基础模块ID: BASE_MODULE_ID VARCHAR */
    public static final String PROP_NAME_baseModuleId = "baseModuleId";
    public static final int PROP_ID_baseModuleId = 5;
    
    /* Java包名: BASE_PACKAGE_NAME VARCHAR */
    public static final String PROP_NAME_basePackageName = "basePackageName";
    public static final int PROP_ID_basePackageName = 6;
    
    /* 实体包名: ENTITY_PACKAGE_NAME VARCHAR */
    public static final String PROP_NAME_entityPackageName = "entityPackageName";
    public static final int PROP_ID_entityPackageName = 7;
    
    /* Maven组名: MAVEN_GROUP_ID VARCHAR */
    public static final String PROP_NAME_mavenGroupId = "mavenGroupId";
    public static final int PROP_ID_mavenGroupId = 8;
    
    /* 状态: STATUS INTEGER */
    public static final String PROP_NAME_status = "status";
    public static final int PROP_ID_status = 9;
    
    /* 数据版本: VERSION INTEGER */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 10;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 11;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 12;
    
    /* 修改人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 13;
    
    /* 修改时间: UPDATE_TIME TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 14;
    

    private static int _PROP_ID_BOUND = 15;

    
    /* relation: 基础模块 */
    public static final String PROP_NAME_baseModule = "baseModule";
    
    /* relation: 派生模块 */
    public static final String PROP_NAME_derivedModules = "derivedModules";
    
    /* relation:  */
    public static final String PROP_NAME_appMappings = "appMappings";
    
    /* relation: SQL语句 */
    public static final String PROP_NAME_sqls = "sqls";
    
    /* relation: 模块文件 */
    public static final String PROP_NAME_files = "files";
    
    /* relation: 模块页面 */
    public static final String PROP_NAME_pages = "pages";
    
    /* relation: 模块实体定义 */
    public static final String PROP_NAME_entityMetas = "entityMetas";
    
    /* relation: 数据域定义 */
    public static final String PROP_NAME_domains = "domains";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_moduleId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_moduleId};

    private static final String[] PROP_ID_TO_NAME = new String[15];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_moduleId] = PROP_NAME_moduleId;
          PROP_NAME_TO_ID.put(PROP_NAME_moduleId, PROP_ID_moduleId);
      
          PROP_ID_TO_NAME[PROP_ID_moduleName] = PROP_NAME_moduleName;
          PROP_NAME_TO_ID.put(PROP_NAME_moduleName, PROP_ID_moduleName);
      
          PROP_ID_TO_NAME[PROP_ID_moduleVersion] = PROP_NAME_moduleVersion;
          PROP_NAME_TO_ID.put(PROP_NAME_moduleVersion, PROP_ID_moduleVersion);
      
          PROP_ID_TO_NAME[PROP_ID_displayName] = PROP_NAME_displayName;
          PROP_NAME_TO_ID.put(PROP_NAME_displayName, PROP_ID_displayName);
      
          PROP_ID_TO_NAME[PROP_ID_baseModuleId] = PROP_NAME_baseModuleId;
          PROP_NAME_TO_ID.put(PROP_NAME_baseModuleId, PROP_ID_baseModuleId);
      
          PROP_ID_TO_NAME[PROP_ID_basePackageName] = PROP_NAME_basePackageName;
          PROP_NAME_TO_ID.put(PROP_NAME_basePackageName, PROP_ID_basePackageName);
      
          PROP_ID_TO_NAME[PROP_ID_entityPackageName] = PROP_NAME_entityPackageName;
          PROP_NAME_TO_ID.put(PROP_NAME_entityPackageName, PROP_ID_entityPackageName);
      
          PROP_ID_TO_NAME[PROP_ID_mavenGroupId] = PROP_NAME_mavenGroupId;
          PROP_NAME_TO_ID.put(PROP_NAME_mavenGroupId, PROP_ID_mavenGroupId);
      
          PROP_ID_TO_NAME[PROP_ID_status] = PROP_NAME_status;
          PROP_NAME_TO_ID.put(PROP_NAME_status, PROP_ID_status);
      
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
      
    }

    
    /* 模块ID: MODULE_ID */
    private java.lang.String _moduleId;
    
    /* 模块名: MODULE_NAME */
    private java.lang.String _moduleName;
    
    /* 模块版本: MODULE_VERSION */
    private java.lang.Integer _moduleVersion;
    
    /* 显示名: DISPLAY_NAME */
    private java.lang.String _displayName;
    
    /* 基础模块ID: BASE_MODULE_ID */
    private java.lang.String _baseModuleId;
    
    /* Java包名: BASE_PACKAGE_NAME */
    private java.lang.String _basePackageName;
    
    /* 实体包名: ENTITY_PACKAGE_NAME */
    private java.lang.String _entityPackageName;
    
    /* Maven组名: MAVEN_GROUP_ID */
    private java.lang.String _mavenGroupId;
    
    /* 状态: STATUS */
    private java.lang.Integer _status;
    
    /* 数据版本: VERSION */
    private java.lang.Integer _version;
    
    /* 创建人: CREATED_BY */
    private java.lang.String _createdBy;
    
    /* 创建时间: CREATE_TIME */
    private java.sql.Timestamp _createTime;
    
    /* 修改人: UPDATED_BY */
    private java.lang.String _updatedBy;
    
    /* 修改时间: UPDATE_TIME */
    private java.sql.Timestamp _updateTime;
    

    public _NopDynModule(){
        // for debug
    }

    protected NopDynModule newInstance(){
        NopDynModule entity = new NopDynModule();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopDynModule cloneInstance() {
        NopDynModule entity = newInstance();
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
      return "io.nop.dyn.dao.entity.NopDynModule";
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
    
        return buildSimpleId(PROP_ID_moduleId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_moduleId;
          
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
        
            case PROP_ID_moduleId:
               return getModuleId();
        
            case PROP_ID_moduleName:
               return getModuleName();
        
            case PROP_ID_moduleVersion:
               return getModuleVersion();
        
            case PROP_ID_displayName:
               return getDisplayName();
        
            case PROP_ID_baseModuleId:
               return getBaseModuleId();
        
            case PROP_ID_basePackageName:
               return getBasePackageName();
        
            case PROP_ID_entityPackageName:
               return getEntityPackageName();
        
            case PROP_ID_mavenGroupId:
               return getMavenGroupId();
        
            case PROP_ID_status:
               return getStatus();
        
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
        
           default:
              return super.orm_propValue(propId);
        }
    }

    

    @Override
    public void orm_propValue(int propId, Object value){
        switch(propId){
        
            case PROP_ID_moduleId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_moduleId));
               }
               setModuleId(typedValue);
               break;
            }
        
            case PROP_ID_moduleName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_moduleName));
               }
               setModuleName(typedValue);
               break;
            }
        
            case PROP_ID_moduleVersion:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_moduleVersion));
               }
               setModuleVersion(typedValue);
               break;
            }
        
            case PROP_ID_displayName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_displayName));
               }
               setDisplayName(typedValue);
               break;
            }
        
            case PROP_ID_baseModuleId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_baseModuleId));
               }
               setBaseModuleId(typedValue);
               break;
            }
        
            case PROP_ID_basePackageName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_basePackageName));
               }
               setBasePackageName(typedValue);
               break;
            }
        
            case PROP_ID_entityPackageName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_entityPackageName));
               }
               setEntityPackageName(typedValue);
               break;
            }
        
            case PROP_ID_mavenGroupId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_mavenGroupId));
               }
               setMavenGroupId(typedValue);
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
        
           default:
              super.orm_propValue(propId,value);
        }
    }

    @Override
    public void orm_internalSet(int propId, Object value) {
        switch(propId){
        
            case PROP_ID_moduleId:{
               onInitProp(propId);
               this._moduleId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_moduleName:{
               onInitProp(propId);
               this._moduleName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_moduleVersion:{
               onInitProp(propId);
               this._moduleVersion = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_displayName:{
               onInitProp(propId);
               this._displayName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_baseModuleId:{
               onInitProp(propId);
               this._baseModuleId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_basePackageName:{
               onInitProp(propId);
               this._basePackageName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_entityPackageName:{
               onInitProp(propId);
               this._entityPackageName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_mavenGroupId:{
               onInitProp(propId);
               this._mavenGroupId = (java.lang.String)value;
               
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
        
           default:
              super.orm_internalSet(propId,value);
        }
    }

    
    /**
     * 模块ID: MODULE_ID
     */
    public java.lang.String getModuleId(){
         onPropGet(PROP_ID_moduleId);
         return _moduleId;
    }

    /**
     * 模块ID: MODULE_ID
     */
    public void setModuleId(java.lang.String value){
        if(onPropSet(PROP_ID_moduleId,value)){
            this._moduleId = value;
            internalClearRefs(PROP_ID_moduleId);
            orm_id();
        }
    }
    
    /**
     * 模块名: MODULE_NAME
     */
    public java.lang.String getModuleName(){
         onPropGet(PROP_ID_moduleName);
         return _moduleName;
    }

    /**
     * 模块名: MODULE_NAME
     */
    public void setModuleName(java.lang.String value){
        if(onPropSet(PROP_ID_moduleName,value)){
            this._moduleName = value;
            internalClearRefs(PROP_ID_moduleName);
            
        }
    }
    
    /**
     * 模块版本: MODULE_VERSION
     */
    public java.lang.Integer getModuleVersion(){
         onPropGet(PROP_ID_moduleVersion);
         return _moduleVersion;
    }

    /**
     * 模块版本: MODULE_VERSION
     */
    public void setModuleVersion(java.lang.Integer value){
        if(onPropSet(PROP_ID_moduleVersion,value)){
            this._moduleVersion = value;
            internalClearRefs(PROP_ID_moduleVersion);
            
        }
    }
    
    /**
     * 显示名: DISPLAY_NAME
     */
    public java.lang.String getDisplayName(){
         onPropGet(PROP_ID_displayName);
         return _displayName;
    }

    /**
     * 显示名: DISPLAY_NAME
     */
    public void setDisplayName(java.lang.String value){
        if(onPropSet(PROP_ID_displayName,value)){
            this._displayName = value;
            internalClearRefs(PROP_ID_displayName);
            
        }
    }
    
    /**
     * 基础模块ID: BASE_MODULE_ID
     */
    public java.lang.String getBaseModuleId(){
         onPropGet(PROP_ID_baseModuleId);
         return _baseModuleId;
    }

    /**
     * 基础模块ID: BASE_MODULE_ID
     */
    public void setBaseModuleId(java.lang.String value){
        if(onPropSet(PROP_ID_baseModuleId,value)){
            this._baseModuleId = value;
            internalClearRefs(PROP_ID_baseModuleId);
            
        }
    }
    
    /**
     * Java包名: BASE_PACKAGE_NAME
     */
    public java.lang.String getBasePackageName(){
         onPropGet(PROP_ID_basePackageName);
         return _basePackageName;
    }

    /**
     * Java包名: BASE_PACKAGE_NAME
     */
    public void setBasePackageName(java.lang.String value){
        if(onPropSet(PROP_ID_basePackageName,value)){
            this._basePackageName = value;
            internalClearRefs(PROP_ID_basePackageName);
            
        }
    }
    
    /**
     * 实体包名: ENTITY_PACKAGE_NAME
     */
    public java.lang.String getEntityPackageName(){
         onPropGet(PROP_ID_entityPackageName);
         return _entityPackageName;
    }

    /**
     * 实体包名: ENTITY_PACKAGE_NAME
     */
    public void setEntityPackageName(java.lang.String value){
        if(onPropSet(PROP_ID_entityPackageName,value)){
            this._entityPackageName = value;
            internalClearRefs(PROP_ID_entityPackageName);
            
        }
    }
    
    /**
     * Maven组名: MAVEN_GROUP_ID
     */
    public java.lang.String getMavenGroupId(){
         onPropGet(PROP_ID_mavenGroupId);
         return _mavenGroupId;
    }

    /**
     * Maven组名: MAVEN_GROUP_ID
     */
    public void setMavenGroupId(java.lang.String value){
        if(onPropSet(PROP_ID_mavenGroupId,value)){
            this._mavenGroupId = value;
            internalClearRefs(PROP_ID_mavenGroupId);
            
        }
    }
    
    /**
     * 状态: STATUS
     */
    public java.lang.Integer getStatus(){
         onPropGet(PROP_ID_status);
         return _status;
    }

    /**
     * 状态: STATUS
     */
    public void setStatus(java.lang.Integer value){
        if(onPropSet(PROP_ID_status,value)){
            this._status = value;
            internalClearRefs(PROP_ID_status);
            
        }
    }
    
    /**
     * 数据版本: VERSION
     */
    public java.lang.Integer getVersion(){
         onPropGet(PROP_ID_version);
         return _version;
    }

    /**
     * 数据版本: VERSION
     */
    public void setVersion(java.lang.Integer value){
        if(onPropSet(PROP_ID_version,value)){
            this._version = value;
            internalClearRefs(PROP_ID_version);
            
        }
    }
    
    /**
     * 创建人: CREATED_BY
     */
    public java.lang.String getCreatedBy(){
         onPropGet(PROP_ID_createdBy);
         return _createdBy;
    }

    /**
     * 创建人: CREATED_BY
     */
    public void setCreatedBy(java.lang.String value){
        if(onPropSet(PROP_ID_createdBy,value)){
            this._createdBy = value;
            internalClearRefs(PROP_ID_createdBy);
            
        }
    }
    
    /**
     * 创建时间: CREATE_TIME
     */
    public java.sql.Timestamp getCreateTime(){
         onPropGet(PROP_ID_createTime);
         return _createTime;
    }

    /**
     * 创建时间: CREATE_TIME
     */
    public void setCreateTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_createTime,value)){
            this._createTime = value;
            internalClearRefs(PROP_ID_createTime);
            
        }
    }
    
    /**
     * 修改人: UPDATED_BY
     */
    public java.lang.String getUpdatedBy(){
         onPropGet(PROP_ID_updatedBy);
         return _updatedBy;
    }

    /**
     * 修改人: UPDATED_BY
     */
    public void setUpdatedBy(java.lang.String value){
        if(onPropSet(PROP_ID_updatedBy,value)){
            this._updatedBy = value;
            internalClearRefs(PROP_ID_updatedBy);
            
        }
    }
    
    /**
     * 修改时间: UPDATE_TIME
     */
    public java.sql.Timestamp getUpdateTime(){
         onPropGet(PROP_ID_updateTime);
         return _updateTime;
    }

    /**
     * 修改时间: UPDATE_TIME
     */
    public void setUpdateTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_updateTime,value)){
            this._updateTime = value;
            internalClearRefs(PROP_ID_updateTime);
            
        }
    }
    
    /**
     * 基础模块
     */
    public io.nop.dyn.dao.entity.NopDynModule getBaseModule(){
       return (io.nop.dyn.dao.entity.NopDynModule)internalGetRefEntity(PROP_NAME_baseModule);
    }

    public void setBaseModule(io.nop.dyn.dao.entity.NopDynModule refEntity){
   
           if(refEntity == null){
           
                   this.setBaseModuleId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_baseModule, refEntity,()->{
           
                           this.setBaseModuleId(refEntity.getModuleId());
                       
           });
           }
       
    }
       
    private final OrmEntitySet<io.nop.dyn.dao.entity.NopDynModule> _derivedModules = new OrmEntitySet<>(this, PROP_NAME_derivedModules,
        io.nop.dyn.dao.entity.NopDynModule.PROP_NAME_baseModule, null,io.nop.dyn.dao.entity.NopDynModule.class);

    /**
     * 派生模块。 refPropName: baseModule, keyProp: {rel.keyProp}
     */
    public IOrmEntitySet<io.nop.dyn.dao.entity.NopDynModule> getDerivedModules(){
       return _derivedModules;
    }
       
    private final OrmEntitySet<io.nop.dyn.dao.entity.NopDynAppModule> _appMappings = new OrmEntitySet<>(this, PROP_NAME_appMappings,
        io.nop.dyn.dao.entity.NopDynAppModule.PROP_NAME_module, null,io.nop.dyn.dao.entity.NopDynAppModule.class);

    /**
     * 。 refPropName: module, keyProp: {rel.keyProp}
     */
    public IOrmEntitySet<io.nop.dyn.dao.entity.NopDynAppModule> getAppMappings(){
       return _appMappings;
    }
       
    private final OrmEntitySet<io.nop.dyn.dao.entity.NopDynSql> _sqls = new OrmEntitySet<>(this, PROP_NAME_sqls,
        io.nop.dyn.dao.entity.NopDynSql.PROP_NAME_module, null,io.nop.dyn.dao.entity.NopDynSql.class);

    /**
     * SQL语句。 refPropName: module, keyProp: {rel.keyProp}
     */
    public IOrmEntitySet<io.nop.dyn.dao.entity.NopDynSql> getSqls(){
       return _sqls;
    }
       
    private final OrmEntitySet<io.nop.dyn.dao.entity.NopDynFile> _files = new OrmEntitySet<>(this, PROP_NAME_files,
        io.nop.dyn.dao.entity.NopDynFile.PROP_NAME_module, null,io.nop.dyn.dao.entity.NopDynFile.class);

    /**
     * 模块文件。 refPropName: module, keyProp: {rel.keyProp}
     */
    public IOrmEntitySet<io.nop.dyn.dao.entity.NopDynFile> getFiles(){
       return _files;
    }
       
    private final OrmEntitySet<io.nop.dyn.dao.entity.NopDynPage> _pages = new OrmEntitySet<>(this, PROP_NAME_pages,
        io.nop.dyn.dao.entity.NopDynPage.PROP_NAME_module, null,io.nop.dyn.dao.entity.NopDynPage.class);

    /**
     * 模块页面。 refPropName: module, keyProp: {rel.keyProp}
     */
    public IOrmEntitySet<io.nop.dyn.dao.entity.NopDynPage> getPages(){
       return _pages;
    }
       
    private final OrmEntitySet<io.nop.dyn.dao.entity.NopDynEntityMeta> _entityMetas = new OrmEntitySet<>(this, PROP_NAME_entityMetas,
        io.nop.dyn.dao.entity.NopDynEntityMeta.PROP_NAME_module, null,io.nop.dyn.dao.entity.NopDynEntityMeta.class);

    /**
     * 模块实体定义。 refPropName: module, keyProp: {rel.keyProp}
     */
    public IOrmEntitySet<io.nop.dyn.dao.entity.NopDynEntityMeta> getEntityMetas(){
       return _entityMetas;
    }
       
    private final OrmEntitySet<io.nop.dyn.dao.entity.NopDynDomain> _domains = new OrmEntitySet<>(this, PROP_NAME_domains,
        io.nop.dyn.dao.entity.NopDynDomain.PROP_NAME_module, io.nop.dyn.dao.entity.NopDynDomain.PROP_NAME_domainName,io.nop.dyn.dao.entity.NopDynDomain.class);

    /**
     * 数据域定义。 refPropName: module, keyProp: {rel.keyProp}
     */
    public IOrmEntitySet<io.nop.dyn.dao.entity.NopDynDomain> getDomains(){
       return _domains;
    }
       
        public List<io.nop.dyn.dao.entity.NopDynApp> getRelatedAppList(){
            return (List<io.nop.dyn.dao.entity.NopDynApp>)io.nop.orm.support.OrmEntityHelper.getRefProps(getAppMappings(),"app");
        }

        public List<String> getRelatedAppList_ids(){
            return io.nop.orm.support.OrmEntityHelper.getRefIds(getAppMappings(),"app");
        }

        public void setRelatedAppList_ids(List<String> value){
            io.nop.orm.support.OrmEntityHelper.setRefIds(getAppMappings(),"app",value);
        }
    

    public String getRelatedAppList_label(){
        return io.nop.orm.support.OrmEntityHelper.getLabelForRefProps(getAppMappings(),"app");
    }


}
// resume CPD analysis - CPD-ON
