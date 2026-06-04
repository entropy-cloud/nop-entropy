//__XGEN_FORCE_OVERRIDE__
    package app.demo.api.crud;

    import io.nop.api.core.annotations.biz.BizModel;
    import app.demo.api.beans.LegInputBean;
    import app.demo.api.beans.LegOutputBean;
    import io.nop.api.core.api.ICrudApi;
    

    @BizModel("Leg")
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public interface LegApi extends ICrudApi<LegInputBean, LegOutputBean> {
    }
