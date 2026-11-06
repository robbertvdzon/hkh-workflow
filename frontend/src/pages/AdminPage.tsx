import { type FormEvent, useState } from "react"
import { errorMessage } from "@/api/client"
import { useAdminLogin, useAdminLogout, useLoadedSession } from "@/api/session"
import { Alert } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Card } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { PageHeader } from "@/components/ui/page-header"

export function AdminPage() {
  const session = useLoadedSession()
  return (
    <>
      <PageHeader title="Beheer" tabTitle="Beheer">
        De instellingen zijn alleen voor de beheerder.
      </PageHeader>
      {session.admin ? <LoggedIn /> : <LoginForm />}
    </>
  )
}

function LoggedIn() {
  const logout = useAdminLogout()
  return (
    <Card className="max-w-[520px]">
      <h2 className="text-xl">Je bent ingelogd als beheerder</h2>
      <div>
        <Button variant="secondary" onClick={() => logout.mutate()}>
          Uitloggen als beheerder
        </Button>
      </div>
    </Card>
  )
}

function LoginForm() {
  const [password, setPassword] = useState("")
  const login = useAdminLogin()

  function submit(event: FormEvent) {
    event.preventDefault()
    login.mutate({ password }, { onError: () => setPassword("") })
  }

  return (
    <>
      {login.isError && <Alert variant="error">{errorMessage(login.error)}</Alert>}
      <Card className="max-w-[520px]">
        <h2 className="text-xl">Inloggen als beheerder</h2>
        <form onSubmit={submit} className="flex flex-col gap-4" autoComplete="off">
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="admin-password">Beheerderswachtwoord</Label>
            <Input id="admin-password" type="password" autoFocus autoComplete="new-password" value={password} onChange={(event) => setPassword(event.target.value)} />
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
