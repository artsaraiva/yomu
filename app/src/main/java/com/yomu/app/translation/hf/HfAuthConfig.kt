package com.yomu.app.translation.hf

/**
 * HuggingFace OAuth configuration for the tier-2/3 authenticated download path (ADR-0009, #90 part C).
 *
 * The client is a **public** OAuth app (PKCE, no client secret), so the client id is safe to ship in
 * the APK and commit — exposure is designed for: PKCE plus the registered redirect scheme are what
 * prevent misuse, not secrecy of the id. The user signs into their own HF account and Yomu pulls a
 * GGUF under their credentials, so Yomu is a conduit, not a redistributor.
 */
object HfAuthConfig {
    const val CLIENT_ID = "94e61910-0915-4a65-b40e-a768cf36a275"

    // Custom-scheme deep link registered on the HF app; must match the app's intent-filter byte-for-byte.
    const val REDIRECT_URI = "yomu://auth/callback"

    // Least-privilege scopes: openid (required) + gated-repos (read public gated repos, e.g. Gemma,
    // after the user accepts the gate). No read-repos — that would also grant the user's private repos.
    const val SCOPES = "openid gated-repos"

    const val AUTHORIZATION_ENDPOINT = "https://huggingface.co/oauth/authorize"
    const val TOKEN_ENDPOINT = "https://huggingface.co/oauth/token"
    const val OPENID_CONFIG_URL = "https://huggingface.co/.well-known/openid-configuration"
}
