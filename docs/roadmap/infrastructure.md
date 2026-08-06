# Infrastructure — CI, Review & Security

**Milestone:** #10

## Goal

Move critical quality checks from agent-local verification into repeatable GitHub automation.

## Open issues

- [#1 CI pipeline](https://github.com/artsaraiva/yomu/issues/1)
- [#2 Dependabot](https://github.com/artsaraiva/yomu/issues/2)
- [#3 CodeRabbit](https://github.com/artsaraiva/yomu/issues/3)
- [#4 Security scanning](https://github.com/artsaraiva/yomu/issues/4)

## Delivery order

1. CI: lint, tests, and debug build on PRs.
2. Branch protection requiring those checks.
3. Dependabot and security alerts.
4. Code review automation and deeper vulnerability scanning.
