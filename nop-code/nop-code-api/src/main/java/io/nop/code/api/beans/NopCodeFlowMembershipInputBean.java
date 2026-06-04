//__XGEN_FORCE_OVERRIDE__
    package io.nop.code.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopCodeFlowMembershipInputBean extends CrudInputBase {

    
        private String _id;

    
        @PropMeta(propId=1)
    
        public String getId(){
            return _id;
        }

        public void setId(String value){
            this._id = value;
        }


        private String _flowId;

    
        @PropMeta(propId=2)
    
        public String getFlowId(){
            return _flowId;
        }

        public void setFlowId(String value){
            this._flowId = value;
        }


        private String _symbolId;

    
        @PropMeta(propId=3)
    
        public String getSymbolId(){
            return _symbolId;
        }

        public void setSymbolId(String value){
            this._symbolId = value;
        }


        private Integer _depth;

    
        @PropMeta(propId=4)
    
        public Integer getDepth(){
            return _depth;
        }

        public void setDepth(Integer value){
            this._depth = value;
        }


        private Boolean _isEntry;

    
        @PropMeta(propId=5)
    
        public Boolean getIsEntry(){
            return _isEntry;
        }

        public void setIsEntry(Boolean value){
            this._isEntry = value;
        }


        private java.sql.Timestamp _createTime;

    
        @PropMeta(propId=6)
    
        public java.sql.Timestamp getCreateTime(){
            return _createTime;
        }

        public void setCreateTime(java.sql.Timestamp value){
            this._createTime = value;
        }


        private String _createdBy;

    
        @PropMeta(propId=7)
    
        public String getCreatedBy(){
            return _createdBy;
        }

        public void setCreatedBy(String value){
            this._createdBy = value;
        }


        private java.sql.Timestamp _updateTime;

    
        @PropMeta(propId=8)
    
        public java.sql.Timestamp getUpdateTime(){
            return _updateTime;
        }

        public void setUpdateTime(java.sql.Timestamp value){
            this._updateTime = value;
        }


        private String _updatedBy;

    
        @PropMeta(propId=9)
    
        public String getUpdatedBy(){
            return _updatedBy;
        }

        public void setUpdatedBy(String value){
            this._updatedBy = value;
        }


    }
