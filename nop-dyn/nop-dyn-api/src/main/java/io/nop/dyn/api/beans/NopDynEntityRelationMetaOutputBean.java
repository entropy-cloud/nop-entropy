//__XGEN_FORCE_OVERRIDE__
    package io.nop.dyn.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    
    import java.util.Map;

    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopDynEntityRelationMetaOutputBean {

    
        private String _relMetaId;

    
        @PropMeta(propId=1)
    
        public String getRelMetaId(){
            return _relMetaId;
        }

        public void setRelMetaId(String value){
            this._relMetaId = value;
        }


        private String _entityMetaId;

    
        @PropMeta(propId=2)
    
        public String getEntityMetaId(){
            return _entityMetaId;
        }

        public void setEntityMetaId(String value){
            this._entityMetaId = value;
        }


        private String _refEntityMetaId;

    
        @PropMeta(propId=3)
    
        public String getRefEntityMetaId(){
            return _refEntityMetaId;
        }

        public void setRefEntityMetaId(String value){
            this._refEntityMetaId = value;
        }


        private String _relationName;

    
        @PropMeta(propId=4)
    
        public String getRelationName(){
            return _relationName;
        }

        public void setRelationName(String value){
            this._relationName = value;
        }


        private String _relationDisplayName;

    
        @PropMeta(propId=5)
    
        public String getRelationDisplayName(){
            return _relationDisplayName;
        }

        public void setRelationDisplayName(String value){
            this._relationDisplayName = value;
        }


        private String _relationType;

    
        @PropMeta(propId=6)
    
        public String getRelationType(){
            return _relationType;
        }

        public void setRelationType(String value){
            this._relationType = value;
        }


        private String _relationType_label;

    
        public String getRelationType_label(){
            return _relationType_label;
        }

        public void setRelationType_label(String value){
            this._relationType_label = value;
        }


        private String _middleTableName;

    
        @PropMeta(propId=7)
    
        public String getMiddleTableName(){
            return _middleTableName;
        }

        public void setMiddleTableName(String value){
            this._middleTableName = value;
        }


        private String _middleEntityName;

    
        @PropMeta(propId=8)
    
        public String getMiddleEntityName(){
            return _middleEntityName;
        }

        public void setMiddleEntityName(String value){
            this._middleEntityName = value;
        }


        private String _leftPropName;

    
        @PropMeta(propId=9)
    
        public String getLeftPropName(){
            return _leftPropName;
        }

        public void setLeftPropName(String value){
            this._leftPropName = value;
        }


        private String _rightPropName;

    
        @PropMeta(propId=10)
    
        public String getRightPropName(){
            return _rightPropName;
        }

        public void setRightPropName(String value){
            this._rightPropName = value;
        }


        private String _refSetKeyProp;

    
        @PropMeta(propId=11)
    
        public String getRefSetKeyProp(){
            return _refSetKeyProp;
        }

        public void setRefSetKeyProp(String value){
            this._refSetKeyProp = value;
        }


        private String _refSetSort;

    
        @PropMeta(propId=12)
    
        public String getRefSetSort(){
            return _refSetSort;
        }

        public void setRefSetSort(String value){
            this._refSetSort = value;
        }


        private Integer _status;

    
        @PropMeta(propId=13)
    
        public Integer getStatus(){
            return _status;
        }

        public void setStatus(Integer value){
            this._status = value;
        }


        private String _tagsText;

    
        @PropMeta(propId=14)
    
        public String getTagsText(){
            return _tagsText;
        }

        public void setTagsText(String value){
            this._tagsText = value;
        }


        private String _extConfig;

    
        @PropMeta(propId=15)
    
        public String getExtConfig(){
            return _extConfig;
        }

        public void setExtConfig(String value){
            this._extConfig = value;
        }


        private Integer _version;

    
        @PropMeta(propId=16)
    
        public Integer getVersion(){
            return _version;
        }

        public void setVersion(Integer value){
            this._version = value;
        }


        private String _createdBy;

    
        @PropMeta(propId=17)
    
        public String getCreatedBy(){
            return _createdBy;
        }

        public void setCreatedBy(String value){
            this._createdBy = value;
        }


        private java.sql.Timestamp _createTime;

    
        @PropMeta(propId=18)
    
        public java.sql.Timestamp getCreateTime(){
            return _createTime;
        }

        public void setCreateTime(java.sql.Timestamp value){
            this._createTime = value;
        }


        private String _updatedBy;

    
        @PropMeta(propId=19)
    
        public String getUpdatedBy(){
            return _updatedBy;
        }

        public void setUpdatedBy(String value){
            this._updatedBy = value;
        }


        private java.sql.Timestamp _updateTime;

    
        @PropMeta(propId=20)
    
        public java.sql.Timestamp getUpdateTime(){
            return _updateTime;
        }

        public void setUpdateTime(java.sql.Timestamp value){
            this._updateTime = value;
        }


        private String _remark;

    
        @PropMeta(propId=21)
    
        public String getRemark(){
            return _remark;
        }

        public void setRemark(String value){
            this._remark = value;
        }


        private Map<String,Object> _entityMeta;

        public Map<String,Object> getEntityMeta(){
            return _entityMeta;
        }

        public void setEntityMeta(Map<String,Object> value){
            this._entityMeta = value;
        }


        private Map<String,Object> _refEntityMeta;

        public Map<String,Object> getRefEntityMeta(){
            return _refEntityMeta;
        }

        public void setRefEntityMeta(Map<String,Object> value){
            this._refEntityMeta = value;
        }


    }
