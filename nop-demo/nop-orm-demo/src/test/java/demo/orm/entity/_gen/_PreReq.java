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

import demo.orm.entity.PreReq;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  : pre_req
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _PreReq extends DynamicOrmEntity{
    
    /* : course_id VARCHAR */
    public static final String PROP_NAME_courseId = "courseId";
    public static final int PROP_ID_courseId = 1;
    
    /* : pre_req_id VARCHAR */
    public static final String PROP_NAME_preReqId = "preReqId";
    public static final int PROP_ID_preReqId = 2;
    

    private static int _PROP_ID_BOUND = 3;

    
    /* relation:  */
    public static final String PROP_NAME_course = "course";
    
    /* relation:  */
    public static final String PROP_NAME_preReq = "preReq";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_courseId,PROP_NAME_preReqId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_courseId,PROP_ID_preReqId};

    private static final String[] PROP_ID_TO_NAME = new String[3];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_courseId] = PROP_NAME_courseId;
          PROP_NAME_TO_ID.put(PROP_NAME_courseId, PROP_ID_courseId);
      
          PROP_ID_TO_NAME[PROP_ID_preReqId] = PROP_NAME_preReqId;
          PROP_NAME_TO_ID.put(PROP_NAME_preReqId, PROP_ID_preReqId);
      
    }

    
    /* : course_id */
    private java.lang.String _courseId;
    
    /* : pre_req_id */
    private java.lang.String _preReqId;
    

    public _PreReq(){
        // for debug
    }

    protected PreReq newInstance(){
       return new PreReq();
    }

    @Override
    public PreReq cloneInstance() {
        PreReq entity = newInstance();
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
      return "demo.orm.entity.PreReq";
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
        
            return propId == PROP_ID_courseId || propId == PROP_ID_preReqId;
          
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
        
            case PROP_ID_preReqId:
               return getPreReqId();
        
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
        
            case PROP_ID_preReqId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_preReqId));
               }
               setPreReqId(typedValue);
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
        
            case PROP_ID_preReqId:{
               onInitProp(propId);
               this._preReqId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
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
     * : pre_req_id
     */
    public java.lang.String getPreReqId(){
         onPropGet(PROP_ID_preReqId);
         return _preReqId;
    }

    /**
     * : pre_req_id
     */
    public void setPreReqId(java.lang.String value){
        if(onPropSet(PROP_ID_preReqId,value)){
            this._preReqId = value;
            internalClearRefs(PROP_ID_preReqId);
            orm_id();
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
    public demo.orm.entity.Course getPreReq(){
       return (demo.orm.entity.Course)internalGetRefEntity(PROP_NAME_preReq);
    }

    public void setPreReq(demo.orm.entity.Course refEntity){
   
           if(refEntity == null){
           
                   this.setPreReqId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_preReq, refEntity,()->{
           
                           this.setPreReqId(refEntity.getCourseId());
                       
           });
           }
       
    }
       
}
// resume CPD analysis - CPD-ON
