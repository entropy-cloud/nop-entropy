package io.nop.sys.dao.entity._gen;

import io.nop.orm.model.IEntityModel;
import io.nop.orm.support.DynamicOrmEntity;
import io.nop.orm.support.OrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.orm.IOrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.api.core.convert.ConvertHelper;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;

import io.nop.sys.dao.entity.NopSysDictOption;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  字典明细: nop_sys_dict_option
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101"})
public class _NopSysDictOption extends DynamicOrmEntity{
    
    /* 主键: SID VARCHAR */
    public static final String PROP_NAME_sid = "sid";
    public static final int PROP_ID_sid = 1;
    
    /* 字典ID: DICT_ID VARCHAR */
    public static final String PROP_NAME_dictId = "dictId";
    public static final int PROP_ID_dictId = 2;
    
    /* 显示名: LABEL VARCHAR */
    public static final String PROP_NAME_label = "label";
    public static final int PROP_ID_label = 3;
    
    /* 值: VALUE VARCHAR */
    public static final String PROP_NAME_value = "value";
    public static final int PROP_ID_value = 4;
    
    /* 内部编码: CODE_VALUE VARCHAR */
    public static final String PROP_NAME_codeValue = "codeValue";
    public static final int PROP_ID_codeValue = 5;
    
    /* 分组名: GROUP_NAME VARCHAR */
    public static final String PROP_NAME_groupName = "groupName";
    public static final int PROP_ID_groupName = 6;
    
    /* 是否内部: IS_INTERNAL TINYINT */
    public static final String PROP_NAME_isInternal = "isInternal";
    public static final int PROP_ID_isInternal = 7;
    
    /* 是否已废弃: IS_DEPRECATED TINYINT */
    public static final String PROP_NAME_isDeprecated = "isDeprecated";
    public static final int PROP_ID_isDeprecated = 8;
    
    /* 删除标识: DEL_FLAG TINYINT */
    public static final String PROP_NAME_delFlag = "delFlag";
    public static final int PROP_ID_delFlag = 9;
    
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

    
    /* relation: 字典 */
    public static final String PROP_NAME_dict = "dict";
    

