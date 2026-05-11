package com.lifelog.app.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chart_configs",
    foreignKeys = [ForeignKey(
        entity = EventTypeEntity::class,
        parentColumns = ["id"],
        childColumns = ["eventTypeId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("eventTypeId")]
)
data class ChartConfigEntity(
    @PrimaryKey val id: String,
    val eventTypeId: Long,
    val configJson: String,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
