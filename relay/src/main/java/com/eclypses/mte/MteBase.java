// The MIT License (MIT)
//
// Copyright (c) Eclypses, Inc.
//
// All rights reserved.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in
// all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.
package com.eclypses.mte;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

// Class MteBase
//
// This is the base for encoders and decoders.
//
// To use, derive from this class to create an encoder or decoder.
public class MteBase
{
  // Interface of an entropy input callback.
  public interface EntropyCallback
  {
    // Class to hold a byte buffer and status.
    public static class BuffStatus
    {
      // The buffer and status.
      public ByteBuffer buff;
      public MteStatus status;
    }

    // The returned buffer must be a direct-allocated buffer with the capacity
    // set to the entropy length.
    BuffStatus entropyCallback(int minEntropy, int minLength, long maxLength);
  }

  // Interface of a nonce callback.
  public interface NonceCallback
  {
    int nonceCallback(int minLength, int maxLength, ByteBuffer nonce);
  }

  // Interface of a timestamp callback.
  public interface TimestampCallback
  {
    long timestampCallback();
  }

  // Returns the MTE version number as a string or individual integer parts.
  public static native String getVersion();
  public static native int getVersionMajor();
  public static native int getVersionMinor();
  public static native int getVersionPatch();

  // Initialize with the company name and license code. Returns true if
  // successful or false if not. If true is returned, MTE functions are usable;
  // otherwise functions that return a status will return an error status.
  public static native boolean initLicense(String company, String license);

  // Returns the count of status codes.
  public static native int getStatusCount();

  // Returns the enumeration name for the given status.
  public static String getStatusName(MteStatus status)
  {
    return statusName(status.getValue());
  }

  // Returns the description for the given status.
  public static String getStatusDescription(MteStatus status)
  {
    return statusDescription(status.getValue());
  }

  // Returns the status code for the given enumeration name.
  public static MteStatus getStatusCode(String name)
  {
    return MteStatus.valueOf(statusCode(name));
  }

  // Returns true if the given status is an error, false if it is success or a
  // warning.
  public static boolean statusIsError(MteStatus status)
  {
    return statusIsError(status.getValue());
  }

  // Returns true if runtime options are available or false if not.
  public static native boolean hasRuntimeOpts();

  // Returns the default DRBG. If runtime options are not available, this is
  // the only option available; otherwise it is a suitable default.
  public static MteDrbgs getDefaultDrbg()
  {
    return MteDrbgs.valueOf(defaultDrbg());
  }

  // Returns the default token size. If runtime options are not available, this
  // is the only option available; otherwise it is a suitable default.
  public static native int getDefaultTokBytes();

  // Returns the default verifiers. If runtime options are not available, this
  // is the only option available; otherwise it is a suitable default.
  public static MteVerifiers getDefaultVerifiers()
  {
    return MteVerifiers.valueOf(defaultVerifiers());
  }

  // Returns the default cipher. If runtime options are not available, this is
  // the only option available; otherwise it is a suitable default.
  public static MteCiphers getDefaultCipher()
  {
    return MteCiphers.valueOf(defaultCipher());
  }

  // Returns the default hash. If runtime options are not available, this is
  // the only option available; otherwise it is a suitable default.
  public static MteHashes getDefaultHash()
  {
    return MteHashes.valueOf(defaultHash());
  }

  // Returns the count of DRBG algorithms.
  public static native int getDrbgsCount();

  // Returns the enumeration name for the given algorithm.
  public static String getDrbgsName(MteDrbgs algo)
  {
    return drbgsName(algo.getValue());
  }

  // Returns the algorithm for the given enumeration name.
  public static MteDrbgs getDrbgsAlgo(String name)
  {
    return MteDrbgs.valueOf(drbgsAlgo(name));
  }

  // Returns the security strength for the given algorithm.
  public static int getDrbgsSecStrengthBytes(MteDrbgs algo)
  {
    return drbgsSecStrengthBytes(algo.getValue());
  }

  // Returns the minimum/maximum personalization string size for the given
  // algorithm.
  public static int getDrbgsPersonalMinBytes(MteDrbgs algo)
  {
    return drbgsPersonalMinBytes(algo.getValue());
  }
  public static long getDrbgsPersonalMaxBytes(MteDrbgs algo)
  {
    return drbgsPersonalMaxBytes(algo.getValue());
  }

  // Returns the minimum/maximum entropy size for the given algorithm.
  public static int getDrbgsEntropyMinBytes(MteDrbgs algo)
  {
    return drbgsEntropyMinBytes(algo.getValue());
  }
  public static long getDrbgsEntropyMaxBytes(MteDrbgs algo)
  {
    return drbgsEntropyMaxBytes(algo.getValue());
  }

  // Returns the minimum/maximum nonce size for the given algorithm.
  public static int getDrbgsNonceMinBytes(MteDrbgs algo)
  {
    return drbgsNonceMinBytes(algo.getValue());
  }
  public static int getDrbgsNonceMaxBytes(MteDrbgs algo)
  {
    return drbgsNonceMaxBytes(algo.getValue());
  }

