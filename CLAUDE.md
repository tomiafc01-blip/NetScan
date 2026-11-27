# NetScan — guía para continuar el proyecto

Escáner de la red Wi-Fi propia que identifica los dispositivos conectados (modelo/fabricante), de uso personal. Dueño: tomiafc01-blip, habla español: responde y escribe la UI en español de México. Solo para redes propias o con permiso.

## Compilar y publicar
- Se trabaja directo en `main`. Cada push compila en GitHub Actions (`gradle testDebugUnitTest assembleDebug`, Gradle 8.9) y publica un Release con `NetScan.apk`.
- No hay Android SDK local: para verificar, push y revisar el run (`gh run list -R tomiafc01-blip/NetScan`, `gh run view <id> --log-failed | grep " e: "`). Las pruebas unitarias deben seguir pasando.
- **El repo es público:** no subir tokens, contraseñas ni datos personales. La firma está configurada en `app/build.gradle.kts`; no cambiar la llave o hay que desinstalar la app.
- Auto-actualización desde Releases (`UpdateManager.kt`).
- Jetpack Compose (BOM 2024.10.00), minSdk 24, targetSdk 34.

## Cómo identifica (capas, en `ScanEngine.kt`)
1. `HostScanner` (ping + TCP) → IPs vivas · 2. `MdnsScanner` (Bonjour) · 3. `SsdpScanner` (UPnP: `modelName`/`manufacturer`) · 4. `NetbiosScanner` (UDP 137, nombre y MAC de PCs Windows) · 5. `BannerScanner` (puertos + banners HTTP) · 6. `OuiLookup` (fabricante por MAC). Se fusionan en `Device.kt` con un nivel de confianza.

## Mapa del código (~5 500 líneas)
| Área | Archivos |
|---|---|
| Escaneo | `ScanEngine.kt`, `ScanViewModel.kt`, los `*Scanner.kt`, `OuiLookup.kt`, `NetworkInfo.kt`, `PortScanner.kt`, `IgdClient.kt` (mapeos UPnP del router) |
| Herramientas | `NetTools.kt` (ping), `SpeedTest.kt`, `InternetInfo.kt`, `WifiScanner.kt`, `ChannelUtils.kt`, `RiskAnalyzer.kt` |
| Datos | `DeviceStore.kt` (aparatos conocidos e historial), `WatchWorker.kt` (escaneo cada 30 min, avisa de aparatos nuevos), `Notifier.kt` |
| Interfaz | `ScanScreen.kt`, `WifiTab.kt`, `HistoryTab.kt`, `DeviceSheet.kt`, `DiagSheet.kt`, `Visuals.kt` (radar), `Premium.kt`, `Theme.kt`, `NetScanWidget.kt` |
| Soporte | `Diag.kt` (errores y cierres), `UpdateManager.kt`, `App.kt` |
