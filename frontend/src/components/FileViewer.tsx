import { createContext, type ReactNode, useCallback, useContext, useEffect, useMemo, useRef, useState } from "react"
import { Button } from "@/components/ui/button"

export type ViewedFile = { url: string; title: string; kind: string }

const ViewerContext = createContext<(file: ViewedFile) => void>(() => {})

/** Opent een bestand (afbeelding, pdf of tekst) in een venster boven het scherm. */
export function useFileViewer() {
  return useContext(ViewerContext)
}

export function FileViewerProvider({ children }: { children: ReactNode }) {
  const [file, setFile] = useState<ViewedFile | null>(null)
  const dialog = useRef<HTMLDialogElement>(null)
  const show = useCallback((next: ViewedFile) => setFile(next), [])
  const context = useMemo(() => show, [show])

  useEffect(() => {
    if (file && !dialog.current?.open) dialog.current?.showModal()
  }, [file])

  return (
    <ViewerContext.Provider value={context}>
      {children}
      <dialog
        ref={dialog}
        aria-label="Bestand bekijken"
        onClose={() => setFile(null)}
        // Een klik naast het venster, op de achtergrond, sluit het ook.
        onClick={(event) => event.target === dialog.current && dialog.current?.close()}
        className="m-auto h-[min(92vh,960px)] w-[min(1000px,94vw)] overflow-hidden rounded-xl backdrop:bg-black/55 open:flex open:flex-col"
      >
        {file && (
          <>
            <div className="flex items-center gap-3 border-b border-line bg-card px-3.5 py-2.5">
              <span className="flex-1 truncate font-semibold">{file.title}</span>
              <Button asChild variant="secondary" size="small">
                <a href={`${file.url}${file.url.includes("?") ? "&" : "?"}download=true`} download>
                  Download
                </a>
              </Button>
              <Button size="small" onClick={() => dialog.current?.close()}>
                Sluiten
              </Button>
            </div>
            <div className="flex min-h-0 flex-1 justify-center overflow-auto bg-neutral">
              {file.kind === "image" ? (
                <img src={file.url} alt={file.title} className="h-auto max-w-full self-start" />
              ) : (
                <iframe src={file.url} title={file.title} className="h-full w-full border-0 bg-card" />
              )}
            </div>
          </>
        )}
      </dialog>
    </ViewerContext.Provider>
  )
}
