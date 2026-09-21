# BrainRotNotif — дизайн

Дата: 2026-09-21
Статус: утверждён к реализации

## 1. Что это

Android-приложение, которое следит за тем, сколько времени пользователь непрерывно
сидит в «залипательных» приложениях, и показывает поверх них баннер, когда
индивидуальный лимит приложения исчерпан.

Приложение собирается для личного использования, не для Google Play.

## 2. Требования

1. Трекинг времени использования установленных приложений.
2. Экран со списком отслеживаемых «соцсетей».
3. При первом запуске список автоматически заполняется популярными
   brain-rot-приложениями, найденными среди установленных (YouTube, VK,
   Instagram, TikTok и т.п.).
4. Пользователь может добавить в список любое установленное приложение.
5. Оверлей-баннер поверх отслеживаемого приложения, когда лимит исчерпан.
6. В настройках приложения для каждого отслеживаемого приложения задаётся:
   индивидуальный лимит в минутах и переключатель «показывать оверлей».
7. Экран статистики: отслеживаемые приложения и время за сегодня.

## 3. Платформа и сборка

| Параметр | Значение |
|---|---|
| Язык | Java (без Kotlin) |
| minSdk | 30 (Android 11) |
| targetSdk / compileSdk | 36 (Android 16) |
| AGP | 9.3.1 |
| Gradle | 9.5.0 |
| JDK | 21 |
| Build tools | 36.0.0 |
| DSL | Kotlin DSL + version catalog |
| UI | XML-вёрстка, Material Components, ViewBinding |
| Пакет | `com.brainrotnotif` |

Особенности AGP 9, которые влияют на код:

- R-класс не `final` → `switch` по `R.id.*` не компилируется, только `if`.
- Встроенный Kotlin включён по умолчанию. Для Java-only проекта он отключается
  флагом `android.builtInKotlin=false` в `gradle.properties`. Флаг добавляется
  только если без него сборка падает — проверяется первой же сборкой.

## 4. Ограничения платформы, определившие архитектуру

Подробности с источниками — в разделе 11.

### 4.1 Определение текущего приложения

Единственный доступный обычному приложению способ — `UsageStatsManager.queryEvents()`
с разрешением `PACKAGE_USAGE_STATS`.

Отброшены:
- `ActivityManager.getRunningAppProcesses()` — с Android 10 возвращает только
  собственные процессы приложения.
- `UsageStatsManager.registerAppUsageObserver()` — требует `OBSERVE_APP_USAGE`
  (`signature|privileged`).
- `ActivityManager.addOnUidImportanceListener()` — документация неоднозначна
  относительно доступности обычному приложению; не закладываемся.
- `AccessibilityService` — на Android 13+ для приложений не из стора упирается
  в Restricted Settings; выигрыш не оправдывает сложность.

### 4.2 Поллинг не обязан быть частым

`queryEvents()` отдаёт события с точными системными таймстампами. Период опроса
влияет только на задержку реакции, не на точность измерения времени. Отсюда
адаптивный тик (раздел 6.3).

### 4.3 Системный агрегат не используется

`queryUsageStats().totalTimeInForeground` обновляется пачками и содержит
дублирующиеся записи на приложение. Итоги за день считаются самостоятельно из
того же потока событий, что и сессии. Один источник данных на трекинг и на
статистику.

### 4.4 Тип foreground service

`specialUse`. Причины:
- Android 14+ требует явный тип, иначе `MissingForegroundServiceTypeException`.
- Android 15 ограничил `dataSync` и `mediaProcessing` шестью часами за 24 часа
  с принудительным `onTimeout()`.
- `specialUse` лимита времени не имеет и не входит в список типов, запрещённых
  к старту из `BOOT_COMPLETED` (запрещены `dataSync`, `camera`, `mediaPlayback`,
  `phoneCall`, `mediaProjection`, `microphone`).

Требует `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE">`
в манифесте.

Непреодолимо: на Android 13+ пользователь может остановить сервис из системного
Task Manager, на Android 14+ — смахнуть его уведомление (сервис продолжает работу).

