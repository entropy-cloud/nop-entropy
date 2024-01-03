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

import io.nop.auth.dao.entity.NopAuthUser;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  用户: nop_auth_user
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopAuthUser extends DynamicOrmEntity{
    
    /* 用户ID: USER_ID VARCHAR */
    public static final String PROP_NAME_userId = "userId";
    public static final int PROP_ID_userId = 1;
    
    /* 用户名: USER_NAME VARCHAR */
    public static final String PROP_NAME_userName = "userName";
    public static final int PROP_ID_userName = 2;
    
    /* 密码: PASSWORD VARCHAR */
    public static final String PROP_NAME_password = "password";
    public static final int PROP_ID_password = 3;
    
    /* 密码加盐: SALT VARCHAR */
    public static final String PROP_NAME_salt = "salt";
    public static final int PROP_ID_salt = 4;
    
    /* 昵称: NICK_NAME VARCHAR */
    public static final String PROP_NAME_nickName = "nickName";
    public static final int PROP_ID_nickName = 5;
    
    /* 所属部门: DEPT_ID VARCHAR */
    public static final String PROP_NAME_deptId = "deptId";
    public static final int PROP_ID_deptId = 6;
    
    /* 用户外部标识: OPEN_ID VARCHAR */
    public static final String PROP_NAME_openId = "openId";
    public static final int PROP_ID_openId = 7;
    
    /* 相关部门: REL_DEPT_ID VARCHAR */
    public static final String PROP_NAME_relDeptId = "relDeptId";
    public static final int PROP_ID_relDeptId = 8;
    
    /* 性别: GENDER INTEGER */
    public static final String PROP_NAME_gender = "gender";
    public static final int PROP_ID_gender = 9;
    
    /* 头像: AVATAR VARCHAR */
    public static final String PROP_NAME_avatar = "avatar";
    public static final int PROP_ID_avatar = 10;
    
    /* 邮件: EMAIL VARCHAR */
    public static final String PROP_NAME_email = "email";
    public static final int PROP_ID_email = 11;
    
    /* 电话已验证: EMAIL_VERIFIED TINYINT */
    public static final String PROP_NAME_emailVerified = "emailVerified";
    public static final int PROP_ID_emailVerified = 12;
    
    /* 电话: PHONE VARCHAR */
    public static final String PROP_NAME_phone = "phone";
    public static final int PROP_ID_phone = 13;
    
    /* 电话已验证: PHONE_VERIFIED TINYINT */
    public static final String PROP_NAME_phoneVerified = "phoneVerified";
    public static final int PROP_ID_phoneVerified = 14;
    
    /* 生日: BIRTHDAY DATE */
    public static final String PROP_NAME_birthday = "birthday";
    public static final int PROP_ID_birthday = 15;
    
    /* 用户类型: USER_TYPE INTEGER */
    public static final String PROP_NAME_userType = "userType";
    public static final int PROP_ID_userType = 16;
    
    /* 用户状态: STATUS INTEGER */
    public static final String PROP_NAME_status = "status";
    public static final int PROP_ID_status = 17;
    
    /* 证件类型: ID_TYPE VARCHAR */
    public static final String PROP_NAME_idType = "idType";
    public static final int PROP_ID_idType = 18;
    
    /* 证件号: ID_NBR VARCHAR */
    public static final String PROP_NAME_idNbr = "idNbr";
    public static final int PROP_ID_idNbr = 19;
    
    /* 用户过期时间: EXPIRE_AT DATETIME */
    public static final String PROP_NAME_expireAt = "expireAt";
    public static final int PROP_ID_expireAt = 20;
    
    /* 上次密码更新时间: PWD_UPDATE_TIME DATETIME */
    public static final String PROP_NAME_pwdUpdateTime = "pwdUpdateTime";
    public static final int PROP_ID_pwdUpdateTime = 21;
    
    /* 登陆后立刻修改密码: CHANGE_PWD_AT_LOGIN TINYINT */
    public static final String PROP_NAME_changePwdAtLogin = "changePwdAtLogin";
    public static final int PROP_ID_changePwdAtLogin = 22;
    
    /* 真实姓名: REAL_NAME VARCHAR */
    public static final String PROP_NAME_realName = "realName";
    public static final int PROP_ID_realName = 23;
    
    /* 上级: MANAGER_ID VARCHAR */
    public static final String PROP_NAME_managerId = "managerId";
    public static final int PROP_ID_managerId = 24;
    
    /* 工号: WORK_NO VARCHAR */
    public static final String PROP_NAME_workNo = "workNo";
    public static final int PROP_ID_workNo = 25;
    
    /* 职务: POSITION_ID VARCHAR */
    public static final String PROP_NAME_positionId = "positionId";
    public static final int PROP_ID_positionId = 26;
    
    /* 座机: TELEPHONE VARCHAR */
    public static final String PROP_NAME_telephone = "telephone";
    public static final int PROP_ID_telephone = 27;
    
    /* 设备ID: CLIENT_ID VARCHAR */
    public static final String PROP_NAME_clientId = "clientId";
    public static final int PROP_ID_clientId = 28;
    
    /* 删除标识: DEL_FLAG TINYINT */
    public static final String PROP_NAME_delFlag = "delFlag";
    public static final int PROP_ID_delFlag = 29;
    
    /* 数据版本: VERSION INTEGER */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 30;
    
    /* 租户ID: TENANT_ID VARCHAR */
    public static final String PROP_NAME_tenantId = "tenantId";
    public static final int PROP_ID_tenantId = 31;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 32;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 33;
    
    /* 修改人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 34;
    
    /* 修改时间: UPDATE_TIME TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 35;
    
    /* 备注: REMARK VARCHAR */
    public static final String PROP_NAME_remark = "remark";
    public static final int PROP_ID_remark = 36;
    

    private static int _PROP_ID_BOUND = 37;

    
    /* relation: 部门 */
    public static final String PROP_NAME_dept = "dept";
    
    /* relation: 部门 */
    public static final String PROP_NAME_relatedDept = "relatedDept";
    
    /* relation: 岗位 */
    public static final String PROP_NAME_position = "position";
    
    /* relation: 上级 */
    public static final String PROP_NAME_manager = "manager";
    
    /* relation: 角色映射 */
    public static final String PROP_NAME_roleMappings = "roleMappings";
    
    /* relation: 代理人映射 */
    public static final String PROP_NAME_substitutionMappings = "substitutionMappings";
    
    /* relation: 分组映射 */
    public static final String PROP_NAME_groupMappings = "groupMappings";
    
    /* component:  */
    public static final String PROP_NAME_avatarComponent = "avatarComponent";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_userId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_userId};

    private static final String[] PROP_ID_TO_NAME = new String[37];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_userId] = PROP_NAME_userId;
          PROP_NAME_TO_ID.put(PROP_NAME_userId, PROP_ID_userId);
      
          PROP_ID_TO_NAME[PROP_ID_userName] = PROP_NAME_userName;
          PROP_NAME_TO_ID.put(PROP_NAME_userName, PROP_ID_userName);
      
          PROP_ID_TO_NAME[PROP_ID_password] = PROP_NAME_password;
          PROP_NAME_TO_ID.put(PROP_NAME_password, PROP_ID_password);
      
          PROP_ID_TO_NAME[PROP_ID_salt] = PROP_NAME_salt;
          PROP_NAME_TO_ID.put(PROP_NAME_salt, PROP_ID_salt);
      
          PROP_ID_TO_NAME[PROP_ID_nickName] = PROP_NAME_nickName;
          PROP_NAME_TO_ID.put(PROP_NAME_nickName, PROP_ID_nickName);
      
          PROP_ID_TO_NAME[PROP_ID_deptId] = PROP_NAME_deptId;
          PROP_NAME_TO_ID.put(PROP_NAME_deptId, PROP_ID_deptId);
      
          PROP_ID_TO_NAME[PROP_ID_openId] = PROP_NAME_openId;
          PROP_NAME_TO_ID.put(PROP_NAME_openId, PROP_ID_openId);
      
          PROP_ID_TO_NAME[PROP_ID_relDeptId] = PROP_NAME_relDeptId;
          PROP_NAME_TO_ID.put(PROP_NAME_relDeptId, PROP_ID_relDeptId);
      
          PROP_ID_TO_NAME[PROP_ID_gender] = PROP_NAME_gender;
          PROP_NAME_TO_ID.put(PROP_NAME_gender, PROP_ID_gender);
      
          PROP_ID_TO_NAME[PROP_ID_avatar] = PROP_NAME_avatar;
          PROP_NAME_TO_ID.put(PROP_NAME_avatar, PROP_ID_avatar);
      
          PROP_ID_TO_NAME[PROP_ID_email] = PROP_NAME_email;
          PROP_NAME_TO_ID.put(PROP_NAME_email, PROP_ID_email);
      
          PROP_ID_TO_NAME[PROP_ID_emailVerified] = PROP_NAME_emailVerified;
          PROP_NAME_TO_ID.put(PROP_NAME_emailVerified, PROP_ID_emailVerified);
      
          PROP_ID_TO_NAME[PROP_ID_phone] = PROP_NAME_phone;
          PROP_NAME_TO_ID.put(PROP_NAME_phone, PROP_ID_phone);
      
          PROP_ID_TO_NAME[PROP_ID_phoneVerified] = PROP_NAME_phoneVerified;
          PROP_NAME_TO_ID.put(PROP_NAME_phoneVerified, PROP_ID_phoneVerified);
      
          PROP_ID_TO_NAME[PROP_ID_birthday] = PROP_NAME_birthday;
          PROP_NAME_TO_ID.put(PROP_NAME_birthday, PROP_ID_birthday);
      
          PROP_ID_TO_NAME[PROP_ID_userType] = PROP_NAME_userType;
          PROP_NAME_TO_ID.put(PROP_NAME_userType, PROP_ID_userType);
      
          PROP_ID_TO_NAME[PROP_ID_status] = PROP_NAME_status;
          PROP_NAME_TO_ID.put(PROP_NAME_status, PROP_ID_status);
      
          PROP_ID_TO_NAME[PROP_ID_idType] = PROP_NAME_idType;
          PROP_NAME_TO_ID.put(PROP_NAME_idType, PROP_ID_idType);
      
          PROP_ID_TO_NAME[PROP_ID_idNbr] = PROP_NAME_idNbr;
          PROP_NAME_TO_ID.put(PROP_NAME_idNbr, PROP_ID_idNbr);
      
          PROP_ID_TO_NAME[PROP_ID_expireAt] = PROP_NAME_expireAt;
          PROP_NAME_TO_ID.put(PROP_NAME_expireAt, PROP_ID_expireAt);
      
          PROP_ID_TO_NAME[PROP_ID_pwdUpdateTime] = PROP_NAME_pwdUpdateTime;
          PROP_NAME_TO_ID.put(PROP_NAME_pwdUpdateTime, PROP_ID_pwdUpdateTime);
      
          PROP_ID_TO_NAME[PROP_ID_changePwdAtLogin] = PROP_NAME_changePwdAtLogin;
          PROP_NAME_TO_ID.put(PROP_NAME_changePwdAtLogin, PROP_ID_changePwdAtLogin);
      
          PROP_ID_TO_NAME[PROP_ID_realName] = PROP_NAME_realName;
          PROP_NAME_TO_ID.put(PROP_NAME_realName, PROP_ID_realName);
      
          PROP_ID_TO_NAME[PROP_ID_managerId] = PROP_NAME_managerId;
          PROP_NAME_TO_ID.put(PROP_NAME_managerId, PROP_ID_managerId);
      
          PROP_ID_TO_NAME[PROP_ID_workNo] = PROP_NAME_workNo;
          PROP_NAME_TO_ID.put(PROP_NAME_workNo, PROP_ID_workNo);
      
          PROP_ID_TO_NAME[PROP_ID_positionId] = PROP_NAME_positionId;
          PROP_NAME_TO_ID.put(PROP_NAME_positionId, PROP_ID_positionId);
      
          PROP_ID_TO_NAME[PROP_ID_telephone] = PROP_NAME_telephone;
          PROP_NAME_TO_ID.put(PROP_NAME_telephone, PROP_ID_telephone);
      
          PROP_ID_TO_NAME[PROP_ID_clientId] = PROP_NAME_clientId;
          PROP_NAME_TO_ID.put(PROP_NAME_clientId, PROP_ID_clientId);
      
          PROP_ID_TO_NAME[PROP_ID_delFlag] = PROP_NAME_delFlag;
          PROP_NAME_TO_ID.put(PROP_NAME_delFlag, PROP_ID_delFlag);
      
          PROP_ID_TO_NAME[PROP_ID_version] = PROP_NAME_version;
          PROP_NAME_TO_ID.put(PROP_NAME_version, PROP_ID_version);
      
          PROP_ID_TO_NAME[PROP_ID_tenantId] = PROP_NAME_tenantId;
          PROP_NAME_TO_ID.put(PROP_NAME_tenantId, PROP_ID_tenantId);
      
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

    
    /* 用户ID: USER_ID */
    private java.lang.String _userId;
    
    /* 用户名: USER_NAME */
    private java.lang.String _userName;
    
    /* 密码: PASSWORD */
    private java.lang.String _password;
    
    /* 密码加盐: SALT */
    private java.lang.String _salt;
    
    /* 昵称: NICK_NAME */
    private java.lang.String _nickName;
    
    /* 所属部门: DEPT_ID */
    private java.lang.String _deptId;
    
    /* 用户外部标识: OPEN_ID */
    private java.lang.String _openId;
    
    /* 相关部门: REL_DEPT_ID */
    private java.lang.String _relDeptId;
    
    /* 性别: GENDER */
    private java.lang.Integer _gender;
    
    /* 头像: AVATAR */
    private java.lang.String _avatar;
    
    /* 邮件: EMAIL */
    private java.lang.String _email;
    
    /* 电话已验证: EMAIL_VERIFIED */
    private java.lang.Byte _emailVerified;
    
    /* 电话: PHONE */
    private java.lang.String _phone;
    
    /* 电话已验证: PHONE_VERIFIED */
    private java.lang.Byte _phoneVerified;
    
    /* 生日: BIRTHDAY */
    private java.time.LocalDate _birthday;
    
    /* 用户类型: USER_TYPE */
    private java.lang.Integer _userType;
    
    /* 用户状态: STATUS */
    private java.lang.Integer _status;
    
    /* 证件类型: ID_TYPE */
    private java.lang.String _idType;
    
    /* 证件号: ID_NBR */
    private java.lang.String _idNbr;
    
    /* 用户过期时间: EXPIRE_AT */
    private java.time.LocalDateTime _expireAt;
    
    /* 上次密码更新时间: PWD_UPDATE_TIME */
    private java.time.LocalDateTime _pwdUpdateTime;
    
    /* 登陆后立刻修改密码: CHANGE_PWD_AT_LOGIN */
    private java.lang.Byte _changePwdAtLogin;
    
    /* 真实姓名: REAL_NAME */
    private java.lang.String _realName;
    
    /* 上级: MANAGER_ID */
    private java.lang.String _managerId;
    
    /* 工号: WORK_NO */
    private java.lang.String _workNo;
    
    /* 职务: POSITION_ID */
    private java.lang.String _positionId;
    
    /* 座机: TELEPHONE */
    private java.lang.String _telephone;
    
    /* 设备ID: CLIENT_ID */
    private java.lang.String _clientId;
    
    /* 删除标识: DEL_FLAG */
    private java.lang.Byte _delFlag;
    
    /* 数据版本: VERSION */
    private java.lang.Integer _version;
    
    /* 租户ID: TENANT_ID */
    private java.lang.String _tenantId;
    
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
    

    public _NopAuthUser(){
        // for debug
    }

    protected NopAuthUser newInstance(){
       return new NopAuthUser();
    }

    @Override
    public NopAuthUser cloneInstance() {
        NopAuthUser entity = newInstance();
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
      return "io.nop.auth.dao.entity.NopAuthUser";
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
    
        return buildSimpleId(PROP_ID_userId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_userId;
          
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
        
            case PROP_ID_userId:
               return getUserId();
        
            case PROP_ID_userName:
               return getUserName();
        
            case PROP_ID_password:
               return getPassword();
        
            case PROP_ID_salt:
               return getSalt();
        
            case PROP_ID_nickName:
               return getNickName();
        
            case PROP_ID_deptId:
               return getDeptId();
        
            case PROP_ID_openId:
               return getOpenId();
        
            case PROP_ID_relDeptId:
               return getRelDeptId();
        
            case PROP_ID_gender:
               return getGender();
        
            case PROP_ID_avatar:
               return getAvatar();
        
            case PROP_ID_email:
               return getEmail();
        
            case PROP_ID_emailVerified:
               return getEmailVerified();
        
            case PROP_ID_phone:
               return getPhone();
        
            case PROP_ID_phoneVerified:
               return getPhoneVerified();
        
            case PROP_ID_birthday:
               return getBirthday();
        
            case PROP_ID_userType:
               return getUserType();
        
            case PROP_ID_status:
               return getStatus();
        
            case PROP_ID_idType:
               return getIdType();
        
            case PROP_ID_idNbr:
               return getIdNbr();
        
            case PROP_ID_expireAt:
               return getExpireAt();
        
            case PROP_ID_pwdUpdateTime:
               return getPwdUpdateTime();
        
            case PROP_ID_changePwdAtLogin:
               return getChangePwdAtLogin();
        
            case PROP_ID_realName:
               return getRealName();
        
            case PROP_ID_managerId:
               return getManagerId();
        
            case PROP_ID_workNo:
               return getWorkNo();
        
            case PROP_ID_positionId:
               return getPositionId();
        
            case PROP_ID_telephone:
               return getTelephone();
        
            case PROP_ID_clientId:
               return getClientId();
        
            case PROP_ID_delFlag:
               return getDelFlag();
        
            case PROP_ID_version:
               return getVersion();
        
            case PROP_ID_tenantId:
               return getTenantId();
        
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
        
            case PROP_ID_userId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_userId));
               }
               setUserId(typedValue);
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
        
            case PROP_ID_password:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_password));
               }
               setPassword(typedValue);
               break;
            }
        
            case PROP_ID_salt:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_salt));
               }
               setSalt(typedValue);
               break;
            }
        
            case PROP_ID_nickName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_nickName));
               }
               setNickName(typedValue);
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
        
            case PROP_ID_openId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_openId));
               }
               setOpenId(typedValue);
               break;
            }
        
            case PROP_ID_relDeptId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_relDeptId));
               }
               setRelDeptId(typedValue);
               break;
            }
        
            case PROP_ID_gender:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_gender));
               }
               setGender(typedValue);
               break;
            }
        
            case PROP_ID_avatar:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_avatar));
               }
               setAvatar(typedValue);
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
        
            case PROP_ID_emailVerified:{
               java.lang.Byte typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toByte(value,
                       err-> newTypeConversionError(PROP_NAME_emailVerified));
               }
               setEmailVerified(typedValue);
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
        
            case PROP_ID_phoneVerified:{
               java.lang.Byte typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toByte(value,
                       err-> newTypeConversionError(PROP_NAME_phoneVerified));
               }
               setPhoneVerified(typedValue);
               break;
            }
        
            case PROP_ID_birthday:{
               java.time.LocalDate typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLocalDate(value,
                       err-> newTypeConversionError(PROP_NAME_birthday));
               }
               setBirthday(typedValue);
               break;
            }
        
            case PROP_ID_userType:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_userType));
               }
               setUserType(typedValue);
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
        
            case PROP_ID_idType:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_idType));
               }
               setIdType(typedValue);
               break;
            }
        
            case PROP_ID_idNbr:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_idNbr));
               }
               setIdNbr(typedValue);
               break;
            }
        
            case PROP_ID_expireAt:{
               java.time.LocalDateTime typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLocalDateTime(value,
                       err-> newTypeConversionError(PROP_NAME_expireAt));
               }
               setExpireAt(typedValue);
               break;
            }
        
            case PROP_ID_pwdUpdateTime:{
               java.time.LocalDateTime typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLocalDateTime(value,
                       err-> newTypeConversionError(PROP_NAME_pwdUpdateTime));
               }
               setPwdUpdateTime(typedValue);
               break;
            }
        
            case PROP_ID_changePwdAtLogin:{
               java.lang.Byte typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toByte(value,
                       err-> newTypeConversionError(PROP_NAME_changePwdAtLogin));
               }
               setChangePwdAtLogin(typedValue);
               break;
            }
        
            case PROP_ID_realName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_realName));
               }
               setRealName(typedValue);
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
        
            case PROP_ID_workNo:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_workNo));
               }
               setWorkNo(typedValue);
               break;
            }
        
            case PROP_ID_positionId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_positionId));
               }
               setPositionId(typedValue);
               break;
            }
        
            case PROP_ID_telephone:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_telephone));
               }
               setTelephone(typedValue);
               break;
            }
        
            case PROP_ID_clientId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_clientId));
               }
               setClientId(typedValue);
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
        
            case PROP_ID_tenantId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_tenantId));
               }
               setTenantId(typedValue);
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
        
            case PROP_ID_userId:{
               onInitProp(propId);
               this._userId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_userName:{
               onInitProp(propId);
               this._userName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_password:{
               onInitProp(propId);
               this._password = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_salt:{
               onInitProp(propId);
               this._salt = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_nickName:{
               onInitProp(propId);
               this._nickName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_deptId:{
               onInitProp(propId);
               this._deptId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_openId:{
               onInitProp(propId);
               this._openId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_relDeptId:{
               onInitProp(propId);
               this._relDeptId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_gender:{
               onInitProp(propId);
               this._gender = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_avatar:{
               onInitProp(propId);
               this._avatar = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_email:{
               onInitProp(propId);
               this._email = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_emailVerified:{
               onInitProp(propId);
               this._emailVerified = (java.lang.Byte)value;
               
               break;
            }
        
            case PROP_ID_phone:{
               onInitProp(propId);
               this._phone = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_phoneVerified:{
               onInitProp(propId);
               this._phoneVerified = (java.lang.Byte)value;
               
               break;
            }
        
            case PROP_ID_birthday:{
               onInitProp(propId);
               this._birthday = (java.time.LocalDate)value;
               
               break;
            }
        
            case PROP_ID_userType:{
               onInitProp(propId);
               this._userType = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_status:{
               onInitProp(propId);
               this._status = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_idType:{
               onInitProp(propId);
               this._idType = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_idNbr:{
               onInitProp(propId);
               this._idNbr = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_expireAt:{
               onInitProp(propId);
               this._expireAt = (java.time.LocalDateTime)value;
               
               break;
            }
        
            case PROP_ID_pwdUpdateTime:{
               onInitProp(propId);
               this._pwdUpdateTime = (java.time.LocalDateTime)value;
               
               break;
            }
        
            case PROP_ID_changePwdAtLogin:{
               onInitProp(propId);
               this._changePwdAtLogin = (java.lang.Byte)value;
               
               break;
            }
        
            case PROP_ID_realName:{
               onInitProp(propId);
               this._realName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_managerId:{
               onInitProp(propId);
               this._managerId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_workNo:{
               onInitProp(propId);
               this._workNo = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_positionId:{
               onInitProp(propId);
               this._positionId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_telephone:{
               onInitProp(propId);
               this._telephone = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_clientId:{
               onInitProp(propId);
               this._clientId = (java.lang.String)value;
               
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
        
            case PROP_ID_tenantId:{
               onInitProp(propId);
               this._tenantId = (java.lang.String)value;
               
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
     * 用户ID: USER_ID
     */
    public java.lang.String getUserId(){
         onPropGet(PROP_ID_userId);
         return _userId;
    }

    /**
     * 用户ID: USER_ID
     */
    public void setUserId(java.lang.String value){
        if(onPropSet(PROP_ID_userId,value)){
            this._userId = value;
            internalClearRefs(PROP_ID_userId);
            orm_id();
        }
    }
    
    /**
     * 用户名: USER_NAME
     */
    public java.lang.String getUserName(){
         onPropGet(PROP_ID_userName);
         return _userName;
    }

    /**
     * 用户名: USER_NAME
     */
    public void setUserName(java.lang.String value){
        if(onPropSet(PROP_ID_userName,value)){
            this._userName = value;
            internalClearRefs(PROP_ID_userName);
            
        }
    }
    
    /**
     * 密码: PASSWORD
     */
    public java.lang.String getPassword(){
         onPropGet(PROP_ID_password);
         return _password;
    }

    /**
     * 密码: PASSWORD
     */
    public void setPassword(java.lang.String value){
        if(onPropSet(PROP_ID_password,value)){
            this._password = value;
            internalClearRefs(PROP_ID_password);
            
        }
    }
    
    /**
     * 密码加盐: SALT
     */
    public java.lang.String getSalt(){
         onPropGet(PROP_ID_salt);
         return _salt;
    }

    /**
     * 密码加盐: SALT
     */
    public void setSalt(java.lang.String value){
        if(onPropSet(PROP_ID_salt,value)){
            this._salt = value;
            internalClearRefs(PROP_ID_salt);
            
        }
    }
    
    /**
     * 昵称: NICK_NAME
     */
    public java.lang.String getNickName(){
         onPropGet(PROP_ID_nickName);
         return _nickName;
    }

    /**
     * 昵称: NICK_NAME
     */
    public void setNickName(java.lang.String value){
        if(onPropSet(PROP_ID_nickName,value)){
            this._nickName = value;
            internalClearRefs(PROP_ID_nickName);
            
        }
    }
    
    /**
     * 所属部门: DEPT_ID
     */
    public java.lang.String getDeptId(){
         onPropGet(PROP_ID_deptId);
         return _deptId;
    }

    /**
     * 所属部门: DEPT_ID
     */
    public void setDeptId(java.lang.String value){
        if(onPropSet(PROP_ID_deptId,value)){
            this._deptId = value;
            internalClearRefs(PROP_ID_deptId);
            
        }
    }
    
    /**
     * 用户外部标识: OPEN_ID
     */
    public java.lang.String getOpenId(){
         onPropGet(PROP_ID_openId);
         return _openId;
    }

    /**
     * 用户外部标识: OPEN_ID
     */
    public void setOpenId(java.lang.String value){
        if(onPropSet(PROP_ID_openId,value)){
            this._openId = value;
            internalClearRefs(PROP_ID_openId);
            
        }
    }
    
    /**
     * 相关部门: REL_DEPT_ID
     */
    public java.lang.String getRelDeptId(){
         onPropGet(PROP_ID_relDeptId);
         return _relDeptId;
    }

    /**
     * 相关部门: REL_DEPT_ID
     */
    public void setRelDeptId(java.lang.String value){
        if(onPropSet(PROP_ID_relDeptId,value)){
            this._relDeptId = value;
            internalClearRefs(PROP_ID_relDeptId);
            
        }
    }
    
    /**
     * 性别: GENDER
     */
    public java.lang.Integer getGender(){
         onPropGet(PROP_ID_gender);
         return _gender;
    }

    /**
     * 性别: GENDER
     */
    public void setGender(java.lang.Integer value){
        if(onPropSet(PROP_ID_gender,value)){
            this._gender = value;
            internalClearRefs(PROP_ID_gender);
            
        }
    }
    
    /**
     * 头像: AVATAR
     */
    public java.lang.String getAvatar(){
         onPropGet(PROP_ID_avatar);
         return _avatar;
    }

    /**
     * 头像: AVATAR
     */
    public void setAvatar(java.lang.String value){
        if(onPropSet(PROP_ID_avatar,value)){
            this._avatar = value;
            internalClearRefs(PROP_ID_avatar);
            
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
     * 电话已验证: EMAIL_VERIFIED
     */
    public java.lang.Byte getEmailVerified(){
         onPropGet(PROP_ID_emailVerified);
         return _emailVerified;
    }

    /**
     * 电话已验证: EMAIL_VERIFIED
     */
    public void setEmailVerified(java.lang.Byte value){
        if(onPropSet(PROP_ID_emailVerified,value)){
            this._emailVerified = value;
            internalClearRefs(PROP_ID_emailVerified);
            
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
     * 电话已验证: PHONE_VERIFIED
     */
    public java.lang.Byte getPhoneVerified(){
         onPropGet(PROP_ID_phoneVerified);
         return _phoneVerified;
    }

    /**
     * 电话已验证: PHONE_VERIFIED
     */
    public void setPhoneVerified(java.lang.Byte value){
        if(onPropSet(PROP_ID_phoneVerified,value)){
            this._phoneVerified = value;
            internalClearRefs(PROP_ID_phoneVerified);
            
        }
    }
    
    /**
     * 生日: BIRTHDAY
     */
    public java.time.LocalDate getBirthday(){
         onPropGet(PROP_ID_birthday);
         return _birthday;
    }

    /**
     * 生日: BIRTHDAY
     */
    public void setBirthday(java.time.LocalDate value){
        if(onPropSet(PROP_ID_birthday,value)){
            this._birthday = value;
            internalClearRefs(PROP_ID_birthday);
            
        }
    }
    
    /**
     * 用户类型: USER_TYPE
     */
    public java.lang.Integer getUserType(){
         onPropGet(PROP_ID_userType);
         return _userType;
    }

    /**
     * 用户类型: USER_TYPE
     */
    public void setUserType(java.lang.Integer value){
        if(onPropSet(PROP_ID_userType,value)){
            this._userType = value;
            internalClearRefs(PROP_ID_userType);
            
        }
    }
    
    /**
     * 用户状态: STATUS
     */
    public java.lang.Integer getStatus(){
         onPropGet(PROP_ID_status);
         return _status;
    }

    /**
     * 用户状态: STATUS
     */
    public void setStatus(java.lang.Integer value){
        if(onPropSet(PROP_ID_status,value)){
            this._status = value;
            internalClearRefs(PROP_ID_status);
            
        }
    }
    
    /**
     * 证件类型: ID_TYPE
     */
    public java.lang.String getIdType(){
         onPropGet(PROP_ID_idType);
         return _idType;
    }

    /**
     * 证件类型: ID_TYPE
     */
    public void setIdType(java.lang.String value){
        if(onPropSet(PROP_ID_idType,value)){
            this._idType = value;
            internalClearRefs(PROP_ID_idType);
            
        }
    }
    
    /**
     * 证件号: ID_NBR
     */
    public java.lang.String getIdNbr(){
         onPropGet(PROP_ID_idNbr);
         return _idNbr;
    }

    /**
     * 证件号: ID_NBR
     */
    public void setIdNbr(java.lang.String value){
        if(onPropSet(PROP_ID_idNbr,value)){
            this._idNbr = value;
            internalClearRefs(PROP_ID_idNbr);
            
        }
    }
    
    /**
     * 用户过期时间: EXPIRE_AT
     */
    public java.time.LocalDateTime getExpireAt(){
         onPropGet(PROP_ID_expireAt);
         return _expireAt;
    }

    /**
     * 用户过期时间: EXPIRE_AT
     */
    public void setExpireAt(java.time.LocalDateTime value){
        if(onPropSet(PROP_ID_expireAt,value)){
            this._expireAt = value;
            internalClearRefs(PROP_ID_expireAt);
            
        }
    }
    
    /**
     * 上次密码更新时间: PWD_UPDATE_TIME
     */
    public java.time.LocalDateTime getPwdUpdateTime(){
         onPropGet(PROP_ID_pwdUpdateTime);
         return _pwdUpdateTime;
    }

    /**
     * 上次密码更新时间: PWD_UPDATE_TIME
     */
    public void setPwdUpdateTime(java.time.LocalDateTime value){
        if(onPropSet(PROP_ID_pwdUpdateTime,value)){
            this._pwdUpdateTime = value;
            internalClearRefs(PROP_ID_pwdUpdateTime);
            
        }
    }
    
    /**
     * 登陆后立刻修改密码: CHANGE_PWD_AT_LOGIN
     */
    public java.lang.Byte getChangePwdAtLogin(){
         onPropGet(PROP_ID_changePwdAtLogin);
         return _changePwdAtLogin;
    }

    /**
     * 登陆后立刻修改密码: CHANGE_PWD_AT_LOGIN
     */
    public void setChangePwdAtLogin(java.lang.Byte value){
        if(onPropSet(PROP_ID_changePwdAtLogin,value)){
            this._changePwdAtLogin = value;
            internalClearRefs(PROP_ID_changePwdAtLogin);
            
        }
    }
    
    /**
     * 真实姓名: REAL_NAME
     */
    public java.lang.String getRealName(){
         onPropGet(PROP_ID_realName);
         return _realName;
    }

    /**
     * 真实姓名: REAL_NAME
     */
    public void setRealName(java.lang.String value){
        if(onPropSet(PROP_ID_realName,value)){
            this._realName = value;
            internalClearRefs(PROP_ID_realName);
            
        }
    }
    
    /**
     * 上级: MANAGER_ID
     */
    public java.lang.String getManagerId(){
         onPropGet(PROP_ID_managerId);
         return _managerId;
    }

    /**
     * 上级: MANAGER_ID
     */
    public void setManagerId(java.lang.String value){
        if(onPropSet(PROP_ID_managerId,value)){
            this._managerId = value;
            internalClearRefs(PROP_ID_managerId);
            
        }
    }
    
    /**
     * 工号: WORK_NO
     */
    public java.lang.String getWorkNo(){
         onPropGet(PROP_ID_workNo);
         return _workNo;
    }

    /**
     * 工号: WORK_NO
     */
    public void setWorkNo(java.lang.String value){
        if(onPropSet(PROP_ID_workNo,value)){
            this._workNo = value;
            internalClearRefs(PROP_ID_workNo);
            
        }
    }
    
    /**
     * 职务: POSITION_ID
     */
    public java.lang.String getPositionId(){
         onPropGet(PROP_ID_positionId);
         return _positionId;
    }

    /**
     * 职务: POSITION_ID
     */
    public void setPositionId(java.lang.String value){
        if(onPropSet(PROP_ID_positionId,value)){
            this._positionId = value;
            internalClearRefs(PROP_ID_positionId);
            
        }
    }
    
    /**
     * 座机: TELEPHONE
     */
    public java.lang.String getTelephone(){
         onPropGet(PROP_ID_telephone);
         return _telephone;
    }

    /**
     * 座机: TELEPHONE
     */
    public void setTelephone(java.lang.String value){
        if(onPropSet(PROP_ID_telephone,value)){
            this._telephone = value;
            internalClearRefs(PROP_ID_telephone);
            
        }
    }
    
    /**
     * 设备ID: CLIENT_ID
     */
    public java.lang.String getClientId(){
         onPropGet(PROP_ID_clientId);
         return _clientId;
    }

    /**
     * 设备ID: CLIENT_ID
     */
    public void setClientId(java.lang.String value){
        if(onPropSet(PROP_ID_clientId,value)){
            this._clientId = value;
            internalClearRefs(PROP_ID_clientId);
            
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
     * 租户ID: TENANT_ID
     */
    public java.lang.String getTenantId(){
         onPropGet(PROP_ID_tenantId);
         return _tenantId;
    }

    /**
     * 租户ID: TENANT_ID
     */
    public void setTenantId(java.lang.String value){
        if(onPropSet(PROP_ID_tenantId,value)){
            this._tenantId = value;
            internalClearRefs(PROP_ID_tenantId);
            
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
     * 部门
     */
    public io.nop.auth.dao.entity.NopAuthDept getDept(){
       return (io.nop.auth.dao.entity.NopAuthDept)internalGetRefEntity(PROP_NAME_dept);
    }

    public void setDept(io.nop.auth.dao.entity.NopAuthDept refEntity){
       if(refEntity == null){
         
         this.setDeptId(null);
         
       }else{
          internalSetRefEntity(PROP_NAME_dept, refEntity,()->{
             
                    this.setDeptId(refEntity.getDeptId());
                 
          });
       }
    }
       
    /**
     * 部门
     */
    public io.nop.auth.dao.entity.NopAuthDept getRelatedDept(){
       return (io.nop.auth.dao.entity.NopAuthDept)internalGetRefEntity(PROP_NAME_relatedDept);
    }

    public void setRelatedDept(io.nop.auth.dao.entity.NopAuthDept refEntity){
       if(refEntity == null){
         
         this.setRelDeptId(null);
         
       }else{
          internalSetRefEntity(PROP_NAME_relatedDept, refEntity,()->{
             
                    this.setRelDeptId(refEntity.getDeptId());
                 
          });
       }
    }
       
    /**
     * 岗位
     */
    public io.nop.auth.dao.entity.NopAuthPosition getPosition(){
       return (io.nop.auth.dao.entity.NopAuthPosition)internalGetRefEntity(PROP_NAME_position);
    }

    public void setPosition(io.nop.auth.dao.entity.NopAuthPosition refEntity){
       if(refEntity == null){
         
         this.setPositionId(null);
         
       }else{
          internalSetRefEntity(PROP_NAME_position, refEntity,()->{
             
                    this.setPositionId(refEntity.getPositionId());
                 
          });
       }
    }
       
    /**
     * 上级
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
       
    private final OrmEntitySet<io.nop.auth.dao.entity.NopAuthUserRole> _roleMappings = new OrmEntitySet<>(this, PROP_NAME_roleMappings,
        io.nop.auth.dao.entity.NopAuthUserRole.PROP_NAME_user, null,io.nop.auth.dao.entity.NopAuthUserRole.class);

    /**
     * 角色映射。 refPropName: user, keyProp: {rel.keyProp}
     */
    public IOrmEntitySet<io.nop.auth.dao.entity.NopAuthUserRole> getRoleMappings(){
       return _roleMappings;
    }
       
    private final OrmEntitySet<io.nop.auth.dao.entity.NopAuthUserSubstitution> _substitutionMappings = new OrmEntitySet<>(this, PROP_NAME_substitutionMappings,
        io.nop.auth.dao.entity.NopAuthUserSubstitution.PROP_NAME_user, null,io.nop.auth.dao.entity.NopAuthUserSubstitution.class);

    /**
     * 代理人映射。 refPropName: user, keyProp: {rel.keyProp}
     */
    public IOrmEntitySet<io.nop.auth.dao.entity.NopAuthUserSubstitution> getSubstitutionMappings(){
       return _substitutionMappings;
    }
       
    private final OrmEntitySet<io.nop.auth.dao.entity.NopAuthGroupDept> _groupMappings = new OrmEntitySet<>(this, PROP_NAME_groupMappings,
        io.nop.auth.dao.entity.NopAuthGroupDept.PROP_NAME_user, null,io.nop.auth.dao.entity.NopAuthGroupDept.class);

    /**
     * 分组映射。 refPropName: user, keyProp: {rel.keyProp}
     */
    public IOrmEntitySet<io.nop.auth.dao.entity.NopAuthGroupDept> getGroupMappings(){
       return _groupMappings;
    }
       
   private io.nop.orm.component.OrmFileComponent _avatarComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_avatarComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_avatarComponent.put(io.nop.orm.component.OrmFileComponent.PROP_NAME_filePath,PROP_ID_avatar);
      
   }

   public io.nop.orm.component.OrmFileComponent getAvatarComponent(){
      if(_avatarComponent == null){
          _avatarComponent = new io.nop.orm.component.OrmFileComponent();
          _avatarComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_avatarComponent);
      }
      return _avatarComponent;
   }

        public List<io.nop.auth.dao.entity.NopAuthRole> getRelatedRoleList(){
            return (List<io.nop.auth.dao.entity.NopAuthRole>)io.nop.orm.support.OrmEntityHelper.getRefProps(getRoleMappings(),io.nop.auth.dao.entity.NopAuthUserRole.PROP_NAME_role);
        }
    
        public String getRelatedRoleList_label(){
        return io.nop.core.lang.utils.Underscore.pluckThenJoin(getRelatedRoleList(),io.nop.auth.dao.entity.NopAuthRole.PROP_NAME_roleName);
        }
    
        public List<java.lang.String> getRelatedRoleIdList(){
        return (List<java.lang.String>)io.nop.orm.support.OrmEntityHelper.getRefProps(getRoleMappings(),io.nop.auth.dao.entity.NopAuthUserRole.PROP_NAME_roleId);
        }

        public void setRelatedRoleIdList(List<java.lang.String> value){
        io.nop.orm.support.OrmEntityHelper.setRefProps(getRoleMappings(),io.nop.auth.dao.entity.NopAuthUserRole.PROP_NAME_roleId,value);
        }
    
        public List<io.nop.auth.dao.entity.NopAuthGroup> getRelatedGroupList(){
            return (List<io.nop.auth.dao.entity.NopAuthGroup>)io.nop.orm.support.OrmEntityHelper.getRefProps(getGroupMappings(),io.nop.auth.dao.entity.NopAuthGroupDept.PROP_NAME_group);
        }
    
        public String getRelatedGroupList_label(){
        return io.nop.core.lang.utils.Underscore.pluckThenJoin(getRelatedGroupList(),io.nop.auth.dao.entity.NopAuthGroup.PROP_NAME_name);
        }
    
        public List<java.lang.String> getRelatedGroupIdList(){
        return (List<java.lang.String>)io.nop.orm.support.OrmEntityHelper.getRefProps(getGroupMappings(),io.nop.auth.dao.entity.NopAuthGroupDept.PROP_NAME_groupId);
        }

        public void setRelatedGroupIdList(List<java.lang.String> value){
        io.nop.orm.support.OrmEntityHelper.setRefProps(getGroupMappings(),io.nop.auth.dao.entity.NopAuthGroupDept.PROP_NAME_groupId,value);
        }
    
}
// resume CPD analysis - CPD-ON
