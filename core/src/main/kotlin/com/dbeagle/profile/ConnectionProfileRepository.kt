package com.dbeagle.profile

import com.dbeagle.model.ConnectionProfile

/**
 * Repository for persisting and retrieving connection profiles.
 * Passwords are encrypted using a master password.
 */
interface ConnectionProfileRepository {
    /**
     * Saves a connection profile. The password field will be encrypted before persistence.
     * On first save, the master password provider will be invoked to obtain the encryption key.
     * 
     * @param profile The profile to save. The password should be provided in plaintext.
     * @param plaintextPassword The plaintext password to encrypt and store
     * @throws Exception if master password cannot be obtained or encryption fails
     */
    suspend fun save(profile: ConnectionProfile, plaintextPassword: String)

    /**
     * Loads a connection profile by ID. The password field will be decrypted.
     * The master password provider will be invoked to obtain the decryption key.
     * 
     * @param id The profile ID
     * @return The profile with decrypted password, or null if not found
     * @throws IllegalArgumentException if decryption fails (wrong master password)
     */
    suspend fun load(id: String): ConnectionProfile?

    /**
     * Loads all stored connection profiles. Passwords will be decrypted.
     * The master password provider will be invoked to obtain the decryption key.
     * 
     * @return List of all profiles with decrypted passwords
     * @throws IllegalArgumentException if decryption fails (wrong master password)
     */
    suspend fun loadAll(): List<ConnectionProfile>

    /**
     * Deletes a connection profile by ID.
     * 
     * @param id The profile ID to delete
     */
    suspend fun delete(id: String)
}
