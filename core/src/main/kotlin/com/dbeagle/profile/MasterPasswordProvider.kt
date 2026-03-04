package com.dbeagle.profile

/**
 * Provider interface for master password retrieval.
 * Implementations should prompt the user or retrieve the password from a secure source.
 */
fun interface MasterPasswordProvider {
    /**
     * Retrieves the master password used for encrypting/decrypting connection profiles.
     * @return The master password string
     * @throws Exception if password cannot be obtained (e.g., user cancels prompt)
     */
    suspend fun getMasterPassword(): String
}
