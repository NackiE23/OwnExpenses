# OwnExpenses

Особистий Android-застосунок для швидкого запису витрат і контролю балансу. Локальна база, без акаунтів і синхронізації, експорт у CSV. iOS буде пізніше з тієї ж кодової бази.

Власник проєкту спілкується українською, відповідай українською. Назви в коді, коментарі та commit-повідомлення англійською.

## Джерела правди

- `docs/TZ.md`: технічне завдання, усі рішення прийняті. Читай перед будь-якою роботою над функціоналом.
- `docs/design-reference/`: скриншоти стороннього застосунку як орієнтир дизайну (див. нижче). Це натхнення, а не специфікація; не копіюй брендинг і не додавай того, чого немає в ТЗ.

Якщо ТЗ і скриншоти суперечать одне одному, виграє ТЗ. Якщо щось у ТЗ неоднозначне, спитай, а не вгадуй.

## Ключові рішення (коротко)

- Один баланс, без рахунків і карток. Баланс накопичувальний: Σ надходжень − Σ витрат за весь час, рахується запитом, не зберігається полем.
- Надходження це виділена на витрати сума, а не реальні заощадження.
- Місяць починається з нуля витрат і надходжень лише як фільтр періоду; баланс не скидається.
- Категорії + підкатегорії (один рівень), опис до кожної операції. Один запис належить одній категорії.
- Історія з фільтром за періодом: день, тиждень (пн–нд), місяць (з 1-го числа), рік, увесь час, довільний діапазон.
- Швидкий ввід: тап по плитці категорії, цифри, «Додати» (2–3 дії), undo через snackbar без діалогів підтвердження.
- Суми зберігати як Long у мінорних одиницях (копійки). Дата запису це календарна дата без часу.
- Одна валюта (UAH). Без захисту входу.
- CSV: UTF-8 з BOM, роздільник «;» за замовчуванням, при «;» десяткова кома.
- Мови: uk та en через ресурси рядків, без зовнішніх сервісів локалізації.

## Чого не робити

- Не додавати дозвіл INTERNET, аналітику, краш-репортинг, хмарне резервування чи будь-які мережеві залежності.
- Не додавати рахунки, кілька валют, ліміти бюджету, PIN чи біометрію (поза обсягом).
- Не зберігати баланс окремим полем.
- Не змінювати ID пакета `com.nackie.ownexpenses` після першої публікації.

## Стек і структура

Kotlin Multiplatform + Jetpack Compose, MVVM з Flow/StateFlow, Room (SQLite, KMP), DataStore, Koin, Jetpack Glance (віджет, пізніше), Gradle Kotlin DSL з version catalog.

Мінімальна версія Android 8.0 (API 26).

```
androidApp/   тонка оболонка: Application (старт Koin), MainActivity, віджет, маніфест
sharedUi/     екрани на Compose Multiplatform (commonMain), тема, навігація, рядки uk/en (composeResources)
shared/       моделі, репозиторії, ViewModel, Room, DataStore, CSV; без Compose
iosApp/       Xcode-проєкт з'являється на етапі 5; iOS-таргети в shared і sharedUi вже є
docs/         TZ.md, design-reference/
```

Залежності: `androidApp → sharedUi → shared`. Екрани й віджет працюють лише через репозиторії зі `shared`. Бізнес-логіка (баланс, підсумки періоду, CSV) живе в `shared` і покривається модульними тестами.

Пакети (`com.nackie.ownexpenses`):

- `shared`: `domain.model` (моделі), `domain.repository` (інтерфейси репозиторіїв), `data.local` (Room: `AppDatabase`, `entity`, `dao`), `data.settings` (DataStore), `data.repository` (реалізації, сідер категорій), `di` (`sharedModule` + `platformModule` через expect/actual), `AppInitializer` (разовий старт: категорії за замовчуванням), `domain` (`TodayProvider`, `PeriodSelection`: один вибір періоду на всі екрани), `presentation` (ViewModel по пакету на екран: `home`, `quickentry` (додавання, редагування, видалення й undo), `history`, `statistics` (стовпчики й частки категорій як чисті функції), `settings`, `widget` (`WidgetSnapshotSource`); `AppViewModel` для теми; `presentation.format.MoneyFormatter`), `export` (`CsvExporter`), `backup` (`BackupService`: JSON-копія, перевірка, відновлення однією транзакцією).
- `androidApp`: `widget` (Glance-віджет `QuickEntryWidget`, `QuickEntryActivity` — прозора панель вводу поверх домашнього екрана, `WidgetUpdater` оновлює віджет при змінах даних).
- `sharedUi`: `ui` (`App`, `UiTestTags`), `ui.di` (`uiModule`), `ui.theme`, `ui.navigation`, `ui.components`, `ui.format` (дати, періоди, гроші за мовою UI), `ui.period` (перемикач і діалог періоду), по пакету на екран (`ui.home`, `ui.quickentry`, `ui.operations`, `ui.statistics`, `ui.more`). Клас ресурсів: `com.nackie.ownexpenses.ui.resources.Res`.

