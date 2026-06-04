//__XGEN_FORCE_OVERRIDE__
    package io.nop.task.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    import java.util.List;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopTaskDefinitionInputBean extends CrudInputBase {

    
        private String _taskDefId;

    
        @PropMeta(propId=1)
    
        public String getTaskDefId(){
            return _taskDefId;
        }

        public void setTaskDefId(String value){
            this._taskDefId = value;
        }


        private String _taskName;

    
        @PropMeta(propId=2)
    
        public String getTaskName(){
            return _taskName;
        }

        public void setTaskName(String value){
            this._taskName = value;
        }


        private Long _taskVersion;

    
        @PropMeta(propId=3)
    
        public Long getTaskVersion(){
            return _taskVersion;
        }

        public void setTaskVersion(Long value){
            this._taskVersion = value;
        }


        private String _displayName;

    
        @PropMeta(propId=4)
    
        public String getDisplayName(){
            return _displayName;
        }

        public void setDisplayName(String value){
            this._displayName = value;
        }


        private String _description;

    
        @PropMeta(propId=5)
    
        public String getDescription(){
            return _description;
        }

        public void setDescription(String value){
            this._description = value;
        }


        private String _modelText;

    
        @PropMeta(propId=6)
    
        public String getModelText(){
            return _modelText;
        }

        public void setModelText(String value){
            this._modelText = value;
        }


        private Integer _status;

    
        @PropMeta(propId=7)
    
        public Integer getStatus(){
            return _status;
        }

        public void setStatus(Integer value){
            this._status = value;
        }


        private String _remark;

    
        @PropMeta(propId=13)
    
        public String getRemark(){
            return _remark;
        }

        public void setRemark(String value){
            this._remark = value;
        }


        private List<NopTaskDefinitionAuthInputBean> _definitionAuths;

        public List<NopTaskDefinitionAuthInputBean> getDefinitionAuths(){
            return _definitionAuths;
        }

        public void setDefinitionAuths(List<NopTaskDefinitionAuthInputBean> value){
            this._definitionAuths = value;
        }


    }
