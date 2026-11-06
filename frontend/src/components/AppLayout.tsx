import { Home, Settings } from "lucide-react"
import type { ReactNode } from "react"
import { NavLink, Outlet, useNavigate } from "react-router"
import { errorMessage } from "@/api/client"
import { type Session, useAdminLogout, useLogout, useSession } from "@/api/session"
import { Alert } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { cn } from "@/lib/utils"

/** Het menu en daarnaast het scherm. Het scherm komt pas als bekend is wie er is. */
export function AppLayout() {
  const session = useSession()
  return (
    <div className="flex min-h-full flex-col md:flex-row">
      {session.data && <Sidebar session={session.data} />}
      <main className="flex min-w-0 grow flex-col gap-5.5 px-4 pt-9 pb-30 md:px-10">
        {session.data ? (
          <Outlet />
        ) : session.isError ? (
          <Alert variant="error">
            {errorMessage(session.error)}{" "}
            <button className="cursor-pointer underline" onClick={() => session.refetch()}>
              Opnieuw proberen
            </button>
          </Alert>
        ) : (
          <p className="text-muted">Even laden…</p>
        )}
      </main>
    </div>
  )
}

function Sidebar({ session }: { session: Session }) {
  const navigate = useNavigate()
  const logout = useLogout()
  const adminLogout = useAdminLogout()
  return (
    <nav
      aria-label="Hoofdmenu"
      className="flex shrink-0 flex-col gap-1 border-b border-sidebar-line bg-sidebar px-4 py-7 md:sticky md:top-0 md:h-screen md:w-60 md:border-r md:border-b-0"
    >
      <div className="px-3 pb-7">
        <b className="block font-serif text-[23px] font-semibold">ZcbsServer</b>
        <span className="text-xs text-muted">Historische Kring Heemskerk</span>
      </div>
      <MenuLink to="/" icon={<Home size={18} aria-hidden />}>
        Start
      </MenuLink>
      {session.user && (
        <Button variant="secondary" className="mt-2 w-full" onClick={() => logout.mutate(undefined, { onSuccess: () => navigate("/") })}>
          Uitloggen
        </Button>
      )}

      <div className="grow" />

      {session.user && (
        <div className="px-3 pt-3.5 text-[13px] text-muted">
          <b>Rechten in ZCBS</b>
          <br />
          {session.rights.length ? session.rights.join(", ") : "geen"}
        </div>
      )}
      {session.admin ? (
        <>
          <div className="px-3 pt-4.5 pb-1.5 text-[11px] tracking-wider text-muted uppercase">Beheer</div>
          <MenuLink to="/instellingen" end icon={<Settings size={18} aria-hidden />}>
            Instellingen
          </MenuLink>
          <MenuLink to="/instellingen/sessies" indented>
            Sessies
          </MenuLink>
          <MenuLink to="/instellingen/versie" indented>
            Versie
          </MenuLink>
          <MenuLink to="/instellingen/logs" indented>
            Logs
          </MenuLink>
        </>
      ) : (
        <MenuLink to="/beheer" icon={<Settings size={18} aria-hidden />}>
          Beheer
        </MenuLink>
      )}
      {session.admin && (
        <button className="cursor-pointer px-3 py-1.5 text-left text-sm text-primary underline" onClick={() => adminLogout.mutate(undefined, { onSuccess: () => navigate("/") })}>
          Uitloggen als beheerder
        </button>
      )}
      {session.zcbs.realArchive && <div className="mt-4 border-t border-sidebar-line px-3 pt-4 text-[13px] font-semibold text-danger">Het echte archief</div>}
      <div className="px-3 pt-3.5 text-[11px] leading-tight text-muted" title={session.build.details}>
        {session.build.label}
      </div>
    </nav>
  )
}

function MenuLink({ to, icon, end, indented, children }: { to: string; icon?: ReactNode; end?: boolean; indented?: boolean; children: ReactNode }) {
  return (
    <NavLink
      to={to}
      end={end ?? to === "/"}
      className={({ isActive }) =>
        cn(
          "flex items-center gap-2.5 rounded-lg px-3 py-2.75 text-sm font-medium no-underline",
          indented && "pl-10",
          isActive ? "bg-primary text-white" : "text-foreground-soft hover:bg-sidebar-hover hover:text-foreground",
        )
      }
    >
      {icon}
      {children}
    </NavLink>
  )
}
