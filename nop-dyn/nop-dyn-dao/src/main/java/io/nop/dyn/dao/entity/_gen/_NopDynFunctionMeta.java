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

import io.nop.dyn.dao.entity.NopDynFunctionMeta;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  实体函数定义: nop_dyn_function_meta
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopDynFunctionMeta extends DynamicOrmEntity{
    
    /* 函数定义ID: FUNC_META_ID VARCHAR */
    public static final String PROP_NAME_funcMetaId = "funcMetaId";
    public static final int PROP_ID_funcMetaId = 1;
    
    /* 实体定义ID: ENTITY_META_ID VARCHAR */
    public static final String PROP_NAME_entityMetaId = "entityMetaId";
    public static final int PROP_ID_entityMetaId = 2;
    
    /* 函数名: NAME VARCHAR */
    public static final String PROP_NAME_name = "name";
    public static final int PROP_ID_name = 3;
    
    /* 显示名: DISPLAY_NAME VARCHAR */
    public static final String PROP_NAME_displayName = "displayName";
    public static final int PROP_ID_displayName = 4;
    
    /* 函数类型: FUNCTION_TYPE VARCHAR */
    public static final String PROP_NAME_functionType = "functionType";
    public static final int PROP_ID_functionType = 5;
    
    /* 返回类型: RETURN_TYPE VARCHAR */
    public static final String PROP_NAME_returnType = "returnType";
    public static final int PROP_ID_returnType = 6;
    
    /* GraphQL返回类型: RETURN_GQL_TYPE VARCHAR */
    public static final String PROP_NAME_returnGqlType = "returnGqlType";
    public static final int PROP_ID_returnGqlType = 7;
    
    /* 状态: STATUS INTEGER */
    public static final String PROP_NAME_status = "status";
    public static final int PROP_ID_status = 8;
    
    /* 标签: TAGS_TEXT VARCHAR */
    public static final String PROP_NAME_tagsText = "tagsText";
    public static final int PROP_ID_tagsText = 9;
    
    /* 脚本语言: SCRIPT_LANG VARCHAR */
    public static final String PROP_NAME_scriptLang = "scriptLang";
    public static final int PROP_ID_scriptLang = 10;
    
    /* 函数元数据: FUNC_META VARCHAR */
    public static final String PROP_NAME_funcMeta = "funcMeta";
    public static final int PROP_ID_funcMeta = 11;
    
    /* 源码: SOURCE VARCHAR */
    public static final String PROP_NAME_source = "source";
    public static final int PROP_ID_source = 12;
    
    /* 数据版本: VERSION INTEGER */
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

    
    /* relation: 所属模块 */
    public static final String PROP_NAME_entityMeta = "entityMeta";
    
    /* component:  */
    public static final String PROP_NAME_funcMetaComponent = "funcMetaComponent";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_funcMetaId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_funcMetaId};

    private static final String[] PROP_ID_TO_NAME = new String[19];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_funcMetaId] = PROP_NAME_funcMetaId;
          PROP_NAME_TO_ID.put(PROP_NAME_funcMetaId, PROP_ID_funcMetaId);
      
          PROP_ID_TO_NAME[PROP_ID_entityMetaId] = PROP_NAME_entityMetaId;
          PROP_NAME_TO_ID.put(PROP_NAME_entityMetaId, PROP_ID_entityMetaId);
      
          PROP_ID_TO_NAME[PROP_ID_name] = PROP_NAME_name;
          PROP_NAME_TO_ID.put(PROP_NAME_name, PROP_ID_name);
      
          PROP_ID_TO_NAME[PROP_ID_displayName] = PROP_NAME_displayName;
          PROP_NAME_TO_ID.put(PROP_NAME_displayName, PROP_ID_displayName);
      
          PROP_ID_TO_NAME[PROP_ID_functionType] = PROP_NAME_functionType;
          PROP_NAME_TO_ID.put(PROP_NAME_functionType, PROP_ID_functionType);
      
          PROP_ID_TO_NAME[PROP_ID_returnType] = PROP_NAME_returnType;
          PROP_NAME_TO_ID.put(PROP_NAME_returnType, PROP_ID_returnType);
      
          PROP_ID_TO_NAME[PROP_ID_returnGqlType] = PROP_NAME_returnGqlType;
          PROP_NAME_TO_ID.put(PROP_NAME_returnGqlType, PROP_ID_returnGqlType);
      
          PROP_ID_TO_NAME[PROP_ID_status] = PROP_NAME_status;
          PROP_NAME_TO_ID.put(PROP_NAME_status, PROP_ID_status);
      
          PROP_ID_TO_NAME[PROP_ID_tagsText] = PROP_NAME_tagsText;
          PROP_NAME_TO_ID.put(PROP_NAME_tagsText, PROP_ID_tagsText);
      
          PROP_ID_TO_NAME[PROP_ID_scriptLang] = PROP_NAME_scriptLang;
          PROP_NAME_TO_ID.put(PROP_NAME_scriptLang, PROP_ID_scriptLang);
      
          PROP_ID_TO_NAME[PROP_ID_funcMeta] = PROP_NAME_funcMeta;
          PROP_NAME_TO_ID.put(PROP_NAME_funcMeta, PROP_ID_funcMeta);
      
          PROP_ID_TO_NAME[PROP_ID_source] = PROP_NAME_source;
          PROP_NAME_TO_ID.put(PROP_NAME_source, PROP_ID_source);
      
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

    
    /* 函数定义ID: FUNC_META_ID */
    private java.lang.String _funcMetaId;
    
    /* 实体定义ID: ENTITY_META_ID */
    private java.lang.String _entityMetaId;
    
    /* 函数名: NAME */
    private java.lang.String _name;
    
    /* 显示名: DISPLAY_NAME */
    private java.lang.String _displayName;
    
    /* 函数类型: FUNCTION_TYPE */
    private java.lang.String _functionType;
    
    /* 返回类型: RETURN_TYPE */
    private java.lang.String _returnType;
    
    /* GraphQL返回类型: RETURN_GQL_TYPE */
    private java.lang.String _returnGqlType;
    
    /* 状态: STATUS */
    private java.lang.Integer _status;
    
    /* 标签: TAGS_TEXT */
    private java.lang.String _tagsText;
    
    /* 脚本语言: SCRIPT_LANG */
    private java.lang.String _scriptLang;
    
    /* 函数元数据: FUNC_META */
    private java.lang.String _funcMeta;
    
    /* 源码: SOURCE */
    private java.lang.String _source;
    
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
    

    public _NopDynFunctionMeta(){
        // for debug
    }

    protected NopDynFunctionMeta newInstance(){
        NopDynFunctionMeta entity = new NopDynFunctionMeta();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopDynFunctionMeta cloneInstance() {
        NopDynFunctionMeta entity = newInstance();
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
      return "io.nop.dyn.dao.entity.NopDynFunctionMeta";
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
    
        return buildSimpleId(PROP_ID_funcMetaId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_funcMetaId;
          
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
        
            case PROP_ID_funcMetaId:
               return getFuncMetaId();
        
            case PROP_ID_entityMetaId:
               return getEntityMetaId();
        
            case PROP_ID_name:
               return getName();
        
            case PROP_ID_displayName:
               return getDisplayName();
        
            case PROP_ID_functionType:
               return getFunctionType();
        
            case PROP_ID_returnType:
               return getReturnType();
        
            case PROP_ID_returnGqlType:
               return getReturnGqlType();
        
            case PROP_ID_status:
               return getStatus();
        
            case PROP_ID_tagsText:
               return getTagsText();
        
            case PROP_ID_scriptLang:
               return getScriptLang();
        
            case PROP_ID_funcMeta:
               return getFuncMeta();
        
            case PROP_ID_source:
               return getSource();
        
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
        
            case PROP_ID_funcMetaId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_funcMetaId));
               }
               setFuncMetaId(typedValue);
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
        
            case PROP_ID_functionType:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_functionType));
               }
               setFunctionType(typedValue);
               break;
            }
        
            case PROP_ID_returnType:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_returnType));
               }
               setReturnType(typedValue);
               break;
            }
        
            case PROP_ID_returnGqlType:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_returnGqlType));
               }
               setReturnGqlType(typedValue);
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
        
            case PROP_ID_scriptLang:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_scriptLang));
               }
               setScriptLang(typedValue);
               break;
            }
        
            case PROP_ID_funcMeta:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_funcMeta));
               }
               setFuncMeta(typedValue);
               break;
            }
        
            case PROP_ID_source:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_source));
               }
               setSource(typedValue);
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
        
            case PROP_ID_funcMetaId:{
               onInitProp(propId);
               this._funcMetaId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_entityMetaId:{
               onInitProp(propId);
               this._entityMetaId = (java.lang.String)value;
               
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
        
            case PROP_ID_functionType:{
               onInitProp(propId);
               this._functionType = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_returnType:{
               onInitProp(propId);
               this._returnType = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_returnGqlType:{
               onInitProp(propId);
               this._returnGqlType = (java.lang.String)value;
               
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
        
            case PROP_ID_scriptLang:{
               onInitProp(propId);
               this._scriptLang = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_funcMeta:{
               onInitProp(propId);
               this._funcMeta = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_source:{
               onInitProp(propId);
               this._source = (java.lang.String)value;
               
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
     * 函数定义ID: FUNC_META_ID
     */
    public java.lang.String getFuncMetaId(){
         onPropGet(PROP_ID_funcMetaId);
         return _funcMetaId;
    }

    /**
     * 函数定义ID: FUNC_META_ID
     */
    public void setFuncMetaId(java.lang.String value){
        if(onPropSet(PROP_ID_funcMetaId,value)){
            this._funcMetaId = value;
            internalClearRefs(PROP_ID_funcMetaId);
            orm_id();
        }
    }
    
    /**
     * 实体定义ID: ENTITY_META_ID
     */
    public java.lang.String getEntityMetaId(){
         onPropGet(PROP_ID_entityMetaId);
         return _entityMetaId;
    }

    /**
     * 实体定义ID: ENTITY_META_ID
     */
    public void setEntityMetaId(java.lang.String value){
        if(onPropSet(PROP_ID_entityMetaId,value)){
            this._entityMetaId = value;
            internalClearRefs(PROP_ID_entityMetaId);
            
        }
    }
    
    /**
     * 函数名: NAME
     */
    public java.lang.String getName(){
         onPropGet(PROP_ID_name);
         return _name;
    }

    /**
     * 函数名: NAME
     */
    public void setName(java.lang.String value){
        if(onPropSet(PROP_ID_name,value)){
            this._name = value;
            internalClearRefs(PROP_ID_name);
            
        }
    }
    
    /**
     * 显示名: DISPLAY_NAME
     */
    public java.lang.String getDisplayName(){
         onPropGet(PROP_ID_displayName);
         return _displayName;
    }

    /**
     * 显示名: DISPLAY_NAME
     */
    public void setDisplayName(java.lang.String value){
        if(onPropSet(PROP_ID_displayName,value)){
            this._displayName = value;
            internalClearRefs(PROP_ID_displayName);
            
        }
    }
    
    /**
     * 函数类型: FUNCTION_TYPE
     */
    public java.lang.String getFunctionType(){
         onPropGet(PROP_ID_functionType);
         return _functionType;
    }

    /**
     * 函数类型: FUNCTION_TYPE
     */
    public void setFunctionType(java.lang.String value){
        if(onPropSet(PROP_ID_functionType,value)){
            this._functionType = value;
            internalClearRefs(PROP_ID_functionType);
            
        }
    }
    
    /**
     * 返回类型: RETURN_TYPE
     */
    public java.lang.String getReturnType(){
         onPropGet(PROP_ID_returnType);
         return _returnType;
    }

    /**
     * 返回类型: RETURN_TYPE
     */
    public void setReturnType(java.lang.String value){
        if(onPropSet(PROP_ID_returnType,value)){
            this._returnType = value;
            internalClearRefs(PROP_ID_returnType);
            
        }
    }
    
    /**
     * GraphQL返回类型: RETURN_GQL_TYPE
     */
    public java.lang.String getReturnGqlType(){
         onPropGet(PROP_ID_returnGqlType);
         return _returnGqlType;
    }

    /**
     * GraphQL返回类型: RETURN_GQL_TYPE
     */
    public void setReturnGqlType(java.lang.String value){
        if(onPropSet(PROP_ID_returnGqlType,value)){
            this._returnGqlType = value;
            internalClearRefs(PROP_ID_returnGqlType);
            
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
     * 标签: TAGS_TEXT
     */
    public java.lang.String getTagsText(){
         onPropGet(PROP_ID_tagsText);
         return _tagsText;
    }

    /**
     * 标签: TAGS_TEXT
     */
    public void setTagsText(java.lang.String value){
        if(onPropSet(PROP_ID_tagsText,value)){
            this._tagsText = value;
            internalClearRefs(PROP_ID_tagsText);
            
        }
    }
    
    /**
     * 脚本语言: SCRIPT_LANG
     */
    public java.lang.String getScriptLang(){
         onPropGet(PROP_ID_scriptLang);
         return _scriptLang;
    }

    /**
     * 脚本语言: SCRIPT_LANG
     */
    public void setScriptLang(java.lang.String value){
        if(onPropSet(PROP_ID_scriptLang,value)){
            this._scriptLang = value;
            internalClearRefs(PROP_ID_scriptLang);
            
        }
    }
    
    /**
     * 函数元数据: FUNC_META
     */
    public java.lang.String getFuncMeta(){
         onPropGet(PROP_ID_funcMeta);
         return _funcMeta;
    }

    /**
     * 函数元数据: FUNC_META
     */
    public void setFuncMeta(java.lang.String value){
        if(onPropSet(PROP_ID_funcMeta,value)){
            this._funcMeta = value;
            internalClearRefs(PROP_ID_funcMeta);
            
        }
    }
    
    /**
     * 源码: SOURCE
     */
    public java.lang.String getSource(){
         onPropGet(PROP_ID_source);
         return _source;
    }

    /**
     * 源码: SOURCE
     */
    public void setSource(java.lang.String value){
        if(onPropSet(PROP_ID_source,value)){
            this._source = value;
            internalClearRefs(PROP_ID_source);
            
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
     * 所属模块
     */
    public io.nop.dyn.dao.entity.NopDynEntityMeta getEntityMeta(){
       return (io.nop.dyn.dao.entity.NopDynEntityMeta)internalGetRefEntity(PROP_NAME_entityMeta);
    }

    public void setEntityMeta(io.nop.dyn.dao.entity.NopDynEntityMeta refEntity){
   
           if(refEntity == null){
           
                   this.setEntityMetaId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_entityMeta, refEntity,()->{
           
                           this.setEntityMetaId(refEntity.getEntityMetaId());
                       
           });
           }
       
    }
       
   private io.nop.orm.component.JsonOrmComponent _funcMetaComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_funcMetaComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_funcMetaComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_funcMeta);
      
   }

   public io.nop.orm.component.JsonOrmComponent getFuncMetaComponent(){
      if(_funcMetaComponent == null){
          _funcMetaComponent = new io.nop.orm.component.JsonOrmComponent();
          _funcMetaComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_funcMetaComponent);
      }
      return _funcMetaComponent;
   }

}
// resume CPD analysis - CPD-ON
