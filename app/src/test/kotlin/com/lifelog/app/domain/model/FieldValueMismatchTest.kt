package com.lifelog.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FieldValueMismatchTest {

    @Test
    fun `storedType maps every subtype to its native field type`() {
        assertEquals(FieldType.NUMERIC, FieldValue.Numeric(1.0).storedType())
        assertEquals(FieldType.TEXT, FieldValue.Text("x").storedType())
        assertEquals(FieldType.BOOLEAN, FieldValue.Bool(true).storedType())
        assertEquals(FieldType.CHOICE, FieldValue.Choice("a").storedType())
        assertEquals(FieldType.MULTI_SELECT, FieldValue.MultiSelect(listOf("a")).storedType())
    }

    @Test
    fun `null value is never a mismatch`() {
        assertNull(legacyMismatchOf(FieldType.TEXT, null))
        assertNull(legacyMismatchOf(FieldType.NUMERIC, null))
    }

    @Test
    fun `matching subtype and declared type is not a mismatch`() {
        assertNull(legacyMismatchOf(FieldType.NUMERIC, FieldValue.Numeric(42.0)))
        assertNull(legacyMismatchOf(FieldType.TEXT, FieldValue.Text("hi")))
        assertNull(legacyMismatchOf(FieldType.BOOLEAN, FieldValue.Bool(false)))
        assertNull(legacyMismatchOf(FieldType.CHOICE, FieldValue.Choice("a")))
        assertNull(legacyMismatchOf(FieldType.MULTI_SELECT, FieldValue.MultiSelect(listOf("a"))))
    }

    @Test
    fun `number stored under a text field is a mismatch with readable value`() {
        val mismatch = legacyMismatchOf(FieldType.TEXT, FieldValue.Numeric(42.0))
        assertEquals(FieldType.NUMERIC, mismatch?.storedType)
        assertEquals(FieldType.TEXT, mismatch?.declaredType)
        // whole doubles render without a trailing .0, matching FieldValue.displayString()
        assertEquals("42", mismatch?.displayValue)
    }

    @Test
    fun `choice stored under a boolean field is a mismatch`() {
        val mismatch = legacyMismatchOf(FieldType.BOOLEAN, FieldValue.Choice("Morning"))
        assertEquals(FieldType.CHOICE, mismatch?.storedType)
        assertEquals(FieldType.BOOLEAN, mismatch?.declaredType)
        assertEquals("Morning", mismatch?.displayValue)
    }

    @Test
    fun `text stored under a numeric field is a mismatch`() {
        val mismatch = legacyMismatchOf(FieldType.NUMERIC, FieldValue.Text("hello"))
        assertEquals(FieldType.TEXT, mismatch?.storedType)
        assertEquals(FieldType.NUMERIC, mismatch?.declaredType)
        assertEquals("hello", mismatch?.displayValue)
    }
}
