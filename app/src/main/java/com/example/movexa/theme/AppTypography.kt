package com.example.movexa.theme

import android.graphics.Typeface
import android.widget.TextView
import androidx.annotation.DimenRes
import com.example.movexa.R

/**
 * Centralized typography definitions for the Movexa design system.
 * Provides consistent text styling across the application.
 */
object AppTypography {

    // ─── Font Families ──────────────────────────────────────────
    const val FONT_FAMILY_DEFAULT = "sans-serif"
    const val FONT_FAMILY_MEDIUM = "sans-serif-medium"
    const val FONT_FAMILY_LIGHT = "sans-serif-light"
    const val FONT_FAMILY_BOLD = "sans-serif-medium"
    const val FONT_FAMILY_MONO = "monospace"

    // ─── Text Size Resource References ──────────────────────────
    @DimenRes val SIZE_DISPLAY = R.dimen.text_display
    @DimenRes val SIZE_HEADLINE = R.dimen.text_headline
    @DimenRes val SIZE_TITLE = R.dimen.text_title
    @DimenRes val SIZE_SUBTITLE = R.dimen.text_subtitle
    @DimenRes val SIZE_BODY = R.dimen.text_body
    @DimenRes val SIZE_CAPTION = R.dimen.text_caption
    @DimenRes val SIZE_OVERLINE = R.dimen.text_overline
    @DimenRes val SIZE_BRAND = R.dimen.text_brand

    // ─── Letter Spacing ─────────────────────────────────────────
    const val LETTER_SPACING_TIGHT = -0.02f
    const val LETTER_SPACING_NORMAL = 0f
    const val LETTER_SPACING_WIDE = 0.05f
    const val LETTER_SPACING_EXTRA_WIDE = 0.1f
    const val LETTER_SPACING_BRAND = 0.15f

    /**
     * Text style configuration data class for programmatic styling.
     */
    data class TextStyle(
        @DimenRes val sizeRes: Int,
        val fontFamily: String,
        val typeface: Int = Typeface.NORMAL,
        val letterSpacing: Float = LETTER_SPACING_NORMAL,
        val color: Int = AppColors.TEXT_PRIMARY
    )

    // ─── Predefined Text Styles ─────────────────────────────────
    val DISPLAY = TextStyle(
        sizeRes = SIZE_DISPLAY,
        fontFamily = FONT_FAMILY_BOLD,
        typeface = Typeface.BOLD,
        letterSpacing = LETTER_SPACING_TIGHT,
        color = AppColors.TEXT_PRIMARY
    )

    val HEADLINE = TextStyle(
        sizeRes = SIZE_HEADLINE,
        fontFamily = FONT_FAMILY_MEDIUM,
        typeface = Typeface.BOLD,
        letterSpacing = LETTER_SPACING_NORMAL,
        color = AppColors.TEXT_PRIMARY
    )

    val TITLE = TextStyle(
        sizeRes = SIZE_TITLE,
        fontFamily = FONT_FAMILY_MEDIUM,
        typeface = Typeface.NORMAL,
        letterSpacing = LETTER_SPACING_NORMAL,
        color = AppColors.TEXT_PRIMARY
    )

    val SUBTITLE = TextStyle(
        sizeRes = SIZE_SUBTITLE,
        fontFamily = FONT_FAMILY_DEFAULT,
        typeface = Typeface.NORMAL,
        color = AppColors.TEXT_SECONDARY
    )

    val BODY = TextStyle(
        sizeRes = SIZE_BODY,
        fontFamily = FONT_FAMILY_DEFAULT,
        typeface = Typeface.NORMAL,
        color = AppColors.TEXT_PRIMARY
    )

    val BODY_SECONDARY = TextStyle(
        sizeRes = SIZE_BODY,
        fontFamily = FONT_FAMILY_DEFAULT,
        typeface = Typeface.NORMAL,
        color = AppColors.TEXT_SECONDARY
    )

    val CAPTION = TextStyle(
        sizeRes = SIZE_CAPTION,
        fontFamily = FONT_FAMILY_DEFAULT,
        typeface = Typeface.NORMAL,
        color = AppColors.TEXT_SECONDARY
    )

    val OVERLINE = TextStyle(
        sizeRes = SIZE_OVERLINE,
        fontFamily = FONT_FAMILY_MEDIUM,
        typeface = Typeface.NORMAL,
        letterSpacing = LETTER_SPACING_WIDE,
        color = AppColors.TEXT_SECONDARY
    )

    val BRAND = TextStyle(
        sizeRes = SIZE_BRAND,
        fontFamily = FONT_FAMILY_MEDIUM,
        typeface = Typeface.BOLD,
        letterSpacing = LETTER_SPACING_BRAND,
        color = AppColors.PRIMARY_DARK
    )

    val BUTTON = TextStyle(
        sizeRes = SIZE_BODY,
        fontFamily = FONT_FAMILY_MEDIUM,
        typeface = Typeface.NORMAL,
        letterSpacing = LETTER_SPACING_WIDE,
        color = AppColors.ON_PRIMARY
    )

    /**
     * Apply a text style to a TextView programmatically.
     */
    fun applyStyle(textView: TextView, style: TextStyle) {
        textView.apply {
            setTextColor(style.color)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                resources.getDimension(style.sizeRes))
            typeface = Typeface.create(style.fontFamily, style.typeface)
            letterSpacing = style.letterSpacing
        }
    }
}
