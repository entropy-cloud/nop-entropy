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

import io.nop.datav.dao.entity.NopDatavDashboardSnapshot;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  发布快照: nop_datav_snapshot
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopDatavDashboardSnapshot extends DynamicOrmEntity{
    
    /* 快照ID: SNAPSHOT_ID VARCHAR */
    public static final String PROP_NAME_snapshotId = "snapshotId";
    public static final int PROP_ID_snapshotId = 1;
    
    /* 看板ID: DASHBOARD_ID VARCHAR */
    public static final String PROP_NAME_dashboardId = "dashboardId";
    public static final int PROP_ID_dashboardId = 2;
    
    /* 快照版本: SNAPSHOT_VERSION BIGINT */
    public static final String PROP_NAME_snapshotVersion = "snapshotVersion";
    public static final int PROP_ID_snapshotVersion = 3;
    
    /* 快照内容: SNAPSHOT_CONTENT CLOB */
    public static final String PROP_NAME_snapshotContent = "snapshotContent";
    public static final int PROP_ID_snapshotContent = 4;
    
    /* 发布人: PUBLISHED_BY VARCHAR */
    public static final String PROP_NAME_publishedBy = "publishedBy";
    public static final int PROP_ID_publishedBy = 5;
    
    /* 发布时间: PUBLISHED_TIME TIMESTAMP */
    public static final String PROP_NAME_publishedTime = "publishedTime";
    public static final int PROP_ID_publishedTime = 6;
    
    /* 删除标记: DEL_FLAG TINYINT */
    public static final String PROP_NAME_delFlag = "delFlag";
    public static final int PROP_ID_delFlag = 7;
    
    /* 数据版本: VERSION BIGINT */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 8;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 9;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 10;
    
    /* 修改人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 11;
    
    /* 修改时间: UPDATE_TIME TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 12;
    
    /* 备注: REMARK VARCHAR */
    public static final String PROP_NAME_remark = "remark";
    public static final int PROP_ID_remark = 13;
    

    private static int _PROP_ID_BOUND = 14;

    
    /* relation: 看板 */
    public static final String PROP_NAME_dashboard = "dashboard";
    
    /* component:  */
    public static final String PROP_NAME_snapshotContentComponent = "snapshotContentComponent";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_snapshotId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_snapshotId};

    private static final String[] PROP_ID_TO_NAME = new String[14];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_snapshotId] = PROP_NAME_snapshotId;
          PROP_NAME_TO_ID.put(PROP_NAME_snapshotId, PROP_ID_snapshotId);
      
          PROP_ID_TO_NAME[PROP_ID_dashboardId] = PROP_NAME_dashboardId;
          PROP_NAME_TO_ID.put(PROP_NAME_dashboardId, PROP_ID_dashboardId);
      
          PROP_ID_TO_NAME[PROP_ID_snapshotVersion] = PROP_NAME_snapshotVersion;
          PROP_NAME_TO_ID.put(PROP_NAME_snapshotVersion, PROP_ID_snapshotVersion);
      
          PROP_ID_TO_NAME[PROP_ID_snapshotContent] = PROP_NAME_snapshotContent;
          PROP_NAME_TO_ID.put(PROP_NAME_snapshotContent, PROP_ID_snapshotContent);
      
          PROP_ID_TO_NAME[PROP_ID_publishedBy] = PROP_NAME_publishedBy;
          PROP_NAME_TO_ID.put(PROP_NAME_publishedBy, PROP_ID_publishedBy);
      
          PROP_ID_TO_NAME[PROP_ID_publishedTime] = PROP_NAME_publishedTime;
          PROP_NAME_TO_ID.put(PROP_NAME_publishedTime, PROP_ID_publishedTime);
      
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

    
    /* 快照ID: SNAPSHOT_ID */
    private java.lang.String _snapshotId;
    
    /* 看板ID: DASHBOARD_ID */
    private java.lang.String _dashboardId;
    
    /* 快照版本: SNAPSHOT_VERSION */
    private java.lang.Long _snapshotVersion;
    
    /* 快照内容: SNAPSHOT_CONTENT */
    private java.lang.String _snapshotContent;
    
    /* 发布人: PUBLISHED_BY */
    private java.lang.String _publishedBy;
    
    /* 发布时间: PUBLISHED_TIME */
    private java.sql.Timestamp _publishedTime;
    
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
    

    public _NopDatavDashboardSnapshot(){
        // for debug
    }

    protected NopDatavDashboardSnapshot newInstance(){
        NopDatavDashboardSnapshot entity = new NopDatavDashboardSnapshot();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopDatavDashboardSnapshot cloneInstance() {
        NopDatavDashboardSnapshot entity = newInstance();
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
      return "io.nop.datav.dao.entity.NopDatavDashboardSnapshot";
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
    
        return buildSimpleId(PROP_ID_snapshotId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_snapshotId;
          
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
        
            case PROP_ID_snapshotId:
               return getSnapshotId();
        
            case PROP_ID_dashboardId:
               return getDashboardId();
        
            case PROP_ID_snapshotVersion:
               return getSnapshotVersion();
        
            case PROP_ID_snapshotContent:
               return getSnapshotContent();
        
            case PROP_ID_publishedBy:
               return getPublishedBy();
        
            case PROP_ID_publishedTime:
               return getPublishedTime();
        
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
        
            case PROP_ID_snapshotId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_snapshotId));
               }
               setSnapshotId(typedValue);
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
        
            case PROP_ID_snapshotVersion:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_snapshotVersion));
               }
               setSnapshotVersion(typedValue);
               break;
            }
        
            case PROP_ID_snapshotContent:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_snapshotContent));
               }
               setSnapshotContent(typedValue);
               break;
            }
        
            case PROP_ID_publishedBy:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_publishedBy));
               }
               setPublishedBy(typedValue);
               break;
            }
        
            case PROP_ID_publishedTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_publishedTime));
               }
               setPublishedTime(typedValue);
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
        
            case PROP_ID_snapshotId:{
               onInitProp(propId);
               this._snapshotId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_dashboardId:{
               onInitProp(propId);
               this._dashboardId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_snapshotVersion:{
               onInitProp(propId);
               this._snapshotVersion = (java.lang.Long)value;
               
               break;
            }
        
            case PROP_ID_snapshotContent:{
               onInitProp(propId);
               this._snapshotContent = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_publishedBy:{
               onInitProp(propId);
               this._publishedBy = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_publishedTime:{
               onInitProp(propId);
               this._publishedTime = (java.sql.Timestamp)value;
               
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
     * 快照ID: SNAPSHOT_ID
     */
    public final java.lang.String getSnapshotId(){
         onPropGet(PROP_ID_snapshotId);
         return _snapshotId;
    }

    /**
     * 快照ID: SNAPSHOT_ID
     */
    public final void setSnapshotId(java.lang.String value){
        if(onPropSet(PROP_ID_snapshotId,value)){
            this._snapshotId = value;
            internalClearRefs(PROP_ID_snapshotId);
            orm_id();
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
     * 快照版本: SNAPSHOT_VERSION
     */
    public final java.lang.Long getSnapshotVersion(){
         onPropGet(PROP_ID_snapshotVersion);
         return _snapshotVersion;
    }

    /**
     * 快照版本: SNAPSHOT_VERSION
     */
    public final void setSnapshotVersion(java.lang.Long value){
        if(onPropSet(PROP_ID_snapshotVersion,value)){
            this._snapshotVersion = value;
            internalClearRefs(PROP_ID_snapshotVersion);
            
        }
    }
    
    /**
     * 快照内容: SNAPSHOT_CONTENT
     */
    public final java.lang.String getSnapshotContent(){
         onPropGet(PROP_ID_snapshotContent);
         return _snapshotContent;
    }

    /**
     * 快照内容: SNAPSHOT_CONTENT
     */
    public final void setSnapshotContent(java.lang.String value){
        if(onPropSet(PROP_ID_snapshotContent,value)){
            this._snapshotContent = value;
            internalClearRefs(PROP_ID_snapshotContent);
            
        }
    }
    
    /**
     * 发布人: PUBLISHED_BY
     */
    public final java.lang.String getPublishedBy(){
         onPropGet(PROP_ID_publishedBy);
         return _publishedBy;
    }

    /**
     * 发布人: PUBLISHED_BY
     */
    public final void setPublishedBy(java.lang.String value){
        if(onPropSet(PROP_ID_publishedBy,value)){
            this._publishedBy = value;
            internalClearRefs(PROP_ID_publishedBy);
            
        }
    }
    
    /**
     * 发布时间: PUBLISHED_TIME
     */
    public final java.sql.Timestamp getPublishedTime(){
         onPropGet(PROP_ID_publishedTime);
         return _publishedTime;
    }

    /**
     * 发布时间: PUBLISHED_TIME
     */
    public final void setPublishedTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_publishedTime,value)){
            this._publishedTime = value;
            internalClearRefs(PROP_ID_publishedTime);
            
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
       
   private io.nop.orm.component.JsonOrmComponent _snapshotContentComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_snapshotContentComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_snapshotContentComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_snapshotContent);
      
   }

   public final io.nop.orm.component.JsonOrmComponent getSnapshotContentComponent(){
      if(_snapshotContentComponent == null){
          _snapshotContentComponent = new io.nop.orm.component.JsonOrmComponent();
          _snapshotContentComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_snapshotContentComponent);
      }
      return _snapshotContentComponent;
   }

}
// resume CPD analysis - CPD-ON
