# The CLA Assistant app collects CLA agreements

**Status:** accepted, 2026-11-23. Decides [#479](https://github.com/artsaraiva/yomu/issues/479). Revises "The check" and its Consequences in [ADR-0021](0021-outside-contributions-need-a-cla.md). The agreement in [`CLA.md`](../../CLA.md), and the reasons for asking for it, stand.

Outside contributors agree to the CLA through the [CLA Assistant](https://cla-assistant.io) GitHub App. On a pull request from someone who hasn't agreed, the app comments with a link. The contributor signs in with GitHub and agrees, and the `license/cla` check passes. The repository has no CLA workflow and no branch for agreements.

## Why

ADR-0021 chose CLA Assistant Lite, a GitHub Action, so that the record of agreements would live in the repository. In practice that record is a branch, `cla-signatures`. It stays in the branch list for good, and GitHub suggests opening a pull request from it after every write. The only alternative the action offers is a second repository, which needs a personal access token stored as a secret. The action's own repository was archived in March 2026, so any later runner or API change could break it.

ADR-0021 already named the app as the fallback. It is maintained, and it needs nothing in the repository.

## Considered Options

- **Keep the action, with agreements on `cla-signatures`.** Rejected: a permanent extra branch, an archived dependency, and a bug that makes every check fail if the branch is ever deleted.
- **Keep the action, with agreements in a separate repository.** Rejected: the clutter moves to a new repository, a personal access token has to be stored as a secret, and the action is still archived.
- **No bot: agreement by pull-request comment, checked by the maintainer before merging.** Workable while outside pull requests are rare, but nothing blocks a merge if the check is forgotten.
- **The CLA Assistant GitHub App. (Chosen.)**

## Consequences

**Agreements live on cla-assistant.io, not in the repository.** The service is run by SAP. It keeps each signer's GitHub username and the time they agreed, against the revision of the agreement they saw.

**The agreement text is in two places.** The app shows signers a gist, and the gist holds the same text as the Agreement section of `CLA.md`. Change both together. A change to the terms is a new version of the CLA.

**The required check is `license/cla`.** The app reports it as a commit status. It replaces ADR-0021's `CLA` check in the `main` ruleset. As before, the admin bypass for docs-only pull requests skips every required check, so on an outside contributor's docs-only pull request, check that `license/cla` passed before bypassing.

**The maintainer and bots are on the app's allowlist:** `artsaraiva`, `dependabot[bot]` and `coderabbitai[bot]`.

**`cla-signatures` is deleted.** It held no agreements: nobody had agreed under the action. Merged pull requests are no longer locked either, because no agreement depends on a comment any more.
