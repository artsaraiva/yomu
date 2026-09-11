#include "prompt_budget.h"
#include <jni.h>
#include <string>
#include <vector>
#include <cstring>
#include <cmath>
#include <chrono>
#include <atomic>
#include <algorithm>
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
// Held OUTSIDE g_sampler on purpose: llama_grammar_apply_impl writes -INFINITY without clearing
// cur_p->sorted, and top_k leaves it true, so a grammar placed after top_k in the same chain yields
// NaN probabilities. Applied with upstream's rejection-sampling shape in sample_token.
static llama_sampler *g_grammar = nullptr;
static int g_grammar_rejections = 0;
static std::atomic<int64_t> g_abort_deadline_ms{0};

// Why an empty reply was empty, read back by nativeLastStatus. The JNI used to collapse a refused
// prompt, an aborted decode and a genuinely empty completion into "", which the batch path cannot
// tell apart: it must fall back to per-line on an overflow and only on an overflow (#149).
enum GenerationStatus {
    GENERATION_OK = 0,
    GENERATION_OVERFLOW = 1,
    GENERATION_TIMEOUT = 2
};
static std::atomic<int> g_last_status{GENERATION_OK};

static const int64_t DEFAULT_TIMEOUT_MS = 1000;

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

// Index order of the sampler parameter array handed over the JNI. Mirrors
// GenerationParams.SAMPLER_INDEX in core/src/main/java/com/yomu/core/TranslationSlot.kt — the two
// lists must be edited together. Every value lives in Kotlin; nothing here is a constant.
enum SamplerIndex {
    SAMPLER_TEMPERATURE = 0,
    SAMPLER_TOP_K,
    SAMPLER_TOP_P,
    SAMPLER_PENALTY_LAST_N,
    SAMPLER_PENALTY_REPEAT,
    SAMPLER_PENALTY_FREQ,
    SAMPLER_PENALTY_PRESENT,
    SAMPLER_PARAM_COUNT
};

static bool rebuild_sampler(const float *params, uint32_t seed) {
    if (g_sampler) {
        llama_sampler_free(g_sampler);
        g_sampler = nullptr;
    }

    auto sparams = llama_sampler_chain_default_params();
    g_sampler = llama_sampler_chain_init(sparams);
    if (!g_sampler) {
        return false;
    }

    llama_sampler_chain_add(g_sampler, llama_sampler_init_penalties(
        (int32_t)params[SAMPLER_PENALTY_LAST_N],
        params[SAMPLER_PENALTY_REPEAT],
        params[SAMPLER_PENALTY_FREQ],
        params[SAMPLER_PENALTY_PRESENT]));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_top_k((int32_t)params[SAMPLER_TOP_K]));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_top_p(params[SAMPLER_TOP_P], 1));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_temp(params[SAMPLER_TEMPERATURE]));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_dist(seed));
    return true;
}

static void free_grammar() {
    if (g_grammar) {
        llama_sampler_free(g_grammar);
        g_grammar = nullptr;
    }
}

// Build the grammar sampler for this generation. An empty string means "no grammar" and succeeds.
//
// llama_sampler_init_grammar returns NULL when grammar_str fails to parse, and
// llama_sampler_chain_add dereferences unconditionally, so an unchecked return SIGSEGVs. Failing
// here refuses the generation rather than silently sampling unconstrained.
static bool rebuild_grammar(const char *grammar_str) {
    free_grammar();
    g_grammar_rejections = 0;
    if (!grammar_str || grammar_str[0] == '\0') {
        return true;
    }

    llama_sampler *grammar = llama_sampler_init_grammar(g_vocab, grammar_str, "root");
    if (!grammar) {
        LOGE("Grammar failed to parse; refusing to generate unconstrained");
        return false;
    }

    g_grammar = llama_sampler_chain_init(llama_sampler_chain_default_params());
    if (!g_grammar) {
        llama_sampler_free(grammar);
        LOGE("Failed to create grammar chain");
        return false;
    }
    llama_sampler_chain_add(g_grammar, grammar);
    return true;
}

static void set_logits(std::vector<llama_token_data> &cur, int n_vocab) {
    const float *logits = llama_get_logits_ith(g_ctx, -1);
    cur.resize((size_t)n_vocab);
    for (llama_token i = 0; i < n_vocab; i++) {
        cur[i] = { i, logits[i], 0.0f };
    }
}

