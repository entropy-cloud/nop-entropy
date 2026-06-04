//__XGEN_FORCE_OVERRIDE__
    package io.nop.auth.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    import java.util.List;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopAuthUser_tenantInputBean extends CrudInputBase {

    
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


        private String _password;

    
        @PropMeta(propId=3)
    
        public String getPassword(){
            return _password;
        }

        public void setPassword(String value){
            this._password = value;
        }


        private String _salt;

    
        @PropMeta(propId=4)
    
        public String getSalt(){
            return _salt;
        }

        public void setSalt(String value){
            this._salt = value;
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


        private Integer _status;

    
        @PropMeta(propId=17)
    
        public Integer getStatus(){
            return _status;
        }

        public void setStatus(Integer value){
            this._status = value;
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


        private String _tenantId;

    
        @PropMeta(propId=31)
    
        public String getTenantId(){
            return _tenantId;
        }

        public void setTenantId(String value){
            this._tenantId = value;
        }


        private String _remark;

    
        @PropMeta(propId=36)
    
        public String getRemark(){
            return _remark;
        }

        public void setRemark(String value){
            this._remark = value;
        }


        private java.util.List<java.lang.String> _relatedRoleList_ids;

    
        public java.util.List<java.lang.String> getRelatedRoleList_ids(){
            return _relatedRoleList_ids;
        }

        public void setRelatedRoleList_ids(java.util.List<java.lang.String> value){
            this._relatedRoleList_ids = value;
        }


        private java.util.List<java.lang.String> _relatedGroupList_ids;

    
        public java.util.List<java.lang.String> getRelatedGroupList_ids(){
            return _relatedGroupList_ids;
        }

        public void setRelatedGroupList_ids(java.util.List<java.lang.String> value){
            this._relatedGroupList_ids = value;
        }


        private List<NopAuthUserRoleInputBean> _roleMappings;

        public List<NopAuthUserRoleInputBean> getRoleMappings(){
            return _roleMappings;
        }

        public void setRoleMappings(List<NopAuthUserRoleInputBean> value){
            this._roleMappings = value;
        }


        private List<NopAuthGroupUserInputBean> _groupMappings;

        public List<NopAuthGroupUserInputBean> getGroupMappings(){
            return _groupMappings;
        }

        public void setGroupMappings(List<NopAuthGroupUserInputBean> value){
            this._groupMappings = value;
        }


        private List<NopAuthRoleInputBean> _relatedRoleList;

        public List<NopAuthRoleInputBean> getRelatedRoleList(){
            return _relatedRoleList;
        }

        public void setRelatedRoleList(List<NopAuthRoleInputBean> value){
            this._relatedRoleList = value;
        }


        private List<NopAuthGroupInputBean> _relatedGroupList;

        public List<NopAuthGroupInputBean> getRelatedGroupList(){
            return _relatedGroupList;
        }

        public void setRelatedGroupList(List<NopAuthGroupInputBean> value){
            this._relatedGroupList = value;
        }


    }
