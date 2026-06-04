//__XGEN_FORCE_OVERRIDE__
    package app.demo.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    
    import java.util.Map;

    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class CarrierMovementOutputBean {

    
        private Long _id;

    
        @PropMeta(propId=1)
    
        public Long getId(){
            return _id;
        }

        public void setId(Long value){
            this._id = value;
        }


        private java.time.LocalDateTime _arrivalTime;

    
        @PropMeta(propId=2)
    
        public java.time.LocalDateTime getArrivalTime(){
            return _arrivalTime;
        }

        public void setArrivalTime(java.time.LocalDateTime value){
            this._arrivalTime = value;
        }


        private java.time.LocalDateTime _departureTime;

    
        @PropMeta(propId=3)
    
        public java.time.LocalDateTime getDepartureTime(){
            return _departureTime;
        }

        public void setDepartureTime(java.time.LocalDateTime value){
            this._departureTime = value;
        }


        private Long _arrivalLocationId;

    
        @PropMeta(propId=4)
    
        public Long getArrivalLocationId(){
            return _arrivalLocationId;
        }

        public void setArrivalLocationId(Long value){
            this._arrivalLocationId = value;
        }


        private Long _departureLocationId;

    
        @PropMeta(propId=5)
    
        public Long getDepartureLocationId(){
            return _departureLocationId;
        }

        public void setDepartureLocationId(Long value){
            this._departureLocationId = value;
        }


        private Long _voyageId;

    
        @PropMeta(propId=6)
    
        public Long getVoyageId(){
            return _voyageId;
        }

        public void setVoyageId(Long value){
            this._voyageId = value;
        }


        private Map<String,Object> _voyage;

        public Map<String,Object> getVoyage(){
            return _voyage;
        }

        public void setVoyage(Map<String,Object> value){
            this._voyage = value;
        }


        private Map<String,Object> _departureLocation;

        public Map<String,Object> getDepartureLocation(){
            return _departureLocation;
        }

        public void setDepartureLocation(Map<String,Object> value){
            this._departureLocation = value;
        }


        private Map<String,Object> _arrivalLocation;

        public Map<String,Object> getArrivalLocation(){
            return _arrivalLocation;
        }

        public void setArrivalLocation(Map<String,Object> value){
            this._arrivalLocation = value;
        }


    }
