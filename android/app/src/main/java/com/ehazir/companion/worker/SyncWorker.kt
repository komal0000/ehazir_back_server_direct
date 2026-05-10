package com.ehazir.companion.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ehazir.companion.data.repository.AttendanceRepository
import com.ehazir.companion.data.repository.Result
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: AttendanceRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): androidx.work.ListenableWorker.Result {
        return when (repository.syncPendingRecords()) {
            is Result.Success -> androidx.work.ListenableWorker.Result.success()
            is Result.Error -> {
                if (runAttemptCount < 3) {
                    androidx.work.ListenableWorker.Result.retry()
                } else {
                    androidx.work.ListenableWorker.Result.failure()
                }
            }
            else -> androidx.work.ListenableWorker.Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "ehazir_sync_worker"
    }
}
