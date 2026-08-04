package com.zhy20.teleprompter.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.zhy20.teleprompter.data.local.dao.ScriptDao
import com.zhy20.teleprompter.data.local.dao.ScriptFolderDao
import com.zhy20.teleprompter.data.local.entity.ScriptEntity
import com.zhy20.teleprompter.data.local.entity.ScriptFolderEntity

@Database(
    entities = [ScriptEntity::class, ScriptFolderEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class TeleprompterDatabase : RoomDatabase() {
    abstract fun scriptDao(): ScriptDao
    abstract fun scriptFolderDao(): ScriptFolderDao
}
