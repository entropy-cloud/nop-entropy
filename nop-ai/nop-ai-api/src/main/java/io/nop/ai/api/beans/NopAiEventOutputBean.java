//__XGEN_FORCE_OVERRIDE__
    package io.nop.ai.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    
    import java.util.Map;

    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopAiEventOutputBean {

    
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


        private Long _seq;

    
        @PropMeta(propId=3)
    
        public Long getSeq(){
            return _seq;
        }

        public void setSeq(Long value){
            this._seq = value;
        }


        private Integer _eventType;

    
        @PropMeta(propId=4)
    
        public Integer getEventType(){
            return _eventType;
        }

        public void setEventType(Integer value){
            this._eventType = value;
        }


        private String _eventType_label;

    
        public String getEventType_label(){
            return _eventType_label;
        }

        public void setEventType_label(String value){
            this._eventType_label = value;
        }


        private String _data;

    
        @PropMeta(propId=5)
    
        public String getData(){
            return _data;
        }

        public void setData(String value){
            this._data = value;
        }


        private String _createdBy;

    
        @PropMeta(propId=6)
    
        public String getCreatedBy(){
            return _createdBy;
        }

        public void setCreatedBy(String value){
            this._createdBy = value;
        }


        private java.sql.Timestamp _createTime;

    
        @PropMeta(propId=7)
    
        public java.sql.Timestamp getCreateTime(){
            return _createTime;
        }

        public void setCreateTime(java.sql.Timestamp value){
            this._createTime = value;
        }


    }
