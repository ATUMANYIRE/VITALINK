# Three-minute demonstration

Use only SYN-prefixed patient identifiers and randomly generated local demo credentials. Rehearse against the final release audit; do not present unexecuted device steps as verified.

| Time | Action | Narration |
|---|---|---|
| 00:00–00:20 | Open Android; show research boundary and rules-only mode | "VITALINK records maternal measurements offline and makes referral handoff visible." |
| 00:20–00:45 | Enable airplane mode; enter SYN-DEMO-001, 166/112 mmHg, gestational week 32 | "Severe systolic OR diastolic BP prompts urgent clinical assessment on device." |
| 00:45–01:05 | Show urgent alert and fallback handoff | "Contact the responsible clinician or local emergency pathway now; do not wait for this app or connectivity." |
| 01:05–01:25 | Save referral; show pending state and stable UUID; restart app | "Saved locally does not mean the hospital was notified. The committed record survives restart." |
| 01:25–01:50 | Restore connectivity; synchronize; show server receipt | "Retry uses the same identifier. Server receipt remains separate from clinician acknowledgment." |
| 01:50–02:20 | Sign in to doctor workspace; inspect matching UUID, BP, timestamp and rule triggers | "The clinician sees actual stored records within their facility, with no invented AI score." |
| 02:20–02:40 | Acknowledge explicitly; show actor/history; poll mobile | "The doctor's action records acknowledgment, which returns to the mobile client." |
| 02:40–03:00 | Show audit/evidence and limitations | "Engineering verification is recorded. Predictive AI is blocked pending suitable data; patient use requires clinical and governance review." |

If device execution is blocked, demonstrate the real API/database and browser flow using scripts/verify_lifecycle.py and explicitly identify the Android portion as build/JVM evidence only. Do not simulate airplane-mode success in narration. No deployed URL or fabricated video is supplied.
