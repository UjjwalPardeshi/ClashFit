package com.clashfit.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.clashfit.auth.AuthError
import com.clashfit.auth.AuthResult
import com.clashfit.auth.AuthRules
import com.clashfit.auth.AuthService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The sign-up and sign-in forms. Validation runs on the phone before anything is sent; a
 * backend error lands under the field it belongs to, and everything else in a banner.
 */
class AuthViewModel(private val auth: AuthService) : ViewModel() {

    data class Form(
        val name: String = "",
        val email: String = "",
        val password: String = "",
        val confirm: String = "",
        val busy: Boolean = false,
        val nameError: String? = null,
        val emailError: String? = null,
        val passwordError: String? = null,
        val confirmError: String? = null,
        /** An error that is not about one field: network, unknown, not configured. */
        val banner: String? = null,
        val resetSent: Boolean = false,
    ) {
        val canSubmit: Boolean get() = !busy
    }

    private val _form = MutableStateFlow(Form())
    val form: StateFlow<Form> = _form.asStateFlow()

    val isCloud: Boolean get() = auth.isCloud

    fun onName(v: String) = _form.update { it.copy(name = v, nameError = null, banner = null) }
    fun onEmail(v: String) = _form.update { it.copy(email = v, emailError = null, banner = null, resetSent = false) }
    fun onPassword(v: String) = _form.update { it.copy(password = v, passwordError = null, banner = null) }
    fun onConfirm(v: String) = _form.update { it.copy(confirm = v, confirmError = null) }

    fun signUp(onDone: () -> Unit) {
        val f = _form.value
        val nameError = if (AuthRules.nameOk(f.name)) null else "Two to twenty-four characters."
        val emailError = AuthRules.emailError(f.email)?.message
        val passwordError = AuthRules.passwordError(f.password)?.message
        val confirmError = if (f.confirm == f.password) null else "Passwords do not match."
        if (listOf(nameError, emailError, passwordError, confirmError).any { it != null }) {
            _form.update { it.copy(nameError = nameError, emailError = emailError, passwordError = passwordError, confirmError = confirmError) }
            return
        }
        submit(onDone) { auth.signUp(f.email.trim(), f.password, f.name.trim()) }
    }

    fun signIn(onDone: () -> Unit) {
        val f = _form.value
        val emailError = AuthRules.emailError(f.email)?.message
        val passwordError = if (f.password.isEmpty()) "Enter your password." else null
        if (emailError != null || passwordError != null) {
            _form.update { it.copy(emailError = emailError, passwordError = passwordError) }
            return
        }
        submit(onDone) { auth.signIn(f.email.trim(), f.password) }
    }

    fun sendReset() {
        val f = _form.value
        val emailError = AuthRules.emailError(f.email)?.message
        if (emailError != null) { _form.update { it.copy(emailError = emailError) }; return }
        _form.update { it.copy(busy = true) }
        viewModelScope.launch {
            val error = auth.sendPasswordReset(f.email.trim())
            _form.update { it.copy(busy = false, resetSent = error == null) }
            if (error != null) place(error)
        }
    }

    private fun submit(onDone: () -> Unit, call: suspend () -> AuthResult) {
        _form.update { it.copy(busy = true, banner = null) }
        viewModelScope.launch {
            when (val r = call()) {
                is AuthResult.Ok -> { _form.update { it.copy(busy = false) }; onDone() }
                is AuthResult.Failed -> { _form.update { it.copy(busy = false) }; place(r.error) }
            }
        }
    }

    /** Put a backend error where the player will look for it. */
    private fun place(error: AuthError) = _form.update {
        when (error) {
            AuthError.INVALID_EMAIL, AuthError.EMAIL_IN_USE, AuthError.USER_NOT_FOUND -> it.copy(emailError = error.message)
            AuthError.WEAK_PASSWORD, AuthError.WRONG_PASSWORD -> it.copy(passwordError = error.message)
            AuthError.TOO_MANY_ATTEMPTS, AuthError.NETWORK, AuthError.NOT_CONFIGURED, AuthError.UNKNOWN -> it.copy(banner = error.message)
        }
    }

    companion object {
        fun factory(auth: AuthService) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = AuthViewModel(auth) as T
        }
    }
}
