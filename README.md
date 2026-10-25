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
  Consumer Group `bot` und bestätigt jedes verarbeitete Event mit `XACK`.

Der Command `/ping` geht als Smoke-Test einmal durch alle Teile: Der Bot ruft `POST /api/ping` auf, das Backend
speichert den Ping, fragt die WarEra-API ab und publiziert ein `PingEvent`, das der Bot verarbeitet und bestätigt.

Mit `/verify <WarEra-Name>` verknüpft ein Nutzer seinen Discord-Account mit seinem WarEra-Account. Er bekommt
einen Einmal-Code, der 15 Minuten gültig ist, schreibt ihn in den Namen einer seiner WarEra-Firmen und klickt auf
„Prüfen“. Das Backend sucht den Code über die öffentliche API, speichert die Verknüpfung und publiziert ein
`AccountLinkedEvent`. Der Code zählt nur in Firmen, die dem Nutzer gehören. Mit `/unverify` oder unter „Mein
Account“ im Dashboard löst er die Verknüpfung wieder. Wer den Server verlässt, verliert seine Verknüpfung und muss sich nach einem erneuten Beitritt neu
verifizieren.

Mit `/profil` zeigt der Bot im Kanal das WarEra-Profil des eigenen verknüpften Accounts, mit `/profil @nutzer` das
eines anderen Mitglieds: Name mit Link zu WarEra, Level, Militärrang, Staatsbürgerschaft und Military Unit. Das
Backend lädt den Nutzer dafür frisch über `POST /api/profiles`; die Namen von Land und Military Unit kommen aus dem
Cache. Hinweise, etwa auf eine fehlende Verknüpfung, sieht nur, wer den Command aufgerufen hat.

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

Bevor eine Regel angelegt oder gelöscht wird, zeigt das Dashboard, wem die Änderung die Rolle gibt und wem sie sie
nimmt, aufklappbar mit Namen (`POST /web/admin/role-sync/rules/preview`, `GET …/rules/{id}/deletion-preview`).
Das Backend wertet dafür die geänderten Regeln wie der folgende Abgleich aus, mit den WarEra-Daten des letzten
Abgleichs und den Rollen, die der Bot mit der Mitgliederliste meldet, und speichert nichts. Mitglieder ohne WarEra-Daten
aus dem letzten Abgleich zählen als „unbekannt“. Nach einem Neustart des Backends gibt es die Vorschau erst mit der
nächsten Mitgliederliste und ist bis zum nächsten stündlichen Abgleich ungenau; das Dashboard sagt das dazu.

Mit `/nickname an` oder dem Button „Nickname übernehmen“ nach der Verknüpfung entscheidet ein Mitglied selbst, dass
BITS seinen Nickname auf den WarEra-Namen setzt; das geht auch vor dem Verknüpfen. Der Bot gleicht ihn bei jedem
Abgleich an, also auch nach einer Umbenennung in WarEra spätestens nach einer Stunde, und nennt „BITS: WarEra-Name“ als
Grund im Audit-Log. Beim Lösen der Verknüpfung oder wenn ein anderer Discord-Account den WarEra-Account übernimmt,
entfernt er den Nickname wieder, sofern er noch dem WarEra-Namen entspricht. Mit `/nickname aus` hört BITS auf, der
Nickname bleibt, wie er ist; Mitglieder ohne Opt-in fasst BITS nie an. Kann ein Nutzer nicht geladen werden, bleibt
sein Nickname ebenfalls. Dafür braucht der Bot die Berechtigung „Nicknames verwalten“. Den Server-Besitzer und
Mitglieder über seiner Rolle kann er nicht umbenennen; das landet als übersprungene Änderung im System-Log.

Mit `/rollen` sieht ein verknüpftes Mitglied, nur für sich, zu jeder Rolle, die BITS verwaltet, ob es sie
erfüllt und warum: die greifende Regel oder was fehlt, etwa „Level 8 von 10“. Das Backend wertet die Regeln über
`POST /api/role-explanations` mit derselben Logik wie der Abgleich aus, aber mit frischen WarEra-Daten, und ändert
keine Rollen; Abweichungen gleicht der nächste Abgleich an. Verwaiste Regeln tauchen nicht auf.

Mit `/abstimmung starten frage rolle kanal [ende]` startet jemand eine Ja/Nein/Enthaltung-Abstimmung, ebenso im
Dashboard unter „Abstimmungen“. Das Ende ist eine Dauer wie `48h` (Standard), `3d` oder `90m` oder `offen`. Der Bot
postet die Frage mit drei Buttons im Kanal und pingt die Rolle. Abstimmen darf, wer die Rolle und einen verknüpften
WarEra-Account hat; die Antwort sieht nur der Abstimmende, und bis zum Ende kann er seine Stimme ändern. Die
Nachricht zeigt nur, wie viele abgestimmt haben. Zum Ende, vorzeitig mit `/abstimmung beenden`, meldet der Bot die
aktuellen Mitglieder der Rolle; Stimmen von Mitgliedern, die die Rolle nicht mehr haben, zählen nicht. Das Backend
ordnet die Stimmen über WarEra den Parteien im Land der Staatsbürgerschaft zu, wer in keiner ist, gilt als
„Parteilos“. Der Bot postet das Ergebnis als Grafik und Text im Kanal, mit den Namen derer, die nicht abgestimmt
haben. Danach löscht BITS, wer wie abgestimmt hat. `/abstimmung abbrechen` beendet eine Abstimmung ohne Auswertung.
Starten, beenden und abbrechen dürfen Admins und die Rollen, die Admins auf der Seite „Abstimmungen“ freigeben. Damit
der Bot nur Kanäle anbietet, in denen er posten kann, meldet er seine Textkanäle per `PUT /api/discord-channels`.

