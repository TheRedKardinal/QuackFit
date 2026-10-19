package it.quackfit.seed;

import java.util.List;

import it.quackfit.contenuti.CreazioneDomandaRequest;
import it.quackfit.contenuti.CreazioneDomandaRequest.CoppiaRequest;
import it.quackfit.contenuti.CreazioneDomandaRequest.OpzioneRequest;
import it.quackfit.contenuti.TipoDomanda;

/**
 * Contenuti iniziali del percorso (task 35): 3 tappe, 3 lezioni ciascuna, 5 domande per lezione.
 * Generati in modo meccanico dalla bozza rivista dall'utente (docs/contenuti-bozza.md, solo in locale): i testi
 * vanno cambiati lì e poi rigenerati, non a mano qui. Le domande sono già nella forma delle richieste dell'editor
 * admin, così il seeder le inserisce con le stesse regole (vedi ContenutiSeeder).
 */
final class ContenutiIniziali {

	record LezioneIniziale(String titolo, String descrizione, List<CreazioneDomandaRequest> domande) {
	}

	record TappaIniziale(String titolo, String descrizione, List<LezioneIniziale> lezioni) {
	}

	private ContenutiIniziali() {
	}

	static final List<TappaIniziale> TAPPE = List.of(
		tappa("Come funziona il corpo",
				"Muscoli, energia e recupero: le basi per capire che cosa succede quando ti alleni.",
				lezione("I muscoli principali",
						"Dove si trovano e che cosa fanno i muscoli più usati negli esercizi.",
						veroFalso("Il quadricipite si trova nella parte anteriore della coscia.", true,
							"Il quadricipite è il grande muscolo sul davanti della coscia: distende il ginocchio, per esempio quando ti alzi da una sedia."),
						sceltaMultipla("Quale muscolo piega il gomito?",
							"Il bicipite, sulla parte anteriore del braccio, piega il gomito; il tricipite, sulla parte posteriore, lo distende.",
							0,
							"Il bicipite",
							"Il tricipite",
							"Il deltoide",
							"Il polpaccio"),
						abbinamento("Abbina ogni muscolo alla sua zona del corpo.",
							"Sapere dove si trovano i muscoli aiuta a capire quali lavorano in ogni esercizio.",
							coppia("Pettorale", "Petto"),
							coppia("Dorsale", "Schiena"),
							coppia("Gluteo", "Anca e bacino"),
							coppia("Polpaccio", "Parte posteriore della gamba")),
						veroFalso("Gli addominali servono solo a piegare il busto in avanti.", false,
							"Gli addominali stabilizzano anche il tronco: sostengono la schiena quando sollevi qualcosa, cammini o mantieni la postura."),
						sceltaMultipla("Che cosa collega un muscolo a un osso?",
							"Il tendine collega il muscolo all'osso e trasmette la forza del movimento; il legamento invece collega un osso a un altro.",
							0,
							"Il tendine",
							"Il legamento",
							"La cartilagine")),
				lezione("Energia e calorie",
						"Da dove prende energia il corpo e che cosa vuol dire bilancio energetico.",
						veroFalso("Il corpo consuma energia anche quando siamo fermi.", true,
							"Respirare, far battere il cuore e mantenere la temperatura richiede energia: è il metabolismo di base, attivo anche a riposo."),
						sceltaMultipla("Che cosa si intende per bilancio energetico?",
							"Il bilancio energetico confronta l'energia in entrata (il cibo) con quella in uscita (metabolismo e movimento): nel tempo orienta l'aumento o la perdita di peso.",
							0,
							"Il confronto tra l'energia che arriva dal cibo e quella che il corpo consuma",
							"Il numero di pasti che fai in un giorno",
							"La quantità di acqua che bevi",
							"Il peso che sollevi in palestra"),
						abbinamento("Abbina ogni componente dell'alimentazione al suo ruolo principale.",
							"Ogni componente ha un compito diverso: per questo un'alimentazione varia è più utile di una che ne elimina uno.",
							coppia("Carboidrati", "Fonte di energia pronta"),
							coppia("Proteine", "Costruzione e riparazione dei tessuti"),
							coppia("Grassi", "Riserva di energia e funzioni dell'organismo"),
							coppia("Acqua", "Trasporto delle sostanze e controllo della temperatura")),
						veroFalso("Per perdere peso conta solo l'allenamento, non quello che si mangia.", false,
							"Allenamento e alimentazione lavorano insieme: il movimento aumenta l'energia consumata, ma il bilancio dipende anche da quanta energia arriva dal cibo."),
						sceltaMultipla("Durante uno sforzo lungo e moderato, come una camminata veloce, che cosa usa il corpo come energia?",
							"Negli sforzi moderati e prolungati il corpo usa insieme carboidrati e grassi; le proteine contribuiscono solo in piccola parte.",
							0,
							"Un insieme di carboidrati e grassi",
							"Solo le proteine dei muscoli",
							"Solo i carboidrati",
							"Solo l'acqua")),
				lezione("Recupero e riposo",
						"Perché i progressi arrivano anche nei giorni in cui non ti alleni.",
						veroFalso("I muscoli si rafforzano durante il recupero, non solo durante l'allenamento.", true,
							"L'allenamento è lo stimolo; l'adattamento che rende il muscolo più forte avviene nelle ore e nei giorni di recupero."),
						sceltaMultipla("Quale abitudine aiuta di più il recupero?",
							"Il sonno è uno dei momenti più importanti per il recupero: un riposo regolare aiuta muscoli e sistema nervoso a riprendersi.",
							0,
							"Dormire in modo regolare",
							"Allenare ogni giorno lo stesso muscolo al massimo",
							"Saltare i pasti dopo l'allenamento",
							"Ignorare la stanchezza"),
						abbinamento("Abbina ogni termine al suo significato.",
							"Conoscere questi termini aiuta ad ascoltare il corpo e a dosare allenamento e riposo.",
							coppia("Recupero attivo", "Movimento leggero nei giorni di riposo"),
							coppia("Indolenzimento muscolare", "Fastidio che compare uno o due giorni dopo uno sforzo nuovo"),
							coppia("Sovrallenamento", "Stanchezza che dura nel tempo per troppo allenamento e poco riposo"),
							coppia("Defaticamento", "Attività leggera alla fine della seduta")),
						veroFalso("Sentire i muscoli indolenziti il giorno dopo è sempre il segno di un allenamento fatto bene.", false,
							"L'indolenzimento dice che il muscolo ha ricevuto uno stimolo nuovo, non quanto è stato buono l'allenamento: si può migliorare anche senza sentirlo."),
						sceltaMultipla("Che cosa conviene fare se durante un esercizio senti un dolore acuto e improvviso?",
							"Un dolore acuto e improvviso è diverso dalla normale fatica: è un segnale per fermarsi, non da ignorare.",
							0,
							"Fermarti e, se il dolore continua, rivolgerti a un professionista",
							"Finire comunque la serie",
							"Aumentare il peso per scaldare il muscolo"))),
		tappa("Tecnica degli esercizi",
				"Riscaldamento, respirazione e i movimenti fondamentali eseguiti bene.",
				lezione("Riscaldamento e respirazione",
						"Come preparare il corpo allo sforzo e respirare con il ritmo giusto.",
						veroFalso("Il riscaldamento prepara muscoli e articolazioni allo sforzo.", true,
							"Qualche minuto di movimento leggero scalda i muscoli e rende i movimenti più fluidi e controllati."),
						sceltaMultipla("Quale riscaldamento è più adatto prima di un allenamento con i pesi?",
							"Muoversi in modo leggero e provare l'esercizio con poco peso prepara proprio i movimenti che farai dopo.",
							0,
							"Movimento leggero e qualche ripetizione dell'esercizio con poco peso",
							"Restare a lungo fermi in allungamento",
							"Iniziare subito con il peso più alto",
							"Nessun riscaldamento"),
						abbinamento("Abbina ogni momento dell'esercizio alla respirazione più comune.",
							"Di solito si espira nella fase più faticosa e si inspira nel ritorno: aiuta a mantenere il ritmo senza trattenere il fiato a lungo.",
							coppia("Fase di sforzo", "Espirare"),
							coppia("Fase di ritorno", "Inspirare"),
							coppia("Pausa tra le serie", "Respirare con calma")),
						veroFalso("Trattenere il fiato a lungo durante ogni esercizio è sempre consigliato.", false,
							"Trattenere il fiato a lungo affatica e fa salire la pressione: nella maggior parte degli esercizi conviene respirare con un ritmo regolare."),
						sceltaMultipla("A che cosa serve il defaticamento alla fine dell'allenamento?",
							"Qualche minuto di attività leggera a fine seduta aiuta il respiro e il battito a tornare con calma ai valori di riposo.",
							0,
							"A tornare gradualmente a uno stato di riposo",
							"A bruciare il grasso di una zona precisa",
							"A sostituire il sonno")),
				lezione("Lo squat",
						"Il piegamento sulle gambe: posizione, schiena e appoggio dei piedi.",
						veroFalso("Nello squat le ginocchia dovrebbero seguire la direzione delle punte dei piedi.", true,
							"Le ginocchia allineate alle punte distribuiscono meglio il carico; se cedono verso l'interno, il movimento diventa meno stabile."),
						sceltaMultipla("Come dovrebbe essere la schiena durante lo squat?",
							"Una schiena neutra mantiene le sue curve naturali e regge meglio il carico; arrotondarla o inarcarla molto la mette sotto sforzo.",
							0,
							"Neutra, mantenendo le curve naturali",
							"Molto arrotondata",
							"Molto inarcata",
							"Non ha importanza"),
						abbinamento("Abbina ogni parte del corpo all'indicazione giusta per lo squat.",
							"Ogni parte del corpo ha il suo compito: insieme rendono il movimento stabile e sicuro.",
							coppia("Piedi", "Ben appoggiati a terra"),
							coppia("Ginocchia", "Nella direzione delle punte dei piedi"),
							coppia("Schiena", "Neutra e stabile"),
							coppia("Sguardo", "Rivolto in avanti")),
						veroFalso("Nello squat bisogna sollevare i talloni per scendere di più.", false,
							"I talloni restano a terra: sollevarli sposta il peso sulle punte e riduce l'equilibrio. Se scendere è difficile, si scende meno e si lavora sulla mobilità."),
						sceltaMultipla("Quali muscoli lavorano di più nello squat?",
							"Nello squat lavorano soprattutto i quadricipiti, sul davanti della coscia, e i glutei; gli addominali aiutano a tenere stabile il tronco.",
							0,
							"Quadricipiti e glutei",
							"Bicipiti e tricipiti",
							"Pettorali e deltoidi")),
				lezione("Spinte e trazioni",
						"Piegamenti sulle braccia e rematore: allineamento del corpo e controllo del movimento.",
						veroFalso("Nei piegamenti sulle braccia il corpo dovrebbe restare in linea dalla testa ai talloni.", true,
							"Tenere il corpo in linea fa lavorare petto e braccia e chiede agli addominali di stabilizzare il tronco."),
						sceltaMultipla("Nel rematore con manubrio, quale muscolo lavora di più?",
							"Nel rematore tiri il peso verso il busto: lavorano soprattutto i dorsali, con l'aiuto delle braccia.",
							0,
							"Il dorsale",
							"Il quadricipite",
							"Il polpaccio"),
						abbinamento("Abbina ogni esercizio al muscolo che lavora di più.",
							"Ogni esercizio ha dei muscoli principali: conoscerli aiuta a costruire un allenamento equilibrato.",
							coppia("Piegamenti sulle braccia", "Pettorali"),
							coppia("Rematore", "Dorsali"),
							coppia("Affondi", "Quadricipiti e glutei"),
							coppia("Curl con manubri", "Bicipiti")),
						veroFalso("Fare i movimenti in fretta e senza controllo rende l'esercizio più efficace.", false,
							"Un movimento controllato fa lavorare il muscolo per tutto il percorso e riduce il rischio di farsi male; senza controllo lo sforzo passa allo slancio."),
						sceltaMultipla("Nei piegamenti sulle braccia, quale segnale indica che il bacino sta cedendo?",
							"Se la pancia scende, gli addominali non stanno stabilizzando: meglio rendere l'esercizio più facile, per esempio appoggiando le ginocchia.",
							0,
							"La pancia scende verso il pavimento",
							"Le mani sono sotto le spalle",
							"Lo sguardo è rivolto verso il pavimento"))),
		tappa("Falsi miti",
				"Le convinzioni più diffuse sul fitness, smontate con le basi che hai imparato.",
				lezione("Dimagrimento localizzato e sudore",
						"Perché il grasso non si consuma solo dove lavora il muscolo e che cosa misura davvero il sudore.",
						veroFalso("Fare tanti addominali fa perdere il grasso proprio sulla pancia.", false,
							"Il grasso non si consuma solo dove lavora il muscolo: si riduce in tutto il corpo, grazie al bilancio energetico."),
						sceltaMultipla("Che cosa indica soprattutto la quantità di sudore?",
							"Il sudore serve a disperdere calore: dipende da temperatura, umidità e persona, non misura il grasso consumato.",
							0,
							"Il modo in cui il corpo si raffredda",
							"Quanto grasso hai bruciato",
							"Quanto è stato efficace l'allenamento"),
						abbinamento("Abbina ogni mito alla spiegazione corretta.",
							"Questi miti sono molto diffusi perché sembrano dare risultati veloci, ma confondono il sudore con il grasso.",
							coppia("Sudare tanto fa dimagrire", "Il peso perso con il sudore è acqua e si recupera bevendo"),
							coppia("Gli addominali bruciano la pancia", "Il grasso si riduce in tutto il corpo"),
							coppia("Le fasce termiche sciolgono il grasso", "Fanno solo sudare di più la zona coperta")),
						veroFalso("Il peso perso sudando durante un allenamento è in gran parte acqua.", true,
							"Con il sudore si perdono soprattutto liquidi: il peso torna quando si beve, ed è importante farlo per reintegrarli."),
						sceltaMultipla("Qual è il modo più affidabile per ridurre il grasso nel tempo?",
							"La riduzione del grasso dipende dal bilancio energetico nel tempo: movimento costante e alimentazione adeguata lavorano insieme.",
							0,
							"Unire movimento regolare e un'alimentazione adeguata",
							"Allenare solo la zona da snellire",
							"Sudare il più possibile con vestiti pesanti",
							"Saltare i pasti")),
				lezione("Pesi e forma fisica",
						"Che cosa fanno davvero i pesi ai muscoli e che cosa vuol dire tonificare.",
						veroFalso("Sollevare pesi fa diventare automaticamente molto muscolosi.", false,
							"Aumentare molto la massa muscolare richiede tempo, un allenamento mirato e un'alimentazione adatta: per la maggior parte delle persone i pesi rendono più forti e tonici."),
						sceltaMultipla("Che cosa significa davvero tonificare?",
							"Tonificare vuol dire rafforzare i muscoli e, spesso, ridurre il grasso che li copre: il grasso non si trasforma in muscolo, sono tessuti diversi.",
							0,
							"Rendere i muscoli più forti e visibili, anche riducendo il grasso",
							"Allungare i muscoli",
							"Trasformare il grasso in muscolo"),
						abbinamento("Abbina ogni mito alla spiegazione corretta.",
							"Muscolo e grasso sono tessuti diversi, il dolore non misura i progressi e la forza serve a tutte le età.",
							coppia("Se smetti di allenarti il muscolo diventa grasso", "Il muscolo si riduce, ma non si trasforma in grasso"),
							coppia("Senza dolore non c'è progresso", "Si può migliorare anche senza indolenzimento"),
							coppia("I pesi sono solo per i giovani", "Allenare la forza è utile a ogni età")),
						veroFalso("Allenare la forza può essere utile a qualsiasi età.", true,
							"Con esercizi e carichi adatti, allenare la forza aiuta a mantenere muscoli, ossa e autonomia nei movimenti; in caso di problemi di salute è bene parlarne prima con il medico."),
						sceltaMultipla("Che cosa succede ai muscoli se smetti di allenarti per un lungo periodo?",
							"Senza stimolo i muscoli si riducono e perdono forza; riprendendo ad allenarsi si recupera.",
							0,
							"Diventano più piccoli e meno forti",
							"Si trasformano in grasso",
							"Restano uguali per sempre")),
				lezione("Miti a tavola",
						"Orari dei pasti, cibi light, integratori e acqua: che cosa conta davvero.",
						veroFalso("Mangiare carboidrati la sera fa ingrassare di per sé.", false,
							"Conta il bilancio energetico dell'intera giornata, non l'orario in cui si mangiano i carboidrati."),
						sceltaMultipla("Quale affermazione sui cibi light è corretta?",
							"Light vuol dire solo meno di qualcosa rispetto al prodotto normale: l'energia che danno si somma comunque a quella del resto della giornata.",
							0,
							"Vanno comunque contati nell'alimentazione della giornata",
							"Si possono mangiare senza limiti",
							"Fanno dimagrire da soli"),
						abbinamento("Abbina ogni mito alla spiegazione corretta.",
							"Anche a tavola i miti nascono da regole troppo semplici: conta l'insieme dell'alimentazione, non il singolo gesto.",
							coppia("Più pasti al giorno accelerano il metabolismo", "Conta l'energia totale, non il numero di pasti"),
							coppia("I carboidrati vanno eliminati", "Sono una fonte di energia utile anche a chi si allena"),
							coppia("Bere durante l'allenamento fa male", "Bere a piccoli sorsi aiuta a reintegrare i liquidi")),
						veroFalso("Per allenarsi bene servono per forza gli integratori.", false,
							"Per la maggior parte delle persone un'alimentazione varia copre ciò che serve; gli integratori hanno senso solo in casi specifici, da valutare con un professionista."),
						sceltaMultipla("Che cosa conviene fare con l'acqua quando ti alleni?",
							"Durante l'allenamento si perdono liquidi con il sudore: bere con regolarità aiuta a reintegrarli.",
							0,
							"Bere regolarmente, anche durante e dopo l'allenamento",
							"Evitare di bere per sudare di più",
							"Bere solo il giorno dopo"))));

