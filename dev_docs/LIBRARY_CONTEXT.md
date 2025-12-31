# MTE Relay Client - Android Library Context

## 1. Project Overview
This project (`eclypses-aws-mte-relay-client-android`) is an Android AAR library designed to facilitate **MTE (MicroToken Exchange)** encrypted communication between an Android application and an **MTE Relay Server**.

**Primary Goal:** To intercept standard HTTP requests, encrypt sensitive headers and payloads on the device, and tunnel them through a Relay Server to the final customer backend. This ensures end-to-end security and obscures the backend endpoints.

## 2. Intended Use
This library is a **client-side wrapper** for networking. It is **not** a transparent interceptor; it requires developers to use specific library methods to send requests.

*   **Supported Stacks:** Volley, OkHttp.
*   **Core Features:**
    *   Secure GET/POST requests.
    *   Encrypted Header protection.
    *   Streamed File Uploads/Downloads (for handling large files >10MB).
    *   Automatic MTE State management (Pairing/Re-pairing).

## 3. Core Architecture
The library uses a **Singleton-Host** pattern to manage connections and encryption states.

### A. `Relay` Singleton (`com.mte.relay.Relay`)
*   **Role**: The public facade and entry point.
*   **Responsibility**:
    *   Initializes the MTE license and environment.
    *   Manages a registry of `Host` objects (one per Relay Server URL).
    *   Exposes the public API (`addToMteRequestQueue`, `send`, `uploadFile`).
*   **Lifecycle**: Initialized once via `Relay.getInstance(context, listener)`.

### B. `Host` Class (`com.mte.relay.Host`)
*   **Role**: Represents a connection to a specific Relay Server Endpoint.
*   **Responsibility**:
    *   **Pairing**: Performs the MTE handshake with the server to synchronize crypto states.
    *   **Concurrency**: Maintains a **Pool of Pairs** (configured via `RelaySettings.pairPoolSize`) to allow multiple simultaneous requests without sequence errors.
    *   **Crypto Operations**: Handles the actual encryption of request bodies/headers and decryption of responses.
    *   **Auto-Recovery**: Automatically attempts to "Re-Pair" if the server rejects a request due to state mismatch.

## 4. Data Flow
1.  **Request Initiation**: The app creates a standard `Volley` or `OkHttp` request object targeting the **Relay Server URL**.
2.  **Delegation**: The app passes this request to the `Relay` singleton.
3.  **Host Resolution**: `Relay` delegates to the `Host` instance matching the URL.
4.  **Encryption**:
    *   The `Host` checks out an MTE Pair.
    *   The payload and specified headers are encrypted.
5.  **Transmission**: The modified request is sent to the Relay Server.
6.  **Relay Processing (Server-Side)**: The Relay Server decrypts the traffic and proxies it to the actual Backend API.
7.  **Response**: The Backend responds -> Relay Server encrypts -> `Host` receives encrypted response.
8.  **Decryption**: The `Host` decrypts the response and invokes the App's callback with cleartext data.

## 5. Key File Structure
*   **`Relay.java`**: Public API surface.
*   **`Host.java`**: Internal logic for MTE state, pairing, and request wrapping.
*   **`RelaySettings.java`**: Static configuration (Version, Chunk Sizes, License Keys).
*   **`*Helper.java`**: Utilities for File I/O (`FileUploadHelper`), Logging (`LogHelper`), and MTE wrappers (`MteHelper`).
*   **`*Listener.java`**: Interfaces for asynchronous callbacks.

## 6. Configuration
Configuration is primarily handled in `RelaySettings.java` (internally) or via `Relay.adjustRelaySettings` (runtime).
*   **`pairPoolSize`**: Number of concurrent MTE states per host (Default: 5).
*   **`streamChunkSize`**: Buffer size for file streaming operations (Default: 64KB).