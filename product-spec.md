# Yomu

**Read Any Manga. Offline. Instantly.**

---

# Overview

Yomu is a local-first AI-powered manga translation application that enables users to read manga in their native language directly on their mobile device.

Unlike traditional OCR-based translators, Yomu is designed specifically for manga. It understands panel structure, conversation flow, character interactions, and speech bubble context before generating translations.

Every stage runs on the reader's phone; the network is used only to download models ([ADR-0018](docs/adr/0018-translation-stays-on-the-phone.md)). Cloud translation and paid tiers are parked, not planned. The Cloud Boost, cloud models and pricing sections that used to be here now live, verbatim, in [#324](https://github.com/artsaraiva/yomu/issues/324).

---

# Vision

Enable anyone to read manga from any language with natural, context-aware translations while preserving the original reading experience.

---

# Problem

Current manga translation tools suffer from several limitations:

* Literal translations that lose meaning.
* No understanding of conversation context.
* Poor speech bubble rendering.
* Heavy reliance on cloud APIs.
* Privacy concerns.
* Poor mobile experience.
* No manga-specific optimization.

Most solutions perform:

```text
OCR
↓
Translate
↓
Display text
```

This frequently results in awkward and unnatural translations.

---

# Solution

Yomu introduces a manga-aware translation pipeline:

```text
Manga Page
↓
Speech Bubble Detection
↓
OCR
↓
Panel Detection
↓
Reading Order Reconstruction
↓
Conversation Context Assembly
↓
Translation
↓
Artwork Preservation
↓
Bubble Typesetting
↓
Translated Manga Page
```

This enables significantly higher-quality translations while preserving the visual appearance of the manga.

---

# Advanced Capabilities

## 1. Context-Aware Translation

Instead of translating each speech bubble independently, Yomu reconstructs conversations.

### Traditional Workflow

```text
Bubble
↓
Translation
```

### Yomu Workflow

```text
Panel
↓
Conversation
↓
Translation
```

Benefits:

* Better pronoun resolution.
* Better emotional tone.
* Improved slang translation.
* Improved cultural adaptation.
* More natural dialogue.

---

## 2. Local-First AI

All core functionality runs directly on the user's device.

Benefits:

* Privacy.
* Offline usage.
* No recurring API costs.
* Instant response times.

---

## 3. Manga-Aware Typesetting

Yomu automatically re-renders translated text inside speech bubbles.

Features:

* Dynamic font sizing.
* Bubble-aware layout.
* Vertical text support.
* Emphasis preservation.
* Multi-line balancing.
* Natural manga appearance.

---

## 4. One-Tap Translation

User flow:

```text
Open Manga Page
↓
Tap Translate
↓
Read
```

No prompts.

No settings.

No manual editing.

---

# Target Audience

## Primary Users

* Manga readers.
* Language learners.
* Anime fans.
* Scanlation communities.
* International readers.

## Secondary Users

* Comic readers.
* Manhwa readers.
* Visual novel readers.
* Translators.

---

# Technical Architecture

## High-Level Pipeline

```text
Input Image
↓
Bubble Detection
↓
OCR Extraction
↓
Panel Detection
↓
Reading Order Reconstruction
↓
Context Engine
↓
Translation Engine
↓
Text Removal
↓
Typesetting Engine
↓
Output Image
```

---

# Component Design

## Bubble Detection

### Purpose

Identify speech bubbles and text regions.

### Candidate Models

* YOLOv11 Nano
* MobileSAM
* Custom manga detector

### Output

```json
{
  "bubble_id": 1,
  "bounding_box": [],
  "text_region": []
}
```

---

## OCR Engine

### Purpose

Extract Japanese text.

### Recommended Model

MangaOCR

Advantages:

* Manga-specialized.
* Open source.
* High recognition accuracy.
* Handles stylized manga fonts.

### Mobile Deployment

* ONNX
* TensorFlow Lite
* Qualcomm NPU acceleration

---

## Reading Order Engine

### Purpose

Determine panel order.

Tasks:

* Panel segmentation.
* Reading direction analysis.
* Bubble grouping.
* Conversation reconstruction.

Output:

```json
{
  "panel_order": [],
  "conversation_groups": []
}
```

---

## Context Engine

### Purpose

Create translation context before invoking the language model.

Example:

Instead of:

```text
Bubble A
Bubble B
Bubble C
```

Create:

```text
Conversation Block
```

Benefits:

* Better coherence.
* Better speaker consistency.
* Better narrative understanding.

---

# Translation Engine

## Requirements

* Small.
* Fast.
* Multilingual.
* Mobile-friendly.
* Fine-tunable.

---

## V1 Model

### CAT-Translate 0.8B

Recommended deployment:

```text
4-bit Quantized
GGUF
```

Advantages:

* Purpose-built for Japanese↔English translation.
* MIT-licensed.
* Smaller download than general multilingual models.

Estimated Size:

~500 MB

---

## V2 Model

### Fine-Tuned Manga Translator

Base:

```text
CAT-Translate 0.8B
```

Training Data:

```text
Japanese Manga
↓
Official English Release
```

Objective:

Create a manga-specialized translation model.

Benefits:

* Better idiom handling.
* Better cultural adaptation.
* Better character voice preservation.
* Better manga-specific language.

---

# Text Removal Engine

### Purpose

Remove original Japanese text.

Methods:

* Diffusion inpainting.
* Lightweight mobile inpainting.
* Region-based artwork reconstruction.

Goal:

Preserve original artwork.

---

# Typesetting Engine

### Responsibilities

* Font sizing.
* Text wrapping.
* Bubble fitting.
* Vertical layouts.
* Emphasis rendering.

### Output

Natural-looking translated pages.

---

# Mobile Architecture

## Android

Primary platform.

Technologies:

* Kotlin
* Jetpack Compose
* ONNX Runtime Mobile
* TensorFlow Lite

---

## iOS

Future release.

Technologies:

* SwiftUI
* CoreML

---

# Roadmap

## Phase 1

MVP

Features:

* Japanese → English.
* Single-page translation.
* Android app.
* Local-only mode.

---

## Phase 2

Multi-language support.

Languages:

* English
* Portuguese
* Spanish
* French
* German

---

## Phase 3

Chapter Translation

Features:

* PDF support.
* CBZ support.
* Batch translation.
* Library management.

---

## Phase 4

Live Translation Mode

Features:

* Camera translation.
* Real-time overlays.
* Instant manga reading.

---

## Phase 5

Community Features

Features:

* Shared translations.
* Translation corrections.
* Community dictionaries.
* Character-specific translation memory.

---

# Competitive Advantage

Most competitors optimize for translation.

Yomu optimizes for reading.

Competitors:

```text
OCR
↓
Translation
```

Yomu:

```text
Understand
↓
Translate
↓
Read Naturally
```

This distinction creates a defensible product position and enables a significantly better user experience.

---

# Success Metrics

## Product

* Translation latency < 3 seconds/page.
* OCR accuracy > 95%.
* User correction rate < 10%.
* Offline completion rate > 80%.

## Business

* Monthly active readers.
* Pages translated per day.
* Subscription conversion rate.

---

# Tagline

**Yomu — Read Any Manga. Offline. Instantly.**
