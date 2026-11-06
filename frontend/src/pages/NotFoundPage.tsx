import { Link } from "react-router"
import { PageHeader } from "@/components/ui/page-header"

export function NotFoundPage() {
  return (
    <>
      <PageHeader title="Dit scherm bestaat niet" tabTitle="Niet gevonden">
        Misschien is de link verouderd.
      </PageHeader>
      <div>
        <Link to="/">Terug naar het begin</Link>
      </div>
    </>
  )
}
