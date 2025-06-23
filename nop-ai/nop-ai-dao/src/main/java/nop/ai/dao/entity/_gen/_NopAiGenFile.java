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

import nop.ai.dao.entity.NopAiGenFile;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  生成文件: nop_ai_gen_file
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopAiGenFile extends DynamicOrmEntity{
    
    /* 主键: id VARCHAR */
    public static final String PROP_NAME_id = "id";
    public static final int PROP_ID_id = 1;
    
    /* 项目ID: project_id VARCHAR */
    public static final String PROP_NAME_projectId = "projectId";
    public static final int PROP_ID_projectId = 2;
    
    /* 需求ID: requirement_id VARCHAR */
    public static final String PROP_NAME_requirementId = "requirementId";
    public static final int PROP_ID_requirementId = 3;
    
    /* 模块类型: module_type VARCHAR */
    public static final String PROP_NAME_moduleType = "moduleType";
    public static final int PROP_ID_moduleType = 4;
    
    /* 文件内容: content VARCHAR */
    public static final String PROP_NAME_content = "content";
    public static final int PROP_ID_content = 5;
    
    /* 文件路径: file_path VARCHAR */
    public static final String PROP_NAME_filePath = "filePath";
    public static final int PROP_ID_filePath = 6;
    
    /* 响应ID: chat_response_id VARCHAR */
    public static final String PROP_NAME_chatResponseId = "chatResponseId";
    public static final int PROP_ID_chatResponseId = 7;
    
    /* 状态: status VARCHAR */
    public static final String PROP_NAME_status = "status";
    public static final int PROP_ID_status = 8;
    

    private static int _PROP_ID_BOUND = 9;

    
    /* relation:  */
    public static final String PROP_NAME_project = "project";
    
    /* relation:  */
    public static final String PROP_NAME_requirement = "requirement";
    
    /* relation:  */
    public static final String PROP_NAME_chatResponse = "chatResponse";
    
    /* relation: 历史版本 */
    public static final String PROP_NAME_historyRecords = "historyRecords";
    
    /* relation: 测试用例 */
    public static final String PROP_NAME_testCases = "testCases";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_id);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_id};

    private static final String[] PROP_ID_TO_NAME = new String[9];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_id] = PROP_NAME_id;
          PROP_NAME_TO_ID.put(PROP_NAME_id, PROP_ID_id);
      
          PROP_ID_TO_NAME[PROP_ID_projectId] = PROP_NAME_projectId;
          PROP_NAME_TO_ID.put(PROP_NAME_projectId, PROP_ID_projectId);
      
          PROP_ID_TO_NAME[PROP_ID_requirementId] = PROP_NAME_requirementId;
          PROP_NAME_TO_ID.put(PROP_NAME_requirementId, PROP_ID_requirementId);
      
          PROP_ID_TO_NAME[PROP_ID_moduleType] = PROP_NAME_moduleType;
          PROP_NAME_TO_ID.put(PROP_NAME_moduleType, PROP_ID_moduleType);
      
          PROP_ID_TO_NAME[PROP_ID_content] = PROP_NAME_content;
          PROP_NAME_TO_ID.put(PROP_NAME_content, PROP_ID_content);
      
          PROP_ID_TO_NAME[PROP_ID_filePath] = PROP_NAME_filePath;
          PROP_NAME_TO_ID.put(PROP_NAME_filePath, PROP_ID_filePath);
      
          PROP_ID_TO_NAME[PROP_ID_chatResponseId] = PROP_NAME_chatResponseId;
          PROP_NAME_TO_ID.put(PROP_NAME_chatResponseId, PROP_ID_chatResponseId);
      
          PROP_ID_TO_NAME[PROP_ID_status] = PROP_NAME_status;
          PROP_NAME_TO_ID.put(PROP_NAME_status, PROP_ID_status);
      
    }

    
    /* 主键: id */
    private java.lang.String _id;
    
    /* 项目ID: project_id */
    private java.lang.String _projectId;
    
    /* 需求ID: requirement_id */
    private java.lang.String _requirementId;
    
    /* 模块类型: module_type */
    private java.lang.String _moduleType;
    
    /* 文件内容: content */
    private java.lang.String _content;
    
    /* 文件路径: file_path */
    private java.lang.String _filePath;
    
    /* 响应ID: chat_response_id */
    private java.lang.String _chatResponseId;
    
    /* 状态: status */
    private java.lang.String _status;
    

    public _NopAiGenFile(){
        // for debug
    }

    protected NopAiGenFile newInstance(){
        NopAiGenFile entity = new NopAiGenFile();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopAiGenFile cloneInstance() {
        NopAiGenFile entity = newInstance();
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
      return "nop.ai.dao.entity.NopAiGenFile";
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
        
            case PROP_ID_projectId:
               return getProjectId();
        
            case PROP_ID_requirementId:
               return getRequirementId();
        
            case PROP_ID_moduleType:
               return getModuleType();
        
            case PROP_ID_content:
               return getContent();
        
            case PROP_ID_filePath:
               return getFilePath();
        
            case PROP_ID_chatResponseId:
               return getChatResponseId();
        
            case PROP_ID_status:
               return getStatus();
        
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
        
            case PROP_ID_projectId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_projectId));
               }
               setProjectId(typedValue);
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
        
            case PROP_ID_moduleType:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_moduleType));
               }
               setModuleType(typedValue);
               break;
            }
        
            case PROP_ID_content:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_content));
               }
               setContent(typedValue);
               break;
            }
        
            case PROP_ID_filePath:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_filePath));
               }
               setFilePath(typedValue);
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
        
            case PROP_ID_projectId:{
               onInitProp(propId);
               this._projectId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_requirementId:{
               onInitProp(propId);
               this._requirementId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_moduleType:{
               onInitProp(propId);
               this._moduleType = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_content:{
               onInitProp(propId);
               this._content = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_filePath:{
               onInitProp(propId);
               this._filePath = (java.lang.String)value;
               
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
     * 项目ID: project_id
     */
    public final java.lang.String getProjectId(){
         onPropGet(PROP_ID_projectId);
         return _projectId;
    }

    /**
     * 项目ID: project_id
     */
    public final void setProjectId(java.lang.String value){
        if(onPropSet(PROP_ID_projectId,value)){
            this._projectId = value;
            internalClearRefs(PROP_ID_projectId);
            
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
     * 模块类型: module_type
     */
    public final java.lang.String getModuleType(){
         onPropGet(PROP_ID_moduleType);
         return _moduleType;
    }

    /**
     * 模块类型: module_type
     */
    public final void setModuleType(java.lang.String value){
        if(onPropSet(PROP_ID_moduleType,value)){
            this._moduleType = value;
            internalClearRefs(PROP_ID_moduleType);
            
        }
    }
    
    /**
     * 文件内容: content
     */
    public final java.lang.String getContent(){
         onPropGet(PROP_ID_content);
         return _content;
    }

    /**
     * 文件内容: content
     */
    public final void setContent(java.lang.String value){
        if(onPropSet(PROP_ID_content,value)){
            this._content = value;
            internalClearRefs(PROP_ID_content);
            
        }
    }
    
    /**
     * 文件路径: file_path
     */
    public final java.lang.String getFilePath(){
         onPropGet(PROP_ID_filePath);
         return _filePath;
    }

    /**
     * 文件路径: file_path
     */
    public final void setFilePath(java.lang.String value){
        if(onPropSet(PROP_ID_filePath,value)){
            this._filePath = value;
            internalClearRefs(PROP_ID_filePath);
            
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
     * 
     */
    public final nop.ai.dao.entity.NopAiProject getProject(){
       return (nop.ai.dao.entity.NopAiProject)internalGetRefEntity(PROP_NAME_project);
    }

    public final void setProject(nop.ai.dao.entity.NopAiProject refEntity){
   
           if(refEntity == null){
           
                   this.setProjectId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_project, refEntity,()->{
           
                           this.setProjectId(refEntity.getId());
                       
           });
           }
       
    }
       
    /**
     * 
     */
    public final nop.ai.dao.entity.NopAiRequirement getRequirement(){
       return (nop.ai.dao.entity.NopAiRequirement)internalGetRefEntity(PROP_NAME_requirement);
    }

    public final void setRequirement(nop.ai.dao.entity.NopAiRequirement refEntity){
   
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
    public final nop.ai.dao.entity.NopAiChatResponse getChatResponse(){
       return (nop.ai.dao.entity.NopAiChatResponse)internalGetRefEntity(PROP_NAME_chatResponse);
    }

    public final void setChatResponse(nop.ai.dao.entity.NopAiChatResponse refEntity){
   
           if(refEntity == null){
           
                   this.setChatResponseId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_chatResponse, refEntity,()->{
           
                           this.setChatResponseId(refEntity.getId());
                       
           });
           }
       
    }
       
    private final OrmEntitySet<nop.ai.dao.entity.NopAiGenFileHistory> _historyRecords = new OrmEntitySet<>(this, PROP_NAME_historyRecords,
        nop.ai.dao.entity.NopAiGenFileHistory.PROP_NAME_genFile, null,nop.ai.dao.entity.NopAiGenFileHistory.class);

    /**
     * 历史版本。 refPropName: genFile, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<nop.ai.dao.entity.NopAiGenFileHistory> getHistoryRecords(){
       return _historyRecords;
    }
       
    private final OrmEntitySet<nop.ai.dao.entity.NopAiTestCase> _testCases = new OrmEntitySet<>(this, PROP_NAME_testCases,
        nop.ai.dao.entity.NopAiTestCase.PROP_NAME_genFile, null,nop.ai.dao.entity.NopAiTestCase.class);

    /**
     * 测试用例。 refPropName: genFile, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<nop.ai.dao.entity.NopAiTestCase> getTestCases(){
       return _testCases;
    }
       
}
// resume CPD analysis - CPD-ON
