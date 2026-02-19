<center>
<img src="Eclypses.png" style="width:50%;"/>
</center>

<div align="center" style="font-size:40pt; font-weight:900; font-family:arial; margin-top:50px;" >
MteRelay Mobile Client  
Android Library</div>

![Latest Release](https://img.shields.io/github/v/release/Eclypses/eclypses-aws-mte-relay-client-android?style=flat-square)

## Introduction
This AAR library provides the Eclypses MteRelay Mobile Client for Android. It enables secure, encrypted HTTP(S) communication between your Android app and your backend via an MteRelay server. You must have licensed access to an MteRelay server instance. [More Info](https://eclypses.com/mte-technology/amazon-web-services-aws/)

**Purpose of MteRelay:**
- Securely relay HTTP requests to your server
- Protect sensitive headers and data with MTE encryption
- Stream large files efficiently

## Quick Links

📚 **[Official Getting Started Guide](https://public-docs.eclypses.com/docs/mte-relay-server/client-libraries/android)** - Concise guide for experienced developers

💡 This README provides comprehensive reference documentation with detailed examples suitable for developers at all experience levels. If you're already familiar with Android networking and MTE concepts, the official docs above offer a faster quick-start path.

## Prerequisites

- **Android API Level 21 (Android 5.0 Lollipop) or later** - Required for modern networking APIs and security features
- **Java 8 or later** (or Kotlin with Java 8 bytecode compatibility)
- **Android Studio Arctic Fox (2020.3.1) or later** - For Gradle and dependency management
- **Access to a licensed MteRelay server instance** - You'll need the server URL and the Relay Server pre-configured to route encrypted requests to your backend

## Installation

### Maven Central (Recommended)

Add the following to your app's `build.gradle` file:

```groovy
dependencies {
    implementation 'com.eclypses:eclypses-aws-mte-relay-client-android-release:4.2.6'
}
```

Ensure Maven Central is listed in your project's `settings.gradle` or root `build.gradle`:

```groovy
repositories {
    mavenCentral()
    // ... other repositories
}
```

**Common Maven Central Issues:**
- **"Failed to resolve"** - Run **File > Sync Project with Gradle Files** and ensure you have internet connectivity
- **Version not found** - Check the [releases page](https://github.com/Eclypses/eclypses-aws-mte-relay-client-android/releases) for the latest version number

### Local AAR File

1. Create a `libs` directory at the same level as `src` in your app module
2. Download and compile the library from [GitHub](https://github.com/Eclypses/eclypses-aws-mte-relay-client-android.git)
3. Copy the resulting `.aar` file to your `libs` directory
4. Add the following to your module's `build.gradle`:

```groovy
dependencies {
    implementation 'com.eclypses:eclypses-aws-mte-relay-client-android-release:4.2.6'
    
    // Required logging dependencies
    implementation 'org.slf4j:slf4j-api:2.0.9'
    implementation 'com.github.tony19:logback-android:3.0.0'
}
```

**Common Local AAR Issues:**
- **"Unable to resolve dependency"** - Verify the AAR filename matches exactly in your `build.gradle`
- **Build errors after adding** - Try **Build > Clean Project** then **Build > Rebuild Project**

## Setup

### Step 1: Configure Your MteRelay Server

**Important:** 
Before using this client library, ensure your MteRelay server is set up and configured to receive encrypted requests from your Android app. The server handles the decryption and forwards requests to your actual backend API.

The MteRelay server acts as a secure intermediary - it receives encrypted requests from your Android app, decrypts them, and forwards them to your backend API endpoint.

### Step 2: Import the Package

In any Java file where you'll use MteRelay, add the import statements:

```java
import com.mte.relay.Relay;
import com.mte.relay.RelayResponseListener;
```

### Step 3: Create a Relay Instance

The `Relay` class is your main interface to the library. It uses a singleton pattern. Here's a complete example of setting it up in a typical Android app:

```java
import android.content.Context;
import android.util.Log;

import com.mte.relay.Relay;
import com.mte.relay.RelayResponseListener;

// This class manages your network communication through MteRelay
public class NetworkManager {
    private static final String TAG = "NetworkManager";
    
    // The relay instance handles all encrypted communication
    private Relay relay;
    
    // Store your MteRelay server URL (e.g., "https://your-relay-server.com")
    private final String relayServerUrl;
    
    public NetworkManager(Context context, String relayServerUrl) {
        this.relayServerUrl = relayServerUrl;
        
        // Initialize the Relay singleton
        // This validates MTE licensing and sets up the encryption infrastructure
        relay = Relay.getInstance(context, new RelayResponseListener() {
            @Override
            public void onCompletion(Boolean success, String message) {
                if (success) {
                    Log.d(TAG, "✅ Relay operation successful: " + message);
                } else {
                    Log.e(TAG, "❌ Relay operation failed: " + message);
                    // Handle failure - you might want to:
                    // - Show an alert to the user
                    // - Retry the operation
                    // - Fall back to alternative behavior
                }
            }
        });
    }
    
    public Relay getRelay() {
        return relay;
    }
}
```

**Understanding the Singleton Pattern:**
- The `Relay` class uses a singleton pattern - only one instance exists per application
- Call `Relay.getInstance()` with the same context to get the existing instance
- The `RelayResponseListener` receives callbacks for pairing operations and errors

**Example usage in an Activity:**
```java
public class MainActivity extends AppCompatActivity {
    private NetworkManager networkManager;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // Initialize the network manager
        networkManager = new NetworkManager(
            getApplicationContext(),
            "https://your-relay-server.com"
        );
        
        Log.d("MainActivity", "NetworkManager initialized");
    }
}
```

**Example usage in an Application class (recommended for app-wide access):**
```java
public class MyApplication extends Application {
    private static NetworkManager networkManager;
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        // Initialize once at app startup
        networkManager = new NetworkManager(
            getApplicationContext(),
            "https://your-relay-server.com"
        );
    }
    
    public static NetworkManager getNetworkManager() {
        return networkManager;
    }
}
```

## Usage

### Making Secure HTTP Requests

Once you've set up your `Relay` instance, you can use it to make secure requests. The library supports both **Volley** and **OkHttp** networking libraries. The library automatically encrypts your data, sends it through the MteRelay server, and decrypts the response.

**Important:** When creating your requests, use your **MteRelay server URL** (not your actual backend API URL). The MteRelay server will decrypt your request and forward it to your actual backend.

---

### Volley Requests

The library integrates with [Volley](https://developer.android.com/training/volley), Google's HTTP library for Android.

#### Basic GET Request with Volley

```java
import com.android.volley.Request;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.NetworkResponse;
import com.mte.relay.RelayVolleyRequestListener;

import java.util.List;
import java.util.Map;

// Create a Volley request using your MteRelay server URL
// The MteRelay server will forward the decrypted request to your actual backend API
String url = "https://your-relay-server.com/api/users/123";

StringRequest request = new StringRequest(
    Request.Method.GET,
    url,
    response -> {
        // This callback is not used when going through Relay
        // Use the RelayVolleyRequestListener instead
    },
    error -> {
        // This callback is not used when going through Relay
        // Use the RelayVolleyRequestListener instead
    }
);

// Specify which headers should be encrypted (optional but recommended for sensitive data)
// Headers not in this array will be sent unencrypted
String[] headersToEncrypt = new String[] {"Authorization"};

// Make the request through MteRelay
relay.addToMteRequestQueue(request, headersToEncrypt, new RelayVolleyRequestListener() {
    @Override
    public void onError(NetworkResponse networkResponse, String message, 
                        Map<String, List<String>> responseHeaders) {
        // Handle errors
        Log.e(TAG, "Request failed: " + message);
        
        if (networkResponse != null) {
            Log.e(TAG, "Status code: " + networkResponse.statusCode);
        }
    }

    @Override
    public void onResponse(NetworkResponse networkResponse, byte[] responseBytes, 
                          Map<String, List<String>> responseHeaders) {
        // Handle successful response
        String responseString = new String(responseBytes);
        Log.d(TAG, "Response: " + responseString);
        
        // Parse JSON if needed
        try {
            JSONObject json = new JSONObject(responseString);
            // Process your JSON data
        } catch (JSONException e) {
            Log.e(TAG, "JSON parsing error: " + e.getMessage());
        }
    }
});
```

#### POST Request with JSON Body (Volley)

```java
import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;

import org.json.JSONObject;
import org.json.JSONException;

// Create the request body
JSONObject requestBody = new JSONObject();
try {
    requestBody.put("name", "John Doe");
    requestBody.put("email", "john@example.com");
} catch (JSONException e) {
    Log.e(TAG, "Error creating request body: " + e.getMessage());
    return;
}

// Create a POST request
String url = "https://your-relay-server.com/api/users";

JsonObjectRequest request = new JsonObjectRequest(
    Request.Method.POST,
    url,
    requestBody,
    response -> { /* Not used with Relay */ },
    error -> { /* Not used with Relay */ }
);

// Encrypt the Authorization header
String[] headersToEncrypt = new String[] {"Authorization"};

// Make the request
relay.addToMteRequestQueue(request, headersToEncrypt, new RelayVolleyRequestListener() {
    @Override
    public void onError(NetworkResponse networkResponse, String message, 
                        Map<String, List<String>> responseHeaders) {
        Log.e(TAG, "Error creating user: " + message);
    }

    @Override
    public void onResponse(NetworkResponse networkResponse, byte[] responseBytes, 
                          Map<String, List<String>> responseHeaders) {
        Log.d(TAG, "User created successfully!");
        Log.d(TAG, "Status: " + networkResponse.statusCode);
    }
});
```

#### Using pathnamePrefix with Volley

If your relay server is configured with specific routing paths, use the `pathnamePrefix` parameter:

```java
String pathnamePrefix = "api/v1";

relay.addToMteRequestQueue(request, headersToEncrypt, pathnamePrefix, 
    new RelayVolleyRequestListener() {
        @Override
        public void onError(NetworkResponse networkResponse, String message, 
                            Map<String, List<String>> responseHeaders) {
            // Handle error
        }

        @Override
        public void onResponse(NetworkResponse networkResponse, byte[] responseBytes, 
                              Map<String, List<String>> responseHeaders) {
            // Handle response
        }
    }
);
```

---

### OkHttp Requests

The library also integrates with [OkHttp](https://square.github.io/okhttp/), a popular HTTP client for Android.

#### Basic GET Request with OkHttp

```java
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import com.mte.relay.RelayOkHttpRequestListener;

// Create an OkHttp request using your MteRelay server URL
Request request = new Request.Builder()
    .url("https://your-relay-server.com/api/users/123")
    .addHeader("Accept", "application/json")
    .addHeader("Authorization", "Bearer abc123")
    .build();

// Specify which headers should be encrypted
String[] headersToEncrypt = new String[] {"Authorization"};

// Make the request through MteRelay
relay.send(request, headersToEncrypt, new RelayOkHttpRequestListener() {
    @Override
    public void onError(Response response) {
        // Handle error
        Log.e(TAG, "Request failed");
        if (response != null) {
            Log.e(TAG, "Status: " + response.code());
        }
    }

    @Override
    public void onResponse(Response response) {
        // Handle successful response
        try {
            String responseBody = response.body().string();
            Log.d(TAG, "Response: " + responseBody);
            
            // Parse JSON if needed
            JSONObject json = new JSONObject(responseBody);
            // Process your JSON data
            
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Error processing response: " + e.getMessage());
        }
    }
});
```

#### POST Request with JSON Body (OkHttp)

```java
import okhttp3.MediaType;
import okhttp3.RequestBody;

// Create the request body
JSONObject requestBody = new JSONObject();
try {
    requestBody.put("name", "John Doe");
    requestBody.put("email", "john@example.com");
} catch (JSONException e) {
    Log.e(TAG, "Error creating request body: " + e.getMessage());
    return;
}

MediaType JSON = MediaType.parse("application/json; charset=utf-8");
RequestBody body = RequestBody.create(requestBody.toString(), JSON);

// Create a POST request
Request request = new Request.Builder()
    .url("https://your-relay-server.com/api/users")
    .post(body)
    .addHeader("Content-Type", "application/json")
    .addHeader("Authorization", "Bearer abc123")
    .build();

// Encrypt sensitive headers
String[] headersToEncrypt = new String[] {"Authorization"};

// Make the request
relay.send(request, headersToEncrypt, new RelayOkHttpRequestListener() {
    @Override
    public void onError(Response response) {
        Log.e(TAG, "Error creating user");
    }

    @Override
    public void onResponse(Response response) {
        Log.d(TAG, "User created! Status: " + response.code());
    }
});
```

#### Using pathnamePrefix with OkHttp

```java
String pathnamePrefix = "api/v1";

relay.send(request, headersToEncrypt, pathnamePrefix, new RelayOkHttpRequestListener() {
    @Override
    public void onError(Response response) {
        // Handle error
    }

    @Override
    public void onResponse(Response response) {
        // Handle response
    }
});
```

**Parameter Details:**

- **`request`** - Your Volley `Request<T>` or OkHttp `Request` object configured with URL, method, headers, and body
- **`headersToEncrypt`** (`String[]`) - Array of header names to encrypt. For example, `{"Authorization", "X-API-Key"}`. Headers not in this array are sent unencrypted. Pass `null` or empty array to send all headers unencrypted (not recommended for sensitive data)
- **`pathnamePrefix`** (`String`, optional) - Path to prepend to requests on the relay server. This is typically `null` unless your relay server is configured with specific routing paths
- **Listener** - Callback interface for receiving the response or error

---

### File Upload (Streaming)

For uploading files, especially large ones, use the streaming upload API. This is more memory-efficient than loading the entire file into memory.

**When to use streaming vs regular requests:**
- **Use streaming** for files larger than a few MB, video uploads, or when you want progress tracking
- **Use regular requests** for small payloads (JSON, small images under 1-2 MB)

#### Complete Streaming Upload Example

```java
import com.mte.relay.RelayFileRequestProperties;
import com.mte.relay.RelayStreamCallback;
import com.mte.relay.RelayStreamResponseListener;
import com.mte.relay.RelayStreamCompletionCallback;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PipedOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FileUploadManager {
    private static final String TAG = "FileUploadManager";
    private final Relay relay;
    
    public FileUploadManager(Relay relay) {
        this.relay = relay;
    }
    
    /**
     * Upload a file using streaming with multipart/form-data
     * @param file The file to upload
     * @param serverUrl Your MteRelay server URL
     * @param route The API route for the upload endpoint (e.g., "api/upload")
     */
    public void uploadFile(File file, String serverUrl, String route) {
        // Prepare request headers
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/octet-stream");
        // Add any other headers your API requires
        // headers.put("Authorization", "Bearer your-token");
        
        // Specify which headers to encrypt
        String[] headersToEncrypt = new String[] {"Authorization"};
        
        // Create the multipart boundary
        String boundary = "Boundary-" + System.currentTimeMillis();
        
        // Create RelayFileRequestProperties with stream callback
        RelayFileRequestProperties reqProperties = new RelayFileRequestProperties(
            serverUrl,
            headers,
            headersToEncrypt,
            new RelayStreamCallback() {
                @Override
                public void getRequestBodyStream(PipedOutputStream outputStream) {
                    try {
                        // Write multipart prefix
                        String prefix = "--" + boundary + "\r\n" +
                            "Content-Disposition: form-data; name=\"file\"; filename=\"" + 
                            file.getName() + "\"\r\n" +
                            "Content-Type: application/octet-stream\r\n\r\n";
                        outputStream.write(prefix.getBytes());
                        outputStream.flush();
                        
                        // Stream the file in chunks
                        FileInputStream inputStream = new FileInputStream(file);
                        byte[] buffer = new byte[65536]; // 64KB chunks
                        int bytesRead;
                        
                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, bytesRead);
                            outputStream.flush();
                        }
                        
                        // Write multipart postfix
                        String postfix = "\r\n--" + boundary + "--\r\n";
                        outputStream.write(postfix.getBytes());
                        outputStream.flush();
                        
                        // Close streams
                        inputStream.close();
                        outputStream.close();
                        
                    } catch (IOException e) {
                        Log.e(TAG, "Error streaming file: " + e.getMessage());
                    }
                }
            }
        );
        
        // Start the upload
        relay.uploadFile(
            reqProperties,
            route,
            null, // pathnamePrefix - use null unless required by your infrastructure
            new RelayStreamResponseListener() {
                @Override
                public void relayStreamResponse(int statusCode, boolean success, 
                                               String responseStr, String errorMessage, 
                                               Map<String, List<String>> responseHeaders) {
                    if (success) {
                        Log.d(TAG, "✅ Upload successful!");
                        Log.d(TAG, "Status: " + statusCode);
                        if (responseStr != null) {
                            Log.d(TAG, "Response: " + responseStr);
                        }
                    } else {
                        Log.e(TAG, "❌ Upload failed: " + errorMessage);
                        Log.e(TAG, "Status: " + statusCode);
                    }
                }
            },
            new RelayStreamCompletionCallback() {
                @Override
                public void onProgressUpdate(int bytesCompleted, int totalBytes) {
                    // Calculate and display progress
                    float percentage = (float) bytesCompleted / totalBytes * 100;
                    Log.d(TAG, String.format("Upload progress: %.1f%%", percentage));
                    
                    // Update UI on main thread
                    // runOnUiThread(() -> progressBar.setProgress((int) percentage));
                }
            }
        );
    }
}

// Usage example:
File fileToUpload = new File(getFilesDir(), "large-video.mp4");
FileUploadManager uploadManager = new FileUploadManager(relay);
uploadManager.uploadFile(
    fileToUpload,
    "https://your-relay-server.com",
    "api/files/upload"
);
```

---

### File Download (Streaming)

Download large files efficiently using the streaming download API.

#### Complete Streaming Download Example

```java
import com.mte.relay.RelayFileRequestProperties;
import com.mte.relay.RelayStreamResponseListener;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FileDownloadManager {
    private static final String TAG = "FileDownloadManager";
    private final Relay relay;
    
    public FileDownloadManager(Relay relay) {
        this.relay = relay;
    }
    
    /**
     * Download a file and save it to the specified location
     * @param serverUrl Your MteRelay server URL
     * @param route The download URL route (e.g., "api/files/download/video.mp4")
     * @param downloadPath Local path where file will be saved
     */
    public void downloadFile(String serverUrl, String route, String downloadPath) {
        // Prepare request headers
        Map<String, String> headers = new HashMap<>();
        // Add any headers your API requires
        // headers.put("Authorization", "Bearer your-token");
        
        // Only encrypt headers that actually exist in the request
        String[] headersToEncrypt = new String[] {}; // Or {"Authorization"} if you added one
        
        // Create RelayFileRequestProperties for download
        RelayFileRequestProperties reqProperties = new RelayFileRequestProperties(
            serverUrl,
            route,
            downloadPath,
            headers,
            headersToEncrypt
        );
        
        // Start the download
        relay.downloadFile(
            reqProperties,
            null, // pathnamePrefix - use null unless required by your infrastructure
            new RelayStreamResponseListener() {
                @Override
                public void relayStreamResponse(int statusCode, boolean success, 
                                               String responseStr, String errorMessage, 
                                               Map<String, List<String>> responseHeaders) {
                    if (success) {
                        Log.d(TAG, "✅ Download complete!");
                        Log.d(TAG, "File saved to: " + downloadPath);
                        Log.d(TAG, "Status: " + statusCode);
                        
                        // Verify the file exists
                        File downloadedFile = new File(downloadPath);
                        if (downloadedFile.exists()) {
                            Log.d(TAG, "File size: " + downloadedFile.length() + " bytes");
                        }
                    } else {
                        Log.e(TAG, "❌ Download failed: " + errorMessage);
                        Log.e(TAG, "Status: " + statusCode);
                    }
                }
            }
        );
    }
}

// Usage example:
String downloadPath = new File(getFilesDir(), "downloaded-video.mp4").getAbsolutePath();
FileDownloadManager downloadManager = new FileDownloadManager(relay);
downloadManager.downloadFile(
    "https://your-relay-server.com",
    "api/files/download/video.mp4",
    downloadPath
);
```

---

### Re-Pairing with the Server

**What is pairing?**
When your app first connects to the MteRelay server, it establishes encryption "pairs" - synchronized encryption/decryption states. Sometimes you need to re-establish these pairs, such as:
- After server restart or deployment
- When encountering persistent decryption errors
- When switching between development/production environments
- As part of a security refresh policy

**When to re-pair:**
- After receiving specific error messages from the relay server
- If you implement a periodic re-pairing schedule (e.g., every 24 hours)
- When you detect authentication or encryption failures

```java
// Re-establish encryption pairs with the relay server
// The result is delivered via the RelayResponseListener you provided during initialization
relay.rePairWithRelayServer("https://your-relay-server.com");

// With pathnamePrefix:
String pathnamePrefix = "api/v1";
relay.rePairWithRelayServer("https://your-relay-server.com", pathnamePrefix);
```

**Note:** The pairing result is delivered asynchronously via the `RelayResponseListener.onCompletion()` callback you provided when initializing the Relay.

---

### Adjusting Relay Settings

You can customize how the Relay behaves by adjusting various settings. These affect performance, memory usage, and security.

#### Settings Explained

**`streamChunkSize`** (bytes, default: 65,536 = 64KB)
- Size of chunks when streaming files
- **Larger values** = faster transfer but more memory usage
- **Smaller values** = slower transfer but lower memory footprint
- **Valid range:** 4,096 (4KB) to 10,485,760 (10MB)
- **Recommendations:**
  - **Low-end devices** or **limited bandwidth**: 32KB (32,768)
  - **Standard usage**: 64KB (65,536) - default
  - **High-performance needs**: 256KB - 1MB (262,144 - 1,048,576)

**`pairPoolSize`** (default: 5)
- Number of encryption pairs to maintain per host
- Each pair allows one concurrent request
- **Larger values** = more concurrent requests but more memory
- **Smaller values** = fewer concurrent requests but less overhead
- **Valid range:** 1 to 10
- **Recommendations:**
  - **Light usage** (few concurrent requests): 3
  - **Standard usage**: 5 - default
  - **Heavy usage** (many concurrent requests): 10

**`persistPairs`** (boolean, default: false)
- Whether to save pairs to device storage between app launches
- **`true`** = Faster app startup (no re-pairing needed), but pairs stored on device
- **`false`** = Must re-pair on each app launch, but no persistent storage
- **Recommendations:**
  - **Use `true`** for apps that make frequent restarts and need fast startup
  - **Use `false`** for maximum security or infrequent startups

#### Complete Settings Example

```java
// Adjust settings and re-pair with new configuration
String result = relay.adjustRelaySettings(
    "https://your-relay-server.com",
    null, // pathnamePrefix - use null unless required
    262144,  // 256KB chunks for better performance
    10,      // Support 10 concurrent requests
    true     // Persist pairs for faster app startup
);

Log.d(TAG, "Settings adjustment result: " + result);

// With pathnamePrefix:
String pathnamePrefix = "api/v1";
String result = relay.adjustRelaySettings(
    "https://your-relay-server.com",
    pathnamePrefix,
    262144,  // newStreamChunkSize
    10,      // newPairPoolSize
    true     // persistPairs
);
```

**Note:** Setting any size parameter to `0` means "don't change this setting." If any settings actually change, the Relay will automatically re-pair with the server.

---

### Logging

The MteRelay library includes built-in logging to help you debug issues during development and diagnose problems in production.

#### Log Output

By default, the library logs to **LogCat** with appropriate log levels. You can filter LogCat using the tag `"Relay"` to see library-specific logs.

#### When to Use File Logging

**✅ Enable file logging when:**
- Developing and testing your integration
- Troubleshooting pairing or encryption issues
- Diagnosing network problems
- Investigating user-reported issues (in production, temporarily)

**❌ Consider disabling file logging when:**
- App is stable and working correctly
- Concerned about storage space (logs can grow large)
- Maximum performance is needed

#### Enabling File Logging

```java
import com.mte.relay.Relay;

// Enable file logging
Relay.enableFileLogging(
    "https://your-relay-server.com",
    null, // pathnamePrefix - use null unless required
    true  // isEnabled
);

// Disable file logging
Relay.enableFileLogging(
    "https://your-relay-server.com",
    null,
    false
);
```

#### Reading Log Files

```java
// Read the entire log file contents
String logContents = Relay.readLogFile(
    "https://your-relay-server.com",
    null // pathnamePrefix
);

if (logContents != null && !logContents.isEmpty()) {
    Log.d(TAG, "=== MteRelay Logs ===");
    Log.d(TAG, logContents);
    
    // You might want to:
    // 1. Display in a debug view in your app
    // 2. Send to your analytics service
    // 3. Email to support
} else {
    Log.d(TAG, "No log file exists yet");
}
```

#### Clearing Log Files

```java
// Clear the log file to free up space
Relay.clearLogFile(
    "https://your-relay-server.com",
    null // pathnamePrefix
);

Log.d(TAG, "Log file cleared");
```

#### Interpreting Logs

**Understanding log levels:**
```
TRACE   - Detailed flow information (method entries, request lifecycle)
INFO    - Normal operations (pairing started, request sent, etc.)
WARNING - Potential issues (retry attempts, deprecated API usage)
ERROR   - Failures (pairing failed, decryption error, network timeout)
```

**Common log entries and what they mean:**

```
"Using Relay Version X.X.X and Mte Version Y.Y.Y"
  ✅ Relay initialized successfully

"MTE License Check Failed"
  ❌ MTE license is invalid or expired - check your credentials

"Successfully Re-Paired with <url>"
  ✅ Re-pairing operation completed successfully

"Relay operation failed: ..."
  ❌ An operation failed - check the message for details
```

#### Complete Logging Example

```java
public class DebugActivity extends AppCompatActivity {
    private static final String SERVER_URL = "https://your-relay-server.com";
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_debug);
        
        // Enable file logging for debugging
        Relay.enableFileLogging(SERVER_URL, null, true);
    }
    
    public void showLogs(View view) {
        String logs = Relay.readLogFile(SERVER_URL, null);
        
        if (logs != null && !logs.isEmpty()) {
            // Display in a dialog or TextView
            new AlertDialog.Builder(this)
                .setTitle("Debug Logs")
                .setMessage(logs)
                .setPositiveButton("OK", null)
                .setNegativeButton("Clear Logs", (dialog, which) -> {
                    Relay.clearLogFile(SERVER_URL, null);
                    Toast.makeText(this, "Logs cleared", Toast.LENGTH_SHORT).show();
                })
                .show();
        } else {
            Toast.makeText(this, "No logs available", Toast.LENGTH_SHORT).show();
        }
    }
    
    public void clearLogs(View view) {
        Relay.clearLogFile(SERVER_URL, null);
        Toast.makeText(this, "Logs cleared successfully", Toast.LENGTH_SHORT).show();
    }
}
```

**Best Practice:**
In production apps, consider adding a hidden debug menu (e.g., tap app version 5 times) that allows you to:
1. Enable/disable file logging
2. View current logs
3. Export logs for support
4. Clear logs

---

## Listeners Reference

The MteRelay library uses listener interfaces to communicate asynchronous events back to your app. Understanding these listeners is crucial for proper integration.

### RelayResponseListener (Required)

**Purpose:** Receives responses from pairing operations and general relay events.

**When to use:** You must implement this listener when initializing the Relay singleton.

```java
public interface RelayResponseListener {
    void onCompletion(Boolean success, String message);
}
```

**Parameters:**
- `success`: `true` if operation succeeded, `false` if failed
- `message`: Description of what happened (e.g., "Successfully Re-Paired with server")

**Example implementation:**
```java
Relay relay = Relay.getInstance(context, new RelayResponseListener() {
    @Override
    public void onCompletion(Boolean success, String message) {
        if (success) {
            Log.d(TAG, "✅ Relay operation successful: " + message);
            // Update UI to show ready state
        } else {
            Log.e(TAG, "❌ Relay operation failed: " + message);
            // Show error alert to user
            // Maybe attempt retry
        }
    }
});
```

---

### RelayVolleyRequestListener (For Volley Requests)

**Purpose:** Receives responses from Volley HTTP requests.

**When to use:** Required when calling `addToMteRequestQueue()` with Volley requests.

```java
public interface RelayVolleyRequestListener {
    void onError(NetworkResponse networkResponse, String message, 
                 Map<String, List<String>> responseHeaders);
    
    void onResponse(NetworkResponse networkResponse, byte[] responseBytes, 
                   Map<String, List<String>> responseHeaders);
}
```

**Parameters:**
- `networkResponse`: The Volley NetworkResponse object (may be null on connection errors)
- `message`: Error message (for onError)
- `responseBytes`: Response body as byte array
- `responseHeaders`: HTTP response headers

**Example implementation:**
```java
new RelayVolleyRequestListener() {
    @Override
    public void onError(NetworkResponse networkResponse, String message, 
                        Map<String, List<String>> responseHeaders) {
        Log.e(TAG, "Request failed: " + message);
        if (networkResponse != null) {
            Log.e(TAG, "Status: " + networkResponse.statusCode);
        }
    }

    @Override
    public void onResponse(NetworkResponse networkResponse, byte[] responseBytes, 
                          Map<String, List<String>> responseHeaders) {
        String response = new String(responseBytes);
        Log.d(TAG, "Response received: " + response);
    }
}
```

---

### RelayOkHttpRequestListener (For OkHttp Requests)

**Purpose:** Receives responses from OkHttp HTTP requests.

**When to use:** Required when calling `send()` with OkHttp requests.

```java
public interface RelayOkHttpRequestListener {
    void onError(Response response);
    void onResponse(Response response);
}
```

**Parameters:**
- `response`: The OkHttp Response object

**Example implementation:**
```java
new RelayOkHttpRequestListener() {
    @Override
    public void onError(Response response) {
        if (response != null) {
            Log.e(TAG, "Request failed with status: " + response.code());
        } else {
            Log.e(TAG, "Request failed - no response");
        }
    }

    @Override
    public void onResponse(Response response) {
        try {
            String body = response.body().string();
            Log.d(TAG, "Response: " + body);
        } catch (IOException e) {
            Log.e(TAG, "Error reading response: " + e.getMessage());
        }
    }
}
```

---

### RelayStreamCallback (For File Uploads)

**Purpose:** Provides file data to upload when streaming files.

**When to use:** Required when creating `RelayFileRequestProperties` for uploads.

```java
public interface RelayStreamCallback {
    void getRequestBodyStream(PipedOutputStream outputStream);
}
```

**Parameters:**
- `outputStream`: The stream to write your file data to

**What this does:** The Relay calls this method when it's ready to send data. You must write the complete request body to `outputStream` and close it when done.

**Example implementation:**
```java
new RelayStreamCallback() {
    @Override
    public void getRequestBodyStream(PipedOutputStream outputStream) {
        try {
            // Write file data to stream
            FileInputStream fis = new FileInputStream(file);
            byte[] buffer = new byte[65536];
            int bytesRead;
            
            while ((bytesRead = fis.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
                outputStream.flush();
            }
            
            fis.close();
            outputStream.close();
        } catch (IOException e) {
            Log.e(TAG, "Error streaming: " + e.getMessage());
        }
    }
}
```

---

### RelayStreamResponseListener (For Streaming Operations)

**Purpose:** Receives the final response when a streaming operation (upload or download) completes.

**When to use:** Required when using `uploadFile()` or `downloadFile()` to know when the operation finishes.

```java
public interface RelayStreamResponseListener {
    void relayStreamResponse(int statusCode, boolean success, String responseStr, 
                            String errorMessage, Map<String, List<String>> responseHeaders);
}
```

**Parameters:**
- `statusCode`: HTTP status code (-1 if connection failed)
- `success`: Whether the operation succeeded
- `responseStr`: Response body (if any)
- `errorMessage`: Error description if operation failed
- `responseHeaders`: HTTP response headers

**Example implementation:**
```java
new RelayStreamResponseListener() {
    @Override
    public void relayStreamResponse(int statusCode, boolean success, 
                                   String responseStr, String errorMessage, 
                                   Map<String, List<String>> responseHeaders) {
        if (success) {
            Log.d(TAG, "Operation successful! Status: " + statusCode);
            if (responseStr != null) {
                Log.d(TAG, "Response: " + responseStr);
            }
        } else {
            Log.e(TAG, "Operation failed: " + errorMessage);
        }
    }
}
```

---

### RelayStreamCompletionCallback (For Progress Tracking)

**Purpose:** Provides progress updates during streaming operations.

**When to use:** Optional - set this callback when you want to show upload progress to the user.

```java
public interface RelayStreamCompletionCallback {
    void onProgressUpdate(int bytesCompleted, int totalBytes);
}
```

**Parameters:**
- `bytesCompleted`: Number of bytes transferred so far
- `totalBytes`: Total bytes to transfer

**Example implementation:**
```java
new RelayStreamCompletionCallback() {
    @Override
    public void onProgressUpdate(int bytesCompleted, int totalBytes) {
        float percentage = (float) bytesCompleted / totalBytes * 100;
        
        runOnUiThread(() -> {
            // Update UI on main thread
            progressBar.setProgress((int) percentage);
            statusText.setText(String.format("%.1f%% (%d / %d bytes)", 
                percentage, bytesCompleted, totalBytes));
        });
    }
}
```

---

### Summary: Which Listeners Do I Need?

| Operation | Required Listeners | Optional Listeners |
|-----------|-------------------|-------------------|
| Initialize Relay | `RelayResponseListener` | None |
| Volley requests | `RelayResponseListener`<br>`RelayVolleyRequestListener` | None |
| OkHttp requests | `RelayResponseListener`<br>`RelayOkHttpRequestListener` | None |
| File upload | `RelayResponseListener`<br>`RelayStreamCallback`<br>`RelayStreamResponseListener` | `RelayStreamCompletionCallback` |
| File download | `RelayResponseListener`<br>`RelayStreamResponseListener` | None |
| Re-pairing | `RelayResponseListener` | None |
| Adjust settings | `RelayResponseListener` | None |

---

## Troubleshooting

This section covers common issues and their solutions.

### Initialization Issues

**Problem: "MTE License Check Failed" error**
```
RelayException: MTE License Check Failed
```
**Solutions:**
1. Verify your license credentials are correctly configured
2. Contact Eclypses support to verify your license is active
3. Ensure your application ID matches your license

---

**Problem: NullPointerException in getInstance()**
**Solutions:**
1. Ensure you're calling `Relay.getInstance()` with a valid Context
2. Use `getApplicationContext()` instead of Activity context when possible:
   ```java
   Relay relay = Relay.getInstance(getApplicationContext(), listener);
   ```
3. Don't call `getInstance()` before `onCreate()` completes

---

### Pairing Issues

**Problem: Pairing fails repeatedly**
```
RelayResponseListener.onCompletion(false, "...")
```
**Solutions:**
1. **Check relay server is running:**
   ```bash
   curl https://your-relay-server.com/health
   ```
2. **Verify server URL is correct:**
   - No trailing slashes
   - Correct protocol (https:// vs http://)
   - Correct port if using non-standard
3. **Check network connectivity:**
   - Device has internet access
   - No firewall blocking the connection
   - VPN not interfering
4. **Check server logs** for errors on the relay server side
5. **Try re-pairing manually:**
   ```java
   relay.rePairWithRelayServer(serverUrl);
   ```

---

### Network & Connection Issues

**Problem: Requests fail with timeout errors**
**Solutions:**
1. Check relay server is responsive
2. Verify network connectivity on the device
3. For large files, ensure chunk size is appropriate:
   ```java
   relay.adjustRelaySettings(serverUrl, null, 65536, 5, false);
   ```

---

**Problem: "ServerUrl must be a valid String path"**
**Solutions:**
1. Ensure your server URL is not null or empty:
   ```java
   String serverUrl = "https://your-relay-server.com"; // Not null!
   ```
2. Check URL is properly formatted (no spaces, valid characters)

---

### Streaming Issues

**Problem: File uploads fail or freeze**
**Solutions:**
1. **Verify file exists and is readable:**
   ```java
   File file = new File(filePath);
   if (!file.exists() || !file.canRead()) {
       Log.e(TAG, "File not accessible");
       return;
   }
   ```
2. **Check file size:**
   ```java
   Log.d(TAG, "File size: " + file.length() + " bytes");
   ```
3. **Ensure the RelayStreamCallback closes the stream:**
   ```java
   @Override
   public void getRequestBodyStream(PipedOutputStream outputStream) {
       try {
           // ... write data ...
           outputStream.close(); // Don't forget this!
       } catch (IOException e) {
           Log.e(TAG, e.getMessage());
       }
   }
   ```
4. **Verify Content-Type header is set correctly**

---

**Problem: File upload fails after 10MB**
**Solutions:**
1. Verify your server supports streamed uploads
2. Check server-side file size limits
3. Consider increasing the stream chunk size:
   ```java
   relay.adjustRelaySettings(serverUrl, null, 262144, 5, false); // 256KB chunks
   ```

---

**Problem: Downloads fail or save corrupted files**
**Solutions:**
1. **Verify download directory exists:**
   ```java
   File downloadDir = new File(downloadPath).getParentFile();
   if (!downloadDir.exists()) {
       downloadDir.mkdirs();
   }
   ```
2. **Check the app has write permissions:**
   ```xml
   <!-- In AndroidManifest.xml -->
   <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
   ```
3. **Verify response status code is successful (200-299)**

---

### Memory & Performance Issues

**Problem: High memory usage during file operations**
**Solutions:**
1. **Use streaming APIs for large files** - don't load entire files into memory
2. **Reduce chunk size:**
   ```java
   relay.adjustRelaySettings(serverUrl, null, 32768, 5, false); // 32KB
   ```
3. **Reduce pair pool size if making few concurrent requests:**
   ```java
   relay.adjustRelaySettings(serverUrl, null, 0, 3, false);
   ```

---

**Problem: Slow performance**
**Solutions:**
1. **Increase chunk size for better throughput:**
   ```java
   relay.adjustRelaySettings(serverUrl, null, 262144, 0, false); // 256KB
   ```
2. **Enable pair persistence to avoid re-pairing:**
   ```java
   relay.adjustRelaySettings(serverUrl, null, 0, 0, true);
   ```
3. **Increase pair pool size for more concurrent requests:**
   ```java
   relay.adjustRelaySettings(serverUrl, null, 0, 10, false);
   ```
4. **Check network conditions** (Wi-Fi vs cellular)

---

### Debugging Tips

1. **Enable file logging:**
   ```java
   Relay.enableFileLogging(serverUrl, null, true);
   ```

2. **Check LogCat** with filter `"Relay"` for library logs

3. **Use network debugging tools:**
   - Android Studio's Network Profiler
   - Charles Proxy or mitmproxy for traffic inspection

4. **Check both client and server logs** - issues can be on either side

5. **Test with a simple request first:**
   ```java
   Request request = new Request.Builder()
       .url("https://your-relay-server.com/ping")
       .build();
   
   relay.send(request, null, new RelayOkHttpRequestListener() {
       @Override
       public void onError(Response response) {
           Log.e(TAG, "Simple request failed");
       }
       
       @Override
       public void onResponse(Response response) {
           Log.d(TAG, "Simple request succeeded: " + response.code());
       }
   });
   ```

6. **Get the list of paired hosts:**
   ```java
   String[] hosts = relay.getHostList();
   Log.d(TAG, "Paired hosts: " + Arrays.toString(hosts));
   ```

---

### Getting Help

If you're still stuck after trying these solutions:

1. **Gather information:**
   - Android version
   - Device model
   - MteRelay library version
   - Error messages from logs
   - Steps to reproduce

2. **Check official documentation:**
   - [Getting Started Guide](https://public-docs.eclypses.com/docs/mte-relay-server/client-libraries/android)
   - Server-side MteRelay documentation

3. **Contact support:**
   - Email: [info@eclypses.com](mailto:info@eclypses.com)
   - Developer Portal: [developers.eclypses.com/dashboard](https://developers.eclypses.com/dashboard)
   - Include logs and detailed description

---

## API Reference

Complete reference of the main Relay class methods.

### Relay Class Methods

#### `getInstance(Context context, RelayResponseListener listener)`
Gets or creates the Relay singleton instance.
- **Parameters:**
  - `context`: Android Context (recommend using `getApplicationContext()`)
  - `listener`: Callback for pairing operations and errors
- **Returns:** `Relay` instance
- **Throws:** `RelayException` if MTE license validation fails
- **Usage:** `Relay relay = Relay.getInstance(context, listener);`

---

#### `addToMteRequestQueue(Request<T> req, String[] headersToEncrypt, RelayVolleyRequestListener listener)`
Sends a Volley request through MteRelay.
- **Parameters:**
  - `req`: Volley Request object
  - `headersToEncrypt`: Array of header names to encrypt (may be null)
  - `listener`: Callback for response/error
- **Returns:** void
- **Usage:** See "Volley Requests" section above

---

#### `addToMteRequestQueue(Request<T> req, String[] headersToEncrypt, String pathnamePrefix, RelayVolleyRequestListener listener)`
Sends a Volley request with pathname prefix.
- **Parameters:**
  - `req`: Volley Request object
  - `headersToEncrypt`: Array of header names to encrypt (may be null)
  - `pathnamePrefix`: Path prefix for relay routing (may be null)
  - `listener`: Callback for response/error
- **Returns:** void

---

#### `send(okhttp3.Request req, String[] headersToEncrypt, RelayOkHttpRequestListener listener)`
Sends an OkHttp request through MteRelay.
- **Parameters:**
  - `req`: OkHttp Request object
  - `headersToEncrypt`: Array of header names to encrypt (may be null)
  - `listener`: Callback for response/error
- **Returns:** void
- **Usage:** See "OkHttp Requests" section above

---

#### `send(okhttp3.Request req, String[] headersToEncrypt, String pathnamePrefix, RelayOkHttpRequestListener listener)`
Sends an OkHttp request with pathname prefix.
- **Parameters:**
  - `req`: OkHttp Request object
  - `headersToEncrypt`: Array of header names to encrypt (may be null)
  - `pathnamePrefix`: Path prefix for relay routing (may be null)
  - `listener`: Callback for response/error
- **Returns:** void

---

#### `uploadFile(RelayFileRequestProperties reqProperties, String route, RelayStreamResponseListener listener, RelayStreamCompletionCallback completionCallback)`
Uploads a file using streaming.
- **Parameters:**
  - `reqProperties`: File upload configuration
  - `route`: API route for the upload endpoint
  - `listener`: Callback for completion
  - `completionCallback`: Callback for progress updates (may be null)
- **Returns:** void
- **Usage:** See "File Upload (Streaming)" section above

---

#### `uploadFile(RelayFileRequestProperties reqProperties, String route, String pathnamePrefix, RelayStreamResponseListener listener, RelayStreamCompletionCallback completionCallback)`
Uploads a file with pathname prefix.
- **Parameters:**
  - `reqProperties`: File upload configuration
  - `route`: API route for the upload endpoint
  - `pathnamePrefix`: Path prefix for relay routing (may be null)
  - `listener`: Callback for completion
  - `completionCallback`: Callback for progress updates (may be null)
- **Returns:** void

---

#### `downloadFile(RelayFileRequestProperties reqProperties, RelayStreamResponseListener listener)`
Downloads a file using streaming.
- **Parameters:**
  - `reqProperties`: File download configuration
  - `listener`: Callback for completion
- **Returns:** void
- **Usage:** See "File Download (Streaming)" section above

---

#### `downloadFile(RelayFileRequestProperties reqProperties, String pathnamePrefix, RelayStreamResponseListener listener)`
Downloads a file with pathname prefix.
- **Parameters:**
  - `reqProperties`: File download configuration
  - `pathnamePrefix`: Path prefix for relay routing (may be null)
  - `listener`: Callback for completion
- **Returns:** void

---

#### `rePairWithRelayServer(String serverUrl)`
Re-establishes encryption pairs with the relay server.
- **Parameters:**
  - `serverUrl`: URL of your relay server
- **Returns:** void (result delivered via RelayResponseListener)
- **Usage:** `relay.rePairWithRelayServer("https://your-server.com");`

---

#### `rePairWithRelayServer(String serverUrl, String pathnamePrefix)`
Re-establishes encryption pairs with pathname prefix.
- **Parameters:**
  - `serverUrl`: URL of your relay server
  - `pathnamePrefix`: Path prefix (may be null)
- **Returns:** void (result delivered via RelayResponseListener)

---

#### `adjustRelaySettings(String serverUrl, int newStreamChunkSize, int newPairPoolSize, Boolean persistPairs)`
Updates relay configuration.
- **Parameters:**
  - `serverUrl`: URL of your relay server
  - `newStreamChunkSize`: Chunk size in bytes (0 = no change)
  - `newPairPoolSize`: Number of pairs to maintain (0 = no change)
  - `persistPairs`: Whether to persist pairs to storage
- **Returns:** `String` describing what changed
- **Usage:** See "Adjusting Relay Settings" section above

---

#### `adjustRelaySettings(String serverUrl, String pathnamePrefix, int newStreamChunkSize, int newPairPoolSize, Boolean persistPairs)`
Updates relay configuration with pathname prefix.
- **Parameters:**
  - `serverUrl`: URL of your relay server
  - `pathnamePrefix`: Path prefix (may be null)
  - `newStreamChunkSize`: Chunk size in bytes (0 = no change)
  - `newPairPoolSize`: Number of pairs to maintain (0 = no change)
  - `persistPairs`: Whether to persist pairs to storage
- **Returns:** `String` describing what changed

---

#### `getHostList()`
Gets array of all paired host URLs.
- **Returns:** `String[]` of paired host URLs
- **Usage:** `String[] hosts = relay.getHostList();`

---

### Static Methods

#### `enableFileLogging(String serverUrl, Boolean isEnabled)`
Enables or disables file logging.
- **Parameters:**
  - `serverUrl`: URL of your relay server
  - `isEnabled`: true to enable, false to disable
- **Usage:** `Relay.enableFileLogging(serverUrl, true);`

---

#### `enableFileLogging(String serverUrl, String pathnamePrefix, Boolean isEnabled)`
Enables or disables file logging with pathname prefix.
- **Parameters:**
  - `serverUrl`: URL of your relay server
  - `pathnamePrefix`: Path prefix (may be null)
  - `isEnabled`: true to enable, false to disable

---

#### `readLogFile(String serverUrl)`
Reads the contents of the log file.
- **Parameters:**
  - `serverUrl`: URL of your relay server
- **Returns:** Log file contents as String
- **Usage:** `String logs = Relay.readLogFile(serverUrl);`

---

#### `readLogFile(String serverUrl, String pathnamePrefix)`
Reads log file with pathname prefix.
- **Parameters:**
  - `serverUrl`: URL of your relay server
  - `pathnamePrefix`: Path prefix (may be null)
- **Returns:** Log file contents as String

---

#### `clearLogFile(String serverUrl)`
Clears the log file.
- **Parameters:**
  - `serverUrl`: URL of your relay server
- **Usage:** `Relay.clearLogFile(serverUrl);`

---

#### `clearLogFile(String serverUrl, String pathnamePrefix)`
Clears log file with pathname prefix.
- **Parameters:**
  - `serverUrl`: URL of your relay server
  - `pathnamePrefix`: Path prefix (may be null)

---

### Key Classes and Interfaces

- **`Relay`** - Main entry point (singleton) for secure requests and file streaming
- **`RelayResponseListener`** - Interface for receiving pairing responses and errors
- **`RelayVolleyRequestListener`** - Interface for receiving Volley request responses
- **`RelayOkHttpRequestListener`** - Interface for receiving OkHttp request responses
- **`RelayStreamCallback`** - Interface for providing file data during upload
- **`RelayStreamResponseListener`** - Interface for receiving streaming operation results
- **`RelayStreamCompletionCallback`** - Interface for receiving progress updates
- **`RelayFileRequestProperties`** - Configuration class for file upload/download operations

---

## Support

**Email:** [info@eclypses.com](mailto:info@eclypses.com)  
**Web:** [www.eclypses.com](https://www.eclypses.com)  
**Developer Portal:** [developers.eclypses.com/dashboard](https://developers.eclypses.com/dashboard)

---

<p style="font-size: 8pt; margin-bottom: 0; margin: 100px 24px 30px 24px;">
<b>All trademarks of Eclypses Inc.</b> may not be used without Eclypses Inc.'s prior written consent. No license for any use thereof has been granted without express written consent. Any unauthorized use thereof may violate copyright laws, trademark laws, privacy and publicity laws and communications regulations and statutes. The names, images and likeness of the Eclypses logo, along with all representations thereof, are valuable intellectual property assets of Eclypses, Inc. Accordingly, no party or parties, without the prior written consent of Eclypses, Inc., (which may be withheld in Eclypses' sole discretion), use or permit the use of any of the Eclypses trademarked names or logos of Eclypses, Inc. for any purpose other than as part of the address for the Premises, or use or permit the use of, for any purpose whatsoever, any image or rendering of, or any design based on, the exterior appearance or profile of the Eclypses trademarks and or logo(s).
</p>
