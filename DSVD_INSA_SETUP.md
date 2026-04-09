# DSVD INSA — Setup Notes

## Origin

This repository is a fork of [EnPULSE](https://github.com/Kaist-ICLab/EnPULSE), an Android passive sensing platform developed by the ICLab at KAIST. The original project collects sensor data from Android phones and Wear OS watches and uploads it to a Supabase backend via Google authentication.

## Architecture overview

The application authenticates users through **Google Sign-In** using Android's Credential Manager API. The flow is:

1. The Android app requests a Google ID token, using a **web OAuth client ID** as the audience (`serverClientId`).
2. The ID token is sent to **Supabase**, which verifies it against the same web client configured in its Google Auth provider settings.
3. On success, Supabase creates a session and the app proceeds to data collection.

The original repository was wired to the KAIST ICLab's own Firebase/Google Cloud project and Supabase instance. This fork replaces those with our own infrastructure.

## What was changed

### 1. Supabase project (`local.properties`)

`local.properties` is not committed (see `.gitignore`). Create it at the repository root with the following content:

```
sdk.dir=/path/to/your/Android/Sdk
SUPABASE_URL=https://<your-project-ref>.supabase.co
SUPABASE_ANON_KEY=<your-anon-key>
```

Both values are available in the Supabase dashboard under **Project Settings → API**.

### 2. Google OAuth client (`app-mobile-tracker/src/main/res/values/strings.xml`)

The `default_web_client_id` string was updated from the original KAIST web client to our own Google Cloud web application OAuth client:

```
657299009238-fc7uodi1sbk5k8lodma0hk7t6sn0fq2h.apps.googleusercontent.com
```

This client is configured in the Supabase dashboard under **Authentication → Providers → Google** (Client ID + Client Secret).

### 3. Firebase/Google Services plugin removed (`app-mobile-tracker/build.gradle.kts`)

The original project used the `com.google.gms.google-services` Gradle plugin to process `google-services.json`. That file belonged to the original authors' Firebase project and was generating a `default_web_client_id` resource that overrode the value in `strings.xml`. Since this application does not use any Firebase SDK — only Supabase — the plugin was removed.

### 4. New debug keystore (`app-mobile-tracker/debug.keystore`)

Google requires the Android OAuth client and the web OAuth client to be registered in the **same Google Cloud project**. The original `debug.keystore` had a SHA-1 fingerprint already registered in the KAIST project, making it impossible to register in ours.

A new `debug.keystore` was generated (standard Android debug credentials: password `android`, alias `androiddebugkey`). Its SHA-1 fingerprint is:

```
E8:31:1D:04:3B:BF:EA:8A:C6:26:EC:01:CD:35:75:03:8B:74:E0:16
```

This keystore is committed to the repository. An **Android OAuth client** has already been registered in our Google Cloud project (`657299009238`) for this SHA-1 and package name. Anyone building from this repository uses the same keystore and therefore the same registered identity — no additional GCP configuration is needed.

## Building and installing the app

The only prerequisite is `local.properties`. Create it at the repository root:

```
sdk.dir=/path/to/your/Android/Sdk
SUPABASE_URL=https://gmcltrmblcgwldluvmmn.supabase.co
SUPABASE_ANON_KEY=<anon key — ask a project maintainer>
```

Then build and install:

```bash
./gradlew installDebug
```

## Setting up entirely new infrastructure

If you need to deploy your own Supabase instance and Google Cloud project (rather than using the existing one), the steps are:

1. Create a new Supabase project.
2. In **Authentication → Providers → Google**, enable Google.
3. In Google Cloud Console, create a **Web application** OAuth client. Copy its Client ID and Client Secret into Supabase.
4. Update `default_web_client_id` in `app-mobile-tracker/src/main/res/values/strings.xml` (and `values-ko/strings.xml`) to the new web client ID.
5. Generate a new `debug.keystore` (the SHA-1 of the existing one is already claimed by another project):
   ```bash
   keytool -genkey -v -keystore app-mobile-tracker/debug.keystore \
     -storepass android -alias androiddebugkey -keypass android \
     -keyalg RSA -keysize 2048 -validity 10000 \
     -dname "CN=Android Debug,O=Android,C=US"
   ```
6. Get its SHA-1: `keytool -list -v -keystore app-mobile-tracker/debug.keystore -storepass android`
7. In Google Cloud Console, create an **Android** OAuth client with package name `kaist.iclab.trackerSystem` and that SHA-1.
8. Uninstall any previous version from the device before installing (`adb uninstall kaist.iclab.trackerSystem`), as the signing certificate will have changed.
