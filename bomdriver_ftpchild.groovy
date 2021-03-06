/*
 * BoMWeather FTP child driver
 *
 * Retrieving Forecast data requires two telnet connections at once - need a child device to create the second connection.
 *
 */
metadata {
    definition (name: "BoM Weather Forecasts FTP component", namespace: "community", author: "cometfish", importUrl: "https://raw.githubusercontent.com/cometfish/hubitat_driver_bomweather/master/bomdriver_ftpchild.groovy") {
        capability "Telnet"
	
        attribute "ip", "string"
		attribute "port", "number"
		attribute "logEnable", "bool"
		
		command "connect"
    }
}

preferences {
    section("URIs") {
		
    }
}

void connect() { 
    if (state.logEnable) log.debug "$device connecting"
	try {
    	telnetConnect([termChars:[10]], state.ip, state.port.toInteger(), "", "")
	} catch (Exception e) {
		//could not connect - tell parent to reset, otherwise we're stuck waiting for file indefinitely
		log.error "Connection to download file failed: ${e.message}"
		parent.ftpChildFailed();
	}
}
void setIP(ipaddr) {
	if (state.logEnable) log.debug "set IP to $ipaddr"
	state.ip = ipaddr
}
void setPort(prt) {
	if (state.logEnable) log.debug "set port to $prt"
	state.port = prt
}
void setLogEnable(enable) {
	log.debug "set log enable to $enable"
	state.logEnable = enable
}

def parse(String description) {
	//if (state.logEnable) log.debug "child received $description"
	//send the file data up to the parent
	parent.childParse(description)	
}

def telnetStatus(String status){
	//child connection is automatically disconnected by host when done
	//so it's not an error
	//log.warn "child telnetStatus: " + status
}