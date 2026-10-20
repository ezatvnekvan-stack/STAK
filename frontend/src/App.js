import "@/App.css";
import PersonPage from "./pages/PersonPage";
import TrackingPage,{TrackingRedirect} from "./pages/TrackingPage";
import RemotePage from "./pages/RemotePage";
import JellyfinItemPage from "./pages/JellyfinItemPage";
import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { Toaster } from "sonner";
import { AppProvider } from "./context/AppContext";
import AppShell from "./components/AppShell";
import SearchPage from "./pages/SearchPage";
import DiscoverPage from "./pages/DiscoverPage";
import HomeSections from "./components/HomeSections";
import TitlePage from "./pages/TitlePage";
import LibraryPage from "./pages/LibraryPage";
import JellyfinLibraryPage from "./pages/JellyfinLibraryPage";
import WatchlistPage from "./pages/WatchlistPage";
import SharesPage from "./pages/SharesPage";
import ShareViewPage from "./pages/ShareViewPage";
import AccountPage from "./pages/AccountPage";
import { useTheme } from "./lib/theme";
import { MotionProvider } from './components/design/MotionSystem';

function App() {
  const { resolved } = useTheme();
  return (
    <div className="App">
      <AppProvider>
        <MotionProvider>
        <BrowserRouter>
          <Routes>
            <Route element={<AppShell />}>
              <Route path="/" element={<HomeSections />} />
              <Route path="/search" element={<SearchPage />} />
              <Route path="/discover" element={<DiscoverPage />} />
              <Route path="/title/:type/:id" element={<TitlePage />} />
              <Route path="/watchlist" element={<WatchlistPage />} />
              <Route path="/tracking" element={<TrackingPage />} />
              <Route path="/history" element={<TrackingRedirect view="activity" />} />
              <Route path="/progress" element={<TrackingRedirect view="activity" />} />
              <Route path="/library" element={<JellyfinLibraryPage />} />
              <Route path="/torbox" element={<LibraryPage />} />
              <Route path="/shares" element={<SharesPage />} />
              <Route path="/share/:id" element={<ShareViewPage />} />
              <Route path="/account" element={<AccountPage />} />
              <Route path="/person/:id" element={<PersonPage />} />
              <Route path="/calendar" element={<TrackingRedirect view="calendar" />} />
              <Route path="/remote" element={<RemotePage />} />
              <Route path="/jellyfin/item/:id" element={<JellyfinItemPage />} />
              <Route path="*" element={<Navigate to="/" replace />} />
            </Route>
          </Routes>
        </BrowserRouter>
        </MotionProvider>
        <Toaster
          theme={resolved}
          position="bottom-center"
          offset="96px"
          mobileOffset={{ bottom: "96px", left: "12px", right: "12px" }}
          toastOptions={{
            style: { background: "var(--c-111111)", border: "1px solid var(--c-2a2a2a)", color: "var(--fg-1)", fontFamily: "Space Mono, monospace", fontSize: 12, borderRadius: 'var(--stak-radius-card)' },
          }}
        />
      </AppProvider>
    </div>
  );
}

export default App;
