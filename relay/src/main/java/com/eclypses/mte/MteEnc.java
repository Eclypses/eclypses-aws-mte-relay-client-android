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

// Class MteEnc
//
// This is the MTE encoder.
//
// To use, create an object of this type, call instantiate(), call encode()
// zero or more times to encode each piece of data, then optionally call
// uninstantiate() to clear the random state.
//
// Alternatively, the state can be saved any time after instantiate() and
// restored instead of instantiate() to pick up at a known point.
public class MteEnc extends MteBase
{
  // Constructor using default options. If the library has buildtime options,
  // they are used; otherwise, the options chosen are defined by the
  // MteBase.ourDefault* constants.
  public MteEnc()
  {
    this(MteBase.getDefaultDrbg(),
         MteBase.getDefaultTokBytes(),
         MteBase.getDefaultVerifiers());
  }

  // Constructor taking the DRBG, token size in bytes, and verifiers algorithm.
  public MteEnc(MteDrbgs drbg, int tokBytes, MteVerifiers verifiers)
  {
    // Initialize the base.
    initBase(drbg,
             tokBytes,
             verifiers,
             MteCiphers.mte_ciphers_none,
             MteHashes.mte_hashes_none);

    // Get the encoder size.
    int bytes = stateBytes(drbg.getValue(), tokBytes, verifiers.getValue());
    if (bytes == 0)
    {
      throw new IllegalArgumentException("MteEnc: Invalid options.");
    }

    // Allocate the encoder.
    myEncoder = ByteBuffer.allocateDirect(bytes);

    // Initialize the encoder state.
    MteStatus status = MteStatus.valueOf(stateInit(myEncoder,
                                                   drbg.getValue(),
                                                   tokBytes,
                                                   verifiers.getValue()));
    if (status != MteStatus.mte_status_success)
    {
      throw new IllegalArgumentException("MteEnc: Invalid options.");
    }

    // Allocate the save buffers.
    mySaveBuff = new byte[saveBytes(myEncoder)];
    mySaveBuff64 = ByteBuffer.allocateDirect(saveBytesB64(myEncoder));
  }

  // Instantiate the encoder with the personalization string. The entropy and
  // nonce callbacks will be called to get the rest of the seeding material.
  // Returns the status.
  public MteStatus instantiate(byte[] ps)
  {
    return MteStatus.valueOf(instantiate(myEncoder, ps));
  }
  public MteStatus instantiate(String ps)
  {
    return instantiate(ps.getBytes(StandardCharsets.UTF_8));
  }

  // Returns the reseed counter.
  public long getReseedCounter()
  {
    return reseedCounter(myEncoder);
  }

  // Returns the saved state. The Base64 version returns a Base64-encoded
  // saved state instead. On error, null is returned.
  public byte[] saveState()
  {
    MteStatus status = MteStatus.valueOf(stateSave(myEncoder, mySaveBuff));
    return status == MteStatus.mte_status_success ? mySaveBuff : null;
  }
  public String saveStateB64()
  {
    return stateSaveB64(myEncoder, mySaveBuff64);
  }

  // Restore a saved state, which must be the same length as was returned
  // from the saveState() call. The Base64 version takes a Base64-encoded
  // saved state as produced by saveStateB64(). Returns the status.
  public MteStatus restoreState(byte[] saved)
  {
    return MteStatus.valueOf(stateRestore(myEncoder, saved));
  }
  public MteStatus restoreStateB64(String saved)
  {
    return MteStatus.valueOf(stateRestoreB64(myEncoder, saved));
  }

  // Returns the encode buffer size in bytes given the data length in bytes.
  public int getBuffBytes(int dataBytes)
  {
    return buffBytes(myEncoder, dataBytes);
  }
  public int getBuffBytesB64(int dataBytes)
  {
    return buffBytesB64(myEncoder, dataBytes);
  }

  // Encode the given data. Returns the encoded message and status.
  public ArrStatus encode(byte[] data)
  {
    // Get the encode buffer requirement and resize if necessary.
    int buffBytes = buffBytes(myEncoder, data.length);
    if (buffBytes > myEncBuff.capacity())
    {
      myEncBuff = ByteBuffer.allocateDirect(buffBytes);
    }

    // Encode.
    myEncOff = 0;
    myEncBytes = 0;
    myArrStatus.status = MteStatus.valueOf(encodeB(myEncoder, data, myEncBuff));
    if (myArrStatus.status != MteStatus.mte_status_success)
    {
      myArrStatus.arr = null;
      return myArrStatus;
    }

    // Return the encoded part.
    ((Buffer)myEncBuff).position(myEncOff);
    myArrStatus.arr = new byte[myEncBytes];
    myEncBuff.get(myArrStatus.arr);
    ((Buffer)myEncBuff).position(0);
    return myArrStatus;
  }
  public StrStatus encodeB64(byte[] data)
  {
    // Get the encode buffer requirement and resize if necessary.
    int buffBytes = buffBytesB64(myEncoder, data.length);
    if (buffBytes > myEncBuff.capacity())
    {
      myEncBuff = ByteBuffer.allocateDirect(buffBytes);
    }

    // Encode.
    myStrStatus.status = MteStatus.valueOf(encodeB64B(myEncoder,
                                                      data,
                                                      myEncBuff));
    if (myStrStatus.status != MteStatus.mte_status_success)
    {
      myStrStatus.str = null;
      return myStrStatus;
    }

    // Return the encoded part.
    myStrStatus.str = myEncStr64;
    myEncStr64 = null;
    return myStrStatus;
  }

