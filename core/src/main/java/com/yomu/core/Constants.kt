package com.yomu.core

object Constants {
    const val APP_NAME = "Yomu"
    const val PACKAGE_NAME = "com.yomu.app"
    
    const val MODELS_DIR = "models"
    const val VISION_MODELS_DIR = "vision"
    const val LLM_MODELS_DIR = "llm"
    const val TRANSLATION_MODELS_DIR = "translation"
    const val ML_KIT_JA_EN_MODEL_ID = "mlkit_ja_en_v1"
    const val BUBBLE_DETECTION_MODEL_ID = "bubble_detection_v1"
    const val MANGA_OCR_MODEL_ID = "manga_ocr_v1"
    const val CAT_TRANSLATION_MODEL_ID = "cat_translate_0.8b_v1"
    const val OPUS_MT_MODEL_ID = "opus_mt_ja_en_v1"

    // #84 bake-off: larger context-capable id-keyed-batch challengers. Not the shipped default
    // (ADR-0008 keeps the 0.8b); these are measured by EngineBenchmarkTest and reach enthusiasts
    // via the ADR-0001 custom-model slot until proven adequate. #72 promotes the winner.
    const val CAT_TRANSLATION_14B_MODEL_ID = "cat_translate_1.4b_v1"
    const val CAT_TRANSLATION_14B_I1_MODEL_ID = "cat_translate_1.4b_i1_v1"
    const val CAT_TRANSLATION_7B_MODEL_ID = "cat_translate_7b_v1"
    const val QWEN3_MODEL_ID = "qwen3_4b_v1"
    const val HUNYUAN_MT_MODEL_ID = "hunyuan_mt_7b_v1"
    
    const val BUBBLE_DETECTION_MODEL = "bubble_detection.onnx"
    const val OCR_ENCODER_MODEL = "manga_ocr_encoder.onnx"
    const val OCR_DECODER_MODEL = "manga_ocr_decoder.onnx"
    const val OCR_VOCAB_FILE = "vocab.txt"
    const val TRANSLATION_MODEL_4BIT = "cat_translate_0.8b_q4_k_m.gguf"
    const val TRANSLATION_MODEL_8BIT = "cat_translate_0.8b_q8_0.gguf"

    // #84 challenger GGUF file names, staged under models/llm/ alongside the 0.8b (Q4_K_M tier).
    const val CAT_TRANSLATION_14B_MODEL = "cat_translate_1.4b_q4_k_m.gguf"
    // imatrix quant of the 1.4b winner: same size/RAM/latency, typically better quality per bit.
    const val CAT_TRANSLATION_14B_I1_MODEL = "cat_translate_1.4b_i1_q4_k_m.gguf"
    // 7B CAT sibling: too big for the 8GB reference device (like Hunyuan-7B); emulator/high-RAM only.
    const val CAT_TRANSLATION_7B_MODEL = "cat_translate_7b_q4_k_m.gguf"
    const val QWEN3_MODEL = "qwen3_4b_q4_k_m.gguf"
    // Q3_K_M, not Q4: the 7B Q4 (4.6GB) OOM-kills on load on an 8GB device (#84 bake-off). Q3_K_M
    // (3.8GB) fits; the quant drop is the price of measuring a 7B on-device at all.
    const val HUNYUAN_MT_MODEL = "hunyuan_mt_7b_q3_k_m.gguf"
    
    const val OPUS_MT_ENCODER_MODEL = "opus_mt_encoder_model_quantized.onnx"
    const val OPUS_MT_DECODER_MODEL = "opus_mt_decoder_with_past_model_quantized.onnx"
    const val OPUS_MT_TOKENIZER = "tokenizer.json"
    
    const val DATABASE_NAME = "yomu_db"
    const val DATABASE_VERSION = 2
    
    const val OVERLAY_CHANNEL_ID = "overlay_service"
    const val OVERLAY_NOTIFICATION_ID = 1
    
    const val OVERLAY_PERMISSION_REQUEST_CODE = 1001
    const val MEDIA_PROJECTION_PERMISSION_REQUEST_CODE = 1002
    
    const val PREF_TRANSLATION_MODE = "translation_mode"
    const val PREF_TARGET_LANGUAGE = "target_language"
    const val PREF_SOURCE_LANGUAGE = "source_language"
    const val PREF_AUTO_DETECT = "auto_detect"
    const val PREF_BUTTON_POSITION_X = "button_position_x"
    const val PREF_BUTTON_POSITION_Y = "button_position_y"
    const val PREF_TRANSLATION_ENGINE = "translation_engine"
    const val PREF_FONT_SIZE_SCALE = "font_size_scale"
    
    const val DEFAULT_TARGET_LANGUAGE = "en"
    const val DEFAULT_SOURCE_LANGUAGE = "ja"
    const val DEFAULT_AUTO_DETECT = true
    const val DEFAULT_FONT_SIZE_SCALE = 1.0f
}
