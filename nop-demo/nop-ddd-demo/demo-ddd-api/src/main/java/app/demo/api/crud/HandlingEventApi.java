//__XGEN_FORCE_OVERRIDE__
    package app.demo.api.crud;

    import io.nop.api.core.annotations.biz.BizModel;
    import app.demo.api.beans.HandlingEventInputBean;
    import app.demo.api.beans.HandlingEventOutputBean;
    import io.nop.api.core.api.ICrudApi;
    

    @BizModel("HandlingEvent")
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public interface HandlingEventApi extends ICrudApi<HandlingEventInputBean, HandlingEventOutputBean> {
    }
