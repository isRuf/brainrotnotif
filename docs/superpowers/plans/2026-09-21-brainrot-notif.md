# BrainRotNotif Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Android-приложение, которое считает непрерывные сессии в «залипательных» приложениях и показывает поверх них баннер, когда индивидуальный лимит приложения исчерпан.

**Architecture:** Foreground service типа `specialUse` с адаптивным тиком читает события `UsageStatsManager.queryEvents()` и сворачивает их в состояние: текущее приложение, живые сессии, итоги за день. Всё состояние выводится из таймстампов событий, поэтому пропущенные тики ничего не ломают. Баннер — touchable-окно `TYPE_APPLICATION_OVERLAY`. Настройки лежат в `SharedPreferences`.

**Tech Stack:** Java 17, AGP 9.3.1, Gradle 9.5.0, minSdk 30, targetSdk 36, AndroidX AppCompat + Material Components + RecyclerView, ViewBinding, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-09-21-brainrot-notif-design.md`

## Global Constraints

- Язык — Java. Kotlin не использовать.
- `minSdk = 30`, `targetSdk = 36`, `compileSdk = 36`, `applicationId = "com.brainrotnotif"`, namespace тот же.
- AGP `9.3.1`, Gradle `9.5.0`, JDK 21, build-tools `36.0.0`.
- **R-класс в AGP 9 не `final`:** `switch` по `R.id.*` не компилируется. Ветвление по id — только через `if`/`else if`.
- **targetSdk 36 → edge-to-edge принудительно.** Корневые layout'ы активити обрабатывают инсеты через `ViewCompat.setOnApplyWindowInsetsListener`. `android:statusBarColor` / `navigationBarColor` не использовать — не действуют.
- **targetSdk 36 → `onBackPressed()` не вызывается.** Только `OnBackPressedDispatcher`.
- `QUERY_ALL_PACKAGES` не использовать. Видимость пакетов — через `<queries>` с интентом `MAIN`/`LAUNCHER`.
- Тип foreground service — только `specialUse`. Не `dataSync` (лимит 6 ч/24 ч на Android 15+).
- Оверлей-окно всегда **touchable**. `FLAG_NOT_TOUCHABLE` не ставить — Android 12+ блокирует сквозные касания при непрозрачности выше 0.8.
- Константы поведения: grace-период сессии `60_000` мс, максимальный период тика `5_000` мс, минимальный `250` мс, лимит по умолчанию `15` минут. В настройки не выносятся.
- Проверка доступа к статистике использования — только через `AppOpsManager.unsafeCheckOpNoThrow(OPSTR_GET_USAGE_STATS, ...)`, не `checkSelfPermission`.
- Все вызовы `startActivity` на системные экраны настроек — в `try/catch (ActivityNotFoundException)`.
- Юнит-тесты — чистый JVM (`app/src/test/java`), без Robolectric и без Android-зависимостей в тестируемом коде.
- Путь к закэшированному Gradle для генерации wrapper'а:
  `/home/skipper/.gradle/wrapper/dists/gradle-9.5.0-bin/bvnork1r7n8i6kp5cnkibsc9q/gradle-9.5.0/bin/gradle`
- Android SDK: `/home/skipper/Android/Sdk`.

## Отклонение от спеки

Спека в разделе 6.5 говорит «SharedPreferences + JSON». План использует построчный
текстовый формат вместо JSON: `org.json` в JVM-юнит-тестах подменяется заглушкой из
`android.jar`, которая бросает исключение на каждый вызов, и обход этого требует
лишней тестовой зависимости. Формат — одна запись на строку,
`packageName\tenabled(0|1)\tlimitMinutes`. Имена пакетов не содержат табов и
переводов строки, так что формат однозначен.

## Карта файлов

```
app/src/main/java/com/brainrotnotif/
  data/AppEvent.java              модель нормализованного события
  data/UsageEventReader.java      интерфейс чтения событий, контракт [from, to)
  data/SystemUsageEventReader.java   реализация над UsageStatsManager
  data/UsageTracker.java          ядро: сессии, итоги за день
  data/TrackedApp.java            модель записи настроек приложения
  data/TrackedAppsCodec.java      сериализация списка в строку и обратно
  data/TrackedAppsStore.java      SharedPreferences-обёртка
  data/BrainRotCatalog.java       каталог известных пакетов + автоопределение
  data/InstalledApps.java         список launchable-приложений, иконки, названия
  monitor/MonitorService.java     foreground service, тик, уведомление
  monitor/OverlayBanner.java      окно оверлея
  monitor/ScreenStateReceiver.java  ACTION_SCREEN_ON / OFF
  monitor/BootReceiver.java       автозапуск
  util/Permissions.java           проверки и переходы в системные настройки
  util/Formats.java               форматирование длительности
  ui/MainActivity.java            хост вкладок
  ui/AppsFragment.java            вкладка «Приложения»
  ui/TrackedAppsAdapter.java
  ui/LimitDialog.java
  ui/StatsFragment.java           вкладка «Статистика»
  ui/StatsAdapter.java
  ui/SettingsFragment.java        вкладка «Настройки»
  ui/AppPickerActivity.java       добавление приложения
  ui/AppPickerAdapter.java
app/src/test/java/com/brainrotnotif/
  data/FakeUsageEventReader.java
  data/UsageTrackerTest.java
  data/TrackedAppsCodecTest.java
  data/BrainRotCatalogTest.java
```

---

### Task 1: Каркас Gradle и пустое запускающееся приложение

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `local.properties`
- Create: `app/build.gradle.kts`, `app/proguard-rules.pro`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/brainrotnotif/ui/MainActivity.java`
- Create: `app/src/main/res/layout/activity_main.xml`, `app/src/main/res/values/strings.xml`, `app/src/main/res/values/themes.xml`
- Generated: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`

**Interfaces:**
- Consumes: ничего
- Produces: собираемый модуль `:app`, класс `com.brainrotnotif.ui.MainActivity`

- [ ] **Step 1: Создать `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "BrainRotNotif"
include(":app")
```

- [ ] **Step 2: Создать `gradle/libs.versions.toml`**

```toml
[versions]
agp = "9.3.1"
appcompat = "1.7.1"
material = "1.12.0"
recyclerview = "1.3.2"
junit = "4.13.2"

[libraries]
appcompat = { module = "androidx.appcompat:appcompat", version.ref = "appcompat" }
material = { module = "com.google.android.material:material", version.ref = "material" }
recyclerview = { module = "androidx.recyclerview:recyclerview", version.ref = "recyclerview" }
junit = { module = "junit:junit", version.ref = "junit" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
```

- [ ] **Step 3: Создать корневой `build.gradle.kts`, `gradle.properties`, `local.properties`**

`build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
}
```

`gradle.properties`:

```properties
org.gradle.jvmargs=-Xmx2048m
org.gradle.caching=true
android.useAndroidX=true
android.nonTransitiveRClass=true
```

`local.properties` (не коммитится, уже в `.gitignore`):

```properties
sdk.dir=/home/skipper/Android/Sdk
```

- [ ] **Step 4: Создать `app/build.gradle.kts` и пустой `app/proguard-rules.pro`**

```kotlin
plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.brainrotnotif"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.brainrotnotif"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.recyclerview)
    testImplementation(libs.junit)
}
```

`proguard-rules.pro` создать пустым — AGP 9 падает, если файл, указанный в
`proguardFiles`, отсутствует.

- [ ] **Step 5: Создать манифест, тему, строки, layout и `MainActivity`**

`app/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:allowBackup="true"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.BrainRotNotif">

        <activity
            android:name=".ui.MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

`app/src/main/res/values/strings.xml`:

```xml
<resources>
    <string name="app_name">BrainRotNotif</string>
</resources>
```

`app/src/main/res/values/themes.xml`:

```xml
<resources>
    <style name="Theme.BrainRotNotif" parent="Theme.Material3.DayNight.NoActionBar" />
</resources>
```

`app/src/main/res/layout/activity_main.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/root"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />
```

`app/src/main/java/com/brainrotnotif/ui/MainActivity.java`:

```java
package com.brainrotnotif.ui;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.brainrotnotif.R;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }
}
```

- [ ] **Step 6: Сгенерировать Gradle wrapper**

Run:

```bash
cd /home/skipper/projects/brainrotnotif && \
/home/skipper/.gradle/wrapper/dists/gradle-9.5.0-bin/bvnork1r7n8i6kp5cnkibsc9q/gradle-9.5.0/bin/gradle \
  wrapper --gradle-version 9.5.0 --distribution-type bin
```

Expected: созданы `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`,
`gradle/wrapper/gradle-wrapper.properties`.

- [ ] **Step 7: Собрать приложение**

Run: `cd /home/skipper/projects/brainrotnotif && ./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`, файл `app/build/outputs/apk/debug/app-debug.apk`.

Если сборка падает с требованием Kotlin Gradle Plugin — добавить в
`gradle.properties` строку `android.builtInKotlin=false` и собрать снова.
Если не падает, флаг не добавлять.

- [ ] **Step 8: Коммит**

```bash
git add -A
git commit -m "build: gradle skeleton and empty launchable app"
```

---

### Task 2: UsageTracker — текущее приложение и итоги за день

**Files:**
- Create: `app/src/main/java/com/brainrotnotif/data/AppEvent.java`
- Create: `app/src/main/java/com/brainrotnotif/data/UsageEventReader.java`
- Create: `app/src/main/java/com/brainrotnotif/data/UsageTracker.java`
- Test: `app/src/test/java/com/brainrotnotif/data/FakeUsageEventReader.java`
- Test: `app/src/test/java/com/brainrotnotif/data/UsageTrackerTest.java`

**Interfaces:**
- Consumes: ничего
- Produces:
  - `AppEvent(String packageName, long timestamp, int type)`, константы `AppEvent.FOREGROUND = 1`, `AppEvent.BACKGROUND = 2`; `packageName == null` в `BACKGROUND` означает «всё ушло с переднего плана»
  - `interface UsageEventReader { List<AppEvent> read(long fromMs, long toMs); }` — полуинтервал `[fromMs, toMs)`
  - `UsageTracker(UsageEventReader reader, ZoneId zone, long graceMs)`
  - `void advanceTo(long nowMs)`
  - `String getForegroundPackage()` — `null`, если ничего
  - `Map<String, Long> getTodayTotals()`

- [ ] **Step 1: Написать падающие тесты**

`app/src/test/java/com/brainrotnotif/data/FakeUsageEventReader.java`:

```java
package com.brainrotnotif.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FakeUsageEventReader implements UsageEventReader {

    private final List<AppEvent> all = new ArrayList<>();
    public int readCalls = 0;

    public FakeUsageEventReader fg(String pkg, long ts) {
        all.add(new AppEvent(pkg, ts, AppEvent.FOREGROUND));
        return this;
    }

    public FakeUsageEventReader bg(String pkg, long ts) {
        all.add(new AppEvent(pkg, ts, AppEvent.BACKGROUND));
        return this;
    }

    public FakeUsageEventReader screenOff(long ts) {
        all.add(new AppEvent(null, ts, AppEvent.BACKGROUND));
        return this;
    }

    @Override
    public List<AppEvent> read(long fromMs, long toMs) {
        readCalls++;
        List<AppEvent> out = new ArrayList<>();
        for (AppEvent e : all) {
            if (e.timestamp >= fromMs && e.timestamp < toMs) {
                out.add(e);
            }
        }
        Collections.sort(out, (a, b) -> Long.compare(a.timestamp, b.timestamp));
        return out;
    }
}
```

`app/src/test/java/com/brainrotnotif/data/UsageTrackerTest.java`:

