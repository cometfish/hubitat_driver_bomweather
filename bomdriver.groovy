/*
 * BoMWeather driver
 *
 * Gets current Australian weather info (observations and forecasts) from the Bureau of Meteorology (BOM)
 * 
 */
metadata {
    definition(name: "BoM Weather", namespace: "community", author: "cometfish") {
	    capability "Sensor"
        capability "TemperatureMeasurement"
		capability "Relative Humidity Measurement"
		capability "Telnet"
		
		//observations
		attribute "temperature", "number"
		attribute "lastupdate", "date"
		attribute "apparent_temperature", "number"
		attribute "dew_point", "number"
		attribute "humidity", "number"
		attribute "windDirection", "number"
		attribute "windSpeed", "number"
		
		//forecast
		attribute "area", "string"
		attribute "iconCode", "number"
		attribute "weatherIcon", "string"
		attribute "forecastlastupdate", "date"
		attribute "forecastnextupdate", "date"
		attribute "weather", "string"
		attribute "rainProbability", "number"
		attribute "rainRange", "string"
		attribute "forecastLow", "number"
		attribute "forecastHigh", "number"
		attribute "tile", "string"
		
		command "poll"
		command "refresh"
		command "clearForecastNextUpdate"
        command "resetFTPStatus"
		command "updateTile"
    }
}

preferences {
    section("URIs") {
		input "idv", "text", title: "Observation IDV number (eg. IDV60901)", required: true
        input "wmo", "text", title: "Observation WMO number for your local weather station (eg. 95936)", required: true
        
		input "forecastidv", "text", title: "Forecast Precis IDV number (eg. IDV10753)", required: true
        input "aac", "text", title: "Forecast Precis AAC code for your local weather station (eg. VIC_PT042)", required: true
        
        input "iconcustomurl", "text", title: "Icon custom url (Leave blank for default. Include trailing slash)", required: false
		
		input "autoPoll", "bool", required: true, title: "Enable Auto Poll", defaultValue: false
        input "pollInterval", "text", title: "Poll interval (which minutes of the hour to run on, eg. 5,35)", required: true, defaultValue: "5,35"
		input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
    }
}

import groovy.transform.Field
@Field static StringBuilder file

def installed() {
	state.ftpstatus = "idle"
	createChildDevice()
}

def recreateChildDevice() {
    if (logEnable) log.debug "recreateChildDevice"
    deleteChild()
    createChildDevice()
}
def deleteChild() {
	if (logEnable) log.debug "deleteChild"
	def children = getChildDevice()
    
    children.each {child->
  		deleteChildDevice(child.deviceNetworkId)
    }
}
private void createChildDevice() {
    if (logEnable) log.debug "createChildDevice"
    
	addChildDevice("community", "BoM Weather Forecasts FTP component", "$device.deviceNetworkId-ftp", [name: "BoMFTPComponent", label: "$device.displayName FTP", isComponent: true])
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
}

def poll(){
    refresh()
}

def clearForecastNextUpdate() {
	sendEvent(name: "forecastnextupdate", value: new Date(), isStateChange: true)
}

def refresh() {
    if (logEnable) log.debug "Refreshing data"
	
	//forecast (do these first, because it will take longer to load)
	try {
		nextupdate = Date.parseToStringDate(device.currentValue("forecastnextupdate"))

		if (nextupdate == null || nextupdate<(new Date())) {
            if (state.ftpstatus!="idle")
                log.error "Past forecast update time, but FTP is still in use."
            else
                telnetConnect("ftp.bom.gov.au", 21, "", "")
		} else {
			if (logEnable)
				log.info "Forecast data still current, will not refresh until: "+nextupdate
			
			//check if we need to switch tile
			icontype = "day"
	        nowdate = new Date()
	        if (nowdate<location.sunrise || nowdate>=location.sunset)
		        icontype = "night"
			if (state.icontype != icontype) {
				state.icontype = icontype
				iconCodeStr = device.currentValue("iconCode").toString()
				
			    iconurl = settings.iconcustomurl
                if (iconurl==null || iconurl=='')
                    iconurl = "https://raw.githubusercontent.com/cometfish/hubitat_driver_bomweather/master/images/monochrome/"
                wIcon = "${iconurl}${iconCodeStr}${icontype}.png"
                sendEvent(name: "weatherIcon", value: wIcon, isStateChange: true)
	
				vals = [:]
                vals.weatherIcon = device.currentValue("weatherIcon")
                vals.weather = device.currentValue("weather")
                vals.forecastHigh=device.currentValue("forecastHigh")
                vals.temperature=device.currentValue("temperature")
                vals.apparent_temperature=device.currentValue("apparent_temperature")
                vals.windDirection = device.currentValue("windDirection")
                vals.windSpeed = device.currentValue("windSpeed")
				updateTileWithVals(vals) 
			}
		}
	} catch (Exception e) {
		log.warn "Refresh call forecasts failed: ${e.message}"
	}

    try {
		//observations
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
				vals = [:]
                vals.weatherIcon = device.currentValue("weatherIcon")
                vals.weather = device.currentValue("weather")
                vals.forecastHigh=device.currentValue("forecastHigh")
                vals.temperature=resp.data.observations.data[0].air_temp
                vals.apparent_temperature=resp.data.observations.data[0].apparent_t
                vals.windDirection = deg
                vals.windSpeed = resp.data.observations.data[0].wind_spd_kmh
				updateTileWithVals(vals)
				
            } else {
                log.debug "Error: ${resp}"
			}
        }
    } catch (Exception e) {
        log.warn "Refresh call failed: ${e.message}"
    }
}

