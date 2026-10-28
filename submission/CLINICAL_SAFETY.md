# Clinical boundary for judges

Research/hackathon prototype. Synthetic records only. No validated medical-device, diagnosis, clinical accuracy, medication or treatment claim.

- Systolic >=160 **OR** diastolic >=110 mmHg triggers urgent clinical assessment.
- Concerning symptoms remain urgent with normal BP.
- Missing/invalid data never imply a negative clinical finding.
- A no-trigger result does not establish that a patient is healthy.
- Emergency escalation does not wait for internet or synchronization.
- Saved, attempted, server-received and clinician-acknowledged states are distinct.
- Acknowledgment is a coordination event, not proof that care occurred.
- Statistical AI is unavailable and explicitly abstains; no invented probability or SHAP output is shown.

The detailed rule specification, source, hazard controls and unresolved review requirements are in ../docs/CLINICAL_SAFETY.md. Local clinical/ethics/privacy review is required before patient use. Hackathon testing cannot authorize clinical deployment.
