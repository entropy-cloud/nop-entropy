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

import io.nop.metadata.dao.entity.NopMetaEntityUniqueKey;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  实体唯一键: nop_meta_entity_unique_key
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopMetaEntityUniqueKey extends DynamicOrmEntity{
    
    /* 唯一键ID: UNIQUE_KEY_ID VARCHAR */
    public static final String PROP_NAME_uniqueKeyId = "uniqueKeyId";
    public static final int PROP_ID_uniqueKeyId = 1;
    
    /* 实体ID: META_ENTITY_ID VARCHAR */
    public static final String PROP_NAME_metaEntityId = "metaEntityId";
    public static final int PROP_ID_metaEntityId = 2;
    
    /* 是否Delta: IS_DELTA TINYINT */
    public static final String PROP_NAME_isDelta = "isDelta";
    public static final int PROP_ID_isDelta = 3;
    
    /* 唯一键名: UK_NAME VARCHAR */
    public static final String PROP_NAME_ukName = "ukName";
    public static final int PROP_ID_ukName = 4;
    
    /* 显示名: DISPLAY_NAME VARCHAR */
    public static final String PROP_NAME_displayName = "displayName";
    public static final int PROP_ID_displayName = 5;
    
    /* 字段列表: COLUMNS VARCHAR */
    public static final String PROP_NAME_columns = "columns";
    public static final int PROP_ID_columns = 6;
    
    /* 约束名: CONSTRAINT VARCHAR */
    public static final String PROP_NAME_constraintName = "constraintName";
    public static final int PROP_ID_constraintName = 7;
    
    /* 标签集: TAG_SET VARCHAR */
    public static final String PROP_NAME_tagSet = "tagSet";
    public static final int PROP_ID_tagSet = 8;
    
    /* 数据版本: VERSION BIGINT */
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

    
    /* relation: 元数据实体 */
    public static final String PROP_NAME_metaEntity = "metaEntity";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_uniqueKeyId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_uniqueKeyId};

    private static final String[] PROP_ID_TO_NAME = new String[15];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_uniqueKeyId] = PROP_NAME_uniqueKeyId;
          PROP_NAME_TO_ID.put(PROP_NAME_uniqueKeyId, PROP_ID_uniqueKeyId);
      
          PROP_ID_TO_NAME[PROP_ID_metaEntityId] = PROP_NAME_metaEntityId;
          PROP_NAME_TO_ID.put(PROP_NAME_metaEntityId, PROP_ID_metaEntityId);
      
          PROP_ID_TO_NAME[PROP_ID_isDelta] = PROP_NAME_isDelta;
          PROP_NAME_TO_ID.put(PROP_NAME_isDelta, PROP_ID_isDelta);
      
          PROP_ID_TO_NAME[PROP_ID_ukName] = PROP_NAME_ukName;
          PROP_NAME_TO_ID.put(PROP_NAME_ukName, PROP_ID_ukName);
      
          PROP_ID_TO_NAME[PROP_ID_displayName] = PROP_NAME_displayName;
          PROP_NAME_TO_ID.put(PROP_NAME_displayName, PROP_ID_displayName);
      
          PROP_ID_TO_NAME[PROP_ID_columns] = PROP_NAME_columns;
          PROP_NAME_TO_ID.put(PROP_NAME_columns, PROP_ID_columns);
      
          PROP_ID_TO_NAME[PROP_ID_constraintName] = PROP_NAME_constraintName;
          PROP_NAME_TO_ID.put(PROP_NAME_constraintName, PROP_ID_constraintName);
      
          PROP_ID_TO_NAME[PROP_ID_tagSet] = PROP_NAME_tagSet;
          PROP_NAME_TO_ID.put(PROP_NAME_tagSet, PROP_ID_tagSet);
      
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

    
    /* 唯一键ID: UNIQUE_KEY_ID */
    private java.lang.String _uniqueKeyId;
    
    /* 实体ID: META_ENTITY_ID */
    private java.lang.String _metaEntityId;
    
    /* 是否Delta: IS_DELTA */
    private java.lang.Byte _isDelta;
    
    /* 唯一键名: UK_NAME */
    private java.lang.String _ukName;
    
    /* 显示名: DISPLAY_NAME */
    private java.lang.String _displayName;
    
    /* 字段列表: COLUMNS */
    private java.lang.String _columns;
    
    /* 约束名: CONSTRAINT */
    private java.lang.String _constraintName;
    
    /* 标签集: TAG_SET */
    private java.lang.String _tagSet;
    
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
    

    public _NopMetaEntityUniqueKey(){
        // for debug
    }

    protected NopMetaEntityUniqueKey newInstance(){
        NopMetaEntityUniqueKey entity = new NopMetaEntityUniqueKey();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopMetaEntityUniqueKey cloneInstance() {
        NopMetaEntityUniqueKey entity = newInstance();
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
      return "io.nop.metadata.dao.entity.NopMetaEntityUniqueKey";
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
    
        return buildSimpleId(PROP_ID_uniqueKeyId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_uniqueKeyId;
          
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
        
            case PROP_ID_uniqueKeyId:
               return getUniqueKeyId();
        
            case PROP_ID_metaEntityId:
               return getMetaEntityId();
        
            case PROP_ID_isDelta:
               return getIsDelta();
        
            case PROP_ID_ukName:
               return getUkName();
        
            case PROP_ID_displayName:
               return getDisplayName();
        
            case PROP_ID_columns:
               return getColumns();
        
            case PROP_ID_constraintName:
               return getConstraintName();
        
            case PROP_ID_tagSet:
               return getTagSet();
        
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
        
            case PROP_ID_uniqueKeyId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_uniqueKeyId));
               }
               setUniqueKeyId(typedValue);
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
        
            case PROP_ID_ukName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_ukName));
               }
               setUkName(typedValue);
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
        
            case PROP_ID_columns:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_columns));
               }
               setColumns(typedValue);
               break;
            }
        
            case PROP_ID_constraintName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_constraintName));
               }
               setConstraintName(typedValue);
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
        
            case PROP_ID_uniqueKeyId:{
               onInitProp(propId);
               this._uniqueKeyId = (java.lang.String)value;
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
        
            case PROP_ID_ukName:{
               onInitProp(propId);
               this._ukName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_displayName:{
               onInitProp(propId);
               this._displayName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_columns:{
               onInitProp(propId);
               this._columns = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_constraintName:{
               onInitProp(propId);
               this._constraintName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_tagSet:{
               onInitProp(propId);
               this._tagSet = (java.lang.String)value;
               
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
     * 唯一键ID: UNIQUE_KEY_ID
     */
    public final java.lang.String getUniqueKeyId(){
         onPropGet(PROP_ID_uniqueKeyId);
         return _uniqueKeyId;
    }

    /**
     * 唯一键ID: UNIQUE_KEY_ID
     */
    public final void setUniqueKeyId(java.lang.String value){
        if(onPropSet(PROP_ID_uniqueKeyId,value)){
            this._uniqueKeyId = value;
            internalClearRefs(PROP_ID_uniqueKeyId);
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
     * 唯一键名: UK_NAME
     */
    public final java.lang.String getUkName(){
         onPropGet(PROP_ID_ukName);
         return _ukName;
    }

    /**
     * 唯一键名: UK_NAME
     */
    public final void setUkName(java.lang.String value){
        if(onPropSet(PROP_ID_ukName,value)){
            this._ukName = value;
            internalClearRefs(PROP_ID_ukName);
            
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
     * 字段列表: COLUMNS
     */
    public final java.lang.String getColumns(){
         onPropGet(PROP_ID_columns);
         return _columns;
    }

    /**
     * 字段列表: COLUMNS
     */
    public final void setColumns(java.lang.String value){
        if(onPropSet(PROP_ID_columns,value)){
            this._columns = value;
            internalClearRefs(PROP_ID_columns);
            
        }
    }
    
    /**
     * 约束名: CONSTRAINT
     */
    public final java.lang.String getConstraintName(){
         onPropGet(PROP_ID_constraintName);
         return _constraintName;
    }

    /**
     * 约束名: CONSTRAINT
     */
    public final void setConstraintName(java.lang.String value){
        if(onPropSet(PROP_ID_constraintName,value)){
            this._constraintName = value;
            internalClearRefs(PROP_ID_constraintName);
            
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
       
}
// resume CPD analysis - CPD-ON
