import { Navigate, Outlet, useParams } from "react-router"
import { useLoadedSession } from "@/api/session"
import { recordsPath } from "@/api/records"
import { Choice, Choices } from "@/components/ui/choice"
import { PageHeader } from "@/components/ui/page-header"
import { BackLink } from "@/components/ui/page-section"

/** Wat je in een collectie kunt doen. */
export function CollectionPage() {
  const { collection = "" } = useParams()

  return (
    <>
      <BackLink to="/">Andere collectie</BackLink>
      <PageHeader title={`Collectie ${collection}`} tabTitle="Wat wil je doen?">
        Wat wil je doen?
      </PageHeader>
      <Choices>
        <Choice to={`/collectie/${encodeURIComponent(collection)}/nieuw`} title="Nieuwe bestanden in ZCBS zetten">
          Scans van K: kiezen, ze een recordnummer geven, pdf2zcbs.exe en images.exe draaien, uploaden en een back-up maken. De gegevens vul je later in.
        </Choice>
        <Choice to={recordsPath(collection)} title="ZCBS records aanpassen">
          Kies een record of een reeks records en vul de titel en andere gegevens in, via het formulier van ZCBS.
        </Choice>
      </Choices>
    </>
  )
}

/** Alle schermen van een collectie vragen een collectie waar je mee mag werken. */
export function RequireCollection() {
  const session = useLoadedSession()
  const { collection } = useParams()
  if (!session.collections.some((it) => it.name === collection && it.available)) return <Navigate to="/" replace />
  return <Outlet />
}
