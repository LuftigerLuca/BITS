# BITS

Discord-Bot für den deutschen WarEra-Discord, mit Backend und Web-Dashboard. BITS verknüpft Discord- mit
WarEra-Accounts und vergibt Discord-Rollen anhand von WarEra-Daten.

## Aufbau

| Modul           | Inhalt                                                                          |
|-----------------|---------------------------------------------------------------------------------|
| `shared`        | DTOs der REST-API und Events (kotlinx.serialization)                            |
| `warera-client` | Client für die tRPC-API von WarEra (Spring `RestClient`, Resilience4j)          |
| `backend`       | Spring Boot: REST-API, PostgreSQL (JPA, Flyway), publiziert Events in Redis     |
| `bot`           | JDA: Slash-Commands, ruft das Backend per REST auf, liest Events aus Redis      |
| `frontend`      | Dashboard mit Vite, React und shadcn/ui                                         |

Nur das Backend greift auf Datenbank und WarEra-API zu, nur der Bot auf Discord. Einzige Ausnahme: Für den
Login ins Dashboard ruft das Backend Discord im Namen des Nutzers auf (OAuth2).

- **Bot → Backend:** REST unter `/api/**`, mit dem Service-Token als `Authorization: Bearer <token>`
  (`BackendClient`).
- **Dashboard → Backend:** `/auth/**` und `/web/**` über denselben Origin (nginx bzw. Vite-Dev-Proxy), mit
  der Sitzung als HttpOnly-Cookie. `/api/**` des Bots ist von außen nicht erreichbar.
- **Backend → Bot:** Events im Redis Stream `bits:events` (`EventPublisher`). Der Bot liest sie über die
  Consumer Groups `bot` und, für Role-Syncs, `bot-role-sync` und bestätigt jedes verarbeitete Event mit `XACK`.

Der Command `/ping` geht als Smoke-Test einmal durch alle Teile: Der Bot ruft `POST /api/ping` auf, das Backend
speichert den Ping, fragt die WarEra-API ab und publiziert ein `PingEvent`, das der Bot verarbeitet und bestätigt.

## Funktionen

Was BITS kann und wie Mitglieder, Abstimmungsleiter und Admins es benutzen, beschreibt das Handbuch im Dashboard
unter `/docs`, lokal http://localhost:5173/docs. Es ist ohne Login lesbar. Die Texte stehen in
[`frontend/src/features/manual/manual.ts`](frontend/src/features/manual/manual.ts) und ändern sich zusammen mit dem
Verhalten, das sie beschreiben.

## Datenschutz

BITS speichert nur, was es für die Verknüpfung, die Rollen und die Abstimmungen braucht:

