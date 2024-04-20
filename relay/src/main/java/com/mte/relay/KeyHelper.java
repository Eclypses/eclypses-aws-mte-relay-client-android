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


    import android.security.keystore.KeyGenParameterSpec;
    import android.security.keystore.KeyProperties;

    import java.io.IOException;
    import java.nio.charset.StandardCharsets;
    import java.security.InvalidAlgorithmParameterException;
    import java.security.KeyStore;
    import java.security.KeyStoreException;
    import java.security.NoSuchAlgorithmException;
    import java.security.NoSuchProviderException;
    import java.security.UnrecoverableEntryException;
    import java.security.cert.CertificateException;

    import javax.crypto.Cipher;
    import javax.crypto.KeyGenerator;
    import javax.crypto.SecretKey;
    import javax.crypto.spec.IvParameterSpec;

    public class KeyHelper {

        private final String KEY_ALIAS;
        private static final String KEYSTORE_PROVIDER = "AndroidKeyStore";
        private static final String TRANSFORMATION = "AES/CBC/PKCS7Padding";
        private final KeyStore keyStore;

        public KeyHelper(String hostUrlB64) {
            this.KEY_ALIAS = hostUrlB64;
            try {
                this.keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER);
                keyStore.load(null);
            } catch (NoSuchAlgorithmException | KeyStoreException |
                     IOException | CertificateException e) {
                throw new RelayException(getClass().getSimpleName(),
                        "Unable to instantiate Key Helper: Error: " + e.getMessage());
            }
        }

        public void generateSecretKey() {
            try {
                if (!keyStore.containsAlias(KEY_ALIAS)) {
                    // Generate a new secret key
                    KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER);

                    // Set up key generation parameters
                    KeyGenParameterSpec keyGenParameterSpec = new KeyGenParameterSpec.Builder(
                            KEY_ALIAS,
                            KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
                    )
                            .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                            .setRandomizedEncryptionRequired(false)  // Not always necessary
                            .build();

                    keyGenerator.init(keyGenParameterSpec);
                    keyGenerator.generateKey();
                }
            } catch (NoSuchAlgorithmException | NoSuchProviderException | KeyStoreException |
                     InvalidAlgorithmParameterException e) {
                throw new RelayException(getClass().getSimpleName(),
                        "Unable to generateSecretKey: Error: " + e.getMessage());
            }
        }

        public Boolean hasSecretKey() throws KeyStoreException {
            if (keyStore.containsAlias(KEY_ALIAS)) {
                return true;
            }
            return false;
        }

        public SecretKey getSecretKey() {
            try {
                if (keyStore.containsAlias(KEY_ALIAS)) {

                    // Retrieve the secret key entry from the Keystore
                    KeyStore.SecretKeyEntry secretKeyEntry = (KeyStore.SecretKeyEntry) keyStore.getEntry(KEY_ALIAS, null);

                    // Get the secret key from the entry
                    return secretKeyEntry.getSecretKey();
                } else {
                    return null;
                }
            } catch (NoSuchAlgorithmException | KeyStoreException
                     | UnrecoverableEntryException e) {
                throw new RelayException(getClass().getSimpleName(),
                        "Unable to retrieve SecretKey: Error: " + e.getMessage());
            }
        }

        public byte[] encryptString(String plaintext) {
            try {
                if (keyStore.containsAlias(KEY_ALIAS)) {
                    // Retrieve the secret key entry from the keystore
                    KeyStore.SecretKeyEntry secretKeyEntry = (KeyStore.SecretKeyEntry) keyStore.getEntry(KEY_ALIAS, null);
                    SecretKey secretKey = secretKeyEntry.getSecretKey();

                    // Initialize Cipher for encryption
                    Cipher cipher = Cipher.getInstance(TRANSFORMATION);
                    cipher.init(Cipher.ENCRYPT_MODE, secretKey);

                    // Get IV from cipher
                    byte[] iv = cipher.getIV();
                    byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

                    // Combine IV and encrypted data
                    byte[] result = new byte[iv.length + encrypted.length];
                    System.arraycopy(iv, 0, result, 0, iv.length);
                    System.arraycopy(encrypted, 0, result, iv.length, encrypted.length);

                    return result;
                }

            } catch (Exception e) {
                throw new RelayException(getClass().getSimpleName(),
                        "Unable to encryptString: Error: " + e.getMessage());
            }
            return null;
        }

        public String decryptBytes(byte[] encryptedBytes) {
            try {
                if (keyStore.containsAlias(KEY_ALIAS)) {
                    // Retrieve the secret key entry from the keystore
                    KeyStore.SecretKeyEntry secretKeyEntry = (KeyStore.SecretKeyEntry) keyStore.getEntry(KEY_ALIAS, null);
                    SecretKey secretKey = secretKeyEntry.getSecretKey();

                    // Initialize Cipher for decryption
                    Cipher cipher = Cipher.getInstance(TRANSFORMATION);

                    // Extract IV from the input
                    byte[] iv = new byte[16];
                    System.arraycopy(encryptedBytes, 0, iv, 0, iv.length);

                    // Initialize cipher with IV and secret key
                    cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(iv));

                    // Decrypt the data
                    byte[] decrypted = cipher.doFinal(encryptedBytes, iv.length, encryptedBytes.length - iv.length);
                    return new String(decrypted, StandardCharsets.UTF_8);
                }
            } catch (Exception e) {
                throw new RelayException(getClass().getSimpleName(),
                        "Unable to decryptBytes: Error: " + e.getMessage());
            }
            return null;
        }
    }
