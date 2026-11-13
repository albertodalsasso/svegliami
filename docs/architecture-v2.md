# Svegliami 2.0 — architettura e audit iniziale

## Baseline del repository

- Branch di lavoro richiesto: `Svegliami_2.0`.
- Package/application id: `it.svegliami`; min SDK 26, target/compile SDK 36.
- Kotlin 2.0.21, AGP 8.9.1, Java target 17. UI XML/AppCompat/Material, senza Compose.
- Sveglie, luogo e cache meteo: `SharedPreferences` (`Prefs.kt`), con migrazione dalla configurazione a sveglia singola.
- Sessioni sonno: JSON in `filesDir/sleep_sessions.json`, massimo 30, senza migrazione SQL. Ogni sessione memorizza timestamp, punteggi al minuto, modalità microfono e russamento.
- Sveglia: `Scheduler` programma l'occorrenza normale e il controllo meteo separatamente. `WeatherCheck` aggiunge una sveglia anticipata se la previsione lo richiede; errori rete/cache lasciano attiva quella normale. `BootReceiver` riprogramma dopo riavvio, cambio ora/fuso e aggiornamento app.
- Meteo: `WeatherClient` interroga geocoding e forecast Open-Meteo; la posizione viene salvata in primo piano e riusata durante il controllo notturno.
- Monitoraggio: `SleepService` è un foreground service avviato dall'utente; sceglie microfono **oppure** accelerometro. Il microfono calcola livello, banda bassa e periodicità per russamento, ma non conserva PCM. I campioni sono aggregati per minuto.
- Analisi: `SleepAnalyzer` stima sveglio/leggero/profondo da movimento o rumore. Non implementa REM. I nomi «leggero/profondo» sono euristiche non validate e non vanno presentate come misure fisiologiche.
- UI: dashboard sveglia e storico custom in Views/XML, grafico Canvas custom. Non ci sono navigazione a cinque sezioni, diario, eventi audio persistiti, score composito, Health Connect, Room o registrazione.

## Rischi e vincoli di compatibilità

1. Il formato JSON sessioni e le preferenze sono dati utente esistenti: non rinominarli né sovrascriverli. Una migrazione Room dovrà importare sessioni in transazione, conservare gli originali finché il conteggio non è verificato e supportare reinstallazioni/rollback senza perdita.
2. Il servizio di tracking e la sveglia condividono il processo ma non devono condividere un percorso critico. Errori di sensore, storage o rete non devono cancellare né ritardare l'occorrenza normale di `AlarmManager`.
3. `SleepService` acquisisce un wake lock per un massimo di 14 ore e usa campioni accelerometro UI. Vanno misurati batteria, frequenza dei campioni e comportamento su dispositivi reali prima di affermare accuratezza.
4. I permessi microfono/foreground service sono già nel manifest. Registrare file introduce ulteriori requisiti di consenso, spazio, retention e cifratura; resta disattivato finché queste impostazioni non sono esplicite.
5. Le fasi REM e profonde richiedono un validatore e un dataset adeguati. Il solo silenzio/mancanza di movimento non le dimostra: l'hypnogram iniziale deve etichettare le euristiche come stime e non inventare REM.
6. Il checkout non contiene Gradle wrapper né `local.properties`. In questo ambiente la baseline ha richiesto Gradle 8.14.3 dalla cache, JDK Android Studio e `ANDROID_HOME` esplicito. Non committare `local.properties`.

## Studio dei repository esterni (clone temporanei)

I repository sono stati clonati in `%TEMP%/Svegliami2-research`, fuori da questo checkout. Sono stati letti README, licenze e sorgenti relativi a capture/DSP, classificazione, storage, score/grafici e Health Connect.

