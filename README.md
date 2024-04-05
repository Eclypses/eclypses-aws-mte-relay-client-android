<center>
<img src="Eclypses.png" style="width:50%;"/>
</center>

<div align="center" style="font-size:40pt; font-weight:900; font-family:arial; margin-top:50px;" >
The Android Java MTE Relay Library</div>
<br><br><br>

# Introduction 
This AAR library provides the Java language Eclypses MteRelay Client library.

The current version of this library is 4.1.0 (identical to the MTE version being used).
<br><br><br>

# Getting Started
This guide assumes a working knowledge of including an AAR library (either from a local directory on your computer or directly from a git site) in your Android project.
1.	The most simple way to use the library is to list it as a dependency for your app (Module Settings / Dependencies).\
Copy the prebuilt `package-java-mte-android-relay-x.x.x-release.aar` file (x.x.x repesenting the version number) from the root directory of this repository to your `./<your_project>/app/libs/` directory.
2.	**NOTE - Currently, this library supports Volley requests for simple GET and POST requests. Additionally, file streamed uploads and downloads are supported. See below for implementation details.**
3. In the class where you will maintain the Relay reference ... 
   - Create a class variable for the relay singleton. 
   - Currently, the Relay library supports a String array containing a single hostApi url. Support for multiple Relay Host Api's coming soon!
   - Instantiate the Relay class, passing the Context and the String aray with the hostApi url and store the object in the class Relay variable.

# Simple Volley GET and POST requests
   
1. After creating your Volley request, instead of calling `RequestSingleton.getInstance(context).addToRequestQueue(request);`, call relay.addToMteRequestQueue(), passing the request object and a new RelayResponseListener.


<div style="page-break-after: always; break-after: page;"></div>


# Contact Eclypses

<p align="center" style="font-weight: bold; font-size: 20pt;">Email: <a href="mailto:info@eclypses.com">info@eclypses.com</a></p>
<p align="center" style="font-weight: bold; font-size: 20pt;">Web: <a href="https://www.eclypses.com">www.eclypses.com</a></p>
<p align="center" style="font-weight: bold; font-size: 20pt;">Chat with us: <a href="https://developers.eclypses.com/dashboard">Developer Portal</a></p>
<p style="font-size: 8pt; margin-bottom: 0; margin: 100px 24px 30px 24px; " >
<b>All trademarks of Eclypses Inc.</b> may not be used without Eclypses Inc.'s prior written consent. No license for any use thereof has been granted without express written consent. Any unauthorized use thereof may violate copyright laws, trademark laws, privacy and publicity laws and communications regulations and statutes. The names, images and likeness of the Eclypses logo, along with all representations thereof, are valuable intellectual property assets of Eclypses, Inc. Accordingly, no party or parties, without the prior written consent of Eclypses, Inc., (which may be withheld in Eclypses' sole discretion), use or permit the use of any of the Eclypses trademarked names or logos of Eclypses, Inc. for any purpose other than as part of the address for the Premises, or use or permit the use of, for any purpose whatsoever, any image or rendering of, or any design based on, the exterior appearance or profile of the Eclypses trademarks and or logo(s).
</p>
