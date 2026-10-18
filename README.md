# BITS

Discord-Bot für den deutschen WarEra-Discord, mit Backend und Web-Dashboard. BITS verknüpft Discord- mit
WarEra-Accounts und gleicht Rollen und Nicknames anhand von WarEra-Daten ab.

## Aufbau

| Modul           | Inhalt                                                                          |
|-----------------|---------------------------------------------------------------------------------|
| `shared`        | DTOs der REST-API und Events (kotlinx.serialization)                            |
| `warera-client` | Client für die tRPC-API von WarEra (Spring `RestClient`, Resilience4j)          |
| `backend`       | Spring Boot: REST-API, PostgreSQL (JPA, Flyway), publiziert Events in Redis     |
| `bot`           | JDA: Slash-Commands, ruft das Backend per REST auf, liest Events aus Redis      |
| `frontend`      | Dashboard mit Vite, React und shadcn/ui                                         |

Nur das Backend greift auf Datenbank und WarEra-API zu, nur der Bot auf Discord.

- **Bot → Backend:** REST unter `/api/**`, mit dem Service-Token als `Authorization: Bearer <token>`
  (`BackendClient`).
- **Backend → Bot:** Events im Redis Stream `bits:events` (`EventPublisher`). Der Bot liest sie über die
  Consumer Group `bot` und bestätigt jedes verarbeitete Event mit `XACK`.

Der Command `/ping` geht einmal durch alle Teile: Der Bot ruft `POST /api/ping` auf, das Backend speichert den
Ping, fragt die WarEra-API ab und publiziert ein `PingEvent`, das der Bot verarbeitet und bestätigt.

## Starten

Voraussetzungen: JDK 21, Node.js 24, Docker.

```sh
cp .env.example .env   # Werte eintragen
docker compose up --build
```

- Backend: http://localhost:8080, Health unter `/actuator/health`
- Frontend: http://localhost:5173

## Entwickeln

```sh
./gradlew build                 # alle Module bauen, ktlint, Tests (Backend-Tests brauchen Docker)
./gradlew ktlintFormat          # Kotlin-Code formatieren
docker compose up postgres redis
./gradlew :backend:bootRun      # braucht BITS_SERVICE_TOKEN
cd frontend && npm run dev
```
