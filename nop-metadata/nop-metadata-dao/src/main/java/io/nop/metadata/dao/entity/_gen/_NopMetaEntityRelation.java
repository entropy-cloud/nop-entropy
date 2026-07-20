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

import io.nop.metadata.dao.entity.NopMetaEntityRelation;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  实体关系: nop_meta_entity_relation
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopMetaEntityRelation extends DynamicOrmEntity{
    
    /* 关系ID: RELATION_ID VARCHAR */
    public static final String PROP_NAME_relationId = "relationId";
    public static final int PROP_ID_relationId = 1;
    
    /* 实体ID: META_ENTITY_ID VARCHAR */
    public static final String PROP_NAME_metaEntityId = "metaEntityId";
    public static final int PROP_ID_metaEntityId = 2;
    
    /* 是否Delta: IS_DELTA TINYINT */
    public static final String PROP_NAME_isDelta = "isDelta";
    public static final int PROP_ID_isDelta = 3;
    
    /* 关系名: RELATION_NAME VARCHAR */
    public static final String PROP_NAME_relationName = "relationName";
    public static final int PROP_ID_relationName = 4;
    
    /* 关系类型: RELATION_TYPE VARCHAR */
    public static final String PROP_NAME_relationType = "relationType";
    public static final int PROP_ID_relationType = 5;
    
    /* 引用实体名: REF_ENTITY_NAME VARCHAR */
    public static final String PROP_NAME_refEntityName = "refEntityName";
    public static final int PROP_ID_refEntityName = 6;
    
    /* 引用属性名: REF_PROP_NAME VARCHAR */
    public static final String PROP_NAME_refPropName = "refPropName";
    public static final int PROP_ID_refPropName = 7;
    
    /* 级联删除: CASCADE_DELETE TINYINT */
    public static final String PROP_NAME_cascadeDelete = "cascadeDelete";
    public static final int PROP_ID_cascadeDelete = 8;
    
    /* 自动级联删除: AUTO_CASCADE_DELETE TINYINT */
    public static final String PROP_NAME_autoCascadeDelete = "autoCascadeDelete";
    public static final int PROP_ID_autoCascadeDelete = 9;
    
    /* 可查询: QUERYABLE TINYINT */
    public static final String PROP_NAME_queryable = "queryable";
    public static final int PROP_ID_queryable = 10;
    
    /* 内嵌: EMBEDDED TINYINT */
    public static final String PROP_NAME_embedded = "embedded";
    public static final int PROP_ID_embedded = 11;
    
    /* 不生成代码: NOT_GEN_CODE TINYINT */
    public static final String PROP_NAME_notGenCode = "notGenCode";
    public static final int PROP_ID_notGenCode = 12;
    
    /* 标签集: TAG_SET VARCHAR */
    public static final String PROP_NAME_tagSet = "tagSet";
    public static final int PROP_ID_tagSet = 13;
    
    /* 关联条件: JOIN_CONDITIONS VARCHAR */
    public static final String PROP_NAME_joinConditions = "joinConditions";
    public static final int PROP_ID_joinConditions = 14;
    
    /* 数据版本: VERSION BIGINT */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 15;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 16;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 17;
    
    /* 修改人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 18;
    
    /* 修改时间: UPDATE_TIME TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 19;
    
    /* 备注: REMARK VARCHAR */
    public static final String PROP_NAME_remark = "remark";
    public static final int PROP_ID_remark = 20;
    

    private static int _PROP_ID_BOUND = 21;

    
    /* relation: 元数据实体 */
    public static final String PROP_NAME_metaEntity = "metaEntity";
    
    /* component:  */
    public static final String PROP_NAME_joinConditionsComponent = "joinConditionsComponent";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_relationId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_relationId};

    private static final String[] PROP_ID_TO_NAME = new String[21];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_relationId] = PROP_NAME_relationId;
          PROP_NAME_TO_ID.put(PROP_NAME_relationId, PROP_ID_relationId);
      
          PROP_ID_TO_NAME[PROP_ID_metaEntityId] = PROP_NAME_metaEntityId;
          PROP_NAME_TO_ID.put(PROP_NAME_metaEntityId, PROP_ID_metaEntityId);
      
          PROP_ID_TO_NAME[PROP_ID_isDelta] = PROP_NAME_isDelta;
          PROP_NAME_TO_ID.put(PROP_NAME_isDelta, PROP_ID_isDelta);
      
          PROP_ID_TO_NAME[PROP_ID_relationName] = PROP_NAME_relationName;
          PROP_NAME_TO_ID.put(PROP_NAME_relationName, PROP_ID_relationName);
      
          PROP_ID_TO_NAME[PROP_ID_relationType] = PROP_NAME_relationType;
          PROP_NAME_TO_ID.put(PROP_NAME_relationType, PROP_ID_relationType);
      
          PROP_ID_TO_NAME[PROP_ID_refEntityName] = PROP_NAME_refEntityName;
          PROP_NAME_TO_ID.put(PROP_NAME_refEntityName, PROP_ID_refEntityName);
      
          PROP_ID_TO_NAME[PROP_ID_refPropName] = PROP_NAME_refPropName;
          PROP_NAME_TO_ID.put(PROP_NAME_refPropName, PROP_ID_refPropName);
      
          PROP_ID_TO_NAME[PROP_ID_cascadeDelete] = PROP_NAME_cascadeDelete;
          PROP_NAME_TO_ID.put(PROP_NAME_cascadeDelete, PROP_ID_cascadeDelete);
      
          PROP_ID_TO_NAME[PROP_ID_autoCascadeDelete] = PROP_NAME_autoCascadeDelete;
          PROP_NAME_TO_ID.put(PROP_NAME_autoCascadeDelete, PROP_ID_autoCascadeDelete);
      
          PROP_ID_TO_NAME[PROP_ID_queryable] = PROP_NAME_queryable;
          PROP_NAME_TO_ID.put(PROP_NAME_queryable, PROP_ID_queryable);
      
          PROP_ID_TO_NAME[PROP_ID_embedded] = PROP_NAME_embedded;
          PROP_NAME_TO_ID.put(PROP_NAME_embedded, PROP_ID_embedded);
      
          PROP_ID_TO_NAME[PROP_ID_notGenCode] = PROP_NAME_notGenCode;
          PROP_NAME_TO_ID.put(PROP_NAME_notGenCode, PROP_ID_notGenCode);
      
          PROP_ID_TO_NAME[PROP_ID_tagSet] = PROP_NAME_tagSet;
          PROP_NAME_TO_ID.put(PROP_NAME_tagSet, PROP_ID_tagSet);
      
          PROP_ID_TO_NAME[PROP_ID_joinConditions] = PROP_NAME_joinConditions;
          PROP_NAME_TO_ID.put(PROP_NAME_joinConditions, PROP_ID_joinConditions);
      
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

    
    /* 关系ID: RELATION_ID */
    private java.lang.String _relationId;
    
    /* 实体ID: META_ENTITY_ID */
    private java.lang.String _metaEntityId;
    
    /* 是否Delta: IS_DELTA */
    private java.lang.Byte _isDelta;
    
    /* 关系名: RELATION_NAME */
    private java.lang.String _relationName;
    
    /* 关系类型: RELATION_TYPE */
    private java.lang.String _relationType;
    
    /* 引用实体名: REF_ENTITY_NAME */
    private java.lang.String _refEntityName;
    
    /* 引用属性名: REF_PROP_NAME */
    private java.lang.String _refPropName;
    
    /* 级联删除: CASCADE_DELETE */
    private java.lang.Byte _cascadeDelete;
    
    /* 自动级联删除: AUTO_CASCADE_DELETE */
    private java.lang.Byte _autoCascadeDelete;
    
    /* 可查询: QUERYABLE */
    private java.lang.Byte _queryable;
    
    /* 内嵌: EMBEDDED */
    private java.lang.Byte _embedded;
    
    /* 不生成代码: NOT_GEN_CODE */
    private java.lang.Byte _notGenCode;
    
    /* 标签集: TAG_SET */
    private java.lang.String _tagSet;
    
    /* 关联条件: JOIN_CONDITIONS */
    private java.lang.String _joinConditions;
    
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
    

    public _NopMetaEntityRelation(){
        // for debug
    }

    protected NopMetaEntityRelation newInstance(){
        NopMetaEntityRelation entity = new NopMetaEntityRelation();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopMetaEntityRelation cloneInstance() {
        NopMetaEntityRelation entity = newInstance();
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
      return "io.nop.metadata.dao.entity.NopMetaEntityRelation";
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
    
        return buildSimpleId(PROP_ID_relationId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_relationId;
          
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
        
            case PROP_ID_relationId:
               return getRelationId();
        
            case PROP_ID_metaEntityId:
               return getMetaEntityId();
        
            case PROP_ID_isDelta:
               return getIsDelta();
        
            case PROP_ID_relationName:
               return getRelationName();
        
            case PROP_ID_relationType:
               return getRelationType();
        
            case PROP_ID_refEntityName:
               return getRefEntityName();
        
            case PROP_ID_refPropName:
               return getRefPropName();
        
            case PROP_ID_cascadeDelete:
               return getCascadeDelete();
        
            case PROP_ID_autoCascadeDelete:
               return getAutoCascadeDelete();
        
            case PROP_ID_queryable:
               return getQueryable();
        
            case PROP_ID_embedded:
               return getEmbedded();
        
            case PROP_ID_notGenCode:
               return getNotGenCode();
        
            case PROP_ID_tagSet:
               return getTagSet();
        
            case PROP_ID_joinConditions:
               return getJoinConditions();
        
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
        
            case PROP_ID_relationId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_relationId));
               }
               setRelationId(typedValue);
               break;
            }
        
            case PROP_ID_metaEntityId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_metaEntityId));
               }
               setMetaEntityId(typedValue);
               break;
            }
        
            case PROP_ID_isDelta:{
               java.lang.Byte typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toByte(value,
                       err-> newTypeConversionError(PROP_NAME_isDelta));
               }
               setIsDelta(typedValue);
               break;
            }
        
            case PROP_ID_relationName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_relationName));
               }
               setRelationName(typedValue);
               break;
            }
        
            case PROP_ID_relationType:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_relationType));
               }
               setRelationType(typedValue);
               break;
            }
        
            case PROP_ID_refEntityName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_refEntityName));
               }
               setRefEntityName(typedValue);
               break;
            }
        
            case PROP_ID_refPropName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_refPropName));
               }
               setRefPropName(typedValue);
               break;
            }
        
            case PROP_ID_cascadeDelete:{
               java.lang.Byte typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toByte(value,
                       err-> newTypeConversionError(PROP_NAME_cascadeDelete));
               }
               setCascadeDelete(typedValue);
               break;
            }
        
            case PROP_ID_autoCascadeDelete:{
               java.lang.Byte typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toByte(value,
                       err-> newTypeConversionError(PROP_NAME_autoCascadeDelete));
               }
               setAutoCascadeDelete(typedValue);
               break;
            }
        
            case PROP_ID_queryable:{
               java.lang.Byte typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toByte(value,
                       err-> newTypeConversionError(PROP_NAME_queryable));
               }
               setQueryable(typedValue);
               break;
            }
        
            case PROP_ID_embedded:{
               java.lang.Byte typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toByte(value,
                       err-> newTypeConversionError(PROP_NAME_embedded));
               }
               setEmbedded(typedValue);
               break;
            }
        
            case PROP_ID_notGenCode:{
               java.lang.Byte typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toByte(value,
                       err-> newTypeConversionError(PROP_NAME_notGenCode));
               }
               setNotGenCode(typedValue);
               break;
            }
        
            case PROP_ID_tagSet:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_tagSet));
               }
               setTagSet(typedValue);
               break;
            }
        
            case PROP_ID_joinConditions:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_joinConditions));
               }
               setJoinConditions(typedValue);
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
        
            case PROP_ID_relationId:{
               onInitProp(propId);
               this._relationId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_metaEntityId:{
               onInitProp(propId);
               this._metaEntityId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_isDelta:{
               onInitProp(propId);
               this._isDelta = (java.lang.Byte)value;
               
               break;
            }
        
            case PROP_ID_relationName:{
               onInitProp(propId);
               this._relationName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_relationType:{
               onInitProp(propId);
               this._relationType = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_refEntityName:{
               onInitProp(propId);
               this._refEntityName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_refPropName:{
               onInitProp(propId);
               this._refPropName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_cascadeDelete:{
               onInitProp(propId);
               this._cascadeDelete = (java.lang.Byte)value;
               
               break;
            }
        
            case PROP_ID_autoCascadeDelete:{
               onInitProp(propId);
               this._autoCascadeDelete = (java.lang.Byte)value;
               
               break;
            }
        
            case PROP_ID_queryable:{
               onInitProp(propId);
               this._queryable = (java.lang.Byte)value;
               
               break;
            }
        
            case PROP_ID_embedded:{
               onInitProp(propId);
               this._embedded = (java.lang.Byte)value;
               
               break;
            }
        
            case PROP_ID_notGenCode:{
               onInitProp(propId);
               this._notGenCode = (java.lang.Byte)value;
               
               break;
            }
        
            case PROP_ID_tagSet:{
               onInitProp(propId);
               this._tagSet = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_joinConditions:{
               onInitProp(propId);
               this._joinConditions = (java.lang.String)value;
               
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
     * 关系ID: RELATION_ID
     */
    public final java.lang.String getRelationId(){
         onPropGet(PROP_ID_relationId);
         return _relationId;
    }

    /**
     * 关系ID: RELATION_ID
     */
    public final void setRelationId(java.lang.String value){
        if(onPropSet(PROP_ID_relationId,value)){
            this._relationId = value;
            internalClearRefs(PROP_ID_relationId);
            orm_id();
        }
    }
    
    /**
     * 实体ID: META_ENTITY_ID
     */
    public final java.lang.String getMetaEntityId(){
         onPropGet(PROP_ID_metaEntityId);
         return _metaEntityId;
    }

    /**
     * 实体ID: META_ENTITY_ID
     */
    public final void setMetaEntityId(java.lang.String value){
        if(onPropSet(PROP_ID_metaEntityId,value)){
            this._metaEntityId = value;
            internalClearRefs(PROP_ID_metaEntityId);
            
        }
    }
    
    /**
     * 是否Delta: IS_DELTA
     */
    public final java.lang.Byte getIsDelta(){
         onPropGet(PROP_ID_isDelta);
         return _isDelta;
    }

    /**
     * 是否Delta: IS_DELTA
     */
    public final void setIsDelta(java.lang.Byte value){
        if(onPropSet(PROP_ID_isDelta,value)){
            this._isDelta = value;
            internalClearRefs(PROP_ID_isDelta);
            
        }
    }
    
    /**
     * 关系名: RELATION_NAME
     */
    public final java.lang.String getRelationName(){
         onPropGet(PROP_ID_relationName);
         return _relationName;
    }

    /**
     * 关系名: RELATION_NAME
     */
    public final void setRelationName(java.lang.String value){
        if(onPropSet(PROP_ID_relationName,value)){
            this._relationName = value;
            internalClearRefs(PROP_ID_relationName);
            
        }
    }
    
    /**
     * 关系类型: RELATION_TYPE
     */
    public final java.lang.String getRelationType(){
         onPropGet(PROP_ID_relationType);
         return _relationType;
    }

    /**
     * 关系类型: RELATION_TYPE
     */
    public final void setRelationType(java.lang.String value){
        if(onPropSet(PROP_ID_relationType,value)){
            this._relationType = value;
            internalClearRefs(PROP_ID_relationType);
            
        }
    }
    
    /**
     * 引用实体名: REF_ENTITY_NAME
     */
    public final java.lang.String getRefEntityName(){
         onPropGet(PROP_ID_refEntityName);
         return _refEntityName;
    }

    /**
     * 引用实体名: REF_ENTITY_NAME
     */
    public final void setRefEntityName(java.lang.String value){
        if(onPropSet(PROP_ID_refEntityName,value)){
            this._refEntityName = value;
            internalClearRefs(PROP_ID_refEntityName);
            
        }
    }
    
    /**
     * 引用属性名: REF_PROP_NAME
     */
    public final java.lang.String getRefPropName(){
         onPropGet(PROP_ID_refPropName);
         return _refPropName;
    }

    /**
     * 引用属性名: REF_PROP_NAME
     */
    public final void setRefPropName(java.lang.String value){
        if(onPropSet(PROP_ID_refPropName,value)){
            this._refPropName = value;
            internalClearRefs(PROP_ID_refPropName);
            
        }
    }
    
    /**
     * 级联删除: CASCADE_DELETE
     */
    public final java.lang.Byte getCascadeDelete(){
         onPropGet(PROP_ID_cascadeDelete);
         return _cascadeDelete;
    }

    /**
     * 级联删除: CASCADE_DELETE
     */
    public final void setCascadeDelete(java.lang.Byte value){
        if(onPropSet(PROP_ID_cascadeDelete,value)){
            this._cascadeDelete = value;
            internalClearRefs(PROP_ID_cascadeDelete);
            
        }
    }
    
    /**
     * 自动级联删除: AUTO_CASCADE_DELETE
     */
    public final java.lang.Byte getAutoCascadeDelete(){
         onPropGet(PROP_ID_autoCascadeDelete);
         return _autoCascadeDelete;
    }

    /**
     * 自动级联删除: AUTO_CASCADE_DELETE
     */
    public final void setAutoCascadeDelete(java.lang.Byte value){
        if(onPropSet(PROP_ID_autoCascadeDelete,value)){
            this._autoCascadeDelete = value;
            internalClearRefs(PROP_ID_autoCascadeDelete);
            
        }
    }
    
    /**
     * 可查询: QUERYABLE
     */
    public final java.lang.Byte getQueryable(){
         onPropGet(PROP_ID_queryable);
         return _queryable;
    }

    /**
     * 可查询: QUERYABLE
     */
    public final void setQueryable(java.lang.Byte value){
        if(onPropSet(PROP_ID_queryable,value)){
            this._queryable = value;
            internalClearRefs(PROP_ID_queryable);
            
        }
    }
    
    /**
     * 内嵌: EMBEDDED
     */
    public final java.lang.Byte getEmbedded(){
         onPropGet(PROP_ID_embedded);
         return _embedded;
    }

    /**
     * 内嵌: EMBEDDED
     */
    public final void setEmbedded(java.lang.Byte value){
        if(onPropSet(PROP_ID_embedded,value)){
            this._embedded = value;
            internalClearRefs(PROP_ID_embedded);
            
        }
    }
    
    /**
     * 不生成代码: NOT_GEN_CODE
     */
    public final java.lang.Byte getNotGenCode(){
         onPropGet(PROP_ID_notGenCode);
         return _notGenCode;
    }

    /**
     * 不生成代码: NOT_GEN_CODE
     */
    public final void setNotGenCode(java.lang.Byte value){
        if(onPropSet(PROP_ID_notGenCode,value)){
            this._notGenCode = value;
            internalClearRefs(PROP_ID_notGenCode);
            
        }
    }
    
    /**
     * 标签集: TAG_SET
     */
    public final java.lang.String getTagSet(){
         onPropGet(PROP_ID_tagSet);
         return _tagSet;
    }

    /**
     * 标签集: TAG_SET
     */
    public final void setTagSet(java.lang.String value){
        if(onPropSet(PROP_ID_tagSet,value)){
            this._tagSet = value;
            internalClearRefs(PROP_ID_tagSet);
            
        }
    }
    
    /**
     * 关联条件: JOIN_CONDITIONS
     */
    public final java.lang.String getJoinConditions(){
         onPropGet(PROP_ID_joinConditions);
         return _joinConditions;
    }

    /**
     * 关联条件: JOIN_CONDITIONS
     */
    public final void setJoinConditions(java.lang.String value){
        if(onPropSet(PROP_ID_joinConditions,value)){
            this._joinConditions = value;
            internalClearRefs(PROP_ID_joinConditions);
            
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
    
    /**
     * 元数据实体
     */
    public final io.nop.metadata.dao.entity.NopMetaEntity getMetaEntity(){
       return (io.nop.metadata.dao.entity.NopMetaEntity)internalGetRefEntity(PROP_NAME_metaEntity);
    }

    public final void setMetaEntity(io.nop.metadata.dao.entity.NopMetaEntity refEntity){
   
           if(refEntity == null){
           
                   this.setMetaEntityId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_metaEntity, refEntity,()->{
           
                           this.setMetaEntityId(refEntity.getMetaEntityId());
                       
           });
           }
       
    }
       
   private io.nop.orm.component.JsonOrmComponent _joinConditionsComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_joinConditionsComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_joinConditionsComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_joinConditions);
      
   }

   public final io.nop.orm.component.JsonOrmComponent getJoinConditionsComponent(){
      if(_joinConditionsComponent == null){
          _joinConditionsComponent = new io.nop.orm.component.JsonOrmComponent();
          _joinConditionsComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_joinConditionsComponent);
      }
      return _joinConditionsComponent;
   }

}
// resume CPD analysis - CPD-ON
