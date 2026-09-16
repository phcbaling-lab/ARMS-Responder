import { supabase } from "./supabase"
import type {
  Ambulance,
  AmbulanceLocation,
  Incident,
} from "../types/arms"

export async function getActiveIncidents(): Promise<Incident[]> {
  const { data, error } = await supabase
    .from("incidents")
    .select("*")
    .neq("status", "COMPLETED")
    .order("created_at", { ascending: false })

  if (error) throw error

  return data as Incident[]
}

export async function getAmbulances(): Promise<Ambulance[]> {
  const { data, error } = await supabase
    .from("ambulances")
    .select("*")
    .order("vehicle_number")

  if (error) throw error

  return data as Ambulance[]
}

export async function getLatestAmbulanceLocations(): Promise<AmbulanceLocation[]> {
  const { data, error } = await supabase
    .from("ambulance_locations")
    .select("*")
    .order("recorded_at", { ascending: false })
    .limit(100)

  if (error) throw error

  const latest = new Map<string, AmbulanceLocation>()

  for (const location of data as AmbulanceLocation[]) {
    if (!latest.has(location.ambulance_id)) {
      latest.set(location.ambulance_id, location)
    }
  }

  return Array.from(latest.values())
}

export async function getDashboardData() {
  const [incidents, ambulances, locations] = await Promise.all([
    getActiveIncidents(),
    getAmbulances(),
    getLatestAmbulanceLocations(),
  ])

  return {
    incidents,
    ambulances,
    locations,
  }
}

export async function dispatchIncident(
  incidentId: string,
  ambulanceId: string,
): Promise<void> {
  const {
    data: { user },
    error: userError,
  } = await supabase.auth.getUser()

  if (userError) throw userError

  if (!user) {
    throw new Error("Dispatcher session is not available.")
  }

  const { data: crew, error: crewError } = await supabase
    .from("ambulance_crews")
    .select("member_id, is_team_leader")
    .eq("ambulance_id", ambulanceId)
    .is("active_until", null)
    .order("is_team_leader", { ascending: false })
    .limit(1)
    .maybeSingle()

  if (crewError) throw crewError

  if (!crew?.member_id) {
    throw new Error(
      "No active responder crew is assigned to this ambulance.",
    )
  }

  const { data: dispatch, error: dispatchError } = await supabase
  .from("dispatches")
  .insert({
    incident_id: incidentId,
    ambulance_id: ambulanceId,
    dispatcher_id: user.id,
    primary_responder_id: crew.member_id,
  })
  .select("id")
  .single()

if (dispatchError) {
  if (dispatchError.code === "23505") {
    throw new Error(
      "This incident already has an active dispatch.",
    )
  }

  throw dispatchError
}

  const { error: ambulanceError } = await supabase
    .from("ambulances")
    .update({
      status: "DISPATCHED",
    })
    .eq("id", ambulanceId)

  if (ambulanceError) throw ambulanceError

  const { error: incidentError } = await supabase
    .from("incidents")
    .update({
      status: "DISPATCHED",
    })
    .eq("id", incidentId)

  if (incidentError) throw incidentError

  const notificationResponse = await fetch(
  `${import.meta.env.VITE_SUPABASE_URL}/functions/v1/send-dispatch-notification`,
  {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      incident_id: incidentId,
    }),
  },
)

const notificationResult = await notificationResponse.json()

if (!notificationResponse.ok) {
  throw new Error(
    `Dispatch created, but notification failed: ${
      notificationResult?.error ?? "Unknown notification error"
    }`,
  )
}

if (!notificationResult?.success) {
  throw new Error(
    `Dispatch created, but notification failed: ${
      notificationResult?.error ?? "Unknown notification error"
    }`,
  )
}

  if (!dispatch?.id) {
    throw new Error("Dispatch was created without a dispatch ID.")
  }
}

