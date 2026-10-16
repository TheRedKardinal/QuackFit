package it.quackfit.tentativi;

/** Stato di un tentativo: IN_CORSO → SUPERATO oppure FALLITO. Chiuso non si riapre. */
public enum EsitoTentativo {
	IN_CORSO,
	SUPERATO,
	FALLITO
}