  // Returns the reseed interval for the given algorithm.
  public static long getDrbgsReseedInterval(MteDrbgs algo)
  {
    return drbgsReseedInterval(algo.getValue());
  }

  // Set the increment DRBG to return an error during instantiation and
  // uninstantiation (if true) or not (if false). This is useful for testing
  // error handling. The flag is false until set with this.
  public static native void setIncrInstError(boolean flag);

  // Set the increment DRBG to produce an error after the given number of
  // values have been generated (if flag is true) or turn off errors (if flag
  // is false) other than the reseed error, which is always produced when the
  // seed interval is reached. The flag is false until set with this.
  public static native void setIncrGenError(boolean flag, int after);

  // Returns the count of verifier algorithms.
  public static native int getVerifiersCount();

  // Returns the enumeration name for the given algorithm.
  public static String getVerifiersName(MteVerifiers algo)
  {
    return verifiersName(algo.getValue());
  }

  // Returns the algorithm for the given enumeration name.
  public static MteVerifiers getVerifiersAlgo(String name)
  {
    return MteVerifiers.valueOf(verifiersAlgo(name));
  }

  // Returns the count of cipher algorithms.
  public static native int getCiphersCount();

  // Returns the enumeration name for the given algorithm.
  public static String getCiphersName(MteCiphers algo)
  {
    return ciphersName(algo.getValue());
  }

  // Returns the algorithm for the given enumeration name.
  public static MteCiphers getCiphersAlgo(String name)
  {
    return MteCiphers.valueOf(ciphersAlgo(name));
  }

  // Returns the block size for the given algorithm.
  public static int getCiphersBlockBytes(MteCiphers algo)
  {
    return ciphersBlockBytes(algo.getValue());
  }

  // Returns the count of hash algorithms.
  public static native int getHashesCount();

  // Returns the enumeration name for the given algorithm.
  public static String getHashesName(MteHashes algo)
  {
    return hashesName(algo.getValue());
  }

  // Returns the algorithm for the given enumeration name.
  public static MteHashes getHashesAlgo(String name)
  {
    return MteHashes.valueOf(hashesAlgo(name));
  }

  // Class to hold a byte array result and status.
  public static class ArrStatus
  {
    // The array and status.
    public byte[] arr;
    public MteStatus status;
  }

  // Class to hold a string result and status.
  public static class StrStatus
  {
    // The string and status.
    public String str;
    public MteStatus status;
  }

  // Class to hold an offset, length, and status.
  public static class OffLenStatus
  {
    // The offset, length, and status.
    public int off;
    public int bytes;
    public MteStatus status;
  }

  // Constructor. Derived classes must call initBase() from their constructor.
  public MteBase() { }

  // Return the options in use.
  public MteDrbgs getDrbg() { return myDrbg; }
  public int getTokBytes() { return myTokBytes; }
  public MteVerifiers getVerifiers() { return myVerifiers; }
  public MteCiphers getCipher() { return myCipher; }
  public MteHashes getHash() { return myHash; }

  // Set the entropy callback. If not null, it is called to get entropy. If
  // null, the entropy set with setEntropy() is used.
  public void setEntropyCallback(EntropyCallback cb)
  {
    myEntropyCb = cb;
  }

  // Set the entropy input value. This must be done before calling an
  // instantiation method that will trigger the entropy callback.
  //
  // The entropy is zeroized when used by an instantiation call.
  //
  // If the entropy callback is null, entropyInput is used as the entropy.
  public void setEntropy(byte[] entropyInput)
  {
    // Make sure the byte buffer has the correct capacity.
    if (myEntropyInput == null ||
        myEntropyInput.capacity() != entropyInput.length)
    {
      myEntropyInput = ByteBuffer.allocateDirect(entropyInput.length);
    }

    // Copy to the direct buffer.
    myEntropyInput.put(entropyInput);
    ((Buffer)myEntropyInput).position(0);

    // Zeroize the original.
    for (int i = 0; i < entropyInput.length; ++i) {
      entropyInput[i] = 0;
    }
  }

  // Set the nonce callback. If not null, it is used to get the nonce. If null,
  // the nonce set with setNonce() is used.
  public void setNonceCallback(NonceCallback cb)
  {
    myNonceCb = cb;
  }

  // Set the nonce. This must be done before calling an instantiation method
  // that will trigger the nonce callback.
  //
  // If the nonce callback is null, nonce is used as the entropy.
  public void setNonce(byte[] nonce)
  {
    myNonce = nonce;
  }

  // Calls setNonce() with the nonce value as an array of bytes in little
  // endian format.
  public void setNonce(long nonce)
  {
    // Allocate a buffer if necessary.
    if (myNonce == null || myNonce.length != myNonceIntBytes)
    {
      myNonce = new byte[myNonceIntBytes];
    }

    // Copy as little endian.
    for (int i = 0; i < Long.BYTES && i < myNonceIntBytes; ++i)
    {
      myNonce[i] = (byte)(nonce >> (i * 8));
    }
    for (int i = Long.BYTES; i < myNonceIntBytes; ++i)
    {
      myNonce[i] = 0;
    }
  }

