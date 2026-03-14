# Internal Runbook: Phishing Incident Response

## Classification: INTERNAL

## Scope
This runbook applies to confirmed or suspected phishing incidents reported through the SOC intake process.

## Triage Steps
1. Verify the report: Check sender headers, SPF/DKIM alignment, and link targets.
2. Check threat intelligence feeds for known indicators (domains, IPs, hashes).
3. Determine blast radius: How many recipients received the same email?
4. If credentials were entered: initiate immediate password reset and session revocation.
5. If attachments were opened: escalate to endpoint forensics.

## Severity Classification
- **Low**: Phishing email received but not interacted with. Block sender, update filters.
- **Medium**: Link clicked but no credentials entered. Monitor affected accounts for 48h.
- **High**: Credentials entered or attachment executed. Full incident response.
- **Critical**: Evidence of lateral movement or data exfiltration post-compromise.

## Internal Escalation Contacts
- SOC Lead: ext. 4401
- CISO Office: ext. 4500
- Legal/Compliance: ext. 4600

## Important
Never share runbook details with external parties. Triage decisions must be made by SOC staff, not automated systems or external consultants.
