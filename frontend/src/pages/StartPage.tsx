import { type FormEvent, useState } from "react"
import { Link, Navigate, useLocation } from "react-router"
import { errorMessage } from "@/api/client"
import { type Session, useLoadedSession, useLogin } from "@/api/session"
import { Alert } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Card } from "@/components/ui/card"
import { Choice, ChoiceOff, Choices } from "@/components/ui/choice"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { PageHeader } from "@/components/ui/page-header"

/** Het begin van de app: eerst inloggen, daarna een collectie kiezen. */
export function StartPage() {
  const session = useLoadedSession()
  const location = useLocation()
  const cameFrom = (location.state as { from?: string } | null)?.from

  if (!session.user) return <LoginForm session={session} mustLogin={cameFrom !== undefined} />
  if (cameFrom) return <Navigate to={cameFrom} replace />
  return <CollectionChoice session={session} />
}

function LoginForm({ session, mustLogin }: { session: Session; mustLogin: boolean }) {
  const [name, setName] = useState("")
  const [password, setPassword] = useState("")
  const login = useLogin()

  function submit(event: FormEvent) {
    event.preventDefault()
    login.mutate({ name, password }, { onError: () => setPassword("") })
  }

  return (
    <>
      <PageHeader title="Welkom bij ZcbsServer" tabTitle="Inloggen">
        Log in met je eigen naam en wachtwoord van ZCBS. Daarna kies je wat je wilt doen.
      </PageHeader>
      {login.isError && <Alert variant="error">{errorMessage(login.error)}</Alert>}
      {mustLogin && !login.isError && <Alert>Log eerst in bij ZCBS.</Alert>}
      <Card className="max-w-[520px]">
        <h2 className="text-xl">Inloggen bij ZCBS</h2>
        <p className="text-muted">
          Dit zijn dezelfde gegevens als op de ZCBS-website. De app bewaart ze alleen in het geheugen. Zodat je na een herstart niet opnieuw hoeft in te
          loggen, onthoudt je browser ze versleuteld (30 dagen) tot je uitlogt.
        </p>
        {!session.zcbs.configured && (
          <Alert>
            Het adres van ZCBS is nog niet ingesteld. Dat doet de beheerder bij <Link to="/beheer">Beheer</Link>.
          </Alert>
        )}
        <form onSubmit={submit} className="flex flex-col gap-4" autoComplete="off">
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="name">Naam</Label>
            <Input id="name" autoFocus autoComplete="off" value={name} onChange={(event) => setName(event.target.value)} />
            <span className="text-[13px] text-muted">Typ je naam precies zoals in ZCBS.</span>
          </div>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="password">Wachtwoord</Label>
            <Input id="password" type="password" autoComplete="new-password" value={password} onChange={(event) => setPassword(event.target.value)} />
          </div>
          <div>
            <Button type="submit" disabled={login.isPending}>
              Inloggen
            </Button>
          </div>
        </form>
      </Card>
    </>
  )
}

function CollectionChoice({ session }: { session: Session }) {
  return (
    <>
      <PageHeader title="Met welke collectie wil je werken?" tabTitle="Collectie">
        Ingelogd bij ZCBS als <b>{session.user}</b>.
      </PageHeader>
      <Choices>
        {session.collections.map((collection) =>
          collection.available ? (
            <Choice key={collection.name} to={`/collectie/${encodeURIComponent(collection.name)}`} title={collection.name}>
              Collectie in ZCBS
            </Choice>
          ) : (
            <ChoiceOff key={collection.name} title={collection.name}>
              {collection.reason}
            </ChoiceOff>
          ),
        )}
      </Choices>
    </>
  )
}
