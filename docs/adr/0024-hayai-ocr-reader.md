# Yomu reads text with Hayai OCR v2.7, capped at 192 patches

**Status:** accepted, 2026-11-25. Decides [#510](https://github.com/artsaraiva/yomu/issues/510) on the evidence of spike [#470](https://github.com/artsaraiva/yomu/issues/470).

The OCR slot downloads Hayai v2.7 Supernova's smallest ONNX set from Yomu's own export, [`artsaraiva/hayai-ocr-v2.7-onnx`](https://huggingface.co/artsaraiva/hayai-ocr-v2.7-onnx/tree/faa0433c8d5150f1745ea1e3b711ecc674498fdc) at revision `faa0433` ([#519](https://github.com/artsaraiva/yomu/issues/519)): `hayai_prefill_int4.onnx` (vision tower and prompt), `hayai_decode_int8.onnx` (one token per step with a key/value cache), `tokenizer.json` and `pos_embedding.npy`, 199 MB together. It replaces `manga-ocr-base-2025` (140 MB).

## What was looked at

The #470 results comment and the two #510 comments have the full tables. The spike's desktop replica is on tag `spike-470`.

- **Accuracy.** On JMangaBench_Read v1.3.3 the old reader scores 88.82, Hayai 98.78 and Hayai capped at 192 patches 98.82. The gap is text that is not printed type: 45.9% of those crops read exactly before, 91.1% now.
- **Speed.** Hayai's own tier choice goes up to 384 patches for big crops, which made the two wide fixture spreads take 2.1–2.2× the old reader's OCR time on the emulator. With the tier capped at 192, no fixture page passes 1.47× and the five together take 1.33×. These are emulator ratios. On one desktop thread the capped ratios reach 2.26×, so a phone that gives OCR one core feels it more.
- **Size.** 227 MB against 140 MB for the author's files; Yomu's own export is 199 MB, since its script also quantises the output head.

## How it runs

- **The crop keeps its shape.** It is resized, PIL bilinear, to a grid of 16 px patches of at most 192, so a wide line or a tall column is not squeezed square. The 64-patch floor scales tiny crops up.
- **The position table is resampled to each grid** with the same triangle filter, in host code, as the published host loop does.
- **Prefill, then greedy decode** until `<eos>` or `<pad>`, at most 128 tokens. Byte-level BPE turns the tokens back into UTF-8.
- **The 0.85 gate stays.** Hayai's mean confidence sits near 0.99 for right and wrong reads alike, but at the 192 cap the gate still drops 52 of 7,789 benchmark reads, 42 of them wrong. 0.90 drops 46 more wrong reads and 31 more right ones. The lowest token probability separates no better: a cut at 0.3 drops 43 wrong and 7 right.
- **Art reads as `...`,** which has no kana or kanji, so `readsAsJapanese` already keeps it from the prompt.
- **The piece reader is gone.** It cut wide crops into columns or line pieces because manga-ocr squeezed every crop to 224×224. Hayai reads the wide fixture crops whole, including the one-line title that manga-ocr garbled at 0.49.
- **Runs of dots become `...` and runs of ー one ー.** Nothing downstream counts them.

## Licence

The weights repo, [`hayai-ocr-v2.7-supernova`](https://huggingface.co/JustANormalTinkerer/hayai-ocr-v2.7-supernova), is Apache-2.0, and its author publishes the ONNX export script. The author's ONNX repo, [`hayai-ocr-v2.7-supernova-onnx`](https://huggingface.co/JustANormalTinkerer/hayai-ocr-v2.7-supernova-onnx/tree/bc1e39f962b6c500a4855bd8eb4e02f11483bedb), had no licence tag at `bc1e39f` on 2026-11-25.

So Yomu downloads its own export ([#519](https://github.com/artsaraiva/yomu/issues/519)), as it did for manga-ocr at `artsaraiva/manga-ocr-2025-onnx-past`. [`artsaraiva/hayai-ocr-v2.7-onnx`](https://huggingface.co/artsaraiva/hayai-ocr-v2.7-onnx/tree/faa0433c8d5150f1745ea1e3b711ecc674498fdc) holds the four files, the Apache-2.0 licence and a model card. The card names the weights revision (`5289b2c`, the checkpoint the author's ONNX files came from; later revisions carry a newer fine-tune), the export script's revision, the command and the library versions, and says the files were converted and quantised. The files are Apache-2.0 like the weights, so the app labels the model "Apache-2.0", and new installs keep working if the upstream repos move.

The export is not byte-identical to the author's: `tokenizer.json` and `pos_embedding.npy` are, the two graphs are not. At the 192-patch cap it scores 98.80 on JMangaBench_Read against the author's 98.82, with 25 of 7,789 reads differing. JMangaBench_Read is CC BY-NC-SA 4.0 and was used for evaluation only. None of it is in the repository.

## Considered Options

- **Keep manga-ocr.** Rejected. It reads half of the non-printed text wrong, and most of its wrong reads score over the gate.
- **Hayai uncapped, or capped at 256.** Rejected. Uncapped fails #510's gate on the wide spreads. 256 passes at up to 1.81× but is slower than 192 and scores no better.
- **Re-read only the crops under the gate with Hayai.** Rejected in #470. It fixed 332 benchmark crops where replacing fixed 1,387, and it ships both readers.
- **Baberu v1.2.** Rejected in #470. It takes 7× the time and trails the old reader on OpenMantra.

## Consequences

- A fresh install downloads Hayai. On upgrade, the new model id makes `refreshModelList` drop the manga-ocr row with its encoder, decoder and vocabulary, and setup asks for the 199 MB download once. An install that already holds the author's files gets Yomu's export once, because `refreshModelList` treats the new URLs as a repin.
- [ADR-0005](0005-ocr-eval-contract.md)'s evaluation contract stays as history; under [ADR-0015](0015-quality-belongs-to-the-model.md) it was not a bar this choice had to clear.
