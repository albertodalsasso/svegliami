# Svegliami su Google Play: cosa inserire nella Play Console

Testi e immagini della scheda sono in `metadata/android/it-IT/`, nella struttura standard di fastlane. F-Droid usa la stessa struttura.

| Campo della Play Console | File | Limite |
|---|---|---|
| Nome dell'app | `title.txt` | 30 caratteri |
| Descrizione breve | `short_description.txt` | 80 caratteri |
| Descrizione completa | `full_description.txt` | 4000 caratteri |
| Icona dell'app (512×512 PNG) | `images/icon.png` | |
| Immagine in evidenza (1024×500 PNG) | `images/featureGraphic.png` | |
| Screenshot del telefono | `images/phoneScreenshots/1-3.png` | da 2 a 8, rapporto 1:2 |

La scheda è solo in italiano perché anche l'app è solo in italiano.

## 1. Crea l'app
- **Nome**: Svegliami: sveglia e pioggia. **Lingua predefinita**: Italiano. **App**, **Gratuita**.
- **Pacchetto**: `it.svegliami`, letto dall'AAB al primo caricamento.

## 2. Configura l'app (Dashboard → "Configura l'app")

**Norme sulla privacy**: https://albertodalsasso.github.io/svegliami/privacy.html

**Accesso all'app**: "Tutte le funzionalità sono disponibili senza restrizioni di accesso" (non ci sono login).

**Annunci**: No, l'app non contiene annunci.

**Classificazione dei contenuti** (questionario IARC):
- Categoria: *Tutte le altre tipologie di app* (utilità, produttività).
- Rispondi **No** a tutto: violenza, contenuti sessuali, linguaggio, droghe, gioco d'azzardo, interazione tra utenti, condivisione della posizione con altri utenti, acquisti digitali.
- Risultato atteso: PEGI 3 / "Everyone".

**Pubblico di destinazione**: solo **18 anni e oltre**. Includere fasce sotto i 13 anni farebbe scattare le norme per le famiglie.

**App di notizie**: No. **App governativa**: No. **Funzionalità finanziarie**: nessuna.

**App per la salute**: l'app monitora il sonno, quindi seleziona la voce sulla **gestione del sonno**. Le altre categorie restano non selezionate. Non è un dispositivo medico.

### Sicurezza dei dati
Le domande seguono l'ordine del modulo.

- **L'app raccoglie o condivide dati utente?** Sì.
- **Tutti i dati sono criptati in transito?** Sì: le chiamate a Open-Meteo usano HTTPS.
- **Gli utenti possono chiedere la cancellazione?** Lo sviluppatore non conserva dati, quindi non c'è niente da cancellare sui server. Se il modulo lo consente, spiega che i dati sul telefono si eliminano disinstallando l'app.

Tipi di dati:

| Tipo | Raccolto | Condiviso | Note per il modulo |
|---|---|---|---|
| Posizione approssimativa | Sì | No | Solo per le funzionalità dell'app (previsioni meteo). Trattata in modo temporaneo: inviata a Open-Meteo e non conservata dallo sviluppatore. Facoltativa: si può cercare la città a mano |
| Posizione precisa | Sì | No | Come sopra (GPS) |
| Audio (microfono) | **No** | No | Analizzato solo sul dispositivo e mai trasmesso. L'elaborazione sul dispositivo non conta come raccolta |
| Salute e fitness (sonno) | **No** | No | Resta sul dispositivo |
| Tutti gli altri tipi | No | No | |

"Condiviso: No" vale perché l'invio della posizione al servizio meteo avviene solo per mostrare le previsioni richieste dall'utente. Se preferisci la scelta più prudente, segna "Condiviso: Sì" con scopo *Funzionalità dell'app*: non cambia nulla per l'approvazione.

## 3. Dichiarazioni dei permessi
Arrivano dopo il caricamento del primo AAB, in **Contenuti dell'app**.

**Sveglie esatte (USE_EXACT_ALARM)**: la funzionalità principale è una **sveglia**. Testo:
> Svegliami è un'app sveglia: le sveglie devono suonare all'orario esatto impostato dall'utente, o in anticipo se è prevista pioggia.

**Notifiche a schermo intero (USE_FULL_SCREEN_INTENT)**: caso d'uso **Sveglie**. Testo:
> Quando la sveglia suona, mostra a tutto schermo i pulsanti Ferma e Posticipa, anche con il telefono bloccato.

**Servizi in primo piano**: per ogni tipo serve una descrizione e il link a un breve video. Va bene un video YouTube non in elenco, girato con lo schermo del telefono.

| Tipo | Descrizione | Cosa mostrare nel video |
|---|---|---|
| `systemExempted` | Riproduce la suoneria e la vibrazione della sveglia finché l'utente non la ferma o la posticipa. | Una sveglia che suona e viene fermata |
| `health` | Monitoraggio del sonno avviato dall'utente con «Buonanotte»: legge l'accelerometro tutta la notte per stimare le fasi del sonno e svegliare nel sonno leggero. | «Buonanotte» in modalità Materasso, la notifica attiva, lo storico del sonno |
| `microphone` | Monitoraggio del sonno in modalità Comodino, avviato dall'utente: misura il volume dei movimenti e del russare. L'audio è elaborato sul dispositivo e mai registrato o salvato. | «Buonanotte» in modalità Comodino, la richiesta del permesso microfono, la notifica attiva |

In tutti i casi l'impatto se il sistema interrompesse il servizio è lo stesso: la sveglia o il monitoraggio si fermerebbero e l'utente rischierebbe di non svegliarsi.

**Esclusione dall'ottimizzazione batteria**: non ha un modulo. L'app la chiede perché il controllo meteo notturno abbia la rete, ed è un uso ammesso per le sveglie. Se in revisione la contestassero, si può togliere: il controllo userebbe la previsione in cache.

## 4. Test chiuso (obbligatorio per i nuovi account personali)
1. **Test → Test chiuso → Crea traccia**. Aggiungi i tester con un elenco di email o un Gruppo Google.
2. **Crea release**: carica `svegliami.aab` da https://github.com/albertodalsasso/svegliami/releases/download/apk/svegliami.aab e accetta **Play App Signing**.
3. Note di rilascio: `Prima versione: sveglia che si anticipa se piove, sveglia intelligente e monitoraggio del sonno.`
4. Manda ai tester il link di adesione. Devono restare iscritti e con l'app installata per **14 giorni**. Servono almeno **12** tester, meglio qualcuno in più.
5. Dopo 14 giorni compare **Richiedi accesso alla produzione**, con alcune domande sul test.

Ogni nuova build ha un numero di versione più alto, quindi gli aggiornamenti si caricano scaricando di nuovo l'AAB dallo stesso link.