```java
package com.brainrotnotif.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.time.ZoneId;
import java.util.Map;

public class UsageTrackerTest {

    static final ZoneId UTC = ZoneId.of("UTC");
    static final long DAY = 86_400_000L;
    static final long MIN = 60_000L;
    static final long GRACE = 60_000L;

    /** Полдень условных суток. */
    static long t(long offsetMs) {
        return 1_000 * DAY + 12 * 60 * MIN + offsetMs;
    }

    static UsageTracker tracker(FakeUsageEventReader reader) {
        return new UsageTracker(reader, UTC, GRACE);
    }

    @Test
    public void reportsForegroundPackage() {
        FakeUsageEventReader reader = new FakeUsageEventReader().fg("yt", t(0));
        UsageTracker tracker = tracker(reader);

        tracker.advanceTo(t(5 * MIN));

        assertEquals("yt", tracker.getForegroundPackage());
    }

    @Test
    public void reportsNothingAfterBackground() {
        FakeUsageEventReader reader = new FakeUsageEventReader()
                .fg("yt", t(0))
                .bg("yt", t(MIN));
        UsageTracker tracker = tracker(reader);

        tracker.advanceTo(t(5 * MIN));

        assertNull(tracker.getForegroundPackage());
    }

    @Test
    public void totalsIncludeRunningSegment() {
        FakeUsageEventReader reader = new FakeUsageEventReader().fg("yt", t(0));
        UsageTracker tracker = tracker(reader);

        tracker.advanceTo(t(5 * MIN));

        assertEquals(Long.valueOf(5 * MIN), tracker.getTodayTotals().get("yt"));
    }

    @Test
    public void totalsSumSeparateSegments() {
        FakeUsageEventReader reader = new FakeUsageEventReader()
                .fg("yt", t(0))
                .bg("yt", t(3 * MIN))
                .fg("yt", t(10 * MIN))
                .bg("yt", t(14 * MIN));
        UsageTracker tracker = tracker(reader);

        tracker.advanceTo(t(20 * MIN));

        assertEquals(Long.valueOf(7 * MIN), tracker.getTodayTotals().get("yt"));
    }

    @Test
    public void totalsTrackSeveralPackages() {
        FakeUsageEventReader reader = new FakeUsageEventReader()
                .fg("yt", t(0))
                .fg("ig", t(2 * MIN))
                .bg("ig", t(5 * MIN));
        UsageTracker tracker = tracker(reader);

        tracker.advanceTo(t(5 * MIN));

        Map<String, Long> totals = tracker.getTodayTotals();
        assertEquals(Long.valueOf(2 * MIN), totals.get("yt"));
        assertEquals(Long.valueOf(3 * MIN), totals.get("ig"));
    }

    @Test
    public void screenOffEndsForegroundSegment() {
        FakeUsageEventReader reader = new FakeUsageEventReader()
                .fg("yt", t(0))
                .screenOff(t(4 * MIN));
        UsageTracker tracker = tracker(reader);

        tracker.advanceTo(t(20 * MIN));

        assertNull(tracker.getForegroundPackage());
        assertEquals(Long.valueOf(4 * MIN), tracker.getTodayTotals().get("yt"));
    }

}
```

- [ ] **Step 2: Запустить тесты и убедиться, что они падают**

Run: `./gradlew :app:testDebugUnitTest`
Expected: FAIL — классы `AppEvent`, `UsageEventReader`, `UsageTracker` не существуют.

- [ ] **Step 3: Создать `AppEvent` и `UsageEventReader`**

```java
package com.brainrotnotif.data;

public final class AppEvent {

    public static final int FOREGROUND = 1;
    public static final int BACKGROUND = 2;

    /** Пакет приложения. null в BACKGROUND означает «всё ушло с переднего плана». */
    public final String packageName;
    public final long timestamp;
    public final int type;

    public AppEvent(String packageName, long timestamp, int type) {
        this.packageName = packageName;
        this.timestamp = timestamp;
        this.type = type;
    }
}
```

```java
package com.brainrotnotif.data;

import java.util.List;

public interface UsageEventReader {

    /**
     * События в полуинтервале [fromMs, toMs), отсортированные по времени.
     * Контракт полуинтервала обязателен: вызывающая сторона использует toMs
     * предыдущего вызова как fromMs следующего и полагается на отсутствие
     * пересечений.
     */
    List<AppEvent> read(long fromMs, long toMs);
}
```

- [ ] **Step 4: Реализовать `UsageTracker` в объёме этой задачи**

```java
package com.brainrotnotif.data;

import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class UsageTracker {

    private final UsageEventReader reader;
    private final ZoneId zone;
    private final long graceMs;

    private final Map<String, Long> todayTotals = new HashMap<>();

    private boolean initialized = false;
    private long cursorMs;
    private long dayStartMs;
    private long nowMs;

    private String currentPkg;
    private long currentSinceMs;

    public UsageTracker(UsageEventReader reader, ZoneId zone, long graceMs) {
        this.reader = reader;
        this.zone = zone;
        this.graceMs = graceMs;
    }

    public void advanceTo(long nowMs) {
        if (!initialized) {
            dayStartMs = startOfDay(nowMs);
            cursorMs = dayStartMs;
            this.nowMs = dayStartMs;
            initialized = true;
        }
        if (nowMs <= cursorMs) {
            this.nowMs = Math.max(this.nowMs, nowMs);
            return;
        }
        List<AppEvent> events = reader.read(cursorMs, nowMs);
        for (AppEvent e : events) {
            apply(e);
        }
        cursorMs = nowMs;
        this.nowMs = nowMs;
    }

    public String getForegroundPackage() {
        return currentPkg;
    }

    public Map<String, Long> getTodayTotals() {
        Map<String, Long> out = new HashMap<>(todayTotals);
        if (currentPkg != null) {
            long from = Math.max(currentSinceMs, dayStartMs);
            if (nowMs > from) {
                add(out, currentPkg, nowMs - from);
            }
        }
        return out;
    }

    private void apply(AppEvent e) {
        if (e.type == AppEvent.FOREGROUND) {
            if (e.packageName == null || e.packageName.equals(currentPkg)) {
                return;
            }
            closeCurrent(e.timestamp);
            currentPkg = e.packageName;
            currentSinceMs = e.timestamp;
        } else {
            if (e.packageName == null || e.packageName.equals(currentPkg)) {
                closeCurrent(e.timestamp);
                currentPkg = null;
            }
        }
    }

    private void closeCurrent(long atMs) {
        if (currentPkg == null) {
            return;
        }
        long from = Math.max(currentSinceMs, dayStartMs);
        if (atMs > from) {
            add(todayTotals, currentPkg, atMs - from);
        }
        currentSinceMs = 0;
    }

    private long startOfDay(long ms) {
        return Instant.ofEpochMilli(ms)
                .atZone(zone)
                .toLocalDate()
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli();
    }

    private static void add(Map<String, Long> map, String key, long value) {
        Long prev = map.get(key);
        map.put(key, prev == null ? value : prev + value);
    }
}
```

Поле `graceMs` пока не используется — оно понадобится в Task 3. Предупреждение
компилятора об этом игнорируется.

- [ ] **Step 5: Запустить тесты**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS, 6 тестов.

- [ ] **Step 6: Коммит**

```bash
git add app/src/main/java/com/brainrotnotif/data app/src/test/java/com/brainrotnotif/data
git commit -m "feat(tracker): foreground package detection and daily totals"
```

---

### Task 3: UsageTracker — сессии и grace-период

**Files:**
- Modify: `app/src/main/java/com/brainrotnotif/data/UsageTracker.java`
- Test: `app/src/test/java/com/brainrotnotif/data/UsageTrackerTest.java`

**Interfaces:**
- Consumes: `UsageTracker`, `AppEvent`, `FakeUsageEventReader` из Task 2
- Produces:
  - `long getSessionDurationMs(String pkg)` — суммарное время на переднем плане внутри живой сессии, `0` если сессии нет
  - `long getSessionStartMs(String pkg)` — момент начала сессии, служит её идентификатором; `0` если сессии нет

- [ ] **Step 1: Дописать падающие тесты в `UsageTrackerTest`**

```java
    @Test
    public void sessionDurationEqualsForegroundTime() {
        FakeUsageEventReader reader = new FakeUsageEventReader().fg("yt", t(0));
        UsageTracker tracker = tracker(reader);

        tracker.advanceTo(t(7 * MIN));

        assertEquals(7 * MIN, tracker.getSessionDurationMs("yt"));
    }

    @Test
    public void shortGapKeepsSessionAndIsNotCounted() {
        FakeUsageEventReader reader = new FakeUsageEventReader()
                .fg("yt", t(0))
                .bg("yt", t(5 * MIN))
                .fg("tg", t(5 * MIN))
                .bg("tg", t(5 * MIN + 30_000))
                .fg("yt", t(5 * MIN + 30_000));
        UsageTracker tracker = tracker(reader);

        tracker.advanceTo(t(8 * MIN));

        // 5 минут до ухода + 2.5 минуты после возврата, пауза в 30 с не считается
        assertEquals(5 * MIN + 150_000, tracker.getSessionDurationMs("yt"));
    }

    @Test
    public void longGapStartsNewSession() {
        FakeUsageEventReader reader = new FakeUsageEventReader()
                .fg("yt", t(0))
                .bg("yt", t(5 * MIN))
                .fg("yt", t(7 * MIN));
        UsageTracker tracker = tracker(reader);

        tracker.advanceTo(t(9 * MIN));

        assertEquals(2 * MIN, tracker.getSessionDurationMs("yt"));
        assertEquals(t(7 * MIN), tracker.getSessionStartMs("yt"));
    }

    @Test
    public void screenOffLongerThanGraceStartsNewSession() {
        FakeUsageEventReader reader = new FakeUsageEventReader()
                .fg("yt", t(0))
                .screenOff(t(3 * MIN))
                .fg("yt", t(10 * MIN));
        UsageTracker tracker = tracker(reader);

        tracker.advanceTo(t(11 * MIN));

        assertEquals(MIN, tracker.getSessionDurationMs("yt"));
    }

    @Test
    public void eachPackageHasItsOwnSession() {
        FakeUsageEventReader reader = new FakeUsageEventReader()
                .fg("yt", t(0))
                .bg("yt", t(2 * MIN))
                .fg("ig", t(2 * MIN))
                .bg("ig", t(3 * MIN))
                .fg("yt", t(3 * MIN));
        UsageTracker tracker = tracker(reader);

        tracker.advanceTo(t(4 * MIN));

        assertEquals(3 * MIN, tracker.getSessionDurationMs("yt"));
        assertEquals(MIN, tracker.getSessionDurationMs("ig"));
        assertEquals(t(0), tracker.getSessionStartMs("yt"));
    }

    @Test
    public void expiredSessionIsForgotten() {
        FakeUsageEventReader reader = new FakeUsageEventReader()
                .fg("yt", t(0))
                .bg("yt", t(2 * MIN));
        UsageTracker tracker = tracker(reader);

        tracker.advanceTo(t(10 * MIN));

        assertEquals(0, tracker.getSessionDurationMs("yt"));
        assertEquals(0, tracker.getSessionStartMs("yt"));
    }
```

- [ ] **Step 2: Запустить тесты и убедиться, что они падают**

Run: `./gradlew :app:testDebugUnitTest`
Expected: FAIL — методы `getSessionDurationMs` и `getSessionStartMs` не существуют.

- [ ] **Step 3: Добавить сессии в `UsageTracker`**

Добавить импорт `java.util.Iterator` и вложенный класс:

```java
    private static final class Session {
        long startMs;
        long accumulatedMs;
        long resumedAtMs;
        long suspendedAtMs;
    }

    private final Map<String, Session> sessions = new HashMap<>();
```

Добавить публичные методы:

```java
    public long getSessionDurationMs(String pkg) {
        Session s = sessions.get(pkg);
        if (s == null) {
            return 0;
        }
        long duration = s.accumulatedMs;
        if (s.resumedAtMs > 0) {
            duration += Math.max(0, nowMs - s.resumedAtMs);
        }
        return duration;
    }

    public long getSessionStartMs(String pkg) {
        Session s = sessions.get(pkg);
        return s == null ? 0 : s.startMs;
    }
```

Добавить приватные методы:

```java
    private void openSession(String pkg, long atMs) {
        Session s = sessions.get(pkg);
        if (s != null && s.resumedAtMs == 0 && atMs - s.suspendedAtMs > graceMs) {
            s = null;
        }
        if (s == null) {
            s = new Session();
            s.startMs = atMs;
            sessions.put(pkg, s);
        }
        s.resumedAtMs = atMs;
        s.suspendedAtMs = 0;
    }

    private void suspendSession(String pkg, long atMs) {
        Session s = sessions.get(pkg);
        if (s == null || s.resumedAtMs == 0) {
            return;
        }
        s.accumulatedMs += atMs - s.resumedAtMs;
        s.resumedAtMs = 0;
        s.suspendedAtMs = atMs;
    }

    private void pruneSessions(long atMs) {
        Iterator<Map.Entry<String, Session>> it = sessions.entrySet().iterator();
        while (it.hasNext()) {
            Session s = it.next().getValue();
            if (s.resumedAtMs == 0 && atMs - s.suspendedAtMs > graceMs) {
                it.remove();
            }
        }
    }
```

В `apply`, в ветке `FOREGROUND`, после `currentSinceMs = e.timestamp;` добавить:

