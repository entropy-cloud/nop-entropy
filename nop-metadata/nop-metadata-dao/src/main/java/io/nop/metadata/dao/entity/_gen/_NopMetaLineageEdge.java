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

import io.nop.metadata.dao.entity.NopMetaLineageEdge;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  血缘边: nop_meta_lineage_edge
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopMetaLineageEdge extends DynamicOrmEntity{
    
    /* 血缘边ID: LINEAGE_EDGE_ID VARCHAR */
    public static final String PROP_NAME_lineageEdgeId = "lineageEdgeId";
    public static final int PROP_ID_lineageEdgeId = 1;
    
    /* 源表ID: SOURCE_TABLE_ID VARCHAR */
    public static final String PROP_NAME_sourceTableId = "sourceTableId";
    public static final int PROP_ID_sourceTableId = 2;
    
    /* 目标表ID: TARGET_TABLE_ID VARCHAR */
    public static final String PROP_NAME_targetTableId = "targetTableId";
    public static final int PROP_ID_targetTableId = 3;
    
    /* 源列名: SOURCE_COLUMN VARCHAR */
    public static final String PROP_NAME_sourceColumn = "sourceColumn";
    public static final int PROP_ID_sourceColumn = 4;
    
    /* 目标列名: TARGET_COLUMN VARCHAR */
    public static final String PROP_NAME_targetColumn = "targetColumn";
    public static final int PROP_ID_targetColumn = 5;
    
    /* 转换类型: TRANSFORM_TYPE VARCHAR */
    public static final String PROP_NAME_transformType = "transformType";
    public static final int PROP_ID_transformType = 6;
    
    /* 转换表达式: TRANSFORM_EXPR VARCHAR */
    public static final String PROP_NAME_transformExpr = "transformExpr";
    public static final int PROP_ID_transformExpr = 7;
    
    /* 血缘来源: LINEAGE_SOURCE VARCHAR */
    public static final String PROP_NAME_lineageSource = "lineageSource";
    public static final int PROP_ID_lineageSource = 8;
    
    /* 管道ID: PIPELINE_ID VARCHAR */
    public static final String PROP_NAME_pipelineId = "pipelineId";
    public static final int PROP_ID_pipelineId = 9;
    
    /* 置信度: CONFIDENCE DOUBLE */
    public static final String PROP_NAME_confidence = "confidence";
    public static final int PROP_ID_confidence = 10;
    
    /* 扩展配置: EXT_CONFIG VARCHAR */
    public static final String PROP_NAME_extConfig = "extConfig";
    public static final int PROP_ID_extConfig = 11;
    
    /* 数据版本: DEL_VERSION BIGINT */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 12;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 13;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 14;
    
    /* 修改人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 15;
    
    /* 修改时间: UPDATE_TIME TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 16;
    
    /* 备注: REMARK VARCHAR */
    public static final String PROP_NAME_remark = "remark";
    public static final int PROP_ID_remark = 17;
    

    private static int _PROP_ID_BOUND = 18;

    
    /* component:  */
    public static final String PROP_NAME_extConfigComponent = "extConfigComponent";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_lineageEdgeId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_lineageEdgeId};

    private static final String[] PROP_ID_TO_NAME = new String[18];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_lineageEdgeId] = PROP_NAME_lineageEdgeId;
          PROP_NAME_TO_ID.put(PROP_NAME_lineageEdgeId, PROP_ID_lineageEdgeId);
      
          PROP_ID_TO_NAME[PROP_ID_sourceTableId] = PROP_NAME_sourceTableId;
          PROP_NAME_TO_ID.put(PROP_NAME_sourceTableId, PROP_ID_sourceTableId);
      
          PROP_ID_TO_NAME[PROP_ID_targetTableId] = PROP_NAME_targetTableId;
          PROP_NAME_TO_ID.put(PROP_NAME_targetTableId, PROP_ID_targetTableId);
      
          PROP_ID_TO_NAME[PROP_ID_sourceColumn] = PROP_NAME_sourceColumn;
          PROP_NAME_TO_ID.put(PROP_NAME_sourceColumn, PROP_ID_sourceColumn);
      
          PROP_ID_TO_NAME[PROP_ID_targetColumn] = PROP_NAME_targetColumn;
          PROP_NAME_TO_ID.put(PROP_NAME_targetColumn, PROP_ID_targetColumn);
      
          PROP_ID_TO_NAME[PROP_ID_transformType] = PROP_NAME_transformType;
          PROP_NAME_TO_ID.put(PROP_NAME_transformType, PROP_ID_transformType);
      
          PROP_ID_TO_NAME[PROP_ID_transformExpr] = PROP_NAME_transformExpr;
          PROP_NAME_TO_ID.put(PROP_NAME_transformExpr, PROP_ID_transformExpr);
      
          PROP_ID_TO_NAME[PROP_ID_lineageSource] = PROP_NAME_lineageSource;
          PROP_NAME_TO_ID.put(PROP_NAME_lineageSource, PROP_ID_lineageSource);
      
          PROP_ID_TO_NAME[PROP_ID_pipelineId] = PROP_NAME_pipelineId;
          PROP_NAME_TO_ID.put(PROP_NAME_pipelineId, PROP_ID_pipelineId);
      
          PROP_ID_TO_NAME[PROP_ID_confidence] = PROP_NAME_confidence;
          PROP_NAME_TO_ID.put(PROP_NAME_confidence, PROP_ID_confidence);
      
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

    
    /* 血缘边ID: LINEAGE_EDGE_ID */
    private java.lang.String _lineageEdgeId;
    
    /* 源表ID: SOURCE_TABLE_ID */
    private java.lang.String _sourceTableId;
    
    /* 目标表ID: TARGET_TABLE_ID */
    private java.lang.String _targetTableId;
    
    /* 源列名: SOURCE_COLUMN */
    private java.lang.String _sourceColumn;
    
    /* 目标列名: TARGET_COLUMN */
    private java.lang.String _targetColumn;
    
    /* 转换类型: TRANSFORM_TYPE */
    private java.lang.String _transformType;
    
    /* 转换表达式: TRANSFORM_EXPR */
    private java.lang.String _transformExpr;
    
    /* 血缘来源: LINEAGE_SOURCE */
    private java.lang.String _lineageSource;
    
    /* 管道ID: PIPELINE_ID */
    private java.lang.String _pipelineId;
    
    /* 置信度: CONFIDENCE */
    private java.lang.Double _confidence;
    
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
    

    public _NopMetaLineageEdge(){
        // for debug
    }

    protected NopMetaLineageEdge newInstance(){
        NopMetaLineageEdge entity = new NopMetaLineageEdge();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopMetaLineageEdge cloneInstance() {
        NopMetaLineageEdge entity = newInstance();
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
      return "io.nop.metadata.dao.entity.NopMetaLineageEdge";
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
    
        return buildSimpleId(PROP_ID_lineageEdgeId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_lineageEdgeId;
          
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
        
            case PROP_ID_lineageEdgeId:
               return getLineageEdgeId();
        
            case PROP_ID_sourceTableId:
               return getSourceTableId();
        
            case PROP_ID_targetTableId:
               return getTargetTableId();
        
            case PROP_ID_sourceColumn:
               return getSourceColumn();
        
            case PROP_ID_targetColumn:
               return getTargetColumn();
        
            case PROP_ID_transformType:
               return getTransformType();
        
            case PROP_ID_transformExpr:
               return getTransformExpr();
        
            case PROP_ID_lineageSource:
               return getLineageSource();
        
            case PROP_ID_pipelineId:
               return getPipelineId();
        
            case PROP_ID_confidence:
               return getConfidence();
        
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
        
            case PROP_ID_lineageEdgeId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_lineageEdgeId));
               }
               setLineageEdgeId(typedValue);
               break;
            }
        
            case PROP_ID_sourceTableId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_sourceTableId));
               }
               setSourceTableId(typedValue);
               break;
            }
        
            case PROP_ID_targetTableId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_targetTableId));
               }
               setTargetTableId(typedValue);
               break;
            }
        
            case PROP_ID_sourceColumn:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_sourceColumn));
               }
               setSourceColumn(typedValue);
               break;
            }
        
            case PROP_ID_targetColumn:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_targetColumn));
               }
               setTargetColumn(typedValue);
               break;
            }
        
            case PROP_ID_transformType:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_transformType));
               }
               setTransformType(typedValue);
               break;
            }
        
            case PROP_ID_transformExpr:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_transformExpr));
               }
               setTransformExpr(typedValue);
               break;
            }
        
            case PROP_ID_lineageSource:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_lineageSource));
               }
               setLineageSource(typedValue);
               break;
            }
        
            case PROP_ID_pipelineId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_pipelineId));
               }
               setPipelineId(typedValue);
               break;
            }
        
            case PROP_ID_confidence:{
               java.lang.Double typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toDouble(value,
                       err-> newTypeConversionError(PROP_NAME_confidence));
               }
               setConfidence(typedValue);
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
        
            case PROP_ID_lineageEdgeId:{
               onInitProp(propId);
               this._lineageEdgeId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_sourceTableId:{
               onInitProp(propId);
               this._sourceTableId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_targetTableId:{
               onInitProp(propId);
               this._targetTableId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_sourceColumn:{
               onInitProp(propId);
               this._sourceColumn = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_targetColumn:{
               onInitProp(propId);
               this._targetColumn = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_transformType:{
               onInitProp(propId);
               this._transformType = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_transformExpr:{
               onInitProp(propId);
               this._transformExpr = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_lineageSource:{
               onInitProp(propId);
               this._lineageSource = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_pipelineId:{
               onInitProp(propId);
               this._pipelineId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_confidence:{
               onInitProp(propId);
               this._confidence = (java.lang.Double)value;
               
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
     * 血缘边ID: LINEAGE_EDGE_ID
     */
    public final java.lang.String getLineageEdgeId(){
         onPropGet(PROP_ID_lineageEdgeId);
         return _lineageEdgeId;
    }

    /**
     * 血缘边ID: LINEAGE_EDGE_ID
     */
    public final void setLineageEdgeId(java.lang.String value){
        if(onPropSet(PROP_ID_lineageEdgeId,value)){
            this._lineageEdgeId = value;
            internalClearRefs(PROP_ID_lineageEdgeId);
            orm_id();
        }
    }
    
    /**
     * 源表ID: SOURCE_TABLE_ID
     */
    public final java.lang.String getSourceTableId(){
         onPropGet(PROP_ID_sourceTableId);
         return _sourceTableId;
    }

    /**
     * 源表ID: SOURCE_TABLE_ID
     */
    public final void setSourceTableId(java.lang.String value){
        if(onPropSet(PROP_ID_sourceTableId,value)){
            this._sourceTableId = value;
            internalClearRefs(PROP_ID_sourceTableId);
            
        }
    }
    
    /**
     * 目标表ID: TARGET_TABLE_ID
     */
    public final java.lang.String getTargetTableId(){
         onPropGet(PROP_ID_targetTableId);
         return _targetTableId;
    }

    /**
     * 目标表ID: TARGET_TABLE_ID
     */
    public final void setTargetTableId(java.lang.String value){
        if(onPropSet(PROP_ID_targetTableId,value)){
            this._targetTableId = value;
            internalClearRefs(PROP_ID_targetTableId);
            
        }
    }
    
    /**
     * 源列名: SOURCE_COLUMN
     */
    public final java.lang.String getSourceColumn(){
         onPropGet(PROP_ID_sourceColumn);
         return _sourceColumn;
    }

    /**
     * 源列名: SOURCE_COLUMN
     */
    public final void setSourceColumn(java.lang.String value){
        if(onPropSet(PROP_ID_sourceColumn,value)){
            this._sourceColumn = value;
            internalClearRefs(PROP_ID_sourceColumn);
            
        }
    }
    
    /**
     * 目标列名: TARGET_COLUMN
     */
    public final java.lang.String getTargetColumn(){
         onPropGet(PROP_ID_targetColumn);
         return _targetColumn;
    }

    /**
     * 目标列名: TARGET_COLUMN
     */
    public final void setTargetColumn(java.lang.String value){
        if(onPropSet(PROP_ID_targetColumn,value)){
            this._targetColumn = value;
            internalClearRefs(PROP_ID_targetColumn);
            
        }
    }
    
    /**
     * 转换类型: TRANSFORM_TYPE
     */
    public final java.lang.String getTransformType(){
         onPropGet(PROP_ID_transformType);
         return _transformType;
    }

    /**
     * 转换类型: TRANSFORM_TYPE
     */
    public final void setTransformType(java.lang.String value){
        if(onPropSet(PROP_ID_transformType,value)){
            this._transformType = value;
            internalClearRefs(PROP_ID_transformType);
            
        }
    }
    
    /**
     * 转换表达式: TRANSFORM_EXPR
     */
    public final java.lang.String getTransformExpr(){
         onPropGet(PROP_ID_transformExpr);
         return _transformExpr;
    }

    /**
     * 转换表达式: TRANSFORM_EXPR
     */
    public final void setTransformExpr(java.lang.String value){
        if(onPropSet(PROP_ID_transformExpr,value)){
            this._transformExpr = value;
            internalClearRefs(PROP_ID_transformExpr);
            
        }
    }
    
    /**
     * 血缘来源: LINEAGE_SOURCE
     */
    public final java.lang.String getLineageSource(){
         onPropGet(PROP_ID_lineageSource);
         return _lineageSource;
    }

    /**
     * 血缘来源: LINEAGE_SOURCE
     */
    public final void setLineageSource(java.lang.String value){
        if(onPropSet(PROP_ID_lineageSource,value)){
            this._lineageSource = value;
            internalClearRefs(PROP_ID_lineageSource);
            
        }
    }
    
    /**
     * 管道ID: PIPELINE_ID
     */
    public final java.lang.String getPipelineId(){
         onPropGet(PROP_ID_pipelineId);
         return _pipelineId;
    }

    /**
     * 管道ID: PIPELINE_ID
     */
    public final void setPipelineId(java.lang.String value){
        if(onPropSet(PROP_ID_pipelineId,value)){
            this._pipelineId = value;
            internalClearRefs(PROP_ID_pipelineId);
            
        }
    }
    
    /**
     * 置信度: CONFIDENCE
     */
    public final java.lang.Double getConfidence(){
         onPropGet(PROP_ID_confidence);
         return _confidence;
    }

    /**
     * 置信度: CONFIDENCE
     */
    public final void setConfidence(java.lang.Double value){
        if(onPropSet(PROP_ID_confidence,value)){
            this._confidence = value;
            internalClearRefs(PROP_ID_confidence);
            
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