def parse(String description) {
    if (logEnable) log.debug(description)
	if (description.substring(0,4)=="220 ")
	{
		state.ftpstatus = "connectionopen"
		if (logEnable) log.debug("status: ${state.ftpstatus}")
		sendMsg("USER anonymous")
	} else if (description.substring(0,4)=="220-")
	{
		//sending info messages, but we are waiting for the last one which has no dash(above)
	} else if (description.substring(0,4)=="331 ")
	{
		state.ftpstatus = "usersent"
		if (logEnable) log.debug("status: ${state.ftpstatus}")
		sendMsg("PASS")
	} else if (description.substring(0,4)=="230 ")
	{
		state.ftpstatus = "loggedin"
		if (logEnable) log.debug("status: ${state.ftpstatus}")
		sendMsg("PASV")
	} else if (description.substring(0,4)=="227 ")
	{
		state.ftpstatus = "gettingfilesize"
		if (logEnable) log.debug("status: ${state.ftpstatus}")
		parts = description.split("\\(")
		nums = parts[1].split("\\,")
		state.ipaddr = "${nums[0]}.${nums[1]}.${nums[2]}.${nums[3]}"
		if (logEnable) log.debug(state.ipaddr)
		state.port = nums[4].toInteger()*256+nums[5].split("\\)")[0].toInteger()
		if (logEnable) log.debug(state.port)
		sendMsg("SIZE anon/gen/fwo/"+settings.forecastidv+".xml")
	} else if (description.substring(0,4)=="213 ")
	{
		state.ftpstatus = "downloadingfile"
		if (logEnable) log.debug("status: ${state.ftpstatus}")
		state.filesize = description.split(" ")[1].toInteger()
		file = new StringBuilder(state.filesize)
		def children = getChildDevices()
		children.each {child->
			child.setLogEnable(logEnable)
			child.setIP(state.ipaddr)
			child.setPort(state.port)
			child.connect()
		}
		sendMsg("RETR anon/gen/fwo/"+settings.forecastidv+".xml")
		//(second session gets file)
	} else if (description.substring(0,4)=="226 ")
	{
		//226 Transfer complete
		//but we're still waiting for the data to finish coming through!
		//so don't update status, but we can close the control connection
		sendMsg("QUIT")
		telnetClose()
	} else if (description.substring(0,4)=="150 ")
	{
		//150 Opening BINARY mode data connection for (filename)
		//ignore
	} else {
		if (logEnable) log.debug("unexpected msg: ${description}")
	}
}
def childParse(String description) {
	if (state.ftpstatus=="downloadingfile") {
		//if (logEnable) log.debug("part of file received: ${description}")
		file.append(description)
		file.append("\n")
	    if (file.length()>=state.filesize) {
			//finished
			state.ftpstatus = "downloadfinished"
			if (logEnable) log.debug("File download finished")
			state.ipaddr = ""
			state.port = 0
			readXMLData()
		}
	} else {
		log.warn("unexpected msg from child: ${description}")	
	}
}

