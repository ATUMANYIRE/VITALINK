import type { Metadata } from "next";
import "./styles.css";
export const metadata: Metadata = {
  title: "VITALINK | Clinical workspace",
  description:
    "Offline maternal screening and referral research prototype. Synthetic demonstration records only.",
  robots: { index: false, follow: false },
};
export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en">
      <body>
        <a className="skip-link" href="#main">
          Skip to main content
        </a>
        {children}
      </body>
    </html>
  );
}
