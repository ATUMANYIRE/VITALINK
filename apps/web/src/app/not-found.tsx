import Link from "next/link";
export default function NotFound() {
  return (
    <main id="main" className="not-found">
      <span className="wordmark">VITALINK</span>
      <p className="eyebrow">Page unavailable</p>
      <h1>Return to your workspace.</h1>
      <p>
        This page could not be found. Your referral records remain in the
        clinical workspace.
      </p>
      <Link className="button" href="/">
        Open clinical workspace
      </Link>
    </main>
  );
}
