#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>

#define LOG_TAG "LlamaJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

struct llama_model;
struct llama_context;

enum llama_model_quantization {
    LLAMA_FTYPE_MOSTLY_Q4_0 = 2,
};

using llama_model_load_fn = llama_model* (*)(const char* path, int n_gpu_layers);
using llama_create_context_fn = llama_context* (*)(llama_model* model, int n_ctx);
using llama_eval_fn = bool (*)(llama_context* ctx, const int* tokens, int n_tokens, int n_past);
using llama_tokenize_fn = std::vector<int> (*)(llama_context* ctx, const std::string& text, bool bos);
using llama_detokenize_fn = std::string (*)(llama_context* ctx, int token);
using llama_free_model_fn = void (*)(llama_model* model);
using llama_free_context_fn = void (*)(llama_context* ctx);
using llama_n_vocab_fn = int (*)(llama_context* ctx);
using llama_token_to_str_fn = const char* (*)(llama_context* ctx, int token);
using llama_token_eos_fn = int (*)(llama_context* ctx);

static JavaVM* g_jvm = nullptr;
static llama_model* g_model = nullptr;
static llama_context* g_ctx = nullptr;

extern "C" JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM* vm, void* reserved) {
    g_jvm = vm;
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT void JNICALL
Java_com_yomu_ml_LlamaBridge_nativeInit(JNIEnv* env, jobject /* this */) {
    LOGI("llama.cpp native library loaded");
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_yomu_ml_LlamaBridge_nativeLoadModel(
    JNIEnv* env,
    jobject /* this */,
    jstring model_path,
    jint n_ctx,
    jint n_gpu_layers
) {
    const char* path = env->GetStringUTFChars(model_path, nullptr);
    LOGI("Loading model from: %s", path);

    env->ReleaseStringUTFChars(model_path, path);

    if (g_model != nullptr) {
        LOGE("Model already loaded");
        return JNI_FALSE;
    }

    LOGE("llama.cpp native library not yet compiled. "
         "Run: git submodule add https://github.com/ggerganov/llama.cpp ml/llama.cpp");
    return JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_yomu_ml_LlamaBridge_nativeGenerate(
    JNIEnv* env,
    jobject /* this */,
    jstring prompt,
    jint max_tokens,
    jfloat temperature
) {
    if (g_ctx == nullptr) {
        LOGE("Model not loaded");
        return env->NewStringUTF("");
    }

    const char* prompt_str = env->GetStringUTFChars(prompt, nullptr);
    std::string result = "[llama.cpp inference pending - native library not built]";

    env->ReleaseStringUTFChars(prompt, prompt_str);
    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_yomu_ml_LlamaBridge_nativeRelease(JNIEnv* env, jobject /* this */) {
    LOGI("Releasing model resources");
    g_model = nullptr;
    g_ctx = nullptr;
}