    public static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_sid);
    public static final int[] PK_PROP_IDS = new int[]{PROP_ID_sid};

    private static final String[] PROP_ID_TO_NAME = new String[16];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_sid] = PROP_NAME_sid;
          PROP_NAME_TO_ID.put(PROP_NAME_sid, PROP_ID_sid);
      
          PROP_ID_TO_NAME[PROP_ID_dictId] = PROP_NAME_dictId;
          PROP_NAME_TO_ID.put(PROP_NAME_dictId, PROP_ID_dictId);
      
          PROP_ID_TO_NAME[PROP_ID_label] = PROP_NAME_label;
          PROP_NAME_TO_ID.put(PROP_NAME_label, PROP_ID_label);
      
          PROP_ID_TO_NAME[PROP_ID_value] = PROP_NAME_value;
          PROP_NAME_TO_ID.put(PROP_NAME_value, PROP_ID_value);
      
          PROP_ID_TO_NAME[PROP_ID_codeValue] = PROP_NAME_codeValue;
          PROP_NAME_TO_ID.put(PROP_NAME_codeValue, PROP_ID_codeValue);
      
          PROP_ID_TO_NAME[PROP_ID_groupName] = PROP_NAME_groupName;
          PROP_NAME_TO_ID.put(PROP_NAME_groupName, PROP_ID_groupName);
      
          PROP_ID_TO_NAME[PROP_ID_isInternal] = PROP_NAME_isInternal;
          PROP_NAME_TO_ID.put(PROP_NAME_isInternal, PROP_ID_isInternal);
      
          PROP_ID_TO_NAME[PROP_ID_isDeprecated] = PROP_NAME_isDeprecated;
          PROP_NAME_TO_ID.put(PROP_NAME_isDeprecated, PROP_ID_isDeprecated);
      
          PROP_ID_TO_NAME[PROP_ID_delFlag] = PROP_NAME_delFlag;
          PROP_NAME_TO_ID.put(PROP_NAME_delFlag, PROP_ID_delFlag);
      
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

    
    /* 主键: SID */
    private java.lang.String _sid;
    
    /* 字典ID: DICT_ID */
    private java.lang.String _dictId;
    
    /* 显示名: LABEL */
    private java.lang.String _label;
    
    /* 值: VALUE */
    private java.lang.String _value;
    
    /* 内部编码: CODE_VALUE */
    private java.lang.String _codeValue;
    
    /* 分组名: GROUP_NAME */
    private java.lang.String _groupName;
    
    /* 是否内部: IS_INTERNAL */
    private java.lang.Byte _isInternal;
    
    /* 是否已废弃: IS_DEPRECATED */
    private java.lang.Byte _isDeprecated;
    
    /* 删除标识: DEL_FLAG */
    private java.lang.Byte _delFlag;
    
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
    

    public _NopSysDictOption(){
    }

    protected NopSysDictOption newInstance(){
       return new NopSysDictOption();
    }

    @Override
    public NopSysDictOption cloneInstance() {
        NopSysDictOption entity = newInstance();
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
      return "io.nop.sys.dao.entity.NopSysDictOption";
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
        
            case PROP_ID_dictId:
               return getDictId();
        
            case PROP_ID_label:
               return getLabel();
        
            case PROP_ID_value:
               return getValue();
        
            case PROP_ID_codeValue:
               return getCodeValue();
        
            case PROP_ID_groupName:
               return getGroupName();
        
            case PROP_ID_isInternal:
               return getIsInternal();
        
            case PROP_ID_isDeprecated:
               return getIsDeprecated();
        
            case PROP_ID_delFlag:
               return getDelFlag();
        
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
        
            case PROP_ID_dictId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_dictId));
               }
               setDictId(typedValue);
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
        
            case PROP_ID_value:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_value));
               }
               setValue(typedValue);
               break;
            }
        
            case PROP_ID_codeValue:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_codeValue));
               }
               setCodeValue(typedValue);
               break;
            }
        
            case PROP_ID_groupName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_groupName));
               }
               setGroupName(typedValue);
               break;
            }
        
            case PROP_ID_isInternal:{
               java.lang.Byte typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toByte(value,
                       err-> newTypeConversionError(PROP_NAME_isInternal));
               }
               setIsInternal(typedValue);
               break;
            }
        
            case PROP_ID_isDeprecated:{
               java.lang.Byte typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toByte(value,
                       err-> newTypeConversionError(PROP_NAME_isDeprecated));
               }
               setIsDeprecated(typedValue);
               break;
            }
        
            case PROP_ID_delFlag:{
               java.lang.Byte typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toByte(value,
                       err-> newTypeConversionError(PROP_NAME_delFlag));
               }
               setDelFlag(typedValue);
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
        
            case PROP_ID_dictId:{
               onInitProp(propId);
               this._dictId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_label:{
               onInitProp(propId);
               this._label = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_value:{
               onInitProp(propId);
               this._value = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_codeValue:{
               onInitProp(propId);
               this._codeValue = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_groupName:{
               onInitProp(propId);
               this._groupName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_isInternal:{
               onInitProp(propId);
               this._isInternal = (java.lang.Byte)value;
               
               break;
            }
        
            case PROP_ID_isDeprecated:{
               onInitProp(propId);
               this._isDeprecated = (java.lang.Byte)value;
               
               break;
            }
        
            case PROP_ID_delFlag:{
               onInitProp(propId);
               this._delFlag = (java.lang.Byte)value;
               
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
     * 主键: SID
     */
    public java.lang.String getSid(){
         onPropGet(PROP_ID_sid);
         return _sid;
    }

    /**
     * 主键: SID
     */
    public void setSid(java.lang.String value){
        if(onPropSet(PROP_ID_sid,value)){
            this._sid = value;
            internalClearRefs(PROP_ID_sid);
            orm_id();
        }
    }
    
    /**
     * 字典ID: DICT_ID
     */
    public java.lang.String getDictId(){
         onPropGet(PROP_ID_dictId);
         return _dictId;
    }

    /**
     * 字典ID: DICT_ID
     */
    public void setDictId(java.lang.String value){
        if(onPropSet(PROP_ID_dictId,value)){
            this._dictId = value;
            internalClearRefs(PROP_ID_dictId);
            
        }
    }
    
    /**
     * 显示名: LABEL
     */
    public java.lang.String getLabel(){
         onPropGet(PROP_ID_label);
         return _label;
    }

    /**
     * 显示名: LABEL
     */
    public void setLabel(java.lang.String value){
        if(onPropSet(PROP_ID_label,value)){
            this._label = value;
            internalClearRefs(PROP_ID_label);
            
        }
    }
    
    /**
     * 值: VALUE
     */
    public java.lang.String getValue(){
         onPropGet(PROP_ID_value);
         return _value;
    }

    /**
     * 值: VALUE
     */
    public void setValue(java.lang.String value){
        if(onPropSet(PROP_ID_value,value)){
            this._value = value;
            internalClearRefs(PROP_ID_value);
            
        }
    }
    
    /**
     * 内部编码: CODE_VALUE
     */
    public java.lang.String getCodeValue(){
         onPropGet(PROP_ID_codeValue);
         return _codeValue;
    }

    /**
     * 内部编码: CODE_VALUE
     */
    public void setCodeValue(java.lang.String value){
        if(onPropSet(PROP_ID_codeValue,value)){
            this._codeValue = value;
            internalClearRefs(PROP_ID_codeValue);
            
        }
    }
    
    /**
     * 分组名: GROUP_NAME
     */
    public java.lang.String getGroupName(){
         onPropGet(PROP_ID_groupName);
         return _groupName;
    }

    /**
     * 分组名: GROUP_NAME
     */
    public void setGroupName(java.lang.String value){
        if(onPropSet(PROP_ID_groupName,value)){
            this._groupName = value;
            internalClearRefs(PROP_ID_groupName);
            
        }
    }
    
    /**
     * 是否内部: IS_INTERNAL
     */
    public java.lang.Byte getIsInternal(){
         onPropGet(PROP_ID_isInternal);
         return _isInternal;
    }

    /**
     * 是否内部: IS_INTERNAL
     */
    public void setIsInternal(java.lang.Byte value){
        if(onPropSet(PROP_ID_isInternal,value)){
            this._isInternal = value;
            internalClearRefs(PROP_ID_isInternal);
            
        }
    }
    
    /**
     * 是否已废弃: IS_DEPRECATED
     */
    public java.lang.Byte getIsDeprecated(){
         onPropGet(PROP_ID_isDeprecated);
         return _isDeprecated;
    }

    /**
     * 是否已废弃: IS_DEPRECATED
     */
    public void setIsDeprecated(java.lang.Byte value){
        if(onPropSet(PROP_ID_isDeprecated,value)){
            this._isDeprecated = value;
            internalClearRefs(PROP_ID_isDeprecated);
            
        }
    }
    
    /**
     * 删除标识: DEL_FLAG
     */
    public java.lang.Byte getDelFlag(){
         onPropGet(PROP_ID_delFlag);
         return _delFlag;
    }

    /**
     * 删除标识: DEL_FLAG
     */
    public void setDelFlag(java.lang.Byte value){
        if(onPropSet(PROP_ID_delFlag,value)){
            this._delFlag = value;
            internalClearRefs(PROP_ID_delFlag);
            
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
     * 字典
     */
    public io.nop.sys.dao.entity.NopSysDict getDict(){
       return (io.nop.sys.dao.entity.NopSysDict)internalGetRefEntity(PROP_NAME_dict);
    }

    public void setDict(io.nop.sys.dao.entity.NopSysDict refEntity){
       if(refEntity == null){
         
         this.setDictId(null);
         
       }else{
          internalSetRefEntity(PROP_NAME_dict, refEntity,()->{
             
                    this.setDictId(refEntity.getSid());
                 
          });
       }
    }
       
}
// resume CPD analysis - CPD-ON
