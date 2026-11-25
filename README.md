# SmartGrid — практична робота №4

Варіант 8: система розподілу електроенергії (Smart Grid). Kotlin + Jetpack Compose.
Застосунок з практичних робіт №1–3, доповнений заняттями 13–16: Flow і корутини,
динамічні списки LazyColumn, фільтрація/сортування/пошук, мапа Google Maps і GPS.

## Що нового порівняно з ПР3

- **Моніторинг мережі в реальному часі.** «Холодний» потік опитує сервер кожні 2 с, у ViewModel він
  стає «гарячим» (`shareIn`). Оператори: `filter` (відкидає збійні показники), `retryWhen`
  (повторне підключення), `catch`, `combine` (факт + прогнозна модель), `scan` (історія для графіка),
  `distinctUntilChanged` (попередження про перевантаження). Графік на Canvas.
- **Енергооб'єкти.** LazyColumn з картками, пошук за назвою (`debounce`), фільтри за типом (кілька),
  станом і потужністю, сортування за 6 критеріями в обидва боки, вибір картки з додатковими
  параметрами, перехід на екран деталей (`object/{id}`).
- **Мапа об'єктів.** Маркери з кольором за станом, InfoWindow, позиція користувача
  (FusedLocationProviderClient + `callbackFlow`), найближчий об'єкт, двостороння синхронізація
  списку і мапи, обробка відсутності дозволу та вимкненої геолокації.

## Ключ Google Maps

Мапі потрібен ключ Maps SDK for Android (Google Cloud Console → APIs & Services → Credentials).
Додайте рядок у файл `local.properties` у корені проєкту (файл не потрапляє в GitHub):

```
MAPS_API_KEY=ваш_ключ
```

Без ключа застосунок працює, але плитки мапи не завантажуються. Емулятор має бути з Google Play,
позицію Києва можна задати в Extended Controls → Location (напр. 50.45, 30.52).

## Тестові облікові записи

- оператор — `operator@grid.ua` / `grid2026`
- адміністратор — `admin@grid.ua` / `admin2026`

## Структура

```
app/src/main/java/ua/kpi/smartgrid/
├── navigation/        Screen, AppNavHost
├── data/              local (Room, DataStore), remote (Retrofit, MockServerInterceptor), auth,
│                      GridRepository (+ потоки telemetry() і forecastFlow()), AuthRepository
├── network/           NetworkChecker, ApiCaller, ErrorHandler, ErrorLogger
├── location/          LocationProvider (GPS як Flow)
├── domain/            Calculator, Validator, LoadForecast, GridLabels, ObjectFilter
├── viewmodel/         Auth, Monitoring (Flow), Modes, Objects, Measurement, Admin
└── ui/
    ├── screens/       Start, Login, Register, MainMenu, Monitoring, Objects, ObjectDetail, Map,
    │                  Modes, Calculator, Measurements, MeasurementForm, Admin
    └── components/    ObjectCard, ObjectFiltersPanel, LoadChart, UserLocationEffect, …
```

Запуск: Android Studio → File → Open → папка проєкту → Gradle Sync → Run.
