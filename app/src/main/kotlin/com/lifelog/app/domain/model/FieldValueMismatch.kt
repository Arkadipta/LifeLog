package com.lifelog.app.domain.model

/**
 * The [FieldType] that natively backs a stored [FieldValue]'s subtype.
 *
 * Unlike a field's *declared* type (which the user can change at any time on the
 * event), this is intrinsic to the serialized value and never changes once it
 * has been written to an entry.
 */
fun FieldValue.storedType(): FieldType = when (this) {
    is FieldValue.Numeric -> FieldType.NUMERIC
    is FieldValue.Text -> FieldType.TEXT
    is FieldValue.Bool -> FieldType.BOOLEAN
    is FieldValue.Choice -> FieldType.CHOICE
    is FieldValue.MultiSelect -> FieldType.MULTI_SELECT
}

/**
 * A stored value whose serialized subtype no longer matches the declared type of
 * the field it belongs to — e.g. a Number recorded before the field was changed
 * to Text. Purely descriptive: it carries enough to explain the mismatch in the
 * UI and never implies that any conversion will be performed.
 */
data class LegacyValueMismatch(
    val storedType: FieldType,
    val declaredType: FieldType,
    val displayValue: String
)

/**
 * Detects a legacy type mismatch for a single field value.
 *
 * Returns a [LegacyValueMismatch] when [value] is present and its subtype
 * differs from [declaredType]; returns null when the value is absent or its
 * subtype already matches the field. Detection only — no migration is performed
 * and the original serialized value is left untouched.
 */
fun legacyMismatchOf(declaredType: FieldType, value: FieldValue?): LegacyValueMismatch? {
    if (value == null) return null
    val stored = value.storedType()
    if (stored == declaredType) return null
    return LegacyValueMismatch(
        storedType = stored,
        declaredType = declaredType,
        displayValue = value.displayString()
    )
}
