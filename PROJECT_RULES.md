# Project Isolation & Change-Safety Rules

These are standing requirements for all new and existing projects maintained with ChatGPT.

## Non-negotiable rule
Keep every new requirement, feature, experiment, integration, and project separate from existing working functionality by default. Protect the working parts first; avoid regressions.

## Required workflow
1. **Inspect before changing.** Identify the target project, its architecture, entry points, existing behavior, dependencies, build/deployment workflow, and likely blast radius.
2. **Isolate the feature.** Prefer a new page, module, component, package, service, route, folder, or repository. Reuse shared technical infrastructure where sensible, but do not merge feature-specific code into unrelated working features.
3. **Reuse safely.** Copy or adapt technical patterns, utilities, configuration, and build setup when useful. Keep copied components scoped to the new feature and make dependencies explicit. Do not blindly duplicate secrets, credentials, private data, or licences.
4. **Preserve existing behavior.** Avoid broad rewrites, renames, dependency upgrades, global CSS/JS changes, and edits to unrelated files. If a shared file must change, make the smallest backward-compatible change possible and explain why.
5. **Use separate branches and commits where practical.** Prefer a feature branch or a clearly isolated commit. Keep changes easy to review and revert. Do not overwrite user work.
6. **Validate before declaring completion.** Run relevant tests/builds and check important existing paths. If validation cannot be run, say exactly what is and is not verified. Never claim a build, deploy, stream, or feature works unless the evidence supports it.
7. **Provide a rollback path.** Identify the exact commit or files to revert and preserve a way to recover the previous working version.
8. **Deployment and releases.** Do not replace a stable release or APK silently. Check workflow status and release artifacts before saying the update is ready to install.
9. **Keep boundaries explicit.** New features should have their own files, routes, configuration, and tests wherever feasible. Shared integration points should be thin, documented, and tested.
10. **When the request is ambiguous, default to separation.** Do not ask the user to repeat this rule. Only seek clarification if a necessary technical decision cannot be made safely; otherwise use a conservative, isolated implementation.

## Default acceptance checklist
- [ ] Target project and current baseline inspected
- [ ] New functionality isolated in dedicated files/modules/routes
- [ ] Existing features and working paths left untouched unless integration is necessary
- [ ] Shared-file changes are minimal and backward-compatible
- [ ] New/updated tests or practical validation performed
- [ ] Existing functionality regression-checked where feasible
- [ ] Build/deployment/release status accurately reported
- [ ] Rollback steps or the revert commit identified
- [ ] Documentation updated for new entry points and dependencies

If a one-line integration into an existing index/navigation is needed, keep it minimal and make the new feature page/module independent. This rule applies to web apps, Android/TV apps, games, scripts, automation, APIs, documents, data projects, repositories, and every other new project.
