param(
    [Parameter(Mandatory = $true)]
    [string]$TemplatePath,
    [Parameter(Mandatory = $true)]
    [string]$SourceLayoutPath,
    [Parameter(Mandatory = $true)]
    [string]$OutputPath
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing

$layout = Get-Content -LiteralPath $SourceLayoutPath -Raw | ConvertFrom-Json
$template = Get-Content -LiteralPath $TemplatePath -Raw

function Convert-ReferenceImage {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ItemId,
        [Parameter(Mandatory = $true)]
        [int]$Width,
        [Parameter(Mandatory = $true)]
        [int]$Height
    )

    $item = $layout.items | Where-Object { $_.id -eq $ItemId } | Select-Object -First 1
    if ($null -eq $item -or [string]::IsNullOrWhiteSpace($item.imageData)) {
        throw "Missing reference image $ItemId"
    }

    $payload = ($item.imageData -split ",", 2)[1]
    $sourceBytes = [Convert]::FromBase64String($payload)
    $sourceStream = [IO.MemoryStream]::new($sourceBytes)
    $sourceImage = [Drawing.Image]::FromStream($sourceStream)
    $bitmap = [Drawing.Bitmap]::new($Width, $Height)
    $graphics = [Drawing.Graphics]::FromImage($bitmap)
    $graphics.Clear([Drawing.Color]::Black)
    $graphics.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graphics.PixelOffsetMode = [Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $graphics.DrawImage($sourceImage, 0, 0, $Width, $Height)

    $outputStream = [IO.MemoryStream]::new()
    $bitmap.Save($outputStream, [Drawing.Imaging.ImageFormat]::Png)
    $result = "data:image/png;base64," + [Convert]::ToBase64String($outputStream.ToArray())

    $outputStream.Dispose()
    $graphics.Dispose()
    $bitmap.Dispose()
    $sourceImage.Dispose()
    $sourceStream.Dispose()
    return $result
}

$top = Convert-ReferenceImage -ItemId "image-1788018461257-2" -Width 124 -Height 225
$side = Convert-ReferenceImage -ItemId "image-1788018966949-5" -Width 182 -Height 101
$bar = Convert-ReferenceImage -ItemId "image-1788019948695-8" -Width 200 -Height 90

$result = $template.Replace("__REF_TOP__", $top)
$result = $result.Replace("__REF_SIDE__", $side)
$result = $result.Replace("__REF_BAR__", $bar)

[IO.File]::WriteAllText($OutputPath, $result, [Text.UTF8Encoding]::new($false))

if ($result.Contains("__REF_TOP__") -or $result.Contains("__REF_SIDE__") -or $result.Contains("__REF_BAR__")) {
    throw "Reference placeholders remain in generated editor"
}

Write-Output ("Generated {0} bytes at {1}" -f ([Text.Encoding]::UTF8.GetByteCount($result)), $OutputPath)
