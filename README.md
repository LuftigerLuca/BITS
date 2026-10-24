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

Nur das Backend greift auf Datenbank und WarEra-API zu, nur der Bot auf Discord. Einzige Ausnahme: Für den
Login ins Dashboard ruft das Backend Discord im Namen des Nutzers auf (OAuth2).

- **Bot → Backend:** REST unter `/api/**`, mit dem Service-Token als `Authorization: Bearer <token>`
  (`BackendClient`).
- **Dashboard → Backend:** `/auth/**` und `/web/**` über denselben Origin (nginx bzw. Vite-Dev-Proxy), mit
  der Sitzung als HttpOnly-Cookie. `/api/**` des Bots ist von außen nicht erreichbar.
- **Backend → Bot:** Events im Redis Stream `bits:events` (`EventPublisher`). Der Bot liest sie über die
  Consumer Group `bot` und bestätigt jedes verarbeitete Event mit `XACK`.

Der Command `/ping` geht einmal durch alle Teile: Der Bot ruft `POST /api/ping` auf, das Backend speichert den
Ping, fragt die WarEra-API ab und publiziert ein `PingEvent`, das der Bot verarbeitet und bestätigt.

Mit `/verify <WarEra-Name>` verknüpft ein Nutzer seinen Discord-Account mit seinem WarEra-Account. Er bekommt
einen Einmal-Code, der 15 Minuten gültig ist, schreibt ihn in den Namen einer seiner WarEra-Firmen und klickt auf
„Prüfen“. Das Backend sucht den Code über die öffentliche API, speichert die Verknüpfung und publiziert ein
`AccountLinkedEvent`. Der Code zählt nur in Firmen, die dem Nutzer gehören. Mit `/unverify` löst er die Verknüpfung
wieder.

Im Dashboard legen Admins unter „Verwaltung“ Regeln an, die eine Discord-Rolle an ein WarEra-Merkmal koppeln:
Staatsbürgerschaft, Regierungsamt in einem Land, Mitgliedschaft in einer Military Unit, Level oder Militärrang ab
einem Schwellwert. Mehrere Regeln für dieselbe Rolle gelten als ODER. Eine Rolle mit mindestens einer Regel
verwaltet BITS: Wer keine ihrer Regeln erfüllt, verliert sie, auch wenn sie von Hand vergeben wurde. Das Backend
berechnet die Soll-Rollen und publiziert ein `RoleSyncEvent`, der Bot setzt es mit Grund im Audit-Log um. Abgeglichen
wird beim Verknüpfen für den Nutzer (und den zuvor verknüpften Discord-Account), nach jeder Regeländerung für alle
mit den WarEra-Daten des letzten Abgleichs und stündlich für alle mit frischen Daten, eine WarEra-Abfrage pro
Sekunde. Kann ein Nutzer oder eine Regierung nicht geladen werden, bleiben die betroffenen Rollen unverändert. Der
Bot meldet die Rollen des Servers per `PUT /api/discord-roles` beim Start und bei jeder Änderung; Regeln für
gelöschte Rollen gelten als verwaist und werden nicht ausgeführt. Dafür braucht der Bot den privilegierten
Server-Members-Intent und die Berechtigung „Rollen verwalten“.

Im Dashboard melden sich Mitglieder des Discord-Servers mit Discord an (OAuth2, Scopes `identify guilds`).
Wer auf dem Server die Berechtigung Administrator hat oder ihn besitzt, bekommt die Rolle `ADMIN` und sieht
den Verwaltungsbereich, alle anderen `USER`. Zusätzlich gibt es optional einen Admin-Account mit Benutzername
und Passwort aus der Umgebung (`BITS_ADMIN_USERNAME`, `BITS_ADMIN_PASSWORD_HASH`), der ohne Discord
funktioniert. Pro IP werden in 15 Minuten höchstens 5 Anmeldeversuche geprüft, über alle IPs höchstens 50; danach
ist er bis zum Ende des Zeitfensters gesperrt. Der Hash braucht BCrypt-Kosten von mindestens 10. Die Sitzung ist
ein JWT im Cookie, gilt 8 Stunden, für `ADMIN` 1 Stunde, und wird nicht verlängert. Rollen werden nur beim Login
geprüft. Der Logout sperrt die Sitzung auch für Kopien des Cookies.

## Datenschutz

BITS speichert nur, was es für die Verknüpfung und die Rollen braucht:

| Daten                                            | Wo                         | Wozu                              | Wie lange                                                                   |
|--------------------------------------------------|----------------------------|-----------------------------------|-----------------------------------------------------------------------------|
| Discord-ID, WarEra-ID und -Name, Zeitpunkt       | `account_link`             | Rollen nach WarEra-Daten vergeben | bis `/unverify` oder bis eine neue Verknüpfung sie ersetzt                  |
| Discord-ID, WarEra-ID und -Name, Code            | `account_verification`     | offene Verifizierung              | bis zur Verknüpfung oder zum nächsten Code, sonst bis 2 Stunden nach Ablauf |
| Discord-ID, Zeitpunkt                            | `ping_log`                 | `/ping`                           | 30 Tage                                                                     |
| Events mit Discord-IDs                           | Redis-Stream `bits:events` | Zustellung an den Bot             | 7 Tage                                                                      |
| Logs mit Discord- und WarEra-IDs                 | Container-Logs             | Fehlersuche                       | im Deployment höchstens 5 × 10 MB pro Dienst                                |
| alles aus der Datenbank                          | Dumps in `BACKUP_PATH`     | Wiederherstellung                 | bis zu 4 Wochen, siehe [deploy/README.md](deploy/README.md#backups)         |

WarEra-Daten der Nutzer hält das Backend nur im Speicher. Ein Hinweis in `/verify` sagt, was gespeichert wird.
Mit `/unverify` löscht ein Nutzer seine Verknüpfung und einen offenen Code; die Rollen, die BITS verwaltet, verliert
er beim folgenden Abgleich. Abgelaufene Codes und alte Pings löscht ein Job jede Stunde. Wie Admins Auskunft geben
oder für jemanden löschen, steht in [deploy/README.md](deploy/README.md#data-protection-requests).

Verifiziert sich ein anderer Discord-Account für einen schon verknüpften WarEra-Account, ersetzt die neue
Verknüpfung die alte. Der vorherige Account bekommt dann eine Direktnachricht vom Bot, falls er welche annimmt.

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
