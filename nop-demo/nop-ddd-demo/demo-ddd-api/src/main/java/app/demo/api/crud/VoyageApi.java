//__XGEN_FORCE_OVERRIDE__
    package app.demo.api.crud;

    import io.nop.api.core.annotations.biz.BizModel;
    import app.demo.api.beans.VoyageInputBean;
    import app.demo.api.beans.VoyageOutputBean;
    import io.nop.api.core.api.ICrudApi;
    

    @BizModel("Voyage")
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public interface VoyageApi extends ICrudApi<VoyageInputBean, VoyageOutputBean> {
    }
