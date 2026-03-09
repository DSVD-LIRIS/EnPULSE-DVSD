# EnPULSE
**EnPULSE(Enabling Platform for User Logging and Sensing Environment)** is a sensor data collection platform for mobile and wrist-worn wearable device.

# Overview
EnPULSE consist of several core components, which can be used individually.
Together, they support end-to-end sensor data collection.

## Android Library
The library is capable of collecting various kinds of data from mobile and Galaxy Watch devices. It includes 21 sensors, with 5 of them (`AccelerometerSensor`, `PPGSensor`, `HeartRateSensor`, `SkinTemperatureSensor`, `EDASensor`) only usable on Galaxy Watch and `StepSensor` only usable on the Samsung mobile device.

## Mobile Tracker Application (For Samsung mobile devices)
## Wearable Tracker Application (For Galaxy Watch devices)
## Backend
## Dashboard

# Things to Download / Set Manually
## Samsung Health Sensor/Data SDK
The SDK is essential for collecting biosignals in real time from Galaxy Watch devices. Even if you are not going to use the feature, the library currently has dependency to the SDK so it should be configured.
1. Download the Samsung Health [Sensor SDK](https://developer.samsung.com/health/data/overview.html#SDK-download) and [Data SDK](https://developer.samsung.com/health/data/overview.html#SDK-download). Samsung account is required in this case.
2. Rename the downloaded `.aar` file to `samsung-health-sensor-api.aar` and `samsung-health-data-api.aar`.
3. Put the corresponding `.arr` files into `samsung-health-data-api` and `samsung-health-sensor-api` folder.

## Providing keys to access Supabase
Supabase annonymous keys and server address are used to join campaign and send data. In `local.properties`, set these 2 variables.

```
sdk.dir=C\:\\Your\\Android\\SDK\\directory
#Supabase key should go in here
SUPABASE_ANON_KEY=your_annoymous_key
SUPABASE_URL=your_self_hosted_supabase_server_address
```
* `SUPABASE_ANON_KEY`: The annonymous key can be found in the .env file in your supabase folder.
* `SUPABASE_URL`: The url is the same address that you access the supabase dashboard. Without manual setting, it uses port 8000.


