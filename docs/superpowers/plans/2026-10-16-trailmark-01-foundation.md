# Trailmark — خطة 01: الأساس والوحدات البدئية

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use `- [x]` checkboxes.

**Goal:** مشروع Gradle متعدّد الوحدات لأندرويد + CI يبني ويختبر + نقل الوحدات النقية `core:time`, `core:geo`, `core:model`.

**Architecture:** Kotlin/Android متعدّد الوحدات. `:core:*` بلا Android، تُختبَر على JVM. اعتماد أحادي الاتجاه.

**Tech Stack:** Kotlin 2.0.21, AGP 8.7.3, Gradle 8.9, JDK 17, JUnit5, AndroidX.

**Spec:** `docs/superpowers/specs/2026-10-16-trailmark-design.md`

## Global Constraints
- package: `dev.toufik.trailmark`؛ minSdk 26، compile/targetSdk 35، JDK 17.
- `:core:*` و`:engine` بلا اعتماد Android.
- MIT؛ تبعيات متساهلة فقط.
- لغات: ar/fr/en.
## Review Focus
- زمن مساوٍ أو راجع ⇒ يُرفض ولا يفسد المدة.
- نقطتان متطابقتان ⇒ مسافة صفر بلا قسمة على صفر.
- قيمة نوع غير معروفة مخزّنة ⇒ لا انهيار.
- مسار فارغ (صفر نقاط) ⇒ لا انهيار.
- `displayablePoints` يستبعد REJECTED فقط ويحفظ الخمول.

## File Structure
```
settings.gradle.kts, build.gradle.kts, gradle.properties, gradle/libs.versions.toml
gradle/wrapper/gradle-wrapper.properties, gradlew, gradlew.bat
core/model/build.gradle.kts + src/main/kotlin/dev/toufik/trailmark/core/model/Model.kt
core/geo/build.gradle.kts   + .../core/geo/Geo.kt (+ test)
core/time/build.gradle.kts  + .../core/time/MonotonicTicker.kt (+ test)
.github/workflows/ci.yml
```
---
## Task 1: هيكل المشروع + `core:time`

**Files:** Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `core/time/build.gradle.kts`, `core/time/src/main/kotlin/dev/toufik/trailmark/core/time/MonotonicTicker.kt`, `core/time/src/test/kotlin/dev/toufik/trailmark/core/time/MonotonicTickerTest.kt`

**Interfaces:** Produces: `class MonotonicTicker { val lastNanos: Long; fun accept(nanos: Long): Boolean; fun secondsSince(fromNanos: Long): Double }`

- [x] **Step 1: اختبار فاشل**
```kotlin
package dev.toufik.trailmark.core.time
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
class MonotonicTickerTest {
  @Test fun rejectsNonMonotonic() {
    val t = MonotonicTicker()
    assertTrue(t.accept(1000))
    assertFalse(t.accept(900))
    assertFalse(t.accept(1000))
    assertEquals(1000L, t.lastNanos)
    assertTrue(t.accept(3_000_000_000L))
    assertEquals(2.999999, t.secondsSince(1000), 1e-9)
  }
}
```
- [x] **Step 2: شغّل وافشل** — `./gradlew :core:time:test` ⇒ FAIL (غير موجود).
- [x] **Step 3: التنفيذ**
```kotlin
package dev.toufik.trailmark.core.time
class MonotonicTicker {
  var lastNanos: Long = -1L; private set
  fun accept(nanos: Long): Boolean { if (nanos <= lastNanos) return false; lastNanos = nanos; return true }
  fun secondsSince(fromNanos: Long): Double = (lastNanos - fromNanos) / 1e9
}
```
- [x] **Step 4: شغّل ونجح** — `./gradlew :core:time:test` ⇒ PASS.
- [x] **Step 5: commit** — `feat: core:time MonotonicTicker`.
---
## Task 2: `core:geo`

**Files:** Create: `core/geo/build.gradle.kts`, `core/geo/src/main/kotlin/dev/toufik/trailmark/core/geo/Geo.kt`, test.
**Interfaces:** Produces: `haversineMeters(Double,Double,Double,Double): Double`, `bearingDeg(...): Double`.

