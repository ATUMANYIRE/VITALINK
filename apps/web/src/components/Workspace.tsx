"use client";
import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type FormEvent,
} from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import {
  parseHistory,
  parseMetrics,
  parsePage,
  parseReferral,
  parseUser,
  statusLabel,
  triggerLabel,
  urgencyLabel,
  type HistoryEntry,
  type Metrics,
  type Referral,
  type ReferralPage,
  type Status,
  type Urgency,
  type User,
} from "@/lib/contracts";

type View = "overview" | "inbox" | "urgent" | "history";
class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message);
  }
}
async function jsonRequest(
  path: string,
  options?: RequestInit,
): Promise<unknown> {
  const response = await fetch(path, {
    ...options,
    cache: "no-store",
    headers: { Accept: "application/json", ...options?.headers },
  });
  let data: unknown;
  try {
    data = await response.json();
  } catch {
    throw new Error(
      "The service returned an unreadable response. No action has been confirmed.",
    );
  }
  if (!response.ok) {
    const detail =
      data &&
      typeof data === "object" &&
      "detail" in data &&
      typeof data.detail === "string"
        ? data.detail
        : "The request could not be completed.";
    throw new ApiError(detail, response.status);
  }
  return data;
}
function formatTime(value: string, long = false) {
  return new Intl.DateTimeFormat("en-GB", {
    day: "2-digit",
    month: "short",
    ...(long ? { year: "numeric" as const } : {}),
    hour: "2-digit",
    minute: "2-digit",
    timeZoneName: "short",
  }).format(new Date(value));
}
function elapsed(value: string) {
  const minutes = Math.max(
    0,
    Math.floor((Date.now() - Date.parse(value)) / 60000),
  );
  return minutes < 60
    ? `${minutes} min`
    : minutes < 1440
      ? `${Math.floor(minutes / 60)}h ${minutes % 60}m`
      : `${Math.floor(minutes / 1440)}d`;
}
function SymbolMark() {
  return (
    <svg aria-hidden="true" className="symbol" viewBox="0 0 34 34" fill="none">
      <path d="M17 4v26M4 17h26" stroke="currentColor" strokeWidth="7" />
      <path d="M4 5h7M23 29h7" stroke="currentColor" strokeWidth="2" />
    </svg>
  );
}
function Badge({ urgency, status }: { urgency?: Urgency; status?: Status }) {
  return (
    <span
      className={`badge ${urgency ? urgency.toLowerCase() : "status-badge"}`}
    >
      {urgency ? urgencyLabel(urgency) : status ? statusLabel(status) : ""}
    </span>
  );
}
function Login({
  onLogin,
  notice,
}: {
  onLogin: (u: User) => void;
  notice: string;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError("");
    const fields = new FormData(event.currentTarget);
    try {
      const result = (await jsonRequest("/api/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          username: fields.get("username"),
          password: fields.get("password"),
        }),
      })) as { user: unknown };
      onLogin(parseUser(result.user));
    } catch (e) {
      setError(e instanceof Error ? e.message : "Sign-in failed.");
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="login-layout">
      <aside className="login-story">
        <div className="brand">
          <SymbolMark />
          <span className="wordmark">
            VITALINK<span>MATERNAL &amp; NEWBORN EARLY-WARNING</span>
          </span>
        </div>
        <div className="landing-hero">
          <div className="maternal-tag">
            <span className="heart-icon" aria-hidden="true">♥</span>
            <span>Mother &amp; Child Health Network</span>
          </div>
          <h1>
            Protecting Mother &amp; Baby.
            <br />
            <em>Where care begins.</em>
          </h1>
          <p className="story-text">
            An offline-first clinical early-warning system bridging frontline community health workers at village doorsteps with regional hospital physicians. Instant preeclampsia detection with zero network latency.
          </p>
          <div className="maternal-stats-grid">
            <div className="stat-box">
              <strong>830+</strong>
              <span>Mothers lost daily worldwide</span>
            </div>
            <div className="stat-box">
              <strong>0s</strong>
              <span>On-device triage latency</span>
            </div>
            <div className="stat-box">
              <strong>100%</strong>
              <span>Encrypted offline outbox</span>
            </div>
          </div>
          <div className="maternal-pillars">
            <div className="pillar-card">
              <div className="pillar-badge">🤰</div>
              <div>
                <strong>Early Preeclampsia Detection</strong>
                <p>NICE NG133 deterministic rules: BP &ge; 160/110 mmHg and red-flag maternal symptoms trigger immediate hospital escalation.</p>
              </div>
            </div>
            <div className="pillar-card">
              <div className="pillar-badge">📱</div>
              <div>
                <strong>Frontline Doorstep Screening</strong>
                <p>Field workers screen offline with encrypted SQLite Room database and Android KeyStore AES-256-GCM protection.</p>
              </div>
            </div>
            <div className="pillar-card">
              <div className="pillar-badge">🏥</div>
              <div>
                <strong>Auditable Clinician Handoff</strong>
                <p>Hospital doctors review raw vital measurements, rule trigger reasons, and sign explicit two-step acknowledgments.</p>
              </div>
            </div>
          </div>
          <div className="landing-downloads">
            <a href="/app-debug.apk" className="download-pill" download>
              <span>📱 Download Android Mobile APK (Field App)</span>
            </a>
          </div>
        </div>
        <p className="prototype-label">
          HEALTH HACKATHON 3.0 · VIT BHOPAL UNIVERSITY · RESEARCH PROTOTYPE
        </p>
      </aside>
      <main id="main" className="login-main">
        <div className="login-card">
          <span className="small-cross" aria-hidden="true">
            +
          </span>
          <p className="eyebrow">Clinical workspace</p>
          <h2>Welcome back.</h2>
          <p className="muted">Sign in with your assigned facility account.</p>
          {notice && (
            <p className="notice" role="status">
              {notice}
            </p>
          )}
          <form onSubmit={submit}>
            <label htmlFor="username">Username</label>
            <input
              id="username"
              name="username"
              autoComplete="username"
              required
              maxLength={100}
              autoCapitalize="none"
              spellCheck={false}
            />
            <label htmlFor="password">Password</label>
            <input
              id="password"
              type="password"
              name="password"
              autoComplete="current-password"
              required
              maxLength={256}
            />
            {error && (
              <p className="error" role="alert">
                {error}
              </p>
            )}
            <button className="full-width" disabled={busy} type="submit">
              {busy ? "Signing in…" : "Sign in to workspace"}
              <span aria-hidden="true">→</span>
            </button>
          </form>
          <p className="account-help">
            Need access? Contact your facility administrator. Accounts are
            restricted to their assigned facility.
          </p>
          <div className="research-note">
            <strong>Synthetic demonstration only</strong>
            <p>
              This is a research prototype, not a validated medical device. It
              does not diagnose disease or recommend treatment.
            </p>
          </div>
        </div>
      </main>
    </div>
  );
}

