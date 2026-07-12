# Repository instructions for AI agents

## Agent documents

- Treat `slop/` as the dedicated directory for documents created for or maintained by AI agents.
- Store agent-facing requirements, technical specifications, implementation backlogs, plans, prompts, handoff notes, and temporary design or analysis artifacts in `slop/`.
- Do not place production source code, application resources, secrets, private API exports, signing material, or generated build artifacts in `slop/`.

## Language

- Write all repository documentation in English, including files under `slop/`, ADRs, plans, backlogs, release notes, and agent handoff notes.
- Write Git commit subjects and bodies in English.

## Local Bambuddy development instance

- Local read-only integration checks may use credentials from the repository-root `.env.local` file.
- Load `BAMBUDDY_BASE_URL`, `BAMBUDDY_OPENAPI_URL`, and `BAMBUDDY_API_KEY` from that file; never copy their values into tracked files, logs, reports, fixtures, prompts, or command output.
- Treat `.env.local` as private local configuration. It must remain ignored by Git and must never be committed.
- CI and automated tests must use synthetic fixtures or mock servers and must not depend on the private instance.
- Do not send mutation requests to the private instance without explicit user authorization for the specific operation.
- Sanitize any response-derived fixture before committing it, removing private hosts, credentials, serial numbers, access codes, and personal inventory data.
