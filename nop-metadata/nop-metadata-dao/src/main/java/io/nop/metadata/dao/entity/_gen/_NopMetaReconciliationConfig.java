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

import io.nop.metadata.dao.entity.NopMetaReconciliationConfig;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  对账配置: nop_meta_recon_config
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopMetaReconciliationConfig extends DynamicOrmEntity{
    
    /* 配置ID: CONFIG_ID VARCHAR */
    public static final String PROP_NAME_configId = "configId";
    public static final int PROP_ID_configId = 1;
    
    /* 配置名: CONFIG_NAME VARCHAR */
    public static final String PROP_NAME_configName = "configName";
    public static final int PROP_ID_configName = 2;
    
    /* 显示名: DISPLAY_NAME VARCHAR */
    public static final String PROP_NAME_displayName = "displayName";
    public static final int PROP_ID_displayName = 3;
    
    /* 模块版本ID: META_MODULE_ID VARCHAR */
    public static final String PROP_NAME_metaModuleId = "metaModuleId";
    public static final int PROP_ID_metaModuleId = 4;
    
    /* 逻辑表ID: META_TABLE_ID VARCHAR */
    public static final String PROP_NAME_metaTableId = "metaTableId";
    public static final int PROP_ID_metaTableId = 5;
    
    /* 待对账列名: COLUMN_NAME VARCHAR */
    public static final String PROP_NAME_columnName = "columnName";
    public static final int PROP_ID_columnName = 6;
    
    /* 标识符空间: IDENTIFIER_SPACE VARCHAR */
    public static final String PROP_NAME_identifierSpace = "identifierSpace";
    public static final int PROP_ID_identifierSpace = 7;
    
    /* 目标实体类型: TARGET_ENTITY_TYPE VARCHAR */
    public static final String PROP_NAME_targetEntityType = "targetEntityType";
    public static final int PROP_ID_targetEntityType = 8;
    
    /* 匹配策略: MATCH_STRATEGY VARCHAR */
    public static final String PROP_NAME_matchStrategy = "matchStrategy";
    public static final int PROP_ID_matchStrategy = 9;
    
    /* 是否自动匹配: AUTO_MATCH TINYINT */
    public static final String PROP_NAME_autoMatch = "autoMatch";
    public static final int PROP_ID_autoMatch = 10;
    
    /* 自动匹配阈值: AUTO_MATCH_THRESHOLD DOUBLE */
    public static final String PROP_NAME_autoMatchThreshold = "autoMatchThreshold";
    public static final int PROP_ID_autoMatchThreshold = 11;
    
    /* 扩展配置: EXT_CONFIG VARCHAR */
    public static final String PROP_NAME_extConfig = "extConfig";
    public static final int PROP_ID_extConfig = 12;
    
    /* 数据版本: DEL_VERSION BIGINT */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 13;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 14;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 15;
    
    /* 修改人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 16;
    
    /* 修改时间: UPDATE_TIME TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 17;
    
    /* 备注: REMARK VARCHAR */
    public static final String PROP_NAME_remark = "remark";
    public static final int PROP_ID_remark = 18;
    

    private static int _PROP_ID_BOUND = 19;

    
    /* relation: 逻辑表 */
    public static final String PROP_NAME_metaTable = "metaTable";
    
    /* relation: 元数据模块 */
    public static final String PROP_NAME_metaModule = "metaModule";
    
    /* component:  */
    public static final String PROP_NAME_extConfigComponent = "extConfigComponent";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_configId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_configId};

    private static final String[] PROP_ID_TO_NAME = new String[19];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_configId] = PROP_NAME_configId;
          PROP_NAME_TO_ID.put(PROP_NAME_configId, PROP_ID_configId);
      
          PROP_ID_TO_NAME[PROP_ID_configName] = PROP_NAME_configName;
          PROP_NAME_TO_ID.put(PROP_NAME_configName, PROP_ID_configName);
      
          PROP_ID_TO_NAME[PROP_ID_displayName] = PROP_NAME_displayName;
          PROP_NAME_TO_ID.put(PROP_NAME_displayName, PROP_ID_displayName);
      
          PROP_ID_TO_NAME[PROP_ID_metaModuleId] = PROP_NAME_metaModuleId;
          PROP_NAME_TO_ID.put(PROP_NAME_metaModuleId, PROP_ID_metaModuleId);
      
          PROP_ID_TO_NAME[PROP_ID_metaTableId] = PROP_NAME_metaTableId;
          PROP_NAME_TO_ID.put(PROP_NAME_metaTableId, PROP_ID_metaTableId);
      
          PROP_ID_TO_NAME[PROP_ID_columnName] = PROP_NAME_columnName;
          PROP_NAME_TO_ID.put(PROP_NAME_columnName, PROP_ID_columnName);
      
          PROP_ID_TO_NAME[PROP_ID_identifierSpace] = PROP_NAME_identifierSpace;
          PROP_NAME_TO_ID.put(PROP_NAME_identifierSpace, PROP_ID_identifierSpace);
      
          PROP_ID_TO_NAME[PROP_ID_targetEntityType] = PROP_NAME_targetEntityType;
          PROP_NAME_TO_ID.put(PROP_NAME_targetEntityType, PROP_ID_targetEntityType);
      
          PROP_ID_TO_NAME[PROP_ID_matchStrategy] = PROP_NAME_matchStrategy;
          PROP_NAME_TO_ID.put(PROP_NAME_matchStrategy, PROP_ID_matchStrategy);
      
          PROP_ID_TO_NAME[PROP_ID_autoMatch] = PROP_NAME_autoMatch;
          PROP_NAME_TO_ID.put(PROP_NAME_autoMatch, PROP_ID_autoMatch);
      
          PROP_ID_TO_NAME[PROP_ID_autoMatchThreshold] = PROP_NAME_autoMatchThreshold;
          PROP_NAME_TO_ID.put(PROP_NAME_autoMatchThreshold, PROP_ID_autoMatchThreshold);
      
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

    
    /* 配置ID: CONFIG_ID */
    private java.lang.String _configId;
    
    /* 配置名: CONFIG_NAME */
    private java.lang.String _configName;
    
    /* 显示名: DISPLAY_NAME */
    private java.lang.String _displayName;
    
    /* 模块版本ID: META_MODULE_ID */
    private java.lang.String _metaModuleId;
    
    /* 逻辑表ID: META_TABLE_ID */
    private java.lang.String _metaTableId;
    
    /* 待对账列名: COLUMN_NAME */
    private java.lang.String _columnName;
    
    /* 标识符空间: IDENTIFIER_SPACE */
    private java.lang.String _identifierSpace;
    
    /* 目标实体类型: TARGET_ENTITY_TYPE */
    private java.lang.String _targetEntityType;
    
    /* 匹配策略: MATCH_STRATEGY */
    private java.lang.String _matchStrategy;
    
    /* 是否自动匹配: AUTO_MATCH */
    private java.lang.Byte _autoMatch;
    
    /* 自动匹配阈值: AUTO_MATCH_THRESHOLD */
    private java.lang.Double _autoMatchThreshold;
    
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
    

    public _NopMetaReconciliationConfig(){
        // for debug
    }

    protected NopMetaReconciliationConfig newInstance(){
        NopMetaReconciliationConfig entity = new NopMetaReconciliationConfig();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopMetaReconciliationConfig cloneInstance() {
        NopMetaReconciliationConfig entity = newInstance();
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
      return "io.nop.metadata.dao.entity.NopMetaReconciliationConfig";
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
    
        return buildSimpleId(PROP_ID_configId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_configId;
          
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
        
            case PROP_ID_configId:
               return getConfigId();
        
            case PROP_ID_configName:
               return getConfigName();
        
            case PROP_ID_displayName:
               return getDisplayName();
        
            case PROP_ID_metaModuleId:
               return getMetaModuleId();
        
            case PROP_ID_metaTableId:
               return getMetaTableId();
        
            case PROP_ID_columnName:
               return getColumnName();
        
            case PROP_ID_identifierSpace:
               return getIdentifierSpace();
        
            case PROP_ID_targetEntityType:
               return getTargetEntityType();
        
            case PROP_ID_matchStrategy:
               return getMatchStrategy();
        
            case PROP_ID_autoMatch:
               return getAutoMatch();
        
            case PROP_ID_autoMatchThreshold:
               return getAutoMatchThreshold();
        
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
        
            case PROP_ID_configId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_configId));
               }
               setConfigId(typedValue);
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
        
            case PROP_ID_displayName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_displayName));
               }
               setDisplayName(typedValue);
               break;
            }
        
            case PROP_ID_metaModuleId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_metaModuleId));
               }
               setMetaModuleId(typedValue);
               break;
            }
        
            case PROP_ID_metaTableId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_metaTableId));
               }
               setMetaTableId(typedValue);
               break;
            }
        
            case PROP_ID_columnName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_columnName));
               }
               setColumnName(typedValue);
               break;
            }
        
            case PROP_ID_identifierSpace:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_identifierSpace));
               }
               setIdentifierSpace(typedValue);
               break;
            }
        
            case PROP_ID_targetEntityType:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_targetEntityType));
               }
               setTargetEntityType(typedValue);
               break;
            }
        
            case PROP_ID_matchStrategy:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_matchStrategy));
               }
               setMatchStrategy(typedValue);
               break;
            }
        
            case PROP_ID_autoMatch:{
               java.lang.Byte typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toByte(value,
                       err-> newTypeConversionError(PROP_NAME_autoMatch));
               }
               setAutoMatch(typedValue);
               break;
            }
        
            case PROP_ID_autoMatchThreshold:{
               java.lang.Double typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toDouble(value,
                       err-> newTypeConversionError(PROP_NAME_autoMatchThreshold));
               }
               setAutoMatchThreshold(typedValue);
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
        
            case PROP_ID_configId:{
               onInitProp(propId);
               this._configId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_configName:{
               onInitProp(propId);
               this._configName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_displayName:{
               onInitProp(propId);
               this._displayName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_metaModuleId:{
               onInitProp(propId);
               this._metaModuleId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_metaTableId:{
               onInitProp(propId);
               this._metaTableId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_columnName:{
               onInitProp(propId);
               this._columnName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_identifierSpace:{
               onInitProp(propId);
               this._identifierSpace = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_targetEntityType:{
               onInitProp(propId);
               this._targetEntityType = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_matchStrategy:{
               onInitProp(propId);
               this._matchStrategy = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_autoMatch:{
               onInitProp(propId);
               this._autoMatch = (java.lang.Byte)value;
               
               break;
            }
        
            case PROP_ID_autoMatchThreshold:{
               onInitProp(propId);
               this._autoMatchThreshold = (java.lang.Double)value;
               
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
     * 配置ID: CONFIG_ID
     */
    public final java.lang.String getConfigId(){
         onPropGet(PROP_ID_configId);
         return _configId;
    }

    /**
     * 配置ID: CONFIG_ID
     */
    public final void setConfigId(java.lang.String value){
        if(onPropSet(PROP_ID_configId,value)){
            this._configId = value;
            internalClearRefs(PROP_ID_configId);
            orm_id();
        }
    }
    
    /**
     * 配置名: CONFIG_NAME
     */
    public final java.lang.String getConfigName(){
         onPropGet(PROP_ID_configName);
         return _configName;
    }

    /**
     * 配置名: CONFIG_NAME
     */
    public final void setConfigName(java.lang.String value){
        if(onPropSet(PROP_ID_configName,value)){
            this._configName = value;
            internalClearRefs(PROP_ID_configName);
            
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
            
        }
    }
    
    /**
     * 逻辑表ID: META_TABLE_ID
     */
    public final java.lang.String getMetaTableId(){
         onPropGet(PROP_ID_metaTableId);
         return _metaTableId;
    }

    /**
     * 逻辑表ID: META_TABLE_ID
     */
    public final void setMetaTableId(java.lang.String value){
        if(onPropSet(PROP_ID_metaTableId,value)){
            this._metaTableId = value;
            internalClearRefs(PROP_ID_metaTableId);
            
        }
    }
    
    /**
     * 待对账列名: COLUMN_NAME
     */
    public final java.lang.String getColumnName(){
         onPropGet(PROP_ID_columnName);
         return _columnName;
    }

    /**
     * 待对账列名: COLUMN_NAME
     */
    public final void setColumnName(java.lang.String value){
        if(onPropSet(PROP_ID_columnName,value)){
            this._columnName = value;
            internalClearRefs(PROP_ID_columnName);
            
        }
    }
    
    /**
     * 标识符空间: IDENTIFIER_SPACE
     */
    public final java.lang.String getIdentifierSpace(){
         onPropGet(PROP_ID_identifierSpace);
         return _identifierSpace;
    }

    /**
     * 标识符空间: IDENTIFIER_SPACE
     */
    public final void setIdentifierSpace(java.lang.String value){
        if(onPropSet(PROP_ID_identifierSpace,value)){
            this._identifierSpace = value;
            internalClearRefs(PROP_ID_identifierSpace);
            
        }
    }
    
    /**
     * 目标实体类型: TARGET_ENTITY_TYPE
     */
    public final java.lang.String getTargetEntityType(){
         onPropGet(PROP_ID_targetEntityType);
         return _targetEntityType;
    }

    /**
     * 目标实体类型: TARGET_ENTITY_TYPE
     */
    public final void setTargetEntityType(java.lang.String value){
        if(onPropSet(PROP_ID_targetEntityType,value)){
            this._targetEntityType = value;
            internalClearRefs(PROP_ID_targetEntityType);
            
        }
    }
    
    /**
     * 匹配策略: MATCH_STRATEGY
     */
    public final java.lang.String getMatchStrategy(){
         onPropGet(PROP_ID_matchStrategy);
         return _matchStrategy;
    }

    /**
     * 匹配策略: MATCH_STRATEGY
     */
    public final void setMatchStrategy(java.lang.String value){
        if(onPropSet(PROP_ID_matchStrategy,value)){
            this._matchStrategy = value;
            internalClearRefs(PROP_ID_matchStrategy);
            
        }
    }
    
    /**
     * 是否自动匹配: AUTO_MATCH
     */
    public final java.lang.Byte getAutoMatch(){
         onPropGet(PROP_ID_autoMatch);
         return _autoMatch;
    }

    /**
     * 是否自动匹配: AUTO_MATCH
     */
    public final void setAutoMatch(java.lang.Byte value){
        if(onPropSet(PROP_ID_autoMatch,value)){
            this._autoMatch = value;
            internalClearRefs(PROP_ID_autoMatch);
            
        }
    }
    
    /**
     * 自动匹配阈值: AUTO_MATCH_THRESHOLD
     */
    public final java.lang.Double getAutoMatchThreshold(){
         onPropGet(PROP_ID_autoMatchThreshold);
         return _autoMatchThreshold;
    }

    /**
     * 自动匹配阈值: AUTO_MATCH_THRESHOLD
     */
    public final void setAutoMatchThreshold(java.lang.Double value){
        if(onPropSet(PROP_ID_autoMatchThreshold,value)){
            this._autoMatchThreshold = value;
            internalClearRefs(PROP_ID_autoMatchThreshold);
            
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
     * 逻辑表
     */
    public final io.nop.metadata.dao.entity.NopMetaTable getMetaTable(){
       return (io.nop.metadata.dao.entity.NopMetaTable)internalGetRefEntity(PROP_NAME_metaTable);
    }

    public final void setMetaTable(io.nop.metadata.dao.entity.NopMetaTable refEntity){
   
           if(refEntity == null){
           
                   this.setMetaTableId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_metaTable, refEntity,()->{
           
                           this.setMetaTableId(refEntity.getMetaTableId());
                       
           });
           }
       
    }
       
    /**
     * 元数据模块
     */
    public final io.nop.metadata.dao.entity.NopMetaModule getMetaModule(){
       return (io.nop.metadata.dao.entity.NopMetaModule)internalGetRefEntity(PROP_NAME_metaModule);
    }

    public final void setMetaModule(io.nop.metadata.dao.entity.NopMetaModule refEntity){
   
           if(refEntity == null){
           
                   this.setMetaModuleId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_metaModule, refEntity,()->{
           
                           this.setMetaModuleId(refEntity.getMetaModuleId());
                       
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
