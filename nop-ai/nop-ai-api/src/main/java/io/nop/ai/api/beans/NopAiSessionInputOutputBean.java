//__XGEN_FORCE_OVERRIDE__
    package io.nop.ai.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    
    import java.util.Map;

    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopAiSessionInputOutputBean {

    
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


        private String _prompt;

    
        @PropMeta(propId=3)
    
        public String getPrompt(){
            return _prompt;
        }

        public void setPrompt(String value){
            this._prompt = value;
        }


        private Integer _delivery;

    
        @PropMeta(propId=4)
    
        public Integer getDelivery(){
            return _delivery;
        }

        public void setDelivery(Integer value){
            this._delivery = value;
        }


        private String _delivery_label;

    
        public String getDelivery_label(){
            return _delivery_label;
        }

        public void setDelivery_label(String value){
            this._delivery_label = value;
        }


        private Long _admittedSeq;

    
        @PropMeta(propId=5)
    
        public Long getAdmittedSeq(){
            return _admittedSeq;
        }

        public void setAdmittedSeq(Long value){
            this._admittedSeq = value;
        }


        private Long _promotedSeq;

    
        @PropMeta(propId=6)
    
        public Long getPromotedSeq(){
            return _promotedSeq;
        }

        public void setPromotedSeq(Long value){
            this._promotedSeq = value;
        }


        private Integer _version;

    
        @PropMeta(propId=7)
    
        public Integer getVersion(){
            return _version;
        }

        public void setVersion(Integer value){
            this._version = value;
        }


        private String _createdBy;

    
        @PropMeta(propId=8)
    
        public String getCreatedBy(){
            return _createdBy;
        }

        public void setCreatedBy(String value){
            this._createdBy = value;
        }


        private java.sql.Timestamp _createTime;

    
        @PropMeta(propId=9)
    
        public java.sql.Timestamp getCreateTime(){
            return _createTime;
        }

        public void setCreateTime(java.sql.Timestamp value){
            this._createTime = value;
        }


        private String _updatedBy;

    
        @PropMeta(propId=10)
    
        public String getUpdatedBy(){
            return _updatedBy;
        }

        public void setUpdatedBy(String value){
            this._updatedBy = value;
        }


        private java.sql.Timestamp _updateTime;

    
        @PropMeta(propId=11)
    
        public java.sql.Timestamp getUpdateTime(){
            return _updateTime;
        }

        public void setUpdateTime(java.sql.Timestamp value){
            this._updateTime = value;
        }


    }
