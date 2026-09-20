#include "chat_prompt.h"

#include <exception>

std::string cat_prompt(const std::string &user_prompt) {
    return "<|user|>" + user_prompt + "</s><|assistant|>";
}

std::string format_chat_prompt(const common_chat_templates *templates, const std::string &user_prompt) {
    if (!templates) return cat_prompt(user_prompt);

    // CAT-Translate carries its instruction in the user turn and was trained with no system
    // prompt (model card). Injecting one made the 0.8b echo/refuse the instruction instead of
    // translating (#68).
    common_chat_msg user;
    user.role = "user";
    user.content = user_prompt;

    common_chat_templates_inputs inputs;
    inputs.messages = {user};
    // The assistant cue is what tells the model to answer now rather than keep completing the user's turn.
    inputs.add_generation_prompt = true;
    // Explicit: llama.cpp defaults it to true, and a thinking model spends the token budget reasoning.
    inputs.enable_thinking = false;
    // Render the template as-is and skip building an output parser: Yomu decodes and parses its own
    // replies, under its own grammar (ADR-0013).
    inputs.force_pure_content = true;

    try {
        return common_chat_templates_apply(templates, inputs).prompt;
    } catch (const std::exception &) {
        return cat_prompt(user_prompt);
    }
}
