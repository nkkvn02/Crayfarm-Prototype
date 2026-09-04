package com.example.crayfarm

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import java.util.Locale

internal fun linkRegisteredDeviceAndCreatePond(
    user: CrayFarmUser,
    pondName: String,
    deviceId: String,
    onSuccess: (PondSummary) -> Unit,
    onError: (String) -> Unit
) {
    if (!canAddPond(user.role)) {
        onError("You do not have permission to add this device.")
        return
    }

    if (user.farmId.isBlank()) {
        onError("This account is not connected to a farm.")
        return
    }

    val database = FirebaseDatabase.getInstance().reference
    val pondId = database
        .child("farms")
        .child(user.farmId)
        .child("ponds")
        .push()
        .key

    if (pondId == null) {
        onError("Unable to create a pond identifier.")
        return
    }

    val normalizedDeviceId = deviceId.trim().uppercase(Locale.ROOT)
    val deviceRef = database
        .child("devices")
        .child(normalizedDeviceId)
    val linkedAt = System.currentTimeMillis()

    deviceRef.runTransaction(
        object : Transaction.Handler {
            override fun doTransaction(
                currentData: MutableData
            ): Transaction.Result {
                val eligibility = determineDeviceEligibility(
                    requestedDeviceId = normalizedDeviceId,
                    state = currentData.toDeviceRegistryState()
                )

                if (eligibility != DeviceEligibility.AVAILABLE) {
                    return Transaction.abort()
                }

                currentData.child("farmId").value = user.farmId
                currentData.child("pondId").value = pondId
                currentData.child("status").value = DEVICE_STATUS_ASSIGNED
                currentData.child("linkedAt").value = linkedAt
                currentData.child("linkedBy").value = user.uid

                return Transaction.success(currentData)
            }

            override fun onComplete(
                error: DatabaseError?,
                committed: Boolean,
                currentData: DataSnapshot?
            ) {
                when {
                    error != null -> {
                        onError(error.deviceVerificationMessage())
                    }

                    !committed -> {
                        val eligibility = determineDeviceEligibility(
                            requestedDeviceId = normalizedDeviceId,
                            state = currentData.toDeviceRegistryState()
                        )
                        onError(eligibility.userMessage())
                    }

                    else -> {
                        savePondAfterDeviceClaim(
                            database = database,
                            deviceRef = deviceRef,
                            user = user,
                            pondId = pondId,
                            pondName = pondName,
                            deviceId = normalizedDeviceId,
                            linkedAt = linkedAt,
                            onSuccess = onSuccess,
                            onError = onError
                        )
                    }
                }
            }
        }
    )
}

internal fun deletePondAndReleaseDevice(
    user: CrayFarmUser,
    pond: PondSummary,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    if (user.farmId.isBlank()) {
        onError("This account is not connected to a farm.")
        return
    }

    val updates = hashMapOf<String, Any?>(
        "farms/${user.farmId}/ponds/${pond.pondId}" to null
    )

    if (pond.deviceId.isNotBlank()) {
        val devicePath = "devices/${pond.deviceId}"
        updates["$devicePath/farmId"] = ""
        updates["$devicePath/pondId"] = ""
        updates["$devicePath/status"] = DEVICE_STATUS_AVAILABLE
        updates["$devicePath/linkedAt"] = null
        updates["$devicePath/linkedBy"] = null
    }

    FirebaseDatabase.getInstance().reference
        .updateChildren(updates)
        .addOnSuccessListener {
            addActivityLog(
                farmId = user.farmId,
                type = "pond_deleted",
                title = "Pond deleted",
                message =
                    "${pond.pondName} was deleted and ${pond.deviceId.ifBlank { "its ESP32" }} was released by ${user.fullName}.",
                actorUid = user.uid,
                actorName = user.fullName
            )
            onSuccess()
        }
        .addOnFailureListener { exception ->
            onError(exception.message ?: "Unable to delete the pond.")
        }
}

private fun savePondAfterDeviceClaim(
    database: DatabaseReference,
    deviceRef: DatabaseReference,
    user: CrayFarmUser,
    pondId: String,
    pondName: String,
    deviceId: String,
    linkedAt: Long,
    onSuccess: (PondSummary) -> Unit,
    onError: (String) -> Unit
) {
    val pondData = mapOf(
        "pondName" to pondName,
        "deviceId" to deviceId,
        "createdAt" to linkedAt,
        "createdBy" to user.uid,
        "active" to true
    )

    database
        .child("farms")
        .child(user.farmId)
        .child("ponds")
        .child(pondId)
        .setValue(pondData)
        .addOnSuccessListener {
            addActivityLog(
                farmId = user.farmId,
                type = "pond_added",
                title = "Pond added",
                message = "$pondName was linked to $deviceId by ${user.fullName}.",
                actorUid = user.uid,
                actorName = user.fullName
            )

            onSuccess(
                PondSummary(
                    pondId = pondId,
                    pondName = pondName,
                    deviceId = deviceId
                )
            )
        }
        .addOnFailureListener { exception ->
            releaseDeviceClaim(
                deviceRef = deviceRef,
                farmId = user.farmId,
                pondId = pondId
            ) { released ->
                onError(
                    if (released) {
                        exception.message ?: "Unable to save the pond."
                    } else {
                        "Unable to save the pond and automatically release the device. Contact the system administrator."
                    }
                )
            }
        }
}

private fun releaseDeviceClaim(
    deviceRef: DatabaseReference,
    farmId: String,
    pondId: String,
    onComplete: (Boolean) -> Unit
) {
    deviceRef.runTransaction(
        object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val currentFarmId = currentData.child("farmId").stringValue()
                val currentPondId = currentData.child("pondId").stringValue()

                if (currentFarmId != farmId || currentPondId != pondId) {
                    return Transaction.abort()
                }

                currentData.child("farmId").value = ""
                currentData.child("pondId").value = ""
                currentData.child("status").value = DEVICE_STATUS_AVAILABLE
                currentData.child("linkedAt").value = null
                currentData.child("linkedBy").value = null

                return Transaction.success(currentData)
            }

            override fun onComplete(
                error: DatabaseError?,
                committed: Boolean,
                currentData: DataSnapshot?
            ) {
                onComplete(error == null && committed)
            }
        }
    )
}

private fun MutableData.toDeviceRegistryState(): DeviceRegistryState {
    return DeviceRegistryState(
        exists = value != null,
        deviceId = child("deviceId").stringValue(),
        active = child("active").getValue(Boolean::class.java),
        status = child("status").stringValue(),
        farmId = child("farmId").stringValue(),
        pondId = child("pondId").stringValue()
    )
}

private fun DataSnapshot?.toDeviceRegistryState(): DeviceRegistryState {
    return DeviceRegistryState(
        exists = this?.exists() == true,
        deviceId = this?.child("deviceId")?.getValue(String::class.java).orEmpty(),
        active = this?.child("active")?.getValue(Boolean::class.java),
        status = this?.child("status")?.getValue(String::class.java).orEmpty(),
        farmId = this?.child("farmId")?.getValue(String::class.java).orEmpty(),
        pondId = this?.child("pondId")?.getValue(String::class.java).orEmpty()
    )
}

private fun MutableData.stringValue(): String =
    getValue(String::class.java).orEmpty()

private fun DatabaseError.deviceVerificationMessage(): String {
    return if (code == DatabaseError.PERMISSION_DENIED) {
        "You do not have permission to add this device."
    } else {
        "Unable to verify device. Please try again."
    }
}
