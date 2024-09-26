<center>
<img src="Eclypses.png" style="width:50%;"/>
</center>

<div align="center" style="font-size:40pt; font-weight:900; font-family:arial; margin-top:50px;" >
Android Java MteRelay Library for Amazon Web Services</div>
<br><br><br>

# Introduction 
This AAR library provides the Java language Eclypses MteRelay Client library.

- This guide assumes a working knowledge of including an AAR library (either from a local directory on your computer or directly from Maven Central) in your Android project. [HowTo](https://developer.android.com/build/dependencies#groovy)
-	The simplest way to use the library is to list it as a dependency for your app (Module build.gradle / Dependencies). Add 'implementation 'com.eclypses:eclypses-aws-mte-relay-client-android-release:x.x.x' and confirm that MavenCentral is one of your listed repositories.
- Alternatively, you can add a 'libs' directory to the same level as the src directory n your app, then download the Relay Library from https://github.com/Eclypses/eclypses-aws-mte-relay-client-android.git and compile it. Add the resulting .aar (eclypses-aws-mte-relay-client-android-release-3.4.9-release.aar) to the libs dir you just created and add - implementation files('libs/eclypses-aws-mte-relay-client-android-release-3.4.9-release.aar') - line to your module build.gradle file's dependancies block.


<br><br>

# Getting Started
-	**NOTE - Currently, this library supports Volley requests for simple GET and POST requests. Additionally, file streamed uploads and downloads are supported. **
- In the class where you will maintain the Relay reference ... 
   - Create a class variable for the relay singleton. 
   <br><br>
   ``` java
   private static Relay relay;

   ```
   - Then, in the constructor for that class, instantiate the Relay class, passing ...
      -  the context,
      - and a new instance of InstantiateRelayCallback.
   ``` java
   relay = Relay.getInstance(ctx, new InstantiateRelayCallback() {
      @Override
      public void onError(String message) {
         // handle instantiate errors appropriately
      }

      @Override
      public void relayInstantiated() {
         // any code to run after Relay is instantiated
      }
   });
   ```
<br><br>

# Simple Volley GET and POST requests
   
- When creating your Volley request, instead of adding your original server Url, add the url (Scheme and authority, i.e. https://myAwsRelayServer/) of the AWS Relay Server that you are targeting. 
- Then, after creating your Volley request, instead of calling `RequestSingleton.getInstance(context).addToRequestQueue(request);`, call relay.addToMteRequestQueue(), passing ...
   - the request object, 
   - a String[] of the names of any http headers you wish to have protected by Mte, 
   - and a new RelayResponseListener.
   <br><br>
``` java
String[] headersToEncrypt = new String[] {"Content-Length"};

relay.addToMteRequestQueue(request, headersToEncrypt, new RelayResponseListener() {
   @Override
   public void onError(String message) {
      // Handle errors appropriately
   }

   @Override
   public void onResponse(byte[] responseBytes, Map<String, List<String>> responseHeaders) {
   // Returns returns the response body as a byte[], and the response headers as a Map  
   }

   @Override
   public void onResponse(JSONObject responseJson, Map<String, List<String>> responseHeaders) {
   // Returns returns the response body as a JSONObject, and the response headers as a Map 
   }
});
```
<br><br>

# Simple Streamed File Upload request

- Create a new RelayFileRequestProperties object
``` java
File origFile = new File(ctx.getFilesDir(), filename);
String route = "route/portion/of/url";
RelayFileRequestProperties reqProperties = new RelayFileRequestProperties(
                        // File object to upload,
                        // Server path ("https://myRelayServer.com")
                        // request headers for this request as a Map<String, String>,
                        // String[] of header names to protect with Mte,
                        // Instance of RelayStreamCallback);
```
- Then, call relay.uploadFile, passing ...
   - the RelayFileRequestProperties object you just created'
   - the route portion of the url you are uploading to,
   - a new instance of RelayResponseListener.
   <br><br>
``` java
relay.uploadFile(AppSettings.relayHosts[0], reqProperties, route, new RelayResponseListener() {
   @Override
   public void onError(String message) {
      // Handle errors appropriately
   }

   @Override
   public void onResponse(byte[] responseBytes, Map<String, List<String>> responseHeaders) {
      // We don't expect to receive the response as a byte[].
   }

   @Override
   public void onResponse(JSONObject responseJson, Map<String, List<String>> responseHeaders) {
   // Returns returns the response body as a JSONObject, and the response headers as a Map 
      });
   }
});
```
- Additionally, in the class where your HttpRequest is created, implement RelayStreamCallback and add the required function 'getRequestBodyStream(PipedOutputStream outputStream)'

<br><br>

``` java
@Override // Callback from Relay Module
    public void getRequestBodyStream(PipedOutputStream outputStream) {
        // convert your entire HttpRequest, including the file bytes to a byte[] and write it to the output stream. In our demonstration app, we convert the request object to a byte[] and write it to the OutputStream, then read the file by chunks and write each chunk into the OutputStream. This allows that even a large file (up to nearly 2 gigabytes) to be streamed up to the server, provided that your server can accept a file stream. 
        
        outputStream.write(HttpRequestBytes, 0, HttpRequestBytes.length);
        outputStream.flush();

        FileInputStream inputStream = new FileInputStream(fileToUpload);
        byte[] buffer = new byte[fileChunkSize];
        int bytesRead = -1;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
            outputStream.flush();
        }

        inputStream.close();
        outputStream.close();
    }
```
- The entire stream will be protected with Mte, including any headers you have included in the 'headersToEncrypt' reqProperties object. Upon being received by the Relay Server, the Stream and Headers will be decrypted 'on-the-fly' and the original request including headers and file will be streamed through to your Server.

<br><br>

# Simple Streamed File Download request

- Create a new RelayFileRequestProperties object
``` java
 RelayFileRequestProperties reqProperties = new RelayFileRequestProperties(
                        // Name of the file to download,
                        // Server path ("https://myRelayServer.com")
                        // route portion of download Url with preceding '/' removed,
                        // path of location where you want to store the downloaded file,
                        // request headers for this request as a Map<String, String>,
                        // String[] of header names to protect with Mte
```
- Then call relay.downloadFile, passing ...
   - the RelayFileRequestProperties object you just created'
   - a new instance of RelayResponseListener
 <br><br>

``` java
relay.downloadFile(AppSettings.relayHosts[0], reqProperties, new RelayResponseListener() {
   @Override
   public void onError(String message) {
      // Handle errors appropriately
   }

   @Override
   public void onResponse(byte[] responseBytes, Map<String, List<String>> responseHeaders) {
      // We don't expect to receive the response as a byte[].
   }

   @Override
   public void onResponse(JSONObject responseJson, Map<String, List<String>> responseHeaders) {
   // Returns returns a JSONObject, and the response headers as a Map
   }
});
```
<br><br>

# RePair with Server
- Most situations where Client and Server get out of sync are handled automatically but a function is available to trigger a rePair attempt.
- Call 'relay.rePairWithRelayServer' passing ...
   - the path of the server with which you wish to rePair (https://myRelayServer.com),
   - and a new instance of RelayResponseListener
<br><br>

``` java
relay.rePairWithRelayServer(relayServerPath, new RelayResponseListener() {
   @Override
   public void onError(String message) {
         // Handle errors appropriately
   }

   @Override
   public void onResponse(byte[] bytes, Map<String, List<String>> responseHeaders) {
         // We don't expect to receive the response as a byte[].
   }

   @Override
   public void onResponse(JSONObject jsonObject, Map<String, List<String>> responseHeaders) {
         // Returns returns a JSONObject, and the response headers as a Map
   }
});
```

### An AWS MteRelay Client YouTube integration video will soon be available.
<br><br>

<div style="page-break-after: always; break-after: page;"></div>


# Contact Eclypses

<p align="center" style="font-weight: bold; font-size: 20pt;">Email: <a href="mailto:info@eclypses.com">info@eclypses.com</a></p>
<p align="center" style="font-weight: bold; font-size: 20pt;">Web: <a href="https://www.eclypses.com">www.eclypses.com</a></p>
<p align="center" style="font-weight: bold; font-size: 20pt;">Chat with us: <a href="https://developers.eclypses.com/dashboard">Developer Portal</a></p>
<p style="font-size: 8pt; margin-bottom: 0; margin: 100px 24px 30px 24px; " >
<b>All trademarks of Eclypses Inc.</b> may not be used without Eclypses Inc.'s prior written consent. No license for any use thereof has been granted without express written consent. Any unauthorized use thereof may violate copyright laws, trademark laws, privacy and publicity laws and communications regulations and statutes. The names, images and likeness of the Eclypses logo, along with all representations thereof, are valuable intellectual property assets of Eclypses, Inc. Accordingly, no party or parties, without the prior written consent of Eclypses, Inc., (which may be withheld in Eclypses' sole discretion), use or permit the use of any of the Eclypses trademarked names or logos of Eclypses, Inc. for any purpose other than as part of the address for the Premises, or use or permit the use of, for any purpose whatsoever, any image or rendering of, or any design based on, the exterior appearance or profile of the Eclypses trademarks and or logo(s).
</p>