### 4.5 Форма оверлея

Android 12 блокирует касания, проходящие сквозь окно `TYPE_APPLICATION_OVERLAY`
с `FLAG_NOT_TOUCHABLE`, если непрозрачность выше 0.8.

Баннер поэтому делается **touchable** (у него своя кнопка), `FLAG_NOT_FOCUSABLE`,
`WRAP_CONTENT` по высоте, `gravity=TOP`. Касания внутри баннера принадлежат ему,
касания вне его границ штатно уходят приложению снизу. Сценарий pass-through
не возникает.

Остаточные ограничения: приложение с `HIDE_OVERLAY_WINDOWS` (например, банковское)
может скрыть баннер; оверлей не рисуется поверх системных диалогов разрешений.

### 4.6 Видимость пакетов

`QUERY_ALL_PACKAGES` не используется. Вместо него `<queries>` с интентом
`MAIN`/`LAUNCHER` — даёт ровно те приложения, которые показываются в списке.

### 4.7 Edge-to-edge и predictive back

targetSdk 36 принудительно включает edge-to-edge без возможности отказа;
`statusBarColor` и `navigationBarColor` не действуют. Инсеты обрабатываются
с самого начала.

`onBackPressed()` при targetSdk 36 не вызывается — только `OnBackPressedDispatcher`.

### 4.8 Прочее

- `PACKAGE_USAGE_STATS` в манифесте вызывает lint `ProtectedPermissions` →
  `tools:ignore="ProtectedPermissions"`.
- Выданность usage access проверяется через
  `AppOpsManager.unsafeCheckOpNoThrow(OPSTR_GET_USAGE_STATS, uid, pkg)`,
  не через `checkSelfPermission`.
- `Settings.ACTION_USAGE_ACCESS_SETTINGS` существует не на всех прошивках →
  вызов в try/catch с фоллбэком на `Settings.ACTION_SETTINGS`.
- На Android 13+ «Показ поверх других приложений» входит в Restricted Settings
  для приложений, установленных не из стора. При `adb install` ограничение
  обычно не срабатывает, при установке APK из файлового менеджера —
  срабатывает, и тумблер серый до «Разрешить ограниченные настройки» в App info.
- Xiaomi/HyperOS и Huawei отключают автозапуск сторонних приложений по умолчанию
  и сбрасывают настройку после OTA. `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
  на стоковом Android помогает, у вендоров гарантий нет.
- Гибернация неиспользуемых приложений (Android 11+) через несколько месяцев
  простоя делает force-stop и сбрасывает разрешения. Риск низкий, отдельного
  кода не требует.

## 5. Семантика сессии и лимита

Определения, на которых держится вся логика:

- **Сессия** принадлежит пакету P. Начинается, когда P выходит на передний план
  и живой сессии для P нет.
- Пока P на переднем плане, длительность сессии растёт.
- Когда P уходит с переднего плана, сессия **приостанавливается**, а не
  заканчивается. Длительность не растёт.
- Если P возвращается на передний план в течение **grace-периода (60 с)** после
  ухода, сессия возобновляется с накопленной длительностью.
- Если grace-период истёк, сессия заканчивается и забывается.
- **Длительность сессии = суммарное время на переднем плане внутри сессии.**
  Паузы до 60 с склеивают сессию, но сами в лимит не засчитываются.

Выключение экрана и показ keyguard трактуются как уход с переднего плана.

Одновременно может существовать несколько приостановленных сессий (пользователь
скачет между YouTube и Instagram) — у каждого пакета своя.

**Срабатывание баннера:** при достижении длительностью сессии отметки
`limit`, затем `2×limit`, `3×limit` и так далее, пока сессия жива. Счётчик
сработавших отметок привязан к сессии и обнуляется вместе с ней.

## 6. Архитектура

### 6.1 Слои

```
MonitorService (FGS, type=specialUse)
  -> UsageTracker        состояние: курсор, сессии, итоги за день
       -> UsageEventReader   интерфейс над UsageStatsManager
  -> OverlayBanner       WindowManager, TYPE_APPLICATION_OVERLAY
  -> ScreenStateReceiver ACTION_SCREEN_ON / ACTION_SCREEN_OFF
