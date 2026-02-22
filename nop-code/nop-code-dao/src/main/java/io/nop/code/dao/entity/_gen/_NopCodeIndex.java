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

import io.nop.code.dao.entity.NopCodeIndex;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  代码索引: nop_code_index
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopCodeIndex extends DynamicOrmEntity{
    
    /* 索引ID: id VARCHAR */
    public static final String PROP_NAME_id = "id";
    public static final int PROP_ID_id = 1;
    
    /* 索引名称: name VARCHAR */
    public static final String PROP_NAME_name = "name";
    public static final int PROP_ID_name = 2;
    
    /* 根路径: root_path VARCHAR */
    public static final String PROP_NAME_rootPath = "rootPath";
    public static final int PROP_ID_rootPath = 3;
    
    /* 编程语言: language VARCHAR */
    public static final String PROP_NAME_language = "language";
    public static final int PROP_ID_language = 4;
    
    /* 符号数量: symbol_count INTEGER */
    public static final String PROP_NAME_symbolCount = "symbolCount";
    public static final int PROP_ID_symbolCount = 5;
    
    /* 文件数量: file_count INTEGER */
    public static final String PROP_NAME_fileCount = "fileCount";
    public static final int PROP_ID_fileCount = 6;
    
    /* 状态: status VARCHAR */
    public static final String PROP_NAME_status = "status";
    public static final int PROP_ID_status = 7;
    

    private static int _PROP_ID_BOUND = 8;

    
    /* relation: 文件列表 */
    public static final String PROP_NAME_files = "files";
    
    /* relation: 符号列表 */
    public static final String PROP_NAME_symbols = "symbols";
    
    /* relation: 引用 */
    public static final String PROP_NAME_usages = "usages";
    
    /* relation: 调用 */
    public static final String PROP_NAME_calls = "calls";
    
    /* relation: 继承 */
    public static final String PROP_NAME_inheritances = "inheritances";
    
    /* relation: 注解使用 */
    public static final String PROP_NAME_annotationUsages = "annotationUsages";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_id);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_id};

    private static final String[] PROP_ID_TO_NAME = new String[8];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_id] = PROP_NAME_id;
          PROP_NAME_TO_ID.put(PROP_NAME_id, PROP_ID_id);
      
          PROP_ID_TO_NAME[PROP_ID_name] = PROP_NAME_name;
          PROP_NAME_TO_ID.put(PROP_NAME_name, PROP_ID_name);
      
          PROP_ID_TO_NAME[PROP_ID_rootPath] = PROP_NAME_rootPath;
          PROP_NAME_TO_ID.put(PROP_NAME_rootPath, PROP_ID_rootPath);
      
          PROP_ID_TO_NAME[PROP_ID_language] = PROP_NAME_language;
          PROP_NAME_TO_ID.put(PROP_NAME_language, PROP_ID_language);
      
          PROP_ID_TO_NAME[PROP_ID_symbolCount] = PROP_NAME_symbolCount;
          PROP_NAME_TO_ID.put(PROP_NAME_symbolCount, PROP_ID_symbolCount);
      
          PROP_ID_TO_NAME[PROP_ID_fileCount] = PROP_NAME_fileCount;
          PROP_NAME_TO_ID.put(PROP_NAME_fileCount, PROP_ID_fileCount);
      
          PROP_ID_TO_NAME[PROP_ID_status] = PROP_NAME_status;
          PROP_NAME_TO_ID.put(PROP_NAME_status, PROP_ID_status);
      
    }

    
    /* 索引ID: id */
    private java.lang.String _id;
    
    /* 索引名称: name */
    private java.lang.String _name;
    
    /* 根路径: root_path */
    private java.lang.String _rootPath;
    
    /* 编程语言: language */
    private java.lang.String _language;
    
    /* 符号数量: symbol_count */
    private java.lang.Integer _symbolCount;
    
    /* 文件数量: file_count */
    private java.lang.Integer _fileCount;
    
    /* 状态: status */
    private java.lang.String _status;
    

    public _NopCodeIndex(){
        // for debug
    }

    protected NopCodeIndex newInstance(){
        NopCodeIndex entity = new NopCodeIndex();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopCodeIndex cloneInstance() {
        NopCodeIndex entity = newInstance();
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
      return "io.nop.code.dao.entity.NopCodeIndex";
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
        
            case PROP_ID_name:
               return getName();
        
            case PROP_ID_rootPath:
               return getRootPath();
        
            case PROP_ID_language:
               return getLanguage();
        
            case PROP_ID_symbolCount:
               return getSymbolCount();
        
            case PROP_ID_fileCount:
               return getFileCount();
        
            case PROP_ID_status:
               return getStatus();
        
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
        
            case PROP_ID_name:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_name));
               }
               setName(typedValue);
               break;
            }
        
            case PROP_ID_rootPath:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_rootPath));
               }
               setRootPath(typedValue);
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
        
            case PROP_ID_symbolCount:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_symbolCount));
               }
               setSymbolCount(typedValue);
               break;
            }
        
            case PROP_ID_fileCount:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_fileCount));
               }
               setFileCount(typedValue);
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
        
            case PROP_ID_name:{
               onInitProp(propId);
               this._name = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_rootPath:{
               onInitProp(propId);
               this._rootPath = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_language:{
               onInitProp(propId);
               this._language = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_symbolCount:{
               onInitProp(propId);
               this._symbolCount = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_fileCount:{
               onInitProp(propId);
               this._fileCount = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_status:{
               onInitProp(propId);
               this._status = (java.lang.String)value;
               
               break;
            }
        
           default:
              super.orm_internalSet(propId,value);
        }
    }

    
    /**
     * 索引ID: id
     */
    public final java.lang.String getId(){
         onPropGet(PROP_ID_id);
         return _id;
    }

    /**
     * 索引ID: id
     */
    public final void setId(java.lang.String value){
        if(onPropSet(PROP_ID_id,value)){
            this._id = value;
            internalClearRefs(PROP_ID_id);
            orm_id();
        }
    }
    
    /**
     * 索引名称: name
     */
    public final java.lang.String getName(){
         onPropGet(PROP_ID_name);
         return _name;
    }

    /**
     * 索引名称: name
     */
    public final void setName(java.lang.String value){
        if(onPropSet(PROP_ID_name,value)){
            this._name = value;
            internalClearRefs(PROP_ID_name);
            
        }
    }
    
    /**
     * 根路径: root_path
     */
    public final java.lang.String getRootPath(){
         onPropGet(PROP_ID_rootPath);
         return _rootPath;
    }

    /**
     * 根路径: root_path
     */
    public final void setRootPath(java.lang.String value){
        if(onPropSet(PROP_ID_rootPath,value)){
            this._rootPath = value;
            internalClearRefs(PROP_ID_rootPath);
            
        }
    }
    
    /**
     * 编程语言: language
     */
    public final java.lang.String getLanguage(){
         onPropGet(PROP_ID_language);
         return _language;
    }

    /**
     * 编程语言: language
     */
    public final void setLanguage(java.lang.String value){
        if(onPropSet(PROP_ID_language,value)){
            this._language = value;
            internalClearRefs(PROP_ID_language);
            
        }
    }
    
    /**
     * 符号数量: symbol_count
     */
    public final java.lang.Integer getSymbolCount(){
         onPropGet(PROP_ID_symbolCount);
         return _symbolCount;
    }

    /**
     * 符号数量: symbol_count
     */
    public final void setSymbolCount(java.lang.Integer value){
        if(onPropSet(PROP_ID_symbolCount,value)){
            this._symbolCount = value;
            internalClearRefs(PROP_ID_symbolCount);
            
        }
    }
    
    /**
     * 文件数量: file_count
     */
    public final java.lang.Integer getFileCount(){
         onPropGet(PROP_ID_fileCount);
         return _fileCount;
    }

    /**
     * 文件数量: file_count
     */
    public final void setFileCount(java.lang.Integer value){
        if(onPropSet(PROP_ID_fileCount,value)){
            this._fileCount = value;
            internalClearRefs(PROP_ID_fileCount);
            
        }
    }
    
    /**
     * 状态: status
     */
    public final java.lang.String getStatus(){
         onPropGet(PROP_ID_status);
         return _status;
    }

    /**
     * 状态: status
     */
    public final void setStatus(java.lang.String value){
        if(onPropSet(PROP_ID_status,value)){
            this._status = value;
            internalClearRefs(PROP_ID_status);
            
        }
    }
    
    private final OrmEntitySet<io.nop.code.dao.entity.NopCodeFile> _files = new OrmEntitySet<>(this, PROP_NAME_files,
        io.nop.code.dao.entity.NopCodeFile.PROP_NAME_index, null,io.nop.code.dao.entity.NopCodeFile.class);

    /**
     * 文件列表。 refPropName: index, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<io.nop.code.dao.entity.NopCodeFile> getFiles(){
       return _files;
    }
       
    private final OrmEntitySet<io.nop.code.dao.entity.NopCodeSymbol> _symbols = new OrmEntitySet<>(this, PROP_NAME_symbols,
        io.nop.code.dao.entity.NopCodeSymbol.PROP_NAME_index, null,io.nop.code.dao.entity.NopCodeSymbol.class);

    /**
     * 符号列表。 refPropName: index, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<io.nop.code.dao.entity.NopCodeSymbol> getSymbols(){
       return _symbols;
    }
       
    private final OrmEntitySet<io.nop.code.dao.entity.NopCodeUsage> _usages = new OrmEntitySet<>(this, PROP_NAME_usages,
        io.nop.code.dao.entity.NopCodeUsage.PROP_NAME_index, null,io.nop.code.dao.entity.NopCodeUsage.class);

    /**
     * 引用。 refPropName: index, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<io.nop.code.dao.entity.NopCodeUsage> getUsages(){
       return _usages;
    }
       
    private final OrmEntitySet<io.nop.code.dao.entity.NopCodeCall> _calls = new OrmEntitySet<>(this, PROP_NAME_calls,
        io.nop.code.dao.entity.NopCodeCall.PROP_NAME_index, null,io.nop.code.dao.entity.NopCodeCall.class);

    /**
     * 调用。 refPropName: index, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<io.nop.code.dao.entity.NopCodeCall> getCalls(){
       return _calls;
    }
       
    private final OrmEntitySet<io.nop.code.dao.entity.NopCodeInheritance> _inheritances = new OrmEntitySet<>(this, PROP_NAME_inheritances,
        io.nop.code.dao.entity.NopCodeInheritance.PROP_NAME_index, null,io.nop.code.dao.entity.NopCodeInheritance.class);

    /**
     * 继承。 refPropName: index, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<io.nop.code.dao.entity.NopCodeInheritance> getInheritances(){
       return _inheritances;
    }
       
    private final OrmEntitySet<io.nop.code.dao.entity.NopCodeAnnotationUsage> _annotationUsages = new OrmEntitySet<>(this, PROP_NAME_annotationUsages,
        io.nop.code.dao.entity.NopCodeAnnotationUsage.PROP_NAME_index, null,io.nop.code.dao.entity.NopCodeAnnotationUsage.class);

    /**
     * 注解使用。 refPropName: index, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<io.nop.code.dao.entity.NopCodeAnnotationUsage> getAnnotationUsages(){
       return _annotationUsages;
    }
       
}
// resume CPD analysis - CPD-ON
