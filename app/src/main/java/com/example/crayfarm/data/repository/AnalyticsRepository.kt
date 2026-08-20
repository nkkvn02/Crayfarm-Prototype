package com.example.crayfarm

import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.DataSnapshot

internal interface AnalyticsRepository {
    fun observeHistory(
        farmId: String,
        pondId: String,
        onData: (List<PondHistoryRecord>) -> Unit,
        onError: (String) -> Unit
    ): ReportSubscription
}

internal class FirebaseAnalyticsRepository(
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()
) : AnalyticsRepository {
    override fun observeHistory(
        farmId: String,
        pondId: String,
        onData: (List<PondHistoryRecord>) -> Unit,
        onError: (String) -> Unit
    ): ReportSubscription {
        val historyRef = database.getReference("farms")
            .child(farmId)
            .child("ponds")
            .child(pondId)
            .child("history")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                onData(
                    snapshot.children.mapNotNull { child ->
                        child.toPondHistoryRecord()
                    }.sortedBy { record -> record.recordedAt }
                )
            }

            override fun onCancelled(error: DatabaseError) {
                onError(error.message)
            }
        }

        historyRef.addValueEventListener(listener)
        return ReportSubscription { historyRef.removeEventListener(listener) }
    }
}
