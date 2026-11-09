import { type FormEvent, type ReactNode, useState } from "react"
import { errorMessage } from "@/api/client"
import { type Settings, type SettingsRequest, useFtpTest, useSaveSettings, useSettings } from "@/api/admin"
import { Alert } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Card } from "@/components/ui/card"
import { Input, Select, Textarea } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { PageHeader } from "@/components/ui/page-header"
import { QueryState } from "@/components/ui/page-section"

const Mono = ({ children }: { children: ReactNode }) => <span className="font-mono">{children}</span>

export function SettingsPage() {
  const settings = useSettings()
  return (
    <>
      <PageHeader title="Instellingen" tabTitle="Instellingen">
        Hier staat hoe deze app samenwerkt met ZCBS, het systeem achter de archieven van de Historische Kring Heemskerk. De app zet nieuwe scans in ZCBS met <Mono>pdf2zcbs.exe</Mono> en{" "}
        <Mono>images.exe</Mono> en laat daarna de records aanpassen. Hieronder staat welke ZCBS dat is, waar de programma's, scans en back-ups op deze computer staan en hoe de app naar de server
        schrijft. Gebruik volledige paden, bijvoorbeeld <Mono>{"C:\\zcbs\\images-werk\\images.exe"}</Mono>.
      </PageHeader>
      <QueryState query={settings}>
        {(data) => (
          <>
            <SettingsForm key={data.settingsFile} settings={data} />
            <FtpTest settings={data} />
          </>
        )}
      </QueryState>
    </>
  )
}

function requestOf(s: Settings): SettingsRequest {
  return {
    workFolder: s.workFolder,
    imagesExe: s.imagesExe,
    pdf2zcbsExe: s.pdf2zcbsExe,
    perlioUnbuffered: s.perlioUnbuffered,
    sourceFolders: s.sourceFolders,
    useTestZcbs: s.useTestZcbs,
    leftoversFolder: s.leftoversFolder,
    collections: s.collections.map(({ name, imagesResolution, backup100pctFolder, backupPdfFolder }) => ({ name, imagesResolution, backup100pctFolder, backupPdfFolder })),
  }
}

