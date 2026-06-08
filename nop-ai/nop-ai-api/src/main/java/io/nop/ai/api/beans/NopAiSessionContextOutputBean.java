//__XGEN_FORCE_OVERRIDE__
    package io.nop.ai.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    
    import java.util.Map;

    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopAiSessionContextOutputBean {

    
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


        private String _baseline;

    
        @PropMeta(propId=3)
    
        public String getBaseline(){
            return _baseline;
        }

        public void setBaseline(String value){
            this._baseline = value;
        }


        private String _snapshot;

    
        @PropMeta(propId=4)
    
        public String getSnapshot(){
            return _snapshot;
        }

        public void setSnapshot(String value){
            this._snapshot = value;
        }


        private Long _baselineSeq;

    
        @PropMeta(propId=5)
    
        public Long getBaselineSeq(){
            return _baselineSeq;
        }

        public void setBaselineSeq(Long value){
            this._baselineSeq = value;
        }


        private Long _replacementSeq;

    
        @PropMeta(propId=6)
    
        public Long getReplacementSeq(){
            return _replacementSeq;
        }

        public void setReplacementSeq(Long value){
            this._replacementSeq = value;
        }


        private Integer _revision;

    
        @PropMeta(propId=7)
    
        public Integer getRevision(){
            return _revision;
        }

        public void setRevision(Integer value){
            this._revision = value;
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
