package com.example.movexa.utils

/**
 * Validation utility for form inputs across the application.
 * Provides consistent validation rules and error messages.
 */
object ValidationUtils {

    /**
     * Validation result data class.
     */
    data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String? = null
    )

    /**
     * Validate an email address.
     */
    fun validateEmail(email: String): ValidationResult {
        return when {
            email.isBlank() -> ValidationResult(false, "Email is required")
            !email.isValidEmail() -> ValidationResult(false, "Please enter a valid email address")
            email.length > 254 -> ValidationResult(false, "Email address is too long")
            else -> ValidationResult(true)
        }
    }

    /**
     * Validate a password.
     */
    fun validatePassword(password: String): ValidationResult {
        return when {
            password.isBlank() -> ValidationResult(false, "Password is required")
            password.length < 8 -> ValidationResult(false, "Password must be at least 8 characters")
            !password.any { it.isUpperCase() } ->
                ValidationResult(false, "Password must contain an uppercase letter")
            !password.any { it.isLowerCase() } ->
                ValidationResult(false, "Password must contain a lowercase letter")
            !password.any { it.isDigit() } ->
                ValidationResult(false, "Password must contain a number")
            else -> ValidationResult(true)
        }
    }

    /**
     * Validate password confirmation matches.
     */
    fun validatePasswordMatch(password: String, confirmPassword: String): ValidationResult {
        return when {
            confirmPassword.isBlank() -> ValidationResult(false, "Please confirm your password")
            password != confirmPassword -> ValidationResult(false, "Passwords do not match")
            else -> ValidationResult(true)
        }
    }

    /**
     * Validate a full name.
     */
    fun validateFullName(name: String): ValidationResult {
        return when {
            name.isBlank() -> ValidationResult(false, "Full name is required")
            name.length < 2 -> ValidationResult(false, "Name is too short")
            name.length > 100 -> ValidationResult(false, "Name is too long")
            else -> ValidationResult(true)
        }
    }

    /**
     * Validate a phone number.
     */
    fun validatePhone(phone: String): ValidationResult {
        return when {
            phone.isBlank() -> ValidationResult(false, "Phone number is required")
            !phone.isValidPhone() -> ValidationResult(false, "Please enter a valid phone number")
            else -> ValidationResult(true)
        }
    }

    /**
     * Validate a required text field.
     */
    fun validateRequired(value: String, fieldName: String = "This field"): ValidationResult {
        return when {
            value.isBlank() -> ValidationResult(false, "$fieldName is required")
            else -> ValidationResult(true)
        }
    }

    /**
     * Run multiple validations and return the first failure, or success if all pass.
     */
    fun validateAll(vararg results: ValidationResult): ValidationResult {
        return results.firstOrNull { !it.isValid } ?: ValidationResult(true)
    }
}
