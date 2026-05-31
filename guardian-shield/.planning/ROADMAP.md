# Roadmap: Guardian Shield

## Overview

A robust, multi-layer development roadmap for the dual-app native Android ecosystem (Parent App & Child App) built using Kotlin, Clean Architecture, and Supabase. The system is designed to provide real-time location tracking, offline SOS fallbacks, and low-latency P2P ambient streaming, specifically optimized to survive on budget Android devices.

---

## Development Layers (L0 to L17)

### ✅ L0 - Documentation
- **Status**: `[COMPLETE]`
- **Goal**: Establish the PRD, architecture definitions, WebRTC streaming design, and database schemas.

### ✅ L1 - Project Scaffold
- **Status**: `[COMPLETE]`
- **Goal**: Set up dual-app modules (`:child-app` and `:parent-app`), Hilt dependency injection, KSP, and gradle build structures. Both apps build successfully.

### ✅ L2 - DB Schema & Supabase Auth
- **Status**: `[COMPLETE]`
- **Goal**: Configure Supabase client, tables (`families`, `parents`, `children`, etc.), RLS policies, and Phone OTP auth login flows.

### ✅ L3 - GPS Telemetry
- **Status**: `[COMPLETE]`
- **Goal**: Build `LocationTrackingService` with background GPS polling and DataStore offline coordinate caching.

### ✅ L4 - Background Service Survival
- **Status**: `[COMPLETE]`
- **Goal**: Implement the 3-layer foreground service watchdog system (sticky intents, AlarmManager restarts, WorkManager connectivity checks).

### ✅ L5 - Device Admin
- **Status**: `[COMPLETE]`
- **Goal**: Integrate `DeviceAdminReceiver` inside the child app manifest to enforce remote lock and block uninstallation.

### ✅ L6 - App Monitor Service
- **Status**: `[COMPLETE]`
- **Goal**: Poll foreground package names via `UsageStatsManager` and overlay parental lock screens on blocked apps.

### ✅ L7 - Emergency SOS Engine
- **Status**: `[COMPLETE]`
- **Goal**: Implement low-battery location polling, stationary detection algorithm, and encrypted SMS fallback transmission.

### ✅ L8 - WebRTC Signaling
- **Status**: `[COMPLETE]`
- **Goal**: Establish connection handshake through Railway.app Node.js Socket.io signaling server.

### ✅ L9 - ScreenCaptureService
- **Status**: `[COMPLETE]`
- **Goal**: Implement custom WebRTC VideoCapturer using `AccessibilityService.takeScreenshot()` for permanent, silent screen streaming without MediaProjection.

### ✅ L10 - Parent OSMDroid Live Map
- **Status**: `[COMPLETE]`
- **Goal**: Integrate offline OSMDroid maps inside the parent app to render real-time geofence circles and movement trails.

### ✅ L11 - Parent WebRTC Live Screen Viewer
- **Status**: `[COMPLETE]`
- **Goal**: Embed WebRTC `SurfaceViewRenderer` in parent dashboard with adaptive bitrate controls and Open Relay TURN configurations.

### ✅ L12 - Monitoring Tab
- **Status**: `[COMPLETE]`
- **Goal**: Securely query child call logs, SMS previews, contacts list, and today's app usage graphs.

### ✅ L13 - Remote Controls Panel
- **Status**: `[COMPLETE]`
- **Goal**: Add controls to trigger remote LOCK, Siren Alarm, App Blocks, and Toast Messages.

### ✅ L14 - Emergency SOS Alert Activity
- **Status**: `[COMPLETE]`
- **Goal**: Create lockscreen-override full-screen SOS activity in the parent app with a looping siren alarm.

### ✅ L15 - Setup Wizard
- **Status**: `[COMPLETE]`
- **Goal**: Build the 6-step runtime permission flow, Accessibility service guidance, Device Admin activation, and stealth launcher hiding.

### ✅ L16 - Device Linking
- **Status**: `[COMPLETE]`
- **Goal**: Implement a 6-digit UPI-style linking pin generator and validator inside Jetpack DataStore.

### 🔲 L17 - Real Device Testing
- **Status**: `[TODO]`
- **Goal**: Run end-to-end telemetry field testing and measure device battery impact.
