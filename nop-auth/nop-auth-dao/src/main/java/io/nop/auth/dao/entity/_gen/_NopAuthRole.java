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

import io.nop.auth.dao.entity.NopAuthRole;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  角色: nop_auth_role
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101"})
public class _NopAuthRole extends DynamicOrmEntity{
    
    /* 角色ID: ROLE_ID VARCHAR */
    public static final String PROP_NAME_roleId = "roleId";
    public static final int PROP_ID_roleId = 1;
    
    /* 角色名: ROLE_NAME VARCHAR */
    public static final String PROP_NAME_roleName = "roleName";
    public static final int PROP_ID_roleName = 2;
    
    /* 子角色: CHILD_ROLE_IDS VARCHAR */
    public static final String PROP_NAME_childRoleIds = "childRoleIds";
    public static final int PROP_ID_childRoleIds = 3;
    
    /* 是否主角色: IS_PRIMARY TINYINT */
    public static final String PROP_NAME_isPrimary = "isPrimary";
    public static final int PROP_ID_isPrimary = 4;
    
    /* 删除标识: DEL_FLAG TINYINT */
    public static final String PROP_NAME_delFlag = "delFlag";
    public static final int PROP_ID_delFlag = 5;
    
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

    
    /* relation: 用户映射 */
    public static final String PROP_NAME_userMappings = "userMappings";
    
    /* relation: 资源映射 */
    public static final String PROP_NAME_resourceMappings = "resourceMappings";
    

