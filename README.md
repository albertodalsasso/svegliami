# Svegliami

Sveglia Android che **si anticipa se piove**. Puoi creare più sveglie, ognuna con orario, giorni, anticipo, soglia di pioggia e suoneria propri.

Esempio: sveglia alle 07:00 con anticipo di 30 minuti.
1. Alle **06:10** l'app scarica le previsioni da Open-Meteo per la tua località.
2. Se tra le **06:30 e le 07:00** è prevista pioggia (≥ 0,1 mm nei 15 minuti, oppure probabilità ≥ soglia impostata), aggiunge una sveglia alle **06:30**.
3. Se suona quella delle 06:30, la sveglia delle 07:00 di quel giorno viene annullata. Se non piove, suona normalmente alle 07:00.

La sveglia normale è **sempre** programmata. Se meteo o rete non funzionano, suoni comunque all'orario normale.

## Sveglia intelligente e monitoraggio del sonno
Senza smartwatch, scegli una delle due modalità:
- **Sul materasso** (accelerometro): il telefono sente le vibrazioni quando ti muovi.
- **Sul comodino** (microfono): l'app misura solo il **volume** ogni 100 ms (fruscio di lenzuola e coperte) rispetto al rumore di fondo della stanza. **L'audio non viene mai registrato né salvato**, resta solo un punteggio al minuto. Rumori costanti come un ventilatore non contano, ma un partner o un animale nella stanza possono disturbare la misura.
1. In una sveglia scegli **🌙 Sveglia intelligente**: fino a 10/20/30/45 minuti prima.
2. La sera scegli dove metti il telefono, premi **🌙 Buonanotte** e lascialo sul materasso vicino al cuscino oppure sul comodino (meglio in carica).
3. Nella finestra, al primo movimento (sonno leggero) la sveglia suona. Se non ti muovi, suona comunque all'orario previsto.
   Se piove, la finestra si sposta prima insieme alla sveglia: per questo il controllo meteo avviene prima dell'inizio della finestra.
4. Il monitoraggio si ferma da solo quando suona la sveglia. In **Storico del sonno** trovi il grafico di ogni notte con le fasi stimate (profondo, leggero, sveglio), il tempo a letto, il sonno stimato e i risvegli.

**Russare** (solo con il telefono sul comodino): un minuto conta come russato se ci sono almeno 4 suoni **gravi** (energia sotto ~800 Hz), lunghi 0,3–2,5 s, ripetuti a intervalli regolari da 2–8 s come il respiro. Il fruscio delle coperte è acuto e irregolare, quindi non conta. I minuti di russare compaiono come tacche rosse nel grafico e non vengono contati come movimento. Anche qui l'audio viene solo analizzato al volo, mai salvato.

Le fasi sono stimate dai movimenti (attigrafia): sono un'indicazione, non una misura clinica.


Lo storico mostra anche un **punteggio del sonno sperimentale** da 0 a 100. Il punteggio spiega le componenti misurate e non penalizza i sensori non disponibili; la regolarità usa per ora l'orario di avvio del monitoraggio come proxy. Non è una valutazione medica. Il diario consente di annotare umore, caffeina, attività, stress, pasto e note per ogni sessione.

La sezione **Statistiche** riassume le sessioni raccolte, mostra l'andamento dello score e delle ore dormite, e stima il debito sulle ultime sette sessioni osservate rispetto a un riferimento di 8 ore.

## Come aprirlo
1. Apri questa cartella con **Android Studio** (Ladybug o più recente) e lascia completare la sincronizzazione Gradle.
2. Collega il telefono con il debug USB attivo e premi ▶ Run.
3. In **Opzioni** (barra in basso) imposta la **località** (vale per tutte le sveglie):
   - **📍 Usa la mia posizione (GPS)**: rileva la posizione una volta. Aspetta il GPS fino a 20 s, poi usa la posizione da rete.
   - **Aggiorna a ogni apertura dell'app**: rileva di nuovo la posizione ogni volta che apri l'app, comodo in viaggio. Il controllo meteo notturno usa l'ultima posizione rilevata: l'app non chiede la posizione in background.
   - oppure cerca una città per nome. Scegliendola, l'aggiornamento automatico si disattiva.
   Poi, nella Home, premi **+ Nuova** accanto a «Sveglie»: nome, orario, giorni, anticipo, soglia e suoneria. **Nessun giorno selezionato = suona una volta sola** e poi si disattiva. Tocca una sveglia per modificarla o eliminarla, l'interruttore la attiva o la disattiva.
