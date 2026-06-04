//__XGEN_FORCE_OVERRIDE__
    package app.demo.api.crud;

    import io.nop.api.core.annotations.biz.BizModel;
    import app.demo.api.beans.CarrierMovementInputBean;
    import app.demo.api.beans.CarrierMovementOutputBean;
    import io.nop.api.core.api.ICrudApi;
    

    @BizModel("CarrierMovement")
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public interface CarrierMovementApi extends ICrudApi<CarrierMovementInputBean, CarrierMovementOutputBean> {
    }
