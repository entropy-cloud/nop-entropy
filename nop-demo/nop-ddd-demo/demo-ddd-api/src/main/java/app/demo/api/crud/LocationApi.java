//__XGEN_FORCE_OVERRIDE__
    package app.demo.api.crud;

    import io.nop.api.core.annotations.biz.BizModel;
    import app.demo.api.beans.LocationInputBean;
    import app.demo.api.beans.LocationOutputBean;
    import io.nop.api.core.api.ICrudApi;
    

    @BizModel("Location")
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public interface LocationApi extends ICrudApi<LocationInputBean, LocationOutputBean> {
    }
