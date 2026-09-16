export type IncidentStatus =
  | "DISPATCHED"
  | "ACCEPTED"
  | "EN_ROUTE"
  | "ARRIVED"
  | "TRANSPORTING"
  | "AT_HOSPITAL"
  | "COMPLETED"

export type IncidentPriority = "RED" | "YELLOW" | "GREEN"

export type AmbulanceStatus =
  | "AVAILABLE"
  | "DISPATCHED"
  | "EN_ROUTE"
  | "ON_SCENE"
  | "TRANSPORTING"
  | "AT_HOSPITAL"
  | "OUT_OF_SERVICE"
  | "OFFLINE"

export interface Incident {
  id: string
  incident_number: string
  priority: IncidentPriority
  incident_type: string
  status: IncidentStatus
  caller_name: string | null
  caller_phone: string | null
  incident_address: string | null
  latitude: number | null
  longitude: number | null
  notes: string | null
  created_at: string
}

export interface Ambulance {
  id: string
  vehicle_number: string
  status: AmbulanceStatus
  station_id: string | null
  is_active: boolean
}

export interface AmbulanceLocation {
  id: string
  ambulance_id: string
  responder_id: string
  latitude: number
  longitude: number
  accuracy_meters: number | null
  speed_kmh: number | null
  heading: number | null
  recorded_at: string
}
