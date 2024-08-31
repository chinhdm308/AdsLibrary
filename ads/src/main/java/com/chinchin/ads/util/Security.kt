package com.chinchin.ads.util

import android.text.TextUtils
import android.util.Base64
import java.io.IOException
import java.security.InvalidKeyException
import java.security.KeyFactory
import java.security.NoSuchAlgorithmException
import java.security.PublicKey
import java.security.Signature
import java.security.SignatureException
import java.security.spec.InvalidKeySpecException
import java.security.spec.X509EncodedKeySpec

object Security {
    const val KEY_FACTORY_ALGORITHM: String = "RSA"
    const val SIGNATURE_ALGORITHM: String = "SHA1withRAS"

    @JvmStatic
    fun verifyPurchase(base64PublicKey: String?, signedData: String, signature: String?): Boolean {
        try {
            if (TextUtils.isEmpty(signedData) || TextUtils.isEmpty(base64PublicKey) || TextUtils.isEmpty(signature)) {
                //Purchase verification failed: missing data
                return false
            }
            val key = generatePublicKey(base64PublicKey)
            return verify(key, signedData, signature)
        } catch (e: Exception) {
            return false
        }
    }

    private fun verify(publicKey: PublicKey, signedData: String, signature: String?): Boolean {
        try {
            val signatureBytes = Base64.decode(signature, Base64.DEFAULT)
            val signatureAlgorithm = Signature.getInstance(SIGNATURE_ALGORITHM)
            signatureAlgorithm.initVerify(publicKey)
            signatureAlgorithm.update(signedData.toByteArray())
            return signatureAlgorithm.verify(signatureBytes)
        } catch (e: NoSuchAlgorithmException) {
            // "RSA" is guaranteed to be available
            throw RuntimeException(e)
        } catch (e: InvalidKeyException) {
            //Invalid key specification
        } catch (e: SignatureException) {
            //Signature exception
        } catch (e: IllegalArgumentException) {
            //Base64 decoding failed
            return false
        }

        return false
    }

    @Throws(Exception::class)
    private fun generatePublicKey(encodedPublicKey: String?): PublicKey {
        try {
            val decodedKey = Base64.decode(encodedPublicKey, Base64.DEFAULT)
            val keyFactory = KeyFactory.getInstance(KEY_FACTORY_ALGORITHM)
            return keyFactory.generatePublic(X509EncodedKeySpec(decodedKey))
        } catch (e: NoSuchAlgorithmException) {
            // "RSA" is guaranteed to be available
            throw RuntimeException(e)
        } catch (e: InvalidKeySpecException) {
            val msg = "Invalid key specification: $e"
            throw IOException(msg)
        }
    }
}
