# BoM Weather Hubitat driver

Hubitat Driver for Australian weather data from your local weather station at the Bureau of Meteorology (BOM).

1. Add `bomdriver.groovy` to your Hubitat as a new Driver (under `Drivers Code`)
2. Add a new device for weather to your Hubitat, set device Type to your User driver of 'BoM Weather'
3. Configure your IDV and WMO numbers:
  1. Visit [BoM Observations](http://www.bom.gov.au/catalogue/data-feeds.shtml#obs-ind) and find your closest weather station
  2. Once you've browsed down to the weather station, your URL will look something like: `http://reg.bom.gov.au/products/IDV60901/IDV60901.94866.shtml`
  3. Copy `IDV60901` and paste it into the BoM IDV number field
  4. Copy `94866` and paste it into the BoM WMO number field
4. Set the polling interval... if the weather station only updates every 30mins, set it to `5,35` to refresh every xx:05 and xx:35 mark of each hour (the BoM hasn't always refreshed its data right on xx:00/xx:30, so you might miss it if you set it to that).
5. Enable auto poll, and enjoy :)

References:
Polling function modified from [Scottma61's WU driver](https://github.com/Scottma61/Hubitat/blob/master/Weather-Display%20WU%20Driver)