```java
            openSession(e.packageName, e.timestamp);
```

В `closeCurrent`, перед `currentSinceMs = 0;`, добавить:

```java
        suspendSession(currentPkg, atMs);
```

Переписать `advanceTo` так, чтобы чистка сессий выполнялась при любом выходе:

```java
    public void advanceTo(long nowMs) {
        if (!initialized) {
            dayStartMs = startOfDay(nowMs);
            cursorMs = dayStartMs;
            this.nowMs = dayStartMs;
            initialized = true;
        }
        if (nowMs > cursorMs) {
            List<AppEvent> events = reader.read(cursorMs, nowMs);
            for (AppEvent e : events) {
                apply(e);
            }
            cursorMs = nowMs;
        }
        this.nowMs = Math.max(this.nowMs, nowMs);
        pruneSessions(this.nowMs);
    }
```

- [ ] **Step 4: Запустить тесты**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS, 12 тестов.

- [ ] **Step 5: Коммит**

```bash
git add app/src/main/java/com/brainrotnotif/data app/src/test/java/com/brainrotnotif/data
git commit -m "feat(tracker): sessions with grace period"
```

---

### Task 4: UsageTracker — смена суток и инкрементальность

**Files:**
- Modify: `app/src/main/java/com/brainrotnotif/data/UsageTracker.java`
- Test: `app/src/test/java/com/brainrotnotif/data/UsageTrackerTest.java`

**Interfaces:**
- Consumes: всё из Task 3
- Produces: гарантию, что серия мелких `advanceTo` даёт тот же результат, что один большой

- [ ] **Step 1: Дописать падающие тесты**

```java
    @Test
    public void dayRolloverClearsTotalsButKeepsSession() {
        long beforeMidnight = 1_001 * DAY - 10 * MIN;
        FakeUsageEventReader reader = new FakeUsageEventReader().fg("yt", beforeMidnight);
        UsageTracker tracker = tracker(reader);

        tracker.advanceTo(beforeMidnight + MIN);
        tracker.advanceTo(1_001 * DAY + 10 * MIN);

        assertEquals(Long.valueOf(10 * MIN), tracker.getTodayTotals().get("yt"));
        assertEquals(20 * MIN, tracker.getSessionDurationMs("yt"));
    }

    @Test
    public void totalsCountOnlyTimeSinceStartOfDay() {
        long beforeMidnight = 1_001 * DAY - 10 * MIN;
        FakeUsageEventReader reader = new FakeUsageEventReader()
                .fg("yt", beforeMidnight)
                .bg("yt", 1_001 * DAY + 5 * MIN);
        UsageTracker tracker = tracker(reader);

        tracker.advanceTo(beforeMidnight + MIN);
        tracker.advanceTo(1_001 * DAY + 20 * MIN);

        assertEquals(Long.valueOf(5 * MIN), tracker.getTodayTotals().get("yt"));
    }

    @Test
    public void incrementalAdvanceMatchesSingleAdvance() {
        FakeUsageEventReader incrementalReader = new FakeUsageEventReader()
                .fg("yt", t(0))
                .bg("yt", t(3 * MIN))
                .fg("ig", t(3 * MIN))
                .bg("ig", t(4 * MIN))
                .fg("yt", t(4 * MIN))
                .screenOff(t(9 * MIN))
                .fg("yt", t(12 * MIN));
        FakeUsageEventReader singleReader = new FakeUsageEventReader()
                .fg("yt", t(0))
                .bg("yt", t(3 * MIN))
                .fg("ig", t(3 * MIN))
                .bg("ig", t(4 * MIN))
                .fg("yt", t(4 * MIN))
                .screenOff(t(9 * MIN))
                .fg("yt", t(12 * MIN));

        UsageTracker incremental = tracker(incrementalReader);
        for (long step = 0; step <= 15; step++) {
            incremental.advanceTo(t(step * MIN));
        }

        UsageTracker single = tracker(singleReader);
        single.advanceTo(t(15 * MIN));

        assertEquals(single.getTodayTotals(), incremental.getTodayTotals());
        assertEquals(single.getSessionDurationMs("yt"), incremental.getSessionDurationMs("yt"));
        assertEquals(single.getSessionStartMs("yt"), incremental.getSessionStartMs("yt"));
        assertEquals(single.getForegroundPackage(), incremental.getForegroundPackage());
    }
```

- [ ] **Step 2: Запустить тесты и убедиться, что они падают**

Run: `./gradlew :app:testDebugUnitTest`
Expected: FAIL — `dayRolloverClearsTotalsButKeepsSession` и `totalsCountOnlyTimeSinceStartOfDay`
дают время, накопленное со вчерашнего дня.

- [ ] **Step 3: Добавить обработку смены суток**

Добавить приватный метод:

```java
    private void maybeRollDay(long atMs) {
        long start = startOfDay(atMs);
        if (start != dayStartMs) {
            todayTotals.clear();
            dayStartMs = start;
        }
    }
```

Обрезка сегмента по началу суток уже есть в `closeCurrent` и `getTodayTotals`
(`Math.max(currentSinceMs, dayStartMs)`), поэтому `currentSinceMs` трогать не нужно.

В `advanceTo` вызвать его перед применением каждого события и после цикла:

```java
        if (nowMs > cursorMs) {
            List<AppEvent> events = reader.read(cursorMs, nowMs);
            for (AppEvent e : events) {
                maybeRollDay(e.timestamp);
                apply(e);
            }
            maybeRollDay(nowMs);
            cursorMs = nowMs;
        }
```

- [ ] **Step 4: Запустить тесты**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS, 15 тестов.

- [ ] **Step 5: Коммит**

```bash
git add app/src/main/java/com/brainrotnotif/data app/src/test/java/com/brainrotnotif/data
git commit -m "feat(tracker): day rollover handling"
```

---

### Task 5: Реальный источник событий и разрешения

**Files:**
- Create: `app/src/main/java/com/brainrotnotif/data/SystemUsageEventReader.java`
- Create: `app/src/main/java/com/brainrotnotif/data/InstalledApps.java`
- Create: `app/src/main/java/com/brainrotnotif/util/Permissions.java`
- Create: `app/src/main/java/com/brainrotnotif/util/Formats.java`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/brainrotnotif/ui/MainActivity.java`
- Modify: `app/src/main/res/layout/activity_main.xml`

**Interfaces:**
- Consumes: `UsageEventReader`, `AppEvent`, `UsageTracker`
- Produces:
  - `SystemUsageEventReader(Context context)` реализует `UsageEventReader`
  - `InstalledApps.Entry { String packageName; String label; }`
  - `List<InstalledApps.Entry> InstalledApps.launchable(Context)` — отсортировано по названию
  - `String InstalledApps.label(Context, String pkg)`
  - `Drawable InstalledApps.icon(Context, String pkg)` — `null`, если пакет не найден
  - `boolean Permissions.hasUsageAccess(Context)`
  - `boolean Permissions.canDrawOverlays(Context)`
  - `boolean Permissions.isIgnoringBatteryOptimizations(Context)`
  - `void Permissions.openUsageAccessSettings(Context)`
  - `void Permissions.openOverlaySettings(Context)`
  - `void Permissions.requestIgnoreBatteryOptimizations(Context)`
  - `String Formats.duration(long ms)` — `"7 мин"`, `"1 ч 12 мин"`, `"меньше минуты"`

- [ ] **Step 1: Добавить разрешения и `<queries>` в манифест**

В корень `<manifest>` добавить атрибут `xmlns:tools="http://schemas.android.com/tools"`,
а перед `<application>` — блок:

```xml
    <uses-permission
        android:name="android.permission.PACKAGE_USAGE_STATS"
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
```

- [ ] **Step 2: Создать `SystemUsageEventReader`**

```java
package com.brainrotnotif.data;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SystemUsageEventReader implements UsageEventReader {

    private final UsageStatsManager usageStatsManager;

    public SystemUsageEventReader(Context context) {
        this.usageStatsManager =
                (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
    }

    @Override
    public List<AppEvent> read(long fromMs, long toMs) {
        List<AppEvent> out = new ArrayList<>();
        if (usageStatsManager == null) {
            return out;
        }
        UsageEvents events = usageStatsManager.queryEvents(fromMs, toMs);
        UsageEvents.Event event = new UsageEvents.Event();
        while (events.getNextEvent(event)) {
            AppEvent mapped = map(event);
            if (mapped != null) {
                out.add(mapped);
            }
        }
        Collections.sort(out, (a, b) -> Long.compare(a.timestamp, b.timestamp));
        return out;
    }

    private static AppEvent map(UsageEvents.Event event) {
        int type = event.getEventType();
        long ts = event.getTimeStamp();
        if (type == UsageEvents.Event.ACTIVITY_RESUMED) {
            return new AppEvent(event.getPackageName(), ts, AppEvent.FOREGROUND);
        }
        if (type == UsageEvents.Event.ACTIVITY_PAUSED
                || type == UsageEvents.Event.ACTIVITY_STOPPED) {
            return new AppEvent(event.getPackageName(), ts, AppEvent.BACKGROUND);
        }
        if (type == UsageEvents.Event.SCREEN_NON_INTERACTIVE
                || type == UsageEvents.Event.KEYGUARD_SHOWN
                || type == UsageEvents.Event.DEVICE_SHUTDOWN) {
            return new AppEvent(null, ts, AppEvent.BACKGROUND);
        }
        return null;
    }
}
```

- [ ] **Step 3: Создать `Permissions`**

```java
package com.brainrotnotif.util;

import android.app.AppOpsManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.PowerManager;
import android.os.Process;
import android.provider.Settings;
import android.widget.Toast;

public final class Permissions {

    private Permissions() {
    }

    public static boolean hasUsageAccess(Context context) {
        AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        if (appOps == null) {
            return false;
        }
        int mode = appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    public static boolean canDrawOverlays(Context context) {
        return Settings.canDrawOverlays(context);
    }

    public static boolean isIgnoringBatteryOptimizations(Context context) {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(context.getPackageName());
    }

    public static void openUsageAccessSettings(Context context) {
        if (!start(context, new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))) {
            start(context, new Intent(Settings.ACTION_SETTINGS));
        }
    }

    public static void openOverlaySettings(Context context) {
        Intent intent = new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + context.getPackageName()));
        if (!start(context, intent)) {
            start(context, new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
        }
    }

    @SuppressWarnings("BatteryLife")
    public static void requestIgnoreBatteryOptimizations(Context context) {
        Intent intent = new Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:" + context.getPackageName()));
        if (!start(context, intent)) {
            start(context, new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        }
    }

    private static boolean start(Context context, Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(context, "Не удалось открыть настройки", Toast.LENGTH_SHORT).show();
            return false;
        }
    }
}
```

- [ ] **Step 4: Создать `Formats` и `InstalledApps`**

```java
package com.brainrotnotif.util;

public final class Formats {

    private Formats() {
    }

