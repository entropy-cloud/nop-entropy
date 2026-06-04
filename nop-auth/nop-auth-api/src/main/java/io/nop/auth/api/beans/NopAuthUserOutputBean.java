//__XGEN_FORCE_OVERRIDE__
    package io.nop.auth.api.beans;

    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import java.util.List;
    
    import java.util.Map;

    @DataBean
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopAuthUserOutputBean {

    
        private String _userId;

    
        @PropMeta(propId=1)
    
        public String getUserId(){
            return _userId;
        }

        public void setUserId(String value){
            this._userId = value;
        }


        private String _userName;

    
        @PropMeta(propId=2)
    
        public String getUserName(){
            return _userName;
        }

        public void setUserName(String value){
            this._userName = value;
        }


        private String _nickName;

    
        @PropMeta(propId=5)
    
        public String getNickName(){
            return _nickName;
        }

        public void setNickName(String value){
            this._nickName = value;
        }


        private String _deptId;

    
        @PropMeta(propId=6)
    
        public String getDeptId(){
            return _deptId;
        }

        public void setDeptId(String value){
            this._deptId = value;
        }


        private String _openId;

    
        @PropMeta(propId=7)
    
        public String getOpenId(){
            return _openId;
        }

        public void setOpenId(String value){
            this._openId = value;
        }


        private String _relDeptId;

    
        @PropMeta(propId=8)
    
        public String getRelDeptId(){
            return _relDeptId;
        }

        public void setRelDeptId(String value){
            this._relDeptId = value;
        }


        private Integer _gender;

    
        @PropMeta(propId=9)
    
        public Integer getGender(){
            return _gender;
        }

        public void setGender(Integer value){
            this._gender = value;
        }


        private String _gender_label;

    
        public String getGender_label(){
            return _gender_label;
        }

        public void setGender_label(String value){
            this._gender_label = value;
        }


        private String _avatar;

    
        @PropMeta(propId=10)
    
        public String getAvatar(){
            return _avatar;
        }

        public void setAvatar(String value){
            this._avatar = value;
        }


        private String _email;

    
        @PropMeta(propId=11)
    
        public String getEmail(){
            return _email;
        }

        public void setEmail(String value){
            this._email = value;
        }


        private Byte _emailVerified;

    
        @PropMeta(propId=12)
    
        public Byte getEmailVerified(){
            return _emailVerified;
        }

        public void setEmailVerified(Byte value){
            this._emailVerified = value;
        }


        private String _phone;

    
        @PropMeta(propId=13)
    
        public String getPhone(){
            return _phone;
        }

        public void setPhone(String value){
            this._phone = value;
        }


        private Byte _phoneVerified;

    
        @PropMeta(propId=14)
    
        public Byte getPhoneVerified(){
            return _phoneVerified;
        }

        public void setPhoneVerified(Byte value){
            this._phoneVerified = value;
        }


        private java.time.LocalDate _birthday;

    
        @PropMeta(propId=15)
    
        public java.time.LocalDate getBirthday(){
            return _birthday;
        }

        public void setBirthday(java.time.LocalDate value){
            this._birthday = value;
        }


        private Integer _userType;

    
        @PropMeta(propId=16)
    
        public Integer getUserType(){
            return _userType;
        }

        public void setUserType(Integer value){
            this._userType = value;
        }


        private String _userType_label;

    
        public String getUserType_label(){
            return _userType_label;
        }

        public void setUserType_label(String value){
            this._userType_label = value;
        }


        private Integer _status;

    
        @PropMeta(propId=17)
    
        public Integer getStatus(){
            return _status;
        }

        public void setStatus(Integer value){
            this._status = value;
        }


        private String _status_label;

    
        public String getStatus_label(){
            return _status_label;
        }

        public void setStatus_label(String value){
            this._status_label = value;
        }


        private String _idType;

    
        @PropMeta(propId=18)
    
        public String getIdType(){
            return _idType;
        }

        public void setIdType(String value){
            this._idType = value;
        }


        private String _idNbr;

    
        @PropMeta(propId=19)
    
        public String getIdNbr(){
            return _idNbr;
        }

        public void setIdNbr(String value){
            this._idNbr = value;
        }


        private java.time.LocalDateTime _expireAt;

    
        @PropMeta(propId=20)
    
        public java.time.LocalDateTime getExpireAt(){
            return _expireAt;
        }

        public void setExpireAt(java.time.LocalDateTime value){
            this._expireAt = value;
        }


        private java.time.LocalDateTime _pwdUpdateTime;

    
        @PropMeta(propId=21)
    
        public java.time.LocalDateTime getPwdUpdateTime(){
            return _pwdUpdateTime;
        }

        public void setPwdUpdateTime(java.time.LocalDateTime value){
            this._pwdUpdateTime = value;
        }


        private Byte _changePwdAtLogin;

    
        @PropMeta(propId=22)
    
        public Byte getChangePwdAtLogin(){
            return _changePwdAtLogin;
        }

        public void setChangePwdAtLogin(Byte value){
            this._changePwdAtLogin = value;
        }


        private String _realName;

    
        @PropMeta(propId=23)
    
        public String getRealName(){
            return _realName;
        }

        public void setRealName(String value){
            this._realName = value;
        }


        private String _managerId;

    
        @PropMeta(propId=24)
    
        public String getManagerId(){
            return _managerId;
        }

        public void setManagerId(String value){
            this._managerId = value;
        }


        private String _workNo;

    
        @PropMeta(propId=25)
    
        public String getWorkNo(){
            return _workNo;
        }

        public void setWorkNo(String value){
            this._workNo = value;
        }


        private String _positionId;

    
        @PropMeta(propId=26)
    
        public String getPositionId(){
            return _positionId;
        }

        public void setPositionId(String value){
            this._positionId = value;
        }


        private String _telephone;

    
        @PropMeta(propId=27)
    
        public String getTelephone(){
            return _telephone;
        }

        public void setTelephone(String value){
            this._telephone = value;
        }


        private String _clientId;

    
        @PropMeta(propId=28)
    
        public String getClientId(){
            return _clientId;
        }

        public void setClientId(String value){
            this._clientId = value;
        }


        private Byte _delFlag;

    
        @PropMeta(propId=29)
    
        public Byte getDelFlag(){
            return _delFlag;
        }

        public void setDelFlag(Byte value){
            this._delFlag = value;
        }


        private Integer _version;

    
        @PropMeta(propId=30)
    
        public Integer getVersion(){
            return _version;
        }

        public void setVersion(Integer value){
            this._version = value;
        }


        private String _tenantId;

    
        @PropMeta(propId=31)
    
        public String getTenantId(){
            return _tenantId;
        }

        public void setTenantId(String value){
            this._tenantId = value;
        }


        private String _tenantId_label;

    
        public String getTenantId_label(){
            return _tenantId_label;
        }

        public void setTenantId_label(String value){
            this._tenantId_label = value;
        }


        private String _createdBy;

    
        @PropMeta(propId=32)
    
        public String getCreatedBy(){
            return _createdBy;
        }

        public void setCreatedBy(String value){
            this._createdBy = value;
        }


        private java.sql.Timestamp _createTime;

    
        @PropMeta(propId=33)
    
        public java.sql.Timestamp getCreateTime(){
            return _createTime;
        }

        public void setCreateTime(java.sql.Timestamp value){
            this._createTime = value;
        }


        private String _updatedBy;

    
        @PropMeta(propId=34)
    
        public String getUpdatedBy(){
            return _updatedBy;
        }

        public void setUpdatedBy(String value){
            this._updatedBy = value;
        }


        private java.sql.Timestamp _updateTime;

    
        @PropMeta(propId=35)
    
        public java.sql.Timestamp getUpdateTime(){
            return _updateTime;
        }

        public void setUpdateTime(java.sql.Timestamp value){
            this._updateTime = value;
        }


        private String _remark;

    
        @PropMeta(propId=36)
    
        public String getRemark(){
            return _remark;
        }

        public void setRemark(String value){
            this._remark = value;
        }


        private io.nop.api.core.beans.file.FileStatusBean _avatarComponentFileStatus;

    
        public io.nop.api.core.beans.file.FileStatusBean getAvatarComponentFileStatus(){
            return _avatarComponentFileStatus;
        }

        public void setAvatarComponentFileStatus(io.nop.api.core.beans.file.FileStatusBean value){
            this._avatarComponentFileStatus = value;
        }


        private java.util.List<java.lang.String> _relatedRoleList_ids;

    
        public java.util.List<java.lang.String> getRelatedRoleList_ids(){
            return _relatedRoleList_ids;
        }

        public void setRelatedRoleList_ids(java.util.List<java.lang.String> value){
            this._relatedRoleList_ids = value;
        }


        private String _relatedRoleList_label;

    
        public String getRelatedRoleList_label(){
            return _relatedRoleList_label;
        }

        public void setRelatedRoleList_label(String value){
            this._relatedRoleList_label = value;
        }


        private java.util.List<java.lang.String> _relatedGroupList_ids;

    
        public java.util.List<java.lang.String> getRelatedGroupList_ids(){
            return _relatedGroupList_ids;
        }

        public void setRelatedGroupList_ids(java.util.List<java.lang.String> value){
            this._relatedGroupList_ids = value;
        }


        private String _relatedGroupList_label;

    
        public String getRelatedGroupList_label(){
            return _relatedGroupList_label;
        }

        public void setRelatedGroupList_label(String value){
            this._relatedGroupList_label = value;
        }


        private Map<String,Object> _dept;

        public Map<String,Object> getDept(){
            return _dept;
        }

        public void setDept(Map<String,Object> value){
            this._dept = value;
        }


        private Map<String,Object> _relatedDept;

        public Map<String,Object> getRelatedDept(){
            return _relatedDept;
        }

        public void setRelatedDept(Map<String,Object> value){
            this._relatedDept = value;
        }


        private Map<String,Object> _position;

        public Map<String,Object> getPosition(){
            return _position;
        }

        public void setPosition(Map<String,Object> value){
            this._position = value;
        }


        private Map<String,Object> _manager;

        public Map<String,Object> getManager(){
            return _manager;
        }

        public void setManager(Map<String,Object> value){
            this._manager = value;
        }


        private List<Map<String,Object>> _roleMappings;

        public List<Map<String,Object>> getRoleMappings(){
            return _roleMappings;
        }

        public void setRoleMappings(List<Map<String,Object>> value){
            this._roleMappings = value;
        }


        private List<Map<String,Object>> _substitutionMappings;

        public List<Map<String,Object>> getSubstitutionMappings(){
            return _substitutionMappings;
        }

        public void setSubstitutionMappings(List<Map<String,Object>> value){
            this._substitutionMappings = value;
        }


        private List<Map<String,Object>> _groupMappings;

        public List<Map<String,Object>> getGroupMappings(){
            return _groupMappings;
        }

        public void setGroupMappings(List<Map<String,Object>> value){
            this._groupMappings = value;
        }


        private List<Map<String,Object>> _relatedRoleList;

        public List<Map<String,Object>> getRelatedRoleList(){
            return _relatedRoleList;
        }

        public void setRelatedRoleList(List<Map<String,Object>> value){
            this._relatedRoleList = value;
        }


        private List<Map<String,Object>> _relatedGroupList;

        public List<Map<String,Object>> getRelatedGroupList(){
            return _relatedGroupList;
        }

        public void setRelatedGroupList(List<Map<String,Object>> value){
            this._relatedGroupList = value;
        }


    }