| Daten                                            | Wo                         | Wozu                                | Wie lange                                                                   |
|--------------------------------------------------|----------------------------|-------------------------------------|-----------------------------------------------------------------------------|
| Discord-ID, WarEra-ID und -Name, Zeitpunkt       | `account_link`             | Rollen nach WarEra-Daten, `/profil` | bis zum Lösen, zum Verlassen des Servers oder zu einer neuen Verknüpfung    |
| Discord-ID, Zeitpunkt                            | `nickname_opt_in`          | Nickname aus WarEra                 | bis `/nickname aus` oder zum Verlassen des Servers                          |
| Discord-IDs, -Namen und Rollen der Mitglieder    | Speicher des Backends      | Kennzahlen, Links, Mitgliedersuche  | bis zur nächsten Meldung des Bots oder zum Neustart                         |
| Discord-ID, WarEra-ID und -Name, Code            | `account_verification`     | offene Verifizierung                | bis zur Verknüpfung oder zum nächsten Code, sonst bis 2 Stunden nach Ablauf |
| Discord-ID, WarEra-ID, Stimme                    | `vote_ballot`              | laufende Abstimmung                 | bis zur Auswertung oder zum Abbruch                                         |
| Discord-ID und -Name der Rollenmitglieder        | `vote_elector`             | Auswertung einer Abstimmung         | bis zur Auswertung                                                          |
| Namen: Start, Ende, nicht abgestimmt             | `vote`, `vote_non_voter`   | Archiv der Abstimmungen             | unbegrenzt                                                                  |
| Discord-ID, Name beim Hinzufügen                 | `embassy_manual_member`    | Botschafts-Threads                  | bis ein Admin das Mitglied entfernt oder die Zuordnung aufhebt              |
| Discord-ID, Name, Grund der Thread-Mitglieder    | `embassy_thread_member`    | Botschafts-Threads im Dashboard     | bis zur nächsten Meldung des Bots oder zum Aufheben der Zuordnung           |
| Discord-ID, Zeitpunkt                            | `ping_log`                 | `/ping`                             | 30 Tage                                                                     |
| Discord-ID, Name und Avatar von Admins, Änderung | `audit_entry`              | Audit-Log im Dashboard              | unbegrenzt                                                                  |
| Name des Mitglieds bei Abgleich oder Lösen       | `audit_entry`              | Audit-Log im Dashboard              | unbegrenzt                                                                  |
| Discord-ID und -Name, WarEra-Name, Rollen        | `system_log_*`             | System-Log im Dashboard             | 30 Tage                                                                     |
| Events mit Discord-IDs                           | Redis-Stream `bits:events` | Zustellung an den Bot               | 7 Tage                                                                      |
| Logs mit Discord- und WarEra-IDs                 | Container-Logs             | Fehlersuche                         | im Deployment höchstens 5 × 10 MB pro Dienst                                |
| alles aus der Datenbank                          | Dumps in `BACKUP_PATH`     | Wiederherstellung                   | bis zu 4 Wochen, siehe [deploy/README.md](deploy/README.md#backups)         |

WarEra-Daten der Nutzer hält das Backend nur im Speicher. Ein Hinweis in `/verify` sagt, was gespeichert wird.
Mit `/unverify` oder unter „Mein Account“ im Dashboard löscht ein Nutzer seine Verknüpfung und einen offenen Code, ein
Admin ebenso unter „Mitglieder“; die Rollen, die BITS verwaltet, verliert der Nutzer beim folgenden Abgleich. Wer den
Server verlässt, verliert beides ebenso, spätestens beim nächsten Start des Bots. Abgelaufene Codes und alte Pings löscht
ein Job jede Stunde.

Auskunft holt sich jedes Mitglied selbst unter „Mein Account“: Der Download enthält alle Zeilen der Tabellen oben, die
seine Discord-ID betreffen, aus dem System-Log aber nur seine eigene Seite eines Eintrags und aus dem Audit-Log nur, was
es selbst als Admin geändert hat. Events in Redis, Container-Logs und Backups enthält er nicht. Wie Admins Auskunft
geben oder für jemanden löschen, steht in [deploy/README.md](deploy/README.md#data-protection-requests).

Verifiziert sich ein anderer Discord-Account für einen schon verknüpften WarEra-Account, ersetzt die neue
Verknüpfung die alte. Der vorherige Account bekommt dann eine Direktnachricht vom Bot, falls er welche annimmt.

Warum BITS so gebaut ist, halten die [Architekturentscheidungen](docs/adr/README.md) fest, das Verhalten bei
Ausfällen beschreibt [docs/failure-modes.md](docs/failure-modes.md).

## Starten

Voraussetzungen: JDK 21, Node.js 24, Docker.

```sh
cp .env.example .env   # Werte eintragen
docker compose up --build
```

Für den Discord-Login braucht die Discord-Application unter **OAuth2 → Redirects** die URL
`<BITS_PUBLIC_URL>/auth/oauth2/callback/discord`, lokal also `http://localhost:5173/auth/oauth2/callback/discord`.

- Backend: http://localhost:8080, Health unter `/actuator/health`
- Frontend: http://localhost:5173

Alle Ports sind nur auf `127.0.0.1` gebunden, da Redis lokal ohne Passwort läuft und Docker die Firewall des
Rechners umgeht. Wer von einem anderen Gerät zugreifen will, ändert die Bindung in `docker-compose.yml` bewusst.

## Deployment

Staging (mit eigenem Test-Bot auf einem Test-Server) und später Produktion werden über den Workflow **CD** auf
GitHub deployt. Einrichtung und Betrieb beschreibt [deploy/README.md](deploy/README.md).

## Entwickeln

```sh
./gradlew build                 # alle Module bauen, ktlint, Tests (Backend-Tests brauchen Docker)
./gradlew ktlintFormat          # Kotlin-Code formatieren
docker compose up postgres redis
./gradlew :backend:bootRun      # braucht die Backend-Variablen aus .env.example
cd frontend && npm run dev      # http://localhost:5173, leitet /auth und /web an das Backend weiter
```

## Lizenz

BITS steht unter der [GNU Affero General Public License v3.0](LICENSE) oder, nach Wahl, einer späteren Version
(`AGPL-3.0-or-later`).
