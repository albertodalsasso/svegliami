# Componenti e licenze di terze parti

Nessun sorgente, modello, peso o asset dei repository sotto elencati è stato copiato nell'app. Sono stati studiati come riferimenti. Verificare sempre il file LICENSE e la licenza separata di modelli/dataset alla versione eventualmente scelta per l'integrazione.

| Progetto | Licenza dichiarata | Esito |
|---|---|---|
| [Sleep Secret](https://github.com/14790897/sleep-secret) | GPL-3.0-or-later | Solo studio; niente codice/pesi incorporati. Il modello CED-tiny richiede verifica licenza/provenienza separata. |
| [FOSS Snore Detector](https://github.com/elgrande73/FOSS-snore-detector-android-app) | Apache-2.0 dichiarata dal progetto | Riferimento AudioRecord/DSP; nessun codice copiato. Prima del riuso verificare licenza per file e avvisi copyright. |
| [DeltaSleep](https://github.com/ntufar/DeltaSleep) | MIT dichiarata dal progetto | Riferimento DSP Kotlin/Rust; nessun codice copiato. |
| [Somn](https://github.com/Vic-41148/somn) | GPL-3.0 dichiarata dal progetto | Solo studio; nessun codice o asset incorporato. |
| [Vico](https://github.com/patrykandpatrick/vico) | Apache-2.0 | Non integrata: app ancora in Views/XML, quindi valutare dopo una scelta concreta Compose. |
| [Android Health Samples](https://github.com/android/health-samples) | Apache-2.0 | Riferimento agli esempi ufficiali; nessun codice incorporato e Health Connect non integrato. |

## Dipendenze app

Le dipendenze Android/Kotlin e le rispettive licenze sono definite dai metadati Gradle e dagli artefatti distribuiti: AndroidX Core, AppCompat, Material Components, Room, Kotlin/Android Gradle Plugin e JUnit per i test. Prima di una pubblicazione occorre generare un inventario aggiornato di dipendenze transitive e avvisi di licenza per le versioni effettivamente risolte. Non sono inclusi modelli ML scaricabili.

## Attribution e policy

Non incorporare componenti GPL senza una decisione di distribuzione esplicita. Un modello o dataset deve avere una verifica distinta dal repository che lo ospita. Aggiornare questo documento quando una dipendenza, codice, modello, peso o asset viene aggiunto.
