# Generates procedural mob sounds (growls, snarls, villager hums) as
# 16-bit mono PCM WAV files into src/main/resources/sounds.
# The engine has no artist-made sound assets for mobs, so these clips are
# synthesized here once. Re-run to regenerate them identically (the RNG is
# seeded, so output is deterministic).

$ErrorActionPreference = "Stop"
$rate = 22050
$outRoot = Join-Path $PSScriptRoot "..\src\main\resources\sounds"
$rng = New-Object System.Random(20240815)

function Write-Wav {
    param([byte[]]$Pcm, [string]$Path)
    $ms = New-Object System.IO.MemoryStream
    $w = New-Object System.IO.BinaryWriter($ms)
    $dataLen = $Pcm.Length
    $w.Write([System.Text.Encoding]::ASCII.GetBytes("RIFF"))
    $w.Write([int](36 + $dataLen))
    $w.Write([System.Text.Encoding]::ASCII.GetBytes("WAVE"))
    $w.Write([System.Text.Encoding]::ASCII.GetBytes("fmt "))
    $w.Write([int]16)
    $w.Write([int16]1)      # PCM
    $w.Write([int16]1)      # mono
    $w.Write([int]$rate)
    $w.Write([int]($rate * 2))
    $w.Write([int16]2)
    $w.Write([int16]16)
    $w.Write([System.Text.Encoding]::ASCII.GetBytes("data"))
    $w.Write([int]$dataLen)
    $w.Write($Pcm)
    $w.Flush()
    $dir = Split-Path $Path -Parent
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    [System.IO.File]::WriteAllBytes($Path, $ms.ToArray())
    $w.Dispose()
    $ms.Dispose()
    Write-Host "  wrote $Path" -ForegroundColor DarkGray
}

function New-Pcm {
    param([int]$Samples, [scriptblock]$Fn)
    $bytes = New-Object byte[] ($Samples * 2)
    $i = 0
    for ($n = 0; $n -lt $Samples; $n++) {
        $t = $n / $rate
        $v = [double](& $Fn $t $n)
        if ($v -gt 1.0) { $v = 1.0 }
        if ($v -lt -1.0) { $v = -1.0 }
        $s = [int]($v * 32767)
        $bytes[$i]   = $s -band 0xFF
        $bytes[$i+1] = ($s -shr 8) -band 0xFF
        $i += 2
    }
    return $bytes
}

# --- Zoloy: low, gravelly zombie growl ---
# 3 idle variants, deterministic RNG picks the wobble seed each run.
foreach ($k in 0,1,2) {
    $seed = $k * 7919 + 13
    $samples = [int](0.7 * $rate)
    $pcm = New-Pcm -Samples $samples -Fn {
        param($t, $n)
        $slowWobble = [Math]::Sin(2 * [Math]::PI * (2.0 + $k * 0.7) * $t)
        $midWobble  = [Math]::Sin(2 * [Math]::PI * (5.3 + $k) * $t + 1.2)
        $amp = 0.55 + 0.18 * $slowWobble + 0.12 * $midWobble
        if ($t -lt 0.06) { $amp *= $t / 0.06 }                 # attack
        if ($t -gt 0.55) { $amp *= (0.7 - ($t - 0.55)) / 0.15 } # release
        $growl = [Math]::Sin(2 * [Math]::PI * 82 * $t) * 0.55
        $growl += [Math]::Sin(2 * [Math]::PI * 164 * $t) * 0.30
        $noise = ($rng.NextDouble() * 2 - 1) * 0.22
        return ($growl + $noise) * $amp
    }
    Write-Wav -Pcm $pcm -Path (Join-Path $outRoot "zoloy\$k.wav")
}

