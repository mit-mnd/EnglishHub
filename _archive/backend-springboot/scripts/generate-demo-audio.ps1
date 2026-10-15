<#
  Tạo audio demo cho các bài Listening bằng giọng đọc TTS có sẵn của Windows (David / Zira).
  - Mỗi câu được đọc riêng, ghép lại thành 1 file WAV (16 kHz, 16-bit, mono), cách nhau 350 ms.
  - In ra mốc thời gian (StartMs/EndMs) của từng câu để dùng trong migration phụ đề (V4).
  Chạy lại script sẽ ghi đè file WAV -> phải cập nhật mốc thời gian trong migration tương ứng.

  Cách chạy (Windows PowerShell 5.1):  powershell -File scripts/generate-demo-audio.ps1
#>
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Speech

$outDir = Join-Path $PSScriptRoot '..\src\main\resources\static\media\lessons'
New-Item -ItemType Directory -Force $outDir | Out-Null

$sampleRate = 16000
$bytesPerMs = $sampleRate * 2 / 1000          # 16-bit mono
$gapMs = 350

$lessons = [ordered]@{
  'greetings' = @(
    @('Zira',  "Hi! I'm Anna. What's your name?"),
    @('David', 'Hello, Anna. My name is Nam.'),
    @('Zira',  'Nice to meet you, Nam. Where are you from?'),
    @('David', "I'm from Vietnam. And you?"),
    @('Zira',  "I'm from Canada. Nice to meet you too.")
  )
  'coffee-shop' = @(
    @('Zira',  'Good morning! What can I get for you?'),
    @('David', 'Can I have a large iced latte, please?'),
    @('Zira',  'Sure. Would you like anything to eat?'),
    @('David', 'Yes, a chocolate muffin, please.'),
    @('Zira',  "That's six dollars fifty."),
    @('David', 'Here you are. Thank you!')
  )
  'job-interview' = @(
    @('David', 'Good afternoon. Thank you for coming in today.'),
    @('Zira',  "Thank you for having me. I'm glad to be here."),
    @('David', 'Could you tell me a little about yourself?'),
    @('Zira',  'Sure. I graduated in marketing two years ago, and since then I have worked for a small tech company.'),
    @('David', 'Why do you want to work for us?'),
    @('Zira',  'I admire your products, and I want to grow in a larger, international team.'),
    @('David', 'What is your greatest strength?'),
    @('Zira',  'I am a good listener, and I always meet my deadlines.'),
    @('David', 'Great. We will contact you by the end of the week.')
  )
}

function Get-SpeechPcm([string]$voice, [string]$text) {
  $synth = New-Object System.Speech.Synthesis.SpeechSynthesizer
  try {
    $synth.SelectVoice("Microsoft $voice Desktop")
    $synth.Rate = -1                               # đọc chậm hơn một chút cho người học
    $stream = New-Object System.IO.MemoryStream
    $format = New-Object System.Speech.AudioFormat.SpeechAudioFormatInfo($sampleRate,
      [System.Speech.AudioFormat.AudioBitsPerSample]::Sixteen, [System.Speech.AudioFormat.AudioChannel]::Mono)
    $synth.SetOutputToAudioStream($stream, $format)
    $synth.Speak($text)
    return , $stream.ToArray()
  } finally {
    $synth.Dispose()
  }
}

function Write-Wav([string]$path, [byte[]]$pcm) {
  $fs = [System.IO.File]::Create($path)
  $w = New-Object System.IO.BinaryWriter($fs)
  try {
    $w.Write([Text.Encoding]::ASCII.GetBytes('RIFF')); $w.Write([int](36 + $pcm.Length))
    $w.Write([Text.Encoding]::ASCII.GetBytes('WAVE')); $w.Write([Text.Encoding]::ASCII.GetBytes('fmt '))
    $w.Write([int]16); $w.Write([int16]1); $w.Write([int16]1)               # PCM, mono
    $w.Write([int]$sampleRate); $w.Write([int]($sampleRate * 2))          # byte rate
    $w.Write([int16]2); $w.Write([int16]16)                                # block align, bits
    $w.Write([Text.Encoding]::ASCII.GetBytes('data')); $w.Write([int]$pcm.Length)
    $w.Write($pcm)
  } finally {
    $w.Dispose()
  }
}

$timings = [ordered]@{}
foreach ($name in $lessons.Keys) {
  $buffer = New-Object System.Collections.Generic.List[byte]
  $segments = @()
  $silence = New-Object byte[] ([int]($gapMs * $bytesPerMs))
  foreach ($line in $lessons[$name]) {
    $pcm = Get-SpeechPcm $line[0] $line[1]
    $start = [int]($buffer.Count / $bytesPerMs)
    $buffer.AddRange($pcm)
    $end = [int]($buffer.Count / $bytesPerMs)
    $segments += [pscustomobject]@{ start = $start; end = $end; text = $line[1] }
    $buffer.AddRange($silence)
  }
  $file = Join-Path $outDir "$name.wav"
  Write-Wav $file $buffer.ToArray()
  $timings[$name] = [pscustomobject]@{ durationMs = [int]($buffer.Count / $bytesPerMs); segments = $segments }
}

$timings | ConvertTo-Json -Depth 5
