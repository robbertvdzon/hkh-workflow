import { fileURLToPath } from "node:url"
import tailwindcss from "@tailwindcss/vite"
import react from "@vitejs/plugin-react"
import { defineConfig } from "vite"

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: { alias: { "@": fileURLToPath(new URL("./src", import.meta.url)) } },
  // Spring Boot serveert dit uit de jar (FrontendConfig).
  build: { outDir: "../target/classes/frontend", emptyOutDir: true },
  // Tijdens het ontwikkelen draait de backend op 9000.
  server: { proxy: { "/api": "http://localhost:9000", "/actuator": "http://localhost:9000" } },
})
