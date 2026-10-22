# Making a release

1. Raise the version in two places: `versionCode` (by one) and
   `versionName` in `AndroidManifest.xml`, and `VERSION` in
   `src/com/shumtugle/umbra/World.java`.
2. Add a section for the version to `CHANGELOG.md`.
3. Build:

   ```sh
   export ANDROID_JAR=/path/to/android.jar
   export KEYSTORE=/path/to/your.keystore KEY_ALIAS=yourkey KS_PASS=yourpassword
   ./build.sh
   ```

4. Check the signature. The certificate digest must be the same as in every
   earlier release, or phones will refuse the update:

   ```sh
   apksigner verify --print-certs out/umbra.apk
   ```

5. Name the package after the version, for example `umbra-0.2.apk`.
6. Commit, tag and push:

   ```sh
   git add -A
   git commit -m "Release 0.2"
   git tag v0.2
   git push && git push --tags
   ```

7. On the repository page, draft a new release from the tag, paste the
   version's section of the changelog, and attach the APK.
8. The project page is the `docs` folder. Once, in the repository settings,
   set the pages source to the `docs` folder of the main branch.

The signing key never goes into the repository: `.gitignore` keeps keystores
and packages out. Keep a copy of the key somewhere safe; without it there are
no updates, only new installs.
