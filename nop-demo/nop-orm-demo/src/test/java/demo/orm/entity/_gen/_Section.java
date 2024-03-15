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

import demo.orm.entity.Section;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  : section
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _Section extends DynamicOrmEntity{
    
    /* : course_id VARCHAR */
    public static final String PROP_NAME_courseId = "courseId";
    public static final int PROP_ID_courseId = 1;
    
    /* : sec_id VARCHAR */
    public static final String PROP_NAME_secId = "secId";
    public static final int PROP_ID_secId = 2;
    
    /* : semester VARCHAR */
    public static final String PROP_NAME_semester = "semester";
    public static final int PROP_ID_semester = 3;
    
    /* : year DECIMAL */
    public static final String PROP_NAME_year = "year";
    public static final int PROP_ID_year = 4;
    
    /* : building VARCHAR */
    public static final String PROP_NAME_building = "building";
    public static final int PROP_ID_building = 5;
    
    /* : room_number VARCHAR */
    public static final String PROP_NAME_roomNumber = "roomNumber";
    public static final int PROP_ID_roomNumber = 6;
    
    /* : time_slot_id VARCHAR */
    public static final String PROP_NAME_timeSlotId = "timeSlotId";
    public static final int PROP_ID_timeSlotId = 7;
    

    private static int _PROP_ID_BOUND = 8;

    
    /* relation:  */
    public static final String PROP_NAME_course = "course";
    
    /* relation:  */
    public static final String PROP_NAME_classroom = "classroom";
    
    /* relation:  */
    public static final String PROP_NAME_teachings = "teachings";
    
    /* relation:  */
    public static final String PROP_NAME_takings = "takings";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_courseId,PROP_NAME_secId,PROP_NAME_semester,PROP_NAME_year);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_courseId,PROP_ID_secId,PROP_ID_semester,PROP_ID_year};

    private static final String[] PROP_ID_TO_NAME = new String[8];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_courseId] = PROP_NAME_courseId;
          PROP_NAME_TO_ID.put(PROP_NAME_courseId, PROP_ID_courseId);
      
          PROP_ID_TO_NAME[PROP_ID_secId] = PROP_NAME_secId;
          PROP_NAME_TO_ID.put(PROP_NAME_secId, PROP_ID_secId);
      
          PROP_ID_TO_NAME[PROP_ID_semester] = PROP_NAME_semester;
          PROP_NAME_TO_ID.put(PROP_NAME_semester, PROP_ID_semester);
      
          PROP_ID_TO_NAME[PROP_ID_year] = PROP_NAME_year;
          PROP_NAME_TO_ID.put(PROP_NAME_year, PROP_ID_year);
      
          PROP_ID_TO_NAME[PROP_ID_building] = PROP_NAME_building;
          PROP_NAME_TO_ID.put(PROP_NAME_building, PROP_ID_building);
      
          PROP_ID_TO_NAME[PROP_ID_roomNumber] = PROP_NAME_roomNumber;
          PROP_NAME_TO_ID.put(PROP_NAME_roomNumber, PROP_ID_roomNumber);
      
          PROP_ID_TO_NAME[PROP_ID_timeSlotId] = PROP_NAME_timeSlotId;
          PROP_NAME_TO_ID.put(PROP_NAME_timeSlotId, PROP_ID_timeSlotId);
      
    }

    
    /* : course_id */
    private java.lang.String _courseId;
    
    /* : sec_id */
    private java.lang.String _secId;
    
    /* : semester */
    private java.lang.String _semester;
    
    /* : year */
    private java.math.BigDecimal _year;
    
    /* : building */
    private java.lang.String _building;
    
    /* : room_number */
    private java.lang.String _roomNumber;
    
    /* : time_slot_id */
    private java.lang.String _timeSlotId;
    

    public _Section(){
        // for debug
    }

    protected Section newInstance(){
       return new Section();
    }

    @Override
    public Section cloneInstance() {
        Section entity = newInstance();
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
      return "demo.orm.entity.Section";
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
        
            return propId == PROP_ID_courseId || propId == PROP_ID_secId || propId == PROP_ID_semester || propId == PROP_ID_year;
          
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
        
            case PROP_ID_courseId:
               return getCourseId();
        
            case PROP_ID_secId:
               return getSecId();
        
            case PROP_ID_semester:
               return getSemester();
        
            case PROP_ID_year:
               return getYear();
        
            case PROP_ID_building:
               return getBuilding();
        
            case PROP_ID_roomNumber:
               return getRoomNumber();
        
            case PROP_ID_timeSlotId:
               return getTimeSlotId();
        
           default:
              return super.orm_propValue(propId);
        }
    }

    

    @Override
    public void orm_propValue(int propId, Object value){
        switch(propId){
        
            case PROP_ID_courseId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_courseId));
               }
               setCourseId(typedValue);
               break;
            }
        
            case PROP_ID_secId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_secId));
               }
               setSecId(typedValue);
               break;
            }
        
            case PROP_ID_semester:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_semester));
               }
               setSemester(typedValue);
               break;
            }
        
            case PROP_ID_year:{
               java.math.BigDecimal typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toBigDecimal(value,
                       err-> newTypeConversionError(PROP_NAME_year));
               }
               setYear(typedValue);
               break;
            }
        
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
        
            case PROP_ID_timeSlotId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_timeSlotId));
               }
               setTimeSlotId(typedValue);
               break;
            }
        
           default:
              super.orm_propValue(propId,value);
        }
    }

    @Override
    public void orm_internalSet(int propId, Object value) {
        switch(propId){
        
            case PROP_ID_courseId:{
               onInitProp(propId);
               this._courseId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_secId:{
               onInitProp(propId);
               this._secId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_semester:{
               onInitProp(propId);
               this._semester = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_year:{
               onInitProp(propId);
               this._year = (java.math.BigDecimal)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_building:{
               onInitProp(propId);
               this._building = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_roomNumber:{
               onInitProp(propId);
               this._roomNumber = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_timeSlotId:{
               onInitProp(propId);
               this._timeSlotId = (java.lang.String)value;
               
               break;
            }
        
           default:
              super.orm_internalSet(propId,value);
        }
    }

    
    /**
     * : course_id
     */
    public java.lang.String getCourseId(){
         onPropGet(PROP_ID_courseId);
         return _courseId;
    }

    /**
     * : course_id
     */
    public void setCourseId(java.lang.String value){
        if(onPropSet(PROP_ID_courseId,value)){
            this._courseId = value;
            internalClearRefs(PROP_ID_courseId);
            orm_id();
        }
    }
    
    /**
     * : sec_id
     */
    public java.lang.String getSecId(){
         onPropGet(PROP_ID_secId);
         return _secId;
    }

    /**
     * : sec_id
     */
    public void setSecId(java.lang.String value){
        if(onPropSet(PROP_ID_secId,value)){
            this._secId = value;
            internalClearRefs(PROP_ID_secId);
            orm_id();
        }
    }
    
    /**
     * : semester
     */
    public java.lang.String getSemester(){
         onPropGet(PROP_ID_semester);
         return _semester;
    }

    /**
     * : semester
     */
    public void setSemester(java.lang.String value){
        if(onPropSet(PROP_ID_semester,value)){
            this._semester = value;
            internalClearRefs(PROP_ID_semester);
            orm_id();
        }
    }
    
    /**
     * : year
     */
    public java.math.BigDecimal getYear(){
         onPropGet(PROP_ID_year);
         return _year;
    }

    /**
     * : year
     */
    public void setYear(java.math.BigDecimal value){
        if(onPropSet(PROP_ID_year,value)){
            this._year = value;
            internalClearRefs(PROP_ID_year);
            orm_id();
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
            
        }
    }
    
    /**
     * 
     */
    public demo.orm.entity.Course getCourse(){
       return (demo.orm.entity.Course)internalGetRefEntity(PROP_NAME_course);
    }

    public void setCourse(demo.orm.entity.Course refEntity){
   
           if(refEntity == null){
           
                   this.setCourseId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_course, refEntity,()->{
           
                           this.setCourseId(refEntity.getCourseId());
                       
           });
           }
       
    }
       
    /**
     * 
     */
    public demo.orm.entity.Classroom getClassroom(){
       return (demo.orm.entity.Classroom)internalGetRefEntity(PROP_NAME_classroom);
    }

    public void setClassroom(demo.orm.entity.Classroom refEntity){
   
           if(refEntity == null){
           
                   this.setBuilding(null);
               
                   this.setRoomNumber(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_classroom, refEntity,()->{
           
                           this.setBuilding(refEntity.getBuilding());
                       
                           this.setRoomNumber(refEntity.getRoomNumber());
                       
           });
           }
       
    }
       
    private final OrmEntitySet<demo.orm.entity.Teaching> _teachings = new OrmEntitySet<>(this, PROP_NAME_teachings,
        demo.orm.entity.Teaching.PROP_NAME_section, null,demo.orm.entity.Teaching.class);

    /**
     * 。 refPropName: section, keyProp: {rel.keyProp}
     */
    public IOrmEntitySet<demo.orm.entity.Teaching> getTeachings(){
       return _teachings;
    }
       
    private final OrmEntitySet<demo.orm.entity.Taking> _takings = new OrmEntitySet<>(this, PROP_NAME_takings,
        demo.orm.entity.Taking.PROP_NAME_section, null,demo.orm.entity.Taking.class);

    /**
     * 。 refPropName: section, keyProp: {rel.keyProp}
     */
    public IOrmEntitySet<demo.orm.entity.Taking> getTakings(){
       return _takings;
    }
       
}
// resume CPD analysis - CPD-ON
