param([string]$Manifest, [string]$OutputDirectory)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Speech
$scenes = Get-Content -Raw -LiteralPath $Manifest | ConvertFrom-Json
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$voice = New-Object System.Speech.Synthesis.SpeechSynthesizer
$voice.SelectVoice('Microsoft Zira Desktop')
$voice.Rate = -1
$voice.Volume = 100
try {
    foreach ($scene in $scenes) {
        $index = 0
        foreach ($sentence in $scene.sentences) {
            $index++
            $filename = '{0:D2}-{1:D2}.wav' -f [int]$scene.id, $index
            $voice.SetOutputToWaveFile((Join-Path $OutputDirectory $filename))
            $voice.Speak([string]$sentence)
            $voice.SetOutputToNull()
        }
    }
} finally { $voice.Dispose() }
Write-Output 'English narration rendered with Microsoft Zira Desktop.'
