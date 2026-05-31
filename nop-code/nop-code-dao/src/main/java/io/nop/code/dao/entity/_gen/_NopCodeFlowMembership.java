package io.nop.code.dao.entity._gen;

import io.nop.orm.model.IEntityModel;
import io.nop.orm.support.DynamicOrmEntity;
import io.nop.orm.support.OrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.orm.IOrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.api.core.convert.ConvertHelper;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;

import io.nop.code.dao.entity.NopCodeFlowMembership;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  执行流成员: nop_code_flow_membership
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopCodeFlowMembership extends DynamicOrmEntity{
    
    /* ID: ID VARCHAR */
    public static final String PROP_NAME_id = "id";
    public static final int PROP_ID_id = 1;
    
    /* 流ID: FLOW_ID VARCHAR */
    public static final String PROP_NAME_flowId = "flowId";
    public static final int PROP_ID_flowId = 2;
    
    /* 符号ID: SYMBOL_ID VARCHAR */
    public static final String PROP_NAME_symbolId = "symbolId";
    public static final int PROP_ID_symbolId = 3;
    
    /* 深度: DEPTH INTEGER */
    public static final String PROP_NAME_depth = "depth";
    public static final int PROP_ID_depth = 4;
    
    /* 是否入口: IS_ENTRY BOOLEAN */
    public static final String PROP_NAME_isEntry = "isEntry";
    public static final int PROP_ID_isEntry = 5;
    
    /* 创建时间: CREATED_TIME DATETIME */
    public static final String PROP_NAME_createdTime = "createdTime";
    public static final int PROP_ID_createdTime = 6;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 7;
    
    /* 更新时间: UPDATE_TIME DATETIME */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 8;
    
    /* 更新人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 9;
    

    private static int _PROP_ID_BOUND = 10;

    
    /* relation:  */
    public static final String PROP_NAME_flow = "flow";
    
    /* relation:  */
    public static final String PROP_NAME_symbol = "symbol";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_id);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_id};

    private static final String[] PROP_ID_TO_NAME = new String[10];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_id] = PROP_NAME_id;
          PROP_NAME_TO_ID.put(PROP_NAME_id, PROP_ID_id);
      
          PROP_ID_TO_NAME[PROP_ID_flowId] = PROP_NAME_flowId;
          PROP_NAME_TO_ID.put(PROP_NAME_flowId, PROP_ID_flowId);
      
          PROP_ID_TO_NAME[PROP_ID_symbolId] = PROP_NAME_symbolId;
          PROP_NAME_TO_ID.put(PROP_NAME_symbolId, PROP_ID_symbolId);
      
          PROP_ID_TO_NAME[PROP_ID_depth] = PROP_NAME_depth;
          PROP_NAME_TO_ID.put(PROP_NAME_depth, PROP_ID_depth);
      
          PROP_ID_TO_NAME[PROP_ID_isEntry] = PROP_NAME_isEntry;
          PROP_NAME_TO_ID.put(PROP_NAME_isEntry, PROP_ID_isEntry);
      
          PROP_ID_TO_NAME[PROP_ID_createdTime] = PROP_NAME_createdTime;
          PROP_NAME_TO_ID.put(PROP_NAME_createdTime, PROP_ID_createdTime);
      
          PROP_ID_TO_NAME[PROP_ID_createdBy] = PROP_NAME_createdBy;
          PROP_NAME_TO_ID.put(PROP_NAME_createdBy, PROP_ID_createdBy);
      
          PROP_ID_TO_NAME[PROP_ID_updateTime] = PROP_NAME_updateTime;
          PROP_NAME_TO_ID.put(PROP_NAME_updateTime, PROP_ID_updateTime);
      
          PROP_ID_TO_NAME[PROP_ID_updatedBy] = PROP_NAME_updatedBy;
          PROP_NAME_TO_ID.put(PROP_NAME_updatedBy, PROP_ID_updatedBy);
      
    }

    
    /* ID: ID */
    private java.lang.String _id;
    
    /* 流ID: FLOW_ID */
    private java.lang.String _flowId;
    
    /* 符号ID: SYMBOL_ID */
    private java.lang.String _symbolId;
    
    /* 深度: DEPTH */
    private java.lang.Integer _depth;
    
    /* 是否入口: IS_ENTRY */
    private java.lang.Boolean _isEntry;
    
    /* 创建时间: CREATED_TIME */
    private java.sql.Timestamp _createdTime;
    
    /* 创建人: CREATED_BY */
    private java.lang.String _createdBy;
    
    /* 更新时间: UPDATE_TIME */
    private java.sql.Timestamp _updateTime;
    
    /* 更新人: UPDATED_BY */
    private java.lang.String _updatedBy;
    

    public _NopCodeFlowMembership(){
        // for debug
    }

    protected NopCodeFlowMembership newInstance(){
        NopCodeFlowMembership entity = new NopCodeFlowMembership();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopCodeFlowMembership cloneInstance() {
        NopCodeFlowMembership entity = newInstance();
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
      return "io.nop.code.dao.entity.NopCodeFlowMembership";
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
        
            case PROP_ID_flowId:
               return getFlowId();
        
            case PROP_ID_symbolId:
               return getSymbolId();
        
            case PROP_ID_depth:
               return getDepth();
        
            case PROP_ID_isEntry:
               return getIsEntry();
        
            case PROP_ID_createdTime:
               return getCreatedTime();
        
            case PROP_ID_createdBy:
               return getCreatedBy();
        
            case PROP_ID_updateTime:
               return getUpdateTime();
        
            case PROP_ID_updatedBy:
               return getUpdatedBy();
        
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
        
            case PROP_ID_flowId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_flowId));
               }
               setFlowId(typedValue);
               break;
            }
        
            case PROP_ID_symbolId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_symbolId));
               }
               setSymbolId(typedValue);
               break;
            }
        
            case PROP_ID_depth:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_depth));
               }
               setDepth(typedValue);
               break;
            }
        
            case PROP_ID_isEntry:{
               java.lang.Boolean typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toBoolean(value,
                       err-> newTypeConversionError(PROP_NAME_isEntry));
               }
               setIsEntry(typedValue);
               break;
            }
        
            case PROP_ID_createdTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_createdTime));
               }
               setCreatedTime(typedValue);
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
        
            case PROP_ID_updateTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_updateTime));
               }
               setUpdateTime(typedValue);
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
        
            case PROP_ID_flowId:{
               onInitProp(propId);
               this._flowId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_symbolId:{
               onInitProp(propId);
               this._symbolId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_depth:{
               onInitProp(propId);
               this._depth = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_isEntry:{
               onInitProp(propId);
               this._isEntry = (java.lang.Boolean)value;
               
               break;
            }
        
            case PROP_ID_createdTime:{
               onInitProp(propId);
               this._createdTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_createdBy:{
               onInitProp(propId);
               this._createdBy = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_updateTime:{
               onInitProp(propId);
               this._updateTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_updatedBy:{
               onInitProp(propId);
               this._updatedBy = (java.lang.String)value;
               
               break;
            }
        
           default:
              super.orm_internalSet(propId,value);
        }
    }

    
    /**
     * ID: ID
     */
    public final java.lang.String getId(){
         onPropGet(PROP_ID_id);
         return _id;
    }

    /**
     * ID: ID
     */
    public final void setId(java.lang.String value){
        if(onPropSet(PROP_ID_id,value)){
            this._id = value;
            internalClearRefs(PROP_ID_id);
            orm_id();
        }
    }
    
    /**
     * 流ID: FLOW_ID
     */
    public final java.lang.String getFlowId(){
         onPropGet(PROP_ID_flowId);
         return _flowId;
    }

    /**
     * 流ID: FLOW_ID
     */
    public final void setFlowId(java.lang.String value){
        if(onPropSet(PROP_ID_flowId,value)){
            this._flowId = value;
            internalClearRefs(PROP_ID_flowId);
            
        }
    }
    
    /**
     * 符号ID: SYMBOL_ID
     */
    public final java.lang.String getSymbolId(){
         onPropGet(PROP_ID_symbolId);
         return _symbolId;
    }

    /**
     * 符号ID: SYMBOL_ID
     */
    public final void setSymbolId(java.lang.String value){
        if(onPropSet(PROP_ID_symbolId,value)){
            this._symbolId = value;
            internalClearRefs(PROP_ID_symbolId);
            
        }
    }
    
    /**
     * 深度: DEPTH
     */
    public final java.lang.Integer getDepth(){
         onPropGet(PROP_ID_depth);
         return _depth;
    }

    /**
     * 深度: DEPTH
     */
    public final void setDepth(java.lang.Integer value){
        if(onPropSet(PROP_ID_depth,value)){
            this._depth = value;
            internalClearRefs(PROP_ID_depth);
            
        }
    }
    
    /**
     * 是否入口: IS_ENTRY
     */
    public final java.lang.Boolean getIsEntry(){
         onPropGet(PROP_ID_isEntry);
         return _isEntry;
    }

    /**
     * 是否入口: IS_ENTRY
     */
    public final void setIsEntry(java.lang.Boolean value){
        if(onPropSet(PROP_ID_isEntry,value)){
            this._isEntry = value;
            internalClearRefs(PROP_ID_isEntry);
            
        }
    }
    
    /**
     * 创建时间: CREATED_TIME
     */
    public final java.sql.Timestamp getCreatedTime(){
         onPropGet(PROP_ID_createdTime);
         return _createdTime;
    }

    /**
     * 创建时间: CREATED_TIME
     */
    public final void setCreatedTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_createdTime,value)){
            this._createdTime = value;
            internalClearRefs(PROP_ID_createdTime);
            
        }
    }
    
    /**
     * 创建人: CREATED_BY
     */
    public final java.lang.String getCreatedBy(){
         onPropGet(PROP_ID_createdBy);
         return _createdBy;
    }

    /**
     * 创建人: CREATED_BY
     */
    public final void setCreatedBy(java.lang.String value){
        if(onPropSet(PROP_ID_createdBy,value)){
            this._createdBy = value;
            internalClearRefs(PROP_ID_createdBy);
            
        }
    }
    
    /**
     * 更新时间: UPDATE_TIME
     */
    public final java.sql.Timestamp getUpdateTime(){
         onPropGet(PROP_ID_updateTime);
         return _updateTime;
    }

    /**
     * 更新时间: UPDATE_TIME
     */
    public final void setUpdateTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_updateTime,value)){
            this._updateTime = value;
            internalClearRefs(PROP_ID_updateTime);
            
        }
    }
    
    /**
     * 更新人: UPDATED_BY
     */
    public final java.lang.String getUpdatedBy(){
         onPropGet(PROP_ID_updatedBy);
         return _updatedBy;
    }

    /**
     * 更新人: UPDATED_BY
     */
    public final void setUpdatedBy(java.lang.String value){
        if(onPropSet(PROP_ID_updatedBy,value)){
            this._updatedBy = value;
            internalClearRefs(PROP_ID_updatedBy);
            
        }
    }
    
    /**
     * 
     */
    public final io.nop.code.dao.entity.NopCodeFlow getFlow(){
       return (io.nop.code.dao.entity.NopCodeFlow)internalGetRefEntity(PROP_NAME_flow);
    }

    public final void setFlow(io.nop.code.dao.entity.NopCodeFlow refEntity){
   
           if(refEntity == null){
           
                   this.setFlowId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_flow, refEntity,()->{
           
                           this.setFlowId(refEntity.getId());
                       
           });
           }
       
    }
       
    /**
     * 
     */
    public final io.nop.code.dao.entity.NopCodeSymbol getSymbol(){
       return (io.nop.code.dao.entity.NopCodeSymbol)internalGetRefEntity(PROP_NAME_symbol);
    }

    public final void setSymbol(io.nop.code.dao.entity.NopCodeSymbol refEntity){
   
           if(refEntity == null){
           
                   this.setSymbolId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_symbol, refEntity,()->{
           
                           this.setSymbolId(refEntity.getId());
                       
           });
           }
       
    }
       
}
// resume CPD analysis - CPD-ON
