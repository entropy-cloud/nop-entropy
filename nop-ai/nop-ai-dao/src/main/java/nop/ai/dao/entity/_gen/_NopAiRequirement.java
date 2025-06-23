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

import nop.ai.dao.entity.NopAiRequirement;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  需求条目: nop_ai_requirement
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopAiRequirement extends DynamicOrmEntity{
    
    /* 主键: id VARCHAR */
    public static final String PROP_NAME_id = "id";
    public static final int PROP_ID_id = 1;
    
    /* 项目ID: project_id VARCHAR */
    public static final String PROP_NAME_projectId = "projectId";
    public static final int PROP_ID_projectId = 2;
    
    /* 需求编号: req_number VARCHAR */
    public static final String PROP_NAME_reqNumber = "reqNumber";
    public static final int PROP_ID_reqNumber = 3;
    
    /* 需求标题: title VARCHAR */
    public static final String PROP_NAME_title = "title";
    public static final int PROP_ID_title = 4;
    
    /* 需求内容: content VARCHAR */
    public static final String PROP_NAME_content = "content";
    public static final int PROP_ID_content = 5;
    
    /* 当前版本: version VARCHAR */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 6;
    
    /* 父需求ID: parent_id VARCHAR */
    public static final String PROP_NAME_parentId = "parentId";
    public static final int PROP_ID_parentId = 7;
    
    /* 需求类型: type VARCHAR */
    public static final String PROP_NAME_type = "type";
    public static final int PROP_ID_type = 8;
    
    /* AI摘要: ai_summary VARCHAR */
    public static final String PROP_NAME_aiSummary = "aiSummary";
    public static final int PROP_ID_aiSummary = 9;
    
    /* 状态: status VARCHAR */
    public static final String PROP_NAME_status = "status";
    public static final int PROP_ID_status = 10;
    

    private static int _PROP_ID_BOUND = 11;

    
    /* relation:  */
    public static final String PROP_NAME_project = "project";
    
    /* relation:  */
    public static final String PROP_NAME_parent = "parent";
    
    /* relation: 子需求 */
    public static final String PROP_NAME_children = "children";
    
    /* relation: 历史版本 */
    public static final String PROP_NAME_historyRecords = "historyRecords";
    
    /* relation: 关联文件 */
    public static final String PROP_NAME_generatedFiles = "generatedFiles";
    
    /* relation: 测试用例 */
    public static final String PROP_NAME_testCases = "testCases";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_id);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_id};

    private static final String[] PROP_ID_TO_NAME = new String[11];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_id] = PROP_NAME_id;
          PROP_NAME_TO_ID.put(PROP_NAME_id, PROP_ID_id);
      
          PROP_ID_TO_NAME[PROP_ID_projectId] = PROP_NAME_projectId;
          PROP_NAME_TO_ID.put(PROP_NAME_projectId, PROP_ID_projectId);
      
          PROP_ID_TO_NAME[PROP_ID_reqNumber] = PROP_NAME_reqNumber;
          PROP_NAME_TO_ID.put(PROP_NAME_reqNumber, PROP_ID_reqNumber);
      
          PROP_ID_TO_NAME[PROP_ID_title] = PROP_NAME_title;
          PROP_NAME_TO_ID.put(PROP_NAME_title, PROP_ID_title);
      
          PROP_ID_TO_NAME[PROP_ID_content] = PROP_NAME_content;
          PROP_NAME_TO_ID.put(PROP_NAME_content, PROP_ID_content);
      
          PROP_ID_TO_NAME[PROP_ID_version] = PROP_NAME_version;
          PROP_NAME_TO_ID.put(PROP_NAME_version, PROP_ID_version);
      
          PROP_ID_TO_NAME[PROP_ID_parentId] = PROP_NAME_parentId;
          PROP_NAME_TO_ID.put(PROP_NAME_parentId, PROP_ID_parentId);
      
          PROP_ID_TO_NAME[PROP_ID_type] = PROP_NAME_type;
          PROP_NAME_TO_ID.put(PROP_NAME_type, PROP_ID_type);
      
          PROP_ID_TO_NAME[PROP_ID_aiSummary] = PROP_NAME_aiSummary;
          PROP_NAME_TO_ID.put(PROP_NAME_aiSummary, PROP_ID_aiSummary);
      
          PROP_ID_TO_NAME[PROP_ID_status] = PROP_NAME_status;
          PROP_NAME_TO_ID.put(PROP_NAME_status, PROP_ID_status);
      
    }

    
    /* 主键: id */
    private java.lang.String _id;
    
    /* 项目ID: project_id */
    private java.lang.String _projectId;
    
    /* 需求编号: req_number */
    private java.lang.String _reqNumber;
    
    /* 需求标题: title */
    private java.lang.String _title;
    
    /* 需求内容: content */
    private java.lang.String _content;
    
    /* 当前版本: version */
    private java.lang.String _version;
    
    /* 父需求ID: parent_id */
    private java.lang.String _parentId;
    
    /* 需求类型: type */
    private java.lang.String _type;
    
    /* AI摘要: ai_summary */
    private java.lang.String _aiSummary;
    
    /* 状态: status */
    private java.lang.String _status;
    

    public _NopAiRequirement(){
        // for debug
    }

    protected NopAiRequirement newInstance(){
        NopAiRequirement entity = new NopAiRequirement();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopAiRequirement cloneInstance() {
        NopAiRequirement entity = newInstance();
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
      return "nop.ai.dao.entity.NopAiRequirement";
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
        
            case PROP_ID_reqNumber:
               return getReqNumber();
        
            case PROP_ID_title:
               return getTitle();
        
            case PROP_ID_content:
               return getContent();
        
            case PROP_ID_version:
               return getVersion();
        
            case PROP_ID_parentId:
               return getParentId();
        
            case PROP_ID_type:
               return getType();
        
            case PROP_ID_aiSummary:
               return getAiSummary();
        
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
        
            case PROP_ID_reqNumber:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_reqNumber));
               }
               setReqNumber(typedValue);
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
        
            case PROP_ID_content:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_content));
               }
               setContent(typedValue);
               break;
            }
        
            case PROP_ID_version:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_version));
               }
               setVersion(typedValue);
               break;
            }
        
            case PROP_ID_parentId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_parentId));
               }
               setParentId(typedValue);
               break;
            }
        
            case PROP_ID_type:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_type));
               }
               setType(typedValue);
               break;
            }
        
            case PROP_ID_aiSummary:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_aiSummary));
               }
               setAiSummary(typedValue);
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
        
            case PROP_ID_reqNumber:{
               onInitProp(propId);
               this._reqNumber = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_title:{
               onInitProp(propId);
               this._title = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_content:{
               onInitProp(propId);
               this._content = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_version:{
               onInitProp(propId);
               this._version = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_parentId:{
               onInitProp(propId);
               this._parentId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_type:{
               onInitProp(propId);
               this._type = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_aiSummary:{
               onInitProp(propId);
               this._aiSummary = (java.lang.String)value;
               
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
     * 需求编号: req_number
     */
    public final java.lang.String getReqNumber(){
         onPropGet(PROP_ID_reqNumber);
         return _reqNumber;
    }

    /**
     * 需求编号: req_number
     */
    public final void setReqNumber(java.lang.String value){
        if(onPropSet(PROP_ID_reqNumber,value)){
            this._reqNumber = value;
            internalClearRefs(PROP_ID_reqNumber);
            
        }
    }
    
    /**
     * 需求标题: title
     */
    public final java.lang.String getTitle(){
         onPropGet(PROP_ID_title);
         return _title;
    }

    /**
     * 需求标题: title
     */
    public final void setTitle(java.lang.String value){
        if(onPropSet(PROP_ID_title,value)){
            this._title = value;
            internalClearRefs(PROP_ID_title);
            
        }
    }
    
    /**
     * 需求内容: content
     */
    public final java.lang.String getContent(){
         onPropGet(PROP_ID_content);
         return _content;
    }

    /**
     * 需求内容: content
     */
    public final void setContent(java.lang.String value){
        if(onPropSet(PROP_ID_content,value)){
            this._content = value;
            internalClearRefs(PROP_ID_content);
            
        }
    }
    
    /**
     * 当前版本: version
     */
    public final java.lang.String getVersion(){
         onPropGet(PROP_ID_version);
         return _version;
    }

    /**
     * 当前版本: version
     */
    public final void setVersion(java.lang.String value){
        if(onPropSet(PROP_ID_version,value)){
            this._version = value;
            internalClearRefs(PROP_ID_version);
            
        }
    }
    
    /**
     * 父需求ID: parent_id
     */
    public final java.lang.String getParentId(){
         onPropGet(PROP_ID_parentId);
         return _parentId;
    }

    /**
     * 父需求ID: parent_id
     */
    public final void setParentId(java.lang.String value){
        if(onPropSet(PROP_ID_parentId,value)){
            this._parentId = value;
            internalClearRefs(PROP_ID_parentId);
            
        }
    }
    
    /**
     * 需求类型: type
     */
    public final java.lang.String getType(){
         onPropGet(PROP_ID_type);
         return _type;
    }

    /**
     * 需求类型: type
     */
    public final void setType(java.lang.String value){
        if(onPropSet(PROP_ID_type,value)){
            this._type = value;
            internalClearRefs(PROP_ID_type);
            
        }
    }
    
    /**
     * AI摘要: ai_summary
     */
    public final java.lang.String getAiSummary(){
         onPropGet(PROP_ID_aiSummary);
         return _aiSummary;
    }

    /**
     * AI摘要: ai_summary
     */
    public final void setAiSummary(java.lang.String value){
        if(onPropSet(PROP_ID_aiSummary,value)){
            this._aiSummary = value;
            internalClearRefs(PROP_ID_aiSummary);
            
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
    public final nop.ai.dao.entity.NopAiRequirement getParent(){
       return (nop.ai.dao.entity.NopAiRequirement)internalGetRefEntity(PROP_NAME_parent);
    }

    public final void setParent(nop.ai.dao.entity.NopAiRequirement refEntity){
   
           if(refEntity == null){
           
                   this.setParentId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_parent, refEntity,()->{
           
                           this.setParentId(refEntity.getId());
                       
           });
           }
       
    }
       
    private final OrmEntitySet<nop.ai.dao.entity.NopAiRequirement> _children = new OrmEntitySet<>(this, PROP_NAME_children,
        nop.ai.dao.entity.NopAiRequirement.PROP_NAME_parent, null,nop.ai.dao.entity.NopAiRequirement.class);

    /**
     * 子需求。 refPropName: parent, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<nop.ai.dao.entity.NopAiRequirement> getChildren(){
       return _children;
    }
       
    private final OrmEntitySet<nop.ai.dao.entity.NopAiRequirementHistory> _historyRecords = new OrmEntitySet<>(this, PROP_NAME_historyRecords,
        nop.ai.dao.entity.NopAiRequirementHistory.PROP_NAME_requirement, null,nop.ai.dao.entity.NopAiRequirementHistory.class);

    /**
     * 历史版本。 refPropName: requirement, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<nop.ai.dao.entity.NopAiRequirementHistory> getHistoryRecords(){
       return _historyRecords;
    }
       
    private final OrmEntitySet<nop.ai.dao.entity.NopAiGenFile> _generatedFiles = new OrmEntitySet<>(this, PROP_NAME_generatedFiles,
        nop.ai.dao.entity.NopAiGenFile.PROP_NAME_requirement, null,nop.ai.dao.entity.NopAiGenFile.class);

    /**
     * 关联文件。 refPropName: requirement, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<nop.ai.dao.entity.NopAiGenFile> getGeneratedFiles(){
       return _generatedFiles;
    }
       
    private final OrmEntitySet<nop.ai.dao.entity.NopAiTestCase> _testCases = new OrmEntitySet<>(this, PROP_NAME_testCases,
        nop.ai.dao.entity.NopAiTestCase.PROP_NAME_requirement, null,nop.ai.dao.entity.NopAiTestCase.class);

    /**
     * 测试用例。 refPropName: requirement, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<nop.ai.dao.entity.NopAiTestCase> getTestCases(){
       return _testCases;
    }
       
}
// resume CPD analysis - CPD-ON
