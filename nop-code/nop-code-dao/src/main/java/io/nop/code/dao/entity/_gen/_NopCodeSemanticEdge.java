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

import io.nop.code.dao.entity.NopCodeSemanticEdge;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  语义边: nop_code_semantic_edge
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopCodeSemanticEdge extends DynamicOrmEntity{
    
    /* 边ID: ID VARCHAR */
    public static final String PROP_NAME_id = "id";
    public static final int PROP_ID_id = 1;
    
    /* 索引ID: INDEX_ID VARCHAR */
    public static final String PROP_NAME_indexId = "indexId";
    public static final int PROP_ID_indexId = 2;
    
    /* 源符号ID: SOURCE_SYMBOL_ID VARCHAR */
    public static final String PROP_NAME_sourceSymbolId = "sourceSymbolId";
    public static final int PROP_ID_sourceSymbolId = 3;
    
    /* 目标符号ID: TARGET_SYMBOL_ID VARCHAR */
    public static final String PROP_NAME_targetSymbolId = "targetSymbolId";
    public static final int PROP_ID_targetSymbolId = 4;
    
    /* 有向: DIRECTED BOOLEAN */
    public static final String PROP_NAME_directed = "directed";
    public static final int PROP_ID_directed = 5;
    
    /* 关系类型: RELATION_TYPE VARCHAR */
    public static final String PROP_NAME_relationType = "relationType";
    public static final int PROP_ID_relationType = 6;
    
    /* 置信度级别: CONFIDENCE INTEGER */
    public static final String PROP_NAME_confidence = "confidence";
    public static final int PROP_ID_confidence = 7;
    
    /* 置信度分数: CONFIDENCE_SCORE DOUBLE */
    public static final String PROP_NAME_confidenceScore = "confidenceScore";
    public static final int PROP_ID_confidenceScore = 8;
    
    /* 原因: RATIONALE VARCHAR */
    public static final String PROP_NAME_rationale = "rationale";
    public static final int PROP_ID_rationale = 9;
    
    /* 提取器ID: EXTRACTOR_ID VARCHAR */
    public static final String PROP_NAME_extractorId = "extractorId";
    public static final int PROP_ID_extractorId = 10;
    
    /* 扩展数据: EXT_DATA VARCHAR */
    public static final String PROP_NAME_extData = "extData";
    public static final int PROP_ID_extData = 11;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 12;
    
    /* 创建时间: CREATE_TIME BIGINT */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 13;
    
    /* 逻辑删除: DEL_FLAG SMALLINT */
    public static final String PROP_NAME_delFlag = "delFlag";
    public static final int PROP_ID_delFlag = 14;
    

    private static int _PROP_ID_BOUND = 15;

    
    /* relation:  */
    public static final String PROP_NAME_index = "index";
    
    /* relation:  */
    public static final String PROP_NAME_sourceSymbol = "sourceSymbol";
    
    /* relation:  */
    public static final String PROP_NAME_targetSymbol = "targetSymbol";
    
    /* component:  */
    public static final String PROP_NAME_extDataComponent = "extDataComponent";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_id);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_id};

    private static final String[] PROP_ID_TO_NAME = new String[15];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_id] = PROP_NAME_id;
          PROP_NAME_TO_ID.put(PROP_NAME_id, PROP_ID_id);
      
          PROP_ID_TO_NAME[PROP_ID_indexId] = PROP_NAME_indexId;
          PROP_NAME_TO_ID.put(PROP_NAME_indexId, PROP_ID_indexId);
      
          PROP_ID_TO_NAME[PROP_ID_sourceSymbolId] = PROP_NAME_sourceSymbolId;
          PROP_NAME_TO_ID.put(PROP_NAME_sourceSymbolId, PROP_ID_sourceSymbolId);
      
          PROP_ID_TO_NAME[PROP_ID_targetSymbolId] = PROP_NAME_targetSymbolId;
          PROP_NAME_TO_ID.put(PROP_NAME_targetSymbolId, PROP_ID_targetSymbolId);
      
          PROP_ID_TO_NAME[PROP_ID_directed] = PROP_NAME_directed;
          PROP_NAME_TO_ID.put(PROP_NAME_directed, PROP_ID_directed);
      
          PROP_ID_TO_NAME[PROP_ID_relationType] = PROP_NAME_relationType;
          PROP_NAME_TO_ID.put(PROP_NAME_relationType, PROP_ID_relationType);
      
          PROP_ID_TO_NAME[PROP_ID_confidence] = PROP_NAME_confidence;
          PROP_NAME_TO_ID.put(PROP_NAME_confidence, PROP_ID_confidence);
      
          PROP_ID_TO_NAME[PROP_ID_confidenceScore] = PROP_NAME_confidenceScore;
          PROP_NAME_TO_ID.put(PROP_NAME_confidenceScore, PROP_ID_confidenceScore);
      
          PROP_ID_TO_NAME[PROP_ID_rationale] = PROP_NAME_rationale;
          PROP_NAME_TO_ID.put(PROP_NAME_rationale, PROP_ID_rationale);
      
          PROP_ID_TO_NAME[PROP_ID_extractorId] = PROP_NAME_extractorId;
          PROP_NAME_TO_ID.put(PROP_NAME_extractorId, PROP_ID_extractorId);
      
          PROP_ID_TO_NAME[PROP_ID_extData] = PROP_NAME_extData;
          PROP_NAME_TO_ID.put(PROP_NAME_extData, PROP_ID_extData);
      
          PROP_ID_TO_NAME[PROP_ID_createdBy] = PROP_NAME_createdBy;
          PROP_NAME_TO_ID.put(PROP_NAME_createdBy, PROP_ID_createdBy);
      
          PROP_ID_TO_NAME[PROP_ID_createTime] = PROP_NAME_createTime;
          PROP_NAME_TO_ID.put(PROP_NAME_createTime, PROP_ID_createTime);
      
          PROP_ID_TO_NAME[PROP_ID_delFlag] = PROP_NAME_delFlag;
          PROP_NAME_TO_ID.put(PROP_NAME_delFlag, PROP_ID_delFlag);
      
    }

    
    /* 边ID: ID */
    private java.lang.String _id;
    
    /* 索引ID: INDEX_ID */
    private java.lang.String _indexId;
    
    /* 源符号ID: SOURCE_SYMBOL_ID */
    private java.lang.String _sourceSymbolId;
    
    /* 目标符号ID: TARGET_SYMBOL_ID */
    private java.lang.String _targetSymbolId;
    
    /* 有向: DIRECTED */
    private java.lang.Boolean _directed;
    
    /* 关系类型: RELATION_TYPE */
    private java.lang.String _relationType;
    
    /* 置信度级别: CONFIDENCE */
    private java.lang.Integer _confidence;
    
    /* 置信度分数: CONFIDENCE_SCORE */
    private java.lang.Double _confidenceScore;
    
    /* 原因: RATIONALE */
    private java.lang.String _rationale;
    
    /* 提取器ID: EXTRACTOR_ID */
    private java.lang.String _extractorId;
    
    /* 扩展数据: EXT_DATA */
    private java.lang.String _extData;
    
    /* 创建人: CREATED_BY */
    private java.lang.String _createdBy;
    
    /* 创建时间: CREATE_TIME */
    private java.lang.Long _createTime;
    
    /* 逻辑删除: DEL_FLAG */
    private java.lang.Integer _delFlag;
    

    public _NopCodeSemanticEdge(){
        // for debug
    }

    protected NopCodeSemanticEdge newInstance(){
        NopCodeSemanticEdge entity = new NopCodeSemanticEdge();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopCodeSemanticEdge cloneInstance() {
        NopCodeSemanticEdge entity = newInstance();
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
      return "io.nop.code.dao.entity.NopCodeSemanticEdge";
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
        
            case PROP_ID_sourceSymbolId:
               return getSourceSymbolId();
        
            case PROP_ID_targetSymbolId:
               return getTargetSymbolId();
        
            case PROP_ID_directed:
               return getDirected();
        
            case PROP_ID_relationType:
               return getRelationType();
        
            case PROP_ID_confidence:
               return getConfidence();
        
            case PROP_ID_confidenceScore:
               return getConfidenceScore();
        
            case PROP_ID_rationale:
               return getRationale();
        
            case PROP_ID_extractorId:
               return getExtractorId();
        
            case PROP_ID_extData:
               return getExtData();
        
            case PROP_ID_createdBy:
               return getCreatedBy();
        
            case PROP_ID_createTime:
               return getCreateTime();
        
            case PROP_ID_delFlag:
               return getDelFlag();
        
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
        
            case PROP_ID_sourceSymbolId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_sourceSymbolId));
               }
               setSourceSymbolId(typedValue);
               break;
            }
        
            case PROP_ID_targetSymbolId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_targetSymbolId));
               }
               setTargetSymbolId(typedValue);
               break;
            }
        
            case PROP_ID_directed:{
               java.lang.Boolean typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toBoolean(value,
                       err-> newTypeConversionError(PROP_NAME_directed));
               }
               setDirected(typedValue);
               break;
            }
        
            case PROP_ID_relationType:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_relationType));
               }
               setRelationType(typedValue);
               break;
            }
        
            case PROP_ID_confidence:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_confidence));
               }
               setConfidence(typedValue);
               break;
            }
        
            case PROP_ID_confidenceScore:{
               java.lang.Double typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toDouble(value,
                       err-> newTypeConversionError(PROP_NAME_confidenceScore));
               }
               setConfidenceScore(typedValue);
               break;
            }
        
            case PROP_ID_rationale:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_rationale));
               }
               setRationale(typedValue);
               break;
            }
        
            case PROP_ID_extractorId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_extractorId));
               }
               setExtractorId(typedValue);
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
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_createTime));
               }
               setCreateTime(typedValue);
               break;
            }
        
            case PROP_ID_delFlag:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_delFlag));
               }
               setDelFlag(typedValue);
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
        
            case PROP_ID_sourceSymbolId:{
               onInitProp(propId);
               this._sourceSymbolId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_targetSymbolId:{
               onInitProp(propId);
               this._targetSymbolId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_directed:{
               onInitProp(propId);
               this._directed = (java.lang.Boolean)value;
               
               break;
            }
        
            case PROP_ID_relationType:{
               onInitProp(propId);
               this._relationType = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_confidence:{
               onInitProp(propId);
               this._confidence = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_confidenceScore:{
               onInitProp(propId);
               this._confidenceScore = (java.lang.Double)value;
               
               break;
            }
        
            case PROP_ID_rationale:{
               onInitProp(propId);
               this._rationale = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_extractorId:{
               onInitProp(propId);
               this._extractorId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_extData:{
               onInitProp(propId);
               this._extData = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_createdBy:{
               onInitProp(propId);
               this._createdBy = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_createTime:{
               onInitProp(propId);
               this._createTime = (java.lang.Long)value;
               
               break;
            }
        
            case PROP_ID_delFlag:{
               onInitProp(propId);
               this._delFlag = (java.lang.Integer)value;
               
               break;
            }
        
           default:
              super.orm_internalSet(propId,value);
        }
    }

    
    /**
     * 边ID: ID
     */
    public final java.lang.String getId(){
         onPropGet(PROP_ID_id);
         return _id;
    }

    /**
     * 边ID: ID
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
     * 源符号ID: SOURCE_SYMBOL_ID
     */
    public final java.lang.String getSourceSymbolId(){
         onPropGet(PROP_ID_sourceSymbolId);
         return _sourceSymbolId;
    }

    /**
     * 源符号ID: SOURCE_SYMBOL_ID
     */
    public final void setSourceSymbolId(java.lang.String value){
        if(onPropSet(PROP_ID_sourceSymbolId,value)){
            this._sourceSymbolId = value;
            internalClearRefs(PROP_ID_sourceSymbolId);
            
        }
    }
    
    /**
     * 目标符号ID: TARGET_SYMBOL_ID
     */
    public final java.lang.String getTargetSymbolId(){
         onPropGet(PROP_ID_targetSymbolId);
         return _targetSymbolId;
    }

    /**
     * 目标符号ID: TARGET_SYMBOL_ID
     */
    public final void setTargetSymbolId(java.lang.String value){
        if(onPropSet(PROP_ID_targetSymbolId,value)){
            this._targetSymbolId = value;
            internalClearRefs(PROP_ID_targetSymbolId);
            
        }
    }
    
    /**
     * 有向: DIRECTED
     */
    public final java.lang.Boolean getDirected(){
         onPropGet(PROP_ID_directed);
         return _directed;
    }

    /**
     * 有向: DIRECTED
     */
    public final void setDirected(java.lang.Boolean value){
        if(onPropSet(PROP_ID_directed,value)){
            this._directed = value;
            internalClearRefs(PROP_ID_directed);
            
        }
    }
    
    /**
     * 关系类型: RELATION_TYPE
     */
    public final java.lang.String getRelationType(){
         onPropGet(PROP_ID_relationType);
         return _relationType;
    }

    /**
     * 关系类型: RELATION_TYPE
     */
    public final void setRelationType(java.lang.String value){
        if(onPropSet(PROP_ID_relationType,value)){
            this._relationType = value;
            internalClearRefs(PROP_ID_relationType);
            
        }
    }
    
    /**
     * 置信度级别: CONFIDENCE
     */
    public final java.lang.Integer getConfidence(){
         onPropGet(PROP_ID_confidence);
         return _confidence;
    }

    /**
     * 置信度级别: CONFIDENCE
     */
    public final void setConfidence(java.lang.Integer value){
        if(onPropSet(PROP_ID_confidence,value)){
            this._confidence = value;
            internalClearRefs(PROP_ID_confidence);
            
        }
    }
    
    /**
     * 置信度分数: CONFIDENCE_SCORE
     */
    public final java.lang.Double getConfidenceScore(){
         onPropGet(PROP_ID_confidenceScore);
         return _confidenceScore;
    }

    /**
     * 置信度分数: CONFIDENCE_SCORE
     */
    public final void setConfidenceScore(java.lang.Double value){
        if(onPropSet(PROP_ID_confidenceScore,value)){
            this._confidenceScore = value;
            internalClearRefs(PROP_ID_confidenceScore);
            
        }
    }
    
    /**
     * 原因: RATIONALE
     */
    public final java.lang.String getRationale(){
         onPropGet(PROP_ID_rationale);
         return _rationale;
    }

    /**
     * 原因: RATIONALE
     */
    public final void setRationale(java.lang.String value){
        if(onPropSet(PROP_ID_rationale,value)){
            this._rationale = value;
            internalClearRefs(PROP_ID_rationale);
            
        }
    }
    
    /**
     * 提取器ID: EXTRACTOR_ID
     */
    public final java.lang.String getExtractorId(){
         onPropGet(PROP_ID_extractorId);
         return _extractorId;
    }

    /**
     * 提取器ID: EXTRACTOR_ID
     */
    public final void setExtractorId(java.lang.String value){
        if(onPropSet(PROP_ID_extractorId,value)){
            this._extractorId = value;
            internalClearRefs(PROP_ID_extractorId);
            
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
    public final java.lang.Long getCreateTime(){
         onPropGet(PROP_ID_createTime);
         return _createTime;
    }

    /**
     * 创建时间: CREATE_TIME
     */
    public final void setCreateTime(java.lang.Long value){
        if(onPropSet(PROP_ID_createTime,value)){
            this._createTime = value;
            internalClearRefs(PROP_ID_createTime);
            
        }
    }
    
    /**
     * 逻辑删除: DEL_FLAG
     */
    public final java.lang.Integer getDelFlag(){
         onPropGet(PROP_ID_delFlag);
         return _delFlag;
    }

    /**
     * 逻辑删除: DEL_FLAG
     */
    public final void setDelFlag(java.lang.Integer value){
        if(onPropSet(PROP_ID_delFlag,value)){
            this._delFlag = value;
            internalClearRefs(PROP_ID_delFlag);
            
        }
    }
    
    /**
     * 
     */
    public final io.nop.code.dao.entity.NopCodeIndex getIndex(){
       return (io.nop.code.dao.entity.NopCodeIndex)internalGetRefEntity(PROP_NAME_index);
    }

    public final void setIndex(io.nop.code.dao.entity.NopCodeIndex refEntity){
   
           if(refEntity == null){
           
                   this.setIndexId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_index, refEntity,()->{
           
                           this.setIndexId(refEntity.getId());
                       
           });
           }
       
    }
       
    /**
     * 
     */
    public final io.nop.code.dao.entity.NopCodeSymbol getSourceSymbol(){
       return (io.nop.code.dao.entity.NopCodeSymbol)internalGetRefEntity(PROP_NAME_sourceSymbol);
    }

    public final void setSourceSymbol(io.nop.code.dao.entity.NopCodeSymbol refEntity){
   
           if(refEntity == null){
           
                   this.setSourceSymbolId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_sourceSymbol, refEntity,()->{
           
                           this.setSourceSymbolId(refEntity.getId());
                       
           });
           }
       
    }
       
    /**
     * 
     */
    public final io.nop.code.dao.entity.NopCodeSymbol getTargetSymbol(){
       return (io.nop.code.dao.entity.NopCodeSymbol)internalGetRefEntity(PROP_NAME_targetSymbol);
    }

    public final void setTargetSymbol(io.nop.code.dao.entity.NopCodeSymbol refEntity){
   
           if(refEntity == null){
           
                   this.setTargetSymbolId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_targetSymbol, refEntity,()->{
           
                           this.setTargetSymbolId(refEntity.getId());
                       
           });
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