function SettingsForm({ settings }: { settings: Settings }) {
  const [form, setForm] = useState(() => requestOf(settings))
  const [ftpPassword, setFtpPassword] = useState("")
  const [clearFtpPassword, setClearFtpPassword] = useState(false)
  const save = useSaveSettings()
  const problems = settings.problems

  function set<K extends keyof SettingsRequest>(key: K, value: SettingsRequest[K]) {
    save.reset()
    setForm((current) => ({ ...current, [key]: value }))
  }

  function setCollection(name: string, key: "imagesResolution" | "backup100pctFolder" | "backupPdfFolder", value: string) {
    set("collections", form.collections.map((it) => (it.name === name ? { ...it, [key]: value } : it)))
  }

  function submit(event: FormEvent) {
    event.preventDefault()
    // Leeg laten is het opgeslagen wachtwoord houden, en dan hoort het veld niet mee te gaan.
    const password = clearFtpPassword ? { ftpPassword: "" } : ftpPassword ? { ftpPassword } : {}
    save.mutate({ ...form, ...password }, {
      onSuccess: () => {
        setFtpPassword("")
        setClearFtpPassword(false)
      },
    })
  }

  return (
    <>
      {save.isSuccess && <Alert variant="ok">Instellingen opgeslagen.</Alert>}
      {save.isError && <Alert variant="error">{errorMessage(save.error)}</Alert>}
      <form onSubmit={submit} className="flex max-w-[980px] flex-col gap-5.5 rounded-xl border border-line bg-card px-6 py-5">
        <Text id="workFolder" label="Werkmap" value={form.workFolder} placeholder="\\server\werk (of C:\zcbs\werk)" problem={problems.testFolder} onChange={(value) => set("workFolder", value)}>
          Hier werkt elke gebruiker in een eigen map per collectie: <Mono>{"<werkmap>\\<ZCBS-naam>\\<collectie>\\"}</Mono> met daarin <Mono>invoer</Mono> (de gekozen bestanden), <Mono>werk</Mono>{" "}
          (de kopieën met recordnummer), <Mono>pdf2zcbs</Mono> en <Mono>images</Mono> (een kopie van de programma's met alleen hun bestanden, zodat de gebruiker kan meekijken) en <Mono>resten</Mono>.
          Zet hem op een netwerkmap als gebruikers mee willen kijken. Op een server met Windows liever een UNC-pad (<Mono>{"\\\\server\\werk"}</Mono>) dan een gekoppelde schijf. Na afloop ruimt de
          app de kopieën op; de originelen op K: blijven staan.
        </Text>
        <Text id="pdf2zcbsExe" label="pdf2zcbs.exe (hoofdmap)" value={form.pdf2zcbsExe} placeholder="C:\zcbs\pdf2zcbs\pdf2zcbs.exe" problem={problems.pdf2zcbsExe} onChange={(value) => set("pdf2zcbsExe", value)}>
          Het programma zoals het is geïnstalleerd (met zijn ini). De app zet een kopie in de map <Mono>pdf2zcbs</Mono> van de gebruiker en draait die; de pdf wordt daar naast gezet en de uitvoer komt
          in de submap <Mono>output</Mono>. Wijzig je hier iets aan de ini, dan nemen gebruikers dat bij de volgende start over.
        </Text>
        <Text id="imagesExe" label="images.exe (hoofdmap)" value={form.imagesExe} placeholder="C:\zcbs\images-werk\images.exe" problem={problems.imagesExe} onChange={(value) => set("imagesExe", value)}>
          Het programma zoals het is geïnstalleerd (met images.ini, i_view32 en plugins). De app zet een kopie in de map <Mono>images</Mono> van de gebruiker en draait die; de large-jpg's van pdf2zcbs
          (en gekozen afbeeldingen) komen in de submap <Mono>images</Mono> daarvan.
        </Text>
        <Check label={<b>Start met PERLIO=:unix</b>} checked={form.perlioUnbuffered} onChange={(value) => set("perlioUnbuffered", value)}>
          Zet de uitvoerbuffer van Perl uit, zodat de vragen van images.exe en pdf2zcbs.exe meteen zichtbaar zijn. Nodig voor automatisch uitvoeren. Invoer eindigt dan op LF.
        </Check>

        <Group title="Bestanden van de server en ZCBS">
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="sourceFolders">Mappen met scans</Label>
            <Textarea id="sourceFolders" rows={3} className="font-mono" placeholder={"Jelly=K:\\scans jelly\nRia=K:\\scans ria"} value={form.sourceFolders} onChange={(event) => set("sourceFolders", event.target.value)} />
            <Hint>
              Eén map per regel: <Mono>naam=pad</Mono>. In elke map staan submappen met scans. Op de pagina Nieuwe bestanden bladert de gebruiker door deze mappen en vinkt aan wat op het archief moet
              komen. De naam laat zien wie het gescand heeft. De bestanden op de schijf blijven staan.
            </Hint>
            {problems.sourceFolders && <Problem>{problems.sourceFolders}</Problem>}
          </div>
          <Check label={<b>Gebruik de test-ZCBS op localhost</b>} checked={form.useTestZcbs} onChange={(value) => set("useTestZcbs", value)}>
            Aan: de test-ZCBS in Docker (<Mono>tools/docker/zcbs</Mono>), nu <Mono>{settings.testZcbsUrl}</Mono>. Er wordt niets in het echte archief geschreven. Uit: het echte archief,{" "}
            <Mono>https://www.historischekringheemskerk.nl</Mono>. Dan schrijft de app echt in de collectie en op de echte server. De ftp-server volgt dezelfde keuze.
          </Check>
        </Group>

        <Group title="Resten van een eerdere run">
          <Text id="leftoversFolder" label="Map voor resten van een eerdere run" value={form.leftoversFolder} placeholder="K:\resten (leeg = de map resten van de gebruiker)" onChange={(value) => set("leftoversFolder", value)}>
            Staan er bij het begin nog bestanden in de mappen van pdf2zcbs.exe en images.exe, dan kunnen die hierheen verplaatst (of verwijderd) worden voordat de gebruiker begint.
          </Text>
        </Group>

        <Group title="FTP-wachtwoord">
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="ftpPassword">
              Wachtwoord van <Mono>{settings.ftpUser}</Mono>
            </Label>
            <div className="flex items-center gap-3">
              <Input
                id="ftpPassword"
                type="password"
                autoComplete="new-password"
                className="max-w-80"
                placeholder={settings.ftpPasswordSet ? "•••••••• (opgeslagen)" : ""}
                value={ftpPassword}
                onChange={(event) => setFtpPassword(event.target.value)}
              />
              {settings.ftpPasswordSet && (
                <label className="flex items-center gap-1.5">
                  <input type="checkbox" className="size-4.5 accent-primary" checked={clearFtpPassword} onChange={(event) => setClearFtpPassword(event.target.checked)} /> wissen
                </label>
              )}
            </div>
            <Hint>
              Eén wachtwoord voor de hele server, dus voor alle collecties. Het gaat naar images.exe en naar de ftp-upload van de app (gewone ftp, niet versleuteld, op{" "}
              <Mono>{`${settings.ftpHost}:${settings.ftpPort}`}</Mono>). Leeg laten = het opgeslagen wachtwoord houden. Weet je het niet, dan kun je het één keer intypen bij Handmatig uitvoeren van
              images.exe: de app neemt het dan over. Het staat leesbaar in het instellingenbestand, dat niet in git komt.
            </Hint>
            {problems.ftpPassword && <Problem>{problems.ftpPassword}</Problem>}
          </div>
        </Group>

        <Group title="Per collectie">
          <Hint>
            De antwoorden van images.exe en pdf2zcbs.exe staan vast in de app, op de resolutie na. Die verschilt per collectie, net als de mappen voor de back-up. Collecties waar de app nog niet mee
            werkt kun je alvast instellen.
          </Hint>
          {settings.collections.map((collection) => {
            const current = form.collections.find((it) => it.name === collection.name)!
            return (
              <Card key={collection.name} className="gap-3 border-sidebar-line">
                <h3 className="font-semibold">
                  {collection.name}
                  {!collection.implemented && " (nog niet in gebruik)"}
                </h3>
                <div className="flex flex-col gap-1.5">
                  <Label htmlFor={`resolution-${collection.name}`}>Resolutie van images.exe ({collection.name})</Label>
                  <Select id={`resolution-${collection.name}`} value={current.imagesResolution} onChange={(event) => setCollection(collection.name, "imagesResolution", event.target.value)}>
                    {settings.resolutions.map((resolution) => (
                      <option key={resolution.value} value={resolution.value}>
                        {resolution.label}
                      </option>
                    ))}
                  </Select>
                  <Hint>Het antwoord op "Kies 1,2,3 (objecten, boeken, etc), of kies 6,7,8 (foto's, etc)".</Hint>
                </div>
                <Text
                  id={`backup100pct-${collection.name}`}
                  label={`Back-upmap voor 100pct (${collection.name})`}
                  value={current.backup100pctFolder}
                  placeholder="K:\backup_100pct"
                  problem={problems[`${collection.name}.backup100pctFolder`]}
                  onChange={(value) => setCollection(collection.name, "backup100pctFolder", value)}
                >
                  De 100pct-afbeeldingen worden hierheen gekopieerd voordat de mappen van de programma's worden opgeruimd.
                </Text>
                <Text
                  id={`backupPdf-${collection.name}`}
                  label={`Back-upmap voor de pdf's (${collection.name})`}
                  value={current.backupPdfFolder}
                  placeholder="K:\backup_invoer_org"
                  problem={problems[`${collection.name}.backupPdfFolder`]}
                  onChange={(value) => setCollection(collection.name, "backupPdfFolder", value)}
                >
                  De verwerkte pdf's, met hun recordnummer als naam, worden hierheen gekopieerd voordat de invoermap wordt opgeruimd.
                </Text>
              </Card>
            )
          })}
        </Group>

        <div className="flex items-center justify-between gap-4">
          <Hint>
            Opgeslagen in <Mono>{settings.settingsFile}</Mono>
          </Hint>
          <Button type="submit" disabled={save.isPending}>
            Opslaan
          </Button>
        </div>
      </form>
    </>
  )
}

function FtpTest({ settings }: { settings: Settings }) {
  const test = useFtpTest()
  return (
    <Card className="max-w-[980px] gap-3">
      <h2 className="text-xl">Test verbinding</h2>
      <Hint>
        De app zet tekst en pdf's op de server met gewone ftp, met hetzelfde account als <Mono>images.exe</Mono>: <Mono>{settings.ftpUser}</Mono> op <Mono>{`${settings.ftpHost}:${settings.ftpPort}`}</Mono>,
        basismap <Mono>{settings.ftpBaseDir}</Mono>. Gewone ftp is niet versleuteld: wachtwoord en bestanden gaan leesbaar over het netwerk.
      </Hint>
      <Hint>
        De test maakt verbinding met de <b>opgeslagen</b> instellingen, logt in en controleert of de mappen voor tekst en pdf's bestaan. Er wordt niets geschreven.
      </Hint>
      <div>
        <Button variant="secondary" disabled={test.isPending} onClick={() => test.mutate()}>
          Test verbinding
        </Button>
      </div>
      {test.isError && <Alert variant="error">{errorMessage(test.error)}</Alert>}
      {test.data && (
        <div className="flex flex-col gap-2">
          <Alert variant={test.data.ok ? "ok" : "error"}>{test.data.message}</Alert>
          {test.data.folders.length > 0 && (
            <ul className="list-disc pl-5">
              {test.data.folders.map((folder) => (
                <li key={folder.path}>
                  <Mono>{folder.path}</Mono>: <span className={folder.exists ? "text-ok" : "text-danger"}>{folder.exists ? "bestaat" : "bestaat niet"}</span>{" "}
                  {folder.message && <span className="text-[13px] text-muted">{folder.message}</span>}
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </Card>
  )
}

const Hint = ({ children }: { children: ReactNode }) => <span className="text-[13px] text-muted">{children}</span>
const Problem = ({ children }: { children: ReactNode }) => <span className="text-[13px] text-busy">{children}</span>

function Group({ title, children }: { title: string; children: ReactNode }) {
  return (
    <fieldset className="flex flex-col gap-4">
      <legend className="mb-1.5 text-sm font-semibold">{title}</legend>
      {children}
    </fieldset>
  )
}

function Text({ id, label, value, placeholder, problem, onChange, children }: { id: string; label: string; value: string; placeholder?: string; problem?: string; onChange: (value: string) => void; children: ReactNode }) {
  return (
    <div className="flex flex-col gap-1.5">
      <Label htmlFor={id}>{label}</Label>
      <Input id={id} value={value} placeholder={placeholder} onChange={(event) => onChange(event.target.value)} />
      <Hint>{children}</Hint>
      {problem && <Problem>{problem}</Problem>}
    </div>
  )
}

function Check({ label, checked, onChange, children }: { label: ReactNode; checked: boolean; onChange: (value: boolean) => void; children: ReactNode }) {
  return (
    <label className="flex items-start gap-2.5">
      <input type="checkbox" className="mt-0.5 size-4.5 shrink-0 accent-primary" checked={checked} onChange={(event) => onChange(event.target.checked)} />
      <span>
        {label}
        <br />
        <Hint>{children}</Hint>
      </span>
    </label>
  )
}
