# Account Opening UI

Task inbox + forms for the Account Opening process running in `kogito-springboot-app`
(the FOSS/KYC/CDD/BSA groups from `ntb.bpmn2`). Talks to that app's REST API through a
client generated from `src/main/resources/openapi/account-opening-api.yaml` by
openapi-generator, and logs in via the same Keycloak realm as its Task/Management Consoles.

## Run

```
# 1. Keycloak (+ consoles), from the parent directory shared with kogito-springboot-app:
(cd .. && docker compose up -d keycloak)

# 2. kogito-springboot-app:
(cd ../kogito-springboot-app && mvn spring-boot:run)

# 3. this project:
mvn spring-boot:run
open http://localhost:8090
```

Log in as `alice`/`alice` (FOSS), `bob`/`bob` (KYC), `carol`/`carol` (CDD) or `dave`/`dave` (BSA).

## ADFS

`../keycloak/kogito-realm.json` (shared with kogito-springboot-app) has a disabled `adfs` SAML identity
provider as a placeholder. To go live: fill in `singleSignOnServiceUrl`, `entityId` and
`signingCertificate` from your ADFS server's federation metadata
(`https://<adfs-host>/federationmetadata/2007-06/federationmetadata.xml`), set
`"enabled": true`, and re-import the realm. This app itself needs no changes - it still
just does OIDC login against Keycloak, which does the ADFS hop.