  // Encode the given string. Returns the encoded message and status.
  public ArrStatus encode(String str)
  {
    return encode(str.getBytes(StandardCharsets.UTF_8));
  }
  public StrStatus encodeB64(String str)
  {
    return encodeB64(str.getBytes(StandardCharsets.UTF_8));
  }

  // Encode the given data of the given length at the given offset to the given
  // buffer at the given offset. Returns the offset of the encoded version,
  // length of the encoded version, and status. The encoded buffer must have
  // sufficient length remaining after the offset. Use getBuffBytes() or
  // getBuffBytes64() to determine the buffer requirement for raw or Base64
  // respectively.
  public OffLenStatus encode(byte[] data, int dataOff, int dataBytes,
                             byte[] encoded, int encOff)
  {
    // Encode.
    myEncOff = 0;
    myEncBytes = 0;
    myOffLenStatus.status = MteStatus.valueOf(encodeA(myEncoder,
                                                      data, dataOff, dataBytes,
                                                      encoded, encOff));
    if (myOffLenStatus.status != MteStatus.mte_status_success)
    {
      myOffLenStatus.off = 0;
      myOffLenStatus.bytes = 0;
      return myOffLenStatus;
    }

    // Set the encoded offset and length. Return the information.
    myOffLenStatus.off = myEncOff;
    myOffLenStatus.bytes = myEncBytes;
    return myOffLenStatus;
  }
  public OffLenStatus encodeB64(byte[] data, int dataOff, int dataBytes,
                                byte[] encoded, int encOff)
  {
    // Encode.
    myEncOff = 0;
    myEncBytes = 0;
    myOffLenStatus.status =
      MteStatus.valueOf(encodeB64A(myEncoder,
                                   data, dataOff, dataBytes,
                                   encoded, encOff));
    if (myOffLenStatus.status != MteStatus.mte_status_success)
    {
      myOffLenStatus.off = 0;
      myOffLenStatus.bytes = 0;
      return myOffLenStatus;
    }

    // Set the encoded offset and length. Return the information.
    myOffLenStatus.off = myEncOff;
    myOffLenStatus.bytes = myEncBytes;
    return myOffLenStatus;
  }

  // Uninstantiate the encoder. It is no longer usable after this call.
  // Returns the status.
  public MteStatus uninstantiate()
  {
    return MteStatus.valueOf(uninstantiate(myEncoder));
  }

  // The encoder state.
  private final ByteBuffer myEncoder;

  // Encoder buffer.
  private int myEncOff;
  private int myEncBytes;
  private String myEncStr64 = null;
  private ByteBuffer myEncBuff = ByteBuffer.allocateDirect(1);

  // State save buffer.
  private final byte[] mySaveBuff;
  private final ByteBuffer mySaveBuff64;

  // Return values.
  private final ArrStatus myArrStatus = new ArrStatus();
  private final StrStatus myStrStatus = new StrStatus();
  private final OffLenStatus myOffLenStatus = new OffLenStatus();

  // Static initializer.
  static
  {
    // Do one-time init.
    init();
  }

  // Library functions.
  private static native void init();
  private static native int stateBytes(int drbg, int tokBytes, int verifiers);
  private static native int stateInit(ByteBuffer state,
                                      int drbg,
                                      int tokBytes,
                                      int verifiers);
  private native int instantiate(ByteBuffer state, byte[] ps);
  private static native long reseedCounter(ByteBuffer state);
  private static native int saveBytes(ByteBuffer state);
  private static native int saveBytesB64(ByteBuffer state);
  private static native int stateSave(ByteBuffer state, byte[] saved);
  private static native String stateSaveB64(ByteBuffer state, ByteBuffer saved);
  private static native int stateRestore(ByteBuffer state, byte[] saved);
  private static native int stateRestoreB64(ByteBuffer state, String saved);
  private static native int buffBytes(ByteBuffer state, int dataBytes);
  private static native int buffBytesB64(ByteBuffer state, int dataBytes);
  private native int encodeB(ByteBuffer state, byte[] data, ByteBuffer encBuff);
  private native int encodeA(ByteBuffer state,
                             byte[] data, int dataOff, int dataBytes,
                             byte[] encoded, int encOff);
  private native int encodeB64B(ByteBuffer state,
                                byte[] data,
                                ByteBuffer encBuff);
  private native int encodeB64A(ByteBuffer state,
                                byte[] data, int dataOff, int dataBytes,
                                byte[] encoded, int encOff);
  private static native int uninstantiate(ByteBuffer state);
}

