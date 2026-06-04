//__XGEN_FORCE_OVERRIDE__
    package io.nop.rule.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import java.util.List;
    
    import java.util.Map;

    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopRuleNodeOutputBean {

    
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


        private java.util.Map _predicateMap;

    
        public java.util.Map getPredicateMap(){
            return _predicateMap;
        }

        public void setPredicateMap(java.util.Map value){
            this._predicateMap = value;
        }


        private String _predicateLabel;

    
        public String getPredicateLabel(){
            return _predicateLabel;
        }

        public void setPredicateLabel(String value){
            this._predicateLabel = value;
        }


        private String _outputs;

    
        @PropMeta(propId=6)
    
        public String getOutputs(){
            return _outputs;
        }

        public void setOutputs(String value){
            this._outputs = value;
        }


        private Object _outputsMap;

    
        public Object getOutputsMap(){
            return _outputsMap;
        }

        public void setOutputsMap(Object value){
            this._outputsMap = value;
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


        private Integer _version;

    
        @PropMeta(propId=9)
    
        public Integer getVersion(){
            return _version;
        }

        public void setVersion(Integer value){
            this._version = value;
        }


        private String _createdBy;

    
        @PropMeta(propId=10)
    
        public String getCreatedBy(){
            return _createdBy;
        }

        public void setCreatedBy(String value){
            this._createdBy = value;
        }


        private java.sql.Timestamp _createTime;

    
        @PropMeta(propId=11)
    
        public java.sql.Timestamp getCreateTime(){
            return _createTime;
        }

        public void setCreateTime(java.sql.Timestamp value){
            this._createTime = value;
        }


        private String _updatedBy;

    
        @PropMeta(propId=12)
    
        public String getUpdatedBy(){
            return _updatedBy;
        }

        public void setUpdatedBy(String value){
            this._updatedBy = value;
        }


        private java.sql.Timestamp _updateTime;

    
        @PropMeta(propId=13)
    
        public java.sql.Timestamp getUpdateTime(){
            return _updateTime;
        }

        public void setUpdateTime(java.sql.Timestamp value){
            this._updateTime = value;
        }


        private String _remark;

    
        @PropMeta(propId=14)
    
        public String getRemark(){
            return _remark;
        }

        public void setRemark(String value){
            this._remark = value;
        }


        private Map<String,Object> _parent;

        public Map<String,Object> getParent(){
            return _parent;
        }

        public void setParent(Map<String,Object> value){
            this._parent = value;
        }


        private Map<String,Object> _ruleDefinition;

        public Map<String,Object> getRuleDefinition(){
            return _ruleDefinition;
        }

        public void setRuleDefinition(Map<String,Object> value){
            this._ruleDefinition = value;
        }


        private List<Map<String,Object>> _children;

        public List<Map<String,Object>> getChildren(){
            return _children;
        }

        public void setChildren(List<Map<String,Object>> value){
            this._children = value;
        }


    }
