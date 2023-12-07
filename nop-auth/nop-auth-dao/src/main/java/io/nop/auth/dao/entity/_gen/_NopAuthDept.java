package io.nop.auth.dao.entity._gen;

import io.nop.orm.model.IEntityModel;
import io.nop.orm.support.DynamicOrmEntity;
import io.nop.orm.support.OrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.orm.IOrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.api.core.convert.ConvertHelper;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;

import io.nop.auth.dao.entity.NopAuthDept;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  部门: nop_auth_dept
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116"})
public class _NopAuthDept extends DynamicOrmEntity{
    
    /* 主键: DEPT_ID VARCHAR */
    public static final String PROP_NAME_deptId = "deptId";
    public static final int PROP_ID_deptId = 1;
    
    /* 名称: DEPT_NAME VARCHAR */
    public static final String PROP_NAME_deptName = "deptName";
    public static final int PROP_ID_deptName = 2;
    
    /* 父ID: PARENT_ID VARCHAR */
    public static final String PROP_NAME_parentId = "parentId";
    public static final int PROP_ID_parentId = 3;
    
    /* 排序: ORDER_NUM INTEGER */
    public static final String PROP_NAME_orderNum = "orderNum";
    public static final int PROP_ID_orderNum = 4;
    
    /* 类型: DEPT_TYPE VARCHAR */
    public static final String PROP_NAME_deptType = "deptType";
    public static final int PROP_ID_deptType = 5;
    
    /* 部分负责人: MANAGER_ID VARCHAR */
    public static final String PROP_NAME_managerId = "managerId";
    public static final int PROP_ID_managerId = 6;
    
    /* 邮件: EMAIL VARCHAR */
    public static final String PROP_NAME_email = "email";
    public static final int PROP_ID_email = 7;
    
    /* 电话: PHONE VARCHAR */
    public static final String PROP_NAME_phone = "phone";
    public static final int PROP_ID_phone = 8;
    
    /* 删除标识: DEL_FLAG TINYINT */
    public static final String PROP_NAME_delFlag = "delFlag";
    public static final int PROP_ID_delFlag = 9;
    
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

    
    /* relation: 父资源 */
    public static final String PROP_NAME_parent = "parent";
    
    /* relation: 部门负责人 */
    public static final String PROP_NAME_manager = "manager";
    
    /* relation: 部门用户 */
    public static final String PROP_NAME_deptUsers = "deptUsers";
    
    /* relation: 子资源 */
    public static final String PROP_NAME_children = "children";
    

