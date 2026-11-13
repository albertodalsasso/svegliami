# Architettura account e sincronizzazione

## Stato attuale

L'app funziona senza account. Le sveglie, le preferenze, le sessioni e il diario sono locali (SharedPreferences, JSON e Room per il nuovo schema/import). Non è configurato un backend né un flusso di login; l'interfaccia deve dichiararlo chiaramente e non simulare una sincronizzazione.

## Direzione proposta

1. Mantenere offline-first: ogni funzione principale continua a funzionare senza rete e senza account.
2. In una futura fase valutare autenticazione Google tramite API Android supportata e backend con regole di accesso per utente; non memorizzare segreti di servizio nell'app.
3. Sincronizzare solo dati selezionati: preferenze, profili, metadati sessioni e diario, con consenso separato e cancellazione/esportazione.
4. Escludere per default i file audio; nessun upload automatico. Richiedere consenso specifico se in futuro venisse proposto backup audio.
5. Aggiungere ID stabili, `updatedAt`, versione schema e tombstone per cancellazioni; gestire conflitti con revisione esplicita e test offline/riordino.
6. Proteggere trasporto e storage backend, minimizzare dati identificativi e documentare retention, costi e procedure di eliminazione prima del rilascio.

## Decisione backend

Nessuna scelta Firebase o altro backend è stata attivata: mancano requisiti operativi, budget, privacy policy e credenziali/configurazione. La decisione va presa prima di implementare il login remoto. L'app locale non deve dipendere dal backend per sveglie, tracking o consultazione dello storico.
