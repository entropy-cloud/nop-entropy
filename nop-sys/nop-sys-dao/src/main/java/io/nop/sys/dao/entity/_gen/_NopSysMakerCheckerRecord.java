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

import io.nop.sys.dao.entity.NopSysMakerCheckerRecord;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  MakerChecker审批记录: nop_sys_maker_checker_record
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116"})
public class _NopSysMakerCheckerRecord extends DynamicOrmEntity{
    
    /* 主键: SID VARCHAR */
    public static final String PROP_NAME_sid = "sid";
    public static final int PROP_ID_sid = 1;
    
    /* 业务对象名: BIZ_OBJ_NAME VARCHAR */
    public static final String PROP_NAME_bizObjName = "bizObjName";
    public static final int PROP_ID_bizObjName = 2;
    
    /* 业务对象ID: BIZ_OBJ_ID VARCHAR */
    public static final String PROP_NAME_bizObjId = "bizObjId";
    public static final int PROP_ID_bizObjId = 3;
    
    /* 请求发起人ID: MAKER_ID VARCHAR */
    public static final String PROP_NAME_makerId = "makerId";
    public static final int PROP_ID_makerId = 4;
    
    /* 请求发起人: MAKER_NAME VARCHAR */
    public static final String PROP_NAME_makerName = "makerName";
    public static final int PROP_ID_makerName = 5;
    
    /* 请求操作: REQUEST_ACTION VARCHAR */
    public static final String PROP_NAME_requestAction = "requestAction";
    public static final int PROP_ID_requestAction = 6;
    
    /* 请求数据: REQUEST_DATA VARCHAR */
    public static final String PROP_NAME_requestData = "requestData";
    public static final int PROP_ID_requestData = 7;
    
    /* 请求时间: REQUEST_TIME DATETIME */
    public static final String PROP_NAME_requestTime = "requestTime";
    public static final int PROP_ID_requestTime = 8;
    
    /* 审批人ID: CHECKER_ID VARCHAR */
    public static final String PROP_NAME_checkerId = "checkerId";
    public static final int PROP_ID_checkerId = 9;
    
    /* 审批人: CHECKER_NAME VARCHAR */
    public static final String PROP_NAME_checkerName = "checkerName";
    public static final int PROP_ID_checkerName = 10;
    
    /* 审批时间: CHECK_TIME DATETIME */
    public static final String PROP_NAME_checkTime = "checkTime";
    public static final int PROP_ID_checkTime = 11;
    
    /* 请求结果: TRY_RESULT VARCHAR */
    public static final String PROP_NAME_tryResult = "tryResult";
    public static final int PROP_ID_tryResult = 12;
    
    /* 输入页面: INPUT_PAGE VARCHAR */
    public static final String PROP_NAME_inputPage = "inputPage";
    public static final int PROP_ID_inputPage = 13;
    
    /* 审批状态: STATUS INTEGER */
    public static final String PROP_NAME_status = "status";
    public static final int PROP_ID_status = 14;
    
    /* 取消方法: CANCEL_ACTION VARCHAR */
    public static final String PROP_NAME_cancelAction = "cancelAction";
    public static final int PROP_ID_cancelAction = 15;
    
    /* 回调错误码: CB_ERR_CODE VARCHAR */
    public static final String PROP_NAME_cbErrCode = "cbErrCode";
    public static final int PROP_ID_cbErrCode = 16;
    
    /* 回调错误消息: CE_ERR_MSG VARCHAR */
    public static final String PROP_NAME_ceErrMsg = "ceErrMsg";
    public static final int PROP_ID_ceErrMsg = 17;
    
    /* 数据版本: VERSION INTEGER */
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

    