// Upstream's rejection-sampling shape (common/sampling.cpp): sample from the ordinary chain first,
// test the chosen token against the grammar through a one-element array, and only on rejection
// re-sample with the grammar applied *before* the chain -- the ordering that keeps the sorted flag
// honest. Without a grammar this is the shipped call unchanged.
static llama_token sample_token(std::vector<llama_token_data> &cur, int n_vocab) {
    if (!g_grammar) {
        // llama_sampler_sample accepts into the chain internally; an extra llama_sampler_accept
        // here would double-advance grammar state.
        return llama_sampler_sample(g_sampler, g_ctx, -1);
    }

    set_logits(cur, n_vocab);
    llama_token_data_array cur_p = { cur.data(), cur.size(), -1, false };
    llama_sampler_apply(g_sampler, &cur_p);
    llama_token id = cur_p.data[cur_p.selected].id;

    llama_token_data single = { id, 1.0f, 0.0f };
    llama_token_data_array single_arr = { &single, 1, -1, false };
    llama_sampler_apply(g_grammar, &single_arr);
    if (single_arr.data[0].logit == -INFINITY) {
        g_grammar_rejections++;
        set_logits(cur, n_vocab);
        cur_p = { cur.data(), cur.size(), -1, false };
        llama_sampler_apply(g_grammar, &cur_p);
        llama_sampler_apply(g_sampler, &cur_p);
        id = cur_p.data[cur_p.selected].id;
    }

    llama_sampler_accept(g_grammar, id);
    llama_sampler_accept(g_sampler, id);
    return id;
}

