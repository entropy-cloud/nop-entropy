//__XGEN_FORCE_OVERRIDE__
    package io.nop.rule.api.beans._gen;

    import io.nop.api.core.annotations.meta.PropMeta;

    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class _RuleMetaBean{

    
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
    
        private String _displayName;

        /**
         * 显示名称 
         */
        @PropMeta(propId=3,displayName="显示名称")
        public String getDisplayName(){
            return _displayName;
        }

        /**
         * 显示名称 
         */
        public void setDisplayName(String value){
            this._displayName = value;
        }
    
        private String _description;

        /**
         * 描述 
         */
        @PropMeta(propId=4,displayName="描述")
        public String getDescription(){
            return _description;
        }

        /**
         * 描述 
         */
        public void setDescription(String value){
            this._description = value;
        }
    
        private java.util.List<io.nop.api.core.beans.VarMetaBean> _inputs;

        /**
         * 输入数据类型 
         */
        @PropMeta(propId=5,displayName="输入数据类型")
        public java.util.List<io.nop.api.core.beans.VarMetaBean> getInputs(){
            return _inputs;
        }

        /**
         * 输入数据类型 
         */
        public void setInputs(java.util.List<io.nop.api.core.beans.VarMetaBean> value){
            this._inputs = value;
        }
    
        private java.util.List<io.nop.api.core.beans.VarMetaBean> _outputs;

        /**
         * 输出数据类型 
         */
        @PropMeta(propId=6,displayName="输出数据类型")
        public java.util.List<io.nop.api.core.beans.VarMetaBean> getOutputs(){
            return _outputs;
        }

        /**
         * 输出数据类型 
         */
        public void setOutputs(java.util.List<io.nop.api.core.beans.VarMetaBean> value){
            this._outputs = value;
        }
    
    }