    public static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_deptId);
    public static final int[] PK_PROP_IDS = new int[]{PROP_ID_deptId};

    private static final String[] PROP_ID_TO_NAME = new String[16];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_deptId] = PROP_NAME_deptId;
          PROP_NAME_TO_ID.put(PROP_NAME_deptId, PROP_ID_deptId);
      
          PROP_ID_TO_NAME[PROP_ID_deptName] = PROP_NAME_deptName;
          PROP_NAME_TO_ID.put(PROP_NAME_deptName, PROP_ID_deptName);
      
          PROP_ID_TO_NAME[PROP_ID_parentId] = PROP_NAME_parentId;
          PROP_NAME_TO_ID.put(PROP_NAME_parentId, PROP_ID_parentId);
      
          PROP_ID_TO_NAME[PROP_ID_orderNum] = PROP_NAME_orderNum;
          PROP_NAME_TO_ID.put(PROP_NAME_orderNum, PROP_ID_orderNum);
      
          PROP_ID_TO_NAME[PROP_ID_deptType] = PROP_NAME_deptType;
          PROP_NAME_TO_ID.put(PROP_NAME_deptType, PROP_ID_deptType);
      
          PROP_ID_TO_NAME[PROP_ID_managerId] = PROP_NAME_managerId;
          PROP_NAME_TO_ID.put(PROP_NAME_managerId, PROP_ID_managerId);
      
          PROP_ID_TO_NAME[PROP_ID_email] = PROP_NAME_email;
          PROP_NAME_TO_ID.put(PROP_NAME_email, PROP_ID_email);
      
          PROP_ID_TO_NAME[PROP_ID_phone] = PROP_NAME_phone;
          PROP_NAME_TO_ID.put(PROP_NAME_phone, PROP_ID_phone);
      
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

    
    /* 主键: DEPT_ID */
    private java.lang.String _deptId;
    
    /* 名称: DEPT_NAME */
    private java.lang.String _deptName;
    
    /* 父ID: PARENT_ID */
    private java.lang.String _parentId;
    
    /* 排序: ORDER_NUM */
    private java.lang.Integer _orderNum;
    
    /* 类型: DEPT_TYPE */
    private java.lang.String _deptType;
    
    /* 部分负责人: MANAGER_ID */
    private java.lang.String _managerId;
    
    /* 邮件: EMAIL */
    private java.lang.String _email;
    
    /* 电话: PHONE */
    private java.lang.String _phone;
    
    /* 删除标识: DEL_FLAG */
    private java.lang.Byte _delFlag;
    
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
    

    public _NopAuthDept(){
    }

    protected NopAuthDept newInstance(){
       return new NopAuthDept();
    }

    @Override
    public NopAuthDept cloneInstance() {
        NopAuthDept entity = newInstance();
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
      return "io.nop.auth.dao.entity.NopAuthDept";
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
    
        return buildSimpleId(PROP_ID_deptId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_deptId;
          
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
        
            case PROP_ID_deptId:
               return getDeptId();
        
            case PROP_ID_deptName:
               return getDeptName();
        
            case PROP_ID_parentId:
               return getParentId();
        
            case PROP_ID_orderNum:
               return getOrderNum();
        
            case PROP_ID_deptType:
               return getDeptType();
        
            case PROP_ID_managerId:
               return getManagerId();
        
            case PROP_ID_email:
               return getEmail();
        
            case PROP_ID_phone:
               return getPhone();
        
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
        
            case PROP_ID_deptId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_deptId));
               }
               setDeptId(typedValue);
               break;
            }
        
            case PROP_ID_deptName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_deptName));
               }
               setDeptName(typedValue);
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
        
            case PROP_ID_orderNum:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_orderNum));
               }
               setOrderNum(typedValue);
               break;
            }
        
            case PROP_ID_deptType:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_deptType));
               }
               setDeptType(typedValue);
               break;
            }
        
            case PROP_ID_managerId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_managerId));
               }
               setManagerId(typedValue);
               break;
            }
        
            case PROP_ID_email:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_email));
               }
               setEmail(typedValue);
               break;
            }
        
            case PROP_ID_phone:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_phone));
               }
               setPhone(typedValue);
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
        
            case PROP_ID_deptId:{
               onInitProp(propId);
               this._deptId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_deptName:{
               onInitProp(propId);
               this._deptName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_parentId:{
               onInitProp(propId);
               this._parentId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_orderNum:{
               onInitProp(propId);
               this._orderNum = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_deptType:{
               onInitProp(propId);
               this._deptType = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_managerId:{
               onInitProp(propId);
               this._managerId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_email:{
               onInitProp(propId);
               this._email = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_phone:{
               onInitProp(propId);
               this._phone = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_delFlag:{
               onInitProp(propId);
               this._delFlag = (java.lang.Byte)value;
               
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
     * 主键: DEPT_ID
     */
    public java.lang.String getDeptId(){
         onPropGet(PROP_ID_deptId);
         return _deptId;
    }

    /**
     * 主键: DEPT_ID
     */
    public void setDeptId(java.lang.String value){
        if(onPropSet(PROP_ID_deptId,value)){
            this._deptId = value;
            internalClearRefs(PROP_ID_deptId);
            orm_id();
        }
    }
    
    /**
     * 名称: DEPT_NAME
     */
    public java.lang.String getDeptName(){
         onPropGet(PROP_ID_deptName);
         return _deptName;
    }

    /**
     * 名称: DEPT_NAME
     */
    public void setDeptName(java.lang.String value){
        if(onPropSet(PROP_ID_deptName,value)){
            this._deptName = value;
            internalClearRefs(PROP_ID_deptName);
            
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
     * 排序: ORDER_NUM
     */
    public java.lang.Integer getOrderNum(){
         onPropGet(PROP_ID_orderNum);
         return _orderNum;
    }

    /**
     * 排序: ORDER_NUM
     */
    public void setOrderNum(java.lang.Integer value){
        if(onPropSet(PROP_ID_orderNum,value)){
            this._orderNum = value;
            internalClearRefs(PROP_ID_orderNum);
            
        }
    }
    
    /**
     * 类型: DEPT_TYPE
     */
    public java.lang.String getDeptType(){
         onPropGet(PROP_ID_deptType);
         return _deptType;
    }

    /**
     * 类型: DEPT_TYPE
     */
    public void setDeptType(java.lang.String value){
        if(onPropSet(PROP_ID_deptType,value)){
            this._deptType = value;
            internalClearRefs(PROP_ID_deptType);
            
        }
    }
    
    /**
     * 部分负责人: MANAGER_ID
     */
    public java.lang.String getManagerId(){
         onPropGet(PROP_ID_managerId);
         return _managerId;
    }

    /**
     * 部分负责人: MANAGER_ID
     */
    public void setManagerId(java.lang.String value){
        if(onPropSet(PROP_ID_managerId,value)){
            this._managerId = value;
            internalClearRefs(PROP_ID_managerId);
            
        }
    }
    
    /**
     * 邮件: EMAIL
     */
    public java.lang.String getEmail(){
         onPropGet(PROP_ID_email);
         return _email;
    }

    /**
     * 邮件: EMAIL
     */
    public void setEmail(java.lang.String value){
        if(onPropSet(PROP_ID_email,value)){
            this._email = value;
            internalClearRefs(PROP_ID_email);
            
        }
    }
    
    /**
     * 电话: PHONE
     */
    public java.lang.String getPhone(){
         onPropGet(PROP_ID_phone);
         return _phone;
    }

    /**
     * 电话: PHONE
     */
    public void setPhone(java.lang.String value){
        if(onPropSet(PROP_ID_phone,value)){
            this._phone = value;
            internalClearRefs(PROP_ID_phone);
            
        }
    }
    
    /**
     * 删除标识: DEL_FLAG
     */
    public java.lang.Byte getDelFlag(){
         onPropGet(PROP_ID_delFlag);
         return _delFlag;
    }

    /**
     * 删除标识: DEL_FLAG
     */
    public void setDelFlag(java.lang.Byte value){
        if(onPropSet(PROP_ID_delFlag,value)){
            this._delFlag = value;
            internalClearRefs(PROP_ID_delFlag);
            
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
     * 父资源
     */
    public io.nop.auth.dao.entity.NopAuthDept getParent(){
       return (io.nop.auth.dao.entity.NopAuthDept)internalGetRefEntity(PROP_NAME_parent);
    }

    public void setParent(io.nop.auth.dao.entity.NopAuthDept refEntity){
       if(refEntity == null){
         
         this.setParentId(null);
         
       }else{
          internalSetRefEntity(PROP_NAME_parent, refEntity,()->{
             
                    this.setParentId(refEntity.getDeptId());
                 
          });
       }
    }
       
    /**
     * 部门负责人
     */
    public io.nop.auth.dao.entity.NopAuthUser getManager(){
       return (io.nop.auth.dao.entity.NopAuthUser)internalGetRefEntity(PROP_NAME_manager);
    }

    public void setManager(io.nop.auth.dao.entity.NopAuthUser refEntity){
       if(refEntity == null){
         
         this.setManagerId(null);
         
       }else{
          internalSetRefEntity(PROP_NAME_manager, refEntity,()->{
             
                    this.setManagerId(refEntity.getUserId());
                 
          });
       }
    }
       
    private final OrmEntitySet<io.nop.auth.dao.entity.NopAuthUser> _deptUsers = new OrmEntitySet<>(this, PROP_NAME_deptUsers,
        io.nop.auth.dao.entity.NopAuthUser.PROP_NAME_dept, null,io.nop.auth.dao.entity.NopAuthUser.class);

    /**
     * 部门用户。 refPropName: dept, keyProp: {rel.keyProp}
     */
    public IOrmEntitySet<io.nop.auth.dao.entity.NopAuthUser> getDeptUsers(){
       return _deptUsers;
    }
       
    private final OrmEntitySet<io.nop.auth.dao.entity.NopAuthDept> _children = new OrmEntitySet<>(this, PROP_NAME_children,
        io.nop.auth.dao.entity.NopAuthDept.PROP_NAME_parent, null,io.nop.auth.dao.entity.NopAuthDept.class);

    /**
     * 子资源。 refPropName: parent, keyProp: {rel.keyProp}
     */
    public IOrmEntitySet<io.nop.auth.dao.entity.NopAuthDept> getChildren(){
       return _children;
    }
       
}
// resume CPD analysis - CPD-ON
