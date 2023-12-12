//__XGEN_FORCE_OVERRIDE__
    package io.nop.rule.api.beans._gen;

    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class _RuleResultBean{

    
        private String _ruleName;

        /**
         * 规则名称 
         */
        public String getRuleName(){
            return _ruleName;
        }

        /**
         * 规则名称 
         */
        public void setRuleName(String value){
            this._ruleName = value;
        }
    
        private Long _ruleVersion;

        /**
         * 规则版本 
         */
        public Long getRuleVersion(){
            return _ruleVersion;
        }

        /**
         * 规则版本 
         */
        public void setRuleVersion(Long value){
            this._ruleVersion = value;
        }
    
        private boolean _ruleMatch;

        /**
         * 是否匹配 是否匹配了所有业务条件
         */
        public boolean getRuleMatch(){
            return _ruleMatch;
        }

        /**
         * 是否匹配 是否匹配了所有业务条件
         */
        public void setRuleMatch(boolean value){
            this._ruleMatch = value;
        }
    
        private java.util.Map<java.lang.String,java.lang.Object> _outputs;

        /**
         * 输出结果 
         */
        public java.util.Map<java.lang.String,java.lang.Object> getOutputs(){
            return _outputs;
        }

        /**
         * 输出结果 
         */
        public void setOutputs(java.util.Map<java.lang.String,java.lang.Object> value){
            this._outputs = value;
        }
    
        private java.util.List<io.nop.rule.api.beans.RuleLogMessageBean> _logMessages;

        /**
         * 日志消息 
         */
        public java.util.List<io.nop.rule.api.beans.RuleLogMessageBean> getLogMessages(){
            return _logMessages;
        }

        /**
         * 日志消息 
         */
        public void setLogMessages(java.util.List<io.nop.rule.api.beans.RuleLogMessageBean> value){
            this._logMessages = value;
        }
    
    }
