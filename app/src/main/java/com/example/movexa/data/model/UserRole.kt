package com.example.movexa.data.model

/**
 * Enumeration of user roles within the Movexa fleet management system.
 * Each role determines dashboard views, permissions, and available actions.
 */
enum class UserRole(val displayName: String, val level: Int) {
    ADMIN("Administrator", 0),
    MANAGER("Fleet Manager", 1),
    DRIVER("Driver", 2),
    MECHANIC("Mechanic", 3);

    companion object {
        /**
         * Get a UserRole from its string name (case-insensitive).
         */
        fun fromString(role: String?): UserRole {
            return entries.find {
                it.name.equals(role, ignoreCase = true)
            } ?: DRIVER
        }

        /**
         * Get a UserRole from its display name.
         */
        fun fromDisplayName(displayName: String?): UserRole {
            return entries.find {
                it.displayName.equals(displayName, ignoreCase = true)
            } ?: DRIVER
        }
    }

    /**
     * Check if this role has equal or higher privileges than another role.
     */
    fun hasPrivilege(requiredRole: UserRole): Boolean {
        return this.level <= requiredRole.level
    }

    /**
     * Check if this is an admin role.
     */
    fun isAdmin(): Boolean = this == ADMIN

    /**
     * Check if this is a management role (admin or manager).
     */
    fun isManagement(): Boolean = this == ADMIN || this == MANAGER
}
