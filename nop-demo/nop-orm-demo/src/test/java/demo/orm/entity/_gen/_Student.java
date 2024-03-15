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

import demo.orm.entity.Student;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  : student
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _Student extends DynamicOrmEntity{
    
    /* : id VARCHAR */
    public static final String PROP_NAME_id = "id";
    public static final int PROP_ID_id = 1;
    
    /* : name VARCHAR */
    public static final String PROP_NAME_name = "name";
    public static final int PROP_ID_name = 2;
    
    /* : dept_name VARCHAR */
    public static final String PROP_NAME_deptName = "deptName";
    public static final int PROP_ID_deptName = 3;
    
    /* : tot_cred DECIMAL */
    public static final String PROP_NAME_totCred = "totCred";
    public static final int PROP_ID_totCred = 4;
    

    private static int _PROP_ID_BOUND = 5;

    
    /* relation:  */
    public static final String PROP_NAME_department = "department";
    
    /* relation:  */
    public static final String PROP_NAME_takings = "takings";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_id);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_id};

    private static final String[] PROP_ID_TO_NAME = new String[5];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_id] = PROP_NAME_id;
          PROP_NAME_TO_ID.put(PROP_NAME_id, PROP_ID_id);
      
          PROP_ID_TO_NAME[PROP_ID_name] = PROP_NAME_name;
          PROP_NAME_TO_ID.put(PROP_NAME_name, PROP_ID_name);
      
          PROP_ID_TO_NAME[PROP_ID_deptName] = PROP_NAME_deptName;
          PROP_NAME_TO_ID.put(PROP_NAME_deptName, PROP_ID_deptName);
      
          PROP_ID_TO_NAME[PROP_ID_totCred] = PROP_NAME_totCred;
          PROP_NAME_TO_ID.put(PROP_NAME_totCred, PROP_ID_totCred);
      
    }

    
    /* : id */
    private java.lang.String _id;
    
    /* : name */
    private java.lang.String _name;
    
    /* : dept_name */
    private java.lang.String _deptName;
    
    /* : tot_cred */
    private java.math.BigDecimal _totCred;
    

    public _Student(){
        // for debug
    }

    protected Student newInstance(){
       return new Student();
    }

    @Override
    public Student cloneInstance() {
        Student entity = newInstance();
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
      return "demo.orm.entity.Student";
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
        
            case PROP_ID_name:
               return getName();
        
            case PROP_ID_deptName:
               return getDeptName();
        
            case PROP_ID_totCred:
               return getTotCred();
        
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
        
            case PROP_ID_name:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_name));
               }
               setName(typedValue);
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
        
            case PROP_ID_totCred:{
               java.math.BigDecimal typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toBigDecimal(value,
                       err-> newTypeConversionError(PROP_NAME_totCred));
               }
               setTotCred(typedValue);
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
        
            case PROP_ID_name:{
               onInitProp(propId);
               this._name = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_deptName:{
               onInitProp(propId);
               this._deptName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_totCred:{
               onInitProp(propId);
               this._totCred = (java.math.BigDecimal)value;
               
               break;
            }
        
           default:
              super.orm_internalSet(propId,value);
        }
    }

    
    /**
     * : id
     */
    public java.lang.String getId(){
         onPropGet(PROP_ID_id);
         return _id;
    }

    /**
     * : id
     */
    public void setId(java.lang.String value){
        if(onPropSet(PROP_ID_id,value)){
            this._id = value;
            internalClearRefs(PROP_ID_id);
            orm_id();
        }
    }
    
    /**
     * : name
     */
    public java.lang.String getName(){
         onPropGet(PROP_ID_name);
         return _name;
    }

    /**
     * : name
     */
    public void setName(java.lang.String value){
        if(onPropSet(PROP_ID_name,value)){
            this._name = value;
            internalClearRefs(PROP_ID_name);
            
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
     * : tot_cred
     */
    public java.math.BigDecimal getTotCred(){
         onPropGet(PROP_ID_totCred);
         return _totCred;
    }

    /**
     * : tot_cred
     */
    public void setTotCred(java.math.BigDecimal value){
        if(onPropSet(PROP_ID_totCred,value)){
            this._totCred = value;
            internalClearRefs(PROP_ID_totCred);
            
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
       
    private final OrmEntitySet<demo.orm.entity.Taking> _takings = new OrmEntitySet<>(this, PROP_NAME_takings,
        demo.orm.entity.Taking.PROP_NAME_student, null,demo.orm.entity.Taking.class);

    /**
     * 。 refPropName: student, keyProp: {rel.keyProp}
     */
    public IOrmEntitySet<demo.orm.entity.Taking> getTakings(){
       return _takings;
    }
       
}
// resume CPD analysis - CPD-ON
