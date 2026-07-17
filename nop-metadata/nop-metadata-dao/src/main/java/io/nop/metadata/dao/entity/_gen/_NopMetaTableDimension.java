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

import io.nop.metadata.dao.entity.NopMetaTableDimension;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  表维度: nop_meta_table_dimension
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopMetaTableDimension extends DynamicOrmEntity{
    
    /* 维度ID: DIMENSION_ID VARCHAR */
    public static final String PROP_NAME_dimensionId = "dimensionId";
    public static final int PROP_ID_dimensionId = 1;
    
    /* 逻辑表ID: META_TABLE_ID VARCHAR */
    public static final String PROP_NAME_metaTableId = "metaTableId";
    public static final int PROP_ID_metaTableId = 2;
    
    /* 维度名: DIMENSION_NAME VARCHAR */
    public static final String PROP_NAME_dimensionName = "dimensionName";
    public static final int PROP_ID_dimensionName = 3;
    
    /* 显示名: DISPLAY_NAME VARCHAR */
    public static final String PROP_NAME_displayName = "displayName";
    public static final int PROP_ID_displayName = 4;
    
    /* 实体字段ID: ENTITY_FIELD_ID VARCHAR */
    public static final String PROP_NAME_entityFieldId = "entityFieldId";
    public static final int PROP_ID_entityFieldId = 5;
    
    /* 维度类型: DIMENSION_TYPE VARCHAR */
    public static final String PROP_NAME_dimensionType = "dimensionType";
    public static final int PROP_ID_dimensionType = 6;
    
    /* 时间粒度: GRANULARITY VARCHAR */
    public static final String PROP_NAME_granularity = "granularity";
    public static final int PROP_ID_granularity = 7;
    
    /* 显示格式: FORMAT VARCHAR */
    public static final String PROP_NAME_format = "format";
    public static final int PROP_ID_format = 8;
    
    /* 排序: SORT_ORDER INTEGER */
    public static final String PROP_NAME_sortOrder = "sortOrder";
    public static final int PROP_ID_sortOrder = 9;
    
    /* 扩展配置: EXT_CONFIG VARCHAR */
    public static final String PROP_NAME_extConfig = "extConfig";
    public static final int PROP_ID_extConfig = 10;
    
    /* 数据版本: DEL_VERSION BIGINT */
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
    
    /* 侧别: SIDE VARCHAR */
    public static final String PROP_NAME_side = "side";
    public static final int PROP_ID_side = 17;
    

    private static int _PROP_ID_BOUND = 18;

    
    /* relation: 逻辑表 */
    public static final String PROP_NAME_metaTable = "metaTable";
    
    /* component:  */
    public static final String PROP_NAME_extConfigComponent = "extConfigComponent";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_dimensionId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_dimensionId};

    private static final String[] PROP_ID_TO_NAME = new String[18];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_dimensionId] = PROP_NAME_dimensionId;
          PROP_NAME_TO_ID.put(PROP_NAME_dimensionId, PROP_ID_dimensionId);
      
          PROP_ID_TO_NAME[PROP_ID_metaTableId] = PROP_NAME_metaTableId;
          PROP_NAME_TO_ID.put(PROP_NAME_metaTableId, PROP_ID_metaTableId);
      
          PROP_ID_TO_NAME[PROP_ID_dimensionName] = PROP_NAME_dimensionName;
          PROP_NAME_TO_ID.put(PROP_NAME_dimensionName, PROP_ID_dimensionName);
      
          PROP_ID_TO_NAME[PROP_ID_displayName] = PROP_NAME_displayName;
          PROP_NAME_TO_ID.put(PROP_NAME_displayName, PROP_ID_displayName);
      
          PROP_ID_TO_NAME[PROP_ID_entityFieldId] = PROP_NAME_entityFieldId;
          PROP_NAME_TO_ID.put(PROP_NAME_entityFieldId, PROP_ID_entityFieldId);
      
          PROP_ID_TO_NAME[PROP_ID_dimensionType] = PROP_NAME_dimensionType;
          PROP_NAME_TO_ID.put(PROP_NAME_dimensionType, PROP_ID_dimensionType);
      
          PROP_ID_TO_NAME[PROP_ID_granularity] = PROP_NAME_granularity;
          PROP_NAME_TO_ID.put(PROP_NAME_granularity, PROP_ID_granularity);
      
          PROP_ID_TO_NAME[PROP_ID_format] = PROP_NAME_format;
          PROP_NAME_TO_ID.put(PROP_NAME_format, PROP_ID_format);
      
          PROP_ID_TO_NAME[PROP_ID_sortOrder] = PROP_NAME_sortOrder;
          PROP_NAME_TO_ID.put(PROP_NAME_sortOrder, PROP_ID_sortOrder);
      
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
      
          PROP_ID_TO_NAME[PROP_ID_side] = PROP_NAME_side;
          PROP_NAME_TO_ID.put(PROP_NAME_side, PROP_ID_side);
      
    }

    
    /* 维度ID: DIMENSION_ID */
    private java.lang.String _dimensionId;
    
    /* 逻辑表ID: META_TABLE_ID */
    private java.lang.String _metaTableId;
    
    /* 维度名: DIMENSION_NAME */
    private java.lang.String _dimensionName;
    
    /* 显示名: DISPLAY_NAME */
    private java.lang.String _displayName;
    
    /* 实体字段ID: ENTITY_FIELD_ID */
    private java.lang.String _entityFieldId;
    
    /* 维度类型: DIMENSION_TYPE */
    private java.lang.String _dimensionType;
    
    /* 时间粒度: GRANULARITY */
    private java.lang.String _granularity;
    
    /* 显示格式: FORMAT */
    private java.lang.String _format;
    
    /* 排序: SORT_ORDER */
    private java.lang.Integer _sortOrder;
    
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
    
    /* 侧别: SIDE */
    private java.lang.String _side;
    

    public _NopMetaTableDimension(){
        // for debug
    }

    protected NopMetaTableDimension newInstance(){
        NopMetaTableDimension entity = new NopMetaTableDimension();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopMetaTableDimension cloneInstance() {
        NopMetaTableDimension entity = newInstance();
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
      return "io.nop.metadata.dao.entity.NopMetaTableDimension";
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
    
        return buildSimpleId(PROP_ID_dimensionId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_dimensionId;
          
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
        
            case PROP_ID_dimensionId:
               return getDimensionId();
        
            case PROP_ID_metaTableId:
               return getMetaTableId();
        
            case PROP_ID_dimensionName:
               return getDimensionName();
        
            case PROP_ID_displayName:
               return getDisplayName();
        
            case PROP_ID_entityFieldId:
               return getEntityFieldId();
        
            case PROP_ID_dimensionType:
               return getDimensionType();
        
            case PROP_ID_granularity:
               return getGranularity();
        
            case PROP_ID_format:
               return getFormat();
        
            case PROP_ID_sortOrder:
               return getSortOrder();
        
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
        
            case PROP_ID_side:
               return getSide();
        
           default:
              return super.orm_propValue(propId);
        }
    }

    

    @Override
    public void orm_propValue(int propId, Object value){
        switch(propId){
        
            case PROP_ID_dimensionId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_dimensionId));
               }
               setDimensionId(typedValue);
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
        
            case PROP_ID_dimensionName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_dimensionName));
               }
               setDimensionName(typedValue);
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
        
            case PROP_ID_entityFieldId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_entityFieldId));
               }
               setEntityFieldId(typedValue);
               break;
            }
        
            case PROP_ID_dimensionType:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_dimensionType));
               }
               setDimensionType(typedValue);
               break;
            }
        
            case PROP_ID_granularity:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_granularity));
               }
               setGranularity(typedValue);
               break;
            }
        
            case PROP_ID_format:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_format));
               }
               setFormat(typedValue);
               break;
            }
        
            case PROP_ID_sortOrder:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_sortOrder));
               }
               setSortOrder(typedValue);
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
        
            case PROP_ID_side:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_side));
               }
               setSide(typedValue);
               break;
            }
        
           default:
              super.orm_propValue(propId,value);
        }
    }

    @Override
    public void orm_internalSet(int propId, Object value) {
        switch(propId){
        
            case PROP_ID_dimensionId:{
               onInitProp(propId);
               this._dimensionId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_metaTableId:{
               onInitProp(propId);
               this._metaTableId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_dimensionName:{
               onInitProp(propId);
               this._dimensionName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_displayName:{
               onInitProp(propId);
               this._displayName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_entityFieldId:{
               onInitProp(propId);
               this._entityFieldId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_dimensionType:{
               onInitProp(propId);
               this._dimensionType = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_granularity:{
               onInitProp(propId);
               this._granularity = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_format:{
               onInitProp(propId);
               this._format = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_sortOrder:{
               onInitProp(propId);
               this._sortOrder = (java.lang.Integer)value;
               
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
        
            case PROP_ID_side:{
               onInitProp(propId);
               this._side = (java.lang.String)value;
               
               break;
            }
        
           default:
              super.orm_internalSet(propId,value);
        }
    }

    
    /**
     * 维度ID: DIMENSION_ID
     */
    public final java.lang.String getDimensionId(){
         onPropGet(PROP_ID_dimensionId);
         return _dimensionId;
    }

    /**
     * 维度ID: DIMENSION_ID
     */
    public final void setDimensionId(java.lang.String value){
        if(onPropSet(PROP_ID_dimensionId,value)){
            this._dimensionId = value;
            internalClearRefs(PROP_ID_dimensionId);
            orm_id();
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
     * 维度名: DIMENSION_NAME
     */
    public final java.lang.String getDimensionName(){
         onPropGet(PROP_ID_dimensionName);
         return _dimensionName;
    }

    /**
     * 维度名: DIMENSION_NAME
     */
    public final void setDimensionName(java.lang.String value){
        if(onPropSet(PROP_ID_dimensionName,value)){
            this._dimensionName = value;
            internalClearRefs(PROP_ID_dimensionName);
            
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
     * 实体字段ID: ENTITY_FIELD_ID
     */
    public final java.lang.String getEntityFieldId(){
         onPropGet(PROP_ID_entityFieldId);
         return _entityFieldId;
    }

    /**
     * 实体字段ID: ENTITY_FIELD_ID
     */
    public final void setEntityFieldId(java.lang.String value){
        if(onPropSet(PROP_ID_entityFieldId,value)){
            this._entityFieldId = value;
            internalClearRefs(PROP_ID_entityFieldId);
            
        }
    }
    
    /**
     * 维度类型: DIMENSION_TYPE
     */
    public final java.lang.String getDimensionType(){
         onPropGet(PROP_ID_dimensionType);
         return _dimensionType;
    }

    /**
     * 维度类型: DIMENSION_TYPE
     */
    public final void setDimensionType(java.lang.String value){
        if(onPropSet(PROP_ID_dimensionType,value)){
            this._dimensionType = value;
            internalClearRefs(PROP_ID_dimensionType);
            
        }
    }
    
    /**
     * 时间粒度: GRANULARITY
     */
    public final java.lang.String getGranularity(){
         onPropGet(PROP_ID_granularity);
         return _granularity;
    }

    /**
     * 时间粒度: GRANULARITY
     */
    public final void setGranularity(java.lang.String value){
        if(onPropSet(PROP_ID_granularity,value)){
            this._granularity = value;
            internalClearRefs(PROP_ID_granularity);
            
        }
    }
    
    /**
     * 显示格式: FORMAT
     */
    public final java.lang.String getFormat(){
         onPropGet(PROP_ID_format);
         return _format;
    }

    /**
     * 显示格式: FORMAT
     */
    public final void setFormat(java.lang.String value){
        if(onPropSet(PROP_ID_format,value)){
            this._format = value;
            internalClearRefs(PROP_ID_format);
            
        }
    }
    
    /**
     * 排序: SORT_ORDER
     */
    public final java.lang.Integer getSortOrder(){
         onPropGet(PROP_ID_sortOrder);
         return _sortOrder;
    }

    /**
     * 排序: SORT_ORDER
     */
    public final void setSortOrder(java.lang.Integer value){
        if(onPropSet(PROP_ID_sortOrder,value)){
            this._sortOrder = value;
            internalClearRefs(PROP_ID_sortOrder);
            
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
     * 侧别: SIDE
     */
    public final java.lang.String getSide(){
         onPropGet(PROP_ID_side);
         return _side;
    }

    /**
     * 侧别: SIDE
     */
    public final void setSide(java.lang.String value){
        if(onPropSet(PROP_ID_side,value)){
            this._side = value;
            internalClearRefs(PROP_ID_side);
            
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
