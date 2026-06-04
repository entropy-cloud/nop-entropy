//__XGEN_FORCE_OVERRIDE__
    package app.demo.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    
    import java.util.Map;

    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class HandlingEventOutputBean {

    
        private Long _id;

    
        @PropMeta(propId=1)
    
        public Long getId(){
            return _id;
        }

        public void setId(Long value){
            this._id = value;
        }


        private java.time.LocalDateTime _completionTime;

    
        @PropMeta(propId=2)
    
        public java.time.LocalDateTime getCompletionTime(){
            return _completionTime;
        }

        public void setCompletionTime(java.time.LocalDateTime value){
            this._completionTime = value;
        }


        private java.time.LocalDateTime _registrationTime;

    
        @PropMeta(propId=3)
    
        public java.time.LocalDateTime getRegistrationTime(){
            return _registrationTime;
        }

        public void setRegistrationTime(java.time.LocalDateTime value){
            this._registrationTime = value;
        }


        private String _type;

    
        @PropMeta(propId=4)
    
        public String getType(){
            return _type;
        }

        public void setType(String value){
            this._type = value;
        }


        private Long _cargoId;

    
        @PropMeta(propId=5)
    
        public Long getCargoId(){
            return _cargoId;
        }

        public void setCargoId(Long value){
            this._cargoId = value;
        }


        private Long _locationId;

    
        @PropMeta(propId=6)
    
        public Long getLocationId(){
            return _locationId;
        }

        public void setLocationId(Long value){
            this._locationId = value;
        }


        private Long _voyageId;

    
        @PropMeta(propId=7)
    
        public Long getVoyageId(){
            return _voyageId;
        }

        public void setVoyageId(Long value){
            this._voyageId = value;
        }


        private Map<String,Object> _cargo;

        public Map<String,Object> getCargo(){
            return _cargo;
        }

        public void setCargo(Map<String,Object> value){
            this._cargo = value;
        }


        private Map<String,Object> _voyage;

        public Map<String,Object> getVoyage(){
            return _voyage;
        }

        public void setVoyage(Map<String,Object> value){
            this._voyage = value;
        }


        private Map<String,Object> _location;

        public Map<String,Object> getLocation(){
            return _location;
        }

        public void setLocation(Map<String,Object> value){
            this._location = value;
        }


    }
