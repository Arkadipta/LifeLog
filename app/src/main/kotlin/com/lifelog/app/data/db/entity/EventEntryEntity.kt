package com.lifelog.app.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "event_entries",
    foreignKeys = [ForeignKey(
        entity = EventTypeEntity::class,
        parentColumns = ["id"],
        childColumns = ["eventTypeId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("eventTypeId"), Index("createdAt")]
)
data class EventEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventTypeId: Long,
    val fieldValuesJson: String = "{}",
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