// Format the prompt the way the loaded model was trained to receive it.
//
// This used to be a bare English instruction glued in front of the Japanese with no role markers
// at all. The model never saw the turn structure it expects, so it stayed in raw-completion mode:
// it continued the text instead of answering, never emitted EOS, and ran to the token cap on
// nearly every line. That produced both the repetition ("I'm sorry, I'm sorry, ...") and roughly
// four times the necessary latency, since the cost is per generated token.
//
// Preference order: the template embedded in the GGUF, so a swapped model brings its own format
// (ADR-0001 permits user-supplied models); then the explicit fallback below for models that ship
// no template or one llama.cpp cannot parse.
static std::string apply_chat_template(const char *user_prompt) {
    const char *tmpl = g_model ? llama_model_chat_template(g_model, nullptr) : nullptr;

    if (tmpl) {
        // CAT-Translate carries its instruction in the user turn and was trained with no system
        // prompt (model card). Injecting one made the 0.8b echo/refuse the instruction instead of
        // translating (#68 probe: 48% non-translation with a system prompt).
        const llama_chat_message messages[] = {
            {"user", user_prompt},
        };
        const int n_msg = (int)(sizeof(messages) / sizeof(messages[0]));

        // add_ass = true appends the assistant cue, which is what tells the model to answer now
        // rather than keep completing the user's turn.
        std::vector<char> buf(4096);
        int32_t n = llama_chat_apply_template(tmpl, messages, n_msg, true, buf.data(), (int32_t)buf.size());
        if (n > (int32_t)buf.size()) {
            buf.resize(n);
            n = llama_chat_apply_template(tmpl, messages, n_msg, true, buf.data(), (int32_t)buf.size());
        }
        if (n > 0) {
            return std::string(buf.data(), n);
        }
        LOGW("Model chat template not applicable (rc=%d); using explicit fallback", n);
    }

    // Fallback matching CAT-Translate's own template: <|user|>...</s><|assistant|>
    return std::string("<|user|>") + user_prompt + "</s>" + "<|assistant|>";
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
    jint n_gpu_layers,
    jint n_threads) {

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

    const int threads = std::max(n_threads, 1);

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = (uint32_t)n_ctx;
    // Equal to n_ctx on purpose: prompt_fits carries two ceilings (tokens <= batch and
    // tokens <= context - output - 8) and 512 made the first four times tighter than the second,
    // which is the self-refusal #136 measured. 512 was Yomu's line, not upstream's default.
    ctx_params.n_batch = (uint32_t)n_ctx;
    ctx_params.n_threads = threads;
    ctx_params.n_threads_batch = threads;
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

    LOGI("Model loaded: n_ctx=%d n_threads=%d n_gpu_layers=%d", n_ctx, threads, n_gpu_layers);
    LOGI("Model loaded successfully");
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_yomu_ml_LlamaBridge_nativeGenerate(
    JNIEnv *env,
    jobject /* this */,
    jstring prompt,
    jint max_tokens,
    jint timeout_ms,
    jfloatArray sampler_params,
    jint seed,
    jstring grammar) {

    const int64_t started_ms = now_ms();
    g_last_status.store(GENERATION_OK, std::memory_order_relaxed);

    if (!g_ctx || !g_model || !g_vocab) {
        LOGE("Model not loaded");
        return env->NewStringUTF("");
    }

    auto *memory = llama_get_memory(g_ctx);
    if (memory) {
        llama_memory_clear(memory, false);
    }

    const int64_t effective_timeout = timeout_ms > 0 ? (int64_t)timeout_ms : DEFAULT_TIMEOUT_MS;
    g_abort_deadline_ms.store(now_ms() + effective_timeout, std::memory_order_relaxed);
    llama_set_abort_callback(g_ctx, abort_if_timed_out, nullptr);

    if (env->GetArrayLength(sampler_params) < SAMPLER_PARAM_COUNT) {
        LOGE("Sampler parameter array too short: %d", (int)env->GetArrayLength(sampler_params));
        g_abort_deadline_ms.store(0, std::memory_order_relaxed);
        return env->NewStringUTF("");
    }

    jfloat *params = env->GetFloatArrayElements(sampler_params, nullptr);
    const bool sampler_ok = params && rebuild_sampler(params, (uint32_t)seed);
    if (params) {
        env->ReleaseFloatArrayElements(sampler_params, params, JNI_ABORT);
    }
    if (!sampler_ok) {
        LOGE("Failed to rebuild sampler");
        g_abort_deadline_ms.store(0, std::memory_order_relaxed);
        return env->NewStringUTF("");
    }

    bool grammar_ok;
    if (grammar) {
        const char *grammar_str = env->GetStringUTFChars(grammar, nullptr);
        grammar_ok = rebuild_grammar(grammar_str);
        env->ReleaseStringUTFChars(grammar, grammar_str);
    } else {
        free_grammar();
        grammar_ok = true;
    }
    if (!grammar_ok) {
        g_abort_deadline_ms.store(0, std::memory_order_relaxed);
        return env->NewStringUTF("");
    }

    const char *prompt_str = env->GetStringUTFChars(prompt, nullptr);
    std::string formatted = apply_chat_template(prompt_str);
    env->ReleaseStringUTFChars(prompt, prompt_str);

    int prompt_len = (int)formatted.size();

    // parse_special must be true: the formatted prompt carries role markers like <|user|> and
    // </s>, and with it false they tokenize as literal punctuation instead of the control tokens
    // the model was trained on, which defeats the templating entirely.
    int n_tokens = llama_tokenize(g_vocab, formatted.c_str(), prompt_len, nullptr, 0, true, true);
    if (n_tokens < 0) {
        n_tokens = -n_tokens;
    }
    if (n_tokens == 0) {
        LOGE("Failed to tokenize prompt");
        g_abort_deadline_ms.store(0, std::memory_order_relaxed);
        free_grammar();
        return env->NewStringUTF("");
    }

    std::vector<llama_token> tokens(n_tokens);
    int written = llama_tokenize(g_vocab, formatted.c_str(), prompt_len, tokens.data(), n_tokens, true, true);
    if (written < 0) {
        LOGE("Failed to write prompt tokens");
        g_abort_deadline_ms.store(0, std::memory_order_relaxed);
        free_grammar();
        return env->NewStringUTF("");
    }
    tokens.resize(written);

    if (!prompt_fits((int)tokens.size(), (int)llama_n_batch(g_ctx), (int)llama_n_ctx(g_ctx), max_tokens)) {
        LOGE("Prompt exceeds decode budget: %d tokens; refusing to truncate instructions", (int)tokens.size());
        g_last_status.store(GENERATION_OVERFLOW, std::memory_order_relaxed);
        g_abort_deadline_ms.store(0, std::memory_order_relaxed);
        free_grammar();
        return env->NewStringUTF("");
    }

    // Process prompt
    llama_batch batch = llama_batch_get_one(tokens.data(), (int)tokens.size());
    if (llama_decode(g_ctx, batch) != 0) {
        const int64_t elapsed_ms = now_ms() - started_ms;
        if (is_past_deadline()) {
            LOGW("Decode prompt aborted deadline elapsedMs=%lld", (long long)elapsed_ms);
            g_last_status.store(GENERATION_TIMEOUT, std::memory_order_relaxed);
        } else {
            LOGE("Failed to decode prompt elapsedMs=%lld", (long long)elapsed_ms);
        }
        g_abort_deadline_ms.store(0, std::memory_order_relaxed);
        free_grammar();
        return env->NewStringUTF("");
    }

    // Generation loop
    std::string result;
    int n_len = 0;
    llama_token new_token_id;
    llama_token eos = llama_vocab_eos(g_vocab);
    const int n_vocab = llama_vocab_n_tokens(g_vocab);
    std::vector<llama_token_data> cur;

    while (n_len < max_tokens) {
        new_token_id = sample_token(cur, n_vocab);

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
            g_last_status.store(GENERATION_TIMEOUT, std::memory_order_relaxed);
            } else {
                LOGE("Failed to decode generated token at=%d elapsedMs=%lld", n_len, (long long)elapsed_ms);
            }
            break;
        }

        n_len++;
    }

    const int64_t elapsed_ms = now_ms() - started_ms;
    LOGI("Generation complete tokens=%d resultLength=%zu durationMs=%lld grammarRejections=%d grammar=%d",
         n_len, result.size(), (long long)elapsed_ms, g_grammar_rejections, g_grammar ? 1 : 0);
    free_grammar();
    g_abort_deadline_ms.store(0, std::memory_order_relaxed);
    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT jint JNICALL
Java_com_yomu_ml_LlamaBridge_nativeLastStatus(JNIEnv *, jobject /* this */) {
    return (jint)g_last_status.load(std::memory_order_relaxed);
}

extern "C" JNIEXPORT void JNICALL
Java_com_yomu_ml_LlamaBridge_nativeClearMemory(JNIEnv *, jobject /* this */) {
    auto *memory = llama_get_memory(g_ctx);
    if (memory) {
        llama_memory_clear(memory, false);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_yomu_ml_LlamaBridge_nativeRelease(JNIEnv *env, jobject /* this */) {
    LOGI("Releasing model resources");

    free_grammar();

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
