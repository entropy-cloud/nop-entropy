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

import io.nop.code.dao.entity.NopCodeUsage;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  符号引用: nop_code_usage
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopCodeUsage extends DynamicOrmEntity{
    
    /* ID: id VARCHAR */
    public static final String PROP_NAME_id = "id";
    public static final int PROP_ID_id = 1;
    
    /* 索引ID: index_id VARCHAR */
    public static final String PROP_NAME_indexId = "indexId";
    public static final int PROP_ID_indexId = 2;
    
    /* 被引用符号ID: symbol_id VARCHAR */
    public static final String PROP_NAME_symbolId = "symbolId";
    public static final int PROP_ID_symbolId = 3;
    
    /* 文件ID: file_id VARCHAR */
    public static final String PROP_NAME_fileId = "fileId";
    public static final int PROP_ID_fileId = 4;
    
    /* 引用类型: kind VARCHAR */
    public static final String PROP_NAME_kind = "kind";
    public static final int PROP_ID_kind = 5;
    
    /* 行号: line INTEGER */
    public static final String PROP_NAME_line = "line";
    public static final int PROP_ID_line = 6;
    
    /* 列号: column INTEGER */
    public static final String PROP_NAME_column = "column";
    public static final int PROP_ID_column = 7;
    
    /* 所在符号ID: enclosing_symbol_id VARCHAR */
    public static final String PROP_NAME_enclosingSymbolId = "enclosingSymbolId";
    public static final int PROP_ID_enclosingSymbolId = 8;
    
    /* 上下文: context VARCHAR */
    public static final String PROP_NAME_context = "context";
    public static final int PROP_ID_context = 9;
    

    private static int _PROP_ID_BOUND = 10;

    
    /* relation:  */
    public static final String PROP_NAME_index = "index";
    
    /* relation:  */
    public static final String PROP_NAME_symbol = "symbol";
    
    /* relation:  */
    public static final String PROP_NAME_file = "file";
    
    /* relation:  */
    public static final String PROP_NAME_enclosingSymbol = "enclosingSymbol";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_id);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_id};

    private static final String[] PROP_ID_TO_NAME = new String[10];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_id] = PROP_NAME_id;
          PROP_NAME_TO_ID.put(PROP_NAME_id, PROP_ID_id);
      
          PROP_ID_TO_NAME[PROP_ID_indexId] = PROP_NAME_indexId;
          PROP_NAME_TO_ID.put(PROP_NAME_indexId, PROP_ID_indexId);
      
          PROP_ID_TO_NAME[PROP_ID_symbolId] = PROP_NAME_symbolId;
          PROP_NAME_TO_ID.put(PROP_NAME_symbolId, PROP_ID_symbolId);
      
          PROP_ID_TO_NAME[PROP_ID_fileId] = PROP_NAME_fileId;
          PROP_NAME_TO_ID.put(PROP_NAME_fileId, PROP_ID_fileId);
      
          PROP_ID_TO_NAME[PROP_ID_kind] = PROP_NAME_kind;
          PROP_NAME_TO_ID.put(PROP_NAME_kind, PROP_ID_kind);
      
          PROP_ID_TO_NAME[PROP_ID_line] = PROP_NAME_line;
          PROP_NAME_TO_ID.put(PROP_NAME_line, PROP_ID_line);
      
          PROP_ID_TO_NAME[PROP_ID_column] = PROP_NAME_column;
          PROP_NAME_TO_ID.put(PROP_NAME_column, PROP_ID_column);
      
          PROP_ID_TO_NAME[PROP_ID_enclosingSymbolId] = PROP_NAME_enclosingSymbolId;
          PROP_NAME_TO_ID.put(PROP_NAME_enclosingSymbolId, PROP_ID_enclosingSymbolId);
      
          PROP_ID_TO_NAME[PROP_ID_context] = PROP_NAME_context;
          PROP_NAME_TO_ID.put(PROP_NAME_context, PROP_ID_context);
      
    }

    
    /* ID: id */
    private java.lang.String _id;
    
    /* 索引ID: index_id */
    private java.lang.String _indexId;
    
    /* 被引用符号ID: symbol_id */
    private java.lang.String _symbolId;
    
    /* 文件ID: file_id */
    private java.lang.String _fileId;
    
    /* 引用类型: kind */
    private java.lang.String _kind;
    
    /* 行号: line */
    private java.lang.Integer _line;
    
    /* 列号: column */
    private java.lang.Integer _column;
    
    /* 所在符号ID: enclosing_symbol_id */
    private java.lang.String _enclosingSymbolId;
    
    /* 上下文: context */
    private java.lang.String _context;
    

    public _NopCodeUsage(){
        // for debug
    }

    protected NopCodeUsage newInstance(){
        NopCodeUsage entity = new NopCodeUsage();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopCodeUsage cloneInstance() {
        NopCodeUsage entity = newInstance();
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
      return "io.nop.code.dao.entity.NopCodeUsage";
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
        
            case PROP_ID_symbolId:
               return getSymbolId();
        
            case PROP_ID_fileId:
               return getFileId();
        
            case PROP_ID_kind:
               return getKind();
        
            case PROP_ID_line:
               return getLine();
        
            case PROP_ID_column:
               return getColumn();
        
            case PROP_ID_enclosingSymbolId:
               return getEnclosingSymbolId();
        
            case PROP_ID_context:
               return getContext();
        
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
        
            case PROP_ID_symbolId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_symbolId));
               }
               setSymbolId(typedValue);
               break;
            }
        
            case PROP_ID_fileId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_fileId));
               }
               setFileId(typedValue);
               break;
            }
        
            case PROP_ID_kind:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_kind));
               }
               setKind(typedValue);
               break;
            }
        
            case PROP_ID_line:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_line));
               }
               setLine(typedValue);
               break;
            }
        
            case PROP_ID_column:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_column));
               }
               setColumn(typedValue);
               break;
            }
        
            case PROP_ID_enclosingSymbolId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_enclosingSymbolId));
               }
               setEnclosingSymbolId(typedValue);
               break;
            }
        
            case PROP_ID_context:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_context));
               }
               setContext(typedValue);
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
        
            case PROP_ID_symbolId:{
               onInitProp(propId);
               this._symbolId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_fileId:{
               onInitProp(propId);
               this._fileId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_kind:{
               onInitProp(propId);
               this._kind = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_line:{
               onInitProp(propId);
               this._line = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_column:{
               onInitProp(propId);
               this._column = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_enclosingSymbolId:{
               onInitProp(propId);
               this._enclosingSymbolId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_context:{
               onInitProp(propId);
               this._context = (java.lang.String)value;
               
               break;
            }
        
           default:
              super.orm_internalSet(propId,value);
        }
    }

    
    /**
     * ID: id
     */
    public final java.lang.String getId(){
         onPropGet(PROP_ID_id);
         return _id;
    }

    /**
     * ID: id
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
     * 被引用符号ID: symbol_id
     */
    public final java.lang.String getSymbolId(){
         onPropGet(PROP_ID_symbolId);
         return _symbolId;
    }

    /**
     * 被引用符号ID: symbol_id
     */
    public final void setSymbolId(java.lang.String value){
        if(onPropSet(PROP_ID_symbolId,value)){
            this._symbolId = value;
            internalClearRefs(PROP_ID_symbolId);
            
        }
    }
    
    /**
     * 文件ID: file_id
     */
    public final java.lang.String getFileId(){
         onPropGet(PROP_ID_fileId);
         return _fileId;
    }

    /**
     * 文件ID: file_id
     */
    public final void setFileId(java.lang.String value){
        if(onPropSet(PROP_ID_fileId,value)){
            this._fileId = value;
            internalClearRefs(PROP_ID_fileId);
            
        }
    }
    
    /**
     * 引用类型: kind
     */
    public final java.lang.String getKind(){
         onPropGet(PROP_ID_kind);
         return _kind;
    }

    /**
     * 引用类型: kind
     */
    public final void setKind(java.lang.String value){
        if(onPropSet(PROP_ID_kind,value)){
            this._kind = value;
            internalClearRefs(PROP_ID_kind);
            
        }
    }
    
    /**
     * 行号: line
     */
    public final java.lang.Integer getLine(){
         onPropGet(PROP_ID_line);
         return _line;
    }

    /**
     * 行号: line
     */
    public final void setLine(java.lang.Integer value){
        if(onPropSet(PROP_ID_line,value)){
            this._line = value;
            internalClearRefs(PROP_ID_line);
            
        }
    }
    
    /**
     * 列号: column
     */
    public final java.lang.Integer getColumn(){
         onPropGet(PROP_ID_column);
         return _column;
    }

    /**
     * 列号: column
     */
    public final void setColumn(java.lang.Integer value){
        if(onPropSet(PROP_ID_column,value)){
            this._column = value;
            internalClearRefs(PROP_ID_column);
            
        }
    }
    
    /**
     * 所在符号ID: enclosing_symbol_id
     */
    public final java.lang.String getEnclosingSymbolId(){
         onPropGet(PROP_ID_enclosingSymbolId);
         return _enclosingSymbolId;
    }

    /**
     * 所在符号ID: enclosing_symbol_id
     */
    public final void setEnclosingSymbolId(java.lang.String value){
        if(onPropSet(PROP_ID_enclosingSymbolId,value)){
            this._enclosingSymbolId = value;
            internalClearRefs(PROP_ID_enclosingSymbolId);
            
        }
    }
    
    /**
     * 上下文: context
     */
    public final java.lang.String getContext(){
         onPropGet(PROP_ID_context);
         return _context;
    }

    /**
     * 上下文: context
     */
    public final void setContext(java.lang.String value){
        if(onPropSet(PROP_ID_context,value)){
            this._context = value;
            internalClearRefs(PROP_ID_context);
            
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
    public final io.nop.code.dao.entity.NopCodeSymbol getSymbol(){
       return (io.nop.code.dao.entity.NopCodeSymbol)internalGetRefEntity(PROP_NAME_symbol);
    }

    public final void setSymbol(io.nop.code.dao.entity.NopCodeSymbol refEntity){
   
           if(refEntity == null){
           
                   this.setSymbolId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_symbol, refEntity,()->{
           
                           this.setSymbolId(refEntity.getId());
                       
           });
           }
       
    }
       
    /**
     * 
     */
    public final io.nop.code.dao.entity.NopCodeFile getFile(){
       return (io.nop.code.dao.entity.NopCodeFile)internalGetRefEntity(PROP_NAME_file);
    }

    public final void setFile(io.nop.code.dao.entity.NopCodeFile refEntity){
   
           if(refEntity == null){
           
                   this.setFileId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_file, refEntity,()->{
           
                           this.setFileId(refEntity.getId());
                       
           });
           }
       
    }
       
    /**
     * 
     */
    public final io.nop.code.dao.entity.NopCodeSymbol getEnclosingSymbol(){
       return (io.nop.code.dao.entity.NopCodeSymbol)internalGetRefEntity(PROP_NAME_enclosingSymbol);
    }

    public final void setEnclosingSymbol(io.nop.code.dao.entity.NopCodeSymbol refEntity){
   
           if(refEntity == null){
           
                   this.setEnclosingSymbolId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_enclosingSymbol, refEntity,()->{
           
                           this.setEnclosingSymbolId(refEntity.getId());
                       
           });
           }
       
    }
       
}
// resume CPD analysis - CPD-ON
