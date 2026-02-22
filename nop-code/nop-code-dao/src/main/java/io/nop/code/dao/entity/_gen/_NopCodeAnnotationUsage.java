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

import io.nop.code.dao.entity.NopCodeAnnotationUsage;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  注解使用: nop_code_annotation_usage
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopCodeAnnotationUsage extends DynamicOrmEntity{
    
    /* ID: id VARCHAR */
    public static final String PROP_NAME_id = "id";
    public static final int PROP_ID_id = 1;
    
    /* 索引ID: index_id VARCHAR */
    public static final String PROP_NAME_indexId = "indexId";
    public static final int PROP_ID_indexId = 2;
    
    /* 注解类型ID: annotation_type_id VARCHAR */
    public static final String PROP_NAME_annotationTypeId = "annotationTypeId";
    public static final int PROP_ID_annotationTypeId = 3;
    
    /* 被注解符号ID: annotated_symbol_id VARCHAR */
    public static final String PROP_NAME_annotatedSymbolId = "annotatedSymbolId";
    public static final int PROP_ID_annotatedSymbolId = 4;
    
    /* 行号: line INTEGER */
    public static final String PROP_NAME_line = "line";
    public static final int PROP_ID_line = 5;
    
    /* 列号: column INTEGER */
    public static final String PROP_NAME_column = "column";
    public static final int PROP_ID_column = 6;
    
    /* 属性值: attributes VARCHAR */
    public static final String PROP_NAME_attributes = "attributes";
    public static final int PROP_ID_attributes = 7;
    

    private static int _PROP_ID_BOUND = 8;

    
    /* relation:  */
    public static final String PROP_NAME_index = "index";
    
    /* relation:  */
    public static final String PROP_NAME_annotationType = "annotationType";
    
    /* relation:  */
    public static final String PROP_NAME_annotatedSymbol = "annotatedSymbol";
    
    /* component:  */
    public static final String PROP_NAME_attributesComponent = "attributesComponent";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_id);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_id};

    private static final String[] PROP_ID_TO_NAME = new String[8];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_id] = PROP_NAME_id;
          PROP_NAME_TO_ID.put(PROP_NAME_id, PROP_ID_id);
      
          PROP_ID_TO_NAME[PROP_ID_indexId] = PROP_NAME_indexId;
          PROP_NAME_TO_ID.put(PROP_NAME_indexId, PROP_ID_indexId);
      
          PROP_ID_TO_NAME[PROP_ID_annotationTypeId] = PROP_NAME_annotationTypeId;
          PROP_NAME_TO_ID.put(PROP_NAME_annotationTypeId, PROP_ID_annotationTypeId);
      
          PROP_ID_TO_NAME[PROP_ID_annotatedSymbolId] = PROP_NAME_annotatedSymbolId;
          PROP_NAME_TO_ID.put(PROP_NAME_annotatedSymbolId, PROP_ID_annotatedSymbolId);
      
          PROP_ID_TO_NAME[PROP_ID_line] = PROP_NAME_line;
          PROP_NAME_TO_ID.put(PROP_NAME_line, PROP_ID_line);
      
          PROP_ID_TO_NAME[PROP_ID_column] = PROP_NAME_column;
          PROP_NAME_TO_ID.put(PROP_NAME_column, PROP_ID_column);
      
          PROP_ID_TO_NAME[PROP_ID_attributes] = PROP_NAME_attributes;
          PROP_NAME_TO_ID.put(PROP_NAME_attributes, PROP_ID_attributes);
      
    }

    
    /* ID: id */
    private java.lang.String _id;
    
    /* 索引ID: index_id */
    private java.lang.String _indexId;
    
    /* 注解类型ID: annotation_type_id */
    private java.lang.String _annotationTypeId;
    
    /* 被注解符号ID: annotated_symbol_id */
    private java.lang.String _annotatedSymbolId;
    
    /* 行号: line */
    private java.lang.Integer _line;
    
    /* 列号: column */
    private java.lang.Integer _column;
    
    /* 属性值: attributes */
    private java.lang.String _attributes;
    

    public _NopCodeAnnotationUsage(){
        // for debug
    }

    protected NopCodeAnnotationUsage newInstance(){
        NopCodeAnnotationUsage entity = new NopCodeAnnotationUsage();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopCodeAnnotationUsage cloneInstance() {
        NopCodeAnnotationUsage entity = newInstance();
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
      return "io.nop.code.dao.entity.NopCodeAnnotationUsage";
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
        
            case PROP_ID_annotationTypeId:
               return getAnnotationTypeId();
        
            case PROP_ID_annotatedSymbolId:
               return getAnnotatedSymbolId();
        
            case PROP_ID_line:
               return getLine();
        
            case PROP_ID_column:
               return getColumn();
        
            case PROP_ID_attributes:
               return getAttributes();
        
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
        
            case PROP_ID_annotationTypeId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_annotationTypeId));
               }
               setAnnotationTypeId(typedValue);
               break;
            }
        
            case PROP_ID_annotatedSymbolId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_annotatedSymbolId));
               }
               setAnnotatedSymbolId(typedValue);
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
        
            case PROP_ID_attributes:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_attributes));
               }
               setAttributes(typedValue);
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
        
            case PROP_ID_annotationTypeId:{
               onInitProp(propId);
               this._annotationTypeId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_annotatedSymbolId:{
               onInitProp(propId);
               this._annotatedSymbolId = (java.lang.String)value;
               
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
        
            case PROP_ID_attributes:{
               onInitProp(propId);
               this._attributes = (java.lang.String)value;
               
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
     * 注解类型ID: annotation_type_id
     */
    public final java.lang.String getAnnotationTypeId(){
         onPropGet(PROP_ID_annotationTypeId);
         return _annotationTypeId;
    }

    /**
     * 注解类型ID: annotation_type_id
     */
    public final void setAnnotationTypeId(java.lang.String value){
        if(onPropSet(PROP_ID_annotationTypeId,value)){
            this._annotationTypeId = value;
            internalClearRefs(PROP_ID_annotationTypeId);
            
        }
    }
    
    /**
     * 被注解符号ID: annotated_symbol_id
     */
    public final java.lang.String getAnnotatedSymbolId(){
         onPropGet(PROP_ID_annotatedSymbolId);
         return _annotatedSymbolId;
    }

    /**
     * 被注解符号ID: annotated_symbol_id
     */
    public final void setAnnotatedSymbolId(java.lang.String value){
        if(onPropSet(PROP_ID_annotatedSymbolId,value)){
            this._annotatedSymbolId = value;
            internalClearRefs(PROP_ID_annotatedSymbolId);
            
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
     * 属性值: attributes
     */
    public final java.lang.String getAttributes(){
         onPropGet(PROP_ID_attributes);
         return _attributes;
    }

    /**
     * 属性值: attributes
     */
    public final void setAttributes(java.lang.String value){
        if(onPropSet(PROP_ID_attributes,value)){
            this._attributes = value;
            internalClearRefs(PROP_ID_attributes);
            
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
    public final io.nop.code.dao.entity.NopCodeSymbol getAnnotationType(){
       return (io.nop.code.dao.entity.NopCodeSymbol)internalGetRefEntity(PROP_NAME_annotationType);
    }

    public final void setAnnotationType(io.nop.code.dao.entity.NopCodeSymbol refEntity){
   
           if(refEntity == null){
           
                   this.setAnnotationTypeId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_annotationType, refEntity,()->{
           
                           this.setAnnotationTypeId(refEntity.getId());
                       
           });
           }
       
    }
       
    /**
     * 
     */
    public final io.nop.code.dao.entity.NopCodeSymbol getAnnotatedSymbol(){
       return (io.nop.code.dao.entity.NopCodeSymbol)internalGetRefEntity(PROP_NAME_annotatedSymbol);
    }

    public final void setAnnotatedSymbol(io.nop.code.dao.entity.NopCodeSymbol refEntity){
   
           if(refEntity == null){
           
                   this.setAnnotatedSymbolId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_annotatedSymbol, refEntity,()->{
           
                           this.setAnnotatedSymbolId(refEntity.getId());
                       
           });
           }
       
    }
       
   private io.nop.orm.component.JsonOrmComponent _attributesComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_attributesComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_attributesComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_attributes);
      
   }

   public final io.nop.orm.component.JsonOrmComponent getAttributesComponent(){
      if(_attributesComponent == null){
          _attributesComponent = new io.nop.orm.component.JsonOrmComponent();
          _attributesComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_attributesComponent);
      }
      return _attributesComponent;
   }

}
// resume CPD analysis - CPD-ON
