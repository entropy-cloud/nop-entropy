//__XGEN_FORCE_OVERRIDE__
    package io.nop.rule.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopRuleLogInputBean extends CrudInputBase {

    
        private String _sid;

    
        @PropMeta(propId=1)
    
        public String getSid(){
            return _sid;
        }

        public void setSid(String value){
            this._sid = value;
        }


        private String _ruleId;

    
        @PropMeta(propId=2)
    
        public String getRuleId(){
            return _ruleId;
        }

        public void setRuleId(String value){
            this._ruleId = value;
        }


        private Integer _logLevel;

    
        @PropMeta(propId=3)
    
        public Integer getLogLevel(){
            return _logLevel;
        }

        public void setLogLevel(Integer value){
            this._logLevel = value;
        }


        private String _logMsg;

    
        @PropMeta(propId=4)
    
        public String getLogMsg(){
            return _logMsg;
        }

        public void setLogMsg(String value){
            this._logMsg = value;
        }


    }
