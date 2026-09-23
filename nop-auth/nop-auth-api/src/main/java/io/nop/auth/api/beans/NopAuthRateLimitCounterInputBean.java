//__XGEN_FORCE_OVERRIDE__
    package io.nop.auth.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopAuthRateLimitCounterInputBean extends CrudInputBase {

    
        private String _counterKey;

    
        @PropMeta(propId=1)
    
        public String getCounterKey(){
            return _counterKey;
        }

        public void setCounterKey(String value){
            this._counterKey = value;
        }


        private Long _counterCount;

    
        @PropMeta(propId=2)
    
        public Long getCounterCount(){
            return _counterCount;
        }

        public void setCounterCount(Long value){
            this._counterCount = value;
        }


        private Long _expireAt;

    
        @PropMeta(propId=3)
    
        public Long getExpireAt(){
            return _expireAt;
        }

        public void setExpireAt(Long value){
            this._expireAt = value;
        }


    }
