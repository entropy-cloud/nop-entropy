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
    public class NopAuthMfaSettingInputBean extends CrudInputBase {

    
        private String _userId;

    
        @PropMeta(propId=1)
    
        public String getUserId(){
            return _userId;
        }

        public void setUserId(String value){
            this._userId = value;
        }


        private String _mfaType;

    
        @PropMeta(propId=2)
    
        public String getMfaType(){
            return _mfaType;
        }

        public void setMfaType(String value){
            this._mfaType = value;
        }


        private String _secret;

    
        @PropMeta(propId=3)
    
        public String getSecret(){
            return _secret;
        }

        public void setSecret(String value){
            this._secret = value;
        }


        private String _status;

    
        @PropMeta(propId=4)
    
        public String getStatus(){
            return _status;
        }

        public void setStatus(String value){
            this._status = value;
        }


        private String _bindToken;

    
        @PropMeta(propId=5)
    
        public String getBindToken(){
            return _bindToken;
        }

        public void setBindToken(String value){
            this._bindToken = value;
        }


        private String _phone;

    
        @PropMeta(propId=6)
    
        public String getPhone(){
            return _phone;
        }

        public void setPhone(String value){
            this._phone = value;
        }


        private Long _lastVerifiedWindow;

    
        @PropMeta(propId=7)
    
        public Long getLastVerifiedWindow(){
            return _lastVerifiedWindow;
        }

        public void setLastVerifiedWindow(Long value){
            this._lastVerifiedWindow = value;
        }


        private java.sql.Timestamp _lastVerifiedAt;

    
        @PropMeta(propId=8)
    
        public java.sql.Timestamp getLastVerifiedAt(){
            return _lastVerifiedAt;
        }

        public void setLastVerifiedAt(java.sql.Timestamp value){
            this._lastVerifiedAt = value;
        }


        private Byte _delFlag;

    
        @PropMeta(propId=9)
    
        public Byte getDelFlag(){
            return _delFlag;
        }

        public void setDelFlag(Byte value){
            this._delFlag = value;
        }


        private String _tenantId;

    
        @PropMeta(propId=11)
    
        public String getTenantId(){
            return _tenantId;
        }

        public void setTenantId(String value){
            this._tenantId = value;
        }


        private String _remark;

    
        @PropMeta(propId=16)
    
        public String getRemark(){
            return _remark;
        }

        public void setRemark(String value){
            this._remark = value;
        }


        private Integer _totpFailCount;

    
        @PropMeta(propId=17)
    
        public Integer getTotpFailCount(){
            return _totpFailCount;
        }

        public void setTotpFailCount(Integer value){
            this._totpFailCount = value;
        }


        private java.sql.Timestamp _totpFailAt;

    
        @PropMeta(propId=18)
    
        public java.sql.Timestamp getTotpFailAt(){
            return _totpFailAt;
        }

        public void setTotpFailAt(java.sql.Timestamp value){
            this._totpFailAt = value;
        }


        private List<NopAuthMfaRecoveryCodeInputBean> _recoveryCodes;

        public List<NopAuthMfaRecoveryCodeInputBean> getRecoveryCodes(){
            return _recoveryCodes;
        }

        public void setRecoveryCodes(List<NopAuthMfaRecoveryCodeInputBean> value){
            this._recoveryCodes = value;
        }


    }
