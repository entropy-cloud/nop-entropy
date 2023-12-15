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

import io.nop.wf.dao.entity.NopWfDynEntity;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  动态实体: nop_wf_dyn_entity
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101"})
public class _NopWfDynEntity extends DynamicOrmEntity{
    
    /* 主键: SID VARCHAR */
    public static final String PROP_NAME_sid = "sid";
    public static final int PROP_ID_sid = 1;
    
    /* 对象类型: OBJ_TYPE VARCHAR */
    public static final String PROP_NAME_objType = "objType";
    public static final int PROP_ID_objType = 2;
    
    /* 名称: NAME VARCHAR */
    public static final String PROP_NAME_name = "name";
    public static final int PROP_ID_name = 3;
    
    /* 显示名称: DISPLAY_NAME VARCHAR */
    public static final String PROP_NAME_displayName = "displayName";
    public static final int PROP_ID_displayName = 4;
    
    /* 排序: SORT_ORDER INTEGER */
    public static final String PROP_NAME_sortOrder = "sortOrder";
    public static final int PROP_ID_sortOrder = 5;
    
    /* 工作流实例ID: NOP_FLOW_ID VARCHAR */
    public static final String PROP_NAME_nopFlowId = "nopFlowId";
    public static final int PROP_ID_nopFlowId = 6;
    
    /* 业务状态码: BIZ_STATUS INTEGER */
    public static final String PROP_NAME_bizStatus = "bizStatus";
    public static final int PROP_ID_bizStatus = 7;
    
    /* 业务状态: BIZ_STATE VARCHAR */
    public static final String PROP_NAME_bizState = "bizState";
    public static final int PROP_ID_bizState = 8;
    
    /* 父ID: PARENT_ID VARCHAR */
    public static final String PROP_NAME_parentId = "parentId";
    public static final int PROP_ID_parentId = 9;
    
    /* 拥有者姓名: OWNER_NAME VARCHAR */
    public static final String PROP_NAME_ownerName = "ownerName";
    public static final int PROP_ID_ownerName = 10;
    
    /* 拥有者ID: OWNER_ID VARCHAR */
    public static final String PROP_NAME_ownerId = "ownerId";
    public static final int PROP_ID_ownerId = 11;
    
    /* 部门ID: DEPT_ID VARCHAR */
    public static final String PROP_NAME_deptId = "deptId";
    public static final int PROP_ID_deptId = 12;
    
    /* 字符串字段1: STRING_FLD1 VARCHAR */
    public static final String PROP_NAME_stringFld1 = "stringFld1";
    public static final int PROP_ID_stringFld1 = 13;
    
    /* 浮点型字段1: DECIMAL_FLD1 DECIMAL */
    public static final String PROP_NAME_decimalFld1 = "decimalFld1";
    public static final int PROP_ID_decimalFld1 = 14;
    
    /* 整数型字段1: INT_FLD1 INTEGER */
    public static final String PROP_NAME_intFld1 = "intFld1";
    public static final int PROP_ID_intFld1 = 15;
    
    /* 长整型字段1: LONG_FLD1 BIGINT */
    public static final String PROP_NAME_longFld1 = "longFld1";
    public static final int PROP_ID_longFld1 = 16;
    
    /* 日期字段1: DATE_FLD1 DATE */
    public static final String PROP_NAME_dateFld1 = "dateFld1";
    public static final int PROP_ID_dateFld1 = 17;
    
    /* 时间戳字段1: TIMESTAMP_FLD1 TIMESTAMP */
    public static final String PROP_NAME_timestampFld1 = "timestampFld1";
    public static final int PROP_ID_timestampFld1 = 18;
    
    /* 文件字段1: FILE_FLD1 VARCHAR */
    public static final String PROP_NAME_fileFld1 = "fileFld1";
    public static final int PROP_ID_fileFld1 = 19;
    
    /* 字符串字段2: STRING_FLD2 VARCHAR */
    public static final String PROP_NAME_stringFld2 = "stringFld2";
    public static final int PROP_ID_stringFld2 = 20;
    
    /* 浮点型字段2: DECIMAL_FLD2 DECIMAL */
    public static final String PROP_NAME_decimalFld2 = "decimalFld2";
    public static final int PROP_ID_decimalFld2 = 21;
    
    /* 整数型字段2: INT_FLD2 INTEGER */
    public static final String PROP_NAME_intFld2 = "intFld2";
    public static final int PROP_ID_intFld2 = 22;
    
