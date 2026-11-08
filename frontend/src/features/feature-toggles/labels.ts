import type { Feature } from './api'

/** In the order of the card; the backend names them the same in the audit log. */
export const FEATURES: Record<Feature, { label: string; description: string }> = {
  VERIFICATION: {
    label: 'Verifizierung',
    description: '/verify. Bestehende Verknüpfungen bleiben, und lösen lässt sich eine Verknüpfung immer.',
  },
  ROLE_SYNC: {
    label: 'Rollen-Sync',
    description:
      'Vergibt und entzieht die Rollen nach den Regeln, dazu /roles und die Rollen unter „Mein Account“. Solange ' +
      'er aus ist, bleiben alle Rollen, wie sie sind; beim Einschalten werden alle Mitglieder neu abgeglichen.',
  },
  NICKNAMES: {
    label: 'Nicknames',
    description: 'Setzt WarEra-Namen als Nickname, dazu /nickname. Gesetzte Nicknames bleiben, wenn du sie ausschaltest.',
  },
  PROFILE: { label: 'Profil', description: '/profile zeigt WarEra-Profile im Discord.' },
  VOTES: {
    label: 'Abstimmungen',
    description:
      'Neue Abstimmungen starten. Laufende laufen zu Ende und lassen sich in Discord weiter beenden oder abbrechen.',
  },
  EMBASSIES: {
    label: 'Botschaften',
    description:
      'Gleicht die Botschafts-Threads mit den Regierungen ab. Solange sie aus sind, bleiben die Threads, wie sie sind.',
  },
  TOPS: {
    label: 'TOPs',
    description: '/topclose und /topopen in den TOP-Foren. Geschlossene Posts bleiben, wie sie sind.',
  },
  REPRIMANDS: {
    label: 'Rügen',
    description:
      '/reprimand mit Timeout für das gerügte Mitglied; startet ausgeschaltet. Bestehende Rügen zählen weiter und ' +
      'lassen sich unter „Rügen“ zurücknehmen.',
  },
}

export const ERROR_MESSAGE = 'Etwas ist schiefgelaufen. Bitte versuche es später erneut.'
