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

import io.nop.dyn.dao.entity.NopDynSql;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  SQL定义: nop_dyn_sql
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopDynSql extends DynamicOrmEntity{
    
    /* SQL ID: SQL_ID VARCHAR */
    public static final String PROP_NAME_sqlId = "sqlId";
    public static final int PROP_ID_sqlId = 1;
    
    /* 模块ID: MODULE_ID VARCHAR */
    public static final String PROP_NAME_moduleId = "moduleId";
    public static final int PROP_ID_moduleId = 2;
    
    /* SQL名称: NAME VARCHAR */
    public static final String PROP_NAME_name = "name";
    public static final int PROP_ID_name = 3;
    
    /* 显示名称: DISPLAY_NAME VARCHAR */
    public static final String PROP_NAME_displayName = "displayName";
    public static final int PROP_ID_displayName = 4;
    
    /* SQL方法: SQL_METHOD VARCHAR */
    public static final String PROP_NAME_sqlMethod = "sqlMethod";
    public static final int PROP_ID_sqlMethod = 5;
    
    /* 行类型: ROW_TYPE VARCHAR */
    public static final String PROP_NAME_rowType = "rowType";
    public static final int PROP_ID_rowType = 6;
    
    /* 描述: DESCRIPTION VARCHAR */
    public static final String PROP_NAME_description = "description";
    public static final int PROP_ID_description = 7;
    
    /* 缓存名称: CACHE_NAME VARCHAR */
    public static final String PROP_NAME_cacheName = "cacheName";
    public static final int PROP_ID_cacheName = 8;
    
    /* 缓存键表达式: CACHE_KEY_EXPR VARCHAR */
    public static final String PROP_NAME_cacheKeyExpr = "cacheKeyExpr";
    public static final int PROP_ID_cacheKeyExpr = 9;
    
    /* 批量加载选择集: BATCH_LOAD_SELECTION VARCHAR */
    public static final String PROP_NAME_batchLoadSelection = "batchLoadSelection";
    public static final int PROP_ID_batchLoadSelection = 10;
    
    /* 类型: SQL_KIND VARCHAR */
    public static final String PROP_NAME_sqlKind = "sqlKind";
    public static final int PROP_ID_sqlKind = 11;
    
    /* 查询空间: QUERY_SPACE VARCHAR */
    public static final String PROP_NAME_querySpace = "querySpace";
    public static final int PROP_ID_querySpace = 12;
    
    /* SQL文本: SOURCE VARCHAR */
    public static final String PROP_NAME_source = "source";
    public static final int PROP_ID_source = 13;
    
    /* 读取块大小: FETCH_SIZE INTEGER */
    public static final String PROP_NAME_fetchSize = "fetchSize";
    public static final int PROP_ID_fetchSize = 14;
    
    /* 超时时间: TIMEOUT INTEGER */
    public static final String PROP_NAME_timeout = "timeout";
    public static final int PROP_ID_timeout = 15;
    
    /* 禁用逻辑删除: DISABLE_LOGICAL_DELETE TINYINT */
    public static final String PROP_NAME_disableLogicalDelete = "disableLogicalDelete";
    public static final int PROP_ID_disableLogicalDelete = 16;
    
    /* 启用数据权限: ENABLE_FILTER TINYINT */
    public static final String PROP_NAME_enableFilter = "enableFilter";
    public static final int PROP_ID_enableFilter = 17;
    
    /* 实体刷新规则: REFRESH_BEHAVIOR VARCHAR */
    public static final String PROP_NAME_refreshBehavior = "refreshBehavior";
    public static final int PROP_ID_refreshBehavior = 18;
    
    /* 列名需要转换为驼峰: COL_NAME_CAMEL_CASE TINYINT */
    public static final String PROP_NAME_colNameCamelCase = "colNameCamelCase";
    public static final int PROP_ID_colNameCamelCase = 19;
    
    /* 参数列表: ARGS VARCHAR */
    public static final String PROP_NAME_args = "args";
    public static final int PROP_ID_args = 20;
    
    /* 状态: STATUS INTEGER */
    public static final String PROP_NAME_status = "status";
    public static final int PROP_ID_status = 21;
    
    /* 数据版本: VERSION INTEGER */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 22;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 23;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 24;
    
    /* 修改人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 25;
    
    /* 修改时间: UPDATE_TIME TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 26;
    
    /* 备注: REMARK VARCHAR */
    public static final String PROP_NAME_remark = "remark";
    public static final int PROP_ID_remark = 27;
    

    private static int _PROP_ID_BOUND = 28;

    
    /* relation: 所属模块 */
    public static final String PROP_NAME_module = "module";
    
    /* component:  */
    public static final String PROP_NAME_argsComponent = "argsComponent";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_sqlId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_sqlId};

    private static final String[] PROP_ID_TO_NAME = new String[28];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_sqlId] = PROP_NAME_sqlId;
          PROP_NAME_TO_ID.put(PROP_NAME_sqlId, PROP_ID_sqlId);
      
          PROP_ID_TO_NAME[PROP_ID_moduleId] = PROP_NAME_moduleId;
          PROP_NAME_TO_ID.put(PROP_NAME_moduleId, PROP_ID_moduleId);
      
          PROP_ID_TO_NAME[PROP_ID_name] = PROP_NAME_name;
          PROP_NAME_TO_ID.put(PROP_NAME_name, PROP_ID_name);
      
          PROP_ID_TO_NAME[PROP_ID_displayName] = PROP_NAME_displayName;
          PROP_NAME_TO_ID.put(PROP_NAME_displayName, PROP_ID_displayName);
      
          PROP_ID_TO_NAME[PROP_ID_sqlMethod] = PROP_NAME_sqlMethod;
          PROP_NAME_TO_ID.put(PROP_NAME_sqlMethod, PROP_ID_sqlMethod);
      
          PROP_ID_TO_NAME[PROP_ID_rowType] = PROP_NAME_rowType;
          PROP_NAME_TO_ID.put(PROP_NAME_rowType, PROP_ID_rowType);
      
          PROP_ID_TO_NAME[PROP_ID_description] = PROP_NAME_description;
          PROP_NAME_TO_ID.put(PROP_NAME_description, PROP_ID_description);
      
          PROP_ID_TO_NAME[PROP_ID_cacheName] = PROP_NAME_cacheName;
          PROP_NAME_TO_ID.put(PROP_NAME_cacheName, PROP_ID_cacheName);
      
          PROP_ID_TO_NAME[PROP_ID_cacheKeyExpr] = PROP_NAME_cacheKeyExpr;
          PROP_NAME_TO_ID.put(PROP_NAME_cacheKeyExpr, PROP_ID_cacheKeyExpr);
      
          PROP_ID_TO_NAME[PROP_ID_batchLoadSelection] = PROP_NAME_batchLoadSelection;
          PROP_NAME_TO_ID.put(PROP_NAME_batchLoadSelection, PROP_ID_batchLoadSelection);
      
          PROP_ID_TO_NAME[PROP_ID_sqlKind] = PROP_NAME_sqlKind;
          PROP_NAME_TO_ID.put(PROP_NAME_sqlKind, PROP_ID_sqlKind);
      
          PROP_ID_TO_NAME[PROP_ID_querySpace] = PROP_NAME_querySpace;
          PROP_NAME_TO_ID.put(PROP_NAME_querySpace, PROP_ID_querySpace);
      
          PROP_ID_TO_NAME[PROP_ID_source] = PROP_NAME_source;
          PROP_NAME_TO_ID.put(PROP_NAME_source, PROP_ID_source);
      
          PROP_ID_TO_NAME[PROP_ID_fetchSize] = PROP_NAME_fetchSize;
          PROP_NAME_TO_ID.put(PROP_NAME_fetchSize, PROP_ID_fetchSize);
      
          PROP_ID_TO_NAME[PROP_ID_timeout] = PROP_NAME_timeout;
          PROP_NAME_TO_ID.put(PROP_NAME_timeout, PROP_ID_timeout);
      
          PROP_ID_TO_NAME[PROP_ID_disableLogicalDelete] = PROP_NAME_disableLogicalDelete;
          PROP_NAME_TO_ID.put(PROP_NAME_disableLogicalDelete, PROP_ID_disableLogicalDelete);
      
          PROP_ID_TO_NAME[PROP_ID_enableFilter] = PROP_NAME_enableFilter;
          PROP_NAME_TO_ID.put(PROP_NAME_enableFilter, PROP_ID_enableFilter);
      
          PROP_ID_TO_NAME[PROP_ID_refreshBehavior] = PROP_NAME_refreshBehavior;
          PROP_NAME_TO_ID.put(PROP_NAME_refreshBehavior, PROP_ID_refreshBehavior);
      
          PROP_ID_TO_NAME[PROP_ID_colNameCamelCase] = PROP_NAME_colNameCamelCase;
          PROP_NAME_TO_ID.put(PROP_NAME_colNameCamelCase, PROP_ID_colNameCamelCase);
      
          PROP_ID_TO_NAME[PROP_ID_args] = PROP_NAME_args;
          PROP_NAME_TO_ID.put(PROP_NAME_args, PROP_ID_args);
      
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
      
          PROP_ID_TO_NAME[PROP_ID_remark] = PROP_NAME_remark;
          PROP_NAME_TO_ID.put(PROP_NAME_remark, PROP_ID_remark);
      
    }

    
    /* SQL ID: SQL_ID */
    private java.lang.String _sqlId;
    
    /* 模块ID: MODULE_ID */
    private java.lang.String _moduleId;
    
    /* SQL名称: NAME */
    private java.lang.String _name;
    
    /* 显示名称: DISPLAY_NAME */
    private java.lang.String _displayName;
    
    /* SQL方法: SQL_METHOD */
    private java.lang.String _sqlMethod;
    
    /* 行类型: ROW_TYPE */
    private java.lang.String _rowType;
    
    /* 描述: DESCRIPTION */
    private java.lang.String _description;
    
    /* 缓存名称: CACHE_NAME */
    private java.lang.String _cacheName;
    
    /* 缓存键表达式: CACHE_KEY_EXPR */
    private java.lang.String _cacheKeyExpr;
    
    /* 批量加载选择集: BATCH_LOAD_SELECTION */
    private java.lang.String _batchLoadSelection;
    
    /* 类型: SQL_KIND */
    private java.lang.String _sqlKind;
    
    /* 查询空间: QUERY_SPACE */
    private java.lang.String _querySpace;
    
    /* SQL文本: SOURCE */
    private java.lang.String _source;
    
    /* 读取块大小: FETCH_SIZE */
    private java.lang.Integer _fetchSize;
    
    /* 超时时间: TIMEOUT */
    private java.lang.Integer _timeout;
    
    /* 禁用逻辑删除: DISABLE_LOGICAL_DELETE */
    private java.lang.Byte _disableLogicalDelete;
    
    /* 启用数据权限: ENABLE_FILTER */
    private java.lang.Byte _enableFilter;
    
    /* 实体刷新规则: REFRESH_BEHAVIOR */
    private java.lang.String _refreshBehavior;
    
    /* 列名需要转换为驼峰: COL_NAME_CAMEL_CASE */
    private java.lang.Byte _colNameCamelCase;
    
    /* 参数列表: ARGS */
    private java.lang.String _args;
    
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
    
    /* 备注: REMARK */
    private java.lang.String _remark;
    

    public _NopDynSql(){
        // for debug
    }

    protected NopDynSql newInstance(){
        NopDynSql entity = new NopDynSql();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopDynSql cloneInstance() {
        NopDynSql entity = newInstance();
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
      return "io.nop.dyn.dao.entity.NopDynSql";
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
    
        return buildSimpleId(PROP_ID_sqlId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_sqlId;
          
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
        
            case PROP_ID_sqlId:
               return getSqlId();
        
            case PROP_ID_moduleId:
               return getModuleId();
        
            case PROP_ID_name:
               return getName();
        
            case PROP_ID_displayName:
               return getDisplayName();
        
            case PROP_ID_sqlMethod:
               return getSqlMethod();
        
            case PROP_ID_rowType:
               return getRowType();
        
            case PROP_ID_description:
               return getDescription();
        
            case PROP_ID_cacheName:
               return getCacheName();
        
            case PROP_ID_cacheKeyExpr:
               return getCacheKeyExpr();
        
            case PROP_ID_batchLoadSelection:
               return getBatchLoadSelection();
        
            case PROP_ID_sqlKind:
               return getSqlKind();
        
            case PROP_ID_querySpace:
               return getQuerySpace();
        
            case PROP_ID_source:
               return getSource();
        
            case PROP_ID_fetchSize:
               return getFetchSize();
        
            case PROP_ID_timeout:
               return getTimeout();
        
            case PROP_ID_disableLogicalDelete:
               return getDisableLogicalDelete();
        
            case PROP_ID_enableFilter:
               return getEnableFilter();
        
            case PROP_ID_refreshBehavior:
               return getRefreshBehavior();
        
            case PROP_ID_colNameCamelCase:
               return getColNameCamelCase();
        
            case PROP_ID_args:
               return getArgs();
        
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
        
            case PROP_ID_remark:
               return getRemark();
        
           default:
              return super.orm_propValue(propId);
        }
    }

    

    @Override
    public void orm_propValue(int propId, Object value){
        switch(propId){
        
            case PROP_ID_sqlId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_sqlId));
               }
               setSqlId(typedValue);
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
        
            case PROP_ID_name:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_name));
               }
               setName(typedValue);
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
        
            case PROP_ID_sqlMethod:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_sqlMethod));
               }
               setSqlMethod(typedValue);
               break;
            }
        
            case PROP_ID_rowType:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_rowType));
               }
               setRowType(typedValue);
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
        
            case PROP_ID_cacheName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_cacheName));
               }
               setCacheName(typedValue);
               break;
            }
        
            case PROP_ID_cacheKeyExpr:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_cacheKeyExpr));
               }
               setCacheKeyExpr(typedValue);
               break;
            }
        
            case PROP_ID_batchLoadSelection:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_batchLoadSelection));
               }
               setBatchLoadSelection(typedValue);
               break;
            }
        
            case PROP_ID_sqlKind:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_sqlKind));
               }
               setSqlKind(typedValue);
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
        
            case PROP_ID_source:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_source));
               }
               setSource(typedValue);
               break;
            }
        
            case PROP_ID_fetchSize:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_fetchSize));
               }
               setFetchSize(typedValue);
               break;
            }
        
            case PROP_ID_timeout:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_timeout));
               }
               setTimeout(typedValue);
               break;
            }
        
            case PROP_ID_disableLogicalDelete:{
               java.lang.Byte typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toByte(value,
                       err-> newTypeConversionError(PROP_NAME_disableLogicalDelete));
               }
               setDisableLogicalDelete(typedValue);
               break;
            }
        
            case PROP_ID_enableFilter:{
               java.lang.Byte typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toByte(value,
                       err-> newTypeConversionError(PROP_NAME_enableFilter));
               }
               setEnableFilter(typedValue);
               break;
            }
        
            case PROP_ID_refreshBehavior:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_refreshBehavior));
               }
               setRefreshBehavior(typedValue);
               break;
            }
        
            case PROP_ID_colNameCamelCase:{
               java.lang.Byte typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toByte(value,
                       err-> newTypeConversionError(PROP_NAME_colNameCamelCase));
               }
               setColNameCamelCase(typedValue);
               break;
            }
        
            case PROP_ID_args:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_args));
               }
               setArgs(typedValue);
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
        
            case PROP_ID_sqlId:{
               onInitProp(propId);
               this._sqlId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_moduleId:{
               onInitProp(propId);
               this._moduleId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_name:{
               onInitProp(propId);
               this._name = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_displayName:{
               onInitProp(propId);
               this._displayName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_sqlMethod:{
               onInitProp(propId);
               this._sqlMethod = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_rowType:{
               onInitProp(propId);
               this._rowType = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_description:{
               onInitProp(propId);
               this._description = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_cacheName:{
               onInitProp(propId);
               this._cacheName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_cacheKeyExpr:{
               onInitProp(propId);
               this._cacheKeyExpr = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_batchLoadSelection:{
               onInitProp(propId);
               this._batchLoadSelection = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_sqlKind:{
               onInitProp(propId);
               this._sqlKind = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_querySpace:{
               onInitProp(propId);
               this._querySpace = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_source:{
               onInitProp(propId);
               this._source = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_fetchSize:{
               onInitProp(propId);
               this._fetchSize = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_timeout:{
               onInitProp(propId);
               this._timeout = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_disableLogicalDelete:{
               onInitProp(propId);
               this._disableLogicalDelete = (java.lang.Byte)value;
               
               break;
            }
        
            case PROP_ID_enableFilter:{
               onInitProp(propId);
               this._enableFilter = (java.lang.Byte)value;
               
               break;
            }
        
            case PROP_ID_refreshBehavior:{
               onInitProp(propId);
               this._refreshBehavior = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_colNameCamelCase:{
               onInitProp(propId);
               this._colNameCamelCase = (java.lang.Byte)value;
               
               break;
            }
        
            case PROP_ID_args:{
               onInitProp(propId);
               this._args = (java.lang.String)value;
               
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
     * SQL ID: SQL_ID
     */
    public final java.lang.String getSqlId(){
         onPropGet(PROP_ID_sqlId);
         return _sqlId;
    }

    /**
     * SQL ID: SQL_ID
     */
    public final void setSqlId(java.lang.String value){
        if(onPropSet(PROP_ID_sqlId,value)){
            this._sqlId = value;
            internalClearRefs(PROP_ID_sqlId);
            orm_id();
        }
    }
    
    /**
     * 模块ID: MODULE_ID
     */
    public final java.lang.String getModuleId(){
         onPropGet(PROP_ID_moduleId);
         return _moduleId;
    }

    /**
     * 模块ID: MODULE_ID
     */
    public final void setModuleId(java.lang.String value){
        if(onPropSet(PROP_ID_moduleId,value)){
            this._moduleId = value;
            internalClearRefs(PROP_ID_moduleId);
            
        }
    }
    
    /**
     * SQL名称: NAME
     */
    public final java.lang.String getName(){
         onPropGet(PROP_ID_name);
         return _name;
    }

    /**
     * SQL名称: NAME
     */
    public final void setName(java.lang.String value){
        if(onPropSet(PROP_ID_name,value)){
            this._name = value;
            internalClearRefs(PROP_ID_name);
            
        }
    }
    
    /**
     * 显示名称: DISPLAY_NAME
     */
    public final java.lang.String getDisplayName(){
         onPropGet(PROP_ID_displayName);
         return _displayName;
    }

    /**
     * 显示名称: DISPLAY_NAME
     */
    public final void setDisplayName(java.lang.String value){
        if(onPropSet(PROP_ID_displayName,value)){
            this._displayName = value;
            internalClearRefs(PROP_ID_displayName);
            
        }
    }
    
    /**
     * SQL方法: SQL_METHOD
     */
    public final java.lang.String getSqlMethod(){
         onPropGet(PROP_ID_sqlMethod);
         return _sqlMethod;
    }

    /**
     * SQL方法: SQL_METHOD
     */
    public final void setSqlMethod(java.lang.String value){
        if(onPropSet(PROP_ID_sqlMethod,value)){
            this._sqlMethod = value;
            internalClearRefs(PROP_ID_sqlMethod);
            
        }
    }
    
    /**
     * 行类型: ROW_TYPE
     */
    public final java.lang.String getRowType(){
         onPropGet(PROP_ID_rowType);
         return _rowType;
    }

    /**
     * 行类型: ROW_TYPE
     */
    public final void setRowType(java.lang.String value){
        if(onPropSet(PROP_ID_rowType,value)){
            this._rowType = value;
            internalClearRefs(PROP_ID_rowType);
            
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
     * 缓存名称: CACHE_NAME
     */
    public final java.lang.String getCacheName(){
         onPropGet(PROP_ID_cacheName);
         return _cacheName;
    }

    /**
     * 缓存名称: CACHE_NAME
     */
    public final void setCacheName(java.lang.String value){
        if(onPropSet(PROP_ID_cacheName,value)){
            this._cacheName = value;
            internalClearRefs(PROP_ID_cacheName);
            
        }
    }
    
    /**
     * 缓存键表达式: CACHE_KEY_EXPR
     */
    public final java.lang.String getCacheKeyExpr(){
         onPropGet(PROP_ID_cacheKeyExpr);
         return _cacheKeyExpr;
    }

    /**
     * 缓存键表达式: CACHE_KEY_EXPR
     */
    public final void setCacheKeyExpr(java.lang.String value){
        if(onPropSet(PROP_ID_cacheKeyExpr,value)){
            this._cacheKeyExpr = value;
            internalClearRefs(PROP_ID_cacheKeyExpr);
            
        }
    }
    
    /**
     * 批量加载选择集: BATCH_LOAD_SELECTION
     */
    public final java.lang.String getBatchLoadSelection(){
         onPropGet(PROP_ID_batchLoadSelection);
         return _batchLoadSelection;
    }

    /**
     * 批量加载选择集: BATCH_LOAD_SELECTION
     */
    public final void setBatchLoadSelection(java.lang.String value){
        if(onPropSet(PROP_ID_batchLoadSelection,value)){
            this._batchLoadSelection = value;
            internalClearRefs(PROP_ID_batchLoadSelection);
            
        }
    }
    
    /**
     * 类型: SQL_KIND
     */
    public final java.lang.String getSqlKind(){
         onPropGet(PROP_ID_sqlKind);
         return _sqlKind;
    }

    /**
     * 类型: SQL_KIND
     */
    public final void setSqlKind(java.lang.String value){
        if(onPropSet(PROP_ID_sqlKind,value)){
            this._sqlKind = value;
            internalClearRefs(PROP_ID_sqlKind);
            
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
     * SQL文本: SOURCE
     */
    public final java.lang.String getSource(){
         onPropGet(PROP_ID_source);
         return _source;
    }

    /**
     * SQL文本: SOURCE
     */
    public final void setSource(java.lang.String value){
        if(onPropSet(PROP_ID_source,value)){
            this._source = value;
            internalClearRefs(PROP_ID_source);
            
        }
    }
    
    /**
     * 读取块大小: FETCH_SIZE
     */
    public final java.lang.Integer getFetchSize(){
         onPropGet(PROP_ID_fetchSize);
         return _fetchSize;
    }

    /**
     * 读取块大小: FETCH_SIZE
     */
    public final void setFetchSize(java.lang.Integer value){
        if(onPropSet(PROP_ID_fetchSize,value)){
            this._fetchSize = value;
            internalClearRefs(PROP_ID_fetchSize);
            
        }
    }
    
    /**
     * 超时时间: TIMEOUT
     */
    public final java.lang.Integer getTimeout(){
         onPropGet(PROP_ID_timeout);
         return _timeout;
    }

    /**
     * 超时时间: TIMEOUT
     */
    public final void setTimeout(java.lang.Integer value){
        if(onPropSet(PROP_ID_timeout,value)){
            this._timeout = value;
            internalClearRefs(PROP_ID_timeout);
            
        }
    }
    
    /**
     * 禁用逻辑删除: DISABLE_LOGICAL_DELETE
     */
    public final java.lang.Byte getDisableLogicalDelete(){
         onPropGet(PROP_ID_disableLogicalDelete);
         return _disableLogicalDelete;
    }

    /**
     * 禁用逻辑删除: DISABLE_LOGICAL_DELETE
     */
    public final void setDisableLogicalDelete(java.lang.Byte value){
        if(onPropSet(PROP_ID_disableLogicalDelete,value)){
            this._disableLogicalDelete = value;
            internalClearRefs(PROP_ID_disableLogicalDelete);
            
        }
    }
    
    /**
     * 启用数据权限: ENABLE_FILTER
     */
    public final java.lang.Byte getEnableFilter(){
         onPropGet(PROP_ID_enableFilter);
         return _enableFilter;
    }

    /**
     * 启用数据权限: ENABLE_FILTER
     */
    public final void setEnableFilter(java.lang.Byte value){
        if(onPropSet(PROP_ID_enableFilter,value)){
            this._enableFilter = value;
            internalClearRefs(PROP_ID_enableFilter);
            
        }
    }
    
    /**
     * 实体刷新规则: REFRESH_BEHAVIOR
     */
    public final java.lang.String getRefreshBehavior(){
         onPropGet(PROP_ID_refreshBehavior);
         return _refreshBehavior;
    }

    /**
     * 实体刷新规则: REFRESH_BEHAVIOR
     */
    public final void setRefreshBehavior(java.lang.String value){
        if(onPropSet(PROP_ID_refreshBehavior,value)){
            this._refreshBehavior = value;
            internalClearRefs(PROP_ID_refreshBehavior);
            
        }
    }
    
    /**
     * 列名需要转换为驼峰: COL_NAME_CAMEL_CASE
     */
    public final java.lang.Byte getColNameCamelCase(){
         onPropGet(PROP_ID_colNameCamelCase);
         return _colNameCamelCase;
    }

    /**
     * 列名需要转换为驼峰: COL_NAME_CAMEL_CASE
     */
    public final void setColNameCamelCase(java.lang.Byte value){
        if(onPropSet(PROP_ID_colNameCamelCase,value)){
            this._colNameCamelCase = value;
            internalClearRefs(PROP_ID_colNameCamelCase);
            
        }
    }
    
    /**
     * 参数列表: ARGS
     */
    public final java.lang.String getArgs(){
         onPropGet(PROP_ID_args);
         return _args;
    }

    /**
     * 参数列表: ARGS
     */
    public final void setArgs(java.lang.String value){
        if(onPropSet(PROP_ID_args,value)){
            this._args = value;
            internalClearRefs(PROP_ID_args);
            
        }
    }
    
    /**
     * 状态: STATUS
     */
    public final java.lang.Integer getStatus(){
         onPropGet(PROP_ID_status);
         return _status;
    }

    /**
     * 状态: STATUS
     */
    public final void setStatus(java.lang.Integer value){
        if(onPropSet(PROP_ID_status,value)){
            this._status = value;
            internalClearRefs(PROP_ID_status);
            
        }
    }
    
    /**
     * 数据版本: VERSION
     */
    public final java.lang.Integer getVersion(){
         onPropGet(PROP_ID_version);
         return _version;
    }

    /**
     * 数据版本: VERSION
     */
    public final void setVersion(java.lang.Integer value){
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
     * 所属模块
     */
    public final io.nop.dyn.dao.entity.NopDynModule getModule(){
       return (io.nop.dyn.dao.entity.NopDynModule)internalGetRefEntity(PROP_NAME_module);
    }

    public final void setModule(io.nop.dyn.dao.entity.NopDynModule refEntity){
   
           if(refEntity == null){
           
                   this.setModuleId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_module, refEntity,()->{
           
                           this.setModuleId(refEntity.getModuleId());
                       
           });
           }
       
    }
       
   private io.nop.orm.component.JsonOrmComponent _argsComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_argsComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_argsComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_args);
      
   }

   public final io.nop.orm.component.JsonOrmComponent getArgsComponent(){
      if(_argsComponent == null){
          _argsComponent = new io.nop.orm.component.JsonOrmComponent();
          _argsComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_argsComponent);
      }
      return _argsComponent;
   }

}
// resume CPD analysis - CPD-ON
