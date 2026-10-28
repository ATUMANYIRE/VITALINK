# Submission Checklist

- [x] Review [`../docs/FINAL_RELEASE_AUDIT.md`](../docs/FINAL_RELEASE_AUDIT.md) and [`../docs/TEST_EVIDENCE.md`](../docs/TEST_EVIDENCE.md).
- [x] Verify generated runtime credentials stay gitignored (`services/api/.runtime/` and `.env`); never publish secrets.
- [x] Confirm browser screenshots (`../apps/web/evidence/*.png`) show actual software and synthetic data (`SYN-*`) only.
- [x] Update 10-slide presentation text ([`SLIDES_10.md`](SLIDES_10.md)) and PDF deck ([`VITALINK_Updated_Pitch_Deck.pdf`](VITALINK_Updated_Pitch_Deck.pdf)) to match final audited results (`221/221` automated tests passing).
- [x] Confirm all clinical claims and local escalation wording retain the research prototype restriction.
- [x] Confirm no fabricated accuracy, ROC-AUC, lives-saved, measured cost-savings, or public deployment claims exist.
- [ ] Rehearse the physical Android device workflow on the demonstration handset/network (`app-debug.apk` built at `apps/android/app/build/outputs/apk/debug/app-debug.apk`).
- [ ] Review licenses and conduct an online dependency vulnerability advisory scan when connected to an unrestricted network.
- [ ] Record a live 3-minute demonstration video following [`DEMO_SCRIPT_3_MIN.md`](DEMO_SCRIPT_3_MIN.md) if required by the hackathon portal.
- [ ] Confirm team member details, competition track, submission deadline, and portal fields manually.
- [ ] Obtain explicit user authorization before any remote `git push`, cloud deployment, or external portal submission.

This package has not been submitted to any external competition platform and contains no fabricated public demo URLs.
