# BoM Weather Hubitat driver

Hubitat Driver for Australian weather data (observations and forecasts) from your local weather station at the Bureau of Meteorology (BOM).

1. Add `bomdriver.groovy` and `bomdriver_ftpchild.groovy` to your Hubitat as two new Drivers (under `Drivers Code`)
2. Add a new device for weather to your Hubitat, set device Type to your User driver of 'BoM Weather'
3. Configure your ID and WMO numbers:
    1. Visit [BoM Observations](http://www.bom.gov.au/catalogue/data-feeds.shtml#obs-ind) and find your closest weather station
    2. Once you've browsed down to the weather station, your URL will look something like: `http://reg.bom.gov.au/products/IDV60901/IDV60901.94866.shtml`
    3. Copy `IDV60901` and paste it into the Observation ID number field
    4. Copy `94866` and paste it into the Observation WMO number field
	5. Visit [BoM Weather Data Feeds - Long form](http://www.bom.gov.au/catalogue/data-feeds.shtml#forecasts) and find your state, then click on the XML link under Precis
	6. Your URL will look something like: `ftp://ftp.bom.gov.au/anon/gen/fwo/IDV10753.xml`
	7. Copy `IDV10753` and paste it into the Forecast Precis ID number field
	8. Search the XML file for your local station (it's a large file, use text search for eg. "Melbourne" or "Tullamarine"), and you should find the line that looks something like `<area aac="VIC_PT042" description="Melbourne" type="location" parent-aac="VIC_PW007">`
	9. Copy `VIC_PT042` and paste it into the Forecast Precis AAC number field (make sure you copy the `aac` not the `parent-aac` value)
4. Set the polling interval... if the weather station only updates every 30mins, set it to `5,35` to refresh every xx:05 and xx:35 mark of each hour (the BoM hasn't always refreshed its data right on xx:00/xx:30, so you might miss it if you set it to that). Note that forecasts are only updated twice a day, and will only update (on the times you've set) once the 'forecastnextdate' has passed.
5. Enable auto poll, and enjoy :)

References:
Polling function modified from [Scottma61's WU driver](https://github.com/Scottma61/Hubitat/blob/master/Weather-Display%20WU%20Driver)