// Pagina di prova per controllare font, colori e pulsanti: verrà sostituita dal router nelle prossime task.
const colori = ['primario', 'successo', 'streak', 'sfondo', 'superficie', 'bordo']

function App() {
  return (
    <main className="container py-4" style={{ maxWidth: 480 }}>
      <h1>QuackFit</h1>
      <p>Impara la teoria del fitness, una lezione alla volta.</p>

      <h2 className="h5 mt-4">Colori</h2>
      <div className="row g-2">
        {colori.map((colore) => (
          <div key={colore} className="col-4">
            <div
              className="rounded-3 border"
              style={{ height: 56, backgroundColor: `var(--colore-${colore})` }}
            />
            <small>{colore}</small>
          </div>
        ))}
      </div>

      <h2 className="h5 mt-4">Pulsanti</h2>
      <div className="d-grid gap-3">
        <button type="button" className="btn-push">Inizia</button>
        <button type="button" className="btn-push btn-push-successo">Continua</button>
        <button type="button" className="btn-push btn-push-neutro">Annulla</button>
        <button type="button" className="btn-push" disabled>Disattivato</button>
      </div>
    </main>
  )
}

export default App
