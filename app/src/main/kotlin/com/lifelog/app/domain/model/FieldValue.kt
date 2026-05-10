package com.lifelog.app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class FieldValue {
    @Serializable
    @SerialName("numeric")
    data class Numeric(val value: Double) : FieldValue()

    @Serializable
    @SerialName("text")
    data class Text(val value: String) : FieldValue()

    @Serializable
    @SerialName("boolean")
    data class Bool(val value: Boolean) : FieldValue()

    @Serializable
    @SerialName("choice")
    data class Choice(val value: String) : FieldValue()

    @Serializable
    @SerialName("multiselect")
    data class MultiSelect(val values: List<String>) : FieldValue()

    fun displayString(): String = when (this) {
        is Numeric -> if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
        is Text -> value
        is Bool -> if (value) "Yes" else "No"
        is Choice -> value
        is MultiSelect -> values.joinToString(", ")
    }
}
