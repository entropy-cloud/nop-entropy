package nop.ai.dao.entity._gen;

import io.nop.orm.model.IEntityModel;
import io.nop.orm.support.DynamicOrmEntity;
import io.nop.orm.support.OrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.orm.IOrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.api.core.convert.ConvertHelper;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;

import nop.ai.dao.entity.NopAiTestResult;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  测试结果: nop_ai_test_result
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopAiTestResult extends DynamicOrmEntity{
    
    /* 主键: id VARCHAR */
    public static final String PROP_NAME_id = "id";
    public static final int PROP_ID_id = 1;
    
    /* 测试用例ID: test_case_id VARCHAR */
    public static final String PROP_NAME_testCaseId = "testCaseId";
    public static final int PROP_ID_testCaseId = 2;
    
    /* 执行时间: execution_time TIMESTAMP */
    public static final String PROP_NAME_executionTime = "executionTime";
    public static final int PROP_ID_executionTime = 3;
    
    /* 是否成功: success BOOLEAN */
    public static final String PROP_NAME_success = "success";
    public static final int PROP_ID_success = 4;
    
    /* 错误日志: error_log VARCHAR */
    public static final String PROP_NAME_errorLog = "errorLog";
    public static final int PROP_ID_errorLog = 5;
    

    private static int _PROP_ID_BOUND = 6;

    
    /* relation:  */
    public static final String PROP_NAME_testCase = "testCase";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_id);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_id};

    private static final String[] PROP_ID_TO_NAME = new String[6];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_id] = PROP_NAME_id;
          PROP_NAME_TO_ID.put(PROP_NAME_id, PROP_ID_id);
      
          PROP_ID_TO_NAME[PROP_ID_testCaseId] = PROP_NAME_testCaseId;
          PROP_NAME_TO_ID.put(PROP_NAME_testCaseId, PROP_ID_testCaseId);
      
          PROP_ID_TO_NAME[PROP_ID_executionTime] = PROP_NAME_executionTime;
          PROP_NAME_TO_ID.put(PROP_NAME_executionTime, PROP_ID_executionTime);
      
          PROP_ID_TO_NAME[PROP_ID_success] = PROP_NAME_success;
          PROP_NAME_TO_ID.put(PROP_NAME_success, PROP_ID_success);
      
          PROP_ID_TO_NAME[PROP_ID_errorLog] = PROP_NAME_errorLog;
          PROP_NAME_TO_ID.put(PROP_NAME_errorLog, PROP_ID_errorLog);
      
    }

    
    /* 主键: id */
    private java.lang.String _id;
    
    /* 测试用例ID: test_case_id */
    private java.lang.String _testCaseId;
    
    /* 执行时间: execution_time */
    private java.sql.Timestamp _executionTime;
    
    /* 是否成功: success */
    private java.lang.Boolean _success;
    
    /* 错误日志: error_log */
    private java.lang.String _errorLog;
    

    public _NopAiTestResult(){
        // for debug
    }

    protected NopAiTestResult newInstance(){
        NopAiTestResult entity = new NopAiTestResult();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopAiTestResult cloneInstance() {
        NopAiTestResult entity = newInstance();
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
      return "nop.ai.dao.entity.NopAiTestResult";
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
        
            case PROP_ID_testCaseId:
               return getTestCaseId();
        
            case PROP_ID_executionTime:
               return getExecutionTime();
        
            case PROP_ID_success:
               return getSuccess();
        
            case PROP_ID_errorLog:
               return getErrorLog();
        
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
        
            case PROP_ID_testCaseId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_testCaseId));
               }
               setTestCaseId(typedValue);
               break;
            }
        
            case PROP_ID_executionTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_executionTime));
               }
               setExecutionTime(typedValue);
               break;
            }
        
            case PROP_ID_success:{
               java.lang.Boolean typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toBoolean(value,
                       err-> newTypeConversionError(PROP_NAME_success));
               }
               setSuccess(typedValue);
               break;
            }
        
            case PROP_ID_errorLog:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_errorLog));
               }
               setErrorLog(typedValue);
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
        
            case PROP_ID_testCaseId:{
               onInitProp(propId);
               this._testCaseId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_executionTime:{
               onInitProp(propId);
               this._executionTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_success:{
               onInitProp(propId);
               this._success = (java.lang.Boolean)value;
               
               break;
            }
        
            case PROP_ID_errorLog:{
               onInitProp(propId);
               this._errorLog = (java.lang.String)value;
               
               break;
            }
        
           default:
              super.orm_internalSet(propId,value);
        }
    }

    
    /**
     * 主键: id
     */
    public final java.lang.String getId(){
         onPropGet(PROP_ID_id);
         return _id;
    }

    /**
     * 主键: id
     */
    public final void setId(java.lang.String value){
        if(onPropSet(PROP_ID_id,value)){
            this._id = value;
            internalClearRefs(PROP_ID_id);
            orm_id();
        }
    }
    
    /**
     * 测试用例ID: test_case_id
     */
    public final java.lang.String getTestCaseId(){
         onPropGet(PROP_ID_testCaseId);
         return _testCaseId;
    }

    /**
     * 测试用例ID: test_case_id
     */
    public final void setTestCaseId(java.lang.String value){
        if(onPropSet(PROP_ID_testCaseId,value)){
            this._testCaseId = value;
            internalClearRefs(PROP_ID_testCaseId);
            
        }
    }
    
    /**
     * 执行时间: execution_time
     */
    public final java.sql.Timestamp getExecutionTime(){
         onPropGet(PROP_ID_executionTime);
         return _executionTime;
    }

    /**
     * 执行时间: execution_time
     */
    public final void setExecutionTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_executionTime,value)){
            this._executionTime = value;
            internalClearRefs(PROP_ID_executionTime);
            
        }
    }
    
    /**
     * 是否成功: success
     */
    public final java.lang.Boolean getSuccess(){
         onPropGet(PROP_ID_success);
         return _success;
    }

    /**
     * 是否成功: success
     */
    public final void setSuccess(java.lang.Boolean value){
        if(onPropSet(PROP_ID_success,value)){
            this._success = value;
            internalClearRefs(PROP_ID_success);
            
        }
    }
    
    /**
     * 错误日志: error_log
     */
    public final java.lang.String getErrorLog(){
         onPropGet(PROP_ID_errorLog);
         return _errorLog;
    }

    /**
     * 错误日志: error_log
     */
    public final void setErrorLog(java.lang.String value){
        if(onPropSet(PROP_ID_errorLog,value)){
            this._errorLog = value;
            internalClearRefs(PROP_ID_errorLog);
            
        }
    }
    
    /**
     * 
     */
    public final nop.ai.dao.entity.NopAiTestCase getTestCase(){
       return (nop.ai.dao.entity.NopAiTestCase)internalGetRefEntity(PROP_NAME_testCase);
    }

    public final void setTestCase(nop.ai.dao.entity.NopAiTestCase refEntity){
   
           if(refEntity == null){
           
                   this.setTestCaseId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_testCase, refEntity,()->{
           
                           this.setTestCaseId(refEntity.getId());
                       
           });
           }
       
    }
       
}
// resume CPD analysis - CPD-ON
