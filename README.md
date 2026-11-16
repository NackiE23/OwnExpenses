# OwnExpenses

Personal offline-first expense tracker for Android: record an expense in 2–3 taps and always see the balance. Local database, no accounts; optional one-way sync to your own OwnBudget server (the only network use). iOS comes later from the same Kotlin Multiplatform codebase.

Requirements and decisions: [docs/TZ.md](docs/TZ.md) (Ukrainian).

## Modules

| Module | Contents |
| --- | --- |
| `androidApp` | Android shell: `Application` (Koin start), `MainActivity`, manifest, widget later |
| `sharedUi` | Compose Multiplatform screens, theme, navigation, uk/en strings |
| `shared` | Domain models, Room database, DataStore settings, repositories, ViewModels, CSV |

Dependency direction: `androidApp → sharedUi → shared`.

## Stack

Kotlin Multiplatform, Compose Multiplatform, Room (KMP, bundled SQLite), DataStore, Koin, Navigation Compose, kotlinx-datetime, Ktor client and WorkManager (sync). Versions live in [gradle/libs.versions.toml](gradle/libs.versions.toml).

## Commands

```
./gradlew :androidApp:assembleDebug
./gradlew :shared:jvmTest :shared:testAndroidHostTest
./gradlew spotlessCheck          # spotlessApply to fix
./gradlew :androidApp:lintDebug
./gradlew :shared:compileKotlinIosArm64 :sharedUi:compileKotlinIosArm64
```

`shared` and `sharedUi` already target iOS (`iosArm64`, `iosSimulatorArm64`); the Xcode project arrives at stage 5.

CI runs the same set on every push to `master` and on pull requests.
