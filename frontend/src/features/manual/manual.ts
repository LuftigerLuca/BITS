import { SettingsIcon, UsersIcon, VoteIcon, type LucideIcon } from 'lucide-react'

/**
 * The user manual at /docs: its pages in reading order, each with the topics it explains. A topic has a title,
 * an intro and, as it needs, `steps` (done in order), `points` (in no order) and `tips`.
 *
 * A page only explains what its readers need beyond the pages in `buildsOn`, so nothing is written down twice.
 * Change the texts here together with the behavior they describe.
 */

export type ManualTopic = {
  id: string
  title: string
  intro: string
  steps?: string[]
  points?: string[]
  tips?: string[]
}

export type ManualPage = {
  slug: string
  icon: LucideIcon
  title: string
  summary: string
  intro: string
  buildsOn: string[]
  topics: ManualTopic[]
}

export const MANUAL_PATH = '/docs'

export const MANUAL_PAGES: ManualPage[] = [
  {
    slug: 'mitglieder',
    icon: UsersIcon,
    title: 'Mitglieder',
    summary: 'Den WarEra-Account verknüpfen, Rollen und Profile ansehen und abstimmen.',
    intro:
      'BITS verknüpft deinen Discord-Account mit deinem WarEra-Account. Danach vergibt es dir Discord-Rollen nach ' +
      'deinen WarEra-Daten, zeigt dein Profil und lässt dich an Abstimmungen teilnehmen.',
    buildsOn: [],
    topics: [
      {
        id: 'verify',
        title: 'Account verknüpfen',
        intro:
          'Mit /verify zeigst du BITS, dass dir ein WarEra-Account gehört. Dafür schreibst du einen Code in den Namen ' +
          'einer deiner WarEra-Firmen.',
        steps: [
          'Schreibe in Discord /verify und deinen Namen in WarEra.',
          'BITS antwortet nur dir mit einem Einmal-Code. Er ist 15 Minuten gültig.',
          'Benenne in WarEra eine deiner Firmen so um, dass der Code im Namen steht.',
          'Klicke in Discord auf „Prüfen“. BITS sucht den Code und verknüpft die Accounts.',
          'Danach kannst du der Firma wieder ihren alten Namen geben.',
        ],
        tips: [
          'Der Code zählt nur in einer Firma, die dir gehört.',
          'Verknüpft sich ein anderer Discord-Account mit demselben WarEra-Account, ersetzt das die alte ' +
            'Verknüpfung. Der vorherige Account bekommt dann eine Direktnachricht, falls er welche annimmt.',
        ],
      },
      {
        id: 'unverify',
        title: 'Verknüpfung lösen',
        intro:
          'Mit /unverify oder unter „Mein Account“ im Dashboard löst du die Verknüpfung wieder. BITS löscht sie und ' +
          'einen offenen Code.',
        points: [
          'Die Rollen, die BITS verwaltet, verlierst du beim nächsten Abgleich.',
          'Wer den Server verlässt, verliert seine Verknüpfung ebenso und muss sich nach einem erneuten Beitritt neu ' +
            'verifizieren.',
        ],
      },
      {
        id: 'nickname',
        title: 'WarEra-Name als Nickname',
        intro:
          'Mit /nickname an oder dem Button „Nickname übernehmen“ nach der Verknüpfung setzt BITS deinen Nickname ' +
          'auf deinen WarEra-Namen. Das geht auch schon vor dem Verknüpfen.',
        points: [
          'BITS gleicht den Nickname bei jedem Abgleich an, nach einer Umbenennung in WarEra also spätestens nach ' +
            'einer Stunde.',
          'Löst du die Verknüpfung oder übernimmt ein anderer Discord-Account deinen WarEra-Account, entfernt BITS ' +
            'den Nickname wieder, sofern er noch dem WarEra-Namen entspricht.',
          'Mit /nickname aus hört BITS auf; der Nickname bleibt, wie er ist.',
        ],
        tips: ['Ohne dein Einverständnis ändert BITS deinen Nickname nie.'],
      },
      {
        id: 'profile',
        title: 'Profile ansehen',
        intro:
          'Mit /profil zeigt BITS im Kanal das WarEra-Profil deines verknüpften Accounts, mit /profil @mitglied das ' +
          'eines anderen Mitglieds.',
        points: [
          'Name mit Link zu WarEra, Level, Militärrang und Staatsbürgerschaft',
          'Military Unit, Partei im Land der Staatsbürgerschaft, mit „(Vorsitz)“ für die Parteiführung',
          'Regierungsamt und Anzahl der Firmen',
          'die Rollen, die BITS verwaltet und die das Mitglied auf dem Server hat',
        ],
        tips: [
          'Parteien und Regierungen können bis zu 10 Minuten alt sein. Kann WarEra etwas gerade nicht liefern, ' +
            'steht dort „nicht verfügbar“.',
        ],
      },
      {
        id: 'roles',
        title: 'Warum du eine Rolle hast',
        intro:
          'Mit /rollen siehst du, nur für dich, zu jeder Rolle, die BITS verwaltet, ob du sie bekommst und warum: die ' +
          'Regel, die greift, oder was dir fehlt, etwa „Level 8 von 10“.',
        points: [
          'BITS prüft dafür deine aktuellen WarEra-Daten, ändert aber keine Rollen.',
          'Auf dem Server gleicht der nächste Abgleich deine Rollen an, spätestens nach einer Stunde.',
          'Rollen von Hand zu vergeben hilft nicht: Wer keine Regel einer verwalteten Rolle erfüllt, verliert sie.',
        ],
      },
      {
        id: 'vote',
        title: 'Abstimmen',
        intro:
          'Eine Abstimmung erscheint als Nachricht mit den Buttons Ja, Nein und Enthaltung. Abstimmen darf, wer die ' +
          'angepingte Rolle und einen verknüpften WarEra-Account hat.',
        steps: [
          'Klicke auf Ja, Nein oder Enthaltung. Deine Antwort siehst nur du.',
          'Bis zum Ende kannst du deine Stimme ändern, indem du einen anderen Button klickst.',
          'Zum Ende postet BITS das Ergebnis nach Parteien als Grafik und Text im selben Kanal.',
        ],
        tips: [
          'Die Nachricht zeigt nur, wie viele abgestimmt haben. Nach der Auswertung löscht BITS, wer wie abgestimmt ' +
            'hat; nur die Namen derer, die nicht abgestimmt haben, stehen im Ergebnis.',
          'Hast du die Rolle zum Ende nicht mehr, zählt deine Stimme nicht.',
        ],
      },
      {
        id: 'login',
        title: 'Im Dashboard anmelden',
        intro: 'Im Dashboard meldest du dich mit deinem Discord-Account an. Das geht nur als Mitglied des Servers.',
        steps: [
          'Öffne das Dashboard und klicke auf „Mit Discord anmelden“.',
          'Erlaube BITS in Discord, deinen Namen, deine Server und deine Rollen auf dem Server zu lesen.',
          'Du landest auf der Startseite des Dashboards.',
        ],
        tips: [
          'Die Sitzung gilt 8 Stunden, für Admins 1 Stunde, und wird nicht verlängert. Neue Rollen auf dem Server ' +
            'zählen im Dashboard erst nach dem nächsten Login.',
        ],
      },
      {
        id: 'account',
        title: 'Mein Account',
        intro: 'Unter „Mein Account“ im Dashboard siehst du, was BITS über dich weiß.',
        points: [
          'deine Verknüpfung und wann alle Mitglieder zuletzt abgeglichen wurden',
          'die Rollen, die BITS verwaltet, mit Begründung wie bei /rollen',
          '„Verknüpfung lösen“, das wie /unverify wirkt',
          'einen Download als JSON-Datei mit allem, was BITS zu deiner Discord-ID speichert',
        ],
      },
    ],
  },
  {
    slug: 'abstimmungen',
    icon: VoteIcon,
    title: 'Abstimmungen leiten',
    summary: 'Abstimmungen starten, vorzeitig beenden oder abbrechen.',
    intro:
      'Abstimmungen starten, beenden und abbrechen dürfen Admins und die Rollen, die Admins unter „Verwaltung“ ' +
      'dafür freigeben. Das geht in Discord und im Dashboard unter „Abstimmungen“ im Bereich „Admin“ der ' +
      'Seitenleiste.',
    buildsOn: ['mitglieder'],
    topics: [
      {
        id: 'start',
        title: 'Abstimmung starten',
        intro:
          'In Discord startest du eine Abstimmung mit /abstimmung starten, im Dashboard unter „Abstimmungen“ mit ' +
          '„Neue Abstimmung“.',
        steps: [
          'Gib die Frage ein.',
          'Wähle die Rolle, die abstimmen darf. BITS pingt sie mit der Abstimmung.',
          'Wähle den Kanal, in dem Abstimmung und Ergebnis erscheinen. Angeboten werden nur Kanäle, in denen der Bot ' +
            'posten kann.',
          'Wähle das Ende: eine Dauer wie 48h (Standard), 3d oder 90m, oder „offen“ für kein festes Ende.',
          'Starte die Abstimmung. Der Bot postet sie mit den Buttons Ja, Nein und Enthaltung.',
        ],
      },
      {
        id: 'threshold',
        title: 'Vorzeitig enden lassen',
        intro:
          'Mit einer Schwelle von 1 bis 99 % endet eine Abstimmung vorzeitig, sobald mehr als so viel Prozent aller ' +
          'Stimmberechtigten mit der gewählten Stimme gestimmt haben, Ja (Standard) oder Nein.',
        points: [
          '50 heißt mehr als die Hälfte.',
          'Es gilt, was zuerst eintritt: das Ende oder die Schwelle.',
        ],
      },
      {
        id: 'end',
        title: 'Beenden und abbrechen',
        intro: 'Eine laufende Abstimmung kannst du vor ihrem Ende beenden oder abbrechen.',
        points: [
          '/abstimmung beenden oder „Beenden“ im Dashboard wertet sie sofort aus; das Ergebnis erscheint kurz darauf ' +
            'im Kanal.',
          '/abstimmung abbrechen oder „Abbrechen“ beendet sie ohne Ergebnis und löscht die abgegebenen Stimmen.',
        ],
      },
      {
        id: 'result',
        title: 'Das Ergebnis',
        intro:
          'Zum Ende zählt BITS die Stimmen aller, die die Rolle dann noch haben, und ordnet sie über WarEra den ' +
          'Parteien im Land ihrer Staatsbürgerschaft zu.',
        points: [
          'Wer in keiner Partei ist, zählt als „Parteilos“.',
          'Das Ergebnis erscheint als Grafik und Text im Kanal der Abstimmung, mit den Namen derer, die nicht ' +
            'abgestimmt haben.',
          'Unter „Abstimmungen“ im Dashboard stehen beendete Abstimmungen im Archiv.',
        ],
      },
    ],
  },
  {
    slug: 'admins',
    icon: SettingsIcon,
    title: 'Admins',
    summary: 'Rollenregeln, Botschaften, Einstellungen und Logs im Dashboard.',
    intro:
      'Wer auf dem Server die Berechtigung Administrator hat oder ihn besitzt, ist im Dashboard Admin und sieht in ' +
      'der Seitenleiste die Bereiche „Admin“ mit Rollen, Botschaften und Abstimmungen und „System“ mit Verwaltung ' +
      'und den Logs.',
    buildsOn: ['mitglieder', 'abstimmungen'],
    topics: [
      {
        id: 'overview',
        title: 'Startseite und Verwaltung',
        intro: 'Die Startseite zeigt Admins Kennzahlen des Servers und des eigenen Landes.',
        points: [
          'wie viele Mitglieder der Server ohne Bots hat und wie viele davon verifiziert sind',
          'wie viele Bürger, gesamt und aktiv, das Land hat, das du unter „Verwaltung“ einstellst',
        ],
        tips: [
          'Das Land unter „Verwaltung“ bekommt auch keinen Botschafts-Thread.',
          'Lange Auswahllisten wie Rollen, Kanäle, Länder und Parteien haben oben ein Suchfeld: Tippe einen Teil des ' +
            'Namens, Groß- und Kleinschreibung und Akzente spielen keine Rolle.',
        ],
      },
      {
        id: 'features',
        title: 'Funktionen ein- und ausschalten',
        intro:
          'Unter „Verwaltung“ schaltest du einzelne Funktionen aus: Verifizierung, Rollen-Sync, Nicknames, Profil, ' +
          'Abstimmungen und Botschaften. Eine ausgeschaltete Funktion pausiert nur, gelöscht wird nichts.',
        points: [
          'Ihre Befehle antworten in Discord mit „Diese Funktion ist gerade ausgeschaltet.“ Der Bot übernimmt eine ' +
            'Änderung innerhalb von 30 Sekunden.',
          'Ihre Seiten im Dashboard bleiben und zeigen oben einen Hinweis. Regeln und Einstellungen kannst du weiter ' +
            'ändern; sie wirken, sobald die Funktion wieder an ist.',
          '/unverify, „Verknüpfung lösen“ und der Datenexport gehen immer.',
          'Ohne Rollen-Sync und ohne Botschaften bleiben Rollen und Threads, wie sie sind. Schaltest du sie wieder ' +
            'ein, gleicht BITS sofort alle Mitglieder ab.',
          'Ohne Abstimmungen startet niemand eine neue. Laufende enden wie geplant und lassen sich in Discord mit ' +
            '/abstimmung beenden oder abbrechen.',
        ],
        tips: ['Jedes Umschalten steht im Audit-Log.'],
      },
      {
        id: 'bot-presence',
        title: 'Status des Bots',
        intro:
          'Unter „Verwaltung“ legst du fest, wie Discord den Bot in der Mitgliederliste zeigt: Online, Abwesend, Bitte ' +
          'nicht stören oder Unsichtbar, dazu auf Wunsch eine Aktivität.',
        points: [
          'Aktivitäten sind „Spielt …“, „Schaut …“, „Hört …“, „Tritt an in …“ oder ein eigener Status-Text, jeweils bis ' +
            '128 Zeichen.',
          'Discord zeigt die Änderung nach wenigen Sekunden. Nach einem Neustart setzt der Bot sie wieder.',
        ],
        tips: ['Jede Änderung steht im Audit-Log.'],
      },
      {
        id: 'rules',
        title: 'Rollen nach WarEra-Daten',
        intro:
          'Unter „Rollen“ legst du Regeln an, die eine Discord-Rolle an WarEra-Merkmale koppeln. Eine Rolle mit ' +
          'mindestens einer Regel verwaltet BITS.',
        steps: [
          'Wähle unter „Neue Regel“ die Rolle.',
          'Füge eine bis zehn Bedingungen hinzu: Staatsbürgerschaft, Regierungsamt in einem Land, Mitgliedschaft in ' +
            'einer Military Unit oder einer Partei, Level, Militärrang oder Zahl der Firmen ab einem Wert.',
          'Prüfe die Vorschau, wem die Regel die Rolle gibt und wem sie sie nimmt.',
          'Klicke auf „Regel anlegen“.',
        ],
        points: [
          'Die Bedingungen einer Regel müssen alle erfüllt sein, etwa „Staatsbürgerschaft Germany und Level ab 10“.',
          'Von mehreren Regeln für dieselbe Rolle genügt eine.',
          'Wer keine Regel einer verwalteten Rolle erfüllt, verliert sie, auch wenn sie von Hand vergeben wurde.',
          'Regeln für Rollen, die in Discord gelöscht wurden, gelten als verwaist und wirken nicht.',
        ],
        tips: [
          'Die Vorschau rechnet mit den WarEra-Daten des letzten Abgleichs. Mitglieder ohne solche Daten zählen als ' +
            '„unbekannt“. Kurz nach einem Neustart von BITS ist sie ungenau; das Dashboard sagt das dazu.',
        ],
      },
      {
        id: 'sync',
        title: 'Wann BITS Rollen abgleicht',
        intro: 'BITS gibt und nimmt Rollen bei jedem Abgleich und nennt dabei im Audit-Log von Discord den Grund.',
        points: [
          'beim Verknüpfen für das Mitglied',
          'nach jeder Regeländerung für alle, mit den WarEra-Daten des letzten Abgleichs',
          'stündlich für alle mit frischen WarEra-Daten',
        ],
        tips: [
          'Kann BITS ein Mitglied, seine Firmen, eine Regierung oder die Parteien eines Landes gerade nicht laden, ' +
            'bleiben die betroffenen Rollen, wie sie sind, außer eine andere Bedingung derselben Regel ist sicher nicht ' +
            'erfüllt.',
          'Regierungen und Parteien können dabei bis zu 10 Minuten alt sein.',
        ],
      },
      {
        id: 'embassies',
        title: 'Botschafts-Threads',
        intro:
          'Diplomatie mit anderen Ländern läuft in privaten Threads, einer pro Land. Unter „Botschaften“ ordnest du ' +
          'einem Land seinen Thread zu, und BITS hält die Mitglieder aktuell.',
        steps: [
          'Lege den privaten Thread in Discord im Botschafts-Kanal selbst an.',
          'Wähle unter „Botschaften“ die Ämter, die jemanden zum Botschafter machen (Standard: Präsident, ' +
            'Vizepräsident, Außenminister), die Botschafter-Rolle und Gastgeber-Rollen wie die eigene Regierung.',
          'Ordne unter „Botschafts-Threads“ den Thread einem Land zu.',
          'Füge bei Bedarf Mitglieder von Hand hinzu.',
        ],
        points: [
          'Im Thread sind die verknüpften Mitglieder mit einem der Ämter im Land, die von Hand Hinzugefügten und die ' +
            'Mitglieder der Gastgeber-Rollen. Alle anderen entfernt BITS, außer wer im Kanal Threads verwalten darf.',
          'Botschafter und von Hand Hinzugefügte bekommen die Botschafter-Rolle und verlieren sie, sobald sie in ' +
            'keinem Thread mehr sind. Gastgeber-Rollen bekommen sie nicht.',
          'BITS gleicht die Threads nach jeder Verknüpfung, nach jeder Änderung auf der Seite und stündlich ab. Die ' +
            'Seite zeigt, wer in welchem Thread ist und warum.',
          'Kann BITS die Regierung eines Landes nicht laden, entfernt es aus dessen Thread niemanden.',
        ],
        tips: [
          'Der Bot braucht im Botschafts-Kanal die Rechte „Threads verwalten“ und „Nachrichten in Threads senden“, und ' +
            'die Botschafter-Rolle muss den Kanal sehen dürfen. Diese Rechte richtest du selbst ein; fehlen sie, lehnt ' +
            'das Dashboard die Zuordnung und die Einstellungen ab.',
        ],
      },
      {
        id: 'votes',
        title: 'Einstellungen für Abstimmungen',
        intro: 'Unter „Verwaltung“ stellst du ein, wer Abstimmungen leiten darf und wie das Ergebnis aussieht.',
        points: [
          '„Wer Abstimmungen verwalten darf“: die Rollen, die Abstimmungen starten, beenden und abbrechen dürfen. ' +
            'Admins dürfen das immer.',
          '„Parteifarben“: eigene Farben für die Parteien des eigenen Landes in der Grafik.',
          '„Bild der Auswertung“: ein eigener Hintergrund für die Grafik.',
        ],
      },
      {
        id: 'logs',
        title: 'Audit-Log und System-Log',
        intro: 'Zwei Logs zeigen, was im Dashboard geändert wurde und was BITS selbst getan hat.',
        points: [
          '„Audit-Log“: welcher Admin wann was im Dashboard geändert hat.',
          '„System-Log“: Abgleiche mit den Rollenänderungen des Bots, Verknüpfungen und Fehler, zum Beispiel ' +
            'Mitglieder, die der Bot nicht umbenennen konnte. Einträge werden nach 30 Tagen gelöscht.',
        ],
      },
      {
        id: 'safety',
        title: 'Schutz vor Fehlern',
        intro:
          'Verlässt jemand den Server, löscht BITS seine Verknüpfung. Damit eine unvollständige Mitgliederliste nicht ' +
          'viele Verknüpfungen auf einmal löscht, gibt es eine Grenze.',
        points: [
          'Würde eine Liste mehr als die Hälfte und mindestens 10 Verknüpfungen auf einmal löschen, verwirft BITS sie ' +
            'und vermerkt das im System-Log.',
          'Rollen über der höchsten Rolle des Bots kann BITS nicht vergeben. Den Server-Besitzer und Mitglieder über ' +
            'der Rolle des Bots kann es nicht umbenennen; das steht im System-Log.',
        ],
      },
      {
        id: 'admin-login',
        title: 'Admin-Account ohne Discord',
        intro:
          'Wenn er eingerichtet ist, gibt es auf der Login-Seite zusätzlich einen Admin-Account mit Benutzername und ' +
          'Passwort, der ohne Discord funktioniert.',
        points: [
          'Pro IP zählen in 15 Minuten höchstens 5 Anmeldeversuche, über alle IPs höchstens 50; danach ist die ' +
            'Anmeldung bis zum Ende des Zeitfensters gesperrt.',
          'Dieser Account hat keine Seite „Mein Account“.',
        ],
      },
    ],
  },
]

export function manualPage(slug: string | undefined): ManualPage | undefined {
  return MANUAL_PAGES.find((page) => page.slug === slug)
}

export function manualPath(page?: ManualPage): string {
  return page ? `${MANUAL_PATH}/${page.slug}` : MANUAL_PATH
}
