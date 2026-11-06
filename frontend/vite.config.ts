import { fileURLToPath } from "node:url"
import tailwindcss from "@tailwindcss/vite"
import react from "@vitejs/plugin-react"
import { defineConfig } from "vite"

// De oude schermen staan nog op de root; tot die weg zijn woont de frontend onder /app.
export default defineConfig({
  base: "/app/",
  plugins: [react(), tailwindcss()],
  resolve: { alias: { "@": fileURLToPath(new URL("./src", import.meta.url)) } },
  // Spring Boot serveert dit uit de jar (FrontendConfig).
  build: { outDir: "../target/classes/frontend", emptyOutDir: true },
  // Tijdens het ontwikkelen draait de backend op 9000.
  server: { proxy: { "/api": "http://localhost:9000" } },
})
