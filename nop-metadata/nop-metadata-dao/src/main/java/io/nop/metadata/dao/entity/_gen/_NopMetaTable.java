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

import io.nop.metadata.dao.entity.NopMetaTable;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  逻辑表: nop_meta_table
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopMetaTable extends DynamicOrmEntity{
    
    /* 逻辑表ID: META_TABLE_ID VARCHAR */
    public static final String PROP_NAME_metaTableId = "metaTableId";
    public static final int PROP_ID_metaTableId = 1;
    
    /* 模块版本ID: META_MODULE_ID VARCHAR */
    public static final String PROP_NAME_metaModuleId = "metaModuleId";
    public static final int PROP_ID_metaModuleId = 2;
    
    /* 表名: TABLE_NAME VARCHAR */
    public static final String PROP_NAME_tableName = "tableName";
    public static final int PROP_ID_tableName = 3;
    
    /* 显示名: DISPLAY_NAME VARCHAR */
    public static final String PROP_NAME_displayName = "displayName";
    public static final int PROP_ID_displayName = 4;
    
    /* 表类型: TABLE_TYPE VARCHAR */
    public static final String PROP_NAME_tableType = "tableType";
    public static final int PROP_ID_tableType = 5;
    
    /* 查询空间: QUERY_SPACE VARCHAR */
    public static final String PROP_NAME_querySpace = "querySpace";
    public static final int PROP_ID_querySpace = 6;
    
    /* 来源SQL: SOURCE_SQL VARCHAR */
    public static final String PROP_NAME_sourceSql = "sourceSql";
    public static final int PROP_ID_sourceSql = 7;
    
    /* 主要实体ID: BASE_ENTITY_ID VARCHAR */
    public static final String PROP_NAME_baseEntityId = "baseEntityId";
    public static final int PROP_ID_baseEntityId = 8;
    
    /* 描述: DESCRIPTION VARCHAR */
    public static final String PROP_NAME_description = "description";
    public static final int PROP_ID_description = 9;
    
    /* 合成SQL: BUILD_SQL VARCHAR */
    public static final String PROP_NAME_buildSql = "buildSql";
    public static final int PROP_ID_buildSql = 10;
    
    /* 数据版本: VERSION BIGINT */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 11;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 12;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 13;
    
    /* 修改人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 14;
    
    /* 修改时间: UPDATE_TIME TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 15;
    
    /* 备注: REMARK VARCHAR */
    public static final String PROP_NAME_remark = "remark";
    public static final int PROP_ID_remark = 16;
    
    /* 源schema: META_SCHEMA VARCHAR */
    public static final String PROP_NAME_metaSchema = "metaSchema";
    public static final int PROP_ID_metaSchema = 17;
    
    /* 业务域ID: BUSINESS_DOMAIN_ID VARCHAR */
    public static final String PROP_NAME_businessDomainId = "businessDomainId";
    public static final int PROP_ID_businessDomainId = 18;
    

    private static int _PROP_ID_BOUND = 19;

    
    /* relation: 元数据模块 */
    public static final String PROP_NAME_metaModule = "metaModule";
    
    /* relation: 表维度集 */
    public static final String PROP_NAME_dimensions = "dimensions";
    
    /* relation: 表指标集 */
    public static final String PROP_NAME_measures = "measures";
    
    /* relation: 表过滤器集 */
    public static final String PROP_NAME_filters = "filters";
    
    /* relation: 表关联集 */
    public static final String PROP_NAME_joins = "joins";
    
    /* relation: 作为左表的关联集 */
    public static final String PROP_NAME_joinAsLeftTable = "joinAsLeftTable";
    
    /* relation: 作为右表的关联集 */
    public static final String PROP_NAME_joinAsRightTable = "joinAsRightTable";
    
    /* relation: 作为血缘源表的边集 */
    public static final String PROP_NAME_lineageAsSource = "lineageAsSource";
    
    /* relation: 作为血缘目标表的边集 */
    public static final String PROP_NAME_lineageAsTarget = "lineageAsTarget";
    
    /* relation: 运行时统计快照集 */
    public static final String PROP_NAME_catalogs = "catalogs";
    
    /* relation: 数据剖析规则集 */
    public static final String PROP_NAME_profilingRules = "profilingRules";
    
    /* relation: 数据剖析结果集 */
    public static final String PROP_NAME_profilingResults = "profilingResults";
    
    /* relation: 质量评分集 */
    public static final String PROP_NAME_qualityScores = "qualityScores";
    
    /* relation: 数据契约集 */
    public static final String PROP_NAME_dataContracts = "dataContracts";
    
    /* relation: 对账配置集 */
    public static final String PROP_NAME_reconciliationConfigs = "reconciliationConfigs";
    
    /* relation: 对账结果集 */
    public static final String PROP_NAME_reconciliationResults = "reconciliationResults";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_metaTableId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_metaTableId};

    private static final String[] PROP_ID_TO_NAME = new String[19];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_metaTableId] = PROP_NAME_metaTableId;
          PROP_NAME_TO_ID.put(PROP_NAME_metaTableId, PROP_ID_metaTableId);
      
          PROP_ID_TO_NAME[PROP_ID_metaModuleId] = PROP_NAME_metaModuleId;
          PROP_NAME_TO_ID.put(PROP_NAME_metaModuleId, PROP_ID_metaModuleId);
      
          PROP_ID_TO_NAME[PROP_ID_tableName] = PROP_NAME_tableName;
          PROP_NAME_TO_ID.put(PROP_NAME_tableName, PROP_ID_tableName);
      
          PROP_ID_TO_NAME[PROP_ID_displayName] = PROP_NAME_displayName;
          PROP_NAME_TO_ID.put(PROP_NAME_displayName, PROP_ID_displayName);
      
          PROP_ID_TO_NAME[PROP_ID_tableType] = PROP_NAME_tableType;
          PROP_NAME_TO_ID.put(PROP_NAME_tableType, PROP_ID_tableType);
      
          PROP_ID_TO_NAME[PROP_ID_querySpace] = PROP_NAME_querySpace;
          PROP_NAME_TO_ID.put(PROP_NAME_querySpace, PROP_ID_querySpace);
      
          PROP_ID_TO_NAME[PROP_ID_sourceSql] = PROP_NAME_sourceSql;
          PROP_NAME_TO_ID.put(PROP_NAME_sourceSql, PROP_ID_sourceSql);
      
          PROP_ID_TO_NAME[PROP_ID_baseEntityId] = PROP_NAME_baseEntityId;
          PROP_NAME_TO_ID.put(PROP_NAME_baseEntityId, PROP_ID_baseEntityId);
      
          PROP_ID_TO_NAME[PROP_ID_description] = PROP_NAME_description;
          PROP_NAME_TO_ID.put(PROP_NAME_description, PROP_ID_description);
      
          PROP_ID_TO_NAME[PROP_ID_buildSql] = PROP_NAME_buildSql;
          PROP_NAME_TO_ID.put(PROP_NAME_buildSql, PROP_ID_buildSql);
      
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
      
          PROP_ID_TO_NAME[PROP_ID_metaSchema] = PROP_NAME_metaSchema;
          PROP_NAME_TO_ID.put(PROP_NAME_metaSchema, PROP_ID_metaSchema);
      
          PROP_ID_TO_NAME[PROP_ID_businessDomainId] = PROP_NAME_businessDomainId;
          PROP_NAME_TO_ID.put(PROP_NAME_businessDomainId, PROP_ID_businessDomainId);
      
    }

    
    /* 逻辑表ID: META_TABLE_ID */
    private java.lang.String _metaTableId;
    
    /* 模块版本ID: META_MODULE_ID */
    private java.lang.String _metaModuleId;
    
    /* 表名: TABLE_NAME */
    private java.lang.String _tableName;
    
    /* 显示名: DISPLAY_NAME */
    private java.lang.String _displayName;
    
    /* 表类型: TABLE_TYPE */
    private java.lang.String _tableType;
    
    /* 查询空间: QUERY_SPACE */
    private java.lang.String _querySpace;
    
    /* 来源SQL: SOURCE_SQL */
    private java.lang.String _sourceSql;
    
    /* 主要实体ID: BASE_ENTITY_ID */
    private java.lang.String _baseEntityId;
    
    /* 描述: DESCRIPTION */
    private java.lang.String _description;
    
    /* 合成SQL: BUILD_SQL */
    private java.lang.String _buildSql;
    
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
    
    /* 源schema: META_SCHEMA */
    private java.lang.String _metaSchema;
    
    /* 业务域ID: BUSINESS_DOMAIN_ID */
    private java.lang.String _businessDomainId;
    

    public _NopMetaTable(){
        // for debug
    }

    protected NopMetaTable newInstance(){
        NopMetaTable entity = new NopMetaTable();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopMetaTable cloneInstance() {
        NopMetaTable entity = newInstance();
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
      return "io.nop.metadata.dao.entity.NopMetaTable";
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
    
        return buildSimpleId(PROP_ID_metaTableId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_metaTableId;
          
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
        
            case PROP_ID_metaTableId:
               return getMetaTableId();
        
            case PROP_ID_metaModuleId:
               return getMetaModuleId();
        
            case PROP_ID_tableName:
               return getTableName();
        
            case PROP_ID_displayName:
               return getDisplayName();
        
            case PROP_ID_tableType:
               return getTableType();
        
            case PROP_ID_querySpace:
               return getQuerySpace();
        
            case PROP_ID_sourceSql:
               return getSourceSql();
        
            case PROP_ID_baseEntityId:
               return getBaseEntityId();
        
            case PROP_ID_description:
               return getDescription();
        
            case PROP_ID_buildSql:
               return getBuildSql();
        
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
        
            case PROP_ID_metaSchema:
               return getMetaSchema();
        
            case PROP_ID_businessDomainId:
               return getBusinessDomainId();
        
           default:
              return super.orm_propValue(propId);
        }
    }

    

    @Override
    public void orm_propValue(int propId, Object value){
        switch(propId){
        
            case PROP_ID_metaTableId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_metaTableId));
               }
               setMetaTableId(typedValue);
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
        
            case PROP_ID_tableType:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_tableType));
               }
               setTableType(typedValue);
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
        
            case PROP_ID_sourceSql:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_sourceSql));
               }
               setSourceSql(typedValue);
               break;
            }
        
            case PROP_ID_baseEntityId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_baseEntityId));
               }
               setBaseEntityId(typedValue);
               break;
            }
        
            case PROP_ID_description:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_description));
               }
               setDescription(typedValue);
               break;
            }
        
            case PROP_ID_buildSql:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_buildSql));
               }
               setBuildSql(typedValue);
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
        
            case PROP_ID_metaSchema:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_metaSchema));
               }
               setMetaSchema(typedValue);
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
        
           default:
              super.orm_propValue(propId,value);
        }
    }

    @Override
    public void orm_internalSet(int propId, Object value) {
        switch(propId){
        
            case PROP_ID_metaTableId:{
               onInitProp(propId);
               this._metaTableId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_metaModuleId:{
               onInitProp(propId);
               this._metaModuleId = (java.lang.String)value;
               
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
        
            case PROP_ID_tableType:{
               onInitProp(propId);
               this._tableType = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_querySpace:{
               onInitProp(propId);
               this._querySpace = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_sourceSql:{
               onInitProp(propId);
               this._sourceSql = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_baseEntityId:{
               onInitProp(propId);
               this._baseEntityId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_description:{
               onInitProp(propId);
               this._description = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_buildSql:{
               onInitProp(propId);
               this._buildSql = (java.lang.String)value;
               
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
        
            case PROP_ID_metaSchema:{
               onInitProp(propId);
               this._metaSchema = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_businessDomainId:{
               onInitProp(propId);
               this._businessDomainId = (java.lang.String)value;
               
               break;
            }
        
           default:
              super.orm_internalSet(propId,value);
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
            orm_id();
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
     * 表类型: TABLE_TYPE
     */
    public final java.lang.String getTableType(){
         onPropGet(PROP_ID_tableType);
         return _tableType;
    }

    /**
     * 表类型: TABLE_TYPE
     */
    public final void setTableType(java.lang.String value){
        if(onPropSet(PROP_ID_tableType,value)){
            this._tableType = value;
            internalClearRefs(PROP_ID_tableType);
            
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
     * 来源SQL: SOURCE_SQL
     */
    public final java.lang.String getSourceSql(){
         onPropGet(PROP_ID_sourceSql);
         return _sourceSql;
    }

    /**
     * 来源SQL: SOURCE_SQL
     */
    public final void setSourceSql(java.lang.String value){
        if(onPropSet(PROP_ID_sourceSql,value)){
            this._sourceSql = value;
            internalClearRefs(PROP_ID_sourceSql);
            
        }
    }
    
    /**
     * 主要实体ID: BASE_ENTITY_ID
     */
    public final java.lang.String getBaseEntityId(){
         onPropGet(PROP_ID_baseEntityId);
         return _baseEntityId;
    }

    /**
     * 主要实体ID: BASE_ENTITY_ID
     */
    public final void setBaseEntityId(java.lang.String value){
        if(onPropSet(PROP_ID_baseEntityId,value)){
            this._baseEntityId = value;
            internalClearRefs(PROP_ID_baseEntityId);
            
        }
    }
    
    /**
     * 描述: DESCRIPTION
     */
    public final java.lang.String getDescription(){
         onPropGet(PROP_ID_description);
         return _description;
    }

    /**
     * 描述: DESCRIPTION
     */
    public final void setDescription(java.lang.String value){
        if(onPropSet(PROP_ID_description,value)){
            this._description = value;
            internalClearRefs(PROP_ID_description);
            
        }
    }
    
    /**
     * 合成SQL: BUILD_SQL
     */
    public final java.lang.String getBuildSql(){
         onPropGet(PROP_ID_buildSql);
         return _buildSql;
    }

    /**
     * 合成SQL: BUILD_SQL
     */
    public final void setBuildSql(java.lang.String value){
        if(onPropSet(PROP_ID_buildSql,value)){
            this._buildSql = value;
            internalClearRefs(PROP_ID_buildSql);
            
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
     * 源schema: META_SCHEMA
     */
    public final java.lang.String getMetaSchema(){
         onPropGet(PROP_ID_metaSchema);
         return _metaSchema;
    }

    /**
     * 源schema: META_SCHEMA
     */
    public final void setMetaSchema(java.lang.String value){
        if(onPropSet(PROP_ID_metaSchema,value)){
            this._metaSchema = value;
            internalClearRefs(PROP_ID_metaSchema);
            
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
       
    private final OrmEntitySet<io.nop.metadata.dao.entity.NopMetaTableDimension> _dimensions = new OrmEntitySet<>(this, PROP_NAME_dimensions,
        io.nop.metadata.dao.entity.NopMetaTableDimension.PROP_NAME_metaTable, null,io.nop.metadata.dao.entity.NopMetaTableDimension.class);

    /**
     * 表维度集。 refPropName: metaTable, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<io.nop.metadata.dao.entity.NopMetaTableDimension> getDimensions(){
       return _dimensions;
    }
       
    private final OrmEntitySet<io.nop.metadata.dao.entity.NopMetaTableMeasure> _measures = new OrmEntitySet<>(this, PROP_NAME_measures,
        io.nop.metadata.dao.entity.NopMetaTableMeasure.PROP_NAME_metaTable, null,io.nop.metadata.dao.entity.NopMetaTableMeasure.class);

    /**
     * 表指标集。 refPropName: metaTable, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<io.nop.metadata.dao.entity.NopMetaTableMeasure> getMeasures(){
       return _measures;
    }
       
    private final OrmEntitySet<io.nop.metadata.dao.entity.NopMetaTableFilter> _filters = new OrmEntitySet<>(this, PROP_NAME_filters,
        io.nop.metadata.dao.entity.NopMetaTableFilter.PROP_NAME_metaTable, null,io.nop.metadata.dao.entity.NopMetaTableFilter.class);

    /**
     * 表过滤器集。 refPropName: metaTable, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<io.nop.metadata.dao.entity.NopMetaTableFilter> getFilters(){
       return _filters;
    }
       
    private final OrmEntitySet<io.nop.metadata.dao.entity.NopMetaTableJoin> _joins = new OrmEntitySet<>(this, PROP_NAME_joins,
        io.nop.metadata.dao.entity.NopMetaTableJoin.PROP_NAME_metaTable, null,io.nop.metadata.dao.entity.NopMetaTableJoin.class);

    /**
     * 表关联集。 refPropName: metaTable, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<io.nop.metadata.dao.entity.NopMetaTableJoin> getJoins(){
       return _joins;
    }
       
    private final OrmEntitySet<io.nop.metadata.dao.entity.NopMetaTableJoin> _joinAsLeftTable = new OrmEntitySet<>(this, PROP_NAME_joinAsLeftTable,
        io.nop.metadata.dao.entity.NopMetaTableJoin.PROP_NAME_leftTable, null,io.nop.metadata.dao.entity.NopMetaTableJoin.class);

    /**
     * 作为左表的关联集。 refPropName: leftTable, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<io.nop.metadata.dao.entity.NopMetaTableJoin> getJoinAsLeftTable(){
       return _joinAsLeftTable;
    }
       
    private final OrmEntitySet<io.nop.metadata.dao.entity.NopMetaTableJoin> _joinAsRightTable = new OrmEntitySet<>(this, PROP_NAME_joinAsRightTable,
        io.nop.metadata.dao.entity.NopMetaTableJoin.PROP_NAME_rightTable, null,io.nop.metadata.dao.entity.NopMetaTableJoin.class);

    /**
     * 作为右表的关联集。 refPropName: rightTable, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<io.nop.metadata.dao.entity.NopMetaTableJoin> getJoinAsRightTable(){
       return _joinAsRightTable;
    }
       
    private final OrmEntitySet<io.nop.metadata.dao.entity.NopMetaLineageEdge> _lineageAsSource = new OrmEntitySet<>(this, PROP_NAME_lineageAsSource,
        io.nop.metadata.dao.entity.NopMetaLineageEdge.PROP_NAME_sourceTable, null,io.nop.metadata.dao.entity.NopMetaLineageEdge.class);

    /**
     * 作为血缘源表的边集。 refPropName: sourceTable, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<io.nop.metadata.dao.entity.NopMetaLineageEdge> getLineageAsSource(){
       return _lineageAsSource;
    }
       
    private final OrmEntitySet<io.nop.metadata.dao.entity.NopMetaLineageEdge> _lineageAsTarget = new OrmEntitySet<>(this, PROP_NAME_lineageAsTarget,
        io.nop.metadata.dao.entity.NopMetaLineageEdge.PROP_NAME_targetTable, null,io.nop.metadata.dao.entity.NopMetaLineageEdge.class);

    /**
     * 作为血缘目标表的边集。 refPropName: targetTable, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<io.nop.metadata.dao.entity.NopMetaLineageEdge> getLineageAsTarget(){
       return _lineageAsTarget;
    }
       
    private final OrmEntitySet<io.nop.metadata.dao.entity.NopMetaCatalog> _catalogs = new OrmEntitySet<>(this, PROP_NAME_catalogs,
        io.nop.metadata.dao.entity.NopMetaCatalog.PROP_NAME_metaTable, null,io.nop.metadata.dao.entity.NopMetaCatalog.class);

    /**
     * 运行时统计快照集。 refPropName: metaTable, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<io.nop.metadata.dao.entity.NopMetaCatalog> getCatalogs(){
       return _catalogs;
    }
       
    private final OrmEntitySet<io.nop.metadata.dao.entity.NopMetaProfilingRule> _profilingRules = new OrmEntitySet<>(this, PROP_NAME_profilingRules,
        io.nop.metadata.dao.entity.NopMetaProfilingRule.PROP_NAME_metaTable, null,io.nop.metadata.dao.entity.NopMetaProfilingRule.class);

    /**
     * 数据剖析规则集。 refPropName: metaTable, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<io.nop.metadata.dao.entity.NopMetaProfilingRule> getProfilingRules(){
       return _profilingRules;
    }
       
    private final OrmEntitySet<io.nop.metadata.dao.entity.NopMetaProfilingResult> _profilingResults = new OrmEntitySet<>(this, PROP_NAME_profilingResults,
        io.nop.metadata.dao.entity.NopMetaProfilingResult.PROP_NAME_metaTable, null,io.nop.metadata.dao.entity.NopMetaProfilingResult.class);

    /**
     * 数据剖析结果集。 refPropName: metaTable, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<io.nop.metadata.dao.entity.NopMetaProfilingResult> getProfilingResults(){
       return _profilingResults;
    }
       
    private final OrmEntitySet<io.nop.metadata.dao.entity.NopMetaQualityScore> _qualityScores = new OrmEntitySet<>(this, PROP_NAME_qualityScores,
        io.nop.metadata.dao.entity.NopMetaQualityScore.PROP_NAME_metaTable, null,io.nop.metadata.dao.entity.NopMetaQualityScore.class);

    /**
     * 质量评分集。 refPropName: metaTable, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<io.nop.metadata.dao.entity.NopMetaQualityScore> getQualityScores(){
       return _qualityScores;
    }
       
    private final OrmEntitySet<io.nop.metadata.dao.entity.NopMetaDataContract> _dataContracts = new OrmEntitySet<>(this, PROP_NAME_dataContracts,
        io.nop.metadata.dao.entity.NopMetaDataContract.PROP_NAME_metaTable, null,io.nop.metadata.dao.entity.NopMetaDataContract.class);

    /**
     * 数据契约集。 refPropName: metaTable, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<io.nop.metadata.dao.entity.NopMetaDataContract> getDataContracts(){
       return _dataContracts;
    }
       
    private final OrmEntitySet<io.nop.metadata.dao.entity.NopMetaReconciliationConfig> _reconciliationConfigs = new OrmEntitySet<>(this, PROP_NAME_reconciliationConfigs,
        io.nop.metadata.dao.entity.NopMetaReconciliationConfig.PROP_NAME_metaTable, null,io.nop.metadata.dao.entity.NopMetaReconciliationConfig.class);

    /**
     * 对账配置集。 refPropName: metaTable, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<io.nop.metadata.dao.entity.NopMetaReconciliationConfig> getReconciliationConfigs(){
       return _reconciliationConfigs;
    }
       
    private final OrmEntitySet<io.nop.metadata.dao.entity.NopMetaReconciliationResult> _reconciliationResults = new OrmEntitySet<>(this, PROP_NAME_reconciliationResults,
        io.nop.metadata.dao.entity.NopMetaReconciliationResult.PROP_NAME_metaTable, null,io.nop.metadata.dao.entity.NopMetaReconciliationResult.class);

    /**
     * 对账结果集。 refPropName: metaTable, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<io.nop.metadata.dao.entity.NopMetaReconciliationResult> getReconciliationResults(){
       return _reconciliationResults;
    }
       
}
// resume CPD analysis - CPD-ON
