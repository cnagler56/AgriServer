# Local dev environment for AgriServer.
#
# Usage:
#   1. Copy this file to `dev-env.ps1` (which is gitignored).
#   2. Fill in your actual values below.
#   3. Dot-source it into your PowerShell session BEFORE running mvnw:
#        . .\dev-env.ps1
#      The leading dot + space matters — it loads the variables into
#      your current shell instead of a child process.
#   4. Then:
#        .\mvnw.cmd spring-boot:run
#
# A permanent alternative: set these the same way under Windows
# "Environment Variables" so any PowerShell window inherits them.

# ── Java (only needed if JAVA_HOME isn't set permanently) ──
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17.0.4.1"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

# ── Database ──────────────────────────────────────────────
$env:DB_URL      = "jdbc:mysql://localhost:3306/corn"
$env:DB_USER     = "root"
$env:DB_PASSWORD = "REPLACE_ME"

# ── USDA NASS Quick Stats API key ─────────────────────────
# Get one at https://quickstats.nass.usda.gov/api
$env:USDA_API_KEY = "REPLACE_ME"
