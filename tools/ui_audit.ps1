# Trailmark UI layout audit — parses a uiautomator dump and reports
# layout/accessibility violations in text I can read (no image needed).
# Usage: powershell -File tools/ui_audit.ps1 <dump.xml> [density]
param(
  [Parameter(Mandatory=$true)][string]$XmlPath,
  [int]$Density = 2
)
$xml = Get-Content -Raw $XmlPath
$nodes = @()
foreach ($m in [regex]::Matches($xml, '<node[^>]*>')) {
  $n = $m.Value
  $b = [regex]::Match($n, 'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
  if (-not $b.Success) { continue }
  $nodes += [pscustomobject]@{
    x1 = [int]$b.Groups[1].Value
    y1 = [int]$b.Groups[2].Value
    x2 = [int]$b.Groups[3].Value
    y2 = [int]$b.Groups[4].Value
    text = [regex]::Match($n, 'text="([^"]*)"').Groups[1].Value
    desc = [regex]::Match($n, 'content-desc="([^"]*)"').Groups[1].Value
    cls  = [regex]::Match($n, 'class="([^"]*)"').Groups[1].Value
    clickable = ([regex]::Match($n, 'clickable="([^"]*)"').Groups[1].Value -eq 'true')
    clickableParent = [regex]::Match($n, 'clickable="([^"]*)"').Groups[1].Value
  }
}
if ($nodes.Count -eq 0) { Write-Output "no nodes parsed"; exit 1 }

$root = $nodes[0]
$W = $root.x2; $H = $root.y2
Write-Output "SCREEN ${W}x${H}px  (density $Density -> $([int]($W/$Density))x$([int]($H/$Density))dp)"
$issues = 0

# 1) Out of screen bounds (ignore the map/root which is exactly the screen)
foreach ($n in $nodes) {
  if ($n.x1 -lt 0 -or $n.y1 -lt 0 -or $n.x2 -gt $W -or $n.y2 -gt $H) {
    if ($n.cls -notmatch 'FrameLayout' -and $n.text -ne '' ) {
      Write-Output "OUT-OF-BOUNDS: [$($n.x1),$($n.y1)][$($n.x2),$($n.y2)] cls=$($n.cls) t='$($n.text)'"
      $issues++
    }
  }
}

# 2) Small touch targets (clickable, not a full-screen map, <44dp side)
$minPx = 44 * $Density
foreach ($n in $nodes) {
  if ($n.clickable) {
    $w = $n.x2 - $n.x1; $h = $n.y2 - $n.y1
    $isMap = $n.cls -match 'FrameLayout' -and ($w -ge $W - 4)
    if (-not $isMap -and ($w -lt $minPx -or $h -lt $minPx)) {
      Write-Output ("SMALL-TARGET: {0}x{1}dp at {2},{3} c='{4}'" -f [int]($w/$Density),[int]($h/$Density),[int]($n.x1/$Density),[int]($n.y1/$Density),$n.desc)
      $issues++
    }
  }
}

# 3) Overlapping siblings that both carry text/desc (visual collision)
$labeled = $nodes | Where-Object { $_.text -ne '' -or $_.desc -ne '' }
for ($i = 0; $i -lt $labeled.Count; $i++) {
  for ($j = $i + 1; $j -lt $labeled.Count; $j++) {
    $a = $labeled[$i]; $b = $labeled[$j]
    $ix = [Math]::Max(0, [Math]::Min($a.x2,$b.x2) - [Math]::Max($a.x1,$b.x1))
    $iy = [Math]::Max(0, [Math]::Min($a.y2,$b.y2) - [Math]::Max($a.y1,$b.y1))
    if ($ix -gt 2 -and $iy -gt 2) {
      Write-Output ("OVERLAP: '{0}' x '{1}' area={2}x{3}px" -f ($a.text+$a.desc), ($b.text+$b.desc), $ix, $iy)
      $issues++
    }
  }
}

Write-Output "TOTAL ISSUES: $issues"
