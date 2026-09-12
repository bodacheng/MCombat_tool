# HEADSLING iOS App Store pipeline

Jenkins job: `HEADSLING-iOS-AppStore`

- Pipeline source: `pipeline_script/DevBuild/ios/headsling_app_store.groovy`
- SCM: `https://github.com/bodacheng/MCombat_tool.git`, branch `master`
- Workspace: `/Users/daisei/HeadGame`
- Unity entry point: `HeadSling.Editor.HeadSlingMobileBuilder.BuildIOS`
- Bundle ID: `com.headsling.prototype`
- Apple team ID: `S88E744TXJ`
- Export method: `app-store-connect`
- Provisioning profile: `HEADSLING App Store`
- App Store Connect authentication: Jenkins credentials; secrets are not stored in this repository

## Parameters

- `APP_VERSION`: marketing version, default `1.0`
- `CLEAN_LIBRARY`: optionally rebuild the Unity Library cache
- `UPLOAD_APP_STORE`: validate and upload the IPA, default enabled

The Jenkins build number is used as `CFBundleVersion`. Each upload therefore has a unique build number.

## Build stages

1. Check Unity, Xcode, and signing identities.
2. Export the Unity iOS Xcode project.
3. Archive the app with Xcode.
4. Export and archive the IPA as a Jenkins artifact.
5. Validate and upload the IPA to App Store Connect.

## Verified delivery

Build `#10` was loaded from this SCM pipeline and completed successfully on 2026-09-12:

- Version: `1.0 (10)`
- Archive: succeeded
- Export: succeeded
- App Store validation: succeeded with no errors
- Upload: succeeded with no errors
- Delivery UUID: `3c4c22a5-6638-412a-9a9b-e736e18aff03`
- Export compliance: `ITSAppUsesNonExemptEncryption=false`

The uploaded build contains the production HEADSLING icon from `Assets/Editor/iOS/HEADSLING_AppIcon_1024_v2.png`.
