# Releasing

Every push to `main` gets a dev release without manual work. `publish.yml` gives the commit the version
`<devkit-version without -SNAPSHOT>-dev-<first-parent commit count>`. It uploads all three builds to the
[Space EAP repository](https://packages.jetbrains.team/maven/p/compiler-plugin-dev-kit/eap). A full release, such as
`0.1.0`, needs the steps on this page.

## Prepare the repository

In `CHANGELOG.md`, move the `[Unreleased]` entries into a new `## [<version>] - <date>` section. Leave an empty
`[Unreleased]` section at the top. Add the compare link of the new version at the bottom of the file.

Set the version referenced in [prompt.md](./docs/migration/prompt.md) to the new version.

Set `devkit-version` in `gradle/libs.versions.toml` to a version above the release, with the `-SNAPSHOT` suffix. The
publication takes its version from the `DEVKIT_PUBLISH_VERSION` variable, so this entry doesn't change the published
version. `publish.yml` derives the dev release version of every push from this entry. Without the bump, the next dev
releases sort below the full release.

Cut a `release/<version>` branch from `main` to freeze the published state.

## Publish on TeamCity

Run the publish build on TeamCity manually. The build needs:

- `DEVKIT_PUBLISH_VERSION` set to `<version>`.
- `libs.sign.key.id`, `libs.sign.key.private`, and `libs.sign.passphrase` set. When the key is set, the build signs
  every publication. Maven Central rejects unsigned artifacts.

TeamCity publishes the root build and `version-resolution` to Maven Central, and the `plugins` build to the Gradle
Plugin Portal.

## Publish the GitHub release

After publication, publish a GitHub release for the version:

- Tag `v<version>` on the published commit.
- Use the changelog section of the version as the body.

## Update compiler-plugin-template

After publication, update [compiler-plugin-template](https://github.com/Kotlin/compiler-plugin-template/tree/devkit) to
use the new version.
