package dev.wolzard.recon.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import dev.wolzard.recon.core.model.FailureReason
import dev.wolzard.recon.core.model.Job
import dev.wolzard.recon.core.model.JobState
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "jobs")
data class JobEntity(
    @PrimaryKey val id: String,
    val sharedText: String,
    val url: String?,
    val localUri: String?,
    val state: JobState,
    val failure: FailureReason?,
    val failureDetail: String?,
    val retryAtEpochMs: Long?,
    val progress: Float?,
