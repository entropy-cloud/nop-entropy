package io.nop.metadata.dao.entity._gen;

import io.nop.orm.model.IEntityModel;
import io.nop.orm.support.DynamicOrmEntity;
import io.nop.orm.support.OrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.orm.IOrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code

import io.nop.api.core.convert.ConvertHelper;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;

import io.nop.metadata.dao.entity.NopMetaGlossary;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  词汇表: nop_meta_glossary
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopMetaGlossary extends DynamicOrmEntity{
    
    /* 词汇表ID: GLOSSARY_ID VARCHAR */
    public static final String PROP_NAME_glossaryId = "glossaryId";
    public static final int PROP_ID_glossaryId = 1;
    
    /* 词汇表名: NAME VARCHAR */
    public static final String PROP_NAME_name = "name";
    public static final int PROP_ID_name = 2;
    
    /* 显示名: DISPLAY_NAME VARCHAR */
    public static final String PROP_NAME_displayName = "displayName";
    public static final int PROP_ID_displayName = 3;
    
    /* 描述: DESCRIPTION VARCHAR */
    public static final String PROP_NAME_description = "description";
    public static final int PROP_ID_description = 4;
    
    /* 负责人: OWNER VARCHAR */
    public static final String PROP_NAME_owner = "owner";
    public static final int PROP_ID_owner = 5;
    
    /* 审核人列表: REVIEWERS VARCHAR */
    public static final String PROP_NAME_reviewers = "reviewers";
    public static final int PROP_ID_reviewers = 6;
    
    /* 是否互斥: MUTUALLY_EXCLUSIVE TINYINT */
    public static final String PROP_NAME_mutuallyExclusive = "mutuallyExclusive";
    public static final int PROP_ID_mutuallyExclusive = 7;
    
    /* 命名空间列表: NAMESPACES VARCHAR */
    public static final String PROP_NAME_namespaces = "namespaces";
    public static final int PROP_ID_namespaces = 8;
    
    /* 扩展配置: EXT_CONFIG VARCHAR */
    public static final String PROP_NAME_extConfig = "extConfig";
    public static final int PROP_ID_extConfig = 9;
    
    /* 数据版本: VERSION BIGINT */
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

    
    /* relation: 术语集 */
    public static final String PROP_NAME_childTerms = "childTerms";
    
    /* component:  */
    public static final String PROP_NAME_reviewersComponent = "reviewersComponent";
    
    /* component:  */
    public static final String PROP_NAME_namespacesComponent = "namespacesComponent";
    
    /* component:  */
    public static final String PROP_NAME_extConfigComponent = "extConfigComponent";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_glossaryId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_glossaryId};

    private static final String[] PROP_ID_TO_NAME = new String[16];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_glossaryId] = PROP_NAME_glossaryId;
          PROP_NAME_TO_ID.put(PROP_NAME_glossaryId, PROP_ID_glossaryId);
      
          PROP_ID_TO_NAME[PROP_ID_name] = PROP_NAME_name;
          PROP_NAME_TO_ID.put(PROP_NAME_name, PROP_ID_name);
      
          PROP_ID_TO_NAME[PROP_ID_displayName] = PROP_NAME_displayName;
          PROP_NAME_TO_ID.put(PROP_NAME_displayName, PROP_ID_displayName);
      
          PROP_ID_TO_NAME[PROP_ID_description] = PROP_NAME_description;
          PROP_NAME_TO_ID.put(PROP_NAME_description, PROP_ID_description);
      
          PROP_ID_TO_NAME[PROP_ID_owner] = PROP_NAME_owner;
          PROP_NAME_TO_ID.put(PROP_NAME_owner, PROP_ID_owner);
      
          PROP_ID_TO_NAME[PROP_ID_reviewers] = PROP_NAME_reviewers;
          PROP_NAME_TO_ID.put(PROP_NAME_reviewers, PROP_ID_reviewers);
      
          PROP_ID_TO_NAME[PROP_ID_mutuallyExclusive] = PROP_NAME_mutuallyExclusive;
          PROP_NAME_TO_ID.put(PROP_NAME_mutuallyExclusive, PROP_ID_mutuallyExclusive);
      
          PROP_ID_TO_NAME[PROP_ID_namespaces] = PROP_NAME_namespaces;
          PROP_NAME_TO_ID.put(PROP_NAME_namespaces, PROP_ID_namespaces);
      
          PROP_ID_TO_NAME[PROP_ID_extConfig] = PROP_NAME_extConfig;
          PROP_NAME_TO_ID.put(PROP_NAME_extConfig, PROP_ID_extConfig);
      
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

    
    /* 词汇表ID: GLOSSARY_ID */
    private java.lang.String _glossaryId;
    
    /* 词汇表名: NAME */
    private java.lang.String _name;
    
    /* 显示名: DISPLAY_NAME */
    private java.lang.String _displayName;
    
    /* 描述: DESCRIPTION */
    private java.lang.String _description;
    
    /* 负责人: OWNER */
    private java.lang.String _owner;
    
    /* 审核人列表: REVIEWERS */
    private java.lang.String _reviewers;
    
    /* 是否互斥: MUTUALLY_EXCLUSIVE */
    private java.lang.Byte _mutuallyExclusive;
    
    /* 命名空间列表: NAMESPACES */
    private java.lang.String _namespaces;
    
    /* 扩展配置: EXT_CONFIG */
    private java.lang.String _extConfig;
    
    /* 数据版本: VERSION */
    private java.lang.Long _version;
    
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
    

    public _NopMetaGlossary(){
        // for debug
    }

    protected NopMetaGlossary newInstance(){
        NopMetaGlossary entity = new NopMetaGlossary();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopMetaGlossary cloneInstance() {
        NopMetaGlossary entity = newInstance();
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
      return "io.nop.metadata.dao.entity.NopMetaGlossary";
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
    
        return buildSimpleId(PROP_ID_glossaryId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_glossaryId;
          
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
        
            case PROP_ID_glossaryId:
               return getGlossaryId();
        
            case PROP_ID_name:
               return getName();
        
            case PROP_ID_displayName:
               return getDisplayName();
        
            case PROP_ID_description:
               return getDescription();
        
            case PROP_ID_owner:
               return getOwner();
        
            case PROP_ID_reviewers:
               return getReviewers();
        
            case PROP_ID_mutuallyExclusive:
               return getMutuallyExclusive();
        
            case PROP_ID_namespaces:
               return getNamespaces();
        
            case PROP_ID_extConfig:
               return getExtConfig();
        
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
        
            case PROP_ID_glossaryId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_glossaryId));
               }
               setGlossaryId(typedValue);
               break;
            }
        
            case PROP_ID_name:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_name));
               }
               setName(typedValue);
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
        
            case PROP_ID_description:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_description));
               }
               setDescription(typedValue);
               break;
            }
        
            case PROP_ID_owner:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_owner));
               }
               setOwner(typedValue);
               break;
            }
        
            case PROP_ID_reviewers:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_reviewers));
               }
               setReviewers(typedValue);
               break;
            }
        
            case PROP_ID_mutuallyExclusive:{
               java.lang.Byte typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toByte(value,
                       err-> newTypeConversionError(PROP_NAME_mutuallyExclusive));
               }
               setMutuallyExclusive(typedValue);
               break;
            }
        
            case PROP_ID_namespaces:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_namespaces));
               }
               setNamespaces(typedValue);
               break;
            }
        
            case PROP_ID_extConfig:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_extConfig));
               }
               setExtConfig(typedValue);
               break;
            }
        
            case PROP_ID_version:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
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
        
            case PROP_ID_glossaryId:{
               onInitProp(propId);
               this._glossaryId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_name:{
               onInitProp(propId);
               this._name = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_displayName:{
               onInitProp(propId);
               this._displayName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_description:{
               onInitProp(propId);
               this._description = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_owner:{
               onInitProp(propId);
               this._owner = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_reviewers:{
               onInitProp(propId);
               this._reviewers = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_mutuallyExclusive:{
               onInitProp(propId);
               this._mutuallyExclusive = (java.lang.Byte)value;
               
               break;
            }
        
            case PROP_ID_namespaces:{
               onInitProp(propId);
               this._namespaces = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_extConfig:{
               onInitProp(propId);
               this._extConfig = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_version:{
               onInitProp(propId);
               this._version = (java.lang.Long)value;
               
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
     * 词汇表ID: GLOSSARY_ID
     */
    public final java.lang.String getGlossaryId(){
         onPropGet(PROP_ID_glossaryId);
         return _glossaryId;
    }

    /**
     * 词汇表ID: GLOSSARY_ID
     */
    public final void setGlossaryId(java.lang.String value){
        if(onPropSet(PROP_ID_glossaryId,value)){
            this._glossaryId = value;
            internalClearRefs(PROP_ID_glossaryId);
            orm_id();
        }
    }
    
    /**
     * 词汇表名: NAME
     */
    public final java.lang.String getName(){
         onPropGet(PROP_ID_name);
         return _name;
    }

    /**
     * 词汇表名: NAME
     */
    public final void setName(java.lang.String value){
        if(onPropSet(PROP_ID_name,value)){
            this._name = value;
            internalClearRefs(PROP_ID_name);
            
        }
    }
    
    /**
     * 显示名: DISPLAY_NAME
     */
    public final java.lang.String getDisplayName(){
         onPropGet(PROP_ID_displayName);
         return _displayName;
    }

    /**
     * 显示名: DISPLAY_NAME
     */
    public final void setDisplayName(java.lang.String value){
        if(onPropSet(PROP_ID_displayName,value)){
            this._displayName = value;
            internalClearRefs(PROP_ID_displayName);
            
        }
    }
    
    /**
     * 描述: DESCRIPTION
     */
    public final java.lang.String getDescription(){
         onPropGet(PROP_ID_description);
         return _description;
    }

    /**
     * 描述: DESCRIPTION
     */
    public final void setDescription(java.lang.String value){
        if(onPropSet(PROP_ID_description,value)){
            this._description = value;
            internalClearRefs(PROP_ID_description);
            
        }
    }
    
    /**
     * 负责人: OWNER
     */
    public final java.lang.String getOwner(){
         onPropGet(PROP_ID_owner);
         return _owner;
    }

    /**
     * 负责人: OWNER
     */
    public final void setOwner(java.lang.String value){
        if(onPropSet(PROP_ID_owner,value)){
            this._owner = value;
            internalClearRefs(PROP_ID_owner);
            
        }
    }
    
    /**
     * 审核人列表: REVIEWERS
     */
    public final java.lang.String getReviewers(){
         onPropGet(PROP_ID_reviewers);
         return _reviewers;
    }

    /**
     * 审核人列表: REVIEWERS
     */
    public final void setReviewers(java.lang.String value){
        if(onPropSet(PROP_ID_reviewers,value)){
            this._reviewers = value;
            internalClearRefs(PROP_ID_reviewers);
            
        }
    }
    
    /**
     * 是否互斥: MUTUALLY_EXCLUSIVE
     */
    public final java.lang.Byte getMutuallyExclusive(){
         onPropGet(PROP_ID_mutuallyExclusive);
         return _mutuallyExclusive;
    }

    /**
     * 是否互斥: MUTUALLY_EXCLUSIVE
     */
    public final void setMutuallyExclusive(java.lang.Byte value){
        if(onPropSet(PROP_ID_mutuallyExclusive,value)){
            this._mutuallyExclusive = value;
            internalClearRefs(PROP_ID_mutuallyExclusive);
            
        }
    }
    
    /**
     * 命名空间列表: NAMESPACES
     */
    public final java.lang.String getNamespaces(){
         onPropGet(PROP_ID_namespaces);
         return _namespaces;
    }

    /**
     * 命名空间列表: NAMESPACES
     */
    public final void setNamespaces(java.lang.String value){
        if(onPropSet(PROP_ID_namespaces,value)){
            this._namespaces = value;
            internalClearRefs(PROP_ID_namespaces);
            
        }
    }
    
    /**
     * 扩展配置: EXT_CONFIG
     */
    public final java.lang.String getExtConfig(){
         onPropGet(PROP_ID_extConfig);
         return _extConfig;
    }

    /**
     * 扩展配置: EXT_CONFIG
     */
    public final void setExtConfig(java.lang.String value){
        if(onPropSet(PROP_ID_extConfig,value)){
            this._extConfig = value;
            internalClearRefs(PROP_ID_extConfig);
            
        }
    }
    
    /**
     * 数据版本: VERSION
     */
    public final java.lang.Long getVersion(){
         onPropGet(PROP_ID_version);
         return _version;
    }

    /**
     * 数据版本: VERSION
     */
    public final void setVersion(java.lang.Long value){
        if(onPropSet(PROP_ID_version,value)){
            this._version = value;
            internalClearRefs(PROP_ID_version);
            
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
     * 创建时间: CREATE_TIME
     */
    public final java.sql.Timestamp getCreateTime(){
         onPropGet(PROP_ID_createTime);
         return _createTime;
    }

    /**
     * 创建时间: CREATE_TIME
     */
    public final void setCreateTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_createTime,value)){
            this._createTime = value;
            internalClearRefs(PROP_ID_createTime);
            
        }
    }
    
    /**
     * 修改人: UPDATED_BY
     */
    public final java.lang.String getUpdatedBy(){
         onPropGet(PROP_ID_updatedBy);
         return _updatedBy;
    }

    /**
     * 修改人: UPDATED_BY
     */
    public final void setUpdatedBy(java.lang.String value){
        if(onPropSet(PROP_ID_updatedBy,value)){
            this._updatedBy = value;
            internalClearRefs(PROP_ID_updatedBy);
            
        }
    }
    
    /**
     * 修改时间: UPDATE_TIME
     */
    public final java.sql.Timestamp getUpdateTime(){
         onPropGet(PROP_ID_updateTime);
         return _updateTime;
    }

    /**
     * 修改时间: UPDATE_TIME
     */
    public final void setUpdateTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_updateTime,value)){
            this._updateTime = value;
            internalClearRefs(PROP_ID_updateTime);
            
        }
    }
    
    /**
     * 备注: REMARK
     */
    public final java.lang.String getRemark(){
         onPropGet(PROP_ID_remark);
         return _remark;
    }

    /**
     * 备注: REMARK
     */
    public final void setRemark(java.lang.String value){
        if(onPropSet(PROP_ID_remark,value)){
            this._remark = value;
            internalClearRefs(PROP_ID_remark);
            
        }
    }
    
    private final OrmEntitySet<io.nop.metadata.dao.entity.NopMetaGlossaryTerm> _childTerms = new OrmEntitySet<>(this, PROP_NAME_childTerms,
        io.nop.metadata.dao.entity.NopMetaGlossaryTerm.PROP_NAME_glossary, null,io.nop.metadata.dao.entity.NopMetaGlossaryTerm.class);

    /**
     * 术语集。 refPropName: glossary, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<io.nop.metadata.dao.entity.NopMetaGlossaryTerm> getChildTerms(){
       return _childTerms;
    }
       
   private io.nop.orm.component.JsonOrmComponent _reviewersComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_reviewersComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_reviewersComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_reviewers);
      
   }

   public final io.nop.orm.component.JsonOrmComponent getReviewersComponent(){
      if(_reviewersComponent == null){
          _reviewersComponent = new io.nop.orm.component.JsonOrmComponent();
          _reviewersComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_reviewersComponent);
      }
      return _reviewersComponent;
   }

   private io.nop.orm.component.JsonOrmComponent _namespacesComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_namespacesComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_namespacesComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_namespaces);
      
   }

   public final io.nop.orm.component.JsonOrmComponent getNamespacesComponent(){
      if(_namespacesComponent == null){
          _namespacesComponent = new io.nop.orm.component.JsonOrmComponent();
          _namespacesComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_namespacesComponent);
      }
      return _namespacesComponent;
   }

   private io.nop.orm.component.JsonOrmComponent _extConfigComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_extConfigComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_extConfigComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_extConfig);
      
   }

   public final io.nop.orm.component.JsonOrmComponent getExtConfigComponent(){
      if(_extConfigComponent == null){
          _extConfigComponent = new io.nop.orm.component.JsonOrmComponent();
          _extConfigComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_extConfigComponent);
      }
      return _extConfigComponent;
   }

}
// resume CPD analysis - CPD-ON
