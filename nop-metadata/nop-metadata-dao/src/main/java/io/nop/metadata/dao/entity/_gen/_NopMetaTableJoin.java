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

import io.nop.metadata.dao.entity.NopMetaTableJoin;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  表关联: nop_meta_table_join
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopMetaTableJoin extends DynamicOrmEntity{
    
    /* 关联ID: JOIN_ID VARCHAR */
    public static final String PROP_NAME_joinId = "joinId";
    public static final int PROP_ID_joinId = 1;
    
    /* 逻辑表ID: META_TABLE_ID VARCHAR */
    public static final String PROP_NAME_metaTableId = "metaTableId";
    public static final int PROP_ID_metaTableId = 2;
    
    /* 关联类型: JOIN_TYPE VARCHAR */
    public static final String PROP_NAME_joinType = "joinType";
    public static final int PROP_ID_joinType = 3;
    
    /* 左实体ID: LEFT_ENTITY_ID VARCHAR */
    public static final String PROP_NAME_leftEntityId = "leftEntityId";
    public static final int PROP_ID_leftEntityId = 4;
    
    /* 右实体ID: RIGHT_ENTITY_ID VARCHAR */
    public static final String PROP_NAME_rightEntityId = "rightEntityId";
    public static final int PROP_ID_rightEntityId = 5;
    
    /* 左关联字段: LEFT_FIELD VARCHAR */
    public static final String PROP_NAME_leftField = "leftField";
    public static final int PROP_ID_leftField = 6;
    
    /* 右关联字段: RIGHT_FIELD VARCHAR */
    public static final String PROP_NAME_rightField = "rightField";
    public static final int PROP_ID_rightField = 7;
    
    /* 右表别名: ALIAS VARCHAR */
    public static final String PROP_NAME_alias = "alias";
    public static final int PROP_ID_alias = 8;
    
    /* 数据版本: DEL_VERSION BIGINT */
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
    
    /* 左表ID: LEFT_TABLE_ID VARCHAR */
    public static final String PROP_NAME_leftTableId = "leftTableId";
    public static final int PROP_ID_leftTableId = 15;
    
    /* 右表ID: RIGHT_TABLE_ID VARCHAR */
    public static final String PROP_NAME_rightTableId = "rightTableId";
    public static final int PROP_ID_rightTableId = 16;
    

    private static int _PROP_ID_BOUND = 17;

    
    /* relation: 逻辑表 */
    public static final String PROP_NAME_metaTable = "metaTable";
    
    /* relation: 左实体 */
    public static final String PROP_NAME_leftEntity = "leftEntity";
    
    /* relation: 右实体 */
    public static final String PROP_NAME_rightEntity = "rightEntity";
    
    /* relation: 左表 */
    public static final String PROP_NAME_leftTable = "leftTable";
    
    /* relation: 右表 */
    public static final String PROP_NAME_rightTable = "rightTable";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_joinId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_joinId};

    private static final String[] PROP_ID_TO_NAME = new String[17];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_joinId] = PROP_NAME_joinId;
          PROP_NAME_TO_ID.put(PROP_NAME_joinId, PROP_ID_joinId);
      
          PROP_ID_TO_NAME[PROP_ID_metaTableId] = PROP_NAME_metaTableId;
          PROP_NAME_TO_ID.put(PROP_NAME_metaTableId, PROP_ID_metaTableId);
      
          PROP_ID_TO_NAME[PROP_ID_joinType] = PROP_NAME_joinType;
          PROP_NAME_TO_ID.put(PROP_NAME_joinType, PROP_ID_joinType);
      
          PROP_ID_TO_NAME[PROP_ID_leftEntityId] = PROP_NAME_leftEntityId;
          PROP_NAME_TO_ID.put(PROP_NAME_leftEntityId, PROP_ID_leftEntityId);
      
          PROP_ID_TO_NAME[PROP_ID_rightEntityId] = PROP_NAME_rightEntityId;
          PROP_NAME_TO_ID.put(PROP_NAME_rightEntityId, PROP_ID_rightEntityId);
      
          PROP_ID_TO_NAME[PROP_ID_leftField] = PROP_NAME_leftField;
          PROP_NAME_TO_ID.put(PROP_NAME_leftField, PROP_ID_leftField);
      
          PROP_ID_TO_NAME[PROP_ID_rightField] = PROP_NAME_rightField;
          PROP_NAME_TO_ID.put(PROP_NAME_rightField, PROP_ID_rightField);
      
          PROP_ID_TO_NAME[PROP_ID_alias] = PROP_NAME_alias;
          PROP_NAME_TO_ID.put(PROP_NAME_alias, PROP_ID_alias);
      
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
      
          PROP_ID_TO_NAME[PROP_ID_leftTableId] = PROP_NAME_leftTableId;
          PROP_NAME_TO_ID.put(PROP_NAME_leftTableId, PROP_ID_leftTableId);
      
          PROP_ID_TO_NAME[PROP_ID_rightTableId] = PROP_NAME_rightTableId;
          PROP_NAME_TO_ID.put(PROP_NAME_rightTableId, PROP_ID_rightTableId);
      
    }

    
    /* 关联ID: JOIN_ID */
    private java.lang.String _joinId;
    
    /* 逻辑表ID: META_TABLE_ID */
    private java.lang.String _metaTableId;
    
    /* 关联类型: JOIN_TYPE */
    private java.lang.String _joinType;
    
    /* 左实体ID: LEFT_ENTITY_ID */
    private java.lang.String _leftEntityId;
    
    /* 右实体ID: RIGHT_ENTITY_ID */
    private java.lang.String _rightEntityId;
    
    /* 左关联字段: LEFT_FIELD */
    private java.lang.String _leftField;
    
    /* 右关联字段: RIGHT_FIELD */
    private java.lang.String _rightField;
    
    /* 右表别名: ALIAS */
    private java.lang.String _alias;
    
    /* 数据版本: DEL_VERSION */
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
    
    /* 左表ID: LEFT_TABLE_ID */
    private java.lang.String _leftTableId;
    
    /* 右表ID: RIGHT_TABLE_ID */
    private java.lang.String _rightTableId;
    

    public _NopMetaTableJoin(){
        // for debug
    }

    protected NopMetaTableJoin newInstance(){
        NopMetaTableJoin entity = new NopMetaTableJoin();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopMetaTableJoin cloneInstance() {
        NopMetaTableJoin entity = newInstance();
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
      return "io.nop.metadata.dao.entity.NopMetaTableJoin";
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
    
        return buildSimpleId(PROP_ID_joinId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_joinId;
          
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
        
            case PROP_ID_joinId:
               return getJoinId();
        
            case PROP_ID_metaTableId:
               return getMetaTableId();
        
            case PROP_ID_joinType:
               return getJoinType();
        
            case PROP_ID_leftEntityId:
               return getLeftEntityId();
        
            case PROP_ID_rightEntityId:
               return getRightEntityId();
        
            case PROP_ID_leftField:
               return getLeftField();
        
            case PROP_ID_rightField:
               return getRightField();
        
            case PROP_ID_alias:
               return getAlias();
        
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
        
            case PROP_ID_leftTableId:
               return getLeftTableId();
        
            case PROP_ID_rightTableId:
               return getRightTableId();
        
           default:
              return super.orm_propValue(propId);
        }
    }

    

    @Override
    public void orm_propValue(int propId, Object value){
        switch(propId){
        
            case PROP_ID_joinId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_joinId));
               }
               setJoinId(typedValue);
               break;
            }
        
            case PROP_ID_metaTableId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_metaTableId));
               }
               setMetaTableId(typedValue);
               break;
            }
        
            case PROP_ID_joinType:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_joinType));
               }
               setJoinType(typedValue);
               break;
            }
        
            case PROP_ID_leftEntityId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_leftEntityId));
               }
               setLeftEntityId(typedValue);
               break;
            }
        
            case PROP_ID_rightEntityId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_rightEntityId));
               }
               setRightEntityId(typedValue);
               break;
            }
        
            case PROP_ID_leftField:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_leftField));
               }
               setLeftField(typedValue);
               break;
            }
        
            case PROP_ID_rightField:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_rightField));
               }
               setRightField(typedValue);
               break;
            }
        
            case PROP_ID_alias:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_alias));
               }
               setAlias(typedValue);
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
        
            case PROP_ID_leftTableId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_leftTableId));
               }
               setLeftTableId(typedValue);
               break;
            }
        
            case PROP_ID_rightTableId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_rightTableId));
               }
               setRightTableId(typedValue);
               break;
            }
        
           default:
              super.orm_propValue(propId,value);
        }
    }

    @Override
    public void orm_internalSet(int propId, Object value) {
        switch(propId){
        
            case PROP_ID_joinId:{
               onInitProp(propId);
               this._joinId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_metaTableId:{
               onInitProp(propId);
               this._metaTableId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_joinType:{
               onInitProp(propId);
               this._joinType = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_leftEntityId:{
               onInitProp(propId);
               this._leftEntityId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_rightEntityId:{
               onInitProp(propId);
               this._rightEntityId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_leftField:{
               onInitProp(propId);
               this._leftField = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_rightField:{
               onInitProp(propId);
               this._rightField = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_alias:{
               onInitProp(propId);
               this._alias = (java.lang.String)value;
               
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
        
            case PROP_ID_leftTableId:{
               onInitProp(propId);
               this._leftTableId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_rightTableId:{
               onInitProp(propId);
               this._rightTableId = (java.lang.String)value;
               
               break;
            }
        
           default:
              super.orm_internalSet(propId,value);
        }
    }

    
    /**
     * 关联ID: JOIN_ID
     */
    public final java.lang.String getJoinId(){
         onPropGet(PROP_ID_joinId);
         return _joinId;
    }

    /**
     * 关联ID: JOIN_ID
     */
    public final void setJoinId(java.lang.String value){
        if(onPropSet(PROP_ID_joinId,value)){
            this._joinId = value;
            internalClearRefs(PROP_ID_joinId);
            orm_id();
        }
    }
    
    /**
     * 逻辑表ID: META_TABLE_ID
     */
    public final java.lang.String getMetaTableId(){
         onPropGet(PROP_ID_metaTableId);
         return _metaTableId;
    }

    /**
     * 逻辑表ID: META_TABLE_ID
     */
    public final void setMetaTableId(java.lang.String value){
        if(onPropSet(PROP_ID_metaTableId,value)){
            this._metaTableId = value;
            internalClearRefs(PROP_ID_metaTableId);
            
        }
    }
    
    /**
     * 关联类型: JOIN_TYPE
     */
    public final java.lang.String getJoinType(){
         onPropGet(PROP_ID_joinType);
         return _joinType;
    }

    /**
     * 关联类型: JOIN_TYPE
     */
    public final void setJoinType(java.lang.String value){
        if(onPropSet(PROP_ID_joinType,value)){
            this._joinType = value;
            internalClearRefs(PROP_ID_joinType);
            
        }
    }
    
    /**
     * 左实体ID: LEFT_ENTITY_ID
     */
    public final java.lang.String getLeftEntityId(){
         onPropGet(PROP_ID_leftEntityId);
         return _leftEntityId;
    }

    /**
     * 左实体ID: LEFT_ENTITY_ID
     */
    public final void setLeftEntityId(java.lang.String value){
        if(onPropSet(PROP_ID_leftEntityId,value)){
            this._leftEntityId = value;
            internalClearRefs(PROP_ID_leftEntityId);
            
        }
    }
    
    /**
     * 右实体ID: RIGHT_ENTITY_ID
     */
    public final java.lang.String getRightEntityId(){
         onPropGet(PROP_ID_rightEntityId);
         return _rightEntityId;
    }

    /**
     * 右实体ID: RIGHT_ENTITY_ID
     */
    public final void setRightEntityId(java.lang.String value){
        if(onPropSet(PROP_ID_rightEntityId,value)){
            this._rightEntityId = value;
            internalClearRefs(PROP_ID_rightEntityId);
            
        }
    }
    
    /**
     * 左关联字段: LEFT_FIELD
     */
    public final java.lang.String getLeftField(){
         onPropGet(PROP_ID_leftField);
         return _leftField;
    }

    /**
     * 左关联字段: LEFT_FIELD
     */
    public final void setLeftField(java.lang.String value){
        if(onPropSet(PROP_ID_leftField,value)){
            this._leftField = value;
            internalClearRefs(PROP_ID_leftField);
            
        }
    }
    
    /**
     * 右关联字段: RIGHT_FIELD
     */
    public final java.lang.String getRightField(){
         onPropGet(PROP_ID_rightField);
         return _rightField;
    }

    /**
     * 右关联字段: RIGHT_FIELD
     */
    public final void setRightField(java.lang.String value){
        if(onPropSet(PROP_ID_rightField,value)){
            this._rightField = value;
            internalClearRefs(PROP_ID_rightField);
            
        }
    }
    
    /**
     * 右表别名: ALIAS
     */
    public final java.lang.String getAlias(){
         onPropGet(PROP_ID_alias);
         return _alias;
    }

    /**
     * 右表别名: ALIAS
     */
    public final void setAlias(java.lang.String value){
        if(onPropSet(PROP_ID_alias,value)){
            this._alias = value;
            internalClearRefs(PROP_ID_alias);
            
        }
    }
    
    /**
     * 数据版本: DEL_VERSION
     */
    public final java.lang.Long getVersion(){
         onPropGet(PROP_ID_version);
         return _version;
    }

    /**
     * 数据版本: DEL_VERSION
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
     * 左表ID: LEFT_TABLE_ID
     */
    public final java.lang.String getLeftTableId(){
         onPropGet(PROP_ID_leftTableId);
         return _leftTableId;
    }

    /**
     * 左表ID: LEFT_TABLE_ID
     */
    public final void setLeftTableId(java.lang.String value){
        if(onPropSet(PROP_ID_leftTableId,value)){
            this._leftTableId = value;
            internalClearRefs(PROP_ID_leftTableId);
            
        }
    }
    
    /**
     * 右表ID: RIGHT_TABLE_ID
     */
    public final java.lang.String getRightTableId(){
         onPropGet(PROP_ID_rightTableId);
         return _rightTableId;
    }

    /**
     * 右表ID: RIGHT_TABLE_ID
     */
    public final void setRightTableId(java.lang.String value){
        if(onPropSet(PROP_ID_rightTableId,value)){
            this._rightTableId = value;
            internalClearRefs(PROP_ID_rightTableId);
            
        }
    }
    
    /**
     * 逻辑表
     */
    public final io.nop.metadata.dao.entity.NopMetaTable getMetaTable(){
       return (io.nop.metadata.dao.entity.NopMetaTable)internalGetRefEntity(PROP_NAME_metaTable);
    }

    public final void setMetaTable(io.nop.metadata.dao.entity.NopMetaTable refEntity){
   
           if(refEntity == null){
           
                   this.setMetaTableId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_metaTable, refEntity,()->{
           
                           this.setMetaTableId(refEntity.getMetaTableId());
                       
           });
           }
       
    }
       
    /**
     * 左实体
     */
    public final io.nop.metadata.dao.entity.NopMetaEntity getLeftEntity(){
       return (io.nop.metadata.dao.entity.NopMetaEntity)internalGetRefEntity(PROP_NAME_leftEntity);
    }

    public final void setLeftEntity(io.nop.metadata.dao.entity.NopMetaEntity refEntity){
   
           if(refEntity == null){
           
                   this.setLeftEntityId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_leftEntity, refEntity,()->{
           
                           this.setLeftEntityId(refEntity.getMetaEntityId());
                       
           });
           }
       
    }
       
    /**
     * 右实体
     */
    public final io.nop.metadata.dao.entity.NopMetaEntity getRightEntity(){
       return (io.nop.metadata.dao.entity.NopMetaEntity)internalGetRefEntity(PROP_NAME_rightEntity);
    }

    public final void setRightEntity(io.nop.metadata.dao.entity.NopMetaEntity refEntity){
   
           if(refEntity == null){
           
                   this.setRightEntityId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_rightEntity, refEntity,()->{
           
                           this.setRightEntityId(refEntity.getMetaEntityId());
                       
           });
           }
       
    }
       
    /**
     * 左表
     */
    public final io.nop.metadata.dao.entity.NopMetaTable getLeftTable(){
       return (io.nop.metadata.dao.entity.NopMetaTable)internalGetRefEntity(PROP_NAME_leftTable);
    }

    public final void setLeftTable(io.nop.metadata.dao.entity.NopMetaTable refEntity){
   
           if(refEntity == null){
           
                   this.setLeftTableId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_leftTable, refEntity,()->{
           
                           this.setLeftTableId(refEntity.getMetaTableId());
                       
           });
           }
       
    }
       
    /**
     * 右表
     */
    public final io.nop.metadata.dao.entity.NopMetaTable getRightTable(){
       return (io.nop.metadata.dao.entity.NopMetaTable)internalGetRefEntity(PROP_NAME_rightTable);
    }

    public final void setRightTable(io.nop.metadata.dao.entity.NopMetaTable refEntity){
   
           if(refEntity == null){
           
                   this.setRightTableId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_rightTable, refEntity,()->{
           
                           this.setRightTableId(refEntity.getMetaTableId());
                       
           });
           }
       
    }
       
}
// resume CPD analysis - CPD-ON
