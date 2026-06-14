# PocketCraft Third-Party Notices

Generated on: 2026-04-05
Generator: Gradle task `generateLicenseReport` using `com.github.jk1.dependency-license-report`

This file is the release notice index for third-party software used by PocketCraft (including transitive dependencies).

## Canonical License Inventory

- Full transitive dependency inventory (module + license): `build/reports/dependency-license/licenses.md`
- Machine-readable inventory: `build/reports/dependency-license/licenses.json`

## Observed License Families in Current Scan

- Apache License 2.0
- Android Software Development Kit License (Google Play Services / Firebase / Ads SDK components)

## Release Packaging Guidance

For release packaging, include this file and the generated inventory from `build/reports/dependency-license/` in your compliance archive.

## Re-generation Command

Run before each release candidate:

`./gradlew generateLicenseReport --no-daemon --console=plain`

## Notes

- This notices file is generated from dependency metadata and should be reviewed by legal/compliance before public release.
- If any dependency lacks declared license metadata, manually verify and append attribution in this file.
