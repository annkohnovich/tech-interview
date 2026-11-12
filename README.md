# Flight Ingestion — SDET practice project

A deliberately small flight-data ingestion system, built so you can practise
writing **Unit**, **Integration** and **API** tests against it.

**Start with [TRAINING.md](TRAINING.md)**: it adds a second microservice
(`airport-service/`) and walks through unit, mock, contract, integration tests and CI.
Each test file has worked examples and TODOs for you to write.

```
flights.json → FlightParser → Flight → FlightValidator → FlightService
                                                              ↓
                                        FlightRepository → H2 → FlightController
```

---

## Run it

```bash
mvn spring-boot:run          # http://localhost:8080
mvn test                     # unit tests      (*Test)
mvn verify                   # + integration/API tests (*IT)
```

Requires **Java 17+** and Maven. H2 runs in-memory — no external infrastructure.
The schema in `src/main/resources/schema.sql` is applied on every startup and on
every test Spring context, so tests always begin against a known table.

Browse the data at `http://localhost:8080/h2-console`
(JDBC URL `jdbc:h2:mem:flightsdb`, user `sa`, no password).

---

## API

| Method | Path                | Success           | Failure |
|--------|---------------------|-------------------|---------|
| GET    | `/flights`          | `200` array       | — |
| GET    | `/flights/{id}`     | `200` flight      | `404` unknown id |
| POST   | `/flights`          | `201` + `Location`| `400` invalid body/rules · `409` duplicate |
| POST   | `/flights/import`   | `200` report      | `400` unparseable file / missing file part |

`POST /flights/import` takes `multipart/form-data` with a part named `file`.

```bash
curl -F "file=@src/test/resources/testdata/flights.json" \
     http://localhost:8080/flights/import
```

Every error returns the same shape:

```json
{
  "timestamp": "2026-09-08T10:00:00Z",
  "status": 400,
  "error": "Validation Failed",
  "message": "Flight failed validation",
  "details": ["delayMinutes: must not be negative"]
}
```

---

## The one idea worth internalising: three failure tiers

The reason this project exists is that "bad data" is not one thing. Each tier
fails in a different layer, and each is tested differently.

| Tier | Concern | Fails in | Example fixture |
|------|---------|----------|-----------------|
| **1. Structural** | JSON syntax, wrong types, unparseable dates, unknown enum | `FlightParser` — the `Flight` object is never built | `flights-invalid-type.json` |
| **2. Business (per record)** | null/blank fields, negative delay, same airports, arrival before departure | `FlightValidator` — object exists, rules broken | `flights-negative-delay.json` |
| **3. Cross-record / persistence** | duplicate `flightId` | `FlightService` + the `flight_id` primary key | `flights-duplicate.json` |

A missing field is **not** a parsing error — it deserialises to `null` and is
caught in tier 2. A `"delayMinutes": "abc"` **is** a parsing error and never
reaches tier 2. Keeping these apart is what makes each layer independently
testable.

---

## Invalid-record policy

Documented in `FlightService`, restated here because tests assert on it:

1. **Parse failure is fail-fast for the whole file.** One structurally broken
   record rejects the entire batch, persists nothing, and returns `400`.
   A broken file means a broken producer; importing half of it hides that.
2. **Validation failure is per record and non-fatal.** Valid records are
   persisted, invalid ones are skipped and listed in the `IngestionReport`.
   The import still returns `200` — partial success is intended.
3. **Duplicates are rejected, never updated.**
   - Duplicate of a row already in the database → rejected.
   - Duplicate *within the same file* → **first wins**, later ones rejected.

   Reject-not-upsert is the safe default for an append-only feed; an upsert
   would silently rewrite history.

The whole ingest runs in one transaction, so a database failure rolls the batch
back rather than leaving a half-imported file behind.

---

## Test fixtures — `src/test/resources/testdata/`

| File | Tier | What it exercises |
|------|------|-------------------|
| `flights.json` | — | 8 valid records, the happy path |
| `flights-mixed.json` | 2 | 2 valid + 1 invalid → partial success |
| `flights-missing-required-field.json` | 2 | `airline` absent → `null` |
| `flights-empty-required-field.json` | 2 | `flightNumber` blank, `airline` empty |
| `flights-negative-delay.json` | 2 | `delayMinutes: -30` |
| `flights-same-airport.json` | 2 | `AMS → AMS` |
| `flights-duplicate.json` | 3 | same `flightId` twice in one file |
| `flights-invalid-type.json` | 1 | `delayMinutes: "not-a-number"` |
| `flights-invalid-date.json` | 1 | `"2026-13-45T99:99:00"` |
| `flights-invalid-status.json` | 1 | `"status": "FLYING"` |
| `flights-malformed.json` | 1 | broken JSON syntax |
| `flights-empty-array.json` | — | `[]`, the empty-input edge case |

---

## Design decisions (and their trade-offs)

**Hand-written validator instead of Bean Validation.** `FlightValidator` is a
pure function — `Flight` in, `List<ValidationError>` out — so it unit tests with
`new FlightValidator()` and no Spring context, and cross-field rules
(`departureAirport != arrivalAirport`) stay readable. With Bean Validation you
would annotate `Flight` with `@NotBlank`/`@PositiveOrZero`, add `@Valid` in the
controller, and write a class-level `@ConstraintValidator` for the cross-field
rules — more moving parts, and the controller path and ingestion path would then
validate through different mechanisms. One explicit path is easier to test.

**JdbcTemplate instead of JPA.** Every SQL statement is visible, so there is no
ORM behaviour (dirty checking, lazy loading, flush ordering) to reason about
while you are trying to test your own logic.

**`Flight` is a record.** Immutable, Jackson deserialises it natively, and
`equals` is free — which matters when you assert
`assertThat(actual).isEqualTo(expected)`.

**The parser builds its own `ObjectMapper`.** Not injected, so unit tests are
`new FlightParser()`. Note the controller's `@RequestBody` uses *Spring's*
mapper, not this one — a real difference worth knowing when a `POST /flights`
test behaves differently from a parser test.

**Unknown JSON fields are ignored.** `FAIL_ON_UNKNOWN_PROPERTIES` is disabled so
extra fields in the feed do not break ingestion. Flipping it to strict is a
one-line change and a good test scenario.

**Database constraints duplicate the validator.** `CHECK (delay_minutes >= 0)`
and the `flight_id` primary key restate rules the validator already enforces.
That redundancy is deliberate: it is the safety net if the service check is ever
bypassed, and `DuplicateKeyException` is mapped to `409` for exactly that case.

---

## Layout

```
src/main/java/com/example/flights/
  FlightIngestionApplication.java
  controller/   FlightController, GlobalExceptionHandler, ErrorResponse
  service/      FlightService + 3 exceptions
  repository/   FlightRepository            (JdbcTemplate)
  parser/       FlightParser, FlightParseException
  validator/    FlightValidator
  model/        Flight, FlightStatus, ValidationError, IngestionReport
src/main/resources/
  application.properties, schema.sql
src/test/resources/testdata/
  flights.json + 11 fixtures
```

Naming convention wired into the build: **`*Test`** runs under Surefire
(`mvn test`), **`*IT`** runs under Failsafe (`mvn verify`).
