//__XGEN_FORCE_OVERRIDE__
    package app.demo.api.crud;

    import io.nop.api.core.annotations.biz.BizModel;
    import app.demo.api.beans.CargoInputBean;
    import app.demo.api.beans.CargoOutputBean;
    import io.nop.api.core.api.ICrudApi;
    

    @BizModel("Cargo")
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public interface CargoApi extends ICrudApi<CargoInputBean, CargoOutputBean> {
    }
