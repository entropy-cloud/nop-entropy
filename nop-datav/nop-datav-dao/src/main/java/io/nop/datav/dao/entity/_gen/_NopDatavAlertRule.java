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

import io.nop.datav.dao.entity.NopDatavAlertRule;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  告警规则: nop_datav_alert_rule
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopDatavAlertRule extends DynamicOrmEntity{
    
    /* 告警规则ID: ALERT_RULE_ID VARCHAR */
    public static final String PROP_NAME_alertRuleId = "alertRuleId";
    public static final int PROP_ID_alertRuleId = 1;
    
    /* 规则名: RULE_NAME VARCHAR */
    public static final String PROP_NAME_ruleName = "ruleName";
    public static final int PROP_ID_ruleName = 2;
    
    /* 显示名: DISPLAY_NAME VARCHAR */
    public static final String PROP_NAME_displayName = "displayName";
    public static final int PROP_ID_displayName = 3;
    
    /* 面板ID: PANEL_ID VARCHAR */
    public static final String PROP_NAME_panelId = "panelId";
    public static final int PROP_ID_panelId = 4;
    
    /* 取值字段: VALUE_FIELD VARCHAR */
    public static final String PROP_NAME_valueField = "valueField";
    public static final int PROP_ID_valueField = 5;
    
    /* 聚合方式: AGGREGATION VARCHAR */
    public static final String PROP_NAME_aggregation = "aggregation";
    public static final int PROP_ID_aggregation = 6;
    
    /* 比较运算符: OPERATOR VARCHAR */
    public static final String PROP_NAME_operator = "operator";
    public static final int PROP_ID_operator = 7;
    
    /* 阈值: THRESHOLD_VALUE DECIMAL */
    public static final String PROP_NAME_thresholdValue = "thresholdValue";
    public static final int PROP_ID_thresholdValue = 8;
    
    /* 阈值上限: THRESHOLD_VALUE2 DECIMAL */
    public static final String PROP_NAME_thresholdValue2 = "thresholdValue2";
    public static final int PROP_ID_thresholdValue2 = 9;
    
    /* 冷静期秒数: REARM_SECONDS INTEGER */
    public static final String PROP_NAME_rearmSeconds = "rearmSeconds";
    public static final int PROP_ID_rearmSeconds = 10;
    
    /* 通知渠道: NOTIFY_CHANNELS CLOB */
    public static final String PROP_NAME_notifyChannels = "notifyChannels";
    public static final int PROP_ID_notifyChannels = 11;
    
    /* 收件人: RECIPIENTS CLOB */
    public static final String PROP_NAME_recipients = "recipients";
    public static final int PROP_ID_recipients = 12;
    
    /* cron表达式: CRON_EXPR VARCHAR */
    public static final String PROP_NAME_cronExpr = "cronExpr";
    public static final int PROP_ID_cronExpr = 13;
    
    /* 查询参数: PARAMS CLOB */
    public static final String PROP_NAME_params = "params";
    public static final int PROP_ID_params = 14;
    
    /* 模板键: TEMPLATE_KEY VARCHAR */
    public static final String PROP_NAME_templateKey = "templateKey";
    public static final int PROP_ID_templateKey = 15;
    
    /* 规则状态: STATUS INTEGER */
    public static final String PROP_NAME_status = "status";
    public static final int PROP_ID_status = 16;
    
    /* 删除标记: DEL_FLAG TINYINT */
    public static final String PROP_NAME_delFlag = "delFlag";
    public static final int PROP_ID_delFlag = 17;
    
    /* 数据版本: VERSION BIGINT */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 18;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 19;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 20;
    
    /* 修改人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 21;
    
    /* 修改时间: UPDATE_TIME TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 22;
    
    /* 备注: REMARK VARCHAR */
    public static final String PROP_NAME_remark = "remark";
    public static final int PROP_ID_remark = 23;
    

    private static int _PROP_ID_BOUND = 24;

    
    /* relation: 面板 */
    public static final String PROP_NAME_panel = "panel";
    
    /* component:  */
    public static final String PROP_NAME_notifyChannelsComponent = "notifyChannelsComponent";
    
    /* component:  */
    public static final String PROP_NAME_recipientsComponent = "recipientsComponent";
    
    /* component:  */
    public static final String PROP_NAME_paramsComponent = "paramsComponent";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_alertRuleId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_alertRuleId};

    private static final String[] PROP_ID_TO_NAME = new String[24];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_alertRuleId] = PROP_NAME_alertRuleId;
          PROP_NAME_TO_ID.put(PROP_NAME_alertRuleId, PROP_ID_alertRuleId);
      
          PROP_ID_TO_NAME[PROP_ID_ruleName] = PROP_NAME_ruleName;
          PROP_NAME_TO_ID.put(PROP_NAME_ruleName, PROP_ID_ruleName);
      
          PROP_ID_TO_NAME[PROP_ID_displayName] = PROP_NAME_displayName;
          PROP_NAME_TO_ID.put(PROP_NAME_displayName, PROP_ID_displayName);
      
          PROP_ID_TO_NAME[PROP_ID_panelId] = PROP_NAME_panelId;
          PROP_NAME_TO_ID.put(PROP_NAME_panelId, PROP_ID_panelId);
      
          PROP_ID_TO_NAME[PROP_ID_valueField] = PROP_NAME_valueField;
          PROP_NAME_TO_ID.put(PROP_NAME_valueField, PROP_ID_valueField);
      
          PROP_ID_TO_NAME[PROP_ID_aggregation] = PROP_NAME_aggregation;
          PROP_NAME_TO_ID.put(PROP_NAME_aggregation, PROP_ID_aggregation);
      
          PROP_ID_TO_NAME[PROP_ID_operator] = PROP_NAME_operator;
          PROP_NAME_TO_ID.put(PROP_NAME_operator, PROP_ID_operator);
      
          PROP_ID_TO_NAME[PROP_ID_thresholdValue] = PROP_NAME_thresholdValue;
          PROP_NAME_TO_ID.put(PROP_NAME_thresholdValue, PROP_ID_thresholdValue);
      
          PROP_ID_TO_NAME[PROP_ID_thresholdValue2] = PROP_NAME_thresholdValue2;
          PROP_NAME_TO_ID.put(PROP_NAME_thresholdValue2, PROP_ID_thresholdValue2);
      
          PROP_ID_TO_NAME[PROP_ID_rearmSeconds] = PROP_NAME_rearmSeconds;
          PROP_NAME_TO_ID.put(PROP_NAME_rearmSeconds, PROP_ID_rearmSeconds);
      
          PROP_ID_TO_NAME[PROP_ID_notifyChannels] = PROP_NAME_notifyChannels;
          PROP_NAME_TO_ID.put(PROP_NAME_notifyChannels, PROP_ID_notifyChannels);
      
          PROP_ID_TO_NAME[PROP_ID_recipients] = PROP_NAME_recipients;
          PROP_NAME_TO_ID.put(PROP_NAME_recipients, PROP_ID_recipients);
      
          PROP_ID_TO_NAME[PROP_ID_cronExpr] = PROP_NAME_cronExpr;
          PROP_NAME_TO_ID.put(PROP_NAME_cronExpr, PROP_ID_cronExpr);
      
          PROP_ID_TO_NAME[PROP_ID_params] = PROP_NAME_params;
          PROP_NAME_TO_ID.put(PROP_NAME_params, PROP_ID_params);
      
          PROP_ID_TO_NAME[PROP_ID_templateKey] = PROP_NAME_templateKey;
          PROP_NAME_TO_ID.put(PROP_NAME_templateKey, PROP_ID_templateKey);
      
          PROP_ID_TO_NAME[PROP_ID_status] = PROP_NAME_status;
          PROP_NAME_TO_ID.put(PROP_NAME_status, PROP_ID_status);
      
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

    
    /* 告警规则ID: ALERT_RULE_ID */
    private java.lang.String _alertRuleId;
    
    /* 规则名: RULE_NAME */
    private java.lang.String _ruleName;
    
    /* 显示名: DISPLAY_NAME */
    private java.lang.String _displayName;
    
    /* 面板ID: PANEL_ID */
    private java.lang.String _panelId;
    
    /* 取值字段: VALUE_FIELD */
    private java.lang.String _valueField;
    
    /* 聚合方式: AGGREGATION */
    private java.lang.String _aggregation;
    
    /* 比较运算符: OPERATOR */
    private java.lang.String _operator;
    
    /* 阈值: THRESHOLD_VALUE */
    private java.math.BigDecimal _thresholdValue;
    
    /* 阈值上限: THRESHOLD_VALUE2 */
    private java.math.BigDecimal _thresholdValue2;
    
    /* 冷静期秒数: REARM_SECONDS */
    private java.lang.Integer _rearmSeconds;
    
    /* 通知渠道: NOTIFY_CHANNELS */
    private java.lang.String _notifyChannels;
    
    /* 收件人: RECIPIENTS */
    private java.lang.String _recipients;
    
    /* cron表达式: CRON_EXPR */
    private java.lang.String _cronExpr;
    
    /* 查询参数: PARAMS */
    private java.lang.String _params;
    
    /* 模板键: TEMPLATE_KEY */
    private java.lang.String _templateKey;
    
    /* 规则状态: STATUS */
    private java.lang.Integer _status;
    
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
    

    public _NopDatavAlertRule(){
        // for debug
    }

    protected NopDatavAlertRule newInstance(){
        NopDatavAlertRule entity = new NopDatavAlertRule();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopDatavAlertRule cloneInstance() {
        NopDatavAlertRule entity = newInstance();
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
      return "io.nop.datav.dao.entity.NopDatavAlertRule";
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
    
        return buildSimpleId(PROP_ID_alertRuleId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_alertRuleId;
          
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
        
            case PROP_ID_alertRuleId:
               return getAlertRuleId();
        
            case PROP_ID_ruleName:
               return getRuleName();
        
            case PROP_ID_displayName:
               return getDisplayName();
        
            case PROP_ID_panelId:
               return getPanelId();
        
            case PROP_ID_valueField:
               return getValueField();
        
            case PROP_ID_aggregation:
               return getAggregation();
        
            case PROP_ID_operator:
               return getOperator();
        
            case PROP_ID_thresholdValue:
               return getThresholdValue();
        
            case PROP_ID_thresholdValue2:
               return getThresholdValue2();
        
            case PROP_ID_rearmSeconds:
               return getRearmSeconds();
        
            case PROP_ID_notifyChannels:
               return getNotifyChannels();
        
            case PROP_ID_recipients:
               return getRecipients();
        
            case PROP_ID_cronExpr:
               return getCronExpr();
        
            case PROP_ID_params:
               return getParams();
        
            case PROP_ID_templateKey:
               return getTemplateKey();
        
            case PROP_ID_status:
               return getStatus();
        
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
        
            case PROP_ID_alertRuleId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_alertRuleId));
               }
               setAlertRuleId(typedValue);
               break;
            }
        
            case PROP_ID_ruleName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_ruleName));
               }
               setRuleName(typedValue);
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
        
            case PROP_ID_panelId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_panelId));
               }
               setPanelId(typedValue);
               break;
            }
        
            case PROP_ID_valueField:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_valueField));
               }
               setValueField(typedValue);
               break;
            }
        
            case PROP_ID_aggregation:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_aggregation));
               }
               setAggregation(typedValue);
               break;
            }
        
            case PROP_ID_operator:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_operator));
               }
               setOperator(typedValue);
               break;
            }
        
            case PROP_ID_thresholdValue:{
               java.math.BigDecimal typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toBigDecimal(value,
                       err-> newTypeConversionError(PROP_NAME_thresholdValue));
               }
               setThresholdValue(typedValue);
               break;
            }
        
            case PROP_ID_thresholdValue2:{
               java.math.BigDecimal typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toBigDecimal(value,
                       err-> newTypeConversionError(PROP_NAME_thresholdValue2));
               }
               setThresholdValue2(typedValue);
               break;
            }
        
            case PROP_ID_rearmSeconds:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_rearmSeconds));
               }
               setRearmSeconds(typedValue);
               break;
            }
        
            case PROP_ID_notifyChannels:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_notifyChannels));
               }
               setNotifyChannels(typedValue);
               break;
            }
        
            case PROP_ID_recipients:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_recipients));
               }
               setRecipients(typedValue);
               break;
            }
        
            case PROP_ID_cronExpr:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_cronExpr));
               }
               setCronExpr(typedValue);
               break;
            }
        
            case PROP_ID_params:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_params));
               }
               setParams(typedValue);
               break;
            }
        
            case PROP_ID_templateKey:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_templateKey));
               }
               setTemplateKey(typedValue);
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
        
            case PROP_ID_alertRuleId:{
               onInitProp(propId);
               this._alertRuleId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_ruleName:{
               onInitProp(propId);
               this._ruleName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_displayName:{
               onInitProp(propId);
               this._displayName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_panelId:{
               onInitProp(propId);
               this._panelId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_valueField:{
               onInitProp(propId);
               this._valueField = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_aggregation:{
               onInitProp(propId);
               this._aggregation = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_operator:{
               onInitProp(propId);
               this._operator = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_thresholdValue:{
               onInitProp(propId);
               this._thresholdValue = (java.math.BigDecimal)value;
               
               break;
            }
        
            case PROP_ID_thresholdValue2:{
               onInitProp(propId);
               this._thresholdValue2 = (java.math.BigDecimal)value;
               
               break;
            }
        
            case PROP_ID_rearmSeconds:{
               onInitProp(propId);
               this._rearmSeconds = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_notifyChannels:{
               onInitProp(propId);
               this._notifyChannels = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_recipients:{
               onInitProp(propId);
               this._recipients = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_cronExpr:{
               onInitProp(propId);
               this._cronExpr = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_params:{
               onInitProp(propId);
               this._params = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_templateKey:{
               onInitProp(propId);
               this._templateKey = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_status:{
               onInitProp(propId);
               this._status = (java.lang.Integer)value;
               
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
     * 告警规则ID: ALERT_RULE_ID
     */
    public final java.lang.String getAlertRuleId(){
         onPropGet(PROP_ID_alertRuleId);
         return _alertRuleId;
    }

    /**
     * 告警规则ID: ALERT_RULE_ID
     */
    public final void setAlertRuleId(java.lang.String value){
        if(onPropSet(PROP_ID_alertRuleId,value)){
            this._alertRuleId = value;
            internalClearRefs(PROP_ID_alertRuleId);
            orm_id();
        }
    }
    
    /**
     * 规则名: RULE_NAME
     */
    public final java.lang.String getRuleName(){
         onPropGet(PROP_ID_ruleName);
         return _ruleName;
    }

    /**
     * 规则名: RULE_NAME
     */
    public final void setRuleName(java.lang.String value){
        if(onPropSet(PROP_ID_ruleName,value)){
            this._ruleName = value;
            internalClearRefs(PROP_ID_ruleName);
            
        }
    }
    
    /**
     * 显示名: DISPLAY_NAME
     */
    public final java.lang.String getDisplayName(){
         onPropGet(PROP_ID_displayName);
         return _displayName;
    }

    /**
     * 显示名: DISPLAY_NAME
     */
    public final void setDisplayName(java.lang.String value){
        if(onPropSet(PROP_ID_displayName,value)){
            this._displayName = value;
            internalClearRefs(PROP_ID_displayName);
            
        }
    }
    
    /**
     * 面板ID: PANEL_ID
     */
    public final java.lang.String getPanelId(){
         onPropGet(PROP_ID_panelId);
         return _panelId;
    }

    /**
     * 面板ID: PANEL_ID
     */
    public final void setPanelId(java.lang.String value){
        if(onPropSet(PROP_ID_panelId,value)){
            this._panelId = value;
            internalClearRefs(PROP_ID_panelId);
            
        }
    }
    
    /**
     * 取值字段: VALUE_FIELD
     */
    public final java.lang.String getValueField(){
         onPropGet(PROP_ID_valueField);
         return _valueField;
    }

    /**
     * 取值字段: VALUE_FIELD
     */
    public final void setValueField(java.lang.String value){
        if(onPropSet(PROP_ID_valueField,value)){
            this._valueField = value;
            internalClearRefs(PROP_ID_valueField);
            
        }
    }
    
    /**
     * 聚合方式: AGGREGATION
     */
    public final java.lang.String getAggregation(){
         onPropGet(PROP_ID_aggregation);
         return _aggregation;
    }

    /**
     * 聚合方式: AGGREGATION
     */
    public final void setAggregation(java.lang.String value){
        if(onPropSet(PROP_ID_aggregation,value)){
            this._aggregation = value;
            internalClearRefs(PROP_ID_aggregation);
            
        }
    }
    
    /**
     * 比较运算符: OPERATOR
     */
    public final java.lang.String getOperator(){
         onPropGet(PROP_ID_operator);
         return _operator;
    }

    /**
     * 比较运算符: OPERATOR
     */
    public final void setOperator(java.lang.String value){
        if(onPropSet(PROP_ID_operator,value)){
            this._operator = value;
            internalClearRefs(PROP_ID_operator);
            
        }
    }
    
    /**
     * 阈值: THRESHOLD_VALUE
     */
    public final java.math.BigDecimal getThresholdValue(){
         onPropGet(PROP_ID_thresholdValue);
         return _thresholdValue;
    }

    /**
     * 阈值: THRESHOLD_VALUE
     */
    public final void setThresholdValue(java.math.BigDecimal value){
        if(onPropSet(PROP_ID_thresholdValue,value)){
            this._thresholdValue = value;
            internalClearRefs(PROP_ID_thresholdValue);
            
        }
    }
    
    /**
     * 阈值上限: THRESHOLD_VALUE2
     */
    public final java.math.BigDecimal getThresholdValue2(){
         onPropGet(PROP_ID_thresholdValue2);
         return _thresholdValue2;
    }

    /**
     * 阈值上限: THRESHOLD_VALUE2
     */
    public final void setThresholdValue2(java.math.BigDecimal value){
        if(onPropSet(PROP_ID_thresholdValue2,value)){
            this._thresholdValue2 = value;
            internalClearRefs(PROP_ID_thresholdValue2);
            
        }
    }
    
    /**
     * 冷静期秒数: REARM_SECONDS
     */
    public final java.lang.Integer getRearmSeconds(){
         onPropGet(PROP_ID_rearmSeconds);
         return _rearmSeconds;
    }

    /**
     * 冷静期秒数: REARM_SECONDS
     */
    public final void setRearmSeconds(java.lang.Integer value){
        if(onPropSet(PROP_ID_rearmSeconds,value)){
            this._rearmSeconds = value;
            internalClearRefs(PROP_ID_rearmSeconds);
            
        }
    }
    
    /**
     * 通知渠道: NOTIFY_CHANNELS
     */
    public final java.lang.String getNotifyChannels(){
         onPropGet(PROP_ID_notifyChannels);
         return _notifyChannels;
    }

    /**
     * 通知渠道: NOTIFY_CHANNELS
     */
    public final void setNotifyChannels(java.lang.String value){
        if(onPropSet(PROP_ID_notifyChannels,value)){
            this._notifyChannels = value;
            internalClearRefs(PROP_ID_notifyChannels);
            
        }
    }
    
    /**
     * 收件人: RECIPIENTS
     */
    public final java.lang.String getRecipients(){
         onPropGet(PROP_ID_recipients);
         return _recipients;
    }

    /**
     * 收件人: RECIPIENTS
     */
    public final void setRecipients(java.lang.String value){
        if(onPropSet(PROP_ID_recipients,value)){
            this._recipients = value;
            internalClearRefs(PROP_ID_recipients);
            
        }
    }
    
    /**
     * cron表达式: CRON_EXPR
     */
    public final java.lang.String getCronExpr(){
         onPropGet(PROP_ID_cronExpr);
         return _cronExpr;
    }

    /**
     * cron表达式: CRON_EXPR
     */
    public final void setCronExpr(java.lang.String value){
        if(onPropSet(PROP_ID_cronExpr,value)){
            this._cronExpr = value;
            internalClearRefs(PROP_ID_cronExpr);
            
        }
    }
    
    /**
     * 查询参数: PARAMS
     */
    public final java.lang.String getParams(){
         onPropGet(PROP_ID_params);
         return _params;
    }

    /**
     * 查询参数: PARAMS
     */
    public final void setParams(java.lang.String value){
        if(onPropSet(PROP_ID_params,value)){
            this._params = value;
            internalClearRefs(PROP_ID_params);
            
        }
    }
    
    /**
     * 模板键: TEMPLATE_KEY
     */
    public final java.lang.String getTemplateKey(){
         onPropGet(PROP_ID_templateKey);
         return _templateKey;
    }

    /**
     * 模板键: TEMPLATE_KEY
     */
    public final void setTemplateKey(java.lang.String value){
        if(onPropSet(PROP_ID_templateKey,value)){
            this._templateKey = value;
            internalClearRefs(PROP_ID_templateKey);
            
        }
    }
    
    /**
     * 规则状态: STATUS
     */
    public final java.lang.Integer getStatus(){
         onPropGet(PROP_ID_status);
         return _status;
    }

    /**
     * 规则状态: STATUS
     */
    public final void setStatus(java.lang.Integer value){
        if(onPropSet(PROP_ID_status,value)){
            this._status = value;
            internalClearRefs(PROP_ID_status);
            
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
     * 面板
     */
    public final io.nop.datav.dao.entity.NopDatavPanel getPanel(){
       return (io.nop.datav.dao.entity.NopDatavPanel)internalGetRefEntity(PROP_NAME_panel);
    }

    public final void setPanel(io.nop.datav.dao.entity.NopDatavPanel refEntity){
   
           if(refEntity == null){
           
                   this.setPanelId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_panel, refEntity,()->{
           
                           this.setPanelId(refEntity.getPanelId());
                       
           });
           }
       
    }
       
   private io.nop.orm.component.JsonOrmComponent _notifyChannelsComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_notifyChannelsComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_notifyChannelsComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_notifyChannels);
      
   }

   public final io.nop.orm.component.JsonOrmComponent getNotifyChannelsComponent(){
      if(_notifyChannelsComponent == null){
          _notifyChannelsComponent = new io.nop.orm.component.JsonOrmComponent();
          _notifyChannelsComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_notifyChannelsComponent);
      }
      return _notifyChannelsComponent;
   }

   private io.nop.orm.component.JsonOrmComponent _recipientsComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_recipientsComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_recipientsComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_recipients);
      
   }

   public final io.nop.orm.component.JsonOrmComponent getRecipientsComponent(){
      if(_recipientsComponent == null){
          _recipientsComponent = new io.nop.orm.component.JsonOrmComponent();
          _recipientsComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_recipientsComponent);
      }
      return _recipientsComponent;
   }

   private io.nop.orm.component.JsonOrmComponent _paramsComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_paramsComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_paramsComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_params);
      
   }

   public final io.nop.orm.component.JsonOrmComponent getParamsComponent(){
      if(_paramsComponent == null){
          _paramsComponent = new io.nop.orm.component.JsonOrmComponent();
          _paramsComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_paramsComponent);
      }
      return _paramsComponent;
   }

}
// resume CPD analysis - CPD-ON
