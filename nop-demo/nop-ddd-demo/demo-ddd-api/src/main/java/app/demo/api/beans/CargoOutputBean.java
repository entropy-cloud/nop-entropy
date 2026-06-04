//__XGEN_FORCE_OVERRIDE__
    package app.demo.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    
    import java.util.Map;

    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class CargoOutputBean {

    
        private Long _id;

    
        @PropMeta(propId=1)
    
        public Long getId(){
            return _id;
        }

        public void setId(Long value){
            this._id = value;
        }


        private java.time.LocalDateTime _calculatedAt;

    
        @PropMeta(propId=2)
    
        public java.time.LocalDateTime getCalculatedAt(){
            return _calculatedAt;
        }

        public void setCalculatedAt(java.time.LocalDateTime value){
            this._calculatedAt = value;
        }


        private java.time.LocalDateTime _eta;

    
        @PropMeta(propId=3)
    
        public java.time.LocalDateTime getEta(){
            return _eta;
        }

        public void setEta(java.time.LocalDateTime value){
            this._eta = value;
        }


        private Boolean _unloadedAtDest;

    
        @PropMeta(propId=4)
    
        public Boolean getUnloadedAtDest(){
            return _unloadedAtDest;
        }

        public void setUnloadedAtDest(Boolean value){
            this._unloadedAtDest = value;
        }


        private Boolean _misdirected;

    
        @PropMeta(propId=5)
    
        public Boolean getMisdirected(){
            return _misdirected;
        }

        public void setMisdirected(Boolean value){
            this._misdirected = value;
        }


        private String _nextExpectedHandlingEventType;

    
        @PropMeta(propId=6)
    
        public String getNextExpectedHandlingEventType(){
            return _nextExpectedHandlingEventType;
        }

        public void setNextExpectedHandlingEventType(String value){
            this._nextExpectedHandlingEventType = value;
        }


        private String _routingStatus;

    
        @PropMeta(propId=7)
    
        public String getRoutingStatus(){
            return _routingStatus;
        }

        public void setRoutingStatus(String value){
            this._routingStatus = value;
        }


        private String _transportStatus;

    
        @PropMeta(propId=8)
    
        public String getTransportStatus(){
            return _transportStatus;
        }

        public void setTransportStatus(String value){
            this._transportStatus = value;
        }


        private java.time.LocalDateTime _specArrivalDeadline;

    
        @PropMeta(propId=9)
    
        public java.time.LocalDateTime getSpecArrivalDeadline(){
            return _specArrivalDeadline;
        }

        public void setSpecArrivalDeadline(java.time.LocalDateTime value){
            this._specArrivalDeadline = value;
        }


        private String _trackingId;

    
        @PropMeta(propId=10)
    
        public String getTrackingId(){
            return _trackingId;
        }

        public void setTrackingId(String value){
            this._trackingId = value;
        }


        private Long _currentVoyageId;

    
        @PropMeta(propId=11)
    
        public Long getCurrentVoyageId(){
            return _currentVoyageId;
        }

        public void setCurrentVoyageId(Long value){
            this._currentVoyageId = value;
        }


        private Long _lastEventId;

    
        @PropMeta(propId=12)
    
        public Long getLastEventId(){
            return _lastEventId;
        }

        public void setLastEventId(Long value){
            this._lastEventId = value;
        }


        private Long _lastKnownLocationId;

    
        @PropMeta(propId=13)
    
        public Long getLastKnownLocationId(){
            return _lastKnownLocationId;
        }

        public void setLastKnownLocationId(Long value){
            this._lastKnownLocationId = value;
        }


        private Long _nextExpectedLocationId;

    
        @PropMeta(propId=14)
    
        public Long getNextExpectedLocationId(){
            return _nextExpectedLocationId;
        }

        public void setNextExpectedLocationId(Long value){
            this._nextExpectedLocationId = value;
        }


        private Long _nextExpectedVoyageId;

    
        @PropMeta(propId=15)
    
        public Long getNextExpectedVoyageId(){
            return _nextExpectedVoyageId;
        }

        public void setNextExpectedVoyageId(Long value){
            this._nextExpectedVoyageId = value;
        }


        private Long _originId;

    
        @PropMeta(propId=16)
    
        public Long getOriginId(){
            return _originId;
        }

        public void setOriginId(Long value){
            this._originId = value;
        }


        private Long _specDestinationId;

    
        @PropMeta(propId=17)
    
        public Long getSpecDestinationId(){
            return _specDestinationId;
        }

        public void setSpecDestinationId(Long value){
            this._specDestinationId = value;
        }


        private Long _specOriginId;

    
        @PropMeta(propId=18)
    
        public Long getSpecOriginId(){
            return _specOriginId;
        }

        public void setSpecOriginId(Long value){
            this._specOriginId = value;
        }


        private Map<String,Object> _nextExpectedVoyage;

        public Map<String,Object> getNextExpectedVoyage(){
            return _nextExpectedVoyage;
        }

        public void setNextExpectedVoyage(Map<String,Object> value){
            this._nextExpectedVoyage = value;
        }


        private Map<String,Object> _specDestination;

        public Map<String,Object> getSpecDestination(){
            return _specDestination;
        }

        public void setSpecDestination(Map<String,Object> value){
            this._specDestination = value;
        }


        private Map<String,Object> _origin;

        public Map<String,Object> getOrigin(){
            return _origin;
        }

        public void setOrigin(Map<String,Object> value){
            this._origin = value;
        }


        private Map<String,Object> _lastEvent;

        public Map<String,Object> getLastEvent(){
            return _lastEvent;
        }

        public void setLastEvent(Map<String,Object> value){
            this._lastEvent = value;
        }


        private Map<String,Object> _lastKnownLocation;

        public Map<String,Object> getLastKnownLocation(){
            return _lastKnownLocation;
        }

        public void setLastKnownLocation(Map<String,Object> value){
            this._lastKnownLocation = value;
        }


        private Map<String,Object> _currentVoyage;

        public Map<String,Object> getCurrentVoyage(){
            return _currentVoyage;
        }

        public void setCurrentVoyage(Map<String,Object> value){
            this._currentVoyage = value;
        }


        private Map<String,Object> _nextExpectedLocation;

        public Map<String,Object> getNextExpectedLocation(){
            return _nextExpectedLocation;
        }

        public void setNextExpectedLocation(Map<String,Object> value){
            this._nextExpectedLocation = value;
        }


        private Map<String,Object> _specOrigin;

        public Map<String,Object> getSpecOrigin(){
            return _specOrigin;
        }

        public void setSpecOrigin(Map<String,Object> value){
            this._specOrigin = value;
        }


    }
