# Реліз без Google Play

Застосунок поширюється підписаним APK, який встановлюється на телефон вручну. Оновлення ставиться поверх старої версії, і дані зберігаються, якщо виконано три умови: той самий ID пакета `com.nackie.ownexpenses`, той самий ключ підпису й більший `versionCode`.

## Ключ підпису (один раз)

Ключ підтверджує, що оновлення від вас. Загубите ключ або пароль, і оновити встановлений застосунок уже не вийде: доведеться видалити його разом з даними й відновити з резервної копії. Тримайте файл ключа й пароль у менеджері паролів або іншому надійному місці поза комп'ютером.

1. Створіть ключ (пароль вводиться в терміналі й ніде не зберігається). У PowerShell (термінал Android Studio на Windows):

   ```powershell
   New-Item -ItemType Directory -Force "$HOME\keys" | Out-Null; & "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -v -keystore "$HOME\keys\ownexpenses-release.jks" -alias ownexpenses -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Dmytro Varchuk, O=OwnExpenses, C=UA"
   ```

   У Git Bash:

   ```bash
   mkdir -p ~/keys && "/c/Program Files/Android/Android Studio/jbr/bin/keytool.exe" -genkeypair -v -keystore ~/keys/ownexpenses-release.jks -alias ownexpenses -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Dmytro Varchuk, O=OwnExpenses, C=UA"
   ```

2. Скопіюйте `keystore.properties.example` у `keystore.properties` у корені проєкту й заповніть: шлях до ключа, пароль (той самий у `storePassword` і `keyPassword`), псевдонім `ownexpenses`. Файл ігнорується git.

Без `keystore.properties` release-збірка лишається непідписаною (так працює CI) і на телефон не встановлюється.

## Збірка релізу

1. Підніміть `versionCode` на 1 і за потреби `versionName` в `androidApp/build.gradle.kts`.
2. Зберіть:

   ```bash
   ./gradlew :androidApp:assembleRelease
   ```

3. Готовий файл: `androidApp/build/outputs/apk/release/androidApp-release.apk`.

APK зберігається в GitHub Releases: новий реліз з тегом версії (`v1.0.0`, `v1.1.0`…) і APK у вкладеннях. Тег прив’язаний до конкретного коміту, тож нові коміти в master на вже створений реліз не впливають.

## Встановлення на телефон

1. Перед оновленням зробіть резервну копію: «Більше» → «Створити резервну копію».
2. Передайте APK на телефон будь-яким способом (USB, месенджер собі, хмара) і відкрийте його.
3. Перший раз Android попросить дозволити встановлення з цього джерела; це одноразово для кожного застосунку, з якого відкриваєте APK.

| Ситуація | Що буде |
|---|---|
| Новіший `versionCode`, той самий ключ | Оновлення поверх, дані на місці |
| Той самий чи старіший `versionCode` | Android відмовить; відкат лише через видалення |
| Інший ключ | «Застосунок не встановлено»; лише видалення з втратою даних |

Хмарне резервування Android вимкнене навмисно, тому після видалення дані повертаються тільки з JSON-копії («Відновити з резервної копії»).

Збірка з Android Studio (debug) має ID `com.nackie.ownexpenses.debug` і ставиться окремим застосунком поруч із релізом, з окремими даними.

## Google Play пізніше

Цей самий ключ можна зареєструвати як ключ завантаження (upload key) у Google Play з Play App Signing. Тоді встановлена вручну версія оновлюватиметься з Play без втрати даних.
