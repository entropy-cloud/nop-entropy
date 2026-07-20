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

import io.nop.metadata.dao.entity.NopMetaDictItem;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  字典项: nop_meta_dict_item
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopMetaDictItem extends DynamicOrmEntity{
    
    /* 字典项ID: DICT_ITEM_ID VARCHAR */
    public static final String PROP_NAME_dictItemId = "dictItemId";
    public static final int PROP_ID_dictItemId = 1;
    
    /* 字典ID: META_DICT_ID VARCHAR */
    public static final String PROP_NAME_metaDictId = "metaDictId";
    public static final int PROP_ID_metaDictId = 2;
    
    /* 字典值: ITEM_VALUE VARCHAR */
    public static final String PROP_NAME_itemValue = "itemValue";
    public static final int PROP_ID_itemValue = 3;
    
    /* 字典标签: ITEM_LABEL VARCHAR */
    public static final String PROP_NAME_itemLabel = "itemLabel";
    public static final int PROP_ID_itemLabel = 4;
    
    /* 字典编码: ITEM_CODE VARCHAR */
    public static final String PROP_NAME_itemCode = "itemCode";
    public static final int PROP_ID_itemCode = 5;
    
    /* 分组: ITEM_GROUP VARCHAR */
    public static final String PROP_NAME_itemGroup = "itemGroup";
    public static final int PROP_ID_itemGroup = 6;
    
    /* 描述: DESCRIPTION VARCHAR */
    public static final String PROP_NAME_description = "description";
    public static final int PROP_ID_description = 7;
    
    /* 排序: SORT_ORDER INTEGER */
    public static final String PROP_NAME_sortOrder = "sortOrder";
    public static final int PROP_ID_sortOrder = 8;
    
    /* 已废弃: DEPRECATED TINYINT */
    public static final String PROP_NAME_deprecated = "deprecated";
    public static final int PROP_ID_deprecated = 9;
    
    /* 内部使用: INTERNAL TINYINT */
    public static final String PROP_NAME_internal = "internal";
    public static final int PROP_ID_internal = 10;
    
    /* 是否Delta: IS_DELTA TINYINT */
    public static final String PROP_NAME_isDelta = "isDelta";
    public static final int PROP_ID_isDelta = 11;
    
    /* 数据版本: VERSION BIGINT */
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

    
    /* relation: 元数据字典 */
    public static final String PROP_NAME_metaDict = "metaDict";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_dictItemId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_dictItemId};

    private static final String[] PROP_ID_TO_NAME = new String[18];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_dictItemId] = PROP_NAME_dictItemId;
          PROP_NAME_TO_ID.put(PROP_NAME_dictItemId, PROP_ID_dictItemId);
      
          PROP_ID_TO_NAME[PROP_ID_metaDictId] = PROP_NAME_metaDictId;
          PROP_NAME_TO_ID.put(PROP_NAME_metaDictId, PROP_ID_metaDictId);
      
          PROP_ID_TO_NAME[PROP_ID_itemValue] = PROP_NAME_itemValue;
          PROP_NAME_TO_ID.put(PROP_NAME_itemValue, PROP_ID_itemValue);
      
          PROP_ID_TO_NAME[PROP_ID_itemLabel] = PROP_NAME_itemLabel;
          PROP_NAME_TO_ID.put(PROP_NAME_itemLabel, PROP_ID_itemLabel);
      
          PROP_ID_TO_NAME[PROP_ID_itemCode] = PROP_NAME_itemCode;
          PROP_NAME_TO_ID.put(PROP_NAME_itemCode, PROP_ID_itemCode);
      
          PROP_ID_TO_NAME[PROP_ID_itemGroup] = PROP_NAME_itemGroup;
          PROP_NAME_TO_ID.put(PROP_NAME_itemGroup, PROP_ID_itemGroup);
      
          PROP_ID_TO_NAME[PROP_ID_description] = PROP_NAME_description;
          PROP_NAME_TO_ID.put(PROP_NAME_description, PROP_ID_description);
      
          PROP_ID_TO_NAME[PROP_ID_sortOrder] = PROP_NAME_sortOrder;
          PROP_NAME_TO_ID.put(PROP_NAME_sortOrder, PROP_ID_sortOrder);
      
          PROP_ID_TO_NAME[PROP_ID_deprecated] = PROP_NAME_deprecated;
          PROP_NAME_TO_ID.put(PROP_NAME_deprecated, PROP_ID_deprecated);
      
          PROP_ID_TO_NAME[PROP_ID_internal] = PROP_NAME_internal;
          PROP_NAME_TO_ID.put(PROP_NAME_internal, PROP_ID_internal);
      
          PROP_ID_TO_NAME[PROP_ID_isDelta] = PROP_NAME_isDelta;
          PROP_NAME_TO_ID.put(PROP_NAME_isDelta, PROP_ID_isDelta);
      
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

    
    /* 字典项ID: DICT_ITEM_ID */
    private java.lang.String _dictItemId;
    
    /* 字典ID: META_DICT_ID */
    private java.lang.String _metaDictId;
    
    /* 字典值: ITEM_VALUE */
    private java.lang.String _itemValue;
    
    /* 字典标签: ITEM_LABEL */
    private java.lang.String _itemLabel;
    
    /* 字典编码: ITEM_CODE */
    private java.lang.String _itemCode;
    
    /* 分组: ITEM_GROUP */
    private java.lang.String _itemGroup;
    
    /* 描述: DESCRIPTION */
    private java.lang.String _description;
    
    /* 排序: SORT_ORDER */
    private java.lang.Integer _sortOrder;
    
    /* 已废弃: DEPRECATED */
    private java.lang.Byte _deprecated;
    
    /* 内部使用: INTERNAL */
    private java.lang.Byte _internal;
    
    /* 是否Delta: IS_DELTA */
    private java.lang.Byte _isDelta;
    
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
    

    public _NopMetaDictItem(){
        // for debug
    }

    protected NopMetaDictItem newInstance(){
        NopMetaDictItem entity = new NopMetaDictItem();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopMetaDictItem cloneInstance() {
        NopMetaDictItem entity = newInstance();
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
      return "io.nop.metadata.dao.entity.NopMetaDictItem";
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
    
        return buildSimpleId(PROP_ID_dictItemId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_dictItemId;
          
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
        
            case PROP_ID_dictItemId:
               return getDictItemId();
        
            case PROP_ID_metaDictId:
               return getMetaDictId();
        
            case PROP_ID_itemValue:
               return getItemValue();
        
            case PROP_ID_itemLabel:
               return getItemLabel();
        
            case PROP_ID_itemCode:
               return getItemCode();
        
            case PROP_ID_itemGroup:
               return getItemGroup();
        
            case PROP_ID_description:
               return getDescription();
        
            case PROP_ID_sortOrder:
               return getSortOrder();
        
            case PROP_ID_deprecated:
               return getDeprecated();
        
            case PROP_ID_internal:
               return getInternal();
        
            case PROP_ID_isDelta:
               return getIsDelta();
        
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
        
            case PROP_ID_dictItemId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_dictItemId));
               }
               setDictItemId(typedValue);
               break;
            }
        
            case PROP_ID_metaDictId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_metaDictId));
               }
               setMetaDictId(typedValue);
               break;
            }
        
            case PROP_ID_itemValue:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_itemValue));
               }
               setItemValue(typedValue);
               break;
            }
        
            case PROP_ID_itemLabel:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_itemLabel));
               }
               setItemLabel(typedValue);
               break;
            }
        
            case PROP_ID_itemCode:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_itemCode));
               }
               setItemCode(typedValue);
               break;
            }
        
            case PROP_ID_itemGroup:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_itemGroup));
               }
               setItemGroup(typedValue);
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
        
            case PROP_ID_sortOrder:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_sortOrder));
               }
               setSortOrder(typedValue);
               break;
            }
        
            case PROP_ID_deprecated:{
               java.lang.Byte typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toByte(value,
                       err-> newTypeConversionError(PROP_NAME_deprecated));
               }
               setDeprecated(typedValue);
               break;
            }
        
            case PROP_ID_internal:{
               java.lang.Byte typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toByte(value,
                       err-> newTypeConversionError(PROP_NAME_internal));
               }
               setInternal(typedValue);
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
        
            case PROP_ID_dictItemId:{
               onInitProp(propId);
               this._dictItemId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_metaDictId:{
               onInitProp(propId);
               this._metaDictId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_itemValue:{
               onInitProp(propId);
               this._itemValue = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_itemLabel:{
               onInitProp(propId);
               this._itemLabel = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_itemCode:{
               onInitProp(propId);
               this._itemCode = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_itemGroup:{
               onInitProp(propId);
               this._itemGroup = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_description:{
               onInitProp(propId);
               this._description = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_sortOrder:{
               onInitProp(propId);
               this._sortOrder = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_deprecated:{
               onInitProp(propId);
               this._deprecated = (java.lang.Byte)value;
               
               break;
            }
        
            case PROP_ID_internal:{
               onInitProp(propId);
               this._internal = (java.lang.Byte)value;
               
               break;
            }
        
            case PROP_ID_isDelta:{
               onInitProp(propId);
               this._isDelta = (java.lang.Byte)value;
               
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
     * 字典项ID: DICT_ITEM_ID
     */
    public final java.lang.String getDictItemId(){
         onPropGet(PROP_ID_dictItemId);
         return _dictItemId;
    }

    /**
     * 字典项ID: DICT_ITEM_ID
     */
    public final void setDictItemId(java.lang.String value){
        if(onPropSet(PROP_ID_dictItemId,value)){
            this._dictItemId = value;
            internalClearRefs(PROP_ID_dictItemId);
            orm_id();
        }
    }
    
    /**
     * 字典ID: META_DICT_ID
     */
    public final java.lang.String getMetaDictId(){
         onPropGet(PROP_ID_metaDictId);
         return _metaDictId;
    }

    /**
     * 字典ID: META_DICT_ID
     */
    public final void setMetaDictId(java.lang.String value){
        if(onPropSet(PROP_ID_metaDictId,value)){
            this._metaDictId = value;
            internalClearRefs(PROP_ID_metaDictId);
            
        }
    }
    
    /**
     * 字典值: ITEM_VALUE
     */
    public final java.lang.String getItemValue(){
         onPropGet(PROP_ID_itemValue);
         return _itemValue;
    }

    /**
     * 字典值: ITEM_VALUE
     */
    public final void setItemValue(java.lang.String value){
        if(onPropSet(PROP_ID_itemValue,value)){
            this._itemValue = value;
            internalClearRefs(PROP_ID_itemValue);
            
        }
    }
    
    /**
     * 字典标签: ITEM_LABEL
     */
    public final java.lang.String getItemLabel(){
         onPropGet(PROP_ID_itemLabel);
         return _itemLabel;
    }

    /**
     * 字典标签: ITEM_LABEL
     */
    public final void setItemLabel(java.lang.String value){
        if(onPropSet(PROP_ID_itemLabel,value)){
            this._itemLabel = value;
            internalClearRefs(PROP_ID_itemLabel);
            
        }
    }
    
    /**
     * 字典编码: ITEM_CODE
     */
    public final java.lang.String getItemCode(){
         onPropGet(PROP_ID_itemCode);
         return _itemCode;
    }

    /**
     * 字典编码: ITEM_CODE
     */
    public final void setItemCode(java.lang.String value){
        if(onPropSet(PROP_ID_itemCode,value)){
            this._itemCode = value;
            internalClearRefs(PROP_ID_itemCode);
            
        }
    }
    
    /**
     * 分组: ITEM_GROUP
     */
    public final java.lang.String getItemGroup(){
         onPropGet(PROP_ID_itemGroup);
         return _itemGroup;
    }

    /**
     * 分组: ITEM_GROUP
     */
    public final void setItemGroup(java.lang.String value){
        if(onPropSet(PROP_ID_itemGroup,value)){
            this._itemGroup = value;
            internalClearRefs(PROP_ID_itemGroup);
            
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
     * 排序: SORT_ORDER
     */
    public final java.lang.Integer getSortOrder(){
         onPropGet(PROP_ID_sortOrder);
         return _sortOrder;
    }

    /**
     * 排序: SORT_ORDER
     */
    public final void setSortOrder(java.lang.Integer value){
        if(onPropSet(PROP_ID_sortOrder,value)){
            this._sortOrder = value;
            internalClearRefs(PROP_ID_sortOrder);
            
        }
    }
    
    /**
     * 已废弃: DEPRECATED
     */
    public final java.lang.Byte getDeprecated(){
         onPropGet(PROP_ID_deprecated);
         return _deprecated;
    }

    /**
     * 已废弃: DEPRECATED
     */
    public final void setDeprecated(java.lang.Byte value){
        if(onPropSet(PROP_ID_deprecated,value)){
            this._deprecated = value;
            internalClearRefs(PROP_ID_deprecated);
            
        }
    }
    
    /**
     * 内部使用: INTERNAL
     */
    public final java.lang.Byte getInternal(){
         onPropGet(PROP_ID_internal);
         return _internal;
    }

    /**
     * 内部使用: INTERNAL
     */
    public final void setInternal(java.lang.Byte value){
        if(onPropSet(PROP_ID_internal,value)){
            this._internal = value;
            internalClearRefs(PROP_ID_internal);
            
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
     * 元数据字典
     */
    public final io.nop.metadata.dao.entity.NopMetaDict getMetaDict(){
       return (io.nop.metadata.dao.entity.NopMetaDict)internalGetRefEntity(PROP_NAME_metaDict);
    }

    public final void setMetaDict(io.nop.metadata.dao.entity.NopMetaDict refEntity){
   
           if(refEntity == null){
           
                   this.setMetaDictId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_metaDict, refEntity,()->{
           
                           this.setMetaDictId(refEntity.getMetaDictId());
                       
           });
           }
       
    }
       
}
// resume CPD analysis - CPD-ON
