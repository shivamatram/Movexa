package com.example.movexa.utils

import com.example.movexa.data.model.UserRole
import com.example.movexa.data.session.SessionManager

/**
 * Centralized role-based access control utility.
 *
 * Provides security validation for:
 * - Fragment/screen access control
 * - Role elevation prevention
 * - Manager creation authorization
 * - Self-operation protection
 *
 * All role checks route through this utility to maintain a single
 * source of truth for permission logic across the application.
 *
 * Usage:
 *   if (!RoleGuard.canCreateManager()) { /* deny */ }
 *   RoleGuard.requireRole(UserRole.ADMIN) { /* grant */ }
 */
object RoleGuard {

    // ─── Allowed Roles for Public Signup ─────────────────────────

    /**
     * Roles that can be selected during public signup.
     * ADMIN and MANAGER are explicitly excluded — they can only be
     * created through privileged admin operations.
     */
    val ALLOWED_SIGNUP_ROLES: Set<UserRole> = setOf(
        UserRole.DRIVER,
        UserRole.MECHANIC
    )

    /**
     * Roles that are restricted from public creation.
     * These can only be created by authorized admins.
     */
    val RESTRICTED_ROLES: Set<UserRole> = setOf(
        UserRole.ADMIN,
        UserRole.MANAGER
    )

    // ─── Session Helpers ────────────────────────────────────────

    private val sessionManager: SessionManager
        get() = SessionManager.getInstance()

    /**
     * Get the current user's cached role, or null if not logged in.
     */
    fun currentRole(): UserRole? = sessionManager.currentRole.value

    /**
     * Get the current user's UID from session.
     */
    fun currentUserId(): String? = sessionManager.currentUser.value?.uid

    // ─── Role Validation ────────────────────────────────────────

    /**
     * Check if a role is allowed for public self-registration.
     *
     * @param role The role to validate
     * @return true if the role can be self-registered
     */
    fun isAllowedSignupRole(role: UserRole): Boolean {
        return role in ALLOWED_SIGNUP_ROLES
    }

    /**
     * Check if a role is restricted from public creation.
     *
     * @param role The role to check
     * @return true if the role requires admin authorization to create
     */
    fun isRestrictedRole(role: UserRole): Boolean {
        return role in RESTRICTED_ROLES
    }

    // ─── Permission Checks ──────────────────────────────────────

    /**
     * Check if the current user is an admin.
     */
    fun isAdmin(): Boolean {
        return currentRole() == UserRole.ADMIN
    }

    /**
     * Check if the current user is a manager.
     */
    fun isManager(): Boolean {
        return currentRole() == UserRole.MANAGER
    }

    /**
     * Check if the current user is management-level (admin or manager).
     */
    fun isManagement(): Boolean {
        return currentRole()?.isManagement() == true
    }

    /**
     * Check if the current user has at least the required privilege level.
     *
     * @param requiredRole The minimum role required
     * @return true if the current user's role is equal or higher privilege
     */
    fun hasPrivilege(requiredRole: UserRole): Boolean {
        return currentRole()?.hasPrivilege(requiredRole) == true
    }

    // ─── Operation-Specific Guards ──────────────────────────────

    /**
     * Check if the current user can create a manager account.
     * Only ADMIN can create MANAGER accounts.
     *
     * @return true if the current user is authorized
     */
    fun canCreateManager(): Boolean {
        return currentRole() == UserRole.ADMIN
    }

    /**
     * Check if the current user can deactivate/remove a manager.
     * Only ADMIN can modify manager accounts.
     *
     * @return true if the current user is authorized
     */
    fun canModifyManager(): Boolean {
        return currentRole() == UserRole.ADMIN
    }

    /**
     * Check if the current user can deactivate a specific user.
     * Prevents self-deactivation and ensures admin-only access.
     *
     * @param targetUid The UID of the user to deactivate
     * @return true if the operation is allowed
     */
    fun canDeactivateUser(targetUid: String): Boolean {
        val currentUid = currentUserId()
        if (currentUid == null || currentUid == targetUid) return false
        return currentRole() == UserRole.ADMIN
    }

    /**
     * Check if the target user is the current user (self-operation check).
     *
     * @param targetUid The UID to check
     * @return true if the target is the current user
     */
    fun isSelf(targetUid: String): Boolean {
        return currentUserId() == targetUid
    }

    // ─── Enforcement Helpers ────────────────────────────────────

    /**
     * Execute a block only if the current user has the required role.
     * Otherwise, invoke the onDenied callback.
     *
     * @param requiredRole The minimum role required
     * @param onDenied Called when access is denied (optional)
     * @param onGranted Called when access is granted
     */
    inline fun requireRole(
        requiredRole: UserRole,
        onDenied: () -> Unit = {},
        onGranted: () -> Unit
    ) {
        if (hasPrivilege(requiredRole)) {
            onGranted()
        } else {
            onDenied()
        }
    }

    /**
     * Execute a block only if the current user is an admin.
     * Otherwise, invoke the onDenied callback.
     *
     * @param onDenied Called when access is denied (optional)
     * @param onGranted Called when access is granted
     */
    inline fun requireAdmin(
        onDenied: () -> Unit = {},
        onGranted: () -> Unit
    ) {
        if (isAdmin()) {
            onGranted()
        } else {
            onDenied()
        }
    }

    // ─── Error Messages ─────────────────────────────────────────

    /**
     * Standard error message for unauthorized signup role.
     */
    const val ERROR_RESTRICTED_ROLE =
        "Admin and Manager accounts cannot be created through signup. " +
                "Contact your company administrator."

    /**
     * Standard error message for unauthorized manager creation.
     */
    const val ERROR_UNAUTHORIZED_MANAGER_CREATION =
        "Only administrators can create manager accounts."

    /**
     * Standard error message for self-deactivation attempt.
     */
    const val ERROR_SELF_DEACTIVATION =
        "You cannot deactivate your own account."

    /**
     * Standard error message for insufficient privileges.
     */
    const val ERROR_INSUFFICIENT_PRIVILEGES =
        "You do not have sufficient privileges to perform this action."
}
