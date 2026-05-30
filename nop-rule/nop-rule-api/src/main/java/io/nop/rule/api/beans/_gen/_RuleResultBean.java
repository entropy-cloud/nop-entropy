//__XGEN_FORCE_OVERRIDE__
    package io.nop.rule.api.beans._gen;

    import io.nop.api.core.annotations.meta.PropMeta;
    import java.util.List;
    import java.util.Map;
    import java.util.Set;

    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class _RuleResultBean{

    
        private String _ruleName;

        /**
         * 规则名称 
         */
        @PropMeta(propId=1,displayName="规则名称")
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
        @PropMeta(propId=2,displayName="规则版本")
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
        @PropMeta(propId=3,displayName="是否匹配")
        public boolean getRuleMatch(){
            return _ruleMatch;
        }

        /**
         * 是否匹配 是否匹配了所有业务条件
         */
        public void setRuleMatch(boolean value){
            this._ruleMatch = value;
        }
    
        private Map<String,Object> _outputs;

        /**
         * 输出结果 
         */
        @PropMeta(propId=4,displayName="输出结果")
        public Map<String,Object> getOutputs(){
            return _outputs;
        }

        /**
         * 输出结果 
         */
        public void setOutputs(Map<String,Object> value){
            this._outputs = value;
        }
    
        private List _logMessages;

        /**
         * 日志消息 
         */
        @PropMeta(propId=5,displayName="日志消息")
        public List getLogMessages(){
            return _logMessages;
        }

        /**
         * 日志消息 
         */
        public void setLogMessages(List value){
            this._logMessages = value;
        }
    
    }
