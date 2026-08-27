package com.necromagik.pureclock.keeper

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class KeeperWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Log.i("PureClock_KEEPER", "==> [KeeperWorker.doWork] Плановая проверка живости процессов...")
        AppKeeper.reviveAllProcesses(context)
        return Result.success()
    }
}