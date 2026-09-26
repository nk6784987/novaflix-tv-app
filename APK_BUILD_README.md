# Debug APK kaise banayein (GitHub Actions se)

1. GitHub par ek naya repository banayein (private bhi chalega).
2. Is folder ki saari files us repository mein upload/push kar dein.
   (`.github` folder bhi zaroor upload ho — usi mein build ka setup hai.)
3. Repository mein **Actions** tab kholein → "Build Debug APK" → agar khud shuru na ho to
   **Run workflow** dabayein.
4. Build ~5-10 minute leta hai. Green tick aane par us run ko kholein aur niche
   **Artifacts** section se `cinestream-debug-apk` download karein.
5. ZIP kholein, andar `app-debug.apk` milega. Use phone par bhejein aur install karein
   (phone mein "Install unknown apps" allow karna hoga).

Note: App mein Firebase / API keys ki zaroorat ho to `.env.example` dekh kar repo mein
`.env` file add karein, warna kuch features kaam nahi karenge.
