# Agent Notes

Mistysound is the GitHub repository directory. Keep this folder limited to the core source code, build configuration, required metadata, and concise project documentation.

Do not put temporary AI drafts, scratch files, release artifacts, local caches, private signing assets, or bulky source materials in this repository. Prefer `G:\MSgithub\others` for general scratch or migration materials, and `G:\MSgithub\Mistysound_private` for private app resources, signing files, and release artifacts.

Preserve update compatibility unless the user explicitly asks otherwise. Do not change the Android package namespace, `applicationId`, Kotlin package paths, database name, or existing app display name behavior as part of routine cleanup.

For release packaging, build and publish the public APK by default. Use `.\gradlew.bat copyReleaseArtifacts`, or the backwards-compatible `.\gradlew.bat copyDualReleaseArtifacts` alias; both should only produce the public release artifact named `Mistysound-<version>-public-release.apk`. Do not build or publish personal release APKs unless the user explicitly asks for them.

The project is a fork of SoundAura renamed for repository and display purposes. Internal legacy names such as package paths, class names, database names, and historical migration/update text may remain when they support compatibility or avoid unnecessary churn.
