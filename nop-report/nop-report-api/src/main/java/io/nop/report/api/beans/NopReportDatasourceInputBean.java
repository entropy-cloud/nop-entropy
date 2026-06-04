//__XGEN_FORCE_OVERRIDE__
    package io.nop.report.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    import java.util.List;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopReportDatasourceInputBean extends CrudInputBase {

    
        private String _sid;

    
        @PropMeta(propId=1)
    
        public String getSid(){
            return _sid;
        }

        public void setSid(String value){
            this._sid = value;
        }


        private String _name;

    
        @PropMeta(propId=2)
    
        public String getName(){
            return _name;
        }

        public void setName(String value){
            this._name = value;
        }


        private String _datasourceType;

    
        @PropMeta(propId=3)
    
        public String getDatasourceType(){
            return _datasourceType;
        }

        public void setDatasourceType(String value){
            this._datasourceType = value;
        }


        private String _datasourceConfig;

    
        @PropMeta(propId=4)
    
        public String getDatasourceConfig(){
            return _datasourceConfig;
        }

        public void setDatasourceConfig(String value){
            this._datasourceConfig = value;
        }


        private Integer _status;

    
        @PropMeta(propId=5)
    
        public Integer getStatus(){
            return _status;
        }

        public void setStatus(Integer value){
            this._status = value;
        }


        private String _remark;

    
        @PropMeta(propId=6)
    
        public String getRemark(){
            return _remark;
        }

        public void setRemark(String value){
            this._remark = value;
        }


        private List<NopReportDatasourceAuthInputBean> _datasourceAuths;

        public List<NopReportDatasourceAuthInputBean> getDatasourceAuths(){
            return _datasourceAuths;
        }

        public void setDatasourceAuths(List<NopReportDatasourceAuthInputBean> value){
            this._datasourceAuths = value;
        }


    }
