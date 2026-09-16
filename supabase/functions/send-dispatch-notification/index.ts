const FIREBASE_SCOPE =
  "https://www.googleapis.com/auth/firebase.messaging";

const FIREBASE_TOKEN_URL =
  "https://oauth2.googleapis.com/token";

function base64UrlEncode(data: Uint8Array): string {
  let binary = "";
  for (const byte of data) {
    binary += String.fromCharCode(byte);
  }

  return btoa(binary)
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/, "");
}

function base64UrlEncodeString(value: string): string {
  return base64UrlEncode(
    new TextEncoder().encode(value)
  );
}

function pemToArrayBuffer(pem: string): ArrayBuffer {
  const base64 = pem
    .replace("-----BEGIN PRIVATE KEY-----", "")
    .replace("-----END PRIVATE KEY-----", "")
    .replace(/\s/g, "");

  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);

  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }

  return bytes.buffer;
}

async function createFirebaseAccessToken(
  serviceAccount: {
    client_email: string;
    private_key: string;
  }
): Promise<string> {

  const now = Math.floor(Date.now() / 1000);

  const header = {
    alg: "RS256",
    typ: "JWT"
  };

  const payload = {
    iss: serviceAccount.client_email,
    scope: FIREBASE_SCOPE,
    aud: FIREBASE_TOKEN_URL,
    iat: now,
    exp: now + 3600
  };

  const encodedHeader =
    base64UrlEncodeString(JSON.stringify(header));

  const encodedPayload =
    base64UrlEncodeString(JSON.stringify(payload));

  const unsignedToken =
    `${encodedHeader}.${encodedPayload}`;

  const privateKey =
    await crypto.subtle.importKey(
      "pkcs8",
      pemToArrayBuffer(serviceAccount.private_key),
      {
        name: "RSASSA-PKCS1-v1_5",
        hash: "SHA-256"
      },
      false,
      ["sign"]
    );

  const signature =
    await crypto.subtle.sign(
      "RSASSA-PKCS1-v1_5",
      privateKey,
      new TextEncoder().encode(unsignedToken)
    );

  const jwt =
    `${unsignedToken}.${base64UrlEncode(new Uint8Array(signature))}`;

  const response =
    await fetch(FIREBASE_TOKEN_URL, {
      method: "POST",
      headers: {
        "Content-Type":
          "application/x-www-form-urlencoded"
      },
      body:
        `grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer` +
        `&assertion=${encodeURIComponent(jwt)}`
    });

  if (!response.ok) {
    const errorText = await response.text();

    throw new Error(
      `Firebase OAuth token request failed: ${response.status} ${errorText}`
    );
  }

  const tokenData = await response.json();

  return tokenData.access_token;
}

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers":
    "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods":
    "POST, OPTIONS",
};

function jsonResponse(
  body: unknown,
  status = 200
): Response {
  return new Response(
    JSON.stringify(body),
    {
      status,
      headers: {
        ...corsHeaders,
        "Content-Type": "application/json"
      }
    }
  );
}

