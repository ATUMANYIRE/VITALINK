# Problem and intended impact

A maternal measurement can be recorded in the field while connectivity is unavailable. Without a durable handoff, workers may not know whether a referral reached a server or was seen by a clinician. VITALINK makes these events distinct and preserves a referral identifier through the workflow.

Target users: community health workers, ANMs/ASHA workers, primary health centres and receiving clinicians. Actual local workflow, terminology, staffing and escalation contacts require review with the intended users; the hackathon does not establish usability for every setting.

Intended operational effects to test: fewer lost committed referrals, fewer duplicate submissions after retries, clearer acknowledgment ownership and reduced uncertainty about handoff. Engineering tests can examine the first two within specified failure scenarios. Clinical outcomes and real-world workflow improvement remain hypotheses.

An approved pilot should measure receipt and acknowledgment latency separately, denominator-defined undelivered cases, documentation time, staff workload and escalation actions. Compare against the existing process. Avoid attributing patient outcomes to an app acknowledgment, and do not claim reduced mortality without an appropriately designed study.
