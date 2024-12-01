package app.demo.ddd.entity._gen;

import io.nop.orm.model.IEntityModel;
import io.nop.orm.support.DynamicOrmEntity;
import io.nop.orm.support.OrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.orm.IOrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.api.core.convert.ConvertHelper;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;

import app.demo.ddd.entity.Leg;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  航段: leg
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _Leg extends DynamicOrmEntity{
    
    /* Id: ID BIGINT */
    public static final String PROP_NAME_id = "id";
    public static final int PROP_ID_id = 1;
    
    /* 装货时间: LOAD_TIME DATETIME */
    public static final String PROP_NAME_loadTime = "loadTime";
    public static final int PROP_ID_loadTime = 2;
    
    /* 卸货时间: UNLOAD_TIME DATETIME */
    public static final String PROP_NAME_unloadTime = "unloadTime";
    public static final int PROP_ID_unloadTime = 3;
    
    /* 装货地点: LOAD_LOCATION_ID BIGINT */
    public static final String PROP_NAME_loadLocationId = "loadLocationId";
    public static final int PROP_ID_loadLocationId = 4;
    
    /* 卸货地点: UNLOAD_LOCATION_ID BIGINT */
    public static final String PROP_NAME_unloadLocationId = "unloadLocationId";
    public static final int PROP_ID_unloadLocationId = 5;
    
    /* 航程ID: VOYAGE_ID BIGINT */
    public static final String PROP_NAME_voyageId = "voyageId";
    public static final int PROP_ID_voyageId = 6;
    
    /* 货物ID: CARGO_ID BIGINT */
    public static final String PROP_NAME_cargoId = "cargoId";
    public static final int PROP_ID_cargoId = 7;
    

    private static int _PROP_ID_BOUND = 8;

    
    /* relation: 装货地点 */
    public static final String PROP_NAME_loadLocation = "loadLocation";
    
    /* relation: 货物 */
    public static final String PROP_NAME_cargo = "cargo";
    
    /* relation: 航程 */
    public static final String PROP_NAME_voyage = "voyage";
    
    /* relation: 卸载地点 */
    public static final String PROP_NAME_unloadLocation = "unloadLocation";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_id);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_id};

    private static final String[] PROP_ID_TO_NAME = new String[8];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_id] = PROP_NAME_id;
          PROP_NAME_TO_ID.put(PROP_NAME_id, PROP_ID_id);
      
          PROP_ID_TO_NAME[PROP_ID_loadTime] = PROP_NAME_loadTime;
          PROP_NAME_TO_ID.put(PROP_NAME_loadTime, PROP_ID_loadTime);
      
          PROP_ID_TO_NAME[PROP_ID_unloadTime] = PROP_NAME_unloadTime;
          PROP_NAME_TO_ID.put(PROP_NAME_unloadTime, PROP_ID_unloadTime);
      
          PROP_ID_TO_NAME[PROP_ID_loadLocationId] = PROP_NAME_loadLocationId;
          PROP_NAME_TO_ID.put(PROP_NAME_loadLocationId, PROP_ID_loadLocationId);
      
          PROP_ID_TO_NAME[PROP_ID_unloadLocationId] = PROP_NAME_unloadLocationId;
          PROP_NAME_TO_ID.put(PROP_NAME_unloadLocationId, PROP_ID_unloadLocationId);
      
          PROP_ID_TO_NAME[PROP_ID_voyageId] = PROP_NAME_voyageId;
          PROP_NAME_TO_ID.put(PROP_NAME_voyageId, PROP_ID_voyageId);
      
          PROP_ID_TO_NAME[PROP_ID_cargoId] = PROP_NAME_cargoId;
          PROP_NAME_TO_ID.put(PROP_NAME_cargoId, PROP_ID_cargoId);
      
    }

    
    /* Id: ID */
    private java.lang.Long _id;
    
    /* 装货时间: LOAD_TIME */
    private java.time.LocalDateTime _loadTime;
    
    /* 卸货时间: UNLOAD_TIME */
    private java.time.LocalDateTime _unloadTime;
    
    /* 装货地点: LOAD_LOCATION_ID */
    private java.lang.Long _loadLocationId;
    
    /* 卸货地点: UNLOAD_LOCATION_ID */
    private java.lang.Long _unloadLocationId;
    
    /* 航程ID: VOYAGE_ID */
    private java.lang.Long _voyageId;
    
    /* 货物ID: CARGO_ID */
    private java.lang.Long _cargoId;
    

    public _Leg(){
        // for debug
    }

    protected Leg newInstance(){
        Leg entity = new Leg();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public Leg cloneInstance() {
        Leg entity = newInstance();
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
      return "app.demo.ddd.entity.Leg";
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
        
            case PROP_ID_loadTime:
               return getLoadTime();
        
            case PROP_ID_unloadTime:
               return getUnloadTime();
        
            case PROP_ID_loadLocationId:
               return getLoadLocationId();
        
            case PROP_ID_unloadLocationId:
               return getUnloadLocationId();
        
            case PROP_ID_voyageId:
               return getVoyageId();
        
            case PROP_ID_cargoId:
               return getCargoId();
        
           default:
              return super.orm_propValue(propId);
        }
    }

    

    @Override
    public void orm_propValue(int propId, Object value){
        switch(propId){
        
            case PROP_ID_id:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_id));
               }
               setId(typedValue);
               break;
            }
        
            case PROP_ID_loadTime:{
               java.time.LocalDateTime typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLocalDateTime(value,
                       err-> newTypeConversionError(PROP_NAME_loadTime));
               }
               setLoadTime(typedValue);
               break;
            }
        
            case PROP_ID_unloadTime:{
               java.time.LocalDateTime typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLocalDateTime(value,
                       err-> newTypeConversionError(PROP_NAME_unloadTime));
               }
               setUnloadTime(typedValue);
               break;
            }
        
            case PROP_ID_loadLocationId:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_loadLocationId));
               }
               setLoadLocationId(typedValue);
               break;
            }
        
            case PROP_ID_unloadLocationId:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_unloadLocationId));
               }
               setUnloadLocationId(typedValue);
               break;
            }
        
            case PROP_ID_voyageId:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_voyageId));
               }
               setVoyageId(typedValue);
               break;
            }
        
            case PROP_ID_cargoId:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_cargoId));
               }
               setCargoId(typedValue);
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
               this._id = (java.lang.Long)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_loadTime:{
               onInitProp(propId);
               this._loadTime = (java.time.LocalDateTime)value;
               
               break;
            }
        
            case PROP_ID_unloadTime:{
               onInitProp(propId);
               this._unloadTime = (java.time.LocalDateTime)value;
               
               break;
            }
        
            case PROP_ID_loadLocationId:{
               onInitProp(propId);
               this._loadLocationId = (java.lang.Long)value;
               
               break;
            }
        
            case PROP_ID_unloadLocationId:{
               onInitProp(propId);
               this._unloadLocationId = (java.lang.Long)value;
               
               break;
            }
        
            case PROP_ID_voyageId:{
               onInitProp(propId);
               this._voyageId = (java.lang.Long)value;
               
               break;
            }
        
            case PROP_ID_cargoId:{
               onInitProp(propId);
               this._cargoId = (java.lang.Long)value;
               
               break;
            }
        
           default:
              super.orm_internalSet(propId,value);
        }
    }

    
    /**
     * Id: ID
     */
    public java.lang.Long getId(){
         onPropGet(PROP_ID_id);
         return _id;
    }

    /**
     * Id: ID
     */
    public void setId(java.lang.Long value){
        if(onPropSet(PROP_ID_id,value)){
            this._id = value;
            internalClearRefs(PROP_ID_id);
            orm_id();
        }
    }
    
    /**
     * 装货时间: LOAD_TIME
     */
    public java.time.LocalDateTime getLoadTime(){
         onPropGet(PROP_ID_loadTime);
         return _loadTime;
    }

    /**
     * 装货时间: LOAD_TIME
     */
    public void setLoadTime(java.time.LocalDateTime value){
        if(onPropSet(PROP_ID_loadTime,value)){
            this._loadTime = value;
            internalClearRefs(PROP_ID_loadTime);
            
        }
    }
    
    /**
     * 卸货时间: UNLOAD_TIME
     */
    public java.time.LocalDateTime getUnloadTime(){
         onPropGet(PROP_ID_unloadTime);
         return _unloadTime;
    }

    /**
     * 卸货时间: UNLOAD_TIME
     */
    public void setUnloadTime(java.time.LocalDateTime value){
        if(onPropSet(PROP_ID_unloadTime,value)){
            this._unloadTime = value;
            internalClearRefs(PROP_ID_unloadTime);
            
        }
    }
    
    /**
     * 装货地点: LOAD_LOCATION_ID
     */
    public java.lang.Long getLoadLocationId(){
         onPropGet(PROP_ID_loadLocationId);
         return _loadLocationId;
    }

    /**
     * 装货地点: LOAD_LOCATION_ID
     */
    public void setLoadLocationId(java.lang.Long value){
        if(onPropSet(PROP_ID_loadLocationId,value)){
            this._loadLocationId = value;
            internalClearRefs(PROP_ID_loadLocationId);
            
        }
    }
    
    /**
     * 卸货地点: UNLOAD_LOCATION_ID
     */
    public java.lang.Long getUnloadLocationId(){
         onPropGet(PROP_ID_unloadLocationId);
         return _unloadLocationId;
    }

    /**
     * 卸货地点: UNLOAD_LOCATION_ID
     */
    public void setUnloadLocationId(java.lang.Long value){
        if(onPropSet(PROP_ID_unloadLocationId,value)){
            this._unloadLocationId = value;
            internalClearRefs(PROP_ID_unloadLocationId);
            
        }
    }
    
    /**
     * 航程ID: VOYAGE_ID
     */
    public java.lang.Long getVoyageId(){
         onPropGet(PROP_ID_voyageId);
         return _voyageId;
    }

    /**
     * 航程ID: VOYAGE_ID
     */
    public void setVoyageId(java.lang.Long value){
        if(onPropSet(PROP_ID_voyageId,value)){
            this._voyageId = value;
            internalClearRefs(PROP_ID_voyageId);
            
        }
    }
    
    /**
     * 货物ID: CARGO_ID
     */
    public java.lang.Long getCargoId(){
         onPropGet(PROP_ID_cargoId);
         return _cargoId;
    }

    /**
     * 货物ID: CARGO_ID
     */
    public void setCargoId(java.lang.Long value){
        if(onPropSet(PROP_ID_cargoId,value)){
            this._cargoId = value;
            internalClearRefs(PROP_ID_cargoId);
            
        }
    }
    
    /**
     * 装货地点
     */
    public app.demo.ddd.entity.Location getLoadLocation(){
       return (app.demo.ddd.entity.Location)internalGetRefEntity(PROP_NAME_loadLocation);
    }

    public void setLoadLocation(app.demo.ddd.entity.Location refEntity){
   
           if(refEntity == null){
           
                   this.setLoadLocationId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_loadLocation, refEntity,()->{
           
                           this.setLoadLocationId(refEntity.getId());
                       
           });
           }
       
    }
       
    /**
     * 货物
     */
    public app.demo.ddd.entity.Cargo getCargo(){
       return (app.demo.ddd.entity.Cargo)internalGetRefEntity(PROP_NAME_cargo);
    }

    public void setCargo(app.demo.ddd.entity.Cargo refEntity){
   
           if(refEntity == null){
           
                   this.setCargoId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_cargo, refEntity,()->{
           
                           this.setCargoId(refEntity.getId());
                       
           });
           }
       
    }
       
    /**
     * 航程
     */
    public app.demo.ddd.entity.Voyage getVoyage(){
       return (app.demo.ddd.entity.Voyage)internalGetRefEntity(PROP_NAME_voyage);
    }

    public void setVoyage(app.demo.ddd.entity.Voyage refEntity){
   
           if(refEntity == null){
           
                   this.setVoyageId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_voyage, refEntity,()->{
           
                           this.setVoyageId(refEntity.getId());
                       
           });
           }
       
    }
       
    /**
     * 卸载地点
     */
    public app.demo.ddd.entity.Location getUnloadLocation(){
       return (app.demo.ddd.entity.Location)internalGetRefEntity(PROP_NAME_unloadLocation);
    }

    public void setUnloadLocation(app.demo.ddd.entity.Location refEntity){
   
           if(refEntity == null){
           
                   this.setUnloadLocationId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_unloadLocation, refEntity,()->{
           
                           this.setUnloadLocationId(refEntity.getId());
                       
           });
           }
       
    }
       
}
// resume CPD analysis - CPD-ON
