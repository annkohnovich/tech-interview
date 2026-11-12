DROP TABLE IF EXISTS flights;

CREATE TABLE flights (
    flight_id         VARCHAR(20)  PRIMARY KEY,
    flight_number     VARCHAR(10)  NOT NULL,
    airline           VARCHAR(100) NOT NULL,
    departure_airport CHAR(3)      NOT NULL,
    arrival_airport   CHAR(3)      NOT NULL,
    departure_time    TIMESTAMP    NOT NULL,
    arrival_time      TIMESTAMP    NOT NULL,
    delay_minutes     INT          NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    CONSTRAINT chk_delay_non_negative CHECK (delay_minutes >= 0),
    CONSTRAINT chk_airports_differ    CHECK (departure_airport <> arrival_airport)
);
