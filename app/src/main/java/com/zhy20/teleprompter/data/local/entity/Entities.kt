package com.zhy20.teleprompter.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "script_folders",
    indices = [Index(value = ["name"], unique = true)],
)
data class ScriptFolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val sortOrder: Int,
)

@Entity(
    tableName = "scripts",
    foreignKeys = [
        ForeignKey(
            entity = ScriptFolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [Index("folderId"), Index("updatedAt")],
)
data class ScriptEntity(
    @PrimaryKey val id: String,
    val title: String,
    val folderId: String?,
    val documentJson: String,
    val plainText: String,
    val wordCount: Int,
    val normalEstimatedDurationSeconds: Long,
    val playbackSettingsJson: String,
    val createdAt: Long,
    val updatedAt: Long,
)
