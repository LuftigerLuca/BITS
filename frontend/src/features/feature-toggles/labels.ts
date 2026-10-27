import type { Feature } from '@/lib/api'

/** In the order of the card; the backend names them the same in the audit log. */
export const FEATURES: Record<Feature, { label: string; description: string }> = {
  VERIFICATION: {
    label: 'Verifizierung',
    description: '/verify. Bestehende Verknüpfungen bleiben, und lösen lässt sich eine Verknüpfung immer.',
  },
  ROLE_SYNC: {
    label: 'Rollen-Sync',
    description:
      'Vergibt und entzieht die Rollen nach den Regeln, dazu /rollen und die Rollen unter „Mein Account“. Solange ' +
      'er aus ist, bleiben alle Rollen, wie sie sind; beim Einschalten werden alle Mitglieder neu abgeglichen.',
  },
  NICKNAMES: {
    label: 'Nicknames',
    description: 'Setzt WarEra-Namen als Nickname, dazu /nickname. Gesetzte Nicknames bleiben, wenn du sie ausschaltest.',
  },
  PROFILE: { label: 'Profil', description: '/profil zeigt WarEra-Profile im Discord.' },
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
}

export const ERROR_MESSAGE = 'Etwas ist schiefgelaufen. Bitte versuche es später erneut.'
