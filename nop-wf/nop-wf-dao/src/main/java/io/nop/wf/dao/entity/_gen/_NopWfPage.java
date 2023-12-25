package io.nop.wf.dao.entity._gen;

import io.nop.orm.model.IEntityModel;
import io.nop.orm.support.DynamicOrmEntity;
import io.nop.orm.support.OrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.orm.IOrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.api.core.convert.ConvertHelper;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;

import io.nop.wf.dao.entity.NopWfPage;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  页面定义: nop_wf_page
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopWfPage extends DynamicOrmEntity{
    
    /* ID: SID VARCHAR */
    public static final String PROP_NAME_sid = "sid";
    public static final int PROP_ID_sid = 1;
    
    /* 页面名称: PAGE_NAME VARCHAR */
    public static final String PROP_NAME_pageName = "pageName";
    public static final int PROP_ID_pageName = 2;
    
    /* 页面分组: PAGE_GROUP VARCHAR */
    public static final String PROP_NAME_pageGroup = "pageGroup";
    public static final int PROP_ID_pageGroup = 3;
    
    /* 页面类型: PAGE_SCHEMA_TYPE VARCHAR */
    public static final String PROP_NAME_pageSchemaType = "pageSchemaType";
    public static final int PROP_ID_pageSchemaType = 4;
    
    /* 页面内容: PAGE_CONTENT VARCHAR */
    public static final String PROP_NAME_pageContent = "pageContent";
    public static final int PROP_ID_pageContent = 5;
    
    /* 状态: STATUS INTEGER */
    public static final String PROP_NAME_status = "status";
    public static final int PROP_ID_status = 6;
    
    /* 拥有者ID: OWNER_ID VARCHAR */
    public static final String PROP_NAME_ownerId = "ownerId";
    public static final int PROP_ID_ownerId = 7;
    
    /* 拥有者姓名: OWNER_NAME VARCHAR */
    public static final String PROP_NAME_ownerName = "ownerName";
    public static final int PROP_ID_ownerName = 8;
    
    /* 数据版本: VERSION INTEGER */
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

    
    /* component:  */
    public static final String PROP_NAME_pageContentComponent = "pageContentComponent";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_sid);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_sid};

    private static final String[] PROP_ID_TO_NAME = new String[15];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_sid] = PROP_NAME_sid;
          PROP_NAME_TO_ID.put(PROP_NAME_sid, PROP_ID_sid);
      
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
      
          PROP_ID_TO_NAME[PROP_ID_ownerId] = PROP_NAME_ownerId;
          PROP_NAME_TO_ID.put(PROP_NAME_ownerId, PROP_ID_ownerId);
      
          PROP_ID_TO_NAME[PROP_ID_ownerName] = PROP_NAME_ownerName;
          PROP_NAME_TO_ID.put(PROP_NAME_ownerName, PROP_ID_ownerName);
      
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

    
    /* ID: SID */
    private java.lang.String _sid;
    
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
    
    /* 拥有者ID: OWNER_ID */
    private java.lang.String _ownerId;
    
    /* 拥有者姓名: OWNER_NAME */
    private java.lang.String _ownerName;
    
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
    

    public _NopWfPage(){
        // for debug
    }

    protected NopWfPage newInstance(){
       return new NopWfPage();
    }

    @Override
    public NopWfPage cloneInstance() {
        NopWfPage entity = newInstance();
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
      return "io.nop.wf.dao.entity.NopWfPage";
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
        
            case PROP_ID_ownerId:
               return getOwnerId();
        
            case PROP_ID_ownerName:
               return getOwnerName();
        
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
        
            case PROP_ID_ownerId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_ownerId));
               }
               setOwnerId(typedValue);
               break;
            }
        
            case PROP_ID_ownerName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_ownerName));
               }
               setOwnerName(typedValue);
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
        
            case PROP_ID_ownerId:{
               onInitProp(propId);
               this._ownerId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_ownerName:{
               onInitProp(propId);
               this._ownerName = (java.lang.String)value;
               
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
     * ID: SID
     */
    public java.lang.String getSid(){
         onPropGet(PROP_ID_sid);
         return _sid;
    }

    /**
     * ID: SID
     */
    public void setSid(java.lang.String value){
        if(onPropSet(PROP_ID_sid,value)){
            this._sid = value;
            internalClearRefs(PROP_ID_sid);
            orm_id();
        }
    }
    
    /**
     * 页面名称: PAGE_NAME
     */
    public java.lang.String getPageName(){
         onPropGet(PROP_ID_pageName);
         return _pageName;
    }

    /**
     * 页面名称: PAGE_NAME
     */
    public void setPageName(java.lang.String value){
        if(onPropSet(PROP_ID_pageName,value)){
            this._pageName = value;
            internalClearRefs(PROP_ID_pageName);
            
        }
    }
    
    /**
     * 页面分组: PAGE_GROUP
     */
    public java.lang.String getPageGroup(){
         onPropGet(PROP_ID_pageGroup);
         return _pageGroup;
    }

    /**
     * 页面分组: PAGE_GROUP
     */
    public void setPageGroup(java.lang.String value){
        if(onPropSet(PROP_ID_pageGroup,value)){
            this._pageGroup = value;
            internalClearRefs(PROP_ID_pageGroup);
            
        }
    }
    
    /**
     * 页面类型: PAGE_SCHEMA_TYPE
     */
    public java.lang.String getPageSchemaType(){
         onPropGet(PROP_ID_pageSchemaType);
         return _pageSchemaType;
    }

    /**
     * 页面类型: PAGE_SCHEMA_TYPE
     */
    public void setPageSchemaType(java.lang.String value){
        if(onPropSet(PROP_ID_pageSchemaType,value)){
            this._pageSchemaType = value;
            internalClearRefs(PROP_ID_pageSchemaType);
            
        }
    }
    
    /**
     * 页面内容: PAGE_CONTENT
     */
    public java.lang.String getPageContent(){
         onPropGet(PROP_ID_pageContent);
         return _pageContent;
    }

    /**
     * 页面内容: PAGE_CONTENT
     */
    public void setPageContent(java.lang.String value){
        if(onPropSet(PROP_ID_pageContent,value)){
            this._pageContent = value;
            internalClearRefs(PROP_ID_pageContent);
            
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
     * 拥有者ID: OWNER_ID
     */
    public java.lang.String getOwnerId(){
         onPropGet(PROP_ID_ownerId);
         return _ownerId;
    }

    /**
     * 拥有者ID: OWNER_ID
     */
    public void setOwnerId(java.lang.String value){
        if(onPropSet(PROP_ID_ownerId,value)){
            this._ownerId = value;
            internalClearRefs(PROP_ID_ownerId);
            
        }
    }
    
    /**
     * 拥有者姓名: OWNER_NAME
     */
    public java.lang.String getOwnerName(){
         onPropGet(PROP_ID_ownerName);
         return _ownerName;
    }

    /**
     * 拥有者姓名: OWNER_NAME
     */
    public void setOwnerName(java.lang.String value){
        if(onPropSet(PROP_ID_ownerName,value)){
            this._ownerName = value;
            internalClearRefs(PROP_ID_ownerName);
            
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
    
   private io.nop.orm.component.JsonOrmComponent _pageContentComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_pageContentComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_pageContentComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_pageContent);
      
   }

   public io.nop.orm.component.JsonOrmComponent getPageContentComponent(){
      if(_pageContentComponent == null){
          _pageContentComponent = new io.nop.orm.component.JsonOrmComponent();
          _pageContentComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_pageContentComponent);
      }
      return _pageContentComponent;
   }

}
// resume CPD analysis - CPD-ON