| Repository | Osservazioni tecniche | Licenza e decisione |
|---|---|---|
| [sleep-secret](https://github.com/14790897/sleep-secret) | Flutter; CED-tiny via ONNX Runtime, finestre PCM, filtro energia, accumulo eventi/timeline e clip. Il modello è incluso separatamente dal codice e ha note di provenienza proprie. | GPL-3.0; non copiare codice o pesi. Ricercare in seguito un modello con licenza/pesi compatibili oppure usare DSP proprietario semplice e dichiarato non clinico. ONNX Runtime da solo non concede i diritti sul modello. |
| [FOSS-snore-detector-android-app](https://github.com/elgrande73/FOSS-snore-detector-android-app) | Kotlin/Compose, `AudioRecord`, gestione input/routing, Room e processing/registrazione; utile per failure handling, stato sensori, sessione foreground e storage a segmenti. | Apache-2.0; riuso puntuale possibile solo dopo inventario file e attribuzioni. Per ora usato come riferimento, nessun codice copiato. |
| [DeltaSleep](https://github.com/ntufar/DeltaSleep) | Kotlin/Rust con JNI; frame PCM a 16 kHz, DSP nativo RMS/ZCR/banda e aggregazione epoch; codice Rust puro testabile host e shim JNI. Introduce NDK/cargo-ndk e costi di build/manutenzione. | MIT; tecnicamente compatibile, ma si preferisce prototipare un motore Kotlin autonomo e misurare i limiti prima di introdurre Rust/JNI. Nessun codice copiato. |
| [somn](https://github.com/Vic-41148/somn) | Compose a moduli, Room, score/debito, correlazioni e timeline; utile per inventario funzionale e separazione UI/dati. | GPL-3.0; solo studio, nessun codice/asset incorporato. |
| [Vico](https://github.com/patrykandpatrick/vico) | Libreria Compose con line/bar/cartesian charts. La versione stabile va scelta quando si migra la UI Compose e verificata contro l'AGP/Kotlin del progetto. | Apache-2.0. Non aggiunta nella fase di baseline perché la UI corrente è Views e il cambio va fatto in una fase isolata. |
| [android/health-samples](https://github.com/android/health-samples) | Esempi ufficiali Health Connect per permessi runtime, lettura `SleepSessionRecord`/stages e gestione disponibilità del provider. Integrazione opzionale e importazione separata dai dati stimati/locali. | Apache-2.0; riferimento ufficiale. Nessun codice incorporato finora. |

Gli algoritmi di rilevamento e gli score presi dai repository non costituiscono validazione clinica. I pesi e i dataset necessitano di una verifica licenza distinta dal codice sorgente. Le librerie del repository non dichiarano necessariamente la licenza di ogni modello/dataset distribuito.

## Target architetturale incrementale

- `alarm` e `weather`: mantenere scheduler e client isolati, con l'occorrenza normale come fallback indipendente.
- `sleep` e `sensors`: sessione, modalità comodino/materasso/combinata e quality/confidence per ogni sorgente.
- `audio`: pipeline bounded-memory, buffer circolare, DSP a finestre e repository clip opzionale con retention.
- `analysis`: score composito configurabile, explainability per componente, stime esplicite e nessuna REM senza validazione.
- `storage`: prima adattatore compatibile del JSON esistente; poi Room con importazione verificata e migrazione versionata.
- `journal`, `health`, `ui`: introdurre dopo i contratti dati e i casi di permesso; Compose/Material 3 e Vico con rollout per schermata.

### Ordine di consegna

1. Baseline e documentazione (questa fase).
2. Dominio/modelli e compatibilità dati; score non clinico spiegabile.
3. UI premium progressiva con navigazione e componenti riusabili.
4. Modalità combinata e sensor fusion senza cambiare il percorso della sveglia.
5. Eventi audio e registrazione opzionale; cifratura, playback, limiti e cancellazione prima dell'attivazione.
6. Journal/insights e profili trasporto; adattamento smart alarm con hard deadline.
7. Health Connect opzionale; test di migrazione, processo interrotto e dispositivo reale.

Una fase è completata solo quando compila e i test applicabili passano. Le misure di fasi/score restano sperimentali fino a validazione con dati etichettati e dispositivi diversi.

## Incrementi già presenti nel branch

- Sleep Score spiegabile con pesi iniziali configurabili nel calcolatore, componenti audio omesse quando non misurate, e indicazione che la regolarità corrente usa l'avvio del monitoraggio come proxy.
- Modalità combinata con sensor fusion pesata dalla copertura campioni e fallback del microfono all'accelerometro.
- Campo JSON opzionale per la modalità e per i flussi movimento/suono; le sessioni legacy vengono lette con i default esistenti.
- Room 2.8.5 con entità per sessioni/campioni/eventi/registrazioni/diario/sveglie/profili e importazione iniziale asincrona dei JSON legacy. JSON resta il percorso compatibile della UI in questo incremento; un successivo passaggio potrà rendere Room fonte di lettura dopo verifica della migrazione su dispositivi reali.
- Nessun codice o modello esterno incorporato finora. Room usa la release stabile AndroidX 2.8.5; le guide ufficiali Health Connect richiedono consenso/permessi separati e distinguono sessione e fasi importate.
- Insights iniziali calcolati dalle sessioni reali: media sonno/score, grafici per notte e debito sulle ultime sette sessioni osservate rispetto al riferimento 8h.
- Navigazione inferiore a cinque sezioni condivisa tra Home, storico Sleep, Insights, Diario e Impostazioni.

## Aggiornamento prodotto

La specifica di prodotto successiva restringe la scelta visibile a comodino oppure materasso. Il supporto `COMBINED` rimane nel modello/servizio per leggere preferenze e sessioni legacy; `Prefs.sleepTrackingMode` normalizza la vecchia preferenza a materasso e la nuova UI non offre la combinazione. È stata aggiunta una `SettingsActivity` con selezione persistente del sensore, località meteo corrente e stato esplicito dell'account/audio. La località continua a essere modificata nella sezione Home esistente.

La navigazione a cinque voci è ora instradata alla pagina Impostazioni effettiva; la pagina non implementa account, tema o configurazioni che non esistono ancora. I documenti di progetto aggiornati sono nella root (`ARCHITECTURE.md`, `ROADMAP.md`, `TECHNICAL_AUDIT.md`, `KNOWN_ISSUES.md`, `THIRD_PARTY_LICENSES.md`, `SYNC_ARCHITECTURE.md`).
