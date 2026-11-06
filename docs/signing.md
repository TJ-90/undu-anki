# Family APK signing

The family build is not yet verified for installation or upgrades. The **Family APK** GitHub Actions workflow only runs on trusted `main` and requires these repository secrets:

- `ANDROID_KEYSTORE_BASE64`: the base64-encoded persistent keystore.
- `ANDROID_KEYSTORE_PASSWORD`: keystore password.
- `ANDROID_KEY_ALIAS`: signing key alias.
- `ANDROID_KEY_PASSWORD`: key password.

Create and keep the keystore in secure custody outside this public repository, and retain a secure backup. Never commit the keystore or passwords. Losing the key prevents updates signed with that key from installing over the existing family app. Configure the four secrets in GitHub repository settings before manually running the workflow; missing secrets cause it to fail instead of falling back to a debug key.

The workflow uses version code `1000 + github.run_number`, verifies the APK signature, and includes its certificate fingerprint and checksum with the artifact. A new workflow run increases the version code; rerunning the same run may reuse it. Verify two Actions-built APKs have the same application ID and signing certificate, then install the later APK over the earlier one on a test device and check saved progress before relying on this update path. The disposable debug APK uses a separate application ID and is not a substitute for this proof.
