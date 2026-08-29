// LlamaCpp JNI bridge for VaniFlow — real on-device LLM inference (no fake responses).
// Uses llama.cpp (MIT). Builds against the llama.h C API for the pinned commit.
#include <jni.h>
#include <string>
#include <vector>
#include <cmath>
#include <cstdlib>
#include <cstring>
#include <mutex>
#include <android/log.h>
#include "llama.h"

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "LLAMAJNI", __VA_ARGS__)

static void llama_log_bridge(enum ggml_log_level level, const char * text, void * user_data) {
    (void)level; (void)user_data;
    __android_log_print(ANDROID_LOG_INFO, "LLAMACPP", "%s", text);
}

static std::mutex g_mutex;
static struct llama_model * g_model = nullptr;
static struct llama_context * g_ctx = nullptr;
static const struct llama_vocab * g_vocab = nullptr;
static int32_t g_n_vocab = 0;
static volatile bool g_abort = false;
static bool g_backend_init = false;

static bool g_abort_callback(void *) {
    return g_abort;
}

// ---- llama globals ----

static llama_token sample_token(const float * logits, int n_vocab, float temperature, int top_k, llama_token eos) {
    if (temperature <= 0.01f) {
        int best = 0;
        float bv = -1e30f;
        for (int i = 0; i < n_vocab; i++) {
            if (i == eos) continue;
            if (logits[i] > bv) { bv = logits[i]; best = i; }
        }
        return best;
    }
    int K = (top_k > 0 && top_k < n_vocab) ? top_k : n_vocab;
    std::vector<int> idx;
    idx.reserve(K);
    std::vector<float> lg;
    lg.reserve(K);
    for (int i = 0; i < n_vocab; i++) {
        if (i == eos) continue;
        float v = logits[i];
        if ((int)idx.size() < K) {
            idx.push_back(i);
            lg.push_back(v);
        } else {
            int m = 0;
            for (int j = 1; j < (int)lg.size(); j++) {
                if (lg[j] < lg[m]) m = j;
            }
            if (v > lg[m]) { idx[m] = i; lg[m] = v; }
        }
    }
    if (idx.empty()) return eos;
    float maxl = -1e30f;
    for (float v : lg) if (v > maxl) maxl = v;
    float sum = 0.0f;
    for (int j = 0; j < (int)lg.size(); j++) {
        lg[j] = expf((lg[j] - maxl) / temperature);
        sum += lg[j];
    }
    float r = (float)rand() / (float)RAND_MAX;
    float acc = 0.0f;
    for (int j = 0; j < (int)lg.size(); j++) {
        acc += lg[j] / sum;
        if (r <= acc) return idx[j];
    }
    return idx.back();
}

// Length (in bytes) of the first complete UTF-8 character in `s`, or 0 if the
// leading bytes do not yet form a complete character (e.g. a multi-byte char
// split across token pieces). Used to avoid passing invalid Modified UTF-8 to
// NewStringUTF, which would otherwise abort the process.
static size_t utf8_first_char_len(const std::string & s) {
    if (s.empty()) return 0;
    unsigned char c = (unsigned char)s[0];
    size_t need;
    if (c < 0x80) need = 1;
    else if ((c & 0xE0) == 0xC0) need = 2;
    else if ((c & 0xF0) == 0xE0) need = 3;
    else if ((c & 0xF8) == 0xF0) need = 4;
    else return 0; // invalid/unsupported lead byte — never emit, avoid crash
    if (s.size() >= need) return need;
    return 0; // incomplete trailing sequence — wait for more bytes
}

