//__XGEN_FORCE_OVERRIDE__
    package io.nop.auth.api.beans._gen;

    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class _NopAuthTenantInputBean extends CrudInputBase {

    
        private String _name;

        @PropMeta(propId=2)
        public String getName(){
            return _name;
        }

        public void setName(String value){
            this._name = value;
        }


        private java.time.LocalDateTime _beginTime;

        @PropMeta(propId=3)
        public java.time.LocalDateTime getBeginTime(){
            return _beginTime;
        }

        public void setBeginTime(java.time.LocalDateTime value){
            this._beginTime = value;
        }


        private java.time.LocalDateTime _endTime;

        @PropMeta(propId=4)
        public java.time.LocalDateTime getEndTime(){
            return _endTime;
        }

        public void setEndTime(java.time.LocalDateTime value){
            this._endTime = value;
        }


        private Integer _status;

        @PropMeta(propId=5)
        public Integer getStatus(){
            return _status;
        }

        public void setStatus(Integer value){
            this._status = value;
        }


        private Byte _delFlag;

        @PropMeta(propId=6)
        public Byte getDelFlag(){
            return _delFlag;
        }

        public void setDelFlag(Byte value){
            this._delFlag = value;
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
