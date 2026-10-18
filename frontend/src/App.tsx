import { Navigate, Route, Routes } from 'react-router-dom'
import LayoutApp from './common/layout/LayoutApp'
import Segnaposto from './common/Segnaposto'
import Uscita from './common/Uscita'
import PaginaLogin from './features/auth/PaginaLogin'
import PaginaPasswordDimenticata from './features/auth/PaginaPasswordDimenticata'
import PaginaRegistrazione from './features/auth/PaginaRegistrazione'
import PaginaReimpostaPassword from './features/auth/PaginaReimpostaPassword'
import PaginaVerificaEmail from './features/auth/PaginaVerificaEmail'
import RichiedeAdmin from './features/auth/RichiedeAdmin'
import RichiedeAutenticazione from './features/auth/RichiedeAutenticazione'
import RichiedeEmailVerificata from './features/auth/RichiedeEmailVerificata'
import SoloOspite from './features/auth/SoloOspite'
import PaginaModificaProfilo from './features/utente/PaginaModificaProfilo'
import PaginaProfilo from './features/utente/PaginaProfilo'

/**
 * Rotte dell'app con le guardie annidate: autenticato → email verificata → admin.
 * Le pagine dell'app stanno nel guscio LayoutApp (barra in alto e in basso); mappa e admin sono ancora
 * segnaposto finché non arrivano le schermate vere.
 * Il router (BrowserRouter) sta in main.tsx, così i test possono usare MemoryRouter.
 */
function App() {
  return (
    <Routes>
      <Route element={<SoloOspite />}>
        <Route path="/login" element={<PaginaLogin />} />
        <Route path="/register" element={<PaginaRegistrazione />} />
        <Route path="/forgot-password" element={<PaginaPasswordDimenticata />} />
        <Route path="/reset-password" element={<PaginaReimpostaPassword />} />
      </Route>

      <Route element={<RichiedeAutenticazione />}>
        <Route path="/verify-email" element={<PaginaVerificaEmail />} />
        <Route element={<RichiedeEmailVerificata />}>
          <Route element={<LayoutApp />}>
            <Route path="/" element={<Segnaposto titolo="Percorso" />} />
            <Route path="/profile" element={<PaginaProfilo />} />
            <Route path="/profile/edit" element={<PaginaModificaProfilo />} />
            <Route element={<RichiedeAdmin />}>
              <Route path="/admin" element={<Segnaposto titolo="Admin" />} />
            </Route>
          </Route>
        </Route>
      </Route>

      {/* fuori dalle guardie: logout e poi login con lo state (vedi common/Uscita) */}
      <Route path="/logout" element={<Uscita />} />

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}

export default App
