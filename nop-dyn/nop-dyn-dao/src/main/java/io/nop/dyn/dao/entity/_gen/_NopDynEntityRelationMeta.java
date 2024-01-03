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
 *  实体关联元数据: nop_dyn_entity_relation_meta
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopDynEntityRelationMeta extends DynamicOrmEntity{
    
    /* 关联定义ID: REL_META_ID VARCHAR */
    public static final String PROP_NAME_relMetaId = "relMetaId";
    public static final int PROP_ID_relMetaId = 1;
    
    /* 实体1元数据: ENTITY1_META_ID VARCHAR */
    public static final String PROP_NAME_entity1MetaId = "entity1MetaId";
    public static final int PROP_ID_entity1MetaId = 2;
    
    /* 实体2元数据: ENTITY2_META_ID VARCHAR */
    public static final String PROP_NAME_entity2MetaId = "entity2MetaId";
    public static final int PROP_ID_entity2MetaId = 3;
    
    /* 关联名: RELATION_NAME VARCHAR */
    public static final String PROP_NAME_relationName = "relationName";
    public static final int PROP_ID_relationName = 4;
    
    /* 实体1上属性名: ENTITY1_PROP_NAME VARCHAR */
    public static final String PROP_NAME_entity1PropName = "entity1PropName";
    public static final int PROP_ID_entity1PropName = 5;
    
    /* 实体1上属性显示名: ENTITY1_DISPLAY_NAME VARCHAR */
    public static final String PROP_NAME_entity1DisplayName = "entity1DisplayName";
    public static final int PROP_ID_entity1DisplayName = 6;
    
    /* 实体2上属性名: ENTITY2_PROP_NAME VARCHAR */
    public static final String PROP_NAME_entity2PropName = "entity2PropName";
    public static final int PROP_ID_entity2PropName = 7;
    
    /* 实体2上属性显示名: ENTITY2_DISPLAY_NAME VARCHAR */
    public static final String PROP_NAME_entity2DisplayName = "entity2DisplayName";
    public static final int PROP_ID_entity2DisplayName = 8;
    
    /* 标签: TAG_SET VARCHAR */
    public static final String PROP_NAME_tagSet = "tagSet";
    public static final int PROP_ID_tagSet = 9;
    
    /* 状态: STATUS INTEGER */
    public static final String PROP_NAME_status = "status";
    public static final int PROP_ID_status = 10;
    
    /* 扩展配置: EXT_CONFIG VARCHAR */
    public static final String PROP_NAME_extConfig = "extConfig";
    public static final int PROP_ID_extConfig = 11;
    
    /* 数据版本: VERSION INTEGER */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 12;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 13;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 14;
    
    /* 修改人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 15;
    
    /* 修改时间: UPDATE_TIME TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 16;
    
    /* 备注: REMARK VARCHAR */
    public static final String PROP_NAME_remark = "remark";
    public static final int PROP_ID_remark = 17;
    

    private static int _PROP_ID_BOUND = 18;

    
    /* relation: 关联实体1元数据 */
    public static final String PROP_NAME_entityMeta1 = "entityMeta1";
    
    /* relation: 关联实体2元数据 */
    public static final String PROP_NAME_entityMeta2 = "entityMeta2";
    
    /* component:  */
    public static final String PROP_NAME_extConfigComponent = "extConfigComponent";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_relMetaId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_relMetaId};

    private static final String[] PROP_ID_TO_NAME = new String[18];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_relMetaId] = PROP_NAME_relMetaId;
          PROP_NAME_TO_ID.put(PROP_NAME_relMetaId, PROP_ID_relMetaId);
      
          PROP_ID_TO_NAME[PROP_ID_entity1MetaId] = PROP_NAME_entity1MetaId;
          PROP_NAME_TO_ID.put(PROP_NAME_entity1MetaId, PROP_ID_entity1MetaId);
      
          PROP_ID_TO_NAME[PROP_ID_entity2MetaId] = PROP_NAME_entity2MetaId;
          PROP_NAME_TO_ID.put(PROP_NAME_entity2MetaId, PROP_ID_entity2MetaId);
      
          PROP_ID_TO_NAME[PROP_ID_relationName] = PROP_NAME_relationName;
          PROP_NAME_TO_ID.put(PROP_NAME_relationName, PROP_ID_relationName);
      
          PROP_ID_TO_NAME[PROP_ID_entity1PropName] = PROP_NAME_entity1PropName;
          PROP_NAME_TO_ID.put(PROP_NAME_entity1PropName, PROP_ID_entity1PropName);
      
          PROP_ID_TO_NAME[PROP_ID_entity1DisplayName] = PROP_NAME_entity1DisplayName;
          PROP_NAME_TO_ID.put(PROP_NAME_entity1DisplayName, PROP_ID_entity1DisplayName);
      
          PROP_ID_TO_NAME[PROP_ID_entity2PropName] = PROP_NAME_entity2PropName;
          PROP_NAME_TO_ID.put(PROP_NAME_entity2PropName, PROP_ID_entity2PropName);
      
          PROP_ID_TO_NAME[PROP_ID_entity2DisplayName] = PROP_NAME_entity2DisplayName;
          PROP_NAME_TO_ID.put(PROP_NAME_entity2DisplayName, PROP_ID_entity2DisplayName);
      
          PROP_ID_TO_NAME[PROP_ID_tagSet] = PROP_NAME_tagSet;
          PROP_NAME_TO_ID.put(PROP_NAME_tagSet, PROP_ID_tagSet);
      
          PROP_ID_TO_NAME[PROP_ID_status] = PROP_NAME_status;
          PROP_NAME_TO_ID.put(PROP_NAME_status, PROP_ID_status);
      
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
    
    /* 实体1元数据: ENTITY1_META_ID */
    private java.lang.String _entity1MetaId;
    
    /* 实体2元数据: ENTITY2_META_ID */
    private java.lang.String _entity2MetaId;
    
    /* 关联名: RELATION_NAME */
    private java.lang.String _relationName;
    
    /* 实体1上属性名: ENTITY1_PROP_NAME */
    private java.lang.String _entity1PropName;
    
    /* 实体1上属性显示名: ENTITY1_DISPLAY_NAME */
    private java.lang.String _entity1DisplayName;
    
    /* 实体2上属性名: ENTITY2_PROP_NAME */
    private java.lang.String _entity2PropName;
    
    /* 实体2上属性显示名: ENTITY2_DISPLAY_NAME */
    private java.lang.String _entity2DisplayName;
    
    /* 标签: TAG_SET */
    private java.lang.String _tagSet;
    
    /* 状态: STATUS */
    private java.lang.Integer _status;
    
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
       return new NopDynEntityRelationMeta();
    }

    @Override
    public NopDynEntityRelationMeta cloneInstance() {
        NopDynEntityRelationMeta entity = newInstance();
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
        
            case PROP_ID_entity1MetaId:
               return getEntity1MetaId();
        
            case PROP_ID_entity2MetaId:
               return getEntity2MetaId();
        
            case PROP_ID_relationName:
               return getRelationName();
        
            case PROP_ID_entity1PropName:
               return getEntity1PropName();
        
            case PROP_ID_entity1DisplayName:
               return getEntity1DisplayName();
        
            case PROP_ID_entity2PropName:
               return getEntity2PropName();
        
            case PROP_ID_entity2DisplayName:
               return getEntity2DisplayName();
        
            case PROP_ID_tagSet:
               return getTagSet();
        
            case PROP_ID_status:
               return getStatus();
        
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
        
            case PROP_ID_entity1MetaId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_entity1MetaId));
               }
               setEntity1MetaId(typedValue);
               break;
            }
        
            case PROP_ID_entity2MetaId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_entity2MetaId));
               }
               setEntity2MetaId(typedValue);
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
        
            case PROP_ID_entity1PropName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_entity1PropName));
               }
               setEntity1PropName(typedValue);
               break;
            }
        
            case PROP_ID_entity1DisplayName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_entity1DisplayName));
               }
               setEntity1DisplayName(typedValue);
               break;
            }
        
            case PROP_ID_entity2PropName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_entity2PropName));
               }
               setEntity2PropName(typedValue);
               break;
            }
        
            case PROP_ID_entity2DisplayName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_entity2DisplayName));
               }
               setEntity2DisplayName(typedValue);
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
        
            case PROP_ID_status:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_status));
               }
               setStatus(typedValue);
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
        
            case PROP_ID_entity1MetaId:{
               onInitProp(propId);
               this._entity1MetaId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_entity2MetaId:{
               onInitProp(propId);
               this._entity2MetaId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_relationName:{
               onInitProp(propId);
               this._relationName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_entity1PropName:{
               onInitProp(propId);
               this._entity1PropName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_entity1DisplayName:{
               onInitProp(propId);
               this._entity1DisplayName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_entity2PropName:{
               onInitProp(propId);
               this._entity2PropName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_entity2DisplayName:{
               onInitProp(propId);
               this._entity2DisplayName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_tagSet:{
               onInitProp(propId);
               this._tagSet = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_status:{
               onInitProp(propId);
               this._status = (java.lang.Integer)value;
               
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
    public java.lang.String getRelMetaId(){
         onPropGet(PROP_ID_relMetaId);
         return _relMetaId;
    }

    /**
     * 关联定义ID: REL_META_ID
     */
    public void setRelMetaId(java.lang.String value){
        if(onPropSet(PROP_ID_relMetaId,value)){
            this._relMetaId = value;
            internalClearRefs(PROP_ID_relMetaId);
            orm_id();
        }
    }
    
    /**
     * 实体1元数据: ENTITY1_META_ID
     */
    public java.lang.String getEntity1MetaId(){
         onPropGet(PROP_ID_entity1MetaId);
         return _entity1MetaId;
    }

    /**
     * 实体1元数据: ENTITY1_META_ID
     */
    public void setEntity1MetaId(java.lang.String value){
        if(onPropSet(PROP_ID_entity1MetaId,value)){
            this._entity1MetaId = value;
            internalClearRefs(PROP_ID_entity1MetaId);
            
        }
    }
    
    /**
     * 实体2元数据: ENTITY2_META_ID
     */
    public java.lang.String getEntity2MetaId(){
         onPropGet(PROP_ID_entity2MetaId);
         return _entity2MetaId;
    }

    /**
     * 实体2元数据: ENTITY2_META_ID
     */
    public void setEntity2MetaId(java.lang.String value){
        if(onPropSet(PROP_ID_entity2MetaId,value)){
            this._entity2MetaId = value;
            internalClearRefs(PROP_ID_entity2MetaId);
            
        }
    }
    
    /**
     * 关联名: RELATION_NAME
     */
    public java.lang.String getRelationName(){
         onPropGet(PROP_ID_relationName);
         return _relationName;
    }

    /**
     * 关联名: RELATION_NAME
     */
    public void setRelationName(java.lang.String value){
        if(onPropSet(PROP_ID_relationName,value)){
            this._relationName = value;
            internalClearRefs(PROP_ID_relationName);
            
        }
    }
    
    /**
     * 实体1上属性名: ENTITY1_PROP_NAME
     */
    public java.lang.String getEntity1PropName(){
         onPropGet(PROP_ID_entity1PropName);
         return _entity1PropName;
    }

    /**
     * 实体1上属性名: ENTITY1_PROP_NAME
     */
    public void setEntity1PropName(java.lang.String value){
        if(onPropSet(PROP_ID_entity1PropName,value)){
            this._entity1PropName = value;
            internalClearRefs(PROP_ID_entity1PropName);
            
        }
    }
    
    /**
     * 实体1上属性显示名: ENTITY1_DISPLAY_NAME
     */
    public java.lang.String getEntity1DisplayName(){
         onPropGet(PROP_ID_entity1DisplayName);
         return _entity1DisplayName;
    }

    /**
     * 实体1上属性显示名: ENTITY1_DISPLAY_NAME
     */
    public void setEntity1DisplayName(java.lang.String value){
        if(onPropSet(PROP_ID_entity1DisplayName,value)){
            this._entity1DisplayName = value;
            internalClearRefs(PROP_ID_entity1DisplayName);
            
        }
    }
    
    /**
     * 实体2上属性名: ENTITY2_PROP_NAME
     */
    public java.lang.String getEntity2PropName(){
         onPropGet(PROP_ID_entity2PropName);
         return _entity2PropName;
    }

    /**
     * 实体2上属性名: ENTITY2_PROP_NAME
     */
    public void setEntity2PropName(java.lang.String value){
        if(onPropSet(PROP_ID_entity2PropName,value)){
            this._entity2PropName = value;
            internalClearRefs(PROP_ID_entity2PropName);
            
        }
    }
    
    /**
     * 实体2上属性显示名: ENTITY2_DISPLAY_NAME
     */
    public java.lang.String getEntity2DisplayName(){
         onPropGet(PROP_ID_entity2DisplayName);
         return _entity2DisplayName;
    }

    /**
     * 实体2上属性显示名: ENTITY2_DISPLAY_NAME
     */
    public void setEntity2DisplayName(java.lang.String value){
        if(onPropSet(PROP_ID_entity2DisplayName,value)){
            this._entity2DisplayName = value;
            internalClearRefs(PROP_ID_entity2DisplayName);
            
        }
    }
    
    /**
     * 标签: TAG_SET
     */
    public java.lang.String getTagSet(){
         onPropGet(PROP_ID_tagSet);
         return _tagSet;
    }

    /**
     * 标签: TAG_SET
     */
    public void setTagSet(java.lang.String value){
        if(onPropSet(PROP_ID_tagSet,value)){
            this._tagSet = value;
            internalClearRefs(PROP_ID_tagSet);
            
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
     * 扩展配置: EXT_CONFIG
     */
    public java.lang.String getExtConfig(){
         onPropGet(PROP_ID_extConfig);
         return _extConfig;
    }

    /**
     * 扩展配置: EXT_CONFIG
     */
    public void setExtConfig(java.lang.String value){
        if(onPropSet(PROP_ID_extConfig,value)){
            this._extConfig = value;
            internalClearRefs(PROP_ID_extConfig);
            
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
     * 关联实体1元数据
     */
    public io.nop.dyn.dao.entity.NopDynEntityMeta getEntityMeta1(){
       return (io.nop.dyn.dao.entity.NopDynEntityMeta)internalGetRefEntity(PROP_NAME_entityMeta1);
    }

    public void setEntityMeta1(io.nop.dyn.dao.entity.NopDynEntityMeta refEntity){
       if(refEntity == null){
         
         this.setEntity1MetaId(null);
         
       }else{
          internalSetRefEntity(PROP_NAME_entityMeta1, refEntity,()->{
             
                    this.setEntity1MetaId(refEntity.getEntityMetaId());
                 
          });
       }
    }
       
    /**
     * 关联实体2元数据
     */
    public io.nop.dyn.dao.entity.NopDynEntityMeta getEntityMeta2(){
       return (io.nop.dyn.dao.entity.NopDynEntityMeta)internalGetRefEntity(PROP_NAME_entityMeta2);
    }

    public void setEntityMeta2(io.nop.dyn.dao.entity.NopDynEntityMeta refEntity){
       if(refEntity == null){
         
         this.setEntity2MetaId(null);
         
       }else{
          internalSetRefEntity(PROP_NAME_entityMeta2, refEntity,()->{
             
                    this.setEntity2MetaId(refEntity.getEntityMetaId());
                 
          });
       }
    }
       
   private io.nop.orm.component.JsonOrmComponent _extConfigComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_extConfigComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_extConfigComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_extConfig);
      
   }

   public io.nop.orm.component.JsonOrmComponent getExtConfigComponent(){
      if(_extConfigComponent == null){
          _extConfigComponent = new io.nop.orm.component.JsonOrmComponent();
          _extConfigComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_extConfigComponent);
      }
      return _extConfigComponent;
   }

}
// resume CPD analysis - CPD-ON