    public static String duration(long ms) {
        long totalMinutes = ms / 60_000L;
        if (totalMinutes <= 0) {
            return "меньше минуты";
        }
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        if (hours == 0) {
            return minutes + " мин";
        }
        return hours + " ч " + minutes + " мин";
    }
}
```

```java
package com.brainrotnotif.data;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class InstalledApps {

    public static final class Entry {
        public final String packageName;
        public final String label;

        public Entry(String packageName, String label) {
            this.packageName = packageName;
            this.label = label;
        }
    }

    private InstalledApps() {
    }

    public static List<Entry> launchable(Context context) {
        PackageManager pm = context.getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolved = pm.queryIntentActivities(intent, 0);
        List<Entry> out = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (ResolveInfo info : resolved) {
            String pkg = info.activityInfo.packageName;
            if (pkg.equals(context.getPackageName()) || seen.contains(pkg)) {
                continue;
            }
            seen.add(pkg);
            out.add(new Entry(pkg, info.loadLabel(pm).toString()));
        }
        Collections.sort(out, (a, b) -> a.label.compareToIgnoreCase(b.label));
        return out;
    }

    public static String label(Context context, String packageName) {
        PackageManager pm = context.getPackageManager();
        try {
            return pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return packageName;
        }
    }

    public static Drawable icon(Context context, String packageName) {
        try {
            return context.getPackageManager().getApplicationIcon(packageName);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }
}
```

- [ ] **Step 5: Временный экран проверки в `MainActivity`**

Этот код существует только ради проверки на устройстве и целиком заменяется
в Task 8.

`activity_main.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/root"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <TextView
        android:id="@+id/dump"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:padding="16dp"
        android:textIsSelectable="true" />
</ScrollView>
```

`MainActivity.java`:

```java
package com.brainrotnotif.ui;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.brainrotnotif.R;
import com.brainrotnotif.data.SystemUsageEventReader;
import com.brainrotnotif.data.UsageTracker;
import com.brainrotnotif.util.Formats;
import com.brainrotnotif.util.Permissions;

import java.time.ZoneId;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        TextView dump = findViewById(R.id.dump);
        if (!Permissions.hasUsageAccess(this)) {
            dump.setText("Нет доступа к статистике использования. Открываю настройки.");
            Permissions.openUsageAccessSettings(this);
            return;
        }
        UsageTracker tracker = new UsageTracker(
                new SystemUsageEventReader(this), ZoneId.systemDefault(), 60_000L);
        tracker.advanceTo(System.currentTimeMillis());

        StringBuilder sb = new StringBuilder();
        sb.append("Сейчас: ").append(tracker.getForegroundPackage()).append("\n\n");
        for (Map.Entry<String, Long> e : tracker.getTodayTotals().entrySet()) {
            sb.append(e.getKey()).append(" — ").append(Formats.duration(e.getValue())).append('\n');
        }
        dump.setText(sb.toString());
    }
}
```

`ViewCompat` и `Insets` приходят из `androidx.core`, который уже подтягивается
транзитивно через `appcompat`.

- [ ] **Step 6: Собрать и проверить на устройстве**

Run:

```bash
./gradlew :app:installDebug
/home/skipper/Android/Sdk/platform-tools/adb shell am start -n com.brainrotnotif/.ui.MainActivity
```

Expected: приложение открывает системные настройки Usage Access. Выдать доступ,
вернуться в приложение. На экране — список пакетов и время за сегодня, цифры
правдоподобны и сходятся с системным «Цифровым благополучием».

Устройства сейчас не подключены и AVD не создано (`avdmanager` отсутствует —
нет `cmdline-tools`). Нужен либо телефон по USB с включённой отладкой, либо AVD,
созданный в Android Studio на базе уже скачанного образа `android-36` x86_64.

- [ ] **Step 7: Коммит**

```bash
git add -A
git commit -m "feat(usage): real usage event reader, permissions and temporary dump screen"
```

---

### Task 6: Хранилище настроек приложений

**Files:**
- Create: `app/src/main/java/com/brainrotnotif/data/TrackedApp.java`
- Create: `app/src/main/java/com/brainrotnotif/data/TrackedAppsCodec.java`
- Create: `app/src/main/java/com/brainrotnotif/data/TrackedAppsStore.java`
- Test: `app/src/test/java/com/brainrotnotif/data/TrackedAppsCodecTest.java`

**Interfaces:**
- Consumes: ничего
- Produces:
  - `TrackedApp(String packageName, boolean overlayEnabled, int limitMinutes)`, поля публичные и финальные, плюс `withOverlayEnabled(boolean)` и `withLimitMinutes(int)`
  - `String TrackedAppsCodec.encode(List<TrackedApp>)`
  - `List<TrackedApp> TrackedAppsCodec.decode(String)` — терпимо к мусору, никогда не бросает
  - `TrackedAppsStore(Context)` с методами `getAll()`, `get(String)`, `put(TrackedApp)`, `remove(String)`, `isMonitoringEnabled()`, `setMonitoringEnabled(boolean)`, `isFirstRunDone()`, `setFirstRunDone()`, `registerListener(...)`, `unregisterListener(...)`
  - `TrackedAppsStore.DEFAULT_LIMIT_MINUTES = 15`

- [ ] **Step 1: Написать падающий тест кодека**

`app/src/test/java/com/brainrotnotif/data/TrackedAppsCodecTest.java`:

```java
package com.brainrotnotif.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TrackedAppsCodecTest {

    @Test
    public void roundTripPreservesEverything() {
        List<TrackedApp> apps = Arrays.asList(
                new TrackedApp("com.google.android.youtube", true, 15),
                new TrackedApp("com.instagram.android", false, 45));

        List<TrackedApp> decoded = TrackedAppsCodec.decode(TrackedAppsCodec.encode(apps));

        assertEquals(2, decoded.size());
        assertEquals("com.google.android.youtube", decoded.get(0).packageName);
        assertTrue(decoded.get(0).overlayEnabled);
        assertEquals(15, decoded.get(0).limitMinutes);
        assertEquals("com.instagram.android", decoded.get(1).packageName);
        assertEquals(false, decoded.get(1).overlayEnabled);
        assertEquals(45, decoded.get(1).limitMinutes);
    }

    @Test
    public void decodesEmptyAndNullToEmptyList() {
        assertTrue(TrackedAppsCodec.decode(null).isEmpty());
        assertTrue(TrackedAppsCodec.decode("").isEmpty());
        assertTrue(TrackedAppsCodec.decode("\n\n").isEmpty());
    }

    @Test
    public void skipsMalformedLines() {
        String raw = "com.a\t1\t10\nсовсем не запись\ncom.b\t1\tне число\ncom.c\t0\t30";

        List<TrackedApp> decoded = TrackedAppsCodec.decode(raw);

        assertEquals(2, decoded.size());
        assertEquals("com.a", decoded.get(0).packageName);
        assertEquals("com.c", decoded.get(1).packageName);
    }

    @Test
    public void encodesEmptyListToEmptyString() {
        assertEquals("", TrackedAppsCodec.encode(new ArrayList<>()));
    }
}
```

- [ ] **Step 2: Запустить тесты и убедиться, что они падают**

Run: `./gradlew :app:testDebugUnitTest`
Expected: FAIL — классы `TrackedApp` и `TrackedAppsCodec` не существуют.

- [ ] **Step 3: Создать `TrackedApp` и `TrackedAppsCodec`**

```java
package com.brainrotnotif.data;

public final class TrackedApp {

    public final String packageName;
    public final boolean overlayEnabled;
    public final int limitMinutes;

    public TrackedApp(String packageName, boolean overlayEnabled, int limitMinutes) {
        this.packageName = packageName;
        this.overlayEnabled = overlayEnabled;
        this.limitMinutes = limitMinutes;
    }

    public TrackedApp withOverlayEnabled(boolean value) {
        return new TrackedApp(packageName, value, limitMinutes);
    }

    public TrackedApp withLimitMinutes(int value) {
        return new TrackedApp(packageName, overlayEnabled, value);
    }
}
```

```java
package com.brainrotnotif.data;

import java.util.ArrayList;
import java.util.List;

public final class TrackedAppsCodec {

    private TrackedAppsCodec() {
    }

    public static String encode(List<TrackedApp> apps) {
        StringBuilder sb = new StringBuilder();
        for (TrackedApp app : apps) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(app.packageName)
                    .append('\t')
                    .append(app.overlayEnabled ? '1' : '0')
                    .append('\t')
                    .append(app.limitMinutes);
        }
        return sb.toString();
    }

    public static List<TrackedApp> decode(String raw) {
        List<TrackedApp> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        for (String line : raw.split("\n")) {
            String[] parts = line.split("\t");
            if (parts.length != 3 || parts[0].isEmpty()) {
                continue;
            }
            int limit;
            try {
                limit = Integer.parseInt(parts[2]);
            } catch (NumberFormatException e) {
                continue;
            }
            out.add(new TrackedApp(parts[0], "1".equals(parts[1]), limit));
        }
        return out;
    }
}
```

- [ ] **Step 4: Запустить тесты**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS, 19 тестов.

- [ ] **Step 5: Создать `TrackedAppsStore`**

```java
package com.brainrotnotif.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

public final class TrackedAppsStore {

    public static final int DEFAULT_LIMIT_MINUTES = 15;

    private static final String PREFS = "brainrot";
    private static final String KEY_APPS = "apps";
    private static final String KEY_MONITORING = "monitoring_enabled";
    private static final String KEY_FIRST_RUN_DONE = "first_run_done";

    private final SharedPreferences prefs;

    public TrackedAppsStore(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public List<TrackedApp> getAll() {
        return TrackedAppsCodec.decode(prefs.getString(KEY_APPS, ""));
    }

    public TrackedApp get(String packageName) {
        for (TrackedApp app : getAll()) {
            if (app.packageName.equals(packageName)) {
                return app;
            }
        }
        return null;
    }

    public void put(TrackedApp app) {
        List<TrackedApp> apps = getAll();
        List<TrackedApp> updated = new ArrayList<>();
        boolean replaced = false;
        for (TrackedApp existing : apps) {
            if (existing.packageName.equals(app.packageName)) {
                updated.add(app);
                replaced = true;
            } else {
                updated.add(existing);
            }
        }
        if (!replaced) {
            updated.add(app);
        }
        save(updated);
    }

    public void remove(String packageName) {
        List<TrackedApp> updated = new ArrayList<>();
        for (TrackedApp existing : getAll()) {
            if (!existing.packageName.equals(packageName)) {
                updated.add(existing);
            }
        }
        save(updated);
    }

    public boolean isMonitoringEnabled() {
        return prefs.getBoolean(KEY_MONITORING, true);
    }

    public void setMonitoringEnabled(boolean value) {
        prefs.edit().putBoolean(KEY_MONITORING, value).apply();
    }

    public boolean isFirstRunDone() {
        return prefs.getBoolean(KEY_FIRST_RUN_DONE, false);
    }

    public void setFirstRunDone() {
        prefs.edit().putBoolean(KEY_FIRST_RUN_DONE, true).apply();
    }

    public void registerListener(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        prefs.registerOnSharedPreferenceChangeListener(listener);
    }

    public void unregisterListener(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener);
    }

    private void save(List<TrackedApp> apps) {
        prefs.edit().putString(KEY_APPS, TrackedAppsCodec.encode(apps)).apply();
    }
}
```

- [ ] **Step 6: Собрать и закоммитить**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`

```bash
git add -A
git commit -m "feat(store): tracked apps model, codec and preferences store"
```

---

### Task 7: Каталог соцсетей и автозаполнение при первом запуске

**Files:**
- Create: `app/src/main/java/com/brainrotnotif/data/BrainRotCatalog.java`
- Test: `app/src/test/java/com/brainrotnotif/data/BrainRotCatalogTest.java`

**Interfaces:**
- Consumes: `InstalledApps.Entry`, `TrackedAppsStore`, `TrackedApp`
- Produces:
  - `List<String> BrainRotCatalog.detect(List<InstalledApps.Entry> installed)` — чистая функция
  - `int BrainRotCatalog.seed(Context context, TrackedAppsStore store)` — добавляет отсутствующие записи, возвращает количество добавленных, существующие не трогает

- [ ] **Step 1: Написать падающий тест**

```java
package com.brainrotnotif.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class BrainRotCatalogTest {

    private static InstalledApps.Entry app(String pkg) {
        return new InstalledApps.Entry(pkg, pkg);
    }

    @Test
    public void detectsKnownPackages() {
        List<String> found = BrainRotCatalog.detect(Arrays.asList(
                app("com.google.android.youtube"),
                app("com.example.calculator"),
                app("com.instagram.android")));

        assertEquals(2, found.size());
        assertTrue(found.contains("com.google.android.youtube"));
        assertTrue(found.contains("com.instagram.android"));
    }

    @Test
    public void detectsByPackagePrefix() {
        List<String> found = BrainRotCatalog.detect(Arrays.asList(
                app("com.vk.im"),
                app("ru.zen.android")));

        assertEquals(2, found.size());
    }

    @Test
    public void prefixMatchIsAnchoredAtStart() {
        List<String> found = BrainRotCatalog.detect(Arrays.asList(
                app("org.example.com.vk.clone"),
                app("com.vkbeautify")));

        assertTrue(found.isEmpty());
    }

    @Test
    public void ignoresUnknownApps() {
        List<String> found = BrainRotCatalog.detect(Arrays.asList(
                app("com.android.settings"),
                app("org.mozilla.firefox")));

        assertFalse(found.contains("com.android.settings"));
        assertTrue(found.isEmpty());
    }
}
```

Тест `prefixMatchIsAnchoredAtStart` фиксирует ключевое требование спеки: сравнение
идёт по началу имени пакета, а не по подстроке, иначе `com.vkbeautify` попадёт
в соцсети.

- [ ] **Step 2: Запустить тесты и убедиться, что они падают**

Run: `./gradlew :app:testDebugUnitTest`
Expected: FAIL — класс `BrainRotCatalog` не существует.

- [ ] **Step 3: Создать `BrainRotCatalog`**

