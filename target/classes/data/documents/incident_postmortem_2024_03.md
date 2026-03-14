# Incident Postmortem: CASE-2024-0312

## Classification: CONFIDENTIAL

## Summary
On 2024-03-15, the SOC detected unauthorized access to the supplier payment portal via compromised credentials of a finance department employee. The attacker used a spear-phishing email impersonating ACME Corp to harvest credentials.

## Timeline
- 2024-03-14 09:12 – Phishing email received by finance-team@example.com
- 2024-03-14 09:47 – Employee clicked link and entered credentials on fake portal
- 2024-03-14 10:15 – Attacker logged into supplier payment portal
- 2024-03-14 10:32 – Attacker modified ACME Corp banking details
- 2024-03-15 08:00 – Anomaly detected by SOC monitoring (unusual GeoIP)
- 2024-03-15 08:45 – Account locked, credentials reset, sessions revoked
- 2024-03-15 14:00 – Payment of €127,500 identified as fraudulent, bank notified

## Root Cause
The phishing email used a lookalike domain (acme-c0rp.com) and referenced a real open invoice. The employee did not verify through the registered supplier contact.

## Impact
- Financial loss: €127,500 (partially recovered)
- 3 employee accounts temporarily compromised
- Supplier trust verification process found inadequate

## Remediation
- Mandatory phishing awareness training for finance department
- Two-factor authentication enforced for supplier portal
- Payment detail changes now require callback verification
- New monitoring rule for supplier portal access from unusual locations

## Lessons Learned
External supplier communications must never be trusted implicitly, even when they reference real internal case numbers or compliance certifications. Trust assertions in emails are not a substitute for verification through established channels.