    public static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_roleId);
    public static final int[] PK_PROP_IDS = new int[]{PROP_ID_roleId};

    private static final String[] PROP_ID_TO_NAME = new String[12];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_roleId] = PROP_NAME_roleId;
          PROP_NAME_TO_ID.put(PROP_NAME_roleId, PROP_ID_roleId);
      
          PROP_ID_TO_NAME[PROP_ID_roleName] = PROP_NAME_roleName;
          PROP_NAME_TO_ID.put(PROP_NAME_roleName, PROP_ID_roleName);
      
          PROP_ID_TO_NAME[PROP_ID_childRoleIds] = PROP_NAME_childRoleIds;
          PROP_NAME_TO_ID.put(PROP_NAME_childRoleIds, PROP_ID_childRoleIds);
      
          PROP_ID_TO_NAME[PROP_ID_isPrimary] = PROP_NAME_isPrimary;
          PROP_NAME_TO_ID.put(PROP_NAME_isPrimary, PROP_ID_isPrimary);
      
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

    
    /* 角色ID: ROLE_ID */
    private java.lang.String _roleId;
    
    /* 角色名: ROLE_NAME */
    private java.lang.String _roleName;
    
    /* 子角色: CHILD_ROLE_IDS */
    private java.lang.String _childRoleIds;
    
    /* 是否主角色: IS_PRIMARY */
    private java.lang.Byte _isPrimary;
    
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
    

    public _NopAuthRole(){
    }

    protected NopAuthRole newInstance(){
       return new NopAuthRole();
    }

    @Override
    public NopAuthRole cloneInstance() {
        NopAuthRole entity = newInstance();
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
      return "io.nop.auth.dao.entity.NopAuthRole";
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
    
        return buildSimpleId(PROP_ID_roleId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_roleId;
          
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
        
            case PROP_ID_roleId:
               return getRoleId();
        
            case PROP_ID_roleName:
               return getRoleName();
        
            case PROP_ID_childRoleIds:
               return getChildRoleIds();
        
            case PROP_ID_isPrimary:
               return getIsPrimary();
        
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
        
            case PROP_ID_roleId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_roleId));
               }
               setRoleId(typedValue);
               break;
            }
        
            case PROP_ID_roleName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_roleName));
               }
               setRoleName(typedValue);
               break;
            }
        
            case PROP_ID_childRoleIds:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_childRoleIds));
               }
               setChildRoleIds(typedValue);
               break;
            }
        
            case PROP_ID_isPrimary:{
               java.lang.Byte typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toByte(value,
                       err-> newTypeConversionError(PROP_NAME_isPrimary));
               }
               setIsPrimary(typedValue);
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
        
            case PROP_ID_roleId:{
               onInitProp(propId);
               this._roleId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_roleName:{
               onInitProp(propId);
               this._roleName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_childRoleIds:{
               onInitProp(propId);
               this._childRoleIds = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_isPrimary:{
               onInitProp(propId);
               this._isPrimary = (java.lang.Byte)value;
               
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
     * 角色ID: ROLE_ID
     */
    public java.lang.String getRoleId(){
         onPropGet(PROP_ID_roleId);
         return _roleId;
    }

    /**
     * 角色ID: ROLE_ID
     */
    public void setRoleId(java.lang.String value){
        if(onPropSet(PROP_ID_roleId,value)){
            this._roleId = value;
            internalClearRefs(PROP_ID_roleId);
            orm_id();
        }
    }
    
    /**
     * 角色名: ROLE_NAME
     */
    public java.lang.String getRoleName(){
         onPropGet(PROP_ID_roleName);
         return _roleName;
    }

    /**
     * 角色名: ROLE_NAME
     */
    public void setRoleName(java.lang.String value){
        if(onPropSet(PROP_ID_roleName,value)){
            this._roleName = value;
            internalClearRefs(PROP_ID_roleName);
            
        }
    }
    
    /**
     * 子角色: CHILD_ROLE_IDS
     */
    public java.lang.String getChildRoleIds(){
         onPropGet(PROP_ID_childRoleIds);
         return _childRoleIds;
    }

    /**
     * 子角色: CHILD_ROLE_IDS
     */
    public void setChildRoleIds(java.lang.String value){
        if(onPropSet(PROP_ID_childRoleIds,value)){
            this._childRoleIds = value;
            internalClearRefs(PROP_ID_childRoleIds);
            
        }
    }
    
    /**
     * 是否主角色: IS_PRIMARY
     */
    public java.lang.Byte getIsPrimary(){
         onPropGet(PROP_ID_isPrimary);
         return _isPrimary;
    }

    /**
     * 是否主角色: IS_PRIMARY
     */
    public void setIsPrimary(java.lang.Byte value){
        if(onPropSet(PROP_ID_isPrimary,value)){
            this._isPrimary = value;
            internalClearRefs(PROP_ID_isPrimary);
            
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
    
    private final OrmEntitySet<io.nop.auth.dao.entity.NopAuthUserRole> _userMappings = new OrmEntitySet<>(this, PROP_NAME_userMappings,
        io.nop.auth.dao.entity.NopAuthUserRole.PROP_NAME_role, null,io.nop.auth.dao.entity.NopAuthUserRole.class);

    /**
     * 用户映射。 refPropName: role, keyProp: {rel.keyProp}
     */
    public IOrmEntitySet<io.nop.auth.dao.entity.NopAuthUserRole> getUserMappings(){
       return _userMappings;
    }
       
    private final OrmEntitySet<io.nop.auth.dao.entity.NopAuthRoleResource> _resourceMappings = new OrmEntitySet<>(this, PROP_NAME_resourceMappings,
        io.nop.auth.dao.entity.NopAuthRoleResource.PROP_NAME_role, null,io.nop.auth.dao.entity.NopAuthRoleResource.class);

    /**
     * 资源映射。 refPropName: role, keyProp: {rel.keyProp}
     */
    public IOrmEntitySet<io.nop.auth.dao.entity.NopAuthRoleResource> getResourceMappings(){
       return _resourceMappings;
    }
       
        public List<io.nop.auth.dao.entity.NopAuthUser> getRelatedUserList(){
            return (List<io.nop.auth.dao.entity.NopAuthUser>)io.nop.orm.support.OrmEntityHelper.getRefProps(getUserMappings(),io.nop.auth.dao.entity.NopAuthUserRole.PROP_NAME_user);
        }
    
        public String getRelatedUserList_label(){
        return io.nop.core.lang.utils.Underscore.pluckThenJoin(getRelatedUserList(),io.nop.auth.dao.entity.NopAuthUser.PROP_NAME_userName);
        }
    
        public List<java.lang.String> getRelatedUserIdList(){
        return (List<java.lang.String>)io.nop.orm.support.OrmEntityHelper.getRefProps(getUserMappings(),io.nop.auth.dao.entity.NopAuthUserRole.PROP_NAME_userId);
        }

        public void setRelatedUserIdList(List<java.lang.String> value){
        io.nop.orm.support.OrmEntityHelper.setRefProps(getUserMappings(),io.nop.auth.dao.entity.NopAuthUserRole.PROP_NAME_userId,value);
        }
    
        public List<io.nop.auth.dao.entity.NopAuthResource> getRelatedResourceList(){
            return (List<io.nop.auth.dao.entity.NopAuthResource>)io.nop.orm.support.OrmEntityHelper.getRefProps(getResourceMappings(),io.nop.auth.dao.entity.NopAuthRoleResource.PROP_NAME_resource);
        }
    
        public String getRelatedResourceList_label(){
        return io.nop.core.lang.utils.Underscore.pluckThenJoin(getRelatedResourceList(),io.nop.auth.dao.entity.NopAuthResource.PROP_NAME_displayName);
        }
    
        public List<java.lang.String> getRelatedResourceIdList(){
        return (List<java.lang.String>)io.nop.orm.support.OrmEntityHelper.getRefProps(getResourceMappings(),io.nop.auth.dao.entity.NopAuthRoleResource.PROP_NAME_resourceId);
        }

        public void setRelatedResourceIdList(List<java.lang.String> value){
        io.nop.orm.support.OrmEntityHelper.setRefProps(getResourceMappings(),io.nop.auth.dao.entity.NopAuthRoleResource.PROP_NAME_resourceId,value);
        }
    
}
// resume CPD analysis - CPD-ON
