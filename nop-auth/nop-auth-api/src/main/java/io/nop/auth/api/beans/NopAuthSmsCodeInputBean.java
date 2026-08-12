//__XGEN_FORCE_OVERRIDE__
    package io.nop.auth.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopAuthSmsCodeInputBean extends CrudInputBase {

    
        private String _codeKey;

    
        @PropMeta(propId=1)
    
        public String getCodeKey(){
            return _codeKey;
        }

        public void setCodeKey(String value){
            this._codeKey = value;
        }


        private String _phone;

    
        @PropMeta(propId=2)
    
        public String getPhone(){
            return _phone;
        }

        public void setPhone(String value){
            this._phone = value;
        }


        private String _code;

    
        @PropMeta(propId=3)
    
        public String getCode(){
            return _code;
        }

        public void setCode(String value){
            this._code = value;
        }


        private Long _expireAt;

    
        @PropMeta(propId=4)
    
        public Long getExpireAt(){
            return _expireAt;
        }

        public void setExpireAt(Long value){
            this._expireAt = value;
        }


        private Integer _failCount;

    
        @PropMeta(propId=5)
    
        public Integer getFailCount(){
            return _failCount;
        }

        public void setFailCount(Integer value){
            this._failCount = value;
        }


    }
