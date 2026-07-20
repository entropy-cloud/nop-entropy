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

import io.nop.metadata.dao.entity.NopMetaQualityScore;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  质量评分: nop_meta_quality_score
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopMetaQualityScore extends DynamicOrmEntity{
    
    /* 评分ID: QUALITY_SCORE_ID VARCHAR */
    public static final String PROP_NAME_qualityScoreId = "qualityScoreId";
    public static final int PROP_ID_qualityScoreId = 1;
    
    /* 逻辑表ID: META_TABLE_ID VARCHAR */
    public static final String PROP_NAME_metaTableId = "metaTableId";
    public static final int PROP_ID_metaTableId = 2;
    
    /* 评分时间: SCORE_TIME TIMESTAMP */
    public static final String PROP_NAME_scoreTime = "scoreTime";
    public static final int PROP_ID_scoreTime = 3;
    
    /* 总分: OVERALL_SCORE DOUBLE */
    public static final String PROP_NAME_overallScore = "overallScore";
    public static final int PROP_ID_overallScore = 4;
    
    /* 维度评分: DIMENSION_SCORES VARCHAR */
    public static final String PROP_NAME_dimensionScores = "dimensionScores";
    public static final int PROP_ID_dimensionScores = 5;
    
    /* 规则汇总: RULE_SUMMARY VARCHAR */
    public static final String PROP_NAME_ruleSummary = "ruleSummary";
    public static final int PROP_ID_ruleSummary = 6;
    
    /* 趋势: TREND VARCHAR */
    public static final String PROP_NAME_trend = "trend";
    public static final int PROP_ID_trend = 7;
    
    /* 扩展配置: EXT_CONFIG VARCHAR */
    public static final String PROP_NAME_extConfig = "extConfig";
    public static final int PROP_ID_extConfig = 8;
    
    /* 数据版本: VERSION BIGINT */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 9;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 10;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 11;
    
    /* 修改人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 12;
    
    /* 修改时间: UPDATE_TIME TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 13;
    
    /* 备注: REMARK VARCHAR */
    public static final String PROP_NAME_remark = "remark";
    public static final int PROP_ID_remark = 14;
    

    private static int _PROP_ID_BOUND = 15;

    
    /* relation: 逻辑表 */
    public static final String PROP_NAME_metaTable = "metaTable";
    
    /* component:  */
    public static final String PROP_NAME_dimensionScoresComponent = "dimensionScoresComponent";
    
    /* component:  */
    public static final String PROP_NAME_ruleSummaryComponent = "ruleSummaryComponent";
    
    /* component:  */
    public static final String PROP_NAME_trendComponent = "trendComponent";
    
    /* component:  */
    public static final String PROP_NAME_extConfigComponent = "extConfigComponent";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_qualityScoreId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_qualityScoreId};

    private static final String[] PROP_ID_TO_NAME = new String[15];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_qualityScoreId] = PROP_NAME_qualityScoreId;
          PROP_NAME_TO_ID.put(PROP_NAME_qualityScoreId, PROP_ID_qualityScoreId);
      
          PROP_ID_TO_NAME[PROP_ID_metaTableId] = PROP_NAME_metaTableId;
          PROP_NAME_TO_ID.put(PROP_NAME_metaTableId, PROP_ID_metaTableId);
      
          PROP_ID_TO_NAME[PROP_ID_scoreTime] = PROP_NAME_scoreTime;
          PROP_NAME_TO_ID.put(PROP_NAME_scoreTime, PROP_ID_scoreTime);
      
          PROP_ID_TO_NAME[PROP_ID_overallScore] = PROP_NAME_overallScore;
          PROP_NAME_TO_ID.put(PROP_NAME_overallScore, PROP_ID_overallScore);
      
          PROP_ID_TO_NAME[PROP_ID_dimensionScores] = PROP_NAME_dimensionScores;
          PROP_NAME_TO_ID.put(PROP_NAME_dimensionScores, PROP_ID_dimensionScores);
      
          PROP_ID_TO_NAME[PROP_ID_ruleSummary] = PROP_NAME_ruleSummary;
          PROP_NAME_TO_ID.put(PROP_NAME_ruleSummary, PROP_ID_ruleSummary);
      
          PROP_ID_TO_NAME[PROP_ID_trend] = PROP_NAME_trend;
          PROP_NAME_TO_ID.put(PROP_NAME_trend, PROP_ID_trend);
      
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

    
    /* 评分ID: QUALITY_SCORE_ID */
    private java.lang.String _qualityScoreId;
    
    /* 逻辑表ID: META_TABLE_ID */
    private java.lang.String _metaTableId;
    
    /* 评分时间: SCORE_TIME */
    private java.sql.Timestamp _scoreTime;
    
    /* 总分: OVERALL_SCORE */
    private java.lang.Double _overallScore;
    
    /* 维度评分: DIMENSION_SCORES */
    private java.lang.String _dimensionScores;
    
    /* 规则汇总: RULE_SUMMARY */
    private java.lang.String _ruleSummary;
    
    /* 趋势: TREND */
    private java.lang.String _trend;
    
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
    

    public _NopMetaQualityScore(){
        // for debug
    }

    protected NopMetaQualityScore newInstance(){
        NopMetaQualityScore entity = new NopMetaQualityScore();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopMetaQualityScore cloneInstance() {
        NopMetaQualityScore entity = newInstance();
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
      return "io.nop.metadata.dao.entity.NopMetaQualityScore";
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
    
        return buildSimpleId(PROP_ID_qualityScoreId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_qualityScoreId;
          
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
        
            case PROP_ID_qualityScoreId:
               return getQualityScoreId();
        
            case PROP_ID_metaTableId:
               return getMetaTableId();
        
            case PROP_ID_scoreTime:
               return getScoreTime();
        
            case PROP_ID_overallScore:
               return getOverallScore();
        
            case PROP_ID_dimensionScores:
               return getDimensionScores();
        
            case PROP_ID_ruleSummary:
               return getRuleSummary();
        
            case PROP_ID_trend:
               return getTrend();
        
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
        
            case PROP_ID_qualityScoreId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_qualityScoreId));
               }
               setQualityScoreId(typedValue);
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
        
            case PROP_ID_scoreTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_scoreTime));
               }
               setScoreTime(typedValue);
               break;
            }
        
            case PROP_ID_overallScore:{
               java.lang.Double typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toDouble(value,
                       err-> newTypeConversionError(PROP_NAME_overallScore));
               }
               setOverallScore(typedValue);
               break;
            }
        
            case PROP_ID_dimensionScores:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_dimensionScores));
               }
               setDimensionScores(typedValue);
               break;
            }
        
            case PROP_ID_ruleSummary:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_ruleSummary));
               }
               setRuleSummary(typedValue);
               break;
            }
        
            case PROP_ID_trend:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_trend));
               }
               setTrend(typedValue);
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
        
            case PROP_ID_qualityScoreId:{
               onInitProp(propId);
               this._qualityScoreId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_metaTableId:{
               onInitProp(propId);
               this._metaTableId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_scoreTime:{
               onInitProp(propId);
               this._scoreTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_overallScore:{
               onInitProp(propId);
               this._overallScore = (java.lang.Double)value;
               
               break;
            }
        
            case PROP_ID_dimensionScores:{
               onInitProp(propId);
               this._dimensionScores = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_ruleSummary:{
               onInitProp(propId);
               this._ruleSummary = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_trend:{
               onInitProp(propId);
               this._trend = (java.lang.String)value;
               
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
     * 评分ID: QUALITY_SCORE_ID
     */
    public final java.lang.String getQualityScoreId(){
         onPropGet(PROP_ID_qualityScoreId);
         return _qualityScoreId;
    }

    /**
     * 评分ID: QUALITY_SCORE_ID
     */
    public final void setQualityScoreId(java.lang.String value){
        if(onPropSet(PROP_ID_qualityScoreId,value)){
            this._qualityScoreId = value;
            internalClearRefs(PROP_ID_qualityScoreId);
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
     * 评分时间: SCORE_TIME
     */
    public final java.sql.Timestamp getScoreTime(){
         onPropGet(PROP_ID_scoreTime);
         return _scoreTime;
    }

    /**
     * 评分时间: SCORE_TIME
     */
    public final void setScoreTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_scoreTime,value)){
            this._scoreTime = value;
            internalClearRefs(PROP_ID_scoreTime);
            
        }
    }
    
    /**
     * 总分: OVERALL_SCORE
     */
    public final java.lang.Double getOverallScore(){
         onPropGet(PROP_ID_overallScore);
         return _overallScore;
    }

    /**
     * 总分: OVERALL_SCORE
     */
    public final void setOverallScore(java.lang.Double value){
        if(onPropSet(PROP_ID_overallScore,value)){
            this._overallScore = value;
            internalClearRefs(PROP_ID_overallScore);
            
        }
    }
    
    /**
     * 维度评分: DIMENSION_SCORES
     */
    public final java.lang.String getDimensionScores(){
         onPropGet(PROP_ID_dimensionScores);
         return _dimensionScores;
    }

    /**
     * 维度评分: DIMENSION_SCORES
     */
    public final void setDimensionScores(java.lang.String value){
        if(onPropSet(PROP_ID_dimensionScores,value)){
            this._dimensionScores = value;
            internalClearRefs(PROP_ID_dimensionScores);
            
        }
    }
    
    /**
     * 规则汇总: RULE_SUMMARY
     */
    public final java.lang.String getRuleSummary(){
         onPropGet(PROP_ID_ruleSummary);
         return _ruleSummary;
    }

    /**
     * 规则汇总: RULE_SUMMARY
     */
    public final void setRuleSummary(java.lang.String value){
        if(onPropSet(PROP_ID_ruleSummary,value)){
            this._ruleSummary = value;
            internalClearRefs(PROP_ID_ruleSummary);
            
        }
    }
    
    /**
     * 趋势: TREND
     */
    public final java.lang.String getTrend(){
         onPropGet(PROP_ID_trend);
         return _trend;
    }

    /**
     * 趋势: TREND
     */
    public final void setTrend(java.lang.String value){
        if(onPropSet(PROP_ID_trend,value)){
            this._trend = value;
            internalClearRefs(PROP_ID_trend);
            
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
       
   private io.nop.orm.component.JsonOrmComponent _dimensionScoresComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_dimensionScoresComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_dimensionScoresComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_dimensionScores);
      
   }

   public final io.nop.orm.component.JsonOrmComponent getDimensionScoresComponent(){
      if(_dimensionScoresComponent == null){
          _dimensionScoresComponent = new io.nop.orm.component.JsonOrmComponent();
          _dimensionScoresComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_dimensionScoresComponent);
      }
      return _dimensionScoresComponent;
   }

   private io.nop.orm.component.JsonOrmComponent _ruleSummaryComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_ruleSummaryComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_ruleSummaryComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_ruleSummary);
      
   }

   public final io.nop.orm.component.JsonOrmComponent getRuleSummaryComponent(){
      if(_ruleSummaryComponent == null){
          _ruleSummaryComponent = new io.nop.orm.component.JsonOrmComponent();
          _ruleSummaryComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_ruleSummaryComponent);
      }
      return _ruleSummaryComponent;
   }

   private io.nop.orm.component.JsonOrmComponent _trendComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_trendComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_trendComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_trend);
      
   }

   public final io.nop.orm.component.JsonOrmComponent getTrendComponent(){
      if(_trendComponent == null){
          _trendComponent = new io.nop.orm.component.JsonOrmComponent();
          _trendComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_trendComponent);
      }
      return _trendComponent;
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
