# Outside contributions need a CLA

**Status:** accepted, 2026-11-23. Decides [#474](https://github.com/artsaraiva/yomu/issues/474). Supersedes the "Contributions: no CLA" section of [ADR-0019](0019-yomu-is-agpl-open-source.md). The rest of ADR-0019 stands, and Yomu stays AGPL-3.0.

Anyone other than the maintainer who contributes to Yomu first agrees to [`CLA.md`](../../CLA.md). It is a licence grant, not a copyright assignment. Contributors keep their copyright and give the maintainer a perpetual, irrevocable licence to use, change, sublicense and distribute their work. That keeps Yomu's licence changeable later without needing every contributor's consent. ADR-0019 recorded the lack of exactly that as the cost of having no CLA.

## Why now

No outside code is in `main` yet: every commit there is the maintainer's or Dependabot's. Asked for up front, a CLA costs a contributor one comment. Added after outside code has landed, it would mean finding every past contributor, or rewriting their code.

## The agreement

`CLA.md` is the Apache Software Foundation's Individual Contributor License Agreement V2.2. Its terms are unchanged except for names and scope:

- "the Foundation" becomes the maintainer, named in the agreement, and "products owned or managed by the Foundation" becomes Yomu.
- The definition of "submitted" gives the Yomu repository, its pull requests and its issues as examples.
- Contributors accept it with a pull-request comment instead of signing and emailing a PDF, so the personal-details form is gone.
- The Foundation's promise to act within its nonprofit status and bylaws is dropped, because it doesn't apply to an individual maintainer.
- The employer clause no longer mentions a corporate CLA, because Yomu doesn't offer one.

Apart from the names, the licence grant, the patent grant and the contributor's representations are word for word. A plain-language summary above the agreement says it is not part of it.

## The check

`.github/workflows/cla.yml` runs CLA Assistant Lite (`contributor-assistant/github-action` v2.6.1) on every pull request and on pull-request comments:

- **It fails until every commit author has agreed.** It finds authors through the GitHub account linked to each commit's email. A contributor agrees by commenting the phrase that `CLA.md` gives, and the comment run then re-runs the failed check.
- **The maintainer and bots are exempt:** `artsaraiva`, `dependabot[bot]` and `coderabbitai[bot]`.
- **Agreements live on the `cla-signatures` branch**, in `signatures/version1/cla.json`, written by the workflow. The `main` ruleset covers only the default branch, so the action can push there. A later version of the CLA would start a new file.
- **It never checks out or runs pull-request code.** It runs on `pull_request_target`, which uses `main`'s copy of the workflow and a write token, and its only step is the pinned action.
- **Merged pull requests are locked.** On a locked pull request only collaborators can add, hide or delete comments, so a contributor can't delete their agreement comment after merge. The signature file keeps its own record either way.
- **It reports on docs-only pull requests too.** Android CI skips those, which is why they need the admin bypass. The CLA check adds nothing to that. The bypass skips every required check, though, so before using it on an outside contributor's docs-only pull request, check that the CLA check passed.

## Considered Options

- **No CLA**, ADR-0019's choice. Rejected: changing the licence would need every contributor's consent.
- **Copyright assignment.** Rejected. It asks contributors to give up ownership, which puts people off, and a broad licence is enough.
- **Developer Certificate of Origin.** Rejected. It certifies the right to contribute under the project's licence, but grants nothing beyond it.
- **A Harmony agreement.** Workable, but not chosen. The Apache ICLA is the more familiar text, and its sublicense right gives the same room.
- **The CLA Assistant GitHub App** (cla-assistant.io). Not chosen. It is a hosted service that keeps agreements in its own database, needs the owner to install it and grant it access, and links the agreement from a gist rather than the repository's file. It is still maintained, though, so it is the fallback.
- **The CLA Assistant Lite action. (Chosen.)** It lives in the repository: the workflow, the agreement and the record of who agreed are all here, with history, and it needs no secret or install.

## Consequences

**The action is archived.** Its repository was archived in March 2026 and will get no further releases. The workflow pins it to the full v2.6.1 commit SHA, so its code can't change. It declares the deprecated Node 20 runtime, which GitHub's runners already replace with Node 24, and it works there (first run on [#476](https://github.com/artsaraiva/yomu/pull/476), 2026-11-23). The risk is that a later runner or API change breaks it. If that happens, switch to the GitHub App or to a maintained fork, and keep `signatures/version1/cla.json` as the record of past agreements.

**The signature file has to exist.** The action's code for creating a missing file never runs, because it compares a numeric HTTP status to the string `"404"`. A missing file or branch makes every run fail, so the `cla-signatures` branch must not be deleted.
