package com.example.movexa.ui.components

import android.content.Context
import android.text.InputType
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.example.movexa.R
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * Reusable input field component following the Movexa design system.
 * Wraps TextInputLayout + TextInputEditText with consistent styling.
 *
 * XML Usage:
 * ```xml
 * <com.example.movexa.ui.components.InputField
 *     android:id="@+id/inputEmail"
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     app:if_hint="Email address"
 *     app:if_inputType="email" />
 * ```
 */
class InputField @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val textInputLayout: TextInputLayout
    private val editText: TextInputEditText

    enum class FieldInputType {
        TEXT, EMAIL, PASSWORD, PHONE, NUMBER
    }

    init {
        val view = LayoutInflater.from(context).inflate(
            R.layout.component_input_field, this, true
        )
        textInputLayout = view.findViewById(R.id.textInputLayout)
        editText = view.findViewById(R.id.editText)

        attrs?.let { applyAttributes(it) }
    }

    private fun applyAttributes(attrs: AttributeSet) {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.InputField)
        try {
            // Hint
            val hint = typedArray.getString(R.styleable.InputField_if_hint) ?: ""
            textInputLayout.hint = hint

            // Input type
            val inputTypeValue = typedArray.getInt(R.styleable.InputField_if_inputType, 0)
            val fieldType = FieldInputType.entries.getOrElse(inputTypeValue) { FieldInputType.TEXT }
            setInputType(fieldType)

            // Start icon
            val startIcon = typedArray.getResourceId(R.styleable.InputField_if_startIcon, 0)
            if (startIcon != 0) {
                textInputLayout.setStartIconDrawable(startIcon)
            }

            // End icon
            val endIcon = typedArray.getResourceId(R.styleable.InputField_if_endIcon, 0)
            if (endIcon != 0) {
                textInputLayout.setEndIconDrawable(endIcon)
                textInputLayout.endIconMode = TextInputLayout.END_ICON_CUSTOM
            }

            // Error enabled
            val errorEnabled = typedArray.getBoolean(R.styleable.InputField_if_errorEnabled, false)
            textInputLayout.isErrorEnabled = errorEnabled
        } finally {
            typedArray.recycle()
        }
    }

    private fun setInputType(type: FieldInputType) {
        when (type) {
            FieldInputType.TEXT -> {
                editText.inputType = InputType.TYPE_CLASS_TEXT
            }
            FieldInputType.EMAIL -> {
                editText.inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            }
            FieldInputType.PASSWORD -> {
                editText.inputType =
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                textInputLayout.endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
            }
            FieldInputType.PHONE -> {
                editText.inputType = InputType.TYPE_CLASS_PHONE
            }
            FieldInputType.NUMBER -> {
                editText.inputType = InputType.TYPE_CLASS_NUMBER
            }
        }
    }

    // ─── Public API ─────────────────────────────────────────────

    /**
     * Get the text value.
     */
    fun getText(): String = editText.text?.toString()?.trim() ?: ""

    /**
     * Set the text value.
     */
    fun setText(value: String) {
        editText.setText(value)
    }

    /**
     * Set an error message.
     */
    fun setError(message: String?) {
        textInputLayout.error = message
        textInputLayout.isErrorEnabled = message != null
    }

    /**
     * Clear the error.
     */
    fun clearError() {
        textInputLayout.error = null
        textInputLayout.isErrorEnabled = false
    }

    /**
     * Set the hint text.
     */
    fun setHint(hint: String) {
        textInputLayout.hint = hint
    }

    /**
     * Enable or disable the input field.
     */
    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        textInputLayout.isEnabled = enabled
        editText.isEnabled = enabled
    }

    /**
     * Request focus on the edit text.
     */
    override fun requestFocus(direction: Int, previouslyFocusedRect: android.graphics.Rect?): Boolean {
        return editText.requestFocus(direction, previouslyFocusedRect)
    }

    /**
     * Get the internal TextInputLayout for advanced customization.
     */
    fun getTextInputLayout(): TextInputLayout = textInputLayout

    /**
     * Get the internal TextInputEditText for advanced customization.
     */
    fun getEditText(): TextInputEditText = editText

    /**
     * Add a text change listener.
     */
    fun addTextChangedListener(watcher: android.text.TextWatcher) {
        editText.addTextChangedListener(watcher)
    }
}
