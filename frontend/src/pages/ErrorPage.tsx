import { PageHeader } from "@/components/ui/page-header"

/** Er ging iets mis in de frontend zelf. */
export function ErrorPage() {
  return (
    <main className="flex flex-col gap-4 px-4 pt-9 md:px-10">
      <PageHeader title="Er ging iets mis" tabTitle="Fout">
        Dit scherm kon niet worden getoond.
      </PageHeader>
      <div>
        <a href={import.meta.env.BASE_URL}>Terug naar het begin</a>
      </div>
    </main>
  )
}
