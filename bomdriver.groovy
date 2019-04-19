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
		attribute "wind_dir", "string"
		attribute "wind_speed", "number"
		
		command "poll"
		command "refresh"
    }
}

preferences {
    section("URIs") {
		input "idv", "text", title: "BoM IDV number (eg. IDV60900)", required: true
        input "wmo", "text", title: "BoM WMO number for your local weather station (eg. 12345)", required: true
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
			    date = Date.parse("yyyyMMddhhmmss", resp.data.observations.data[0].local_date_time_full)
                sendEvent(name: "lastupdate", value: date, isStateChange: true)

				sendEvent(name: "temperature", value: resp.data.observations.data[0].air_temp, unit: "°C", isStateChange: true)
				sendEvent(name: "apparent_temperature", value: resp.data.observations.data[0].apparent_t, unit: "°C", isStateChange: true)
				sendEvent(name: "dew_point", value: resp.data.observations.data[0].dewpt, unit: "°C", isStateChange: true)
				sendEvent(name: "humidity", value: resp.data.observations.data[0].rel_hum, unit: "%", isStateChange: true)
				sendEvent(name: "wind_dir", value: resp.data.observations.data[0].wind_dir, isStateChange: true)
				sendEvent(name: "wind_speed", value: resp.data.observations.data[0].wind_spd_kmh,unit: "kmh", isStateChange: true)
            } else {
			    if (logEnable)
                    log.debug "Error: ${resp}"
			}
        }
    } catch (Exception e) {
        log.warn "Refresh call failed: ${e.message}"
    }
}