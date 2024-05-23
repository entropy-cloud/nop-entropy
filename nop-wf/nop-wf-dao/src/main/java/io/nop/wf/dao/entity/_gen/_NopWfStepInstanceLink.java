package io.nop.wf.dao.entity._gen;

import io.nop.orm.model.IEntityModel;
import io.nop.orm.support.DynamicOrmEntity;
import io.nop.orm.support.OrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.orm.IOrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.api.core.convert.ConvertHelper;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;

import io.nop.wf.dao.entity.NopWfStepInstanceLink;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  工作流步骤关联: nop_wf_step_instance_link
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopWfStepInstanceLink extends DynamicOrmEntity{
    
    /* 工作流实例ID: WF_ID VARCHAR */
    public static final String PROP_NAME_wfId = "wfId";
    public static final int PROP_ID_wfId = 1;
    
    /* 步骤ID: STEP_ID VARCHAR */
    public static final String PROP_NAME_stepId = "stepId";
    public static final int PROP_ID_stepId = 2;
    
    /* 下一步骤 ID: NEXT_STEP_ID VARCHAR */
    public static final String PROP_NAME_nextStepId = "nextStepId";
    public static final int PROP_ID_nextStepId = 3;
    
    /* 执行动作: EXEC_ACTION VARCHAR */
    public static final String PROP_NAME_execAction = "execAction";
    public static final int PROP_ID_execAction = 4;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 5;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 6;
    

    private static int _PROP_ID_BOUND = 7;

    
    /* relation: 工作流实例 */
    public static final String PROP_NAME_wfInstance = "wfInstance";
    
    /* relation: 工作流步骤 */
    public static final String PROP_NAME_wfStep = "wfStep";
    
    /* relation: 工作流步骤 */
    public static final String PROP_NAME_nextWfStep = "nextWfStep";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_wfId,PROP_NAME_stepId,PROP_NAME_nextStepId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_wfId,PROP_ID_stepId,PROP_ID_nextStepId};

    private static final String[] PROP_ID_TO_NAME = new String[7];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_wfId] = PROP_NAME_wfId;
          PROP_NAME_TO_ID.put(PROP_NAME_wfId, PROP_ID_wfId);
      
          PROP_ID_TO_NAME[PROP_ID_stepId] = PROP_NAME_stepId;
          PROP_NAME_TO_ID.put(PROP_NAME_stepId, PROP_ID_stepId);
      
          PROP_ID_TO_NAME[PROP_ID_nextStepId] = PROP_NAME_nextStepId;
          PROP_NAME_TO_ID.put(PROP_NAME_nextStepId, PROP_ID_nextStepId);
      
          PROP_ID_TO_NAME[PROP_ID_execAction] = PROP_NAME_execAction;
          PROP_NAME_TO_ID.put(PROP_NAME_execAction, PROP_ID_execAction);
      
          PROP_ID_TO_NAME[PROP_ID_createdBy] = PROP_NAME_createdBy;
          PROP_NAME_TO_ID.put(PROP_NAME_createdBy, PROP_ID_createdBy);
      
          PROP_ID_TO_NAME[PROP_ID_createTime] = PROP_NAME_createTime;
          PROP_NAME_TO_ID.put(PROP_NAME_createTime, PROP_ID_createTime);
      
    }

    
    /* 工作流实例ID: WF_ID */
    private java.lang.String _wfId;
    
    /* 步骤ID: STEP_ID */
    private java.lang.String _stepId;
    
    /* 下一步骤 ID: NEXT_STEP_ID */
    private java.lang.String _nextStepId;
    
    /* 执行动作: EXEC_ACTION */
    private java.lang.String _execAction;
    
    /* 创建人: CREATED_BY */
    private java.lang.String _createdBy;
    
    /* 创建时间: CREATE_TIME */
    private java.sql.Timestamp _createTime;
    

    public _NopWfStepInstanceLink(){
        // for debug
    }

    protected NopWfStepInstanceLink newInstance(){
       return new NopWfStepInstanceLink();
    }

    @Override
    public NopWfStepInstanceLink cloneInstance() {
        NopWfStepInstanceLink entity = newInstance();
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
      return "io.nop.wf.dao.entity.NopWfStepInstanceLink";
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
        
            return propId == PROP_ID_wfId || propId == PROP_ID_stepId || propId == PROP_ID_nextStepId;
          
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
        
            case PROP_ID_wfId:
               return getWfId();
        
            case PROP_ID_stepId:
               return getStepId();
        
            case PROP_ID_nextStepId:
               return getNextStepId();
        
            case PROP_ID_execAction:
               return getExecAction();
        
            case PROP_ID_createdBy:
               return getCreatedBy();
        
            case PROP_ID_createTime:
               return getCreateTime();
        
           default:
              return super.orm_propValue(propId);
        }
    }

    

    @Override
    public void orm_propValue(int propId, Object value){
        switch(propId){
        
            case PROP_ID_wfId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_wfId));
               }
               setWfId(typedValue);
               break;
            }
        
            case PROP_ID_stepId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_stepId));
               }
               setStepId(typedValue);
               break;
            }
        
            case PROP_ID_nextStepId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_nextStepId));
               }
               setNextStepId(typedValue);
               break;
            }
        
            case PROP_ID_execAction:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_execAction));
               }
               setExecAction(typedValue);
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
        
           default:
              super.orm_propValue(propId,value);
        }
    }

    @Override
    public void orm_internalSet(int propId, Object value) {
        switch(propId){
        
            case PROP_ID_wfId:{
               onInitProp(propId);
               this._wfId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_stepId:{
               onInitProp(propId);
               this._stepId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_nextStepId:{
               onInitProp(propId);
               this._nextStepId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_execAction:{
               onInitProp(propId);
               this._execAction = (java.lang.String)value;
               
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
        
           default:
              super.orm_internalSet(propId,value);
        }
    }

    
    /**
     * 工作流实例ID: WF_ID
     */
    public java.lang.String getWfId(){
         onPropGet(PROP_ID_wfId);
         return _wfId;
    }

    /**
     * 工作流实例ID: WF_ID
     */
    public void setWfId(java.lang.String value){
        if(onPropSet(PROP_ID_wfId,value)){
            this._wfId = value;
            internalClearRefs(PROP_ID_wfId);
            orm_id();
        }
    }
    
    /**
     * 步骤ID: STEP_ID
     */
    public java.lang.String getStepId(){
         onPropGet(PROP_ID_stepId);
         return _stepId;
    }

    /**
     * 步骤ID: STEP_ID
     */
    public void setStepId(java.lang.String value){
        if(onPropSet(PROP_ID_stepId,value)){
            this._stepId = value;
            internalClearRefs(PROP_ID_stepId);
            orm_id();
        }
    }
    
    /**
     * 下一步骤 ID: NEXT_STEP_ID
     */
    public java.lang.String getNextStepId(){
         onPropGet(PROP_ID_nextStepId);
         return _nextStepId;
    }

    /**
     * 下一步骤 ID: NEXT_STEP_ID
     */
    public void setNextStepId(java.lang.String value){
        if(onPropSet(PROP_ID_nextStepId,value)){
            this._nextStepId = value;
            internalClearRefs(PROP_ID_nextStepId);
            orm_id();
        }
    }
    
    /**
     * 执行动作: EXEC_ACTION
     */
    public java.lang.String getExecAction(){
         onPropGet(PROP_ID_execAction);
         return _execAction;
    }

    /**
     * 执行动作: EXEC_ACTION
     */
    public void setExecAction(java.lang.String value){
        if(onPropSet(PROP_ID_execAction,value)){
            this._execAction = value;
            internalClearRefs(PROP_ID_execAction);
            
        }
    }
    
    /**
     * 创建人: CREATED_BY
     */
    public java.lang.String getCreatedBy(){
         onPropGet(PROP_ID_createdBy);
         return _createdBy;
    }

    /**
     * 创建人: CREATED_BY
     */
    public void setCreatedBy(java.lang.String value){
        if(onPropSet(PROP_ID_createdBy,value)){
            this._createdBy = value;
            internalClearRefs(PROP_ID_createdBy);
            
        }
    }
    
    /**
     * 创建时间: CREATE_TIME
     */
    public java.sql.Timestamp getCreateTime(){
         onPropGet(PROP_ID_createTime);
         return _createTime;
    }

    /**
     * 创建时间: CREATE_TIME
     */
    public void setCreateTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_createTime,value)){
            this._createTime = value;
            internalClearRefs(PROP_ID_createTime);
            
        }
    }
    
    /**
     * 工作流实例
     */
    public io.nop.wf.dao.entity.NopWfInstance getWfInstance(){
       return (io.nop.wf.dao.entity.NopWfInstance)internalGetRefEntity(PROP_NAME_wfInstance);
    }

    public void setWfInstance(io.nop.wf.dao.entity.NopWfInstance refEntity){
   
           if(refEntity == null){
           
                   this.setWfId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_wfInstance, refEntity,()->{
           
                           this.setWfId(refEntity.getWfId());
                       
           });
           }
       
    }
       
    /**
     * 工作流步骤
     */
    public io.nop.wf.dao.entity.NopWfStepInstance getWfStep(){
       return (io.nop.wf.dao.entity.NopWfStepInstance)internalGetRefEntity(PROP_NAME_wfStep);
    }

    public void setWfStep(io.nop.wf.dao.entity.NopWfStepInstance refEntity){
   
           if(refEntity == null){
           
                   this.setStepId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_wfStep, refEntity,()->{
           
                           this.setStepId(refEntity.getStepId());
                       
           });
           }
       
    }
       
    /**
     * 工作流步骤
     */
    public io.nop.wf.dao.entity.NopWfStepInstance getNextWfStep(){
       return (io.nop.wf.dao.entity.NopWfStepInstance)internalGetRefEntity(PROP_NAME_nextWfStep);
    }

    public void setNextWfStep(io.nop.wf.dao.entity.NopWfStepInstance refEntity){
   
           if(refEntity == null){
           
                   this.setNextStepId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_nextWfStep, refEntity,()->{
           
                           this.setNextStepId(refEntity.getStepId());
                       
           });
           }
       
    }
       
}
// resume CPD analysis - CPD-ON
