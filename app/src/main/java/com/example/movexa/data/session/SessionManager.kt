package com.example.movexa.data.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.movexa.data.model.User
import com.example.movexa.data.model.UserRole
import com.example.movexa.data.remote.FirebaseProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Manages user session state using DataStore for persistence.
 *
 * Session lifecycle:
 * 1. App start → [initializeSession] checks DataStore + Firebase Auth
 * 2. Login success → [setUser] persists uid/role/loginState
 * 3. Active use → [currentUser]/[currentRole] provide cached state
 * 4. Logout → [clearSession] wipes DataStore and signs out Firebase
 *
 * DataStore keys stored:
 * - uid: Firebase user UID
 * - role: UserRole enum name
 * - isLoggedIn: boolean flag
 * - email: user email
 * - name: user display name
 * - isVerified: driver verification status
 */

// Top-level DataStore extension property — single instance per app
private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "movexa_session"
)

class SessionManager private constructor(
    private val context: Context
) {

    private val dataStore: DataStore<Preferences> = context.sessionDataStore

    // ─── Preference Keys ────────────────────────────────────────

    private object Keys {
        val UID = stringPreferencesKey("session_uid")
        val EMAIL = stringPreferencesKey("session_email")
        val NAME = stringPreferencesKey("session_name")
        val ROLE = stringPreferencesKey("session_role")
        val IS_LOGGED_IN = booleanPreferencesKey("session_is_logged_in")
        val IS_VERIFIED = booleanPreferencesKey("session_is_verified")
    }

    // ─── In-Memory State ────────────────────────────────────────

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _currentRole = MutableStateFlow<UserRole?>(null)
    val currentRole: StateFlow<UserRole?> = _currentRole.asStateFlow()

    private val _sessionState = MutableStateFlow(SessionState.INITIALIZING)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    // ─── DataStore Flow Accessors ───────────────────────────────

    /**
     * Observe logged-in state reactively from DataStore.
     */
    val isLoggedInFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.IS_LOGGED_IN] ?: false
    }

    /**
     * Observe current role reactively from DataStore.
     */
    val roleFlow: Flow<String?> = dataStore.data.map { prefs ->
        prefs[Keys.ROLE]
    }

    /**
     * Observe UID reactively from DataStore.
     */
    val uidFlow: Flow<String?> = dataStore.data.map { prefs ->
        prefs[Keys.UID]
    }

    // ─── Session Operations ─────────────────────────────────────

    /**
     * Initialize session on app startup.
     * Reads persisted DataStore values and checks Firebase auth state.
     *
     * Decision matrix:
     * - DataStore has session + Firebase authenticated → AUTHENTICATED
     * - DataStore has session but Firebase expired → UNAUTHENTICATED (clear)
     * - No DataStore session → UNAUTHENTICATED
     */
    suspend fun initializeSession() {
        _sessionState.value = SessionState.INITIALIZING

        try {
            val prefs = dataStore.data.first()
            val storedLoggedIn = prefs[Keys.IS_LOGGED_IN] ?: false
            val storedUid = prefs[Keys.UID]
            val storedRole = prefs[Keys.ROLE]

            val firebaseUser = FirebaseProvider.currentUser

            if (storedLoggedIn && storedUid != null && firebaseUser != null
                && firebaseUser.uid == storedUid
            ) {
                // Session is valid
                _isLoggedIn.value = true
                _currentRole.value = UserRole.fromString(storedRole)
                _sessionState.value = SessionState.AUTHENTICATED
            } else if (storedLoggedIn && firebaseUser == null) {
                // DataStore says logged in but Firebase session expired
                clearSession()
            } else {
                // No session
                _isLoggedIn.value = false
                _currentUser.value = null
                _currentRole.value = null
                _sessionState.value = SessionState.UNAUTHENTICATED
            }
        } catch (e: Exception) {
            _sessionState.value = SessionState.ERROR
        }
    }

    /**
     * Persist session after successful login.
     * Stores uid, role, email, name, verified flag, and login state to DataStore.
     */
    suspend fun setUser(user: User) {
        // Update in-memory
        _currentUser.value = user
        _isLoggedIn.value = true
        _currentRole.value = user.role
        _sessionState.value = SessionState.AUTHENTICATED

        // Persist to DataStore
        dataStore.edit { prefs ->
            prefs[Keys.UID] = user.uid
            prefs[Keys.EMAIL] = user.email
            prefs[Keys.NAME] = user.fullName
            prefs[Keys.ROLE] = user.role.name
            prefs[Keys.IS_LOGGED_IN] = true
            prefs[Keys.IS_VERIFIED] = user.isVerified
        }
    }

    /**
     * Clear the current session completely (logout).
     * Wipes DataStore, resets in-memory state, signs out Firebase.
     */
    suspend fun clearSession() {
        // Clear in-memory
        _currentUser.value = null
        _isLoggedIn.value = false
        _currentRole.value = null
        _sessionState.value = SessionState.UNAUTHENTICATED

        // Clear DataStore
        dataStore.edit { prefs -> prefs.clear() }

        // Sign out Firebase
        FirebaseProvider.signOut()
    }

    /**
     * Update the cached user profile (e.g. after profile edit).
     */
    suspend fun updateUser(user: User) {
        _currentUser.value = user
        _currentRole.value = user.role

        dataStore.edit { prefs ->
            prefs[Keys.NAME] = user.fullName
            prefs[Keys.ROLE] = user.role.name
            prefs[Keys.IS_VERIFIED] = user.isVerified
        }
    }

    // ─── Session Queries ────────────────────────────────────────

    /**
     * Read cached UID directly from DataStore (one-shot).
     */
    suspend fun getCachedUserId(): String? {
        return dataStore.data.first()[Keys.UID]
    }

    /**
     * Read cached email directly from DataStore (one-shot).
     */
    suspend fun getCachedUserEmail(): String? {
        return dataStore.data.first()[Keys.EMAIL]
    }

    /**
     * Read cached role directly from DataStore (one-shot).
     */
    suspend fun getCachedUserRole(): UserRole? {
        val roleName = dataStore.data.first()[Keys.ROLE]
        return if (roleName != null) UserRole.fromString(roleName) else null
    }

    /**
     * Read cached verified status from DataStore (one-shot).
     */
    suspend fun getCachedVerifiedStatus(): Boolean {
        return dataStore.data.first()[Keys.IS_VERIFIED] ?: true
    }

    /**
     * Check if DataStore contains any persisted session.
     */
    suspend fun hasCachedSession(): Boolean {
        return dataStore.data.first()[Keys.IS_LOGGED_IN] ?: false
    }

    /**
     * Check if the current in-memory user has the required privilege.
     */
    fun hasPrivilege(requiredRole: UserRole): Boolean {
        return _currentRole.value?.hasPrivilege(requiredRole) ?: false
    }

    // ─── Session State Enum ─────────────────────────────────────

    enum class SessionState {
        INITIALIZING,
        AUTHENTICATED,
        UNAUTHENTICATED,
        EXPIRED,
        ERROR
    }

    // ─── Singleton ──────────────────────────────────────────────

    companion object {
        @Volatile
        private var instance: SessionManager? = null

        /**
         * Initialize the singleton. Call from Application.onCreate().
         */
        fun init(context: Context) {
            if (instance == null) {
                synchronized(this) {
                    if (instance == null) {
                        instance = SessionManager(context.applicationContext)
                    }
                }
            }
        }

        /**
         * Get the singleton instance.
         * @throws IllegalStateException if [init] has not been called.
         */
        fun getInstance(): SessionManager =
            instance ?: throw IllegalStateException(
                "SessionManager not initialized. Call init(context) in Application.onCreate()."
            )
    }
}
