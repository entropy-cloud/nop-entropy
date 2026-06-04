//__XGEN_FORCE_OVERRIDE__
    package io.nop.sys.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopSysClusterLeaderInputBean extends CrudInputBase {

    
        private String _clusterId;

    
        @PropMeta(propId=1)
    
        public String getClusterId(){
            return _clusterId;
        }

        public void setClusterId(String value){
            this._clusterId = value;
        }


        private String _leaderId;

    
        @PropMeta(propId=2)
    
        public String getLeaderId(){
            return _leaderId;
        }

        public void setLeaderId(String value){
            this._leaderId = value;
        }


        private String _leaderAdder;

    
        @PropMeta(propId=3)
    
        public String getLeaderAdder(){
            return _leaderAdder;
        }

        public void setLeaderAdder(String value){
            this._leaderAdder = value;
        }


        private Long _leaderEpoch;

    
        @PropMeta(propId=4)
    
        public Long getLeaderEpoch(){
            return _leaderEpoch;
        }

        public void setLeaderEpoch(Long value){
            this._leaderEpoch = value;
        }


        private java.sql.Timestamp _electTime;

    
        @PropMeta(propId=5)
    
        public java.sql.Timestamp getElectTime(){
            return _electTime;
        }

        public void setElectTime(java.sql.Timestamp value){
            this._electTime = value;
        }


        private java.sql.Timestamp _expireAt;

    
        @PropMeta(propId=6)
    
        public java.sql.Timestamp getExpireAt(){
            return _expireAt;
        }

        public void setExpireAt(java.sql.Timestamp value){
            this._expireAt = value;
        }


        private java.sql.Timestamp _refreshTime;

    
        @PropMeta(propId=7)
    
        public java.sql.Timestamp getRefreshTime(){
            return _refreshTime;
        }

        public void setRefreshTime(java.sql.Timestamp value){
            this._refreshTime = value;
        }


        private String _appName;

    
        @PropMeta(propId=9)
    
        public String getAppName(){
            return _appName;
        }

        public void setAppName(String value){
            this._appName = value;
        }


    }
