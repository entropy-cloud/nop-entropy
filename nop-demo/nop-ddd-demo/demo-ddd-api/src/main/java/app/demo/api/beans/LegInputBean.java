//__XGEN_FORCE_OVERRIDE__
    package app.demo.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class LegInputBean extends CrudInputBase {

    
        private Long _id;

    
        @PropMeta(propId=1)
    
        public Long getId(){
            return _id;
        }

        public void setId(Long value){
            this._id = value;
        }


        private java.time.LocalDateTime _loadTime;

    
        @PropMeta(propId=2)
    
        public java.time.LocalDateTime getLoadTime(){
            return _loadTime;
        }

        public void setLoadTime(java.time.LocalDateTime value){
            this._loadTime = value;
        }


        private java.time.LocalDateTime _unloadTime;

    
        @PropMeta(propId=3)
    
        public java.time.LocalDateTime getUnloadTime(){
            return _unloadTime;
        }

        public void setUnloadTime(java.time.LocalDateTime value){
            this._unloadTime = value;
        }


        private Long _loadLocationId;

    
        @PropMeta(propId=4)
    
        public Long getLoadLocationId(){
            return _loadLocationId;
        }

        public void setLoadLocationId(Long value){
            this._loadLocationId = value;
        }


        private Long _unloadLocationId;

    
        @PropMeta(propId=5)
    
        public Long getUnloadLocationId(){
            return _unloadLocationId;
        }

        public void setUnloadLocationId(Long value){
            this._unloadLocationId = value;
        }


        private Long _voyageId;

    
        @PropMeta(propId=6)
    
        public Long getVoyageId(){
            return _voyageId;
        }

        public void setVoyageId(Long value){
            this._voyageId = value;
        }


        private Long _cargoId;

    
        @PropMeta(propId=7)
    
        public Long getCargoId(){
            return _cargoId;
        }

        public void setCargoId(Long value){
            this._cargoId = value;
        }


    }
