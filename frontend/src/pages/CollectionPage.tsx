import { Link, Navigate, useParams } from "react-router"
import { useLoadedSession } from "@/api/session"
import { ChoiceOff, Choices } from "@/components/ui/choice"
import { PageHeader } from "@/components/ui/page-header"

/** Wat je in een collectie kunt doen. De twee schermen daarachter komen hierna; tot die tijd zijn de keuzes nog dicht. */
export function CollectionPage() {
  const session = useLoadedSession()
  const { collection } = useParams()

  if (!session.collections.some((it) => it.name === collection && it.available)) return <Navigate to="/" replace />

  return (
    <>
      <Link to="/" className="text-sm font-medium no-underline">
        ← Andere collectie
      </Link>
      <PageHeader title={`Collectie ${collection}`} tabTitle="Wat wil je doen?">
        Wat wil je doen?
      </PageHeader>
      <Choices>
        <ChoiceOff title="Nieuwe bestanden in ZCBS zetten">
          Scans van K: kiezen, ze een recordnummer geven, pdf2zcbs.exe en images.exe draaien, uploaden en een back-up maken. De gegevens vul je later in.
        </ChoiceOff>
        <ChoiceOff title="ZCBS records aanpassen">Kies een record of een reeks records en vul de titel en andere gegevens in, via het formulier van ZCBS.</ChoiceOff>
      </Choices>
    </>
  )
}
