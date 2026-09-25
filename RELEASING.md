# Releasing to Maven Central

This project publishes `org.yeauty:netty-websocket-spring-boot-starter` through **Central Publisher Portal**. OSSRH shut down on June 30, 2025; use a Portal token and the publishing plugin rather than the former OSSRH endpoints.

## One-time setup

1. Sign in to [Central Portal](https://central.sonatype.com/) and confirm publishing access to the `org.yeauty` namespace. Contact Central support if the migrated namespace is missing.
2. Generate a **Portal user token**. Keep its username/password in environment variables or your private Maven settings; never commit them or paste them into an issue.
3. Merge the `central` server entry from [`release/settings.xml.example`](release/settings.xml.example) into your private settings, or copy that template outside the repository and pass it with `./mvnw -s /path/to/settings.xml`. It reads `CENTRAL_USERNAME` and `CENTRAL_PASSWORD` from the environment.
4. Select a valid signing key using `gpg --list-secret-keys --keyid-format LONG`. Make its public key available on a [Central-supported keyserver](https://central.sonatype.org/publish/requirements/gpg/). Use `-Dgpg.keyname=FINGERPRINT` to select the intended release key. Use gpg-agent or `MAVEN_GPG_PASSPHRASE` for the passphrase; keep passphrases out of command arguments.

## Prepare 1.0.0

- Use JDK 17 or 21 and Maven 3.9.11. Keep the POM, both READMEs and release tag consistent: `1.0.0` / `v1.0.0`.
- Update `project.build.outputTimestamp` for each release; keep it fixed for rebuilds of the same release.
- Review and commit the changes, then run:

```sh
./mvnw -B -ntp -Pconsumer-test clean verify
./mvnw -B -ntp -Dnetty.version=4.2.17.Final verify
./mvnw -B -ntp -Prelease -Dgpg.skip=true verify
```

The last command verifies unsigned packaging and does not upload. Inspect the main, sources and Javadoc JARs, auto-configuration imports, project LICENSE and POM metadata.

## Sign locally without uploading

```sh
./mvnw -B -ntp -Prelease -Dgpg.keyname=FINGERPRINT verify
```

The release profile signs the POM and all three JARs in `target/`. `verify` does not upload and does not require Portal credentials. Keep `gpg.skip` disabled for real release artifacts. Verify each `.asc` with `gpg --verify SIGNATURE ARTIFACT`; the signed POM is `target/netty-websocket-spring-boot-starter-1.0.0.pom`.

On macOS, use a working native pinentry dialog or an already-unlocked gpg-agent. Enter the passphrase only in the local signing dialog, never in chat or committed files.

After the final tests and signature checks pass, commit/push the source and create/push `v1.0.0` from that exact commit. A Git tag identifies the source; it does **not** mean the artifact is available on Maven Central. Do not move the tag to a different commit. If code changes are needed after tagging, use a new version.

## Upload, validate and publish

From the reviewed release commit, with Portal credentials configured:

```sh
./mvnw -B -ntp -Prelease -Dgpg.keyname=FINGERPRINT deploy
```

The plugin creates `target/central-publishing/central-bundle.zip`, uploads it and waits for validation (`autoPublish=false`, `waitUntil=validated`), leaving the deployment for a maintainer to publish in the Portal. Review its coordinates/files, then choose **Publish**. A successful upload alone does not mean the artifact is publicly available.

Use `verify`, not `deploy -DskipPublishing=true`, for a local signing check: with plugin 0.11.0, the latter was observed to skip staging and produce no bundle even though the build succeeded.

After publication:

1. Resolve `org.yeauty:netty-websocket-spring-boot-starter:1.0.0` from Central using a fresh local repository and run the consumer smoke test against it.
2. Verify files/signatures at the Maven repository path. Search indexing can lag behind repository availability.
3. Confirm `v1.0.0` still points to the exact published source commit and publish the GitHub release/migration notes. Never reuse a published version for different contents.

If validation fails, correct the reported bundle errors before retrying. Namespace access and token setup are account requirements; changing only a Maven URL does not establish them.

## Official references

- [Maven publishing plugin](https://central.sonatype.org/publish/publish-portal-maven/)
- [OSSRH sunset and migration](https://central.sonatype.org/pages/ossrh-eol/)
- [Portal tokens](https://central.sonatype.org/publish/generate-portal-token/)
- [Publishing requirements](https://central.sonatype.org/publish/requirements/)
