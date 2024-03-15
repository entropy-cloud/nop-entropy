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

import demo.orm.entity.Course;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  : course
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _Course extends DynamicOrmEntity{
    
    /* : course_id VARCHAR */
    public static final String PROP_NAME_courseId = "courseId";
    public static final int PROP_ID_courseId = 1;
    
    /* : title VARCHAR */
    public static final String PROP_NAME_title = "title";
    public static final int PROP_ID_title = 2;
    
    /* : dept_name VARCHAR */
    public static final String PROP_NAME_deptName = "deptName";
    public static final int PROP_ID_deptName = 3;
    
    /* : credits DECIMAL */
    public static final String PROP_NAME_credits = "credits";
    public static final int PROP_ID_credits = 4;
    

    private static int _PROP_ID_BOUND = 5;

    
    /* relation:  */
    public static final String PROP_NAME_department = "department";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_courseId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_courseId};

    private static final String[] PROP_ID_TO_NAME = new String[5];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_courseId] = PROP_NAME_courseId;
          PROP_NAME_TO_ID.put(PROP_NAME_courseId, PROP_ID_courseId);
      
          PROP_ID_TO_NAME[PROP_ID_title] = PROP_NAME_title;
          PROP_NAME_TO_ID.put(PROP_NAME_title, PROP_ID_title);
      
          PROP_ID_TO_NAME[PROP_ID_deptName] = PROP_NAME_deptName;
          PROP_NAME_TO_ID.put(PROP_NAME_deptName, PROP_ID_deptName);
      
          PROP_ID_TO_NAME[PROP_ID_credits] = PROP_NAME_credits;
          PROP_NAME_TO_ID.put(PROP_NAME_credits, PROP_ID_credits);
      
    }

    
    /* : course_id */
    private java.lang.String _courseId;
    
    /* : title */
    private java.lang.String _title;
    
    /* : dept_name */
    private java.lang.String _deptName;
    
    /* : credits */
    private java.math.BigDecimal _credits;
    

    public _Course(){
        // for debug
    }

    protected Course newInstance(){
       return new Course();
    }

    @Override
    public Course cloneInstance() {
        Course entity = newInstance();
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
      return "demo.orm.entity.Course";
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
    
        return buildSimpleId(PROP_ID_courseId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_courseId;
          
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
        
            case PROP_ID_title:
               return getTitle();
        
            case PROP_ID_deptName:
               return getDeptName();
        
            case PROP_ID_credits:
               return getCredits();
        
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
        
            case PROP_ID_title:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_title));
               }
               setTitle(typedValue);
               break;
            }
        
            case PROP_ID_deptName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_deptName));
               }
               setDeptName(typedValue);
               break;
            }
        
            case PROP_ID_credits:{
               java.math.BigDecimal typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toBigDecimal(value,
                       err-> newTypeConversionError(PROP_NAME_credits));
               }
               setCredits(typedValue);
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
        
            case PROP_ID_title:{
               onInitProp(propId);
               this._title = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_deptName:{
               onInitProp(propId);
               this._deptName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_credits:{
               onInitProp(propId);
               this._credits = (java.math.BigDecimal)value;
               
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
     * : title
     */
    public java.lang.String getTitle(){
         onPropGet(PROP_ID_title);
         return _title;
    }

    /**
     * : title
     */
    public void setTitle(java.lang.String value){
        if(onPropSet(PROP_ID_title,value)){
            this._title = value;
            internalClearRefs(PROP_ID_title);
            
        }
    }
    
    /**
     * : dept_name
     */
    public java.lang.String getDeptName(){
         onPropGet(PROP_ID_deptName);
         return _deptName;
    }

    /**
     * : dept_name
     */
    public void setDeptName(java.lang.String value){
        if(onPropSet(PROP_ID_deptName,value)){
            this._deptName = value;
            internalClearRefs(PROP_ID_deptName);
            
        }
    }
    
    /**
     * : credits
     */
    public java.math.BigDecimal getCredits(){
         onPropGet(PROP_ID_credits);
         return _credits;
    }

    /**
     * : credits
     */
    public void setCredits(java.math.BigDecimal value){
        if(onPropSet(PROP_ID_credits,value)){
            this._credits = value;
            internalClearRefs(PROP_ID_credits);
            
        }
    }
    
    /**
     * 
     */
    public demo.orm.entity.Department getDepartment(){
       return (demo.orm.entity.Department)internalGetRefEntity(PROP_NAME_department);
    }

    public void setDepartment(demo.orm.entity.Department refEntity){
   
           if(refEntity == null){
           
                   this.setDeptName(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_department, refEntity,()->{
           
                           this.setDeptName(refEntity.getDeptName());
                       
           });
           }
       
    }
       
}
// resume CPD analysis - CPD-ON
