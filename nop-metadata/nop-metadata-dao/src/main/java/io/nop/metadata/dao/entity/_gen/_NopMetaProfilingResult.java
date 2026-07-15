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

import io.nop.metadata.dao.entity.NopMetaProfilingResult;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  数据剖析结果: nop_meta_profiling_result
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopMetaProfilingResult extends DynamicOrmEntity{
    
    /* 剖析结果ID: PROFILING_RESULT_ID VARCHAR */
    public static final String PROP_NAME_profilingResultId = "profilingResultId";
    public static final int PROP_ID_profilingResultId = 1;
    
    /* 剖析规则ID: PROFILING_RULE_ID VARCHAR */
    public static final String PROP_NAME_profilingRuleId = "profilingRuleId";
    public static final int PROP_ID_profilingRuleId = 2;
    
    /* 逻辑表ID: META_TABLE_ID VARCHAR */
    public static final String PROP_NAME_metaTableId = "metaTableId";
    public static final int PROP_ID_metaTableId = 3;
    
    /* 快照时间: SNAPSHOT_TIME TIMESTAMP */
    public static final String PROP_NAME_snapshotTime = "snapshotTime";
    public static final int PROP_ID_snapshotTime = 4;
    
    /* 表级统计: TABLE_STATS VARCHAR */
    public static final String PROP_NAME_tableStats = "tableStats";
    public static final int PROP_ID_tableStats = 5;
    
    /* 列级统计: COLUMN_STATS VARCHAR */
    public static final String PROP_NAME_columnStats = "columnStats";
    public static final int PROP_ID_columnStats = 6;
    
    /* 数据版本: DEL_VERSION BIGINT */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 7;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 8;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 9;
    
    /* 修改人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 10;
    
    /* 修改时间: UPDATE_TIME TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 11;
    
    /* 备注: REMARK VARCHAR */
    public static final String PROP_NAME_remark = "remark";
    public static final int PROP_ID_remark = 12;
    

    private static int _PROP_ID_BOUND = 13;

    
    /* relation: 剖析规则 */
    public static final String PROP_NAME_profilingRule = "profilingRule";
    
    /* relation: 逻辑表 */
    public static final String PROP_NAME_metaTable = "metaTable";
    
    /* component:  */
    public static final String PROP_NAME_tableStatsComponent = "tableStatsComponent";
    
    /* component:  */
    public static final String PROP_NAME_columnStatsComponent = "columnStatsComponent";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_profilingResultId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_profilingResultId};

    private static final String[] PROP_ID_TO_NAME = new String[13];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_profilingResultId] = PROP_NAME_profilingResultId;
          PROP_NAME_TO_ID.put(PROP_NAME_profilingResultId, PROP_ID_profilingResultId);
      
          PROP_ID_TO_NAME[PROP_ID_profilingRuleId] = PROP_NAME_profilingRuleId;
          PROP_NAME_TO_ID.put(PROP_NAME_profilingRuleId, PROP_ID_profilingRuleId);
      
          PROP_ID_TO_NAME[PROP_ID_metaTableId] = PROP_NAME_metaTableId;
          PROP_NAME_TO_ID.put(PROP_NAME_metaTableId, PROP_ID_metaTableId);
      
          PROP_ID_TO_NAME[PROP_ID_snapshotTime] = PROP_NAME_snapshotTime;
          PROP_NAME_TO_ID.put(PROP_NAME_snapshotTime, PROP_ID_snapshotTime);
      
          PROP_ID_TO_NAME[PROP_ID_tableStats] = PROP_NAME_tableStats;
          PROP_NAME_TO_ID.put(PROP_NAME_tableStats, PROP_ID_tableStats);
      
          PROP_ID_TO_NAME[PROP_ID_columnStats] = PROP_NAME_columnStats;
          PROP_NAME_TO_ID.put(PROP_NAME_columnStats, PROP_ID_columnStats);
      
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

    
    /* 剖析结果ID: PROFILING_RESULT_ID */
    private java.lang.String _profilingResultId;
    
    /* 剖析规则ID: PROFILING_RULE_ID */
    private java.lang.String _profilingRuleId;
    
    /* 逻辑表ID: META_TABLE_ID */
    private java.lang.String _metaTableId;
    
    /* 快照时间: SNAPSHOT_TIME */
    private java.sql.Timestamp _snapshotTime;
    
    /* 表级统计: TABLE_STATS */
    private java.lang.String _tableStats;
    
    /* 列级统计: COLUMN_STATS */
    private java.lang.String _columnStats;
    
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
    

    public _NopMetaProfilingResult(){
        // for debug
    }

    protected NopMetaProfilingResult newInstance(){
        NopMetaProfilingResult entity = new NopMetaProfilingResult();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopMetaProfilingResult cloneInstance() {
        NopMetaProfilingResult entity = newInstance();
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
      return "io.nop.metadata.dao.entity.NopMetaProfilingResult";
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
    
        return buildSimpleId(PROP_ID_profilingResultId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_profilingResultId;
          
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
        
            case PROP_ID_profilingResultId:
               return getProfilingResultId();
        
            case PROP_ID_profilingRuleId:
               return getProfilingRuleId();
        
            case PROP_ID_metaTableId:
               return getMetaTableId();
        
            case PROP_ID_snapshotTime:
               return getSnapshotTime();
        
            case PROP_ID_tableStats:
               return getTableStats();
        
            case PROP_ID_columnStats:
               return getColumnStats();
        
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
        
            case PROP_ID_profilingResultId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_profilingResultId));
               }
               setProfilingResultId(typedValue);
               break;
            }
        
            case PROP_ID_profilingRuleId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_profilingRuleId));
               }
               setProfilingRuleId(typedValue);
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
        
            case PROP_ID_snapshotTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_snapshotTime));
               }
               setSnapshotTime(typedValue);
               break;
            }
        
            case PROP_ID_tableStats:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_tableStats));
               }
               setTableStats(typedValue);
               break;
            }
        
            case PROP_ID_columnStats:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_columnStats));
               }
               setColumnStats(typedValue);
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
        
            case PROP_ID_profilingResultId:{
               onInitProp(propId);
               this._profilingResultId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_profilingRuleId:{
               onInitProp(propId);
               this._profilingRuleId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_metaTableId:{
               onInitProp(propId);
               this._metaTableId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_snapshotTime:{
               onInitProp(propId);
               this._snapshotTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_tableStats:{
               onInitProp(propId);
               this._tableStats = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_columnStats:{
               onInitProp(propId);
               this._columnStats = (java.lang.String)value;
               
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
     * 剖析结果ID: PROFILING_RESULT_ID
     */
    public final java.lang.String getProfilingResultId(){
         onPropGet(PROP_ID_profilingResultId);
         return _profilingResultId;
    }

    /**
     * 剖析结果ID: PROFILING_RESULT_ID
     */
    public final void setProfilingResultId(java.lang.String value){
        if(onPropSet(PROP_ID_profilingResultId,value)){
            this._profilingResultId = value;
            internalClearRefs(PROP_ID_profilingResultId);
            orm_id();
        }
    }
    
    /**
     * 剖析规则ID: PROFILING_RULE_ID
     */
    public final java.lang.String getProfilingRuleId(){
         onPropGet(PROP_ID_profilingRuleId);
         return _profilingRuleId;
    }

    /**
     * 剖析规则ID: PROFILING_RULE_ID
     */
    public final void setProfilingRuleId(java.lang.String value){
        if(onPropSet(PROP_ID_profilingRuleId,value)){
            this._profilingRuleId = value;
            internalClearRefs(PROP_ID_profilingRuleId);
            
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
     * 快照时间: SNAPSHOT_TIME
     */
    public final java.sql.Timestamp getSnapshotTime(){
         onPropGet(PROP_ID_snapshotTime);
         return _snapshotTime;
    }

    /**
     * 快照时间: SNAPSHOT_TIME
     */
    public final void setSnapshotTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_snapshotTime,value)){
            this._snapshotTime = value;
            internalClearRefs(PROP_ID_snapshotTime);
            
        }
    }
    
    /**
     * 表级统计: TABLE_STATS
     */
    public final java.lang.String getTableStats(){
         onPropGet(PROP_ID_tableStats);
         return _tableStats;
    }

    /**
     * 表级统计: TABLE_STATS
     */
    public final void setTableStats(java.lang.String value){
        if(onPropSet(PROP_ID_tableStats,value)){
            this._tableStats = value;
            internalClearRefs(PROP_ID_tableStats);
            
        }
    }
    
    /**
     * 列级统计: COLUMN_STATS
     */
    public final java.lang.String getColumnStats(){
         onPropGet(PROP_ID_columnStats);
         return _columnStats;
    }

    /**
     * 列级统计: COLUMN_STATS
     */
    public final void setColumnStats(java.lang.String value){
        if(onPropSet(PROP_ID_columnStats,value)){
            this._columnStats = value;
            internalClearRefs(PROP_ID_columnStats);
            
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
     * 剖析规则
     */
    public final io.nop.metadata.dao.entity.NopMetaProfilingRule getProfilingRule(){
       return (io.nop.metadata.dao.entity.NopMetaProfilingRule)internalGetRefEntity(PROP_NAME_profilingRule);
    }

    public final void setProfilingRule(io.nop.metadata.dao.entity.NopMetaProfilingRule refEntity){
   
           if(refEntity == null){
           
                   this.setProfilingRuleId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_profilingRule, refEntity,()->{
           
                           this.setProfilingRuleId(refEntity.getProfilingRuleId());
                       
           });
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
       
   private io.nop.orm.component.JsonOrmComponent _tableStatsComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_tableStatsComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_tableStatsComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_tableStats);
      
   }

   public final io.nop.orm.component.JsonOrmComponent getTableStatsComponent(){
      if(_tableStatsComponent == null){
          _tableStatsComponent = new io.nop.orm.component.JsonOrmComponent();
          _tableStatsComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_tableStatsComponent);
      }
      return _tableStatsComponent;
   }

   private io.nop.orm.component.JsonOrmComponent _columnStatsComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_columnStatsComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_columnStatsComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_columnStats);
      
   }

   public final io.nop.orm.component.JsonOrmComponent getColumnStatsComponent(){
      if(_columnStatsComponent == null){
          _columnStatsComponent = new io.nop.orm.component.JsonOrmComponent();
          _columnStatsComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_columnStatsComponent);
      }
      return _columnStatsComponent;
   }

}
// resume CPD analysis - CPD-ON
