//__XGEN_FORCE_OVERRIDE__
    package io.nop.ai.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    
    import java.util.Map;

    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopAiSessionMessageOutputBean {

    
        private String _id;

    
        @PropMeta(propId=1)
    
        public String getId(){
            return _id;
        }

        public void setId(String value){
            this._id = value;
        }


        private String _sessionId;

    
        @PropMeta(propId=2)
    
        public String getSessionId(){
            return _sessionId;
        }

        public void setSessionId(String value){
            this._sessionId = value;
        }


        private Integer _role;

    
        @PropMeta(propId=3)
    
        public Integer getRole(){
            return _role;
        }

        public void setRole(Integer value){
            this._role = value;
        }


        private String _role_label;

    
        public String getRole_label(){
            return _role_label;
        }

        public void setRole_label(String value){
            this._role_label = value;
        }


        private Long _seq;

    
        @PropMeta(propId=4)
    
        public Long getSeq(){
            return _seq;
        }

        public void setSeq(Long value){
            this._seq = value;
        }


        private String _content;

    
        @PropMeta(propId=5)
    
        public String getContent(){
            return _content;
        }

        public void setContent(String value){
            this._content = value;
        }


        private String _toolDetails;

    
        @PropMeta(propId=6)
    
        public String getToolDetails(){
            return _toolDetails;
        }

        public void setToolDetails(String value){
            this._toolDetails = value;
        }


        private String _reasoning;

    
        @PropMeta(propId=7)
    
        public String getReasoning(){
            return _reasoning;
        }

        public void setReasoning(String value){
            this._reasoning = value;
        }


        private String _metadata;

    
        @PropMeta(propId=8)
    
        public String getMetadata(){
            return _metadata;
        }

        public void setMetadata(String value){
            this._metadata = value;
        }


        private String _parentId;

    
        @PropMeta(propId=9)
    
        public String getParentId(){
            return _parentId;
        }

        public void setParentId(String value){
            this._parentId = value;
        }


        private String _finishReason;

    
        @PropMeta(propId=10)
    
        public String getFinishReason(){
            return _finishReason;
        }

        public void setFinishReason(String value){
            this._finishReason = value;
        }


        private Integer _version;

    
        @PropMeta(propId=11)
    
        public Integer getVersion(){
            return _version;
        }

        public void setVersion(Integer value){
            this._version = value;
        }


        private String _createdBy;

    
        @PropMeta(propId=12)
    
        public String getCreatedBy(){
            return _createdBy;
        }

        public void setCreatedBy(String value){
            this._createdBy = value;
        }


        private java.sql.Timestamp _createTime;

    
        @PropMeta(propId=13)
    
        public java.sql.Timestamp getCreateTime(){
            return _createTime;
        }

        public void setCreateTime(java.sql.Timestamp value){
            this._createTime = value;
        }


        private String _updatedBy;

    
        @PropMeta(propId=14)
    
        public String getUpdatedBy(){
            return _updatedBy;
        }

        public void setUpdatedBy(String value){
            this._updatedBy = value;
        }


        private java.sql.Timestamp _updateTime;

    
        @PropMeta(propId=15)
    
        public java.sql.Timestamp getUpdateTime(){
            return _updateTime;
        }

        public void setUpdateTime(java.sql.Timestamp value){
            this._updateTime = value;
        }


    }