```java
package com.brainrotnotif.data;

import android.content.Context;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class BrainRotCatalog {

    /** Подтверждённые имена пакетов. */
    private static final List<String> PACKAGES = Arrays.asList(
            "com.google.android.youtube",
            "com.instagram.android",
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.trill",
            "com.vkontakte.android",
            "com.twitter.android",
            "com.reddit.frontpage",
            "com.facebook.katana",
            "com.snapchat.android",
            "com.pinterest",
            "org.telegram.messenger");

    /** Префиксы для приложений, точные имена которых не подтверждены. */
    private static final List<String> PREFIXES = Arrays.asList(
            "com.vk.",
            "ru.vk.",
            "ru.zen.",
            "ru.rutube.",
            "video.like");

    private BrainRotCatalog() {
    }

    public static List<String> detect(List<InstalledApps.Entry> installed) {
        List<String> out = new ArrayList<>();
        for (InstalledApps.Entry entry : installed) {
            if (matches(entry.packageName)) {
                out.add(entry.packageName);
            }
        }
        return out;
    }

    public static int seed(Context context, TrackedAppsStore store) {
        int added = 0;
        for (String pkg : detect(InstalledApps.launchable(context))) {
            if (store.get(pkg) == null) {
                store.put(new TrackedApp(pkg, true, TrackedAppsStore.DEFAULT_LIMIT_MINUTES));
                added++;
            }
        }
        return added;
    }

    private static boolean matches(String packageName) {
        if (PACKAGES.contains(packageName)) {
            return true;
        }
        for (String prefix : PREFIXES) {
            if (packageName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
```

- [ ] **Step 4: Запустить тесты**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS, 23 теста.

- [ ] **Step 5: Сверить имена пакетов с реальным устройством**

Run:

```bash
/home/skipper/Android/Sdk/platform-tools/adb shell pm list packages | \
  grep -Ei 'youtube|instagram|tiktok|musically|trill|vk|zen|rutube|like|telegram|twitter|reddit|facebook|snapchat|pinterest'
```

Всё, что из `PACKAGES` не подтвердилось на устройстве, из списка **не удалять** —
это каталог для разных устройств, а не для одного. А вот если вывод покажет
пакет, который явно относится к соцсети и не попадает ни под один префикс
(например, отдельное приложение «ВК Видео» с неожиданным именем) — добавить его
в `PACKAGES` и дописать соответствующий тест в `detectsKnownPackages`.

- [ ] **Step 6: Коммит**

```bash
git add -A
git commit -m "feat(catalog): brain rot app detection"
```

---

### Task 8: Каркас UI и вкладка «Приложения»

**Files:**
- Modify: `app/src/main/java/com/brainrotnotif/ui/MainActivity.java` (полная замена временного экрана из Task 5)
- Modify: `app/src/main/res/layout/activity_main.xml` (полная замена)
- Create: `app/src/main/res/menu/bottom_nav.xml`
- Create: `app/src/main/java/com/brainrotnotif/ui/AppsFragment.java`
- Create: `app/src/main/java/com/brainrotnotif/ui/TrackedAppsAdapter.java`
- Create: `app/src/main/java/com/brainrotnotif/ui/LimitDialog.java`
- Create: `app/src/main/res/layout/fragment_apps.xml`
- Create: `app/src/main/res/layout/item_tracked_app.xml`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `TrackedAppsStore`, `TrackedApp`, `InstalledApps`, `UsageTracker`, `SystemUsageEventReader`, `Formats`, `Permissions`, `BrainRotCatalog.seed`
- Produces:
  - `MainActivity` с `BottomNavigationView` и контейнером `R.id.container`
  - `AppsFragment` — вкладка со списком отслеживаемых приложений
  - `LimitDialog.show(Context context, int currentMinutes, LimitDialog.OnPicked callback)`, где `interface OnPicked { void onPicked(int minutes); }`

- [ ] **Step 1: Создать строки и меню нижней навигации**

Дописать в `strings.xml`:

```xml
    <string name="tab_apps">Приложения</string>
    <string name="add_app">Добавить приложение</string>
    <string name="limit_title">Лимит непрерывной сессии</string>
    <string name="no_usage_access">Нет доступа к статистике использования</string>
```

`app/src/main/res/menu/bottom_nav.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<menu xmlns:android="http://schemas.android.com/apk/res/android">
    <item
        android:id="@+id/tab_apps"
        android:icon="@android:drawable/ic_menu_agenda"
        android:title="@string/tab_apps" />
</menu>
```

Вкладки «Статистика» и «Настройки» добавляются в этот файл в Task 10 и Task 11.

- [ ] **Step 2: Переписать `activity_main.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/root"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">

    <FrameLayout
        android:id="@+id/container"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1" />

    <com.google.android.material.bottomnavigation.BottomNavigationView
        android:id="@+id/nav"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:menu="@menu/bottom_nav" />
</LinearLayout>
```

- [ ] **Step 3: Переписать `MainActivity`**

```java
package com.brainrotnotif.ui;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.brainrotnotif.R;
import com.brainrotnotif.data.BrainRotCatalog;
import com.brainrotnotif.data.TrackedAppsStore;
import com.brainrotnotif.util.Permissions;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private TrackedAppsStore store;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        store = new TrackedAppsStore(this);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        BottomNavigationView nav = findViewById(R.id.nav);
        nav.setOnItemSelectedListener(item -> {
            show(fragmentFor(item.getItemId()));
            return true;
        });

        if (savedInstanceState == null) {
            seedIfFirstRun();
            show(new AppsFragment());
        }
    }

    @NonNull
    private Fragment fragmentFor(int itemId) {
        // R-класс в AGP 9 не final — switch по id не компилируется.
        if (itemId == R.id.tab_apps) {
            return new AppsFragment();
        }
        return new AppsFragment();
    }

    private void show(@NonNull Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.container, fragment)
                .commit();
    }

    private void seedIfFirstRun() {
        if (store.isFirstRunDone()) {
            return;
        }
        if (!Permissions.hasUsageAccess(this)) {
            return;
        }
        BrainRotCatalog.seed(this, store);
        store.setFirstRunDone();
    }
}
```

Метод `fragmentFor` расширяется в Task 10 и Task 11 по мере появления вкладок.

- [ ] **Step 4: Создать layout'ы вкладки**

`app/src/main/res/layout/fragment_apps.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/list"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:clipToPadding="false"
        android:paddingBottom="88dp" />

    <TextView
        android:id="@+id/empty"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center"
        android:padding="24dp"
        android:text="Список пуст. Добавь приложение кнопкой «+»."
        android:visibility="gone" />

    <com.google.android.material.floatingactionbutton.FloatingActionButton
        android:id="@+id/add"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom|end"
        android:layout_margin="16dp"
        android:contentDescription="@string/add_app"
        android:src="@android:drawable/ic_input_add" />
</FrameLayout>
```

`app/src/main/res/layout/item_tracked_app.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:background="?attr/selectableItemBackground"
    android:gravity="center_vertical"
    android:minHeight="72dp"
    android:orientation="horizontal"
    android:paddingStart="16dp"
    android:paddingEnd="8dp">

    <ImageView
        android:id="@+id/icon"
        android:layout_width="40dp"
        android:layout_height="40dp"
        android:contentDescription="@null" />

    <LinearLayout
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_marginStart="16dp"
        android:layout_weight="1"
        android:orientation="vertical">

        <TextView
            android:id="@+id/label"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:textAppearance="?attr/textAppearanceBodyLarge" />

        <TextView
            android:id="@+id/subtitle"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:textAppearance="?attr/textAppearanceBodySmall" />
    </LinearLayout>

    <com.google.android.material.materialswitch.MaterialSwitch
        android:id="@+id/overlay"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content" />
</LinearLayout>
```

- [ ] **Step 5: Создать `LimitDialog`**

```java
package com.brainrotnotif.ui;

import android.content.Context;
import android.widget.NumberPicker;

import androidx.appcompat.app.AlertDialog;

import com.brainrotnotif.R;

public final class LimitDialog {

    public interface OnPicked {
        void onPicked(int minutes);
    }

    private LimitDialog() {
    }

    public static void show(Context context, int currentMinutes, OnPicked callback) {
        NumberPicker picker = new NumberPicker(context);
        picker.setMinValue(1);
        picker.setMaxValue(240);
        picker.setValue(Math.max(1, Math.min(240, currentMinutes)));
        picker.setWrapSelectorWheel(false);

        new AlertDialog.Builder(context)
                .setTitle(R.string.limit_title)
                .setView(picker)
                .setPositiveButton(android.R.string.ok,
                        (dialog, which) -> callback.onPicked(picker.getValue()))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
```

- [ ] **Step 6: Создать `TrackedAppsAdapter`**

```java
package com.brainrotnotif.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.brainrotnotif.R;
import com.brainrotnotif.data.InstalledApps;
import com.brainrotnotif.data.TrackedApp;
import com.brainrotnotif.util.Formats;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TrackedAppsAdapter extends RecyclerView.Adapter<TrackedAppsAdapter.Holder> {

    public interface Callbacks {
        void onOverlayToggled(TrackedApp app, boolean enabled);

        void onLimitClicked(TrackedApp app);

        void onRemoveRequested(TrackedApp app);
    }

    private final Context context;
    private final Callbacks callbacks;
    private final List<TrackedApp> items = new ArrayList<>();
    private final Map<String, Long> todayTotals = new HashMap<>();

    public TrackedAppsAdapter(Context context, Callbacks callbacks) {
        this.context = context;
        this.callbacks = callbacks;
    }

    public void submit(List<TrackedApp> apps, Map<String, Long> totals) {
        items.clear();
        items.addAll(apps);
        todayTotals.clear();
        todayTotals.putAll(totals);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_tracked_app, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        TrackedApp app = items.get(position);
        Long total = todayTotals.get(app.packageName);

        holder.icon.setImageDrawable(InstalledApps.icon(context, app.packageName));
        holder.label.setText(InstalledApps.label(context, app.packageName));
        holder.subtitle.setText("Лимит " + app.limitMinutes + " мин · сегодня "
                + Formats.duration(total == null ? 0 : total));

        holder.overlay.setOnCheckedChangeListener(null);
        holder.overlay.setChecked(app.overlayEnabled);
        holder.overlay.setOnCheckedChangeListener(
                (button, checked) -> callbacks.onOverlayToggled(app, checked));

        holder.itemView.setOnClickListener(v -> callbacks.onLimitClicked(app));
        holder.itemView.setOnLongClickListener(v -> {
            callbacks.onRemoveRequested(app);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView label;
        final TextView subtitle;
        final MaterialSwitch overlay;

        Holder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.icon);
            label = itemView.findViewById(R.id.label);
            subtitle = itemView.findViewById(R.id.subtitle);
            overlay = itemView.findViewById(R.id.overlay);
        }
    }
}
```

- [ ] **Step 7: Создать `AppsFragment`**

```java
package com.brainrotnotif.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.brainrotnotif.R;
import com.brainrotnotif.data.InstalledApps;
import com.brainrotnotif.data.SystemUsageEventReader;
import com.brainrotnotif.data.TrackedApp;
import com.brainrotnotif.data.TrackedAppsStore;
import com.brainrotnotif.data.UsageTracker;
import com.brainrotnotif.util.Permissions;

import java.time.ZoneId;
import java.util.Collections;
import java.util.Map;

public class AppsFragment extends Fragment implements TrackedAppsAdapter.Callbacks {

    private TrackedAppsStore store;
    private TrackedAppsAdapter adapter;
    private TextView empty;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_apps, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        store = new TrackedAppsStore(requireContext());
        adapter = new TrackedAppsAdapter(requireContext(), this);
        empty = view.findViewById(R.id.empty);

        RecyclerView list = view.findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);

        // Обработчик кнопки «+» подключается в Task 9.
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        Map<String, Long> totals = Collections.emptyMap();
        if (Permissions.hasUsageAccess(requireContext())) {
            UsageTracker tracker = new UsageTracker(
                    new SystemUsageEventReader(requireContext()),
                    ZoneId.systemDefault(),
                    60_000L);
            tracker.advanceTo(System.currentTimeMillis());
            totals = tracker.getTodayTotals();
        }
        adapter.submit(store.getAll(), totals);
        empty.setVisibility(store.getAll().isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onOverlayToggled(TrackedApp app, boolean enabled) {
        store.put(app.withOverlayEnabled(enabled));
    }

    @Override
    public void onLimitClicked(TrackedApp app) {
        LimitDialog.show(requireContext(), app.limitMinutes, minutes -> {
            store.put(app.withLimitMinutes(minutes));
            refresh();
        });
    }

    @Override
    public void onRemoveRequested(TrackedApp app) {
        new AlertDialog.Builder(requireContext())
                .setMessage("Убрать " + InstalledApps.label(requireContext(), app.packageName)
                        + " из списка?")
                .setPositiveButton("Убрать", (dialog, which) -> {
                    store.remove(app.packageName);
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
```

