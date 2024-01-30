//__XGEN_FORCE_OVERRIDE__
    package io.nop.rule.api.beans._gen;

    import io.nop.api.core.annotations.meta.PropMeta;

    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class _RuleRequestBean{

    
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
    
        private java.util.Map<java.lang.String,java.lang.Object> _inputs;

        /**
         * 输入参数 
         */
        @PropMeta(propId=3,displayName="输入参数")
        public java.util.Map<java.lang.String,java.lang.Object> getInputs(){
            return _inputs;
        }

        /**
         * 输入参数 
         */
        public void setInputs(java.util.Map<java.lang.String,java.lang.Object> value){
            this._inputs = value;
        }
    
    }
