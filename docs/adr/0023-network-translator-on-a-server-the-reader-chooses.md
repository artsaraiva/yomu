# A network translator can fill the translation slot, on a server the reader chooses

**Status:** accepted, 2026-11-23. Decides [#457](https://github.com/artsaraiva/yomu/issues/457). Supersedes [ADR-0018](0018-translation-stays-on-the-phone.md). Amends [ADR-0013](0013-grammar-constrained-page-level-batch.md) and [ADR-0016](0016-llm-is-the-only-translator.md).

Yomu gains an opt-in **network translator**: the reader enters the address of an OpenAI-compatible server, with an optional API key, and that server translates the page's text. It can be a machine the reader runs (Ollama, llama.cpp, LM Studio) or a hosted API the reader has a key for. The on-device translator stays the default, and nothing leaves the phone until the reader sets a server up and chooses it. Yomu runs no server of its own.

This ADR records the decision. Nothing in the app changes with it; the build is tracked in its own spec issue.

## Why

ADR-0018 kept translation on the phone as a default for now, and rejected a translator on the reader's own hardware for two reasons: page text leaves the phone, and it is a second translation path to support. Both have changed.

- **The second path has a seam.** Since [#478](https://github.com/artsaraiva/yomu/issues/478), more than one translator can fill the translation slot, and the app falls back to the on-device one when the chosen one can't run.
- **The path has been built and reviewed once.** The fork in [#446](https://github.com/artsaraiva/yomu/pull/446) shipped a working version. Its review, recorded in #457, showed what to keep and which mistakes to avoid.
- **A server reaches models a phone can't.** It can run models far larger than a phone holds, and a reader who already runs one gets them with no extra download.

The text leaving the phone is the reader's choice, made per install, to an address they typed. The public wording changes to say so (see Consequences).

## The decision

**One protocol.** The network translator sends a streamed `/v1/chat/completions` request. Ollama, llama.cpp, LM Studio and the hosted APIs all serve it. There is no Ollama-specific endpoint and no server detection.

**Only the page's text leaves the phone.** Detection and OCR stay on the phone. The request carries what the on-device prompt carries: the instruction line and one `[id] source` line per bubble. No image, no device details.

**Same prompt, same parser, no grammar.** The reply is read by the line parser the on-device translator uses, so bubbles still appear one by one, except when a reply that may be thoughts waits (next paragraph). `<think>` blocks are stripped first. No `response_format` is sent.

**Thoughts with no opening tag.** Some setups open the `<think>` block in the chat template's prompt, such as DeepSeek-R1 distills or a server with its reasoning parser turned off. The reply then carries the thoughts and only a bare `</think>`. A reply whose first line is an `[id]` line is taken to be the answer and streams as above. Any other reply waits for its first tag. A `<think>` means the thoughts come in whole blocks, which are stripped as above. A bare `</think>` drops everything before it. If no tag comes, the held lines are shown in order when the model ends the reply or the text passes 16,000 characters, about the 4096-token cap in English. A reply that is cut off, breaks off or times out while held shows none of it, since it may be thoughts. Thoughts that open with an `[id]` line, or run past that limit, still reach the parse ([#500](https://github.com/artsaraiva/yomu/issues/500)). Servers that move reasoning into a separate field, as Ollama, LM Studio and llama.cpp's default parser do, never send it in the reply text.

**Sampler settings.** The request sends the reader's temperature and top-p and a cap of 4096 reply tokens. Top-k and the penalties are not part of the protocol and are not sent. The cap is higher than the phone's 768 because reasoning models spend tokens before they answer.

**Limits.** 10 seconds to connect and 120 seconds for the whole page, the on-device batch's limit. Running out is the existing TIMEOUT outcome.

**No retry.** A line the reply skips, or one dropped as a non-translation or Japanese residue, is shown as not translated, as on the phone.

**A failed page fails visibly.** The message names the cause: the server can't be reached, the key was rejected, the model isn't there, or the page timed out. Yomu does not quietly translate the page on the phone instead. The one automatic fallback is the existing one, for a network translator that isn't set up.

**Plain http only to nearby addresses.** `http://` is accepted when the host resolves to a loopback, private, link-local or carrier-grade NAT (100.64.0.0/10) address. Any other host needs `https://`. The check is made on the resolved address when connecting, and there is no override.

**The key.** It is encrypted with AES-GCM under an Android Keystore key and left out of backups. If the Keystore can't be used, the key is held in memory until the app stops and the reader is told it was not saved. It is never written unencrypted. It is sent as `Authorization: Bearer`, only after the reader taps Connect, and only to the address that Connect confirmed. Editing the address clears the confirmation.

**One server.** One address, one optional key, one model. Connect reads the server's `/v1/models` list for the reader to pick from; a server with no list takes a typed model name.

**Settings.** The Providers entry becomes the screen that sets the server up. The reader chooses the server in the model picker on Home, which lists models in two groups, "On this phone" and "Your server"; the checked row is the model that translates the next page. The server's group lists the models from the last Connect, kept with the server settings so that opening the picker sends nothing, or the typed model name for a server with no list. Until Connect has succeeded and a model is picked, the group offers only the way to Providers. Picking a phone model, in the picker or in Settings › Translation, switches back to the phone, and so does changing the server's address: a new address takes pages only once a server model is picked again. Settings › Translation has no translator choice. While a server model is picked, every target language can be chosen, since "English only" is a phone-model rule. *Amended by [#490](https://github.com/artsaraiva/yomu/issues/490): the choice was first planned as a "Translate with" control in Settings › Translation.*

**The reader is told.** Turning the network translator on shows a notice, once, that the page's text will be sent to the address they entered. The overlay shows a marker while a page is translated over the network.

**Page recall and page reports.** The page's settings gain the translator and, for the network translator, the server's model name. Both are part of the page-recall key and appear in a page report. The server's address is in neither.

**The phone model is needed only while the phone translates.** Bubble detection and OCR always run on the phone, so Yomu is never ready without them. The selected phone deliverable is required only while the on-device translator takes the pages. A reader who translates on their own server would otherwise download a model of about 940 MB they never use, and a phone that can't run any phone model couldn't use Yomu at all. Setup offers the server in place of the translation model: it downloads detection and OCR only, opens Providers, and finishes once a server model is picked. Deleting the phone model while a server model is picked leaves reading on. If the server stops being set up, pages go back to the phone as before, and with no phone model Yomu is not ready until one is downloaded. *Amended by [#536](https://github.com/artsaraiva/yomu/issues/536): an on-device model was first required whichever translator was chosen.*

## Considered Options

- **Keep translation on the phone only.** Rejected. ADR-0018's reasons no longer hold, and the Providers entry has said "Coming soon" since it was added.
- **Home servers only, no hosted APIs.** Rejected. Both speak the same protocol, so excluding hosted APIs would mean blocking addresses, not writing less code. The https rule is what protects a key on the open internet.
- **Ollama's own endpoint as well.** Rejected for now. It buys `keep_alive`, at the cost of a second request shape, a second stream parser and server detection. It can be added if models unloading between pages turns out to hurt.
- **A JSON schema through `response_format`.** Rejected. It needs a second prompt and a second parser, and incremental JSON parsing to keep bubbles streaming. Server support is uneven, so the parse would still be the real guard.
- **Ask the server to keep reasoning out of the reply text.** Rejected. No request field for it is shared across OpenAI-compatible servers, and a strict hosted API may reject a field it doesn't know, as with top-k.
- **Hold every reply until a bare `</think>` or the limit.** Rejected. Most pages are shorter than any useful limit, so a model that doesn't think would lose streaming on nearly every page.
- **Fall back to the phone when the server fails.** Rejected. The reader would get a different translator's output without knowing, and the page would be recalled under the wrong settings.
- **Plain http to any address, or https only.** Both rejected. The first sends a key in the clear over the internet. The second makes every home server need a certificate.
- **`androidx.security:security-crypto` for the key.** Rejected. Its APIs are deprecated, and the fork's use of it fell back to an unencrypted file without telling the reader.
- **Keep the on-device model required whichever translator is chosen.** First chosen, since readiness, setup and ADR-0016 all assumed a downloaded deliverable, then dropped by [#536](https://github.com/artsaraiva/yomu/issues/536): a reader translating on their own server downloaded a model they never used, and a phone that can't run any phone model couldn't read at all. Nothing needs that model while the server takes the pages, since a failed server page is never retried on the phone.

## Consequences

**The app allows cleartext traffic.** Android can't limit cleartext to local addresses in the manifest, so the manifest allows it and the rule above is enforced in code. The model download keeps using https.

**The public promise changes when the feature does.** The README, `SECURITY.md`, `CONTEXT.md`, `AGENTS.md` and the bug report template say nothing leaves the phone. They stay as they are until the pull request that makes the network translator reachable, and change in that pull request: translation stays on the phone unless the reader points Yomu at their own server.

**A translator switch has to change the page-recall key.** Today the key holds the on-device model but not the translator, so a page translated on the phone would be recalled after switching to the server.

**The sampler panel covers two translators.** Its text says the settings are for the on-device model. Top-k applies on the phone only, and the panel has to say so.

**The grammar's guarantees don't cover a network page.** A reply with malformed id tags loses those lines, and a whole page can come back untranslated, the failure ADR-0013 used the grammar to remove. The reader sees how many bubbles were not translated and can translate again.

**Auto backup needs rules.** The manifest sets none today, so Android backs the app's preferences up. The build adds rules that leave the key out.

**A new Keystore alias.** The app still deletes the retired `yomu_hf_token_v1` alias on every start.

## Supersedes ADR-0018

ADR-0018's first paragraph no longer describes the plan: a translator on another machine the reader chose is allowed. What stands from it: the on-device translator is the default, the curated model download is the only network use until the reader opts in, and a page report leaves the phone only through the share sheet.

## Amends ADR-0013

*The grammar is mandatory on the batch path* now reads: mandatory on the on-device batch path. The network translator sends the same id-keyed page prompt with no grammar, and the parse and the post-hoc guards are what check its reply.

## Amends ADR-0016

*A curated LLM deliverable is the only thing that fills the translation slot* now reads: a translator fills the slot, and the on-device translator runs a curated or custom LLM deliverable. The rest stands, including that Yomu is not ready until the selected deliverable is downloaded. A network translator is still an LLM; ML Kit and OPUS-MT stay removed.
