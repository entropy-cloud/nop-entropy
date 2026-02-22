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

import io.nop.code.dao.entity.NopCodeCall;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  方法调用: nop_code_call
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopCodeCall extends DynamicOrmEntity{
    
    /* ID: id VARCHAR */
    public static final String PROP_NAME_id = "id";
    public static final int PROP_ID_id = 1;
    
    /* 索引ID: index_id VARCHAR */
    public static final String PROP_NAME_indexId = "indexId";
    public static final int PROP_ID_indexId = 2;
    
    /* 调用方ID: caller_id VARCHAR */
    public static final String PROP_NAME_callerId = "callerId";
    public static final int PROP_ID_callerId = 3;
    
    /* 被调用方ID: callee_id VARCHAR */
    public static final String PROP_NAME_calleeId = "calleeId";
    public static final int PROP_ID_calleeId = 4;
    
    /* 文件ID: file_id VARCHAR */
    public static final String PROP_NAME_fileId = "fileId";
    public static final int PROP_ID_fileId = 5;
    
    /* 行号: line INTEGER */
    public static final String PROP_NAME_line = "line";
    public static final int PROP_ID_line = 6;
    
    /* 列号: column INTEGER */
    public static final String PROP_NAME_column = "column";
    public static final int PROP_ID_column = 7;
    
    /* 调用类型: call_type VARCHAR */
    public static final String PROP_NAME_callType = "callType";
    public static final int PROP_ID_callType = 8;
    
    /* 上下文: context VARCHAR */
    public static final String PROP_NAME_context = "context";
    public static final int PROP_ID_context = 9;
    

    private static int _PROP_ID_BOUND = 10;

    
    /* relation:  */
    public static final String PROP_NAME_index = "index";
    
    /* relation:  */
    public static final String PROP_NAME_caller = "caller";
    
    /* relation:  */
    public static final String PROP_NAME_callee = "callee";
    
    /* relation:  */
    public static final String PROP_NAME_file = "file";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_id);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_id};

    private static final String[] PROP_ID_TO_NAME = new String[10];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_id] = PROP_NAME_id;
          PROP_NAME_TO_ID.put(PROP_NAME_id, PROP_ID_id);
      
          PROP_ID_TO_NAME[PROP_ID_indexId] = PROP_NAME_indexId;
          PROP_NAME_TO_ID.put(PROP_NAME_indexId, PROP_ID_indexId);
      
          PROP_ID_TO_NAME[PROP_ID_callerId] = PROP_NAME_callerId;
          PROP_NAME_TO_ID.put(PROP_NAME_callerId, PROP_ID_callerId);
      
          PROP_ID_TO_NAME[PROP_ID_calleeId] = PROP_NAME_calleeId;
          PROP_NAME_TO_ID.put(PROP_NAME_calleeId, PROP_ID_calleeId);
      
          PROP_ID_TO_NAME[PROP_ID_fileId] = PROP_NAME_fileId;
          PROP_NAME_TO_ID.put(PROP_NAME_fileId, PROP_ID_fileId);
      
          PROP_ID_TO_NAME[PROP_ID_line] = PROP_NAME_line;
          PROP_NAME_TO_ID.put(PROP_NAME_line, PROP_ID_line);
      
          PROP_ID_TO_NAME[PROP_ID_column] = PROP_NAME_column;
          PROP_NAME_TO_ID.put(PROP_NAME_column, PROP_ID_column);
      
          PROP_ID_TO_NAME[PROP_ID_callType] = PROP_NAME_callType;
          PROP_NAME_TO_ID.put(PROP_NAME_callType, PROP_ID_callType);
      
          PROP_ID_TO_NAME[PROP_ID_context] = PROP_NAME_context;
          PROP_NAME_TO_ID.put(PROP_NAME_context, PROP_ID_context);
      
    }

    
    /* ID: id */
    private java.lang.String _id;
    
    /* 索引ID: index_id */
    private java.lang.String _indexId;
    
    /* 调用方ID: caller_id */
    private java.lang.String _callerId;
    
    /* 被调用方ID: callee_id */
    private java.lang.String _calleeId;
    
    /* 文件ID: file_id */
    private java.lang.String _fileId;
    
    /* 行号: line */
    private java.lang.Integer _line;
    
    /* 列号: column */
    private java.lang.Integer _column;
    
    /* 调用类型: call_type */
    private java.lang.String _callType;
    
    /* 上下文: context */
    private java.lang.String _context;
    

    public _NopCodeCall(){
        // for debug
    }

    protected NopCodeCall newInstance(){
        NopCodeCall entity = new NopCodeCall();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopCodeCall cloneInstance() {
        NopCodeCall entity = newInstance();
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
      return "io.nop.code.dao.entity.NopCodeCall";
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
        
            case PROP_ID_callerId:
               return getCallerId();
        
            case PROP_ID_calleeId:
               return getCalleeId();
        
            case PROP_ID_fileId:
               return getFileId();
        
            case PROP_ID_line:
               return getLine();
        
            case PROP_ID_column:
               return getColumn();
        
            case PROP_ID_callType:
               return getCallType();
        
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
        
            case PROP_ID_callerId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_callerId));
               }
               setCallerId(typedValue);
               break;
            }
        
            case PROP_ID_calleeId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_calleeId));
               }
               setCalleeId(typedValue);
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
        
            case PROP_ID_callType:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_callType));
               }
               setCallType(typedValue);
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
        
            case PROP_ID_callerId:{
               onInitProp(propId);
               this._callerId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_calleeId:{
               onInitProp(propId);
               this._calleeId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_fileId:{
               onInitProp(propId);
               this._fileId = (java.lang.String)value;
               
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
        
            case PROP_ID_callType:{
               onInitProp(propId);
               this._callType = (java.lang.String)value;
               
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
     * 调用方ID: caller_id
     */
    public final java.lang.String getCallerId(){
         onPropGet(PROP_ID_callerId);
         return _callerId;
    }

    /**
     * 调用方ID: caller_id
     */
    public final void setCallerId(java.lang.String value){
        if(onPropSet(PROP_ID_callerId,value)){
            this._callerId = value;
            internalClearRefs(PROP_ID_callerId);
            
        }
    }
    
    /**
     * 被调用方ID: callee_id
     */
    public final java.lang.String getCalleeId(){
         onPropGet(PROP_ID_calleeId);
         return _calleeId;
    }

    /**
     * 被调用方ID: callee_id
     */
    public final void setCalleeId(java.lang.String value){
        if(onPropSet(PROP_ID_calleeId,value)){
            this._calleeId = value;
            internalClearRefs(PROP_ID_calleeId);
            
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
     * 调用类型: call_type
     */
    public final java.lang.String getCallType(){
         onPropGet(PROP_ID_callType);
         return _callType;
    }

    /**
     * 调用类型: call_type
     */
    public final void setCallType(java.lang.String value){
        if(onPropSet(PROP_ID_callType,value)){
            this._callType = value;
            internalClearRefs(PROP_ID_callType);
            
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
    public final io.nop.code.dao.entity.NopCodeSymbol getCaller(){
       return (io.nop.code.dao.entity.NopCodeSymbol)internalGetRefEntity(PROP_NAME_caller);
    }

    public final void setCaller(io.nop.code.dao.entity.NopCodeSymbol refEntity){
   
           if(refEntity == null){
           
                   this.setCallerId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_caller, refEntity,()->{
           
                           this.setCallerId(refEntity.getId());
                       
           });
           }
       
    }
       
    /**
     * 
     */
    public final io.nop.code.dao.entity.NopCodeSymbol getCallee(){
       return (io.nop.code.dao.entity.NopCodeSymbol)internalGetRefEntity(PROP_NAME_callee);
    }

    public final void setCallee(io.nop.code.dao.entity.NopCodeSymbol refEntity){
   
           if(refEntity == null){
           
                   this.setCalleeId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_callee, refEntity,()->{
           
                           this.setCalleeId(refEntity.getId());
                       
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
       
}
// resume CPD analysis - CPD-ON
