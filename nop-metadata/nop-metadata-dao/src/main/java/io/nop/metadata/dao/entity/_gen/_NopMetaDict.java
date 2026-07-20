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

import io.nop.metadata.dao.entity.NopMetaDict;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  元数据字典: nop_meta_dict
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopMetaDict extends DynamicOrmEntity{
    
    /* 字典ID: META_DICT_ID VARCHAR */
    public static final String PROP_NAME_metaDictId = "metaDictId";
    public static final int PROP_ID_metaDictId = 1;
    
    /* 模型ID: ORM_MODEL_ID VARCHAR */
    public static final String PROP_NAME_ormModelId = "ormModelId";
    public static final int PROP_ID_ormModelId = 2;
    
    /* 是否Delta: IS_DELTA TINYINT */
    public static final String PROP_NAME_isDelta = "isDelta";
    public static final int PROP_ID_isDelta = 3;
    
    /* 字典名: DICT_NAME VARCHAR */
    public static final String PROP_NAME_dictName = "dictName";
    public static final int PROP_ID_dictName = 4;
    
    /* 字典标签: LABEL VARCHAR */
    public static final String PROP_NAME_label = "label";
    public static final int PROP_ID_label = 5;
    
    /* 值类型: VALUE_TYPE VARCHAR */
    public static final String PROP_NAME_valueType = "valueType";
    public static final int PROP_ID_valueType = 6;
    
    /* 区域: LOCALE VARCHAR */
    public static final String PROP_NAME_locale = "locale";
    public static final int PROP_ID_locale = 7;
    
    /* 静态字典: IS_STATIC TINYINT */
    public static final String PROP_NAME_staticDict = "staticDict";
    public static final int PROP_ID_staticDict = 8;
    
    /* 已标准化: NORMALIZED TINYINT */
    public static final String PROP_NAME_normalized = "normalized";
    public static final int PROP_ID_normalized = 9;
    
    /* 已废弃: DEPRECATED TINYINT */
    public static final String PROP_NAME_deprecated = "deprecated";
    public static final int PROP_ID_deprecated = 10;
    
    /* 内部使用: INTERNAL TINYINT */
    public static final String PROP_NAME_internal = "internal";
    public static final int PROP_ID_internal = 11;
    
    /* 标签集: TAG_SET VARCHAR */
    public static final String PROP_NAME_tagSet = "tagSet";
    public static final int PROP_ID_tagSet = 12;
    
    /* 数据版本: VERSION BIGINT */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 13;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 14;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 15;
    
    /* 修改人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 16;
    
    /* 修改时间: UPDATE_TIME TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 17;
    
    /* 备注: REMARK VARCHAR */
    public static final String PROP_NAME_remark = "remark";
    public static final int PROP_ID_remark = 18;
    

    private static int _PROP_ID_BOUND = 19;

    
    /* relation: ORM模型 */
    public static final String PROP_NAME_ormModel = "ormModel";
    
    /* relation: 字典项集 */
    public static final String PROP_NAME_dictItems = "dictItems";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_metaDictId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_metaDictId};

    private static final String[] PROP_ID_TO_NAME = new String[19];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_metaDictId] = PROP_NAME_metaDictId;
          PROP_NAME_TO_ID.put(PROP_NAME_metaDictId, PROP_ID_metaDictId);
      
          PROP_ID_TO_NAME[PROP_ID_ormModelId] = PROP_NAME_ormModelId;
          PROP_NAME_TO_ID.put(PROP_NAME_ormModelId, PROP_ID_ormModelId);
      
          PROP_ID_TO_NAME[PROP_ID_isDelta] = PROP_NAME_isDelta;
          PROP_NAME_TO_ID.put(PROP_NAME_isDelta, PROP_ID_isDelta);
      
          PROP_ID_TO_NAME[PROP_ID_dictName] = PROP_NAME_dictName;
          PROP_NAME_TO_ID.put(PROP_NAME_dictName, PROP_ID_dictName);
      
          PROP_ID_TO_NAME[PROP_ID_label] = PROP_NAME_label;
          PROP_NAME_TO_ID.put(PROP_NAME_label, PROP_ID_label);
      
          PROP_ID_TO_NAME[PROP_ID_valueType] = PROP_NAME_valueType;
          PROP_NAME_TO_ID.put(PROP_NAME_valueType, PROP_ID_valueType);
      
          PROP_ID_TO_NAME[PROP_ID_locale] = PROP_NAME_locale;
          PROP_NAME_TO_ID.put(PROP_NAME_locale, PROP_ID_locale);
      
          PROP_ID_TO_NAME[PROP_ID_staticDict] = PROP_NAME_staticDict;
          PROP_NAME_TO_ID.put(PROP_NAME_staticDict, PROP_ID_staticDict);
      
          PROP_ID_TO_NAME[PROP_ID_normalized] = PROP_NAME_normalized;
          PROP_NAME_TO_ID.put(PROP_NAME_normalized, PROP_ID_normalized);
      
          PROP_ID_TO_NAME[PROP_ID_deprecated] = PROP_NAME_deprecated;
          PROP_NAME_TO_ID.put(PROP_NAME_deprecated, PROP_ID_deprecated);
      
          PROP_ID_TO_NAME[PROP_ID_internal] = PROP_NAME_internal;
          PROP_NAME_TO_ID.put(PROP_NAME_internal, PROP_ID_internal);
      
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

    
    /* 字典ID: META_DICT_ID */
    private java.lang.String _metaDictId;
    
    /* 模型ID: ORM_MODEL_ID */
    private java.lang.String _ormModelId;
    
    /* 是否Delta: IS_DELTA */
    private java.lang.Byte _isDelta;
    
    /* 字典名: DICT_NAME */
    private java.lang.String _dictName;
    
    /* 字典标签: LABEL */
    private java.lang.String _label;
    
    /* 值类型: VALUE_TYPE */
    private java.lang.String _valueType;
    
    /* 区域: LOCALE */
    private java.lang.String _locale;
    
    /* 静态字典: IS_STATIC */
    private java.lang.Byte _staticDict;
    
    /* 已标准化: NORMALIZED */
    private java.lang.Byte _normalized;
    
    /* 已废弃: DEPRECATED */
    private java.lang.Byte _deprecated;
    
    /* 内部使用: INTERNAL */
    private java.lang.Byte _internal;
    
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
    

    public _NopMetaDict(){
        // for debug
    }

    protected NopMetaDict newInstance(){
        NopMetaDict entity = new NopMetaDict();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopMetaDict cloneInstance() {
        NopMetaDict entity = newInstance();
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
      return "io.nop.metadata.dao.entity.NopMetaDict";
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
    
        return buildSimpleId(PROP_ID_metaDictId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_metaDictId;
          
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
        
            case PROP_ID_metaDictId:
               return getMetaDictId();
        
            case PROP_ID_ormModelId:
               return getOrmModelId();
        
            case PROP_ID_isDelta:
               return getIsDelta();
        
            case PROP_ID_dictName:
               return getDictName();
        
            case PROP_ID_label:
               return getLabel();
        
            case PROP_ID_valueType:
               return getValueType();
        
            case PROP_ID_locale:
               return getLocale();
        
            case PROP_ID_staticDict:
               return getStaticDict();
        
            case PROP_ID_normalized:
               return getNormalized();
        
            case PROP_ID_deprecated:
               return getDeprecated();
        
            case PROP_ID_internal:
               return getInternal();
        
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
        
            case PROP_ID_metaDictId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_metaDictId));
               }
               setMetaDictId(typedValue);
               break;
            }
        
            case PROP_ID_ormModelId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_ormModelId));
               }
               setOrmModelId(typedValue);
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
        
            case PROP_ID_dictName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_dictName));
               }
               setDictName(typedValue);
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
        
            case PROP_ID_valueType:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_valueType));
               }
               setValueType(typedValue);
               break;
            }
        
            case PROP_ID_locale:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_locale));
               }
               setLocale(typedValue);
               break;
            }
        
            case PROP_ID_staticDict:{
               java.lang.Byte typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toByte(value,
                       err-> newTypeConversionError(PROP_NAME_staticDict));
               }
               setStaticDict(typedValue);
               break;
            }
        
            case PROP_ID_normalized:{
               java.lang.Byte typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toByte(value,
                       err-> newTypeConversionError(PROP_NAME_normalized));
               }
               setNormalized(typedValue);
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
        
            case PROP_ID_metaDictId:{
               onInitProp(propId);
               this._metaDictId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_ormModelId:{
               onInitProp(propId);
               this._ormModelId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_isDelta:{
               onInitProp(propId);
               this._isDelta = (java.lang.Byte)value;
               
               break;
            }
        
            case PROP_ID_dictName:{
               onInitProp(propId);
               this._dictName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_label:{
               onInitProp(propId);
               this._label = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_valueType:{
               onInitProp(propId);
               this._valueType = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_locale:{
               onInitProp(propId);
               this._locale = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_staticDict:{
               onInitProp(propId);
               this._staticDict = (java.lang.Byte)value;
               
               break;
            }
        
            case PROP_ID_normalized:{
               onInitProp(propId);
               this._normalized = (java.lang.Byte)value;
               
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
            orm_id();
        }
    }
    
    /**
     * 模型ID: ORM_MODEL_ID
     */
    public final java.lang.String getOrmModelId(){
         onPropGet(PROP_ID_ormModelId);
         return _ormModelId;
    }

    /**
     * 模型ID: ORM_MODEL_ID
     */
    public final void setOrmModelId(java.lang.String value){
        if(onPropSet(PROP_ID_ormModelId,value)){
            this._ormModelId = value;
            internalClearRefs(PROP_ID_ormModelId);
            
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
     * 字典名: DICT_NAME
     */
    public final java.lang.String getDictName(){
         onPropGet(PROP_ID_dictName);
         return _dictName;
    }

    /**
     * 字典名: DICT_NAME
     */
    public final void setDictName(java.lang.String value){
        if(onPropSet(PROP_ID_dictName,value)){
            this._dictName = value;
            internalClearRefs(PROP_ID_dictName);
            
        }
    }
    
    /**
     * 字典标签: LABEL
     */
    public final java.lang.String getLabel(){
         onPropGet(PROP_ID_label);
         return _label;
    }

    /**
     * 字典标签: LABEL
     */
    public final void setLabel(java.lang.String value){
        if(onPropSet(PROP_ID_label,value)){
            this._label = value;
            internalClearRefs(PROP_ID_label);
            
        }
    }
    
    /**
     * 值类型: VALUE_TYPE
     */
    public final java.lang.String getValueType(){
         onPropGet(PROP_ID_valueType);
         return _valueType;
    }

    /**
     * 值类型: VALUE_TYPE
     */
    public final void setValueType(java.lang.String value){
        if(onPropSet(PROP_ID_valueType,value)){
            this._valueType = value;
            internalClearRefs(PROP_ID_valueType);
            
        }
    }
    
    /**
     * 区域: LOCALE
     */
    public final java.lang.String getLocale(){
         onPropGet(PROP_ID_locale);
         return _locale;
    }

    /**
     * 区域: LOCALE
     */
    public final void setLocale(java.lang.String value){
        if(onPropSet(PROP_ID_locale,value)){
            this._locale = value;
            internalClearRefs(PROP_ID_locale);
            
        }
    }
    
    /**
     * 静态字典: IS_STATIC
     */
    public final java.lang.Byte getStaticDict(){
         onPropGet(PROP_ID_staticDict);
         return _staticDict;
    }

    /**
     * 静态字典: IS_STATIC
     */
    public final void setStaticDict(java.lang.Byte value){
        if(onPropSet(PROP_ID_staticDict,value)){
            this._staticDict = value;
            internalClearRefs(PROP_ID_staticDict);
            
        }
    }
    
    /**
     * 已标准化: NORMALIZED
     */
    public final java.lang.Byte getNormalized(){
         onPropGet(PROP_ID_normalized);
         return _normalized;
    }

    /**
     * 已标准化: NORMALIZED
     */
    public final void setNormalized(java.lang.Byte value){
        if(onPropSet(PROP_ID_normalized,value)){
            this._normalized = value;
            internalClearRefs(PROP_ID_normalized);
            
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
     * ORM模型
     */
    public final io.nop.metadata.dao.entity.NopMetaOrmModel getOrmModel(){
       return (io.nop.metadata.dao.entity.NopMetaOrmModel)internalGetRefEntity(PROP_NAME_ormModel);
    }

    public final void setOrmModel(io.nop.metadata.dao.entity.NopMetaOrmModel refEntity){
   
           if(refEntity == null){
           
                   this.setOrmModelId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_ormModel, refEntity,()->{
           
                           this.setOrmModelId(refEntity.getOrmModelId());
                       
           });
           }
       
    }
       
    private final OrmEntitySet<io.nop.metadata.dao.entity.NopMetaDictItem> _dictItems = new OrmEntitySet<>(this, PROP_NAME_dictItems,
        io.nop.metadata.dao.entity.NopMetaDictItem.PROP_NAME_metaDict, null,io.nop.metadata.dao.entity.NopMetaDictItem.class);

    /**
     * 字典项集。 refPropName: metaDict, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<io.nop.metadata.dao.entity.NopMetaDictItem> getDictItems(){
       return _dictItems;
    }
       
}
// resume CPD analysis - CPD-ON
