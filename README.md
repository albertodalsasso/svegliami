# Svegliami

Sveglia Android che **si anticipa se piove**. Puoi creare più sveglie, ognuna con orario, giorni, anticipo, soglia di pioggia e suoneria propri.

Esempio: sveglia alle 07:00 con anticipo di 30 minuti.
1. Alle **06:10** l'app scarica le previsioni da Open-Meteo per la tua località.
2. Se tra le **06:30 e le 07:00** è prevista pioggia (≥ 0,1 mm nei 15 minuti, oppure probabilità ≥ soglia impostata), aggiunge una sveglia alle **06:30**.
3. Se suona quella delle 06:30, la sveglia delle 07:00 di quel giorno viene annullata. Se non piove, suona normalmente alle 07:00.

La sveglia normale è **sempre** programmata. Se meteo o rete non funzionano, suoni comunque all'orario normale.

## Sveglia intelligente e monitoraggio del sonno
Senza smartwatch: il telefono sul materasso registra i movimenti con l'accelerometro.
1. In una sveglia scegli **🌙 Sveglia intelligente**: fino a 10/20/30/45 minuti prima.
2. La sera premi **🌙 Buonanotte** nella schermata principale e lascia il telefono sul materasso, vicino al cuscino (meglio in carica).
3. Nella finestra, al primo movimento (sonno leggero) la sveglia suona. Se non ti muovi, suona comunque all'orario previsto.
   Se piove, la finestra si sposta prima insieme alla sveglia: per questo il controllo meteo avviene prima dell'inizio della finestra.
4. Il monitoraggio si ferma da solo quando suona la sveglia. In **Storico del sonno** trovi il grafico di ogni notte con le fasi stimate (profondo, leggero, sveglio), il tempo a letto, il sonno stimato e i risvegli.

Le fasi sono stimate dai movimenti (attigrafia): sono un'indicazione, non una misura clinica.

## Come aprirlo
1. Apri questa cartella con **Android Studio** (Ladybug o più recente) e lascia completare la sincronizzazione Gradle.
2. Collega il telefono con il debug USB attivo e premi ▶ Run.
3. Nella schermata principale imposta la **località** (vale per tutte le sveglie):
   - **📍 Usa la mia posizione (GPS)**: rileva la posizione una volta. Aspetta il GPS fino a 20 s, poi usa la posizione da rete.
   - **Aggiorna la posizione automaticamente**: prima di ogni controllo meteo usa l'ultima posizione nota del telefono. Richiede il permesso «Consenti sempre». La posizione viene aggiornata anche a ogni apertura dell'app.
   - oppure cerca una città per nome. Scegliendola, l'aggiornamento automatico si disattiva.
   Poi premi **+ Nuova sveglia**: nome, orario, giorni, anticipo, soglia e suoneria. **Nessun giorno selezionato = suona una volta sola** e poi si disattiva. Tocca una sveglia per modificarla o eliminarla, l'interruttore la attiva o la disattiva.
4. Concedi i permessi segnalati in alto, compresa l'esclusione dall'ottimizzazione della batteria: serve perché il controllo notturno abbia la rete.

Nella schermata di una sveglia, «Controlla il meteo adesso» mostra cosa deciderebbe l'app per la sua prossima occorrenza.

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
| `LocationHelper.kt` | Posizione GPS/rete, nome della località (Geocoder), aggiornamento in background |
| `SleepService.kt` | Monitoraggio notturno (accelerometro) e sveglia intelligente |
| `SleepAnalyzer.kt` | Logica pura: punteggio di movimento → fasi, riepilogo, sonno leggero (con unit test) |
| `SleepStore.kt` / `SleepActivity.kt` / `SleepGraphView.kt` | Storico delle notti e grafico |
| `AlarmFire.kt` | Fa suonare una sveglia (programmata o intelligente) |
| `Receivers.kt` | Receiver per la sveglia, per il controllo meteo e per il riavvio |

## Scaricare l'APK sul telefono
A ogni push su `main`, GitHub Actions compila l'app e pubblica l'APK con un link fisso:
https://github.com/albertodalsasso/svegliami/releases/download/apk/svegliami.apk
Aprilo dal browser del telefono: non serve un account GitHub. Per aggiornare, installa il nuovo APK sopra quello vecchio: le impostazioni restano.
