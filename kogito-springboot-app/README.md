<!--
  Licensed to the Apache Software Foundation (ASF) under one
  or more contributor license agreements.  See the NOTICE file
  distributed with this work for additional information
  regarding copyright ownership.  The ASF licenses this file
  to you under the Apache License, Version 2.0 (the
  "License"); you may not use this file except in compliance
  with the License.  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing,
  software distributed under the License is distributed on an
  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  KIND, either express or implied.  See the License for the
  specific language governing permissions and limitations
  under the License.
  -->

# org.kie.kogito.kogito-spring-boot-archetype - 10.1.0 #

# Running

- Compile and Run

    ```
    mvn clean package spring-boot:run    
    ```

# Test your application

Generated application comes with sample test process that allows you to verify if the application is working as expected. Simply execute following command to try it out

```sh
curl -d '{}' -H "Content-Type: application/json" -X POST http://localhost:8080/greetings
                                                             
```

Once successfully invoked you should see "Hello World" in the console of the running application.

The generated application provides out of the box multiple samples of Kogito assets; you can reference the generated Swagger documentation and JUnit tests.

# Developing

Add your business assets resources (process definition, rules, decisions) into src/main/resources.

Add your java classes (data model, utilities, services) into src/main/java.

Then just build the project and run.


# OpenAPI (Swagger) documentation
[Specification at swagger.io](https://swagger.io/docs/specification/about/)

You can take a look at the [OpenAPI definition](http://localhost:8080/v3/api-docs) - automatically generated and included in this service - to determine all available operations exposed by this service. For easy readability you can visualize the OpenAPI definition file using a UI tool like for example available [Swagger UI](https://editor.swagger.io).

In addition, various clients to interact with this service can be easily generated using this OpenAPI definition.
# Account Opening Process (ntb.bpmn2)

Process id `accountOpening`, exposed at `/accountOpening`; its 14 human steps are served by the
generated `/usertasks/instance` API. Roles are groups: `FOSS`, `KYC`, `CDD`, `BSA`.

```sh
# start an instance
ID=$(curl -s -d '{}' -H "Content-Type: application/json" http://localhost:8080/accountOpening | jq -r .id)

# see the open tasks for a role
curl -s -H "Content-Type: application/json" \
  "http://localhost:8080/usertasks/instance?user=alice&group=FOSS"

# claim, then complete a task (decisions are sent as task output)
curl -s -H "Content-Type: application/json" -X POST \
  "http://localhost:8080/usertasks/instance/$TASK/transition?user=alice&group=FOSS" \
  -d '{"transitionId":"claim"}'
curl -s -H "Content-Type: application/json" -X POST \
  "http://localhost:8080/usertasks/instance/$TASK/transition?user=alice&group=FOSS" \
  -d '{"transitionId":"complete","data":{"applicantName":"Alice","robChecksPass":true}}'
```

Gateways are driven by these process variables, written by the task that precedes them:
`robChecksPass`, `preCheckPass`, `cif`, `cddDecision` (`OK`/`NOK`), `clientUpgrade`,
`kycDecision` (`APPROVED`/`REJECTED`/anything else = return to CDD), `furtherUpgrade`,
`aoSuccessful`. Every gateway has a default branch, so a missing variable takes the "No" path.

`AccountOpeningTest` drives the whole happy path over REST.

## What changed to make the drawing executable

- `isExecutable="true"`, process id/name/package, `gatewayDirection` on every gateway.
- Task names are restricted to letters, digits and spaces (Kogito builds Java class and method
  names from them); the original labels with numbering and role tags are kept as `documentation`.
- Plain `task`s became `userTask`s with `TaskName`/`GroupId` inputs and output mappings; steps 11
  and 16 (notifications) are script tasks.
- jBPM allows one incoming connection per node, so the `Merge_*` converging gateways were added
  in front of the nodes that had several.
- `Gateway_Fork`/`Gateway_Join` are inclusive (OR) instead of parallel — this is the deadlock the
  original file documented on the "KYC Rejected", "Return to CDD" and "no upgrade" paths.

Still open (modelling decisions, not build problems): `Gateway_1`'s "No" branch skips the Control
Tower pre-check, and `Gateway_4`'s "No" branch re-enters `Task_7`, so the happy path produces two
KYC reviews — the fork's and the one coming back from CDD. Each approval fires the inclusive
`Gateway_Join` separately, so every task downstream of it (`10`, `13`) also opens twice;
`AccountOpeningTest.completeAll` drains all open instances of a task name to work around this.

# Management Console, Task Console and Keycloak

The app embeds a Data Index (GraphQL at `/graphql`, backed by a file-based H2 database at
`target/data/dataindex`) plus the process/task management REST endpoints these consoles call —
no Kafka or separate Data Index service needed.

Keycloak and both consoles live in `docker-compose.yml` one level up (`../docker-compose.yml`),
alongside `../keycloak/kogito-realm.json` — shared with the sibling `account-opening-ui` project,
which logs into the same realm.

```sh
mvn spring-boot:run          # the app itself, in one terminal
(cd .. && docker compose up) # Keycloak (:8081), Management Console (:8280), Task Console (:8380)
```

**Keycloak** — http://localhost:8081, admin console login `admin`/`admin`. Realm `kogito`
(`../keycloak/kogito-realm.json`, imported on every start — edits to it need `docker compose up -d
--force-recreate keycloak` from `..`, since Keycloak won't re-import over an existing realm) has one group
per role used across this app - `FOSS`, `KYC`, `CDD`, `BSA` - and one demo user per group plus an
`admin` user in all four, password same as username (`alice`/`alice`, etc). Add, remove or
re-group users from there instead of hand-typing `group` query params.

**Task Console** — http://localhost:8380. Logs in against that Keycloak realm (client
`kogito-console-react`) - try `alice`/`alice`. The console reads the logged-in user's name and
group membership straight from the ID token and uses them for every task-list/claim/complete call,
so who can see and act on which task now follows Keycloak group membership. It's wired for a
single, fixed runtime via container env vars in `docker-compose.yml` (`KOGITO_ENV_MODE=PROD` is
what turns the Keycloak login on - `DEV` skips it entirely, which is what an earlier version of
this file did before Keycloak existed to log into).

**Management Console** — http://localhost:8280. Click "Connect to a runtime", give it any alias
and `http://localhost:8080` as the URL. No login prompt: it probes that URL for an auth
requirement first, and this app's REST API doesn't require one (see caveat below) — it connects
straight through. From there you can browse process instances started via the `/accountOpening`
API above, abort or retrigger one, and view its BPMN diagram with the current node highlighted.

**What Keycloak does *not* cover**: the app's own REST API (`/accountOpening`, `/usertasks/...`)
still takes `user`/`group` as plain, unverified query params - exactly as in the `curl` examples
above. Task Console now *fills those in* from a real Keycloak login instead of asking you to type
them, and Keycloak is genuinely where you add/remove/re-group users, but nothing stops a direct
`curl` call from claiming to be any user or group. Enforcing that would mean adding
`spring-boot-starter-oauth2-resource-server` to validate a bearer token issued by this same realm
on every request - a real, separate piece of work, not done here.

Note on versions: `kogito.version` is `10.2.0` (bumped from `10.1.0` for the embedded Data Index
addon, `org.kie:kogito-addons-springboot-data-index-jpa`, which only publishes at `10.2.0`). The
console images are pulled prebuilt and don't line up exactly: Management Console's newest tag is
`10.1.0`, Task Console's is `10.0.0` — nothing newer is published for either as of this writing.
This is a version skew against the app's `10.2.0`, but the GraphQL schema and REST contracts they
depend on haven't changed across these close releases.
