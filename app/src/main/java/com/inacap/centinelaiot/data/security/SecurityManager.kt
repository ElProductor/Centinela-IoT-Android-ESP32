package com.inacap.centinelaiot.data.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Gestión de Seguridad Criptográfica según lineamientos ISO/IEC 27400:2022
 * para dispositivos y aplicaciones móviles IoT en entornos OT.
 */
object SecurityManager {

    /**
     * Genera un Salt aleatorio criptográficamente seguro de 16 bytes.
     */
    fun generateSalt(): String {
        val random = SecureRandom()
        val salt = ByteArray(16)
        random.nextBytes(salt)
        return Base64.getEncoder().encodeToString(salt)
    }

    /**
     * Calcula el Hash seguro SHA-256 combinando contraseña con Salt.
     */
    fun hashPassword(password: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val combined = "$password:$salt"
        val hash = digest.digest(combined.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    /**
     * Valida una contraseña ingresada contra el hash almacenado en SQLite.
     */
    fun verifyPassword(inputPassword: String, storedHash: String, salt: String): Boolean {
        val computedHash = hashPassword(inputPassword, salt)
        return computedHash.equals(storedHash, ignoreCase = true)
    }

    /**
     * Genera un Token de sesión efímero para autorizar peticiones OT.
     */
    fun generateSessionToken(username: String): String {
        val raw = "$username:${System.currentTimeMillis()}:${generateSalt()}"
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(raw.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
