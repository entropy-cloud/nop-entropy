package test.entity._gen;

import io.nop.orm.model.IEntityModel;
import io.nop.orm.support.DynamicOrmEntity;
import io.nop.orm.support.OrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.orm.IOrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.api.core.convert.ConvertHelper;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;

import test.entity.TestOrmData;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  : TEST_ORM_DATA
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public class _TestOrmData extends DynamicOrmEntity{
    
    /* : sample_method_id VARCHAR */
    public static final String PROP_NAME_sampleMethodId = "sampleMethodId";
    public static final int PROP_ID_sampleMethodId = 3;
    
    /* : method_param_id VARCHAR */
    public static final String PROP_NAME_methodParamId = "methodParamId";
    public static final int PROP_ID_methodParamId = 4;
    
    /* : num_value DECIMAL */
    public static final String PROP_NAME_numValue = "numValue";
    public static final int PROP_ID_numValue = 6;
    
    /* : SID INTEGER */
    public static final String PROP_NAME_sid = "sid";
    public static final int PROP_ID_sid = 10;
    
    /* : str_value VARCHAR */
    public static final String PROP_NAME_strValue = "strValue";
    public static final int PROP_ID_strValue = 100;
    

    private static int _PROP_ID_BOUND = 101;

    
    /* relation:  */
    public static final String PROP_NAME_sampleMethod = "sampleMethod";
    
    /* relation:  */
    public static final String PROP_NAME_methodParam = "methodParam";
    

    public static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_sid);
    public static final int[] PK_PROP_IDS = new int[]{PROP_ID_sid};

    private static final String[] PROP_ID_TO_NAME = new String[101];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_sampleMethodId] = PROP_NAME_sampleMethodId;
          PROP_NAME_TO_ID.put(PROP_NAME_sampleMethodId, PROP_ID_sampleMethodId);
      
          PROP_ID_TO_NAME[PROP_ID_methodParamId] = PROP_NAME_methodParamId;
          PROP_NAME_TO_ID.put(PROP_NAME_methodParamId, PROP_ID_methodParamId);
      
          PROP_ID_TO_NAME[PROP_ID_numValue] = PROP_NAME_numValue;
          PROP_NAME_TO_ID.put(PROP_NAME_numValue, PROP_ID_numValue);
      
          PROP_ID_TO_NAME[PROP_ID_sid] = PROP_NAME_sid;
          PROP_NAME_TO_ID.put(PROP_NAME_sid, PROP_ID_sid);
      
          PROP_ID_TO_NAME[PROP_ID_strValue] = PROP_NAME_strValue;
          PROP_NAME_TO_ID.put(PROP_NAME_strValue, PROP_ID_strValue);
      
    }

    
    /* : sample_method_id */
    private java.lang.String _sampleMethodId;
    
    /* : method_param_id */
    private java.lang.String _methodParamId;
    
    /* : num_value */
    private java.math.BigDecimal _numValue;
    
    /* : SID */
    private java.lang.Integer _sid;
    
    /* : str_value */
    private java.lang.String _strValue;
    

    public _TestOrmData(){
    }

    protected TestOrmData newInstance(){
       return new TestOrmData();
    }

    @Override
    public TestOrmData cloneInstance() {
        TestOrmData entity = newInstance();
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
      return "test.entity.TestOrmData";
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
    
        return buildSimpleId(PROP_ID_sid);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_sid;
          
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
        
            case PROP_ID_sampleMethodId:
               return getSampleMethodId();
        
            case PROP_ID_methodParamId:
               return getMethodParamId();
        
            case PROP_ID_numValue:
               return getNumValue();
        
            case PROP_ID_sid:
               return getSid();
        
            case PROP_ID_strValue:
               return getStrValue();
        
           default:
              return super.orm_propValue(propId);
        }
    }

    

    @Override
    public void orm_propValue(int propId, Object value){
        switch(propId){
        
            case PROP_ID_sampleMethodId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_sampleMethodId));
               }
               setSampleMethodId(typedValue);
               break;
            }
        
            case PROP_ID_methodParamId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_methodParamId));
               }
               setMethodParamId(typedValue);
               break;
            }
        
            case PROP_ID_numValue:{
               java.math.BigDecimal typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toBigDecimal(value,
                       err-> newTypeConversionError(PROP_NAME_numValue));
               }
               setNumValue(typedValue);
               break;
            }
        
            case PROP_ID_sid:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_sid));
               }
               setSid(typedValue);
               break;
            }
        
            case PROP_ID_strValue:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_strValue));
               }
               setStrValue(typedValue);
               break;
            }
        
           default:
              super.orm_propValue(propId,value);
        }
    }

    @Override
    public void orm_internalSet(int propId, Object value) {
        switch(propId){
        
            case PROP_ID_sampleMethodId:{
               onInitProp(propId);
               this._sampleMethodId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_methodParamId:{
               onInitProp(propId);
               this._methodParamId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_numValue:{
               onInitProp(propId);
               this._numValue = (java.math.BigDecimal)value;
               
               break;
            }
        
            case PROP_ID_sid:{
               onInitProp(propId);
               this._sid = (java.lang.Integer)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_strValue:{
               onInitProp(propId);
               this._strValue = (java.lang.String)value;
               
               break;
            }
        
           default:
              super.orm_internalSet(propId,value);
        }
    }

    
    /**
     * : sample_method_id
     */
    public java.lang.String getSampleMethodId(){
         onPropGet(PROP_ID_sampleMethodId);
         return _sampleMethodId;
    }

    /**
     * : sample_method_id
     */
    public void setSampleMethodId(java.lang.String value){
        if(onPropSet(PROP_ID_sampleMethodId,value)){
            this._sampleMethodId = value;
            internalClearRefs(PROP_ID_sampleMethodId);
            
        }
    }
    
    /**
     * : method_param_id
     */
    public java.lang.String getMethodParamId(){
         onPropGet(PROP_ID_methodParamId);
         return _methodParamId;
    }

    /**
     * : method_param_id
     */
    public void setMethodParamId(java.lang.String value){
        if(onPropSet(PROP_ID_methodParamId,value)){
            this._methodParamId = value;
            internalClearRefs(PROP_ID_methodParamId);
            
        }
    }
    
    /**
     * : num_value
     */
    public java.math.BigDecimal getNumValue(){
         onPropGet(PROP_ID_numValue);
         return _numValue;
    }

    /**
     * : num_value
     */
    public void setNumValue(java.math.BigDecimal value){
        if(onPropSet(PROP_ID_numValue,value)){
            this._numValue = value;
            internalClearRefs(PROP_ID_numValue);
            
        }
    }
    
    /**
     * : SID
     */
    public java.lang.Integer getSid(){
         onPropGet(PROP_ID_sid);
         return _sid;
    }

    /**
     * : SID
     */
    public void setSid(java.lang.Integer value){
        if(onPropSet(PROP_ID_sid,value)){
            this._sid = value;
            internalClearRefs(PROP_ID_sid);
            orm_id();
        }
    }
    
    /**
     * : str_value
     */
    public java.lang.String getStrValue(){
         onPropGet(PROP_ID_strValue);
         return _strValue;
    }

    /**
     * : str_value
     */
    public void setStrValue(java.lang.String value){
        if(onPropSet(PROP_ID_strValue,value)){
            this._strValue = value;
            internalClearRefs(PROP_ID_strValue);
            
        }
    }
    
    /**
     * 
     */
    public test.entity.TestOrmSampleMethod getSampleMethod(){
       return (test.entity.TestOrmSampleMethod)internalGetRefEntity(PROP_NAME_sampleMethod);
    }

    public void setSampleMethod(test.entity.TestOrmSampleMethod refEntity){
       if(refEntity == null){
         
         this.setSampleMethodId(null);
         
       }else{
          internalSetRefEntity(PROP_NAME_sampleMethod, refEntity,()->{
             
              this.orm_propValue(PROP_ID_sampleMethodId,
                refEntity.getSid());
                
          });
       }
    }
       
    /**
     * 
     */
    public test.entity.TestOrmMethodParam getMethodParam(){
       return (test.entity.TestOrmMethodParam)internalGetRefEntity(PROP_NAME_methodParam);
    }

    public void setMethodParam(test.entity.TestOrmMethodParam refEntity){
       if(refEntity == null){
         
         this.setMethodParamId(null);
         
       }else{
          internalSetRefEntity(PROP_NAME_methodParam, refEntity,()->{
             
              this.orm_propValue(PROP_ID_methodParamId,
                refEntity.getSid());
                
          });
       }
    }
       
}
// resume CPD analysis - CPD-ON