4. Concedi i permessi segnalati in alto, compresa l'esclusione dall'ottimizzazione della batteria: serve perché il controllo notturno abbia la rete.

Nella schermata di una sveglia, «Controlla il meteo adesso» mostra cosa deciderebbe l'app per la sua prossima occorrenza.

## Impostazioni e stato delle funzionalità

La scheda **Impostazioni** permette di scegliere la modalità di monitoraggio, vedere la località meteo e aprire la relativa gestione. Account e sincronizzazione non sono ancora disponibili. L'audio non viene salvato: clip, Sound Timeline, Morning Report e calendario mensile sono ancora in sviluppo.

## Struttura
| File | Ruolo |
|---|---|
| `RainDecider.kt` | Logica pura: piove nella finestra? (con unit test) |
| `WeatherClient.kt` | Chiamate a Open-Meteo (geocoding + previsioni, senza API key) |
| `Prefs.kt` | Elenco delle sveglie (JSON), stato di ciascuna, località, cache |
| `MainActivity.kt` / `EditAlarmActivity.kt` | Elenco sveglie + località / modifica di una sveglia |
| `Scheduler.kt` | Programmazione di AlarmManager (normale, anticipata, controllo, snooze) |
| `WeatherCheck.kt` | Controllo meteo con nuovi tentativi ogni 5 minuti e cache come ripiego |
| `AlarmService.kt` / `AlarmActivity.kt` | Suoneria, vibrazione e schermata a tutto schermo |
| `LocationHelper.kt` | Posizione GPS/rete, nome della località (Geocoder) |
| `SleepService.kt` | Monitoraggio notturno (accelerometro o microfono) e sveglia intelligente |
| `SystemBars.kt` | Layout a tutto schermo (Android 16): margini per barre di sistema e tastiera |
| `SleepAnalyzer.kt` | Logica pura: punteggio di movimento → fasi, riepilogo, sonno leggero (con unit test) |
| `SleepStore.kt` / `SleepActivity.kt` / `SleepGraphView.kt` | Storico delle notti e grafico |
| `AlarmFire.kt` | Fa suonare una sveglia (programmata o intelligente) |
| `Receivers.kt` | Receiver per la sveglia, per il controllo meteo e per il riavvio |

La situazione tecnica e i limiti correnti sono descritti in [ARCHITECTURE.md](ARCHITECTURE.md), [TECHNICAL_AUDIT.md](TECHNICAL_AUDIT.md), [KNOWN_ISSUES.md](KNOWN_ISSUES.md) e [ROADMAP.md](ROADMAP.md). Le note sulle dipendenze e sulla sincronizzazione sono in [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md) e [SYNC_ARCHITECTURE.md](SYNC_ARCHITECTURE.md).

## Scaricare l'APK sul telefono
A ogni push su `main`, GitHub Actions compila l'app e pubblica l'APK con un link fisso:
https://github.com/albertodalsasso/svegliami/releases/download/apk/svegliami.apk
Aprilo dal browser del telefono: non serve un account GitHub. Per aggiornare, installa il nuovo APK sopra quello vecchio: le impostazioni restano.

La build del branch `Svegliami_2.0` viene pubblicata separatamente qui:
https://github.com/albertodalsasso/svegliami/releases/download/svegliami-2.0/svegliami-2.0.apk

## Google Play
A ogni push su `main` la build produce anche **`svegliami.aab`**, il pacchetto da caricare su Google Play Console. Lo trovi nella stessa release dell'APK:
https://github.com/albertodalsasso/svegliami/releases/download/apk/svegliami.aab
- È firmato con la stessa chiave dell'APK, che su Play fa da **chiave di caricamento**. Al primo caricamento accetta **Play App Signing**.
- Il numero di versione (`versionCode`) è il numero progressivo della build di GitHub Actions, quindi cresce a ogni build.

Privacy policy (italiano e inglese): https://albertodalsasso.github.io/svegliami/privacy.html

Scheda dello store (testi, icona, immagini) e risposte ai moduli della Play Console: [`fastlane/PLAY_CONSOLE.md`](fastlane/PLAY_CONSOLE.md).

## F-Droid
Metadati e istruzioni per la richiesta di inserimento: [`fdroid/README.md`](fdroid/README.md).

## Licenza
Svegliami è software libero, distribuito con licenza [GNU GPL v3 o successiva](LICENSE).
Previsioni meteo: [Open-Meteo.com](https://open-meteo.com/) (CC BY 4.0).
