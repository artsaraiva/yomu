"""Export manga-ocr-base-2025 to ONNX with decoder past key values (#339).

Optimum refuses image-to-text-with-past for a BERT decoder: its BERT config is encoder-only.
BertLMHeadModel does support a KV cache, so register a decoder config for bert first.
"""
import functools
import sys

import optimum.exporters.onnx.model_configs  # noqa: F401  populates the registry
from optimum.exporters.onnx import main_export
from optimum.exporters.onnx.config import TextSeq2SeqOnnxConfig
from optimum.exporters.tasks import TasksManager
from optimum.utils import NormalizedSeq2SeqConfig

MODEL = "jzhang533/manga-ocr-base-2025"
REVISION = "1e64d5beb41ee6bee72c200838ddb93ad541482a"


# Same shape as Optimum's TrOCROnnxConfig: a causal decoder with cross-attention over the image.
class BertDecoderOnnxConfig(TextSeq2SeqOnnxConfig):
    NORMALIZED_CONFIG_CLASS = NormalizedSeq2SeqConfig.with_args(
        decoder_num_layers="num_hidden_layers",
        num_layers="num_hidden_layers",
        decoder_num_attention_heads="num_attention_heads",
        hidden_size="hidden_size",
    )


TasksManager._SUPPORTED_MODEL_TYPE["bert"]["onnx"]["feature-extraction"] = functools.partial(
    BertDecoderOnnxConfig, task="feature-extraction"
)

main_export(MODEL, output=sys.argv[1], task="image-to-text-with-past", revision=REVISION)
