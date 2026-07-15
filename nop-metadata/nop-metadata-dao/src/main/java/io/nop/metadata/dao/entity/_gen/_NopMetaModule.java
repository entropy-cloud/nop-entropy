package io.nop.metadata.dao.entity._gen;

import io.nop.orm.model.IEntityModel;
import io.nop.orm.support.DynamicOrmEntity;
import io.nop.orm.support.OrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.orm.IOrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code

import io.nop.api.core.convert.ConvertHelper;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;

import io.nop.metadata.dao.entity.NopMetaModule;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  元数据模块: nop_meta_module
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopMetaModule extends DynamicOrmEntity{
    
    /* 模块版本ID: META_MODULE_ID VARCHAR */
    public static final String PROP_NAME_metaModuleId = "metaModuleId";
    public static final int PROP_ID_metaModuleId = 1;
    
    /* 模块标识: MODULE_ID VARCHAR */
    public static final String PROP_NAME_moduleId = "moduleId";
    public static final int PROP_ID_moduleId = 2;
    
    /* 模块名: MODULE_NAME VARCHAR */
    public static final String PROP_NAME_moduleName = "moduleName";
    public static final int PROP_ID_moduleName = 3;
    
    /* 显示名: DISPLAY_NAME VARCHAR */
    public static final String PROP_NAME_displayName = "displayName";
    public static final int PROP_ID_displayName = 4;
    
    /* 模块版本号: VERSION BIGINT */
    public static final String PROP_NAME_moduleVersion = "moduleVersion";
    public static final int PROP_ID_moduleVersion = 5;
    
    /* 基线模块版本ID: BASE_MODULE_ID VARCHAR */
    public static final String PROP_NAME_baseModuleId = "baseModuleId";
    public static final int PROP_ID_baseModuleId = 6;
    
    /* 模块状态: STATUS VARCHAR */
    public static final String PROP_NAME_status = "status";
    public static final int PROP_ID_status = 7;
    
    /* Maven GroupId: MAVEN_GROUP_ID VARCHAR */
    public static final String PROP_NAME_mavenGroupId = "mavenGroupId";
    public static final int PROP_ID_mavenGroupId = 8;
    
    /* Maven ArtifactId: MAVEN_ARTIFACT_ID VARCHAR */
    public static final String PROP_NAME_mavenArtifactId = "mavenArtifactId";
    public static final int PROP_ID_mavenArtifactId = 9;
    
    /* Maven版本: MAVEN_VERSION VARCHAR */
    public static final String PROP_NAME_mavenVersion = "mavenVersion";
    public static final int PROP_ID_mavenVersion = 10;
    
    /* Git仓库路径: GIT_REPO_PATH VARCHAR */
    public static final String PROP_NAME_gitRepoPath = "gitRepoPath";
    public static final int PROP_ID_gitRepoPath = 11;
    
    /* Git分支: GIT_BRANCH VARCHAR */
    public static final String PROP_NAME_gitBranch = "gitBranch";
    public static final int PROP_ID_gitBranch = 12;
    
    /* Git提交: GIT_COMMIT_ID VARCHAR */
    public static final String PROP_NAME_gitCommitId = "gitCommitId";
    public static final int PROP_ID_gitCommitId = 13;
    
    /* 导入时间: IMPORTED_AT TIMESTAMP */
    public static final String PROP_NAME_importedAt = "importedAt";
    public static final int PROP_ID_importedAt = 14;
    
    /* 扩展配置: EXT_CONFIG VARCHAR */
    public static final String PROP_NAME_extConfig = "extConfig";
    public static final int PROP_ID_extConfig = 15;
    
    /* 数据版本: DEL_VERSION BIGINT */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 16;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 17;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 18;
    
    /* 修改人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 19;
    
    /* 修改时间: UPDATE_TIME TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 20;
    
    /* 备注: REMARK VARCHAR */
    public static final String PROP_NAME_remark = "remark";
    public static final int PROP_ID_remark = 21;
    

    private static int _PROP_ID_BOUND = 22;

    
    /* relation: 基线模块 */
    public static final String PROP_NAME_baseModule = "baseModule";
    
    /* component:  */
    public static final String PROP_NAME_extConfigComponent = "extConfigComponent";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_metaModuleId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_metaModuleId};

    private static final String[] PROP_ID_TO_NAME = new String[22];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_metaModuleId] = PROP_NAME_metaModuleId;
          PROP_NAME_TO_ID.put(PROP_NAME_metaModuleId, PROP_ID_metaModuleId);
      
          PROP_ID_TO_NAME[PROP_ID_moduleId] = PROP_NAME_moduleId;
          PROP_NAME_TO_ID.put(PROP_NAME_moduleId, PROP_ID_moduleId);
      
          PROP_ID_TO_NAME[PROP_ID_moduleName] = PROP_NAME_moduleName;
          PROP_NAME_TO_ID.put(PROP_NAME_moduleName, PROP_ID_moduleName);
      
          PROP_ID_TO_NAME[PROP_ID_displayName] = PROP_NAME_displayName;
          PROP_NAME_TO_ID.put(PROP_NAME_displayName, PROP_ID_displayName);
      
          PROP_ID_TO_NAME[PROP_ID_moduleVersion] = PROP_NAME_moduleVersion;
          PROP_NAME_TO_ID.put(PROP_NAME_moduleVersion, PROP_ID_moduleVersion);
      
          PROP_ID_TO_NAME[PROP_ID_baseModuleId] = PROP_NAME_baseModuleId;
          PROP_NAME_TO_ID.put(PROP_NAME_baseModuleId, PROP_ID_baseModuleId);
      
          PROP_ID_TO_NAME[PROP_ID_status] = PROP_NAME_status;
          PROP_NAME_TO_ID.put(PROP_NAME_status, PROP_ID_status);
      
          PROP_ID_TO_NAME[PROP_ID_mavenGroupId] = PROP_NAME_mavenGroupId;
          PROP_NAME_TO_ID.put(PROP_NAME_mavenGroupId, PROP_ID_mavenGroupId);
      
          PROP_ID_TO_NAME[PROP_ID_mavenArtifactId] = PROP_NAME_mavenArtifactId;
          PROP_NAME_TO_ID.put(PROP_NAME_mavenArtifactId, PROP_ID_mavenArtifactId);
      
          PROP_ID_TO_NAME[PROP_ID_mavenVersion] = PROP_NAME_mavenVersion;
          PROP_NAME_TO_ID.put(PROP_NAME_mavenVersion, PROP_ID_mavenVersion);
      
          PROP_ID_TO_NAME[PROP_ID_gitRepoPath] = PROP_NAME_gitRepoPath;
          PROP_NAME_TO_ID.put(PROP_NAME_gitRepoPath, PROP_ID_gitRepoPath);
      
          PROP_ID_TO_NAME[PROP_ID_gitBranch] = PROP_NAME_gitBranch;
          PROP_NAME_TO_ID.put(PROP_NAME_gitBranch, PROP_ID_gitBranch);
      
          PROP_ID_TO_NAME[PROP_ID_gitCommitId] = PROP_NAME_gitCommitId;
          PROP_NAME_TO_ID.put(PROP_NAME_gitCommitId, PROP_ID_gitCommitId);
      
          PROP_ID_TO_NAME[PROP_ID_importedAt] = PROP_NAME_importedAt;
          PROP_NAME_TO_ID.put(PROP_NAME_importedAt, PROP_ID_importedAt);
      
          PROP_ID_TO_NAME[PROP_ID_extConfig] = PROP_NAME_extConfig;
          PROP_NAME_TO_ID.put(PROP_NAME_extConfig, PROP_ID_extConfig);
      
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
      
          PROP_ID_TO_NAME[PROP_ID_remark] = PROP_NAME_remark;
          PROP_NAME_TO_ID.put(PROP_NAME_remark, PROP_ID_remark);
      
    }

    
    /* 模块版本ID: META_MODULE_ID */
    private java.lang.String _metaModuleId;
    
    /* 模块标识: MODULE_ID */
    private java.lang.String _moduleId;
    
    /* 模块名: MODULE_NAME */
    private java.lang.String _moduleName;
    
    /* 显示名: DISPLAY_NAME */
    private java.lang.String _displayName;
    
    /* 模块版本号: VERSION */
    private java.lang.Long _moduleVersion;
    
    /* 基线模块版本ID: BASE_MODULE_ID */
    private java.lang.String _baseModuleId;
    
    /* 模块状态: STATUS */
    private java.lang.String _status;
    
    /* Maven GroupId: MAVEN_GROUP_ID */
    private java.lang.String _mavenGroupId;
    
    /* Maven ArtifactId: MAVEN_ARTIFACT_ID */
    private java.lang.String _mavenArtifactId;
    
    /* Maven版本: MAVEN_VERSION */
    private java.lang.String _mavenVersion;
    
    /* Git仓库路径: GIT_REPO_PATH */
    private java.lang.String _gitRepoPath;
    
    /* Git分支: GIT_BRANCH */
    private java.lang.String _gitBranch;
    
    /* Git提交: GIT_COMMIT_ID */
    private java.lang.String _gitCommitId;
    
    /* 导入时间: IMPORTED_AT */
    private java.sql.Timestamp _importedAt;
    
    /* 扩展配置: EXT_CONFIG */
    private java.lang.String _extConfig;
    
    /* 数据版本: DEL_VERSION */
    private java.lang.Long _version;
    
    /* 创建人: CREATED_BY */
    private java.lang.String _createdBy;
    
    /* 创建时间: CREATE_TIME */
    private java.sql.Timestamp _createTime;
    
    /* 修改人: UPDATED_BY */
    private java.lang.String _updatedBy;
    
    /* 修改时间: UPDATE_TIME */
    private java.sql.Timestamp _updateTime;
    
    /* 备注: REMARK */
    private java.lang.String _remark;
    

    public _NopMetaModule(){
        // for debug
    }

    protected NopMetaModule newInstance(){
        NopMetaModule entity = new NopMetaModule();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopMetaModule cloneInstance() {
        NopMetaModule entity = newInstance();
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
      return "io.nop.metadata.dao.entity.NopMetaModule";
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
    
        return buildSimpleId(PROP_ID_metaModuleId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_metaModuleId;
          
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
        
            case PROP_ID_metaModuleId:
               return getMetaModuleId();
        
            case PROP_ID_moduleId:
               return getModuleId();
        
            case PROP_ID_moduleName:
               return getModuleName();
        
            case PROP_ID_displayName:
               return getDisplayName();
        
            case PROP_ID_moduleVersion:
               return getModuleVersion();
        
            case PROP_ID_baseModuleId:
               return getBaseModuleId();
        
            case PROP_ID_status:
               return getStatus();
        
            case PROP_ID_mavenGroupId:
               return getMavenGroupId();
        
            case PROP_ID_mavenArtifactId:
               return getMavenArtifactId();
        
            case PROP_ID_mavenVersion:
               return getMavenVersion();
        
            case PROP_ID_gitRepoPath:
               return getGitRepoPath();
        
            case PROP_ID_gitBranch:
               return getGitBranch();
        
            case PROP_ID_gitCommitId:
               return getGitCommitId();
        
            case PROP_ID_importedAt:
               return getImportedAt();
        
            case PROP_ID_extConfig:
               return getExtConfig();
        
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
        
            case PROP_ID_remark:
               return getRemark();
        
           default:
              return super.orm_propValue(propId);
        }
    }

    

    @Override
    public void orm_propValue(int propId, Object value){
        switch(propId){
        
            case PROP_ID_metaModuleId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_metaModuleId));
               }
               setMetaModuleId(typedValue);
               break;
            }
        
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
        
            case PROP_ID_displayName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_displayName));
               }
               setDisplayName(typedValue);
               break;
            }
        
            case PROP_ID_moduleVersion:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_moduleVersion));
               }
               setModuleVersion(typedValue);
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
        
            case PROP_ID_status:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_status));
               }
               setStatus(typedValue);
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
        
            case PROP_ID_mavenArtifactId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_mavenArtifactId));
               }
               setMavenArtifactId(typedValue);
               break;
            }
        
            case PROP_ID_mavenVersion:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_mavenVersion));
               }
               setMavenVersion(typedValue);
               break;
            }
        
            case PROP_ID_gitRepoPath:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_gitRepoPath));
               }
               setGitRepoPath(typedValue);
               break;
            }
        
            case PROP_ID_gitBranch:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_gitBranch));
               }
               setGitBranch(typedValue);
               break;
            }
        
            case PROP_ID_gitCommitId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_gitCommitId));
               }
               setGitCommitId(typedValue);
               break;
            }
        
            case PROP_ID_importedAt:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_importedAt));
               }
               setImportedAt(typedValue);
               break;
            }
        
            case PROP_ID_extConfig:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_extConfig));
               }
               setExtConfig(typedValue);
               break;
            }
        
            case PROP_ID_version:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
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
        
            case PROP_ID_remark:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_remark));
               }
               setRemark(typedValue);
               break;
            }
        
           default:
              super.orm_propValue(propId,value);
        }
    }

    @Override
    public void orm_internalSet(int propId, Object value) {
        switch(propId){
        
            case PROP_ID_metaModuleId:{
               onInitProp(propId);
               this._metaModuleId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_moduleId:{
               onInitProp(propId);
               this._moduleId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_moduleName:{
               onInitProp(propId);
               this._moduleName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_displayName:{
               onInitProp(propId);
               this._displayName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_moduleVersion:{
               onInitProp(propId);
               this._moduleVersion = (java.lang.Long)value;
               
               break;
            }
        
            case PROP_ID_baseModuleId:{
               onInitProp(propId);
               this._baseModuleId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_status:{
               onInitProp(propId);
               this._status = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_mavenGroupId:{
               onInitProp(propId);
               this._mavenGroupId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_mavenArtifactId:{
               onInitProp(propId);
               this._mavenArtifactId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_mavenVersion:{
               onInitProp(propId);
               this._mavenVersion = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_gitRepoPath:{
               onInitProp(propId);
               this._gitRepoPath = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_gitBranch:{
               onInitProp(propId);
               this._gitBranch = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_gitCommitId:{
               onInitProp(propId);
               this._gitCommitId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_importedAt:{
               onInitProp(propId);
               this._importedAt = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_extConfig:{
               onInitProp(propId);
               this._extConfig = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_version:{
               onInitProp(propId);
               this._version = (java.lang.Long)value;
               
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
        
            case PROP_ID_remark:{
               onInitProp(propId);
               this._remark = (java.lang.String)value;
               
               break;
            }
        
           default:
              super.orm_internalSet(propId,value);
        }
    }

    
    /**
     * 模块版本ID: META_MODULE_ID
     */
    public final java.lang.String getMetaModuleId(){
         onPropGet(PROP_ID_metaModuleId);
         return _metaModuleId;
    }

    /**
     * 模块版本ID: META_MODULE_ID
     */
    public final void setMetaModuleId(java.lang.String value){
        if(onPropSet(PROP_ID_metaModuleId,value)){
            this._metaModuleId = value;
            internalClearRefs(PROP_ID_metaModuleId);
            orm_id();
        }
    }
    
    /**
     * 模块标识: MODULE_ID
     */
    public final java.lang.String getModuleId(){
         onPropGet(PROP_ID_moduleId);
         return _moduleId;
    }

    /**
     * 模块标识: MODULE_ID
     */
    public final void setModuleId(java.lang.String value){
        if(onPropSet(PROP_ID_moduleId,value)){
            this._moduleId = value;
            internalClearRefs(PROP_ID_moduleId);
            
        }
    }
    
    /**
     * 模块名: MODULE_NAME
     */
    public final java.lang.String getModuleName(){
         onPropGet(PROP_ID_moduleName);
         return _moduleName;
    }

    /**
     * 模块名: MODULE_NAME
     */
    public final void setModuleName(java.lang.String value){
        if(onPropSet(PROP_ID_moduleName,value)){
            this._moduleName = value;
            internalClearRefs(PROP_ID_moduleName);
            
        }
    }
    
    /**
     * 显示名: DISPLAY_NAME
     */
    public final java.lang.String getDisplayName(){
         onPropGet(PROP_ID_displayName);
         return _displayName;
    }

    /**
     * 显示名: DISPLAY_NAME
     */
    public final void setDisplayName(java.lang.String value){
        if(onPropSet(PROP_ID_displayName,value)){
            this._displayName = value;
            internalClearRefs(PROP_ID_displayName);
            
        }
    }
    
    /**
     * 模块版本号: VERSION
     */
    public final java.lang.Long getModuleVersion(){
         onPropGet(PROP_ID_moduleVersion);
         return _moduleVersion;
    }

    /**
     * 模块版本号: VERSION
     */
    public final void setModuleVersion(java.lang.Long value){
        if(onPropSet(PROP_ID_moduleVersion,value)){
            this._moduleVersion = value;
            internalClearRefs(PROP_ID_moduleVersion);
            
        }
    }
    
    /**
     * 基线模块版本ID: BASE_MODULE_ID
     */
    public final java.lang.String getBaseModuleId(){
         onPropGet(PROP_ID_baseModuleId);
         return _baseModuleId;
    }

    /**
     * 基线模块版本ID: BASE_MODULE_ID
     */
    public final void setBaseModuleId(java.lang.String value){
        if(onPropSet(PROP_ID_baseModuleId,value)){
            this._baseModuleId = value;
            internalClearRefs(PROP_ID_baseModuleId);
            
        }
    }
    
    /**
     * 模块状态: STATUS
     */
    public final java.lang.String getStatus(){
         onPropGet(PROP_ID_status);
         return _status;
    }

    /**
     * 模块状态: STATUS
     */
    public final void setStatus(java.lang.String value){
        if(onPropSet(PROP_ID_status,value)){
            this._status = value;
            internalClearRefs(PROP_ID_status);
            
        }
    }
    
    /**
     * Maven GroupId: MAVEN_GROUP_ID
     */
    public final java.lang.String getMavenGroupId(){
         onPropGet(PROP_ID_mavenGroupId);
         return _mavenGroupId;
    }

    /**
     * Maven GroupId: MAVEN_GROUP_ID
     */
    public final void setMavenGroupId(java.lang.String value){
        if(onPropSet(PROP_ID_mavenGroupId,value)){
            this._mavenGroupId = value;
            internalClearRefs(PROP_ID_mavenGroupId);
            
        }
    }
    
    /**
     * Maven ArtifactId: MAVEN_ARTIFACT_ID
     */
    public final java.lang.String getMavenArtifactId(){
         onPropGet(PROP_ID_mavenArtifactId);
         return _mavenArtifactId;
    }

    /**
     * Maven ArtifactId: MAVEN_ARTIFACT_ID
     */
    public final void setMavenArtifactId(java.lang.String value){
        if(onPropSet(PROP_ID_mavenArtifactId,value)){
            this._mavenArtifactId = value;
            internalClearRefs(PROP_ID_mavenArtifactId);
            
        }
    }
    
    /**
     * Maven版本: MAVEN_VERSION
     */
    public final java.lang.String getMavenVersion(){
         onPropGet(PROP_ID_mavenVersion);
         return _mavenVersion;
    }

    /**
     * Maven版本: MAVEN_VERSION
     */
    public final void setMavenVersion(java.lang.String value){
        if(onPropSet(PROP_ID_mavenVersion,value)){
            this._mavenVersion = value;
            internalClearRefs(PROP_ID_mavenVersion);
            
        }
    }
    
    /**
     * Git仓库路径: GIT_REPO_PATH
     */
    public final java.lang.String getGitRepoPath(){
         onPropGet(PROP_ID_gitRepoPath);
         return _gitRepoPath;
    }

    /**
     * Git仓库路径: GIT_REPO_PATH
     */
    public final void setGitRepoPath(java.lang.String value){
        if(onPropSet(PROP_ID_gitRepoPath,value)){
            this._gitRepoPath = value;
            internalClearRefs(PROP_ID_gitRepoPath);
            
        }
    }
    
    /**
     * Git分支: GIT_BRANCH
     */
    public final java.lang.String getGitBranch(){
         onPropGet(PROP_ID_gitBranch);
         return _gitBranch;
    }

    /**
     * Git分支: GIT_BRANCH
     */
    public final void setGitBranch(java.lang.String value){
        if(onPropSet(PROP_ID_gitBranch,value)){
            this._gitBranch = value;
            internalClearRefs(PROP_ID_gitBranch);
            
        }
    }
    
    /**
     * Git提交: GIT_COMMIT_ID
     */
    public final java.lang.String getGitCommitId(){
         onPropGet(PROP_ID_gitCommitId);
         return _gitCommitId;
    }

    /**
     * Git提交: GIT_COMMIT_ID
     */
    public final void setGitCommitId(java.lang.String value){
        if(onPropSet(PROP_ID_gitCommitId,value)){
            this._gitCommitId = value;
            internalClearRefs(PROP_ID_gitCommitId);
            
        }
    }
    
    /**
     * 导入时间: IMPORTED_AT
     */
    public final java.sql.Timestamp getImportedAt(){
         onPropGet(PROP_ID_importedAt);
         return _importedAt;
    }

    /**
     * 导入时间: IMPORTED_AT
     */
    public final void setImportedAt(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_importedAt,value)){
            this._importedAt = value;
            internalClearRefs(PROP_ID_importedAt);
            
        }
    }
    
    /**
     * 扩展配置: EXT_CONFIG
     */
    public final java.lang.String getExtConfig(){
         onPropGet(PROP_ID_extConfig);
         return _extConfig;
    }

    /**
     * 扩展配置: EXT_CONFIG
     */
    public final void setExtConfig(java.lang.String value){
        if(onPropSet(PROP_ID_extConfig,value)){
            this._extConfig = value;
            internalClearRefs(PROP_ID_extConfig);
            
        }
    }
    
    /**
     * 数据版本: DEL_VERSION
     */
    public final java.lang.Long getVersion(){
         onPropGet(PROP_ID_version);
         return _version;
    }

    /**
     * 数据版本: DEL_VERSION
     */
    public final void setVersion(java.lang.Long value){
        if(onPropSet(PROP_ID_version,value)){
            this._version = value;
            internalClearRefs(PROP_ID_version);
            
        }
    }
    
    /**
     * 创建人: CREATED_BY
     */
    public final java.lang.String getCreatedBy(){
         onPropGet(PROP_ID_createdBy);
         return _createdBy;
    }

    /**
     * 创建人: CREATED_BY
     */
    public final void setCreatedBy(java.lang.String value){
        if(onPropSet(PROP_ID_createdBy,value)){
            this._createdBy = value;
            internalClearRefs(PROP_ID_createdBy);
            
        }
    }
    
    /**
     * 创建时间: CREATE_TIME
     */
    public final java.sql.Timestamp getCreateTime(){
         onPropGet(PROP_ID_createTime);
         return _createTime;
    }

    /**
     * 创建时间: CREATE_TIME
     */
    public final void setCreateTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_createTime,value)){
            this._createTime = value;
            internalClearRefs(PROP_ID_createTime);
            
        }
    }
    
    /**
     * 修改人: UPDATED_BY
     */
    public final java.lang.String getUpdatedBy(){
         onPropGet(PROP_ID_updatedBy);
         return _updatedBy;
    }

    /**
     * 修改人: UPDATED_BY
     */
    public final void setUpdatedBy(java.lang.String value){
        if(onPropSet(PROP_ID_updatedBy,value)){
            this._updatedBy = value;
            internalClearRefs(PROP_ID_updatedBy);
            
        }
    }
    
    /**
     * 修改时间: UPDATE_TIME
     */
    public final java.sql.Timestamp getUpdateTime(){
         onPropGet(PROP_ID_updateTime);
         return _updateTime;
    }

    /**
     * 修改时间: UPDATE_TIME
     */
    public final void setUpdateTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_updateTime,value)){
            this._updateTime = value;
            internalClearRefs(PROP_ID_updateTime);
            
        }
    }
    
    /**
     * 备注: REMARK
     */
    public final java.lang.String getRemark(){
         onPropGet(PROP_ID_remark);
         return _remark;
    }

    /**
     * 备注: REMARK
     */
    public final void setRemark(java.lang.String value){
        if(onPropSet(PROP_ID_remark,value)){
            this._remark = value;
            internalClearRefs(PROP_ID_remark);
            
        }
    }
    
    /**
     * 基线模块
     */
    public final io.nop.metadata.dao.entity.NopMetaModule getBaseModule(){
       return (io.nop.metadata.dao.entity.NopMetaModule)internalGetRefEntity(PROP_NAME_baseModule);
    }

    public final void setBaseModule(io.nop.metadata.dao.entity.NopMetaModule refEntity){
   
           if(refEntity == null){
           
                   this.setBaseModuleId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_baseModule, refEntity,()->{
           
                           this.setBaseModuleId(refEntity.getMetaModuleId());
                       
           });
           }
       
    }
       
   private io.nop.orm.component.JsonOrmComponent _extConfigComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_extConfigComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_extConfigComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_extConfig);
      
   }

   public final io.nop.orm.component.JsonOrmComponent getExtConfigComponent(){
      if(_extConfigComponent == null){
          _extConfigComponent = new io.nop.orm.component.JsonOrmComponent();
          _extConfigComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_extConfigComponent);
      }
      return _extConfigComponent;
   }

}
// resume CPD analysis - CPD-ON