Панель вводу (`QuickEntryHost`) спільна для головного екрана й історії: екран і хост отримують один `QuickEntryViewModel` через `koinViewModel()` того самого власника. Записи, які ініціює користувач (додавання, редагування, видалення, undo), виконуються через `persist` у `NonCancellable`, щоб закриття екрана (панель віджета) не обірвало їх. Файли (CSV, резервні копії: збереження й відкриття через системний вибір файлу, «Поділитися» через FileProvider) — `expect`/`actual` `DocumentFiles` у `ui.more`; FileProvider оголошений у маніфесті `sharedUi/src/androidMain`.

Зовнішній ключ `categories.parent_id` має `RESTRICT`, SQLite перевіряє його для кожного рядка: перед видаленням батьківських категорій видаляй підкатегорії (див. `CategoryDao.deleteAllSubcategories`). Формат резервної копії (`BackupDocument`) версіонується: перейменування полів лише з новою версією й читачем старої.

Елементи, які чіпають UI-тести, мають тег з `UiTestTags`; тести не шукають за текстом, бо він залежить від мови.

iOS-таргети (`iosArm64`, `iosSimulatorArm64`) тримають commonMain платформно-нейтральним: Android API там не скомпілюється. Платформний код лише в `androidMain` / `iosMain`; кожен `expect` має `actual` для обох платформ. iOS-klib компілюються й на Windows (без лінкування і Xcode), це перевіряє CI. Таргет `jvm` у `shared` існує лише для тестів: Room-тести в `jvmTest` ганяють справжній SQLite без емулятора.

Схема Room експортується в `shared/schemas/` і комітиться.

Назви категорій за замовчуванням лежать у рядках `sharedUi` (`category_*`); `shared` отримує їх через `DefaultCategoryNames`. Новий `DefaultCategory` потребує рядків uk/en і гілки в `ResourceDefaultCategoryNames`.

## Етапи

0. Макети й репозиторій. 1. Ядро (база, категорії, баланс + тести). 2. Швидкий ввід. 3. Історія й CSV-експорт. 4. Статистика, віджет, резервна копія. 5. iOS.

Працюй етап за етапом, не забігай уперед без запиту. Деталі в `docs/TZ.md`.

## Дизайн-референс

Скриншоти лежать у `docs/design-reference/`. Що на кожному й що з цього брати:

- `main-categories.png`: головний екран. Загальний баланс угорі, вибір місяця зі стрілками, перемикач «Expenses / Incomes» із сумами періоду, сітка плиток категорій (іконка, назва, сума за період), плитка «Add». Беремо структуру; нижню навігацію робимо без вкладки Accounts.
- `categories-edit.png`: екран редагування категорій. Перемикач типу, «Create a new category», список категорій. Додаємо підкатегорії.
- `add-sheet-numpad.png`: нижня панель вводу. Дата зі стрілками, картки Account/Category, чипи, сума, цифрова клавіатура з кнопкою підтвердження. Картки Account у нас немає; замість неї чипи підкатегорій і поле опису.
- `operations-history.png`: історія. Групи за днями з підсумком дня, рядок операції (іконка категорії, назва, опис, сума), плаваюча кнопка «+».
- `period-picker.png`: діалог вибору періоду: довільний діапазон, весь час, день, тиждень, рік, місяць. Беремо набір періодів; опцію «місяць від дня виплати» не робимо.
- `statistics.png`: статистика. Кільцева діаграма за категоріями, список із сумами й відсотками. У нас додається стовпчикова діаграма витрат за днями; блок середніх значень не потрібен.

Вкладки нижньої навігації: Categories (головна), Operations, Statistics, More (налаштування, експорт, резервна копія). Вкладки Accounts немає.

## Команди

JDK 21 (Gradle сам підтягує Zulu 21 за `gradle/gradle-daemon-jvm.properties`; локально можна вказати `JAVA_HOME` на JBR з Android Studio).

```
./gradlew :androidApp:assembleDebug        # debug APK (applicationId з суфіксом .debug)
./gradlew :shared:jvmTest                  # модульні тести shared, включно з Room на справжньому SQLite
./gradlew :shared:testAndroidHostTest      # commonTest на Android-хості
./gradlew spotlessCheck                    # ktlint + Compose rules; spotlessApply виправляє
./gradlew :androidApp:lintDebug            # Android Lint, з перевіркою залежних модулів
./gradlew :shared:compileKotlinIosArm64 :sharedUi:compileKotlinIosArm64   # перевірка, що iOS-код компілюється
./gradlew :androidApp:connectedDebugAndroidTest   # UI-тест швидкого вводу, потрібен емулятор (AVD Medium_Phone_API_37.0)
```

CI: `.github/workflows/ci.yml` запускає все це, крім UI-тесту на емуляторі, плюс release-збірку на кожен push у master і PR. Після `connectedDebugAndroidTest` Gradle видаляє застосунок з емулятора.

## Робота над кодом

- Перед змінами схеми Room додавай міграцію й тест міграції.
- Кожну зміну логіки балансу, періодів чи CSV супроводжуй модульним тестом.
- Тримай UI-стан у ViewModel, у Composable лише відображення й події.
- Перевіряй, що запис витрати лишається у 2–3 дії; не додавай обов'язкових полів і підтверджень у цей сценарій.
