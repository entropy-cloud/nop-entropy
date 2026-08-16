package io.nop.datav.dao.entity._gen;

import io.nop.orm.model.IEntityModel;
import io.nop.orm.support.DynamicOrmEntity;
import io.nop.orm.support.OrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.orm.IOrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code

import io.nop.api.core.convert.ConvertHelper;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;

import io.nop.datav.dao.entity.NopDatavFilterState;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  筛选状态: nop_datav_filter_state
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopDatavFilterState extends DynamicOrmEntity{
    
    /* 状态ID: STATE_ID VARCHAR */
    public static final String PROP_NAME_stateId = "stateId";
    public static final int PROP_ID_stateId = 1;
    
    /* 用户名: USER_NAME VARCHAR */
    public static final String PROP_NAME_userName = "userName";
    public static final int PROP_ID_userName = 2;
    
    /* 看板ID: DASHBOARD_ID VARCHAR */
    public static final String PROP_NAME_dashboardId = "dashboardId";
    public static final int PROP_ID_dashboardId = 3;
    
    /* 状态内容: STATE_CONTENT CLOB */
    public static final String PROP_NAME_stateContent = "stateContent";
    public static final int PROP_ID_stateContent = 4;
    
    /* 删除标记: DEL_FLAG TINYINT */
    public static final String PROP_NAME_delFlag = "delFlag";
    public static final int PROP_ID_delFlag = 5;
    
    /* 数据版本: VERSION BIGINT */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 6;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 7;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 8;
    
    /* 修改人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 9;
    
    /* 修改时间: UPDATE_TIME TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 10;
    
    /* 备注: REMARK VARCHAR */
    public static final String PROP_NAME_remark = "remark";
    public static final int PROP_ID_remark = 11;
    

    private static int _PROP_ID_BOUND = 12;

    
    /* relation: 看板 */
    public static final String PROP_NAME_dashboard = "dashboard";
    
    /* component:  */
    public static final String PROP_NAME_stateContentComponent = "stateContentComponent";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_stateId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_stateId};

    private static final String[] PROP_ID_TO_NAME = new String[12];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_stateId] = PROP_NAME_stateId;
          PROP_NAME_TO_ID.put(PROP_NAME_stateId, PROP_ID_stateId);
      
          PROP_ID_TO_NAME[PROP_ID_userName] = PROP_NAME_userName;
          PROP_NAME_TO_ID.put(PROP_NAME_userName, PROP_ID_userName);
      
          PROP_ID_TO_NAME[PROP_ID_dashboardId] = PROP_NAME_dashboardId;
          PROP_NAME_TO_ID.put(PROP_NAME_dashboardId, PROP_ID_dashboardId);
      
          PROP_ID_TO_NAME[PROP_ID_stateContent] = PROP_NAME_stateContent;
          PROP_NAME_TO_ID.put(PROP_NAME_stateContent, PROP_ID_stateContent);
      
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

    
    /* 状态ID: STATE_ID */
    private java.lang.String _stateId;
    
    /* 用户名: USER_NAME */
    private java.lang.String _userName;
    
    /* 看板ID: DASHBOARD_ID */
    private java.lang.String _dashboardId;
    
    /* 状态内容: STATE_CONTENT */
    private java.lang.String _stateContent;
    
    /* 删除标记: DEL_FLAG */
    private java.lang.Byte _delFlag;
    
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
    

    public _NopDatavFilterState(){
        // for debug
    }

    protected NopDatavFilterState newInstance(){
        NopDatavFilterState entity = new NopDatavFilterState();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopDatavFilterState cloneInstance() {
        NopDatavFilterState entity = newInstance();
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
      return "io.nop.datav.dao.entity.NopDatavFilterState";
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
    
        return buildSimpleId(PROP_ID_stateId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_stateId;
          
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
        
            case PROP_ID_stateId:
               return getStateId();
        
            case PROP_ID_userName:
               return getUserName();
        
            case PROP_ID_dashboardId:
               return getDashboardId();
        
            case PROP_ID_stateContent:
               return getStateContent();
        
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
        
            case PROP_ID_stateId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_stateId));
               }
               setStateId(typedValue);
               break;
            }
        
            case PROP_ID_userName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_userName));
               }
               setUserName(typedValue);
               break;
            }
        
            case PROP_ID_dashboardId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_dashboardId));
               }
               setDashboardId(typedValue);
               break;
            }
        
            case PROP_ID_stateContent:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_stateContent));
               }
               setStateContent(typedValue);
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
        
            case PROP_ID_stateId:{
               onInitProp(propId);
               this._stateId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_userName:{
               onInitProp(propId);
               this._userName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_dashboardId:{
               onInitProp(propId);
               this._dashboardId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_stateContent:{
               onInitProp(propId);
               this._stateContent = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_delFlag:{
               onInitProp(propId);
               this._delFlag = (java.lang.Byte)value;
               
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
     * 状态ID: STATE_ID
     */
    public final java.lang.String getStateId(){
         onPropGet(PROP_ID_stateId);
         return _stateId;
    }

    /**
     * 状态ID: STATE_ID
     */
    public final void setStateId(java.lang.String value){
        if(onPropSet(PROP_ID_stateId,value)){
            this._stateId = value;
            internalClearRefs(PROP_ID_stateId);
            orm_id();
        }
    }
    
    /**
     * 用户名: USER_NAME
     */
    public final java.lang.String getUserName(){
         onPropGet(PROP_ID_userName);
         return _userName;
    }

    /**
     * 用户名: USER_NAME
     */
    public final void setUserName(java.lang.String value){
        if(onPropSet(PROP_ID_userName,value)){
            this._userName = value;
            internalClearRefs(PROP_ID_userName);
            
        }
    }
    
    /**
     * 看板ID: DASHBOARD_ID
     */
    public final java.lang.String getDashboardId(){
         onPropGet(PROP_ID_dashboardId);
         return _dashboardId;
    }

    /**
     * 看板ID: DASHBOARD_ID
     */
    public final void setDashboardId(java.lang.String value){
        if(onPropSet(PROP_ID_dashboardId,value)){
            this._dashboardId = value;
            internalClearRefs(PROP_ID_dashboardId);
            
        }
    }
    
    /**
     * 状态内容: STATE_CONTENT
     */
    public final java.lang.String getStateContent(){
         onPropGet(PROP_ID_stateContent);
         return _stateContent;
    }

    /**
     * 状态内容: STATE_CONTENT
     */
    public final void setStateContent(java.lang.String value){
        if(onPropSet(PROP_ID_stateContent,value)){
            this._stateContent = value;
            internalClearRefs(PROP_ID_stateContent);
            
        }
    }
    
    /**
     * 删除标记: DEL_FLAG
     */
    public final java.lang.Byte getDelFlag(){
         onPropGet(PROP_ID_delFlag);
         return _delFlag;
    }

    /**
     * 删除标记: DEL_FLAG
     */
    public final void setDelFlag(java.lang.Byte value){
        if(onPropSet(PROP_ID_delFlag,value)){
            this._delFlag = value;
            internalClearRefs(PROP_ID_delFlag);
            
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
     * 看板
     */
    public final io.nop.datav.dao.entity.NopDatavDashboard getDashboard(){
       return (io.nop.datav.dao.entity.NopDatavDashboard)internalGetRefEntity(PROP_NAME_dashboard);
    }

    public final void setDashboard(io.nop.datav.dao.entity.NopDatavDashboard refEntity){
   
           if(refEntity == null){
           
                   this.setDashboardId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_dashboard, refEntity,()->{
           
                           this.setDashboardId(refEntity.getDashboardId());
                       
           });
           }
       
    }
       
   private io.nop.orm.component.JsonOrmComponent _stateContentComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_stateContentComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_stateContentComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_stateContent);
      
   }

   public final io.nop.orm.component.JsonOrmComponent getStateContentComponent(){
      if(_stateContentComponent == null){
          _stateContentComponent = new io.nop.orm.component.JsonOrmComponent();
          _stateContentComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_stateContentComponent);
      }
      return _stateContentComponent;
   }

}
// resume CPD analysis - CPD-ON
