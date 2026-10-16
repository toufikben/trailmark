# Trailmark — خطة 02: نقل محرّك التسجيل

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development or superpowers:executing-plans. Steps use `- [ ]`.

**Goal:** نقل قواعد محرّك التسجيل من Dart إلى وحدة `:engine` نقية على JVM مع اختباراتها.

**Architecture:** وحدة Kotlin JVM بلا Android، تعتمد على `:core:model`, `:core:geo`, `:core:time`.

**Tech Stack:** Kotlin 2.0.21, JUnit5.

**Spec:** `docs/superpowers/specs/2026-10-16-trailmark-design.md` §5.1

## Global Constraints
- package `dev.toufik.trailmark.engine`؛ بلا اعتماد Android.
- الزمن أحادي فقط؛ لا حذف نقاط (المرفوضة تُحفظ موسومة).

## Review Focus
- زمن مساوٍ/راجع ⇒ يُرفض ولا يفسد المدة.
- أول نقطة ⇒ normal بلا مسافة.
- نبضة تحت البوابة ⇒ تُضاف بلا احتساب (R101).
- نقطة بعد رفض ⇒ تُحتسب من المِرساة غير الملموسة (R39).
- `computeSplits` على فارغ/نقطة واحدة ⇒ لا انهيار.

---
## Task 1: `SportConfig` + `GpsQuality`
**Files:** `engine/build.gradle.kts`, `SportConfig.kt`, `GpsQuality.kt` + tests.
**Produces:** `enum Sport { WALK, RUN, BIKE }`; `SportConfig(...).gateFor(Double)`; `forSport(Sport)`; `enum GpsQuality`; `GpsQualityX.fromAccuracy`; `isFixGoodEnough`; `UNMEASURED_ACCURACY_M`; `FixQualityGate(nowNanos).add/quality`.
- [ ] اختبارات: `max(3,2a)` بلا سقف؛ infinite ⇒ infinite gate؛ tiers؛ ر7 نقطتان؛ ر8 = 20s.
- [ ] تنفيذ ثم PASS ثم commit.

## Task 2: `RecorderEngine`
**Files:** `RecorderEngine.kt` (+ `EngineStats`, `RawFixSink`).
**Produces:** `RecorderEngine(config, startNanos, rawSink=null, resumeFrom=null, resumeDistanceM=0.0)`; `onFix(RawFix)`; `storedPoints`, `distanceMeters`, `elapsedNanos`, `isIdle`, `stats`.
- [ ] اختبارات: القواعد كاملة (بوابة، اندفاع، R39، R40، R41، خمول، نبضة، unmeasured، ترتيب زمني).
- [ ] تنفيذ ثم PASS ثم commit.

## Task 3: `Splits`
**Files:** `Splits.kt` + test.
**Produces:** `data class Split(...)`; `computeSplits(List<StoredPoint>): List<Split>`.
- [ ] اختبارات: 1km split صحيح؛ تخطّي المرفوض؛ مقاطع متعددة الحدود؛ صفر امتداد.
- [ ] تنفيذ ثم PASS ثم commit.
