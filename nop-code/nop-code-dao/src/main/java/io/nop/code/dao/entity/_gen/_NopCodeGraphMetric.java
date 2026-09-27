package io.nop.code.dao.entity._gen;

import io.nop.orm.model.IEntityModel;
import io.nop.orm.support.DynamicOrmEntity;
import io.nop.orm.support.OrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.orm.IOrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code

import io.nop.api.core.convert.ConvertHelper;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;

import io.nop.code.dao.entity.NopCodeGraphMetric;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  图度量: nop_code_graph_metric
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopCodeGraphMetric extends DynamicOrmEntity{
    
    /* 行ID: ID VARCHAR */
    public static final String PROP_NAME_id = "id";
    public static final int PROP_ID_id = 1;
    
    /* 索引ID: INDEX_ID VARCHAR */
    public static final String PROP_NAME_indexId = "indexId";
    public static final int PROP_ID_indexId = 2;
    
    /* 度量类型: METRIC_TYPE VARCHAR */
    public static final String PROP_NAME_metricType = "metricType";
    public static final int PROP_ID_metricType = 3;
    
    /* 符号ID: SYMBOL_ID VARCHAR */
    public static final String PROP_NAME_symbolId = "symbolId";
    public static final int PROP_ID_symbolId = 4;
    
    /* 社区ID: COMMUNITY_ID INTEGER */
    public static final String PROP_NAME_communityId = "communityId";
    public static final int PROP_ID_communityId = 5;
    
    /* 评分: SCORE DOUBLE */
    public static final String PROP_NAME_score = "score";
    public static final int PROP_ID_score = 6;
    
    /* 排名: RANK_NO INTEGER */
    public static final String PROP_NAME_rankNo = "rankNo";
    public static final int PROP_ID_rankNo = 7;
    
    /* 入口点类型: ENTRY_POINT_TYPE VARCHAR */
    public static final String PROP_NAME_entryPointType = "entryPointType";
    public static final int PROP_ID_entryPointType = 8;
    
    /* 计算时间: COMPUTED_AT DATETIME */
    public static final String PROP_NAME_computedAt = "computedAt";
    public static final int PROP_ID_computedAt = 9;
    
    /* 扩展数据: EXT_DATA VARCHAR */
    public static final String PROP_NAME_extData = "extData";
    public static final int PROP_ID_extData = 10;
    
    /* 创建时间: CREATE_TIME DATETIME */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 11;
    
    /* 更新时间: UPDATE_TIME DATETIME */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 12;
    

    private static int _PROP_ID_BOUND = 13;

    
    /* component:  */
    public static final String PROP_NAME_extDataComponent = "extDataComponent";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_id);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_id};

    private static final String[] PROP_ID_TO_NAME = new String[13];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_id] = PROP_NAME_id;
          PROP_NAME_TO_ID.put(PROP_NAME_id, PROP_ID_id);
      
          PROP_ID_TO_NAME[PROP_ID_indexId] = PROP_NAME_indexId;
          PROP_NAME_TO_ID.put(PROP_NAME_indexId, PROP_ID_indexId);
      
          PROP_ID_TO_NAME[PROP_ID_metricType] = PROP_NAME_metricType;
          PROP_NAME_TO_ID.put(PROP_NAME_metricType, PROP_ID_metricType);
      
          PROP_ID_TO_NAME[PROP_ID_symbolId] = PROP_NAME_symbolId;
          PROP_NAME_TO_ID.put(PROP_NAME_symbolId, PROP_ID_symbolId);
      
          PROP_ID_TO_NAME[PROP_ID_communityId] = PROP_NAME_communityId;
          PROP_NAME_TO_ID.put(PROP_NAME_communityId, PROP_ID_communityId);
      
          PROP_ID_TO_NAME[PROP_ID_score] = PROP_NAME_score;
          PROP_NAME_TO_ID.put(PROP_NAME_score, PROP_ID_score);
      
          PROP_ID_TO_NAME[PROP_ID_rankNo] = PROP_NAME_rankNo;
          PROP_NAME_TO_ID.put(PROP_NAME_rankNo, PROP_ID_rankNo);
      
          PROP_ID_TO_NAME[PROP_ID_entryPointType] = PROP_NAME_entryPointType;
          PROP_NAME_TO_ID.put(PROP_NAME_entryPointType, PROP_ID_entryPointType);
      
          PROP_ID_TO_NAME[PROP_ID_computedAt] = PROP_NAME_computedAt;
          PROP_NAME_TO_ID.put(PROP_NAME_computedAt, PROP_ID_computedAt);
      
          PROP_ID_TO_NAME[PROP_ID_extData] = PROP_NAME_extData;
          PROP_NAME_TO_ID.put(PROP_NAME_extData, PROP_ID_extData);
      
          PROP_ID_TO_NAME[PROP_ID_createTime] = PROP_NAME_createTime;
          PROP_NAME_TO_ID.put(PROP_NAME_createTime, PROP_ID_createTime);
      
          PROP_ID_TO_NAME[PROP_ID_updateTime] = PROP_NAME_updateTime;
          PROP_NAME_TO_ID.put(PROP_NAME_updateTime, PROP_ID_updateTime);
      
    }

    
    /* 行ID: ID */
    private java.lang.String _id;
    
    /* 索引ID: INDEX_ID */
    private java.lang.String _indexId;
    
    /* 度量类型: METRIC_TYPE */
    private java.lang.String _metricType;
    
    /* 符号ID: SYMBOL_ID */
    private java.lang.String _symbolId;
    
    /* 社区ID: COMMUNITY_ID */
    private java.lang.Integer _communityId;
    
    /* 评分: SCORE */
    private java.lang.Double _score;
    
    /* 排名: RANK_NO */
    private java.lang.Integer _rankNo;
    
    /* 入口点类型: ENTRY_POINT_TYPE */
    private java.lang.String _entryPointType;
    
    /* 计算时间: COMPUTED_AT */
    private java.sql.Timestamp _computedAt;
    
    /* 扩展数据: EXT_DATA */
    private java.lang.String _extData;
    
    /* 创建时间: CREATE_TIME */
    private java.sql.Timestamp _createTime;
    
    /* 更新时间: UPDATE_TIME */
    private java.sql.Timestamp _updateTime;
    

    public _NopCodeGraphMetric(){
        // for debug
    }

    protected NopCodeGraphMetric newInstance(){
        NopCodeGraphMetric entity = new NopCodeGraphMetric();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopCodeGraphMetric cloneInstance() {
        NopCodeGraphMetric entity = newInstance();
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
      return "io.nop.code.dao.entity.NopCodeGraphMetric";
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
    
        return buildSimpleId(PROP_ID_id);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_id;
          
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
        
            case PROP_ID_id:
               return getId();
        
            case PROP_ID_indexId:
               return getIndexId();
        
            case PROP_ID_metricType:
               return getMetricType();
        
            case PROP_ID_symbolId:
               return getSymbolId();
        
            case PROP_ID_communityId:
               return getCommunityId();
        
            case PROP_ID_score:
               return getScore();
        
            case PROP_ID_rankNo:
               return getRankNo();
        
            case PROP_ID_entryPointType:
               return getEntryPointType();
        
            case PROP_ID_computedAt:
               return getComputedAt();
        
            case PROP_ID_extData:
               return getExtData();
        
            case PROP_ID_createTime:
               return getCreateTime();
        
            case PROP_ID_updateTime:
               return getUpdateTime();
        
           default:
              return super.orm_propValue(propId);
        }
    }

    

    @Override
    public void orm_propValue(int propId, Object value){
        switch(propId){
        
            case PROP_ID_id:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_id));
               }
               setId(typedValue);
               break;
            }
        
            case PROP_ID_indexId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_indexId));
               }
               setIndexId(typedValue);
               break;
            }
        
            case PROP_ID_metricType:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_metricType));
               }
               setMetricType(typedValue);
               break;
            }
        
            case PROP_ID_symbolId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_symbolId));
               }
               setSymbolId(typedValue);
               break;
            }
        
            case PROP_ID_communityId:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_communityId));
               }
               setCommunityId(typedValue);
               break;
            }
        
            case PROP_ID_score:{
               java.lang.Double typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toDouble(value,
                       err-> newTypeConversionError(PROP_NAME_score));
               }
               setScore(typedValue);
               break;
            }
        
            case PROP_ID_rankNo:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_rankNo));
               }
               setRankNo(typedValue);
               break;
            }
        
            case PROP_ID_entryPointType:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_entryPointType));
               }
               setEntryPointType(typedValue);
               break;
            }
        
            case PROP_ID_computedAt:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_computedAt));
               }
               setComputedAt(typedValue);
               break;
            }
        
            case PROP_ID_extData:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_extData));
               }
               setExtData(typedValue);
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
        
            case PROP_ID_id:{
               onInitProp(propId);
               this._id = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_indexId:{
               onInitProp(propId);
               this._indexId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_metricType:{
               onInitProp(propId);
               this._metricType = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_symbolId:{
               onInitProp(propId);
               this._symbolId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_communityId:{
               onInitProp(propId);
               this._communityId = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_score:{
               onInitProp(propId);
               this._score = (java.lang.Double)value;
               
               break;
            }
        
            case PROP_ID_rankNo:{
               onInitProp(propId);
               this._rankNo = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_entryPointType:{
               onInitProp(propId);
               this._entryPointType = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_computedAt:{
               onInitProp(propId);
               this._computedAt = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_extData:{
               onInitProp(propId);
               this._extData = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_createTime:{
               onInitProp(propId);
               this._createTime = (java.sql.Timestamp)value;
               
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
     * 行ID: ID
     */
    public final java.lang.String getId(){
         onPropGet(PROP_ID_id);
         return _id;
    }

    /**
     * 行ID: ID
     */
    public final void setId(java.lang.String value){
        if(onPropSet(PROP_ID_id,value)){
            this._id = value;
            internalClearRefs(PROP_ID_id);
            orm_id();
        }
    }
    
    /**
     * 索引ID: INDEX_ID
     */
    public final java.lang.String getIndexId(){
         onPropGet(PROP_ID_indexId);
         return _indexId;
    }

    /**
     * 索引ID: INDEX_ID
     */
    public final void setIndexId(java.lang.String value){
        if(onPropSet(PROP_ID_indexId,value)){
            this._indexId = value;
            internalClearRefs(PROP_ID_indexId);
            
        }
    }
    
    /**
     * 度量类型: METRIC_TYPE
     */
    public final java.lang.String getMetricType(){
         onPropGet(PROP_ID_metricType);
         return _metricType;
    }

    /**
     * 度量类型: METRIC_TYPE
     */
    public final void setMetricType(java.lang.String value){
        if(onPropSet(PROP_ID_metricType,value)){
            this._metricType = value;
            internalClearRefs(PROP_ID_metricType);
            
        }
    }
    
    /**
     * 符号ID: SYMBOL_ID
     */
    public final java.lang.String getSymbolId(){
         onPropGet(PROP_ID_symbolId);
         return _symbolId;
    }

    /**
     * 符号ID: SYMBOL_ID
     */
    public final void setSymbolId(java.lang.String value){
        if(onPropSet(PROP_ID_symbolId,value)){
            this._symbolId = value;
            internalClearRefs(PROP_ID_symbolId);
            
        }
    }
    
    /**
     * 社区ID: COMMUNITY_ID
     */
    public final java.lang.Integer getCommunityId(){
         onPropGet(PROP_ID_communityId);
         return _communityId;
    }

    /**
     * 社区ID: COMMUNITY_ID
     */
    public final void setCommunityId(java.lang.Integer value){
        if(onPropSet(PROP_ID_communityId,value)){
            this._communityId = value;
            internalClearRefs(PROP_ID_communityId);
            
        }
    }
    
    /**
     * 评分: SCORE
     */
    public final java.lang.Double getScore(){
         onPropGet(PROP_ID_score);
         return _score;
    }

    /**
     * 评分: SCORE
     */
    public final void setScore(java.lang.Double value){
        if(onPropSet(PROP_ID_score,value)){
            this._score = value;
            internalClearRefs(PROP_ID_score);
            
        }
    }
    
    /**
     * 排名: RANK_NO
     */
    public final java.lang.Integer getRankNo(){
         onPropGet(PROP_ID_rankNo);
         return _rankNo;
    }

    /**
     * 排名: RANK_NO
     */
    public final void setRankNo(java.lang.Integer value){
        if(onPropSet(PROP_ID_rankNo,value)){
            this._rankNo = value;
            internalClearRefs(PROP_ID_rankNo);
            
        }
    }
    
    /**
     * 入口点类型: ENTRY_POINT_TYPE
     */
    public final java.lang.String getEntryPointType(){
         onPropGet(PROP_ID_entryPointType);
         return _entryPointType;
    }

    /**
     * 入口点类型: ENTRY_POINT_TYPE
     */
    public final void setEntryPointType(java.lang.String value){
        if(onPropSet(PROP_ID_entryPointType,value)){
            this._entryPointType = value;
            internalClearRefs(PROP_ID_entryPointType);
            
        }
    }
    
    /**
     * 计算时间: COMPUTED_AT
     */
    public final java.sql.Timestamp getComputedAt(){
         onPropGet(PROP_ID_computedAt);
         return _computedAt;
    }

    /**
     * 计算时间: COMPUTED_AT
     */
    public final void setComputedAt(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_computedAt,value)){
            this._computedAt = value;
            internalClearRefs(PROP_ID_computedAt);
            
        }
    }
    
    /**
     * 扩展数据: EXT_DATA
     */
    public final java.lang.String getExtData(){
         onPropGet(PROP_ID_extData);
         return _extData;
    }

    /**
     * 扩展数据: EXT_DATA
     */
    public final void setExtData(java.lang.String value){
        if(onPropSet(PROP_ID_extData,value)){
            this._extData = value;
            internalClearRefs(PROP_ID_extData);
            
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
     * 更新时间: UPDATE_TIME
     */
    public final java.sql.Timestamp getUpdateTime(){
         onPropGet(PROP_ID_updateTime);
         return _updateTime;
    }

    /**
     * 更新时间: UPDATE_TIME
     */
    public final void setUpdateTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_updateTime,value)){
            this._updateTime = value;
            internalClearRefs(PROP_ID_updateTime);
            
        }
    }
    
   private io.nop.orm.component.JsonOrmComponent _extDataComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_extDataComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_extDataComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_extData);
      
   }

   public final io.nop.orm.component.JsonOrmComponent getExtDataComponent(){
      if(_extDataComponent == null){
          _extDataComponent = new io.nop.orm.component.JsonOrmComponent();
          _extDataComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_extDataComponent);
      }
      return _extDataComponent;
   }

}
// resume CPD analysis - CPD-ON
