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

import demo.orm.entity.TimeSlot;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  : time_slot
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _TimeSlot extends DynamicOrmEntity{
    
    /* : time_slot_id VARCHAR */
    public static final String PROP_NAME_timeSlotId = "timeSlotId";
    public static final int PROP_ID_timeSlotId = 1;
    
    /* : day VARCHAR */
    public static final String PROP_NAME_day = "day";
    public static final int PROP_ID_day = 2;
    
    /* : start_hr DECIMAL */
    public static final String PROP_NAME_startHr = "startHr";
    public static final int PROP_ID_startHr = 3;
    
    /* : start_min DECIMAL */
    public static final String PROP_NAME_startMin = "startMin";
    public static final int PROP_ID_startMin = 4;
    
    /* : end_hr DECIMAL */
    public static final String PROP_NAME_endHr = "endHr";
    public static final int PROP_ID_endHr = 5;
    
    /* : end_min DECIMAL */
    public static final String PROP_NAME_endMin = "endMin";
    public static final int PROP_ID_endMin = 6;
    

    private static int _PROP_ID_BOUND = 7;

    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_timeSlotId,PROP_NAME_day,PROP_NAME_startHr,PROP_NAME_startMin);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_timeSlotId,PROP_ID_day,PROP_ID_startHr,PROP_ID_startMin};

    private static final String[] PROP_ID_TO_NAME = new String[7];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_timeSlotId] = PROP_NAME_timeSlotId;
          PROP_NAME_TO_ID.put(PROP_NAME_timeSlotId, PROP_ID_timeSlotId);
      
          PROP_ID_TO_NAME[PROP_ID_day] = PROP_NAME_day;
          PROP_NAME_TO_ID.put(PROP_NAME_day, PROP_ID_day);
      
          PROP_ID_TO_NAME[PROP_ID_startHr] = PROP_NAME_startHr;
          PROP_NAME_TO_ID.put(PROP_NAME_startHr, PROP_ID_startHr);
      
          PROP_ID_TO_NAME[PROP_ID_startMin] = PROP_NAME_startMin;
          PROP_NAME_TO_ID.put(PROP_NAME_startMin, PROP_ID_startMin);
      
          PROP_ID_TO_NAME[PROP_ID_endHr] = PROP_NAME_endHr;
          PROP_NAME_TO_ID.put(PROP_NAME_endHr, PROP_ID_endHr);
      
          PROP_ID_TO_NAME[PROP_ID_endMin] = PROP_NAME_endMin;
          PROP_NAME_TO_ID.put(PROP_NAME_endMin, PROP_ID_endMin);
      
    }

    
    /* : time_slot_id */
    private java.lang.String _timeSlotId;
    
    /* : day */
    private java.lang.String _day;
    
    /* : start_hr */
    private java.math.BigDecimal _startHr;
    
    /* : start_min */
    private java.math.BigDecimal _startMin;
    
    /* : end_hr */
    private java.math.BigDecimal _endHr;
    
    /* : end_min */
    private java.math.BigDecimal _endMin;
    

    public _TimeSlot(){
        // for debug
    }

    protected TimeSlot newInstance(){
       return new TimeSlot();
    }

    @Override
    public TimeSlot cloneInstance() {
        TimeSlot entity = newInstance();
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
      return "demo.orm.entity.TimeSlot";
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
        
            return propId == PROP_ID_timeSlotId || propId == PROP_ID_day || propId == PROP_ID_startHr || propId == PROP_ID_startMin;
          
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
        
            case PROP_ID_timeSlotId:
               return getTimeSlotId();
        
            case PROP_ID_day:
               return getDay();
        
            case PROP_ID_startHr:
               return getStartHr();
        
            case PROP_ID_startMin:
               return getStartMin();
        
            case PROP_ID_endHr:
               return getEndHr();
        
            case PROP_ID_endMin:
               return getEndMin();
        
           default:
              return super.orm_propValue(propId);
        }
    }

    

    @Override
    public void orm_propValue(int propId, Object value){
        switch(propId){
        
            case PROP_ID_timeSlotId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_timeSlotId));
               }
               setTimeSlotId(typedValue);
               break;
            }
        
            case PROP_ID_day:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_day));
               }
               setDay(typedValue);
               break;
            }
        
            case PROP_ID_startHr:{
               java.math.BigDecimal typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toBigDecimal(value,
                       err-> newTypeConversionError(PROP_NAME_startHr));
               }
               setStartHr(typedValue);
               break;
            }
        
            case PROP_ID_startMin:{
               java.math.BigDecimal typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toBigDecimal(value,
                       err-> newTypeConversionError(PROP_NAME_startMin));
               }
               setStartMin(typedValue);
               break;
            }
        
            case PROP_ID_endHr:{
               java.math.BigDecimal typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toBigDecimal(value,
                       err-> newTypeConversionError(PROP_NAME_endHr));
               }
               setEndHr(typedValue);
               break;
            }
        
            case PROP_ID_endMin:{
               java.math.BigDecimal typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toBigDecimal(value,
                       err-> newTypeConversionError(PROP_NAME_endMin));
               }
               setEndMin(typedValue);
               break;
            }
        
           default:
              super.orm_propValue(propId,value);
        }
    }

    @Override
    public void orm_internalSet(int propId, Object value) {
        switch(propId){
        
            case PROP_ID_timeSlotId:{
               onInitProp(propId);
               this._timeSlotId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_day:{
               onInitProp(propId);
               this._day = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_startHr:{
               onInitProp(propId);
               this._startHr = (java.math.BigDecimal)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_startMin:{
               onInitProp(propId);
               this._startMin = (java.math.BigDecimal)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_endHr:{
               onInitProp(propId);
               this._endHr = (java.math.BigDecimal)value;
               
               break;
            }
        
            case PROP_ID_endMin:{
               onInitProp(propId);
               this._endMin = (java.math.BigDecimal)value;
               
               break;
            }
        
           default:
              super.orm_internalSet(propId,value);
        }
    }

    
    /**
     * : time_slot_id
     */
    public java.lang.String getTimeSlotId(){
         onPropGet(PROP_ID_timeSlotId);
         return _timeSlotId;
    }

    /**
     * : time_slot_id
     */
    public void setTimeSlotId(java.lang.String value){
        if(onPropSet(PROP_ID_timeSlotId,value)){
            this._timeSlotId = value;
            internalClearRefs(PROP_ID_timeSlotId);
            orm_id();
        }
    }
    
    /**
     * : day
     */
    public java.lang.String getDay(){
         onPropGet(PROP_ID_day);
         return _day;
    }

    /**
     * : day
     */
    public void setDay(java.lang.String value){
        if(onPropSet(PROP_ID_day,value)){
            this._day = value;
            internalClearRefs(PROP_ID_day);
            orm_id();
        }
    }
    
    /**
     * : start_hr
     */
    public java.math.BigDecimal getStartHr(){
         onPropGet(PROP_ID_startHr);
         return _startHr;
    }

    /**
     * : start_hr
     */
    public void setStartHr(java.math.BigDecimal value){
        if(onPropSet(PROP_ID_startHr,value)){
            this._startHr = value;
            internalClearRefs(PROP_ID_startHr);
            orm_id();
        }
    }
    
    /**
     * : start_min
     */
    public java.math.BigDecimal getStartMin(){
         onPropGet(PROP_ID_startMin);
         return _startMin;
    }

    /**
     * : start_min
     */
    public void setStartMin(java.math.BigDecimal value){
        if(onPropSet(PROP_ID_startMin,value)){
            this._startMin = value;
            internalClearRefs(PROP_ID_startMin);
            orm_id();
        }
    }
    
    /**
     * : end_hr
     */
    public java.math.BigDecimal getEndHr(){
         onPropGet(PROP_ID_endHr);
         return _endHr;
    }

    /**
     * : end_hr
     */
    public void setEndHr(java.math.BigDecimal value){
        if(onPropSet(PROP_ID_endHr,value)){
            this._endHr = value;
            internalClearRefs(PROP_ID_endHr);
            
        }
    }
    
    /**
     * : end_min
     */
    public java.math.BigDecimal getEndMin(){
         onPropGet(PROP_ID_endMin);
         return _endMin;
    }

    /**
     * : end_min
     */
    public void setEndMin(java.math.BigDecimal value){
        if(onPropSet(PROP_ID_endMin,value)){
            this._endMin = value;
            internalClearRefs(PROP_ID_endMin);
            
        }
    }
    
}
// resume CPD analysis - CPD-ON
