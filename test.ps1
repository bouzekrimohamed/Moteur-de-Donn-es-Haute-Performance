$ErrorActionPreference = "Stop"

$baseUrl = "http://localhost:8080"
$parquetFile = "C:\projet base de donnee\Moteur-de-Donn-es-Haute-Performance\yellow_tripdata_2025-01.parquet"
$previewRowCount = 5
$previewColumnCount = 6

Write-Host "====================================" -ForegroundColor Cyan
Write-Host " Visualisation Fichier Parquet" -ForegroundColor Cyan
Write-Host "====================================" -ForegroundColor Cyan

if (-not (Test-Path $parquetFile)) {
  throw "Fichier parquet introuvable: $parquetFile"
}

$file = Get-Item $parquetFile

Write-Host "`nFichier teste :" -ForegroundColor Yellow
Write-Host "Chemin : $($file.FullName)" -ForegroundColor Gray
Write-Host "Taille : $([Math]::Round($file.Length / 1MB, 2)) MB" -ForegroundColor Gray

Write-Host "`n[1] Chargement du fichier parquet" -ForegroundColor Yellow
Write-Host "POST $baseUrl/tables/load-parquet" -ForegroundColor DarkGray
$body = @{
  filePath = $parquetFile
} | ConvertTo-Json -Depth 5

$r1 = Invoke-RestMethod -Method Post -Uri "$baseUrl/tables/load-parquet" -ContentType "application/json" -Body $body
$tableName = $r1.tableName
Write-Host "Table chargee : $tableName" -ForegroundColor Green

Write-Host "`n[2] Liste des tables" -ForegroundColor Yellow
Write-Host "GET $baseUrl/tables" -ForegroundColor DarkGray
$tables = Invoke-RestMethod -Method Get -Uri "$baseUrl/tables"
@($tables) | Select-Object tableName | Format-Table -AutoSize

Write-Host "`n[3] Schema de la table chargee" -ForegroundColor Yellow
Write-Host "GET $baseUrl/tables/$tableName" -ForegroundColor DarkGray
$r2 = Invoke-RestMethod -Method Get -Uri "$baseUrl/tables/$tableName"
Write-Host "Nom de table : $($r2.tableName)" -ForegroundColor Green

Write-Host "`nColonnes detectees :" -ForegroundColor Yellow
@($r2.columns) | Format-Table name, type -AutoSize

$columnCount = @($r2.columns).Count
Write-Host "`nNombre de colonnes : $columnCount" -ForegroundColor Green

Write-Host "`n[4] Apercu des donnees" -ForegroundColor Yellow
Write-Host "GET $baseUrl/tables/$tableName/rows/preview?limit=$previewRowCount" -ForegroundColor DarkGray
$rows = Invoke-RestMethod -Method Get -Uri "$baseUrl/tables/$tableName/rows/preview?limit=$previewRowCount"
$rowCount = @($rows).Count
Write-Host "Nombre de lignes affichees : $rowCount" -ForegroundColor Green

if ($rowCount -gt 0) {
  $previewColumns = @($r2.columns | Select-Object -First $previewColumnCount | ForEach-Object { $_.name })
  Write-Host "`nColonnes affichees dans l'apercu :" -ForegroundColor Yellow
  $previewColumns -join ", " | Write-Host -ForegroundColor Gray

  Write-Host "`nPremieres lignes :" -ForegroundColor Yellow
  @($rows) |
    Select-Object -First $previewRowCount -Property $previewColumns |
    Format-Table -AutoSize
} else {
  Write-Host "Aucune ligne a afficher." -ForegroundColor DarkYellow
}

Write-Host "`n====================================" -ForegroundColor Green
Write-Host " Visualisation terminee" -ForegroundColor Green
Write-Host "====================================" -ForegroundColor Green