TrackedAppsStore  SharedPreferences + JSON
BootReceiver      ACTION_BOOT_COMPLETED, ACTION_MY_PACKAGE_REPLACED
UI                MainActivity + 3 фрагмента + AppPickerActivity
```

Поток при срабатывании:

```
tick -> UsageTracker.advanceTo(now) -> getForegroundPackage()
     -> TrackedAppsStore.get(pkg) -> лимит достигнут?
     -> OverlayBanner.show(pkg, elapsed)
```

### 6.2 UsageTracker — ядро

Единственный компонент с нетривиальной логикой и единственный, покрываемый
юнит-тестами.

Состояние:
- `long cursorMs` — таймстамп последнего обработанного события
- `long dayStartMs` — начало текущих суток
- `String currentPkg`, `long currentPkgSinceMs` — приложение на переднем плане
- `Map<String, Session> sessions` — живые и приостановленные сессии
- `Map<String, Long> todayTotals` — накопленное время за сегодня

API:
- `void advanceTo(long nowMs)` — читает новые события и сворачивает их в состояние
- `String getForegroundPackage()`
- `long getSessionDurationMs(String pkg)`
- `long getSessionStartMs(String pkg)` — идентификатор сессии, 0 если сессии нет
- `Map<String, Long> getTodayTotals()` — с учётом незавершённого отрезка

Нормализация событий в `UsageEventReader`:

| Событие | Трактовка |
|---|---|
| `ACTIVITY_RESUMED` | FOREGROUND(pkg) |
| `ACTIVITY_PAUSED`, `ACTIVITY_STOPPED` | BACKGROUND(pkg) |
| `SCREEN_NON_INTERACTIVE`, `KEYGUARD_SHOWN`, `DEVICE_SHUTDOWN` | BACKGROUND(всё) |

События сортируются по таймстампу.

**Инициализация:** `cursorMs = startOfToday()`, полная перемотка событий с начала
суток. Даёт и итоги за день, и текущую сессию. Поэтому перезапуск сервиса
не теряет состояние.

**Смена суток:** при пересечении полуночи `todayTotals` очищается,
`dayStartMs` пересчитывается, время текущего приложения начинает считаться
с полуночи. Сессии смену суток переживают.

**Инкрементальность:** после инициализации каждый `advanceTo` читает только
события с `cursorMs`. Полная перемотка — только при старте и при смене суток.

**Устойчивость к пропущенным тикам.** Всё состояние выводится из таймстампов
событий, а не из момента вызова `advanceTo`. Поэтому выключенный экран, убитый
системой сервис или пауза между тиками не искажают расчёт: при следующем вызове
события будут прочитаны целиком и свёрнуты с правильными интервалами. Частный
случай: экран выключен на 5 минут внутри YouTube — при возврате `advanceTo`
увидит разрыв больше grace-периода и завершит сессию, хотя тиков в это время
не было.

### 6.3 MonitorService — тик

Работает на `HandlerThread`. Алгоритм тика:

1. Экран не интерактивен → баннер скрыть, тик не планировать. Возобновление —
   по `ACTION_SCREEN_ON`.
2. `tracker.advanceTo(now)`.
3. `pkg = tracker.getForegroundPackage()`.
4. Если `pkg` отслеживается и оверлей для него включён:
   - `elapsed = tracker.getSessionDurationMs(pkg)`
   - `threshold = (firedCount + 1) * limit`
   - `elapsed >= threshold` → показать баннер, `firedCount++`
   - следующая задержка = `clamp(threshold - elapsed, 250 мс, 5 с)`
5. Иначе следующая задержка = 5 с.
6. Если баннер показан не для текущего `pkg` — скрыть.
7. Обновить текст уведомления, только если он изменился.

Счётчик сработавших отметок `firedCount` принадлежит `MonitorService`, а не
`UsageTracker`: трекер не знает про баннеры. Ключ — `pkg + "@" + getSessionStartMs(pkg)`.
Когда сессия заканчивается и начинается новая, ключ меняется, и отсчёт отметок
начинается заново. Записи с мёртвыми ключами вычищаются на тике.

Период 5 с — константа. Grace 60 с — константа. В настройки не выносятся.

### 6.4 OverlayBanner

```java
type    = TYPE_APPLICATION_OVERLAY
flags   = FLAG_NOT_FOCUSABLE
width   = MATCH_PARENT
height  = WRAP_CONTENT
gravity = TOP
format  = TRANSLUCENT
layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
```

Отступ сверху под статус-бар — через `setOnApplyWindowInsetsListener` на корне
баннера, не через `FLAG_LAYOUT_NO_LIMITS`.

Содержимое: иконка приложения, текст «Ты в {приложение} уже {N} мин», кнопка «Ок».
Кнопка убирает баннер. Баннер висит до нажатия.

Перед `addView` проверяется `Settings.canDrawOverlays()`; `addView` и `removeView`
обёрнуты в try/catch на случай отзыва разрешения на лету.

### 6.5 TrackedAppsStore

`SharedPreferences` + JSON-массив. Запись:

```json
{"pkg": "com.google.android.youtube", "enabled": true, "limitMinutes": 15}
```

Лимит по умолчанию для автоопределённых приложений — 15 минут, `enabled = true`.

`enabled` управляет **только оверлеем**. Время считается для всех приложений из
списка независимо от этого флага, поэтому в статистике приложение с выключенным
оверлеем всё равно видно.

Отдельно хранится глобальный флаг `monitoringEnabled` (переключатель на вкладке
«Настройки») и `firstRunDone`.

Сервис подписан на `OnSharedPreferenceChangeListener` и обновляет кэш при
изменении настроек из UI.

**Жизненный цикл сервиса.** Запускается из `MainActivity.onStart()` и из
`BootReceiver`, в обоих случаях при условии `monitoringEnabled == true` и
наличии доступа к статистике использования. Останавливается переключателем
в настройках. Повторный `startForegroundService` на уже работающем сервисе
безвреден.

### 6.6 Автоопределение соцсетей

`BrainRotCatalog` содержит проверенные имена пакетов:

```
com.google.android.youtube      YouTube
com.instagram.android           Instagram
com.zhiliaoapp.musically        TikTok
com.ss.android.ugc.trill        TikTok (альт.)
com.vkontakte.android           ВКонтакте
com.twitter.android             X
com.reddit.frontpage            Reddit
com.facebook.katana             Facebook
com.snapchat.android            Snapchat
com.pinterest                   Pinterest
org.telegram.messenger          Telegram
```

Плюс список префиксов пакетов для приложений, чьи точные имена не подтверждены
(ВК Видео, Дзен, Rutube, Likee): `com.vk.`, `ru.vk.`, `ru.zen.`, `ru.rutube.`,
`video.like`. Сопоставление только по префиксу пакета, не по подстроке — чтобы
не ловить ложные совпадения.

Имена пакетов сверяются с выводом `adb shell pm list packages` на подключённом
устройстве или эмуляторе при реализации. Всё, что не подтвердилось, из каталога
убирается — такие приложения добавляются вручную через пикер.

При первом запуске: пересечение установленных launchable-приложений с каталогом
→ записи в `TrackedAppsStore`. Повторный прогон — по кнопке «Пересканировать»
в настройках, добавляет только новые приложения и не трогает существующие записи.

## 7. UI

Три вкладки в `BottomNavigationView`, фрагменты переключаются вручную,
без Navigation Component.

**Приложения.** `RecyclerView` отслеживаемых приложений: иконка, название,
время за сегодня, `Switch` «оверлей». Клик по строке → диалог с `NumberPicker`
(1–240 минут). Долгий клик → удалить из списка. FAB «+» → `AppPickerActivity`.

**Статистика.** `RecyclerView` отслеживаемых приложений, отсортированный по
времени за сегодня по убыванию, плюс суммарное время сверху. Данные —
свежий экземпляр `UsageTracker` с перемоткой от начала суток на фоновом потоке.
Без графиков.

**Настройки.** Переключатель «Мониторинг включён» (запуск/остановка сервиса),
кнопка «Пересканировать соцсети» и карточки разрешений со статусом и кнопкой
действия:

1. Доступ к статистике использования — `ACTION_USAGE_ACCESS_SETTINGS`
2. Поверх других приложений — `ACTION_MANAGE_OVERLAY_PERMISSION`
3. Уведомления — рантайм-запрос `POST_NOTIFICATIONS`
4. Оптимизация батареи — `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
5. Автозапуск у вендора — информационная карточка со ссылкой на инструкцию

