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
          'Teil der Regel, der greift, oder was dir je Teil fehlt, etwa „Level 8 von 10“.',
        points: [
          'BITS prüft dafür deine aktuellen WarEra-Daten, ändert aber keine Rollen.',
          'Auf dem Server gleicht der nächste Abgleich deine Rollen an, spätestens nach einer Stunde.',
          'Rollen von Hand zu vergeben hilft nicht: Wer die Regel einer verwalteten Rolle nicht erfüllt, verliert sie.',
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
          'Unter „Rollen“ legst du Regeln an, die eine Discord-Rolle an WarEra-Merkmale koppeln. Jede Rolle hat ' +
          'höchstens eine Regel; eine Rolle mit Regel verwaltet BITS.',
        steps: [
          'Wähle unter „Neue Regel“ die Rolle. Hat sie schon eine Regel, bearbeitest du stattdessen diese.',
          'Füge Bedingungen hinzu: Staatsbürgerschaft, Regierungsamt in einem Land, Mitgliedschaft in einer ' +
            'Military Unit oder einer Partei, Führung einer Military Unit, Parteivorsitz, Level, Militärrang oder ' +
            'Zahl der Firmen ab einem Wert.',
          'Wähle zwischen je zwei Bedingungen „und“ oder „oder“.',
          'Prüfe die Vorschau, wem die Regel die Rolle gibt und wem sie sie nimmt.',
          'Klicke auf „Regel anlegen“.',
        ],
        points: [
          '„und“ bindet stärker als „oder“: Jedes „oder“ trennt die Regel in Blöcke, von denen einer genügt; die ' +
            'Bedingungen eines Blocks müssen alle erfüllt sein. „Staatsbürgerschaft Germany und Level ab 10 oder ' +
            'Mitglied der MU Bundeswehr“ gibt die Rolle deutschen Spielern ab Level 10 und allen in der Bundeswehr.',
          'Eine Regel hat bis zu zehn Blöcke mit je bis zu zehn Bedingungen.',
          'Mit dem Stift in der Liste bearbeitest du eine Regel als Ganzes: Bedingungen hinzufügen, entfernen oder ' +
            'zwischen „und“ und „oder“ umstellen. Vor dem Speichern zeigt die Vorschau wieder, wer die Rolle ' +
            'bekommt und wer sie verliert.',
          'Beim Regierungsamt kannst du mehrere Ämter ankreuzen; eines davon genügt, etwa „Präsident oder ' +
            'Vizepräsident in Germany“.',
          'MU-Führung hat die Stufen Kommandeur, Manager und Besitzer; höhere Stufen zählen mit, „Kommandeur“ gilt ' +
            'also auch für Manager und Besitzer. Ohne gewählte MU zählt die Führung der eigenen MU, ohne gewählte ' +
            'Partei der Vorsitz jeder Partei des Landes.',
          'Wer die Regel einer verwalteten Rolle nicht erfüllt, verliert sie, auch wenn sie von Hand vergeben wurde.',
          'Löschst du die Regel, verwaltet BITS die Rolle nicht mehr; niemand verliert sie dadurch.',
          'Regeln für Rollen, die in Discord gelöscht wurden, gelten als verwaist und wirken nicht.',
          'Die Rolle einer DSK kann keine Regel bekommen, siehe „DSKs und ihre Military Units“.',
        ],
        tips: [
          'Die Vorschau rechnet mit den WarEra-Daten des letzten Abgleichs. Mitglieder ohne solche Daten zählen als ' +
            '„unbekannt“. Kurz nach einem Neustart von BITS ist sie ungenau; das Dashboard sagt das dazu.',
        ],
      },
      {
        id: 'dsks',
        title: 'DSKs und ihre Military Units',
        intro:
          'Unter „Militäreinheiten“ fasst du die Military Units des Landes unter „Verwaltung“ zu DSKs zusammen. Wer ' +
          'verknüpft ist und in einer MU einer DSK ist, bekommt die Rolle der DSK; alle anderen verlieren sie.',
        steps: [
          'Lege unter „DSK anlegen“ eine DSK mit Namen an, etwa „DSK1“, und wähle ihre Rolle.',
          'Suche bei der DSK eine MU nach ihrem Namen und klicke auf „Hinzufügen“.',
        ],
        points: [
          'Die Suche zeigt nur MUs des Landes unter „Verwaltung“. Ist dort keins ausgewählt, kannst du keine MU ' +
            'zuordnen. Das Land wird nur beim Zuordnen geprüft; zieht eine MU später um, bleibt sie in ihrer DSK.',
          'WarEra liefert nur die besten Treffer. Fehlt eine MU mit sehr allgemeinem Namen, suche genauer.',
          'Eine MU gehört höchstens zu einer DSK.',
          'Eine Rolle gehört entweder zu einer DSK oder hat eine Regel unter „Rollen“, nie beides. Das Dashboard ' +
            'bietet belegte Rollen nicht an.',
          'Eine DSK ohne Rolle bewirkt nichts.',
          'Entfernst du eine MU, löschst die DSK oder nimmst ihr die Rolle, verlieren die Mitglieder die Rolle ' +
            'beim folgenden Abgleich.',
          'In /rollen und im Audit-Log von Discord steht als Grund etwa „Mitglied der MU Bundeswehr (DSK1)“.',
          'Ist der Rollen-Sync ausgeschaltet, vergibt und entzieht BITS auch die Rollen der DSKs nicht.',
        ],
      },
      {
        id: 'sync',
        title: 'Wann BITS Rollen abgleicht',
        intro:
          'BITS gibt und nimmt Rollen bei jedem Abgleich und nennt dabei im Audit-Log von Discord den Grund: den ' +
          'ersten erfüllten Block der Regel.',
        points: [
          'beim Verknüpfen für das Mitglied',
          'nach jeder Regeländerung und jeder Änderung an einer DSK, die ihre Rolle betrifft, für alle, mit den ' +
            'WarEra-Daten des letzten Abgleichs',
          'stündlich für alle mit frischen WarEra-Daten',
        ],
        tips: [
          'Kann BITS ein Mitglied, seine Firmen, eine Regierung, die Parteien eines Landes oder eine Military Unit ' +
            'gerade nicht laden, bleiben die betroffenen Rollen, wie sie sind, außer ein anderer Block der Regel ist ' +
            'erfüllt oder jeder betroffene Block hat eine andere Bedingung, die sicher nicht erfüllt ist.',
          'Regierungen, Parteien und Military Units können dabei bis zu 10 Minuten alt sein.',
        ],
      },
      {
        id: 'embassies',
        title: 'Botschafts-Threads',
        intro:
          'Diplomatie mit anderen Ländern läuft in privaten Threads, einer pro Land. Unter „Botschaften“ ordnest du ' +
          'einem Land seinen Thread zu, und BITS hält die Mitglieder aktuell.',
        steps: [
          'Wähle unter „Botschaften“ die Ämter, die jemanden zum Botschafter machen (Standard: Präsident, ' +
            'Vizepräsident, Außenminister), die Botschafter-Rolle, Gastgeber-Rollen wie die eigene Regierung und den ' +
            'Botschafts-Kanal.',
          'Verknüpft sich jemand mit einem dieser Ämter in einem Land ohne Thread, legt der Bot im Botschafts-Kanal ' +
            'den privaten Thread „Botschaft <Land>“ an und ordnet ihn zu.',
          'Unter „Botschafts-Threads“ kannst du einem Land auch selbst einen bestehenden Thread zuordnen oder mit ' +
            '„Automatisch anlegen“ einen Thread mit eigenem Namen anlegen lassen.',
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
          'Bis der Bot einen Thread angelegt hat, steht beim Land „Wird angelegt …“. Klappt es nicht innerhalb einer ' +
            'Minute, zeigt die Seite den Grund. BITS versucht es dann nicht von selbst erneut: Lege ihn erneut an, ' +
            'ordne einen anderen Thread zu oder verwirf den Fehler.',
          'Hebst du eine Zuordnung auf, bleibt der Thread in Discord bestehen.',
        ],
        tips: [
          'Der Bot braucht im Botschafts-Kanal die Rechte „Private Threads erstellen“, „Threads verwalten“ und ' +
            '„Nachrichten in Threads senden“, und die Botschafter-Rolle muss den Kanal sehen dürfen. Diese Rechte ' +
            'richtest du selbst ein; fehlen sie, lehnt das Dashboard die Zuordnung und die Einstellungen ab.',
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
