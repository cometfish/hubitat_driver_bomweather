/*
 * BoMWeather driver
 *
 * Gets current Australian weather readings from the Bureau of Meteorology (BOM)
 *
 * Polling function modified from https://github.com/Scottma61/Hubitat/blob/master/Weather-Display%20WU%20Driver
 * 
 */
metadata {
    definition(name: "BoM Weather", namespace: "community", author: "cometfish") {
	    capability "Sensor"
        capability "TemperatureMeasurement"
		capability "Relative Humidity Measurement"
		
		attribute "temperature", "number"
		attribute "lastupdate", "date"
		attribute "apparent_temperature", "number"
		attribute "dew_point", "number"
		attribute "humidity", "number"
		attribute "windDirection", "number"
		attribute "windSpeed", "number"
		
		command "poll"
		command "refresh"
    }
}

preferences {
    section("URIs") {
		input "idv", "text", title: "Observation IDV number (eg. IDV60900)", required: true
        input "wmo", "text", title: "Observation WMO number for your local weather station (eg. 12345)", required: true
		input "autoPoll", "bool", required: true, title: "Enable Auto Poll", defaultValue: false
        input "pollInterval", "text", title: "Poll interval (which minutes of the hour to run on, eg. 5,35)", required: true, defaultValue: "5,35"
		input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
    }
}

def logsOff() {
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable", [value: "false", type: "bool"])
}

def updated() {
    log.info "updated..."
    log.warn "debug logging is: ${logEnable == true}"
    
	unschedule()
    if(autoPoll){
	    def pollIntervalCmd = (settings?.pollInterval)
        Random rand = new Random(now())
    	def randomSeconds = rand.nextInt(60)
        def sched = "${randomSeconds} 0/${pollIntervalCmd} * * * ?"
        schedule("${sched}", "refresh")
    }
    refresh()
}

def poll(){
    refresh()
}
def parse(String description) {
    if (logEnable) log.debug(description)
}

def refresh() {
    if (logEnable) log.debug "Refreshing data"

    try {
		def url = "http://reg.bom.gov.au/fwo/" + settings.idv + "/" + settings.idv + "." + settings.wmo + ".json"
		if (logEnable) log.debug url

        httpGet(["uri":url]) { resp ->
		    if (resp.success) {
				date = Date.parse("yyyyMMddHHmmssX", resp.data.observations.data[0].aifstime_utc+"Z")
				sendEvent(name: "lastupdate", value: date, isStateChange: true)

				sendEvent(name: "temperature", value: resp.data.observations.data[0].air_temp, unit: "°C", isStateChange: true)
				sendEvent(name: "apparent_temperature", value: resp.data.observations.data[0].apparent_t, unit: "°C", isStateChange: true)
				sendEvent(name: "dew_point", value: resp.data.observations.data[0].dewpt, unit: "°C", isStateChange: true)
				sendEvent(name: "humidity", value: resp.data.observations.data[0].rel_hum, unit: "%", isStateChange: true)
				deg = 0
				if (resp.data.observations.data[0].wind_dir=="NNE")
				    deg = 22.5;
				else if (resp.data.observations.data[0].wind_dir=="NE")
				    deg = 45;
				else if (resp.data.observations.data[0].wind_dir=="ENE")
				    deg = 67.5;
				else if (resp.data.observations.data[0].wind_dir=="E")
				    deg = 90;
				else if (resp.data.observations.data[0].wind_dir=="ESE")
				    deg = 112.5;
				else if (resp.data.observations.data[0].wind_dir=="SE")
				    deg = 135;
				else if (resp.data.observations.data[0].wind_dir=="SSE")
				    deg = 157.5;
				else if (resp.data.observations.data[0].wind_dir=="S")
				    deg = 180;
				else if (resp.data.observations.data[0].wind_dir=="SSW")
				    deg = 202.5;
				else if (resp.data.observations.data[0].wind_dir=="SW")
				    deg = 225;
				else if (resp.data.observations.data[0].wind_dir=="WSW")
				    deg = 247.5;
				else if (resp.data.observations.data[0].wind_dir=="W")
				    deg = 270;
				else if (resp.data.observations.data[0].wind_dir=="WNW")
				    deg = 292.5;
				else if (resp.data.observations.data[0].wind_dir=="NW")
				    deg = 315;
				else if (resp.data.observations.data[0].wind_dir=="NNW")
				    deg = 337.5;
				sendEvent(name: "windDirection", value: deg, unit:"°", isStateChange: true)
				sendEvent(name: "windSpeed", value: resp.data.observations.data[0].wind_spd_kmh,unit: "kmh", isStateChange: true)
            } else {
                log.debug "Error: ${resp}"
			}
        }
    } catch (Exception e) {
        log.warn "Refresh call failed: ${e.message}"
    }
}