Отдельного экрана онбординга нет. Если при запуске нет доступа к статистике
использования или разрешения на оверлей, `MainActivity` открывается сразу на
вкладке «Настройки» с поясняющей плашкой сверху.

**AppPickerActivity.** `RecyclerView` всех launchable-приложений с поиском по
названию. Клик добавляет приложение в список с лимитом 15 минут.

## 8. Уведомление сервиса

Канал `monitor`, `IMPORTANCE_LOW`, ongoing. Текст: название и время текущего
отслеживаемого приложения, иначе количество отслеживаемых приложений.
Нажатие открывает `MainActivity` напрямую через `PendingIntent` с
`FLAG_IMMUTABLE` — без trampoline-активити.

## 9. Манифест

```xml
<uses-permission android:name="android.permission.PACKAGE_USAGE_STATS"
    tools:ignore="ProtectedPermissions" />
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />

<queries>
  <intent>
    <action android:name="android.intent.action.MAIN" />
    <category android:name="android.intent.category.LAUNCHER" />
  </intent>
</queries>

<service
    android:name=".monitor.MonitorService"
    android:foregroundServiceType="specialUse"
    android:exported="false">
  <property
      android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
      android:value="Continuous foreground-app monitoring to enforce user-configured
                     per-app screen time limits" />
</service>
```

