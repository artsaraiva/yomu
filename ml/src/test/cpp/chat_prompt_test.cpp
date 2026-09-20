#include <cassert>
#include <fstream>
#include <sstream>
#include <string>
#include <vector>
#include "../../main/cpp/chat_prompt.h"
#include "llama.h"

// Built by ml/src/test/cpp/CMakeLists.txt, which points TEMPLATE_DIR at the fixtures: each is the
// chat_template a pinned catalog GGUF carries (read from the HF GGUF metadata, 2026-09-18).

static std::string read_template(const char *name) {
    std::ifstream in(std::string(TEMPLATE_DIR) + "/" + name);
    assert(in);
    std::stringstream s;
    s << in.rdbuf();
    return s.str();
}

// What llama_jni.cpp produced before #281: llama.cpp's heuristic template guesser, falling back to CAT.
static std::string guesser_prompt(const std::string &tmpl, const std::string &user) {
    const llama_chat_message messages[] = {{"user", user.c_str()}};
    std::vector<char> buf(4096);
    int32_t n = llama_chat_apply_template(tmpl.c_str(), messages, 1, true, buf.data(), (int32_t)buf.size());
    if (n > (int32_t)buf.size()) {
        buf.resize(n);
        n = llama_chat_apply_template(tmpl.c_str(), messages, 1, true, buf.data(), (int32_t)buf.size());
    }
    if (n > 0) return std::string(buf.data(), n);
    return cat_prompt(user);
}

static std::string jinja_prompt(const std::string &tmpl, const char *bos, const char *eos, const std::string &user) {
    auto templates = common_chat_templates_init(nullptr, tmpl, bos, eos);
    return format_chat_prompt(templates.get(), user);
}

int main() {
    const std::string user = "Translate the following Japanese into English.\n\n1: こんにちは\n2: 「行くぞ！」";

    // Neither shipped model is byte-identical to the guesser, and in both the guesser was the one off
    // the model's own template. Qwen2.5's template adds its default system turn when none is sent,
    // which the guesser's hardcoded ChatML skipped; otherwise identical.
    const std::string qwen25 = read_template("qwen2.5-1.5b-instruct.jinja");
    const std::string qwen_system = "<|im_start|>system\nYou are Qwen, created by Alibaba Cloud. You are a helpful assistant.<|im_end|>\n";
    assert(jinja_prompt(qwen25, "<|endoftext|>", "<|im_end|>", user) == qwen_system + guesser_prompt(qwen25, user));

    // CAT-Translate 0.8B and 1.4B ship the same template. The guesser took it for ChatGLM4 and sent
    // "<|user|>\n...<|assistant|>", dropping the </s> that closes CAT's user turn.
    const std::string cat = read_template("cat-translate.jinja");
    assert(guesser_prompt(cat, user) == "<|user|>\n" + user + "<|assistant|>");
    assert(jinja_prompt(cat, "<s>", "</s>", user) == cat_prompt(user));
    assert(cat_prompt(user) == "<|user|>" + user + "</s><|assistant|>");

    // Qwen3.5 thinks unless told not to; its template closes an empty think block when enable_thinking is false.
    // The 0.8B and the shipped 2B (unsloth pin f6d5376) carry the same template, byte for byte.
    const std::string qwen35 = jinja_prompt(read_template("qwen3.5.jinja"), "", "<|im_end|>", user);
    const std::string thinking_off = "<|im_start|>assistant\n<think>\n\n</think>\n\n";
    assert(qwen35.size() > thinking_off.size());
    assert(qwen35.compare(qwen35.size() - thinking_off.size(), thinking_off.size(), thinking_off) == 0);
    assert(qwen35.find("<|im_start|>system") == std::string::npos);

    // No template, or one that fails at render time, keeps the CAT format.
    assert(format_chat_prompt(nullptr, user) == cat_prompt(user));
    assert(jinja_prompt("{{ raise_exception('unsupported') }}", "", "", user) == cat_prompt(user));
}
