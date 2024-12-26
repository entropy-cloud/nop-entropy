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

import app.demo.ddd.entity.CarrierMovement;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  运输动作: carrier_movement
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _CarrierMovement extends DynamicOrmEntity{
    
    /* Id: ID BIGINT */
    public static final String PROP_NAME_id = "id";
    public static final int PROP_ID_id = 1;
    
    /* 到达时间: ARRIVAL_TIME DATETIME */
    public static final String PROP_NAME_arrivalTime = "arrivalTime";
    public static final int PROP_ID_arrivalTime = 2;
    
    /* 出发时间: DEPARTURE_TIME DATETIME */
    public static final String PROP_NAME_departureTime = "departureTime";
    public static final int PROP_ID_departureTime = 3;
    
    /* 到达地点ID: ARRIVAL_LOCATION_ID BIGINT */
    public static final String PROP_NAME_arrivalLocationId = "arrivalLocationId";
    public static final int PROP_ID_arrivalLocationId = 4;
    
    /* 出发地点ID: DEPARTURE_LOCATION_ID BIGINT */
    public static final String PROP_NAME_departureLocationId = "departureLocationId";
    public static final int PROP_ID_departureLocationId = 5;
    
    /* 航程ID: VOYAGE_ID BIGINT */
    public static final String PROP_NAME_voyageId = "voyageId";
    public static final int PROP_ID_voyageId = 6;
    

    private static int _PROP_ID_BOUND = 7;

    
    /* relation: 航程 */
    public static final String PROP_NAME_voyage = "voyage";
    
    /* relation: 出发地点 */
    public static final String PROP_NAME_departureLocation = "departureLocation";
    
    /* relation: 到达地点 */
    public static final String PROP_NAME_arrivalLocation = "arrivalLocation";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_id);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_id};

    private static final String[] PROP_ID_TO_NAME = new String[7];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_id] = PROP_NAME_id;
          PROP_NAME_TO_ID.put(PROP_NAME_id, PROP_ID_id);
      
          PROP_ID_TO_NAME[PROP_ID_arrivalTime] = PROP_NAME_arrivalTime;
          PROP_NAME_TO_ID.put(PROP_NAME_arrivalTime, PROP_ID_arrivalTime);
      
          PROP_ID_TO_NAME[PROP_ID_departureTime] = PROP_NAME_departureTime;
          PROP_NAME_TO_ID.put(PROP_NAME_departureTime, PROP_ID_departureTime);
      
          PROP_ID_TO_NAME[PROP_ID_arrivalLocationId] = PROP_NAME_arrivalLocationId;
          PROP_NAME_TO_ID.put(PROP_NAME_arrivalLocationId, PROP_ID_arrivalLocationId);
      
          PROP_ID_TO_NAME[PROP_ID_departureLocationId] = PROP_NAME_departureLocationId;
          PROP_NAME_TO_ID.put(PROP_NAME_departureLocationId, PROP_ID_departureLocationId);
      
          PROP_ID_TO_NAME[PROP_ID_voyageId] = PROP_NAME_voyageId;
          PROP_NAME_TO_ID.put(PROP_NAME_voyageId, PROP_ID_voyageId);
      
    }

    
    /* Id: ID */
    private java.lang.Long _id;
    
    /* 到达时间: ARRIVAL_TIME */
    private java.time.LocalDateTime _arrivalTime;
    
    /* 出发时间: DEPARTURE_TIME */
    private java.time.LocalDateTime _departureTime;
    
    /* 到达地点ID: ARRIVAL_LOCATION_ID */
    private java.lang.Long _arrivalLocationId;
    
    /* 出发地点ID: DEPARTURE_LOCATION_ID */
    private java.lang.Long _departureLocationId;
    
    /* 航程ID: VOYAGE_ID */
    private java.lang.Long _voyageId;
    

    public _CarrierMovement(){
        // for debug
    }

    protected CarrierMovement newInstance(){
        CarrierMovement entity = new CarrierMovement();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public CarrierMovement cloneInstance() {
        CarrierMovement entity = newInstance();
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
      return "app.demo.ddd.entity.CarrierMovement";
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
        
            case PROP_ID_arrivalTime:
               return getArrivalTime();
        
            case PROP_ID_departureTime:
               return getDepartureTime();
        
            case PROP_ID_arrivalLocationId:
               return getArrivalLocationId();
        
            case PROP_ID_departureLocationId:
               return getDepartureLocationId();
        
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
        
            case PROP_ID_arrivalTime:{
               java.time.LocalDateTime typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLocalDateTime(value,
                       err-> newTypeConversionError(PROP_NAME_arrivalTime));
               }
               setArrivalTime(typedValue);
               break;
            }
        
            case PROP_ID_departureTime:{
               java.time.LocalDateTime typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLocalDateTime(value,
                       err-> newTypeConversionError(PROP_NAME_departureTime));
               }
               setDepartureTime(typedValue);
               break;
            }
        
            case PROP_ID_arrivalLocationId:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_arrivalLocationId));
               }
               setArrivalLocationId(typedValue);
               break;
            }
        
            case PROP_ID_departureLocationId:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_departureLocationId));
               }
               setDepartureLocationId(typedValue);
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
        
            case PROP_ID_arrivalTime:{
               onInitProp(propId);
               this._arrivalTime = (java.time.LocalDateTime)value;
               
               break;
            }
        
            case PROP_ID_departureTime:{
               onInitProp(propId);
               this._departureTime = (java.time.LocalDateTime)value;
               
               break;
            }
        
            case PROP_ID_arrivalLocationId:{
               onInitProp(propId);
               this._arrivalLocationId = (java.lang.Long)value;
               
               break;
            }
        
            case PROP_ID_departureLocationId:{
               onInitProp(propId);
               this._departureLocationId = (java.lang.Long)value;
               
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
    public final java.lang.Long getId(){
         onPropGet(PROP_ID_id);
         return _id;
    }

    /**
     * Id: ID
     */
    public final void setId(java.lang.Long value){
        if(onPropSet(PROP_ID_id,value)){
            this._id = value;
            internalClearRefs(PROP_ID_id);
            orm_id();
        }
    }
    
    /**
     * 到达时间: ARRIVAL_TIME
     */
    public final java.time.LocalDateTime getArrivalTime(){
         onPropGet(PROP_ID_arrivalTime);
         return _arrivalTime;
    }

    /**
     * 到达时间: ARRIVAL_TIME
     */
    public final void setArrivalTime(java.time.LocalDateTime value){
        if(onPropSet(PROP_ID_arrivalTime,value)){
            this._arrivalTime = value;
            internalClearRefs(PROP_ID_arrivalTime);
            
        }
    }
    
    /**
     * 出发时间: DEPARTURE_TIME
     */
    public final java.time.LocalDateTime getDepartureTime(){
         onPropGet(PROP_ID_departureTime);
         return _departureTime;
    }

    /**
     * 出发时间: DEPARTURE_TIME
     */
    public final void setDepartureTime(java.time.LocalDateTime value){
        if(onPropSet(PROP_ID_departureTime,value)){
            this._departureTime = value;
            internalClearRefs(PROP_ID_departureTime);
            
        }
    }
    
    /**
     * 到达地点ID: ARRIVAL_LOCATION_ID
     */
    public final java.lang.Long getArrivalLocationId(){
         onPropGet(PROP_ID_arrivalLocationId);
         return _arrivalLocationId;
    }

    /**
     * 到达地点ID: ARRIVAL_LOCATION_ID
     */
    public final void setArrivalLocationId(java.lang.Long value){
        if(onPropSet(PROP_ID_arrivalLocationId,value)){
            this._arrivalLocationId = value;
            internalClearRefs(PROP_ID_arrivalLocationId);
            
        }
    }
    
    /**
     * 出发地点ID: DEPARTURE_LOCATION_ID
     */
    public final java.lang.Long getDepartureLocationId(){
         onPropGet(PROP_ID_departureLocationId);
         return _departureLocationId;
    }

    /**
     * 出发地点ID: DEPARTURE_LOCATION_ID
     */
    public final void setDepartureLocationId(java.lang.Long value){
        if(onPropSet(PROP_ID_departureLocationId,value)){
            this._departureLocationId = value;
            internalClearRefs(PROP_ID_departureLocationId);
            
        }
    }
    
    /**
     * 航程ID: VOYAGE_ID
     */
    public final java.lang.Long getVoyageId(){
         onPropGet(PROP_ID_voyageId);
         return _voyageId;
    }

    /**
     * 航程ID: VOYAGE_ID
     */
    public final void setVoyageId(java.lang.Long value){
        if(onPropSet(PROP_ID_voyageId,value)){
            this._voyageId = value;
            internalClearRefs(PROP_ID_voyageId);
            
        }
    }
    
    /**
     * 航程
     */
    public final app.demo.ddd.entity.Voyage getVoyage(){
       return (app.demo.ddd.entity.Voyage)internalGetRefEntity(PROP_NAME_voyage);
    }

    public final void setVoyage(app.demo.ddd.entity.Voyage refEntity){
   
           if(refEntity == null){
           
                   this.setVoyageId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_voyage, refEntity,()->{
           
                           this.setVoyageId(refEntity.getId());
                       
           });
           }
       
    }
       
    /**
     * 出发地点
     */
    public final app.demo.ddd.entity.Location getDepartureLocation(){
       return (app.demo.ddd.entity.Location)internalGetRefEntity(PROP_NAME_departureLocation);
    }

    public final void setDepartureLocation(app.demo.ddd.entity.Location refEntity){
   
           if(refEntity == null){
           
                   this.setDepartureLocationId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_departureLocation, refEntity,()->{
           
                           this.setDepartureLocationId(refEntity.getId());
                       
           });
           }
       
    }
       
    /**
     * 到达地点
     */
    public final app.demo.ddd.entity.Location getArrivalLocation(){
       return (app.demo.ddd.entity.Location)internalGetRefEntity(PROP_NAME_arrivalLocation);
    }

    public final void setArrivalLocation(app.demo.ddd.entity.Location refEntity){
   
           if(refEntity == null){
           
                   this.setArrivalLocationId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_arrivalLocation, refEntity,()->{
           
                           this.setArrivalLocationId(refEntity.getId());
                       
           });
           }
       
    }
       
}
// resume CPD analysis - CPD-ON
