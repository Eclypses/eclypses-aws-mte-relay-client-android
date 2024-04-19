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

package com.mte.relay;

import android.util.Log;

import com.android.volley.BuildConfig;
import com.eclypses.mte.MteKyber;

public class KyberException extends RuntimeException {

    private enum KyberErrorCode {
        success("Success"),
        randomFail("RandomFail"),
        invalidPubKey("InvalidPubKey"),
        invalidPrivKey("InvalidPrivKey"),
        memoryFail("MemoryFail"),
        invalidCipherText("InvalidCipherText");
        private final String name;
        KyberErrorCode(String name) {
            this.name = name;
        }
    }

    private String name;
    private String message;

    public KyberException(int status, String message) {
        super(new Exception(resolveEnum(status)));
        this.message = message;
        if (BuildConfig.DEBUG) {
            Log.e("MTE", "MteKyber Exception. " + message +
                    ", Status: " + resolveEnum(status));
        }
    }

    private static String resolveEnum(int value) {
        String valuestr = KyberErrorCode.success.name;
        switch (value) {
            case MteKyber.Success:
                valuestr = KyberErrorCode.success.name;
            break;
            case MteKyber.EntropyFail:
                valuestr = KyberErrorCode.randomFail.name;
            break;
            case MteKyber.InvalidPubKey:
                valuestr = KyberErrorCode.invalidPubKey.name;
                break;
            case MteKyber.InvalidPrivKey:
                valuestr = KyberErrorCode.invalidPrivKey.name;
                break;
            case MteKyber.MemoryFail:
                valuestr = KyberErrorCode.memoryFail.name;
                break;
            case MteKyber.InvalidCipherText:
                valuestr = KyberErrorCode.invalidCipherText.name;
                break;
        }
        return valuestr;
    }
}
