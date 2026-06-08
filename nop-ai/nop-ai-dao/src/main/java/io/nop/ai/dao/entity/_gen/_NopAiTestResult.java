package io.nop.ai.dao.entity._gen;

import io.nop.orm.model.IEntityModel;
import io.nop.orm.support.DynamicOrmEntity;
import io.nop.orm.support.OrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.orm.IOrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.api.core.convert.ConvertHelper;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;

import io.nop.ai.dao.entity.NopAiTestResult;

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
    
    /* 数据版本: version INTEGER */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 6;
    
    /* 创建人: created_by VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 7;
    
    /* 创建时间: create_time TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 8;
    
    /* 修改人: updated_by VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 9;
    
    /* 修改时间: update_time TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 10;
    

    private static int _PROP_ID_BOUND = 11;

    
    /* relation:  */
    public static final String PROP_NAME_testCase = "testCase";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_id);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_id};

    private static final String[] PROP_ID_TO_NAME = new String[11];
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
      
          PROP_ID_TO_NAME[PROP_ID_version] = PROP_NAME_version;
          PROP_NAME_TO_ID.put(PROP_NAME_version, PROP_ID_version);
      
          PROP_ID_TO_NAME[PROP_ID_createdBy] = PROP_NAME_createdBy;
          PROP_NAME_TO_ID.put(PROP_NAME_createdBy, PROP_ID_createdBy);
      
          PROP_ID_TO_NAME[PROP_ID_createTime] = PROP_NAME_createTime;
          PROP_NAME_TO_ID.put(PROP_NAME_createTime, PROP_ID_createTime);
      
          PROP_ID_TO_NAME[PROP_ID_updatedBy] = PROP_NAME_updatedBy;
          PROP_NAME_TO_ID.put(PROP_NAME_updatedBy, PROP_ID_updatedBy);
      
          PROP_ID_TO_NAME[PROP_ID_updateTime] = PROP_NAME_updateTime;
          PROP_NAME_TO_ID.put(PROP_NAME_updateTime, PROP_ID_updateTime);
      
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
    
    /* 数据版本: version */
    private java.lang.Integer _version;
    
    /* 创建人: created_by */
    private java.lang.String _createdBy;
    
    /* 创建时间: create_time */
    private java.sql.Timestamp _createTime;
    
    /* 修改人: updated_by */
    private java.lang.String _updatedBy;
    
    /* 修改时间: update_time */
    private java.sql.Timestamp _updateTime;
    

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
      return "io.nop.ai.dao.entity.NopAiTestResult";
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
        
            case PROP_ID_version:
               return getVersion();
        
            case PROP_ID_createdBy:
               return getCreatedBy();
        
            case PROP_ID_createTime:
               return getCreateTime();
        
            case PROP_ID_updatedBy:
               return getUpdatedBy();
        
            case PROP_ID_updateTime:
               return getUpdateTime();
        
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
        
            case PROP_ID_version:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_version));
               }
               setVersion(typedValue);
               break;
            }
        
            case PROP_ID_createdBy:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_createdBy));
               }
               setCreatedBy(typedValue);
               break;
            }
        
            case PROP_ID_createTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_createTime));
               }
               setCreateTime(typedValue);
               break;
            }
        
            case PROP_ID_updatedBy:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_updatedBy));
               }
               setUpdatedBy(typedValue);
               break;
            }
        
            case PROP_ID_updateTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_updateTime));
               }
               setUpdateTime(typedValue);
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
        
            case PROP_ID_version:{
               onInitProp(propId);
               this._version = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_createdBy:{
               onInitProp(propId);
               this._createdBy = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_createTime:{
               onInitProp(propId);
               this._createTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_updatedBy:{
               onInitProp(propId);
               this._updatedBy = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_updateTime:{
               onInitProp(propId);
               this._updateTime = (java.sql.Timestamp)value;
               
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
     * 数据版本: version
     */
    public final java.lang.Integer getVersion(){
         onPropGet(PROP_ID_version);
         return _version;
    }

    /**
     * 数据版本: version
     */
    public final void setVersion(java.lang.Integer value){
        if(onPropSet(PROP_ID_version,value)){
            this._version = value;
            internalClearRefs(PROP_ID_version);
            
        }
    }
    
    /**
     * 创建人: created_by
     */
    public final java.lang.String getCreatedBy(){
         onPropGet(PROP_ID_createdBy);
         return _createdBy;
    }

    /**
     * 创建人: created_by
     */
    public final void setCreatedBy(java.lang.String value){
        if(onPropSet(PROP_ID_createdBy,value)){
            this._createdBy = value;
            internalClearRefs(PROP_ID_createdBy);
            
        }
    }
    
    /**
     * 创建时间: create_time
     */
    public final java.sql.Timestamp getCreateTime(){
         onPropGet(PROP_ID_createTime);
         return _createTime;
    }

    /**
     * 创建时间: create_time
     */
    public final void setCreateTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_createTime,value)){
            this._createTime = value;
            internalClearRefs(PROP_ID_createTime);
            
        }
    }
    
    /**
     * 修改人: updated_by
     */
    public final java.lang.String getUpdatedBy(){
         onPropGet(PROP_ID_updatedBy);
         return _updatedBy;
    }

    /**
     * 修改人: updated_by
     */
    public final void setUpdatedBy(java.lang.String value){
        if(onPropSet(PROP_ID_updatedBy,value)){
            this._updatedBy = value;
            internalClearRefs(PROP_ID_updatedBy);
            
        }
    }
    
    /**
     * 修改时间: update_time
     */
    public final java.sql.Timestamp getUpdateTime(){
         onPropGet(PROP_ID_updateTime);
         return _updateTime;
    }

    /**
     * 修改时间: update_time
     */
    public final void setUpdateTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_updateTime,value)){
            this._updateTime = value;
            internalClearRefs(PROP_ID_updateTime);
            
        }
    }
    
    /**
     * 
     */
    public final io.nop.ai.dao.entity.NopAiTestCase getTestCase(){
       return (io.nop.ai.dao.entity.NopAiTestCase)internalGetRefEntity(PROP_NAME_testCase);
    }

    public final void setTestCase(io.nop.ai.dao.entity.NopAiTestCase refEntity){
   
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