	private static TappaIniziale tappa(String titolo, String descrizione, LezioneIniziale... lezioni) {
		return new TappaIniziale(titolo, descrizione, List.of(lezioni));
	}

	private static LezioneIniziale lezione(String titolo, String descrizione, CreazioneDomandaRequest... domande) {
		return new LezioneIniziale(titolo, descrizione, List.of(domande));
	}

	private static CreazioneDomandaRequest veroFalso(String testo, boolean rispostaVero, String spiegazione) {
		return new CreazioneDomandaRequest(testo, spiegazione, TipoDomanda.VERO_FALSO, rispostaVero, null, null);
	}

	/** `corretta` è la posizione (da 0) dell'opzione giusta, nell'ordine della bozza. */
	private static CreazioneDomandaRequest sceltaMultipla(String testo, String spiegazione, int corretta,
			String... opzioni) {
		List<OpzioneRequest> elenco = new java.util.ArrayList<>();
		for (int i = 0; i < opzioni.length; i++) {
			elenco.add(new OpzioneRequest(opzioni[i], i == corretta));
		}
		return new CreazioneDomandaRequest(testo, spiegazione, TipoDomanda.SCELTA_MULTIPLA, null, List.copyOf(elenco),
				null);
	}

	private static CreazioneDomandaRequest abbinamento(String testo, String spiegazione, CoppiaRequest... coppie) {
		return new CreazioneDomandaRequest(testo, spiegazione, TipoDomanda.ABBINAMENTO, null, null, List.of(coppie));
	}

	private static CoppiaRequest coppia(String sinistra, String destra) {
		return new CoppiaRequest(sinistra, destra);
	}

}
