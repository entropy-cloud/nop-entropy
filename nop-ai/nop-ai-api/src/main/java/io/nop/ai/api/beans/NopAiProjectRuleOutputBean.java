//__XGEN_FORCE_OVERRIDE__
    package io.nop.ai.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    
    import java.util.Map;

    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopAiProjectRuleOutputBean {

    
        private String _id;

    
        @PropMeta(propId=1)
    
        public String getId(){
            return _id;
        }

        public void setId(String value){
            this._id = value;
        }


        private String _projectId;

    
        @PropMeta(propId=2)
    
        public String getProjectId(){
            return _projectId;
        }

        public void setProjectId(String value){
            this._projectId = value;
        }


        private String _knowledgeId;

    
        @PropMeta(propId=3)
    
        public String getKnowledgeId(){
            return _knowledgeId;
        }

        public void setKnowledgeId(String value){
            this._knowledgeId = value;
        }


        private String _ruleName;

    
        @PropMeta(propId=4)
    
        public String getRuleName(){
            return _ruleName;
        }

        public void setRuleName(String value){
            this._ruleName = value;
        }


        private String _ruleContent;

    
        @PropMeta(propId=5)
    
        public String getRuleContent(){
            return _ruleContent;
        }

        public void setRuleContent(String value){
            this._ruleContent = value;
        }


        private String _ruleType;

    
        @PropMeta(propId=6)
    
        public String getRuleType(){
            return _ruleType;
        }

        public void setRuleType(String value){
            this._ruleType = value;
        }


        private String _ruleType_label;

    
        public String getRuleType_label(){
            return _ruleType_label;
        }

        public void setRuleType_label(String value){
            this._ruleType_label = value;
        }


        private Boolean _isActive;

    
        @PropMeta(propId=7)
    
        public Boolean getIsActive(){
            return _isActive;
        }

        public void setIsActive(Boolean value){
            this._isActive = value;
        }


        private Integer _version;

    
        @PropMeta(propId=8)
    
        public Integer getVersion(){
            return _version;
        }

        public void setVersion(Integer value){
            this._version = value;
        }


        private String _createdBy;

    
        @PropMeta(propId=9)
    
        public String getCreatedBy(){
            return _createdBy;
        }

        public void setCreatedBy(String value){
            this._createdBy = value;
        }


        private java.sql.Timestamp _createTime;

    
        @PropMeta(propId=10)
    
        public java.sql.Timestamp getCreateTime(){
            return _createTime;
        }

        public void setCreateTime(java.sql.Timestamp value){
            this._createTime = value;
        }


        private String _updatedBy;

    
        @PropMeta(propId=11)
    
        public String getUpdatedBy(){
            return _updatedBy;
        }

        public void setUpdatedBy(String value){
            this._updatedBy = value;
        }


        private java.sql.Timestamp _updateTime;

    
        @PropMeta(propId=12)
    
        public java.sql.Timestamp getUpdateTime(){
            return _updateTime;
        }

        public void setUpdateTime(java.sql.Timestamp value){
            this._updateTime = value;
        }


    }
