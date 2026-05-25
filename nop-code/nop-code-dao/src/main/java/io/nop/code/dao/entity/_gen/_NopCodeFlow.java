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

import io.nop.code.dao.entity.NopCodeFlow;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  执行流: nop_code_flow
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopCodeFlow extends DynamicOrmEntity{
    
    /* 流ID: ID VARCHAR */
    public static final String PROP_NAME_id = "id";
    public static final int PROP_ID_id = 1;
    
    /* 索引ID: INDEX_ID VARCHAR */
    public static final String PROP_NAME_indexId = "indexId";
    public static final int PROP_ID_indexId = 2;
    
    /* 流名称: NAME VARCHAR */
    public static final String PROP_NAME_name = "name";
    public static final int PROP_ID_name = 3;
    
    /* 入口符号ID: ENTRY_POINT_ID VARCHAR */
    public static final String PROP_NAME_entryPointId = "entryPointId";
    public static final int PROP_ID_entryPointId = 4;
    
    /* 入口全限定名: ENTRY_POINT_QUALIFIED_NAME VARCHAR */
    public static final String PROP_NAME_entryPointQualifiedName = "entryPointQualifiedName";
    public static final int PROP_ID_entryPointQualifiedName = 5;
    
    /* 最大追踪深度: DEPTH INTEGER */
    public static final String PROP_NAME_depth = "depth";
    public static final int PROP_ID_depth = 6;
    
    /* 符号总数: SYMBOL_COUNT INTEGER */
    public static final String PROP_NAME_symbolCount = "symbolCount";
    public static final int PROP_ID_symbolCount = 7;
    
    /* 文件扩散评分: FILE_SPREAD DOUBLE */
    public static final String PROP_NAME_fileSpread = "fileSpread";
    public static final int PROP_ID_fileSpread = 8;
    
    /* 外部调用评分: EXTERNAL_SCORE DOUBLE */
    public static final String PROP_NAME_externalScore = "externalScore";
    public static final int PROP_ID_externalScore = 9;
    
    /* 安全敏感评分: SECURITY_SCORE DOUBLE */
    public static final String PROP_NAME_securityScore = "securityScore";
    public static final int PROP_ID_securityScore = 10;
    
    /* 测试覆盖缺口评分: TEST_GAP DOUBLE */
    public static final String PROP_NAME_testGap = "testGap";
    public static final int PROP_ID_testGap = 11;
    
    /* 深度复杂度评分: DEPTH_SCORE DOUBLE */
    public static final String PROP_NAME_depthScore = "depthScore";
    public static final int PROP_ID_depthScore = 12;
    
    /* 综合关键度评分: OVERALL_SCORE DOUBLE */
    public static final String PROP_NAME_overallScore = "overallScore";
    public static final int PROP_ID_overallScore = 13;
    
    /* 状态: STATUS VARCHAR */
    public static final String PROP_NAME_status = "status";
    public static final int PROP_ID_status = 14;
    
    /* 创建时间: CREATED_TIME DATETIME */
    public static final String PROP_NAME_createdTime = "createdTime";
    public static final int PROP_ID_createdTime = 15;
    
    /* 修改时间: MODIFIED_TIME DATETIME */
    public static final String PROP_NAME_modifiedTime = "modifiedTime";
    public static final int PROP_ID_modifiedTime = 16;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 17;
    
    /* 修改人: MODIFIED_BY VARCHAR */
    public static final String PROP_NAME_modifiedBy = "modifiedBy";
    public static final int PROP_ID_modifiedBy = 18;
    

    private static int _PROP_ID_BOUND = 19;

    
    /* relation:  */
    public static final String PROP_NAME_index = "index";
    
    /* relation:  */
    public static final String PROP_NAME_entryPoint = "entryPoint";
    
    /* relation: 成员 */
    public static final String PROP_NAME_memberships = "memberships";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_id);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_id};

    private static final String[] PROP_ID_TO_NAME = new String[19];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_id] = PROP_NAME_id;
          PROP_NAME_TO_ID.put(PROP_NAME_id, PROP_ID_id);
      
          PROP_ID_TO_NAME[PROP_ID_indexId] = PROP_NAME_indexId;
          PROP_NAME_TO_ID.put(PROP_NAME_indexId, PROP_ID_indexId);
      
          PROP_ID_TO_NAME[PROP_ID_name] = PROP_NAME_name;
          PROP_NAME_TO_ID.put(PROP_NAME_name, PROP_ID_name);
      
          PROP_ID_TO_NAME[PROP_ID_entryPointId] = PROP_NAME_entryPointId;
          PROP_NAME_TO_ID.put(PROP_NAME_entryPointId, PROP_ID_entryPointId);
      
          PROP_ID_TO_NAME[PROP_ID_entryPointQualifiedName] = PROP_NAME_entryPointQualifiedName;
          PROP_NAME_TO_ID.put(PROP_NAME_entryPointQualifiedName, PROP_ID_entryPointQualifiedName);
      
          PROP_ID_TO_NAME[PROP_ID_depth] = PROP_NAME_depth;
          PROP_NAME_TO_ID.put(PROP_NAME_depth, PROP_ID_depth);
      
          PROP_ID_TO_NAME[PROP_ID_symbolCount] = PROP_NAME_symbolCount;
          PROP_NAME_TO_ID.put(PROP_NAME_symbolCount, PROP_ID_symbolCount);
      
          PROP_ID_TO_NAME[PROP_ID_fileSpread] = PROP_NAME_fileSpread;
          PROP_NAME_TO_ID.put(PROP_NAME_fileSpread, PROP_ID_fileSpread);
      
          PROP_ID_TO_NAME[PROP_ID_externalScore] = PROP_NAME_externalScore;
          PROP_NAME_TO_ID.put(PROP_NAME_externalScore, PROP_ID_externalScore);
      
          PROP_ID_TO_NAME[PROP_ID_securityScore] = PROP_NAME_securityScore;
          PROP_NAME_TO_ID.put(PROP_NAME_securityScore, PROP_ID_securityScore);
      
          PROP_ID_TO_NAME[PROP_ID_testGap] = PROP_NAME_testGap;
          PROP_NAME_TO_ID.put(PROP_NAME_testGap, PROP_ID_testGap);
      
          PROP_ID_TO_NAME[PROP_ID_depthScore] = PROP_NAME_depthScore;
          PROP_NAME_TO_ID.put(PROP_NAME_depthScore, PROP_ID_depthScore);
      
          PROP_ID_TO_NAME[PROP_ID_overallScore] = PROP_NAME_overallScore;
          PROP_NAME_TO_ID.put(PROP_NAME_overallScore, PROP_ID_overallScore);
      
          PROP_ID_TO_NAME[PROP_ID_status] = PROP_NAME_status;
          PROP_NAME_TO_ID.put(PROP_NAME_status, PROP_ID_status);
      
          PROP_ID_TO_NAME[PROP_ID_createdTime] = PROP_NAME_createdTime;
          PROP_NAME_TO_ID.put(PROP_NAME_createdTime, PROP_ID_createdTime);
      
          PROP_ID_TO_NAME[PROP_ID_modifiedTime] = PROP_NAME_modifiedTime;
          PROP_NAME_TO_ID.put(PROP_NAME_modifiedTime, PROP_ID_modifiedTime);
      
          PROP_ID_TO_NAME[PROP_ID_createdBy] = PROP_NAME_createdBy;
          PROP_NAME_TO_ID.put(PROP_NAME_createdBy, PROP_ID_createdBy);
      
          PROP_ID_TO_NAME[PROP_ID_modifiedBy] = PROP_NAME_modifiedBy;
          PROP_NAME_TO_ID.put(PROP_NAME_modifiedBy, PROP_ID_modifiedBy);
      
    }

    
    /* 流ID: ID */
    private java.lang.String _id;
    
    /* 索引ID: INDEX_ID */
    private java.lang.String _indexId;
    
    /* 流名称: NAME */
    private java.lang.String _name;
    
    /* 入口符号ID: ENTRY_POINT_ID */
    private java.lang.String _entryPointId;
    
    /* 入口全限定名: ENTRY_POINT_QUALIFIED_NAME */
    private java.lang.String _entryPointQualifiedName;
    
    /* 最大追踪深度: DEPTH */
    private java.lang.Integer _depth;
    
    /* 符号总数: SYMBOL_COUNT */
    private java.lang.Integer _symbolCount;
    
    /* 文件扩散评分: FILE_SPREAD */
    private java.lang.Double _fileSpread;
    
    /* 外部调用评分: EXTERNAL_SCORE */
    private java.lang.Double _externalScore;
    
    /* 安全敏感评分: SECURITY_SCORE */
    private java.lang.Double _securityScore;
    
    /* 测试覆盖缺口评分: TEST_GAP */
    private java.lang.Double _testGap;
    
    /* 深度复杂度评分: DEPTH_SCORE */
    private java.lang.Double _depthScore;
    
    /* 综合关键度评分: OVERALL_SCORE */
    private java.lang.Double _overallScore;
    
    /* 状态: STATUS */
    private java.lang.String _status;
    
    /* 创建时间: CREATED_TIME */
    private java.sql.Timestamp _createdTime;
    
    /* 修改时间: MODIFIED_TIME */
    private java.sql.Timestamp _modifiedTime;
    
    /* 创建人: CREATED_BY */
    private java.lang.String _createdBy;
    
    /* 修改人: MODIFIED_BY */
    private java.lang.String _modifiedBy;
    

    public _NopCodeFlow(){
        // for debug
    }

    protected NopCodeFlow newInstance(){
        NopCodeFlow entity = new NopCodeFlow();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopCodeFlow cloneInstance() {
        NopCodeFlow entity = newInstance();
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
      return "io.nop.code.dao.entity.NopCodeFlow";
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
        
            case PROP_ID_name:
               return getName();
        
            case PROP_ID_entryPointId:
               return getEntryPointId();
        
            case PROP_ID_entryPointQualifiedName:
               return getEntryPointQualifiedName();
        
            case PROP_ID_depth:
               return getDepth();
        
            case PROP_ID_symbolCount:
               return getSymbolCount();
        
            case PROP_ID_fileSpread:
               return getFileSpread();
        
            case PROP_ID_externalScore:
               return getExternalScore();
        
            case PROP_ID_securityScore:
               return getSecurityScore();
        
            case PROP_ID_testGap:
               return getTestGap();
        
            case PROP_ID_depthScore:
               return getDepthScore();
        
            case PROP_ID_overallScore:
               return getOverallScore();
        
            case PROP_ID_status:
               return getStatus();
        
            case PROP_ID_createdTime:
               return getCreatedTime();
        
            case PROP_ID_modifiedTime:
               return getModifiedTime();
        
            case PROP_ID_createdBy:
               return getCreatedBy();
        
            case PROP_ID_modifiedBy:
               return getModifiedBy();
        
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
        
            case PROP_ID_name:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_name));
               }
               setName(typedValue);
               break;
            }
        
            case PROP_ID_entryPointId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_entryPointId));
               }
               setEntryPointId(typedValue);
               break;
            }
        
            case PROP_ID_entryPointQualifiedName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_entryPointQualifiedName));
               }
               setEntryPointQualifiedName(typedValue);
               break;
            }
        
            case PROP_ID_depth:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_depth));
               }
               setDepth(typedValue);
               break;
            }
        
            case PROP_ID_symbolCount:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_symbolCount));
               }
               setSymbolCount(typedValue);
               break;
            }
        
            case PROP_ID_fileSpread:{
               java.lang.Double typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toDouble(value,
                       err-> newTypeConversionError(PROP_NAME_fileSpread));
               }
               setFileSpread(typedValue);
               break;
            }
        
            case PROP_ID_externalScore:{
               java.lang.Double typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toDouble(value,
                       err-> newTypeConversionError(PROP_NAME_externalScore));
               }
               setExternalScore(typedValue);
               break;
            }
        
            case PROP_ID_securityScore:{
               java.lang.Double typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toDouble(value,
                       err-> newTypeConversionError(PROP_NAME_securityScore));
               }
               setSecurityScore(typedValue);
               break;
            }
        
            case PROP_ID_testGap:{
               java.lang.Double typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toDouble(value,
                       err-> newTypeConversionError(PROP_NAME_testGap));
               }
               setTestGap(typedValue);
               break;
            }
        
            case PROP_ID_depthScore:{
               java.lang.Double typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toDouble(value,
                       err-> newTypeConversionError(PROP_NAME_depthScore));
               }
               setDepthScore(typedValue);
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
        
            case PROP_ID_status:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_status));
               }
               setStatus(typedValue);
               break;
            }
        
            case PROP_ID_createdTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_createdTime));
               }
               setCreatedTime(typedValue);
               break;
            }
        
            case PROP_ID_modifiedTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_modifiedTime));
               }
               setModifiedTime(typedValue);
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
        
            case PROP_ID_modifiedBy:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_modifiedBy));
               }
               setModifiedBy(typedValue);
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
        
            case PROP_ID_name:{
               onInitProp(propId);
               this._name = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_entryPointId:{
               onInitProp(propId);
               this._entryPointId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_entryPointQualifiedName:{
               onInitProp(propId);
               this._entryPointQualifiedName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_depth:{
               onInitProp(propId);
               this._depth = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_symbolCount:{
               onInitProp(propId);
               this._symbolCount = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_fileSpread:{
               onInitProp(propId);
               this._fileSpread = (java.lang.Double)value;
               
               break;
            }
        
            case PROP_ID_externalScore:{
               onInitProp(propId);
               this._externalScore = (java.lang.Double)value;
               
               break;
            }
        
            case PROP_ID_securityScore:{
               onInitProp(propId);
               this._securityScore = (java.lang.Double)value;
               
               break;
            }
        
            case PROP_ID_testGap:{
               onInitProp(propId);
               this._testGap = (java.lang.Double)value;
               
               break;
            }
        
            case PROP_ID_depthScore:{
               onInitProp(propId);
               this._depthScore = (java.lang.Double)value;
               
               break;
            }
        
            case PROP_ID_overallScore:{
               onInitProp(propId);
               this._overallScore = (java.lang.Double)value;
               
               break;
            }
        
            case PROP_ID_status:{
               onInitProp(propId);
               this._status = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_createdTime:{
               onInitProp(propId);
               this._createdTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_modifiedTime:{
               onInitProp(propId);
               this._modifiedTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_createdBy:{
               onInitProp(propId);
               this._createdBy = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_modifiedBy:{
               onInitProp(propId);
               this._modifiedBy = (java.lang.String)value;
               
               break;
            }
        
           default:
              super.orm_internalSet(propId,value);
        }
    }

    
    /**
     * 流ID: ID
     */
    public final java.lang.String getId(){
         onPropGet(PROP_ID_id);
         return _id;
    }

    /**
     * 流ID: ID
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
     * 流名称: NAME
     */
    public final java.lang.String getName(){
         onPropGet(PROP_ID_name);
         return _name;
    }

    /**
     * 流名称: NAME
     */
    public final void setName(java.lang.String value){
        if(onPropSet(PROP_ID_name,value)){
            this._name = value;
            internalClearRefs(PROP_ID_name);
            
        }
    }
    
    /**
     * 入口符号ID: ENTRY_POINT_ID
     */
    public final java.lang.String getEntryPointId(){
         onPropGet(PROP_ID_entryPointId);
         return _entryPointId;
    }

    /**
     * 入口符号ID: ENTRY_POINT_ID
     */
    public final void setEntryPointId(java.lang.String value){
        if(onPropSet(PROP_ID_entryPointId,value)){
            this._entryPointId = value;
            internalClearRefs(PROP_ID_entryPointId);
            
        }
    }
    
    /**
     * 入口全限定名: ENTRY_POINT_QUALIFIED_NAME
     */
    public final java.lang.String getEntryPointQualifiedName(){
         onPropGet(PROP_ID_entryPointQualifiedName);
         return _entryPointQualifiedName;
    }

    /**
     * 入口全限定名: ENTRY_POINT_QUALIFIED_NAME
     */
    public final void setEntryPointQualifiedName(java.lang.String value){
        if(onPropSet(PROP_ID_entryPointQualifiedName,value)){
            this._entryPointQualifiedName = value;
            internalClearRefs(PROP_ID_entryPointQualifiedName);
            
        }
    }
    
    /**
     * 最大追踪深度: DEPTH
     */
    public final java.lang.Integer getDepth(){
         onPropGet(PROP_ID_depth);
         return _depth;
    }

    /**
     * 最大追踪深度: DEPTH
     */
    public final void setDepth(java.lang.Integer value){
        if(onPropSet(PROP_ID_depth,value)){
            this._depth = value;
            internalClearRefs(PROP_ID_depth);
            
        }
    }
    
    /**
     * 符号总数: SYMBOL_COUNT
     */
    public final java.lang.Integer getSymbolCount(){
         onPropGet(PROP_ID_symbolCount);
         return _symbolCount;
    }

    /**
     * 符号总数: SYMBOL_COUNT
     */
    public final void setSymbolCount(java.lang.Integer value){
        if(onPropSet(PROP_ID_symbolCount,value)){
            this._symbolCount = value;
            internalClearRefs(PROP_ID_symbolCount);
            
        }
    }
    
    /**
     * 文件扩散评分: FILE_SPREAD
     */
    public final java.lang.Double getFileSpread(){
         onPropGet(PROP_ID_fileSpread);
         return _fileSpread;
    }

    /**
     * 文件扩散评分: FILE_SPREAD
     */
    public final void setFileSpread(java.lang.Double value){
        if(onPropSet(PROP_ID_fileSpread,value)){
            this._fileSpread = value;
            internalClearRefs(PROP_ID_fileSpread);
            
        }
    }
    
    /**
     * 外部调用评分: EXTERNAL_SCORE
     */
    public final java.lang.Double getExternalScore(){
         onPropGet(PROP_ID_externalScore);
         return _externalScore;
    }

    /**
     * 外部调用评分: EXTERNAL_SCORE
     */
    public final void setExternalScore(java.lang.Double value){
        if(onPropSet(PROP_ID_externalScore,value)){
            this._externalScore = value;
            internalClearRefs(PROP_ID_externalScore);
            
        }
    }
    
    /**
     * 安全敏感评分: SECURITY_SCORE
     */
    public final java.lang.Double getSecurityScore(){
         onPropGet(PROP_ID_securityScore);
         return _securityScore;
    }

    /**
     * 安全敏感评分: SECURITY_SCORE
     */
    public final void setSecurityScore(java.lang.Double value){
        if(onPropSet(PROP_ID_securityScore,value)){
            this._securityScore = value;
            internalClearRefs(PROP_ID_securityScore);
            
        }
    }
    
    /**
     * 测试覆盖缺口评分: TEST_GAP
     */
    public final java.lang.Double getTestGap(){
         onPropGet(PROP_ID_testGap);
         return _testGap;
    }

    /**
     * 测试覆盖缺口评分: TEST_GAP
     */
    public final void setTestGap(java.lang.Double value){
        if(onPropSet(PROP_ID_testGap,value)){
            this._testGap = value;
            internalClearRefs(PROP_ID_testGap);
            
        }
    }
    
    /**
     * 深度复杂度评分: DEPTH_SCORE
     */
    public final java.lang.Double getDepthScore(){
         onPropGet(PROP_ID_depthScore);
         return _depthScore;
    }

    /**
     * 深度复杂度评分: DEPTH_SCORE
     */
    public final void setDepthScore(java.lang.Double value){
        if(onPropSet(PROP_ID_depthScore,value)){
            this._depthScore = value;
            internalClearRefs(PROP_ID_depthScore);
            
        }
    }
    
    /**
     * 综合关键度评分: OVERALL_SCORE
     */
    public final java.lang.Double getOverallScore(){
         onPropGet(PROP_ID_overallScore);
         return _overallScore;
    }

    /**
     * 综合关键度评分: OVERALL_SCORE
     */
    public final void setOverallScore(java.lang.Double value){
        if(onPropSet(PROP_ID_overallScore,value)){
            this._overallScore = value;
            internalClearRefs(PROP_ID_overallScore);
            
        }
    }
    
    /**
     * 状态: STATUS
     */
    public final java.lang.String getStatus(){
         onPropGet(PROP_ID_status);
         return _status;
    }

    /**
     * 状态: STATUS
     */
    public final void setStatus(java.lang.String value){
        if(onPropSet(PROP_ID_status,value)){
            this._status = value;
            internalClearRefs(PROP_ID_status);
            
        }
    }
    
    /**
     * 创建时间: CREATED_TIME
     */
    public final java.sql.Timestamp getCreatedTime(){
         onPropGet(PROP_ID_createdTime);
         return _createdTime;
    }

    /**
     * 创建时间: CREATED_TIME
     */
    public final void setCreatedTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_createdTime,value)){
            this._createdTime = value;
            internalClearRefs(PROP_ID_createdTime);
            
        }
    }
    
    /**
     * 修改时间: MODIFIED_TIME
     */
    public final java.sql.Timestamp getModifiedTime(){
         onPropGet(PROP_ID_modifiedTime);
         return _modifiedTime;
    }

    /**
     * 修改时间: MODIFIED_TIME
     */
    public final void setModifiedTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_modifiedTime,value)){
            this._modifiedTime = value;
            internalClearRefs(PROP_ID_modifiedTime);
            
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
     * 修改人: MODIFIED_BY
     */
    public final java.lang.String getModifiedBy(){
         onPropGet(PROP_ID_modifiedBy);
         return _modifiedBy;
    }

    /**
     * 修改人: MODIFIED_BY
     */
    public final void setModifiedBy(java.lang.String value){
        if(onPropSet(PROP_ID_modifiedBy,value)){
            this._modifiedBy = value;
            internalClearRefs(PROP_ID_modifiedBy);
            
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
    public final io.nop.code.dao.entity.NopCodeSymbol getEntryPoint(){
       return (io.nop.code.dao.entity.NopCodeSymbol)internalGetRefEntity(PROP_NAME_entryPoint);
    }

    public final void setEntryPoint(io.nop.code.dao.entity.NopCodeSymbol refEntity){
   
           if(refEntity == null){
           
                   this.setEntryPointId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_entryPoint, refEntity,()->{
           
                           this.setEntryPointId(refEntity.getId());
                       
           });
           }
       
    }
       
    private final OrmEntitySet<io.nop.code.dao.entity.NopCodeFlowMembership> _memberships = new OrmEntitySet<>(this, PROP_NAME_memberships,
        io.nop.code.dao.entity.NopCodeFlowMembership.PROP_NAME_flow, null,io.nop.code.dao.entity.NopCodeFlowMembership.class);

    /**
     * 成员。 refPropName: flow, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<io.nop.code.dao.entity.NopCodeFlowMembership> getMemberships(){
       return _memberships;
    }
       
}
// resume CPD analysis - CPD-ON
