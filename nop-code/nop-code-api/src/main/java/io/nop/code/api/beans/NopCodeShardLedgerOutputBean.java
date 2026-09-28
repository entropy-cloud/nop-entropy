//__XGEN_FORCE_OVERRIDE__
    package io.nop.code.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    
    import java.util.Map;

    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopCodeShardLedgerOutputBean {

    
        private String _id;

    
        @PropMeta(propId=1)
    
        public String getId(){
            return _id;
        }

        public void setId(String value){
            this._id = value;
        }


        private String _indexId;

    
        @PropMeta(propId=2)
    
        public String getIndexId(){
            return _indexId;
        }

        public void setIndexId(String value){
            this._indexId = value;
        }


        private String _shardId;

    
        @PropMeta(propId=3)
    
        public String getShardId(){
            return _shardId;
        }

        public void setShardId(String value){
            this._shardId = value;
        }


        private String _status;

    
        @PropMeta(propId=4)
    
        public String getStatus(){
            return _status;
        }

        public void setStatus(String value){
            this._status = value;
        }


        private java.sql.Timestamp _completedAt;

    
        @PropMeta(propId=5)
    
        public java.sql.Timestamp getCompletedAt(){
            return _completedAt;
        }

        public void setCompletedAt(java.sql.Timestamp value){
            this._completedAt = value;
        }


    }
