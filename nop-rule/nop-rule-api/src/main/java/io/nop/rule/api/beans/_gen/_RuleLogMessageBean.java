//__XGEN_FORCE_OVERRIDE__
    package io.nop.rule.api.beans._gen;

    import io.nop.api.core.annotations.meta.PropMeta;

    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class _RuleLogMessageBean{

    
        private java.sql.Timestamp _logTime;

        /**
         * 日志时间 
         */
        @PropMeta(propId=1,displayName="日志时间")
        public java.sql.Timestamp getLogTime(){
            return _logTime;
        }

        /**
         * 日志时间 
         */
        public void setLogTime(java.sql.Timestamp value){
            this._logTime = value;
        }
    
        private String _message;

        /**
         * 消息 
         */
        @PropMeta(propId=2,displayName="消息")
        public String getMessage(){
            return _message;
        }

        /**
         * 消息 
         */
        public void setMessage(String value){
            this._message = value;
        }
    
        private String _ruleNodeId;

        /**
         * 规则节点Id 
         */
        @PropMeta(propId=3,displayName="规则节点Id")
        public String getRuleNodeId(){
            return _ruleNodeId;
        }

        /**
         * 规则节点Id 
         */
        public void setRuleNodeId(String value){
            this._ruleNodeId = value;
        }
    
        private String _ruleNodeLabel;

        /**
         * 规则节点标题 
         */
        @PropMeta(propId=4,displayName="规则节点标题")
        public String getRuleNodeLabel(){
            return _ruleNodeLabel;
        }

        /**
         * 规则节点标题 
         */
        public void setRuleNodeLabel(String value){
            this._ruleNodeLabel = value;
        }
    
    }
