package demo.orm.entity._gen;

import io.nop.orm.model.IEntityModel;
import io.nop.orm.support.DynamicOrmEntity;
import io.nop.orm.support.OrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.orm.IOrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.api.core.convert.ConvertHelper;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;

import demo.orm.entity.Classroom;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  : classroom
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _Classroom extends DynamicOrmEntity{
    
    /* : building VARCHAR */
    public static final String PROP_NAME_building = "building";
    public static final int PROP_ID_building = 1;
    
    /* : room_number VARCHAR */
    public static final String PROP_NAME_roomNumber = "roomNumber";
    public static final int PROP_ID_roomNumber = 2;
    
    /* : capacity DECIMAL */
    public static final String PROP_NAME_capacity = "capacity";
    public static final int PROP_ID_capacity = 3;
    

    private static int _PROP_ID_BOUND = 4;

    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_building,PROP_NAME_roomNumber);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_building,PROP_ID_roomNumber};

    private static final String[] PROP_ID_TO_NAME = new String[4];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_building] = PROP_NAME_building;
          PROP_NAME_TO_ID.put(PROP_NAME_building, PROP_ID_building);
      
          PROP_ID_TO_NAME[PROP_ID_roomNumber] = PROP_NAME_roomNumber;
          PROP_NAME_TO_ID.put(PROP_NAME_roomNumber, PROP_ID_roomNumber);
      
          PROP_ID_TO_NAME[PROP_ID_capacity] = PROP_NAME_capacity;
          PROP_NAME_TO_ID.put(PROP_NAME_capacity, PROP_ID_capacity);
      
    }

    
    /* : building */
    private java.lang.String _building;
    
    /* : room_number */
    private java.lang.String _roomNumber;
    
    /* : capacity */
    private java.math.BigDecimal _capacity;
    

    public _Classroom(){
        // for debug
    }

    protected Classroom newInstance(){
       return new Classroom();
    }

    @Override
    public Classroom cloneInstance() {
        Classroom entity = newInstance();
        orm_forEachInitedProp((value, propId) -> {
            entity.onInitProp(propId);
        });
        return entity;
    }

    @Override
    public String orm_entityName() {
      // 如果存在实体模型对象，则以模型对象上的设置为准
      IEntityModel entityModel = orm_entityModel();
      if(entityModel != null)
          return entityModel.getName();
      return "demo.orm.entity.Classroom";
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
    
        return buildCompositeId(PK_PROP_NAMES,PK_PROP_IDS);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_building || propId == PROP_ID_roomNumber;
          
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
        
            case PROP_ID_building:
               return getBuilding();
        
            case PROP_ID_roomNumber:
               return getRoomNumber();
        
            case PROP_ID_capacity:
               return getCapacity();
        
           default:
              return super.orm_propValue(propId);
        }
    }

    

    @Override
    public void orm_propValue(int propId, Object value){
        switch(propId){
        
            case PROP_ID_building:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_building));
               }
               setBuilding(typedValue);
               break;
            }
        
            case PROP_ID_roomNumber:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_roomNumber));
               }
               setRoomNumber(typedValue);
               break;
            }
        
            case PROP_ID_capacity:{
               java.math.BigDecimal typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toBigDecimal(value,
                       err-> newTypeConversionError(PROP_NAME_capacity));
               }
               setCapacity(typedValue);
               break;
            }
        
           default:
              super.orm_propValue(propId,value);
        }
    }

    @Override
    public void orm_internalSet(int propId, Object value) {
        switch(propId){
        
            case PROP_ID_building:{
               onInitProp(propId);
               this._building = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_roomNumber:{
               onInitProp(propId);
               this._roomNumber = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_capacity:{
               onInitProp(propId);
               this._capacity = (java.math.BigDecimal)value;
               
               break;
            }
        
           default:
              super.orm_internalSet(propId,value);
        }
    }

    
    /**
     * : building
     */
    public java.lang.String getBuilding(){
         onPropGet(PROP_ID_building);
         return _building;
    }

    /**
     * : building
     */
    public void setBuilding(java.lang.String value){
        if(onPropSet(PROP_ID_building,value)){
            this._building = value;
            internalClearRefs(PROP_ID_building);
            orm_id();
        }
    }
    
    /**
     * : room_number
     */
    public java.lang.String getRoomNumber(){
         onPropGet(PROP_ID_roomNumber);
         return _roomNumber;
    }

    /**
     * : room_number
     */
    public void setRoomNumber(java.lang.String value){
        if(onPropSet(PROP_ID_roomNumber,value)){
            this._roomNumber = value;
            internalClearRefs(PROP_ID_roomNumber);
            orm_id();
        }
    }
    
    /**
     * : capacity
     */
    public java.math.BigDecimal getCapacity(){
         onPropGet(PROP_ID_capacity);
         return _capacity;
    }

    /**
     * : capacity
     */
    public void setCapacity(java.math.BigDecimal value){
        if(onPropSet(PROP_ID_capacity,value)){
            this._capacity = value;
            internalClearRefs(PROP_ID_capacity);
            
        }
    }
    
}
// resume CPD analysis - CPD-ON
