//__XGEN_FORCE_OVERRIDE__
    package io.nop.wf.api.beans._gen;

    import io.nop.api.core.annotations.meta.PropMeta;
    import java.util.List;
    import java.util.Map;
    import java.util.Set;

    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class _WfTransferResultBean{

    
        private int _successCount;

        /**
         * 成功数量 
         */
        @PropMeta(propId=1,displayName="成功数量")
        public int getSuccessCount(){
            return _successCount;
        }

        /**
         * 成功数量 
         */
        public void setSuccessCount(int value){
            this._successCount = value;
        }
    
        private List _failedItems;

        /**
         * 失败明细 
         */
        @PropMeta(propId=2,displayName="失败明细")
        public List getFailedItems(){
            return _failedItems;
        }

        /**
         * 失败明细 
         */
        public void setFailedItems(List value){
            this._failedItems = value;
        }
    
    }
