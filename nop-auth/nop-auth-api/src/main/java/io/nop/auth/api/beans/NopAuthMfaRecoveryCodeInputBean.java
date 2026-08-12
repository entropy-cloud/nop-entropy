//__XGEN_FORCE_OVERRIDE__
    package io.nop.auth.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopAuthMfaRecoveryCodeInputBean extends CrudInputBase {

    
        private String _sid;

    
        @PropMeta(propId=1)
    
        public String getSid(){
            return _sid;
        }

        public void setSid(String value){
            this._sid = value;
        }


        private String _userId;

    
        @PropMeta(propId=2)
    
        public String getUserId(){
            return _userId;
        }

        public void setUserId(String value){
            this._userId = value;
        }


        private String _codeHash;

    
        @PropMeta(propId=3)
    
        public String getCodeHash(){
            return _codeHash;
        }

        public void setCodeHash(String value){
            this._codeHash = value;
        }


        private Byte _used;

    
        @PropMeta(propId=4)
    
        public Byte getUsed(){
            return _used;
        }

        public void setUsed(Byte value){
            this._used = value;
        }


        private java.sql.Timestamp _usedAt;

    
        @PropMeta(propId=5)
    
        public java.sql.Timestamp getUsedAt(){
            return _usedAt;
        }

        public void setUsedAt(java.sql.Timestamp value){
            this._usedAt = value;
        }


        private java.sql.Timestamp _expireAt;

    
        @PropMeta(propId=6)
    
        public java.sql.Timestamp getExpireAt(){
            return _expireAt;
        }

        public void setExpireAt(java.sql.Timestamp value){
            this._expireAt = value;
        }


        private Byte _delFlag;

    
        @PropMeta(propId=7)
    
        public Byte getDelFlag(){
            return _delFlag;
        }

        public void setDelFlag(Byte value){
            this._delFlag = value;
        }


    }
