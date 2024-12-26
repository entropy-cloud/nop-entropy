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

import io.nop.dyn.dao.entity.NopDynEntity;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  动态实体: nop_dyn_entity
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopDynEntity extends DynamicOrmEntity{
    
    /* 主键: SID VARCHAR */
    public static final String PROP_NAME_sid = "sid";
    public static final int PROP_ID_sid = 1;
    
    /* 对象类型: NOP_OBJ_TYPE VARCHAR */
    public static final String PROP_NAME_nopObjType = "nopObjType";
    public static final int PROP_ID_nopObjType = 2;
    
    /* 名称: NOP_NAME VARCHAR */
    public static final String PROP_NAME_nopName = "nopName";
    public static final int PROP_ID_nopName = 3;
    
    /* 显示名称: NOP_DISPLAY_NAME VARCHAR */
    public static final String PROP_NAME_nopDisplayName = "nopDisplayName";
    public static final int PROP_ID_nopDisplayName = 4;
    
    /* 排序: NOP_SORT_ORDER INTEGER */
    public static final String PROP_NAME_nopSortOrder = "nopSortOrder";
    public static final int PROP_ID_nopSortOrder = 5;
    
    /* 工作流实例ID: NOP_FLOW_ID VARCHAR */
    public static final String PROP_NAME_nopFlowId = "nopFlowId";
    public static final int PROP_ID_nopFlowId = 6;
    
    /* 业务状态码: NOP_STATUS INTEGER */
    public static final String PROP_NAME_nopStatus = "nopStatus";
    public static final int PROP_ID_nopStatus = 7;
    
    /* 业务状态: NOP_BIZ_STATE VARCHAR */
    public static final String PROP_NAME_nopBizState = "nopBizState";
    public static final int PROP_ID_nopBizState = 8;
    
    /* 父ID: NOP_PARENT_ID VARCHAR */
    public static final String PROP_NAME_nopParentId = "nopParentId";
    public static final int PROP_ID_nopParentId = 9;
    
    /* 拥有者姓名: NOP_OWNER_NAME VARCHAR */
    public static final String PROP_NAME_nopOwnerName = "nopOwnerName";
    public static final int PROP_ID_nopOwnerName = 10;
    
    /* 拥有者ID: NOP_OWNER_ID VARCHAR */
    public static final String PROP_NAME_nopOwnerId = "nopOwnerId";
    public static final int PROP_ID_nopOwnerId = 11;
    
    /* 部门ID: NOP_DEPT_ID VARCHAR */
    public static final String PROP_NAME_nopDeptId = "nopDeptId";
    public static final int PROP_ID_nopDeptId = 12;
    
    /* 字符串字段1: NOP_STRING_FLD1 VARCHAR */
    public static final String PROP_NAME_nopStringFld1 = "nopStringFld1";
    public static final int PROP_ID_nopStringFld1 = 13;
    
    /* 浮点型字段1: NOP_DECIMAL_FLD1 DECIMAL */
    public static final String PROP_NAME_nopDecimalFld1 = "nopDecimalFld1";
    public static final int PROP_ID_nopDecimalFld1 = 14;
    
    /* 整数型字段1: NOP_INT_FLD1 INTEGER */
    public static final String PROP_NAME_nopIntFld1 = "nopIntFld1";
    public static final int PROP_ID_nopIntFld1 = 15;
    
    /* 长整型字段1: NOP_LONG_FLD1 BIGINT */
    public static final String PROP_NAME_nopLongFld1 = "nopLongFld1";
    public static final int PROP_ID_nopLongFld1 = 16;
    
    /* 日期字段1: NOP_DATE_FLD1 DATE */
    public static final String PROP_NAME_nopDateFld1 = "nopDateFld1";
    public static final int PROP_ID_nopDateFld1 = 17;
    
    /* 时间戳字段1: NOP_TIMESTAMP_FLD1 TIMESTAMP */
    public static final String PROP_NAME_nopTimestampFld1 = "nopTimestampFld1";
    public static final int PROP_ID_nopTimestampFld1 = 18;
    
    /* 文件字段1: NOP_FILE_FLD1 VARCHAR */
    public static final String PROP_NAME_nopFileFld1 = "nopFileFld1";
    public static final int PROP_ID_nopFileFld1 = 19;
    
    /* 字符串字段2: NOP_STRING_FLD2 VARCHAR */
    public static final String PROP_NAME_nopStringFld2 = "nopStringFld2";
    public static final int PROP_ID_nopStringFld2 = 20;
    
    /* 浮点型字段2: NOP_DECIMAL_FLD2 DECIMAL */
    public static final String PROP_NAME_nopDecimalFld2 = "nopDecimalFld2";
    public static final int PROP_ID_nopDecimalFld2 = 21;
    
    /* 整数型字段2: NOP_INT_FLD2 INTEGER */
    public static final String PROP_NAME_nopIntFld2 = "nopIntFld2";
    public static final int PROP_ID_nopIntFld2 = 22;
    
    /* 长整型字段2: NOP_LONG_FLD2 BIGINT */
    public static final String PROP_NAME_nopLongFld2 = "nopLongFld2";
    public static final int PROP_ID_nopLongFld2 = 23;
    
    /* 日期字段2: NOP_DATE_FLD2 DATE */
    public static final String PROP_NAME_nopDateFld2 = "nopDateFld2";
    public static final int PROP_ID_nopDateFld2 = 24;
    
    /* 时间戳字段2: NOP_TIMESTAMP_FLD2 TIMESTAMP */
    public static final String PROP_NAME_nopTimestampFld2 = "nopTimestampFld2";
    public static final int PROP_ID_nopTimestampFld2 = 25;
    
    /* 文件字段2: NOP_FILE_FLD2 VARCHAR */
    public static final String PROP_NAME_nopFileFld2 = "nopFileFld2";
    public static final int PROP_ID_nopFileFld2 = 26;
    
    /* 数据版本: VERSION INTEGER */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 27;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 28;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 29;
    
    /* 修改人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 30;
    
    /* 修改时间: UPDATE_TIME TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 31;
    
    /* 备注: REMARK VARCHAR */
    public static final String PROP_NAME_remark = "remark";
    public static final int PROP_ID_remark = 32;
    

    private static int _PROP_ID_BOUND = 33;

    
    /* relation: 父对象 */
    public static final String PROP_NAME_nopParent = "nopParent";
    
    /* relation: 子对象 */
    public static final String PROP_NAME_nopChildren = "nopChildren";
    
    /* relation:  */
    public static final String PROP_NAME_extFields = "extFields";
    
    /* component:  */
    public static final String PROP_NAME_nopFileFld1Component = "nopFileFld1Component";
    
    /* component:  */
    public static final String PROP_NAME_nopFileFld2Component = "nopFileFld2Component";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_sid);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_sid};

    private static final String[] PROP_ID_TO_NAME = new String[33];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_sid] = PROP_NAME_sid;
          PROP_NAME_TO_ID.put(PROP_NAME_sid, PROP_ID_sid);
      
          PROP_ID_TO_NAME[PROP_ID_nopObjType] = PROP_NAME_nopObjType;
          PROP_NAME_TO_ID.put(PROP_NAME_nopObjType, PROP_ID_nopObjType);
      
          PROP_ID_TO_NAME[PROP_ID_nopName] = PROP_NAME_nopName;
          PROP_NAME_TO_ID.put(PROP_NAME_nopName, PROP_ID_nopName);
      
          PROP_ID_TO_NAME[PROP_ID_nopDisplayName] = PROP_NAME_nopDisplayName;
          PROP_NAME_TO_ID.put(PROP_NAME_nopDisplayName, PROP_ID_nopDisplayName);
      
          PROP_ID_TO_NAME[PROP_ID_nopSortOrder] = PROP_NAME_nopSortOrder;
          PROP_NAME_TO_ID.put(PROP_NAME_nopSortOrder, PROP_ID_nopSortOrder);
      
          PROP_ID_TO_NAME[PROP_ID_nopFlowId] = PROP_NAME_nopFlowId;
          PROP_NAME_TO_ID.put(PROP_NAME_nopFlowId, PROP_ID_nopFlowId);
      
          PROP_ID_TO_NAME[PROP_ID_nopStatus] = PROP_NAME_nopStatus;
          PROP_NAME_TO_ID.put(PROP_NAME_nopStatus, PROP_ID_nopStatus);
      
          PROP_ID_TO_NAME[PROP_ID_nopBizState] = PROP_NAME_nopBizState;
          PROP_NAME_TO_ID.put(PROP_NAME_nopBizState, PROP_ID_nopBizState);
      
          PROP_ID_TO_NAME[PROP_ID_nopParentId] = PROP_NAME_nopParentId;
          PROP_NAME_TO_ID.put(PROP_NAME_nopParentId, PROP_ID_nopParentId);
      
          PROP_ID_TO_NAME[PROP_ID_nopOwnerName] = PROP_NAME_nopOwnerName;
          PROP_NAME_TO_ID.put(PROP_NAME_nopOwnerName, PROP_ID_nopOwnerName);
      
          PROP_ID_TO_NAME[PROP_ID_nopOwnerId] = PROP_NAME_nopOwnerId;
          PROP_NAME_TO_ID.put(PROP_NAME_nopOwnerId, PROP_ID_nopOwnerId);
      
          PROP_ID_TO_NAME[PROP_ID_nopDeptId] = PROP_NAME_nopDeptId;
          PROP_NAME_TO_ID.put(PROP_NAME_nopDeptId, PROP_ID_nopDeptId);
      
          PROP_ID_TO_NAME[PROP_ID_nopStringFld1] = PROP_NAME_nopStringFld1;
          PROP_NAME_TO_ID.put(PROP_NAME_nopStringFld1, PROP_ID_nopStringFld1);
      
          PROP_ID_TO_NAME[PROP_ID_nopDecimalFld1] = PROP_NAME_nopDecimalFld1;
          PROP_NAME_TO_ID.put(PROP_NAME_nopDecimalFld1, PROP_ID_nopDecimalFld1);
      
          PROP_ID_TO_NAME[PROP_ID_nopIntFld1] = PROP_NAME_nopIntFld1;
          PROP_NAME_TO_ID.put(PROP_NAME_nopIntFld1, PROP_ID_nopIntFld1);
      
          PROP_ID_TO_NAME[PROP_ID_nopLongFld1] = PROP_NAME_nopLongFld1;
          PROP_NAME_TO_ID.put(PROP_NAME_nopLongFld1, PROP_ID_nopLongFld1);
      
          PROP_ID_TO_NAME[PROP_ID_nopDateFld1] = PROP_NAME_nopDateFld1;
          PROP_NAME_TO_ID.put(PROP_NAME_nopDateFld1, PROP_ID_nopDateFld1);
      
          PROP_ID_TO_NAME[PROP_ID_nopTimestampFld1] = PROP_NAME_nopTimestampFld1;
          PROP_NAME_TO_ID.put(PROP_NAME_nopTimestampFld1, PROP_ID_nopTimestampFld1);
      
          PROP_ID_TO_NAME[PROP_ID_nopFileFld1] = PROP_NAME_nopFileFld1;
          PROP_NAME_TO_ID.put(PROP_NAME_nopFileFld1, PROP_ID_nopFileFld1);
      
          PROP_ID_TO_NAME[PROP_ID_nopStringFld2] = PROP_NAME_nopStringFld2;
          PROP_NAME_TO_ID.put(PROP_NAME_nopStringFld2, PROP_ID_nopStringFld2);
      
          PROP_ID_TO_NAME[PROP_ID_nopDecimalFld2] = PROP_NAME_nopDecimalFld2;
          PROP_NAME_TO_ID.put(PROP_NAME_nopDecimalFld2, PROP_ID_nopDecimalFld2);
      
          PROP_ID_TO_NAME[PROP_ID_nopIntFld2] = PROP_NAME_nopIntFld2;
          PROP_NAME_TO_ID.put(PROP_NAME_nopIntFld2, PROP_ID_nopIntFld2);
      
          PROP_ID_TO_NAME[PROP_ID_nopLongFld2] = PROP_NAME_nopLongFld2;
          PROP_NAME_TO_ID.put(PROP_NAME_nopLongFld2, PROP_ID_nopLongFld2);
      
          PROP_ID_TO_NAME[PROP_ID_nopDateFld2] = PROP_NAME_nopDateFld2;
          PROP_NAME_TO_ID.put(PROP_NAME_nopDateFld2, PROP_ID_nopDateFld2);
      
          PROP_ID_TO_NAME[PROP_ID_nopTimestampFld2] = PROP_NAME_nopTimestampFld2;
          PROP_NAME_TO_ID.put(PROP_NAME_nopTimestampFld2, PROP_ID_nopTimestampFld2);
      
          PROP_ID_TO_NAME[PROP_ID_nopFileFld2] = PROP_NAME_nopFileFld2;
          PROP_NAME_TO_ID.put(PROP_NAME_nopFileFld2, PROP_ID_nopFileFld2);
      
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
    
    /* 对象类型: NOP_OBJ_TYPE */
    private java.lang.String _nopObjType;
    
    /* 名称: NOP_NAME */
    private java.lang.String _nopName;
    
    /* 显示名称: NOP_DISPLAY_NAME */
    private java.lang.String _nopDisplayName;
    
    /* 排序: NOP_SORT_ORDER */
    private java.lang.Integer _nopSortOrder;
    
    /* 工作流实例ID: NOP_FLOW_ID */
    private java.lang.String _nopFlowId;
    
    /* 业务状态码: NOP_STATUS */
    private java.lang.Integer _nopStatus;
    
    /* 业务状态: NOP_BIZ_STATE */
    private java.lang.String _nopBizState;
    
    /* 父ID: NOP_PARENT_ID */
    private java.lang.String _nopParentId;
    
    /* 拥有者姓名: NOP_OWNER_NAME */
    private java.lang.String _nopOwnerName;
    
    /* 拥有者ID: NOP_OWNER_ID */
    private java.lang.String _nopOwnerId;
    
    /* 部门ID: NOP_DEPT_ID */
    private java.lang.String _nopDeptId;
    
    /* 字符串字段1: NOP_STRING_FLD1 */
    private java.lang.String _nopStringFld1;
    
    /* 浮点型字段1: NOP_DECIMAL_FLD1 */
    private java.math.BigDecimal _nopDecimalFld1;
    
    /* 整数型字段1: NOP_INT_FLD1 */
    private java.lang.Integer _nopIntFld1;
    
    /* 长整型字段1: NOP_LONG_FLD1 */
    private java.lang.Long _nopLongFld1;
    
    /* 日期字段1: NOP_DATE_FLD1 */
    private java.time.LocalDate _nopDateFld1;
    
    /* 时间戳字段1: NOP_TIMESTAMP_FLD1 */
    private java.sql.Timestamp _nopTimestampFld1;
    
    /* 文件字段1: NOP_FILE_FLD1 */
    private java.lang.String _nopFileFld1;
    
    /* 字符串字段2: NOP_STRING_FLD2 */
    private java.lang.String _nopStringFld2;
    
    /* 浮点型字段2: NOP_DECIMAL_FLD2 */
    private java.math.BigDecimal _nopDecimalFld2;
    
    /* 整数型字段2: NOP_INT_FLD2 */
    private java.lang.Integer _nopIntFld2;
    
    /* 长整型字段2: NOP_LONG_FLD2 */
    private java.lang.Long _nopLongFld2;
    
    /* 日期字段2: NOP_DATE_FLD2 */
    private java.time.LocalDate _nopDateFld2;
    
    /* 时间戳字段2: NOP_TIMESTAMP_FLD2 */
    private java.sql.Timestamp _nopTimestampFld2;
    
    /* 文件字段2: NOP_FILE_FLD2 */
    private java.lang.String _nopFileFld2;
    
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
    

    public _NopDynEntity(){
        // for debug
    }

    protected NopDynEntity newInstance(){
        NopDynEntity entity = new NopDynEntity();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopDynEntity cloneInstance() {
        NopDynEntity entity = newInstance();
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
      return "io.nop.dyn.dao.entity.NopDynEntity";
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
        
            case PROP_ID_nopObjType:
               return getNopObjType();
        
            case PROP_ID_nopName:
               return getNopName();
        
            case PROP_ID_nopDisplayName:
               return getNopDisplayName();
        
            case PROP_ID_nopSortOrder:
               return getNopSortOrder();
        
            case PROP_ID_nopFlowId:
               return getNopFlowId();
        
            case PROP_ID_nopStatus:
               return getNopStatus();
        
            case PROP_ID_nopBizState:
               return getNopBizState();
        
            case PROP_ID_nopParentId:
               return getNopParentId();
        
            case PROP_ID_nopOwnerName:
               return getNopOwnerName();
        
            case PROP_ID_nopOwnerId:
               return getNopOwnerId();
        
            case PROP_ID_nopDeptId:
               return getNopDeptId();
        
            case PROP_ID_nopStringFld1:
               return getNopStringFld1();
        
            case PROP_ID_nopDecimalFld1:
               return getNopDecimalFld1();
        
            case PROP_ID_nopIntFld1:
               return getNopIntFld1();
        
            case PROP_ID_nopLongFld1:
               return getNopLongFld1();
        
            case PROP_ID_nopDateFld1:
               return getNopDateFld1();
        
            case PROP_ID_nopTimestampFld1:
               return getNopTimestampFld1();
        
            case PROP_ID_nopFileFld1:
               return getNopFileFld1();
        
            case PROP_ID_nopStringFld2:
               return getNopStringFld2();
        
            case PROP_ID_nopDecimalFld2:
               return getNopDecimalFld2();
        
            case PROP_ID_nopIntFld2:
               return getNopIntFld2();
        
            case PROP_ID_nopLongFld2:
               return getNopLongFld2();
        
            case PROP_ID_nopDateFld2:
               return getNopDateFld2();
        
            case PROP_ID_nopTimestampFld2:
               return getNopTimestampFld2();
        
            case PROP_ID_nopFileFld2:
               return getNopFileFld2();
        
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
        
            case PROP_ID_nopObjType:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_nopObjType));
               }
               setNopObjType(typedValue);
               break;
            }
        
            case PROP_ID_nopName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_nopName));
               }
               setNopName(typedValue);
               break;
            }
        
            case PROP_ID_nopDisplayName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_nopDisplayName));
               }
               setNopDisplayName(typedValue);
               break;
            }
        
            case PROP_ID_nopSortOrder:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_nopSortOrder));
               }
               setNopSortOrder(typedValue);
               break;
            }
        
            case PROP_ID_nopFlowId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_nopFlowId));
               }
               setNopFlowId(typedValue);
               break;
            }
        
            case PROP_ID_nopStatus:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_nopStatus));
               }
               setNopStatus(typedValue);
               break;
            }
        
            case PROP_ID_nopBizState:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_nopBizState));
               }
               setNopBizState(typedValue);
               break;
            }
        
            case PROP_ID_nopParentId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_nopParentId));
               }
               setNopParentId(typedValue);
               break;
            }
        
            case PROP_ID_nopOwnerName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_nopOwnerName));
               }
               setNopOwnerName(typedValue);
               break;
            }
        
            case PROP_ID_nopOwnerId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_nopOwnerId));
               }
               setNopOwnerId(typedValue);
               break;
            }
        
            case PROP_ID_nopDeptId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_nopDeptId));
               }
               setNopDeptId(typedValue);
               break;
            }
        
            case PROP_ID_nopStringFld1:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_nopStringFld1));
               }
               setNopStringFld1(typedValue);
               break;
            }
        
            case PROP_ID_nopDecimalFld1:{
               java.math.BigDecimal typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toBigDecimal(value,
                       err-> newTypeConversionError(PROP_NAME_nopDecimalFld1));
               }
               setNopDecimalFld1(typedValue);
               break;
            }
        
            case PROP_ID_nopIntFld1:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_nopIntFld1));
               }
               setNopIntFld1(typedValue);
               break;
            }
        
            case PROP_ID_nopLongFld1:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_nopLongFld1));
               }
               setNopLongFld1(typedValue);
               break;
            }
        
            case PROP_ID_nopDateFld1:{
               java.time.LocalDate typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLocalDate(value,
                       err-> newTypeConversionError(PROP_NAME_nopDateFld1));
               }
               setNopDateFld1(typedValue);
               break;
            }
        
            case PROP_ID_nopTimestampFld1:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_nopTimestampFld1));
               }
               setNopTimestampFld1(typedValue);
               break;
            }
        
            case PROP_ID_nopFileFld1:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_nopFileFld1));
               }
               setNopFileFld1(typedValue);
               break;
            }
        
            case PROP_ID_nopStringFld2:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_nopStringFld2));
               }
               setNopStringFld2(typedValue);
               break;
            }
        
            case PROP_ID_nopDecimalFld2:{
               java.math.BigDecimal typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toBigDecimal(value,
                       err-> newTypeConversionError(PROP_NAME_nopDecimalFld2));
               }
               setNopDecimalFld2(typedValue);
               break;
            }
        
            case PROP_ID_nopIntFld2:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_nopIntFld2));
               }
               setNopIntFld2(typedValue);
               break;
            }
        
            case PROP_ID_nopLongFld2:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_nopLongFld2));
               }
               setNopLongFld2(typedValue);
               break;
            }
        
            case PROP_ID_nopDateFld2:{
               java.time.LocalDate typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLocalDate(value,
                       err-> newTypeConversionError(PROP_NAME_nopDateFld2));
               }
               setNopDateFld2(typedValue);
               break;
            }
        
            case PROP_ID_nopTimestampFld2:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_nopTimestampFld2));
               }
               setNopTimestampFld2(typedValue);
               break;
            }
        
            case PROP_ID_nopFileFld2:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_nopFileFld2));
               }
               setNopFileFld2(typedValue);
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
        
            case PROP_ID_nopObjType:{
               onInitProp(propId);
               this._nopObjType = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_nopName:{
               onInitProp(propId);
               this._nopName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_nopDisplayName:{
               onInitProp(propId);
               this._nopDisplayName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_nopSortOrder:{
               onInitProp(propId);
               this._nopSortOrder = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_nopFlowId:{
               onInitProp(propId);
               this._nopFlowId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_nopStatus:{
               onInitProp(propId);
               this._nopStatus = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_nopBizState:{
               onInitProp(propId);
               this._nopBizState = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_nopParentId:{
               onInitProp(propId);
               this._nopParentId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_nopOwnerName:{
               onInitProp(propId);
               this._nopOwnerName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_nopOwnerId:{
               onInitProp(propId);
               this._nopOwnerId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_nopDeptId:{
               onInitProp(propId);
               this._nopDeptId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_nopStringFld1:{
               onInitProp(propId);
               this._nopStringFld1 = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_nopDecimalFld1:{
               onInitProp(propId);
               this._nopDecimalFld1 = (java.math.BigDecimal)value;
               
               break;
            }
        
            case PROP_ID_nopIntFld1:{
               onInitProp(propId);
               this._nopIntFld1 = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_nopLongFld1:{
               onInitProp(propId);
               this._nopLongFld1 = (java.lang.Long)value;
               
               break;
            }
        
            case PROP_ID_nopDateFld1:{
               onInitProp(propId);
               this._nopDateFld1 = (java.time.LocalDate)value;
               
               break;
            }
        
            case PROP_ID_nopTimestampFld1:{
               onInitProp(propId);
               this._nopTimestampFld1 = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_nopFileFld1:{
               onInitProp(propId);
               this._nopFileFld1 = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_nopStringFld2:{
               onInitProp(propId);
               this._nopStringFld2 = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_nopDecimalFld2:{
               onInitProp(propId);
               this._nopDecimalFld2 = (java.math.BigDecimal)value;
               
               break;
            }
        
            case PROP_ID_nopIntFld2:{
               onInitProp(propId);
               this._nopIntFld2 = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_nopLongFld2:{
               onInitProp(propId);
               this._nopLongFld2 = (java.lang.Long)value;
               
               break;
            }
        
            case PROP_ID_nopDateFld2:{
               onInitProp(propId);
               this._nopDateFld2 = (java.time.LocalDate)value;
               
               break;
            }
        
            case PROP_ID_nopTimestampFld2:{
               onInitProp(propId);
               this._nopTimestampFld2 = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_nopFileFld2:{
               onInitProp(propId);
               this._nopFileFld2 = (java.lang.String)value;
               
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
     * 对象类型: NOP_OBJ_TYPE
     */
    public final java.lang.String getNopObjType(){
         onPropGet(PROP_ID_nopObjType);
         return _nopObjType;
    }

    /**
     * 对象类型: NOP_OBJ_TYPE
     */
    public final void setNopObjType(java.lang.String value){
        if(onPropSet(PROP_ID_nopObjType,value)){
            this._nopObjType = value;
            internalClearRefs(PROP_ID_nopObjType);
            
        }
    }
    
    /**
     * 名称: NOP_NAME
     */
    public final java.lang.String getNopName(){
         onPropGet(PROP_ID_nopName);
         return _nopName;
    }

    /**
     * 名称: NOP_NAME
     */
    public final void setNopName(java.lang.String value){
        if(onPropSet(PROP_ID_nopName,value)){
            this._nopName = value;
            internalClearRefs(PROP_ID_nopName);
            
        }
    }
    
    /**
     * 显示名称: NOP_DISPLAY_NAME
     */
    public final java.lang.String getNopDisplayName(){
         onPropGet(PROP_ID_nopDisplayName);
         return _nopDisplayName;
    }

    /**
     * 显示名称: NOP_DISPLAY_NAME
     */
    public final void setNopDisplayName(java.lang.String value){
        if(onPropSet(PROP_ID_nopDisplayName,value)){
            this._nopDisplayName = value;
            internalClearRefs(PROP_ID_nopDisplayName);
            
        }
    }
    
    /**
     * 排序: NOP_SORT_ORDER
     */
    public final java.lang.Integer getNopSortOrder(){
         onPropGet(PROP_ID_nopSortOrder);
         return _nopSortOrder;
    }

    /**
     * 排序: NOP_SORT_ORDER
     */
    public final void setNopSortOrder(java.lang.Integer value){
        if(onPropSet(PROP_ID_nopSortOrder,value)){
            this._nopSortOrder = value;
            internalClearRefs(PROP_ID_nopSortOrder);
            
        }
    }
    
    /**
     * 工作流实例ID: NOP_FLOW_ID
     */
    public final java.lang.String getNopFlowId(){
         onPropGet(PROP_ID_nopFlowId);
         return _nopFlowId;
    }

    /**
     * 工作流实例ID: NOP_FLOW_ID
     */
    public final void setNopFlowId(java.lang.String value){
        if(onPropSet(PROP_ID_nopFlowId,value)){
            this._nopFlowId = value;
            internalClearRefs(PROP_ID_nopFlowId);
            
        }
    }
    
    /**
     * 业务状态码: NOP_STATUS
     */
    public final java.lang.Integer getNopStatus(){
         onPropGet(PROP_ID_nopStatus);
         return _nopStatus;
    }

    /**
     * 业务状态码: NOP_STATUS
     */
    public final void setNopStatus(java.lang.Integer value){
        if(onPropSet(PROP_ID_nopStatus,value)){
            this._nopStatus = value;
            internalClearRefs(PROP_ID_nopStatus);
            
        }
    }
    
    /**
     * 业务状态: NOP_BIZ_STATE
     */
    public final java.lang.String getNopBizState(){
         onPropGet(PROP_ID_nopBizState);
         return _nopBizState;
    }

    /**
     * 业务状态: NOP_BIZ_STATE
     */
    public final void setNopBizState(java.lang.String value){
        if(onPropSet(PROP_ID_nopBizState,value)){
            this._nopBizState = value;
            internalClearRefs(PROP_ID_nopBizState);
            
        }
    }
    
    /**
     * 父ID: NOP_PARENT_ID
     */
    public final java.lang.String getNopParentId(){
         onPropGet(PROP_ID_nopParentId);
         return _nopParentId;
    }

    /**
     * 父ID: NOP_PARENT_ID
     */
    public final void setNopParentId(java.lang.String value){
        if(onPropSet(PROP_ID_nopParentId,value)){
            this._nopParentId = value;
            internalClearRefs(PROP_ID_nopParentId);
            
        }
    }
    
    /**
     * 拥有者姓名: NOP_OWNER_NAME
     */
    public final java.lang.String getNopOwnerName(){
         onPropGet(PROP_ID_nopOwnerName);
         return _nopOwnerName;
    }

    /**
     * 拥有者姓名: NOP_OWNER_NAME
     */
    public final void setNopOwnerName(java.lang.String value){
        if(onPropSet(PROP_ID_nopOwnerName,value)){
            this._nopOwnerName = value;
            internalClearRefs(PROP_ID_nopOwnerName);
            
        }
    }
    
    /**
     * 拥有者ID: NOP_OWNER_ID
     */
    public final java.lang.String getNopOwnerId(){
         onPropGet(PROP_ID_nopOwnerId);
         return _nopOwnerId;
    }

    /**
     * 拥有者ID: NOP_OWNER_ID
     */
    public final void setNopOwnerId(java.lang.String value){
        if(onPropSet(PROP_ID_nopOwnerId,value)){
            this._nopOwnerId = value;
            internalClearRefs(PROP_ID_nopOwnerId);
            
        }
    }
    
    /**
     * 部门ID: NOP_DEPT_ID
     */
    public final java.lang.String getNopDeptId(){
         onPropGet(PROP_ID_nopDeptId);
         return _nopDeptId;
    }

    /**
     * 部门ID: NOP_DEPT_ID
     */
    public final void setNopDeptId(java.lang.String value){
        if(onPropSet(PROP_ID_nopDeptId,value)){
            this._nopDeptId = value;
            internalClearRefs(PROP_ID_nopDeptId);
            
        }
    }
    
    /**
     * 字符串字段1: NOP_STRING_FLD1
     */
    public final java.lang.String getNopStringFld1(){
         onPropGet(PROP_ID_nopStringFld1);
         return _nopStringFld1;
    }

    /**
     * 字符串字段1: NOP_STRING_FLD1
     */
    public final void setNopStringFld1(java.lang.String value){
        if(onPropSet(PROP_ID_nopStringFld1,value)){
            this._nopStringFld1 = value;
            internalClearRefs(PROP_ID_nopStringFld1);
            
        }
    }
    
    /**
     * 浮点型字段1: NOP_DECIMAL_FLD1
     */
    public final java.math.BigDecimal getNopDecimalFld1(){
         onPropGet(PROP_ID_nopDecimalFld1);
         return _nopDecimalFld1;
    }

    /**
     * 浮点型字段1: NOP_DECIMAL_FLD1
     */
    public final void setNopDecimalFld1(java.math.BigDecimal value){
        if(onPropSet(PROP_ID_nopDecimalFld1,value)){
            this._nopDecimalFld1 = value;
            internalClearRefs(PROP_ID_nopDecimalFld1);
            
        }
    }
    
    /**
     * 整数型字段1: NOP_INT_FLD1
     */
    public final java.lang.Integer getNopIntFld1(){
         onPropGet(PROP_ID_nopIntFld1);
         return _nopIntFld1;
    }

    /**
     * 整数型字段1: NOP_INT_FLD1
     */
    public final void setNopIntFld1(java.lang.Integer value){
        if(onPropSet(PROP_ID_nopIntFld1,value)){
            this._nopIntFld1 = value;
            internalClearRefs(PROP_ID_nopIntFld1);
            
        }
    }
    
    /**
     * 长整型字段1: NOP_LONG_FLD1
     */
    public final java.lang.Long getNopLongFld1(){
         onPropGet(PROP_ID_nopLongFld1);
         return _nopLongFld1;
    }

    /**
     * 长整型字段1: NOP_LONG_FLD1
     */
    public final void setNopLongFld1(java.lang.Long value){
        if(onPropSet(PROP_ID_nopLongFld1,value)){
            this._nopLongFld1 = value;
            internalClearRefs(PROP_ID_nopLongFld1);
            
        }
    }
    
    /**
     * 日期字段1: NOP_DATE_FLD1
     */
    public final java.time.LocalDate getNopDateFld1(){
         onPropGet(PROP_ID_nopDateFld1);
         return _nopDateFld1;
    }

    /**
     * 日期字段1: NOP_DATE_FLD1
     */
    public final void setNopDateFld1(java.time.LocalDate value){
        if(onPropSet(PROP_ID_nopDateFld1,value)){
            this._nopDateFld1 = value;
            internalClearRefs(PROP_ID_nopDateFld1);
            
        }
    }
    
    /**
     * 时间戳字段1: NOP_TIMESTAMP_FLD1
     */
    public final java.sql.Timestamp getNopTimestampFld1(){
         onPropGet(PROP_ID_nopTimestampFld1);
         return _nopTimestampFld1;
    }

    /**
     * 时间戳字段1: NOP_TIMESTAMP_FLD1
     */
    public final void setNopTimestampFld1(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_nopTimestampFld1,value)){
            this._nopTimestampFld1 = value;
            internalClearRefs(PROP_ID_nopTimestampFld1);
            
        }
    }
    
    /**
     * 文件字段1: NOP_FILE_FLD1
     */
    public final java.lang.String getNopFileFld1(){
         onPropGet(PROP_ID_nopFileFld1);
         return _nopFileFld1;
    }

    /**
     * 文件字段1: NOP_FILE_FLD1
     */
    public final void setNopFileFld1(java.lang.String value){
        if(onPropSet(PROP_ID_nopFileFld1,value)){
            this._nopFileFld1 = value;
            internalClearRefs(PROP_ID_nopFileFld1);
            
        }
    }
    
    /**
     * 字符串字段2: NOP_STRING_FLD2
     */
    public final java.lang.String getNopStringFld2(){
         onPropGet(PROP_ID_nopStringFld2);
         return _nopStringFld2;
    }

    /**
     * 字符串字段2: NOP_STRING_FLD2
     */
    public final void setNopStringFld2(java.lang.String value){
        if(onPropSet(PROP_ID_nopStringFld2,value)){
            this._nopStringFld2 = value;
            internalClearRefs(PROP_ID_nopStringFld2);
            
        }
    }
    
    /**
     * 浮点型字段2: NOP_DECIMAL_FLD2
     */
    public final java.math.BigDecimal getNopDecimalFld2(){
         onPropGet(PROP_ID_nopDecimalFld2);
         return _nopDecimalFld2;
    }

    /**
     * 浮点型字段2: NOP_DECIMAL_FLD2
     */
    public final void setNopDecimalFld2(java.math.BigDecimal value){
        if(onPropSet(PROP_ID_nopDecimalFld2,value)){
            this._nopDecimalFld2 = value;
            internalClearRefs(PROP_ID_nopDecimalFld2);
            
        }
    }
    
    /**
     * 整数型字段2: NOP_INT_FLD2
     */
    public final java.lang.Integer getNopIntFld2(){
         onPropGet(PROP_ID_nopIntFld2);
         return _nopIntFld2;
    }

    /**
     * 整数型字段2: NOP_INT_FLD2
     */
    public final void setNopIntFld2(java.lang.Integer value){
        if(onPropSet(PROP_ID_nopIntFld2,value)){
            this._nopIntFld2 = value;
            internalClearRefs(PROP_ID_nopIntFld2);
            
        }
    }
    
    /**
     * 长整型字段2: NOP_LONG_FLD2
     */
    public final java.lang.Long getNopLongFld2(){
         onPropGet(PROP_ID_nopLongFld2);
         return _nopLongFld2;
    }

    /**
     * 长整型字段2: NOP_LONG_FLD2
     */
    public final void setNopLongFld2(java.lang.Long value){
        if(onPropSet(PROP_ID_nopLongFld2,value)){
            this._nopLongFld2 = value;
            internalClearRefs(PROP_ID_nopLongFld2);
            
        }
    }
    
    /**
     * 日期字段2: NOP_DATE_FLD2
     */
    public final java.time.LocalDate getNopDateFld2(){
         onPropGet(PROP_ID_nopDateFld2);
         return _nopDateFld2;
    }

    /**
     * 日期字段2: NOP_DATE_FLD2
     */
    public final void setNopDateFld2(java.time.LocalDate value){
        if(onPropSet(PROP_ID_nopDateFld2,value)){
            this._nopDateFld2 = value;
            internalClearRefs(PROP_ID_nopDateFld2);
            
        }
    }
    
    /**
     * 时间戳字段2: NOP_TIMESTAMP_FLD2
     */
    public final java.sql.Timestamp getNopTimestampFld2(){
         onPropGet(PROP_ID_nopTimestampFld2);
         return _nopTimestampFld2;
    }

    /**
     * 时间戳字段2: NOP_TIMESTAMP_FLD2
     */
    public final void setNopTimestampFld2(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_nopTimestampFld2,value)){
            this._nopTimestampFld2 = value;
            internalClearRefs(PROP_ID_nopTimestampFld2);
            
        }
    }
    
    /**
     * 文件字段2: NOP_FILE_FLD2
     */
    public final java.lang.String getNopFileFld2(){
         onPropGet(PROP_ID_nopFileFld2);
         return _nopFileFld2;
    }

    /**
     * 文件字段2: NOP_FILE_FLD2
     */
    public final void setNopFileFld2(java.lang.String value){
        if(onPropSet(PROP_ID_nopFileFld2,value)){
            this._nopFileFld2 = value;
            internalClearRefs(PROP_ID_nopFileFld2);
            
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
     * 父对象
     */
    public final io.nop.dyn.dao.entity.NopDynEntity getNopParent(){
       return (io.nop.dyn.dao.entity.NopDynEntity)internalGetRefEntity(PROP_NAME_nopParent);
    }

    public final void setNopParent(io.nop.dyn.dao.entity.NopDynEntity refEntity){
   
           if(refEntity == null){
           
                   this.setNopParentId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_nopParent, refEntity,()->{
           
                           this.setNopParentId(refEntity.getSid());
                       
           });
           }
       
    }
       
    private final OrmEntitySet<io.nop.dyn.dao.entity.NopDynEntity> _nopChildren = new OrmEntitySet<>(this, PROP_NAME_nopChildren,
        io.nop.dyn.dao.entity.NopDynEntity.PROP_NAME_nopParent, null,io.nop.dyn.dao.entity.NopDynEntity.class);

    /**
     * 子对象。 refPropName: nopParent, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<io.nop.dyn.dao.entity.NopDynEntity> getNopChildren(){
       return _nopChildren;
    }
       
    private final OrmEntitySet<io.nop.orm.support.DynamicOrmKeyValueTable> _extFields = new OrmEntitySet<>(this, PROP_NAME_extFields,
        null, "fieldName",io.nop.orm.support.DynamicOrmKeyValueTable.class,"io.nop.dyn.dao.entity.NopDynEntityExt");

    /**
     * 。 refPropName: , keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<io.nop.orm.support.DynamicOrmKeyValueTable> getExtFields(){
       return _extFields;
    }
       
   private io.nop.orm.component.OrmFileComponent _nopFileFld1Component;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_nopFileFld1Component = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_nopFileFld1Component.put(io.nop.orm.component.OrmFileComponent.PROP_NAME_filePath,PROP_ID_nopFileFld1);
      
   }

   public final io.nop.orm.component.OrmFileComponent getNopFileFld1Component(){
      if(_nopFileFld1Component == null){
          _nopFileFld1Component = new io.nop.orm.component.OrmFileComponent();
          _nopFileFld1Component.bindToEntity(this, COMPONENT_PROP_ID_MAP_nopFileFld1Component);
      }
      return _nopFileFld1Component;
   }

   private io.nop.orm.component.OrmFileComponent _nopFileFld2Component;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_nopFileFld2Component = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_nopFileFld2Component.put(io.nop.orm.component.OrmFileComponent.PROP_NAME_filePath,PROP_ID_nopFileFld2);
      
   }

   public final io.nop.orm.component.OrmFileComponent getNopFileFld2Component(){
      if(_nopFileFld2Component == null){
          _nopFileFld2Component = new io.nop.orm.component.OrmFileComponent();
          _nopFileFld2Component.bindToEntity(this, COMPONENT_PROP_ID_MAP_nopFileFld2Component);
      }
      return _nopFileFld2Component;
   }

}
// resume CPD analysis - CPD-ON
