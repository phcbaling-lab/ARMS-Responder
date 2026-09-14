# ARMS Responder Android App

Ambulance Response Management System (ARMS) responder-side Android starter application.

## Included

- Kotlin + Jetpack Compose
- Login screen
- Responder dashboard
- Ambulance duty status
- Active emergency incident
- Dispatch acceptance
- Response state machine
- Navigation hand-off to Google Maps
- Foreground location tracking service
- Firebase Cloud Messaging service
- Repository layer ready for REST API integration
- MVVM-style ViewModel/state flow

## Demo login

Any non-empty username and password work because this build uses an in-memory demo repository.

## Open in Android Studio

1. Extract this ZIP.
2. Open the `ARMSResponder` folder in Android Studio.
3. Let Gradle sync.
4. Run on an Android emulator or physical Android device.

## Before production

This project deliberately uses a demo repository. Replace `ARMSRepository` with the real ARMS REST API.

Expected backend endpoints:

POST   /api/v1/auth/login
GET    /api/v1/responders/me
GET    /api/v1/ambulances/me
GET    /api/v1/incidents/active
POST   /api/v1/incidents/{id}/accept
POST   /api/v1/incidents/{id}/status
POST   /api/v1/ambulances/{id}/location
POST   /api/v1/devices/fcm-token

Recommended production additions:

- HTTPS only
- JWT access + refresh tokens
- encrypted token storage
- certificate/network security configuration
- server-side authorization
- audit logging
- offline queue and retry
- encrypted local incident cache
- proper FCM server integration
- Google Maps/Navigation configuration
- crash reporting
- automated tests
- privacy/consent and medical-data security review

## Firebase

Add your own Firebase project and place the Firebase-generated
`google-services.json` inside `app/`.

Then apply the Google Services Gradle plugin and Firebase dependencies
according to the Firebase Android setup documentation.

## Location

The included foreground service is intended for active ambulance
response tracking. Request location permission from the UI before
starting it. Android versions 14+ require the appropriate foreground
service location permission/type, and modern Android versions impose
restrictions on starting location foreground services from the
background.

## Architecture

UI -> ViewModel -> Repository -> API

Location:
Android Location Services -> LocationTrackingService -> ARMS API

Push:
ARMS Backend -> Firebase Cloud Messaging -> Responder device

## Next implementation phase

Connect the repository to the ARMS PostgreSQL-backed REST API and add:
- real authentication
- dispatcher assignments
- patient assessment form
- hospital selection
- ambulance crew management
- incident timeline
- offline mode
- real-time WebSocket status
- audit trail
- attachments/photos where clinically and legally appropriate
