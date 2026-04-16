$ErrorActionPreference = "Stop"

$baseUrl = "http://localhost:8080"
$parquetFile = "C:\projet base de donnee\Moteur-de-Donn-es-Haute-Performance\yellow_tripdata_2025-01.parquet"

Write-Host "== Test API simple ==" -ForegroundColor Cyan

# 1) Create table users
Write-Host "`n[1] Create table users" -ForegroundColor Yellow
$create = @{
  tableName = "users"
  columns = @(
    @{ name = "id"; type = "INT" },
    @{ name = "name"; type = "STRING" }
  )
} | ConvertTo-Json -Depth 5

try {
  Invoke-RestMethod -Method Post -Uri "$baseUrl/tables" -ContentType "application/json" -Body $create | Out-Null
  Write-Host "OK: table users creee" -ForegroundColor Green
} catch {
  Write-Host "INFO: table users existe deja (ou erreur)" -ForegroundColor DarkYellow
}

# 2) Load  in users
Write-Host "`n[2] Load rows in users" -ForegroundColor Yellow
$load = @{
  rows = @(
    @{ id = 1; name = "Alice" },
    @{ id = 2; name = "Bob" }
  )
} | ConvertTo-Json -Depth 5

Invoke-RestMethod -Method Post -Uri "$baseUrl/tables/users/load" -ContentType "application/json" -Body $load | ConvertTo-Json -Depth 10

# 3) Preview users rows
Write-Host "`n[3] Preview users rows (limit 5)" -ForegroundColor Yellow
Invoke-RestMethod -Method Get -Uri "$baseUrl/tables/users/rows/preview?limit=5" | Format-Table -AutoSize

# 4) OPTIONAL: load parquet and preview
if (Test-Path $parquetFile) {
  Write-Host "`n[4] Load parquet file (optional)" -ForegroundColor Yellow
  $pbody = @{ filePath = $parquetFile } | ConvertTo-Json -Depth 3
  $ptable = Invoke-RestMethod -Method Post -Uri "$baseUrl/tables/load-parquet" -ContentType "application/json" -Body $pbody

  Write-Host "Table parquet chargee: $($ptable.tableName)" -ForegroundColor Green

  Write-Host "`nPreview parquet rows (limit 5)" -ForegroundColor Yellow
  Invoke-RestMethod -Method Get -Uri "$baseUrl/tables/$($ptable.tableName)/rows/preview?limit=5" | Format-Table -AutoSize
} else {
  Write-Host "`n[4] Parquet introuvable, skip" -ForegroundColor DarkYellow
}

Write-Host "`n== FIN ==" -ForegroundColor Green