#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "llama.h"

#define LOG_TAG "LlamaJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static JavaVM *g_jvm = nullptr;
static llama_model *g_model = nullptr;
static llama_context *g_ctx = nullptr;
static const llama_vocab *g_vocab = nullptr;
static llama_sampler *g_sampler = nullptr;

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

    g_ctx = llama_init_from_model(g_model, ctx_params);
    if (!g_ctx) {
        LOGE("Failed to create context");
        llama_model_free(g_model);
        g_model = nullptr;
        return JNI_FALSE;
    }

    g_vocab = llama_model_get_vocab(g_model);

    auto sparams = llama_sampler_chain_default_params();
    g_sampler = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(g_sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_temp(0.8f));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

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

    if (!g_ctx || !g_model) {
        LOGE("Model not loaded");
        return env->NewStringUTF("");
    }

    const char *prompt_str = env->GetStringUTFChars(prompt, nullptr);
    int prompt_len = (int)strlen(prompt_str);

    // Tokenize input
    int n_tokens = llama_tokenize(g_vocab, prompt_str, prompt_len, nullptr, 0, true, false);
    if (n_tokens < 0) {
        LOGE("Failed to tokenize prompt");
        env->ReleaseStringUTFChars(prompt, prompt_str);
        return env->NewStringUTF("");
    }

    std::vector<llama_token> tokens(n_tokens);
    llama_tokenize(g_vocab, prompt_str, prompt_len, tokens.data(), n_tokens, true, false);
    env->ReleaseStringUTFChars(prompt, prompt_str);

    // Process prompt
    llama_batch batch = llama_batch_get_one(tokens.data(), n_tokens);
    if (llama_decode(g_ctx, batch) != 0) {
        LOGE("Failed to decode prompt");
        return env->NewStringUTF("");
    }

    // Generation loop
    std::string result;
    int n_len = 0;
    llama_token new_token_id;
    llama_token eos = llama_vocab_eos(g_vocab);

    while (n_len < max_tokens) {
        new_token_id = llama_sampler_sample(g_sampler, g_ctx, -1);

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
            break;
        }

        n_len++;
    }

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
