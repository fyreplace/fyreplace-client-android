package app.fyreplace.client.viewmodels

import androidx.lifecycle.*
import app.fyreplace.client.data.repositories.AuthRepository

class LoginFragmentViewModel(private val authRepository: AuthRepository) : ViewModel() {
    private val mAuthToken = MutableLiveData<String>()
    private val mLoginAllowed = MutableLiveData(true)

    val authToken: LiveData<String> = mAuthToken
    val loginAllowed: LiveData<Boolean> = mLoginAllowed.distinctUntilChanged()
    val username = MutableLiveData<String>()
    val password = MutableLiveData<String>()
    val usernameValid: LiveData<Boolean> = username.map { it.isNotEmpty() }
    val passwordValid: LiveData<Boolean> = password.map { it.isNotEmpty() }

    suspend fun attemptLogin(username: String, password: String) =
        mAuthToken.postValue(authRepository.getAuthToken(username, password))

    fun setLoginAllowed(allowed: Boolean) = mLoginAllowed.postValue(allowed)
}