private readXMLData() {
	state.ftpstatus = "parsingxml"
	if (logEnable) log.debug("status: ${state.ftpstatus}")
	fcdata = parseXML(file.toString())
	state.filesize = 0
	file = new StringBuilder()
	state.ftpstatus = "idle"
	if (logEnable) log.debug("status: ${state.ftpstatus}")
	
	date = Date.parse("yyyy-MM-dd'T'HH:mm:ssX", fcdata.amoc.'issue-time-utc'.text())
    sendEvent(name: "forecastlastupdate", value: date, isStateChange: true)
	date = Date.parse("yyyy-MM-dd'T'HH:mm:ssX", fcdata.amoc.'next-routine-issue-time-utc'.text())
    sendEvent(name: "forecastnextupdate", value: date, isStateChange: true)
	local = fcdata.forecast.area.find{it.@aac == settings.aac}
	sendEvent(name: "area", value: local.@description, isStateChange: true)
	today = local.'forecast-period'.find{it.@index == "0"}
				
	el = today.'**'.find{it.@type == 'precis'}
	w = el.text()
	sendEvent(name: "weather", value: w,  isStateChange: true)
	el = today.'**'.find{it.@type == 'probability_of_precipitation'}
	sendEvent(name: "rainProbability", value: el.text().substring(0,el.text().length()-1).toInteger(), unit: "%", isStateChange: true)
	el = today.element.find{it.@type == 'precipitation_range'}
	sendEvent(name: "rainRange", value: el.text(), isStateChange: true)
	el = today.element.find{it.@type == 'air_temperature_minimum'}
	sendEvent(name: "forecastLow", value: el.text(), unit: "°C", isStateChange: true)
	el = today.element.find{it.@type == 'air_temperature_maximum'}
	fhigh=el.text()
	sendEvent(name: "forecastHigh", value: fhigh, unit: "°C", isStateChange: true)
	
	el = today.element.find{it.@type == 'forecast_icon_code'}
	iconCodeStr = el.text()
	sendEvent(name: "iconCode", value: iconCodeStr.toInteger(), isStateChange: true)
	icontype = "day"
	nowdate = new Date()
	if (nowdate<location.sunrise || nowdate>=location.sunset)
		icontype = "night"
	state.icontype = icontype
    iconurl = settings.iconcustomurl
    if (iconurl==null || iconurl=='')
        iconurl = "https://raw.githubusercontent.com/cometfish/hubitat_driver_bomweather/master/images/monochrome/"
	wIcon = "${iconurl}${iconCodeStr}${icontype}.png"
	sendEvent(name: "weatherIcon", value: wIcon, isStateChange: true)
	sendEvent(name: "tile", value: "<br /><img src=\"" + wIcon + "\" /><br />" + w, isStateChange: true)
	
	vals = [:]
	vals.weatherIcon = wIcon
	vals.weather = w
	vals.forecastHigh=fHigh
	vals.temperature=device.currentValue("temperature")
	vals.apparent_temperature=device.currentValue("apparent_temperature")
	vals.windDirection = device.currentValue("windDirection")
	vals.windSpeed = device.currentValue("windSpeed")
	updateTileWithVals(vals) 
}

def resetFTPStatus() {
    telnetClose()
    state.filesize = 0
	file = new StringBuilder()
    state.ftpstatus = "idle"
}

def updateTile() {
	vals = [:]
	vals.weatherIcon = device.currentValue("weatherIcon")
	vals.weather = device.currentValue("weather")
	vals.forecastHigh=device.currentValue("forecastHigh")
	vals.temperature=device.currentValue("temperature")
	vals.apparent_temperature=device.currentValue("apparent_temperature")
	vals.windDirection = device.currentValue("windDirection")
	vals.windSpeed = device.currentValue("windSpeed")
	
	updateTileWithVals(vals)
	
}
def updateTileWithVals(vals) {
	sendEvent(name: "tile", value: """
<style>.wfc {display:inline-block;padding:0 8px;font-size:12px;}
span.v {font-size:20px;}
</style>
<div id="bomw">
<div class="wfc today">
Today<br>
<img src="${vals.weatherIcon}" /><br>
${vals.weather}<br>
Max: ${vals.forecastHigh}&deg;C
</div>
<br>
<div class="wfc"><span class="v">${vals.temperature}&deg;C</span><br>
Currently
</div><div class="wfc"><span class="v">${vals.apparent_temperature}&deg;C</span><br>
Feels like
</div><div class="wfc">
<span class="v"><div style="transform:rotate(${vals.windDirection}deg);display:inline-block;font-weight:bold;">&uarr;</div> ${vals.windSpeed}km/h</span><br>
Wind
</div>
</div>""", isStateChange: true)
}

def sendMsg(msg) {
	if (logEnable) log.debug(msg)
	new hubitat.device.HubAction(msg, hubitat.device.Protocol.TELNET)
}
def telnetStatus(String status){
	log.warn "telnetStatus: " + status
}