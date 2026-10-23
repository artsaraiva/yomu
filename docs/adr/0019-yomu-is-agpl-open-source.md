# Yomu is open source under AGPL-3.0, with no CLA

**Status:** accepted, 2026-10-23. Decides [#403](https://github.com/artsaraiva/yomu/issues/403). Supersedes the closed-source assumption that [ADR-0007](0007-bubble-text-detection-approach.md) attributes to [ADR-0001](0001-custom-model-permissiveness.md), and resolves ADR-0007's AGPL-clearance precondition.

Yomu's code is published under the GNU Affero General Public License v3.0 (`LICENSE` at the repo root). Contributions are accepted under the same licence, with no contributor licence agreement. Each model keeps its own licence, and [ADR-0017](0017-experimental-curated-tier.md)'s Apache-2.0-or-MIT rule for curated deliverables is unchanged.

## Why

Yomu is already handed out as an APK, but no licence was ever published, so legally all rights were reserved. The docs assumed closed source: ADR-0007 says "Per ADR-0001, Yomu ships closed-source", although ADR-0001 itself only says the monetised surface was cloud credits, and [ADR-0018](0018-translation-stays-on-the-phone.md) has since parked those.

Closed source conflicts with the detector in production. `Kiuyha/Manga-Bubble-YOLO` is a fine-tune of Ultralytics YOLO26, which is AGPL-3.0, and ADR-0007 left "AGPL clearance" as an open shipping precondition with two exits: buy an Ultralytics Enterprise licence, or train a replacement detector. Licensing Yomu itself under AGPL-3.0 is a third exit that costs nothing and changes no model.

Open source also backs the README's "Nothing leaves your phone" promise: readers can check it in the code instead of taking it on trust. It also lets Yomu be distributed through channels that require source, such as F-Droid and IzzyOnDroid.

## Considered Options

- **Stay closed and clear AGPL by purchase or retraining.** Rejected. An Enterprise licence is a recurring cost with no revenue to set against it (ADR-0018: no paid surface). A home-trained detector is a training project that nobody has started.
- **GPL-3.0.** Rejected, narrowly. GPL-3.0 §13 allows combining with AGPL-3.0 code, but the combination still carries AGPL terms for that part. Using AGPL-3.0 for the whole app leaves a single licence to explain.
- **A permissive licence (MIT or Apache-2.0).** Rejected. It does not answer the AGPL question, and it lets anyone ship a closed fork of Yomu.
- **AGPL-3.0. (Chosen.)**

## Contributions: no CLA

Contributions come in under AGPL-3.0, the same licence they go out under. Yomu asks for no CLA and no copyright assignment.

The cost is stated here so it is not rediscovered later: once outside code is merged, Yomu cannot be relicensed under other terms, including proprietary ones, without the consent of every contributor whose code remains. This does not block a paid build. AGPL allows selling a build, such as a paid Play Store listing, as long as the corresponding source is offered. It does block a closed build.

## Consequences

**ADR-0007's AGPL paragraph is resolved.** The detector stays, and so does the incumbent decision. Training a Yomu-owned detector stays a possible future project, but licensing no longer motivates it.

**Ads are out.** The issue weighed ad-supported Play distribution and rejected it. An ad SDK means network traffic and tracking, which breaks ADR-0018, and Play policy forbids ads shown outside the app, which rules out the overlay where readers actually spend their time. Donations and a paid build of the same code remain open. ADR-0018's reopening condition (a Play Store launch with paid features) is untouched.

**Dependencies are compatible.** llama.cpp and ONNX Runtime are MIT. The other runtime libraries (AndroidX, Hilt, Room, OkHttp, Retrofit, Gson, Coil, kotlinx-coroutines) are Apache-2.0. Both licences can be combined into an AGPL-3.0 work. Test-only dependencies are not part of the app that ships.

**Models are separate works.** Yomu downloads models from their own Hugging Face repos at pinned revisions, so their licences govern them, not this one. The README's Models table stays the source of truth for each model's licence.
