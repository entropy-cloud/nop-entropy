//__XGEN_FORCE_OVERRIDE__
    package io.nop.dyn.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    
    import java.util.Map;

    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopDynEntityRelationOutputBean {

    
        private String _sid;

    
        @PropMeta(propId=1)
    
        public String getSid(){
            return _sid;
        }

        public void setSid(String value){
            this._sid = value;
        }


        private String _relationName;

    
        @PropMeta(propId=2)
    
        public String getRelationName(){
            return _relationName;
        }

        public void setRelationName(String value){
            this._relationName = value;
        }


        private String _entityName1;

    
        @PropMeta(propId=3)
    
        public String getEntityName1(){
            return _entityName1;
        }

        public void setEntityName1(String value){
            this._entityName1 = value;
        }


        private String _entityId1;

    
        @PropMeta(propId=4)
    
        public String getEntityId1(){
            return _entityId1;
        }

        public void setEntityId1(String value){
            this._entityId1 = value;
        }


        private String _entityName2;

    
        @PropMeta(propId=5)
    
        public String getEntityName2(){
            return _entityName2;
        }

        public void setEntityName2(String value){
            this._entityName2 = value;
        }


        private String _entityId2;

    
        @PropMeta(propId=6)
    
        public String getEntityId2(){
            return _entityId2;
        }

        public void setEntityId2(String value){
            this._entityId2 = value;
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


        private String _remark;

    
        @PropMeta(propId=12)
    
        public String getRemark(){
            return _remark;
        }

        public void setRemark(String value){
            this._remark = value;
        }


    }
