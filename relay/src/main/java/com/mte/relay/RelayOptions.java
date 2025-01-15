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

import java.util.ArrayList;
import java.util.List;

public class RelayOptions {
    public String getClientId() {
        return clientId;
    }

    public String getPairId() {
        return pairId;
    }

    public String getEncodeType() {
        return encodeType;
    }

    public Boolean getUrlIsEncoded() {
        return urlIsEncoded;
    }

    public Boolean getHeadersAreEncoded() {
        return headersAreEncoded;
    }

    public Boolean getBodyIsEncoded() {
        return bodyIsEncoded;
    }

    String clientId;
    String pairId;
    String encodeType;
    Boolean urlIsEncoded;
    Boolean headersAreEncoded;
    Boolean bodyIsEncoded;

    public RelayOptions(String clientId,
                        String pairId,
                        String encodeType,
                        Boolean urlIsEncoded,
                        Boolean headersAreEncoded,
                        Boolean bodyIsEncoded) {
        this.clientId = clientId;
        this.pairId = pairId;
        this.encodeType = encodeType;
        this.urlIsEncoded = urlIsEncoded;
        this.headersAreEncoded = headersAreEncoded;
        this.bodyIsEncoded = bodyIsEncoded;
    }

    public static String formatMteRelayHeader(RelayOptions options) {
        List<String> args = new ArrayList<>();
        args.add(options.getClientId());
        args.add(options.getPairId());
        args.add(options.getEncodeType().equals("MTE") ? "0" : "1");
        args.add(options.getUrlIsEncoded() ? "1" : "0");
        args.add(options.getHeadersAreEncoded() ? "1" : "0");
        args.add(options.getBodyIsEncoded() ? "1" : "0");
        return String.join(",", args);
    }

    public static RelayOptions parseMteRelayHeader(String header) {
        String[] args = header.split(",");
        if (args.length > 0) {
            if (args.length > 1) {
                return new RelayOptions(args[0],
                        args[1],
                        args[2].equals("0") ? "MTE" : "MKE",
                        args[3].equals("1"),
                        args[4].equals("1"),
                        args[5].equals("1"));
            } else {
                return new RelayOptions(args[0],
                        "",
                        "",
                        false,
                        false,
                        false);
            }
        } else {
            // The header doesn't have any elements
            return null;
        }
    }
}



