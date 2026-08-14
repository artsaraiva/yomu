# Yomu

On-device manga translation for Android. A page is captured on manual trigger, its speech bubbles are detected and read, and the text is translated locally without leaving the device.

## Language

### Models

**Curated model**:
A model Yomu selects, hosts a download URL for, and supports. The only kind of model allowed in the detection and OCR slots.
_Avoid_: bundled model, official model, default model

**Custom model**:
A GGUF the user supplies from their own storage for the translation slot. Permitted but explicitly unsupported, and labelled as such wherever its output appears.
_Avoid_: sideloaded model, user model, BYO model, third-party model

### Translation context

**Panel**:
A group of bubbles inferred to belong to the same comic frame. Used to order bubbles and to mark grouping inside the page prompt — never to split a page into multiple model calls.
_Avoid_: conversation block, chunk, frame

**Session context**:
The previous page's source/translation pairs, carried into the next page's prompt so pronouns, names, and register stay consistent across a reading session. Held by the caller, cleared when the session ends.
_Avoid_: history, memory, conversation history