  // Set the timestamp callback. If not null, it is used to get the timestamp.
  // If null, 0 is used.
  public void setTimestampCallback(TimestampCallback cb)
  {
    myTimestampCb = cb;
  }

  // Initialize.
  protected void initBase(MteDrbgs drbg,
                          int tokBytes,
                          MteVerifiers verifiers,
                          MteCiphers cipher,
                          MteHashes hash) {
    // Set options.
    myDrbg = drbg;
    myTokBytes = tokBytes;
    myVerifiers = verifiers;
    myCipher = cipher;
    myHash = hash;

    // The ideal nonce length is the size of the nonce integer, but it must be
    // at least the minimum for the DRBG and no more than the maximum for the
    // DRBG.
    myNonceIntBytes = Math.max(MteBase.getDrbgsNonceMinBytes(drbg),
                               Math.min(Long.BYTES,
                                        MteBase.getDrbgsNonceMaxBytes(drbg)));
  }

  // Return a string from the byte buffer that contains a (possibly) null-
  // terminated C string, assumed to be UTF-8.
  protected static String getCString(byte[] buff)
  {
    return getCString(buff, 0);
  }
  protected static String getCString(byte[] buff, int startOff)
  {
    // Find the null terminator.
    int endOff = startOff;
    while (endOff < buff.length && buff[endOff] != 0)
    {
      ++endOff;
    }

    // Convert to string.
    int bytes = endOff - startOff;
    return new String(buff, startOff, bytes, StandardCharsets.UTF_8);
  }

  // The entropy callback.
  private int entropyCallback(int minEntropy, int minLength, long maxLength)
  {
    // Call the callback if set.
    if (myEntropyCb != null)
    {
      EntropyCallback.BuffStatus bs =
        myEntropyCb.entropyCallback(minEntropy, minLength, maxLength);
      myEntropyInput = bs.buff;
      return bs.status.getValue();
    }

    // Check the length.
    int eiLen = myEntropyInput == null ? 0 : myEntropyInput.capacity();
    if (eiLen < minLength || eiLen > maxLength)
    {
      return MteStatus.mte_status_drbg_catastrophic.getValue();
    }

    // Success.
    return MteStatus.mte_status_success.getValue();
  }

  // The nonce callback.
  private int nonceCallback(int minLength, int maxLength, ByteBuffer nonce)
  {
    // Call the callback if set.
    if (myNonceCb != null)
    {
      return myNonceCb.nonceCallback(minLength, maxLength, nonce);
    }
    // Copy to the provided buffer.
    nonce.put(myNonce, 0, myNonce.length);
    ((Buffer)nonce).position(0);
    return myNonce.length;
  }

  // The timestamp callback.
  private long timestampCallback()
  {
    // Call the callback if set.
    if (myTimestampCb != null)
    {
      return myTimestampCb.timestampCallback();
    }

    // Default to 0 otherwise.
    return 0;
  }

  // Options.
  private MteDrbgs myDrbg;
  private int myTokBytes;
  private MteVerifiers myVerifiers;
  private MteCiphers myCipher;
  private MteHashes myHash;

  // Callbacks.
  private EntropyCallback myEntropyCb;
  private NonceCallback myNonceCb;
  private TimestampCallback myTimestampCb;

  // Instantiation inputs.
  private ByteBuffer myEntropyInput;
  private byte[] myNonce;

  // Nonce length when set as an integer.
  private int myNonceIntBytes;

  // Static initializer.
  static
  {
    // Load the JNI library.
    System.loadLibrary("mtejni");

    // Do one-time init.
    init();

    // Check version.
    if (getVersionMajor() != MteVersion.ourVersionMajor ||
        getVersionMinor() != MteVersion.ourVersionMinor ||
        getVersionPatch() != MteVersion.ourVersionPatch)
    {
      throw new RuntimeException("MTE version mismatch.");
    }
  }

  // Library functions.
  private static native void init();
  private static native String statusName(int status);
  private static native String statusDescription(int status);
  private static native int statusCode(String name);
  private static native boolean statusIsError(int algo);
  private static native int defaultDrbg();
  private static native int defaultVerifiers();
  private static native int defaultCipher();
  private static native int defaultHash();
  private static native String drbgsName(int algo);
  private static native int drbgsAlgo(String name);
  private static native int drbgsSecStrengthBytes(int algo);
  private static native int drbgsPersonalMinBytes(int algo);
  private static native long drbgsPersonalMaxBytes(int algo);
  private static native int drbgsEntropyMinBytes(int algo);
  private static native long drbgsEntropyMaxBytes(int algo);
  private static native int drbgsNonceMinBytes(int algo);
  private static native int drbgsNonceMaxBytes(int algo);
  private static native long drbgsReseedInterval(int algo);
  private static native String verifiersName(int algo);
  private static native int verifiersAlgo(String name);
  private static native String ciphersName(int algo);
  private static native int ciphersAlgo(String name);
  private static native int ciphersBlockBytes(int algo);
  private static native String hashesName(int algo);
  private static native int hashesAlgo(String name);
}