    /* 长整型字段2: LONG_FLD2 BIGINT */
    public static final String PROP_NAME_longFld2 = "longFld2";
    public static final int PROP_ID_longFld2 = 23;
    
    /* 日期字段2: DATE_FLD2 DATE */
    public static final String PROP_NAME_dateFld2 = "dateFld2";
    public static final int PROP_ID_dateFld2 = 24;
    
    /* 时间戳字段2: TIMESTAMP_FLD2 TIMESTAMP */
    public static final String PROP_NAME_timestampFld2 = "timestampFld2";
    public static final int PROP_ID_timestampFld2 = 25;
    
    /* 文件字段2: FILE_FLD2 VARCHAR */
    public static final String PROP_NAME_fileFld2 = "fileFld2";
    public static final int PROP_ID_fileFld2 = 26;
    
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

    
    /* relation: 工作流实例 */
    public static final String PROP_NAME_wfInstance = "wfInstance";
    
    /* relation: 父对象 */
    public static final String PROP_NAME_parent = "parent";
    
    /* relation: 子对象 */
    public static final String PROP_NAME_children = "children";
    
    /* relation:  */
    public static final String PROP_NAME_extFields = "extFields";
    
    /* component:  */
    public static final String PROP_NAME_fileFld1Component = "fileFld1Component";
    
    /* component:  */
    public static final String PROP_NAME_fileFld2Component = "fileFld2Component";
    

