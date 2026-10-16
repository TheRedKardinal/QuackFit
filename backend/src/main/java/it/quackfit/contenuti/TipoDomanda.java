package it.quackfit.contenuti;

/** Tipo di una domanda: decide se ha opzioni (VERO_FALSO, SCELTA_MULTIPLA) o elementi da abbinare (ABBINAMENTO). */
public enum TipoDomanda {
	VERO_FALSO,
	/** Più opzioni elencate, una sola corretta. */
	SCELTA_MULTIPLA,
	ABBINAMENTO
}
