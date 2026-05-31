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

import io.nop.code.dao.entity.NopCodeDependency;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  文件依赖: nop_code_dependency
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopCodeDependency extends DynamicOrmEntity{
    
    /* 依赖ID: ID VARCHAR */
    public static final String PROP_NAME_id = "id";
    public static final int PROP_ID_id = 1;
    
    /* 索引ID: INDEX_ID VARCHAR */
    public static final String PROP_NAME_indexId = "indexId";
    public static final int PROP_ID_indexId = 2;
    
    /* 源文件路径: SOURCE_FILE_PATH VARCHAR */
    public static final String PROP_NAME_sourceFilePath = "sourceFilePath";
    public static final int PROP_ID_sourceFilePath = 3;
    
    /* 目标文件路径: TARGET_FILE_PATH VARCHAR */
    public static final String PROP_NAME_targetFilePath = "targetFilePath";
    public static final int PROP_ID_targetFilePath = 4;
    
    /* 导入语句: IMPORT_STATEMENT VARCHAR */
    public static final String PROP_NAME_importStatement = "importStatement";
    public static final int PROP_ID_importStatement = 5;
    
    /* 是否已解析: RESOLVED BOOLEAN */
    public static final String PROP_NAME_resolved = "resolved";
    public static final int PROP_ID_resolved = 6;
    

    private static int _PROP_ID_BOUND = 7;

    
    /* relation:  */
    public static final String PROP_NAME_index = "index";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_id);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_id};

    private static final String[] PROP_ID_TO_NAME = new String[7];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_id] = PROP_NAME_id;
          PROP_NAME_TO_ID.put(PROP_NAME_id, PROP_ID_id);
      
          PROP_ID_TO_NAME[PROP_ID_indexId] = PROP_NAME_indexId;
          PROP_NAME_TO_ID.put(PROP_NAME_indexId, PROP_ID_indexId);
      
          PROP_ID_TO_NAME[PROP_ID_sourceFilePath] = PROP_NAME_sourceFilePath;
          PROP_NAME_TO_ID.put(PROP_NAME_sourceFilePath, PROP_ID_sourceFilePath);
      
          PROP_ID_TO_NAME[PROP_ID_targetFilePath] = PROP_NAME_targetFilePath;
          PROP_NAME_TO_ID.put(PROP_NAME_targetFilePath, PROP_ID_targetFilePath);
      
          PROP_ID_TO_NAME[PROP_ID_importStatement] = PROP_NAME_importStatement;
          PROP_NAME_TO_ID.put(PROP_NAME_importStatement, PROP_ID_importStatement);
      
          PROP_ID_TO_NAME[PROP_ID_resolved] = PROP_NAME_resolved;
          PROP_NAME_TO_ID.put(PROP_NAME_resolved, PROP_ID_resolved);
      
    }

    
    /* 依赖ID: ID */
    private java.lang.String _id;
    
    /* 索引ID: INDEX_ID */
    private java.lang.String _indexId;
    
    /* 源文件路径: SOURCE_FILE_PATH */
    private java.lang.String _sourceFilePath;
    
    /* 目标文件路径: TARGET_FILE_PATH */
    private java.lang.String _targetFilePath;
    
    /* 导入语句: IMPORT_STATEMENT */
    private java.lang.String _importStatement;
    
    /* 是否已解析: RESOLVED */
    private java.lang.Boolean _resolved;
    

    public _NopCodeDependency(){
        // for debug
    }

    protected NopCodeDependency newInstance(){
        NopCodeDependency entity = new NopCodeDependency();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopCodeDependency cloneInstance() {
        NopCodeDependency entity = newInstance();
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
      return "io.nop.code.dao.entity.NopCodeDependency";
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
        
            case PROP_ID_sourceFilePath:
               return getSourceFilePath();
        
            case PROP_ID_targetFilePath:
               return getTargetFilePath();
        
            case PROP_ID_importStatement:
               return getImportStatement();
        
            case PROP_ID_resolved:
               return getResolved();
        
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
        
            case PROP_ID_sourceFilePath:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_sourceFilePath));
               }
               setSourceFilePath(typedValue);
               break;
            }
        
            case PROP_ID_targetFilePath:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_targetFilePath));
               }
               setTargetFilePath(typedValue);
               break;
            }
        
            case PROP_ID_importStatement:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_importStatement));
               }
               setImportStatement(typedValue);
               break;
            }
        
            case PROP_ID_resolved:{
               java.lang.Boolean typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toBoolean(value,
                       err-> newTypeConversionError(PROP_NAME_resolved));
               }
               setResolved(typedValue);
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
        
            case PROP_ID_sourceFilePath:{
               onInitProp(propId);
               this._sourceFilePath = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_targetFilePath:{
               onInitProp(propId);
               this._targetFilePath = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_importStatement:{
               onInitProp(propId);
               this._importStatement = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_resolved:{
               onInitProp(propId);
               this._resolved = (java.lang.Boolean)value;
               
               break;
            }
        
           default:
              super.orm_internalSet(propId,value);
        }
    }

    
    /**
     * 依赖ID: ID
     */
    public final java.lang.String getId(){
         onPropGet(PROP_ID_id);
         return _id;
    }

    /**
     * 依赖ID: ID
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
     * 源文件路径: SOURCE_FILE_PATH
     */
    public final java.lang.String getSourceFilePath(){
         onPropGet(PROP_ID_sourceFilePath);
         return _sourceFilePath;
    }

    /**
     * 源文件路径: SOURCE_FILE_PATH
     */
    public final void setSourceFilePath(java.lang.String value){
        if(onPropSet(PROP_ID_sourceFilePath,value)){
            this._sourceFilePath = value;
            internalClearRefs(PROP_ID_sourceFilePath);
            
        }
    }
    
    /**
     * 目标文件路径: TARGET_FILE_PATH
     */
    public final java.lang.String getTargetFilePath(){
         onPropGet(PROP_ID_targetFilePath);
         return _targetFilePath;
    }

    /**
     * 目标文件路径: TARGET_FILE_PATH
     */
    public final void setTargetFilePath(java.lang.String value){
        if(onPropSet(PROP_ID_targetFilePath,value)){
            this._targetFilePath = value;
            internalClearRefs(PROP_ID_targetFilePath);
            
        }
    }
    
    /**
     * 导入语句: IMPORT_STATEMENT
     */
    public final java.lang.String getImportStatement(){
         onPropGet(PROP_ID_importStatement);
         return _importStatement;
    }

    /**
     * 导入语句: IMPORT_STATEMENT
     */
    public final void setImportStatement(java.lang.String value){
        if(onPropSet(PROP_ID_importStatement,value)){
            this._importStatement = value;
            internalClearRefs(PROP_ID_importStatement);
            
        }
    }
    
    /**
     * 是否已解析: RESOLVED
     */
    public final java.lang.Boolean getResolved(){
         onPropGet(PROP_ID_resolved);
         return _resolved;
    }

    /**
     * 是否已解析: RESOLVED
     */
    public final void setResolved(java.lang.Boolean value){
        if(onPropSet(PROP_ID_resolved,value)){
            this._resolved = value;
            internalClearRefs(PROP_ID_resolved);
            
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
       
}
// resume CPD analysis - CPD-ON
