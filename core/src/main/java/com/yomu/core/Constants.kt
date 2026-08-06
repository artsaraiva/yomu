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
    const val QWEN_TRANSLATION_MODEL_ID = "qwen3_1.7b_4bit_v1"
    
    const val BUBBLE_DETECTION_MODEL = "bubble_detection.onnx"
    const val OCR_ENCODER_MODEL = "manga_ocr_encoder.onnx"
    const val OCR_DECODER_MODEL = "manga_ocr_decoder.onnx"
    const val OCR_VOCAB_FILE = "vocab.txt"
    const val TRANSLATION_MODEL_4BIT = "qwen3_1.7b_4bit.gguf"
    const val TRANSLATION_MODEL_8BIT = "qwen3_1.7b_8bit.gguf"
    
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
