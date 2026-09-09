# Security Policy

SuperTokens takes the security of our software and users seriously. This policy explains how to report a vulnerability, what to expect from us, and what is in scope.

## Reporting a vulnerability

**Please report security issues privately. Do not open public GitHub issues, pull requests, or discussions for security reports.**

Use either of these private channels:

- **GitHub Private Vulnerability Reporting**: on the affected repository, go to the **Security** tab -> **Report a vulnerability**. This is the preferred channel for issues in a specific repo.
- **Email**: security@supertokens.com.

To help us assess and fix issues quickly, please include:

- the affected component and version (e.g., SuperTokens Core vX.Y.Z, `supertokens-node` vX.Y.Z);
- the configuration used, including:
  - all non-default configuration passed to all components (to the core, backend and frontend SDKs)
  - all API calls made during the setup, with the exact payloads
- a clear description of the issue and its impact;
- reproduction steps and, where possible, a minimal proof-of-concept.

## What to expect from us

We follow a coordinated disclosure process:

- **Acknowledgement:** within 5 business days.
- **Initial assessment:** within 14 days, including a severity evaluation (CVSS) and whether we consider it in scope.
- **Resolution & disclosure:** we work to a coordinated timeline, typically up to **90 days**, or until a fix is released, whichever comes first. We will keep you informed about progress.
- **Publication:** confirmed vulnerabilities are published as **GitHub Security Advisories** with an associated **CVE**, naming the fixed version and remediation steps. We are ready to coordinate public-disclosure timing with you.

We assess and remediate issues based on their technical merits and a timeline appropriate to their severity. We do not base remediation or disclosure decisions on whether a report is private or public.

## Supported versions

We backport security fixes across SuperTokens Core and our SDKs according to the following policy:

- **All security fixes** are backported for at least one year of releases.
- **Critical security fixes** are backported further, beyond the one-year window.

For SuperTokens Core, this currently means all security fixes are backported to **v11.0** and later, and critical fixes to **v6.0** and later. Each SDK follows the same one-year principle on its own release line. Versions outside these windows are end-of-life and do not receive security fixes; please upgrade to a supported version.

## Scope and deployment model

**Scope:** SuperTokens Core, the backend SDKs, the frontend SDKs, and the SuperTokens managed service.

Please test **only against your own self-hosted or local instances**. Do not test against SuperTokens' managed service, our infrastructure, or any deployment you do not own. Do not access, modify, or exfiltrate others' data.

**Deployment model.** SuperTokens Core is a backend component designed to run on a private, trusted network and to be accessed only by your application's own backend (normally via the SuperTokens SDKs). Per our documentation, when the Core is reachable beyond that trusted network, you must protect it by setting an API key. Reports whose impact depends **solely** on exposing the Core directly to an untrusted network, contrary to this documented guidance, are handled as deployment/hardening feedback and not product vulnerabilities. See: https://supertokens.com/docs/deployment/self-host-supertokens and https://supertokens.com/docs/platform-configuration/supertokens-core/api-keys.

## Behaviors that are intentional by design

The following are documented, intentional design decisions, not vulnerabilities in themselves. Reports resting solely on these behaviors, without a concrete exploit under the documented deployment model, are handled as hardening feedback.

- **No API key by default.** The Core ships without an API key and must be protected with an API key and/or private-network deployment when exposed. See: https://supertokens.com/docs/platform-configuration/supertokens-core/api-keys.
- **The backend is a trusted part of the architecture.** The Core returns codes and tokens to the calling backend: Passwordless/OTP codes, magic-link codes, device IDs, and password-reset tokens are returned in Core API responses because delivery is the responsibility of your application backend; the Core has no channel to the frontend. See: https://supertokens.com/docs/platform-configuration/supertokens-core/security#behaviors-that-are-intentional-by-design.

## Safe harbor

We consider security research and vulnerability disclosure conducted in good faith and in accordance with this policy to be authorized. For such research, we will not pursue or support legal action against you. This authorization applies only to testing against your own instances, requires that you avoid privacy violations and service deterioration, and asks that you give us reasonable time to remediate before any public disclosure.

## Recognition and rewards

We credit researchers who report valid issues in good faith (with your permission).

At our discretion, we may also provide a monetary reward for good-faith reports that follow this policy, based on the severity, quality, and originality of the finding. We evaluate rewards solely on technical merit and never make them contingent on how, whether, or when a reporter files public records or requests identifiers.

## Preferred languages

We prefer reports in English.