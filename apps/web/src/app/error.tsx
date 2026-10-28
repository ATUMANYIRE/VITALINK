"use client";
export default function ErrorPage({ reset }: { reset: () => void }) {
  return (
    <main id="main" className="not-found">
      <span className="wordmark">VITALINK</span>
      <h1>The workspace could not load.</h1>
      <p role="alert">
        No clinical action has been confirmed. Reconnect and try again. Use your
        local escalation pathway for urgent cases.
      </p>
      <button onClick={reset}>Try again</button>
    </main>
  );
}