Чтение `UsageTracker` идёт в главном потоке. Перемотка событий с начала суток
занимает единицы миллисекунд даже к вечеру, поэтому отдельный поток здесь —
преждевременная оптимизация. Если на реальном устройстве список будет
подтормаживать при открытии, вынести `refresh()` в `AsyncTask`-подобный
executor — но только после того, как торможение реально увидено.

- [ ] **Step 8: Собрать, установить и проверить на устройстве**

Run:

```bash
./gradlew :app:installDebug
/home/skipper/Android/Sdk/platform-tools/adb shell am start -n com.brainrotnotif/.ui.MainActivity
```

Expected: открывается вкладка «Приложения», в списке автоматически найденные
соцсети с иконками и временем за сегодня. Переключатель сохраняется после
перезапуска приложения. Тап по строке открывает выбор лимита, длинный тап —
диалог удаления.

- [ ] **Step 9: Коммит**

```bash
git add -A
git commit -m "feat(ui): main shell and tracked apps tab"
```

---

### Task 9: Добавление приложения из полного списка

**Files:**
- Create: `app/src/main/java/com/brainrotnotif/ui/AppPickerActivity.java`
- Create: `app/src/main/java/com/brainrotnotif/ui/AppPickerAdapter.java`
- Create: `app/src/main/res/layout/activity_app_picker.xml`
- Create: `app/src/main/res/layout/item_app.xml`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/brainrotnotif/ui/AppsFragment.java`

**Interfaces:**
- Consumes: `InstalledApps.launchable`, `TrackedAppsStore`
- Produces: `AppPickerActivity`, запускается обычным `startActivity`; результат не возвращает — `AppsFragment.onResume` перечитывает список сам

- [ ] **Step 1: Создать layout'ы**

`app/src/main/res/layout/activity_app_picker.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/root"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">

    <EditText
        android:id="@+id/search"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:autofillHints=""
        android:hint="Поиск"
        android:imeOptions="actionSearch"
        android:inputType="text"
        android:padding="16dp" />

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/list"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />
</LinearLayout>
```

`app/src/main/res/layout/item_app.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:background="?attr/selectableItemBackground"
    android:gravity="center_vertical"
    android:minHeight="64dp"
    android:orientation="horizontal"
    android:paddingStart="16dp"
    android:paddingEnd="16dp">

    <ImageView
        android:id="@+id/icon"
        android:layout_width="36dp"
        android:layout_height="36dp"
        android:contentDescription="@null" />

    <TextView
        android:id="@+id/label"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginStart="16dp"
        android:textAppearance="?attr/textAppearanceBodyLarge" />
</LinearLayout>
```

- [ ] **Step 2: Создать `AppPickerAdapter`**

```java
package com.brainrotnotif.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.brainrotnotif.R;
import com.brainrotnotif.data.InstalledApps;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AppPickerAdapter extends RecyclerView.Adapter<AppPickerAdapter.Holder> {

    public interface OnPicked {
        void onPicked(InstalledApps.Entry entry);
    }

    private final Context context;
    private final OnPicked callback;
    private final List<InstalledApps.Entry> all = new ArrayList<>();
    private final List<InstalledApps.Entry> visible = new ArrayList<>();

    public AppPickerAdapter(Context context, OnPicked callback) {
        this.context = context;
        this.callback = callback;
    }

    public void submit(List<InstalledApps.Entry> entries) {
        all.clear();
        all.addAll(entries);
        filter("");
    }

    public void filter(String query) {
        String needle = query.trim().toLowerCase(Locale.getDefault());
        visible.clear();
        for (InstalledApps.Entry entry : all) {
            if (needle.isEmpty()
                    || entry.label.toLowerCase(Locale.getDefault()).contains(needle)) {
                visible.add(entry);
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_app, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        InstalledApps.Entry entry = visible.get(position);
        holder.icon.setImageDrawable(InstalledApps.icon(context, entry.packageName));
        holder.label.setText(entry.label);
        holder.itemView.setOnClickListener(v -> callback.onPicked(entry));
    }

    @Override
    public int getItemCount() {
        return visible.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView label;

        Holder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.icon);
            label = itemView.findViewById(R.id.label);
        }
    }
}
```

- [ ] **Step 3: Создать `AppPickerActivity`**

```java
package com.brainrotnotif.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.brainrotnotif.R;
import com.brainrotnotif.data.InstalledApps;
import com.brainrotnotif.data.TrackedApp;
import com.brainrotnotif.data.TrackedAppsStore;

public class AppPickerActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_picker);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        TrackedAppsStore store = new TrackedAppsStore(this);
        AppPickerAdapter adapter = new AppPickerAdapter(this, entry -> {
            if (store.get(entry.packageName) != null) {
                Toast.makeText(this, "Уже в списке", Toast.LENGTH_SHORT).show();
                return;
            }
            store.put(new TrackedApp(entry.packageName, true,
                    TrackedAppsStore.DEFAULT_LIMIT_MINUTES));
            Toast.makeText(this, entry.label + " добавлено", Toast.LENGTH_SHORT).show();
            finish();
        });

        RecyclerView list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
        adapter.submit(InstalledApps.launchable(this));

        EditText search = findViewById(R.id.search);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }
}
```

- [ ] **Step 4: Объявить активити в манифесте**

Внутрь `<application>` добавить:

```xml
        <activity
            android:name=".ui.AppPickerActivity"
            android:exported="false"
            android:label="@string/add_app" />
```

- [ ] **Step 5: Подключить кнопку «+» в `AppsFragment`**

Заменить комментарий-заглушку на обработчик:

```java
        view.findViewById(R.id.add).setOnClickListener(v ->
                startActivity(new android.content.Intent(requireContext(), AppPickerActivity.class)));
```

- [ ] **Step 6: Проверить на устройстве**

Run: `./gradlew :app:installDebug`
Expected: кнопка «+» открывает список всех приложений, поиск фильтрует,
выбор добавляет приложение, после возврата оно есть во вкладке «Приложения».
Повторный выбор того же приложения показывает «Уже в списке».

- [ ] **Step 7: Коммит**

```bash
git add -A
git commit -m "feat(ui): app picker"
```

---

### Task 10: Вкладка «Статистика»

**Files:**
- Create: `app/src/main/java/com/brainrotnotif/ui/StatsFragment.java`
- Create: `app/src/main/java/com/brainrotnotif/ui/StatsAdapter.java`
- Create: `app/src/main/res/layout/fragment_stats.xml`
- Create: `app/src/main/res/layout/item_stat.xml`
- Modify: `app/src/main/res/menu/bottom_nav.xml`
- Modify: `app/src/main/java/com/brainrotnotif/ui/MainActivity.java`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `UsageTracker`, `SystemUsageEventReader`, `TrackedAppsStore`, `InstalledApps`, `Formats`, `Permissions`
- Produces: `StatsFragment`, вкладка `R.id.tab_stats`

- [ ] **Step 1: Добавить строку и пункт меню**

В `strings.xml`:

```xml
    <string name="tab_stats">Статистика</string>
```

В `bottom_nav.xml`, после пункта `tab_apps`:

```xml
    <item
        android:id="@+id/tab_stats"
        android:icon="@android:drawable/ic_menu_sort_by_size"
        android:title="@string/tab_stats" />
```

- [ ] **Step 2: Создать layout'ы**

`app/src/main/res/layout/fragment_stats.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">

    <TextView
        android:id="@+id/total"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:padding="16dp"
        android:textAppearance="?attr/textAppearanceTitleMedium" />

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/list"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />
</LinearLayout>
```

`app/src/main/res/layout/item_stat.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:gravity="center_vertical"
    android:minHeight="56dp"
    android:orientation="horizontal"
    android:paddingStart="16dp"
    android:paddingEnd="16dp">

    <ImageView
        android:id="@+id/icon"
        android:layout_width="32dp"
        android:layout_height="32dp"
        android:contentDescription="@null" />

    <TextView
        android:id="@+id/label"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_marginStart="16dp"
        android:layout_weight="1"
        android:textAppearance="?attr/textAppearanceBodyLarge" />

    <TextView
        android:id="@+id/time"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:textAppearance="?attr/textAppearanceBodyMedium" />
</LinearLayout>
```

- [ ] **Step 3: Создать `StatsAdapter`**

```java
package com.brainrotnotif.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.brainrotnotif.R;
import com.brainrotnotif.data.InstalledApps;
import com.brainrotnotif.util.Formats;

import java.util.ArrayList;
import java.util.List;

public class StatsAdapter extends RecyclerView.Adapter<StatsAdapter.Holder> {

    public static final class Row {
        public final String packageName;
        public final long totalMs;

        public Row(String packageName, long totalMs) {
            this.packageName = packageName;
            this.totalMs = totalMs;
        }
    }

    private final Context context;
    private final List<Row> rows = new ArrayList<>();

    public StatsAdapter(Context context) {
        this.context = context;
    }

    public void submit(List<Row> newRows) {
        rows.clear();
        rows.addAll(newRows);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_stat, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Row row = rows.get(position);
        holder.icon.setImageDrawable(InstalledApps.icon(context, row.packageName));
        holder.label.setText(InstalledApps.label(context, row.packageName));
        holder.time.setText(Formats.duration(row.totalMs));
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView label;
        final TextView time;

        Holder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.icon);
            label = itemView.findViewById(R.id.label);
            time = itemView.findViewById(R.id.time);
        }
    }
}
```

- [ ] **Step 4: Создать `StatsFragment`**

```java
package com.brainrotnotif.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.brainrotnotif.R;
import com.brainrotnotif.data.SystemUsageEventReader;
import com.brainrotnotif.data.TrackedApp;
import com.brainrotnotif.data.TrackedAppsStore;
import com.brainrotnotif.data.UsageTracker;
import com.brainrotnotif.util.Formats;
import com.brainrotnotif.util.Permissions;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class StatsFragment extends Fragment {

    private StatsAdapter adapter;
    private TextView total;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_stats, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        adapter = new StatsAdapter(requireContext());
        total = view.findViewById(R.id.total);

        RecyclerView list = view.findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        if (!Permissions.hasUsageAccess(requireContext())) {
            total.setText(R.string.no_usage_access);
            adapter.submit(Collections.emptyList());
            return;
        }

        UsageTracker tracker = new UsageTracker(
                new SystemUsageEventReader(requireContext()),
                ZoneId.systemDefault(),
                60_000L);
        tracker.advanceTo(System.currentTimeMillis());
        Map<String, Long> totals = tracker.getTodayTotals();

        List<StatsAdapter.Row> rows = new ArrayList<>();
        long sum = 0;
        for (TrackedApp app : new TrackedAppsStore(requireContext()).getAll()) {
            Long value = totals.get(app.packageName);
            long ms = value == null ? 0 : value;
            rows.add(new StatsAdapter.Row(app.packageName, ms));
            sum += ms;
        }
        Collections.sort(rows, (a, b) -> Long.compare(b.totalMs, a.totalMs));

        total.setText("Сегодня всего: " + Formats.duration(sum));
        adapter.submit(rows);
    }
}
```

- [ ] **Step 5: Подключить вкладку в `MainActivity`**

В методе `fragmentFor` добавить ветку перед `return new AppsFragment();`:

```java
        if (itemId == R.id.tab_stats) {
            return new StatsFragment();
        }
```

- [ ] **Step 6: Проверить на устройстве**

Run: `./gradlew :app:installDebug`
Expected: вкладка «Статистика» показывает отслеживаемые приложения по убыванию
времени за сегодня и суммарное время сверху. Цифры совпадают с теми, что видны
во вкладке «Приложения».

- [ ] **Step 7: Коммит**

```bash
git add -A
git commit -m "feat(ui): stats tab"
```

---

### Task 11: Вкладка «Настройки»

**Files:**
- Create: `app/src/main/java/com/brainrotnotif/ui/SettingsFragment.java`
- Create: `app/src/main/res/layout/fragment_settings.xml`
- Create: `app/src/main/res/layout/item_permission.xml`
- Modify: `app/src/main/res/menu/bottom_nav.xml`
- Modify: `app/src/main/java/com/brainrotnotif/ui/MainActivity.java`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `Permissions`, `TrackedAppsStore`, `BrainRotCatalog.seed`
- Produces: `SettingsFragment`, вкладка `R.id.tab_settings`

- [ ] **Step 1: Добавить строку и пункт меню**

В `strings.xml`:

```xml
    <string name="tab_settings">Настройки</string>
