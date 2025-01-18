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

import io.nop.sys.dao.entity.NopSysServiceInstance;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  服务实例: nop_sys_service_instance
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopSysServiceInstance extends DynamicOrmEntity{
    
    /* 服务实例ID: INSTANCE_ID VARCHAR */
    public static final String PROP_NAME_instanceId = "instanceId";
    public static final int PROP_ID_instanceId = 1;
    
    /* 服务名: SERVICE_NAME VARCHAR */
    public static final String PROP_NAME_serviceName = "serviceName";
    public static final int PROP_ID_serviceName = 2;
    
    /* 集群名: CLUSTER_NAME VARCHAR */
    public static final String PROP_NAME_clusterName = "clusterName";
    public static final int PROP_ID_clusterName = 3;
    
    /* 分组名: GROUP_NAME VARCHAR */
    public static final String PROP_NAME_groupName = "groupName";
    public static final int PROP_ID_groupName = 4;
    
    /* 标签: TAGS_TEXT VARCHAR */
    public static final String PROP_NAME_tagsText = "tagsText";
    public static final int PROP_ID_tagsText = 5;
    
    /* 服务地址: SERVER_ADDR VARCHAR */
    public static final String PROP_NAME_serverAddr = "serverAddr";
    public static final int PROP_ID_serverAddr = 6;
    
    /* 服务端口: SERVER_PORT INTEGER */
    public static final String PROP_NAME_serverPort = "serverPort";
    public static final int PROP_ID_serverPort = 7;
    
    /* 权重: WEIGHT INTEGER */
    public static final String PROP_NAME_weight = "weight";
    public static final int PROP_ID_weight = 8;
    
    /* 扩展数据: META_DATA VARCHAR */
    public static final String PROP_NAME_metaData = "metaData";
    public static final int PROP_ID_metaData = 9;
    
    /* 是否健康: IS_HEALTHY BOOLEAN */
    public static final String PROP_NAME_isHealthy = "isHealthy";
    public static final int PROP_ID_isHealthy = 10;
    
    /* 是否启用: IS_ENABLED BOOLEAN */
    public static final String PROP_NAME_isEnabled = "isEnabled";
    public static final int PROP_ID_isEnabled = 11;
    
    /* 是否临时: IS_EPHEMERAL BOOLEAN */
    public static final String PROP_NAME_isEphemeral = "isEphemeral";
    public static final int PROP_ID_isEphemeral = 12;
    
    /* 数据版本: VERSION INTEGER */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 13;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 14;
    
    /* 修改时间: UPDATE_TIME TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 15;
    

    private static int _PROP_ID_BOUND = 16;

    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_instanceId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_instanceId};

    private static final String[] PROP_ID_TO_NAME = new String[16];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_instanceId] = PROP_NAME_instanceId;
          PROP_NAME_TO_ID.put(PROP_NAME_instanceId, PROP_ID_instanceId);
      
          PROP_ID_TO_NAME[PROP_ID_serviceName] = PROP_NAME_serviceName;
          PROP_NAME_TO_ID.put(PROP_NAME_serviceName, PROP_ID_serviceName);
      
          PROP_ID_TO_NAME[PROP_ID_clusterName] = PROP_NAME_clusterName;
          PROP_NAME_TO_ID.put(PROP_NAME_clusterName, PROP_ID_clusterName);
      
          PROP_ID_TO_NAME[PROP_ID_groupName] = PROP_NAME_groupName;
          PROP_NAME_TO_ID.put(PROP_NAME_groupName, PROP_ID_groupName);
      
          PROP_ID_TO_NAME[PROP_ID_tagsText] = PROP_NAME_tagsText;
          PROP_NAME_TO_ID.put(PROP_NAME_tagsText, PROP_ID_tagsText);
      
          PROP_ID_TO_NAME[PROP_ID_serverAddr] = PROP_NAME_serverAddr;
          PROP_NAME_TO_ID.put(PROP_NAME_serverAddr, PROP_ID_serverAddr);
      
          PROP_ID_TO_NAME[PROP_ID_serverPort] = PROP_NAME_serverPort;
          PROP_NAME_TO_ID.put(PROP_NAME_serverPort, PROP_ID_serverPort);
      
          PROP_ID_TO_NAME[PROP_ID_weight] = PROP_NAME_weight;
          PROP_NAME_TO_ID.put(PROP_NAME_weight, PROP_ID_weight);
      
          PROP_ID_TO_NAME[PROP_ID_metaData] = PROP_NAME_metaData;
          PROP_NAME_TO_ID.put(PROP_NAME_metaData, PROP_ID_metaData);
      
          PROP_ID_TO_NAME[PROP_ID_isHealthy] = PROP_NAME_isHealthy;
          PROP_NAME_TO_ID.put(PROP_NAME_isHealthy, PROP_ID_isHealthy);
      
          PROP_ID_TO_NAME[PROP_ID_isEnabled] = PROP_NAME_isEnabled;
          PROP_NAME_TO_ID.put(PROP_NAME_isEnabled, PROP_ID_isEnabled);
      
          PROP_ID_TO_NAME[PROP_ID_isEphemeral] = PROP_NAME_isEphemeral;
          PROP_NAME_TO_ID.put(PROP_NAME_isEphemeral, PROP_ID_isEphemeral);
      
          PROP_ID_TO_NAME[PROP_ID_version] = PROP_NAME_version;
          PROP_NAME_TO_ID.put(PROP_NAME_version, PROP_ID_version);
      
          PROP_ID_TO_NAME[PROP_ID_createTime] = PROP_NAME_createTime;
          PROP_NAME_TO_ID.put(PROP_NAME_createTime, PROP_ID_createTime);
      
          PROP_ID_TO_NAME[PROP_ID_updateTime] = PROP_NAME_updateTime;
          PROP_NAME_TO_ID.put(PROP_NAME_updateTime, PROP_ID_updateTime);
      
    }

    
    /* 服务实例ID: INSTANCE_ID */
    private java.lang.String _instanceId;
    
    /* 服务名: SERVICE_NAME */
    private java.lang.String _serviceName;
    
    /* 集群名: CLUSTER_NAME */
    private java.lang.String _clusterName;
    
    /* 分组名: GROUP_NAME */
    private java.lang.String _groupName;
    
    /* 标签: TAGS_TEXT */
    private java.lang.String _tagsText;
    
    /* 服务地址: SERVER_ADDR */
    private java.lang.String _serverAddr;
    
    /* 服务端口: SERVER_PORT */
    private java.lang.Integer _serverPort;
    
    /* 权重: WEIGHT */
    private java.lang.Integer _weight;
    
    /* 扩展数据: META_DATA */
    private java.lang.String _metaData;
    
    /* 是否健康: IS_HEALTHY */
    private java.lang.Boolean _isHealthy;
    
    /* 是否启用: IS_ENABLED */
    private java.lang.Boolean _isEnabled;
    
    /* 是否临时: IS_EPHEMERAL */
    private java.lang.Boolean _isEphemeral;
    
    /* 数据版本: VERSION */
    private java.lang.Integer _version;
    
    /* 创建时间: CREATE_TIME */
    private java.sql.Timestamp _createTime;
    
    /* 修改时间: UPDATE_TIME */
    private java.sql.Timestamp _updateTime;
    

    public _NopSysServiceInstance(){
        // for debug
    }

    protected NopSysServiceInstance newInstance(){
        NopSysServiceInstance entity = new NopSysServiceInstance();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopSysServiceInstance cloneInstance() {
        NopSysServiceInstance entity = newInstance();
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
      return "io.nop.sys.dao.entity.NopSysServiceInstance";
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
    
        return buildSimpleId(PROP_ID_instanceId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_instanceId;
          
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
        
            case PROP_ID_instanceId:
               return getInstanceId();
        
            case PROP_ID_serviceName:
               return getServiceName();
        
            case PROP_ID_clusterName:
               return getClusterName();
        
            case PROP_ID_groupName:
               return getGroupName();
        
            case PROP_ID_tagsText:
               return getTagsText();
        
            case PROP_ID_serverAddr:
               return getServerAddr();
        
            case PROP_ID_serverPort:
               return getServerPort();
        
            case PROP_ID_weight:
               return getWeight();
        
            case PROP_ID_metaData:
               return getMetaData();
        
            case PROP_ID_isHealthy:
               return getIsHealthy();
        
            case PROP_ID_isEnabled:
               return getIsEnabled();
        
            case PROP_ID_isEphemeral:
               return getIsEphemeral();
        
            case PROP_ID_version:
               return getVersion();
        
            case PROP_ID_createTime:
               return getCreateTime();
        
            case PROP_ID_updateTime:
               return getUpdateTime();
        
           default:
              return super.orm_propValue(propId);
        }
    }

    

    @Override
    public void orm_propValue(int propId, Object value){
        switch(propId){
        
            case PROP_ID_instanceId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_instanceId));
               }
               setInstanceId(typedValue);
               break;
            }
        
            case PROP_ID_serviceName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_serviceName));
               }
               setServiceName(typedValue);
               break;
            }
        
            case PROP_ID_clusterName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_clusterName));
               }
               setClusterName(typedValue);
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
        
            case PROP_ID_tagsText:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_tagsText));
               }
               setTagsText(typedValue);
               break;
            }
        
            case PROP_ID_serverAddr:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_serverAddr));
               }
               setServerAddr(typedValue);
               break;
            }
        
            case PROP_ID_serverPort:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_serverPort));
               }
               setServerPort(typedValue);
               break;
            }
        
            case PROP_ID_weight:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_weight));
               }
               setWeight(typedValue);
               break;
            }
        
            case PROP_ID_metaData:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_metaData));
               }
               setMetaData(typedValue);
               break;
            }
        
            case PROP_ID_isHealthy:{
               java.lang.Boolean typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toBoolean(value,
                       err-> newTypeConversionError(PROP_NAME_isHealthy));
               }
               setIsHealthy(typedValue);
               break;
            }
        
            case PROP_ID_isEnabled:{
               java.lang.Boolean typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toBoolean(value,
                       err-> newTypeConversionError(PROP_NAME_isEnabled));
               }
               setIsEnabled(typedValue);
               break;
            }
        
            case PROP_ID_isEphemeral:{
               java.lang.Boolean typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toBoolean(value,
                       err-> newTypeConversionError(PROP_NAME_isEphemeral));
               }
               setIsEphemeral(typedValue);
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
        
            case PROP_ID_createTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_createTime));
               }
               setCreateTime(typedValue);
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
        
           default:
              super.orm_propValue(propId,value);
        }
    }

    @Override
    public void orm_internalSet(int propId, Object value) {
        switch(propId){
        
            case PROP_ID_instanceId:{
               onInitProp(propId);
               this._instanceId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_serviceName:{
               onInitProp(propId);
               this._serviceName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_clusterName:{
               onInitProp(propId);
               this._clusterName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_groupName:{
               onInitProp(propId);
               this._groupName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_tagsText:{
               onInitProp(propId);
               this._tagsText = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_serverAddr:{
               onInitProp(propId);
               this._serverAddr = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_serverPort:{
               onInitProp(propId);
               this._serverPort = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_weight:{
               onInitProp(propId);
               this._weight = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_metaData:{
               onInitProp(propId);
               this._metaData = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_isHealthy:{
               onInitProp(propId);
               this._isHealthy = (java.lang.Boolean)value;
               
               break;
            }
        
            case PROP_ID_isEnabled:{
               onInitProp(propId);
               this._isEnabled = (java.lang.Boolean)value;
               
               break;
            }
        
            case PROP_ID_isEphemeral:{
               onInitProp(propId);
               this._isEphemeral = (java.lang.Boolean)value;
               
               break;
            }
        
            case PROP_ID_version:{
               onInitProp(propId);
               this._version = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_createTime:{
               onInitProp(propId);
               this._createTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_updateTime:{
               onInitProp(propId);
               this._updateTime = (java.sql.Timestamp)value;
               
               break;
            }
        
           default:
              super.orm_internalSet(propId,value);
        }
    }

    
    /**
     * 服务实例ID: INSTANCE_ID
     */
    public final java.lang.String getInstanceId(){
         onPropGet(PROP_ID_instanceId);
         return _instanceId;
    }

    /**
     * 服务实例ID: INSTANCE_ID
     */
    public final void setInstanceId(java.lang.String value){
        if(onPropSet(PROP_ID_instanceId,value)){
            this._instanceId = value;
            internalClearRefs(PROP_ID_instanceId);
            orm_id();
        }
    }
    
    /**
     * 服务名: SERVICE_NAME
     */
    public final java.lang.String getServiceName(){
         onPropGet(PROP_ID_serviceName);
         return _serviceName;
    }

    /**
     * 服务名: SERVICE_NAME
     */
    public final void setServiceName(java.lang.String value){
        if(onPropSet(PROP_ID_serviceName,value)){
            this._serviceName = value;
            internalClearRefs(PROP_ID_serviceName);
            
        }
    }
    
    /**
     * 集群名: CLUSTER_NAME
     */
    public final java.lang.String getClusterName(){
         onPropGet(PROP_ID_clusterName);
         return _clusterName;
    }

    /**
     * 集群名: CLUSTER_NAME
     */
    public final void setClusterName(java.lang.String value){
        if(onPropSet(PROP_ID_clusterName,value)){
            this._clusterName = value;
            internalClearRefs(PROP_ID_clusterName);
            
        }
    }
    
    /**
     * 分组名: GROUP_NAME
     */
    public final java.lang.String getGroupName(){
         onPropGet(PROP_ID_groupName);
         return _groupName;
    }

    /**
     * 分组名: GROUP_NAME
     */
    public final void setGroupName(java.lang.String value){
        if(onPropSet(PROP_ID_groupName,value)){
            this._groupName = value;
            internalClearRefs(PROP_ID_groupName);
            
        }
    }
    
    /**
     * 标签: TAGS_TEXT
     */
    public final java.lang.String getTagsText(){
         onPropGet(PROP_ID_tagsText);
         return _tagsText;
    }

    /**
     * 标签: TAGS_TEXT
     */
    public final void setTagsText(java.lang.String value){
        if(onPropSet(PROP_ID_tagsText,value)){
            this._tagsText = value;
            internalClearRefs(PROP_ID_tagsText);
            
        }
    }
    
    /**
     * 服务地址: SERVER_ADDR
     */
    public final java.lang.String getServerAddr(){
         onPropGet(PROP_ID_serverAddr);
         return _serverAddr;
    }

    /**
     * 服务地址: SERVER_ADDR
     */
    public final void setServerAddr(java.lang.String value){
        if(onPropSet(PROP_ID_serverAddr,value)){
            this._serverAddr = value;
            internalClearRefs(PROP_ID_serverAddr);
            
        }
    }
    
    /**
     * 服务端口: SERVER_PORT
     */
    public final java.lang.Integer getServerPort(){
         onPropGet(PROP_ID_serverPort);
         return _serverPort;
    }

    /**
     * 服务端口: SERVER_PORT
     */
    public final void setServerPort(java.lang.Integer value){
        if(onPropSet(PROP_ID_serverPort,value)){
            this._serverPort = value;
            internalClearRefs(PROP_ID_serverPort);
            
        }
    }
    
    /**
     * 权重: WEIGHT
     */
    public final java.lang.Integer getWeight(){
         onPropGet(PROP_ID_weight);
         return _weight;
    }

    /**
     * 权重: WEIGHT
     */
    public final void setWeight(java.lang.Integer value){
        if(onPropSet(PROP_ID_weight,value)){
            this._weight = value;
            internalClearRefs(PROP_ID_weight);
            
        }
    }
    
    /**
     * 扩展数据: META_DATA
     */
    public final java.lang.String getMetaData(){
         onPropGet(PROP_ID_metaData);
         return _metaData;
    }

    /**
     * 扩展数据: META_DATA
     */
    public final void setMetaData(java.lang.String value){
        if(onPropSet(PROP_ID_metaData,value)){
            this._metaData = value;
            internalClearRefs(PROP_ID_metaData);
            
        }
    }
    
    /**
     * 是否健康: IS_HEALTHY
     */
    public final java.lang.Boolean getIsHealthy(){
         onPropGet(PROP_ID_isHealthy);
         return _isHealthy;
    }

    /**
     * 是否健康: IS_HEALTHY
     */
    public final void setIsHealthy(java.lang.Boolean value){
        if(onPropSet(PROP_ID_isHealthy,value)){
            this._isHealthy = value;
            internalClearRefs(PROP_ID_isHealthy);
            
        }
    }
    
    /**
     * 是否启用: IS_ENABLED
     */
    public final java.lang.Boolean getIsEnabled(){
         onPropGet(PROP_ID_isEnabled);
         return _isEnabled;
    }

    /**
     * 是否启用: IS_ENABLED
     */
    public final void setIsEnabled(java.lang.Boolean value){
        if(onPropSet(PROP_ID_isEnabled,value)){
            this._isEnabled = value;
            internalClearRefs(PROP_ID_isEnabled);
            
        }
    }
    
    /**
     * 是否临时: IS_EPHEMERAL
     */
    public final java.lang.Boolean getIsEphemeral(){
         onPropGet(PROP_ID_isEphemeral);
         return _isEphemeral;
    }

    /**
     * 是否临时: IS_EPHEMERAL
     */
    public final void setIsEphemeral(java.lang.Boolean value){
        if(onPropSet(PROP_ID_isEphemeral,value)){
            this._isEphemeral = value;
            internalClearRefs(PROP_ID_isEphemeral);
            
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
    
}
// resume CPD analysis - CPD-ON
