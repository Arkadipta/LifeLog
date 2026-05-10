package com.lifelog.app.domain.model

enum class FieldType(val displayName: String) {
    NUMERIC("Number"),
    TEXT("Text"),
    BOOLEAN("Yes / No"),
    CHOICE("Single Choice"),
    MULTI_SELECT("Multi-Select / Tags")
}