Admins sehen auf der Startseite des Dashboards, wie viele Mitglieder der Server ohne Bots hat, wie viele davon
verifiziert sind und wie viele Bürger (gesamt und aktiv) das Land hat, das sie unter „Verwaltung“ einstellen. Dafür
meldet der Bot die Mitglieder mit ihren Rollen per `PUT /api/discord-members`, sobald alle geladen sind, beim Start,
stündlich und einige Sekunden nach Rollenänderungen, dazu jeden Beitritt und Austritt. Das Backend hält die Liste nur im Speicher und löscht die Verknüpfungen aller, die
darin fehlen. Würde eine Liste mehr als die Hälfte und mindestens 10 Verknüpfungen auf einmal löschen, verweigert
es sie als vermutlich fehlerhaft und vermerkt das im System-Log.

Im Dashboard melden sich Mitglieder des Discord-Servers mit Discord an (OAuth2, Scopes
`identify guilds guilds.members.read`); seine Rollen auf dem Server liest das Backend dabei, damit das Dashboard
weiß, wer Abstimmungen verwalten darf. Wer auf dem Server die Berechtigung Administrator hat oder ihn besitzt,
bekommt die Rolle `ADMIN` und sieht den Verwaltungsbereich, alle anderen `USER`. Zusätzlich gibt es optional einen
Admin-Account mit Benutzername und Passwort aus der Umgebung (`BITS_ADMIN_USERNAME`, `BITS_ADMIN_PASSWORD_HASH`), der
ohne Discord funktioniert. Pro IP werden in 15 Minuten höchstens 5 Anmeldeversuche geprüft, über alle IPs höchstens 50; danach
ist er bis zum Ende des Zeitfensters gesperrt. Der Hash braucht BCrypt-Kosten von mindestens 10. Die Sitzung ist
ein JWT im Cookie, gilt 8 Stunden, für `ADMIN` 1 Stunde, und wird nicht verlängert. Rollen werden nur beim Login
geprüft. Der Logout sperrt die Sitzung auch für Kopien des Cookies.

Unter „Mein Account“ sieht jedes Mitglied, das sich mit Discord anmeldet, seine Verknüpfung, den letzten Abgleich
aller Mitglieder und die verwalteten Rollen mit Begründung wie bei `/rollen` (`/web/account`). Es kann dort die
Verknüpfung lösen, was wie `/unverify` wirkt, und als JSON herunterladen, was BITS zu seiner Discord-ID speichert.
Die Discord-ID kommt immer aus der Sitzung, nie aus dem Request; der Admin-Account ohne Discord hat die Seite nicht.

## Datenschutz

BITS speichert nur, was es für die Verknüpfung, die Rollen und die Abstimmungen braucht:

| Daten                                            | Wo                         | Wozu                                | Wie lange                                                                   |
|--------------------------------------------------|----------------------------|-------------------------------------|-----------------------------------------------------------------------------|
| Discord-ID, WarEra-ID und -Name, Zeitpunkt       | `account_link`             | Rollen nach WarEra-Daten, `/profil` | bis zum Lösen, zum Verlassen des Servers oder zu einer neuen Verknüpfung    |
| Discord-ID, Zeitpunkt                            | `nickname_opt_in`          | Nickname aus WarEra                 | bis `/nickname aus` oder zum Verlassen des Servers                          |
| Discord-IDs der Mitglieder                       | Speicher des Backends      | Kennzahlen, Abgleich der Links      | bis zur nächsten Meldung des Bots oder zum Neustart                         |
| Discord-ID, WarEra-ID und -Name, Code            | `account_verification`     | offene Verifizierung                | bis zur Verknüpfung oder zum nächsten Code, sonst bis 2 Stunden nach Ablauf |
| Discord-ID, WarEra-ID, Stimme                    | `vote_ballot`              | laufende Abstimmung                 | bis zur Auswertung oder zum Abbruch                                         |
| Discord-ID und -Name der Rollenmitglieder        | `vote_elector`             | Auswertung einer Abstimmung         | bis zur Auswertung                                                          |
| Namen: Start, Ende, nicht abgestimmt             | `vote`, `vote_non_voter`   | Archiv der Abstimmungen             | unbegrenzt                                                                  |
| Discord-ID, Zeitpunkt                            | `ping_log`                 | `/ping`                             | 30 Tage                                                                     |
| Discord-ID, Name und Avatar von Admins, Änderung | `audit_entry`              | Audit-Log im Dashboard              | unbegrenzt                                                                  |
| Discord-ID und -Name, WarEra-Name, Rollen        | `system_log_*`             | System-Log im Dashboard             | 30 Tage                                                                     |
| Events mit Discord-IDs                           | Redis-Stream `bits:events` | Zustellung an den Bot               | 7 Tage                                                                      |
| Logs mit Discord- und WarEra-IDs                 | Container-Logs             | Fehlersuche                         | im Deployment höchstens 5 × 10 MB pro Dienst                                |
| alles aus der Datenbank                          | Dumps in `BACKUP_PATH`     | Wiederherstellung                   | bis zu 4 Wochen, siehe [deploy/README.md](deploy/README.md#backups)         |

WarEra-Daten der Nutzer hält das Backend nur im Speicher. Ein Hinweis in `/verify` sagt, was gespeichert wird.
Mit `/unverify` oder unter „Mein Account“ im Dashboard löscht ein Nutzer seine Verknüpfung und einen offenen Code;
die Rollen, die BITS verwaltet, verliert er beim folgenden Abgleich. Wer den Server verlässt, verliert beides ebenso,
spätestens beim nächsten Start des Bots. Abgelaufene Codes und alte Pings löscht ein Job jede Stunde.

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
