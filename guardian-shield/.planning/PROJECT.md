# Project: Guardian Shield

## Overview

Guardian Shield is an advanced, robust, and highly optimized child safety monitoring system tailored for Indian parents with children under 10 years of age. Developed for the Smart India Hackathon (SIH), the system consists of two separate native Android apps (Child app: `com.guardianshield.child`, Parent app: `com.guardianshield.parent`) built entirely in Kotlin using modern MVVM + Clean Architecture principles.

### Core Value

**Realtime child safety monitoring that stays alive on ₹8,000 Android phones, operates seamlessly in low-internet settings, and ensures direct parent-child P2P connectivity.**

---

## Technical Stack

* **Backend**: Supabase (Postgres + Realtime + Auth + Storage)
* **Auth**: Supabase Phone OTP (Phone auth only)
* **Maps**: OSMDroid (Offline-friendly OpenStreetMap rendering)
* **Streaming**: WebRTC P2P ambient checks via MediaProjection
* **Signaling**: Node.js + Socket.io deployed on Railway.app
* **Alerts/Push**: Supabase Realtime (No Firebase dependency)
* **SOS Fallback**: Android SMS Manager (Offline SOS signaling)
* **Dependency Injection**: Hilt (MVVM Clean Architecture)
* **Local Storage**: Jetpack DataStore (No SharedPreferences)
* **Concurrency**: Kotlin Coroutines + Flow

---

## Requirements

### Validated (Existing in Codebase)

* ✓ Multimodule Gradle structure with `parent-app` and `child-app`
* ✓ Hilt dependency injection setup
* ✓ Clean MVVM project structural boundaries

### Active (To Be Implemented)

* **REQ-01: Service Survival Watchdog**: Foreground services running sticky, recovering automatically on Xiaomi, Realme, and Vivo devices via AlarmManager and WorkManager.
* **REQ-02: Realtime GPS & Geofencing**: Dynamic location polling (10s default, 5s SOS, 30s stationary) rendering on OSMDroid map layer.
* **REQ-03: Supabase Phone OTP**: Secure authentication flow for parents using phone number verification.
* **REQ-04: Ambient Audio/Video WebRTC**: Realtime ambient check stream using WebRTC MediaProjection over low bandwidth (<500KB/s target).
* **REQ-05: Offline SMS SOS**: Automatic location broadcasting using SmsManager fallback when cellular data is disconnected.

### Out of Scope

* Third-party paid maps (Google Maps, Mapbox)
* Firebase Cloud Messaging (FCM)
* Email / Social Sign-In

---

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Kotlin-Only | Complete code safety and modern API usage; strict ban on Java files | Enforced |
| Supabase Realtime Alerts | Bypasses Google Services limits on cheap OEM devices with broken FCM | Enforced |
| WebRTC Frame Targeting | Limit streaming to 500KB/s to survive typical Indian mobile network speeds | Enforced |
| Jetpack DataStore | Thread-safe, non-blocking disk I/O for persistent states | Enforced |

---
*Last updated: 2026-05-29 after autonomous system initialization*
