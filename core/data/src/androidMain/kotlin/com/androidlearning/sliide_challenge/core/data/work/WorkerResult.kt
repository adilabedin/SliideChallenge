package com.androidlearning.sliide_challenge.core.data.work

import androidx.work.ListenableWorker

/** WorkManager's result, renamed so Workers don't confuse it with the domain `Result`. */
typealias WorkerResult = ListenableWorker.Result
