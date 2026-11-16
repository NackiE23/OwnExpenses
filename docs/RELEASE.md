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

Без `keystore.properties` release-збірка лишається непідписаною (так працює звичайний CI) і на телефон не встановлюється.

## Збірка релізу через GitHub (основний спосіб)

Workflow `.github/workflows/release.yml` сам збирає підписаний APK і прикріплює його до релізу, тож комп'ютер з ключем для релізу не потрібен.

**Один раз: секрети репозиторію.** GitHub → Settings → Secrets and variables → Actions → New repository secret:

- `RELEASE_KEYSTORE_PASSWORD`: пароль ключа.
- `RELEASE_KEYSTORE_BASE64`: файл ключа текстом. Ця команда кладе його в буфер обміну, звідти вставте в поле секрету (на диск нічого не пишеться):

  ```powershell
  [Convert]::ToBase64String([IO.File]::ReadAllBytes("$HOME\keys\ownexpenses-release.jks")) | Set-Clipboard
  ```

Значення секретів після збереження не видно нікому, навіть власнику; workflow отримує їх лише під час збірки.

**Один раз: адреса сервера синхронізації.** GitHub → Settings → Secrets and variables → Actions → вкладка **Variables** → New repository variable: `OWNEXPENSES_SYNC_URL` = адреса сервера OwnBudget, лише корінь сайту без `/api/...`: для PythonAnywhere це `https://<логін>.pythonanywhere.com`. Це змінна, а не секрет: адреса потрапляє в APK, а токен пристрою туди не потрапляє ніколи, його вставляють у самому застосунку. Без змінної workflow попереджає, і APK виходить без синхронізації; адреса з `http://` зупиняє збірку, бо release-збірка ходить лише через HTTPS.

**Кожен реліз:**

1. Підніміть `versionCode` на 1 і `versionName` в `androidApp/build.gradle.kts`, закомітьте й запуште в master.
2. На GitHub: Releases → Draft a new release → новий тег `v` + `versionName` (наприклад `v1.3.0`) на master → Publish release.
3. Через кілька хвилин у релізі з'явиться `OwnExpenses-1.3.0.apk` (вкладка Actions → Release APK показує хід збірки).

Workflow зупиняється з помилкою, якщо тег не збігається з `versionName`, якщо секретів немає або якщо APK підписано не тим ключем (порівнює відбиток сертифіката з відомим). Для вже створеного релізу APK можна зібрати вручну: Actions → Release APK → Run workflow → тег.

Тег прив'язаний до конкретного коміту, тож нові коміти в master на вже створений реліз не впливають.

## Збірка релізу локально (запасний спосіб)

1. Підніміть `versionCode` на 1 і за потреби `versionName` в `androidApp/build.gradle.kts`.
2. Задайте адресу сервера синхронізації: змінна середовища `OWNEXPENSES_SYNC_URL` (у PowerShell `$env:OWNEXPENSES_SYNC_URL = "https://budget.example.com"`) або рядок `ownexpenses.syncUrl=https://budget.example.com` у `~/.gradle/gradle.properties` (на Windows `C:\Users\<ім'я>\.gradle\gradle.properties`: це файл користувача, не проєкту, тож адреса не потрапляє в git; Android Studio його теж читає, після зміни зробіть Gradle Sync). Змінна середовища має перевагу над рядком у файлі. Без жодної збірка виходить без синхронізації. Debug-збірка дозволяє й HTTP, тож для локального сервера підходить `http://127.0.0.1:8000` разом з `adb reverse tcp:8000 tcp:8000` (емулятор чи телефон по USB; `10.0.2.2` не годиться: на емуляторах із віртуальним Wi-Fi застосунок туди не достукується) або `http://<IP комп'ютера>:8000` (телефон у тій самій мережі; сервер тоді запускайте як `runserver 0.0.0.0:8000` і додайте IP у `DJANGO_ALLOWED_HOSTS`).
3. Зберіть (у PowerShell спершу `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"`):

   ```bash
   ./gradlew :androidApp:assembleRelease
   ```

4. Готовий файл: `androidApp/build/outputs/apk/release/androidApp-release.apk`.

## Підключення телефона до OwnBudget

1. В адмінці OwnBudget: «OwnExpenses → Пристрої → Додати», назва телефона. Сторінка один раз покаже токен.
2. У застосунку: «Більше» → «Синхронізація з сервером» → «Токен пристрою», вставити токен. Після збереження застосунок одразу надсилає всі записи. «Перевірити з'єднання» перевіряє адресу й токен, нічого не надсилаючи.
3. Загублений токен або новий телефон із відновленою резервною копією: дія «Новий токен» у тому самому пристрої в адмінці, а не новий пристрій. Сервер упізнає записи за UUID і не прийме UUID, які вже належать іншому пристрою (у застосунку це «Сервер відхилив дані» з поясненням).

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
