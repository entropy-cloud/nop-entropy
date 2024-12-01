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

import app.demo.ddd.entity.HandlingEvent;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  业务时间: handling_event
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _HandlingEvent extends DynamicOrmEntity{
    
    /* Id: ID BIGINT */
    public static final String PROP_NAME_id = "id";
    public static final int PROP_ID_id = 1;
    
    /* 完成时间: COMPLETION_TIME DATETIME */
    public static final String PROP_NAME_completionTime = "completionTime";
    public static final int PROP_ID_completionTime = 2;
    
    /* 注册时间: REGISTRATION_TIME DATETIME */
    public static final String PROP_NAME_registrationTime = "registrationTime";
    public static final int PROP_ID_registrationTime = 3;
    
    /* 类型: TYPE VARCHAR */
    public static final String PROP_NAME_type = "type";
    public static final int PROP_ID_type = 4;
    
    /* 货物ID: CARGO_ID BIGINT */
    public static final String PROP_NAME_cargoId = "cargoId";
    public static final int PROP_ID_cargoId = 5;
    
    /* 位置ID: LOCATION_ID BIGINT */
    public static final String PROP_NAME_locationId = "locationId";
    public static final int PROP_ID_locationId = 6;
    
    /* 航程ID: VOYAGE_ID BIGINT */
    public static final String PROP_NAME_voyageId = "voyageId";
    public static final int PROP_ID_voyageId = 7;
    

    private static int _PROP_ID_BOUND = 8;

    
    /* relation: 货物 */
    public static final String PROP_NAME_cargo = "cargo";
    
    /* relation: 航程 */
    public static final String PROP_NAME_voyage = "voyage";
    
    /* relation: 位置 */
    public static final String PROP_NAME_location = "location";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_id);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_id};

    private static final String[] PROP_ID_TO_NAME = new String[8];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_id] = PROP_NAME_id;
          PROP_NAME_TO_ID.put(PROP_NAME_id, PROP_ID_id);
      
          PROP_ID_TO_NAME[PROP_ID_completionTime] = PROP_NAME_completionTime;
          PROP_NAME_TO_ID.put(PROP_NAME_completionTime, PROP_ID_completionTime);
      
          PROP_ID_TO_NAME[PROP_ID_registrationTime] = PROP_NAME_registrationTime;
          PROP_NAME_TO_ID.put(PROP_NAME_registrationTime, PROP_ID_registrationTime);
      
          PROP_ID_TO_NAME[PROP_ID_type] = PROP_NAME_type;
          PROP_NAME_TO_ID.put(PROP_NAME_type, PROP_ID_type);
      
          PROP_ID_TO_NAME[PROP_ID_cargoId] = PROP_NAME_cargoId;
          PROP_NAME_TO_ID.put(PROP_NAME_cargoId, PROP_ID_cargoId);
      
          PROP_ID_TO_NAME[PROP_ID_locationId] = PROP_NAME_locationId;
          PROP_NAME_TO_ID.put(PROP_NAME_locationId, PROP_ID_locationId);
      
          PROP_ID_TO_NAME[PROP_ID_voyageId] = PROP_NAME_voyageId;
          PROP_NAME_TO_ID.put(PROP_NAME_voyageId, PROP_ID_voyageId);
      
    }

    
    /* Id: ID */
    private java.lang.Long _id;
    
    /* 完成时间: COMPLETION_TIME */
    private java.time.LocalDateTime _completionTime;
    
    /* 注册时间: REGISTRATION_TIME */
    private java.time.LocalDateTime _registrationTime;
    
    /* 类型: TYPE */
    private java.lang.String _type;
    
    /* 货物ID: CARGO_ID */
    private java.lang.Long _cargoId;
    
    /* 位置ID: LOCATION_ID */
    private java.lang.Long _locationId;
    
    /* 航程ID: VOYAGE_ID */
    private java.lang.Long _voyageId;
    

    public _HandlingEvent(){
        // for debug
    }

    protected HandlingEvent newInstance(){
        HandlingEvent entity = new HandlingEvent();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public HandlingEvent cloneInstance() {
        HandlingEvent entity = newInstance();
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
      return "app.demo.ddd.entity.HandlingEvent";
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
        
            case PROP_ID_completionTime:
               return getCompletionTime();
        
            case PROP_ID_registrationTime:
               return getRegistrationTime();
        
            case PROP_ID_type:
               return getType();
        
            case PROP_ID_cargoId:
               return getCargoId();
        
            case PROP_ID_locationId:
               return getLocationId();
        
            case PROP_ID_voyageId:
               return getVoyageId();
        
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
        
            case PROP_ID_completionTime:{
               java.time.LocalDateTime typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLocalDateTime(value,
                       err-> newTypeConversionError(PROP_NAME_completionTime));
               }
               setCompletionTime(typedValue);
               break;
            }
        
            case PROP_ID_registrationTime:{
               java.time.LocalDateTime typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLocalDateTime(value,
                       err-> newTypeConversionError(PROP_NAME_registrationTime));
               }
               setRegistrationTime(typedValue);
               break;
            }
        
            case PROP_ID_type:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_type));
               }
               setType(typedValue);
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
        
            case PROP_ID_locationId:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_locationId));
               }
               setLocationId(typedValue);
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
        
            case PROP_ID_completionTime:{
               onInitProp(propId);
               this._completionTime = (java.time.LocalDateTime)value;
               
               break;
            }
        
            case PROP_ID_registrationTime:{
               onInitProp(propId);
               this._registrationTime = (java.time.LocalDateTime)value;
               
               break;
            }
        
            case PROP_ID_type:{
               onInitProp(propId);
               this._type = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_cargoId:{
               onInitProp(propId);
               this._cargoId = (java.lang.Long)value;
               
               break;
            }
        
            case PROP_ID_locationId:{
               onInitProp(propId);
               this._locationId = (java.lang.Long)value;
               
               break;
            }
        
            case PROP_ID_voyageId:{
               onInitProp(propId);
               this._voyageId = (java.lang.Long)value;
               
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
     * 完成时间: COMPLETION_TIME
     */
    public java.time.LocalDateTime getCompletionTime(){
         onPropGet(PROP_ID_completionTime);
         return _completionTime;
    }

    /**
     * 完成时间: COMPLETION_TIME
     */
    public void setCompletionTime(java.time.LocalDateTime value){
        if(onPropSet(PROP_ID_completionTime,value)){
            this._completionTime = value;
            internalClearRefs(PROP_ID_completionTime);
            
        }
    }
    
    /**
     * 注册时间: REGISTRATION_TIME
     */
    public java.time.LocalDateTime getRegistrationTime(){
         onPropGet(PROP_ID_registrationTime);
         return _registrationTime;
    }

    /**
     * 注册时间: REGISTRATION_TIME
     */
    public void setRegistrationTime(java.time.LocalDateTime value){
        if(onPropSet(PROP_ID_registrationTime,value)){
            this._registrationTime = value;
            internalClearRefs(PROP_ID_registrationTime);
            
        }
    }
    
    /**
     * 类型: TYPE
     */
    public java.lang.String getType(){
         onPropGet(PROP_ID_type);
         return _type;
    }

    /**
     * 类型: TYPE
     */
    public void setType(java.lang.String value){
        if(onPropSet(PROP_ID_type,value)){
            this._type = value;
            internalClearRefs(PROP_ID_type);
            
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
     * 位置ID: LOCATION_ID
     */
    public java.lang.Long getLocationId(){
         onPropGet(PROP_ID_locationId);
         return _locationId;
    }

    /**
     * 位置ID: LOCATION_ID
     */
    public void setLocationId(java.lang.Long value){
        if(onPropSet(PROP_ID_locationId,value)){
            this._locationId = value;
            internalClearRefs(PROP_ID_locationId);
            
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
     * 位置
     */
    public app.demo.ddd.entity.Location getLocation(){
       return (app.demo.ddd.entity.Location)internalGetRefEntity(PROP_NAME_location);
    }

    public void setLocation(app.demo.ddd.entity.Location refEntity){
   
           if(refEntity == null){
           
                   this.setLocationId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_location, refEntity,()->{
           
                           this.setLocationId(refEntity.getId());
                       
           });
           }
       
    }
       
}
// resume CPD analysis - CPD-ON
