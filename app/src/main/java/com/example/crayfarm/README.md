# CrayFarm Android source structure

- `MainActivity.kt` — Android lifecycle entry point only.
- `ui/CrayFarmApp.kt` — compact authentication/session application host.
- `navigation/CrayFarmNavigation.kt` — authenticated pond and bottom-navigation
  routing.
- `ui/auth/` — role selection, login, registration, and approval waiting.
- `ui/ponds/` — pond selection and pond management.
- `ui/dashboard/` — live monitoring, alerts, thresholds, and dashboard UI.
- `ui/analytics/` — pond history analytics.
- `ui/reports/` — report UI plus CSV/PDF export.
- `ui/workers/` — active and archived worker management.
- `ui/settings/` — settings, farm profile, account, and worker profile screens.
- `ui/components/` — reusable application UI such as headers and navigation.
- `ui/theme/` — CrayFarm colors, typography, and Material theme.
- `model/` — shared immutable application models and navigation enums.
- `domain/` — pure business rules that can be unit tested without Android.
- `data/repository/` — Firebase report loading, alerts, ponds, workers,
  activities, authentication helpers, and snapshot mapping.
- `viewmodel/` — feature UI state; Reports currently uses a tested ViewModel
  boundary and provides the pattern for future state migrations.
- `notification/` — Android notification integration.
- `util/` — shared Device ID normalization and CrayFarm QR payload parsing.

All extracted files currently keep the `com.example.crayfarm` package so the
refactor does not change public types, navigation behavior, or Firebase paths.
This preserves compatibility while the package declarations can be migrated
separately in a future cleanup.
