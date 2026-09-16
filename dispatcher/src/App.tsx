import { useEffect, useState } from "react"
import "./App.css"
import {
  dispatchIncident,
  getActiveIncidents,
  getAmbulances,
  getLatestAmbulanceLocations,
} from "./lib/armsApi"
import OperationsMap from "./components/OperationsMap"
import { supabase } from "./lib/supabase"
import type {
  Ambulance,
  AmbulanceLocation,
  Incident,
} from "./types/arms"

type Profile = {
  id: string
  full_name: string
  role: string
  is_active: boolean
}

function App() {
  const [sessionReady, setSessionReady] = useState(false)
  const [session, setSession] = useState<unknown>(null)

  const [email, setEmail] = useState("")
  const [password, setPassword] = useState("")
  const [loginLoading, setLoginLoading] = useState(false)
  const [loginError, setLoginError] = useState<string | null>(null)

  const [profile, setProfile] = useState<Profile | null>(null)

  const [incidents, setIncidents] = useState<Incident[]>([])
  const [ambulances, setAmbulances] = useState<Ambulance[]>([])
  const [locations, setLocations] = useState<AmbulanceLocation[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const [selectedIncidentId, setSelectedIncidentId] = useState<string | null>(null)
  const [selectedAmbulanceId, setSelectedAmbulanceId] = useState<string | null>(null)
  const [dispatchLoading, setDispatchLoading] = useState(false)
  const [dispatchMessage, setDispatchMessage] = useState<string | null>(null)

  async function loadProfile() {
    const {
      data: { user },
    } = await supabase.auth.getUser()

    if (!user) {
      setSession(null)
      setProfile(null)
      return
    }

    const { data, error } = await supabase
      .from("profiles")
      .select("id, full_name, role, is_active")
      .eq("id", user.id)
      .single()

    if (error) throw error

    const currentProfile = data as Profile

    if (
      !currentProfile.is_active ||
      ![
        "DISPATCHER",
        "SUPERVISOR",
        "ADMINISTRATOR",
      ].includes(currentProfile.role)
    ) {
      await supabase.auth.signOut()

      throw new Error(
        "This account is not authorized to access the Dispatcher Dashboard.",
      )
    }

    setSession(user)
    setProfile(currentProfile)
  }

  async function loadDashboard() {
    try {
      setLoading(true)
      setError(null)

      const [
        incidentData,
        ambulanceData,
        locationData,
      ] = await Promise.all([
        getActiveIncidents(),
        getAmbulances(),
        getLatestAmbulanceLocations(),
      ])

      setIncidents(incidentData)
      setAmbulances(ambulanceData)
      setLocations(locationData)
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : "Unable to load ARMS dashboard",
      )
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    let mounted = true

    async function initialize() {
      try {
        const {
          data: { session },
        } = await supabase.auth.getSession()

        if (!session) {
          if (mounted) {
            setSession(null)
            setProfile(null)
            setSessionReady(true)
          }
          return
        }

        await loadProfile()

        if (mounted) {
          setSessionReady(true)
        }
      } catch (err) {
        if (mounted) {
          setLoginError(
            err instanceof Error
              ? err.message
              : "Unable to restore Dispatcher session",
          )
          setSession(null)
          setProfile(null)
          setSessionReady(true)
        }
      }
    }

    initialize()

    const {
      data: { subscription },
    } = supabase.auth.onAuthStateChange(
      async (_event, session) => {
        if (!mounted) return

        if (!session) {
          setSession(null)
          setProfile(null)
          return
        }

        try {
          await loadProfile()
        } catch (err) {
          setLoginError(
            err instanceof Error
              ? err.message
              : "Unable to load Dispatcher profile",
          )
          setSession(null)
          setProfile(null)
        }
      },
    )

    return () => {
      mounted = false
      subscription.unsubscribe()
    }
  }, [])

    useEffect(() => {
    if (session && profile) {
      loadDashboard()
    }
  }, [session, profile])

  useEffect(() => {
    if (!session || !profile) {
      return
    }

    const channel = supabase
      .channel("arms-dispatcher-live")
      .on(
        "postgres_changes",
        {
          event: "*",
          schema: "public",
          table: "incidents",
        },
        () => {
          loadDashboard()
        },
      )
      .on(
        "postgres_changes",
        {
          event: "*",
          schema: "public",
          table: "ambulances",
        },
        () => {
          loadDashboard()
        },
      )
      .on(
        "postgres_changes",
        {
          event: "INSERT",
          schema: "public",
          table: "ambulance_locations",
        },
        (payload) => {
          console.log("ARMS REALTIME LOCATION EVENT:", payload)
          loadDashboard()
        },
      )
      .subscribe((status) => {
        console.log("ARMS REALTIME SUBSCRIPTION STATUS:", status)
      })

    return () => {
      supabase.removeChannel(channel)
    }
  }, [session, profile])

  async function handleLogin(
    event: React.FormEvent<HTMLFormElement>,
  ) {
    event.preventDefault()

    try {
      setLoginLoading(true)
      setLoginError(null)

      const { error } = await supabase.auth.signInWithPassword({
        email: email.trim(),
        password,
      })

      if (error) {
        throw error
      }

      await loadProfile()
    } catch (err) {
      setLoginError(
        err instanceof Error
          ? err.message
          : "Login failed",
      )
    } finally {
      setLoginLoading(false)
    }
  }

  async function handleLogout() {
    await supabase.auth.signOut()

    setSession(null)
    setProfile(null)
    setIncidents([])
    setAmbulances([])
    setLocations([])
    setError(null)
    setSelectedIncidentId(null)
    setSelectedAmbulanceId(null)
    setDispatchMessage(null)
  }

  async function handleDispatch() {
    if (!selectedIncidentId) {
      setDispatchMessage("Select an incident first.")
      return
    }

    if (!selectedAmbulanceId) {
      setDispatchMessage("Select an AVAILABLE ambulance first.")
      return
    }

    try {
      setDispatchLoading(true)
      setDispatchMessage(null)
      setError(null)

      await dispatchIncident(
        selectedIncidentId,
        selectedAmbulanceId,
      )

      setDispatchMessage(
        "Dispatch created and responder notification sent.",
      )

      setSelectedIncidentId(null)
      setSelectedAmbulanceId(null)

      await loadDashboard()
    } catch (err) {
      setDispatchMessage(
        err instanceof Error
          ? err.message
          : "Unable to dispatch ambulance.",
      )
    } finally {
      setDispatchLoading(false)
    }
  }

  if (!sessionReady) {
    return (
      <div className="login-page">
        <div className="login-card">
          <div className="brand">ARMS</div>
          <p>Loading Dispatcher session...</p>
        </div>
      </div>
    )
  }

  if (!session || !profile) {
    return (
      <div className="login-page">
        <form
          className="login-card"
          onSubmit={handleLogin}
        >
          <div className="login-brand">
            <div className="brand">ARMS</div>
            <div className="subtitle">
              Ambulance Response Management System
            </div>
          </div>

          <div className="login-title">
            Dispatcher Login
          </div>

          <p className="login-description">
            Sign in with your authorized ARMS account.
          </p>

          {loginError && (
            <div className="error-banner">
              {loginError}
            </div>
          )}

          <label className="field-label">
            EMAIL
          </label>

          <input
            className="login-input"
            type="email"
            value={email}
            onChange={(event) =>
              setEmail(event.target.value)
            }
            autoComplete="username"
            placeholder="Enter email"
            required
          />

          <label className="field-label">
            PASSWORD
          </label>

          <input
            className="login-input"
            type="password"
            value={password}
            onChange={(event) =>
              setPassword(event.target.value)
            }
            autoComplete="current-password"
            placeholder="Enter password"
            required
          />

          <button
            className="login-button"
            type="submit"
            disabled={loginLoading}
          >
            {loginLoading
              ? "SIGNING IN..."
              : "SIGN IN"}
          </button>
        </form>
      </div>
    )
  }

  const availableCount = ambulances.filter(
    (ambulance) => ambulance.status === "AVAILABLE",
  ).length

  const activeCount = incidents.length

  return (
    <div className="arms-app">
      <header className="topbar">
        <div>
          <div className="brand">ARMS</div>
          <div className="subtitle">
            Ambulance Response Management System
          </div>
        </div>

        <div className="dispatcher-info">
          <span className="live-dot" />
          <span>
            {profile.full_name} · {profile.role}
          </span>

          <button
            className="logout-button"
            onClick={handleLogout}
          >
            LOGOUT
          </button>
        </div>
      </header>

      <main className="dashboard">
        <section className="page-header">
          <div>
            <h1>Dispatcher Dashboard</h1>
            <p>
              Monitor ambulance availability, active incidents,
              and responder locations.
            </p>
          </div>

          <button
            className="refresh-button"
            onClick={loadDashboard}
            disabled={loading}
          >
            {loading ? "LOADING..." : "REFRESH"}
          </button>
        </section>

        {error && (
          <div className="error-banner">
            <strong>Connection error:</strong> {error}
          </div>
        )}

        <section className="stats-grid">
          <div className="stat-card">
            <span className="stat-label">
              ACTIVE INCIDENTS
            </span>
            <strong>
              {loading ? "—" : activeCount}
            </strong>
          </div>

          <div className="stat-card">
            <span className="stat-label">
              AMBULANCES
            </span>
            <strong>
              {loading ? "—" : ambulances.length}
            </strong>
          </div>

          <div className="stat-card">
            <span className="stat-label">
              AVAILABLE
            </span>
            <strong>
              {loading ? "—" : availableCount}
            </strong>
          </div>

          <div className="stat-card">
            <span className="stat-label">
              LIVE LOCATIONS
            </span>
            <strong>
              {loading ? "—" : locations.length}
            </strong>
          </div>
        </section>

        <section className="content-grid">
          <div className="panel">
            <div className="panel-header">
              <h2>Active Incidents</h2>
              <span>{incidents.length}</span>
            </div>

            {loading ? (
              <div className="empty-state">
                Loading incidents...
              </div>
            ) : incidents.length === 0 ? (
              <div className="empty-state">
                No active incidents.
              </div>
            ) : (
              <div className="incident-list">
                {incidents.map((incident) => (
                  <div
                    className={`incident-card ${
                      selectedIncidentId === incident.id
                        ? "incident-card-selected"
                        : ""
                    }`}
                    key={incident.id}
                    onClick={() => {
                      setSelectedIncidentId(incident.id)
                      setDispatchMessage(null)
                    }}
                  >
                    <div className="incident-top">
                      <strong>
                        {incident.incident_number}
                      </strong>

                      <span
                        className={`priority priority-${incident.priority.toLowerCase()}`}
                      >
                        {incident.priority}
                      </span>
                    </div>

                    <div className="incident-type">
                      {incident.incident_type}
                    </div>

                    <div className="incident-status">
                      STATUS: {incident.status}
                    </div>

                    <div className="incident-location">
                      {incident.incident_address ??
                        "Location unavailable"}
                    </div>

                    {selectedIncidentId === incident.id && (
                      <div className="selection-label">
                        SELECTED
                      </div>
                    )}
                  </div>
                ))}
              </div>
            )}
          </div>

          <div className="panel">
            <div className="panel-header">
              <h2>Ambulance Fleet</h2>
              <span>{ambulances.length}</span>
            </div>

            {loading ? (
              <div className="empty-state">
                Loading ambulances...
              </div>
            ) : ambulances.length === 0 ? (
              <div className="empty-state">
                No ambulances found.
              </div>
            ) : (
              <div className="ambulance-list">
                {ambulances.map((ambulance) => {
                  const location = locations.find(
                    (item) =>
                      item.ambulance_id === ambulance.id,
                  )

                  return (
                    <div
                      className={`ambulance-row ${
                        selectedAmbulanceId === ambulance.id
                          ? "ambulance-row-selected"
                          : ""
                      }`}
                      key={ambulance.id}
                      onClick={() => {
                        if (ambulance.status === "AVAILABLE") {
                          setSelectedAmbulanceId(ambulance.id)
                          setDispatchMessage(null)
                        }
                      }}
                    >
                      <div>
                        <strong>
                          {ambulance.vehicle_number}
                        </strong>

                        <div className="ambulance-location">
                          {location
                            ? `${location.latitude.toFixed(5)}, ${location.longitude.toFixed(5)}`
                            : "No live location"}
                        </div>
                      </div>

                      <span
                        className={`status status-${ambulance.status.toLowerCase()}`}
                      >
                        {ambulance.status}
                      </span>
                    </div>
                  )
                })}
              </div>
            )}
          </div>
        </section>

        <section className="panel">
        <div className="panel-header">
          <div>
            <h2>Operations Map</h2>
            <p>Live ambulance and incident locations</p>
          </div>
        </div>

        <OperationsMap
          ambulances={ambulances}
          locations={locations}
          incidents={incidents}
        />
      </section>

      <section className="panel dispatch-panel">
          <div className="panel-header">
            <h2>Dispatch Ambulance</h2>
          </div>

          <div className="dispatch-controls">
            <div className="dispatch-selection">
              <strong>INCIDENT</strong>
              <div>
                {selectedIncidentId
                  ? incidents.find(
                      (incident) =>
                        incident.id === selectedIncidentId,
                    )?.incident_number ?? "Selected incident"
                  : "Select an incident above"}
              </div>
            </div>

            <div className="dispatch-selection">
              <strong>AMBULANCE</strong>
              <div>
                {selectedAmbulanceId
                  ? ambulances.find(
                      (ambulance) =>
                        ambulance.id === selectedAmbulanceId,
                    )?.vehicle_number ?? "Selected ambulance"
                  : "Select an AVAILABLE ambulance above"}
              </div>
            </div>

            <button
              className="dispatch-button"
              onClick={handleDispatch}
              disabled={
                dispatchLoading ||
                !selectedIncidentId ||
                !selectedAmbulanceId
              }
            >
              {dispatchLoading
                ? "DISPATCHING..."
                : "DISPATCH"}
            </button>
          </div>

          {dispatchMessage && (
            <div className="dispatch-message">
              {dispatchMessage}
            </div>
          )}
        </section>
      </main>
    </div>
  )
}

export default App