//__XGEN_FORCE_OVERRIDE__
    package io.nop.job.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopJobTaskInputBean extends CrudInputBase {

    
        private String _jobTaskId;

    
        @PropMeta(propId=1)
    
        public String getJobTaskId(){
            return _jobTaskId;
        }

        public void setJobTaskId(String value){
            this._jobTaskId = value;
        }


        private String _jobFireId;

    
        @PropMeta(propId=2)
    
        public String getJobFireId(){
            return _jobFireId;
        }

        public void setJobFireId(String value){
            this._jobFireId = value;
        }


        private Integer _taskNo;

    
        @PropMeta(propId=3)
    
        public Integer getTaskNo(){
            return _taskNo;
        }

        public void setTaskNo(Integer value){
            this._taskNo = value;
        }


        private String _taskPayload;

    
        @PropMeta(propId=7)
    
        public String getTaskPayload(){
            return _taskPayload;
        }

        public void setTaskPayload(String value){
            this._taskPayload = value;
        }


        private Short _partitionIndex;

    
        @PropMeta(propId=14)
    
        public Short getPartitionIndex(){
            return _partitionIndex;
        }

        public void setPartitionIndex(Short value){
            this._partitionIndex = value;
        }


        private String _remark;

    
        @PropMeta(propId=20)
    
        public String getRemark(){
            return _remark;
        }

        public void setRemark(String value){
            this._remark = value;
        }


        private Integer _progress;

    
        @PropMeta(propId=21)
    
        public Integer getProgress(){
            return _progress;
        }

        public void setProgress(Integer value){
            this._progress = value;
        }


        private String _progressMessage;

    
        @PropMeta(propId=22)
    
        public String getProgressMessage(){
            return _progressMessage;
        }

        public void setProgressMessage(String value){
            this._progressMessage = value;
        }


        private String _targetHost;

    
        @PropMeta(propId=23)
    
        public String getTargetHost(){
            return _targetHost;
        }

        public void setTargetHost(String value){
            this._targetHost = value;
        }


        private Integer _shardingIndex;

    
        @PropMeta(propId=24)
    
        public Integer getShardingIndex(){
            return _shardingIndex;
        }

        public void setShardingIndex(Integer value){
            this._shardingIndex = value;
        }


        private Integer _shardingTotal;

    
        @PropMeta(propId=25)
    
        public Integer getShardingTotal(){
            return _shardingTotal;
        }

        public void setShardingTotal(Integer value){
            this._shardingTotal = value;
        }


    }
