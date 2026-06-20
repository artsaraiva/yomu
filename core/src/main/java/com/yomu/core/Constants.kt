package com.yomu.core

object Constants {
    const val APP_NAME = "Yomu"
    const val PACKAGE_NAME = "com.yomu.app"
    
    const val MODELS_DIR = "models"
    const val VISION_MODELS_DIR = "vision"
    const val LLM_MODELS_DIR = "llm"
    
    const val BUBBLE_DETECTION_MODEL = "bubble_detection.onnx"
    const val OCR_ENCODER_MODEL = "manga_ocr_encoder.onnx"
    const val OCR_DECODER_MODEL = "manga_ocr_decoder.onnx"
    const val OCR_VOCAB_FILE = "vocab.txt"
    const val TRANSLATION_MODEL_4BIT = "qwen3_1.7b_4bit.gguf"
    const val TRANSLATION_MODEL_8BIT = "qwen3_1.7b_8bit.gguf"
    
    const val DATABASE_NAME = "yomu_db"
    const val DATABASE_VERSION = 1
    
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
    
    const val DEFAULT_TARGET_LANGUAGE = "en"
    const val DEFAULT_SOURCE_LANGUAGE = "ja"
    const val DEFAULT_AUTO_DETECT = true
}
