$ErrorActionPreference = 'Stop'
New-Item -ItemType Directory -Force -Path "$PSScriptRoot/../design/assets" | Out-Null
curl.exe --fail -L 'https://www.figma.com/api/mcp/asset/58a725a4-16c2-43b3-b9b2-664961596d88/ffd02.svg' -o "$PSScriptRoot/../design/assets/ic_moon.svg"
if ($LASTEXITCODE -ne 0) { throw 'Design asset download failed' }
curl.exe --fail -L 'https://www.figma.com/api/mcp/asset/58a725a4-16c2-43b3-b9b2-664961596d88/3f6a7.svg' -o "$PSScriptRoot/../design/assets/ic_search.svg"
if ($LASTEXITCODE -ne 0) { throw 'Design asset download failed' }
curl.exe --fail -L 'https://www.figma.com/api/mcp/asset/c78cd1b4-abb0-47d1-8c51-6caa72c2b660/ccca0.svg' -o "$PSScriptRoot/../design/assets/ic_back.svg"
if ($LASTEXITCODE -ne 0) { throw 'Design asset download failed' }
curl.exe --fail -L 'https://www.figma.com/api/mcp/asset/330e6327-3b76-445d-b0f5-1d5de5b42c3a/19bb4.svg' -o "$PSScriptRoot/../design/assets/ic_search_blue.svg"
if ($LASTEXITCODE -ne 0) { throw 'Design asset download failed' }
curl.exe --fail -L 'https://www.figma.com/api/mcp/asset/330e6327-3b76-445d-b0f5-1d5de5b42c3a/51547.svg' -o "$PSScriptRoot/../design/assets/ic_close.svg"
if ($LASTEXITCODE -ne 0) { throw 'Design asset download failed' }
curl.exe --fail -L 'https://www.figma.com/api/mcp/asset/330e6327-3b76-445d-b0f5-1d5de5b42c3a/bba0c.svg' -o "$PSScriptRoot/../design/assets/ic_next.svg"
if ($LASTEXITCODE -ne 0) { throw 'Design asset download failed' }
curl.exe --fail -L 'https://www.figma.com/api/mcp/asset/330e6327-3b76-445d-b0f5-1d5de5b42c3a/664f3.svg' -o "$PSScriptRoot/../design/assets/ic_calendar.svg"
if ($LASTEXITCODE -ne 0) { throw 'Design asset download failed' }
curl.exe --fail -L 'https://www.figma.com/api/mcp/asset/6f41b2ad-b1ef-47f3-a81a-8963e89fbaa1/7fb60.svg' -o "$PSScriptRoot/../design/assets/korea_map.svg"
if ($LASTEXITCODE -ne 0) { throw 'Design asset download failed' }
curl.exe --fail -L 'https://www.figma.com/api/mcp/asset/c6a983bf-324d-48ee-9357-5fc08dd14bc8/12375.png' -o "$PSScriptRoot/../design/assets/brand_logo.png"
if ($LASTEXITCODE -ne 0) { throw 'Design asset download failed' }
