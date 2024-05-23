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

import io.nop.rule.dao.entity.NopRuleDefinition;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  规则模型定义: nop_rule_definition
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopRuleDefinition extends DynamicOrmEntity{
    
    /* 主键: RULE_ID VARCHAR */
    public static final String PROP_NAME_ruleId = "ruleId";
    public static final int PROP_ID_ruleId = 1;
    
    /* 规则名称: RULE_NAME VARCHAR */
    public static final String PROP_NAME_ruleName = "ruleName";
    public static final int PROP_ID_ruleName = 2;
    
    /* 规则版本: RULE_VERSION BIGINT */
    public static final String PROP_NAME_ruleVersion = "ruleVersion";
    public static final int PROP_ID_ruleVersion = 3;
    
    /* 显示名称: DISPLAY_NAME VARCHAR */
    public static final String PROP_NAME_displayName = "displayName";
    public static final int PROP_ID_displayName = 4;
    
    /* 规则分组: RULE_GROUP VARCHAR */
    public static final String PROP_NAME_ruleGroup = "ruleGroup";
    public static final int PROP_ID_ruleGroup = 5;
    
    /* 规则类型: RULE_TYPE VARCHAR */
    public static final String PROP_NAME_ruleType = "ruleType";
    public static final int PROP_ID_ruleType = 6;
    
    /* 描述: DESCRIPTION VARCHAR */
    public static final String PROP_NAME_description = "description";
    public static final int PROP_ID_description = 7;
    
    /* 模型文本: MODEL_TEXT VARCHAR */
    public static final String PROP_NAME_modelText = "modelText";
    public static final int PROP_ID_modelText = 8;
    
    /* 状态: STATUS INTEGER */
    public static final String PROP_NAME_status = "status";
    public static final int PROP_ID_status = 9;
    
    /* 数据版本: VERSION INTEGER */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 10;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 11;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 12;
    
    /* 修改人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 13;
    
    /* 修改时间: UPDATE_TIME TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 14;
    
    /* 备注: REMARK VARCHAR */
    public static final String PROP_NAME_remark = "remark";
    public static final int PROP_ID_remark = 15;
    

    private static int _PROP_ID_BOUND = 16;

    
    /* relation: 规则节点 */
    public static final String PROP_NAME_ruleNodes = "ruleNodes";
    
    /* relation: 规则角色 */
    public static final String PROP_NAME_ruleRoles = "ruleRoles";
    
    /* component:  */
    public static final String PROP_NAME_modelTextXmlComponent = "modelTextXmlComponent";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_ruleId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_ruleId};

    private static final String[] PROP_ID_TO_NAME = new String[16];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_ruleId] = PROP_NAME_ruleId;
          PROP_NAME_TO_ID.put(PROP_NAME_ruleId, PROP_ID_ruleId);
      
          PROP_ID_TO_NAME[PROP_ID_ruleName] = PROP_NAME_ruleName;
          PROP_NAME_TO_ID.put(PROP_NAME_ruleName, PROP_ID_ruleName);
      
          PROP_ID_TO_NAME[PROP_ID_ruleVersion] = PROP_NAME_ruleVersion;
          PROP_NAME_TO_ID.put(PROP_NAME_ruleVersion, PROP_ID_ruleVersion);
      
          PROP_ID_TO_NAME[PROP_ID_displayName] = PROP_NAME_displayName;
          PROP_NAME_TO_ID.put(PROP_NAME_displayName, PROP_ID_displayName);
      
          PROP_ID_TO_NAME[PROP_ID_ruleGroup] = PROP_NAME_ruleGroup;
          PROP_NAME_TO_ID.put(PROP_NAME_ruleGroup, PROP_ID_ruleGroup);
      
          PROP_ID_TO_NAME[PROP_ID_ruleType] = PROP_NAME_ruleType;
          PROP_NAME_TO_ID.put(PROP_NAME_ruleType, PROP_ID_ruleType);
      
          PROP_ID_TO_NAME[PROP_ID_description] = PROP_NAME_description;
          PROP_NAME_TO_ID.put(PROP_NAME_description, PROP_ID_description);
      
          PROP_ID_TO_NAME[PROP_ID_modelText] = PROP_NAME_modelText;
          PROP_NAME_TO_ID.put(PROP_NAME_modelText, PROP_ID_modelText);
      
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
      
          PROP_ID_TO_NAME[PROP_ID_remark] = PROP_NAME_remark;
          PROP_NAME_TO_ID.put(PROP_NAME_remark, PROP_ID_remark);
      
    }

    
    /* 主键: RULE_ID */
    private java.lang.String _ruleId;
    
    /* 规则名称: RULE_NAME */
    private java.lang.String _ruleName;
    
    /* 规则版本: RULE_VERSION */
    private java.lang.Long _ruleVersion;
    
    /* 显示名称: DISPLAY_NAME */
    private java.lang.String _displayName;
    
    /* 规则分组: RULE_GROUP */
    private java.lang.String _ruleGroup;
    
    /* 规则类型: RULE_TYPE */
    private java.lang.String _ruleType;
    
    /* 描述: DESCRIPTION */
    private java.lang.String _description;
    
    /* 模型文本: MODEL_TEXT */
    private java.lang.String _modelText;
    
    /* 状态: STATUS */
    private java.lang.Integer _status;
    
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
    

    public _NopRuleDefinition(){
        // for debug
    }

    protected NopRuleDefinition newInstance(){
       return new NopRuleDefinition();
    }

    @Override
    public NopRuleDefinition cloneInstance() {
        NopRuleDefinition entity = newInstance();
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
      return "io.nop.rule.dao.entity.NopRuleDefinition";
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
    
        return buildSimpleId(PROP_ID_ruleId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_ruleId;
          
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
        
            case PROP_ID_ruleId:
               return getRuleId();
        
            case PROP_ID_ruleName:
               return getRuleName();
        
            case PROP_ID_ruleVersion:
               return getRuleVersion();
        
            case PROP_ID_displayName:
               return getDisplayName();
        
            case PROP_ID_ruleGroup:
               return getRuleGroup();
        
            case PROP_ID_ruleType:
               return getRuleType();
        
            case PROP_ID_description:
               return getDescription();
        
            case PROP_ID_modelText:
               return getModelText();
        
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
        
            case PROP_ID_remark:
               return getRemark();
        
           default:
              return super.orm_propValue(propId);
        }
    }

    

    @Override
    public void orm_propValue(int propId, Object value){
        switch(propId){
        
            case PROP_ID_ruleId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_ruleId));
               }
               setRuleId(typedValue);
               break;
            }
        
            case PROP_ID_ruleName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_ruleName));
               }
               setRuleName(typedValue);
               break;
            }
        
            case PROP_ID_ruleVersion:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_ruleVersion));
               }
               setRuleVersion(typedValue);
               break;
            }
        
            case PROP_ID_displayName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_displayName));
               }
               setDisplayName(typedValue);
               break;
            }
        
            case PROP_ID_ruleGroup:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_ruleGroup));
               }
               setRuleGroup(typedValue);
               break;
            }
        
            case PROP_ID_ruleType:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_ruleType));
               }
               setRuleType(typedValue);
               break;
            }
        
            case PROP_ID_description:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_description));
               }
               setDescription(typedValue);
               break;
            }
        
            case PROP_ID_modelText:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_modelText));
               }
               setModelText(typedValue);
               break;
            }
        
            case PROP_ID_status:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
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
        
            case PROP_ID_ruleId:{
               onInitProp(propId);
               this._ruleId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_ruleName:{
               onInitProp(propId);
               this._ruleName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_ruleVersion:{
               onInitProp(propId);
               this._ruleVersion = (java.lang.Long)value;
               
               break;
            }
        
            case PROP_ID_displayName:{
               onInitProp(propId);
               this._displayName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_ruleGroup:{
               onInitProp(propId);
               this._ruleGroup = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_ruleType:{
               onInitProp(propId);
               this._ruleType = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_description:{
               onInitProp(propId);
               this._description = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_modelText:{
               onInitProp(propId);
               this._modelText = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_status:{
               onInitProp(propId);
               this._status = (java.lang.Integer)value;
               
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
     * 主键: RULE_ID
     */
    public java.lang.String getRuleId(){
         onPropGet(PROP_ID_ruleId);
         return _ruleId;
    }

    /**
     * 主键: RULE_ID
     */
    public void setRuleId(java.lang.String value){
        if(onPropSet(PROP_ID_ruleId,value)){
            this._ruleId = value;
            internalClearRefs(PROP_ID_ruleId);
            orm_id();
        }
    }
    
    /**
     * 规则名称: RULE_NAME
     */
    public java.lang.String getRuleName(){
         onPropGet(PROP_ID_ruleName);
         return _ruleName;
    }

    /**
     * 规则名称: RULE_NAME
     */
    public void setRuleName(java.lang.String value){
        if(onPropSet(PROP_ID_ruleName,value)){
            this._ruleName = value;
            internalClearRefs(PROP_ID_ruleName);
            
        }
    }
    
    /**
     * 规则版本: RULE_VERSION
     */
    public java.lang.Long getRuleVersion(){
         onPropGet(PROP_ID_ruleVersion);
         return _ruleVersion;
    }

    /**
     * 规则版本: RULE_VERSION
     */
    public void setRuleVersion(java.lang.Long value){
        if(onPropSet(PROP_ID_ruleVersion,value)){
            this._ruleVersion = value;
            internalClearRefs(PROP_ID_ruleVersion);
            
        }
    }
    
    /**
     * 显示名称: DISPLAY_NAME
     */
    public java.lang.String getDisplayName(){
         onPropGet(PROP_ID_displayName);
         return _displayName;
    }

    /**
     * 显示名称: DISPLAY_NAME
     */
    public void setDisplayName(java.lang.String value){
        if(onPropSet(PROP_ID_displayName,value)){
            this._displayName = value;
            internalClearRefs(PROP_ID_displayName);
            
        }
    }
    
    /**
     * 规则分组: RULE_GROUP
     */
    public java.lang.String getRuleGroup(){
         onPropGet(PROP_ID_ruleGroup);
         return _ruleGroup;
    }

    /**
     * 规则分组: RULE_GROUP
     */
    public void setRuleGroup(java.lang.String value){
        if(onPropSet(PROP_ID_ruleGroup,value)){
            this._ruleGroup = value;
            internalClearRefs(PROP_ID_ruleGroup);
            
        }
    }
    
    /**
     * 规则类型: RULE_TYPE
     */
    public java.lang.String getRuleType(){
         onPropGet(PROP_ID_ruleType);
         return _ruleType;
    }

    /**
     * 规则类型: RULE_TYPE
     */
    public void setRuleType(java.lang.String value){
        if(onPropSet(PROP_ID_ruleType,value)){
            this._ruleType = value;
            internalClearRefs(PROP_ID_ruleType);
            
        }
    }
    
    /**
     * 描述: DESCRIPTION
     */
    public java.lang.String getDescription(){
         onPropGet(PROP_ID_description);
         return _description;
    }

    /**
     * 描述: DESCRIPTION
     */
    public void setDescription(java.lang.String value){
        if(onPropSet(PROP_ID_description,value)){
            this._description = value;
            internalClearRefs(PROP_ID_description);
            
        }
    }
    
    /**
     * 模型文本: MODEL_TEXT
     */
    public java.lang.String getModelText(){
         onPropGet(PROP_ID_modelText);
         return _modelText;
    }

    /**
     * 模型文本: MODEL_TEXT
     */
    public void setModelText(java.lang.String value){
        if(onPropSet(PROP_ID_modelText,value)){
            this._modelText = value;
            internalClearRefs(PROP_ID_modelText);
            
        }
    }
    
    /**
     * 状态: STATUS
     */
    public java.lang.Integer getStatus(){
         onPropGet(PROP_ID_status);
         return _status;
    }

    /**
     * 状态: STATUS
     */
    public void setStatus(java.lang.Integer value){
        if(onPropSet(PROP_ID_status,value)){
            this._status = value;
            internalClearRefs(PROP_ID_status);
            
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
    
    private final OrmEntitySet<io.nop.rule.dao.entity.NopRuleNode> _ruleNodes = new OrmEntitySet<>(this, PROP_NAME_ruleNodes,
        io.nop.rule.dao.entity.NopRuleNode.PROP_NAME_ruleDefinition, null,io.nop.rule.dao.entity.NopRuleNode.class);

    /**
     * 规则节点。 refPropName: ruleDefinition, keyProp: {rel.keyProp}
     */
    public IOrmEntitySet<io.nop.rule.dao.entity.NopRuleNode> getRuleNodes(){
       return _ruleNodes;
    }
       
    private final OrmEntitySet<io.nop.rule.dao.entity.NopRuleRole> _ruleRoles = new OrmEntitySet<>(this, PROP_NAME_ruleRoles,
        io.nop.rule.dao.entity.NopRuleRole.PROP_NAME_ruleDefinition, null,io.nop.rule.dao.entity.NopRuleRole.class);

    /**
     * 规则角色。 refPropName: ruleDefinition, keyProp: {rel.keyProp}
     */
    public IOrmEntitySet<io.nop.rule.dao.entity.NopRuleRole> getRuleRoles(){
       return _ruleRoles;
    }
       
   private io.nop.orm.component.XmlOrmComponent _modelTextXmlComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_modelTextXmlComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_modelTextXmlComponent.put(io.nop.orm.component.XmlOrmComponent.PROP_NAME_xmlText,PROP_ID_modelText);
      
   }

   public io.nop.orm.component.XmlOrmComponent getModelTextXmlComponent(){
      if(_modelTextXmlComponent == null){
          _modelTextXmlComponent = new io.nop.orm.component.XmlOrmComponent();
          _modelTextXmlComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_modelTextXmlComponent);
      }
      return _modelTextXmlComponent;
   }

}
// resume CPD analysis - CPD-ON
