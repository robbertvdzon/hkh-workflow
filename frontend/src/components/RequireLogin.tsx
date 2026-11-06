import { Navigate, Outlet, useLocation } from "react-router"
import { useLoadedSession } from "@/api/session"

/** Schermen die vragen dat je bent ingelogd. Na het inloggen kom je terug waar je wilde zijn. */
export function RequireLogin() {
  const session = useLoadedSession()
  const location = useLocation()
  if (!session.user) return <Navigate to="/" replace state={{ from: location.pathname + location.search }} />
  return <Outlet />
}
