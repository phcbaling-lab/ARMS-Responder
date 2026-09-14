# ARMS API Integration Contract

Base URL example:

https://your-arms-server.example/api/v1

## Login

POST /auth/login

{
  "username": "responder01",
  "password": "secret"
}

Response:

{
  "accessToken": "...",
  "refreshToken": "...",
  "user": {
    "id": "USR-001",
    "name": "Responder",
    "role": "RESPONDER",
    "station": "Baling"
  }
}

## Active incident

GET /incidents/active

Response:

{
  "id": "INC-2026-00125",
  "priority": "RED",
  "emergencyType": "Medical Emergency",
  "callerName": "Ahmad bin Ali",
  "phone": "012-3456789",
  "address": "Baling, Kedah",
  "latitude": 5.6789,
  "longitude": 100.9123,
  "notes": "Patient reported unconscious.",
  "status": "DISPATCHED"
}

## Update incident

POST /incidents/{incidentId}/status

{
  "status": "EN_ROUTE"
}

## Location

POST /ambulances/{ambulanceId}/location

{
  "latitude": 5.6789,
  "longitude": 100.9123,
  "timestamp": 1789360000000
}

## FCM token

POST /devices/fcm-token

{
  "token": "FCM_DEVICE_TOKEN",
  "platform": "ANDROID"
}
