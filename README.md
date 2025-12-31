<center>
<img src="Eclypses.png" style="width:50%;"/>
</center>

<div align="center" style="font-size:40pt; font-weight:900; font-family:arial; margin-top:50px;" >
Android Java MteRelay Library</div>
<br><br><br>

![Latest Release](https://img.shields.io/github/v/release/Eclypses/eclypses-aws-mte-relay-client-android?style=flat-square)

# Introduction 
This AAR library provides the Java language Eclypses MteRelay Mobile Client library and requires licensed access to an MteRelay server instance to receive the secure transmission. [Info](https://eclypses.com/mte-technology/amazon-web-services-aws/)

**Purpose of MteRelay:**
- Securely relay HTTP requests to your server.
- Protect sensitive headers and data with MTE encryption.
- Stream large files efficiently.

- This guide assumes a working knowledge of including an AAR library (either from a local directory on your computer or directly from Maven Central) in your Android project. [HowTo](https://developer.android.com/build/dependencies#groovy)

## Installation
### 1. Maven Central
- Add the following line to your app's `build.gradle` file:
  ```groovy
  implementation 'com.eclypses:eclypses-aws-mte-relay-client-android-release:4.2.4'
  ```
- Confirm that MavenCentral is one of your listed repositories.

### 2. Local AAR File
- Create a 'libs' directory at the same level as the `src` directory in your app.
- Download the Relay Library from [GitHub](https://github.com/Eclypses/eclypses-aws-mte-relay-client-android.git) and compile it.
- Add the resulting `.aar` file (e.g., `eclypses-aws-mte-relay-client-android-release-4.2.4-release.aar`) to the 'libs' directory.
- Add the following line to your module's `build.gradle` dependencies block:
  ```groovy
  implementation files('libs/eclypses-aws-mte-relay-client-android-release-4.2.4-release.aar')
  ```

<br><br>

# Table of Contents
- [Getting Started](#getting-started)
- [Simple Volley GET and POST requests](#simple-volley-get-and-post-requests)
- [Simple OkHttp GET and POST requests](#simple-okhttp-get-and-post-requests)
- [Simple Streamed File Upload request](#simple-streamed-file-upload-request)
- [Simple Streamed File Download request](#simple-streamed-file-download-request)
- [RePair with Server](#repair-with-server)
- [Adjust Relay Settings as Necessary](#adjust-relay-settings-as-necessary)
- [Logging](#logging)
- [Common Issues & Debugging](#common-issues--debugging)
- [Contact Eclypses](#contact-eclypses)

<br><br>

# Getting Started
- **NOTE - Currently, this library supports Volley requests for simple GET and POST requests. Additionally, file streamed uploads and downloads are supported.**
- In the class where you will maintain the Relay reference ... 
   - Create a class variable for the relay singleton. 
   <br><br>
   ``` java
   Relay relay;
   ```
   - Then, in the constructor for that class, instantiate the Relay class, passing ...
      -  the context,
      - and a new instance of RelayResponseListener.
   ``` java
   relay = Relay.getInstance(ctx, new RelayResponseListener() {
      @Override
      public void onCompletion(boolean success, String message) {
         // handle callback appropriately
      }
   });
   ```
<br><br>

# Quick Start Example
```java
Relay relay = Relay.getInstance(context, new RelayResponseListener() {
    @Override
    public void onCompletion(boolean success, String message) {
        Log.d("MTE", "Relay Setup: " + message);
    }
});
String url = "https://myRelayServer.com/api/data";
Request<String> request = new StringRequest(Request.Method.GET, url,
    response -> Log.d("MTE", "Response: " + response),
    error -> Log.e("MTE", "Error: " + error.getMessage())
);

Map<String, String>  headersToEncrypt = new HashMap<>();
   headersToEncrypt.put("Authorization", "<authToken>");
relay.addToMteRequestQueue(request, headersToEncrypt, new RelayDataTaskListener() {
    @Override
    public void onResponse(byte[] responseBytes, Map<String, List<String>> responseHeaders) {
        Log.d("MTE", "Response received.");
    }

    @Override
    public void onError(String message, Map<String, List<String>> responseHeaders) {
        Log.e("MTE", "Request Failed: " + message);
    }
});
```

# Simple Volley GET and POST requests
   
- When creating your Volley request, instead of adding your original server Url, add the url (Scheme and authority, i.e. https://myRelayServer/) of the Relay Server that you are targeting. 
- Then, after creating your Volley request, instead of calling `RequestSingleton.getInstance(context).addToRequestQueue(request);`, call `relay.addToMteRequestQueue()`, passing ...
   - the request object, 
   - a String[] of the names of any HTTP headers you wish to have protected by MTE, 
   - and a new RelayDataTaskListener.
- Optionally,  pathnamePrefix, if required by your infrastructure.
   <br><br>
``` java
String[] headersToEncrypt = new String[] {"Content-Length"};

// Without pathnamePrefix
relay.addToMteRequestQueue(request, headersToEncrypt, new RelayVolleyRequestListener() {
    @Override
    public void onError(NetworkResponse networkResponse, String message, Map<String, List<String>> responseHeaders) {
        // Handle errors appropriately and response headers as necessary
    }

    @Override
    public void onResponse(NetworkResponse networkResponse, byte[] responseBytes, Map<String, List<String>> responseHeaders) {
        // Returns the response body as a byte[], and the response headers as a Map  
    }

    @Override
    public void onResponse(NetworkResponse networkResponse, JSONObject responseJson, Map<String, List<String>> responseHeaders) {
        // Returns the response body as a JSONObject, and the response headers as a Map 
    }
});

// With pathnamePrefix
String pathnamePrefix = "<your-pathname-prefix>";
relay.addToMteRequestQueue(request, headersToEncrypt, pathnamePrefix, new RelayVolleyRequestListener() {
    @Override
    public void onError(NetworkResponse networkResponse, String message, Map<String, List<String>> responseHeaders) {
        // Handle errors appropriately and response headers as necessary
    }

    @Override
    public void onResponse(NetworkResponse networkResponse, byte[] responseBytes, Map<String, List<String>> responseHeaders) {
        // Returns the response body as a byte[], and the response headers as a Map  
    }

    @Override
    public void onResponse(NetworkResponse networkResponse, JSONObject responseJson, Map<String, List<String>> responseHeaders) {
        // Returns the response body as a JSONObject, and the response headers as a Map 
    }
});
```
<br><br>

 # Simple OkHttp GET and POST requests
   
- When creating your OkHttp request, instead of adding your original server Url, add the url (Scheme and authority, i.e. https://myRelayServer/) of the Relay Server that you are targeting. 
- Then, after creating your OkHttp request using client.newCall(request).enqueue(new Callback(), instead, call `relay.send(_,_,_,_)`, passing ...
   - the request object, 
   - a String[] of the names of any HTTP headers you wish to have protected by MTE, 
   - Optionally,  pathnamePrefix, if required by your infrastructure,
   - and a new RelayOkHttpRequestListener.

   <br><br>
``` java
String[] headersToEncrypt = new String[] {"Content-Length"};

// Without pathnamePrefix
relay.send(request, headersToEncrypt, new RelayOkHttpRequestListener() {

        @Override
        public void onError(Response response) {
           // Handle errors appropriately and response headers as necessary
        }

        @Override
        public void onResponse(Response response) {
             // Handle Response as appropriate
        }
});

// With pathnamePrefix
String pathnamePrefix = "<your-pathname-prefix>";

relay.send(request, headersToEncrypt, pathnamePrefix, new RelayOkHttpRequestListener() {

        @Override
        public void onError(Response response) {
           // Handle errors appropriately and response headers as necessary
        }

        @Override
        public void onResponse(Response response) {
            // Handle Response as appropriate
        }
});
```
<br><br>


# Simple Streamed File Upload request

- Create a new `RelayFileRequestProperties` object
``` java
File origFile = new File(ctx.getFilesDir(), filename);
String route = "route/portion/of/url";
RelayFileRequestProperties reqProperties = new RelayFileRequestProperties(
                        origFile, // File object to upload
                        "https://myRelayServer.com", // Server path
                        new HashMap<String, String>(), // Request headers for this request
                        new String[]{"Authorization"}, // Header names to protect with MTE
                        new RelayStreamCallback() {
                            @Override
                            public void getRequestBodyStream(PipedOutputStream outputStream) {
                                // Convert your entire HttpRequest, including the file bytes to a byte[] and write it to the output stream.
                                // This allows large files (up to nearly 2 gigabytes) to be streamed to the server.
                                // Write HttpRequestBytes
                                outputStream.write(HttpRequestBytes, 0, HttpRequestBytes.length);
                                outputStream.flush();

                                // Stream the file in chunks
                                FileInputStream inputStream = new FileInputStream(origFile);
                                byte[] buffer = new byte[fileChunkSize];
                                int bytesRead;
                                while ((bytesRead = inputStream.read(buffer)) != -1) {
                                    outputStream.write(buffer, 0, bytesRead);
                                    outputStream.flush();
                                }

                                inputStream.close();
                                outputStream.close();
                            }
                        });
```
- Then, call `relay.uploadFile`, passing ...
   - the `RelayFileRequestProperties` object you just created,
   - the route portion of the URL you are uploading to,
   - optionally, pathnamePrefix, if required by your infrastructure,
   - an instance of `RelayStreamResponseListener`, // returns response and headers
   - an instance of `RelayStreamCompletionCallback`. // provides upload progress updates
   <br><br>
``` java
relay.uploadFile(<RelayServerUrlPath>, reqProperties, route, <optional pathnamePrefix>, new RelayStreamResponseListener() {
   @Override
   public void relayStreamResponse(int statusCode, boolean success, String message, String errorMessage, Map<String, List<String>> responseHeaders) {
      // Handle response as necessary
   }
}, new RelayStreamCompletionCallback() {
   @Override
   public void onProgressUpdate(int bytesCompleted, int totalBytes) {
      // Handle progress updates as appropriate
   };
};
```
<br><br>

# Simple Streamed File Download request

- Create a new `RelayFileRequestProperties` object
``` java
RelayFileRequestProperties reqProperties = new RelayFileRequestProperties(
                        "https://myRelayServer.com", // Server path
                        "route/portion/of/download/Url", // Route portion of download URL
                        new File(ctx.getFilesDir(), "downloadedFile.txt"), // Path to store downloaded file
                        new HashMap<String, String>(), // Request headers for this request
                        new String[] {"Authorization"} // Header names to protect with MTE
);
```
- Then call `relay.downloadFile`, passing ...
   - the `RelayFileRequestProperties` object you just created,
   - optionally, a pathnamePrefix, if required by your infrastructure,
   - a new instance of `RelayResponseListener`.
 <br><br>

``` java
relay.downloadFile(<RelayServerUrlPath>, reqProperties, <optional pathnamePrefix>, new RelaystreamResponseListener() {
   @Override
   public void relayStreamResponse(int statusCode, boolean success, String message, String errorMessage, Map<String, List<String>> responseHeaders) {
      // Handle response as necessary
   }
});
```
<br><br>

# RePair with Server
- Most situations where Client and Server get out of sync are handled automatically but a function is available to trigger a rePair attempt.
- Call `relay.rePairWithRelayServer` passing ...
   - the path of the server with which you wish to rePair (https://myRelayServer.com),
   - optionally, pathnamePrefix, if required by your infrastructure,
   - and a new instance of `RelayResponseListener`.
<br><br>

``` java
relay.rePairWithRelayServer(<RelayServerUrlPath>, <optional pathnamePrefix>);
// Responses will be received via relayResponseListener parameter passed in Relay Instantiation
```

# Adjust Relay Settings as Necessary
- The `RelaySettings` actor contains a few settings that can be edited at runtime via public functions as shown below. Each new Relay instantiation begins with the default values. Consequently, when `adjustRelaySettings` is called with new values different than the existing values, a rePair with the Relay Server is automatically called as well.

``` java
   // Sets the maximum number of streamed bytes processed in a single chunk. Processing often occurs on fewer bytes.
   int newStreamChunkSize = <newValue>; // Defaults to 1048576. Range 4096 (4KB) to 10485760 (10 MB)

   // The Mobile Relay Client provides multiple pairs used in a round-robin fashion to facilitate high throughput without collisions.  
   int newPairPoolSize = <newValue>; // Defaults to 3. Range 1 to 10

   // The Mobile Relay Client has the ability to persist pairing with server, even though client has been shut down. Default is false because a new pairing happens quickly at relay instantiation and removes the chance of the pairing having been corrupted. 
   boolean persistPair = true; // Defaults to false on each Relay instantiation

   String result = relay.adjustRelaySettings(
                <RelayServerUrlPath>,
                pathnamePrefix, // (if required by your infrastructure)
                newStreamChunkSize,
                newPairPoolSize,
                persistPairs);
```

# Logging
- Logging to LogCat is enabled by default. LogToFile is disabled by default but can be enabled at runtime with this static method.
```java
Relay.enableFileLogging(
                <RelayServerUrlPath>,
                <optional pathnamePrefix>, // (if required by your infrastructure)
                isEnabled); // boolean to enable or disable file logging.
```
- Read Log File Contents
``` java
String fileContents = Relay.readLogFile(
                <RelayServerUrlPath>,
                <optional pathnamePrefix>); // (if required by your infrastructure)
```
- Clear Log File Contents
``` java
String fileContents = Relay.clearLogFile(
                <RelayServerUrlPath>,
                <optional pathnamePrefix>); // (if required by your infrastructure)
```
<br><br>

# Common Issues & Debugging

### ❌ Issue: "relayStreamResponse failed with error"
✅ **Solution:** Check if your Relay server URL is correct and reachable.

### ❌ Issue: "Null Pointer Exception in getInstance()"
✅ **Solution:** Ensure you are calling `Relay.getInstance()` in your **Application class or an Activity with a valid context**.

### ❌ Issue: "File upload fails after 10MB"
✅ **Solution:** Check if your server supports **streamed uploads**.

<br><br>

<div style="page-break-after: always; break-after: page;"></div>

# Contact Eclypses

<p align="center" style="font-weight: bold; font-size: 20pt;">Email: <a href="mailto:info@eclypses.com">info@eclypses.com</a></p>
<p align="center" style="font-weight: bold; font-size: 20pt;">Web: <a href="https://www.eclypses.com">www.eclypses.com</a></p>
<p align="center" style="font-weight: bold; font-size: 20pt;">Chat with us: <a href="https://developers.eclypses.com/dashboard">Developer Portal</a></p>
<p style="font-size: 8pt; margin-bottom: 0; margin: 100px 24px 30px 24px; " >
<b>All trademarks of Eclypses Inc.</b> may not be used without Eclypses Inc.'s prior written consent. No license for any use thereof has been granted without express written consent. Any unauthorized use thereof may violate copyright laws, trademark laws, privacy and publicity laws and communications regulations and statutes. The names, images and likeness of the Eclypses logo, along with all representations thereof, are valuable intellectual property assets of Eclypses, Inc. Accordingly, no party or parties, without the prior written consent of Eclypses, Inc., (which may be withheld in Eclypses' sole discretion), use or permit the use of any of the Eclypses trademarked names or logos of Eclypses, Inc. for any purpose other than as part of the address for the Premises, or use or permit the use of, for any purpose whatsoever, any image or rendering of, or any design based on, the exterior appearance or profile of the Eclypses trademarks and or logo(s).
</p>