## 10. Тестирование

**Юнит-тесты (JVM, без устройства)** — только `UsageTracker`, через подставной
`UsageEventReader` с синтетическим потоком событий:

1. Одно приложение на переднем плане N минут → длительность сессии = N.
2. Уход на 30 с и возврат → сессия продолжается, 30 с в лимит не засчитаны.
3. Уход на 90 с и возврат → новая сессия с нуля.
4. Выключение экрана трактуется как уход.
5. Чередование двух приложений → у каждого своя сессия.
6. Итоги за день суммируют все отрезки переднего плана.
7. Пересечение полуночи обнуляет итоги, но не сессию.
8. Инкрементальный `advanceTo` даёт тот же результат, что полная перемотка.

**Проверка на устройстве/эмуляторе** — всё остальное: выдача разрешений,
появление и снятие баннера, поведение при смене приложения, автозапуск после
перезагрузки, работа при выключении экрана.

## 11. Источники

- https://developer.android.com/about/versions/14/changes/fgs-types-required
- https://developer.android.com/about/versions/15/behavior-changes-15
- https://developer.android.com/about/versions/16/behavior-changes-16
- https://developer.android.com/about/versions/16/behavior-changes-all
- https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- https://developer.android.com/develop/background-work/services/fgs/changes
- https://medium.com/androiddevelopers/untrusted-touch-events-2c0e0b9c374c
- https://developer.android.com/about/versions/12/behavior-changes-all
- https://developer.android.com/training/package-visibility
- https://developer.android.com/reference/android/app/usage/UsageStatsManager
- https://developer.android.com/topic/performance/app-hibernation
- https://bayton.org/android/android-13-restricted-permissions/
- https://cryptax.medium.com/testing-restricted-settings-of-android-13-on-an-emulator-72692da7bca4
- https://dontkillmyapp.com/xiaomi
- https://developer.android.com/build/releases/agp-9-0-0-release-notes

## 12. Осознанно не делается

- Своя база данных истории использования. Глубина статистики ограничена
  системным хранением событий.
- Графики в статистике.
- Блокирующий полноэкранный оверлей.
- Вынос grace-периода и периода опроса в настройки.
- Отдельный экран онбординга.
