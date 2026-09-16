import {
  MapContainer,
  Marker,
  Popup,
  TileLayer,
} from "react-leaflet"
import L from "leaflet"
import "leaflet/dist/leaflet.css"

import type {
  Ambulance,
  AmbulanceLocation,
  Incident,
} from "../types/arms"

interface OperationsMapProps {
  ambulances: Ambulance[]
  locations: AmbulanceLocation[]
  incidents: Incident[]
}

function createAmbulanceIcon(status: Ambulance["status"]) {
  const active =
    status === "DISPATCHED" ||
    status === "EN_ROUTE" ||
    status === "ON_SCENE" ||
    status === "TRANSPORTING" ||
    status === "AT_HOSPITAL"

  return L.divIcon({
    className: "arms-map-marker",
    html: `<div class="ambulance-marker ${active ? "active" : ""}">🚑</div>`,
    iconSize: [40, 40],
    iconAnchor: [20, 20],
    popupAnchor: [0, -20],
  })
}

function createIncidentIcon(priority: Incident["priority"]) {
  return L.divIcon({
    className: "arms-map-marker",
    html: `<div class="incident-marker priority-${priority.toLowerCase()}">!</div>`,
    iconSize: [34, 34],
    iconAnchor: [17, 17],
    popupAnchor: [0, -17],
  })
}

export default function OperationsMap({
  ambulances,
  locations,
  incidents,
}: OperationsMapProps) {
  const latestLocations = new Map<string, AmbulanceLocation>()

  for (const location of locations) {
    const existing = latestLocations.get(location.ambulance_id)

    if (
      !existing ||
      new Date(location.recorded_at).getTime() >
        new Date(existing.recorded_at).getTime()
    ) {
      latestLocations.set(location.ambulance_id, location)
    }
  }

  const ambulanceLocations = ambulances
    .map((ambulance) => ({
      ambulance,
      location: latestLocations.get(ambulance.id),
    }))
    .filter(
      (item) =>
        item.location &&
        Number.isFinite(item.location.latitude) &&
        Number.isFinite(item.location.longitude),
    )

  const incidentLocations = incidents.filter(
    (incident) =>
      incident.latitude !== null &&
      incident.longitude !== null,
  )

  return (
    <div className="operations-map">
      <MapContainer
        center={[5.6804, 100.9000]}
        zoom={11}
        scrollWheelZoom
        className="operations-leaflet-map"
      >
        <TileLayer
          attribution="&copy; OpenStreetMap contributors"
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        />

        {ambulanceLocations.map(({ ambulance, location }) => {
          if (!location) return null

          return (
            <Marker
              key={`ambulance-${ambulance.id}`}
              position={[
                location.latitude,
                location.longitude,
              ]}
              icon={createAmbulanceIcon(ambulance.status)}
            >
              <Popup>
                <strong>{ambulance.vehicle_number}</strong>
                <br />
                Status: {ambulance.status}
                <br />
                GPS: {location.latitude.toFixed(6)},{" "}
                {location.longitude.toFixed(6)}
                <br />
                Accuracy:{" "}
                {location.accuracy_meters?.toFixed(1) ?? "—"} m
                <br />
                Speed:{" "}
                {location.speed_kmh?.toFixed(1) ?? "—"} km/h
                <br />
                Updated:{" "}
                {new Date(location.recorded_at).toLocaleTimeString()}
              </Popup>
            </Marker>
          )
        })}

        {incidentLocations.map((incident) => (
          <Marker
            key={`incident-${incident.id}`}
            position={[
              incident.latitude!,
              incident.longitude!,
            ]}
            icon={createIncidentIcon(incident.priority)}
          >
            <Popup>
              <strong>{incident.incident_number}</strong>
              <br />
              Priority: {incident.priority}
              <br />
              Type: {incident.incident_type}
              <br />
              Status: {incident.status}
              <br />
              {incident.incident_address ?? "No address"}
            </Popup>
          </Marker>
        ))}
      </MapContainer>
    </div>
  )
}
