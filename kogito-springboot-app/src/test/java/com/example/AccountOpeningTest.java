/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.example;

import java.util.List;
import java.util.Map;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives ntb.bpmn2 (Account Opening Process) over REST: start an instance, then claim and
 * complete every user task on the happy path until the instance completes.
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = KogitoApplication.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class AccountOpeningTest {

    private static final List<String> GROUPS = List.of("FOSS", "KYC", "CDD", "BSA");

    @LocalServerPort
    int port;

    @Test
    public void happyPathOpensAccount() {
        RestAssured.port = port;

        String id = given()
                .contentType(ContentType.JSON)
                .body("{}")
            .when()
                .post("/accountOpening")
            .then()
                .statusCode(201)
                .extract().path("id");
        assertNotNull(id);

        complete("Fill static data in ROB", Map.of("applicantName", "Alice", "robChecksPass", true));
        complete("Control Tower pre check", Map.of("preCheckPass", true));
        complete("Reserve a CIF", Map.of("cif", "CIF-0001"));
        complete("Submit to CDD", Map.of());

        // Gateway_Fork runs KYC review and CDD review in parallel.
        complete("CDD review", Map.of("cddDecision", "OK", "clientUpgrade", false));

        // As modelled, Gateway_4 "No" re-enters Task_7, so the fork's KYC review and the one
        // coming back from CDD both open: two reviews on the happy path (occasionally jBPM's
        // inclusive Gateway_Join merges the resulting tokens down to one downstream task
        // instead of two - either is valid OR-join behaviour, so completeAll() below drains
        // however many are actually open rather than assuming a fixed count).
        completeAll("KYC review in OKP", Map.of("kycDecision", "APPROVED"));
        completeAll("PBI and external account opening", Map.of("aoSuccessful", true));
        completeAll("Sync portfolio data from RBO to PBI", Map.of());

        JsonPath remaining = tasks();
        assertTrue(remaining.getList("id").isEmpty(), "instance should have no open tasks left: "
                + remaining.getList("taskName"));
        given().when().get("/accountOpening/" + id).then().statusCode(404);
    }

    /** Claims and completes the first available task with the given name. */
    private void complete(String taskName, Map<String, Object> data) {
        JsonPath open = tasks();
        List<String> ids = open.getList("findAll { it.taskName == '" + taskName + "' }.id");
        assertFalse(ids.isEmpty(), "no open '" + taskName + "', open tasks: " + open.getList("taskName"));
        String taskId = ids.get(0);

        transition(taskId, "claim", Map.of());
        transition(taskId, "complete", data);
    }

    /** Completes every currently open task instance with the given name (see Gateway_Join note above). */
    private void completeAll(String taskName, Map<String, Object> data) {
        int openCount = tasks().getList("findAll { it.taskName == '" + taskName + "' }.id").size();
        for (int i = 0; i < openCount; i++) {
            complete(taskName, data);
        }
    }

    private void transition(String taskId, String transitionId, Map<String, Object> data) {
        given()
                .contentType(ContentType.JSON)
                .queryParam("user", "admin")
                .queryParam("group", GROUPS)
                .body(Map.of("transitionId", transitionId, "data", data))
            .when()
                .post("/usertasks/instance/{taskId}/transition", taskId)
            .then()
                .statusCode(200);
    }

    private JsonPath tasks() {
        return given()
                .contentType(ContentType.JSON)
                .queryParam("user", "admin")
                .queryParam("group", GROUPS)
            .when()
                .get("/usertasks/instance")
            .then()
                .statusCode(200)
                .extract().jsonPath();
    }
}
