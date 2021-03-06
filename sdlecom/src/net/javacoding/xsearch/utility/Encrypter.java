package net.javacoding.xsearch.utility;

import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.KeySpec;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESKeySpec;
import javax.crypto.spec.DESedeKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sun.misc.BASE64Decoder;
import sun.misc.BASE64Encoder;

import java.util.HashMap;

/**
 *  Qiu Password Encrypter
 */

public class Encrypter {
    private static Logger       logger                   = LoggerFactory.getLogger(Encrypter.class.getName());

    public static final String  DESEDE_ENCRYPTION_SCHEME = "DESede";
    public static final String  DES_ENCRYPTION_SCHEME    = "DES";
    public static final String  DEFAULT_ENCRYPTION_KEY   = "This is a fairly long phrase used to encrypt";

    private KeySpec             keySpec;
    private SecretKeyFactory    keyFactory;
    private Cipher              cipher;

    private static final String UNICODE_FORMAT           = "UTF8";

    private static Encrypter    _defaultEncrypter        = null;
    private static HashMap<String, Encrypter>      _Encrypters = new HashMap<String, Encrypter>();

    public static Encrypter getDefaultEncrypter() {
        if (_defaultEncrypter == null) {
            try {
                _defaultEncrypter = new Encrypter(DES_ENCRYPTION_SCHEME, DEFAULT_ENCRYPTION_KEY);
            } catch (EncryptionException e) {
                logger.error("Encrypter Excepiton", e);
                throw new RuntimeException("Encrypter Exception");
            }
        }
        return _defaultEncrypter;
    }

    public static Encrypter getEncrypter(String key) {
        Encrypter encrypter = _Encrypters.get(key);
        if (encrypter == null) {
            try {
                encrypter = new Encrypter(DES_ENCRYPTION_SCHEME, key+DEFAULT_ENCRYPTION_KEY);
                _Encrypters.put(key,encrypter);
            } catch (EncryptionException e) {
                logger.error("Encrypter Excepiton", e);
                throw new RuntimeException("Encrypter Exception");
            }
        }
        return encrypter;
    }
    
    public Encrypter(String encryptionScheme) throws EncryptionException {
        this(encryptionScheme, DEFAULT_ENCRYPTION_KEY);
    }

    public Encrypter(String encryptionScheme, String encryptionKey) throws EncryptionException {

        if (encryptionKey == null) throw new IllegalArgumentException("encryption key was null");
        if (encryptionKey.trim().length() < 24) throw new IllegalArgumentException("encryption key was less than 24 characters");

        try {
            byte[] keyAsBytes = encryptionKey.getBytes(UNICODE_FORMAT);

            if (encryptionScheme.equals(DESEDE_ENCRYPTION_SCHEME)) {
                keySpec = new DESedeKeySpec(keyAsBytes);
            } else if (encryptionScheme.equals(DES_ENCRYPTION_SCHEME)) {
                keySpec = new DESKeySpec(keyAsBytes);
            } else {
                throw new IllegalArgumentException("Encryption scheme not supported: " + encryptionScheme);
            }

            keyFactory = SecretKeyFactory.getInstance(encryptionScheme);
            cipher = Cipher.getInstance(encryptionScheme);

        } catch (InvalidKeyException e) {
            throw new EncryptionException(e);
        } catch (UnsupportedEncodingException e) {
            throw new EncryptionException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new EncryptionException(e);
        } catch (NoSuchPaddingException e) {
            throw new EncryptionException(e);
        }

    }

    public String encrypt(String unencryptedString) {
        if (unencryptedString == null || unencryptedString.trim().length() == 0) {
            return null;
        }

        try {
            SecretKey key = keyFactory.generateSecret(keySpec);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] cleartext = unencryptedString.getBytes(UNICODE_FORMAT);
            byte[] ciphertext = cipher.doFinal(cleartext);

            BASE64Encoder base64encoder = new BASE64Encoder();
            return base64encoder.encode(ciphertext);
        } catch (Exception e) {
            return null;
        }
    }

    public String decrypt(String encryptedString) {
        if (encryptedString == null || encryptedString.trim().length() <= 0) {
            return null;
        }

        try {
            SecretKey key = keyFactory.generateSecret(keySpec);
            cipher.init(Cipher.DECRYPT_MODE, key);
            BASE64Decoder base64decoder = new BASE64Decoder();
            byte[] cleartext = base64decoder.decodeBuffer(encryptedString);
            byte[] ciphertext = cipher.doFinal(cleartext);

            return bytes2String(ciphertext);
        } catch (Exception e) {
            return null;
        }
    }

    private static String bytes2String(byte[] bytes) {
        StringBuffer stringBuffer = new StringBuffer();
        for (int i = 0; i < bytes.length; i++) {
            stringBuffer.append((char) bytes[i]);
        }
        return stringBuffer.toString();
    }

    public static class EncryptionException extends Exception {
        public EncryptionException(Throwable t) {
            super(t);
        }
    }
}
