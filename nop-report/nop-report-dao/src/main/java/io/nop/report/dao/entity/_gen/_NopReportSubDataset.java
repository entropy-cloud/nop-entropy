package io.nop.report.dao.entity._gen;

import io.nop.orm.model.IEntityModel;
import io.nop.orm.support.DynamicOrmEntity;
import io.nop.orm.support.OrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.orm.IOrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.api.core.convert.ConvertHelper;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;

import io.nop.report.dao.entity.NopReportSubDataset;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  子数据源: nop_report_sub_dataset
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopReportSubDataset extends DynamicOrmEntity{
    
    /* 主键: SID VARCHAR */
    public static final String PROP_NAME_sid = "sid";
    public static final int PROP_ID_sid = 1;
    
    /* 数据集ID: DS_ID VARCHAR */
    public static final String PROP_NAME_dsId = "dsId";
    public static final int PROP_ID_dsId = 2;
    
    /* 子数据集ID: SUB_DS_ID VARCHAR */
    public static final String PROP_NAME_subDsId = "subDsId";
    public static final int PROP_ID_subDsId = 3;
    
    /* 关联字段: JOIN_FIELDS VARCHAR */
    public static final String PROP_NAME_joinFields = "joinFields";
    public static final int PROP_ID_joinFields = 4;
    
    /* 子数据集参数: DS_PARAMS VARCHAR */
    public static final String PROP_NAME_dsParams = "dsParams";
    public static final int PROP_ID_dsParams = 5;
    
    /* 数据版本: VERSION INTEGER */
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

    
    /* relation: 数据集定义 */
    public static final String PROP_NAME_reportDataset = "reportDataset";
    
    /* relation: 子数据集定义 */
    public static final String PROP_NAME_reportSubDataset = "reportSubDataset";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_sid);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_sid};

    private static final String[] PROP_ID_TO_NAME = new String[12];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_sid] = PROP_NAME_sid;
          PROP_NAME_TO_ID.put(PROP_NAME_sid, PROP_ID_sid);
      
          PROP_ID_TO_NAME[PROP_ID_dsId] = PROP_NAME_dsId;
          PROP_NAME_TO_ID.put(PROP_NAME_dsId, PROP_ID_dsId);
      
          PROP_ID_TO_NAME[PROP_ID_subDsId] = PROP_NAME_subDsId;
          PROP_NAME_TO_ID.put(PROP_NAME_subDsId, PROP_ID_subDsId);
      
          PROP_ID_TO_NAME[PROP_ID_joinFields] = PROP_NAME_joinFields;
          PROP_NAME_TO_ID.put(PROP_NAME_joinFields, PROP_ID_joinFields);
      
          PROP_ID_TO_NAME[PROP_ID_dsParams] = PROP_NAME_dsParams;
          PROP_NAME_TO_ID.put(PROP_NAME_dsParams, PROP_ID_dsParams);
      
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
    
    /* 数据集ID: DS_ID */
    private java.lang.String _dsId;
    
    /* 子数据集ID: SUB_DS_ID */
    private java.lang.String _subDsId;
    
    /* 关联字段: JOIN_FIELDS */
    private java.lang.String _joinFields;
    
    /* 子数据集参数: DS_PARAMS */
    private java.lang.String _dsParams;
    
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
    

    public _NopReportSubDataset(){
        // for debug
    }

    protected NopReportSubDataset newInstance(){
        NopReportSubDataset entity = new NopReportSubDataset();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopReportSubDataset cloneInstance() {
        NopReportSubDataset entity = newInstance();
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
      return "io.nop.report.dao.entity.NopReportSubDataset";
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
        
            case PROP_ID_dsId:
               return getDsId();
        
            case PROP_ID_subDsId:
               return getSubDsId();
        
            case PROP_ID_joinFields:
               return getJoinFields();
        
            case PROP_ID_dsParams:
               return getDsParams();
        
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
        
            case PROP_ID_dsId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_dsId));
               }
               setDsId(typedValue);
               break;
            }
        
            case PROP_ID_subDsId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_subDsId));
               }
               setSubDsId(typedValue);
               break;
            }
        
            case PROP_ID_joinFields:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_joinFields));
               }
               setJoinFields(typedValue);
               break;
            }
        
            case PROP_ID_dsParams:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_dsParams));
               }
               setDsParams(typedValue);
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
        
            case PROP_ID_dsId:{
               onInitProp(propId);
               this._dsId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_subDsId:{
               onInitProp(propId);
               this._subDsId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_joinFields:{
               onInitProp(propId);
               this._joinFields = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_dsParams:{
               onInitProp(propId);
               this._dsParams = (java.lang.String)value;
               
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
    public final java.lang.String getSid(){
         onPropGet(PROP_ID_sid);
         return _sid;
    }

    /**
     * 主键: SID
     */
    public final void setSid(java.lang.String value){
        if(onPropSet(PROP_ID_sid,value)){
            this._sid = value;
            internalClearRefs(PROP_ID_sid);
            orm_id();
        }
    }
    
    /**
     * 数据集ID: DS_ID
     */
    public final java.lang.String getDsId(){
         onPropGet(PROP_ID_dsId);
         return _dsId;
    }

    /**
     * 数据集ID: DS_ID
     */
    public final void setDsId(java.lang.String value){
        if(onPropSet(PROP_ID_dsId,value)){
            this._dsId = value;
            internalClearRefs(PROP_ID_dsId);
            
        }
    }
    
    /**
     * 子数据集ID: SUB_DS_ID
     */
    public final java.lang.String getSubDsId(){
         onPropGet(PROP_ID_subDsId);
         return _subDsId;
    }

    /**
     * 子数据集ID: SUB_DS_ID
     */
    public final void setSubDsId(java.lang.String value){
        if(onPropSet(PROP_ID_subDsId,value)){
            this._subDsId = value;
            internalClearRefs(PROP_ID_subDsId);
            
        }
    }
    
    /**
     * 关联字段: JOIN_FIELDS
     */
    public final java.lang.String getJoinFields(){
         onPropGet(PROP_ID_joinFields);
         return _joinFields;
    }

    /**
     * 关联字段: JOIN_FIELDS
     */
    public final void setJoinFields(java.lang.String value){
        if(onPropSet(PROP_ID_joinFields,value)){
            this._joinFields = value;
            internalClearRefs(PROP_ID_joinFields);
            
        }
    }
    
    /**
     * 子数据集参数: DS_PARAMS
     */
    public final java.lang.String getDsParams(){
         onPropGet(PROP_ID_dsParams);
         return _dsParams;
    }

    /**
     * 子数据集参数: DS_PARAMS
     */
    public final void setDsParams(java.lang.String value){
        if(onPropSet(PROP_ID_dsParams,value)){
            this._dsParams = value;
            internalClearRefs(PROP_ID_dsParams);
            
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
     * 数据集定义
     */
    public final io.nop.report.dao.entity.NopReportDataset getReportDataset(){
       return (io.nop.report.dao.entity.NopReportDataset)internalGetRefEntity(PROP_NAME_reportDataset);
    }

    public final void setReportDataset(io.nop.report.dao.entity.NopReportDataset refEntity){
   
           if(refEntity == null){
           
                   this.setDsId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_reportDataset, refEntity,()->{
           
                           this.setDsId(refEntity.getSid());
                       
           });
           }
       
    }
       
    /**
     * 子数据集定义
     */
    public final io.nop.report.dao.entity.NopReportDataset getReportSubDataset(){
       return (io.nop.report.dao.entity.NopReportDataset)internalGetRefEntity(PROP_NAME_reportSubDataset);
    }

    public final void setReportSubDataset(io.nop.report.dao.entity.NopReportDataset refEntity){
   
           if(refEntity == null){
           
                   this.setSubDsId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_reportSubDataset, refEntity,()->{
           
                           this.setSubDsId(refEntity.getSid());
                       
           });
           }
       
    }
       
}
// resume CPD analysis - CPD-ON
