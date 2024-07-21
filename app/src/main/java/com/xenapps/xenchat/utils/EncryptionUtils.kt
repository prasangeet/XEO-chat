package com.xenapps.xenchat.utils

import android.util.Base64
import android.util.Log
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

object EncryptionUtils {

    private val PUBLIC_KEY_BASE64 = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCgBqyRG9BGd4m3wFdlAhUV0JbD\n" +
            "HpR3+BL3JlWMfhmaDX0mdqP/WOGjcO7NmjkG9KedVJQX/SFZqaKR60dHzJNoj/IX\n" +
            "jLTX747f2vK9lxQdLEseJKi6R9D7cVkfpFu5iF13TJ6vF2xZsjdcmTjBAswVG8nQ\n" +
            "5CNopGZW5LfZ9B/8OwIDAQAB"
    private val PRIVATE_KEY_BASE64 = "MIICXAIBAAKBgQCgBqyRG9BGd4m3wFdlAhUV0JbDHpR3+BL3JlWMfhmaDX0mdqP/\n" +
            "WOGjcO7NmjkG9KedVJQX/SFZqaKR60dHzJNoj/IXjLTX747f2vK9lxQdLEseJKi6\n" +
            "R9D7cVkfpFu5iF13TJ6vF2xZsjdcmTjBAswVG8nQ5CNopGZW5LfZ9B/8OwIDAQAB\n" +
            "AoGBAIXp7+Ei0GEXyZSOjdQGMRTnUDKKVZ4rZ9uRJcgDAnOrVA6q+8REYdY/PGer\n" +
            "5osOk3GShLeqaY056sSHikfoR3W+FJxkua8XDfgjt8b1d9Uu0A+mJiMn7azIAlSU\n" +
            "7uYylJP+8hy5r8cFT0HMYdBLkoirMqnX6csiMcoLAdIzZ7ABAkEA/yuh0mkF4Sy6\n" +
            "7dnSPIn1ggp0q+tuSMtbnQ3UA/qaksCLNYtsCccsFzfFQF9xeaoj621LiIzWN2pS\n" +
            "i5eIs0coAQJBAKCL23LItwVm8SDWHnTUgHHmoqMLm7kcsIvgUuEOHcMgV+pdqiYZ\n" +
            "DNAyYu7ooKIpmdq0Vl5IgZyzJB5eI0AZxDsCQEZLOMsED5CWh/BaHyZ6Qt3OD5IE\n" +
            "y17WVqiPVKa79LUUwcTAYcTXz3ed74LqSBJiIn8KntJBKgoeChtWZVwt8AECQE/k\n" +
            "aScctN8uVA4YH33aBbUopYRnkW2z1jM1RWkTYkIoxTcuty5QRu0QNeVXxCAOfa61\n" +
            "EUnH4R/+kZm/FOGgMOMCQF4Bcd/zPY8hrEIuRbc28GlExnRZMFU74CWI1aeBps3C\n" +
            "blzj5zDojA/wVFp/ijVerc+M3ocBROmsa4jNwclGBH0="

    private val rsaPublicKey: PublicKey by lazy {
        try {
            val keyBytes = Base64.decode(PUBLIC_KEY_BASE64, Base64.DEFAULT)
            val keySpec = X509EncodedKeySpec(keyBytes)
            KeyFactory.getInstance("RSA").generatePublic(keySpec)
        } catch (e: Exception) {
            Log.e("Encryption", "Public Key Initialization Error: ${e.message}")
            throw e
        }
    }

    private val rsaPrivateKey: PrivateKey by lazy {
        try {
            val keyBytes = Base64.decode(PRIVATE_KEY_BASE64, Base64.DEFAULT)
            val keySpec = PKCS8EncodedKeySpec(keyBytes)
            KeyFactory.getInstance("RSA").generatePrivate(keySpec)
        } catch (e: Exception) {
            Log.e("Encryption", "Private Key Initialization Error: ${e.message}")
            throw e
        }
    }

    fun getPublicKey(): PublicKey {
        return rsaPublicKey
    }

    fun getPrivateKey(): PrivateKey {
        return rsaPrivateKey
    }

    fun encryptRSA(message: String, publicKey: PublicKey): String {
        return try {
            val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            cipher.init(Cipher.ENCRYPT_MODE, publicKey)
            val encryptedBytes = cipher.doFinal(message.toByteArray())
            Base64.encodeToString(encryptedBytes, Base64.DEFAULT)
        } catch (e: Exception) {
            Log.e("Encryption", "Encryption Error: ${e.message}")
            ""
        }
    }

    fun decryptRSA(encryptedMessage: String, privateKey: PrivateKey): String {
        return try {
            val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            cipher.init(Cipher.DECRYPT_MODE, privateKey)
            val encryptedBytes = Base64.decode(encryptedMessage, Base64.DEFAULT)
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            String(decryptedBytes)
        } catch (e: Exception) {
            Log.e("Encryption", "Decryption Error: ${e.message}")
            ""
        }
    }
}