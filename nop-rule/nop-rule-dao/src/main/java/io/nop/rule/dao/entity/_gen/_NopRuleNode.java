package io.nop.rule.dao.entity._gen;

import io.nop.orm.model.IEntityModel;
import io.nop.orm.support.DynamicOrmEntity;
import io.nop.orm.support.OrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.orm.IOrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.api.core.convert.ConvertHelper;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;

import io.nop.rule.dao.entity.NopRuleNode;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  规则节点: nop_rule_node
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public class _NopRuleNode extends DynamicOrmEntity{
    
    /* SID: SID VARCHAR */
    public static final String PROP_NAME_sid = "sid";
    public static final int PROP_ID_sid = 1;
    
    /* 规则ID: RULE_ID VARCHAR */
    public static final String PROP_NAME_ruleId = "ruleId";
    public static final int PROP_ID_ruleId = 2;
    
    /* 显示标签: LABEL VARCHAR */
    public static final String PROP_NAME_label = "label";
    public static final int PROP_ID_label = 3;
    
    /* 排序序号: SORT_NO INTEGER */
    public static final String PROP_NAME_sortNo = "sortNo";
    public static final int PROP_ID_sortNo = 4;
    
    /* 判断条件: PREDICATE VARCHAR */
    public static final String PROP_NAME_predicate = "predicate";
    public static final int PROP_ID_predicate = 5;
    
    /* 输出结果: OUTPUTS VARCHAR */
    public static final String PROP_NAME_outputs = "outputs";
    public static final int PROP_ID_outputs = 6;
    
    /* 父ID: PARENT_ID VARCHAR */
    public static final String PROP_NAME_parentId = "parentId";
    public static final int PROP_ID_parentId = 7;
    
    /* 是否叶子节点: IS_LEAF BOOLEAN */
    public static final String PROP_NAME_isLeaf = "isLeaf";
    public static final int PROP_ID_isLeaf = 8;
    
    /* 数据版本: VERSION INTEGER */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 9;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 10;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 11;
    
    /* 修改人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 12;
    
    /* 修改时间: UPDATE_TIME TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 13;
    
    /* 备注: REMARK VARCHAR */
    public static final String PROP_NAME_remark = "remark";
    public static final int PROP_ID_remark = 14;
    

    private static int _PROP_ID_BOUND = 15;

    
    /* relation: 父节点 */
    public static final String PROP_NAME_parent = "parent";
    
    /* relation: 规则定义 */
    public static final String PROP_NAME_ruleDefinition = "ruleDefinition";
    
    /* relation: 子节点 */
    public static final String PROP_NAME_children = "children";
    

    public static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_sid);
    public static final int[] PK_PROP_IDS = new int[]{PROP_ID_sid};

    private static final String[] PROP_ID_TO_NAME = new String[15];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_sid] = PROP_NAME_sid;
          PROP_NAME_TO_ID.put(PROP_NAME_sid, PROP_ID_sid);
      
          PROP_ID_TO_NAME[PROP_ID_ruleId] = PROP_NAME_ruleId;
          PROP_NAME_TO_ID.put(PROP_NAME_ruleId, PROP_ID_ruleId);
      
          PROP_ID_TO_NAME[PROP_ID_label] = PROP_NAME_label;
          PROP_NAME_TO_ID.put(PROP_NAME_label, PROP_ID_label);
      
          PROP_ID_TO_NAME[PROP_ID_sortNo] = PROP_NAME_sortNo;
          PROP_NAME_TO_ID.put(PROP_NAME_sortNo, PROP_ID_sortNo);
      
          PROP_ID_TO_NAME[PROP_ID_predicate] = PROP_NAME_predicate;
          PROP_NAME_TO_ID.put(PROP_NAME_predicate, PROP_ID_predicate);
      
          PROP_ID_TO_NAME[PROP_ID_outputs] = PROP_NAME_outputs;
          PROP_NAME_TO_ID.put(PROP_NAME_outputs, PROP_ID_outputs);
      
          PROP_ID_TO_NAME[PROP_ID_parentId] = PROP_NAME_parentId;
          PROP_NAME_TO_ID.put(PROP_NAME_parentId, PROP_ID_parentId);
      
          PROP_ID_TO_NAME[PROP_ID_isLeaf] = PROP_NAME_isLeaf;
          PROP_NAME_TO_ID.put(PROP_NAME_isLeaf, PROP_ID_isLeaf);
      
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
      
          PROP_ID_TO_NAME[PROP_ID_remark] = PROP_NAME_remark;
          PROP_NAME_TO_ID.put(PROP_NAME_remark, PROP_ID_remark);
      
    }

    
    /* SID: SID */
    private java.lang.String _sid;
    
    /* 规则ID: RULE_ID */
    private java.lang.String _ruleId;
    
    /* 显示标签: LABEL */
    private java.lang.String _label;
    
    /* 排序序号: SORT_NO */
    private java.lang.Integer _sortNo;
    
    /* 判断条件: PREDICATE */
    private java.lang.String _predicate;
    
    /* 输出结果: OUTPUTS */
    private java.lang.String _outputs;
    
    /* 父ID: PARENT_ID */
    private java.lang.String _parentId;
    
    /* 是否叶子节点: IS_LEAF */
    private java.lang.Boolean _isLeaf;
    
    /* 数据版本: VERSION */
    private java.lang.Integer _version;
    
    /* 创建人: CREATED_BY */
    private java.lang.String _createdBy;
    
    /* 创建时间: CREATE_TIME */
    private java.sql.Timestamp _createTime;
    
    /* 修改人: UPDATED_BY */
    private java.lang.String _updatedBy;
    
    /* 修改时间: UPDATE_TIME */
    private java.sql.Timestamp _updateTime;
    
    /* 备注: REMARK */
    private java.lang.String _remark;
    

    public _NopRuleNode(){
    }

    protected NopRuleNode newInstance(){
       return new NopRuleNode();
    }

    @Override
    public NopRuleNode cloneInstance() {
        NopRuleNode entity = newInstance();
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
      return "io.nop.rule.dao.entity.NopRuleNode";
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
        
            case PROP_ID_sid:
               return getSid();
        
            case PROP_ID_ruleId:
               return getRuleId();
        
            case PROP_ID_label:
               return getLabel();
        
            case PROP_ID_sortNo:
               return getSortNo();
        
            case PROP_ID_predicate:
               return getPredicate();
        
            case PROP_ID_outputs:
               return getOutputs();
        
            case PROP_ID_parentId:
               return getParentId();
        
            case PROP_ID_isLeaf:
               return getIsLeaf();
        
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
        
            case PROP_ID_remark:
               return getRemark();
        
           default:
              return super.orm_propValue(propId);
        }
    }

    

    @Override
    public void orm_propValue(int propId, Object value){
        switch(propId){
        
            case PROP_ID_sid:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_sid));
               }
               setSid(typedValue);
               break;
            }
        
            case PROP_ID_ruleId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_ruleId));
               }
               setRuleId(typedValue);
               break;
            }
        
            case PROP_ID_label:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_label));
               }
               setLabel(typedValue);
               break;
            }
        
            case PROP_ID_sortNo:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_sortNo));
               }
               setSortNo(typedValue);
               break;
            }
        
            case PROP_ID_predicate:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_predicate));
               }
               setPredicate(typedValue);
               break;
            }
        
            case PROP_ID_outputs:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_outputs));
               }
               setOutputs(typedValue);
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
        
            case PROP_ID_isLeaf:{
               java.lang.Boolean typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toBoolean(value,
                       err-> newTypeConversionError(PROP_NAME_isLeaf));
               }
               setIsLeaf(typedValue);
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
        
            case PROP_ID_remark:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_remark));
               }
               setRemark(typedValue);
               break;
            }
        
           default:
              super.orm_propValue(propId,value);
        }
    }

    @Override
    public void orm_internalSet(int propId, Object value) {
        switch(propId){
        
            case PROP_ID_sid:{
               onInitProp(propId);
               this._sid = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_ruleId:{
               onInitProp(propId);
               this._ruleId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_label:{
               onInitProp(propId);
               this._label = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_sortNo:{
               onInitProp(propId);
               this._sortNo = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_predicate:{
               onInitProp(propId);
               this._predicate = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_outputs:{
               onInitProp(propId);
               this._outputs = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_parentId:{
               onInitProp(propId);
               this._parentId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_isLeaf:{
               onInitProp(propId);
               this._isLeaf = (java.lang.Boolean)value;
               
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
        
            case PROP_ID_remark:{
               onInitProp(propId);
               this._remark = (java.lang.String)value;
               
               break;
            }
        
           default:
              super.orm_internalSet(propId,value);
        }
    }

    
    /**
     * SID: SID
     */
    public java.lang.String getSid(){
         onPropGet(PROP_ID_sid);
         return _sid;
    }

    /**
     * SID: SID
     */
    public void setSid(java.lang.String value){
        if(onPropSet(PROP_ID_sid,value)){
            this._sid = value;
            internalClearRefs(PROP_ID_sid);
            orm_id();
        }
    }
    
    /**
     * 规则ID: RULE_ID
     */
    public java.lang.String getRuleId(){
         onPropGet(PROP_ID_ruleId);
         return _ruleId;
    }

    /**
     * 规则ID: RULE_ID
     */
    public void setRuleId(java.lang.String value){
        if(onPropSet(PROP_ID_ruleId,value)){
            this._ruleId = value;
            internalClearRefs(PROP_ID_ruleId);
            
        }
    }
    
    /**
     * 显示标签: LABEL
     */
    public java.lang.String getLabel(){
         onPropGet(PROP_ID_label);
         return _label;
    }

    /**
     * 显示标签: LABEL
     */
    public void setLabel(java.lang.String value){
        if(onPropSet(PROP_ID_label,value)){
            this._label = value;
            internalClearRefs(PROP_ID_label);
            
        }
    }
    
    /**
     * 排序序号: SORT_NO
     */
    public java.lang.Integer getSortNo(){
         onPropGet(PROP_ID_sortNo);
         return _sortNo;
    }

    /**
     * 排序序号: SORT_NO
     */
    public void setSortNo(java.lang.Integer value){
        if(onPropSet(PROP_ID_sortNo,value)){
            this._sortNo = value;
            internalClearRefs(PROP_ID_sortNo);
            
        }
    }
    
    /**
     * 判断条件: PREDICATE
     */
    public java.lang.String getPredicate(){
         onPropGet(PROP_ID_predicate);
         return _predicate;
    }

    /**
     * 判断条件: PREDICATE
     */
    public void setPredicate(java.lang.String value){
        if(onPropSet(PROP_ID_predicate,value)){
            this._predicate = value;
            internalClearRefs(PROP_ID_predicate);
            
        }
    }
    
    /**
     * 输出结果: OUTPUTS
     */
    public java.lang.String getOutputs(){
         onPropGet(PROP_ID_outputs);
         return _outputs;
    }

    /**
     * 输出结果: OUTPUTS
     */
    public void setOutputs(java.lang.String value){
        if(onPropSet(PROP_ID_outputs,value)){
            this._outputs = value;
            internalClearRefs(PROP_ID_outputs);
            
        }
    }
    
    /**
     * 父ID: PARENT_ID
     */
    public java.lang.String getParentId(){
         onPropGet(PROP_ID_parentId);
         return _parentId;
    }

    /**
     * 父ID: PARENT_ID
     */
    public void setParentId(java.lang.String value){
        if(onPropSet(PROP_ID_parentId,value)){
            this._parentId = value;
            internalClearRefs(PROP_ID_parentId);
            
        }
    }
    
    /**
     * 是否叶子节点: IS_LEAF
     */
    public java.lang.Boolean getIsLeaf(){
         onPropGet(PROP_ID_isLeaf);
         return _isLeaf;
    }

    /**
     * 是否叶子节点: IS_LEAF
     */
    public void setIsLeaf(java.lang.Boolean value){
        if(onPropSet(PROP_ID_isLeaf,value)){
            this._isLeaf = value;
            internalClearRefs(PROP_ID_isLeaf);
            
        }
    }
    
    /**
     * 数据版本: VERSION
     */
    public java.lang.Integer getVersion(){
         onPropGet(PROP_ID_version);
         return _version;
    }

    /**
     * 数据版本: VERSION
     */
    public void setVersion(java.lang.Integer value){
        if(onPropSet(PROP_ID_version,value)){
            this._version = value;
            internalClearRefs(PROP_ID_version);
            
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
     * 修改人: UPDATED_BY
     */
    public java.lang.String getUpdatedBy(){
         onPropGet(PROP_ID_updatedBy);
         return _updatedBy;
    }

    /**
     * 修改人: UPDATED_BY
     */
    public void setUpdatedBy(java.lang.String value){
        if(onPropSet(PROP_ID_updatedBy,value)){
            this._updatedBy = value;
            internalClearRefs(PROP_ID_updatedBy);
            
        }
    }
    
    /**
     * 修改时间: UPDATE_TIME
     */
    public java.sql.Timestamp getUpdateTime(){
         onPropGet(PROP_ID_updateTime);
         return _updateTime;
    }

    /**
     * 修改时间: UPDATE_TIME
     */
    public void setUpdateTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_updateTime,value)){
            this._updateTime = value;
            internalClearRefs(PROP_ID_updateTime);
            
        }
    }
    
    /**
     * 备注: REMARK
     */
    public java.lang.String getRemark(){
         onPropGet(PROP_ID_remark);
         return _remark;
    }

    /**
     * 备注: REMARK
     */
    public void setRemark(java.lang.String value){
        if(onPropSet(PROP_ID_remark,value)){
            this._remark = value;
            internalClearRefs(PROP_ID_remark);
            
        }
    }
    
    /**
     * 父节点
     */
    public io.nop.rule.dao.entity.NopRuleNode getParent(){
       return (io.nop.rule.dao.entity.NopRuleNode)internalGetRefEntity(PROP_NAME_parent);
    }

    public void setParent(io.nop.rule.dao.entity.NopRuleNode refEntity){
       if(refEntity == null){
         
         this.setParentId(null);
         
       }else{
          internalSetRefEntity(PROP_NAME_parent, refEntity,()->{
             
                    this.setParentId(refEntity.getSid());
                 
          });
       }
    }
       
    /**
     * 规则定义
     */
    public io.nop.rule.dao.entity.NopRuleDefinition getRuleDefinition(){
       return (io.nop.rule.dao.entity.NopRuleDefinition)internalGetRefEntity(PROP_NAME_ruleDefinition);
    }

    public void setRuleDefinition(io.nop.rule.dao.entity.NopRuleDefinition refEntity){
       if(refEntity == null){
         
         this.setRuleId(null);
         
       }else{
          internalSetRefEntity(PROP_NAME_ruleDefinition, refEntity,()->{
             
                    this.setRuleId(refEntity.getRuleId());
                 
          });
       }
    }
       
    private final OrmEntitySet<io.nop.rule.dao.entity.NopRuleNode> _children = new OrmEntitySet<>(this, PROP_NAME_children,
        io.nop.rule.dao.entity.NopRuleNode.PROP_NAME_parent, null,io.nop.rule.dao.entity.NopRuleNode.class);

    /**
     * 子节点。 refPropName: parent, keyProp: {rel.keyProp}
     */
    public IOrmEntitySet<io.nop.rule.dao.entity.NopRuleNode> getChildren(){
       return _children;
    }
       
}
// resume CPD analysis - CPD-ON
