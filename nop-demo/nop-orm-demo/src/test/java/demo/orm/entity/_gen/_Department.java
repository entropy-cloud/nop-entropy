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

import demo.orm.entity.Department;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  : department
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _Department extends DynamicOrmEntity{
    
    /* : dept_name VARCHAR */
    public static final String PROP_NAME_deptName = "deptName";
    public static final int PROP_ID_deptName = 1;
    
    /* : building VARCHAR */
    public static final String PROP_NAME_building = "building";
    public static final int PROP_ID_building = 2;
    
    /* : budget DECIMAL */
    public static final String PROP_NAME_budget = "budget";
    public static final int PROP_ID_budget = 3;
    

    private static int _PROP_ID_BOUND = 4;

    
    /* relation:  */
    public static final String PROP_NAME_courses = "courses";
    
    /* relation:  */
    public static final String PROP_NAME_instructors = "instructors";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_deptName);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_deptName};

    private static final String[] PROP_ID_TO_NAME = new String[4];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_deptName] = PROP_NAME_deptName;
          PROP_NAME_TO_ID.put(PROP_NAME_deptName, PROP_ID_deptName);
      
          PROP_ID_TO_NAME[PROP_ID_building] = PROP_NAME_building;
          PROP_NAME_TO_ID.put(PROP_NAME_building, PROP_ID_building);
      
          PROP_ID_TO_NAME[PROP_ID_budget] = PROP_NAME_budget;
          PROP_NAME_TO_ID.put(PROP_NAME_budget, PROP_ID_budget);
      
    }

    
    /* : dept_name */
    private java.lang.String _deptName;
    
    /* : building */
    private java.lang.String _building;
    
    /* : budget */
    private java.math.BigDecimal _budget;
    

    public _Department(){
        // for debug
    }

    protected Department newInstance(){
       return new Department();
    }

    @Override
    public Department cloneInstance() {
        Department entity = newInstance();
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
      return "demo.orm.entity.Department";
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
    
        return buildSimpleId(PROP_ID_deptName);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_deptName;
          
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
        
            case PROP_ID_deptName:
               return getDeptName();
        
            case PROP_ID_building:
               return getBuilding();
        
            case PROP_ID_budget:
               return getBudget();
        
           default:
              return super.orm_propValue(propId);
        }
    }

    

    @Override
    public void orm_propValue(int propId, Object value){
        switch(propId){
        
            case PROP_ID_deptName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_deptName));
               }
               setDeptName(typedValue);
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
        
            case PROP_ID_budget:{
               java.math.BigDecimal typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toBigDecimal(value,
                       err-> newTypeConversionError(PROP_NAME_budget));
               }
               setBudget(typedValue);
               break;
            }
        
           default:
              super.orm_propValue(propId,value);
        }
    }

    @Override
    public void orm_internalSet(int propId, Object value) {
        switch(propId){
        
            case PROP_ID_deptName:{
               onInitProp(propId);
               this._deptName = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_building:{
               onInitProp(propId);
               this._building = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_budget:{
               onInitProp(propId);
               this._budget = (java.math.BigDecimal)value;
               
               break;
            }
        
           default:
              super.orm_internalSet(propId,value);
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
     * : budget
     */
    public java.math.BigDecimal getBudget(){
         onPropGet(PROP_ID_budget);
         return _budget;
    }

    /**
     * : budget
     */
    public void setBudget(java.math.BigDecimal value){
        if(onPropSet(PROP_ID_budget,value)){
            this._budget = value;
            internalClearRefs(PROP_ID_budget);
            
        }
    }
    
    private final OrmEntitySet<demo.orm.entity.Course> _courses = new OrmEntitySet<>(this, PROP_NAME_courses,
        demo.orm.entity.Course.PROP_NAME_department, null,demo.orm.entity.Course.class);

    /**
     * 。 refPropName: department, keyProp: {rel.keyProp}
     */
    public IOrmEntitySet<demo.orm.entity.Course> getCourses(){
       return _courses;
    }
       
    private final OrmEntitySet<demo.orm.entity.Instructor> _instructors = new OrmEntitySet<>(this, PROP_NAME_instructors,
        demo.orm.entity.Instructor.PROP_NAME_department, null,demo.orm.entity.Instructor.class);

    /**
     * 。 refPropName: department, keyProp: {rel.keyProp}
     */
    public IOrmEntitySet<demo.orm.entity.Instructor> getInstructors(){
       return _instructors;
    }
       
}
// resume CPD analysis - CPD-ON
