package com.lifelog.app.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "event_fields",
    foreignKeys = [ForeignKey(
        entity = EventTypeEntity::class,
        parentColumns = ["id"],
        childColumns = ["eventTypeId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("eventTypeId")]
)
data class EventFieldEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventTypeId: Long,
    val name: String,
    val type: String,
    val optionsJson: String = "[]",
    val unit: String = "",
    val isRequired: Boolean = false,
    val sortOrder: Int = 0
)
