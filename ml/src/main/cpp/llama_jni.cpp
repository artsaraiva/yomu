#include <jni.h>
#include <string>
#include <vector>
#include <cstring>
#include <chrono>
#include <atomic>
#include <android/log.h>
#include "llama.h"

#define LOG_TAG "LlamaJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static JavaVM *g_jvm = nullptr;
static llama_model *g_model = nullptr;
static llama_context *g_ctx = nullptr;
static const llama_vocab *g_vocab = nullptr;
static llama_sampler *g_sampler = nullptr;
static std::atomic<int64_t> g_abort_deadline_ms{0};

static int64_t now_ms() {
    return std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::steady_clock::now().time_since_epoch()
    ).count();
}

static bool abort_if_timed_out(void *) {
    const int64_t deadline_ms = g_abort_deadline_ms.load(std::memory_order_relaxed);
    return deadline_ms > 0 && now_ms() > deadline_ms;
}

static bool is_past_deadline() {
    const int64_t deadline_ms = g_abort_deadline_ms.load(std::memory_order_relaxed);
    return deadline_ms > 0 && now_ms() > deadline_ms;
}

static bool rebuild_sampler(float temperature) {
    if (g_sampler) {
        llama_sampler_free(g_sampler);
        g_sampler = nullptr;
    }

    auto sparams = llama_sampler_chain_default_params();
    g_sampler = llama_sampler_chain_init(sparams);
    if (!g_sampler) {
        return false;
    }

    llama_sampler_chain_add(g_sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    return true;
}

extern "C" JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM *vm, void *reserved) {
    g_jvm = vm;
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_yomu_ml_LlamaBridge_nativeLoadModel(
    JNIEnv *env,
    jobject /* this */,
    jstring model_path,
    jint n_ctx,
    jint n_gpu_layers) {

    const char *path = env->GetStringUTFChars(model_path, nullptr);
    LOGI("Loading model from: %s", path);

    llama_backend_init();

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = n_gpu_layers;

    g_model = llama_model_load_from_file(path, model_params);
    env->ReleaseStringUTFChars(model_path, path);

    if (!g_model) {
        LOGE("Failed to load model");
        return JNI_FALSE;
    }

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = (uint32_t)n_ctx;
    ctx_params.n_batch = (uint32_t)n_ctx;
    ctx_params.n_threads = 2;
    ctx_params.n_threads_batch = 2;
    ctx_params.abort_callback = abort_if_timed_out;
    ctx_params.abort_callback_data = nullptr;

    g_ctx = llama_init_from_model(g_model, ctx_params);
    if (!g_ctx) {
        LOGE("Failed to create context");
        llama_model_free(g_model);
        g_model = nullptr;
        return JNI_FALSE;
    }

    g_vocab = llama_model_get_vocab(g_model);

    if (!rebuild_sampler(0.8f)) {
        LOGE("Failed to create sampler");
        llama_free(g_ctx);
        g_ctx = nullptr;
        llama_model_free(g_model);
        g_model = nullptr;
        g_vocab = nullptr;
        return JNI_FALSE;
    }

    LOGI("Model loaded successfully");
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_yomu_ml_LlamaBridge_nativeGenerate(
    JNIEnv *env,
    jobject /* this */,
    jstring prompt,
    jint max_tokens,
    jfloat temperature) {

    const int64_t started_ms = now_ms();

    if (!g_ctx || !g_model || !g_vocab) {
        LOGE("Model not loaded");
        return env->NewStringUTF("");
    }

    g_abort_deadline_ms.store(now_ms() + 4000, std::memory_order_relaxed);
    llama_set_abort_callback(g_ctx, abort_if_timed_out, nullptr);

    auto *memory = llama_get_memory(g_ctx);
    if (memory) {
        llama_memory_clear(memory, false);
    }

    if (!rebuild_sampler(temperature)) {
        LOGE("Failed to rebuild sampler");
        g_abort_deadline_ms.store(0, std::memory_order_relaxed);
        return env->NewStringUTF("");
    }

    if (!g_sampler) {
        LOGE("Sampler unavailable");
        g_abort_deadline_ms.store(0, std::memory_order_relaxed);
        return env->NewStringUTF("");
    }

    const char *prompt_str = env->GetStringUTFChars(prompt, nullptr);
    int prompt_len = (int)strlen(prompt_str);

    // Tokenize input
    int n_tokens = llama_tokenize(g_vocab, prompt_str, prompt_len, nullptr, 0, true, false);
    if (n_tokens < 0) {
        n_tokens = -n_tokens;
    }
    if (n_tokens == 0) {
        LOGE("Failed to tokenize prompt");
        env->ReleaseStringUTFChars(prompt, prompt_str);
        g_abort_deadline_ms.store(0, std::memory_order_relaxed);
        return env->NewStringUTF("");
    }

    std::vector<llama_token> tokens(n_tokens);
    int written = llama_tokenize(g_vocab, prompt_str, prompt_len, tokens.data(), n_tokens, true, false);
    env->ReleaseStringUTFChars(prompt, prompt_str);
    if (written < 0) {
        LOGE("Failed to write prompt tokens");
        g_abort_deadline_ms.store(0, std::memory_order_relaxed);
        return env->NewStringUTF("");
    }
    tokens.resize(written);

    int context_size = (int)llama_n_ctx(g_ctx);
    int max_prompt_tokens = 512;
    int context_limit = context_size - max_tokens - 8;
    if (max_prompt_tokens > context_limit) {
        max_prompt_tokens = context_limit;
    }
    if (max_prompt_tokens <= 0) {
        max_prompt_tokens = context_size / 2;
    }
    if ((int)tokens.size() > max_prompt_tokens) {
        LOGE("Prompt too long: %d tokens, trimming to %d", (int)tokens.size(), max_prompt_tokens);
        tokens.erase(tokens.begin(), tokens.end() - max_prompt_tokens);
    }

    // Process prompt
    llama_batch batch = llama_batch_get_one(tokens.data(), (int)tokens.size());
    if (llama_decode(g_ctx, batch) != 0) {
        const int64_t elapsed_ms = now_ms() - started_ms;
        if (is_past_deadline()) {
            LOGW("Decode prompt aborted deadline elapsedMs=%lld", (long long)elapsed_ms);
        } else {
            LOGE("Failed to decode prompt elapsedMs=%lld", (long long)elapsed_ms);
        }
        g_abort_deadline_ms.store(0, std::memory_order_relaxed);
        return env->NewStringUTF("");
    }

    // Generation loop
    std::string result;
    int n_len = 0;
    llama_token new_token_id;
    llama_token eos = llama_vocab_eos(g_vocab);

    while (n_len < max_tokens) {
        new_token_id = llama_sampler_sample(g_sampler, g_ctx, -1);
        llama_sampler_accept(g_sampler, new_token_id);

        if (new_token_id == eos) break;

        // Convert token to text
        char buf[256];
        int n = llama_token_to_piece(g_vocab, new_token_id, buf, sizeof(buf), 0, false);
        if (n > 0) {
            result.append(buf, n);
        }

        // Feed token back
        tokens[0] = new_token_id;
        batch = llama_batch_get_one(tokens.data(), 1);
        if (llama_decode(g_ctx, batch) != 0) {
            const int64_t elapsed_ms = now_ms() - started_ms;
            if (is_past_deadline()) {
                LOGW("Decode token aborted deadline generatedTokens=%d elapsedMs=%lld", n_len, (long long)elapsed_ms);
            } else {
                LOGE("Failed to decode generated token at=%d elapsedMs=%lld", n_len, (long long)elapsed_ms);
            }
            break;
        }

        n_len++;
    }

    const int64_t elapsed_ms = now_ms() - started_ms;
    LOGI("Generation complete tokens=%d resultLength=%zu durationMs=%lld", n_len, result.size(), (long long)elapsed_ms);
    g_abort_deadline_ms.store(0, std::memory_order_relaxed);
    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_yomu_ml_LlamaBridge_nativeRelease(JNIEnv *env, jobject /* this */) {
    LOGI("Releasing model resources");

    if (g_sampler) {
        llama_sampler_free(g_sampler);
        g_sampler = nullptr;
    }

    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }

    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }

    g_vocab = nullptr;
    llama_backend_free();
    LOGI("Model resources released");
}
