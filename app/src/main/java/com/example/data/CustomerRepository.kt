package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class CustomerRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeCustomers(userId: String): Flow<List<Customer>> = flow {
        require(userId.isNotBlank()) { "Authenticated userId is required." }
        emitAll(
            db.collection(COLLECTION_CUSTOMERS)
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Customer::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, COLLECTION_CUSTOMERS)
                    }
                    throw error
                }
        )
    }

    suspend fun addCustomer(
        name: String,
        phone: String,
        dob: String,
        email: String,
        address: String
    ): Result<Unit> {
        var docPath = COLLECTION_CUSTOMERS
        return try {
            val uid = requireUserId()
            validateCustomerInput(name, phone, dob, email, address)
            val docRef = db.collection(COLLECTION_CUSTOMERS).document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "name" to name.trim(),
                "phone" to phone.trim(),
                "dob" to dob.trim(),
                "email" to email.trim(),
                "address" to address.trim(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateCustomer(
        customerId: String,
        name: String,
        phone: String,
        dob: String,
        email: String,
        address: String
    ): Result<Unit> {
        var docPath = "$COLLECTION_CUSTOMERS/$customerId"
        return try {
            requireUserId()
            require(customerId.isNotBlank()) { "Customer ID cannot be blank." }
            validateCustomerInput(name, phone, dob, email, address)
            val docRef = db.collection(COLLECTION_CUSTOMERS).document(customerId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "name" to name.trim(),
                    "phone" to phone.trim(),
                    "dob" to dob.trim(),
                    "email" to email.trim(),
                    "address" to address.trim(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteCustomer(customerId: String): Result<Unit> {
        val docPath = "$COLLECTION_CUSTOMERS/$customerId"
        return try {
            requireUserId()
            require(customerId.isNotBlank()) { "Customer ID cannot be blank." }
            db.collection(COLLECTION_CUSTOMERS).document(customerId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun addDummyCustomers(): Result<Int> {
        return try {
            val uid = requireUserId()
            val batch = db.batch()
            SampleCustomers.defaultList.forEach { sample ->
                val docRef = db.collection(COLLECTION_CUSTOMERS).document()
                val payload = mapOf(
                    "userId" to uid,
                    "name" to sample.name,
                    "phone" to sample.phone,
                    "dob" to sample.dob,
                    "email" to sample.email,
                    "address" to sample.address,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                batch.set(docRef, payload)
            }
            batch.commit().await()
            Result.success(SampleCustomers.defaultList.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, COLLECTION_CUSTOMERS)
            Result.failure(e)
        }
    }

    private fun validateCustomerInput(
        name: String,
        phone: String,
        dob: String,
        email: String,
        address: String
    ) {
        val trimmedName = name.trim()
        val trimmedPhone = phone.trim()
        val trimmedDob = dob.trim()
        val trimmedEmail = email.trim()
        val trimmedAddress = address.trim()

        require(trimmedName.length in 2..100) { "Full name must be between 2 and 100 characters." }
        require(PHONE_REGEX.matches(trimmedPhone)) { "Phone number must be exactly 10 digits." }
        require(trimmedDob.length in 8..20) { "Date of birth is required." }
        require(trimmedEmail.length in 5..120 && EMAIL_REGEX.matches(trimmedEmail)) {
            "Enter a valid email address."
        }
        require(trimmedAddress.length in 5..300) { "Address must be between 5 and 300 characters." }
    }

    companion object {
        const val COLLECTION_CUSTOMERS = "customers"
        val PHONE_REGEX = Regex("^[0-9]{10}$")
        val EMAIL_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    }
}
