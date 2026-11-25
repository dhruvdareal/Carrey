package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.Customer
import com.example.data.CustomerRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface CustomersUiState {
    data object Loading : CustomersUiState
    data class Success(val customers: List<Customer>) : CustomersUiState
    data class Error(val message: String) : CustomersUiState
}

data class CustomerFormState(
    val editingCustomerId: String? = null,
    val name: String = "",
    val phone: String = "",
    val dob: String = "",
    val email: String = "",
    val address: String = "",
    val nameError: String? = null,
    val phoneError: String? = null,
    val dobError: String? = null,
    val emailError: String? = null,
    val addressError: String? = null,
    val isSubmitting: Boolean = false
) {
    val isEditing: Boolean get() = editingCustomerId != null
}

class CustomerViewModel(
    private val repository: CustomerRepository,
    private val currentUserId: String
) : ViewModel() {

    private val retryTrigger = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val customersState: StateFlow<CustomersUiState> = retryTrigger
        .flatMapLatest {
            repository.observeCustomers(currentUserId)
                .map<List<Customer>, CustomersUiState> { CustomersUiState.Success(it) }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val rootError = firestoreError ?: error
                    Log.w(TAG, "Error observing customers", rootError)
                    emit(
                        CustomersUiState.Error(
                            rootError.message ?: "Failed to load customer directory."
                        )
                    )
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = CustomersUiState.Loading
        )

    private val _formState = MutableStateFlow(CustomerFormState())
    val formState: StateFlow<CustomerFormState> = _formState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isAddingDummyData = MutableStateFlow(false)
    val isAddingDummyData: StateFlow<Boolean> = _isAddingDummyData.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    fun retryLoading() {
        retryTrigger.update { it + 1 }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onNameChange(value: String) {
        _formState.update { it.copy(name = value.take(100), nameError = null) }
    }

    fun onPhoneChange(value: String) {
        val digitsOnly = value.filter { it.isDigit() }.take(10)
        _formState.update { it.copy(phone = digitsOnly, phoneError = null) }
    }

    fun onDobChange(value: String) {
        _formState.update { it.copy(dob = value, dobError = null) }
    }

    fun onEmailChange(value: String) {
        _formState.update { it.copy(email = value.take(120), emailError = null) }
    }

    fun onAddressChange(value: String) {
        _formState.update { it.copy(address = value.take(300), addressError = null) }
    }

    fun startEditing(customer: Customer) {
        _formState.value = CustomerFormState(
            editingCustomerId = customer.id,
            name = customer.name,
            phone = customer.phone,
            dob = customer.dob,
            email = customer.email,
            address = customer.address
        )
    }

    fun clearForm() {
        _formState.value = CustomerFormState()
    }

    fun consumeSnackbarMessage() {
        _snackbarMessage.value = null
    }

    fun submitCustomer(onSuccess: () -> Unit = {}) {
        val current = _formState.value
        if (current.isSubmitting) return

        val trimmedName = current.name.trim()
        val trimmedPhone = current.phone.trim()
        val trimmedDob = current.dob.trim()
        val trimmedEmail = current.email.trim()
        val trimmedAddress = current.address.trim()

        val nameError = when {
            trimmedName.isEmpty() -> "Full name is required."
            trimmedName.length < 2 -> "Name must be at least 2 characters."
            else -> null
        }
        val phoneError = when {
            trimmedPhone.isEmpty() -> "Phone number is required."
            !CustomerRepository.PHONE_REGEX.matches(trimmedPhone) -> "Phone must be exactly 10 digits."
            else -> null
        }
        val dobError = if (trimmedDob.isEmpty()) "Date of birth is required." else null
        val emailError = when {
            trimmedEmail.isEmpty() -> "Email address is required."
            !CustomerRepository.EMAIL_REGEX.matches(trimmedEmail) -> "Enter a valid email address."
            else -> null
        }
        val addressError = when {
            trimmedAddress.isEmpty() -> "Address is required."
            trimmedAddress.length < 5 -> "Please enter a complete address (min 5 characters)."
            else -> null
        }

        if (listOf(nameError, phoneError, dobError, emailError, addressError).any { it != null }) {
            _formState.update {
                it.copy(
                    nameError = nameError,
                    phoneError = phoneError,
                    dobError = dobError,
                    emailError = emailError,
                    addressError = addressError
                )
            }
            return
        }

        viewModelScope.launch {
            _formState.update { it.copy(isSubmitting = true) }
            val editingId = current.editingCustomerId
            val result = if (editingId == null) {
                repository.addCustomer(
                    name = trimmedName,
                    phone = trimmedPhone,
                    dob = trimmedDob,
                    email = trimmedEmail,
                    address = trimmedAddress
                )
            } else {
                repository.updateCustomer(
                    customerId = editingId,
                    name = trimmedName,
                    phone = trimmedPhone,
                    dob = trimmedDob,
                    email = trimmedEmail,
                    address = trimmedAddress
                )
            }

            result.fold(
                onSuccess = {
                    _formState.value = CustomerFormState()
                    _snackbarMessage.value = if (editingId == null) {
                        "Customer \"$trimmedName\" registered successfully!"
                    } else {
                        "Customer \"$trimmedName\" updated successfully!"
                    }
                    onSuccess()
                },
                onFailure = { error ->
                    _formState.update { it.copy(isSubmitting = false) }
                    _snackbarMessage.value = error.localizedMessage
                        ?: "Could not save customer. Please try again."
                }
            )
        }
    }

    fun addDummyCustomers() {
        if (_isAddingDummyData.value) return
        viewModelScope.launch {
            _isAddingDummyData.value = true
            repository.addDummyCustomers().fold(
                onSuccess = { count ->
                    _snackbarMessage.value = "$count sample Indian customers added to Firestore!"
                },
                onFailure = { error ->
                    _snackbarMessage.value = error.localizedMessage
                        ?: "Failed to insert sample customers."
                }
            )
            _isAddingDummyData.value = false
        }
    }

    fun deleteCustomer(customer: Customer) {
        viewModelScope.launch {
            repository.deleteCustomer(customer.id).fold(
                onSuccess = {
                    if (_formState.value.editingCustomerId == customer.id) {
                        clearForm()
                    }
                    _snackbarMessage.value = "Removed ${customer.name} from directory."
                },
                onFailure = { error ->
                    _snackbarMessage.value = error.localizedMessage
                        ?: "Failed to delete customer."
                }
            )
        }
    }

    private companion object {
        const val TAG = "CustomerViewModel"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
