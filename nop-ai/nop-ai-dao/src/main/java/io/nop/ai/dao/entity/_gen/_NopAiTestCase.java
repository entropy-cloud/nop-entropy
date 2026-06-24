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

import io.nop.ai.dao.entity.NopAiTestCase;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  测试用例: nop_ai_test_case
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopAiTestCase extends DynamicOrmEntity{
    
    /* 主键: id VARCHAR */
    public static final String PROP_NAME_id = "id";
    public static final int PROP_ID_id = 1;
    
    /* 需求ID: requirement_id VARCHAR */
    public static final String PROP_NAME_requirementId = "requirementId";
    public static final int PROP_ID_requirementId = 2;
    
    /* 测试内容: test_content VARCHAR */
    public static final String PROP_NAME_testContent = "testContent";
    public static final int PROP_ID_testContent = 3;
    
    /* 测试数据: test_data VARCHAR */
    public static final String PROP_NAME_testData = "testData";
    public static final int PROP_ID_testData = 4;
    
    /* 关联文件ID: gen_file_id VARCHAR */
    public static final String PROP_NAME_genFileId = "genFileId";
    public static final int PROP_ID_genFileId = 5;
    
    /* 响应ID: chat_response_id VARCHAR */
    public static final String PROP_NAME_chatResponseId = "chatResponseId";
    public static final int PROP_ID_chatResponseId = 6;
    
    /* 状态: status VARCHAR */
    public static final String PROP_NAME_status = "status";
    public static final int PROP_ID_status = 7;
    
    /* 数据版本: version INTEGER */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 8;
    
    /* 创建人: created_by VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 9;
    
    /* 创建时间: create_time TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 10;
    
    /* 修改人: updated_by VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 11;
    
    /* 修改时间: update_time TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 12;
    

    private static int _PROP_ID_BOUND = 13;

    
    /* relation:  */
    public static final String PROP_NAME_requirement = "requirement";
    
    /* relation:  */
    public static final String PROP_NAME_genFile = "genFile";
    
    /* relation:  */
    public static final String PROP_NAME_chatResponse = "chatResponse";
    
    /* relation: 执行结果 */
    public static final String PROP_NAME_testResults = "testResults";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_id);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_id};

    private static final String[] PROP_ID_TO_NAME = new String[13];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_id] = PROP_NAME_id;
          PROP_NAME_TO_ID.put(PROP_NAME_id, PROP_ID_id);
      
          PROP_ID_TO_NAME[PROP_ID_requirementId] = PROP_NAME_requirementId;
          PROP_NAME_TO_ID.put(PROP_NAME_requirementId, PROP_ID_requirementId);
      
          PROP_ID_TO_NAME[PROP_ID_testContent] = PROP_NAME_testContent;
          PROP_NAME_TO_ID.put(PROP_NAME_testContent, PROP_ID_testContent);
      
          PROP_ID_TO_NAME[PROP_ID_testData] = PROP_NAME_testData;
          PROP_NAME_TO_ID.put(PROP_NAME_testData, PROP_ID_testData);
      
          PROP_ID_TO_NAME[PROP_ID_genFileId] = PROP_NAME_genFileId;
          PROP_NAME_TO_ID.put(PROP_NAME_genFileId, PROP_ID_genFileId);
      
          PROP_ID_TO_NAME[PROP_ID_chatResponseId] = PROP_NAME_chatResponseId;
          PROP_NAME_TO_ID.put(PROP_NAME_chatResponseId, PROP_ID_chatResponseId);
      
          PROP_ID_TO_NAME[PROP_ID_status] = PROP_NAME_status;
          PROP_NAME_TO_ID.put(PROP_NAME_status, PROP_ID_status);
      
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
    
    /* 需求ID: requirement_id */
    private java.lang.String _requirementId;
    
    /* 测试内容: test_content */
    private java.lang.String _testContent;
    
    /* 测试数据: test_data */
    private java.lang.String _testData;
    
    /* 关联文件ID: gen_file_id */
    private java.lang.String _genFileId;
    
    /* 响应ID: chat_response_id */
    private java.lang.String _chatResponseId;
    
    /* 状态: status */
    private java.lang.String _status;
    
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
    

    public _NopAiTestCase(){
        // for debug
    }

    protected NopAiTestCase newInstance(){
        NopAiTestCase entity = new NopAiTestCase();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopAiTestCase cloneInstance() {
        NopAiTestCase entity = newInstance();
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
      return "io.nop.ai.dao.entity.NopAiTestCase";
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
        
            case PROP_ID_requirementId:
               return getRequirementId();
        
            case PROP_ID_testContent:
               return getTestContent();
        
            case PROP_ID_testData:
               return getTestData();
        
            case PROP_ID_genFileId:
               return getGenFileId();
        
            case PROP_ID_chatResponseId:
               return getChatResponseId();
        
            case PROP_ID_status:
               return getStatus();
        
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
        
            case PROP_ID_requirementId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_requirementId));
               }
               setRequirementId(typedValue);
               break;
            }
        
            case PROP_ID_testContent:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_testContent));
               }
               setTestContent(typedValue);
               break;
            }
        
            case PROP_ID_testData:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_testData));
               }
               setTestData(typedValue);
               break;
            }
        
            case PROP_ID_genFileId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_genFileId));
               }
               setGenFileId(typedValue);
               break;
            }
        
            case PROP_ID_chatResponseId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_chatResponseId));
               }
               setChatResponseId(typedValue);
               break;
            }
        
            case PROP_ID_status:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_status));
               }
               setStatus(typedValue);
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
        
            case PROP_ID_requirementId:{
               onInitProp(propId);
               this._requirementId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_testContent:{
               onInitProp(propId);
               this._testContent = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_testData:{
               onInitProp(propId);
               this._testData = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_genFileId:{
               onInitProp(propId);
               this._genFileId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_chatResponseId:{
               onInitProp(propId);
               this._chatResponseId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_status:{
               onInitProp(propId);
               this._status = (java.lang.String)value;
               
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
     * 需求ID: requirement_id
     */
    public final java.lang.String getRequirementId(){
         onPropGet(PROP_ID_requirementId);
         return _requirementId;
    }

    /**
     * 需求ID: requirement_id
     */
    public final void setRequirementId(java.lang.String value){
        if(onPropSet(PROP_ID_requirementId,value)){
            this._requirementId = value;
            internalClearRefs(PROP_ID_requirementId);
            
        }
    }
    
    /**
     * 测试内容: test_content
     */
    public final java.lang.String getTestContent(){
         onPropGet(PROP_ID_testContent);
         return _testContent;
    }

    /**
     * 测试内容: test_content
     */
    public final void setTestContent(java.lang.String value){
        if(onPropSet(PROP_ID_testContent,value)){
            this._testContent = value;
            internalClearRefs(PROP_ID_testContent);
            
        }
    }
    
    /**
     * 测试数据: test_data
     */
    public final java.lang.String getTestData(){
         onPropGet(PROP_ID_testData);
         return _testData;
    }

    /**
     * 测试数据: test_data
     */
    public final void setTestData(java.lang.String value){
        if(onPropSet(PROP_ID_testData,value)){
            this._testData = value;
            internalClearRefs(PROP_ID_testData);
            
        }
    }
    
    /**
     * 关联文件ID: gen_file_id
     */
    public final java.lang.String getGenFileId(){
         onPropGet(PROP_ID_genFileId);
         return _genFileId;
    }

    /**
     * 关联文件ID: gen_file_id
     */
    public final void setGenFileId(java.lang.String value){
        if(onPropSet(PROP_ID_genFileId,value)){
            this._genFileId = value;
            internalClearRefs(PROP_ID_genFileId);
            
        }
    }
    
    /**
     * 响应ID: chat_response_id
     */
    public final java.lang.String getChatResponseId(){
         onPropGet(PROP_ID_chatResponseId);
         return _chatResponseId;
    }

    /**
     * 响应ID: chat_response_id
     */
    public final void setChatResponseId(java.lang.String value){
        if(onPropSet(PROP_ID_chatResponseId,value)){
            this._chatResponseId = value;
            internalClearRefs(PROP_ID_chatResponseId);
            
        }
    }
    
    /**
     * 状态: status
     */
    public final java.lang.String getStatus(){
         onPropGet(PROP_ID_status);
         return _status;
    }

    /**
     * 状态: status
     */
    public final void setStatus(java.lang.String value){
        if(onPropSet(PROP_ID_status,value)){
            this._status = value;
            internalClearRefs(PROP_ID_status);
            
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
    public final io.nop.ai.dao.entity.NopAiRequirement getRequirement(){
       return (io.nop.ai.dao.entity.NopAiRequirement)internalGetRefEntity(PROP_NAME_requirement);
    }

    public final void setRequirement(io.nop.ai.dao.entity.NopAiRequirement refEntity){
   
           if(refEntity == null){
           
                   this.setRequirementId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_requirement, refEntity,()->{
           
                           this.setRequirementId(refEntity.getId());
                       
           });
           }
       
    }
       
    /**
     * 
     */
    public final io.nop.ai.dao.entity.NopAiGenFile getGenFile(){
       return (io.nop.ai.dao.entity.NopAiGenFile)internalGetRefEntity(PROP_NAME_genFile);
    }

    public final void setGenFile(io.nop.ai.dao.entity.NopAiGenFile refEntity){
   
           if(refEntity == null){
           
                   this.setGenFileId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_genFile, refEntity,()->{
           
                           this.setGenFileId(refEntity.getId());
                       
           });
           }
       
    }
       
    /**
     * 
     */
    public final io.nop.ai.dao.entity.NopAiChatResponse getChatResponse(){
       return (io.nop.ai.dao.entity.NopAiChatResponse)internalGetRefEntity(PROP_NAME_chatResponse);
    }

    public final void setChatResponse(io.nop.ai.dao.entity.NopAiChatResponse refEntity){
   
           if(refEntity == null){
           
                   this.setChatResponseId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_chatResponse, refEntity,()->{
           
                           this.setChatResponseId(refEntity.getId());
                       
           });
           }
       
    }
       
    private final OrmEntitySet<io.nop.ai.dao.entity.NopAiTestResult> _testResults = new OrmEntitySet<>(this, PROP_NAME_testResults,
        io.nop.ai.dao.entity.NopAiTestResult.PROP_NAME_testCase, null,io.nop.ai.dao.entity.NopAiTestResult.class);

    /**
     * 执行结果。 refPropName: testCase, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<io.nop.ai.dao.entity.NopAiTestResult> getTestResults(){
       return _testResults;
    }
       
}
// resume CPD analysis - CPD-ON
