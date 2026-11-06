import { MutationCache, QueryCache, QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { StrictMode } from "react"
import { createRoot } from "react-dom/client"
import { createBrowserRouter, RouterProvider } from "react-router"
import { ApiError } from "@/api/client"
import { sessionKey } from "@/api/session"
import { AppLayout } from "@/components/AppLayout"
import { RequireLogin } from "@/components/RequireLogin"
import { AdminPage } from "@/pages/AdminPage"
import { CollectionPage } from "@/pages/CollectionPage"
import { ErrorPage } from "@/pages/ErrorPage"
import { NotFoundPage } from "@/pages/NotFoundPage"
import { StartPage } from "@/pages/StartPage"
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

const router = createBrowserRouter(
  [
    {
      element: <AppLayout />,
      errorElement: <ErrorPage />,
      children: [
        { path: "/", element: <StartPage /> },
        { path: "/beheer", element: <AdminPage /> },
        {
          element: <RequireLogin />,
          children: [{ path: "/collectie/:collection", element: <CollectionPage /> }],
        },
        { path: "*", element: <NotFoundPage /> },
      ],
    },
  ],
  { basename: "/app" },
)

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  </StrictMode>,
)