    public static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_sid);
    public static final int[] PK_PROP_IDS = new int[]{PROP_ID_sid};

    private static final String[] PROP_ID_TO_NAME = new String[24];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_sid] = PROP_NAME_sid;
          PROP_NAME_TO_ID.put(PROP_NAME_sid, PROP_ID_sid);
      
          PROP_ID_TO_NAME[PROP_ID_bizObjName] = PROP_NAME_bizObjName;
          PROP_NAME_TO_ID.put(PROP_NAME_bizObjName, PROP_ID_bizObjName);
      
          PROP_ID_TO_NAME[PROP_ID_bizObjId] = PROP_NAME_bizObjId;
          PROP_NAME_TO_ID.put(PROP_NAME_bizObjId, PROP_ID_bizObjId);
      
          PROP_ID_TO_NAME[PROP_ID_makerId] = PROP_NAME_makerId;
          PROP_NAME_TO_ID.put(PROP_NAME_makerId, PROP_ID_makerId);
      
          PROP_ID_TO_NAME[PROP_ID_makerName] = PROP_NAME_makerName;
          PROP_NAME_TO_ID.put(PROP_NAME_makerName, PROP_ID_makerName);
      
          PROP_ID_TO_NAME[PROP_ID_requestAction] = PROP_NAME_requestAction;
          PROP_NAME_TO_ID.put(PROP_NAME_requestAction, PROP_ID_requestAction);
      
          PROP_ID_TO_NAME[PROP_ID_requestData] = PROP_NAME_requestData;
          PROP_NAME_TO_ID.put(PROP_NAME_requestData, PROP_ID_requestData);
      
          PROP_ID_TO_NAME[PROP_ID_requestTime] = PROP_NAME_requestTime;
          PROP_NAME_TO_ID.put(PROP_NAME_requestTime, PROP_ID_requestTime);
      
          PROP_ID_TO_NAME[PROP_ID_checkerId] = PROP_NAME_checkerId;
          PROP_NAME_TO_ID.put(PROP_NAME_checkerId, PROP_ID_checkerId);
      
          PROP_ID_TO_NAME[PROP_ID_checkerName] = PROP_NAME_checkerName;
          PROP_NAME_TO_ID.put(PROP_NAME_checkerName, PROP_ID_checkerName);
      
          PROP_ID_TO_NAME[PROP_ID_checkTime] = PROP_NAME_checkTime;
          PROP_NAME_TO_ID.put(PROP_NAME_checkTime, PROP_ID_checkTime);
      
          PROP_ID_TO_NAME[PROP_ID_tryResult] = PROP_NAME_tryResult;
          PROP_NAME_TO_ID.put(PROP_NAME_tryResult, PROP_ID_tryResult);
      
          PROP_ID_TO_NAME[PROP_ID_inputPage] = PROP_NAME_inputPage;
          PROP_NAME_TO_ID.put(PROP_NAME_inputPage, PROP_ID_inputPage);
      
          PROP_ID_TO_NAME[PROP_ID_status] = PROP_NAME_status;
          PROP_NAME_TO_ID.put(PROP_NAME_status, PROP_ID_status);
      
          PROP_ID_TO_NAME[PROP_ID_cancelAction] = PROP_NAME_cancelAction;
          PROP_NAME_TO_ID.put(PROP_NAME_cancelAction, PROP_ID_cancelAction);
      
          PROP_ID_TO_NAME[PROP_ID_cbErrCode] = PROP_NAME_cbErrCode;
          PROP_NAME_TO_ID.put(PROP_NAME_cbErrCode, PROP_ID_cbErrCode);
      
          PROP_ID_TO_NAME[PROP_ID_ceErrMsg] = PROP_NAME_ceErrMsg;
          PROP_NAME_TO_ID.put(PROP_NAME_ceErrMsg, PROP_ID_ceErrMsg);
      
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
    
    /* 业务对象名: BIZ_OBJ_NAME */
    private java.lang.String _bizObjName;
    
    /* 业务对象ID: BIZ_OBJ_ID */
    private java.lang.String _bizObjId;
    
    /* 请求发起人ID: MAKER_ID */
    private java.lang.String _makerId;
    
    /* 请求发起人: MAKER_NAME */
    private java.lang.String _makerName;
    
    /* 请求操作: REQUEST_ACTION */
    private java.lang.String _requestAction;
    
    /* 请求数据: REQUEST_DATA */
    private java.lang.String _requestData;
    
    /* 请求时间: REQUEST_TIME */
    private java.time.LocalDateTime _requestTime;
    
    /* 审批人ID: CHECKER_ID */
    private java.lang.String _checkerId;
    
    /* 审批人: CHECKER_NAME */
    private java.lang.String _checkerName;
    
    /* 审批时间: CHECK_TIME */
    private java.time.LocalDateTime _checkTime;
    
    /* 请求结果: TRY_RESULT */
    private java.lang.String _tryResult;
    
    /* 输入页面: INPUT_PAGE */
    private java.lang.String _inputPage;
    
    /* 审批状态: STATUS */
    private java.lang.Integer _status;
    
    /* 取消方法: CANCEL_ACTION */
    private java.lang.String _cancelAction;
    
    /* 回调错误码: CB_ERR_CODE */
    private java.lang.String _cbErrCode;
    
    /* 回调错误消息: CE_ERR_MSG */
    private java.lang.String _ceErrMsg;
    
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
    

    public _NopSysMakerCheckerRecord(){
    }

    protected NopSysMakerCheckerRecord newInstance(){
       return new NopSysMakerCheckerRecord();
    }

    @Override
    public NopSysMakerCheckerRecord cloneInstance() {
        NopSysMakerCheckerRecord entity = newInstance();
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
      return "io.nop.sys.dao.entity.NopSysMakerCheckerRecord";
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
        
            case PROP_ID_bizObjName:
               return getBizObjName();
        
            case PROP_ID_bizObjId:
               return getBizObjId();
        
            case PROP_ID_makerId:
               return getMakerId();
        
            case PROP_ID_makerName:
               return getMakerName();
        
            case PROP_ID_requestAction:
               return getRequestAction();
        
            case PROP_ID_requestData:
               return getRequestData();
        
            case PROP_ID_requestTime:
               return getRequestTime();
        
            case PROP_ID_checkerId:
               return getCheckerId();
        
            case PROP_ID_checkerName:
               return getCheckerName();
        
            case PROP_ID_checkTime:
               return getCheckTime();
        
            case PROP_ID_tryResult:
               return getTryResult();
        
            case PROP_ID_inputPage:
               return getInputPage();
        
            case PROP_ID_status:
               return getStatus();
        
            case PROP_ID_cancelAction:
               return getCancelAction();
        
            case PROP_ID_cbErrCode:
               return getCbErrCode();
        
            case PROP_ID_ceErrMsg:
               return getCeErrMsg();
        
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
        
            case PROP_ID_bizObjName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_bizObjName));
               }
               setBizObjName(typedValue);
               break;
            }
        
            case PROP_ID_bizObjId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_bizObjId));
               }
               setBizObjId(typedValue);
               break;
            }
        
            case PROP_ID_makerId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_makerId));
               }
               setMakerId(typedValue);
               break;
            }
        
            case PROP_ID_makerName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_makerName));
               }
               setMakerName(typedValue);
               break;
            }
        
            case PROP_ID_requestAction:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_requestAction));
               }
               setRequestAction(typedValue);
               break;
            }
        
            case PROP_ID_requestData:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_requestData));
               }
               setRequestData(typedValue);
               break;
            }
        
            case PROP_ID_requestTime:{
               java.time.LocalDateTime typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLocalDateTime(value,
                       err-> newTypeConversionError(PROP_NAME_requestTime));
               }
               setRequestTime(typedValue);
               break;
            }
        
            case PROP_ID_checkerId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_checkerId));
               }
               setCheckerId(typedValue);
               break;
            }
        
            case PROP_ID_checkerName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_checkerName));
               }
               setCheckerName(typedValue);
               break;
            }
        
            case PROP_ID_checkTime:{
               java.time.LocalDateTime typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLocalDateTime(value,
                       err-> newTypeConversionError(PROP_NAME_checkTime));
               }
               setCheckTime(typedValue);
               break;
            }
        
            case PROP_ID_tryResult:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_tryResult));
               }
               setTryResult(typedValue);
               break;
            }
        
            case PROP_ID_inputPage:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_inputPage));
               }
               setInputPage(typedValue);
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
        
            case PROP_ID_cancelAction:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_cancelAction));
               }
               setCancelAction(typedValue);
               break;
            }
        
            case PROP_ID_cbErrCode:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_cbErrCode));
               }
               setCbErrCode(typedValue);
               break;
            }
        
            case PROP_ID_ceErrMsg:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_ceErrMsg));
               }
               setCeErrMsg(typedValue);
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
        
            case PROP_ID_bizObjName:{
               onInitProp(propId);
               this._bizObjName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_bizObjId:{
               onInitProp(propId);
               this._bizObjId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_makerId:{
               onInitProp(propId);
               this._makerId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_makerName:{
               onInitProp(propId);
               this._makerName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_requestAction:{
               onInitProp(propId);
               this._requestAction = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_requestData:{
               onInitProp(propId);
               this._requestData = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_requestTime:{
               onInitProp(propId);
               this._requestTime = (java.time.LocalDateTime)value;
               
               break;
            }
        
            case PROP_ID_checkerId:{
               onInitProp(propId);
               this._checkerId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_checkerName:{
               onInitProp(propId);
               this._checkerName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_checkTime:{
               onInitProp(propId);
               this._checkTime = (java.time.LocalDateTime)value;
               
               break;
            }
        
            case PROP_ID_tryResult:{
               onInitProp(propId);
               this._tryResult = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_inputPage:{
               onInitProp(propId);
               this._inputPage = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_status:{
               onInitProp(propId);
               this._status = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_cancelAction:{
               onInitProp(propId);
               this._cancelAction = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_cbErrCode:{
               onInitProp(propId);
               this._cbErrCode = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_ceErrMsg:{
               onInitProp(propId);
               this._ceErrMsg = (java.lang.String)value;
               
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
     * 业务对象名: BIZ_OBJ_NAME
     */
    public java.lang.String getBizObjName(){
         onPropGet(PROP_ID_bizObjName);
         return _bizObjName;
    }

    /**
     * 业务对象名: BIZ_OBJ_NAME
     */
    public void setBizObjName(java.lang.String value){
        if(onPropSet(PROP_ID_bizObjName,value)){
            this._bizObjName = value;
            internalClearRefs(PROP_ID_bizObjName);
            
        }
    }
    
    /**
     * 业务对象ID: BIZ_OBJ_ID
     */
    public java.lang.String getBizObjId(){
         onPropGet(PROP_ID_bizObjId);
         return _bizObjId;
    }

    /**
     * 业务对象ID: BIZ_OBJ_ID
     */
    public void setBizObjId(java.lang.String value){
        if(onPropSet(PROP_ID_bizObjId,value)){
            this._bizObjId = value;
            internalClearRefs(PROP_ID_bizObjId);
            
        }
    }
    
    /**
     * 请求发起人ID: MAKER_ID
     */
    public java.lang.String getMakerId(){
         onPropGet(PROP_ID_makerId);
         return _makerId;
    }

    /**
     * 请求发起人ID: MAKER_ID
     */
    public void setMakerId(java.lang.String value){
        if(onPropSet(PROP_ID_makerId,value)){
            this._makerId = value;
            internalClearRefs(PROP_ID_makerId);
            
        }
    }
    
    /**
     * 请求发起人: MAKER_NAME
     */
    public java.lang.String getMakerName(){
         onPropGet(PROP_ID_makerName);
         return _makerName;
    }

    /**
     * 请求发起人: MAKER_NAME
     */
    public void setMakerName(java.lang.String value){
        if(onPropSet(PROP_ID_makerName,value)){
            this._makerName = value;
            internalClearRefs(PROP_ID_makerName);
            
        }
    }
    
    /**
     * 请求操作: REQUEST_ACTION
     */
    public java.lang.String getRequestAction(){
         onPropGet(PROP_ID_requestAction);
         return _requestAction;
    }

    /**
     * 请求操作: REQUEST_ACTION
     */
    public void setRequestAction(java.lang.String value){
        if(onPropSet(PROP_ID_requestAction,value)){
            this._requestAction = value;
            internalClearRefs(PROP_ID_requestAction);
            
        }
    }
    
    /**
     * 请求数据: REQUEST_DATA
     */
    public java.lang.String getRequestData(){
         onPropGet(PROP_ID_requestData);
         return _requestData;
    }

    /**
     * 请求数据: REQUEST_DATA
     */
    public void setRequestData(java.lang.String value){
        if(onPropSet(PROP_ID_requestData,value)){
            this._requestData = value;
            internalClearRefs(PROP_ID_requestData);
            
        }
    }
    
    /**
     * 请求时间: REQUEST_TIME
     */
    public java.time.LocalDateTime getRequestTime(){
         onPropGet(PROP_ID_requestTime);
         return _requestTime;
    }

    /**
     * 请求时间: REQUEST_TIME
     */
    public void setRequestTime(java.time.LocalDateTime value){
        if(onPropSet(PROP_ID_requestTime,value)){
            this._requestTime = value;
            internalClearRefs(PROP_ID_requestTime);
            
        }
    }
    
    /**
     * 审批人ID: CHECKER_ID
     */
    public java.lang.String getCheckerId(){
         onPropGet(PROP_ID_checkerId);
         return _checkerId;
    }

    /**
     * 审批人ID: CHECKER_ID
     */
    public void setCheckerId(java.lang.String value){
        if(onPropSet(PROP_ID_checkerId,value)){
            this._checkerId = value;
            internalClearRefs(PROP_ID_checkerId);
            
        }
    }
    
    /**
     * 审批人: CHECKER_NAME
     */
    public java.lang.String getCheckerName(){
         onPropGet(PROP_ID_checkerName);
         return _checkerName;
    }

    /**
     * 审批人: CHECKER_NAME
     */
    public void setCheckerName(java.lang.String value){
        if(onPropSet(PROP_ID_checkerName,value)){
            this._checkerName = value;
            internalClearRefs(PROP_ID_checkerName);
            
        }
    }
    
    /**
     * 审批时间: CHECK_TIME
     */
    public java.time.LocalDateTime getCheckTime(){
         onPropGet(PROP_ID_checkTime);
         return _checkTime;
    }

    /**
     * 审批时间: CHECK_TIME
     */
    public void setCheckTime(java.time.LocalDateTime value){
        if(onPropSet(PROP_ID_checkTime,value)){
            this._checkTime = value;
            internalClearRefs(PROP_ID_checkTime);
            
        }
    }
    
    /**
     * 请求结果: TRY_RESULT
     */
    public java.lang.String getTryResult(){
         onPropGet(PROP_ID_tryResult);
         return _tryResult;
    }

    /**
     * 请求结果: TRY_RESULT
     */
    public void setTryResult(java.lang.String value){
        if(onPropSet(PROP_ID_tryResult,value)){
            this._tryResult = value;
            internalClearRefs(PROP_ID_tryResult);
            
        }
    }
    
    /**
     * 输入页面: INPUT_PAGE
     */
    public java.lang.String getInputPage(){
         onPropGet(PROP_ID_inputPage);
         return _inputPage;
    }

    /**
     * 输入页面: INPUT_PAGE
     */
    public void setInputPage(java.lang.String value){
        if(onPropSet(PROP_ID_inputPage,value)){
            this._inputPage = value;
            internalClearRefs(PROP_ID_inputPage);
            
        }
    }
    
    /**
     * 审批状态: STATUS
     */
    public java.lang.Integer getStatus(){
         onPropGet(PROP_ID_status);
         return _status;
    }

    /**
     * 审批状态: STATUS
     */
    public void setStatus(java.lang.Integer value){
        if(onPropSet(PROP_ID_status,value)){
            this._status = value;
            internalClearRefs(PROP_ID_status);
            
        }
    }
    
    /**
     * 取消方法: CANCEL_ACTION
     */
    public java.lang.String getCancelAction(){
         onPropGet(PROP_ID_cancelAction);
         return _cancelAction;
    }

    /**
     * 取消方法: CANCEL_ACTION
     */
    public void setCancelAction(java.lang.String value){
        if(onPropSet(PROP_ID_cancelAction,value)){
            this._cancelAction = value;
            internalClearRefs(PROP_ID_cancelAction);
            
        }
    }
    
    /**
     * 回调错误码: CB_ERR_CODE
     */
    public java.lang.String getCbErrCode(){
         onPropGet(PROP_ID_cbErrCode);
         return _cbErrCode;
    }

    /**
     * 回调错误码: CB_ERR_CODE
     */
    public void setCbErrCode(java.lang.String value){
        if(onPropSet(PROP_ID_cbErrCode,value)){
            this._cbErrCode = value;
            internalClearRefs(PROP_ID_cbErrCode);
            
        }
    }
    
    /**
     * 回调错误消息: CE_ERR_MSG
     */
    public java.lang.String getCeErrMsg(){
         onPropGet(PROP_ID_ceErrMsg);
         return _ceErrMsg;
    }

    /**
     * 回调错误消息: CE_ERR_MSG
     */
    public void setCeErrMsg(java.lang.String value){
        if(onPropSet(PROP_ID_ceErrMsg,value)){
            this._ceErrMsg = value;
            internalClearRefs(PROP_ID_ceErrMsg);
            
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
    
}
// resume CPD analysis - CPD-ON
