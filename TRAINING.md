# Training plan — QA Automation (Java / API)

Hands-on prep for a backend QA automation role: Java, REST Assured, mocks,
contract tests, integration tests, CI/CD, quality gates.

## The setup: two microservices

```
                     ┌──────────────────────────┐   GET /airports/{iata}   ┌───────────────────┐
  REST Assured ───▶  │ flight-service  :8080     │ ───────────────────────▶ │ airport-service   │
                     │ (this folder)             │ ◀─ 200 / 404 / 5xx ───── │ :8081             │
                     │ controller → service → H2 │                          │ (./airport-service)│
                     └──────────────────────────┘                          └───────────────────┘
```

flight-service now asks airport-service whether each airport **exists** and is **open**:

| airport-service says | flight-service does |
|---|---|
| `200`, `active: true` | accept |
| `200`, `active: false` (e.g. TXL) | reject record → `400` / listed in import report |
| `404` | reject record → `400` "unknown airport XYZ" |
| `5xx`, timeout, connection refused | **`503`**: not the data's fault, nothing saved |

Run both for real:

```bash
cd airport-service && mvn spring-boot:run      # terminal 1
mvn spring-boot:run                             # terminal 2 (project root)
curl -F "file=@src/test/resources/testdata/flights.json" localhost:8080/flights/import
```

Stop airport-service and import again: you get `503`.

---

## The levels: do them in order

Each test file has worked **EXAMPLES** at the top and **TODOs** at the bottom.
Read the examples, then write the TODOs yourself. Run one class at a time:
`mvn test -Dtest=FlightServiceTest`.

| # | Level | File | What is real / what is fake | Runs in |
|---|-------|------|------------------------------|---------|
| 0 | Unit, pure | `validator/FlightValidatorTest` | everything real, no dependencies | `mvn test` |
| 1 | Unit + **Mockito** | `service/FlightServiceTest` | service real; repository, client, parser **mocked** | `mvn test` |
| 2 | HTTP client + **WireMock** | `client/AirportClientTest` | real HTTP; the other service is **stubbed** | `mvn test` |
| 3 | Web slice | `controller/FlightControllerWebMvcTest` | Spring MVC real; service is a `@MockBean` | `mvn test` |
| 4 | **Contract** (Pact) | `contract/AirportClientPactTest` + `airport-service/.../AirportProviderPactTest` | consumer writes `pacts/*.json`; provider replays it | `mvn test` in both |
| 5 | **Integration / API** | `api/FlightApiIT` | whole app, H2 and REST Assured real; airport-service = WireMock | `mvn verify` |
| 6 | CI + quality gates | `Jenkinsfile`, `.github/workflows/ci.yml`, `pom.xml` | — | pipeline |

### Mock vs stub vs spy vs fake (common interview question)

- **Stub**: returns canned answers. `when(client.findByIata("WAW")).thenReturn(...)`, or a WireMock stub.
- **Mock**: a stub you also **verify** interactions on. `verify(repository, never()).save(any())`.
- **Spy**: a real object with some methods overridden. `spy(new FlightValidator())`. Use rarely.
- **Fake**: a working lightweight implementation. H2 instead of Postgres, airport-service's in-memory map.

### Which tool at which level

- **Mockito** replaces a Java object, so no HTTP happens. Use it to test *your logic's decisions*.
- **WireMock** replaces a remote service, but the HTTP call is real. Use it to test *the integration edge*: URL, JSON mapping, status codes, timeouts, faults.
- **Pact** makes sure your WireMock-style assumptions about the provider are *still true*. A stub is your private guess; a contract is verified by both sides.
- **REST Assured** is the HTTP client your *tests* use to call your API.

---

## Level 4: the contract loop, by hand

```bash
mvn test -Dtest=AirportClientPactTest          # writes pacts/flight-service-airport-service.json
cat pacts/flight-service-airport-service.json   # read it: this IS the contract
cd airport-service && mvn test                  # provider verifies it
```

Then break it on purpose: rename `active` → `isOpen` in `airport-service/.../Airport.java`
and run the provider test again. Read the failure, then revert.

Talking point: *"In a real team the pact goes to a **Pact Broker**, and the provider's CI
pulls it. `can-i-deploy` asks the broker whether this version is compatible with
what's in production."*

---

## Level 6: CI/CD and quality gates

A **quality gate** is an automated pass/fail rule that stops a bad build moving forward.
This project has four:

| Gate | Where | Fails when |
|---|---|---|
| Unit + consumer contract tests | `mvn test` (Surefire) | any `*Test` fails |
| Provider contract verification | `airport-service: mvn test` | provider no longer satisfies the pact |
| Integration tests | `mvn verify` (Failsafe) | any `*IT` fails |
| **Coverage ≥ 80% lines** | `mvn verify` (JaCoCo `check`) | coverage below `coverage.minimum` in `pom.xml` |

Watch a gate fail:

```bash
mvn verify -Dcoverage.minimum=0.99
```

Coverage report: `target/site/jacoco/index.html`. Allure report: `mvn allure:serve`
(it shows every REST Assured request and response, attached by `AllureRestAssured`).

Pipeline order, the same in `Jenkinsfile` and GitHub Actions: **cheap and fast first**
(unit → contract → integration), every stage publishes JUnit XML, and a red stage
stops everything after it.

Things companies add on top, worth naming in the interview: **SonarQube** quality gate
(coverage on *new* code, bugs, code smells, duplications), static analysis
(Checkstyle / SpotBugs), dependency vulnerability scan (OWASP Dependency-Check),
flaky-test quarantine, and deploy → smoke test on a test environment.

### Exercises

1. Do some TODOs, then raise `coverage.minimum` to `0.85`.
2. Add a gate: `maven-checkstyle-plugin` with `failOnViolation`.
3. Run Jenkins locally (`brew install jenkins-lts`, then `brew services start jenkins-lts`,
   then http://localhost:8080; stop flight-service first, they share the port). Create a
   **Pipeline** job, point it at this folder's Git repo, and watch the stages run.
4. Split the pipeline so integration tests run only on `main`, and unit tests on every branch.

---

## Not covered here (in the job ad, worth practising next)

- **SOAP / WSDL**: REST Assured can post XML envelopes; assert with `XmlPath`.
- **Kafka**: Testcontainers Kafka, or `spring-kafka-test`'s `@EmbeddedKafka`.
- **Testcontainers + Postgres** instead of H2 (needs Docker).