    public static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_sid);
    public static final int[] PK_PROP_IDS = new int[]{PROP_ID_sid};

    private static final String[] PROP_ID_TO_NAME = new String[33];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_sid] = PROP_NAME_sid;
          PROP_NAME_TO_ID.put(PROP_NAME_sid, PROP_ID_sid);
      
          PROP_ID_TO_NAME[PROP_ID_objType] = PROP_NAME_objType;
          PROP_NAME_TO_ID.put(PROP_NAME_objType, PROP_ID_objType);
      
          PROP_ID_TO_NAME[PROP_ID_name] = PROP_NAME_name;
          PROP_NAME_TO_ID.put(PROP_NAME_name, PROP_ID_name);
      
          PROP_ID_TO_NAME[PROP_ID_displayName] = PROP_NAME_displayName;
          PROP_NAME_TO_ID.put(PROP_NAME_displayName, PROP_ID_displayName);
      
          PROP_ID_TO_NAME[PROP_ID_sortOrder] = PROP_NAME_sortOrder;
          PROP_NAME_TO_ID.put(PROP_NAME_sortOrder, PROP_ID_sortOrder);
      
          PROP_ID_TO_NAME[PROP_ID_nopFlowId] = PROP_NAME_nopFlowId;
          PROP_NAME_TO_ID.put(PROP_NAME_nopFlowId, PROP_ID_nopFlowId);
      
          PROP_ID_TO_NAME[PROP_ID_bizStatus] = PROP_NAME_bizStatus;
          PROP_NAME_TO_ID.put(PROP_NAME_bizStatus, PROP_ID_bizStatus);
      
          PROP_ID_TO_NAME[PROP_ID_bizState] = PROP_NAME_bizState;
          PROP_NAME_TO_ID.put(PROP_NAME_bizState, PROP_ID_bizState);
      
          PROP_ID_TO_NAME[PROP_ID_parentId] = PROP_NAME_parentId;
          PROP_NAME_TO_ID.put(PROP_NAME_parentId, PROP_ID_parentId);
      
          PROP_ID_TO_NAME[PROP_ID_ownerName] = PROP_NAME_ownerName;
          PROP_NAME_TO_ID.put(PROP_NAME_ownerName, PROP_ID_ownerName);
      
          PROP_ID_TO_NAME[PROP_ID_ownerId] = PROP_NAME_ownerId;
          PROP_NAME_TO_ID.put(PROP_NAME_ownerId, PROP_ID_ownerId);
      
          PROP_ID_TO_NAME[PROP_ID_deptId] = PROP_NAME_deptId;
          PROP_NAME_TO_ID.put(PROP_NAME_deptId, PROP_ID_deptId);
      
          PROP_ID_TO_NAME[PROP_ID_stringFld1] = PROP_NAME_stringFld1;
          PROP_NAME_TO_ID.put(PROP_NAME_stringFld1, PROP_ID_stringFld1);
      
          PROP_ID_TO_NAME[PROP_ID_decimalFld1] = PROP_NAME_decimalFld1;
          PROP_NAME_TO_ID.put(PROP_NAME_decimalFld1, PROP_ID_decimalFld1);
      
          PROP_ID_TO_NAME[PROP_ID_intFld1] = PROP_NAME_intFld1;
          PROP_NAME_TO_ID.put(PROP_NAME_intFld1, PROP_ID_intFld1);
      
          PROP_ID_TO_NAME[PROP_ID_longFld1] = PROP_NAME_longFld1;
          PROP_NAME_TO_ID.put(PROP_NAME_longFld1, PROP_ID_longFld1);
      
          PROP_ID_TO_NAME[PROP_ID_dateFld1] = PROP_NAME_dateFld1;
          PROP_NAME_TO_ID.put(PROP_NAME_dateFld1, PROP_ID_dateFld1);
      
          PROP_ID_TO_NAME[PROP_ID_timestampFld1] = PROP_NAME_timestampFld1;
          PROP_NAME_TO_ID.put(PROP_NAME_timestampFld1, PROP_ID_timestampFld1);
      
          PROP_ID_TO_NAME[PROP_ID_fileFld1] = PROP_NAME_fileFld1;
          PROP_NAME_TO_ID.put(PROP_NAME_fileFld1, PROP_ID_fileFld1);
      
          PROP_ID_TO_NAME[PROP_ID_stringFld2] = PROP_NAME_stringFld2;
          PROP_NAME_TO_ID.put(PROP_NAME_stringFld2, PROP_ID_stringFld2);
      
          PROP_ID_TO_NAME[PROP_ID_decimalFld2] = PROP_NAME_decimalFld2;
          PROP_NAME_TO_ID.put(PROP_NAME_decimalFld2, PROP_ID_decimalFld2);
      
          PROP_ID_TO_NAME[PROP_ID_intFld2] = PROP_NAME_intFld2;
          PROP_NAME_TO_ID.put(PROP_NAME_intFld2, PROP_ID_intFld2);
      
          PROP_ID_TO_NAME[PROP_ID_longFld2] = PROP_NAME_longFld2;
          PROP_NAME_TO_ID.put(PROP_NAME_longFld2, PROP_ID_longFld2);
      
          PROP_ID_TO_NAME[PROP_ID_dateFld2] = PROP_NAME_dateFld2;
          PROP_NAME_TO_ID.put(PROP_NAME_dateFld2, PROP_ID_dateFld2);
      
          PROP_ID_TO_NAME[PROP_ID_timestampFld2] = PROP_NAME_timestampFld2;
          PROP_NAME_TO_ID.put(PROP_NAME_timestampFld2, PROP_ID_timestampFld2);
      
          PROP_ID_TO_NAME[PROP_ID_fileFld2] = PROP_NAME_fileFld2;
          PROP_NAME_TO_ID.put(PROP_NAME_fileFld2, PROP_ID_fileFld2);
      
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
    
    /* 对象类型: OBJ_TYPE */
    private java.lang.String _objType;
    
    /* 名称: NAME */
    private java.lang.String _name;
    
    /* 显示名称: DISPLAY_NAME */
    private java.lang.String _displayName;
    
    /* 排序: SORT_ORDER */
    private java.lang.Integer _sortOrder;
    
    /* 工作流实例ID: NOP_FLOW_ID */
    private java.lang.String _nopFlowId;
    
    /* 业务状态码: BIZ_STATUS */
    private java.lang.Integer _bizStatus;
    
    /* 业务状态: BIZ_STATE */
    private java.lang.String _bizState;
    
    /* 父ID: PARENT_ID */
    private java.lang.String _parentId;
    
    /* 拥有者姓名: OWNER_NAME */
    private java.lang.String _ownerName;
    
    /* 拥有者ID: OWNER_ID */
    private java.lang.String _ownerId;
    
    /* 部门ID: DEPT_ID */
    private java.lang.String _deptId;
    
    /* 字符串字段1: STRING_FLD1 */
    private java.lang.String _stringFld1;
    
    /* 浮点型字段1: DECIMAL_FLD1 */
    private java.math.BigDecimal _decimalFld1;
    
    /* 整数型字段1: INT_FLD1 */
    private java.lang.Integer _intFld1;
    
    /* 长整型字段1: LONG_FLD1 */
    private java.lang.Long _longFld1;
    
    /* 日期字段1: DATE_FLD1 */
    private java.time.LocalDate _dateFld1;
    
    /* 时间戳字段1: TIMESTAMP_FLD1 */
    private java.sql.Timestamp _timestampFld1;
    
    /* 文件字段1: FILE_FLD1 */
    private java.lang.String _fileFld1;
    
    /* 字符串字段2: STRING_FLD2 */
    private java.lang.String _stringFld2;
    
    /* 浮点型字段2: DECIMAL_FLD2 */
    private java.math.BigDecimal _decimalFld2;
    
    /* 整数型字段2: INT_FLD2 */
    private java.lang.Integer _intFld2;
    
    /* 长整型字段2: LONG_FLD2 */
    private java.lang.Long _longFld2;
    
    /* 日期字段2: DATE_FLD2 */
    private java.time.LocalDate _dateFld2;
    
    /* 时间戳字段2: TIMESTAMP_FLD2 */
    private java.sql.Timestamp _timestampFld2;
    
    /* 文件字段2: FILE_FLD2 */
    private java.lang.String _fileFld2;
    
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
    

    public _NopWfDynEntity(){
    }

    protected NopWfDynEntity newInstance(){
       return new NopWfDynEntity();
    }

    @Override
    public NopWfDynEntity cloneInstance() {
        NopWfDynEntity entity = newInstance();
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
      return "io.nop.wf.dao.entity.NopWfDynEntity";
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
        
            case PROP_ID_objType:
               return getObjType();
        
            case PROP_ID_name:
               return getName();
        
            case PROP_ID_displayName:
               return getDisplayName();
        
            case PROP_ID_sortOrder:
               return getSortOrder();
        
            case PROP_ID_nopFlowId:
               return getNopFlowId();
        
            case PROP_ID_bizStatus:
               return getBizStatus();
        
            case PROP_ID_bizState:
               return getBizState();
        
            case PROP_ID_parentId:
               return getParentId();
        
            case PROP_ID_ownerName:
               return getOwnerName();
        
            case PROP_ID_ownerId:
               return getOwnerId();
        
            case PROP_ID_deptId:
               return getDeptId();
        
            case PROP_ID_stringFld1:
               return getStringFld1();
        
            case PROP_ID_decimalFld1:
               return getDecimalFld1();
        
            case PROP_ID_intFld1:
               return getIntFld1();
        
            case PROP_ID_longFld1:
               return getLongFld1();
        
            case PROP_ID_dateFld1:
               return getDateFld1();
        
            case PROP_ID_timestampFld1:
               return getTimestampFld1();
        
            case PROP_ID_fileFld1:
               return getFileFld1();
        
            case PROP_ID_stringFld2:
               return getStringFld2();
        
            case PROP_ID_decimalFld2:
               return getDecimalFld2();
        
            case PROP_ID_intFld2:
               return getIntFld2();
        
            case PROP_ID_longFld2:
               return getLongFld2();
        
            case PROP_ID_dateFld2:
               return getDateFld2();
        
            case PROP_ID_timestampFld2:
               return getTimestampFld2();
        
            case PROP_ID_fileFld2:
               return getFileFld2();
        
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
        
            case PROP_ID_objType:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_objType));
               }
               setObjType(typedValue);
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
        
            case PROP_ID_sortOrder:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_sortOrder));
               }
               setSortOrder(typedValue);
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
        
            case PROP_ID_bizStatus:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_bizStatus));
               }
               setBizStatus(typedValue);
               break;
            }
        
            case PROP_ID_bizState:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_bizState));
               }
               setBizState(typedValue);
               break;
            }
        
            case PROP_ID_parentId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_parentId));
               }
               setParentId(typedValue);
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
        
            case PROP_ID_ownerId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_ownerId));
               }
               setOwnerId(typedValue);
               break;
            }
        
            case PROP_ID_deptId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_deptId));
               }
               setDeptId(typedValue);
               break;
            }
        
            case PROP_ID_stringFld1:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_stringFld1));
               }
               setStringFld1(typedValue);
               break;
            }
        
            case PROP_ID_decimalFld1:{
               java.math.BigDecimal typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toBigDecimal(value,
                       err-> newTypeConversionError(PROP_NAME_decimalFld1));
               }
               setDecimalFld1(typedValue);
               break;
            }
        
            case PROP_ID_intFld1:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_intFld1));
               }
               setIntFld1(typedValue);
               break;
            }
        
            case PROP_ID_longFld1:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_longFld1));
               }
               setLongFld1(typedValue);
               break;
            }
        
            case PROP_ID_dateFld1:{
               java.time.LocalDate typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLocalDate(value,
                       err-> newTypeConversionError(PROP_NAME_dateFld1));
               }
               setDateFld1(typedValue);
               break;
            }
        
            case PROP_ID_timestampFld1:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_timestampFld1));
               }
               setTimestampFld1(typedValue);
               break;
            }
        
            case PROP_ID_fileFld1:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_fileFld1));
               }
               setFileFld1(typedValue);
               break;
            }
        
            case PROP_ID_stringFld2:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_stringFld2));
               }
               setStringFld2(typedValue);
               break;
            }
        
            case PROP_ID_decimalFld2:{
               java.math.BigDecimal typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toBigDecimal(value,
                       err-> newTypeConversionError(PROP_NAME_decimalFld2));
               }
               setDecimalFld2(typedValue);
               break;
            }
        
            case PROP_ID_intFld2:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_intFld2));
               }
               setIntFld2(typedValue);
               break;
            }
        
            case PROP_ID_longFld2:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_longFld2));
               }
               setLongFld2(typedValue);
               break;
            }
        
            case PROP_ID_dateFld2:{
               java.time.LocalDate typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLocalDate(value,
                       err-> newTypeConversionError(PROP_NAME_dateFld2));
               }
               setDateFld2(typedValue);
               break;
            }
        
            case PROP_ID_timestampFld2:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_timestampFld2));
               }
               setTimestampFld2(typedValue);
               break;
            }
        
            case PROP_ID_fileFld2:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_fileFld2));
               }
               setFileFld2(typedValue);
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
        
            case PROP_ID_objType:{
               onInitProp(propId);
               this._objType = (java.lang.String)value;
               
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
        
            case PROP_ID_sortOrder:{
               onInitProp(propId);
               this._sortOrder = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_nopFlowId:{
               onInitProp(propId);
               this._nopFlowId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_bizStatus:{
               onInitProp(propId);
               this._bizStatus = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_bizState:{
               onInitProp(propId);
               this._bizState = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_parentId:{
               onInitProp(propId);
               this._parentId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_ownerName:{
               onInitProp(propId);
               this._ownerName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_ownerId:{
               onInitProp(propId);
               this._ownerId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_deptId:{
               onInitProp(propId);
               this._deptId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_stringFld1:{
               onInitProp(propId);
               this._stringFld1 = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_decimalFld1:{
               onInitProp(propId);
               this._decimalFld1 = (java.math.BigDecimal)value;
               
               break;
            }
        
            case PROP_ID_intFld1:{
               onInitProp(propId);
               this._intFld1 = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_longFld1:{
               onInitProp(propId);
               this._longFld1 = (java.lang.Long)value;
               
               break;
            }
        
            case PROP_ID_dateFld1:{
               onInitProp(propId);
               this._dateFld1 = (java.time.LocalDate)value;
               
               break;
            }
        
            case PROP_ID_timestampFld1:{
               onInitProp(propId);
               this._timestampFld1 = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_fileFld1:{
               onInitProp(propId);
               this._fileFld1 = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_stringFld2:{
               onInitProp(propId);
               this._stringFld2 = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_decimalFld2:{
               onInitProp(propId);
               this._decimalFld2 = (java.math.BigDecimal)value;
               
               break;
            }
        
            case PROP_ID_intFld2:{
               onInitProp(propId);
               this._intFld2 = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_longFld2:{
               onInitProp(propId);
               this._longFld2 = (java.lang.Long)value;
               
               break;
            }
        
            case PROP_ID_dateFld2:{
               onInitProp(propId);
               this._dateFld2 = (java.time.LocalDate)value;
               
               break;
            }
        
            case PROP_ID_timestampFld2:{
               onInitProp(propId);
               this._timestampFld2 = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_fileFld2:{
               onInitProp(propId);
               this._fileFld2 = (java.lang.String)value;
               
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
     * 对象类型: OBJ_TYPE
     */
    public java.lang.String getObjType(){
         onPropGet(PROP_ID_objType);
         return _objType;
    }

    /**
     * 对象类型: OBJ_TYPE
     */
    public void setObjType(java.lang.String value){
        if(onPropSet(PROP_ID_objType,value)){
            this._objType = value;
            internalClearRefs(PROP_ID_objType);
            
        }
    }
    
    /**
     * 名称: NAME
     */
    public java.lang.String getName(){
         onPropGet(PROP_ID_name);
         return _name;
    }

    /**
     * 名称: NAME
     */
    public void setName(java.lang.String value){
        if(onPropSet(PROP_ID_name,value)){
            this._name = value;
            internalClearRefs(PROP_ID_name);
            
        }
    }
    
    /**
     * 显示名称: DISPLAY_NAME
     */
    public java.lang.String getDisplayName(){
         onPropGet(PROP_ID_displayName);
         return _displayName;
    }

    /**
     * 显示名称: DISPLAY_NAME
     */
    public void setDisplayName(java.lang.String value){
        if(onPropSet(PROP_ID_displayName,value)){
            this._displayName = value;
            internalClearRefs(PROP_ID_displayName);
            
        }
    }
    
    /**
     * 排序: SORT_ORDER
     */
    public java.lang.Integer getSortOrder(){
         onPropGet(PROP_ID_sortOrder);
         return _sortOrder;
    }

    /**
     * 排序: SORT_ORDER
     */
    public void setSortOrder(java.lang.Integer value){
        if(onPropSet(PROP_ID_sortOrder,value)){
            this._sortOrder = value;
            internalClearRefs(PROP_ID_sortOrder);
            
        }
    }
    
    /**
     * 工作流实例ID: NOP_FLOW_ID
     */
    public java.lang.String getNopFlowId(){
         onPropGet(PROP_ID_nopFlowId);
         return _nopFlowId;
    }

    /**
     * 工作流实例ID: NOP_FLOW_ID
     */
    public void setNopFlowId(java.lang.String value){
        if(onPropSet(PROP_ID_nopFlowId,value)){
            this._nopFlowId = value;
            internalClearRefs(PROP_ID_nopFlowId);
            
        }
    }
    
    /**
     * 业务状态码: BIZ_STATUS
     */
    public java.lang.Integer getBizStatus(){
         onPropGet(PROP_ID_bizStatus);
         return _bizStatus;
    }

    /**
     * 业务状态码: BIZ_STATUS
     */
    public void setBizStatus(java.lang.Integer value){
        if(onPropSet(PROP_ID_bizStatus,value)){
            this._bizStatus = value;
            internalClearRefs(PROP_ID_bizStatus);
            
        }
    }
    
    /**
     * 业务状态: BIZ_STATE
     */
    public java.lang.String getBizState(){
         onPropGet(PROP_ID_bizState);
         return _bizState;
    }

    /**
     * 业务状态: BIZ_STATE
     */
    public void setBizState(java.lang.String value){
        if(onPropSet(PROP_ID_bizState,value)){
            this._bizState = value;
            internalClearRefs(PROP_ID_bizState);
            
        }
    }
    
    /**
     * 父ID: PARENT_ID
     */
    public java.lang.String getParentId(){
         onPropGet(PROP_ID_parentId);
         return _parentId;
    }

    /**
     * 父ID: PARENT_ID
     */
    public void setParentId(java.lang.String value){
        if(onPropSet(PROP_ID_parentId,value)){
            this._parentId = value;
            internalClearRefs(PROP_ID_parentId);
            
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
     * 部门ID: DEPT_ID
     */
    public java.lang.String getDeptId(){
         onPropGet(PROP_ID_deptId);
         return _deptId;
    }

    /**
     * 部门ID: DEPT_ID
     */
    public void setDeptId(java.lang.String value){
        if(onPropSet(PROP_ID_deptId,value)){
            this._deptId = value;
            internalClearRefs(PROP_ID_deptId);
            
        }
    }
    
    /**
     * 字符串字段1: STRING_FLD1
     */
    public java.lang.String getStringFld1(){
         onPropGet(PROP_ID_stringFld1);
         return _stringFld1;
    }

    /**
     * 字符串字段1: STRING_FLD1
     */
    public void setStringFld1(java.lang.String value){
        if(onPropSet(PROP_ID_stringFld1,value)){
            this._stringFld1 = value;
            internalClearRefs(PROP_ID_stringFld1);
            
        }
    }
    
    /**
     * 浮点型字段1: DECIMAL_FLD1
     */
    public java.math.BigDecimal getDecimalFld1(){
         onPropGet(PROP_ID_decimalFld1);
         return _decimalFld1;
    }

    /**
     * 浮点型字段1: DECIMAL_FLD1
     */
    public void setDecimalFld1(java.math.BigDecimal value){
        if(onPropSet(PROP_ID_decimalFld1,value)){
            this._decimalFld1 = value;
            internalClearRefs(PROP_ID_decimalFld1);
            
        }
    }
    
    /**
     * 整数型字段1: INT_FLD1
     */
    public java.lang.Integer getIntFld1(){
         onPropGet(PROP_ID_intFld1);
         return _intFld1;
    }

    /**
     * 整数型字段1: INT_FLD1
     */
    public void setIntFld1(java.lang.Integer value){
        if(onPropSet(PROP_ID_intFld1,value)){
            this._intFld1 = value;
            internalClearRefs(PROP_ID_intFld1);
            
        }
    }
    
    /**
     * 长整型字段1: LONG_FLD1
     */
    public java.lang.Long getLongFld1(){
         onPropGet(PROP_ID_longFld1);
         return _longFld1;
    }

    /**
     * 长整型字段1: LONG_FLD1
     */
    public void setLongFld1(java.lang.Long value){
        if(onPropSet(PROP_ID_longFld1,value)){
            this._longFld1 = value;
            internalClearRefs(PROP_ID_longFld1);
            
        }
    }
    
    /**
     * 日期字段1: DATE_FLD1
     */
    public java.time.LocalDate getDateFld1(){
         onPropGet(PROP_ID_dateFld1);
         return _dateFld1;
    }

    /**
     * 日期字段1: DATE_FLD1
     */
    public void setDateFld1(java.time.LocalDate value){
        if(onPropSet(PROP_ID_dateFld1,value)){
            this._dateFld1 = value;
            internalClearRefs(PROP_ID_dateFld1);
            
        }
    }
    
    /**
     * 时间戳字段1: TIMESTAMP_FLD1
     */
    public java.sql.Timestamp getTimestampFld1(){
         onPropGet(PROP_ID_timestampFld1);
         return _timestampFld1;
    }

    /**
     * 时间戳字段1: TIMESTAMP_FLD1
     */
    public void setTimestampFld1(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_timestampFld1,value)){
            this._timestampFld1 = value;
            internalClearRefs(PROP_ID_timestampFld1);
            
        }
    }
    
    /**
     * 文件字段1: FILE_FLD1
     */
    public java.lang.String getFileFld1(){
         onPropGet(PROP_ID_fileFld1);
         return _fileFld1;
    }

    /**
     * 文件字段1: FILE_FLD1
     */
    public void setFileFld1(java.lang.String value){
        if(onPropSet(PROP_ID_fileFld1,value)){
            this._fileFld1 = value;
            internalClearRefs(PROP_ID_fileFld1);
            
        }
    }
    
    /**
     * 字符串字段2: STRING_FLD2
     */
    public java.lang.String getStringFld2(){
         onPropGet(PROP_ID_stringFld2);
         return _stringFld2;
    }

    /**
     * 字符串字段2: STRING_FLD2
     */
    public void setStringFld2(java.lang.String value){
        if(onPropSet(PROP_ID_stringFld2,value)){
            this._stringFld2 = value;
            internalClearRefs(PROP_ID_stringFld2);
            
        }
    }
    
    /**
     * 浮点型字段2: DECIMAL_FLD2
     */
    public java.math.BigDecimal getDecimalFld2(){
         onPropGet(PROP_ID_decimalFld2);
         return _decimalFld2;
    }

    /**
     * 浮点型字段2: DECIMAL_FLD2
     */
    public void setDecimalFld2(java.math.BigDecimal value){
        if(onPropSet(PROP_ID_decimalFld2,value)){
            this._decimalFld2 = value;
            internalClearRefs(PROP_ID_decimalFld2);
            
        }
    }
    
    /**
     * 整数型字段2: INT_FLD2
     */
    public java.lang.Integer getIntFld2(){
         onPropGet(PROP_ID_intFld2);
         return _intFld2;
    }

    /**
     * 整数型字段2: INT_FLD2
     */
    public void setIntFld2(java.lang.Integer value){
        if(onPropSet(PROP_ID_intFld2,value)){
            this._intFld2 = value;
            internalClearRefs(PROP_ID_intFld2);
            
        }
    }
    
    /**
     * 长整型字段2: LONG_FLD2
     */
    public java.lang.Long getLongFld2(){
         onPropGet(PROP_ID_longFld2);
         return _longFld2;
    }

    /**
     * 长整型字段2: LONG_FLD2
     */
    public void setLongFld2(java.lang.Long value){
        if(onPropSet(PROP_ID_longFld2,value)){
            this._longFld2 = value;
            internalClearRefs(PROP_ID_longFld2);
            
        }
    }
    
    /**
     * 日期字段2: DATE_FLD2
     */
    public java.time.LocalDate getDateFld2(){
         onPropGet(PROP_ID_dateFld2);
         return _dateFld2;
    }

    /**
     * 日期字段2: DATE_FLD2
     */
    public void setDateFld2(java.time.LocalDate value){
        if(onPropSet(PROP_ID_dateFld2,value)){
            this._dateFld2 = value;
            internalClearRefs(PROP_ID_dateFld2);
            
        }
    }
    
    /**
     * 时间戳字段2: TIMESTAMP_FLD2
     */
    public java.sql.Timestamp getTimestampFld2(){
         onPropGet(PROP_ID_timestampFld2);
         return _timestampFld2;
    }

    /**
     * 时间戳字段2: TIMESTAMP_FLD2
     */
    public void setTimestampFld2(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_timestampFld2,value)){
            this._timestampFld2 = value;
            internalClearRefs(PROP_ID_timestampFld2);
            
        }
    }
    
    /**
     * 文件字段2: FILE_FLD2
     */
    public java.lang.String getFileFld2(){
         onPropGet(PROP_ID_fileFld2);
         return _fileFld2;
    }

    /**
     * 文件字段2: FILE_FLD2
     */
    public void setFileFld2(java.lang.String value){
        if(onPropSet(PROP_ID_fileFld2,value)){
            this._fileFld2 = value;
            internalClearRefs(PROP_ID_fileFld2);
            
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
     * 工作流实例
     */
    public io.nop.wf.dao.entity.NopWfInstance getWfInstance(){
       return (io.nop.wf.dao.entity.NopWfInstance)internalGetRefEntity(PROP_NAME_wfInstance);
    }

    public void setWfInstance(io.nop.wf.dao.entity.NopWfInstance refEntity){
       if(refEntity == null){
         
         this.setNopFlowId(null);
         
       }else{
          internalSetRefEntity(PROP_NAME_wfInstance, refEntity,()->{
             
                    this.setNopFlowId(refEntity.getWfId());
                 
          });
       }
    }
       
    /**
     * 父对象
     */
    public io.nop.wf.dao.entity.NopWfDynEntity getParent(){
       return (io.nop.wf.dao.entity.NopWfDynEntity)internalGetRefEntity(PROP_NAME_parent);
    }

    public void setParent(io.nop.wf.dao.entity.NopWfDynEntity refEntity){
       if(refEntity == null){
         
         this.setParentId(null);
         
       }else{
          internalSetRefEntity(PROP_NAME_parent, refEntity,()->{
             
                    this.setParentId(refEntity.getSid());
                 
          });
       }
    }
       
    private final OrmEntitySet<io.nop.wf.dao.entity.NopWfDynEntity> _children = new OrmEntitySet<>(this, PROP_NAME_children,
        io.nop.wf.dao.entity.NopWfDynEntity.PROP_NAME_parent, null,io.nop.wf.dao.entity.NopWfDynEntity.class);

    /**
     * 子对象。 refPropName: parent, keyProp: {rel.keyProp}
     */
    public IOrmEntitySet<io.nop.wf.dao.entity.NopWfDynEntity> getChildren(){
       return _children;
    }
       
    private final OrmEntitySet<io.nop.orm.support.DynamicOrmKeyValueTable> _extFields = new OrmEntitySet<>(this, PROP_NAME_extFields,
        null, "fieldName",io.nop.orm.support.DynamicOrmKeyValueTable.class,"io.nop.wf.dao.entity.NopWfDynEntityExt");

    /**
     * 。 refPropName: , keyProp: {rel.keyProp}
     */
    public IOrmEntitySet<io.nop.orm.support.DynamicOrmKeyValueTable> getExtFields(){
       return _extFields;
    }
       
   private io.nop.orm.component.OrmFileComponent _fileFld1Component;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_fileFld1Component = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_fileFld1Component.put(io.nop.orm.component.OrmFileComponent.PROP_NAME_filePath,PROP_ID_fileFld1);
      
   }

   public io.nop.orm.component.OrmFileComponent getFileFld1Component(){
      if(_fileFld1Component == null){
          _fileFld1Component = new io.nop.orm.component.OrmFileComponent();
          _fileFld1Component.bindToEntity(this, COMPONENT_PROP_ID_MAP_fileFld1Component);
      }
      return _fileFld1Component;
   }

   private io.nop.orm.component.OrmFileComponent _fileFld2Component;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_fileFld2Component = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_fileFld2Component.put(io.nop.orm.component.OrmFileComponent.PROP_NAME_filePath,PROP_ID_fileFld2);
      
   }

   public io.nop.orm.component.OrmFileComponent getFileFld2Component(){
      if(_fileFld2Component == null){
          _fileFld2Component = new io.nop.orm.component.OrmFileComponent();
          _fileFld2Component.bindToEntity(this, COMPONENT_PROP_ID_MAP_fileFld2Component);
      }
      return _fileFld2Component;
   }

}
// resume CPD analysis - CPD-ON