export default function Workspace({ initialId }: { initialId?: string }) {
  const router = useRouter();
  const [user, setUser] = useState<User | null>(null);
  const [sessionLoading, setSessionLoading] = useState(true);
  const [notice, setNotice] = useState("");
  const [view, setView] = useState<View>("overview");
  const [urgency, setUrgency] = useState("");
  const [status, setStatus] = useState("");
  const [page, setPage] = useState(0);
  const [records, setRecords] = useState<ReferralPage | null>(null);
  const [metrics, setMetrics] = useState<Metrics | null>(null);
  const [detail, setDetail] = useState<Referral | null>(null);
  const [history, setHistory] = useState<HistoryEntry[]>([]);
  const [timeline, setTimeline] = useState<ReferralPage | null>(null);
  const [timelineError, setTimelineError] = useState("");
  const [loading, setLoading] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [error, setError] = useState("");
  const [detailError, setDetailError] = useState("");
  const [busyAction, setBusyAction] = useState("");
  const [confirmAction, setConfirmAction] = useState<
    "acknowledge" | "close" | null
  >(null);
  const [actionNotice, setActionNotice] = useState("");
  const [online, setOnline] = useState(true);
  const [lastUpdated, setLastUpdated] = useState<string | null>(null);
  const [refresh, setRefresh] = useState(0);
  const mounted = useRef(true);
  useEffect(() => {
    mounted.current = true;
    jsonRequest("/api/session")
      .then((v) => setUser(parseUser((v as { user: unknown }).user)))
      .catch(() => setUser(null))
      .finally(() => setSessionLoading(false));
    setOnline(navigator.onLine);
    const handleOnline = () => {
      setOnline(true);
      setRefresh((n) => n + 1);
    };
    const handleOffline = () => setOnline(false);
    window.addEventListener("online", handleOnline);
    window.addEventListener("offline", handleOffline);
    return () => {
      mounted.current = false;
      window.removeEventListener("online", handleOnline);
      window.removeEventListener("offline", handleOffline);
    };
  }, []);
  const handleError = useCallback((e: unknown, set: (s: string) => void) => {
    if (e instanceof ApiError && e.status === 401) {
      setUser(null);
      setNotice("Your session has expired. Sign in to continue.");
    } else
      set(
        e instanceof Error ? e.message : "Connection interrupted. Try again.",
      );
  }, []);
  useEffect(() => {
    if (!user || !online) return;
    const interval = setInterval(() => setRefresh((n) => n + 1), 15000);
    return () => clearInterval(interval);
  }, [user, online]);
  useEffect(() => {
    if (!user || !online) return;
    let active = true;
    setLoading(true);
    const query = new URLSearchParams({ page: String(page), size: "10" });
    const selectedUrgency = view === "urgent" ? "URGENT" : urgency;
    const selectedStatus = view === "history" ? "ACKNOWLEDGED" : status;
    if (selectedUrgency) query.set("urgency", selectedUrgency);
    if (selectedStatus) query.set("status", selectedStatus);
    Promise.all([
      jsonRequest(`/api/backend/referrals?${query}`).then(parsePage),
      jsonRequest("/api/backend/metrics").then(parseMetrics),
    ])
      .then(([r, m]) => {
        if (!active) return;
        setRecords(r);
        setMetrics(m);
        setLastUpdated(new Date().toISOString());
        setError("");
      })
      .catch((e) => {
        if (active) handleError(e, setError);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [user, online, view, urgency, status, page, refresh, handleError]);
  useEffect(() => {
    setDetail(null);
    setHistory([]);
    setTimeline(null);
    setDetailError("");
    setTimelineError("");
    setActionNotice("");
    setConfirmAction(null);
  }, [initialId]);
  useEffect(() => {
    if (!user || !initialId || !online) return;
    let active = true;
    setDetailLoading(true);
    Promise.all([
      jsonRequest(
        `/api/backend/referrals/${encodeURIComponent(initialId)}`,
      ).then(parseReferral),
      jsonRequest(
        `/api/backend/referrals/${encodeURIComponent(initialId)}/history`,
      ).then(parseHistory),
    ])
      .then(([r, h]) => {
        if (!active) return;
        if (r.id !== initialId || r.facilityId !== user.facilityId)
          throw new Error(
            "The returned referral does not match this facility or identifier.",
          );
        setDetail(r);
        setHistory(h);
        setDetailError("");
        jsonRequest(
          `/api/backend/referrals?patientId=${encodeURIComponent(r.assessment.patientId)}&size=100&page=0`,
        )
          .then(parsePage)
          .then((t) => {
            if (!active) return;
            if (
              t.items.some(
                (item) =>
                  item.facilityId !== user.facilityId ||
                  item.assessment.patientId !== r.assessment.patientId,
              )
            )
              throw new Error(
                "The measurement history did not match the requested synthetic patient.",
              );
            setTimeline(t);
            setTimelineError("");
          })
          .catch(() => {
            if (active)
              setTimelineError(
                "Earlier measurement snapshots could not be loaded. The current referral remains available above.",
              );
          });
      })
      .catch((e) => {
        if (active) handleError(e, setDetailError);
      })
      .finally(() => {
        if (active) setDetailLoading(false);
      });
    return () => {
      active = false;
    };
  }, [user, initialId, refresh, online, handleError]);
  async function signOut() {
    try {
      const result = (await jsonRequest("/api/logout", { method: "POST" })) as {
        serverSessionRevoked: boolean;
      };
      setNotice(
        result.serverSessionRevoked
          ? "You have been signed out."
          : "Signed out on this browser. Server revocation could not be confirmed; the remote session will expire automatically.",
      );
      setUser(null);
      setDetail(null);
      setRecords(null);
      setMetrics(null);
      router.replace("/");
    } catch {
      setError("Sign-out could not be confirmed. Reconnect and try again.");
    }
  }
  async function action(kind: "acknowledge" | "close") {
    if (!detail) return;
    setBusyAction(kind);
    setDetailError("");
    setConfirmAction(null);
    setActionNotice("");
    try {
      const updated = parseReferral(
        await jsonRequest(`/api/backend/referrals/${detail.id}/${kind}`, {
          method: "POST",
        }),
      );
      if (
        updated.id !== detail.id ||
        updated.facilityId !== user?.facilityId ||
        (kind === "acknowledge" && updated.status !== "ACKNOWLEDGED") ||
        (kind === "close" && updated.status !== "CLOSED")
      )
        throw new Error(
          "The server did not confirm the requested state. Refresh before trying again.",
        );
      setDetail(updated);
      setActionNotice(
        kind === "acknowledge"
          ? "Clinical acknowledgment recorded by the server."
          : "Referral closure recorded by the server.",
      );
      if (typeof document !== "undefined" && document.body) {
        document.body.tabIndex = -1;
        document.body.focus();
        document.body.removeAttribute("tabindex");
      }
      setRefresh((n) => n + 1);
    } catch (e) {
      handleError(e, setDetailError);
    } finally {
      if (mounted.current) setBusyAction("");
    }
  }
  function changeView(next: View) {
    setView(next);
    setPage(0);
    setUrgency("");
    setStatus("");
    setRecords(null);
    if (initialId) router.push("/");
  }
  if (sessionLoading)
    return (
      <main id="main" className="not-found" role="status">
        <span className="wordmark">VITALINK</span>
        <p>Opening your clinical workspace…</p>
      </main>
    );
  if (!user)
    return (
      <Login
        onLogin={(u) => {
          setUser(u);
          setNotice("");
          setRefresh((n) => n + 1);
        }}
        notice={notice}
      />
    );
  const title = initialId
    ? "Referral assessment"
    : {
        overview: "Clinical overview",
        inbox: "Incoming referrals",
        urgent: "Urgent assessment queue",
        history: "Acknowledged referrals",
      }[view];
  const canAct = user.role === "DOCTOR" || user.role === "ADMIN";
  const connectionLabel = !online
    ? "Browser offline"
    : error || detailError
      ? "Connection needs attention"
      : lastUpdated
        ? "Service connected"
        : "Connecting to service";
  return (
    <div className="workspace">
      <header className="topbar">
        <Link
          className="brand"
          href="/"
          aria-label="VITALINK clinical workspace home"
        >
          <SymbolMark />
          <span className="wordmark">
            VITALINK<span>CLINICAL WORKSPACE</span>
          </span>
        </Link>
        <span className="research-chip">
          Research prototype · Synthetic records
        </span>
        <div className="account">
          <span className="avatar" aria-hidden="true">
            {user.username.slice(0, 2).toUpperCase()}
          </span>
          <div>
            <strong>{user.username}</strong>
            <span>
              {user.role === "DOCTOR"
                ? "Clinician"
                : user.role === "ADMIN"
                  ? "Administrator"
                  : "Health worker"}
            </span>
          </div>
          <button className="text-button" onClick={signOut}>
            Sign out
          </button>
        </div>
      </header>
      <div className="workspace-grid">
        <aside className="sidebar">
          <p className="eyebrow">Care coordination</p>
          <nav aria-label="Workspace navigation">
            {(
              [
                ["overview", "Overview", "01"],
                ["inbox", "Referral inbox", "02"],
                ["urgent", "Urgent queue", "03"],
                ["history", "Acknowledgment history", "04"],
              ] as const
            ).map(([key, label, num]) => (
              <button
                key={key}
                className={`nav-item ${view === key && !initialId ? "active" : ""}`}
                aria-current={view === key && !initialId ? "page" : undefined}
                onClick={() => changeView(key)}
              >
                <span className="nav-number">{num}</span>
                {label}
                {key === "urgent" && metrics && (
                  <span className="nav-count">{metrics.urgent}</span>
                )}
              </button>
            ))}
          </nav>
          <div className="facility-block">
            <span className="eyebrow">Authorized facility</span>
            <strong>{user.facilityId}</strong>
            <p>Only records assigned to this facility are visible.</p>
          </div>
          <div className="sidebar-foot">
            <span
              className={`connection-dot ${online && !error ? "connected" : ""}`}
            />
            <span>{connectionLabel}</span>
            <p>
              Local queues remain on the health worker’s device until server
              receipt.
            </p>
          </div>
        </aside>
        <main id="main" className="main-content">
          <div className="page-heading">
            <div>
              <p className="eyebrow">
                Maternal referral network <span className="slash">/</span>{" "}
                {initialId ? "Assessment detail" : "Operations"}
              </p>
              <h1>{title}</h1>
              <p className="muted">
                {initialId
                  ? "An immutable screening snapshot and its clinical handoff."
                  : "A clear view of the people awaiting your clinical attention."}
              </p>
            </div>
            <button
              className="secondary refresh-button"
              disabled={!online || loading || detailLoading}
              onClick={() => setRefresh((n) => n + 1)}
            >
              <span aria-hidden="true">↻</span>{" "}
              {loading || detailLoading ? "Refreshing…" : "Refresh records"}
            </button>
          </div>
          <div className="safety-strip">
            <span className="safety-symbol" aria-hidden="true">
              !
            </span>
            <p>
              <strong>
                Urgent care follows your local escalation pathway.
              </strong>{" "}
              Referral delivery and acknowledgment do not confirm that care has
              been provided. Never wait for this dashboard before escalating an
              emergency.
            </p>
          </div>
          {!online && (
            <div className="error" role="alert">
              This browser is offline. Previously loaded records may be stale.
              Actions are unavailable until connectivity returns.
            </div>
          )}
          {error && (
            <div className="error" role="alert">
              {error}{" "}
              <button
                className="text-button"
                onClick={() => setRefresh((n) => n + 1)}
              >
                Retry connection
              </button>
            </div>
          )}
          {initialId ? (
            <>
              <Link className="back-link" href="/">
                ← Back to referral workspace
              </Link>
              {detailError && (
                <div className="error" role="alert">
                  {detailError}
                </div>
              )}
              {actionNotice && (
                <p className="notice" role="status">
                  {actionNotice}
                </p>
              )}
              {detailLoading && !detail ? (
                <div className="empty-state" role="status">
                  Loading the assessment and handoff history…
                </div>
              ) : detail ? (
                <>
                  <section className="detail-top">
                    <div>
                      <p className="eyebrow">Synthetic patient identifier</p>
                      <h2>{detail.assessment.patientId}</h2>
                      <p className="record-id">Referral {detail.id}</p>
                    </div>
                    <div className="detail-badges">
                      <Badge urgency={detail.urgency} />
                      <Badge status={detail.status} />
                    </div>
                  </section>
                  {detail.urgency === "URGENT" && (
                    <div className="urgent-callout">
                      <strong>Urgent clinical assessment required</strong>
                      <p>
                        Use the local emergency escalation pathway now. A low
                        future model score, normal accompanying measurement, or
                        unavailable network must never suppress these findings.
                      </p>
                    </div>
                  )}
                  <div className="detail-grid">
                    <div>
                      <section className="panel">
                        <div className="panel-heading">
                          <h3>Screening measurements</h3>
                          <span className="muted">Immutable snapshot</span>
                        </div>
                        <div className="vitals">
                          <div>
                            <span>Systolic BP</span>
                            <strong>
                              {detail.assessment.systolic ?? "Missing"}
                              <small> mmHg</small>
                            </strong>
                          </div>
                          <div>
                            <span>Diastolic BP</span>
                            <strong>
                              {detail.assessment.diastolic ?? "Missing"}
                              <small> mmHg</small>
                            </strong>
                          </div>
                          <div>
                            <span>Gestational age</span>
                            <strong>
                              {detail.assessment.gestationalWeeks ?? "Missing"}
                              <small> weeks</small>
                            </strong>
                          </div>
                        </div>
                        <dl className="metadata">
                          <div>
                            <dt>Measured</dt>
                            <dd>
                              {formatTime(detail.assessment.measuredAt, true)}
                            </dd>
                          </div>
                          <div>
                            <dt>Source</dt>
                            <dd>
                              {detail.assessment.source === "MANUAL"
                                ? "Manual entry"
                                : "Device measurement"}
                            </dd>
                          </div>
                          <div>
                            <dt>Assessment ID</dt>
                            <dd className="record-id">
                              {detail.assessment.id}
                            </dd>
                          </div>
                          <div>
                            <dt>Rule version</dt>
                            <dd>{detail.assessment.ruleVersion}</dd>
                          </div>
                        </dl>
                      </section>
                      <section className="panel findings">
                        <div className="panel-heading">
                          <h3>Assessment explanation</h3>
                          <span className="badge neutral">
                            Deterministic rules
                          </span>
                        </div>
                        {detail.ruleTriggers.length ? (
                          <ul className="trigger-list">
                            {detail.ruleTriggers.map((code) => (
                              <li key={code}>
                                <span aria-hidden="true">
                                  {detail.urgency === "URGENT" ? "!" : "·"}
                                </span>
                                <span>{triggerLabel(code)}</span>
                              </li>
                            ))}
                          </ul>
                        ) : (
                          <p>
                            No configured rule was triggered. This is not
                            assurance of health and does not replace clinical
                            review.
                          </p>
                        )}
                        <h4>Reported concerning symptoms</h4>
                        <p>
                          {detail.assessment.symptoms.length
                            ? detail.assessment.symptoms
                                .map((s) =>
                                  s.toLowerCase().replaceAll("_", " "),
                                )
                                .join(", ")
                            : "None selected in this recorded assessment. Confirm the history directly with the referring health worker."}
                        </p>
                        <div className="research-note">
                          <strong>AI research status: unavailable</strong>
                          <p>
                            No validated predictive model is deployed. These
                            findings come from versioned safety rules. No
                            statistical risk score, confidence estimate, or
                            feature contribution is implied.
                          </p>
                        </div>
                      </section>
                      <section className="panel">
                        <div className="panel-heading">
                          <h3>Measurement timeline</h3>
                          <span className="muted">
                            Stored referral snapshots
                          </span>
                        </div>
                        {timelineError && (
                          <p className="error" role="status">
                            {timelineError}
                          </p>
                        )}
                        {(timeline?.items.length
                          ? [...timeline.items].sort(
                              (a, b) =>
                                Date.parse(b.assessment.measuredAt) -
                                Date.parse(a.assessment.measuredAt),
                            )
                          : [detail]
                        ).map((item, index) => (
                          <div
                            className="timeline-item measurement-event"
                            key={item.id}
                          >
                            <span className="timeline-dot" />
                            <div>
                              <strong>
                                {formatTime(item.assessment.measuredAt, true)}
                              </strong>
                              <p>
                                {item.assessment.systolic ?? "Missing"} /{" "}
                                {item.assessment.diastolic ?? "Missing"} mmHg /{" "}
                                {item.assessment.source.toLowerCase()} entry
                              </p>
                              <p className="muted">
                                {item.id === detail.id ? (
                                  "Current referral"
                                ) : (
                                  <Link href={`/referrals/${item.id}`}>
                                    Open recorded referral {index + 1}
                                  </Link>
                                )}
                              </p>
                            </div>
                          </div>
                        ))}
                        <p className="small muted">
                          {timeline
                            ? `Showing ${timeline.items.length} of ${timeline.totalElements} stored referral snapshots for this synthetic identifier.`
                            : "Only the current referral snapshot is available."}{" "}
                          Assessments not synchronized to the server are not
                          included.
                        </p>
                      </section>
                    </div>
                    <aside>
                      <section className="panel handoff">
                        <p className="eyebrow">Clinical handoff</p>
                        <h3>
                          {detail.status === "SERVER_RECEIVED"
                            ? "Awaiting acknowledgment"
                            : detail.status === "ACKNOWLEDGED"
                              ? "Acknowledgment recorded"
                              : "Referral closed"}
                        </h3>
                        <p>
                          {detail.status === "SERVER_RECEIVED"
                            ? "The server has received this referral. A clinician has not yet explicitly acknowledged it."
                            : `Acknowledged by ${detail.acknowledgedBy} on ${detail.acknowledgedAt ? formatTime(detail.acknowledgedAt) : "unavailable"}.`}
                        </p>
                        {canAct &&
                          detail.status !== "CLOSED" &&
                          (confirmAction ? (
                            <div
                              className="confirmation"
                              role="group"
                              aria-label="Confirm clinical action"
                            >
                              <strong>
                                {confirmAction === "acknowledge"
                                  ? "Confirm you have reviewed this referral?"
                                  : "Confirm this referral can be closed?"}
                              </strong>
                              <p>
                                {confirmAction === "acknowledge"
                                  ? "This records your acknowledgment. It does not record treatment or clinical resolution."
                                  : "Closure is an administrative state. It does not establish that the patient is clinically well."}
                              </p>
                              <button
                                className="full-width"
                                disabled={!online || !!busyAction}
                                onClick={() => action(confirmAction)}
                              >
                                Confirm{" "}
                                {confirmAction === "acknowledge"
                                  ? "acknowledgment"
                                  : "closure"}
                              </button>
                              <button
                                className="secondary full-width"
                                onClick={() => setConfirmAction(null)}
                              >
                                Cancel
                              </button>
                            </div>
                          ) : (
                            <button
                              className="full-width"
                              disabled={
                                !online || !!busyAction || !!detailError
                              }
                              onClick={() =>
                                setConfirmAction(
                                  detail.status === "SERVER_RECEIVED"
                                    ? "acknowledge"
                                    : "close",
                                )
                              }
                            >
                              {busyAction
                                ? "Confirming with server…"
                                : detail.status === "SERVER_RECEIVED"
                                  ? "Acknowledge referral"
                                  : "Close referral"}
                            </button>
                          ))}
                        {!canAct && (
                          <p className="notice">
                            Your account can review records. Clinical actions
                            require a doctor or administrator.
                          </p>
                        )}
                        <p className="small muted">
                          The mobile app receives this state on its next
                          successful status synchronization.
                        </p>
                      </section>
                      <section className="panel">
                        <div className="panel-heading">
                          <h3>Status history</h3>
                        </div>
                        <ol className="history-list">
                          {history.map((h, i) => (
                            <li key={`${h.at}-${i}`}>
                              <span className="timeline-dot" />
                              <div>
                                <strong>{statusLabel(h.status)}</strong>
                                <time dateTime={h.at}>{formatTime(h.at)}</time>
                                <span className="muted">{h.actor}</span>
                              </div>
                            </li>
                          ))}
                        </ol>
                      </section>
                    </aside>
                  </div>
                </>
              ) : (
                !detailError && (
                  <div className="empty-state">
                    No referral has been loaded.
                  </div>
                )
              )}
            </>
          ) : (
            <>
              {view === "overview" && (
                <section
                  className="metrics-grid"
                  aria-label="Facility operational metrics"
                >
                  <Metric
                    label="Received referrals"
                    value={metrics?.total}
                    caption="All stored facility records"
                  />
                  <Metric
                    label="Awaiting acknowledgment"
                    value={metrics?.awaitingAcknowledgment}
                    caption="Server received, clinician pending"
                  />
                  <Metric
                    label="Urgent findings"
                    value={metrics?.urgent}
                    caption="Based on clinical safety rules"
                    urgent
                  />
                  <Metric
                    label="Acknowledged"
                    value={metrics?.acknowledged}
                    caption="Explicit clinical handoffs"
                  />
                </section>
              )}
              <section className="queue-panel">
                <div className="section-heading">
                  <div>
                    <p className="eyebrow">
                      {view === "urgent"
                        ? "Immediate clinical attention"
                        : view === "history"
                          ? "Confirmed handoffs"
                          : "Facility referral queue"}
                    </p>
                    <h2>
                      {view === "overview" ? "Incoming referrals" : title}
                    </h2>
                  </div>
                  <span className="record-count">
                    {records
                      ? `${records.totalElements} record${records.totalElements === 1 ? "" : "s"}`
                      : "Loading records"}
                  </span>
                </div>
                <div className="filters">
                  <div className="filter-controls">
                    {view !== "urgent" && (
                      <div>
                        <label htmlFor="urgency">Clinical urgency</label>
                        <select
                          id="urgency"
                          value={urgency}
                          onChange={(e) => {
                            setUrgency(e.target.value);
                            setPage(0);
                            setRecords(null);
                          }}
                        >
                          <option value="">All urgency levels</option>
                          <option value="URGENT">Urgent assessment</option>
                          <option value="REVIEW_REQUIRED">
                            Clinical review
                          </option>
                          <option value="NO_TRIGGER">No rule trigger</option>
                        </select>
                      </div>
                    )}
                    {view !== "history" && (
                      <div>
                        <label htmlFor="status">Handoff status</label>
                        <select
                          id="status"
                          value={status}
                          onChange={(e) => {
                            setStatus(e.target.value);
                            setPage(0);
                            setRecords(null);
                          }}
                        >
                          <option value="">All statuses</option>
                          <option value="SERVER_RECEIVED">
                            Received · awaiting acknowledgment
                          </option>
                          <option value="ACKNOWLEDGED">Acknowledged</option>
                          <option value="CLOSED">Closed</option>
                        </select>
                      </div>
                    )}
                  </div>
                  <p className="sort-label">
                    Urgency first <span aria-hidden="true">·</span> oldest
                    received first
                  </p>
                </div>
                {loading && !records ? (
                  <div className="empty-state" role="status">
                    <span className="loader" />
                    Loading referrals from your facility…
                  </div>
                ) : records?.items.length ? (
                  <>
                    <div className="table-scroll">
                      <table>
                        <caption className="sr-only">
                          Referrals for {user.facilityId}, ordered by clinical
                          urgency then receipt time
                        </caption>
                        <thead>
                          <tr>
                            <th scope="col">Patient / referral</th>
                            <th scope="col">Clinical urgency</th>
                            <th scope="col">Blood pressure</th>
                            <th scope="col">Received / elapsed</th>
                            <th scope="col">Handoff</th>
                            <th scope="col">
                              <span className="sr-only">Open referral</span>
                            </th>
                          </tr>
                        </thead>
                        <tbody>
                          {records.items.map((r) => (
                            <tr key={r.id}>
                              <td>
                                <Link
                                  className="patient-link"
                                  href={`/referrals/${r.id}`}
                                >
                                  {r.assessment.patientId}
                                </Link>
                                <span className="table-sub record-id">
                                  {r.id.slice(0, 8)}
                                </span>
                              </td>
                              <td>
                                <Badge urgency={r.urgency} />
                                <span className="table-sub">
                                  {r.ruleTriggers.length
                                    ? `${r.ruleTriggers.length} rule finding${r.ruleTriggers.length === 1 ? "" : "s"}`
                                    : "No configured trigger"}
                                </span>
                              </td>
                              <td>
                                <span
                                  className={`bp-value ${r.urgency === "URGENT" ? "urgent-text" : ""}`}
                                >
                                  {r.assessment.systolic ?? "—"}
                                  <span> / </span>
                                  {r.assessment.diastolic ?? "—"}
                                </span>
                                <span className="table-sub">
                                  mmHg
                                  {r.assessment.systolic === null ||
                                  r.assessment.diastolic === null
                                    ? " · incomplete"
                                    : ""}
                                </span>
                              </td>
                              <td>
                                <time dateTime={r.receivedAt}>
                                  {formatTime(r.receivedAt)}
                                </time>
                                <span className="table-sub">
                                  {elapsed(r.receivedAt)} since receipt
                                </span>
                              </td>
                              <td>
                                <Badge status={r.status} />
                              </td>
                              <td>
                                <Link
                                  className="open-link"
                                  href={`/referrals/${r.id}`}
                                  aria-label={`Review referral for ${r.assessment.patientId}`}
                                >
                                  Review <span aria-hidden="true">↗</span>
                                </Link>
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                    <div className="pagination">
                      <span>
                        Page {records.page + 1} of{" "}
                        {Math.max(1, records.totalPages)}
                      </span>
                      <div>
                        <button
                          className="secondary"
                          disabled={page === 0 || loading}
                          onClick={() => setPage((n) => n - 1)}
                        >
                          Previous
                        </button>
                        <button
                          className="secondary"
                          disabled={page + 1 >= records.totalPages || loading}
                          onClick={() => setPage((n) => n + 1)}
                        >
                          Next
                        </button>
                      </div>
                    </div>
                  </>
                ) : (
                  <div className="empty-state">
                    <span className="empty-cross" aria-hidden="true">
                      +
                    </span>
                    <h3>
                      {error
                        ? "Records could not be loaded"
                        : "No referrals in this view"}
                    </h3>
                    <p>
                      {error
                        ? "Reconnect and refresh. No clinical action has been confirmed."
                        : "Received referrals appear here after the health worker’s device successfully synchronizes. Locally queued referrals are not visible to this workspace."}
                    </p>
                    {!error && (urgency || status || view !== "overview") && (
                      <button
                        className="secondary"
                        onClick={() => changeView("overview")}
                      >
                        Show all referrals
                      </button>
                    )}
                  </div>
                )}
              </section>
              <div className="context-grid">
                <section className="context-note">
                  <span className="note-index">01 / DELIVERY</span>
                  <h3>Received is not acknowledged.</h3>
                  <p>
                    Server receipt confirms delivery only. A clinician must
                    explicitly review and acknowledge the referral. Offline or
                    unsent referrals remain on the referring device.
                  </p>
                </section>
                <section className="context-note">
                  <span className="note-index">02 / SAFETY</span>
                  <h3>Clinical concern comes first.</h3>
                  <p>
                    Severe-range blood pressure or concerning symptoms require
                    urgent assessment. No-trigger findings are not proof of
                    health. No predictive AI model is deployed.
                  </p>
                </section>
              </div>
            </>
          )}
          <footer className="workspace-footer">
            <span>VITALINK · Research prototype · Synthetic data only</span>
            <span>
              {lastUpdated
                ? `Last server refresh ${formatTime(lastUpdated)}`
                : "No server refresh confirmed"}
            </span>
          </footer>
        </main>
      </div>
    </div>
  );
}
function Metric({
  label,
  value,
  caption,
  urgent,
}: {
  label: string;
  value: number | undefined;
  caption: string;
  urgent?: boolean;
}) {
  return (
    <article className={`metric ${urgent ? "metric-urgent" : ""}`}>
      <p>{label}</p>
      <strong>
        {value === undefined ? "—" : value.toLocaleString("en-GB")}
      </strong>
      <span>{caption}</span>
    </article>
  );
}
