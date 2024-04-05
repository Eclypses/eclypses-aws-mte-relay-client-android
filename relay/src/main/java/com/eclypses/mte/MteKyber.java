//******************************************************************************
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
//******************************************************************************
package com.eclypses.mte;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class MteKyber {

    public enum KyberStrength {
	None(0),
	K512(512),
	K768(768),
	K1024(1024);

	public final int val;

	private KyberStrength(int val) {
	    this.val = val;
	}
    }

    public interface EntropyCallback {
	// Class to hold a byte buffer and status.
	public static class BuffStatus
	{
	    // The buffer and status.
	    public ByteBuffer buff;
	    public int status;
	}
	BuffStatus entropyCallback(int minEntropyBytes, int maxEntropyBytes);
    }

    public static final int Success = 0;
    public static final int InvalidStrength = -1;    
    public static final int EntropyFail = -2;
    public static final int InvalidPubKey = -3;
    public static final int InvalidPrivKey = -4;
    public static final int MemoryFail = -5;
    public static final int InvalidCipherText = -6;

    private static KyberStrength globalKyberStrength = KyberStrength.None;

    private static int publicKeySize = 0;
    private static int privateKeySize = 0;
    private static int minEntropySize = 0;
    private static int maxEntropySize = 0;
    private static int encryptedSize = 0;
    private static int secretSize = 0; 

    public static int init(KyberStrength strength) {
	// Check if passed in strength is valid.
	if (strength != KyberStrength.K512 && strength != KyberStrength.K768 && strength != KyberStrength.K1024) {
	    return InvalidStrength;
	}

	// Check if global strength has already been set and is different.
	if (globalKyberStrength != KyberStrength.None) {
	    if (globalKyberStrength == strength) {
		// No reason to init again.
		return Success;
	    }

	    // Invalid attempt to change the strength.
	    return InvalidStrength;	    	  
	}

	globalKyberStrength = strength;

	// Initialize the Kyber strength.
	int res = jniInitStrength((short)strength.val);

	if (res != Success) {
	    return res;
	}

	publicKeySize = jniGetPublicKeySize();
	privateKeySize = jniGetPrivateKeySize();
	encryptedSize = jniGetEncryptedSize();
	minEntropySize = jniGetMinEntropySize();
	maxEntropySize = jniGetMaxEntropySize();
	secretSize = jniGetSecretSize();

	return res;
    }

    // Constructor.
    public MteKyber() {
	// Check if globalKyberStrength has already been set.
	if (globalKyberStrength == KyberStrength.None) {
	    throw new RuntimeException("KyberSecurityStrength has not been properly set using MteKyber.init.");
	}

	hasCreatedKeys = false;

	myPublicKey = new byte[publicKeySize];
	myPrivateKey = new byte[privateKeySize];
    }

    public int createKeyPair(byte[] publicKey) {
	int szPrivateKey = myPrivateKey.length;
	int szPublicKey = myPublicKey.length;
	int status;
	//---------------------------------------------
	// Check if the keys have already been created. 
	//---------------------------------------------
	if (!hasCreatedKeys) {
	    // Check if publicKey is big enough
	    // to receive a raw key. We know that
	    // myPublicKey is big enough for that.
	    if (publicKey.length < publicKeySize) {
		return InvalidPubKey;	    
	    }
	    // Create the private and public keys.
	    status = jniCreateKeyPair(myPrivateKey, myPublicKey);	    
	    if (status != Success) {
		return status;
	    }	    
	}

	//---------------------------------------------
	// Copy the data from myPublicKey to publicKey.
	//---------------------------------------------
	System.arraycopy(myPublicKey, 0, publicKey, 0, myPublicKey.length);
	hasCreatedKeys = true;
	return Success;
    }

    public int createSecret(byte[] peerPublicKey, byte[] secret, byte[]encrypted) {
	int status = 0;
	// Check peer public key size.
	if (peerPublicKey.length != publicKeySize) {
	    return InvalidPubKey;
	}

	// Check if secret or encrypted buffer would be sufficient.
	if (secret.length < secretSize || encrypted.length != encryptedSize) {
	    return MemoryFail;
	}

	// Encrypt the Kyber secret using the provided peer public key.
	status = jniCreateSecret(peerPublicKey, secret, encrypted);

	return status;
    }

    public int decryptSecret(byte[] encrypted, byte[] secret) {
	int status = 0;
	//------------------------------------------------------
	// If the private key has not been set, return an error.
	//------------------------------------------------------
	if (!hasCreatedKeys) {
	    return InvalidPrivKey;
	}

	//-------------------------------------------------------------------------
	// Check if encrypted is the correct size and if secret is sufficient size.
	//-------------------------------------------------------------------------
	if (encrypted.length != encryptedSize || secret.length < secretSize) {
	    return MemoryFail;
	}

	//----------------------
	// Create shared secret.
	//----------------------
	status = jniDecryptSecret(myPrivateKey, encrypted, secret)	;

	// Zeroize the private key and reset "hasCreatedKeys",
	// whether this just worked or not.
	zeroize(myPrivateKey);
	hasCreatedKeys = false;

	return status;
    }

    public int setEntropy(byte[] entropyInput) {
	if (entropyInput.length < minEntropySize || entropyInput.length > maxEntropySize)
	    return MemoryFail;
	myEntropyInput = entropyInput;
	return Success;
    }

    public void setEntropyCallback(EntropyCallback entropyCB) {
	myEntropyCb = entropyCB;
    }

    static public void zeroize(byte[] dest) {
	Arrays.fill(dest, (byte) 0);
    }

    public static int getPublicKeySize() {
	return publicKeySize;
    }

    public static int getEncryptedSize() {
	return encryptedSize;
    }

    public static int getMinEntropySize() {
	return minEntropySize;
    }

    public static int getMaxEntropySize() {
	return maxEntropySize;
    }

    public static int getSecretSize() {
	return secretSize;
    }

    public static String getAlgorithm() {
	return jniGetAlgorithm();
    }

    //----------------------------------------------------------------------------
    // Our internal default callback function. It is called directly from the JNI
    // layer.
    //----------------------------------------------------------------------------
    protected int entropyCallback(int minLength, int maxLength) {
	// Check if a user callback has been set.
	if (myEntropyCb != null) {
	    // Call the user callback.
	    EntropyCallback.BuffStatus bs = myEntropyCb.entropyCallback(minLength, maxLength);

	    if (bs.status == Success) {
		if (bs.buff.hasArray()) {
		    // myEntropyInput will be copied and zeroized in the jni caller.
		    myEntropyInput = bs.buff.array();	

		    return Success;
		} else {
		    return EntropyFail;
		}	
	    } else {
		return bs.status;
	    }	    
	}
	// If we don't have entropy input, call the random generator.
	// Otherwise, myEntropyInput was set via setEntropy call.
	if (myEntropyInput == null) {
	    myEntropyInput = MteRandom.getBytes(minEntropySize);
	}
	// Check the lengths.
	if (myEntropyInput.length < minLength || myEntropyInput.length > maxLength)
	    return EntropyFail;
	// myEntropyInput will be copied and zeroized in the jni caller.
	return Success;
    }

    private boolean hasCreatedKeys = false;
    private byte[] myPrivateKey;
    private byte[] myPublicKey;
    private EntropyCallback myEntropyCb = null;
    private byte[] myEntropyInput = null;

    // Static initializer.
    static {
	// Load the JNI library.
	System.loadLibrary("mtejni");
	jniInit();
    } 

    // Library functions.
    private static native void jniInit();
    private static native int jniInitStrength(short strength);
    private static native String jniGetAlgorithm();
    private static native int jniGetPublicKeySize();
    private static native int jniGetPrivateKeySize();
    private static native int jniGetSecretSize();
    private static native int jniGetEncryptedSize();
    private static native int jniGetMinEntropySize();  
    private static native int jniGetMaxEntropySize();  
    private native int jniCreateKeyPair(byte[] privateKey, byte[] publicKey);
    private native int jniCreateSecret(byte[] peerPublicKey, byte[] secret, byte[] encrypted);
    private native int jniDecryptSecret(byte[] privateKey, byte[] encrypted, byte[] secret);

}