package com.yomu.app.translation.hf

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Drives the HuggingFace OAuth (PKCE) flow via AppAuth and persists the resulting access token in the
 * Keystore-backed [HfTokenStore] (#90 part C). Yomu never sees a client secret — the public-client
 * PKCE exchange proves possession of the code_verifier instead.
 *
 * The Activity owns the round-trip: it launches [authorizeIntent] for result, then hands the returned
 * intent to [completeAuthorization]. This class holds no Activity reference.
 */
@Singleton
class HfAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokenStore: HfTokenStore
) {
    private val serviceConfig = AuthorizationServiceConfiguration(
        Uri.parse(HfAuthConfig.AUTHORIZATION_ENDPOINT),
        Uri.parse(HfAuthConfig.TOKEN_ENDPOINT)
    )

    fun isSignedIn(): Boolean = tokenStore.hasToken()

    fun accessToken(): String? = tokenStore.get()

    fun signOut() = tokenStore.clear()

    /** Intent the Activity launches for result to start the browser sign-in. */
    fun authorizeIntent(): Intent {
        val request = AuthorizationRequest.Builder(
            serviceConfig,
            HfAuthConfig.CLIENT_ID,
            ResponseTypeValues.CODE,
            Uri.parse(HfAuthConfig.REDIRECT_URI)
        ).setScope(HfAuthConfig.SCOPES).build()
        // The returned intent is self-contained (it launches the browser/custom-tab), so the service
        // is only needed to build it — dispose immediately to avoid leaking the binding.
        val service = AuthorizationService(context)
        return service.getAuthorizationRequestIntent(request).also { service.dispose() }
    }

    /**
     * Complete the flow from the redirect the Activity received: validate the response and exchange
     * the auth code for an access token (PKCE), persisting it on success. Returns false on any
     * cancellation or error.
     */
    suspend fun completeAuthorization(data: Intent): Boolean {
        val response = AuthorizationResponse.fromIntent(data)
        val error = AuthorizationException.fromIntent(data)
        if (response == null) {
            Log.w(TAG, "authorization failed error=${error?.errorDescription}")
            return false
        }
        val service = AuthorizationService(context)
        return try {
            val token = suspendCancellableCoroutine { cont ->
                service.performTokenRequest(response.createTokenExchangeRequest()) { tokenResponse, ex ->
                    cont.resume(tokenResponse?.accessToken)
                    if (ex != null) Log.w(TAG, "token exchange failed error=${ex.errorDescription}")
                }
            }
            if (token.isNullOrBlank()) {
                false
            } else {
                tokenStore.save(token)
                Log.i(TAG, "sign-in complete")
                true
            }
        } finally {
            service.dispose()
        }
    }

    companion object {
        private const val TAG = "HfAuthManager"
    }
}
