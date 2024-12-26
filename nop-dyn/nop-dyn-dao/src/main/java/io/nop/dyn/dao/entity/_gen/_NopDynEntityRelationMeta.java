package io.nop.dyn.dao.entity._gen;

import io.nop.orm.model.IEntityModel;
import io.nop.orm.support.DynamicOrmEntity;
import io.nop.orm.support.OrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.orm.IOrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.api.core.convert.ConvertHelper;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;

import io.nop.dyn.dao.entity.NopDynEntityRelationMeta;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  实体关联属性定义: nop_dyn_entity_relation_meta
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopDynEntityRelationMeta extends DynamicOrmEntity{
    
    /* 关联定义ID: REL_META_ID VARCHAR */
    public static final String PROP_NAME_relMetaId = "relMetaId";
    public static final int PROP_ID_relMetaId = 1;
    
    /* 实体元数据: ENTITY_META_ID VARCHAR */
    public static final String PROP_NAME_entityMetaId = "entityMetaId";
    public static final int PROP_ID_entityMetaId = 2;
    
    /* 关联实体: REF_ENTITY_META_ID VARCHAR */
    public static final String PROP_NAME_refEntityMetaId = "refEntityMetaId";
    public static final int PROP_ID_refEntityMetaId = 3;
    
    /* 关联名: RELATION_NAME VARCHAR */
    public static final String PROP_NAME_relationName = "relationName";
    public static final int PROP_ID_relationName = 4;
    
    /* 关联显示名: RELATION_DISPLAY_NAME VARCHAR */
    public static final String PROP_NAME_relationDisplayName = "relationDisplayName";
    public static final int PROP_ID_relationDisplayName = 5;
    
    /* 关联类型: RELATION_TYPE VARCHAR */
    public static final String PROP_NAME_relationType = "relationType";
    public static final int PROP_ID_relationType = 6;
    
    /* 中间表表名: MIDDLE_TABLE_NAME VARCHAR */
    public static final String PROP_NAME_middleTableName = "middleTableName";
    public static final int PROP_ID_middleTableName = 7;
    
    /* 中间表实体名: MIDDLE_ENTITY_NAME VARCHAR */
    public static final String PROP_NAME_middleEntityName = "middleEntityName";
    public static final int PROP_ID_middleEntityName = 8;
    
    /* 左属性名: LEFT_PROP_NAME VARCHAR */
    public static final String PROP_NAME_leftPropName = "leftPropName";
    public static final int PROP_ID_leftPropName = 9;
    
    /* 右属性名: RIGHT_PROP_NAME VARCHAR */
    public static final String PROP_NAME_rightPropName = "rightPropName";
    public static final int PROP_ID_rightPropName = 10;
    
    /* 集合内唯一标识: REF_SET_KEY_PROP VARCHAR */
    public static final String PROP_NAME_refSetKeyProp = "refSetKeyProp";
    public static final int PROP_ID_refSetKeyProp = 11;
    
    /* 集合排序条件: REF_SET_SORT VARCHAR */
    public static final String PROP_NAME_refSetSort = "refSetSort";
    public static final int PROP_ID_refSetSort = 12;
    
    /* 状态: STATUS INTEGER */
    public static final String PROP_NAME_status = "status";
    public static final int PROP_ID_status = 13;
    
    /* 标签: TAGS_TEXT VARCHAR */
    public static final String PROP_NAME_tagsText = "tagsText";
    public static final int PROP_ID_tagsText = 14;
    
    /* 扩展配置: EXT_CONFIG VARCHAR */
    public static final String PROP_NAME_extConfig = "extConfig";
    public static final int PROP_ID_extConfig = 15;
    
    /* 数据版本: VERSION INTEGER */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 16;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 17;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 18;
    
    /* 修改人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 19;
    
    /* 修改时间: UPDATE_TIME TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 20;
    
    /* 备注: REMARK VARCHAR */
    public static final String PROP_NAME_remark = "remark";
    public static final int PROP_ID_remark = 21;
    

    private static int _PROP_ID_BOUND = 22;

    
    /* relation: 实体元数据 */
    public static final String PROP_NAME_entityMeta = "entityMeta";
    
    /* relation: 关联实体元数据 */
    public static final String PROP_NAME_refEntityMeta = "refEntityMeta";
    
    /* component:  */
    public static final String PROP_NAME_extConfigComponent = "extConfigComponent";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_relMetaId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_relMetaId};

    private static final String[] PROP_ID_TO_NAME = new String[22];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_relMetaId] = PROP_NAME_relMetaId;
          PROP_NAME_TO_ID.put(PROP_NAME_relMetaId, PROP_ID_relMetaId);
      
          PROP_ID_TO_NAME[PROP_ID_entityMetaId] = PROP_NAME_entityMetaId;
          PROP_NAME_TO_ID.put(PROP_NAME_entityMetaId, PROP_ID_entityMetaId);
      
          PROP_ID_TO_NAME[PROP_ID_refEntityMetaId] = PROP_NAME_refEntityMetaId;
          PROP_NAME_TO_ID.put(PROP_NAME_refEntityMetaId, PROP_ID_refEntityMetaId);
      
          PROP_ID_TO_NAME[PROP_ID_relationName] = PROP_NAME_relationName;
          PROP_NAME_TO_ID.put(PROP_NAME_relationName, PROP_ID_relationName);
      
          PROP_ID_TO_NAME[PROP_ID_relationDisplayName] = PROP_NAME_relationDisplayName;
          PROP_NAME_TO_ID.put(PROP_NAME_relationDisplayName, PROP_ID_relationDisplayName);
      
          PROP_ID_TO_NAME[PROP_ID_relationType] = PROP_NAME_relationType;
          PROP_NAME_TO_ID.put(PROP_NAME_relationType, PROP_ID_relationType);
      
          PROP_ID_TO_NAME[PROP_ID_middleTableName] = PROP_NAME_middleTableName;
          PROP_NAME_TO_ID.put(PROP_NAME_middleTableName, PROP_ID_middleTableName);
      
          PROP_ID_TO_NAME[PROP_ID_middleEntityName] = PROP_NAME_middleEntityName;
          PROP_NAME_TO_ID.put(PROP_NAME_middleEntityName, PROP_ID_middleEntityName);
      
          PROP_ID_TO_NAME[PROP_ID_leftPropName] = PROP_NAME_leftPropName;
          PROP_NAME_TO_ID.put(PROP_NAME_leftPropName, PROP_ID_leftPropName);
      
          PROP_ID_TO_NAME[PROP_ID_rightPropName] = PROP_NAME_rightPropName;
          PROP_NAME_TO_ID.put(PROP_NAME_rightPropName, PROP_ID_rightPropName);
      
          PROP_ID_TO_NAME[PROP_ID_refSetKeyProp] = PROP_NAME_refSetKeyProp;
          PROP_NAME_TO_ID.put(PROP_NAME_refSetKeyProp, PROP_ID_refSetKeyProp);
      
          PROP_ID_TO_NAME[PROP_ID_refSetSort] = PROP_NAME_refSetSort;
          PROP_NAME_TO_ID.put(PROP_NAME_refSetSort, PROP_ID_refSetSort);
      
          PROP_ID_TO_NAME[PROP_ID_status] = PROP_NAME_status;
          PROP_NAME_TO_ID.put(PROP_NAME_status, PROP_ID_status);
      
          PROP_ID_TO_NAME[PROP_ID_tagsText] = PROP_NAME_tagsText;
          PROP_NAME_TO_ID.put(PROP_NAME_tagsText, PROP_ID_tagsText);
      
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

    
    /* 关联定义ID: REL_META_ID */
    private java.lang.String _relMetaId;
    
    /* 实体元数据: ENTITY_META_ID */
    private java.lang.String _entityMetaId;
    
    /* 关联实体: REF_ENTITY_META_ID */
    private java.lang.String _refEntityMetaId;
    
    /* 关联名: RELATION_NAME */
    private java.lang.String _relationName;
    
    /* 关联显示名: RELATION_DISPLAY_NAME */
    private java.lang.String _relationDisplayName;
    
    /* 关联类型: RELATION_TYPE */
    private java.lang.String _relationType;
    
    /* 中间表表名: MIDDLE_TABLE_NAME */
    private java.lang.String _middleTableName;
    
    /* 中间表实体名: MIDDLE_ENTITY_NAME */
    private java.lang.String _middleEntityName;
    
    /* 左属性名: LEFT_PROP_NAME */
    private java.lang.String _leftPropName;
    
    /* 右属性名: RIGHT_PROP_NAME */
    private java.lang.String _rightPropName;
    
    /* 集合内唯一标识: REF_SET_KEY_PROP */
    private java.lang.String _refSetKeyProp;
    
    /* 集合排序条件: REF_SET_SORT */
    private java.lang.String _refSetSort;
    
    /* 状态: STATUS */
    private java.lang.Integer _status;
    
    /* 标签: TAGS_TEXT */
    private java.lang.String _tagsText;
    
    /* 扩展配置: EXT_CONFIG */
    private java.lang.String _extConfig;
    
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
    

    public _NopDynEntityRelationMeta(){
        // for debug
    }

    protected NopDynEntityRelationMeta newInstance(){
        NopDynEntityRelationMeta entity = new NopDynEntityRelationMeta();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopDynEntityRelationMeta cloneInstance() {
        NopDynEntityRelationMeta entity = newInstance();
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
      return "io.nop.dyn.dao.entity.NopDynEntityRelationMeta";
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
    
        return buildSimpleId(PROP_ID_relMetaId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_relMetaId;
          
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
        
            case PROP_ID_relMetaId:
               return getRelMetaId();
        
            case PROP_ID_entityMetaId:
               return getEntityMetaId();
        
            case PROP_ID_refEntityMetaId:
               return getRefEntityMetaId();
        
            case PROP_ID_relationName:
               return getRelationName();
        
            case PROP_ID_relationDisplayName:
               return getRelationDisplayName();
        
            case PROP_ID_relationType:
               return getRelationType();
        
            case PROP_ID_middleTableName:
               return getMiddleTableName();
        
            case PROP_ID_middleEntityName:
               return getMiddleEntityName();
        
            case PROP_ID_leftPropName:
               return getLeftPropName();
        
            case PROP_ID_rightPropName:
               return getRightPropName();
        
            case PROP_ID_refSetKeyProp:
               return getRefSetKeyProp();
        
            case PROP_ID_refSetSort:
               return getRefSetSort();
        
            case PROP_ID_status:
               return getStatus();
        
            case PROP_ID_tagsText:
               return getTagsText();
        
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
        
            case PROP_ID_relMetaId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_relMetaId));
               }
               setRelMetaId(typedValue);
               break;
            }
        
            case PROP_ID_entityMetaId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_entityMetaId));
               }
               setEntityMetaId(typedValue);
               break;
            }
        
            case PROP_ID_refEntityMetaId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_refEntityMetaId));
               }
               setRefEntityMetaId(typedValue);
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
        
            case PROP_ID_relationDisplayName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_relationDisplayName));
               }
               setRelationDisplayName(typedValue);
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
        
            case PROP_ID_middleTableName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_middleTableName));
               }
               setMiddleTableName(typedValue);
               break;
            }
        
            case PROP_ID_middleEntityName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_middleEntityName));
               }
               setMiddleEntityName(typedValue);
               break;
            }
        
            case PROP_ID_leftPropName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_leftPropName));
               }
               setLeftPropName(typedValue);
               break;
            }
        
            case PROP_ID_rightPropName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_rightPropName));
               }
               setRightPropName(typedValue);
               break;
            }
        
            case PROP_ID_refSetKeyProp:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_refSetKeyProp));
               }
               setRefSetKeyProp(typedValue);
               break;
            }
        
            case PROP_ID_refSetSort:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_refSetSort));
               }
               setRefSetSort(typedValue);
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
        
            case PROP_ID_tagsText:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_tagsText));
               }
               setTagsText(typedValue);
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
        
            case PROP_ID_relMetaId:{
               onInitProp(propId);
               this._relMetaId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_entityMetaId:{
               onInitProp(propId);
               this._entityMetaId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_refEntityMetaId:{
               onInitProp(propId);
               this._refEntityMetaId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_relationName:{
               onInitProp(propId);
               this._relationName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_relationDisplayName:{
               onInitProp(propId);
               this._relationDisplayName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_relationType:{
               onInitProp(propId);
               this._relationType = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_middleTableName:{
               onInitProp(propId);
               this._middleTableName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_middleEntityName:{
               onInitProp(propId);
               this._middleEntityName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_leftPropName:{
               onInitProp(propId);
               this._leftPropName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_rightPropName:{
               onInitProp(propId);
               this._rightPropName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_refSetKeyProp:{
               onInitProp(propId);
               this._refSetKeyProp = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_refSetSort:{
               onInitProp(propId);
               this._refSetSort = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_status:{
               onInitProp(propId);
               this._status = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_tagsText:{
               onInitProp(propId);
               this._tagsText = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_extConfig:{
               onInitProp(propId);
               this._extConfig = (java.lang.String)value;
               
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
     * 关联定义ID: REL_META_ID
     */
    public final java.lang.String getRelMetaId(){
         onPropGet(PROP_ID_relMetaId);
         return _relMetaId;
    }

    /**
     * 关联定义ID: REL_META_ID
     */
    public final void setRelMetaId(java.lang.String value){
        if(onPropSet(PROP_ID_relMetaId,value)){
            this._relMetaId = value;
            internalClearRefs(PROP_ID_relMetaId);
            orm_id();
        }
    }
    
    /**
     * 实体元数据: ENTITY_META_ID
     */
    public final java.lang.String getEntityMetaId(){
         onPropGet(PROP_ID_entityMetaId);
         return _entityMetaId;
    }

    /**
     * 实体元数据: ENTITY_META_ID
     */
    public final void setEntityMetaId(java.lang.String value){
        if(onPropSet(PROP_ID_entityMetaId,value)){
            this._entityMetaId = value;
            internalClearRefs(PROP_ID_entityMetaId);
            
        }
    }
    
    /**
     * 关联实体: REF_ENTITY_META_ID
     */
    public final java.lang.String getRefEntityMetaId(){
         onPropGet(PROP_ID_refEntityMetaId);
         return _refEntityMetaId;
    }

    /**
     * 关联实体: REF_ENTITY_META_ID
     */
    public final void setRefEntityMetaId(java.lang.String value){
        if(onPropSet(PROP_ID_refEntityMetaId,value)){
            this._refEntityMetaId = value;
            internalClearRefs(PROP_ID_refEntityMetaId);
            
        }
    }
    
    /**
     * 关联名: RELATION_NAME
     */
    public final java.lang.String getRelationName(){
         onPropGet(PROP_ID_relationName);
         return _relationName;
    }

    /**
     * 关联名: RELATION_NAME
     */
    public final void setRelationName(java.lang.String value){
        if(onPropSet(PROP_ID_relationName,value)){
            this._relationName = value;
            internalClearRefs(PROP_ID_relationName);
            
        }
    }
    
    /**
     * 关联显示名: RELATION_DISPLAY_NAME
     */
    public final java.lang.String getRelationDisplayName(){
         onPropGet(PROP_ID_relationDisplayName);
         return _relationDisplayName;
    }

    /**
     * 关联显示名: RELATION_DISPLAY_NAME
     */
    public final void setRelationDisplayName(java.lang.String value){
        if(onPropSet(PROP_ID_relationDisplayName,value)){
            this._relationDisplayName = value;
            internalClearRefs(PROP_ID_relationDisplayName);
            
        }
    }
    
    /**
     * 关联类型: RELATION_TYPE
     */
    public final java.lang.String getRelationType(){
         onPropGet(PROP_ID_relationType);
         return _relationType;
    }

    /**
     * 关联类型: RELATION_TYPE
     */
    public final void setRelationType(java.lang.String value){
        if(onPropSet(PROP_ID_relationType,value)){
            this._relationType = value;
            internalClearRefs(PROP_ID_relationType);
            
        }
    }
    
    /**
     * 中间表表名: MIDDLE_TABLE_NAME
     */
    public final java.lang.String getMiddleTableName(){
         onPropGet(PROP_ID_middleTableName);
         return _middleTableName;
    }

    /**
     * 中间表表名: MIDDLE_TABLE_NAME
     */
    public final void setMiddleTableName(java.lang.String value){
        if(onPropSet(PROP_ID_middleTableName,value)){
            this._middleTableName = value;
            internalClearRefs(PROP_ID_middleTableName);
            
        }
    }
    
    /**
     * 中间表实体名: MIDDLE_ENTITY_NAME
     */
    public final java.lang.String getMiddleEntityName(){
         onPropGet(PROP_ID_middleEntityName);
         return _middleEntityName;
    }

    /**
     * 中间表实体名: MIDDLE_ENTITY_NAME
     */
    public final void setMiddleEntityName(java.lang.String value){
        if(onPropSet(PROP_ID_middleEntityName,value)){
            this._middleEntityName = value;
            internalClearRefs(PROP_ID_middleEntityName);
            
        }
    }
    
    /**
     * 左属性名: LEFT_PROP_NAME
     */
    public final java.lang.String getLeftPropName(){
         onPropGet(PROP_ID_leftPropName);
         return _leftPropName;
    }

    /**
     * 左属性名: LEFT_PROP_NAME
     */
    public final void setLeftPropName(java.lang.String value){
        if(onPropSet(PROP_ID_leftPropName,value)){
            this._leftPropName = value;
            internalClearRefs(PROP_ID_leftPropName);
            
        }
    }
    
    /**
     * 右属性名: RIGHT_PROP_NAME
     */
    public final java.lang.String getRightPropName(){
         onPropGet(PROP_ID_rightPropName);
         return _rightPropName;
    }

    /**
     * 右属性名: RIGHT_PROP_NAME
     */
    public final void setRightPropName(java.lang.String value){
        if(onPropSet(PROP_ID_rightPropName,value)){
            this._rightPropName = value;
            internalClearRefs(PROP_ID_rightPropName);
            
        }
    }
    
    /**
     * 集合内唯一标识: REF_SET_KEY_PROP
     */
    public final java.lang.String getRefSetKeyProp(){
         onPropGet(PROP_ID_refSetKeyProp);
         return _refSetKeyProp;
    }

    /**
     * 集合内唯一标识: REF_SET_KEY_PROP
     */
    public final void setRefSetKeyProp(java.lang.String value){
        if(onPropSet(PROP_ID_refSetKeyProp,value)){
            this._refSetKeyProp = value;
            internalClearRefs(PROP_ID_refSetKeyProp);
            
        }
    }
    
    /**
     * 集合排序条件: REF_SET_SORT
     */
    public final java.lang.String getRefSetSort(){
         onPropGet(PROP_ID_refSetSort);
         return _refSetSort;
    }

    /**
     * 集合排序条件: REF_SET_SORT
     */
    public final void setRefSetSort(java.lang.String value){
        if(onPropSet(PROP_ID_refSetSort,value)){
            this._refSetSort = value;
            internalClearRefs(PROP_ID_refSetSort);
            
        }
    }
    
    /**
     * 状态: STATUS
     */
    public final java.lang.Integer getStatus(){
         onPropGet(PROP_ID_status);
         return _status;
    }

    /**
     * 状态: STATUS
     */
    public final void setStatus(java.lang.Integer value){
        if(onPropSet(PROP_ID_status,value)){
            this._status = value;
            internalClearRefs(PROP_ID_status);
            
        }
    }
    
    /**
     * 标签: TAGS_TEXT
     */
    public final java.lang.String getTagsText(){
         onPropGet(PROP_ID_tagsText);
         return _tagsText;
    }

    /**
     * 标签: TAGS_TEXT
     */
    public final void setTagsText(java.lang.String value){
        if(onPropSet(PROP_ID_tagsText,value)){
            this._tagsText = value;
            internalClearRefs(PROP_ID_tagsText);
            
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
    public final java.lang.Integer getVersion(){
         onPropGet(PROP_ID_version);
         return _version;
    }

    /**
     * 数据版本: VERSION
     */
    public final void setVersion(java.lang.Integer value){
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
     * 实体元数据
     */
    public final io.nop.dyn.dao.entity.NopDynEntityMeta getEntityMeta(){
       return (io.nop.dyn.dao.entity.NopDynEntityMeta)internalGetRefEntity(PROP_NAME_entityMeta);
    }

    public final void setEntityMeta(io.nop.dyn.dao.entity.NopDynEntityMeta refEntity){
   
           if(refEntity == null){
           
                   this.setEntityMetaId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_entityMeta, refEntity,()->{
           
                           this.setEntityMetaId(refEntity.getEntityMetaId());
                       
           });
           }
       
    }
       
    /**
     * 关联实体元数据
     */
    public final io.nop.dyn.dao.entity.NopDynEntityMeta getRefEntityMeta(){
       return (io.nop.dyn.dao.entity.NopDynEntityMeta)internalGetRefEntity(PROP_NAME_refEntityMeta);
    }

    public final void setRefEntityMeta(io.nop.dyn.dao.entity.NopDynEntityMeta refEntity){
   
           if(refEntity == null){
           
                   this.setRefEntityMetaId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_refEntityMeta, refEntity,()->{
           
                           this.setRefEntityMetaId(refEntity.getEntityMetaId());
                       
           });
           }
       
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