Deno.serve(async (req) => {

  if (req.method === "OPTIONS") {
    return new Response("ok", {
      status: 200,
      headers: corsHeaders,
    });
  }

  try {

    if (req.method !== "POST") {
      return jsonResponse(
        {
          success: false,
          error: "POST required"
        },
        405
      );
    }

    const body = await req.json();

    const incidentId = body?.incident_id;

    if (
      typeof incidentId !== "string" ||
      incidentId.trim() === ""
    ) {
      return jsonResponse(
        {
          success: false,
          error: "incident_id is required"
        },
        400
      );
    }

    const supabaseUrl =
      Deno.env.get("SUPABASE_URL");

    const serviceRoleKey =
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");

    const firebaseServiceAccountJson =
      Deno.env.get("FIREBASE_SERVICE_ACCOUNT");

    if (
      !supabaseUrl ||
      !serviceRoleKey ||
      !firebaseServiceAccountJson
    ) {
      throw new Error(
        "Required server environment variables are missing"
      );
    }

    const serviceAccount =
      JSON.parse(firebaseServiceAccountJson);

    const restHeaders = {
      apikey: serviceRoleKey,
      Authorization: `Bearer ${serviceRoleKey}`,
      "Content-Type": "application/json"
    };

    const dispatchResponse =
      await fetch(
        `${supabaseUrl}/rest/v1/dispatches` +
        `?select=id,incident_id,ambulance_id,primary_responder_id,dispatched_at,rejected_at,completed_at` +
        `&order=created_at.desc` +
        `&limit=10`,
        {
          headers: restHeaders
        }
      );

    if (!dispatchResponse.ok) {
      throw new Error(
        `Failed to query dispatch: ${dispatchResponse.status} ` +
        await dispatchResponse.text()
      );
    }

    const dispatches =
      await dispatchResponse.json();

    if (!Array.isArray(dispatches) || dispatches.length === 0) {
      return jsonResponse(
        {
          success: false,
          error: "No active dispatch found",
          incident_id: incidentId,
          dispatch_query_status: dispatchResponse.status,
          dispatch_query_result: dispatches
        },
        404
      );
    }

    const dispatch = dispatches.find(
      (item: {
        incident_id?: string;
        rejected_at?: string | null;
        completed_at?: string | null;
      }) =>
        item.incident_id === incidentId &&
        item.rejected_at == null &&
        item.completed_at == null
    );

    if (!dispatch) {
      return jsonResponse(
        {
          success: false,
          error: "No active dispatch found",
          incident_id: incidentId,
          dispatch_query_status: dispatchResponse.status,
          dispatch_query_result: dispatches
        },
        404
      );
    }

    const incidentResponse =
      await fetch(
        `${supabaseUrl}/rest/v1/incidents` +
        `?id=eq.${encodeURIComponent(incidentId)}` +
        `&select=id,incident_number,priority,incident_type,incident_address,incident_landmark,chief_complaint,latitude,longitude` +
        `&limit=1`,
        {
          headers: restHeaders
        }
      );

    if (!incidentResponse.ok) {
      throw new Error(
        `Failed to query incident: ${incidentResponse.status} ` +
        await incidentResponse.text()
      );
    }

    const incidents =
      await incidentResponse.json();

    if (!Array.isArray(incidents) || incidents.length === 0) {
      return jsonResponse(
        {
          success: false,
          error: "Incident not found",
          incident_id: incidentId,
          incident_query_status: incidentResponse.status,
          incident_query_result: incidents
        },
        404
      );
    }

    const incident = incidents[0];

    const deviceResponse =
      await fetch(
        `${supabaseUrl}/rest/v1/devices` +
        `?user_id=eq.${encodeURIComponent(dispatch.primary_responder_id)}` +
        `&is_active=eq.true` +
        `&select=id,device_token,device_name`,
        {
          headers: restHeaders
        }
      );

    if (!deviceResponse.ok) {
      throw new Error(
        `Failed to query responder devices: ${deviceResponse.status} ` +
        await deviceResponse.text()
      );
    }

    const devices =
      await deviceResponse.json();

    if (!Array.isArray(devices) || devices.length === 0) {
      return jsonResponse(
        {
          success: false,
          error: "No active responder device found",
          responder_id: dispatch.primary_responder_id
        },
        404
      );
    }

    const accessToken =
      await createFirebaseAccessToken(serviceAccount);

    const projectId =
      serviceAccount.project_id;

    if (
      typeof projectId !== "string" ||
      projectId.trim() === ""
    ) {
      throw new Error(
        "Firebase service account project_id is missing"
      );
    }

    const fcmUrl =
      `https://fcm.googleapis.com/v1/projects/${encodeURIComponent(projectId)}/messages:send`;

    const results = [];

    for (const device of devices) {

      const message = {
        message: {
          token: device.device_token,

          notification: {
            title: "🚑 ARMS DISPATCH",
            body:
              `${incident.incident_number} • ` +
              `${incident.priority} • ` +
              `${incident.incident_type}`
          },

          data: {
            type: "DISPATCH",
            incident_id: String(incident.id),
            dispatch_id: String(dispatch.id),
            incident_number:
              String(incident.incident_number ?? ""),
            priority:
              String(incident.priority ?? ""),
            incident_type:
              String(incident.incident_type ?? ""),
            incident_address:
              String(incident.incident_address ?? ""),
            incident_landmark:
              String(incident.incident_landmark ?? ""),
            chief_complaint:
              String(incident.chief_complaint ?? ""),
            latitude:
              String(incident.latitude ?? ""),
            longitude:
              String(incident.longitude ?? "")
          },

          android: {
            priority: "high",
            notification: {
              channel_id: "arms_dispatch",
              sound: "default"
            }
          }
        }
      };

      const fcmResponse =
        await fetch(
          fcmUrl,
          {
            method: "POST",
            headers: {
              Authorization:
                `Bearer ${accessToken}`,
              "Content-Type":
                "application/json"
            },
            body: JSON.stringify(message)
          }
        );

      const responseText =
        await fcmResponse.text();

      results.push({
        device_id: device.id,
        device_name: device.device_name,
        success: fcmResponse.ok,
        status: fcmResponse.status,
        response:
          fcmResponse.ok
            ? "FCM message accepted"
            : responseText
      });
    }

    return jsonResponse({
      success:
        results.some(result => result.success),

      incident_id: incident.id,
      dispatch_id: dispatch.id,
      responder_id:
        dispatch.primary_responder_id,

      devices_attempted: results.length,

      results
    });

  } catch (error) {

    return jsonResponse(
      {
        success: false,
        error:
          error instanceof Error
            ? error.message
            : String(error)
      },
      500
    );
  }
});
