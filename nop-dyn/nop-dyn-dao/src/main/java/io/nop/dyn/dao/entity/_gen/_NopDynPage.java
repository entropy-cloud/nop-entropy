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

import io.nop.dyn.dao.entity.NopDynPage;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  页面定义: nop_dyn_page
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopDynPage extends DynamicOrmEntity{
    
    /* 页面ID: PAGE_ID VARCHAR */
    public static final String PROP_NAME_pageId = "pageId";
    public static final int PROP_ID_pageId = 1;
    
    /* 模块ID: MODULE_ID VARCHAR */
    public static final String PROP_NAME_moduleId = "moduleId";
    public static final int PROP_ID_moduleId = 2;
    
    /* 页面名称: PAGE_NAME VARCHAR */
    public static final String PROP_NAME_pageName = "pageName";
    public static final int PROP_ID_pageName = 3;
    
    /* 页面分组: PAGE_GROUP VARCHAR */
    public static final String PROP_NAME_pageGroup = "pageGroup";
    public static final int PROP_ID_pageGroup = 4;
    
    /* 页面类型: PAGE_SCHEMA_TYPE VARCHAR */
    public static final String PROP_NAME_pageSchemaType = "pageSchemaType";
    public static final int PROP_ID_pageSchemaType = 5;
    
    /* 页面内容: PAGE_CONTENT VARCHAR */
    public static final String PROP_NAME_pageContent = "pageContent";
    public static final int PROP_ID_pageContent = 6;
    
    /* 状态: STATUS INTEGER */
    public static final String PROP_NAME_status = "status";
    public static final int PROP_ID_status = 7;
    
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

    
    /* relation: 所属模块 */
    public static final String PROP_NAME_module = "module";
    
    /* component:  */
    public static final String PROP_NAME_pageContentComponent = "pageContentComponent";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_pageId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_pageId};

    private static final String[] PROP_ID_TO_NAME = new String[16];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_pageId] = PROP_NAME_pageId;
          PROP_NAME_TO_ID.put(PROP_NAME_pageId, PROP_ID_pageId);
      
          PROP_ID_TO_NAME[PROP_ID_moduleId] = PROP_NAME_moduleId;
          PROP_NAME_TO_ID.put(PROP_NAME_moduleId, PROP_ID_moduleId);
      
          PROP_ID_TO_NAME[PROP_ID_pageName] = PROP_NAME_pageName;
          PROP_NAME_TO_ID.put(PROP_NAME_pageName, PROP_ID_pageName);
      
          PROP_ID_TO_NAME[PROP_ID_pageGroup] = PROP_NAME_pageGroup;
          PROP_NAME_TO_ID.put(PROP_NAME_pageGroup, PROP_ID_pageGroup);
      
          PROP_ID_TO_NAME[PROP_ID_pageSchemaType] = PROP_NAME_pageSchemaType;
          PROP_NAME_TO_ID.put(PROP_NAME_pageSchemaType, PROP_ID_pageSchemaType);
      
          PROP_ID_TO_NAME[PROP_ID_pageContent] = PROP_NAME_pageContent;
          PROP_NAME_TO_ID.put(PROP_NAME_pageContent, PROP_ID_pageContent);
      
          PROP_ID_TO_NAME[PROP_ID_status] = PROP_NAME_status;
          PROP_NAME_TO_ID.put(PROP_NAME_status, PROP_ID_status);
      
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

    
    /* 页面ID: PAGE_ID */
    private java.lang.String _pageId;
    
    /* 模块ID: MODULE_ID */
    private java.lang.String _moduleId;
    
    /* 页面名称: PAGE_NAME */
    private java.lang.String _pageName;
    
    /* 页面分组: PAGE_GROUP */
    private java.lang.String _pageGroup;
    
    /* 页面类型: PAGE_SCHEMA_TYPE */
    private java.lang.String _pageSchemaType;
    
    /* 页面内容: PAGE_CONTENT */
    private java.lang.String _pageContent;
    
    /* 状态: STATUS */
    private java.lang.Integer _status;
    
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
    

    public _NopDynPage(){
        // for debug
    }

    protected NopDynPage newInstance(){
        NopDynPage entity = new NopDynPage();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopDynPage cloneInstance() {
        NopDynPage entity = newInstance();
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
      return "io.nop.dyn.dao.entity.NopDynPage";
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
    
        return buildSimpleId(PROP_ID_pageId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_pageId;
          
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
        
            case PROP_ID_pageId:
               return getPageId();
        
            case PROP_ID_moduleId:
               return getModuleId();
        
            case PROP_ID_pageName:
               return getPageName();
        
            case PROP_ID_pageGroup:
               return getPageGroup();
        
            case PROP_ID_pageSchemaType:
               return getPageSchemaType();
        
            case PROP_ID_pageContent:
               return getPageContent();
        
            case PROP_ID_status:
               return getStatus();
        
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
        
            case PROP_ID_pageId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_pageId));
               }
               setPageId(typedValue);
               break;
            }
        
            case PROP_ID_moduleId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_moduleId));
               }
               setModuleId(typedValue);
               break;
            }
        
            case PROP_ID_pageName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_pageName));
               }
               setPageName(typedValue);
               break;
            }
        
            case PROP_ID_pageGroup:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_pageGroup));
               }
               setPageGroup(typedValue);
               break;
            }
        
            case PROP_ID_pageSchemaType:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_pageSchemaType));
               }
               setPageSchemaType(typedValue);
               break;
            }
        
            case PROP_ID_pageContent:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_pageContent));
               }
               setPageContent(typedValue);
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
        
            case PROP_ID_pageId:{
               onInitProp(propId);
               this._pageId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_moduleId:{
               onInitProp(propId);
               this._moduleId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_pageName:{
               onInitProp(propId);
               this._pageName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_pageGroup:{
               onInitProp(propId);
               this._pageGroup = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_pageSchemaType:{
               onInitProp(propId);
               this._pageSchemaType = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_pageContent:{
               onInitProp(propId);
               this._pageContent = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_status:{
               onInitProp(propId);
               this._status = (java.lang.Integer)value;
               
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
     * 页面ID: PAGE_ID
     */
    public final java.lang.String getPageId(){
         onPropGet(PROP_ID_pageId);
         return _pageId;
    }

    /**
     * 页面ID: PAGE_ID
     */
    public final void setPageId(java.lang.String value){
        if(onPropSet(PROP_ID_pageId,value)){
            this._pageId = value;
            internalClearRefs(PROP_ID_pageId);
            orm_id();
        }
    }
    
    /**
     * 模块ID: MODULE_ID
     */
    public final java.lang.String getModuleId(){
         onPropGet(PROP_ID_moduleId);
         return _moduleId;
    }

    /**
     * 模块ID: MODULE_ID
     */
    public final void setModuleId(java.lang.String value){
        if(onPropSet(PROP_ID_moduleId,value)){
            this._moduleId = value;
            internalClearRefs(PROP_ID_moduleId);
            
        }
    }
    
    /**
     * 页面名称: PAGE_NAME
     */
    public final java.lang.String getPageName(){
         onPropGet(PROP_ID_pageName);
         return _pageName;
    }

    /**
     * 页面名称: PAGE_NAME
     */
    public final void setPageName(java.lang.String value){
        if(onPropSet(PROP_ID_pageName,value)){
            this._pageName = value;
            internalClearRefs(PROP_ID_pageName);
            
        }
    }
    
    /**
     * 页面分组: PAGE_GROUP
     */
    public final java.lang.String getPageGroup(){
         onPropGet(PROP_ID_pageGroup);
         return _pageGroup;
    }

    /**
     * 页面分组: PAGE_GROUP
     */
    public final void setPageGroup(java.lang.String value){
        if(onPropSet(PROP_ID_pageGroup,value)){
            this._pageGroup = value;
            internalClearRefs(PROP_ID_pageGroup);
            
        }
    }
    
    /**
     * 页面类型: PAGE_SCHEMA_TYPE
     */
    public final java.lang.String getPageSchemaType(){
         onPropGet(PROP_ID_pageSchemaType);
         return _pageSchemaType;
    }

    /**
     * 页面类型: PAGE_SCHEMA_TYPE
     */
    public final void setPageSchemaType(java.lang.String value){
        if(onPropSet(PROP_ID_pageSchemaType,value)){
            this._pageSchemaType = value;
            internalClearRefs(PROP_ID_pageSchemaType);
            
        }
    }
    
    /**
     * 页面内容: PAGE_CONTENT
     */
    public final java.lang.String getPageContent(){
         onPropGet(PROP_ID_pageContent);
         return _pageContent;
    }

    /**
     * 页面内容: PAGE_CONTENT
     */
    public final void setPageContent(java.lang.String value){
        if(onPropSet(PROP_ID_pageContent,value)){
            this._pageContent = value;
            internalClearRefs(PROP_ID_pageContent);
            
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
     * 所属模块
     */
    public final io.nop.dyn.dao.entity.NopDynModule getModule(){
       return (io.nop.dyn.dao.entity.NopDynModule)internalGetRefEntity(PROP_NAME_module);
    }

    public final void setModule(io.nop.dyn.dao.entity.NopDynModule refEntity){
   
           if(refEntity == null){
           
                   this.setModuleId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_module, refEntity,()->{
           
                           this.setModuleId(refEntity.getModuleId());
                       
           });
           }
       
    }
       
   private io.nop.orm.component.JsonOrmComponent _pageContentComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_pageContentComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_pageContentComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_pageContent);
      
   }

   public final io.nop.orm.component.JsonOrmComponent getPageContentComponent(){
      if(_pageContentComponent == null){
          _pageContentComponent = new io.nop.orm.component.JsonOrmComponent();
          _pageContentComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_pageContentComponent);
      }
      return _pageContentComponent;
   }

}
// resume CPD analysis - CPD-ON
