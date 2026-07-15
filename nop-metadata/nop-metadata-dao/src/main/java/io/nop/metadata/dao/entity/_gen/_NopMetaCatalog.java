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

import io.nop.metadata.dao.entity.NopMetaCatalog;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  运行时统计快照: nop_meta_catalog
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopMetaCatalog extends DynamicOrmEntity{
    
    /* 统计快照ID: META_CATALOG_ID VARCHAR */
    public static final String PROP_NAME_metaCatalogId = "metaCatalogId";
    public static final int PROP_ID_metaCatalogId = 1;
    
    /* 逻辑表ID: META_TABLE_ID VARCHAR */
    public static final String PROP_NAME_metaTableId = "metaTableId";
    public static final int PROP_ID_metaTableId = 2;
    
    /* 行数: ROW_COUNT BIGINT */
    public static final String PROP_NAME_rowCount = "rowCount";
    public static final int PROP_ID_rowCount = 3;
    
    /* 表物理大小: SIZE_BYTES BIGINT */
    public static final String PROP_NAME_sizeBytes = "sizeBytes";
    public static final int PROP_ID_sizeBytes = 4;
    
    /* 索引数量: INDEX_COUNT INTEGER */
    public static final String PROP_NAME_indexCount = "indexCount";
    public static final int PROP_ID_indexCount = 5;
    
    /* 分区数: PARTITION_COUNT INTEGER */
    public static final String PROP_NAME_partitionCount = "partitionCount";
    public static final int PROP_ID_partitionCount = 6;
    
    /* 最后修改时间: LAST_MODIFIED TIMESTAMP */
    public static final String PROP_NAME_lastModified = "lastModified";
    public static final int PROP_ID_lastModified = 7;
    
    /* 扩展详情: DETAILS VARCHAR */
    public static final String PROP_NAME_details = "details";
    public static final int PROP_ID_details = 8;
    
    /* 收集时间: COLLECTED_AT TIMESTAMP */
    public static final String PROP_NAME_collectedAt = "collectedAt";
    public static final int PROP_ID_collectedAt = 9;
    
    /* 数据版本: DEL_VERSION BIGINT */
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
    
    /* 备注: REMARK VARCHAR */
    public static final String PROP_NAME_remark = "remark";
    public static final int PROP_ID_remark = 15;
    

    private static int _PROP_ID_BOUND = 16;

    
    /* relation: 逻辑表 */
    public static final String PROP_NAME_metaTable = "metaTable";
    
    /* component:  */
    public static final String PROP_NAME_detailsComponent = "detailsComponent";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_metaCatalogId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_metaCatalogId};

    private static final String[] PROP_ID_TO_NAME = new String[16];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_metaCatalogId] = PROP_NAME_metaCatalogId;
          PROP_NAME_TO_ID.put(PROP_NAME_metaCatalogId, PROP_ID_metaCatalogId);
      
          PROP_ID_TO_NAME[PROP_ID_metaTableId] = PROP_NAME_metaTableId;
          PROP_NAME_TO_ID.put(PROP_NAME_metaTableId, PROP_ID_metaTableId);
      
          PROP_ID_TO_NAME[PROP_ID_rowCount] = PROP_NAME_rowCount;
          PROP_NAME_TO_ID.put(PROP_NAME_rowCount, PROP_ID_rowCount);
      
          PROP_ID_TO_NAME[PROP_ID_sizeBytes] = PROP_NAME_sizeBytes;
          PROP_NAME_TO_ID.put(PROP_NAME_sizeBytes, PROP_ID_sizeBytes);
      
          PROP_ID_TO_NAME[PROP_ID_indexCount] = PROP_NAME_indexCount;
          PROP_NAME_TO_ID.put(PROP_NAME_indexCount, PROP_ID_indexCount);
      
          PROP_ID_TO_NAME[PROP_ID_partitionCount] = PROP_NAME_partitionCount;
          PROP_NAME_TO_ID.put(PROP_NAME_partitionCount, PROP_ID_partitionCount);
      
          PROP_ID_TO_NAME[PROP_ID_lastModified] = PROP_NAME_lastModified;
          PROP_NAME_TO_ID.put(PROP_NAME_lastModified, PROP_ID_lastModified);
      
          PROP_ID_TO_NAME[PROP_ID_details] = PROP_NAME_details;
          PROP_NAME_TO_ID.put(PROP_NAME_details, PROP_ID_details);
      
          PROP_ID_TO_NAME[PROP_ID_collectedAt] = PROP_NAME_collectedAt;
          PROP_NAME_TO_ID.put(PROP_NAME_collectedAt, PROP_ID_collectedAt);
      
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

    
    /* 统计快照ID: META_CATALOG_ID */
    private java.lang.String _metaCatalogId;
    
    /* 逻辑表ID: META_TABLE_ID */
    private java.lang.String _metaTableId;
    
    /* 行数: ROW_COUNT */
    private java.lang.Long _rowCount;
    
    /* 表物理大小: SIZE_BYTES */
    private java.lang.Long _sizeBytes;
    
    /* 索引数量: INDEX_COUNT */
    private java.lang.Integer _indexCount;
    
    /* 分区数: PARTITION_COUNT */
    private java.lang.Integer _partitionCount;
    
    /* 最后修改时间: LAST_MODIFIED */
    private java.sql.Timestamp _lastModified;
    
    /* 扩展详情: DETAILS */
    private java.lang.String _details;
    
    /* 收集时间: COLLECTED_AT */
    private java.sql.Timestamp _collectedAt;
    
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
    

    public _NopMetaCatalog(){
        // for debug
    }

    protected NopMetaCatalog newInstance(){
        NopMetaCatalog entity = new NopMetaCatalog();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopMetaCatalog cloneInstance() {
        NopMetaCatalog entity = newInstance();
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
      return "io.nop.metadata.dao.entity.NopMetaCatalog";
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
    
        return buildSimpleId(PROP_ID_metaCatalogId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_metaCatalogId;
          
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
        
            case PROP_ID_metaCatalogId:
               return getMetaCatalogId();
        
            case PROP_ID_metaTableId:
               return getMetaTableId();
        
            case PROP_ID_rowCount:
               return getRowCount();
        
            case PROP_ID_sizeBytes:
               return getSizeBytes();
        
            case PROP_ID_indexCount:
               return getIndexCount();
        
            case PROP_ID_partitionCount:
               return getPartitionCount();
        
            case PROP_ID_lastModified:
               return getLastModified();
        
            case PROP_ID_details:
               return getDetails();
        
            case PROP_ID_collectedAt:
               return getCollectedAt();
        
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
        
            case PROP_ID_metaCatalogId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_metaCatalogId));
               }
               setMetaCatalogId(typedValue);
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
        
            case PROP_ID_rowCount:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_rowCount));
               }
               setRowCount(typedValue);
               break;
            }
        
            case PROP_ID_sizeBytes:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_sizeBytes));
               }
               setSizeBytes(typedValue);
               break;
            }
        
            case PROP_ID_indexCount:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_indexCount));
               }
               setIndexCount(typedValue);
               break;
            }
        
            case PROP_ID_partitionCount:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_partitionCount));
               }
               setPartitionCount(typedValue);
               break;
            }
        
            case PROP_ID_lastModified:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_lastModified));
               }
               setLastModified(typedValue);
               break;
            }
        
            case PROP_ID_details:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_details));
               }
               setDetails(typedValue);
               break;
            }
        
            case PROP_ID_collectedAt:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_collectedAt));
               }
               setCollectedAt(typedValue);
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
        
            case PROP_ID_metaCatalogId:{
               onInitProp(propId);
               this._metaCatalogId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_metaTableId:{
               onInitProp(propId);
               this._metaTableId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_rowCount:{
               onInitProp(propId);
               this._rowCount = (java.lang.Long)value;
               
               break;
            }
        
            case PROP_ID_sizeBytes:{
               onInitProp(propId);
               this._sizeBytes = (java.lang.Long)value;
               
               break;
            }
        
            case PROP_ID_indexCount:{
               onInitProp(propId);
               this._indexCount = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_partitionCount:{
               onInitProp(propId);
               this._partitionCount = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_lastModified:{
               onInitProp(propId);
               this._lastModified = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_details:{
               onInitProp(propId);
               this._details = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_collectedAt:{
               onInitProp(propId);
               this._collectedAt = (java.sql.Timestamp)value;
               
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
     * 统计快照ID: META_CATALOG_ID
     */
    public final java.lang.String getMetaCatalogId(){
         onPropGet(PROP_ID_metaCatalogId);
         return _metaCatalogId;
    }

    /**
     * 统计快照ID: META_CATALOG_ID
     */
    public final void setMetaCatalogId(java.lang.String value){
        if(onPropSet(PROP_ID_metaCatalogId,value)){
            this._metaCatalogId = value;
            internalClearRefs(PROP_ID_metaCatalogId);
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
     * 行数: ROW_COUNT
     */
    public final java.lang.Long getRowCount(){
         onPropGet(PROP_ID_rowCount);
         return _rowCount;
    }

    /**
     * 行数: ROW_COUNT
     */
    public final void setRowCount(java.lang.Long value){
        if(onPropSet(PROP_ID_rowCount,value)){
            this._rowCount = value;
            internalClearRefs(PROP_ID_rowCount);
            
        }
    }
    
    /**
     * 表物理大小: SIZE_BYTES
     */
    public final java.lang.Long getSizeBytes(){
         onPropGet(PROP_ID_sizeBytes);
         return _sizeBytes;
    }

    /**
     * 表物理大小: SIZE_BYTES
     */
    public final void setSizeBytes(java.lang.Long value){
        if(onPropSet(PROP_ID_sizeBytes,value)){
            this._sizeBytes = value;
            internalClearRefs(PROP_ID_sizeBytes);
            
        }
    }
    
    /**
     * 索引数量: INDEX_COUNT
     */
    public final java.lang.Integer getIndexCount(){
         onPropGet(PROP_ID_indexCount);
         return _indexCount;
    }

    /**
     * 索引数量: INDEX_COUNT
     */
    public final void setIndexCount(java.lang.Integer value){
        if(onPropSet(PROP_ID_indexCount,value)){
            this._indexCount = value;
            internalClearRefs(PROP_ID_indexCount);
            
        }
    }
    
    /**
     * 分区数: PARTITION_COUNT
     */
    public final java.lang.Integer getPartitionCount(){
         onPropGet(PROP_ID_partitionCount);
         return _partitionCount;
    }

    /**
     * 分区数: PARTITION_COUNT
     */
    public final void setPartitionCount(java.lang.Integer value){
        if(onPropSet(PROP_ID_partitionCount,value)){
            this._partitionCount = value;
            internalClearRefs(PROP_ID_partitionCount);
            
        }
    }
    
    /**
     * 最后修改时间: LAST_MODIFIED
     */
    public final java.sql.Timestamp getLastModified(){
         onPropGet(PROP_ID_lastModified);
         return _lastModified;
    }

    /**
     * 最后修改时间: LAST_MODIFIED
     */
    public final void setLastModified(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_lastModified,value)){
            this._lastModified = value;
            internalClearRefs(PROP_ID_lastModified);
            
        }
    }
    
    /**
     * 扩展详情: DETAILS
     */
    public final java.lang.String getDetails(){
         onPropGet(PROP_ID_details);
         return _details;
    }

    /**
     * 扩展详情: DETAILS
     */
    public final void setDetails(java.lang.String value){
        if(onPropSet(PROP_ID_details,value)){
            this._details = value;
            internalClearRefs(PROP_ID_details);
            
        }
    }
    
    /**
     * 收集时间: COLLECTED_AT
     */
    public final java.sql.Timestamp getCollectedAt(){
         onPropGet(PROP_ID_collectedAt);
         return _collectedAt;
    }

    /**
     * 收集时间: COLLECTED_AT
     */
    public final void setCollectedAt(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_collectedAt,value)){
            this._collectedAt = value;
            internalClearRefs(PROP_ID_collectedAt);
            
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
       
   private io.nop.orm.component.JsonOrmComponent _detailsComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_detailsComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_detailsComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_details);
      
   }

   public final io.nop.orm.component.JsonOrmComponent getDetailsComponent(){
      if(_detailsComponent == null){
          _detailsComponent = new io.nop.orm.component.JsonOrmComponent();
          _detailsComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_detailsComponent);
      }
      return _detailsComponent;
   }

}
// resume CPD analysis - CPD-ON
