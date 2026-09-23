//__XGEN_FORCE_OVERRIDE__
    package io.nop.auth.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopAuthLoginAttemptInputBean extends CrudInputBase {

    
        private String _attemptKey;

    
        @PropMeta(propId=1)
    
        public String getAttemptKey(){
            return _attemptKey;
        }

        public void setAttemptKey(String value){
            this._attemptKey = value;
        }


        private Long _expireAt;

    
        @PropMeta(propId=2)
    
        public Long getExpireAt(){
            return _expireAt;
        }

        public void setExpireAt(Long value){
            this._expireAt = value;
        }


        private Integer _failCount;

    
        @PropMeta(propId=3)
    
        public Integer getFailCount(){
            return _failCount;
        }

        public void setFailCount(Integer value){
            this._failCount = value;
        }


    }