// Repetition penalty: divide logits of recently-seen tokens to discourage loops.
static void apply_repetition_penalty(float * logits, int n_vocab,
                                     const std::vector<llama_token> & hist,
                                     int window, float penalty) {
    if (penalty <= 1.0f || window <= 0) return;
    int start = ((int)hist.size() > window) ? (int)hist.size() - window : 0;
    for (int i = start; i < (int)hist.size(); i++) {
        llama_token t = hist[i];
        if (t >= 0 && t < n_vocab) logits[t] /= penalty;
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_vaniflow_app_engine_ai_llm_LlamaCppRuntime_nativeLoad(JNIEnv *env, jobject, jstring jPath) {
    const char * path = env->GetStringUTFChars(jPath, nullptr);
    if (!path) return JNI_FALSE;

    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_backend_init) {
        llama_backend_init();
        g_backend_init = true;
    }

    struct llama_model_params mparams = llama_model_default_params();
    g_model = llama_model_load_from_file(path, mparams);
    if (!g_model) {
        env->ReleaseStringUTFChars(jPath, path);
        return JNI_FALSE;
    }

    llama_log_set(llama_log_bridge, nullptr);

    struct llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = 2048;
    cparams.n_batch = 512;
    cparams.n_ubatch = 512;
    cparams.n_threads = 4;
    cparams.n_threads_batch = 4;
    cparams.abort_callback = g_abort_callback;
    cparams.abort_callback_data = nullptr;

    g_ctx = llama_init_from_model(g_model, cparams);
    if (!g_ctx) {
        llama_free_model(g_model);
        g_model = nullptr;
        env->ReleaseStringUTFChars(jPath, path);
        return JNI_FALSE;
    }
    g_vocab = llama_model_get_vocab(g_model);
    g_n_vocab = llama_vocab_n_tokens(g_vocab);
    g_abort = false;

    env->ReleaseStringUTFChars(jPath, path);
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_vaniflow_app_engine_ai_llm_LlamaCppRuntime_nativeAbort(JNIEnv *, jobject) {
    g_abort = true;
}

extern "C" JNIEXPORT void JNICALL
Java_com_vaniflow_app_engine_ai_llm_LlamaCppRuntime_nativeRelease(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_ctx) { llama_free(g_ctx); g_ctx = nullptr; }
    if (g_model) { llama_free_model(g_model); g_model = nullptr; }
    g_vocab = nullptr;
    g_n_vocab = 0;
    if (g_backend_init) { llama_backend_free(); g_backend_init = false; }
}

static void emit_token(JNIEnv *env, jobject callback, const char * piece, jmethodID mid) {
    if (!callback || !mid || !piece) return;
    jstring js = env->NewStringUTF(piece);
    if (js) {
        env->CallVoidMethod(callback, mid, js);
        env->DeleteLocalRef(js);
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
        }
    } else {
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
        }
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_vaniflow_app_engine_ai_llm_LlamaCppRuntime_nativeGenerate(
        JNIEnv *env, jobject, jobjectArray jRoles, jobjectArray jContents,
        jboolean addAssistant, jint maxTokens, jfloat temperature, jobject callback) {

    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_ctx || !g_vocab) return nullptr;

    g_abort = false;

    // Reset the KV cache: each call rebuilds the full conversation from scratch,
    // so we must clear prior turns before feeding the prompt at position 0.
    llama_memory_clear(llama_get_memory(g_ctx), true);

    jsize n = env->GetArrayLength(jRoles);
    std::vector<llama_chat_message> msgs;
    msgs.reserve(n);
    for (jsize i = 0; i < n; i++) {
        jstring jr = (jstring)env->GetObjectArrayElement(jRoles, i);
        jstring jc = (jstring)env->GetObjectArrayElement(jContents, i);
        const char * r = env->GetStringUTFChars(jr, nullptr);
        const char * c = env->GetStringUTFChars(jc, nullptr);
        msgs.push_back({r, c});
        env->ReleaseStringUTFChars(jr, r);
        env->ReleaseStringUTFChars(jc, c);
        env->DeleteLocalRef(jr);
        env->DeleteLocalRef(jc);
    }

    // Apply the model's built-in chat template (NULL => use model default, e.g. Qwen2.5).
    int buf_len = 1 << 16;
    char * buf = (char *)malloc(buf_len);
    int written = llama_chat_apply_template(nullptr, msgs.data(), (size_t)n, addAssistant, buf, buf_len);
    if (written > buf_len) {
        buf_len = written + 16;
        buf = (char *)realloc(buf, buf_len);
        written = llama_chat_apply_template(nullptr, msgs.data(), (size_t)n, addAssistant, buf, buf_len);
    }
    LOGI("apply_template written=%d n_msg=%d addAss=%d", written, (int)n, (int)addAssistant);

    // Tokenize the formatted prompt (parse special tokens like <|im_start|>).
    int n_tok_max = 1 << 14;
    std::vector<llama_token> tokens(n_tok_max);
    int n_prompt = llama_tokenize(g_vocab, buf, (int32_t)strlen(buf), tokens.data(), n_tok_max, false, true);
    if (n_prompt < 0) {
        n_tok_max = -n_prompt + 16;
        tokens.resize(n_tok_max);
        n_prompt = llama_tokenize(g_vocab, buf, (int32_t)strlen(buf), tokens.data(), n_tok_max, false, true);
    }
    LOGI("n_prompt=%d (first token=%d)", n_prompt, n_prompt > 0 ? tokens[0] : -1);
    free(buf);
    if (n_prompt <= 0) { LOGI("n_prompt<=0, aborting"); return nullptr; }

    // Resolve callback method ids for this specific callback instance (thread-safe, class-safe).
    jmethodID midOnToken = nullptr;
    jmethodID midOnComplete = nullptr;
    jmethodID midOnError = nullptr;
    if (callback) {
        jclass cbClass = env->GetObjectClass(callback);
        if (cbClass) {
            midOnToken = env->GetMethodID(cbClass, "onToken", "(Ljava/lang/String;)V");
            midOnComplete = env->GetMethodID(cbClass, "onComplete", "()V");
            midOnError = env->GetMethodID(cbClass, "onError", "(Ljava/lang/String;)V");
            env->DeleteLocalRef(cbClass);
        }
    }

    // Feed the prompt through the model in batches.
    int32_t pos = 0;
    for (int i = 0; i < n_prompt; ) {
        int n_batch = (n_prompt - i) > 512 ? 512 : (n_prompt - i);
        struct llama_batch batch = llama_batch_init(n_batch, 0, 1);
        for (int j = 0; j < n_batch; j++) {
            batch.token[j] = tokens[i + j];
            batch.pos[j] = pos++;
            batch.seq_id[j][0] = 0;
            batch.n_seq_id[j] = 1;
            batch.logits[j] = (i + j == n_prompt - 1) ? 1 : 0;
        }
        batch.n_tokens = n_batch;
        if (llama_decode(g_ctx, batch) != 0) {
            llama_batch_free(batch);
            LOGI("decode prompt failed at i=%d", i);
            if (callback && midOnError) emit_token(env, callback, "generation failed", midOnError);
            return nullptr;
        }
        llama_batch_free(batch);
        i += n_batch;
    }
    LOGI("prompt decoded, starting generation");

    // Generate tokens.
    std::string out;        // full reply, always valid complete UTF-8 (never split multibyte)
    std::string streamBuf;  // accumulates raw piece bytes; flushed only at char boundaries
    int n_gen = 0;
    char piece[256];
    const llama_token eos = llama_vocab_eos(g_vocab);
    std::vector<llama_token> genHist(tokens.begin(), tokens.begin() + n_prompt);
    while (n_gen < maxTokens) {
        if (g_abort) break;
        const float * logits = llama_get_logits(g_ctx);
        if (!logits) { LOGI("null logits at n_gen=%d", n_gen); break; }
        std::vector<float> logitsBuf(logits, logits + g_n_vocab);
        apply_repetition_penalty(logitsBuf.data(), g_n_vocab, genHist, 64, 1.15f);
        llama_token next = sample_token(logitsBuf.data(), g_n_vocab, temperature, 40, eos);
        if (llama_vocab_is_eog(g_vocab, next)) { LOGI("EOG at n_gen=%d token=%d", n_gen, next); break; }
        genHist.push_back(next);

        int n = llama_token_to_piece(g_vocab, next, piece, sizeof(piece) - 1, 0, false);
        if (n > 0) {
            piece[n] = '\0';
            streamBuf += piece;
            // Flush only complete UTF-8 characters so NewStringUTF never sees a
            // split multi-byte sequence (which aborts the process).
            size_t clen;
            while ((clen = utf8_first_char_len(streamBuf)) > 0) {
                std::string ch = streamBuf.substr(0, clen);
                out += ch;
                if (callback && midOnToken) emit_token(env, callback, ch.c_str(), midOnToken);
                streamBuf.erase(0, clen);
            }
        } else {
            LOGI("token_to_piece returned %d for token=%d", n, next);
        }

        // Feed the generated token back.
        struct llama_batch batch = llama_batch_init(1, 0, 1);
        batch.token[0] = next;
        batch.pos[0] = pos++;
        batch.seq_id[0][0] = 0;
        batch.n_seq_id[0] = 1;
        batch.logits[0] = 1;
        batch.n_tokens = 1;
        if (llama_decode(g_ctx, batch) != 0) {
            llama_batch_free(batch);
            LOGI("decode gen failed at n_gen=%d", n_gen);
            break;
        }
        llama_batch_free(batch);
        n_gen++;
    }
    // Any bytes left in `streamBuf` are an incomplete trailing multi-byte character
    // (cut by maxTokens); discard them to keep `out` valid UTF-8.
    LOGI("generation done n_gen=%d out_len=%d leftover=%d", n_gen, (int)out.size(), (int)streamBuf.size());

    if (callback && midOnComplete) {
        env->CallVoidMethod(callback, midOnComplete);
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
        }
    }
    g_abort = false;
    return env->NewStringUTF(out.c_str());
}
