# PocketCraft Data Safety Declaration

## Data Collected

| Data Type | Collected | Shared | Purpose | Required |
|-----------|-----------|--------|---------|----------|
| Google Account name | Yes | No | User identification, Drive backup | Yes |
| Google Account email | Yes | No | User identification | Yes |
| Firebase Analytics events | Yes | No (aggregated only) | Crash reporting, usage analytics | No |
| Crashlytics crash logs | Yes | No | Bug fixing | No |
| Server world files | Yes (Drive backup) | No | User backup | No |
| IP address | Yes (relay connection) | No | Server hosting relay | Yes |
| Device info | Yes (Crashlytics) | No | Crash context | No |

## Encryption
- All data in transit: Yes (TLS)
- All data at rest: Yes (Firebase encryption)

## Deletion
- User can delete all data via in-app account deletion (Settings → Delete Account)
- Firebase data deleted within 30 days of account deletion

## Children
- App is not directed at children under 13
- COPPA: Not applicable
