//__XGEN_FORCE_OVERRIDE__
    package io.nop.wf.api.beans._gen;

    import io.nop.api.core.annotations.meta.PropMeta;
    import java.util.List;
    import java.util.Map;
    import java.util.Set;

    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class _WfTransferActorsRequestBean{

    
        private String _fromUserId;

        /**
         * 原处理人ID 
         */
        @PropMeta(propId=1,displayName="原处理人ID")
        public String getFromUserId(){
            return _fromUserId;
        }

        /**
         * 原处理人ID 
         */
        public void setFromUserId(String value){
            this._fromUserId = value;
        }
    
        private String _toUserId;

        /**
         * 目标处理人ID 
         */
        @PropMeta(propId=2,displayName="目标处理人ID")
        public String getToUserId(){
            return _toUserId;
        }

        /**
         * 目标处理人ID 
         */
        public void setToUserId(String value){
            this._toUserId = value;
        }
    
        private Set<String> _wfIds;

        /**
         * 限定工作流ID集合 
         */
        @PropMeta(propId=3,displayName="限定工作流ID集合")
        public Set<String> getWfIds(){
            return _wfIds;
        }

        /**
         * 限定工作流ID集合 
         */
        public void setWfIds(Set<String> value){
            this._wfIds = value;
        }
    
    }
