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

import io.nop.code.dao.entity.NopCodeInheritance;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  继承关系: nop_code_inheritance
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopCodeInheritance extends DynamicOrmEntity{
    
    /* ID: id VARCHAR */
    public static final String PROP_NAME_id = "id";
    public static final int PROP_ID_id = 1;
    
    /* 索引ID: index_id VARCHAR */
    public static final String PROP_NAME_indexId = "indexId";
    public static final int PROP_ID_indexId = 2;
    
    /* 子类型ID: sub_type_id VARCHAR */
    public static final String PROP_NAME_subTypeId = "subTypeId";
    public static final int PROP_ID_subTypeId = 3;
    
    /* 父类型ID: super_type_id VARCHAR */
    public static final String PROP_NAME_superTypeId = "superTypeId";
    public static final int PROP_ID_superTypeId = 4;
    
    /* 关系类型: relation_type VARCHAR */
    public static final String PROP_NAME_relationType = "relationType";
    public static final int PROP_ID_relationType = 5;
    

    private static int _PROP_ID_BOUND = 6;

    
    /* relation:  */
    public static final String PROP_NAME_index = "index";
    
    /* relation:  */
    public static final String PROP_NAME_subType = "subType";
    
    /* relation:  */
    public static final String PROP_NAME_superType = "superType";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_id);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_id};

    private static final String[] PROP_ID_TO_NAME = new String[6];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_id] = PROP_NAME_id;
          PROP_NAME_TO_ID.put(PROP_NAME_id, PROP_ID_id);
      
          PROP_ID_TO_NAME[PROP_ID_indexId] = PROP_NAME_indexId;
          PROP_NAME_TO_ID.put(PROP_NAME_indexId, PROP_ID_indexId);
      
          PROP_ID_TO_NAME[PROP_ID_subTypeId] = PROP_NAME_subTypeId;
          PROP_NAME_TO_ID.put(PROP_NAME_subTypeId, PROP_ID_subTypeId);
      
          PROP_ID_TO_NAME[PROP_ID_superTypeId] = PROP_NAME_superTypeId;
          PROP_NAME_TO_ID.put(PROP_NAME_superTypeId, PROP_ID_superTypeId);
      
          PROP_ID_TO_NAME[PROP_ID_relationType] = PROP_NAME_relationType;
          PROP_NAME_TO_ID.put(PROP_NAME_relationType, PROP_ID_relationType);
      
    }

    
    /* ID: id */
    private java.lang.String _id;
    
    /* 索引ID: index_id */
    private java.lang.String _indexId;
    
    /* 子类型ID: sub_type_id */
    private java.lang.String _subTypeId;
    
    /* 父类型ID: super_type_id */
    private java.lang.String _superTypeId;
    
    /* 关系类型: relation_type */
    private java.lang.String _relationType;
    

    public _NopCodeInheritance(){
        // for debug
    }

    protected NopCodeInheritance newInstance(){
        NopCodeInheritance entity = new NopCodeInheritance();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopCodeInheritance cloneInstance() {
        NopCodeInheritance entity = newInstance();
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
      return "io.nop.code.dao.entity.NopCodeInheritance";
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
        
            case PROP_ID_subTypeId:
               return getSubTypeId();
        
            case PROP_ID_superTypeId:
               return getSuperTypeId();
        
            case PROP_ID_relationType:
               return getRelationType();
        
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
        
            case PROP_ID_subTypeId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_subTypeId));
               }
               setSubTypeId(typedValue);
               break;
            }
        
            case PROP_ID_superTypeId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_superTypeId));
               }
               setSuperTypeId(typedValue);
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
        
            case PROP_ID_subTypeId:{
               onInitProp(propId);
               this._subTypeId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_superTypeId:{
               onInitProp(propId);
               this._superTypeId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_relationType:{
               onInitProp(propId);
               this._relationType = (java.lang.String)value;
               
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
     * 子类型ID: sub_type_id
     */
    public final java.lang.String getSubTypeId(){
         onPropGet(PROP_ID_subTypeId);
         return _subTypeId;
    }

    /**
     * 子类型ID: sub_type_id
     */
    public final void setSubTypeId(java.lang.String value){
        if(onPropSet(PROP_ID_subTypeId,value)){
            this._subTypeId = value;
            internalClearRefs(PROP_ID_subTypeId);
            
        }
    }
    
    /**
     * 父类型ID: super_type_id
     */
    public final java.lang.String getSuperTypeId(){
         onPropGet(PROP_ID_superTypeId);
         return _superTypeId;
    }

    /**
     * 父类型ID: super_type_id
     */
    public final void setSuperTypeId(java.lang.String value){
        if(onPropSet(PROP_ID_superTypeId,value)){
            this._superTypeId = value;
            internalClearRefs(PROP_ID_superTypeId);
            
        }
    }
    
    /**
     * 关系类型: relation_type
     */
    public final java.lang.String getRelationType(){
         onPropGet(PROP_ID_relationType);
         return _relationType;
    }

    /**
     * 关系类型: relation_type
     */
    public final void setRelationType(java.lang.String value){
        if(onPropSet(PROP_ID_relationType,value)){
            this._relationType = value;
            internalClearRefs(PROP_ID_relationType);
            
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
    public final io.nop.code.dao.entity.NopCodeSymbol getSubType(){
       return (io.nop.code.dao.entity.NopCodeSymbol)internalGetRefEntity(PROP_NAME_subType);
    }

    public final void setSubType(io.nop.code.dao.entity.NopCodeSymbol refEntity){
   
           if(refEntity == null){
           
                   this.setSubTypeId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_subType, refEntity,()->{
           
                           this.setSubTypeId(refEntity.getId());
                       
           });
           }
       
    }
       
    /**
     * 
     */
    public final io.nop.code.dao.entity.NopCodeSymbol getSuperType(){
       return (io.nop.code.dao.entity.NopCodeSymbol)internalGetRefEntity(PROP_NAME_superType);
    }

    public final void setSuperType(io.nop.code.dao.entity.NopCodeSymbol refEntity){
   
           if(refEntity == null){
           
                   this.setSuperTypeId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_superType, refEntity,()->{
           
                           this.setSuperTypeId(refEntity.getId());
                       
           });
           }
       
    }
       
}
// resume CPD analysis - CPD-ON
