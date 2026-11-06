import { MutationCache, QueryCache, QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { StrictMode } from "react"
import { createRoot } from "react-dom/client"
import { createBrowserRouter, RouterProvider } from "react-router"
import { ApiError } from "@/api/client"
import { sessionKey } from "@/api/session"
import { AppLayout } from "@/components/AppLayout"
import { FileViewerProvider } from "@/components/FileViewer"
import { RequireAdmin, RequireLogin } from "@/components/RequireLogin"
import { AdminPage } from "@/pages/AdminPage"
import { CollectionPage, RequireCollection } from "@/pages/CollectionPage"
import { ErrorPage } from "@/pages/ErrorPage"
import { NewFilesPage } from "@/pages/NewFilesPage"
import { NotFoundPage } from "@/pages/NotFoundPage"
import { ProgramLogsPage } from "@/pages/ProgramLogsPage"
import { RecordPage } from "@/pages/RecordPage"
import { RecordsPage } from "@/pages/RecordsPage"
import { SessionsPage } from "@/pages/SessionsPage"
import { SettingsPage } from "@/pages/SettingsPage"
import { StartPage } from "@/pages/StartPage"
import { VersionPage } from "@/pages/VersionPage"
import "./index.css"

/** Is de sessie verlopen, dan kijken we opnieuw wie er is; de schermen sturen je dan naar het inloggen. */
function onError(error: unknown) {
  if (error instanceof ApiError && error.code === "not-logged-in") void queryClient.invalidateQueries({ queryKey: sessionKey })
}

const queryClient = new QueryClient({
  queryCache: new QueryCache({ onError }),
  mutationCache: new MutationCache({ onError }),
  defaultOptions: {
    // Een antwoord van de API blijft hetzelfde als je het nog eens vraagt; alleen een onbereikbare server is een nieuwe poging waard.
    queries: { retry: (failures, error) => !(error instanceof ApiError) && failures < 1 },
  },
})

const router = createBrowserRouter([
    {
      element: <AppLayout />,
      errorElement: <ErrorPage />,
      children: [
        { path: "/", element: <StartPage /> },
        { path: "/beheer", element: <AdminPage /> },
        {
          element: <RequireLogin />,
          children: [
            {
              element: <RequireCollection />,
              children: [
                { path: "/collectie/:collection", element: <CollectionPage /> },
                { path: "/collectie/:collection/nieuw", element: <NewFilesPage /> },
                { path: "/collectie/:collection/records", element: <RecordsPage /> },
                { path: "/collectie/:collection/records/:number", element: <RecordPage /> },
              ],
            },
          ],
        },
        {
          element: <RequireAdmin />,
          children: [
            { path: "/instellingen", element: <SettingsPage /> },
            { path: "/instellingen/sessies", element: <SessionsPage /> },
            { path: "/instellingen/versie", element: <VersionPage /> },
            { path: "/instellingen/logs", element: <ProgramLogsPage /> },
          ],
        },
        { path: "*", element: <NotFoundPage /> },
      ],
    },
])

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <FileViewerProvider>
        <RouterProvider router={router} />
      </FileViewerProvider>
    </QueryClientProvider>
  </StrictMode>,
)
