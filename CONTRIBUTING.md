# Contributing to Yomu

Thanks for helping. Yomu is a small project, so a short issue before a big change saves everyone time.

## Ways to help

- **Report a bug** with the [bug report form](https://github.com/artsaraiva/yomu/issues/new?template=bug_report.yml). Yomu runs every model on the phone, so the phone model, Android version and RAM matter most.
- **Suggest a feature** with the [feature request form](https://github.com/artsaraiva/yomu/issues/new?template=feature_request.yml).
- **Ask a question** in [Discussions](https://github.com/artsaraiva/yomu/discussions/categories/q-a), not in issues.
- **Report a vulnerability** privately, as [`SECURITY.md`](SECURITY.md) describes.

## Building

Follow [Build from source](README.md#build-from-source) in the README. Clone with `--recursive`, because llama.cpp is a submodule.

## Making a change

1. **Start from an issue.** Open one, or comment on an existing one, describing the behaviour and how you will know it works. A typo fix can skip this step.
2. **Branch** as `feat/<issue>-<short-name>` or `fix/<issue>-<short-name>`.
3. **Write the failing test first**, then make it pass. [`CODING_STANDARDS.md`](CODING_STANDARDS.md) lists the code and test rules review checks against.
4. **Commit** with [Conventional Commits](https://www.conventionalcommits.org/), for example `fix: keep the overlay above the status bar`.
5. **Open a PR** whose body starts with `Closes #<issue>`. Keep it to that one issue. On your first PR, agree to the [CLA](#licence).

Before pushing, run what CI runs:

```bash
scripts/check-code-standards.sh
./gradlew detekt lint test assembleDebug
```

Some changes need a check that unit tests can't give:

- **Overlay, service or UI changes:** try them on a phone or emulator. The overlays are separate windows, so use `uiautomator dump --windows` to inspect them.
- **Model or llama.cpp changes:** run `scripts/run-speed-benchmark.sh` and paste its table in the PR, naming the device.

PRs are squash-merged into `main` once CI and the CLA check pass and review threads are resolved.

## Test pages

Don't commit manga pages unless their licence allows it. The OpenMantra pages used in local benchmarks are CC BY-NC and stay out of the repository.

## AI coding agents

[`AGENTS.md`](AGENTS.md) holds the same conventions written for coding agents. Using one is fine. You are responsible for every line you submit, so read and test the change yourself.

## Licence

Yomu is licensed under [AGPL-3.0](LICENSE). Contributions need a [contributor licence agreement](CLA.md) (CLA): you keep the copyright to your work and give the maintainer a licence to it, so Yomu's licence can change later without needing every contributor's consent.

You agree once, on your first pull request. CLA Assistant comments with a link; sign in with GitHub and agree, and the `license/cla` check passes. It covers your later pull requests too.
