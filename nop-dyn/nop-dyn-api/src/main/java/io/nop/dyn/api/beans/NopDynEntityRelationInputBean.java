//__XGEN_FORCE_OVERRIDE__
    package io.nop.dyn.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopDynEntityRelationInputBean extends CrudInputBase {

    
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


        private String _remark;

    
        @PropMeta(propId=12)
    
        public String getRemark(){
            return _remark;
        }

        public void setRemark(String value){
            this._remark = value;
        }


    }
