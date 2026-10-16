# Trailmark — خطة 03: طبقة البيانات (SQLDelight)

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development or superpowers:executing-plans. Steps use `- [ ]`.

**Goal:** وحدة `:data` بمخطط SQLDelight + مستودع يطبّق قواعد التخزين، مُختبَرة على JVM.

**Architecture:** Kotlin JVM + SQLDelight؛ تعتمد على `:core:model` و`:engine`.

**Spec:** §5.4

## Review Focus
- الدقة غير المقاسة ⇒ NULL، وتُقرأ عائدة كـ sentinel.
- نقطة استئناف واحدة (upsert).
- استئناف لا يُكرّر النقاط.

---
## Task 1: الوحدة والمخطط
**Files:** `data/build.gradle.kts`; `data/src/main/sqldelight/.../Activity.sq`.
- [ ] جداول: activity, track_point, track_state, app_flag + استعلامات.
- [ ] commit.

## Task 2: المستودع
**Files:** `.../data/DataModule.kt`, `.../data/ActivityRepository.kt`.
- [ ] عمليات: insert/append/finish/pointCount/list/loadWithPoints/resume/flags.
- [ ] commit.

## Task 3: اختبارات JVM
**Files:** `data/src/test/kotlin/.../ActivityRepositoryTest.kt`.
- [ ] نقطة تُكتب وتُقرأ؛ استئناف؛ قائمة؛ علم؛ sentinel.
- [ ] PASS ثم commit.