- [x] **Step 1: اختبار فاشل**
```kotlin
package dev.toufik.trailmark.core.geo
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
class GeoTest {
  @Test fun identicalIsZero() = assertEquals(0.0, haversineMeters(1.0,2.0,1.0,2.0), 1e-9)
  @Test fun oneDegreeLat() = assertEquals(111195.08, haversineMeters(0.0,0.0,1.0,0.0), 1.0)
  @Test fun bearingEast() = assertEquals(90.0, bearingDeg(36.75,3.18,36.75,3.19), 0.5)
}
```
- [x] **Step 2: شغّل وافشل** — `./gradlew :core:geo:test`.
- [x] **Step 3: التنفيذ**
```kotlin
package dev.toufik.trailmark.core.geo
import kotlin.math.*
private const val EARTH_RADIUS_M = 6_371_008.8
private fun rad(d: Double) = d * PI / 180.0
fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
  val p1 = rad(lat1); val p2 = rad(lat2); val dp = p2 - p1; val dl = rad(lon2 - lon1)
  val a = sin(dp/2).pow(2) + cos(p1)*cos(p2)*sin(dl/2).pow(2)
  return 2 * EARTH_RADIUS_M * asin(min(1.0, sqrt(a)))
}
fun bearingDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
  val p1 = rad(lat1); val p2 = rad(lat2); val dl = rad(lon2 - lon1)
  val y = sin(dl)*cos(p2); val x = cos(p1)*sin(p2)-sin(p1)*cos(p2)*cos(dl)
  return (atan2(y,x)*180/PI + 360) % 360
}
```
- [x] **Step 4: PASS** — `./gradlew :core:geo:test`.
- [x] **Step 5: commit** — `feat: core:geo haversine/bearing`.
---
## Task 3: `core:model`

**Files:** Create: `core/model/build.gradle.kts`, `core/model/src/main/kotlin/dev/toufik/trailmark/core/model/Model.kt`, test.
**Interfaces:** Produces: `enum WaypointType { NORMAL, IDLE, REJECTED, SKIPPED }`; `data class RawFix(...)`; `data class StoredPoint(...)`; `fun displayablePoints(List<StoredPoint>): List<StoredPoint>`.

- [x] **Step 1: اختبار فاشل**
```kotlin
package dev.toufik.trailmark.core.model
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
class ModelTest {
  private fun p(t: WaypointType) = StoredPoint(0.0,0.0,0L,5.0,t)
  @Test fun dropsOnlyRejected() {
    val l = listOf(p(WaypointType.NORMAL), p(WaypointType.REJECTED), p(WaypointType.IDLE))
    assertEquals(listOf(WaypointType.NORMAL, WaypointType.IDLE), displayablePoints(l).map { it.type })
  }
  @Test fun emptyStaysEmpty() = assertTrue(displayablePoints(emptyList()).isEmpty())
}
```
- [x] **Step 2: شغّل وافشل** — `./gradlew :core:model:test`.
- [x] **Step 3: التنفيذ**
```kotlin
package dev.toufik.trailmark.core.model
enum class WaypointType { NORMAL, IDLE, REJECTED, SKIPPED }
data class RawFix(val lat: Double, val lon: Double, val timeNanos: Long,
  val accuracyM: Double, val altitudeM: Double? = null, val speedMps: Double? = null,
  val bearingDeg: Double? = null, val isMocked: Boolean = false, val wallMillis: Long = 0L)
data class StoredPoint(val lat: Double, val lon: Double, val timeNanos: Long,
  val accuracyM: Double, val type: WaypointType, val altitudeM: Double? = null,
  val speedMps: Double? = null, val bearingDeg: Double? = null)
fun displayablePoints(points: List<StoredPoint>) = points.filter { it.type != WaypointType.REJECTED }
```
- [x] **Step 4: PASS** — `./gradlew :core:model:test`.
- [x] **Step 5: commit** — `feat: core:model domain types + displayablePoints`.
---
## Task 4: هيكل Gradle والكتالوج والـ CI

**Files:** Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `gradle/wrapper/gradle-wrapper.properties`, `.github/workflows/ci.yml`.

- [x] **Step 1: كتالوج الإصدارات** — `gradle/libs.versions.toml`
```toml
[versions]
kotlin = "2.0.21"
agp = "8.7.3"
junit = "5.10.2"
[libraries]
junit-jupiter = { module = "org.junit.jupiter:junit-jupiter", version.ref = "junit" }
[plugins]
android-library = { id = "com.android.library", version.ref = "agp" }
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
```
- [x] **Step 2: `settings.gradle.kts`** — `pluginManagement`/`dependencyResolutionManagement` (google, mavenCentral)، `include(":core:model", ":core:geo", ":core:time")`.
- [x] **Step 3: وحدة JVM نموذجية** — مثال `core/time/build.gradle.kts`
```kotlin
plugins { `kotlin-jvm` }
kotlin { jvmToolchain(17) }
dependencies { testImplementation(libs.junit.jupiter) }
tasks.test { useJUnitPlatform() }
```
(نفس النمط لـ `core/geo`, `core/model`.)
- [x] **Step 4: wrapper** — `gradle wrapper --gradle-version 8.9`.
- [x] **Step 5: CI** — `.github/workflows/ci.yml`: JDK 17، `./gradlew test --no-daemon`.
- [x] **Step 6: تحقّق** — `./gradlew test` ⇒ BUILD SUCCESSFUL (اختبارات الوحدات الثلاث تمرّ).
- [x] **Step 7: commit** — `build: gradle multi-module skeleton + CI`.

## Self-Review
- تغطية المواصفة: §4 (الوحدات)، §5.1 جزئياً (geo/time/model)، §10 (اختبار JVM)، §11 P0.
- لا placeholders. الأنواع متسقة عبر المهام.
- Review Focus: كل بند له اختبار في مهمّة تملك كوده.

