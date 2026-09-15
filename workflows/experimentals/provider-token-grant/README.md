# Provider token grant consumer (experimental)

This prototype answers the SonataFlow-side consumer question for RHIDP-15907:
the workflow does not retrieve a token into workflow state. A custom Java
operation receives an opaque `{ grantId, provider }` reference, calls the
secure-token-storage broker with a SonataFlow service credential, and uses the
short-lived access token immediately for GitHub's `/user` API. Only `id`,
`login`, and `name` are returned to the workflow.

The access token is intentionally never returned, logged, persisted in state,
or included in an error message. This is an experimental implementation and
will eventually be replaced by the production provider-call adapter.

## Run locally

The broker must be reachable from the workflow runtime, and its configured
caller allow-list must contain the service identity represented by the service
credential. From this directory:

```bash
export SECURE_TOKEN_STORAGE_URL=http://host.containers.internal:7007/api/secure-token-storage/token
export SECURE_TOKEN_STORAGE_SERVICE_TOKEN='<Backstage external-access service token>'
./mvnw quarkus:dev
```

Start an instance with an opaque grant reference:

```bash
curl -X POST http://localhost:8080/provider-token-grant \
  -H 'Content-Type: application/json' \
  -d '{"grantId":"<grant-id>","provider":"github"}'
```

For a Podman container, `host.containers.internal` is the convenient default
for reaching a broker running on the host. In-cluster deployment should use
the Backstage service URL instead.

## Current integration boundary

The current Orchestrator prototype forwards grant references in the
`X-Provider-Token-Grants` metadata header. This workflow currently accepts the
same reference as input so it can be run independently while the SonataFlow
runtime adapter is being finalized. The next integration step is to map that
header into the operation input without copying token material into workflow
data.