```

В `bottom_nav.xml`:

```xml
    <item
        android:id="@+id/tab_settings"
        android:icon="@android:drawable/ic_menu_preferences"
        android:title="@string/tab_settings" />
```

- [ ] **Step 2: Создать layout карточки разрешения**

`app/src/main/res/layout/item_permission.xml` — используется через `<include>`
несколько раз, поэтому id задаются в месте включения:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:gravity="center_vertical"
    android:minHeight="64dp"
    android:orientation="horizontal"
    android:paddingStart="16dp"
    android:paddingEnd="16dp">

    <LinearLayout
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:orientation="vertical">

        <TextView
            android:id="@+id/title"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:textAppearance="?attr/textAppearanceBodyLarge" />

        <TextView
            android:id="@+id/status"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:textAppearance="?attr/textAppearanceBodySmall" />
    </LinearLayout>

    <com.google.android.material.button.MaterialButton
        android:id="@+id/action"
        style="@style/Widget.Material3.Button.TextButton"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Выдать" />
</LinearLayout>
```

- [ ] **Step 3: Создать `fragment_settings.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:paddingBottom="24dp">

        <TextView
            android:id="@+id/warning"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:background="?attr/colorErrorContainer"
            android:padding="16dp"
            android:text="Без доступа к статистике использования и разрешения на показ поверх других приложений ничего работать не будет."
            android:visibility="gone" />

        <com.google.android.material.materialswitch.MaterialSwitch
            android:id="@+id/monitoring"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:minHeight="64dp"
            android:paddingStart="16dp"
            android:paddingEnd="16dp"
            android:text="Мониторинг включён" />

        <include
            android:id="@+id/perm_usage"
            layout="@layout/item_permission" />

        <include
            android:id="@+id/perm_overlay"
            layout="@layout/item_permission" />

        <include
            android:id="@+id/perm_notifications"
            layout="@layout/item_permission" />

        <include
            android:id="@+id/perm_battery"
            layout="@layout/item_permission" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/rescan"
            style="@style/Widget.Material3.Button.TextButton"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginStart="8dp"
            android:text="Пересканировать соцсети" />

        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:padding="16dp"
            android:text="Xiaomi, Huawei и Honor по умолчанию выключают автозапуск сторонних приложений и сбрасывают эту настройку после обновления системы. Если мониторинг перестал работать сам по себе — разреши автозапуск в настройках оболочки. Инструкции: dontkillmyapp.com"
            android:textAppearance="?attr/textAppearanceBodySmall" />
    </LinearLayout>
</ScrollView>
```

- [ ] **Step 4: Создать `SettingsFragment`**

```java
package com.brainrotnotif.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.brainrotnotif.R;
import com.brainrotnotif.data.BrainRotCatalog;
import com.brainrotnotif.data.TrackedAppsStore;
import com.brainrotnotif.util.Permissions;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;

public class SettingsFragment extends Fragment {

    private TrackedAppsStore store;
    private View root;
    private ActivityResultLauncher<String> notificationsLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        notificationsLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> refresh());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        root = view;
        store = new TrackedAppsStore(requireContext());

        MaterialSwitch monitoring = view.findViewById(R.id.monitoring);
        monitoring.setChecked(store.isMonitoringEnabled());
        monitoring.setOnCheckedChangeListener((button, checked) -> {
            store.setMonitoringEnabled(checked);
            // Запуск и остановка сервиса подключаются в Task 12.
        });

        view.findViewById(R.id.rescan).setOnClickListener(v -> {
            int added = BrainRotCatalog.seed(requireContext(), store);
            Toast.makeText(requireContext(),
                    added == 0 ? "Новых соцсетей не найдено" : "Добавлено: " + added,
                    Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        boolean usage = Permissions.hasUsageAccess(requireContext());
        boolean overlay = Permissions.canDrawOverlays(requireContext());

        root.findViewById(R.id.warning)
                .setVisibility(usage && overlay ? View.GONE : View.VISIBLE);

        bind(R.id.perm_usage, "Доступ к статистике использования", usage,
                v -> Permissions.openUsageAccessSettings(requireContext()));
        bind(R.id.perm_overlay, "Показ поверх других приложений", overlay,
                v -> Permissions.openOverlaySettings(requireContext()));
        bind(R.id.perm_notifications, "Уведомления", notificationsGranted(),
                v -> notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS));
        bind(R.id.perm_battery, "Оптимизация батареи отключена",
                Permissions.isIgnoringBatteryOptimizations(requireContext()),
                v -> Permissions.requestIgnoreBatteryOptimizations(requireContext()));
    }

    private boolean notificationsGranted() {
        return ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void bind(int includeId, String title, boolean granted, View.OnClickListener action) {
        View card = root.findViewById(includeId);
        ((TextView) card.findViewById(R.id.title)).setText(title);
        ((TextView) card.findViewById(R.id.status)).setText(granted ? "Выдано" : "Не выдано");
        MaterialButton button = card.findViewById(R.id.action);
        button.setVisibility(granted ? View.GONE : View.VISIBLE);
        button.setOnClickListener(action);
    }
}
```

`registerForActivityResult` требует `androidx.activity`, который приходит
транзитивно через `appcompat`.

- [ ] **Step 5: Подключить вкладку и открытие на ней при отсутствии разрешений**

В `MainActivity.fragmentFor` добавить ветку:

```java
        if (itemId == R.id.tab_settings) {
            return new SettingsFragment();
        }
```

В `MainActivity.onCreate`, в блоке `if (savedInstanceState == null)`, заменить
`show(new AppsFragment());` на:

```java
            boolean ready = Permissions.hasUsageAccess(this) && Permissions.canDrawOverlays(this);
            int startTab = ready ? R.id.tab_apps : R.id.tab_settings;
            show(fragmentFor(startTab));
            nav.setSelectedItemId(startTab);
```

`show(...)` вызывается явно: `BottomNavigationView` не уведомляет слушателя,
если выбираемый пункт уже отмечен, а первый пункт меню отмечен сразу после
инфляции. Без явного вызова экран остался бы пустым. Если слушатель всё же
сработает, `replace` просто выполнится второй раз — видимых последствий нет.

- [ ] **Step 6: Проверить на устройстве**

Run: `./gradlew :app:installDebug`
Expected: при первом запуске без разрешений приложение открывается на вкладке
«Настройки» с красной плашкой. Каждая кнопка ведёт на соответствующий системный
экран, после возврата статус карточки меняется на «Выдано» и кнопка пропадает.
«Пересканировать соцсети» сообщает, сколько добавлено.

- [ ] **Step 7: Коммит**

```bash
git add -A
git commit -m "feat(ui): settings tab with permission cards"
```

---

### Task 12: Foreground service и адаптивный тик

**Files:**
- Create: `app/src/main/java/com/brainrotnotif/monitor/MonitorService.java`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/brainrotnotif/ui/MainActivity.java`
- Modify: `app/src/main/java/com/brainrotnotif/ui/SettingsFragment.java`

**Interfaces:**
- Consumes: `UsageTracker`, `SystemUsageEventReader`, `TrackedAppsStore`, `TrackedApp`, `InstalledApps`, `Formats`, `Permissions`
- Produces:
  - `void MonitorService.start(Context)` и `void MonitorService.stop(Context)`
  - `MonitorService.GRACE_MS = 60_000L`
  - защищённые хуки `showBanner(String pkg, long elapsedMs)` и `hideBanner()`, пустые в этой задаче и наполняемые в Task 13

- [ ] **Step 1: Объявить сервис в манифесте**

Внутрь `<application>` добавить:

```xml
        <service
            android:name=".monitor.MonitorService"
            android:exported="false"
            android:foregroundServiceType="specialUse">
            <property
                android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
                android:value="Continuous foreground-app monitoring to enforce user-configured per-app screen time limits" />
        </service>
```

- [ ] **Step 2: Создать `MonitorService`**

```java
package com.brainrotnotif.monitor;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

import com.brainrotnotif.data.InstalledApps;
import com.brainrotnotif.data.SystemUsageEventReader;
import com.brainrotnotif.data.TrackedApp;
import com.brainrotnotif.data.TrackedAppsStore;
import com.brainrotnotif.data.UsageTracker;
import com.brainrotnotif.ui.MainActivity;
import com.brainrotnotif.util.Formats;
import com.brainrotnotif.util.Permissions;

import java.time.ZoneId;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class MonitorService extends Service
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    public static final long GRACE_MS = 60_000L;

    private static final long TICK_MAX_MS = 5_000L;
    private static final long TICK_MIN_MS = 250L;
    private static final String CHANNEL_ID = "monitor";
    private static final int NOTIFICATION_ID = 1;

    private HandlerThread thread;
    private Handler handler;
    private UsageTracker tracker;
    private TrackedAppsStore store;
    private volatile List<TrackedApp> cache;
    private final Map<String, Integer> fired = new HashMap<>();
    private String lastNotificationText;

    public static void start(Context context) {
        ContextCompat.startForegroundService(context, new Intent(context, MonitorService.class));
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, MonitorService.class));
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        store = new TrackedAppsStore(this);
        store.registerListener(this);
        cache = store.getAll();
        tracker = new UsageTracker(
                new SystemUsageEventReader(this), ZoneId.systemDefault(), GRACE_MS);
        thread = new HandlerThread("monitor");
        thread.start();
        handler = new Handler(thread.getLooper());
    }

    @SuppressLint("InlinedApi")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                ? ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                : 0;
        ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(notificationText(null, 0)),
                type);

        if (!Permissions.hasUsageAccess(this) || !store.isMonitoringEnabled()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        scheduleTick(0);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        hideBanner();
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
        if (thread != null) {
            thread.quitSafely();
        }
        store.unregisterListener(this);
        super.onDestroy();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, @Nullable String key) {
        cache = store.getAll();
    }

    /** Переопределяется содержимым в Task 13. */
    protected void showBanner(String packageName, long elapsedMs) {
    }

    /** Переопределяется содержимым в Task 13. */
    protected void hideBanner() {
    }

    protected void scheduleTick(long delayMs) {
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, delayMs);
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            long now = System.currentTimeMillis();
            tracker.advanceTo(now);

            String pkg = tracker.getForegroundPackage();
            TrackedApp app = pkg == null ? null : find(pkg);
            long delay = TICK_MAX_MS;
            long elapsed = 0;

            if (app != null) {
                elapsed = tracker.getSessionDurationMs(pkg);
            }

            if (app != null && app.overlayEnabled) {
                long limitMs = app.limitMinutes * 60_000L;
                String key = pkg + "@" + tracker.getSessionStartMs(pkg);
                Integer stored = fired.get(key);
                int count = stored == null ? 0 : stored;
                long threshold = (count + 1) * limitMs;
                if (elapsed >= threshold) {
                    fired.put(key, count + 1);
                    threshold += limitMs;
                    showBanner(pkg, elapsed);
                }
                delay = Math.max(TICK_MIN_MS, Math.min(TICK_MAX_MS, threshold - elapsed));
            } else {
                hideBanner();
            }

            pruneFired();
            updateNotification(pkg, elapsed);
            handler.postDelayed(this, delay);
        }
    };

    private TrackedApp find(String packageName) {
        for (TrackedApp app : cache) {
            if (app.packageName.equals(packageName)) {
                return app;
            }
        }
        return null;
    }

    private void pruneFired() {
        Iterator<String> it = fired.keySet().iterator();
        while (it.hasNext()) {
            String key = it.next();
            int at = key.lastIndexOf('@');
            String pkg = key.substring(0, at);
            long start;
            try {
                start = Long.parseLong(key.substring(at + 1));
            } catch (NumberFormatException e) {
                it.remove();
                continue;
            }
            if (tracker.getSessionStartMs(pkg) != start) {
                it.remove();
            }
        }
    }

    private String notificationText(String packageName, long elapsedMs) {
        if (packageName == null) {
            int count = cache == null ? 0 : cache.size();
            return "Отслеживаю приложений: " + count;
        }
        return InstalledApps.label(this, packageName) + " — " + Formats.duration(elapsedMs);
    }

    private void updateNotification(String packageName, long elapsedMs) {
        String text = notificationText(packageName, elapsedMs);
        if (text.equals(lastNotificationText)) {
            return;
        }
        lastNotificationText = text;
        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification(text));
        } catch (SecurityException e) {
            // POST_NOTIFICATIONS не выдано — сервис продолжает работу без видимого уведомления.
        }
    }

    private Notification buildNotification(String text) {
        PendingIntent contentIntent = PendingIntent.getActivity(
                this,
                0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentTitle("BrainRotNotif")
                .setContentText(text)
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void createChannel() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null || manager.getNotificationChannel(CHANNEL_ID) != null) {
            return;
        }
        manager.createNotificationChannel(new NotificationChannel(
                CHANNEL_ID, "Мониторинг", NotificationManager.IMPORTANCE_LOW));
    }
}
```

`FOREGROUND_SERVICE_TYPE_SPECIAL_USE` появился в API 34, а `minSdk` равен 30.
Константа `static final int` подставляется компилятором, поэтому ссылка на неё
безопасна, но **передавать** её в `startForeground` на API 30–33 нельзя: система
сверяет биты типа с объявленными в манифесте и на неизвестный тип бросает
исключение. Отсюда явная проверка версии и `0` на старых системах.
`@SuppressLint("InlinedApi")` глушит lint на саму ссылку.

Если установка на устройство с Android 11 или 12 падает с ошибкой разбора
манифеста — причина в теге `<property>`, который появился в API 31. В этом
случае поднять `minSdk` до 31 и зафиксировать это в спеке.

- [ ] **Step 3: Запускать сервис из `MainActivity`**

Добавить в `MainActivity` метод и вызов из `onStart`:

```java
    @Override
    protected void onStart() {
        super.onStart();
        if (store.isMonitoringEnabled() && Permissions.hasUsageAccess(this)) {
            MonitorService.start(this);
        }
    }
