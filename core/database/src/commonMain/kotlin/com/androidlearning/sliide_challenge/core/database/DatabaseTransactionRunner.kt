package com.androidlearning.sliide_challenge.core.database

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection

/** Runs several DAO calls as one transaction: either all of their writes are kept or none are. */
interface DatabaseTransactionRunner {
    /** [block] must only touch the database: it holds the write connection, so no network calls or new coroutines. */
    suspend fun <R> runInTransaction(block: suspend () -> R): R
}

/** DAO calls made inside the block run on the transaction's connection, so they join it. */
class RoomTransactionRunner(private val database: SliideDatabase) : DatabaseTransactionRunner {
    override suspend fun <R> runInTransaction(block: suspend () -> R): R =
        database.useWriterConnection { transactor -> transactor.immediateTransaction { block() } }
}
