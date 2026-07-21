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

import io.nop.metadata.dao.entity.NopMetaEntity;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  元数据实体: nop_meta_entity
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopMetaEntity extends DynamicOrmEntity{
    
    /* 实体ID: META_ENTITY_ID VARCHAR */
    public static final String PROP_NAME_metaEntityId = "metaEntityId";
    public static final int PROP_ID_metaEntityId = 1;
    
    /* 模型ID: ORM_MODEL_ID VARCHAR */
    public static final String PROP_NAME_ormModelId = "ormModelId";
    public static final int PROP_ID_ormModelId = 2;
    
    /* 是否Delta: IS_DELTA TINYINT */
    public static final String PROP_NAME_isDelta = "isDelta";
    public static final int PROP_ID_isDelta = 3;
    
    /* 实体名: ENTITY_NAME VARCHAR */
    public static final String PROP_NAME_entityName = "entityName";
    public static final int PROP_ID_entityName = 4;
    
    /* 表名: TABLE_NAME VARCHAR */
    public static final String PROP_NAME_tableName = "tableName";
    public static final int PROP_ID_tableName = 5;
    
    /* 显示名: DISPLAY_NAME VARCHAR */
    public static final String PROP_NAME_displayName = "displayName";
    public static final int PROP_ID_displayName = 6;
    
    /* 类名: CLASS_NAME VARCHAR */
    public static final String PROP_NAME_className = "className";
    public static final int PROP_ID_className = 7;
    
    /* 标签集: TAG_SET VARCHAR */
    public static final String PROP_NAME_tagSet = "tagSet";
    public static final int PROP_ID_tagSet = 8;
    
    /* 查询空间: QUERY_SPACE VARCHAR */
    public static final String PROP_NAME_querySpace = "querySpace";
    public static final int PROP_ID_querySpace = 9;
    
    /* 持久化驱动: PERSIST_DRIVER VARCHAR */
    public static final String PROP_NAME_persistDriver = "persistDriver";
    public static final int PROP_ID_persistDriver = 10;
    
    /* 使用租户: USE_TENANT TINYINT */
    public static final String PROP_NAME_useTenant = "useTenant";
    public static final int PROP_ID_useTenant = 11;
    
    /* 使用版本: USE_REVISION TINYINT */
    public static final String PROP_NAME_useRevision = "useRevision";
    public static final int PROP_ID_useRevision = 12;
    
    /* 逻辑删除: USE_LOGICAL_DELETE TINYINT */
    public static final String PROP_NAME_useLogicalDelete = "useLogicalDelete";
    public static final int PROP_ID_useLogicalDelete = 13;
    
    /* 不生成代码: NOT_GEN_CODE TINYINT */
    public static final String PROP_NAME_notGenCode = "notGenCode";
    public static final int PROP_ID_notGenCode = 14;
    
    /* 创建人属性: CREATER_PROP VARCHAR */
    public static final String PROP_NAME_createrProp = "createrProp";
    public static final int PROP_ID_createrProp = 15;
    
    /* 创建时间属性: CREATE_TIME_PROP VARCHAR */
    public static final String PROP_NAME_createTimeProp = "createTimeProp";
    public static final int PROP_ID_createTimeProp = 16;
    
    /* 修改人属性: UPDATER_PROP VARCHAR */
    public static final String PROP_NAME_updaterProp = "updaterProp";
    public static final int PROP_ID_updaterProp = 17;
    
    /* 修改时间属性: UPDATE_TIME_PROP VARCHAR */
    public static final String PROP_NAME_updateTimeProp = "updateTimeProp";
    public static final int PROP_ID_updateTimeProp = 18;
    
    /* 版本属性: VERSION_PROP VARCHAR */
    public static final String PROP_NAME_versionProp = "versionProp";
    public static final int PROP_ID_versionProp = 19;
    
    /* 删除标记属性: DEL_FLAG_PROP VARCHAR */
    public static final String PROP_NAME_delFlagProp = "delFlagProp";
    public static final int PROP_ID_delFlagProp = 20;
    
    /* 删除版本属性: DEL_VERSION_PROP VARCHAR */
    public static final String PROP_NAME_delVersionProp = "delVersionProp";
    public static final int PROP_ID_delVersionProp = 21;
    
    /* 数据库目录: DB_CATALOG VARCHAR */
    public static final String PROP_NAME_dbCatalog = "dbCatalog";
    public static final int PROP_ID_dbCatalog = 22;
    
    /* 数据库Schema: DB_SCHEMA VARCHAR */
    public static final String PROP_NAME_dbSchema = "dbSchema";
    public static final int PROP_ID_dbSchema = 23;
    
    /* 业务域ID: BUSINESS_DOMAIN_ID VARCHAR */
    public static final String PROP_NAME_businessDomainId = "businessDomainId";
    public static final int PROP_ID_businessDomainId = 24;
    
    /* 扩展配置: EXT_CONFIG VARCHAR */
    public static final String PROP_NAME_extConfig = "extConfig";
    public static final int PROP_ID_extConfig = 25;
    
    /* 数据版本: VERSION BIGINT */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 26;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 27;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 28;
    
    /* 修改人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 29;
    
    /* 修改时间: UPDATE_TIME TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 30;
    
    /* 备注: REMARK VARCHAR */
    public static final String PROP_NAME_remark = "remark";
    public static final int PROP_ID_remark = 31;
    

    private static int _PROP_ID_BOUND = 32;

    
    /* relation: ORM模型 */
    public static final String PROP_NAME_ormModel = "ormModel";
    
    /* relation: 实体字段集 */
    public static final String PROP_NAME_entityFields = "entityFields";
    
    /* relation: 实体关系集 */
    public static final String PROP_NAME_entityRelations = "entityRelations";
    
    /* relation: 实体唯一键集 */
    public static final String PROP_NAME_entityUniqueKeys = "entityUniqueKeys";
    
    /* relation: 实体索引集 */
    public static final String PROP_NAME_entityIndexes = "entityIndexes";
    
    /* relation: 作为左实体的关联集 */
    public static final String PROP_NAME_joinAsLeft = "joinAsLeft";
    
    /* relation: 作为右实体的关联集 */
    public static final String PROP_NAME_joinAsRight = "joinAsRight";
    
    /* component:  */
    public static final String PROP_NAME_extConfigComponent = "extConfigComponent";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_metaEntityId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_metaEntityId};

    private static final String[] PROP_ID_TO_NAME = new String[32];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_metaEntityId] = PROP_NAME_metaEntityId;
          PROP_NAME_TO_ID.put(PROP_NAME_metaEntityId, PROP_ID_metaEntityId);
      
          PROP_ID_TO_NAME[PROP_ID_ormModelId] = PROP_NAME_ormModelId;
          PROP_NAME_TO_ID.put(PROP_NAME_ormModelId, PROP_ID_ormModelId);
      
          PROP_ID_TO_NAME[PROP_ID_isDelta] = PROP_NAME_isDelta;
          PROP_NAME_TO_ID.put(PROP_NAME_isDelta, PROP_ID_isDelta);
      
          PROP_ID_TO_NAME[PROP_ID_entityName] = PROP_NAME_entityName;
          PROP_NAME_TO_ID.put(PROP_NAME_entityName, PROP_ID_entityName);
      
          PROP_ID_TO_NAME[PROP_ID_tableName] = PROP_NAME_tableName;
          PROP_NAME_TO_ID.put(PROP_NAME_tableName, PROP_ID_tableName);
      
          PROP_ID_TO_NAME[PROP_ID_displayName] = PROP_NAME_displayName;
          PROP_NAME_TO_ID.put(PROP_NAME_displayName, PROP_ID_displayName);
      
          PROP_ID_TO_NAME[PROP_ID_className] = PROP_NAME_className;
          PROP_NAME_TO_ID.put(PROP_NAME_className, PROP_ID_className);
      
          PROP_ID_TO_NAME[PROP_ID_tagSet] = PROP_NAME_tagSet;
          PROP_NAME_TO_ID.put(PROP_NAME_tagSet, PROP_ID_tagSet);
      
          PROP_ID_TO_NAME[PROP_ID_querySpace] = PROP_NAME_querySpace;
          PROP_NAME_TO_ID.put(PROP_NAME_querySpace, PROP_ID_querySpace);
      
          PROP_ID_TO_NAME[PROP_ID_persistDriver] = PROP_NAME_persistDriver;
          PROP_NAME_TO_ID.put(PROP_NAME_persistDriver, PROP_ID_persistDriver);
      
          PROP_ID_TO_NAME[PROP_ID_useTenant] = PROP_NAME_useTenant;
          PROP_NAME_TO_ID.put(PROP_NAME_useTenant, PROP_ID_useTenant);
      
          PROP_ID_TO_NAME[PROP_ID_useRevision] = PROP_NAME_useRevision;
          PROP_NAME_TO_ID.put(PROP_NAME_useRevision, PROP_ID_useRevision);
      
          PROP_ID_TO_NAME[PROP_ID_useLogicalDelete] = PROP_NAME_useLogicalDelete;
          PROP_NAME_TO_ID.put(PROP_NAME_useLogicalDelete, PROP_ID_useLogicalDelete);
      
          PROP_ID_TO_NAME[PROP_ID_notGenCode] = PROP_NAME_notGenCode;
          PROP_NAME_TO_ID.put(PROP_NAME_notGenCode, PROP_ID_notGenCode);
      
          PROP_ID_TO_NAME[PROP_ID_createrProp] = PROP_NAME_createrProp;
          PROP_NAME_TO_ID.put(PROP_NAME_createrProp, PROP_ID_createrProp);
      
          PROP_ID_TO_NAME[PROP_ID_createTimeProp] = PROP_NAME_createTimeProp;
          PROP_NAME_TO_ID.put(PROP_NAME_createTimeProp, PROP_ID_createTimeProp);
      
          PROP_ID_TO_NAME[PROP_ID_updaterProp] = PROP_NAME_updaterProp;
          PROP_NAME_TO_ID.put(PROP_NAME_updaterProp, PROP_ID_updaterProp);
      
          PROP_ID_TO_NAME[PROP_ID_updateTimeProp] = PROP_NAME_updateTimeProp;
          PROP_NAME_TO_ID.put(PROP_NAME_updateTimeProp, PROP_ID_updateTimeProp);
      
          PROP_ID_TO_NAME[PROP_ID_versionProp] = PROP_NAME_versionProp;
          PROP_NAME_TO_ID.put(PROP_NAME_versionProp, PROP_ID_versionProp);
      
          PROP_ID_TO_NAME[PROP_ID_delFlagProp] = PROP_NAME_delFlagProp;
          PROP_NAME_TO_ID.put(PROP_NAME_delFlagProp, PROP_ID_delFlagProp);
      
          PROP_ID_TO_NAME[PROP_ID_delVersionProp] = PROP_NAME_delVersionProp;
          PROP_NAME_TO_ID.put(PROP_NAME_delVersionProp, PROP_ID_delVersionProp);
      
          PROP_ID_TO_NAME[PROP_ID_dbCatalog] = PROP_NAME_dbCatalog;
          PROP_NAME_TO_ID.put(PROP_NAME_dbCatalog, PROP_ID_dbCatalog);
      
          PROP_ID_TO_NAME[PROP_ID_dbSchema] = PROP_NAME_dbSchema;
          PROP_NAME_TO_ID.put(PROP_NAME_dbSchema, PROP_ID_dbSchema);
      
          PROP_ID_TO_NAME[PROP_ID_businessDomainId] = PROP_NAME_businessDomainId;
          PROP_NAME_TO_ID.put(PROP_NAME_businessDomainId, PROP_ID_businessDomainId);
      
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

    
    /* 实体ID: META_ENTITY_ID */
    private java.lang.String _metaEntityId;
    
    /* 模型ID: ORM_MODEL_ID */
    private java.lang.String _ormModelId;
    
    /* 是否Delta: IS_DELTA */
    private java.lang.Byte _isDelta;
    
    /* 实体名: ENTITY_NAME */
    private java.lang.String _entityName;
    
    /* 表名: TABLE_NAME */
    private java.lang.String _tableName;
    
    /* 显示名: DISPLAY_NAME */
    private java.lang.String _displayName;
    
    /* 类名: CLASS_NAME */
    private java.lang.String _className;
    
    /* 标签集: TAG_SET */
    private java.lang.String _tagSet;
    
    /* 查询空间: QUERY_SPACE */
    private java.lang.String _querySpace;
    
    /* 持久化驱动: PERSIST_DRIVER */
    private java.lang.String _persistDriver;
    
    /* 使用租户: USE_TENANT */
    private java.lang.Byte _useTenant;
    
    /* 使用版本: USE_REVISION */
    private java.lang.Byte _useRevision;
    
    /* 逻辑删除: USE_LOGICAL_DELETE */
    private java.lang.Byte _useLogicalDelete;
    
    /* 不生成代码: NOT_GEN_CODE */
    private java.lang.Byte _notGenCode;
    
    /* 创建人属性: CREATER_PROP */
    private java.lang.String _createrProp;
    
    /* 创建时间属性: CREATE_TIME_PROP */
    private java.lang.String _createTimeProp;
    
    /* 修改人属性: UPDATER_PROP */
    private java.lang.String _updaterProp;
    
    /* 修改时间属性: UPDATE_TIME_PROP */
    private java.lang.String _updateTimeProp;
    
    /* 版本属性: VERSION_PROP */
    private java.lang.String _versionProp;
    
    /* 删除标记属性: DEL_FLAG_PROP */
    private java.lang.String _delFlagProp;
    
    /* 删除版本属性: DEL_VERSION_PROP */
    private java.lang.String _delVersionProp;
    
    /* 数据库目录: DB_CATALOG */
    private java.lang.String _dbCatalog;
    
    /* 数据库Schema: DB_SCHEMA */
    private java.lang.String _dbSchema;
    
    /* 业务域ID: BUSINESS_DOMAIN_ID */
    private java.lang.String _businessDomainId;
    
    /* 扩展配置: EXT_CONFIG */
    private java.lang.String _extConfig;
    
    /* 数据版本: VERSION */
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
    

    public _NopMetaEntity(){
        // for debug
    }

    protected NopMetaEntity newInstance(){
        NopMetaEntity entity = new NopMetaEntity();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopMetaEntity cloneInstance() {
        NopMetaEntity entity = newInstance();
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
      return "io.nop.metadata.dao.entity.NopMetaEntity";
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
    
        return buildSimpleId(PROP_ID_metaEntityId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_metaEntityId;
          
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
        
            case PROP_ID_metaEntityId:
               return getMetaEntityId();
        
            case PROP_ID_ormModelId:
               return getOrmModelId();
        
            case PROP_ID_isDelta:
               return getIsDelta();
        
            case PROP_ID_entityName:
               return getEntityName();
        
            case PROP_ID_tableName:
               return getTableName();
        
            case PROP_ID_displayName:
               return getDisplayName();
        
            case PROP_ID_className:
               return getClassName();
        
            case PROP_ID_tagSet:
               return getTagSet();
        
            case PROP_ID_querySpace:
               return getQuerySpace();
        
            case PROP_ID_persistDriver:
               return getPersistDriver();
        
            case PROP_ID_useTenant:
               return getUseTenant();
        
            case PROP_ID_useRevision:
               return getUseRevision();
        
            case PROP_ID_useLogicalDelete:
               return getUseLogicalDelete();
        
            case PROP_ID_notGenCode:
               return getNotGenCode();
        
            case PROP_ID_createrProp:
               return getCreaterProp();
        
            case PROP_ID_createTimeProp:
               return getCreateTimeProp();
        
            case PROP_ID_updaterProp:
               return getUpdaterProp();
        
            case PROP_ID_updateTimeProp:
               return getUpdateTimeProp();
        
            case PROP_ID_versionProp:
               return getVersionProp();
        
            case PROP_ID_delFlagProp:
               return getDelFlagProp();
        
            case PROP_ID_delVersionProp:
               return getDelVersionProp();
        
            case PROP_ID_dbCatalog:
               return getDbCatalog();
        
            case PROP_ID_dbSchema:
               return getDbSchema();
        
            case PROP_ID_businessDomainId:
               return getBusinessDomainId();
        
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
        
            case PROP_ID_metaEntityId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_metaEntityId));
               }
               setMetaEntityId(typedValue);
               break;
            }
        
            case PROP_ID_ormModelId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_ormModelId));
               }
               setOrmModelId(typedValue);
               break;
            }
        
            case PROP_ID_isDelta:{
               java.lang.Byte typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toByte(value,
                       err-> newTypeConversionError(PROP_NAME_isDelta));
               }
               setIsDelta(typedValue);
               break;
            }
        
            case PROP_ID_entityName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_entityName));
               }
               setEntityName(typedValue);
               break;
            }
        
            case PROP_ID_tableName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_tableName));
               }
               setTableName(typedValue);
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
        
            case PROP_ID_className:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_className));
               }
               setClassName(typedValue);
               break;
            }
        
            case PROP_ID_tagSet:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_tagSet));
               }
               setTagSet(typedValue);
               break;
            }
        
            case PROP_ID_querySpace:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_querySpace));
               }
               setQuerySpace(typedValue);
               break;
            }
        
            case PROP_ID_persistDriver:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_persistDriver));
               }
               setPersistDriver(typedValue);
               break;
            }
        
            case PROP_ID_useTenant:{
               java.lang.Byte typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toByte(value,
                       err-> newTypeConversionError(PROP_NAME_useTenant));
               }
               setUseTenant(typedValue);
               break;
            }
        
            case PROP_ID_useRevision:{
               java.lang.Byte typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toByte(value,
                       err-> newTypeConversionError(PROP_NAME_useRevision));
               }
               setUseRevision(typedValue);
               break;
            }
        
            case PROP_ID_useLogicalDelete:{
               java.lang.Byte typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toByte(value,
                       err-> newTypeConversionError(PROP_NAME_useLogicalDelete));
               }
               setUseLogicalDelete(typedValue);
               break;
            }
        
            case PROP_ID_notGenCode:{
               java.lang.Byte typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toByte(value,
                       err-> newTypeConversionError(PROP_NAME_notGenCode));
               }
               setNotGenCode(typedValue);
               break;
            }
        
            case PROP_ID_createrProp:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_createrProp));
               }
               setCreaterProp(typedValue);
               break;
            }
        
            case PROP_ID_createTimeProp:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_createTimeProp));
               }
               setCreateTimeProp(typedValue);
               break;
            }
        
            case PROP_ID_updaterProp:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_updaterProp));
               }
               setUpdaterProp(typedValue);
               break;
            }
        
            case PROP_ID_updateTimeProp:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_updateTimeProp));
               }
               setUpdateTimeProp(typedValue);
               break;
            }
        
            case PROP_ID_versionProp:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_versionProp));
               }
               setVersionProp(typedValue);
               break;
            }
        
            case PROP_ID_delFlagProp:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_delFlagProp));
               }
               setDelFlagProp(typedValue);
               break;
            }
        
            case PROP_ID_delVersionProp:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_delVersionProp));
               }
               setDelVersionProp(typedValue);
               break;
            }
        
            case PROP_ID_dbCatalog:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_dbCatalog));
               }
               setDbCatalog(typedValue);
               break;
            }
        
            case PROP_ID_dbSchema:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_dbSchema));
               }
               setDbSchema(typedValue);
               break;
            }
        
            case PROP_ID_businessDomainId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_businessDomainId));
               }
               setBusinessDomainId(typedValue);
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
        
            case PROP_ID_metaEntityId:{
               onInitProp(propId);
               this._metaEntityId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_ormModelId:{
               onInitProp(propId);
               this._ormModelId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_isDelta:{
               onInitProp(propId);
               this._isDelta = (java.lang.Byte)value;
               
               break;
            }
        
            case PROP_ID_entityName:{
               onInitProp(propId);
               this._entityName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_tableName:{
               onInitProp(propId);
               this._tableName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_displayName:{
               onInitProp(propId);
               this._displayName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_className:{
               onInitProp(propId);
               this._className = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_tagSet:{
               onInitProp(propId);
               this._tagSet = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_querySpace:{
               onInitProp(propId);
               this._querySpace = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_persistDriver:{
               onInitProp(propId);
               this._persistDriver = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_useTenant:{
               onInitProp(propId);
               this._useTenant = (java.lang.Byte)value;
               
               break;
            }
        
            case PROP_ID_useRevision:{
               onInitProp(propId);
               this._useRevision = (java.lang.Byte)value;
               
               break;
            }
        
            case PROP_ID_useLogicalDelete:{
               onInitProp(propId);
               this._useLogicalDelete = (java.lang.Byte)value;
               
               break;
            }
        
            case PROP_ID_notGenCode:{
               onInitProp(propId);
               this._notGenCode = (java.lang.Byte)value;
               
               break;
            }
        
            case PROP_ID_createrProp:{
               onInitProp(propId);
               this._createrProp = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_createTimeProp:{
               onInitProp(propId);
               this._createTimeProp = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_updaterProp:{
               onInitProp(propId);
               this._updaterProp = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_updateTimeProp:{
               onInitProp(propId);
               this._updateTimeProp = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_versionProp:{
               onInitProp(propId);
               this._versionProp = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_delFlagProp:{
               onInitProp(propId);
               this._delFlagProp = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_delVersionProp:{
               onInitProp(propId);
               this._delVersionProp = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_dbCatalog:{
               onInitProp(propId);
               this._dbCatalog = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_dbSchema:{
               onInitProp(propId);
               this._dbSchema = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_businessDomainId:{
               onInitProp(propId);
               this._businessDomainId = (java.lang.String)value;
               
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
     * 实体ID: META_ENTITY_ID
     */
    public final java.lang.String getMetaEntityId(){
         onPropGet(PROP_ID_metaEntityId);
         return _metaEntityId;
    }

    /**
     * 实体ID: META_ENTITY_ID
     */
    public final void setMetaEntityId(java.lang.String value){
        if(onPropSet(PROP_ID_metaEntityId,value)){
            this._metaEntityId = value;
            internalClearRefs(PROP_ID_metaEntityId);
            orm_id();
        }
    }
    
    /**
     * 模型ID: ORM_MODEL_ID
     */
    public final java.lang.String getOrmModelId(){
         onPropGet(PROP_ID_ormModelId);
         return _ormModelId;
    }

    /**
     * 模型ID: ORM_MODEL_ID
     */
    public final void setOrmModelId(java.lang.String value){
        if(onPropSet(PROP_ID_ormModelId,value)){
            this._ormModelId = value;
            internalClearRefs(PROP_ID_ormModelId);
            
        }
    }
    
    /**
     * 是否Delta: IS_DELTA
     */
    public final java.lang.Byte getIsDelta(){
         onPropGet(PROP_ID_isDelta);
         return _isDelta;
    }

    /**
     * 是否Delta: IS_DELTA
     */
    public final void setIsDelta(java.lang.Byte value){
        if(onPropSet(PROP_ID_isDelta,value)){
            this._isDelta = value;
            internalClearRefs(PROP_ID_isDelta);
            
        }
    }
    
    /**
     * 实体名: ENTITY_NAME
     */
    public final java.lang.String getEntityName(){
         onPropGet(PROP_ID_entityName);
         return _entityName;
    }

    /**
     * 实体名: ENTITY_NAME
     */
    public final void setEntityName(java.lang.String value){
        if(onPropSet(PROP_ID_entityName,value)){
            this._entityName = value;
            internalClearRefs(PROP_ID_entityName);
            
        }
    }
    
    /**
     * 表名: TABLE_NAME
     */
    public final java.lang.String getTableName(){
         onPropGet(PROP_ID_tableName);
         return _tableName;
    }

    /**
     * 表名: TABLE_NAME
     */
    public final void setTableName(java.lang.String value){
        if(onPropSet(PROP_ID_tableName,value)){
            this._tableName = value;
            internalClearRefs(PROP_ID_tableName);
            
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
     * 类名: CLASS_NAME
     */
    public final java.lang.String getClassName(){
         onPropGet(PROP_ID_className);
         return _className;
    }

    /**
     * 类名: CLASS_NAME
     */
    public final void setClassName(java.lang.String value){
        if(onPropSet(PROP_ID_className,value)){
            this._className = value;
            internalClearRefs(PROP_ID_className);
            
        }
    }
    
    /**
     * 标签集: TAG_SET
     */
    public final java.lang.String getTagSet(){
         onPropGet(PROP_ID_tagSet);
         return _tagSet;
    }

    /**
     * 标签集: TAG_SET
     */
    public final void setTagSet(java.lang.String value){
        if(onPropSet(PROP_ID_tagSet,value)){
            this._tagSet = value;
            internalClearRefs(PROP_ID_tagSet);
            
        }
    }
    
    /**
     * 查询空间: QUERY_SPACE
     */
    public final java.lang.String getQuerySpace(){
         onPropGet(PROP_ID_querySpace);
         return _querySpace;
    }

    /**
     * 查询空间: QUERY_SPACE
     */
    public final void setQuerySpace(java.lang.String value){
        if(onPropSet(PROP_ID_querySpace,value)){
            this._querySpace = value;
            internalClearRefs(PROP_ID_querySpace);
            
        }
    }
    
    /**
     * 持久化驱动: PERSIST_DRIVER
     */
    public final java.lang.String getPersistDriver(){
         onPropGet(PROP_ID_persistDriver);
         return _persistDriver;
    }

    /**
     * 持久化驱动: PERSIST_DRIVER
     */
    public final void setPersistDriver(java.lang.String value){
        if(onPropSet(PROP_ID_persistDriver,value)){
            this._persistDriver = value;
            internalClearRefs(PROP_ID_persistDriver);
            
        }
    }
    
    /**
     * 使用租户: USE_TENANT
     */
    public final java.lang.Byte getUseTenant(){
         onPropGet(PROP_ID_useTenant);
         return _useTenant;
    }

    /**
     * 使用租户: USE_TENANT
     */
    public final void setUseTenant(java.lang.Byte value){
        if(onPropSet(PROP_ID_useTenant,value)){
            this._useTenant = value;
            internalClearRefs(PROP_ID_useTenant);
            
        }
    }
    
    /**
     * 使用版本: USE_REVISION
     */
    public final java.lang.Byte getUseRevision(){
         onPropGet(PROP_ID_useRevision);
         return _useRevision;
    }

    /**
     * 使用版本: USE_REVISION
     */
    public final void setUseRevision(java.lang.Byte value){
        if(onPropSet(PROP_ID_useRevision,value)){
            this._useRevision = value;
            internalClearRefs(PROP_ID_useRevision);
            
        }
    }
    
    /**
     * 逻辑删除: USE_LOGICAL_DELETE
     */
    public final java.lang.Byte getUseLogicalDelete(){
         onPropGet(PROP_ID_useLogicalDelete);
         return _useLogicalDelete;
    }

    /**
     * 逻辑删除: USE_LOGICAL_DELETE
     */
    public final void setUseLogicalDelete(java.lang.Byte value){
        if(onPropSet(PROP_ID_useLogicalDelete,value)){
            this._useLogicalDelete = value;
            internalClearRefs(PROP_ID_useLogicalDelete);
            
        }
    }
    
    /**
     * 不生成代码: NOT_GEN_CODE
     */
    public final java.lang.Byte getNotGenCode(){
         onPropGet(PROP_ID_notGenCode);
         return _notGenCode;
    }

    /**
     * 不生成代码: NOT_GEN_CODE
     */
    public final void setNotGenCode(java.lang.Byte value){
        if(onPropSet(PROP_ID_notGenCode,value)){
            this._notGenCode = value;
            internalClearRefs(PROP_ID_notGenCode);
            
        }
    }
    
    /**
     * 创建人属性: CREATER_PROP
     */
    public final java.lang.String getCreaterProp(){
         onPropGet(PROP_ID_createrProp);
         return _createrProp;
    }

    /**
     * 创建人属性: CREATER_PROP
     */
    public final void setCreaterProp(java.lang.String value){
        if(onPropSet(PROP_ID_createrProp,value)){
            this._createrProp = value;
            internalClearRefs(PROP_ID_createrProp);
            
        }
    }
    
    /**
     * 创建时间属性: CREATE_TIME_PROP
     */
    public final java.lang.String getCreateTimeProp(){
         onPropGet(PROP_ID_createTimeProp);
         return _createTimeProp;
    }

    /**
     * 创建时间属性: CREATE_TIME_PROP
     */
    public final void setCreateTimeProp(java.lang.String value){
        if(onPropSet(PROP_ID_createTimeProp,value)){
            this._createTimeProp = value;
            internalClearRefs(PROP_ID_createTimeProp);
            
        }
    }
    
    /**
     * 修改人属性: UPDATER_PROP
     */
    public final java.lang.String getUpdaterProp(){
         onPropGet(PROP_ID_updaterProp);
         return _updaterProp;
    }

    /**
     * 修改人属性: UPDATER_PROP
     */
    public final void setUpdaterProp(java.lang.String value){
        if(onPropSet(PROP_ID_updaterProp,value)){
            this._updaterProp = value;
            internalClearRefs(PROP_ID_updaterProp);
            
        }
    }
    
    /**
     * 修改时间属性: UPDATE_TIME_PROP
     */
    public final java.lang.String getUpdateTimeProp(){
         onPropGet(PROP_ID_updateTimeProp);
         return _updateTimeProp;
    }

    /**
     * 修改时间属性: UPDATE_TIME_PROP
     */
    public final void setUpdateTimeProp(java.lang.String value){
        if(onPropSet(PROP_ID_updateTimeProp,value)){
            this._updateTimeProp = value;
            internalClearRefs(PROP_ID_updateTimeProp);
            
        }
    }
    
    /**
     * 版本属性: VERSION_PROP
     */
    public final java.lang.String getVersionProp(){
         onPropGet(PROP_ID_versionProp);
         return _versionProp;
    }

    /**
     * 版本属性: VERSION_PROP
     */
    public final void setVersionProp(java.lang.String value){
        if(onPropSet(PROP_ID_versionProp,value)){
            this._versionProp = value;
            internalClearRefs(PROP_ID_versionProp);
            
        }
    }
    
    /**
     * 删除标记属性: DEL_FLAG_PROP
     */
    public final java.lang.String getDelFlagProp(){
         onPropGet(PROP_ID_delFlagProp);
         return _delFlagProp;
    }

    /**
     * 删除标记属性: DEL_FLAG_PROP
     */
    public final void setDelFlagProp(java.lang.String value){
        if(onPropSet(PROP_ID_delFlagProp,value)){
            this._delFlagProp = value;
            internalClearRefs(PROP_ID_delFlagProp);
            
        }
    }
    
    /**
     * 删除版本属性: DEL_VERSION_PROP
     */
    public final java.lang.String getDelVersionProp(){
         onPropGet(PROP_ID_delVersionProp);
         return _delVersionProp;
    }

    /**
     * 删除版本属性: DEL_VERSION_PROP
     */
    public final void setDelVersionProp(java.lang.String value){
        if(onPropSet(PROP_ID_delVersionProp,value)){
            this._delVersionProp = value;
            internalClearRefs(PROP_ID_delVersionProp);
            
        }
    }
    
    /**
     * 数据库目录: DB_CATALOG
     */
    public final java.lang.String getDbCatalog(){
         onPropGet(PROP_ID_dbCatalog);
         return _dbCatalog;
    }

    /**
     * 数据库目录: DB_CATALOG
     */
    public final void setDbCatalog(java.lang.String value){
        if(onPropSet(PROP_ID_dbCatalog,value)){
            this._dbCatalog = value;
            internalClearRefs(PROP_ID_dbCatalog);
            
        }
    }
    
    /**
     * 数据库Schema: DB_SCHEMA
     */
    public final java.lang.String getDbSchema(){
         onPropGet(PROP_ID_dbSchema);
         return _dbSchema;
    }

    /**
     * 数据库Schema: DB_SCHEMA
     */
    public final void setDbSchema(java.lang.String value){
        if(onPropSet(PROP_ID_dbSchema,value)){
            this._dbSchema = value;
            internalClearRefs(PROP_ID_dbSchema);
            
        }
    }
    
    /**
     * 业务域ID: BUSINESS_DOMAIN_ID
     */
    public final java.lang.String getBusinessDomainId(){
         onPropGet(PROP_ID_businessDomainId);
         return _businessDomainId;
    }

    /**
     * 业务域ID: BUSINESS_DOMAIN_ID
     */
    public final void setBusinessDomainId(java.lang.String value){
        if(onPropSet(PROP_ID_businessDomainId,value)){
            this._businessDomainId = value;
            internalClearRefs(PROP_ID_businessDomainId);
            
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
     * 数据版本: VERSION
     */
    public final java.lang.Long getVersion(){
         onPropGet(PROP_ID_version);
         return _version;
    }

    /**
     * 数据版本: VERSION
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
     * ORM模型
     */
    public final io.nop.metadata.dao.entity.NopMetaOrmModel getOrmModel(){
       return (io.nop.metadata.dao.entity.NopMetaOrmModel)internalGetRefEntity(PROP_NAME_ormModel);
    }

    public final void setOrmModel(io.nop.metadata.dao.entity.NopMetaOrmModel refEntity){
   
           if(refEntity == null){
           
                   this.setOrmModelId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_ormModel, refEntity,()->{
           
                           this.setOrmModelId(refEntity.getOrmModelId());
                       
           });
           }
       
    }
       
    private final OrmEntitySet<io.nop.metadata.dao.entity.NopMetaEntityField> _entityFields = new OrmEntitySet<>(this, PROP_NAME_entityFields,
        io.nop.metadata.dao.entity.NopMetaEntityField.PROP_NAME_metaEntity, null,io.nop.metadata.dao.entity.NopMetaEntityField.class);

    /**
     * 实体字段集。 refPropName: metaEntity, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<io.nop.metadata.dao.entity.NopMetaEntityField> getEntityFields(){
       return _entityFields;
    }
       
    private final OrmEntitySet<io.nop.metadata.dao.entity.NopMetaEntityRelation> _entityRelations = new OrmEntitySet<>(this, PROP_NAME_entityRelations,
        io.nop.metadata.dao.entity.NopMetaEntityRelation.PROP_NAME_metaEntity, null,io.nop.metadata.dao.entity.NopMetaEntityRelation.class);

    /**
     * 实体关系集。 refPropName: metaEntity, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<io.nop.metadata.dao.entity.NopMetaEntityRelation> getEntityRelations(){
       return _entityRelations;
    }
       
    private final OrmEntitySet<io.nop.metadata.dao.entity.NopMetaEntityUniqueKey> _entityUniqueKeys = new OrmEntitySet<>(this, PROP_NAME_entityUniqueKeys,
        io.nop.metadata.dao.entity.NopMetaEntityUniqueKey.PROP_NAME_metaEntity, null,io.nop.metadata.dao.entity.NopMetaEntityUniqueKey.class);

    /**
     * 实体唯一键集。 refPropName: metaEntity, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<io.nop.metadata.dao.entity.NopMetaEntityUniqueKey> getEntityUniqueKeys(){
       return _entityUniqueKeys;
    }
       
    private final OrmEntitySet<io.nop.metadata.dao.entity.NopMetaEntityIndex> _entityIndexes = new OrmEntitySet<>(this, PROP_NAME_entityIndexes,
        io.nop.metadata.dao.entity.NopMetaEntityIndex.PROP_NAME_metaEntity, null,io.nop.metadata.dao.entity.NopMetaEntityIndex.class);

    /**
     * 实体索引集。 refPropName: metaEntity, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<io.nop.metadata.dao.entity.NopMetaEntityIndex> getEntityIndexes(){
       return _entityIndexes;
    }
       
    private final OrmEntitySet<io.nop.metadata.dao.entity.NopMetaTableJoin> _joinAsLeft = new OrmEntitySet<>(this, PROP_NAME_joinAsLeft,
        io.nop.metadata.dao.entity.NopMetaTableJoin.PROP_NAME_leftEntity, null,io.nop.metadata.dao.entity.NopMetaTableJoin.class);

    /**
     * 作为左实体的关联集。 refPropName: leftEntity, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<io.nop.metadata.dao.entity.NopMetaTableJoin> getJoinAsLeft(){
       return _joinAsLeft;
    }
       
    private final OrmEntitySet<io.nop.metadata.dao.entity.NopMetaTableJoin> _joinAsRight = new OrmEntitySet<>(this, PROP_NAME_joinAsRight,
        io.nop.metadata.dao.entity.NopMetaTableJoin.PROP_NAME_rightEntity, null,io.nop.metadata.dao.entity.NopMetaTableJoin.class);

    /**
     * 作为右实体的关联集。 refPropName: rightEntity, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<io.nop.metadata.dao.entity.NopMetaTableJoin> getJoinAsRight(){
       return _joinAsRight;
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