```

Добавить импорт `com.brainrotnotif.monitor.MonitorService`.

- [ ] **Step 4: Подключить переключатель мониторинга**

В `SettingsFragment`, в обработчике `monitoring`, заменить комментарий-заглушку:

```java
        monitoring.setOnCheckedChangeListener((button, checked) -> {
            store.setMonitoringEnabled(checked);
            if (checked) {
                MonitorService.start(requireContext());
            } else {
                MonitorService.stop(requireContext());
            }
        });
```

Добавить импорт `com.brainrotnotif.monitor.MonitorService`.

- [ ] **Step 5: Проверить на устройстве**

Run:

```bash
./gradlew :app:installDebug
/home/skipper/Android/Sdk/platform-tools/adb shell am start -n com.brainrotnotif/.ui.MainActivity
```

Expected: появляется постоянное уведомление «BrainRotNotif». Свернуть приложение,
открыть YouTube — текст уведомления меняется на «YouTube — N мин» и растёт.
Переключатель «Мониторинг включён» в настройках убирает и возвращает уведомление.

Проверить тип сервиса:

```bash
/home/skipper/Android/Sdk/platform-tools/adb shell dumpsys activity services com.brainrotnotif | grep -i foreground
```

Expected: в выводе виден работающий foreground service.

- [ ] **Step 6: Коммит**

```bash
git add -A
git commit -m "feat(monitor): foreground service with adaptive tick"
```

---

### Task 13: Оверлей-баннер

**Files:**
- Create: `app/src/main/java/com/brainrotnotif/monitor/OverlayBanner.java`
- Create: `app/src/main/res/layout/overlay_banner.xml`
- Modify: `app/src/main/java/com/brainrotnotif/monitor/MonitorService.java`

**Interfaces:**
- Consumes: `InstalledApps`, `Formats`, `Permissions`
- Produces:
  - `OverlayBanner(Context context)`
  - `void show(String packageName, long elapsedMs)` — только с главного потока
  - `void hide()` — только с главного потока

- [ ] **Step 1: Создать `overlay_banner.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:background="?attr/colorErrorContainer"
    android:elevation="8dp"
    android:gravity="center_vertical"
    android:orientation="horizontal"
    android:paddingStart="16dp"
    android:paddingTop="12dp"
    android:paddingEnd="8dp"
    android:paddingBottom="12dp">

    <ImageView
        android:id="@+id/icon"
        android:layout_width="32dp"
        android:layout_height="32dp"
        android:contentDescription="@null" />

    <TextView
        android:id="@+id/text"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_marginStart="12dp"
        android:layout_weight="1"
        android:textAppearance="?attr/textAppearanceBodyLarge"
        android:textColor="?attr/colorOnErrorContainer" />

    <com.google.android.material.button.MaterialButton
        android:id="@+id/ok"
        style="@style/Widget.Material3.Button.TextButton"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Ок" />
</LinearLayout>
```

- [ ] **Step 2: Создать `OverlayBanner`**

```java
package com.brainrotnotif.monitor;

import android.content.Context;
import android.graphics.PixelFormat;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.view.ContextThemeWrapper;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.brainrotnotif.R;
import com.brainrotnotif.data.InstalledApps;
import com.brainrotnotif.util.Formats;

public final class OverlayBanner {

    private final Context themed;
    private final WindowManager windowManager;
    private View view;

    public OverlayBanner(Context context) {
        this.themed = new ContextThemeWrapper(
                context.getApplicationContext(), R.style.Theme_BrainRotNotif);
        this.windowManager =
                (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }

    public void show(String packageName, long elapsedMs) {
        hide();
        if (windowManager == null || !Settings.canDrawOverlays(themed)) {
            return;
        }

        View banner = LayoutInflater.from(themed).inflate(R.layout.overlay_banner, null);
        ((ImageView) banner.findViewById(R.id.icon))
                .setImageDrawable(InstalledApps.icon(themed, packageName));
        ((TextView) banner.findViewById(R.id.text)).setText(
                "Ты в " + InstalledApps.label(themed, packageName)
                        + " уже " + Formats.duration(elapsedMs));
        banner.findViewById(R.id.ok).setOnClickListener(v -> hide());

        ViewCompat.setOnApplyWindowInsetsListener(banner, (target, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            target.setPadding(
                    target.getPaddingLeft(),
                    bars.top + target.getPaddingTop(),
                    target.getPaddingRight(),
                    target.getPaddingBottom());
            return insets;
        });

        // Окно touchable: у баннера своя кнопка. FLAG_NOT_TOUCHABLE ставить нельзя —
        // Android 12+ блокирует сквозные касания при непрозрачности выше 0.8.
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP;
        params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;

        try {
            windowManager.addView(banner, params);
            view = banner;
        } catch (WindowManager.BadTokenException | IllegalStateException e) {
            view = null;
        }
    }

    public void hide() {
        if (view == null || windowManager == null) {
            return;
        }
        try {
            windowManager.removeView(view);
        } catch (IllegalArgumentException e) {
            // Окно уже снято системой.
        }
        view = null;
    }
}
```

- [ ] **Step 3: Подключить баннер в `MonitorService`**

Добавить импорты `android.os.Looper` и поля:

```java
    private Handler mainHandler;
    private OverlayBanner banner;
    private volatile String bannerPackage;
```

В `onCreate`, после создания `handler`:

```java
        mainHandler = new Handler(Looper.getMainLooper());
        banner = new OverlayBanner(this);
```

Заменить тела хуков:

```java
    protected void showBanner(String packageName, long elapsedMs) {
        bannerPackage = packageName;
        mainHandler.post(() -> banner.show(packageName, elapsedMs));
    }

    protected void hideBanner() {
        if (bannerPackage == null) {
            return;
        }
        bannerPackage = null;
        if (mainHandler != null) {
            mainHandler.post(() -> banner.hide());
        }
    }
```

Операции с `WindowManager` обязаны идти с главного потока, а тик работает на
`HandlerThread` — отсюда `mainHandler`. Нажатие «Ок» снимает баннер на главном
потоке, не сообщая сервису; `bannerPackage` при этом остаётся заполненным, но
это безвредно: следующая отметка вызовет `showBanner` заново, а `hide()` на уже
снятом окне ничего не делает.

- [ ] **Step 4: Проверить на устройстве**

Установить лимит для тестового приложения в 1 минуту через вкладку «Приложения».

Run: `./gradlew :app:installDebug`

Expected:
1. Открыть это приложение и подождать минуту — сверху появляется баннер
   «Ты в … уже 1 мин» с кнопкой «Ок».
2. Тапы **мимо** баннера проходят в приложение под ним — прокрутка работает.
3. Тап по «Ок» убирает баннер. Ещё через минуту он появляется снова.
4. Переход в другое приложение снимает баннер.
5. Выключить переключатель «оверлей» у этого приложения — баннер больше не
   появляется, но время во вкладке «Статистика» продолжает считаться.

- [ ] **Step 5: Коммит**

```bash
git add -A
git commit -m "feat(monitor): overlay banner"
```

---

### Task 14: Экономия батареи и автозапуск

**Files:**
- Create: `app/src/main/java/com/brainrotnotif/monitor/ScreenStateReceiver.java`
- Create: `app/src/main/java/com/brainrotnotif/monitor/BootReceiver.java`
- Modify: `app/src/main/java/com/brainrotnotif/monitor/MonitorService.java`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `MonitorService.start`, `TrackedAppsStore`, `Permissions`
- Produces: `ScreenStateReceiver` (регистрируется динамически), `BootReceiver` (объявлен в манифесте)

- [ ] **Step 1: Создать `ScreenStateReceiver`**

```java
package com.brainrotnotif.monitor;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ScreenStateReceiver extends BroadcastReceiver {

    public interface Listener {
        void onScreenOn();

        void onScreenOff();
    }

    private final Listener listener;

    public ScreenStateReceiver(Listener listener) {
        this.listener = listener;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
            listener.onScreenOn();
        } else if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
            listener.onScreenOff();
        }
    }
}
```

- [ ] **Step 2: Гасить тик при выключенном экране**

В `MonitorService` реализовать `ScreenStateReceiver.Listener`:

```java
public class MonitorService extends Service
        implements SharedPreferences.OnSharedPreferenceChangeListener,
        ScreenStateReceiver.Listener {
```

Добавить поле:

```java
    private ScreenStateReceiver screenReceiver;
```

В `onCreate`, после создания `banner`:

```java
        screenReceiver = new ScreenStateReceiver(this);
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        ContextCompat.registerReceiver(this, screenReceiver, filter,
                ContextCompat.RECEIVER_NOT_EXPORTED);
```

Добавить импорт `android.content.IntentFilter`.

В `onDestroy`, перед `store.unregisterListener(this)`:

```java
        if (screenReceiver != null) {
            unregisterReceiver(screenReceiver);
        }
```

Добавить методы:

```java
    @Override
    public void onScreenOn() {
        scheduleTick(0);
    }

    @Override
    public void onScreenOff() {
        handler.removeCallbacks(tick);
        hideBanner();
    }
```

`ACTION_SCREEN_ON` и `ACTION_SCREEN_OFF` можно получать только динамической
регистрацией — в манифесте система их не доставляет. `RECEIVER_NOT_EXPORTED`
обязателен для targetSdk 34+.

- [ ] **Step 3: Создать `BootReceiver` и объявить его**

```java
package com.brainrotnotif.monitor;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.brainrotnotif.data.TrackedAppsStore;
import com.brainrotnotif.util.Permissions;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            return;
        }
        TrackedAppsStore store = new TrackedAppsStore(context);
        if (store.isMonitoringEnabled() && Permissions.hasUsageAccess(context)) {
            MonitorService.start(context);
        }
    }
}
```

В манифест, внутрь `<application>`:

```xml
        <receiver
            android:name=".monitor.BootReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
                <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
            </intent-filter>
        </receiver>
```

Тип `specialUse` не входит в список типов, которым Android 15 запретил старт из
`BOOT_COMPLETED` (`dataSync`, `camera`, `mediaPlayback`, `phoneCall`,
`mediaProjection`, `microphone`), поэтому автозапуск разрешён.

- [ ] **Step 4: Проверить на устройстве**

Run: `./gradlew :app:installDebug`

Expected:
1. Выключить экран на 2 минуты, включить, открыть отслеживаемое приложение —
   сессия начинается заново, потому что пауза больше grace-периода.
2. Выключить экран на 20 секунд и вернуться в то же приложение — сессия
   продолжается, отсчёт не сбросился.
3. Перезагрузить устройство: `adb reboot`. После загрузки, не открывая
   приложение, проверить уведомление:

```bash
/home/skipper/Android/Sdk/platform-tools/adb shell dumpsys activity services com.brainrotnotif | head -20
```

Expected: сервис запущен сам.

- [ ] **Step 5: Коммит и пуш**

```bash
git add -A
git commit -m "feat(monitor): screen state gating and boot autostart"
git push
```
