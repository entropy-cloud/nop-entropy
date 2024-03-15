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

import demo.orm.entity.Taking;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  : taking
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _Taking extends DynamicOrmEntity{
    
    /* : student_id VARCHAR */
    public static final String PROP_NAME_studentId = "studentId";
    public static final int PROP_ID_studentId = 1;
    
    /* : course_id VARCHAR */
    public static final String PROP_NAME_courseId = "courseId";
    public static final int PROP_ID_courseId = 2;
    
    /* : sec_id VARCHAR */
    public static final String PROP_NAME_secId = "secId";
    public static final int PROP_ID_secId = 3;
    
    /* : semester VARCHAR */
    public static final String PROP_NAME_semester = "semester";
    public static final int PROP_ID_semester = 4;
    
    /* : year DECIMAL */
    public static final String PROP_NAME_year = "year";
    public static final int PROP_ID_year = 5;
    
    /* : grade VARCHAR */
    public static final String PROP_NAME_grade = "grade";
    public static final int PROP_ID_grade = 6;
    

    private static int _PROP_ID_BOUND = 7;

    
    /* relation:  */
    public static final String PROP_NAME_section = "section";
    
    /* relation:  */
    public static final String PROP_NAME_student = "student";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_studentId,PROP_NAME_courseId,PROP_NAME_secId,PROP_NAME_semester,PROP_NAME_year);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_studentId,PROP_ID_courseId,PROP_ID_secId,PROP_ID_semester,PROP_ID_year};

    private static final String[] PROP_ID_TO_NAME = new String[7];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_studentId] = PROP_NAME_studentId;
          PROP_NAME_TO_ID.put(PROP_NAME_studentId, PROP_ID_studentId);
      
          PROP_ID_TO_NAME[PROP_ID_courseId] = PROP_NAME_courseId;
          PROP_NAME_TO_ID.put(PROP_NAME_courseId, PROP_ID_courseId);
      
          PROP_ID_TO_NAME[PROP_ID_secId] = PROP_NAME_secId;
          PROP_NAME_TO_ID.put(PROP_NAME_secId, PROP_ID_secId);
      
          PROP_ID_TO_NAME[PROP_ID_semester] = PROP_NAME_semester;
          PROP_NAME_TO_ID.put(PROP_NAME_semester, PROP_ID_semester);
      
          PROP_ID_TO_NAME[PROP_ID_year] = PROP_NAME_year;
          PROP_NAME_TO_ID.put(PROP_NAME_year, PROP_ID_year);
      
          PROP_ID_TO_NAME[PROP_ID_grade] = PROP_NAME_grade;
          PROP_NAME_TO_ID.put(PROP_NAME_grade, PROP_ID_grade);
      
    }

    
    /* : student_id */
    private java.lang.String _studentId;
    
    /* : course_id */
    private java.lang.String _courseId;
    
    /* : sec_id */
    private java.lang.String _secId;
    
    /* : semester */
    private java.lang.String _semester;
    
    /* : year */
    private java.math.BigDecimal _year;
    
    /* : grade */
    private java.lang.String _grade;
    

    public _Taking(){
        // for debug
    }

    protected Taking newInstance(){
       return new Taking();
    }

    @Override
    public Taking cloneInstance() {
        Taking entity = newInstance();
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
      return "demo.orm.entity.Taking";
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
        
            return propId == PROP_ID_studentId || propId == PROP_ID_courseId || propId == PROP_ID_secId || propId == PROP_ID_semester || propId == PROP_ID_year;
          
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
        
            case PROP_ID_studentId:
               return getStudentId();
        
            case PROP_ID_courseId:
               return getCourseId();
        
            case PROP_ID_secId:
               return getSecId();
        
            case PROP_ID_semester:
               return getSemester();
        
            case PROP_ID_year:
               return getYear();
        
            case PROP_ID_grade:
               return getGrade();
        
           default:
              return super.orm_propValue(propId);
        }
    }

    

    @Override
    public void orm_propValue(int propId, Object value){
        switch(propId){
        
            case PROP_ID_studentId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_studentId));
               }
               setStudentId(typedValue);
               break;
            }
        
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
        
            case PROP_ID_grade:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_grade));
               }
               setGrade(typedValue);
               break;
            }
        
           default:
              super.orm_propValue(propId,value);
        }
    }

    @Override
    public void orm_internalSet(int propId, Object value) {
        switch(propId){
        
            case PROP_ID_studentId:{
               onInitProp(propId);
               this._studentId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
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
        
            case PROP_ID_grade:{
               onInitProp(propId);
               this._grade = (java.lang.String)value;
               
               break;
            }
        
           default:
              super.orm_internalSet(propId,value);
        }
    }

    
    /**
     * : student_id
     */
    public java.lang.String getStudentId(){
         onPropGet(PROP_ID_studentId);
         return _studentId;
    }

    /**
     * : student_id
     */
    public void setStudentId(java.lang.String value){
        if(onPropSet(PROP_ID_studentId,value)){
            this._studentId = value;
            internalClearRefs(PROP_ID_studentId);
            orm_id();
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
     * : grade
     */
    public java.lang.String getGrade(){
         onPropGet(PROP_ID_grade);
         return _grade;
    }

    /**
     * : grade
     */
    public void setGrade(java.lang.String value){
        if(onPropSet(PROP_ID_grade,value)){
            this._grade = value;
            internalClearRefs(PROP_ID_grade);
            
        }
    }
    
    /**
     * 
     */
    public demo.orm.entity.Section getSection(){
       return (demo.orm.entity.Section)internalGetRefEntity(PROP_NAME_section);
    }

    public void setSection(demo.orm.entity.Section refEntity){
   
           if(refEntity == null){
           
                   this.setCourseId(null);
               
                   this.setSecId(null);
               
                   this.setSemester(null);
               
                   this.setYear(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_section, refEntity,()->{
           
                           this.setCourseId(refEntity.getCourseId());
                       
                           this.setSecId(refEntity.getSecId());
                       
                           this.setSemester(refEntity.getSemester());
                       
                           this.setYear(refEntity.getYear());
                       
           });
           }
       
    }
       
    /**
     * 
     */
    public demo.orm.entity.Student getStudent(){
       return (demo.orm.entity.Student)internalGetRefEntity(PROP_NAME_student);
    }

    public void setStudent(demo.orm.entity.Student refEntity){
   
           if(refEntity == null){
           
                   this.setStudentId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_student, refEntity,()->{
           
                           this.setStudentId(refEntity.getId());
                       
           });
           }
       
    }
       
}
// resume CPD analysis - CPD-ON
