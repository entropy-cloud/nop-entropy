//__XGEN_FORCE_OVERRIDE__
    package io.nop.rule.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    
    import java.util.Map;

    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopRuleLogOutputBean {

    
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


        private String _logLevel_label;

    
        public String getLogLevel_label(){
            return _logLevel_label;
        }

        public void setLogLevel_label(String value){
            this._logLevel_label = value;
        }


        private String _logMsg;

    
        @PropMeta(propId=4)
    
        public String getLogMsg(){
            return _logMsg;
        }

        public void setLogMsg(String value){
            this._logMsg = value;
        }


        private String _createdBy;

    
        @PropMeta(propId=5)
    
        public String getCreatedBy(){
            return _createdBy;
        }

        public void setCreatedBy(String value){
            this._createdBy = value;
        }


        private java.sql.Timestamp _createTime;

    
        @PropMeta(propId=6)
    
        public java.sql.Timestamp getCreateTime(){
            return _createTime;
        }

        public void setCreateTime(java.sql.Timestamp value){
            this._createTime = value;
        }


        private Map<String,Object> _ruleDefinition;

        public Map<String,Object> getRuleDefinition(){
            return _ruleDefinition;
        }

        public void setRuleDefinition(Map<String,Object> value){
            this._ruleDefinition = value;
        }


    }
