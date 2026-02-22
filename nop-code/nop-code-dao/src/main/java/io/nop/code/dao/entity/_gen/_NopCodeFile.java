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

import io.nop.code.dao.entity.NopCodeFile;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  代码文件: nop_code_file
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopCodeFile extends DynamicOrmEntity{
    
    /* 文件ID: id VARCHAR */
    public static final String PROP_NAME_id = "id";
    public static final int PROP_ID_id = 1;
    
    /* 索引ID: index_id VARCHAR */
    public static final String PROP_NAME_indexId = "indexId";
    public static final int PROP_ID_indexId = 2;
    
    /* 文件路径: file_path VARCHAR */
    public static final String PROP_NAME_filePath = "filePath";
    public static final int PROP_ID_filePath = 3;
    
    /* 包名: package_name VARCHAR */
    public static final String PROP_NAME_packageName = "packageName";
    public static final int PROP_ID_packageName = 4;
    
    /* 语言: language VARCHAR */
    public static final String PROP_NAME_language = "language";
    public static final int PROP_ID_language = 5;
    
    /* 行数: line_count INTEGER */
    public static final String PROP_NAME_lineCount = "lineCount";
    public static final int PROP_ID_lineCount = 6;
    
    /* 导入列表: imports VARCHAR */
    public static final String PROP_NAME_imports = "imports";
    public static final int PROP_ID_imports = 7;
    
    /* 源代码: source_code VARCHAR */
    public static final String PROP_NAME_sourceCode = "sourceCode";
    public static final int PROP_ID_sourceCode = 8;
    

    private static int _PROP_ID_BOUND = 9;

    
    /* relation:  */
    public static final String PROP_NAME_index = "index";
    
    /* relation: 符号列表 */
    public static final String PROP_NAME_symbols = "symbols";
    
    /* relation: 引用 */
    public static final String PROP_NAME_usages = "usages";
    
    /* relation: 调用 */
    public static final String PROP_NAME_calls = "calls";
    
    /* component:  */
    public static final String PROP_NAME_importsComponent = "importsComponent";
    
    /* component:  */
    public static final String PROP_NAME_sourceCodeComponent = "sourceCodeComponent";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_id);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_id};

    private static final String[] PROP_ID_TO_NAME = new String[9];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_id] = PROP_NAME_id;
          PROP_NAME_TO_ID.put(PROP_NAME_id, PROP_ID_id);
      
          PROP_ID_TO_NAME[PROP_ID_indexId] = PROP_NAME_indexId;
          PROP_NAME_TO_ID.put(PROP_NAME_indexId, PROP_ID_indexId);
      
          PROP_ID_TO_NAME[PROP_ID_filePath] = PROP_NAME_filePath;
          PROP_NAME_TO_ID.put(PROP_NAME_filePath, PROP_ID_filePath);
      
          PROP_ID_TO_NAME[PROP_ID_packageName] = PROP_NAME_packageName;
          PROP_NAME_TO_ID.put(PROP_NAME_packageName, PROP_ID_packageName);
      
          PROP_ID_TO_NAME[PROP_ID_language] = PROP_NAME_language;
          PROP_NAME_TO_ID.put(PROP_NAME_language, PROP_ID_language);
      
          PROP_ID_TO_NAME[PROP_ID_lineCount] = PROP_NAME_lineCount;
          PROP_NAME_TO_ID.put(PROP_NAME_lineCount, PROP_ID_lineCount);
      
          PROP_ID_TO_NAME[PROP_ID_imports] = PROP_NAME_imports;
          PROP_NAME_TO_ID.put(PROP_NAME_imports, PROP_ID_imports);
      
          PROP_ID_TO_NAME[PROP_ID_sourceCode] = PROP_NAME_sourceCode;
          PROP_NAME_TO_ID.put(PROP_NAME_sourceCode, PROP_ID_sourceCode);
      
    }

    
    /* 文件ID: id */
    private java.lang.String _id;
    
    /* 索引ID: index_id */
    private java.lang.String _indexId;
    
    /* 文件路径: file_path */
    private java.lang.String _filePath;
    
    /* 包名: package_name */
    private java.lang.String _packageName;
    
    /* 语言: language */
    private java.lang.String _language;
    
    /* 行数: line_count */
    private java.lang.Integer _lineCount;
    
    /* 导入列表: imports */
    private java.lang.String _imports;
    
    /* 源代码: source_code */
    private java.lang.String _sourceCode;
    

    public _NopCodeFile(){
        // for debug
    }

    protected NopCodeFile newInstance(){
        NopCodeFile entity = new NopCodeFile();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopCodeFile cloneInstance() {
        NopCodeFile entity = newInstance();
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
      return "io.nop.code.dao.entity.NopCodeFile";
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
        
            case PROP_ID_filePath:
               return getFilePath();
        
            case PROP_ID_packageName:
               return getPackageName();
        
            case PROP_ID_language:
               return getLanguage();
        
            case PROP_ID_lineCount:
               return getLineCount();
        
            case PROP_ID_imports:
               return getImports();
        
            case PROP_ID_sourceCode:
               return getSourceCode();
        
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
        
            case PROP_ID_filePath:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_filePath));
               }
               setFilePath(typedValue);
               break;
            }
        
            case PROP_ID_packageName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_packageName));
               }
               setPackageName(typedValue);
               break;
            }
        
            case PROP_ID_language:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_language));
               }
               setLanguage(typedValue);
               break;
            }
        
            case PROP_ID_lineCount:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_lineCount));
               }
               setLineCount(typedValue);
               break;
            }
        
            case PROP_ID_imports:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_imports));
               }
               setImports(typedValue);
               break;
            }
        
            case PROP_ID_sourceCode:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_sourceCode));
               }
               setSourceCode(typedValue);
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
        
            case PROP_ID_filePath:{
               onInitProp(propId);
               this._filePath = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_packageName:{
               onInitProp(propId);
               this._packageName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_language:{
               onInitProp(propId);
               this._language = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_lineCount:{
               onInitProp(propId);
               this._lineCount = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_imports:{
               onInitProp(propId);
               this._imports = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_sourceCode:{
               onInitProp(propId);
               this._sourceCode = (java.lang.String)value;
               
               break;
            }
        
           default:
              super.orm_internalSet(propId,value);
        }
    }

    
    /**
     * 文件ID: id
     */
    public final java.lang.String getId(){
         onPropGet(PROP_ID_id);
         return _id;
    }

    /**
     * 文件ID: id
     */
    public final void setId(java.lang.String value){
        if(onPropSet(PROP_ID_id,value)){
            this._id = value;
            internalClearRefs(PROP_ID_id);
            orm_id();
        }
    }
    
    /**
     * 索引ID: index_id
     */
    public final java.lang.String getIndexId(){
         onPropGet(PROP_ID_indexId);
         return _indexId;
    }

    /**
     * 索引ID: index_id
     */
    public final void setIndexId(java.lang.String value){
        if(onPropSet(PROP_ID_indexId,value)){
            this._indexId = value;
            internalClearRefs(PROP_ID_indexId);
            
        }
    }
    
    /**
     * 文件路径: file_path
     */
    public final java.lang.String getFilePath(){
         onPropGet(PROP_ID_filePath);
         return _filePath;
    }

    /**
     * 文件路径: file_path
     */
    public final void setFilePath(java.lang.String value){
        if(onPropSet(PROP_ID_filePath,value)){
            this._filePath = value;
            internalClearRefs(PROP_ID_filePath);
            
        }
    }
    
    /**
     * 包名: package_name
     */
    public final java.lang.String getPackageName(){
         onPropGet(PROP_ID_packageName);
         return _packageName;
    }

    /**
     * 包名: package_name
     */
    public final void setPackageName(java.lang.String value){
        if(onPropSet(PROP_ID_packageName,value)){
            this._packageName = value;
            internalClearRefs(PROP_ID_packageName);
            
        }
    }
    
    /**
     * 语言: language
     */
    public final java.lang.String getLanguage(){
         onPropGet(PROP_ID_language);
         return _language;
    }

    /**
     * 语言: language
     */
    public final void setLanguage(java.lang.String value){
        if(onPropSet(PROP_ID_language,value)){
            this._language = value;
            internalClearRefs(PROP_ID_language);
            
        }
    }
    
    /**
     * 行数: line_count
     */
    public final java.lang.Integer getLineCount(){
         onPropGet(PROP_ID_lineCount);
         return _lineCount;
    }

    /**
     * 行数: line_count
     */
    public final void setLineCount(java.lang.Integer value){
        if(onPropSet(PROP_ID_lineCount,value)){
            this._lineCount = value;
            internalClearRefs(PROP_ID_lineCount);
            
        }
    }
    
    /**
     * 导入列表: imports
     */
    public final java.lang.String getImports(){
         onPropGet(PROP_ID_imports);
         return _imports;
    }

    /**
     * 导入列表: imports
     */
    public final void setImports(java.lang.String value){
        if(onPropSet(PROP_ID_imports,value)){
            this._imports = value;
            internalClearRefs(PROP_ID_imports);
            
        }
    }
    
    /**
     * 源代码: source_code
     */
    public final java.lang.String getSourceCode(){
         onPropGet(PROP_ID_sourceCode);
         return _sourceCode;
    }

    /**
     * 源代码: source_code
     */
    public final void setSourceCode(java.lang.String value){
        if(onPropSet(PROP_ID_sourceCode,value)){
            this._sourceCode = value;
            internalClearRefs(PROP_ID_sourceCode);
            
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
       
    private final OrmEntitySet<io.nop.code.dao.entity.NopCodeSymbol> _symbols = new OrmEntitySet<>(this, PROP_NAME_symbols,
        io.nop.code.dao.entity.NopCodeSymbol.PROP_NAME_file, null,io.nop.code.dao.entity.NopCodeSymbol.class);

    /**
     * 符号列表。 refPropName: file, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<io.nop.code.dao.entity.NopCodeSymbol> getSymbols(){
       return _symbols;
    }
       
    private final OrmEntitySet<io.nop.code.dao.entity.NopCodeUsage> _usages = new OrmEntitySet<>(this, PROP_NAME_usages,
        io.nop.code.dao.entity.NopCodeUsage.PROP_NAME_file, null,io.nop.code.dao.entity.NopCodeUsage.class);

    /**
     * 引用。 refPropName: file, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<io.nop.code.dao.entity.NopCodeUsage> getUsages(){
       return _usages;
    }
       
    private final OrmEntitySet<io.nop.code.dao.entity.NopCodeCall> _calls = new OrmEntitySet<>(this, PROP_NAME_calls,
        io.nop.code.dao.entity.NopCodeCall.PROP_NAME_file, null,io.nop.code.dao.entity.NopCodeCall.class);

    /**
     * 调用。 refPropName: file, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<io.nop.code.dao.entity.NopCodeCall> getCalls(){
       return _calls;
    }
       
   private io.nop.orm.component.JsonOrmComponent _importsComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_importsComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_importsComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_imports);
      
   }

   public final io.nop.orm.component.JsonOrmComponent getImportsComponent(){
      if(_importsComponent == null){
          _importsComponent = new io.nop.orm.component.JsonOrmComponent();
          _importsComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_importsComponent);
      }
      return _importsComponent;
   }

   private io.nop.orm.component.JsonOrmComponent _sourceCodeComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_sourceCodeComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_sourceCodeComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_sourceCode);
      
   }

   public final io.nop.orm.component.JsonOrmComponent getSourceCodeComponent(){
      if(_sourceCodeComponent == null){
          _sourceCodeComponent = new io.nop.orm.component.JsonOrmComponent();
          _sourceCodeComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_sourceCodeComponent);
      }
      return _sourceCodeComponent;
   }

}
// resume CPD analysis - CPD-ON
