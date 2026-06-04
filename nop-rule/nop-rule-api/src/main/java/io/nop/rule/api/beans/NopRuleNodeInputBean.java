//__XGEN_FORCE_OVERRIDE__
    package io.nop.rule.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    import java.util.List;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopRuleNodeInputBean extends CrudInputBase {

    
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


        private String _label;

    
        @PropMeta(propId=3)
    
        public String getLabel(){
            return _label;
        }

        public void setLabel(String value){
            this._label = value;
        }


        private Integer _sortNo;

    
        @PropMeta(propId=4)
    
        public Integer getSortNo(){
            return _sortNo;
        }

        public void setSortNo(Integer value){
            this._sortNo = value;
        }


        private String _predicate;

    
        @PropMeta(propId=5)
    
        public String getPredicate(){
            return _predicate;
        }

        public void setPredicate(String value){
            this._predicate = value;
        }


        private String _outputs;

    
        @PropMeta(propId=6)
    
        public String getOutputs(){
            return _outputs;
        }

        public void setOutputs(String value){
            this._outputs = value;
        }


        private String _parentId;

    
        @PropMeta(propId=7)
    
        public String getParentId(){
            return _parentId;
        }

        public void setParentId(String value){
            this._parentId = value;
        }


        private Boolean _isLeaf;

    
        @PropMeta(propId=8)
    
        public Boolean getIsLeaf(){
            return _isLeaf;
        }

        public void setIsLeaf(Boolean value){
            this._isLeaf = value;
        }


        private String _remark;

    
        @PropMeta(propId=14)
    
        public String getRemark(){
            return _remark;
        }

        public void setRemark(String value){
            this._remark = value;
        }


        private List<NopRuleNodeInputBean> _children;

        public List<NopRuleNodeInputBean> getChildren(){
            return _children;
        }

        public void setChildren(List<NopRuleNodeInputBean> value){
            this._children = value;
        }


    }