# Hurt: shorter, sharper growl that rises in pitch
$samples = [int](0.35 * $rate)
$pcm = New-Pcm -Samples $samples -Fn {
    param($t, $n)
    $f = 130 + 120 * $t
    $amp = 0.8
    if ($t -lt 0.02) { $amp *= $t / 0.02 }
    if ($t -gt 0.25) { $amp *= (0.35 - ($t - 0.25)) / 0.10 }
    $tone = [Math]::Sin(2 * [Math]::PI * $f * $t) * 0.6
    $tone += [Math]::Sin(2 * [Math]::PI * $f * 2 * $t) * 0.25
    $noise = ($rng.NextDouble() * 2 - 1) * 0.3
    return ($tone + $noise) * $amp
}
Write-Wav -Pcm $pcm -Path (Join-Path $outRoot "zoloy\3.wav")

# Death: long descending groan that fades out
$samples = [int](1.0 * $rate)
$pcm = New-Pcm -Samples $samples -Fn {
    param($t, $n)
    $f = 110 - 50 * $t
    $amp = 0.7
    if ($t -lt 0.04) { $amp *= $t / 0.04 }
    if ($t -gt 0.75) { $amp *= (1.0 - ($t - 0.75)) / 0.25 }
    $tone = [Math]::Sin(2 * [Math]::PI * $f * $t) * 0.5
    $tone += [Math]::Sin(2 * [Math]::PI * ($f * 1.5) * $t) * 0.25
    $noise = ($rng.NextDouble() * 2 - 1) * 0.18 * $amp
    return ($tone * $amp + $noise)
}
Write-Wav -Pcm $pcm -Path (Join-Path $outRoot "zoloy\4.wav")

# Attack snarl: noise burst over a rising tone, short and aggressive
$samples = [int](0.28 * $rate)
$pcm = New-Pcm -Samples $samples -Fn {
    param($t, $n)
    $f = 140 + 260 * $t
    $amp = 0.85
    if ($t -lt 0.015) { $amp *= $t / 0.015 }
    if ($t -gt 0.18) { $amp *= (0.28 - ($t - 0.18)) / 0.10 }
    $tone = [Math]::Sin(2 * [Math]::PI * $f * $t) * 0.4
    $noise = ($rng.NextDouble() * 2 - 1) * 0.5
    return ($tone + $noise) * $amp
}
Write-Wav -Pcm $pcm -Path (Join-Path $outRoot "zoloy\5.wav")

# --- Villager: soft, warm hums ---
foreach ($k in 0,1) {
    $samples = [int](0.55 * $rate)
    $pcm = New-Pcm -Samples $samples -Fn {
        param($t, $n)
        $vib = 1.0 + 0.02 * [Math]::Sin(2 * [Math]::PI * 5 * $t)
        $amp = 0.5
        if ($t -lt 0.1) { $amp *= $t / 0.1 }
        if ($t -gt 0.4) { $amp *= (0.55 - ($t - 0.4)) / 0.15 }
        $f = 196; if ($k -eq 1) { $f = 246 }   # G3 / B3
        $tone = [Math]::Sin(2 * [Math]::PI * $f * $vib * $t) * 0.7
        $tone += [Math]::Sin(2 * [Math]::PI * $f * 2 * $vib * $t) * 0.2
        return $tone * $amp
    }
    Write-Wav -Pcm $pcm -Path (Join-Path $outRoot "villager\$k.wav")
}

# Hurt: a startled squeak, quick upward chirp
$samples = [int](0.22 * $rate)
$pcm = New-Pcm -Samples $samples -Fn {
    param($t, $n)
    $f = 380 + 220 * $t
    $amp = 0.6
    if ($t -lt 0.015) { $amp *= $t / 0.015 }
    if ($t -gt 0.14) { $amp *= (0.22 - ($t - 0.14)) / 0.08 }
    $tone = [Math]::Sin(2 * [Math]::PI * $f * $t) * 0.8
    return $tone * $amp
}
Write-Wav -Pcm $pcm -Path (Join-Path $outRoot "villager\2.wav")

Write-Host "Mob sounds generated." -ForegroundColor Green
