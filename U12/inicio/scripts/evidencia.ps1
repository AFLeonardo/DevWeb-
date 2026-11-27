<#
  Evidencia repetible del Reto U12 (sin IDE).

  Uso (desde la carpeta del proyecto, con los contenedores levantados):
      powershell -ExecutionPolicy Bypass -File .\scripts\evidencia.ps1
  Opciones:
      -BaseUrl http://localhost:8080   URL de la aplicación
      -SinFallaPostgres                Omite la prueba que detiene PostgreSQL

  Usa siempre curl.exe (no el alias "curl" de PowerShell, que es Invoke-WebRequest).
  Cada ejecución crea un alumno con matrícula nueva (EV + fecha/hora), así puede repetirse.
  Nunca ejecuta "docker compose down -v": los datos se conservan.
#>
param(
    [string]$BaseUrl = "http://localhost:8080",
    [switch]$SinFallaPostgres
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$proyecto = Split-Path -Parent $PSScriptRoot
Push-Location $proyecto

# Archivos temporales (cookies y cuerpos JSON) fuera del proyecto.
$tmp = Join-Path ([IO.Path]::GetTempPath()) ("evidencia-u12-" + [guid]::NewGuid().ToString("N").Substring(0, 8))
New-Item -ItemType Directory -Path $tmp | Out-Null
$utf8SinBom = New-Object System.Text.UTF8Encoding($false)
$resultados = New-Object System.Collections.Generic.List[object]

function Write-Json([string]$nombre, [string]$json) {
    # PowerShell 5.1 quita las comillas internas al pasar argumentos a programas nativos;
    # por eso el JSON se envía desde archivo con -d "@archivo".
    $ruta = Join-Path $tmp $nombre
    [IO.File]::WriteAllText($ruta, $json, $utf8SinBom)
    return $ruta
}

function Invoke-Curl([string[]]$argumentos) {
    # Devuelve @{ Codigo = "201"; Cuerpo = "..." }.
    $salida = & curl.exe -s --max-time 60 -w "`n__HTTP__%{http_code}" @argumentos
    $texto = ($salida -join "`n")
    $i = $texto.LastIndexOf("__HTTP__")
    return @{ Codigo = $texto.Substring($i + 8).Trim(); Cuerpo = $texto.Substring(0, $i).Trim() }
}

function Test-Caso([string]$titulo, [string]$esperado, [string]$causa, [string[]]$argumentos) {
    Write-Host ""
    Write-Host ("== " + $titulo) -ForegroundColor Cyan
    $r = Invoke-Curl $argumentos
    Write-Host ("HTTP " + $r.Codigo + "  (esperado " + $esperado + ")")
    if ($r.Cuerpo) { Write-Host ("Cuerpo: " + $r.Cuerpo) } else { Write-Host "Cuerpo: (vacío)" }
    if ($causa) { Write-Host ("Causa: " + $causa) -ForegroundColor DarkGray }
    $ok = ($r.Codigo -eq $esperado)
    if ($ok) { Write-Host "OK" -ForegroundColor Green } else { Write-Host "FALLÓ" -ForegroundColor Red }
    $resultados.Add([pscustomobject]@{ Caso = $titulo; Esperado = $esperado; Real = $r.Codigo; Resultado = $(if ($ok) { "OK" } else { "FALLÓ" }) })
    return $r
}

function Get-Csrf([string]$credenciales, [string]$cookies) {
    # GET /api/v1/csrf autenticado: crea la sesión (JSESSIONID en el archivo de cookies) y da el token.
    $r = Invoke-Curl @("-u", $credenciales, "-c", $cookies, "$BaseUrl/api/v1/csrf")
    if ($r.Codigo -ne "200") { throw "No se obtuvo token CSRF para $credenciales (HTTP $($r.Codigo))" }
    return ($r.Cuerpo | ConvertFrom-Json).token
}

function Wait-Readiness([string]$estado, [int]$segundos) {
    $limite = (Get-Date).AddSeconds($segundos)
    while ((Get-Date) -lt $limite) {
        $r = Invoke-Curl @("$BaseUrl/actuator/health/readiness")
        if ($r.Cuerpo -match ('"status":"' + $estado + '"')) { return $true }
        Start-Sleep -Seconds 2
    }
    return $false
}

try {
    Write-Host "Evidencia Reto U12 contra $BaseUrl  ($(Get-Date -Format 'yyyy-MM-dd HH:mm:ss'))" -ForegroundColor Yellow
    if (-not (Wait-Readiness "UP" 90)) { throw "La aplicación no está lista (readiness distinto de UP). ¿Ejecutaste docker compose up -d?" }

    $cookiesDocente = Join-Path $tmp "cookies-docente.txt"
    $cookiesAlumno  = Join-Path $tmp "cookies-alumno.txt"
    $tokenDocente = Get-Csrf "docente:docente123" $cookiesDocente
    $tokenAlumno  = Get-Csrf "alumno:alumno123"  $cookiesAlumno
    Write-Host "Token CSRF de docente obtenido (sesión en $cookiesDocente)"

    $matricula = "EV" + (Get-Date -Format "yyMMddHHmmss")
    $nuevo    = Write-Json "nuevo.json"    ('{"matricula":"' + $matricula + '","nombre":"Alumno Evidencia","correo":"' + $matricula.ToLower() + '@u.mx"}')
    $invalido = Write-Json "invalido.json" '{"matricula":"","nombre":"A","correo":"no-es-correo"}'
    $propio   = Write-Json "propio.json"   '{"matricula":"ALUMNO","nombre":"Cuenta alumno de laboratorio","correo":"alumno@u.mx"}'
    $json = "Content-Type: application/json"

    # ---------- 1. Éxito ----------
    $creado = Test-Caso "1. Éxito: crear alumno $matricula (docente + CSRF + cookie)" "201" "" `
        @("-i", "-u", "docente:docente123", "-b", $cookiesDocente, "-H", "X-CSRF-TOKEN: $tokenDocente", "-H", $json, "-d", "@$nuevo", "$BaseUrl/api/v1/alumnos")
    $idNuevo = $null
    if ($creado.Cuerpo -match '"id":(\d+)') { $idNuevo = $Matches[1] }

    # ---------- 2. Validación ----------
    Test-Caso "2. Validación: datos inválidos" "400" "matrícula vacía, nombre corto y correo mal formado (Bean Validation)" `
        @("-u", "docente:docente123", "-b", $cookiesDocente, "-H", "X-CSRF-TOKEN: $tokenDocente", "-H", $json, "-d", "@$invalido", "$BaseUrl/api/v1/alumnos") | Out-Null

    # ---------- 3. Ausencia ----------
    Test-Caso "3. Ausencia: alumno inexistente" "404" "el id 999999999 no existe (consulta como docente)" `
        @("-u", "docente:docente123", "$BaseUrl/api/v1/alumnos/999999999") | Out-Null

    # ---------- 4. Conflicto ----------
    Test-Caso "4. Conflicto: matrícula duplicada $matricula" "409" "la matrícula ya fue creada en el caso 1" `
        @("-u", "docente:docente123", "-b", $cookiesDocente, "-H", "X-CSRF-TOKEN: $tokenDocente", "-H", $json, "-d", "@$nuevo", "$BaseUrl/api/v1/alumnos") | Out-Null

    # ---------- 5. Seguridad: dos causas distintas ----------
    Test-Caso "5a. Seguridad: sin credenciales" "401" "no hay autenticación (falta Authorization: Basic); la responde el filtro de seguridad" `
        @("-i", "$BaseUrl/api/v1/alumnos") | Out-Null
    Test-Caso "5b. Seguridad: rol insuficiente" "403" "alumno está autenticado y envía un token CSRF válido, pero crear requiere DOCENTE o ADMIN" `
        @("-u", "alumno:alumno123", "-b", $cookiesAlumno, "-H", "X-CSRF-TOKEN: $tokenAlumno", "-H", $json, "-d", "@$nuevo", "$BaseUrl/api/v1/alumnos") | Out-Null
    Test-Caso "5c. Seguridad: falta token CSRF" "403" "docente tiene el rol correcto, pero la escritura no trae X-CSRF-TOKEN (CsrfFilter la rechaza antes de llegar al controlador)" `
        @("-u", "docente:docente123", "-H", $json, "-d", "@$nuevo", "$BaseUrl/api/v1/alumnos") | Out-Null

    # ---------- 6. Acceso por propietario ----------
    # Vínculo: el usuario "alumno" es dueño del registro con matrícula ALUMNO. Se crea si no existe.
    $r = Invoke-Curl @("-u", "docente:docente123", "-b", $cookiesDocente, "-H", "X-CSRF-TOKEN: $tokenDocente", "-H", $json, "-d", "@$propio", "$BaseUrl/api/v1/alumnos")
    Write-Host ""
    Write-Host ("Preparación: registro ALUMNO -> HTTP " + $r.Codigo + " (201 = creado ahora, 409 = ya existía)")
    $lista = (Invoke-Curl @("-u", "docente:docente123", "$BaseUrl/api/v1/alumnos")).Cuerpo | ConvertFrom-Json
    $idPropio = ($lista | Where-Object { $_.matricula -eq "ALUMNO" } | Select-Object -First 1).id
    Test-Caso "6a. Propietario: alumno consulta su registro (id $idPropio)" "200" "la matrícula ALUMNO coincide con el usuario alumno" `
        @("-u", "alumno:alumno123", "$BaseUrl/api/v1/alumnos/$idPropio") | Out-Null
    Test-Caso "6b. Propietario: alumno consulta registro ajeno (id $idNuevo)" "403" "la matrícula $matricula no es la del usuario alumno (ACCESO_DENEGADO)" `
        @("-u", "alumno:alumno123", "$BaseUrl/api/v1/alumnos/$idNuevo") | Out-Null
    Test-Caso "6c. Propietario: docente consulta registro ajeno (id $idNuevo)" "200" "DOCENTE conserva acceso completo" `
        @("-u", "docente:docente123", "$BaseUrl/api/v1/alumnos/$idNuevo") | Out-Null

    # ---------- 7. Falla controlada de PostgreSQL ----------
    if (-not $SinFallaPostgres) {
        Write-Host ""
        Write-Host "== 7. Falla controlada: docker compose stop postgres" -ForegroundColor Cyan
        cmd /c "docker compose stop postgres 2>&1" | ForEach-Object { Write-Host ("   " + $_) }
        Test-Caso "7a. Sin PostgreSQL: readiness" "503" "readiness incluye el indicador db: sin base la instancia no debe recibir tráfico" `
            @("$BaseUrl/actuator/health/readiness") | Out-Null
        Test-Caso "7b. Sin PostgreSQL: liveness" "200" "liveness sólo mira el estado interno del proceso; reiniciarlo no arregla la base" `
            @("$BaseUrl/actuator/health/liveness") | Out-Null

        Write-Host ""
        Write-Host "== 7. Recuperación: docker compose start postgres" -ForegroundColor Cyan
        cmd /c "docker compose start postgres 2>&1" | ForEach-Object { Write-Host ("   " + $_) }
        $volvio = Wait-Readiness "UP" 90
        Write-Host ("Readiness volvió a UP: " + $volvio)
        Test-Caso "7c. Con PostgreSQL de nuevo: readiness" "200" "el pool de conexiones se reconecta solo, sin reiniciar la app" `
            @("$BaseUrl/actuator/health/readiness") | Out-Null
    }
}
finally {
    Pop-Location
    Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
}

Write-Host ""
Write-Host "===== RESUMEN =====" -ForegroundColor Yellow
$resultados | Format-Table -AutoSize | Out-String -Width 200 | Write-Host
$fallos = @($resultados | Where-Object { $_.Resultado -ne "OK" }).Count
Write-Host ("Casos: " + $resultados.Count + "  OK: " + ($resultados.Count - $fallos) + "  Fallidos: " + $fallos)
if ($fallos -gt 0) { exit 1 } else { exit 0 }
