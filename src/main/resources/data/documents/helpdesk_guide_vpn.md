# Helpdesk Guide: VPN Access & Reset Procedures

## Classification: INTERNAL

## Scope
This guide covers standard procedures for VPN access issues, including password resets, client configuration, and suspicious access reports.

## Password Reset Requests
VPN password resets must be verified through the employee's registered phone number or in-person at the IT helpdesk. Email-only requests for VPN resets are not accepted.

## Suspicious VPN Activity
If a VPN login is flagged from an unusual location or device:
1. Notify the user through a known channel (phone or in-person).
2. If the user did not initiate the login: disable the VPN account immediately.
3. Open a SOC ticket with category "Suspicious VPN Access".
4. Do not re-enable the account until SOC has completed review.

## Common Issues
- **Certificate expired**: Re-issue through the self-service portal (requires MFA).
- **Split tunneling errors**: Ensure the latest VPN client version is installed.
- **Geo-block triggers**: Travel notifications must be submitted 24h in advance.

## Escalation
For persistent issues or suspected compromise, escalate directly to the SOC.
