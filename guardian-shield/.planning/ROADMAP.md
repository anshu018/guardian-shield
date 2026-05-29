# Roadmap: Guardian Shield

## Overview

A robust, 5-phase execution roadmap designed to build, secure, and validate a highly resilient child monitoring system. The execution focuses first on the critical background layer (ensuring battery recovery on cheap OEM ROMs), followed by realtime mapping integration, P2P WebRTC ambient checks, zero-connectivity SMS fallbacks, and production-grade security constraints.

---

## Phases

- [ ] **Phase 1: Service Survival & Foundation** - Make services completely bulletproof on aggressive Indian OEM devices (Xiaomi, Realme, Vivo).
- [ ] **Phase 2: Location Tracking & Supabase Auth** - Realtime location tracking on OSMDroid with secure Supabase Phone OTP.
- [ ] **Phase 3: WebRTC P2P Ambient Check** - Ambient audio/video monitoring over low-bandwidth constraints (<500KB/s).
- [ ] **Phase 4: Offline SOS Fallback** - SmsManager signaling for direct broadcast when cellular data is disconnected.
- [ ] **Phase 5: Production Hardening & ProGuard** - DeviceAdmin integration, app stealth mode, and <8MB APK optimization.

---

## Phase Details

### Phase 1: Service Survival & Foundation
**Goal**: Ensure Child foreground services remain sticky, recover automatically from task kills, and run periodic watchdogs.
**Depends on**: Nothing
**Requirements**: REQ-01 (Service Survival Watchdog)
**Success Criteria**:
  1. Child foreground service automatically restarts within 5 seconds of parent task removal.
  2. BootReceiver triggers on both `BOOT_COMPLETED` and `QUICKBOOT_POWERON`.
  3. WorkManager watchdog runs a connectivity and service alive check every 15 minutes.
**Plans**: 2 plans

Plans:
- [ ] 01-01: Implement BootReceiver, Sticky Foreground Service, and AlarmManager restart loop.
- [ ] 01-02: Implement WorkManager watchdog worker and Service survival status checker.

### Phase 2: Location Tracking & Supabase Auth
**Goal**: Build parents' auth gateway and render Child's realtime location coordinates on parent's maps.
**Depends on**: Phase 1
**Requirements**: REQ-02, REQ-03
**Success Criteria**:
  1. Parent logins successfully via Supabase Phone OTP auth.
  2. Child app polls GPS and streams location to Supabase with adaptive interval (10s normal / 30s stationary).
  3. Parent app renders child's geofences and path on OSMDroid map views.
**Plans**: 2 plans

Plans:
- [ ] 02-01: Integrate Supabase Phone OTP authentication UI and repository layer.
- [ ] 02-02: Configure OSMDroid map view and geofence tracking with adaptive location polling.

### Phase 3: WebRTC P2P Ambient Check
**Goal**: Set up ambient audio/video check using direct peer-to-peer streaming.
**Depends on**: Phase 2
**Requirements**: REQ-04
**Success Criteria**:
  1. Parent app initiates an ambient check, sending signaling message via Node.js Railway server.
  2. Child app grabs screen/camera via MediaProjection and streams over WebRTC.
  3. WebRTC stream runs smoothly under a target bandwidth limit of 500KB/s.
**Plans**: 2 plans

Plans:
- [ ] 03-01: Deploy Signaling server and configure WebRTC handshake client interfaces.
- [ ] 03-02: Build ambient video and audio P2P projection streaming.

### Phase 4: Offline SOS Fallback
**Goal**: Broadcast SMS alerts when cellular data connectivity drops to 0%.
**Depends on**: Phase 3
**Requirements**: REQ-05
**Success Criteria**:
  1. Child app detects internet disconnection.
  2. Pressing Child SOS button broadcasts SMS with last known geocoordinates to registered Parent phone numbers.
**Plans**: 1 plan

Plans:
- [ ] 04-01: Implement SmsManager broadcasting and automated connection-drop detection.

### Phase 5: Production Hardening & ProGuard
**Goal**: Enforce extreme stealth and security features to prevent child uninstalls and limit APK size.
**Depends on**: Phase 4
**Success Criteria**:
  1. Child app sets DeviceAdmin uninstall blockage requiring parent PIN to deactivate.
  2. Child app icon is hidden from launcher list after initial registration.
  3. ProGuard minimizes final child APK size to under 8MB.
**Plans**: 2 plans

Plans:
- [ ] 05-01: Build DeviceAdminReceiver, launcher component deactivation, and PIN validation.
- [ ] 05-02: Optimize assets, configure ProGuard rules, and verify final build size.

---

## Progress

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Service Survival | 0/2 | Not started | - |
| 2. Location Tracking | 0/2 | Not started | - |
| 3. WebRTC Ambient Check | 0/2 | Not started | - |
| 4. Offline SOS Fallback | 0/1 | Not started | - |
| 5. Production Hardening | 0/2 | Not started | - |
