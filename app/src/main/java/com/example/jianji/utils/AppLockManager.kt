package com.example.jianji.utils

import android.app.KeyguardManager
import android.content.Context
import android.content.SharedPreferences
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

class AppLockManager(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("app_lock", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_LOCK_ENABLED = "lock_enabled"
        private const val KEY_PIN = "lock_pin"
        private const val KEY_BIOMETRIC = "lock_biometric"
    }

    val isLockEnabled: Boolean get() = prefs.getBoolean(KEY_LOCK_ENABLED, false)
    val isBiometricEnabled: Boolean get() = prefs.getBoolean(KEY_BIOMETRIC, true)
    val hasPin: Boolean get() = prefs.getString(KEY_PIN, null) != null

    fun canUseBiometric(): Boolean {
        val bm = BiometricManager.from(context)
        return bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun enableLock(enable: Boolean) {
        prefs.edit().putBoolean(KEY_LOCK_ENABLED, enable).apply()
    }

    fun setPin(pin: String) {
        prefs.edit().putString(KEY_PIN, pin).apply()
    }

    fun verifyPin(pin: String): Boolean {
        return prefs.getString(KEY_PIN, null) == pin
    }

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC, enabled).apply()
    }

    fun authenticate(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onPinFallback: (() -> Unit)? = null
    ) {
        if (!isBiometricEnabled) {
            onPinFallback?.invoke()
            return
        }
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("简记 · 身份验证")
            .setSubtitle("扫描指纹或面部以解锁应用")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButtonText("使用密碼")
            .build()

        val executor = ContextCompat.getMainExecutor(context)
        val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON || errorCode == BiometricPrompt.ERROR_USER_CANCELED) {
                    onPinFallback?.invoke()
                } else {
                    onError(errString.toString())
                }
            }
            override fun onAuthenticationFailed() {
                onError("验证失败，请重试")
            }
        })
        prompt.authenticate(promptInfo)
    }
}
