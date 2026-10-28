# Existing pitch review

Reviewed the actual local VITALINK_Hackathon_Pitch.pdf using `C:/Program Files/Git/mingw64/bin/pdftotext.exe -layout`. It contains ten concept slides: problem/handoff, workflow, safety, AI differentiation, architecture, offline demo, economics, evidence targets and roadmap. The original is preserved unchanged.

Update final slide content to reflect actual evidence:

- Optional calibrated edge AI / XGBoost / ONNX / feature explanations are roadmap items, not implemented clinical capabilities. The release is rules-only with explicit model abstention.
- Room with Keystore-backed encrypted payloads is the selected local-storage alternative; do not claim SQLCipher if it is not used.
- PENDING, SENT, server receipt and clinician acknowledgment are distinct; add SERVER_RECEIVED to the pitch state story.
- The original p95 <300 ms and 100% supported scenarios are targets, not established results. Replace with measured evidence and the device-test status in FINAL_RELEASE_AUDIT.md.
- The illustrative 10,000/(150*24)+5=7.78 per screening calculation is a scenario with unspecified currency, not a measured cost or savings claim. Final economics states units and exclusions explicitly.
- Rehearse the offline device sequence only if an emulator/device run has actually passed. Label a JVM/API-only fallback demonstration honestly.

Source claims and final results are separate. See submission/SLIDES_10.md for evidence-aligned replacement content and notes. Cached PyMuPDF 1.28.2 was subsequently installed into workspace-local .tools/python and source pages 1, 4, 6 and 9 rendered under evidence/presentation; page 4 was visually inspected. No source PDF was modified.
